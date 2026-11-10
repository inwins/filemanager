package io.github.inwins.filebrowser

import io.github.inwins.filebrowser.util.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesSyncedLyricsAndMetadata() {
        val lyrics = LrcParser.parse(
            """
            [ti:Test Song]
            [ar:Artist]
            [al:Album]
            [offset:+500]
            [00:01.00]First line
            [00:02.50][00:05.00]Repeated line
            [00:10.123]Last line
            [01:02]Minute line
            """.trimIndent()
        )!!
        assertTrue(lyrics.synced)
        assertEquals("Test Song", lyrics.title)
        assertEquals("Artist", lyrics.artist)
        assertEquals("Album", lyrics.album)
        assertEquals(1500L, lyrics.lines[0].timeMs)
        assertEquals("First line", lyrics.lines[0].text)
        assertEquals(3000L, lyrics.lines[1].timeMs)
        assertEquals(5500L, lyrics.lines[2].timeMs)
        assertEquals(10623L, lyrics.lines[3].timeMs)
        assertEquals(62500L, lyrics.lines[4].timeMs)
    }

    @Test
    fun indexAtFindsCurrentLine() {
        val lyrics = LrcParser.parse("[00:01.00]One\n[00:03.00]Two\n[00:06.00]Three")!!
        assertEquals(0, lyrics.indexAt(0))
        assertEquals(0, lyrics.indexAt(1000))
        assertEquals(0, lyrics.indexAt(2500))
        assertEquals(1, lyrics.indexAt(3000))
        assertEquals(2, lyrics.indexAt(60_000))
    }

    @Test
    fun unsyncedLyrics() {
        val lyrics = LrcParser.parse("First line\nSecond line\n\nThird line")!!
        assertFalse(lyrics.synced)
        assertEquals(3, lyrics.lines.size)
        assertEquals("Third line", lyrics.lines[2].text)
    }

    @Test
    fun ignoresMetadataAndEmptyInput() {
        assertNull(LrcParser.parse(""))
        assertNull(LrcParser.parse("[ti:Only title]"))
        val lyrics = LrcParser.parse("[ar:Artist]\nSome plain lyric")!!
        assertFalse(lyrics.synced)
        assertEquals(1, lyrics.lines.size)
    }

    @Test
    fun parsesCompactAndMultiTimestampLines() {
        val lyrics = LrcParser.parse("[00:01]A\n[00:02.5][00:03.50]B")!!
        assertEquals(1000L, lyrics.lines[0].timeMs)
        assertEquals(2500L, lyrics.lines[1].timeMs)
        assertEquals(3500L, lyrics.lines[2].timeMs)
    }
}
