package com.example.filebrowser.data

enum class RemoteProtocol { SMB, SFTP }

data class RemoteProfile(
    val id: String,
    val name: String,
    val protocol: RemoteProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val share: String = "",
    val rootPath: String = "/",
)

data class RemoteEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
) {
    fun toFileItem(): FileItem = FileItem(
        path = path,
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        mime = if (isDirectory) null else com.example.filebrowser.util.MimeTypes.fromName(name),
        remote = true,
    )
}
