package io.github.inwins.filebrowser.util

import io.github.inwins.filebrowser.data.FileItem
import java.text.SimpleDateFormat
import java.util.Locale

object RootListParser {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun parse(output: String, dirPath: String, showHidden: Boolean): List<FileItem> {
        val items = mutableListOf<FileItem>()
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("total ")) return@forEach
            val parts = line.split(Regex("\\s+"), limit = 8)
            if (parts.size < 8) return@forEach
            val permissions = parts[0]
            if (permissions.length < 10) return@forEach
            val isDir = permissions[0] == 'd'
            val isLink = permissions[0] == 'l'
            val size = parts[4].toLongOrNull() ?: 0L
            val modified = runCatching {
                dateFormat.parse("${parts[5]} ${parts[6]}")?.time ?: 0L
            }.getOrDefault(0L)
            var name = parts[7]
            if (isLink && " -> " in name) name = name.substringBefore(" -> ")
            if (name == "." || name == ".." || name.isEmpty()) return@forEach
            if (!showHidden && name.startsWith(".")) return@forEach
            val base = dirPath.trimEnd('/')
            val path = if (base.isEmpty()) "/$name" else "$base/$name"
            items.add(
                FileItem(
                    path = path,
                    name = name,
                    isDirectory = isDir && !isLink,
                    size = if (isDir) 0L else size,
                    lastModified = modified,
                    mime = if (isDir) null else MimeTypes.fromName(name),
                    permissions = permissions,
                    root = true,
                )
            )
        }
        return items
    }
}
