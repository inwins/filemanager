package com.example.filebrowser.util

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
) {
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var answer = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer
    }
}

object LrcParser {
    private val timeToken = Regex("^(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?$")

    fun parse(raw: String): Lyrics? {
        if (raw.isBlank()) return null
        var offsetMs = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()

        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            var position = 0
            val times = mutableListOf<Long>()
            while (position < line.length && line[position] == '[') {
                val end = line.indexOf(']', position)
                if (end < 0) break
                val token = line.substring(position + 1, end)
                val time = parseTime(token)
                if (time == null) break
                times.add(time)
                position = end + 1
            }
            if (times.isNotEmpty()) {
                val text = line.substring(position).trim()
                times.forEach { timed.add(LyricLine(it, text)) }
                return@forEach
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                val body = line.substring(1, line.length - 1)
                val colon = body.indexOf(':')
                if (colon > 0) {
                    val key = body.substring(0, colon).lowercase()
                    val value = body.substring(colon + 1)
                    when (key) {
                        "ti" -> title = value
                        "ar" -> artist = value
                        "al" -> album = value
                        "offset" -> offsetMs = value.trim().toLongOrNull() ?: 0L
                    }
                }
                return@forEach
            }
            plain.add(line)
        }

        if (timed.isEmpty()) {
            if (plain.isEmpty()) return null
            return Lyrics(
                lines = plain.map { LyricLine(-1L, it) },
                synced = false,
                title = title,
                artist = artist,
                album = album,
            )
        }
        return Lyrics(
            lines = timed
                .map { it.copy(timeMs = (it.timeMs + offsetMs).coerceAtLeast(0L)) }
                .sortedBy { it.timeMs },
            synced = true,
            title = title,
            artist = artist,
            album = album,
        )
    }

    private fun parseTime(token: String): Long? {
        val match = timeToken.matchEntire(token.trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fractionText = match.groupValues[3]
        val millis = if (fractionText.isEmpty()) 0L else {
            fractionText.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
