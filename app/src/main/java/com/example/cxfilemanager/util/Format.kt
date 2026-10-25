package com.example.cxfilemanager.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Format {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(exp.toDouble())
        return if (exp == 0) {
            "${bytes} B"
        } else if (value >= 100) {
            String.format(Locale.US, "%.0f %s", value, units[exp])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[exp])
        }
    }

    fun date(millis: Long): String {
        if (millis <= 0) return "—"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    fun shortDate(millis: Long): String {
        if (millis <= 0) return "—"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var si = i
                var sj = j
                while (si < a.length && a[si].isDigit()) si++
                while (sj < b.length && b[sj].isDigit()) sj++
                val na = a.substring(i, si).trimStart('0')
                val nb = b.substring(j, sj).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
                i = si
                j = sj
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return a.length - b.length
    }

    fun relativePath(path: String, root: String): String {
        if (path == root) return ""
        val prefix = if (root.endsWith("/")) root else "$root/"
        return if (path.startsWith(prefix)) path.removePrefix(prefix) else path
    }
}
