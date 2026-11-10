package io.github.inwins.filebrowser.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
