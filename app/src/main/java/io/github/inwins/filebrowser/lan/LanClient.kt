package io.github.inwins.filebrowser.lan

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LanClient {
    fun list(peer: LanPeer, path: String): List<LanEntry> {
        val connection = open(peer, "/api/list?path=" + encode(path), "GET")
        val code = connection.responseCode
        val text = readText(connection, code)
        connection.disconnect()
        if (code !in 200..299) throw IOException(text.ifBlank { "HTTP $code" })
        val json = JSONObject(text)
        val array = json.optJSONArray("entries") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name")
                if (name.isBlank()) continue
                add(
                    LanEntry(
                        name = name,
                        path = item.optString("path"),
                        directory = item.optBoolean("dir"),
                        size = item.optLong("size", 0L),
                        modified = item.optLong("mtime", 0L),
                    )
                )
            }
        }
    }

    fun fileUrl(peer: LanPeer, remotePath: String): String =
        "http://${peer.host}:${peer.port}/${peer.token}/api/download?path=" + encode(remotePath)

    fun mkdir(peer: LanPeer, path: String, name: String) {
        val connection = open(
            peer,
            "/api/mkdir?path=" + encode(path) + "&name=" + encode(name),
            "POST",
        )
        val code = connection.responseCode
        val text = readText(connection, code)
        connection.disconnect()
        if (code !in 200..299) throw IOException(text.ifBlank { "HTTP $code" })
    }

    fun upload(
        peer: LanPeer,
        path: String,
        name: String,
        size: Long,
        input: InputStream,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val connection = open(
            peer,
            "/api/upload?path=" + encode(path) + "&name=" + encode(name),
            "PUT",
        )
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.setFixedLengthStreamingMode(size)
        connection.connectTimeout = 8_000
        connection.readTimeout = 120_000
        try {
            connection.outputStream.use { output ->
                val buffer = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    sent += read
                    onProgress(sent, size)
                }
            }
            val code = connection.responseCode
            val text = readText(connection, code)
            if (code !in 200..299) throw IOException(text.ifBlank { "HTTP $code" })
        } finally {
            connection.disconnect()
        }
    }

    fun download(
        peer: LanPeer,
        remotePath: String,
        destination: File,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val connection = open(peer, "/api/download?path=" + encode(remotePath), "GET")
        connection.connectTimeout = 8_000
        connection.readTimeout = 120_000
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException(readText(connection, code).ifBlank { "HTTP $code" })
            }
            val total = connection.contentLengthLong
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        onProgress(received, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(peer: LanPeer, path: String, method: String): HttpURLConnection {
        val url = URL("http://${peer.host}:${peer.port}/${peer.token}$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        return connection
    }

    private fun readText(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
