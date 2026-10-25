package com.example.cxfilemanager

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
import com.example.cxfilemanager.data.AppItem
import com.example.cxfilemanager.data.AppSettings
import com.example.cxfilemanager.data.Bookmark
import com.example.cxfilemanager.data.Clipboard
import com.example.cxfilemanager.data.ClipboardOp
import com.example.cxfilemanager.data.FileItem
import com.example.cxfilemanager.data.FileOps
import com.example.cxfilemanager.data.FileRepository
import com.example.cxfilemanager.data.OperationState
import com.example.cxfilemanager.data.Prefs
import com.example.cxfilemanager.data.ReaderSettings
import com.example.cxfilemanager.data.RecentEntry
import com.example.cxfilemanager.data.SortField
import com.example.cxfilemanager.data.SortOrder
import com.example.cxfilemanager.data.ViewMode
import com.example.cxfilemanager.ui.Screen
import com.example.cxfilemanager.util.Paths
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
    var operation by mutableStateOf<OperationState?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var refreshTick by mutableStateOf(0)
        private set
    var lastBrowserPath by mutableStateOf(prefs.lastPath(repository.defaultRoot()))
        private set
    var includeSystemApps by mutableStateOf(false)
    var readerSettings by mutableStateOf(prefs.loadReaderSettings())
        private set

    var requestPermissionAction: (() -> Unit)? = null

    @Volatile
    private var operationJob: Job? = null

    val current: Screen get() = backStack.last()

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
        message = appContext.getString(
            if (op == ClipboardOp.COPY) R.string.clipboard_copied else R.string.clipboard_cut,
            items.size,
        )
    }

    fun clearClipboard() {
        clipboard = null
    }

    fun openItem(item: FileItem, siblings: List<FileItem> = listOf(item)) {
        if (item.isDirectory) {
            if (item.path.isBlank()) {
                message = appContext.getString(R.string.open_folder_failed)
            } else {
                navigate(Screen.Browser(item.path))
            }
            return
        }
        addRecent(item)
        if (item.isImage) {
            val imageItems = siblings.filter { it.isImage }
            val models = imageItems.mapNotNull { modelOf(it) }
            val target = modelOf(item)
            if (models.isNotEmpty() && target != null) {
                navigate(Screen.ImageViewer(models, models.indexOf(target).coerceAtLeast(0)))
                return
            }
        }
        if (item.isText && item.path.isNotBlank()) {
            if (item.extension in setOf("txt", "log", "md")) {
                navigate(Screen.Reader(item.path))
            } else {
                navigate(Screen.TextEditor(item.path))
            }
            return
        }
        if (item.isApk) {
            installApk(item)
            return
        }
        openExternal(item)
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
        val intent = repository.openIntent(item)
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
        val intent = repository.shareIntent(items)
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
        val intent = repository.installIntent(item)
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
        val clip = clipboard
        if (clip == null) {
            message = appContext.getString(R.string.nothing_to_paste)
            return
        }
        val destDir = File(destPath)
        val moving = clip.op == ClipboardOp.MOVE
        runOperation(if (moving) R.string.move_progress else R.string.copy_progress) { update, shouldContinue ->
            var success = 0
            val scanned = mutableListOf<File>()
            for (item in clip.items) {
                if (!shouldContinue()) return@runOperation null
                if (item.path.isBlank()) continue
                val source = File(item.path)
                if (!source.exists()) continue
                val target = FileOps.uniqueFile(File(destDir, source.name))
                update(source.name)
                val ok = if (moving) {
                    FileOps.move(source, target, shouldContinue = shouldContinue)
                } else {
                    FileOps.copyRecursive(source, target, shouldContinue = shouldContinue)
                }
                if (ok) {
                    success++
                    scanned.add(target)
                    if (moving) scanned.add(source)
                }
            }
            repository.scan(scanned)
            if (moving && success > 0) clipboard = null
            if (success == 0) null
            else appContext.getString(if (moving) R.string.moved else R.string.copied, success)
        }
    }

    fun delete(items: List<FileItem>) {
        if (items.isEmpty()) return
        runOperation(R.string.delete_progress) { update, shouldContinue ->
            var success = 0
            for (item in items) {
                if (!shouldContinue()) break
                update(item.name)
                if (repository.deleteItem(item)) success++
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
            val target = FileOps.rename(File(item.path), newName)
            if (target == null) {
                null
            } else {
                repository.scan(listOf(File(item.path), target))
                appContext.getString(R.string.renamed_done)
            }
        }
    }

    fun createFolder(parentPath: String, name: String) {
        if (!Paths.safeName(name)) {
            message = appContext.getString(R.string.invalid_name)
            return
        }
        runOperation(R.string.create_progress) { _, _ ->
            val target = FileOps.createFolder(File(parentPath), name)
            if (target == null) {
                null
            } else {
                repository.scan(listOf(target))
                appContext.getString(R.string.created)
            }
        }
    }

    fun createFile(parentPath: String, name: String) {
        if (!Paths.safeName(name)) {
            message = appContext.getString(R.string.invalid_name)
            return
        }
        runOperation(R.string.create_progress) { _, _ ->
            val target = FileOps.createFile(File(parentPath), name)
            if (target == null) {
                null
            } else {
                repository.scan(listOf(target))
                appContext.getString(R.string.created)
            }
        }
    }

    fun zip(items: List<FileItem>, destPath: String) {
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
