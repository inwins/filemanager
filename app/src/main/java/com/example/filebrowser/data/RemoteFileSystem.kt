package com.example.filebrowser.data

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session as SshSession
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class RemoteException(message: String, cause: Throwable? = null) : IOException(message, cause)

class RemoteCancelledException : IOException("Cancelled")

interface RemoteFileSystem : Closeable {
    fun list(path: String): List<RemoteEntry>
    fun download(
        path: String,
        dest: File,
        totalSize: Long = -1L,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    )
    fun upload(
        src: File,
        destPath: String,
        onBytes: (Long) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    )
    fun mkdir(path: String)
    fun delete(path: String, isDirectory: Boolean)
    fun rename(oldPath: String, newPath: String)
    fun exists(path: String): Boolean
}

object RemoteFileSystems {
    fun open(profile: RemoteProfile): RemoteFileSystem {
        return when (profile.protocol) {
            RemoteProtocol.SMB -> SmbRemoteFileSystem(profile)
            RemoteProtocol.SFTP -> SftpRemoteFileSystem(profile)
        }
    }

    fun join(parent: String, name: String): String {
        return if (parent == "/" || parent.isBlank()) "/$name" else "${parent.trimEnd('/')}/$name"
    }

    fun parent(path: String): String {
        val trimmed = path.trimEnd('/')
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        val index = trimmed.lastIndexOf('/')
        return if (index <= 0) "/" else trimmed.substring(0, index)
    }

    fun name(path: String): String = path.trimEnd('/').substringAfterLast('/')
}

class SmbRemoteFileSystem(private val profile: RemoteProfile) : RemoteFileSystem {
    private val client: SMBClient
    private val connection: Connection
    private val session: Session
    private val share: DiskShare

    init {
        try {
            val config = SmbConfig.builder()
                .withTimeout(20, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .build()
            client = SMBClient(config)
            connection = client.connect(profile.host, profile.port)
            val auth = if (profile.username.isBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    profile.username,
                    profile.password.toCharArray(),
                    profile.domain.ifBlank { null },
                )
            }
            session = connection.authenticate(auth)
            share = session.connectShare(profile.share) as? DiskShare
                ?: throw RemoteException("Not a disk share")
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SMB connection failed", error)
        }
    }

