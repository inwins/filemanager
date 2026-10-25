package com.example.filebrowser

import com.example.filebrowser.util.TxtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtParserTest {
    @Test
    fun detectsChineseChapters() {
        val text = buildString {
            append("这是一段前言。\n\n")
            append("第一章 初见\n")
            append("第一段内容。\n")
            append("第二段内容。\n\n")
            append("第二章 再会\n")
            append("第三段内容。\n\n")
            append("番外 后日谈\n")
            append("番外内容。\n")
        }
        val book = TxtParser.parse(text)
        assertEquals(listOf("前言", "第一章 初见", "第二章 再会", "番外 后日谈"), book.chapters.map { it.title })
        assertEquals(4, book.chapters.size)
        assertEquals(1, book.chapters[1].firstBlock)
        assertTrue(book.blocks.first { it.heading && it.chapterIndex == 1 }.let { book.blockText(it) }.contains("第一章"))
        assertTrue(book.blocks.any { book.blockText(it) == "第三段内容。" })
    }

    @Test
    fun detectsEnglishAndMarkdownHeadings() {
        val text = "Chapter 1: Start\nHello.\n\nCHAPTER II\nWorld.\n\n# Markdown Title\nText."
        val book = TxtParser.parse(text)
        assertEquals(3, book.chapters.size)
        assertEquals("Chapter 1: Start", book.chapters[0].title)
        assertEquals("CHAPTER II", book.chapters[1].title)
        assertEquals("# Markdown Title", book.chapters[2].title)
    }

    @Test
    fun noHeadingsUsesSingleChapter() {
        val text = "line one\n\nline two\nline three"
        val book = TxtParser.parse(text, defaultTitle = "Body")
        assertEquals(1, book.chapters.size)
        assertEquals("Body", book.chapters[0].title)
        assertEquals(3, book.blocks.size)
        assertEquals("line one", book.blockText(book.blocks[0]))
    }

    @Test
    fun offsetMapsToBlockAndProgress() {
        val text = "第一章 开始\n甲。\n乙。\n第二章 继续\n丙。"
        val book = TxtParser.parse(text)
        val second = book.blocks.first { book.blockText(it) == "乙。" }
        assertEquals(second.index, book.blockIndexForOffset(second.start))
        assertEquals(second.index, book.blockIndexForOffset(second.start + 1))
        assertTrue(book.percentForBlock(book.blocks.lastIndex) == 100)
    }

    @Test
    fun ttsChunksStayInsideChapter() {
        val text = "第一章 开始\n甲。\n乙。\n第二章 继续\n丙。"
        val book = TxtParser.parse(text)
        val chapter = book.chapters[1]
        val chunks = TxtParser.ttsChunks(book, 1, chapter.firstBlock)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.none { it.blockIndex < chapter.firstBlock })
        assertTrue(chunks.all { book.blocks[it.blockIndex].chapterIndex == 1 })
    }

    @Test
    fun splitForTtsPrefersPunctuation() {
        val text = "第一句。" + "a".repeat(100) + "。第二句。"
        val chunks = TxtParser.splitForTts(text, maxChars = 60)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.first().endsWith("。"))
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
    }

    @Test
    fun normalizesCrLf() {
        val text = "第一章 开始\r\n内容一\r\n\r\n第二章 继续\r\n内容二"
        val book = TxtParser.parse(text)
        assertEquals(2, book.chapters.size)
        assertFalse(book.text.contains('\r'))
        assertTrue(book.blocks.any { book.blockText(it) == "内容二" })
    }

    @Test
    fun headingDetection() {
        assertTrue(TxtParser.isHeading("第一章 少年"))
        assertTrue(TxtParser.isHeading("第１２章")) // full-width digits are not matched, see below
        assertTrue(TxtParser.isHeading("Chapter 12"))
        assertTrue(TxtParser.isHeading("## Part"))
        assertTrue(TxtParser.isHeading("番外篇"))
        assertFalse(TxtParser.isHeading("这是普通的一行文字，不是章节标题"))
        assertFalse(TxtParser.isHeading("第章"))
    }
}
