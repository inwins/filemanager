package io.github.inwins.filebrowser.lan

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanDiscoveryTest {
    @Test
    fun parsesValidBroadcast() {
        val peer = LanDiscovery.parse(
            """{"app":"io.github.inwins.filebrowser","id":"abc","name":"Phone","port":18800,"token":"tok123"}""",
            "192.168.1.20",
        )
        assertEquals(
            LanPeer(id = "abc", name = "Phone", host = "192.168.1.20", port = 18800, token = "tok123"),
            peer,
        )
    }

    @Test
    fun rejectsInvalidBroadcasts() {
        assertNull(LanDiscovery.parse("not json", "192.168.1.20"))
        assertNull(LanDiscovery.parse("""{"app":"other","id":"abc","port":1,"token":"x"}""", "192.168.1.20"))
        assertNull(
            LanDiscovery.parse(
                """{"app":"io.github.inwins.filebrowser","id":"abc","port":0,"token":"x"}""",
                "192.168.1.20",
            )
        )
        assertNull(
            LanDiscovery.parse(
                """{"app":"io.github.inwins.filebrowser","id":"abc","port":18800,"token":""}""",
                "192.168.1.20",
            )
        )
    }

    @Test
    fun scanReceivesPeerDatagrams() {
        val payload = """{"app":"io.github.inwins.filebrowser","id":"integration","name":"Peer","port":18800,"token":"token"}"""
            .toByteArray()
        val sender = DatagramSocket()
        val thread = Thread {
            try {
                val target = InetAddress.getByName("127.0.0.1")
                repeat(10) {
                    runCatching {
                        sender.send(DatagramPacket(payload, payload.size, target, LanDiscovery.DISCOVERY_PORT))
                    }
                    Thread.sleep(80)
                }
            } finally {
                runCatching { sender.close() }
            }
        }
        thread.start()
        try {
            val peers = LanDiscovery.scan(2_000)
            assertTrue(peers.any { it.id == "integration" && it.host == "127.0.0.1" })
        } finally {
            thread.join(2_000)
        }
    }
}
