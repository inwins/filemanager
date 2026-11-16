package io.github.inwins.filebrowser.lan

import io.github.inwins.filebrowser.util.Paths
import java.io.File

object LanTransferPaths {
    fun resolve(root: File, relative: String): File? {
        val clean = relative.trim('/')
        if (clean.contains('\u0000')) return null
        val candidate = if (clean.isEmpty()) root else File(root, clean)
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!Paths.isSameOrChild(root.path, canonical.path)) return null
        return canonical
    }

    fun safeFileName(name: String): String {
        val leaf = name.substringAfterLast('/')
        return if (Paths.safeName(leaf)) leaf else "file"
    }

    fun relative(root: File, file: File): String {
        val rootPath = root.path.trimEnd('/')
        val filePath = file.path
        if (filePath == rootPath) return ""
        return filePath.removePrefix("$rootPath/").replace(File.separatorChar, '/')
    }
}
