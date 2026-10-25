package com.example.cxfilemanager.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.example.cxfilemanager.util.MimeTypes

import com.example.cxfilemanager.util.Paths
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class FileRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val authority = "${context.packageName}.fileprovider"

    fun defaultRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    fun storageVolumes(): List<StorageItem> {
        val result = mutableListOf<StorageItem>()
        val seen = mutableSetOf<String>()

        fun add(id: String, label: String, path: String, removable: Boolean) {
            val file = File(path)
            if (!file.exists()) return
            val canonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            if (!seen.add(canonical)) return
            val (total, free) = stat(canonical)
            result.add(StorageItem(id, label, canonical, removable, total, free))
        }

        val primary = Environment.getExternalStorageDirectory()
        add("primary", context.getString(com.example.cxfilemanager.R.string.storage_internal), primary.absolutePath, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(StorageManager::class.java)
            manager?.storageVolumes?.forEach { volume ->
                val dir = volume.directory ?: return@forEach
                if (dir.absolutePath == primary.absolutePath) return@forEach
                val label = runCatching { volume.getDescription(context) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: context.getString(com.example.cxfilemanager.R.string.storage_sd)
                add("volume:${volume.uuid}", label, dir.absolutePath, volume.isRemovable)
            }
        } else {
            val dirs = context.getExternalFilesDirs(null)
            dirs.drop(1).forEachIndexed { index, dir ->
                if (dir == null) return@forEachIndexed
                val root = deriveStorageRoot(dir.absolutePath) ?: return@forEachIndexed
                add("external:$index", context.getString(com.example.cxfilemanager.R.string.storage_sd), root, true)
            }
        }

        if (File("/").canRead()) {
            add("root", context.getString(com.example.cxfilemanager.R.string.storage_root), "/", false)
        }
        return result
    }

    private fun deriveStorageRoot(path: String): String? {
        val marker = "/Android/data/"
        val index = path.indexOf(marker)
        return if (index > 0) path.substring(0, index) else path
    }

    private fun stat(path: String): Pair<Long, Long> {
        return runCatching {
            val stat = StatFs(path)
            stat.totalBytes to stat.availableBytes
        }.getOrDefault(0L to 0L)
    }

    fun toItem(file: File): FileItem {
        val name = file.name
        val isDir = file.isDirectory
        return FileItem(
            path = file.absolutePath,
            name = name,
            isDirectory = isDir,
            size = if (isDir) 0L else runCatching { file.length() }.getOrDefault(0L),
            lastModified = runCatching { file.lastModified() }.getOrDefault(0L),
            mime = if (isDir) null else MimeTypes.fromName(name),
        )
    }

    fun list(path: String, showHidden: Boolean, sort: SortSpec): List<FileItem> {
        val dir = File(path)
        if (!dir.exists()) throw IllegalArgumentException("Not found: $path")
        if (!dir.isDirectory) throw IllegalArgumentException("Not a directory: $path")
        val children = dir.listFiles() ?: throw SecurityException("Cannot read: $path")
        val items = children.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map(::toItem)
            .toList()
        return FileOps.sort(items, sort)
    }

    fun queryCategory(kind: CategoryKind, showHidden: Boolean): List<FileItem> {
        return runCatching {
            when (kind) {
                CategoryKind.IMAGES -> queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, showHidden, 5000)
                CategoryKind.VIDEOS -> queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, showHidden, 5000)
                CategoryKind.AUDIO -> queryMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, showHidden, 5000)
                CategoryKind.DOCUMENTS -> queryByExtensions(FileItem.DOC_EXTS, showHidden, 3000)
                CategoryKind.DOWNLOADS -> queryDownloads(showHidden)
                CategoryKind.APKS -> queryByExtensions(setOf("apk"), showHidden, 1000)
                CategoryKind.ARCHIVES -> queryByExtensions(FileItem.ARCHIVE_EXTS, showHidden, 2000)
                CategoryKind.RECENT -> queryMedia(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    null,
                    null,
                    showHidden,
                    300,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun queryDownloads(showHidden: Boolean): List<FileItem> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return queryMedia(MediaStore.Downloads.EXTERNAL_CONTENT_URI, null, null, showHidden, 2000)
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val items = dir.listFiles()
            ?.asSequence()
            ?.filter { showHidden || !it.name.startsWith(".") }
            ?.map(::toItem)
            ?.toList()
            .orEmpty()
        return FileOps.sort(items, SortSpec(SortField.DATE, SortOrder.DESC))
    }

    private fun queryByExtensions(extensions: Set<String>, showHidden: Boolean, limit: Int): List<FileItem> {
        val selection = extensions.joinToString(" OR ") {
            "LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?"
        }
        val args = extensions.map { "%.$it" }.toTypedArray()
        return queryMedia(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            selection,
            args,
            showHidden,
            limit,
        )
    }

    private fun queryMedia(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
        showHidden: Boolean,
        limit: Int,
    ): List<FileItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val items = mutableListOf<FileItem>()
        val seen = mutableSetOf<String>()
        resolver.query(uri, projection, selection, selectionArgs, sort)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            while (cursor.moveToNext() && items.size < limit) {
                val name = cursor.getString(nameIdx) ?: continue
                if (!showHidden && name.startsWith(".")) continue
                val path = if (dataIdx >= 0) cursor.getString(dataIdx) ?: "" else ""
                val id = cursor.getLong(idIdx)
                val contentUri = ContentUris.withAppendedId(uri, id).toString()
                val key = path.ifBlank { contentUri }
                if (!seen.add(key)) continue
                items.add(
                    FileItem(
                        path = path,
                        name = name,
                        isDirectory = false,
                        size = cursor.getLong(sizeIdx),
                        lastModified = cursor.getLong(dateIdx) * 1000L,
                        mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else MimeTypes.fromName(name),
                        contentUri = contentUri,
                    )
                )
            }
        }
        return items
    }

    fun installedApps(includeSystem: Boolean): List<AppItem> {
        val pm = context.packageManager
        val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        val result = mutableListOf<AppItem>()
        for (info in packages) {
            val app = info.applicationInfo ?: continue
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && isSystem) continue
            val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(info.packageName)
            val icon = runCatching { pm.getApplicationIcon(app).toBitmap(96, 96) }.getOrNull()
            val apkPath = app.sourceDir ?: ""
            result.add(
                AppItem(
                    label = label,
                    packageName = info.packageName,
                    versionName = info.versionName ?: "",
                    isSystem = isSystem,
                    apkPath = apkPath,
                    size = if (apkPath.isBlank()) 0L else runCatching { File(apkPath).length() }.getOrDefault(0L),
                    icon = icon,
                )
            )
        }
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun openIntent(item: FileItem): Intent? {
        val uri = uriFor(item) ?: return null
        val mime = item.mime ?: MimeTypes.fromName(item.name) ?: "*/*"
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(items: List<FileItem>): Intent? {
        val uris = items.mapNotNull(::uriFor)
        if (uris.isEmpty()) return null
        val mimes = items.mapNotNull { it.mime }.distinct()
        val type = if (mimes.size == 1) mimes.first() else "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        return intent.setType(type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun installIntent(item: FileItem): Intent? {
        val file = File(item.path)
        if (!file.isFile) return null
        val uri = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareApkIntent(app: AppItem): Intent? {
        if (app.apkPath.isBlank()) return null
        val source = File(app.apkPath)
        if (!source.isFile) return null
        val safeLabel = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { app.packageName }
        val dir = File(context.cacheDir, "shared_apks").apply { mkdirs() }
        val target = File(dir, "$safeLabel.apk")
        if (!target.exists() || target.length() != source.length()) {
            if (!runCatching { source.copyTo(target, overwrite = true) }.isSuccess) return null
        }
        val uri = runCatching { FileProvider.getUriForFile(context, authority, target) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun launchAppIntent(packageName: String): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun appInfoIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun uninstallIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun readText(file: File, maxBytes: Long = 2L * 1024 * 1024): String {
        if (file.length() > maxBytes) throw IllegalArgumentException("too large")
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) throw IllegalArgumentException("binary")
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        val replacementRatio = utf8.count { it == '\uFFFD' }.toDouble() / utf8.length.coerceAtLeast(1)
        return if (replacementRatio > 0.01) String(bytes, Charset.forName("GBK")) else utf8
    }

    fun saveText(file: File, text: String) {
        file.writeText(text, StandardCharsets.UTF_8)
    }

    fun folderSize(
        file: File,
        onFile: (File) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): Long = FileOps.folderSize(file, onFile, shouldContinue)

    fun search(
        root: String,
        query: String,
        showHidden: Boolean,
        limit: Int = 500,
        onProgress: (Int) -> Unit = {},
        shouldContinue: () -> Boolean = { true },
    ): List<FileItem> {
        return FileOps.search(File(root), query, showHidden, limit, onProgress, shouldContinue).map(::toItem)
    }

    fun deleteItem(item: FileItem): Boolean {
        if (item.path.isNotBlank()) {
            val file = File(item.path)
            if (file.exists()) {
                val ok = FileOps.deleteRecursive(file)
                if (ok) scan(listOf(file))
                return ok
            }
        }
        val uri = item.contentUri ?: return false
        return runCatching { resolver.delete(Uri.parse(uri), null, null) > 0 }.getOrDefault(false)
    }

    fun uriFor(item: FileItem): Uri? {
        if (item.path.isNotBlank()) {
            val file = File(item.path)
            if (file.exists()) {
                return runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull()
            }
        }
        return item.contentUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun scan(paths: List<File>) {
        if (paths.isEmpty()) return
        val values = paths.map { it.absolutePath }.toTypedArray()
        runCatching { MediaScannerConnection.scanFile(context, values, null, null) }
    }

    fun displayPath(item: FileItem): String =
        item.path.ifBlank { item.contentUri ?: item.name }

    fun exists(item: FileItem): Boolean =
        item.path.isNotBlank() && File(item.path).exists()

    fun parentOf(path: String): String? = Paths.parent(path)
}
