package io.github.inwins.filebrowser.util

data class StreamRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
}

object StreamRangeParser {
    /**
     * Parses a single HTTP Range header. Returns null for malformed or unsatisfiable ranges.
     * A null header is handled by the caller as a full response.
     */
    fun parse(header: String?, total: Long): StreamRange? {
        if (total <= 0) return null
        val value = header?.trim() ?: return StreamRange(0, total - 1)
        if (!value.startsWith("bytes=", ignoreCase = true)) return null
        val spec = value.substringAfter('=').trim()
        if (spec.contains(',')) return null
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()
        if (startText.isEmpty()) {
            val suffix = endText.toLongOrNull() ?: return null
            if (suffix <= 0) return null
            val start = (total - suffix).coerceAtLeast(0)
            return StreamRange(start, total - 1)
        }
        val start = startText.toLongOrNull() ?: return null
        if (start >= total) return null
        val end = if (endText.isEmpty()) total - 1 else {
            val parsed = endText.toLongOrNull() ?: return null
            parsed.coerceAtMost(total - 1)
        }
        if (end < start) return null
        return StreamRange(start, end)
    }
}
