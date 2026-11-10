package io.github.inwins.filebrowser.data

import android.content.Context
import java.io.File
import java.util.UUID

class TrashStore(context: Context) {
    private val root = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "trash",
    ).apply { mkdirs() }

    fun trash(file: File): File? {
        if (!file.exists()) return null
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        if (!dir.mkdirs()) return null
        val target = File(dir, file.name)
        return if (FileOps.move(file, target)) target else {
            runCatching { dir.deleteRecursively() }
            null
        }
    }

    fun restore(entry: TrashEntry): File? {
        val source = File(entry.trashedPath)
        if (!source.exists()) return null
        val original = File(entry.originalPath)
        original.parentFile?.mkdirs()
        val target = if (original.exists()) FileOps.uniqueFile(original) else original
        return if (FileOps.move(source, target)) target else null
    }

    fun delete(entry: TrashEntry): Boolean {
        val source = File(entry.trashedPath)
        val container = source.parentFile
        val ok = FileOps.deleteRecursive(source)
        if (ok) runCatching { container?.delete() }
        return ok
    }

    fun empty(entries: List<TrashEntry>): Int {
        var count = 0
        entries.forEach { if (delete(it)) count++ }
        return count
    }
}
