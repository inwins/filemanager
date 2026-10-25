package com.example.filebrowser.util

import com.example.filebrowser.data.TtsChunk
import com.example.filebrowser.data.TxtBlock
import com.example.filebrowser.data.TxtBook
import com.example.filebrowser.data.TxtChapter

object TxtParser {
    private const val MAX_HEADING_LENGTH = 80
    private const val MAX_BLOCK_LENGTH = 4000
    private const val TTS_CHUNK = 1800

    private val chineseHeading = Regex(
        "^第[0-9０-９零一二三四五六七八九十百千万两〇]{1,12}[章节卷回部篇集]([\\s　:：.、\\-].*)?$"
    )
    private val specialHeading = Regex(
        "^(序章|楔子|前言|后记|尾声|番外篇?|外传|目录)([\\s　:：.、\\-].*)?$"
    )
    private val englishHeading = Regex(
        "^(chapter|ch\\.)\\s*[0-9ivxlcdm]+\\b.*$",
        RegexOption.IGNORE_CASE,
    )
    private val markdownHeading = Regex("^#{1,4}\\s+.+$")

    private data class LineInfo(
        val lineIndex: Int,
        val start: Int,
        val end: Int,
        val raw: String,
    )

    private data class ChapterSpec(
        val title: String,
        val startLine: Int,
        val endLine: Int,
        val heading: Boolean,
    )

    fun isHeading(line: String): Boolean {
        val text = line.trim()
        if (text.isEmpty() || text.length > MAX_HEADING_LENGTH) return false
        return chineseHeading.matches(text) ||
            specialHeading.matches(text) ||
            englishHeading.matches(text) ||
            markdownHeading.matches(text)
    }

    fun parse(
        source: String,
        defaultTitle: String = "正文",
        prefaceTitle: String = "前言",
    ): TxtBook {
        val text = normalizeNewlines(source)
        val lines = splitLines(text)
        val headingLines = lines.filter { it.raw.isNotBlank() && isHeading(it.raw) }

        val specs = mutableListOf<ChapterSpec>()
        if (headingLines.isEmpty()) {
            specs.add(ChapterSpec(defaultTitle, 0, lines.size, false))
        } else {
            val firstHeading = headingLines.first()
            if (firstHeading.lineIndex > 0 &&
                lines.subList(0, firstHeading.lineIndex).any { it.raw.isNotBlank() }
            ) {
                specs.add(ChapterSpec(prefaceTitle, 0, firstHeading.lineIndex, false))
            }
            headingLines.forEachIndexed { index, heading ->
                val endLine = headingLines.getOrNull(index + 1)?.lineIndex ?: lines.size
                specs.add(ChapterSpec(heading.raw.trim(), heading.lineIndex, endLine, true))
            }
        }

        val blocks = mutableListOf<TxtBlock>()
        val chapters = mutableListOf<TxtChapter>()

        specs.forEachIndexed { chapterIndex, spec ->
            val chapterStart = lines.getOrNull(spec.startLine)?.start ?: text.length
            val chapterEnd = if (spec.endLine >= lines.size) {
                text.length
            } else {
                lines[spec.endLine].start
            }
            val firstBlock = blocks.size
            var lineIndex = spec.startLine
            if (spec.heading) {
                val headingLine = lines[spec.startLine]
                addBlock(text, headingLine.start, headingLine.end, chapterIndex, true, blocks)
                lineIndex++
            }
            while (lineIndex < spec.endLine) {
                val line = lines[lineIndex]
                addParagraphBlocks(text, line.start, line.end, chapterIndex, blocks)
                lineIndex++
            }
            chapters.add(
                TxtChapter(
                    index = chapterIndex,
                    title = spec.title,
                    start = chapterStart,
                    end = chapterEnd,
                    firstBlock = firstBlock,
                )
            )
        }

        return TxtBook(text = text, chapters = chapters, blocks = blocks)
    }

    fun splitForTts(text: String, maxChars: Int = TTS_CHUNK): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxChars) return listOf(trimmed)
        val result = mutableListOf<String>()
        var start = 0
        while (start < trimmed.length) {
            var end = minOf(start + maxChars, trimmed.length)
            if (end < trimmed.length) {
                val windowStart = start + 1
                var cut = -1
                for (i in end - 1 downTo windowStart) {
                    if (trimmed[i] in "。！？；.!?;\n") {
                        cut = i + 1
                        break
                    }
                }
                if (cut > start) end = cut
            }
            val part = trimmed.substring(start, end).trim()
            if (part.isNotEmpty()) result.add(part)
            start = end
            while (start < trimmed.length && trimmed[start].isWhitespace()) start++
        }
        return result
    }

    fun ttsChunks(
        book: TxtBook,
        chapterIndex: Int,
        startBlock: Int,
        maxChars: Int = TTS_CHUNK,
    ): List<TtsChunk> {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
        if (book.blocks.isEmpty()) return emptyList()
        val from = startBlock.coerceIn(chapter.firstBlock, book.blocks.lastIndex)
        val result = mutableListOf<TtsChunk>()
        for (index in from..book.blocks.lastIndex) {
            val block = book.blocks[index]
            if (block.chapterIndex != chapterIndex) break
            val text = book.blockText(block)
            splitForTts(text, maxChars).forEach { part ->
                result.add(TtsChunk(index, part))
            }
        }
        return result
    }

    private fun normalizeNewlines(source: String): String {
        var text = source
        if (text.startsWith('\uFEFF')) text = text.substring(1)
        if ('\r' in text) text = text.replace("\r\n", "\n").replace('\r', '\n')
        return text
    }

    private fun splitLines(text: String): List<LineInfo> {
        val lines = mutableListOf<LineInfo>()
        var start = 0
        var index = 0
        var lineIndex = 0
        while (index <= text.length) {
            if (index == text.length || text[index] == '\n') {
                lines.add(LineInfo(lineIndex, start, index, text.substring(start, index)))
                lineIndex++
                start = index + 1
            }
            index++
        }
        return lines
    }

    private fun addParagraphBlocks(
        text: String,
        rawStart: Int,
        rawEnd: Int,
        chapterIndex: Int,
        output: MutableList<TxtBlock>,
    ) {
        if (rawEnd <= rawStart) return
        var start = rawStart
        var end = rawEnd
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start >= end) return
        var cursor = start
        while (cursor < end) {
            var blockEnd = minOf(cursor + MAX_BLOCK_LENGTH, end)
            if (blockEnd < end) {
                val windowStart = maxOf(cursor + MAX_BLOCK_LENGTH / 2, blockEnd - 800)
                var cut = -1
                for (i in blockEnd - 1 downTo windowStart) {
                    if (text[i] in "。！？；.!?;\n") {
                        cut = i + 1
                        break
                    }
                }
                if (cut > cursor) blockEnd = cut
            }
            output.add(
                TxtBlock(
                    index = output.size,
                    chapterIndex = chapterIndex,
                    start = cursor,
                    end = blockEnd,
                    heading = false,
                )
            )
            cursor = blockEnd
            while (cursor < end && text[cursor].isWhitespace()) cursor++
        }
    }

    private fun addBlock(
        text: String,
        rawStart: Int,
        rawEnd: Int,
        chapterIndex: Int,
        heading: Boolean,
        output: MutableList<TxtBlock>,
    ) {
        var start = rawStart
        var end = rawEnd
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start >= end) return
        output.add(
            TxtBlock(
                index = output.size,
                chapterIndex = chapterIndex,
                start = start,
                end = end,
                heading = heading,
            )
        )
    }
}
