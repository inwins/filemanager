package com.example.filebrowser.stream

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class StreamEntry(
    val token: String,
    val profileId: String,
    val remotePath: String,
    val name: String,
    val mime: String,
    val size: Long,
)

object StreamRegistry {
    private val entries = ConcurrentHashMap<String, StreamEntry>()

    fun register(
        profileId: String,
        remotePath: String,
        name: String,
        mime: String,
        size: Long,
    ): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        entries[token] = StreamEntry(token, profileId, remotePath, name, mime, size)
        return token
    }

    fun get(token: String): StreamEntry? = entries[token]

    fun clear() {
        entries.clear()
    }
}
