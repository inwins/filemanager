package io.github.inwins.filebrowser

import io.github.inwins.filebrowser.util.Hasher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class HasherTest {
    @Test
    fun computesKnownHashes() {
        val result = Hasher.hash(ByteArrayInputStream("abc".toByteArray()))!!
        assertEquals("900150983cd24fb0d6963f7d28e17f72", result.md5)
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", result.sha1)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result.sha256,
        )
    }

    @Test
    fun cancellationReturnsNull() {
        val result = Hasher.hash(
            ByteArrayInputStream(ByteArray(100_000)),
            shouldContinue = { false },
        )
        assertEquals(null, result)
    }
}
