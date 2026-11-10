package io.github.inwins.filebrowser.lan

data class LanPeer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
)

data class LanEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modified: Long,
)
