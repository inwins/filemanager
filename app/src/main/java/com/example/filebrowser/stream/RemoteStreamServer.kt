package com.example.filebrowser.stream

import android.content.Context
import com.example.filebrowser.data.Prefs
import com.example.filebrowser.data.RemoteFileSystems
import com.example.filebrowser.util.StreamRange
import com.example.filebrowser.util.StreamRangeParser
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object RemoteStreamServer {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private var appContext: Context? = null

    @Volatile
    var port: Int = 0
        private set

    @Synchronized
    fun start(context: Context): Int {
        if (serverSocket != null && !serverSocket!!.isClosed) return port
        appContext = context.applicationContext
        var socket: ServerSocket? = null
        for (candidate in PORT_START..PORT_END) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), candidate), 32)
                socket = s
                break
            } catch (_: IOException) {
            }
        }
        val server = socket ?: throw IOException("No free streaming port")
        serverSocket = server
        port = server.localPort
        acceptThread = Thread({
            while (!server.isClosed) {
                try {
                    val client = server.accept()
                    clients.add(client)
                    Thread {
                        try {
                            handle(client)
                        } finally {
                            clients.remove(client)
                            runCatching { client.close() }
                        }
                    }.apply {
                        isDaemon = true
                        start()
                    }
                } catch (_: IOException) {
                    break
                }
            }
        }, "remote-stream-accept").apply {
            isDaemon = true
            start()
        }
        return port
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        port = 0
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        acceptThread = null
        StreamRegistry.clear()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 20_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        var rangeHeader: String? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) {
                rangeHeader = line.substringAfter(':').trim()
            }
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val path = parts[1]
        if (method != "GET" && method != "HEAD") {
            respondError(socket, 405, "Method not allowed")
            return
        }
        val token = path.substringBefore('?').removePrefix("/stream/")
        if (!path.startsWith("/stream/") || token.isBlank()) {
            respondError(socket, 404, "Not found")
            return
        }
        val entry = StreamRegistry.get(token)
        if (entry == null) {
            respondError(socket, 404, "Stream not found")
            return
        }
        val context = appContext
        if (context == null) {
            respondError(socket, 500, "Server not ready")
            return
        }
        val profile = Prefs(context).loadRemoteProfiles().firstOrNull { it.id == entry.profileId }
        if (profile == null) {
            respondError(socket, 404, "Storage not found")
            return
        }
        val output = BufferedOutputStream(socket.getOutputStream())
        val total = entry.size
        val mime = entry.mime.ifBlank { "application/octet-stream" }

        val range: StreamRange?
        if (total > 0) {
            range = if (rangeHeader == null) StreamRangeParser.parse(null, total)
            else StreamRangeParser.parse(rangeHeader, total)
            if (range == null) {
                val header = buildString {
                    append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                    append("Content-Range: bytes */").append(total).append("\r\n")
                    append("Content-Length: 0\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
                output.flush()
                return
            }
        } else {
            range = null
        }

        val system = try {
            RemoteFileSystems.open(profile)
        } catch (_: Exception) {
            respondError(socket, 502, "Cannot open storage")
            return
        }

        try {
            val start = range?.start ?: 0L
            val length = range?.length ?: -1L
            val status = if (range != null && rangeHeader != null) "206 Partial Content" else "200 OK"
            val headers = buildString {
                append("HTTP/1.1 ").append(status).append("\r\n")
                append("Content-Type: ").append(mime).append("\r\n")
                append("Accept-Ranges: bytes\r\n")
                if (range != null) {
                    append("Content-Length: ").append(length).append("\r\n")
                    append("Content-Range: bytes ").append(range.start).append('-')
                        .append(range.end).append('/').append(total).append("\r\n")
                }
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()
            if (method == "HEAD") return
            system.openStream(entry.remotePath, start).use { input ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining != 0L) {
                    val max = if (remaining < 0) buffer.size
                    else minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, max)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    if (remaining > 0) remaining -= read
                }
                output.flush()
            }
        } catch (_: IOException) {
        } finally {
            runCatching { system.close() }
            runCatching { output.close() }
        }
    }

    private fun respondError(socket: Socket, code: Int, text: String) {
        runCatching {
            val body = text.toByteArray(StandardCharsets.UTF_8)
            val header = buildString {
                append("HTTP/1.1 ").append(code).append(' ')
                append(
                    when (code) {
                        404 -> "Not Found"
                        405 -> "Method Not Allowed"
                        500 -> "Internal Server Error"
                        502 -> "Bad Gateway"
                        else -> "Error"
                    }
                ).append("\r\n")
                append("Content-Type: text/plain; charset=utf-8\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().use { out ->
                out.write(header.toByteArray(StandardCharsets.ISO_8859_1))
                out.write(body)
                out.flush()
            }
        }
    }

    private const val PORT_START = 18765
    private const val PORT_END = 18795
}
