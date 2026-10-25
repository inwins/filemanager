package com.example.filebrowser.util

import java.io.File

object Paths {
    fun normalize(path: String): String {
        if (path.isBlank()) return "/"
        val f = File(path)
        return runCatching { f.canonicalPath }.getOrElse { f.absolutePath }
    }

    fun parent(path: String): String? {
        val normalized = normalize(path)
        if (normalized == "/") return null
        return File(normalized).parent
    }

    fun join(parent: String, child: String): String = File(parent, child).path

    fun breadcrumbs(path: String): List<Pair<String, String>> {
        val normalized = normalize(path)
        val result = mutableListOf<Pair<String, String>>()
        var current = File(normalized)
        val chain = mutableListOf<File>()
        while (true) {
            chain.add(current)
            current = current.parentFile ?: break
        }
        chain.asReversed().forEach { file ->
            val label = if (file.path == "/") "/" else file.name
            result.add(label to file.path)
        }
        return result
    }

    fun isSameOrChild(parent: String, child: String): Boolean {
        val p = normalize(parent).trimEnd('/')
        val c = normalize(child).trimEnd('/')
        return c == p || c.startsWith("$p/")
    }

    fun safeName(name: String): Boolean =
        name.isNotBlank() && !name.contains('/') && !name.contains('\u0000') && name != "." && name != ".."
}
