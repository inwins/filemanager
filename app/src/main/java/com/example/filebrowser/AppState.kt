package com.example.filebrowser

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.filebrowser.data.AppItem
import com.example.filebrowser.data.AppSettings
import com.example.filebrowser.data.ArchiveOps
import com.example.filebrowser.data.AudioPlayer
import com.example.filebrowser.data.Bookmark
import com.example.filebrowser.data.Clipboard
import com.example.filebrowser.data.ClipboardOp
import com.example.filebrowser.data.FileItem
import com.example.filebrowser.data.FileMarker
import com.example.filebrowser.data.FileOps
import com.example.filebrowser.data.FileRepository
import com.example.filebrowser.data.OperationState
import com.example.filebrowser.data.Prefs
import com.example.filebrowser.data.ReaderSettings
import com.example.filebrowser.data.RecentEntry
import com.example.filebrowser.data.RemoteClipboard
import com.example.filebrowser.data.RemoteFileSystems
import com.example.filebrowser.data.RemoteProfile
import com.example.filebrowser.data.RemoteTransfer
import com.example.filebrowser.data.RootShell
import com.example.filebrowser.data.SortField
import com.example.filebrowser.data.SortOrder
import com.example.filebrowser.data.ViewMode
import com.example.filebrowser.ui.Screen
import com.example.filebrowser.util.Paths
import java.util.Locale
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppState(context: Context) {
    val appContext: Context = context.applicationContext
    val repository = FileRepository(appContext)
    val prefs = Prefs(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var settings by mutableStateOf(prefs.loadSettings())
        private set
    var bookmarks by mutableStateOf(prefs.loadBookmarks())
        private set
    var recents by mutableStateOf(prefs.loadRecents().filter { File(it.path).exists() })
        private set
    var backStack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
        private set
    var permissionGranted by mutableStateOf(hasStoragePermission())
        private set
    var clipboard by mutableStateOf<Clipboard?>(null)
        private set
    var remoteClipboard by mutableStateOf<RemoteClipboard?>(null)
        private set
    var operation by mutableStateOf<OperationState?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    val audioPlayer = AudioPlayer(appContext) { text -> message = text }
    var refreshTick by mutableStateOf(0)
        private set
    var lastBrowserPath by mutableStateOf(prefs.lastPath(repository.defaultRoot()))
        private set
    var includeSystemApps by mutableStateOf(false)
    var readerSettings by mutableStateOf(prefs.loadReaderSettings())
        private set
    var rootEnabled by mutableStateOf(prefs.rootAccessEnabled())
        private set
    var rootAvailable by mutableStateOf(false)
        private set
    var rootChecking by mutableStateOf(false)
        private set
    private var rootPreviewHintShown = false
    var remoteProfiles by mutableStateOf(prefs.loadRemoteProfiles())
        private set
    var markers by mutableStateOf(prefs.loadMarkers())
        private set

    var requestPermissionAction: (() -> Unit)? = null

    @Volatile
    private var operationJob: Job? = null

    init {
        if (rootEnabled) {
            scope.launch {
                rootChecking = true
                rootAvailable = withContext(Dispatchers.IO) { RootShell.isAvailable() }
                rootChecking = false
            }
        }
    }

    val current: Screen get() = backStack.last()

    fun enableRootAccess(enabled: Boolean) {
        rootEnabled = enabled
        prefs.saveRootAccessEnabled(enabled)
        if (!enabled) {
            rootAvailable = false
            return
        }
        rootChecking = true
        scope.launch {
            val available = withContext(Dispatchers.IO) { RootShell.isAvailable(force = true) }
            rootAvailable = available
            rootChecking = false
            if (!available) {
                rootEnabled = false
                prefs.saveRootAccessEnabled(false)
                message = appContext.getString(R.string.root_unavailable)
            } else {
                message = appContext.getString(R.string.root_enabled)
            }
        }
    }

    fun needsRoot(path: String): Boolean =
        rootEnabled && rootAvailable && !runCatching { java.io.File(path).canRead() }.getOrDefault(false)

    fun needsRootForWrite(parentPath: String): Boolean =
        rootEnabled && rootAvailable && !runCatching { java.io.File(parentPath).canWrite() }.getOrDefault(false)

    fun listFiles(path: String, showHidden: Boolean, sort: com.example.filebrowser.data.SortSpec): List<FileItem> {
        return try {
            repository.list(path, showHidden, sort)
        } catch (error: Exception) {
            if (rootEnabled && rootAvailable) RootShell.list(path, showHidden, sort) else throw error
        }
    }

    private fun materializeRoot(item: FileItem): FileItem? {
        val cacheDir = File(
            File(appContext.cacheDir, "root_preview"),
            kotlin.math.abs(item.path.hashCode()).toString(),
        )
        val local = RootShell.copyToCache(item.path, cacheDir, item.name)
        if (local == null) {
            message = appContext.getString(R.string.root_copy_failed)
            return null
        }
        if (item.isAudio) {
            val parent = File(item.path).parent
            val base = item.name.substringBeforeLast('.')
            if (parent != null) {
                RootShell.copyToLocal(
                    File(parent, "$base.lrc").path,
                    File(cacheDir, "$base.lrc").path,
                )
            }
        }
        if (!rootPreviewHintShown) {
            rootPreviewHintShown = true
            message = appContext.getString(R.string.root_preview_hint)
        }
        return repository.toItem(local).copy(name = item.name)
    }

    fun hasStoragePermission(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
            }
        }.getOrDefault(false)
    }

    fun refreshPermission() {
        permissionGranted = hasStoragePermission()
    }

    fun requestPermission() {
        requestPermissionAction?.invoke()
    }

    fun navigate(screen: Screen) {
        backStack = backStack + screen
        if (screen is Screen.Browser) {
            lastBrowserPath = screen.path
            prefs.saveLastPath(screen.path)
        }
    }

    fun openBreadcrumb(path: String) {
        val index = backStack.indexOfLast { it is Screen.Browser && it.path == path }
        if (index >= 0) {
            backStack = backStack.take(index + 1)
        } else {
            navigate(Screen.Browser(path))
        }
        lastBrowserPath = path
        prefs.saveLastPath(path)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack = backStack.dropLast(1)
        val screen = backStack.lastOrNull()
        if (screen is Screen.Browser) {
            lastBrowserPath = screen.path
            prefs.saveLastPath(screen.path)
        }
        return true
    }

    fun goTab(index: Int) {
        val screen = when (index) {
            0 -> Screen.Home
            1 -> {
                val path = lastBrowserPath.takeIf { File(it).isDirectory } ?: repository.defaultRoot()
                Screen.Browser(path)
            }
            2 -> Screen.Categories
            3 -> Screen.Network
            else -> Screen.Settings
        }
        backStack = listOf(screen)
        if (screen is Screen.Browser) {
            lastBrowserPath = screen.path
            prefs.saveLastPath(screen.path)
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        prefs.saveSettings(newSettings)
    }

    fun markerFor(path: String): FileMarker? = markers[path]

    fun remoteMarkerKey(profileId: String, remotePath: String): String =
        "remote://$profileId$remotePath"

    fun markerForRemote(profileId: String, remotePath: String): FileMarker? =
        markers[remoteMarkerKey(profileId, remotePath)]

    fun setMarker(path: String, marker: FileMarker?) {
        if (path.isBlank()) return
        markers = if (marker == null || marker.isEmpty) {
            markers - path
        } else {
            markers + (path to marker)
        }
        prefs.saveMarkers(markers)
        refreshTick++
    }

    fun saveRemoteProfile(profile: RemoteProfile) {
        remoteProfiles = if (remoteProfiles.any { it.id == profile.id }) {
            remoteProfiles.map { if (it.id == profile.id) profile else it }
        } else {
            remoteProfiles + profile
        }
        prefs.saveRemoteProfiles(remoteProfiles)
    }

    fun deleteRemoteProfile(id: String) {
        remoteProfiles = remoteProfiles.filterNot { it.id == id }
        prefs.saveRemoteProfiles(remoteProfiles)
    }

    fun remoteProfile(id: String): RemoteProfile? = remoteProfiles.firstOrNull { it.id == id }

    fun updateReaderSettings(newSettings: ReaderSettings) {
        readerSettings = newSettings
        prefs.saveReaderSettings(newSettings)
    }

    fun toggleViewMode() {
        updateSettings(
            settings.copy(viewMode = if (settings.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
        )
    }

    fun setSort(field: SortField, order: SortOrder) {
        updateSettings(settings.copy(sortField = field, sortOrder = order))
    }

    fun toggleHidden() {
        updateSettings(settings.copy(showHidden = !settings.showHidden))
    }

    fun isBookmarked(path: String): Boolean = bookmarks.any { it.path == path }

    fun addBookmark(path: String) {
        if (isBookmarked(path)) return
        val name = if (path == "/") "/" else File(path).name.ifBlank { path }
        bookmarks = bookmarks + Bookmark(name, path)
        prefs.saveBookmarks(bookmarks)
        message = appContext.getString(R.string.bookmark_added)
    }

    fun removeBookmark(path: String) {
        bookmarks = bookmarks.filterNot { it.path == path }
        prefs.saveBookmarks(bookmarks)
        message = appContext.getString(R.string.bookmark_removed)
    }

    fun clearBookmarks() {
        if (bookmarks.isEmpty()) return
        bookmarks = emptyList()
        prefs.saveBookmarks(emptyList())
        message = appContext.getString(R.string.bookmark_removed)
    }

    fun clearRecents() {
        recents = emptyList()
        prefs.saveRecents(emptyList())
        message = appContext.getString(R.string.recent_cleared)
    }

    private fun addRecent(item: FileItem) {
        if (item.isDirectory || item.path.isBlank()) return
        val entry = RecentEntry(item.name, item.path, System.currentTimeMillis())
        recents = (listOf(entry) + recents.filterNot { it.path == item.path }).take(30)
        prefs.saveRecents(recents)
    }

    fun setClipboard(op: ClipboardOp, items: List<FileItem>) {
        if (items.isEmpty()) return
        clipboard = Clipboard(op, items)
        remoteClipboard = null
        message = appContext.getString(
            if (op == ClipboardOp.COPY) R.string.clipboard_copied else R.string.clipboard_cut,
            items.size,
        )
    }

    fun setRemoteClipboard(op: ClipboardOp, profileId: String, items: List<com.example.filebrowser.data.RemoteEntry>) {
        if (items.isEmpty()) return
        remoteClipboard = RemoteClipboard(op, profileId, items)
        clipboard = null
        message = appContext.getString(
            if (op == ClipboardOp.COPY) R.string.clipboard_copied else R.string.clipboard_cut,
            items.size,
        )
    }

    fun clearClipboard() {
        clipboard = null
        remoteClipboard = null
    }

    fun openItem(
        item: FileItem,
        siblings: List<FileItem> = listOf(item),
        addToRecent: Boolean = true,
    ) {
        if (item.isDirectory) {
            if (item.path.isBlank()) {
                message = appContext.getString(R.string.open_folder_failed)
            } else {
                navigate(Screen.Browser(item.path))
            }
            return
        }
        val actual = if (needsRoot(item.path)) {
            materializeRoot(item) ?: return
        } else {
            if (addToRecent) addRecent(item)
            item
        }
        val actualSiblings = if (actual.path != item.path) listOf(actual) else siblings
        if (actual.isAudio) {
            playAudio(actual, actualSiblings)
            return
        }
        if (actual.isArchive && actual.path.isNotBlank()) {
            navigate(Screen.ArchivePreview(actual.path))
            return
        }
        if (actual.isImage) {
            val imageItems = actualSiblings.filter { it.isImage }
            val models = imageItems.mapNotNull { modelOf(it) }
            val target = modelOf(actual)
            if (models.isNotEmpty() && target != null) {
                navigate(Screen.ImageViewer(models, models.indexOf(target).coerceAtLeast(0)))
                return
            }
        }
        if (actual.isText && actual.path.isNotBlank()) {
            if (actual.extension in setOf("txt", "log", "md")) {
                navigate(Screen.Reader(actual.path))
            } else {
                navigate(Screen.TextEditor(actual.path))
            }
            return
        }
        if (actual.isApk) {
            installApk(actual)
            return
        }
        openExternal(actual)
    }

    fun playAudio(item: FileItem, siblings: List<FileItem> = listOf(item)) {
        val audio = siblings.filter { it.isAudio && (it.path.isNotBlank() || it.contentUri != null) }
            .ifEmpty { listOf(item) }
        val index = audio.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
        audioPlayer.play(audio, index)
        if (backStack.last() !is Screen.AudioPlayer) navigate(Screen.AudioPlayer)
    }

    private fun modelOf(item: FileItem): String? =
        item.path.ifBlank { item.contentUri }

    fun openExternal(item: FileItem) {
        val intent = repository.openIntent(item)
        if (intent == null) {
            message = appContext.getString(R.string.open_failed)
            return
        }
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            message = appContext.getString(R.string.open_failed)
        } catch (_: SecurityException) {
            message = appContext.getString(R.string.open_failed)
        }
    }

    fun openWith(item: FileItem) {
        val target = if (needsRoot(item.path)) materializeRoot(item) ?: return else item
        val intent = repository.openIntent(target)
        if (intent == null) {
            message = appContext.getString(R.string.open_failed)
            return
        }
        try {
            appContext.startActivity(Intent.createChooser(intent, appContext.getString(R.string.open_with)))
        } catch (_: ActivityNotFoundException) {
            message = appContext.getString(R.string.open_failed)
        }
    }

    fun share(items: List<FileItem>) {
        val prepared = items.mapNotNull { item ->
            if (needsRoot(item.path)) {
                if (item.isDirectory) null else materializeRoot(item)
            } else {
                item
            }
        }
        val intent = repository.shareIntent(prepared)
        if (intent == null) {
            message = appContext.getString(R.string.share_failed)
            return
        }
        try {
            appContext.startActivity(Intent.createChooser(intent, appContext.getString(R.string.share)))
        } catch (_: ActivityNotFoundException) {
            message = appContext.getString(R.string.share_failed)
        }
    }

    fun installApk(item: FileItem) {
        val target = if (needsRoot(item.path)) materializeRoot(item) ?: return else item
        val intent = repository.installIntent(target)
        if (intent == null) {
            message = appContext.getString(R.string.install_failed)
            return
        }
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            message = appContext.getString(R.string.install_failed)
        } catch (_: SecurityException) {
            message = appContext.getString(R.string.install_failed)
        }
    }

    fun launchApp(app: AppItem) {
        val intent = repository.launchAppIntent(app.packageName)
        if (intent == null) {
            message = appContext.getString(R.string.app_launch_failed)
            return
        }
        runCatching { appContext.startActivity(intent) }
            .onFailure { message = appContext.getString(R.string.app_launch_failed) }
    }

    fun showAppInfo(app: AppItem) {
        runCatching { appContext.startActivity(repository.appInfoIntent(app.packageName)) }
            .onFailure { message = appContext.getString(R.string.operation_failed) }
    }

    fun uninstallApp(app: AppItem) {
        runCatching { appContext.startActivity(repository.uninstallIntent(app.packageName)) }
            .onFailure { message = appContext.getString(R.string.operation_failed) }
    }

    fun shareApk(app: AppItem) {
        val intent = repository.shareApkIntent(app)
        if (intent == null) {
            message = appContext.getString(R.string.share_failed)
            return
        }
        runCatching {
            appContext.startActivity(Intent.createChooser(intent, appContext.getString(R.string.share)))
        }.onFailure { message = appContext.getString(R.string.share_failed) }
    }

    fun paste(destPath: String) {
        val remote = remoteClipboard
        if (remote != null) {
            pasteRemoteToLocal(remote, destPath)
            return
        }
        val clip = clipboard
        if (clip == null) {
            message = appContext.getString(R.string.nothing_to_paste)
            return
        }
        val destDir = File(destPath)
        val moving = clip.op == ClipboardOp.MOVE
        val destRoot = needsRootForWrite(destPath)
        runOperation(if (moving) R.string.move_progress else R.string.copy_progress) { update, shouldContinue ->
            var success = 0
            val scanned = mutableListOf<File>()
            for (item in clip.items) {
                if (!shouldContinue()) return@runOperation null
                if (item.path.isBlank()) continue
                val sourceRoot = needsRoot(item.path)
                if (!sourceRoot && !File(item.path).exists()) continue
                val targetPath = uniqueTargetPath(destPath, item.name, destRoot)
                update(item.name)
                val ok = if (sourceRoot || destRoot) {
                    RootShell.copy(
                        src = item.path,
                        dest = targetPath,
                        move = moving,
                        destLocal = !destRoot,
                    )
                } else {
                    val source = File(item.path)
                    val target = File(targetPath)
                    if (moving) {
                        FileOps.move(source, target, shouldContinue = shouldContinue)
                    } else {
                        FileOps.copyRecursive(source, target, shouldContinue = shouldContinue)
                    }
                }
                if (ok) {
                    success++
                    if (!destRoot) scanned.add(File(targetPath))
                    if (moving && !sourceRoot) scanned.add(File(item.path))
                }
            }
            repository.scan(scanned)
            if (moving && success > 0) clipboard = null
            if (success == 0) null
            else appContext.getString(if (moving) R.string.moved else R.string.copied, success)
        }
    }

    private fun pasteRemoteToLocal(remote: RemoteClipboard, destPath: String) {
        val profile = remoteProfile(remote.profileId)
        if (profile == null) {
            message = appContext.getString(R.string.network_connection_failed)
            return
        }
        val moving = remote.op == ClipboardOp.MOVE
        runOperation(if (moving) R.string.move_progress else R.string.copy_progress) { update, shouldContinue ->
            val system = runCatching { RemoteFileSystems.open(profile) }.getOrNull()
            if (system == null) return@runOperation null
            try {
                var success = 0
                for (entry in remote.items) {
                    if (!shouldContinue()) break
                    update(entry.name)
                    val ok = RemoteTransfer.remoteToLocal(
                        source = system,
                        entry = entry,
                        destDir = File(destPath),
                        shouldContinue = shouldContinue,
                    )
                    if (ok) {
                        success++
                        if (moving) system.delete(entry.path, entry.isDirectory)
                    }
                }
                repository.scan(listOf(File(destPath)))
                if (moving && success > 0) remoteClipboard = null
                if (success == 0) null
                else appContext.getString(if (moving) R.string.moved else R.string.copied, success)
            } finally {
                runCatching { system.close() }
            }
        }
    }

    fun delete(items: List<FileItem>) {
        if (items.isEmpty()) return
        runOperation(R.string.delete_progress) { update, shouldContinue ->
            var success = 0
            for (item in items) {
                if (!shouldContinue()) break
                update(item.name)
                val ok = if (needsRoot(item.path)) RootShell.delete(item.path) else repository.deleteItem(item)
                if (ok) success++
            }
            if (success == 0) null else appContext.getString(R.string.deleted, success)
        }
    }

    fun rename(item: FileItem, newName: String) {
        if (item.path.isBlank()) return
        if (!Paths.safeName(newName)) {
            message = appContext.getString(R.string.invalid_name)
            return
        }
        runOperation(R.string.rename_progress) { _, _ ->
            if (needsRoot(item.path)) {
                val parent = File(item.path).parent ?: return@runOperation null
                val target = File(parent, newName).path
                if (RootShell.rename(item.path, target)) {
                    appContext.getString(R.string.renamed_done)
                } else {
                    null
                }
            } else {
                val target = FileOps.rename(File(item.path), newName)
                if (target == null) {
                    null
                } else {
                    repository.scan(listOf(File(item.path), target))
                    appContext.getString(R.string.renamed_done)
                }
            }
        }
    }

    fun createFolder(parentPath: String, name: String) {
        if (!Paths.safeName(name)) {
            message = appContext.getString(R.string.invalid_name)
            return
        }
        runOperation(R.string.create_progress) { _, _ ->
            if (needsRootForWrite(parentPath)) {
                val target = File(parentPath, name)
                if (RootShell.mkdir(target.path)) appContext.getString(R.string.created) else null
            } else {
                val target = FileOps.createFolder(File(parentPath), name)
                if (target == null) {
                    null
                } else {
                    repository.scan(listOf(target))
                    appContext.getString(R.string.created)
                }
            }
        }
    }

    fun createFile(parentPath: String, name: String) {
        if (!Paths.safeName(name)) {
            message = appContext.getString(R.string.invalid_name)
            return
        }
        runOperation(R.string.create_progress) { _, _ ->
            if (needsRootForWrite(parentPath)) {
                val target = File(parentPath, name)
                if (RootShell.touch(target.path)) appContext.getString(R.string.created) else null
            } else {
                val target = FileOps.createFile(File(parentPath), name)
                if (target == null) {
                    null
                } else {
                    repository.scan(listOf(target))
                    appContext.getString(R.string.created)
                }
            }
        }
    }

    fun zipWithOptions(
        items: List<FileItem>,
        destPath: String,
        archiveName: String,
        password: String,
        splitSizeMb: Int,
    ) {
        if (items.any { needsRoot(it.path) } || needsRootForWrite(destPath)) {
            message = appContext.getString(R.string.root_zip_unsupported)
            return
        }
        val sources = items.filter { it.path.isNotBlank() }.map { File(it.path) }.filter { it.exists() }
        if (sources.isEmpty()) {
            message = appContext.getString(R.string.zip_failed)
            return
        }
        var name = archiveName.trim()
        if (name.isBlank()) {
            name = if (sources.size == 1) sources.first().name.substringBeforeLast('.') else "archive"
        }
        if (!name.endsWith(".zip", ignoreCase = true)) name += ".zip"
        val zipFile = FileOps.uniqueFile(File(destPath, name))
        val splitBytes = splitSizeMb.toLong() * 1024L * 1024L
        runOperation(R.string.zip_progress) { _, shouldContinue ->
            val ok = if (password.isBlank() && splitSizeMb <= 0) {
                FileOps.zip(sources, zipFile, shouldContinue = shouldContinue)
            } else {
                ArchiveOps.createZip(
                    sources = sources,
                    zipFile = zipFile,
                    password = password.takeIf { it.isNotBlank() },
                    splitBytes = splitBytes,
                )
            }
            if (ok) {
                repository.scan(listOf(zipFile))
                appContext.getString(R.string.zipped, zipFile.name)
            } else {
                null
            }
        }
    }

    fun zip(items: List<FileItem>, destPath: String) {
        if (items.any { needsRoot(it.path) } || needsRootForWrite(destPath)) {
            message = appContext.getString(R.string.root_zip_unsupported)
            return
        }
        val sources = items.filter { it.path.isNotBlank() }.map { File(it.path) }.filter { it.exists() }
        if (sources.isEmpty()) {
            message = appContext.getString(R.string.zip_failed)
            return
        }
        val baseName = if (sources.size == 1) sources.first().name.substringBeforeLast('.') else "archive"
        val zipFile = FileOps.uniqueFile(File(destPath, "$baseName.zip"))
        runOperation(R.string.zip_progress) { update, shouldContinue ->
            update(zipFile.name)
            val ok = FileOps.zip(sources, zipFile, shouldContinue = shouldContinue)
            if (ok) {
                repository.scan(listOf(zipFile))
                appContext.getString(R.string.zipped, zipFile.name)
            } else {
                null
            }
        }
    }

    fun unzip(item: FileItem, destPath: String) {
        if (needsRoot(item.path) || needsRootForWrite(destPath)) {
            message = appContext.getString(R.string.root_zip_unsupported)
            return
        }
        if (item.path.isBlank()) {
            message = appContext.getString(R.string.unzip_failed)
            return
        }
        val base = item.name.substringBeforeLast('.', item.name)
        val target = FileOps.uniqueFile(File(destPath, base))
        runOperation(R.string.unzip_progress) { update, shouldContinue ->
            update(target.name)
            val ok = FileOps.unzip(File(item.path), target, shouldContinue = shouldContinue)
            if (ok) {
                repository.scan(listOf(target))
                appContext.getString(R.string.unzipped, target.name)
            } else {
                null
            }
        }
    }

    fun cancelOperation() {
        operationJob?.cancel()
    }

    fun showMessage(text: String) {
        message = text
    }

    fun refresh() {
        refreshTick++
    }

    private fun uniqueTargetPath(destPath: String, name: String, destRoot: Boolean): String {
        val first = File(destPath, name)
        if (!targetExists(first.path, destRoot)) return first.path
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = File(destPath, "$base ($index)$ext")
            if (!targetExists(candidate.path, destRoot)) return candidate.path
            index++
        }
    }

    private fun targetExists(path: String, destRoot: Boolean): Boolean =
        if (destRoot) RootShell.exists(path) else File(path).exists()

    private fun runOperation(
        titleRes: Int,
        block: (update: (String) -> Unit, shouldContinue: () -> Boolean) -> String?,
    ) {
        if (operation != null) return
        operation = OperationState(appContext.getString(titleRes))
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            var result: String? = null
            var cancelled = false
            try {
                result = block(
                    { detail -> operation = operation?.copy(detail = detail) },
                    { operationJob?.isActive == true },
                )
            } catch (_: CancellationException) {
                cancelled = true
            } catch (_: Throwable) {
                result = null
            } finally {
                val wasCancelled = cancelled || !isActive
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    operation = null
                    operationJob = null
                    refreshTick++
                    message = when {
                        wasCancelled -> appContext.getString(R.string.operation_cancelled)
                        result != null -> result
                        else -> appContext.getString(R.string.operation_failed)
                    }
                }
            }
        }
        operationJob = job
        job.start()
    }
}
