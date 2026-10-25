package com.example.filebrowser.data

import com.github.junrar.Archive as RarArchive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import net.lingala.zip4j.ZipFile as Zip4jFile
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

data class ArchiveEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

class ArchiveCancelledException : Exception("Cancelled")

enum class ArchiveKind { ZIP, SEVEN_Z, RAR, TAR, TAR_GZ, GZ }

object ArchiveOps {
    fun kindOf(file: File): ArchiveKind? {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".zip") -> ArchiveKind.ZIP
            name.endsWith(".7z") -> ArchiveKind.SEVEN_Z
            name.endsWith(".rar") -> ArchiveKind.RAR
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> ArchiveKind.TAR_GZ
            name.endsWith(".tar") -> ArchiveKind.TAR
            name.endsWith(".gz") -> ArchiveKind.GZ
            else -> null
        }
    }

    fun createZip(
        sources: List<File>,
        zipFile: File,
        password: String? = null,
        splitBytes: Long = 0L,
        onProgress: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        if (sources.isEmpty() || !shouldContinue()) return false
        zipFile.parentFile?.mkdirs()
        return runCatching {
            val params = net.lingala.zip4j.model.ZipParameters()
            params.compressionMethod = net.lingala.zip4j.model.enums.CompressionMethod.DEFLATE
            params.isIncludeRootFolder = true
            if (!password.isNullOrEmpty()) {
                params.isEncryptFiles = true
                params.encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                params.aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
            }
            val zip = Zip4jFile(
                zipFile,
                password?.takeIf { it.isNotEmpty() }?.toCharArray(),
            )
            if (splitBytes > 0) {
                zip.createSplitZipFile(sources, params, true, splitBytes)
            } else {
                zip.addFiles(sources, params)
            }
            onProgress(sources.sumOf { if (it.isDirectory) folderSize(it) else it.length() })
            true
        }.getOrDefault(false)
    }

    private fun folderSize(file: File): Long {
        if (!file.isDirectory) return file.length()
        return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }

    fun list(file: File, password: String? = null): List<ArchiveEntry> {
        val kind = kindOf(file) ?: return emptyList()
        return when (kind) {
            ArchiveKind.ZIP -> listZip(file, password)
            ArchiveKind.SEVEN_Z -> list7z(file, password)
            ArchiveKind.RAR -> listRar(file, password)
            ArchiveKind.TAR -> listTar(file, gzip = false)
            ArchiveKind.TAR_GZ -> listTar(file, gzip = true)
            ArchiveKind.GZ -> listOf(
                ArchiveEntry(file.name.removeSuffix(".gz"), false, file.length())
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun extract(
        file: File,
        destDir: File,
        password: String? = null,
        onProgress: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Boolean {
        val kind = kindOf(file) ?: return false
        if (!destDir.exists() && !destDir.mkdirs()) return false
        return try {
            when (kind) {
                ArchiveKind.ZIP -> extractZip(file, destDir, password, onProgress, shouldContinue)
                ArchiveKind.SEVEN_Z -> extract7z(file, destDir, password, onProgress, shouldContinue)
                ArchiveKind.RAR -> extractRar(file, destDir, password, onProgress, shouldContinue)
                ArchiveKind.TAR -> extractTar(file, destDir, gzip = false, onProgress, shouldContinue)
                ArchiveKind.TAR_GZ -> extractTar(file, destDir, gzip = true, onProgress, shouldContinue)
                ArchiveKind.GZ -> extractGz(file, destDir, onProgress, shouldContinue)
            }
        } catch (_: ArchiveCancelledException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun listZip(file: File, password: String?): List<ArchiveEntry> {
        val zip = Zip4jFile(file, password?.takeIf { it.isNotEmpty() }?.toCharArray())
        return zip.fileHeaders.map {
            ArchiveEntry(it.fileName.replace('\\', '/'), it.isDirectory, it.uncompressedSize)
        }
    }

    private fun extractZip(
        file: File,
        destDir: File,
        password: String?,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        val zip = Zip4jFile(file, password?.takeIf { it.isNotEmpty() }?.toCharArray())
        var copied = 0L
        for (header in zip.fileHeaders) {
            if (!shouldContinue()) throw ArchiveCancelledException()
            val target = safeTarget(destDir, header.fileName) ?: continue
            if (header.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                zip.getInputStream(header).use { input ->
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        copied += copyStream(input, output, copied, onProgress, shouldContinue)
                    }
                }
            }
        }
        return true
    }

    private fun list7z(file: File, password: String?): List<ArchiveEntry> {
        val archive = if (password.isNullOrEmpty()) SevenZFile(file)
        else SevenZFile(file, password.toCharArray())
        archive.use {
            return it.entries.map { entry ->
                ArchiveEntry(entry.name, entry.isDirectory, entry.size)
            }
        }
    }

    private fun extract7z(
        file: File,
        destDir: File,
        password: String?,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        val archive = if (password.isNullOrEmpty()) SevenZFile(file)
        else SevenZFile(file, password.toCharArray())
        var copied = 0L
        archive.use {
            for (entry in it.entries) {
                if (!shouldContinue()) throw ArchiveCancelledException()
                val target = safeTarget(destDir, entry.name) ?: continue
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    it.getInputStream(entry).use { input ->
                        BufferedOutputStream(FileOutputStream(target)).use { output ->
                            copied += copyStream(input, output, copied, onProgress, shouldContinue)
                        }
                    }
                }
            }
        }
        return true
    }

    private fun listRar(file: File, password: String?): List<ArchiveEntry> {
        val archive = if (password.isNullOrEmpty()) RarArchive(file) else RarArchive(file, password)
        return archive.use {
            it.fileHeaders.map { header ->
                ArchiveEntry(
                    header.fileNameString.replace('\\', '/'),
                    header.isDirectory,
                    header.fullUnpackSize,
                )
            }
        }
    }

    private fun extractRar(
        file: File,
        destDir: File,
        password: String?,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        val archive = if (password.isNullOrEmpty()) RarArchive(file) else RarArchive(file, password)
        var copied = 0L
        archive.use {
            for (header in it.fileHeaders) {
                if (!shouldContinue()) throw ArchiveCancelledException()
                val target = safeTarget(destDir, header.fileNameString) ?: continue
                if (header.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output ->
                        it.extractFile(header, output)
                    }
                    copied += header.fullUnpackSize
                    onProgress(copied)
                }
            }
        }
        return true
    }

    private fun listTar(file: File, gzip: Boolean): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        openTar(file, gzip).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                entries.add(ArchiveEntry(entry.name, entry.isDirectory, entry.size))
            }
        }
        return entries
    }

    private fun extractTar(
        file: File,
        destDir: File,
        gzip: Boolean,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        var copied = 0L
        openTar(file, gzip).use { input ->
            while (true) {
                if (!shouldContinue()) throw ArchiveCancelledException()
                val entry = input.nextTarEntry ?: break
                val target = safeTarget(destDir, entry.name) ?: continue
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        copied += copyStream(input, output, copied, onProgress, shouldContinue)
                    }
                }
            }
        }
        return true
    }

    private fun extractGz(
        file: File,
        destDir: File,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Boolean {
        val name = file.name.removeSuffix(".gz").ifBlank { "extracted" }
        val target = File(destDir, name)
        GZIPInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            BufferedOutputStream(FileOutputStream(target)).use { output ->
                copyStream(input, output, 0L, onProgress, shouldContinue)
            }
        }
        return true
    }

    private fun openTar(file: File, gzip: Boolean): TarArchiveInputStream {
        val raw = BufferedInputStream(FileInputStream(file))
        val input = if (gzip) GzipCompressorInputStream(raw) else raw
        return TarArchiveInputStream(input)
    }

    private fun copyStream(
        input: InputStream,
        output: OutputStream,
        base: Long,
        onProgress: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            if (!shouldContinue()) throw ArchiveCancelledException()
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            copied += read
            onProgress(base + copied)
        }
        return copied
    }

    private fun safeTarget(destDir: File, name: String): File? {
        val clean = name.replace('\\', '/').trimStart('/')
        if (clean.isEmpty()) return null
        val target = File(destDir, clean)
        val destPath = destDir.canonicalPath
        val targetPath = target.canonicalPath
        return if (targetPath == destPath || targetPath.startsWith(destPath + File.separator)) target else null
    }
}
