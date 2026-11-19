package io.github.inwins.filebrowser.lan

import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanClientTest {
    private val peer = LanPeer(
        id = "peer",
        name = "Peer",
        host = "192.168.1.20",
        port = 18800,
        token = "abc123",
    )

    @Test
    fun buildsEncodedDownloadUrl() {
        assertEquals(
            "http://192.168.1.20:18800/abc123/api/download?path=a%20b%2Bc.mp3",
            LanClient.fileUrl(peer, "a b+c.mp3"),
        )
        assertEquals(
            "http://192.168.1.20:18800/abc123/api/download?path=%E6%B5%8B%E8%AF%95.mp3",
            LanClient.fileUrl(peer, "测试.mp3"),
        )
    }

    @Test
    fun listHandlesMissingAndMalformedEntries() {
        assertEquals(0, withJsonServer("""{"path":""}""") { peer ->
            LanClient.list(peer, "")
        }.size)

        val entries = withJsonServer(
            """{"entries":[null,{"name":""},{"name":"ok","path":"ok","dir":true,"size":1,"mtime":2}]}"""
        ) { peer -> LanClient.list(peer, "") }
        assertEquals(1, entries.size)
        assertEquals("ok", entries[0].name)
        assertEquals("ok", entries[0].path)
        assertTrue(entries[0].directory)
    }

    private fun <T> withJsonServer(body: String, block: (LanPeer) -> T): T {
        val server = ServerSocket(0)
        val peer = LanPeer(
            id = "fake",
            name = "Fake",
            host = "127.0.0.1",
            port = server.localPort,
            token = "tok",
        )
        val thread = Thread {
            try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val bytes = body.toByteArray()
                    val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: " + bytes.size + "\r\n" +
                        "Connection: close\r\n\r\n"
                    client.getOutputStream().apply {
                        write(response.toByteArray(Charsets.ISO_8859_1))
                        write(bytes)
                        flush()
                    }
                }
            } finally {
                runCatching { server.close() }
            }
        }
        thread.start()
        return try {
            block(peer)
        } finally {
            thread.join(2_000)
            runCatching { server.close() }
        }
    }
}
