package io.github.inwins.filebrowser.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanModelsTest {
    @Test
    fun peerValueSemantics() {
        val peer = LanPeer("id", "Phone", "192.168.1.2", 18800, "token")
        val copy = peer.copy()
        assertEquals(peer, copy)
        assertEquals(peer.hashCode(), copy.hashCode())
        assertTrue(peer.toString().contains("Phone"))
    }

    @Test
    fun entryValueSemantics() {
        val entry = LanEntry("song.mp3", "music/song.mp3", false, 123L, 456L)
        val copy = entry.copy()
        assertEquals(entry, copy)
        assertEquals(entry.hashCode(), copy.hashCode())
        assertTrue(entry.toString().contains("song.mp3"))
    }
}
