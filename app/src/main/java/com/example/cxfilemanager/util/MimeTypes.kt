package com.example.cxfilemanager.util

import android.webkit.MimeTypeMap
import com.example.cxfilemanager.data.FileItem

object MimeTypes {
    fun fromName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    fun typeLabel(item: FileItem, folderLabel: String, fileLabel: String): String {
        if (item.isDirectory) return folderLabel
        return item.mime ?: item.extension.ifBlank { fileLabel }
    }
}
