package io.github.inwins.filebrowser.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.PriorityQueue

data class DuplicateGroup(
    val size: Long,
    val files: List<File>,
) {
    val reclaimable: Long get() = size * (files.size - 1).coerceAtLeast(0)
}

data class LargeFile(val file: File, val size: Long)

data class CacheEntry(val label: String, val dir: File, val size: Long)

object StorageCleaner {
    fun findDuplicates(
        root: File,
        onProgress: (Int) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): List<DuplicateGroup> {
        if (!root.exists()) return emptyList()
        val bySize = mutableMapOf<Long, MutableList<File>>()
        var scanned = 0
        walkFiles(root, shouldContinue) { file ->
            scanned++
            if (scanned % 64 == 0) onProgress(scanned)
            val size = file.length()
            if (size <= 0) return@walkFiles
            bySize.getOrPut(size) { mutableListOf() }.add(file)
        }
        if (!shouldContinue()) return emptyList()
        val groups = mutableListOf<DuplicateGroup>()
        for ((size, files) in bySize) {
            if (files.size < 2) continue
            if (!shouldContinue()) break
            val byHash = mutableMapOf<String, MutableList<File>>()
            for (file in files) {
                if (!shouldContinue()) break
                val hash = runCatching { sha256(file, shouldContinue) }.getOrNull() ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(file)
            }
            byHash.values.filter { it.size > 1 }.forEach {
                groups.add(DuplicateGroup(size, it.sortedBy { f -> f.absolutePath }))
            }
        }
        onProgress(scanned)
        return groups.sortedByDescending { it.reclaimable }
    }

    fun findLargeFiles(
        root: File,
        limit: Int = 100,
        onProgress: (Int) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): List<LargeFile> {
        if (!root.exists()) return emptyList()
        val queue = PriorityQueue<LargeFile>(compareBy { it.size })
        var scanned = 0
        walkFiles(root, shouldContinue) { file ->
            scanned++
            if (scanned % 64 == 0) onProgress(scanned)
            val size = file.length()
            if (size <= 0) return@walkFiles
            queue.add(LargeFile(file, size))
            while (queue.size > limit) queue.poll()
        }
        onProgress(scanned)
        return queue.toList().sortedByDescending { it.size }
    }

    fun cacheEntries(context: Context): List<CacheEntry> {
        val candidates = listOf(
            "应用缓存" to context.cacheDir,
            "外部缓存" to context.externalCacheDir,
            "缩略图缓存" to File("/sdcard/DCIM/.thumbnails"),
            "系统缩略图" to File("/sdcard/.thumbnails"),
        )
        return candidates.mapNotNull { (label, dir) ->
            if (dir == null || !dir.exists()) return@mapNotNull null
            val size = FileOps.folderSize(dir)
            if (size <= 0) null else CacheEntry(label, dir, size)
        }
    }

    fun cleanCaches(context: Context): Long {
        var freed = 0L
        context.cacheDir?.listFiles()?.forEach {
            freed += FileOps.folderSize(it)
            FileOps.deleteRecursive(it)
        }
        context.externalCacheDir?.listFiles()?.forEach {
            freed += FileOps.folderSize(it)
            FileOps.deleteRecursive(it)
        }
        listOf(File("/sdcard/DCIM/.thumbnails"), File("/sdcard/.thumbnails")).forEach { dir ->
            runCatching {
                dir.listFiles()?.forEach {
                    freed += FileOps.folderSize(it)
                    FileOps.deleteRecursive(it)
                }
            }
        }
        return freed
    }

    private fun walkFiles(root: File, shouldContinue: () -> Boolean, action: (File) -> Unit) {
        val stack = ArrayDeque<File>()
        stack.add(root)
        val visited = HashSet<String>()
        while (stack.isNotEmpty() && shouldContinue()) {
            val dir = stack.removeLast()
            if (FileOps.isSymlink(dir)) continue
            val canonical = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
            if (!visited.add(canonical)) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (!shouldContinue()) break
                if (child.isDirectory) {
                    if (child.name == "obb" || (child.name == "data" && child.parentFile?.name == "Android")) {
                        continue
                    }
                    stack.add(child)
                } else {
                    action(child)
                }
            }
        }
    }

    private fun sha256(file: File, shouldContinue: () -> Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (!shouldContinue()) return ""
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
