package com.example.filebrowser

import com.example.filebrowser.util.StreamRangeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamRangeParserTest {
    @Test
    fun fullRangeWhenHeaderMissing() {
        val range = StreamRangeParser.parse(null, 1000)!!
        assertEquals(0L, range.start)
        assertEquals(999L, range.end)
        assertEquals(1000L, range.length)
    }

    @Test
    fun parsesOpenEndedAndBoundedRanges() {
        val open = StreamRangeParser.parse("bytes=100-", 1000)!!
        assertEquals(100L, open.start)
        assertEquals(999L, open.end)

        val bounded = StreamRangeParser.parse("bytes=10-19", 1000)!!
        assertEquals(10L, bounded.start)
        assertEquals(19L, bounded.end)
        assertEquals(10L, bounded.length)
    }

    @Test
    fun clampsEndAndSupportsSuffix() {
        val clamped = StreamRangeParser.parse("bytes=990-5000", 1000)!!
        assertEquals(990L, clamped.start)
        assertEquals(999L, clamped.end)

        val suffix = StreamRangeParser.parse("bytes=-100", 1000)!!
        assertEquals(900L, suffix.start)
        assertEquals(999L, suffix.end)
    }

    @Test
    fun rejectsInvalidRanges() {
        assertNull(StreamRangeParser.parse("bytes=1000-", 1000))
        assertNull(StreamRangeParser.parse("bytes=20-10", 1000))
        assertNull(StreamRangeParser.parse("items=0-10", 1000))
        assertNull(StreamRangeParser.parse("bytes=0-1,3-4", 1000))
        assertNull(StreamRangeParser.parse(null, 0))
    }
}
