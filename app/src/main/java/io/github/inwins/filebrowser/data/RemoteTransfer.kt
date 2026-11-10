package io.github.inwins.filebrowser.data

import java.io.File

object RemoteTransfer {
    fun remoteToLocal(
        source: RemoteFileSystem,
        entry: RemoteEntry,
        destDir: File,
        onProgress: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        val counter = LongCounter()
        return remoteToLocalInternal(source, entry, destDir, counter, onProgress, shouldContinue)
    }

    private fun remoteToLocalInternal(
        source: RemoteFileSystem,
        entry: RemoteEntry,
        destDir: File,
        counter: LongCounter,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        if (!shouldContinue()) return false
        if (entry.isDirectory) {
            val target = FileOps.uniqueFile(File(destDir, entry.name))
            if (!target.mkdirs()) return false
            val children = runCatching { source.list(entry.path) }.getOrElse { return false }
            for (child in children) {
                if (!remoteToLocalInternal(source, child, target, counter, onProgress, shouldContinue)) {
                    return false
                }
            }
            return true
        }
        val target = FileOps.uniqueFile(File(destDir, entry.name))
        var last = 0L
        return try {
            source.download(
                entry.path,
                target,
                entry.size,
                onBytes = { done ->
                    counter.value += (done - last).coerceAtLeast(0)
                    last = done
                    onProgress(counter.value)
                },
                shouldContinue = shouldContinue,
            )
            true
        } catch (_: RemoteCancelledException) {
            target.delete()
            false
        } catch (_: Exception) {
            target.delete()
            false
        }
    }

    fun remoteToRemote(
        source: RemoteFileSystem,
        entry: RemoteEntry,
        target: RemoteFileSystem,
        targetDir: String,
        onProgress: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        val counter = LongCounter()
        return remoteToRemoteInternal(
            source, entry, target, targetDir, counter, onProgress, shouldContinue,
        )
    }

    private fun remoteToRemoteInternal(
        source: RemoteFileSystem,
        entry: RemoteEntry,
        target: RemoteFileSystem,
        targetDir: String,
        counter: LongCounter,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        if (!shouldContinue()) return false
        if (entry.isDirectory) {
            if (targetDir == entry.path || targetDir.startsWith(entry.path.trimEnd('/') + "/")) return false
            val name = uniqueRemoteName(target, targetDir, entry.name)
            val newDir = RemoteFileSystems.join(targetDir, name)
            runCatching { target.mkdir(newDir) }.getOrElse { return false }
            val children = runCatching { source.list(entry.path) }.getOrElse { return false }
            for (child in children) {
                if (!remoteToRemoteInternal(
                        source, child, target, newDir, counter, onProgress, shouldContinue,
                    )
                ) {
                    return false
                }
            }
            return true
        }
        val name = uniqueRemoteName(target, targetDir, entry.name)
        val destPath = RemoteFileSystems.join(targetDir, name)
        var last = 0L
        return try {
            source.openStream(entry.path).use { input ->
                target.uploadStream(
                    input,
                    destPath,
                    entry.size,
                    onBytes = { done ->
                        counter.value += (done - last).coerceAtLeast(0)
                        last = done
                        onProgress(counter.value)
                    },
                    shouldContinue = shouldContinue,
                )
            }
            true
        } catch (_: RemoteCancelledException) {
            runCatching { target.delete(destPath, false) }
            false
        } catch (_: Exception) {
            runCatching { target.delete(destPath, false) }
            false
        }
    }

    fun localToRemote(
        source: File,
        target: RemoteFileSystem,
        targetDir: String,
        onProgress: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        val counter = LongCounter()
        return localToRemoteInternal(source, target, targetDir, counter, onProgress, shouldContinue)
    }

    private fun localToRemoteInternal(
        source: File,
        target: RemoteFileSystem,
        targetDir: String,
        counter: LongCounter,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        if (!shouldContinue() || !source.exists()) return false
        if (source.isDirectory) {
            val name = uniqueRemoteName(target, targetDir, source.name)
            val newDir = RemoteFileSystems.join(targetDir, name)
            runCatching { target.mkdir(newDir) }.getOrElse { return false }
            val children = source.listFiles() ?: return true
            for (child in children) {
                if (!localToRemoteInternal(child, target, newDir, counter, onProgress, shouldContinue)) {
                    return false
                }
            }
            return true
        }
        val name = uniqueRemoteName(target, targetDir, source.name)
        val destPath = RemoteFileSystems.join(targetDir, name)
        var last = 0L
        return try {
            target.upload(
                source,
                destPath,
                onBytes = { done ->
                    counter.value += (done - last).coerceAtLeast(0)
                    last = done
                    onProgress(counter.value)
                },
                shouldContinue = shouldContinue,
            )
            true
        } catch (_: RemoteCancelledException) {
            runCatching { target.delete(destPath, false) }
            false
        } catch (_: Exception) {
            runCatching { target.delete(destPath, false) }
            false
        }
    }

    fun uniqueRemoteName(fs: RemoteFileSystem, dir: String, name: String): String {
        if (!fs.exists(RemoteFileSystems.join(dir, name))) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = "$base ($index)$ext"
            if (!fs.exists(RemoteFileSystems.join(dir, candidate))) return candidate
            index++
        }
    }

    private class LongCounter {
        var value = 0L
    }
}
