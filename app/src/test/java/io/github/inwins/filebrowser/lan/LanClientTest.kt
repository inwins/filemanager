package io.github.inwins.filebrowser.lan

import org.junit.Assert.assertEquals
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
}
