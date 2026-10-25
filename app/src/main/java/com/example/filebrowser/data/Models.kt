package com.example.filebrowser.data

import android.graphics.Bitmap

enum class SortField { NAME, SIZE, DATE, TYPE }
enum class SortOrder { ASC, DESC }
enum class ViewMode { LIST, GRID }
enum class ClipboardOp { COPY, MOVE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CategoryKind {
    IMAGES,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    DOWNLOADS,
    APKS,
    ARCHIVES,
    RECENT,
}

data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mime: String? = null,
    val contentUri: String? = null,
    val childCount: Int? = null,
    val permissions: String? = null,
    val root: Boolean = false,
    val remote: Boolean = false,
) {
    val isHidden: Boolean get() = name.startsWith(".")
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    val isImage: Boolean get() = !isDirectory && (mime?.startsWith("image/") == true || extension in IMAGE_EXTS)
    val isVideo: Boolean get() = !isDirectory && (mime?.startsWith("video/") == true || extension in VIDEO_EXTS)
    val isAudio: Boolean get() = !isDirectory && (mime?.startsWith("audio/") == true || extension in AUDIO_EXTS)
    val isArchive: Boolean get() = !isDirectory && extension in ARCHIVE_EXTS
    val isApk: Boolean get() = !isDirectory && extension == "apk"
    val isText: Boolean get() = !isDirectory && (mime?.startsWith("text/") == true || extension in TEXT_EXTS)

    companion object {
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "avif")
        val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "3gp", "m4v", "ts")
        val AUDIO_EXTS = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma", "amr")
        val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "iso")
        val TEXT_EXTS = setOf("txt", "md", "json", "xml", "html", "htm", "css", "js", "kt", "java", "py", "c", "h", "cpp", "sh", "log", "csv", "yml", "yaml", "ini", "conf", "properties")
        val DOC_EXTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "ods", "odp", "epub")
    }
}

data class StorageItem(
    val id: String,
    val label: String,
    val path: String,
    val removable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f else (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

data class Bookmark(val name: String, val path: String)

data class RecentEntry(val name: String, val path: String, val openedAt: Long)

data class Clipboard(val op: ClipboardOp, val items: List<FileItem>)

data class OperationState(
    val title: String,
    val detail: String = "",
    val progress: Float? = null,
)

data class SortSpec(val field: SortField = SortField.NAME, val order: SortOrder = SortOrder.ASC)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val showHidden: Boolean = false,
    val sortField: SortField = SortField.NAME,
    val sortOrder: SortOrder = SortOrder.ASC,
) {
    val sortSpec: SortSpec get() = SortSpec(sortField, sortOrder)
}

enum class MarkerColor { NONE, RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE }

data class FileMarker(
    val color: MarkerColor = MarkerColor.NONE,
    val label: String = "",
    val favorite: Boolean = false,
    val pinned: Boolean = false,
) {
    val isEmpty: Boolean
        get() = color == MarkerColor.NONE && label.isBlank() && !favorite && !pinned
}

data class AppItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val isSystem: Boolean,
    val apkPath: String,
    val size: Long,
    val icon: Bitmap?,
)