    override fun list(path: String): List<RemoteEntry> {
        val smbPath = toSmb(path)
        val entries = try {
            if (smbPath.isEmpty()) {
                runCatching { share.list("") }.getOrElse { share.list("\\") }
            } else {
                share.list(smbPath)
            }
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SMB list failed", error)
        }
        return entries.mapNotNull { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@mapNotNull null
            val isDirectory = (info.fileAttributes and FILE_ATTRIBUTE_DIRECTORY) != 0L
            RemoteEntry(
                path = RemoteFileSystems.join(path, name),
                name = name,
                isDirectory = isDirectory,
                size = if (isDirectory) 0L else info.endOfFile,
                lastModified = runCatching { info.lastWriteTime.toEpochMillis() }.getOrDefault(0L),
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override fun download(
        path: String,
        dest: File,
        totalSize: Long,
        onBytes: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ) {
        try {
            val file = share.openFile(
                toSmb(path),
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            file.use {
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var offset = 0L
                    while (totalSize <= 0L || offset < totalSize) {
                        if (!shouldContinue()) throw RemoteCancelledException()
                        val read = it.read(buffer, offset)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        offset += read
                        onBytes(offset)
                    }
                }
            }
        } catch (error: RemoteCancelledException) {
            throw error
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SMB download failed", error)
        }
    }

    override fun upload(
        src: File,
        destPath: String,
        onBytes: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ) {
        try {
            val file = share.openFile(
                toSmb(destPath),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            )
            file.use {
                FileInputStream(src).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var offset = 0L
                    while (true) {
                        if (!shouldContinue()) throw RemoteCancelledException()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        var written = 0
                        while (written < read) {
                            val count = it.write(buffer, offset + written, written, read - written)
                            if (count <= 0) throw RemoteException("SMB write failed")
                            written += count.toInt()
                        }
                        offset += read
                        onBytes(offset)
                    }
                }
            }
        } catch (error: RemoteCancelledException) {
            throw error
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SMB upload failed", error)
        }
    }

    override fun mkdir(path: String) {
        try {
            share.mkdir(toSmb(path))
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SMB mkdir failed", error)
        }
    }

    override fun delete(path: String, isDirectory: Boolean) {
        try {
            val smbPath = toSmb(path)
            if (isDirectory) share.rmdir(smbPath, true) else share.rm(smbPath)
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SMB delete failed", error)
        }
    }

    override fun rename(oldPath: String, newPath: String) {
        try {
            val old = toSmb(oldPath)
            val new = toSmb(newPath)
            if (share.folderExists(old)) {
                val dir = share.openDirectory(
                    old,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
                dir.use { it.rename(new) }
            } else {
                val file = share.openFile(
                    old,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
                file.use { it.rename(new) }
            }
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SMB rename failed", error)
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            val smbPath = toSmb(path)
            smbPath.isEmpty() || share.folderExists(smbPath) || share.fileExists(smbPath)
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }

    private fun toSmb(path: String): String = path.trim('/').replace('/', '\\')

    companion object {
        private const val FILE_ATTRIBUTE_DIRECTORY = 0x10L
    }
}

class SftpRemoteFileSystem(private val profile: RemoteProfile) : RemoteFileSystem {
    private val session: SshSession
    private val channel: ChannelSftp

    init {
        try {
            val jsch = JSch()
            session = jsch.getSession(profile.username, profile.host, profile.port)
            session.setPassword(profile.password)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
            session.connect(20_000)
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(20_000)
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SFTP connection failed", error)
        }
    }

    override fun list(path: String): List<RemoteEntry> {
        try {
            val entries = channel.ls(path)
            return entries.mapNotNull { raw ->
                val entry = raw as? ChannelSftp.LsEntry ?: return@mapNotNull null
                val name = entry.filename
                if (name == "." || name == "..") return@mapNotNull null
                val attrs = entry.attrs
                RemoteEntry(
                    path = RemoteFileSystems.join(path, name),
                    name = name,
                    isDirectory = attrs.isDir,
                    size = if (attrs.isDir) 0L else attrs.size,
                    lastModified = attrs.mTime.toLong() * 1000L,
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SFTP list failed", error)
        }
    }

    override fun download(
        path: String,
        dest: File,
        totalSize: Long,
        onBytes: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ) {
        try {
            FileOutputStream(dest).use { raw ->
                val output = ProgressOutputStream(raw, onBytes, shouldContinue)
                channel.get(path, output)
                output.flush()
            }
        } catch (error: RemoteCancelledException) {
            throw error
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SFTP download failed", error)
        }
    }

    override fun upload(
        src: File,
        destPath: String,
        onBytes: (Long) -> Unit,
        shouldContinue: () -> Boolean,
    ) {
        try {
            FileInputStream(src).use { raw ->
                val input = ProgressInputStream(raw, onBytes, shouldContinue)
                channel.put(input, destPath)
            }
        } catch (error: RemoteCancelledException) {
            throw error
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SFTP upload failed", error)
        }
    }

    override fun mkdir(path: String) {
        try {
            channel.mkdir(path)
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SFTP mkdir failed", error)
        }
    }

    override fun delete(path: String, isDirectory: Boolean) {
        try {
            if (isDirectory) deleteRecursive(path) else channel.rm(path)
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SFTP delete failed", error)
        }
    }

    private fun deleteRecursive(path: String) {
        val entries = channel.ls(path)
        for (raw in entries) {
            val entry = raw as? ChannelSftp.LsEntry ?: continue
            val name = entry.filename
            if (name == "." || name == "..") continue
            val child = RemoteFileSystems.join(path, name)
            if (entry.attrs.isDir) deleteRecursive(child) else channel.rm(child)
        }
        channel.rmdir(path)
    }

    override fun rename(oldPath: String, newPath: String) {
        try {
            channel.rename(oldPath, newPath)
        } catch (error: Exception) {
            throw RemoteException(error.message ?: "SFTP rename failed", error)
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            channel.stat(path)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }
}


private class ProgressOutputStream(
    out: java.io.OutputStream,
    private val onBytes: (Long) -> Unit,
    private val shouldContinue: () -> Boolean,
) : java.io.FilterOutputStream(out) {
    private var copied = 0L

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (!shouldContinue()) throw RemoteCancelledException()
        out.write(b, off, len)
        copied += len
        onBytes(copied)
    }
}

private class ProgressInputStream(
    input: java.io.InputStream,
    private val onBytes: (Long) -> Unit,
    private val shouldContinue: () -> Boolean,
) : java.io.FilterInputStream(input) {
    private var copied = 0L

    override fun read(): Int {
        if (!shouldContinue()) throw RemoteCancelledException()
        val value = super.read()
        if (value >= 0) {
            copied++
            onBytes(copied)
        }
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!shouldContinue()) throw RemoteCancelledException()
        val read = super.read(b, off, len)
        if (read > 0) {
            copied += read
            onBytes(copied)
        }
        return read
    }
}
