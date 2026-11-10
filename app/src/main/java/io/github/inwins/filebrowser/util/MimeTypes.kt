package io.github.inwins.filebrowser.util

import android.webkit.MimeTypeMap
import io.github.inwins.filebrowser.data.FileItem

object MimeTypes {
    private val common = mapOf(
        "txt" to "text/plain",
        "log" to "text/plain",
        "md" to "text/markdown",
        "json" to "application/json",
        "xml" to "application/xml",
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "pdf" to "application/pdf",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "zip" to "application/zip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "apk" to "application/vnd.android.package-archive",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "heic" to "image/heic",
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "mp3" to "audio/mpeg",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
    )

    fun fromName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        common[ext]?.let { return it }
        return runCatching {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }.getOrNull()
    }

    fun typeLabel(item: FileItem, folderLabel: String, fileLabel: String): String {
        if (item.isDirectory) return folderLabel
        return item.mime ?: item.extension.ifBlank { fileLabel }
    }
}
