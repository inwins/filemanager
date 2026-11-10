package io.github.inwins.filebrowser.data

enum class RemoteProtocol { SMB, SFTP }
enum class RemoteAuthMethod { PASSWORD, KEY }

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
    val authMethod: RemoteAuthMethod = RemoteAuthMethod.PASSWORD,
    val privateKeyPath: String = "",
    val keyPassphrase: String = "",
    val hostKeyFingerprint: String = "",
    val guest: Boolean = false,
)

data class RemoteClipboard(
    val op: ClipboardOp,
    val profileId: String,
    val items: List<RemoteEntry>,
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
        mime = if (isDirectory) null else io.github.inwins.filebrowser.util.MimeTypes.fromName(name),
        remote = true,
    )
}
