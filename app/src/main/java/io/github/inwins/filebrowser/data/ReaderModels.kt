package io.github.inwins.filebrowser.data

enum class ReaderTheme { SYSTEM, PAPER, SEPIA, EYE, DARK }

data class ReaderSettings(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.65f,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val ttsRate: Float = 1.0f,
    val ttsAutoNext: Boolean = true,
)

data class TextBookmark(
    val id: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val offset: Int,
    val preview: String,
    val createdAt: Long,
)

data class TxtChapter(
    val index: Int,
    val title: String,
    val start: Int,
    val end: Int,
    val firstBlock: Int,
)

data class TxtBlock(
    val index: Int,
    val chapterIndex: Int,
    val start: Int,
    val end: Int,
    val heading: Boolean,
)

data class TxtBook(
    val text: String,
    val chapters: List<TxtChapter>,
    val blocks: List<TxtBlock>,
) {
    fun blockText(block: TxtBlock): String {
        if (block.start < 0 || block.end > text.length || block.start >= block.end) return ""
        return text.substring(block.start, block.end)
    }

    fun blockIndexForOffset(offset: Int): Int {
        if (blocks.isEmpty()) return 0
        var lo = 0
        var hi = blocks.lastIndex
        var answer = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (blocks[mid].start <= offset) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer
    }

    fun chapterForBlock(blockIndex: Int): Int =
        blocks.getOrNull(blockIndex)?.chapterIndex ?: 0

    fun percentForBlock(blockIndex: Int): Int {
        if (blocks.isEmpty()) return 0
        return ((blockIndex + 1) * 100 / blocks.size).coerceIn(0, 100)
    }
}

data class TtsChunk(val blockIndex: Int, val text: String)

sealed class TtsEvent {
    data object Ready : TtsEvent()
    data class Started(val blockIndex: Int) : TtsEvent()
    data object ChapterFinished : TtsEvent()
    data class Error(val message: String?) : TtsEvent()
}
