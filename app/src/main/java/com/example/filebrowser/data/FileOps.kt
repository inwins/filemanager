package com.example.filebrowser.data

import com.example.filebrowser.util.Format
import com.example.filebrowser.util.Paths
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FileOps {
    private const val BUFFER = 64 * 1024

    fun sort(items: List<FileItem>, spec: SortSpec): List<FileItem> {
        val comparator = Comparator<FileItem> { a, b ->
            if (a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val cmp = when (spec.field) {
                SortField.NAME -> Format.naturalCompare(a.name, b.name)
                SortField.SIZE -> a.size.compareTo(b.size)
                SortField.DATE -> a.lastModified.compareTo(b.lastModified)
                SortField.TYPE -> {
                    val typeCmp = a.extension.compareTo(b.extension)
                    if (typeCmp != 0) typeCmp else Format.naturalCompare(a.name, b.name)
                }
            }
            if (spec.order == SortOrder.DESC) -cmp else cmp
        }
        return items.sortedWith(comparator)
    }

    fun uniqueFile(dest: File): File {
        if (!dest.exists()) return dest
        val name = dest.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(dest.parentFile, "$base ($index)$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    fun copyRecursive(
        src: File,
        dest: File,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        if (!src.exists()) return false
        val srcCanonical = runCatching { src.canonicalPath }.getOrElse { src.absolutePath }
        val destCanonical = runCatching { dest.canonicalPath }.getOrElse { dest.absolutePath }
        if (srcCanonical == destCanonical) return false
        if (src.isDirectory && destCanonical.startsWith(srcCanonical + File.separator)) return false
        return copyInternal(src, dest, onBytes, shouldContinue, HashSet())
    }

    private fun copyInternal(
        src: File,
        dest: File,
        onBytes: (Long) -> Unit,
        shouldContinue: () -> Boolean,
        visited: MutableSet<String>,
    ): Boolean {
        if (!shouldContinue()) return false
        if (isSymlink(src)) return false
        val canonical = runCatching { src.canonicalPath }.getOrElse { src.absolutePath }
        if (src.isDirectory) {
            if (!visited.add(canonical)) return true
            if (!dest.exists() && !dest.mkdirs()) return false
            val children = src.listFiles() ?: return true
            for (child in children) {
                if (!copyInternal(child, File(dest, child.name), onBytes, shouldContinue, visited)) {
                    return false
                }
            }
            runCatching { dest.setLastModified(src.lastModified()) }
            return true
        }
        val parent = dest.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        return runCatching {
            BufferedInputStream(FileInputStream(src)).use { input ->
                BufferedOutputStream(FileOutputStream(dest)).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        if (!shouldContinue()) return false
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                    output.flush()
                }
            }
            dest.setLastModified(src.lastModified())
            true
        }.getOrDefault(false)
    }

    fun move(
        src: File,
        dest: File,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        if (!src.exists()) return false
        if (src.isDirectory) {
            val srcCanonical = runCatching { src.canonicalPath }.getOrElse { src.absolutePath }
            val destCanonical = runCatching { dest.canonicalPath }.getOrElse { dest.absolutePath }
            if (destCanonical.startsWith(srcCanonical + File.separator)) return false
        }
        if (runCatching { src.renameTo(dest) }.getOrDefault(false)) return true
        if (!copyRecursive(src, dest, onBytes, shouldContinue)) return false
        return deleteRecursive(src)
    }

    fun deleteRecursive(file: File): Boolean {
        if (!file.exists()) return true
        if (isSymlink(file)) return runCatching { file.delete() }.getOrDefault(false)
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteRecursive(child)) return false
                }
            }
        }
        return runCatching { file.delete() }.getOrDefault(false)
    }

    fun rename(file: File, newName: String): File? {
        if (!Paths.safeName(newName)) return null
        val target = File(file.parentFile, newName)
        if (target.exists()) return null
        return if (runCatching { file.renameTo(target) }.getOrDefault(false)) target else null
    }

    fun createFolder(parent: File, name: String): File? {
        if (!Paths.safeName(name)) return null
        val target = File(parent, name)
        if (target.exists()) return null
        return if (target.mkdirs()) target else null
    }

    fun createFile(parent: File, name: String): File? {
        if (!Paths.safeName(name)) return null
        val target = File(parent, name)
        if (target.exists()) return null
        return if (runCatching { target.createNewFile() }.getOrDefault(false)) target else null
    }

    fun zip(
        sources: List<File>,
        zipFile: File,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        if (sources.isEmpty()) return false
        val zipParent = zipFile.parentFile ?: return false
        if (!zipParent.exists() && !zipParent.mkdirs()) return false
        val zipCanonical = runCatching { zipFile.canonicalPath }.getOrElse { zipFile.absolutePath }
        var ok = true
        runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)), StandardCharsets.UTF_8).use { zos ->
                for (source in sources) {
                    if (!ok || !shouldContinue()) {
                        ok = false
                        break
                    }
                    val sourceCanonical = runCatching { source.canonicalPath }.getOrElse { source.absolutePath }
                    if (sourceCanonical == zipCanonical) continue
                    addToZip(zos, source, source.name, zipCanonical, onBytes, shouldContinue) { ok = false }
                }
            }
        }.onFailure { ok = false }
        if (!ok) runCatching { zipFile.delete() }
        return ok
    }

    private fun addToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        zipCanonical: String,
        onBytes: (Long) -> Unit,
        shouldContinue: () -> Boolean,
        onFailure: () -> Unit,
    ) {
        if (!shouldContinue() || isSymlink(file)) {
            onFailure()
            return
        }
        val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        if (canonical == zipCanonical) return
        if (file.isDirectory) {
            val dirName = if (entryName.endsWith("/")) entryName else "$entryName/"
            runCatching { zos.putNextEntry(ZipEntry(dirName)); zos.closeEntry() }.onFailure { onFailure() }
            val children = file.listFiles() ?: return
            for (child in children) {
                addToZip(zos, child, "$dirName${child.name}", zipCanonical, onBytes, shouldContinue, onFailure)
            }
        } else {
            runCatching {
                zos.putNextEntry(ZipEntry(entryName.replace(File.separatorChar, '/')))
                BufferedInputStream(FileInputStream(file)).use { input ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        if (!shouldContinue()) {
                            onFailure()
                            return
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        zos.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
                zos.closeEntry()
            }.onFailure { onFailure() }
        }
    }

    fun unzip(
        zipFile: File,
        destDir: File,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        if (!zipFile.isFile) return false
        if (!destDir.exists() && !destDir.mkdirs()) return false
        val destCanonical = runCatching { destDir.canonicalPath }.getOrElse { destDir.absolutePath }
        var zip: ZipFile? = null
        return runCatching {
            zip = openZip(zipFile)
            val entries = Collections.list(zip!!.entries())
            for (entry in entries) {
                if (!shouldContinue()) return false
                val target = File(destDir, entry.name)
                val targetCanonical = runCatching { target.canonicalPath }.getOrElse { target.absolutePath }
                if (!targetCanonical.startsWith(destCanonical + File.separator) && targetCanonical != destCanonical) {
                    continue
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                val targetParent = target.parentFile ?: continue
                if (!targetParent.exists() && !targetParent.mkdirs()) continue
                zip!!.getInputStream(entry).use { input ->
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            if (!shouldContinue()) return false
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            onBytes(read.toLong())
                        }
                        output.flush()
                    }
                }
                if (entry.time > 0) target.setLastModified(entry.time)
            }
            true
        }.also {
            runCatching { zip?.close() }
        }.getOrDefault(false)
    }

    private fun openZip(file: File): ZipFile {
        val utf8 = runCatching { ZipFile(file, StandardCharsets.UTF_8) }.getOrNull()
        if (utf8 != null) {
            val broken = Collections.list(utf8.entries()).any { it.name.contains('\uFFFD') }
            if (!broken) return utf8
            runCatching { utf8.close() }
        }
        return ZipFile(file, Charset.forName("GBK"))
    }

    fun folderSize(
        dir: File,
        onFile: (File) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
        visited: MutableSet<String> = HashSet(),
    ): Long {
        if (!shouldContinue()) return 0
        if (!dir.exists() || isSymlink(dir)) return 0
        if (dir.isFile) return dir.length()
        val canonical = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        if (!visited.add(canonical)) return 0
        var total = 0L
        val children = dir.listFiles() ?: return 0
        for (child in children) {
            if (!shouldContinue()) break
            if (isSymlink(child)) continue
            total += if (child.isDirectory) {
                folderSize(child, onFile, shouldContinue, visited)
            } else {
                onFile(child)
                child.length()
            }
        }
        return total
    }

    fun search(
        root: File,
        query: String,
        showHidden: Boolean,
        limit: Int = 500,
        onProgress: (Int) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): List<File> {
        val needle = query.trim()
        if (needle.isEmpty() || !root.exists()) return emptyList()
        val results = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        val visited = HashSet<String>()
        stack.add(root)
        var scanned = 0
        while (stack.isNotEmpty() && results.size < limit) {
            if (!shouldContinue()) break
            val dir = stack.removeLast()
            if (isSymlink(dir)) continue
            val canonical = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
            if (!visited.add(canonical)) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (!shouldContinue()) break
                scanned++
                if (scanned % 64 == 0) onProgress(scanned)
                if (!showHidden && child.name.startsWith(".")) continue
                if (child.name.contains(needle, ignoreCase = true)) {
                    results.add(child)
                    if (results.size >= limit) break
                }
                if (child.isDirectory && !isSymlink(child)) stack.add(child)
            }
        }
        onProgress(scanned)
        return results
    }

    fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)
}
