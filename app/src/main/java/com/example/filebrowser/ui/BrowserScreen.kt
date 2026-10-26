package com.example.filebrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.filebrowser.AppState
import com.example.filebrowser.R
import com.example.filebrowser.data.ClipboardOp
import com.example.filebrowser.data.FileItem
import com.example.filebrowser.data.ViewMode
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(state: AppState, screen: Screen.Browser) {
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSort by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showNewSheet by remember { mutableStateOf(false) }
    var showSelectionMore by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showCreateFile by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var propertiesItem by remember { mutableStateOf<FileItem?>(null) }
    var hashItem by remember { mutableStateOf<FileItem?>(null) }
    var markerItem by remember { mutableStateOf<FileItem?>(null) }
    var zipItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var showZipOptions by remember { mutableStateOf(false) }

    LaunchedEffect(
        screen.path,
        state.settings.sortField,
        state.settings.sortOrder,
        state.settings.showHidden,
        state.refreshTick,
    ) {
        loading = true
        error = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                state.listFiles(screen.path, state.settings.showHidden, state.settings.sortSpec)
            }
        }
        result.onSuccess {
            items = it
            loading = false
        }.onFailure {
            items = emptyList()
            error = true
            loading = false
        }
    }

    LaunchedEffect(items) {
        val available = items.map { it.path }.toSet()
        selected = selected.intersect(available)
    }

    BackHandler(enabled = selected.isNotEmpty()) {
        selected = emptySet()
    }

    val selectedItems = items.filter { it.path in selected }
    val folderName = when (screen.path) {
        "/" -> stringResource(R.string.storage_root)
        state.repository.defaultRoot() -> stringResource(R.string.storage_internal)
        else -> File(screen.path).name.ifBlank { "/" }
    }
    val viewMode = state.settings.viewMode

    Column(Modifier.fillMaxSize().conditionalNavigationBarsPadding(state.backStack.size > 1)) {
        if (selected.isNotEmpty()) {
            SelectionActionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onCopy = {
                    state.setClipboard(ClipboardOp.COPY, selectedItems)
                    selected = emptySet()
                },
                onCut = {
                    state.setClipboard(ClipboardOp.MOVE, selectedItems)
                    selected = emptySet()
                },
                onDelete = { showDelete = true },
                onMore = { showSelectionMore = true },
            )
        } else {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { state.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    IconButton(onClick = { state.navigate(Screen.Search(screen.path)) }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(onClick = { showSort = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.sort))
                    }
                    IconButton(onClick = { state.toggleViewMode() }) {
                        Icon(
                            if (viewMode == ViewMode.LIST) Icons.Filled.Apps else Icons.Filled.ViewList,
                            contentDescription = stringResource(R.string.view_list),
                        )
                    }
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                },
            )
        }

        PathBreadcrumb(screen.path) { state.openBreadcrumb(it) }

        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error -> EmptyState(Icons.Filled.FolderOpen, stringResource(R.string.cannot_read), Modifier.fillMaxSize())
                items.isEmpty() -> EmptyState(
                    Icons.Filled.FolderOpen,
                    stringResource(R.string.empty_folder),
                    Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                )
                viewMode == ViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(items, key = { it.path }) { item ->
                            FileGridItem(
                                item = item,
                                selected = item.path in selected,
                                selectionMode = selected.isNotEmpty(),
                                marker = state.markerFor(item.path),
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = toggle(selected, item.path)
                                    } else if (item.isDirectory) {
                                        state.navigate(Screen.Browser(item.path))
                                    } else {
                                        state.openItem(item, items)
                                    }
                                },
                                onLongClick = {
                                    selected = if (item.path in selected) selected - item.path
                                    else selected + item.path
                                },
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        items(items, key = { it.path }) { item ->
                            FileRow(
                                item = item,
                                selected = item.path in selected,
                                selectionMode = selected.isNotEmpty(),
                                marker = state.markerFor(item.path),
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = toggle(selected, item.path)
                                    } else if (item.isDirectory) {
                                        state.navigate(Screen.Browser(item.path))
                                    } else {
                                        state.openItem(item, items)
                                    }
                                },
                                onLongClick = {
                                    selected = if (item.path in selected) selected - item.path
                                    else selected + item.path
                                },
                            )
                        }
                    }
                }
            }

            if (selected.isEmpty()) {
                FloatingActionButton(
                    onClick = { showNewSheet = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_folder))
                }
            }
        }

        val clipCount = state.remoteClipboard?.items?.size ?: state.clipboard?.items?.size ?: 0
        if (clipCount > 0 && selected.isEmpty()) {
            ClipboardBar(
                count = clipCount,
                onPaste = { state.paste(screen.path) },
                onClear = { state.clearClipboard() },
            )
        }
    }

    if (showSort) {
        SortSheet(
            spec = state.settings.sortSpec,
            viewMode = state.settings.viewMode,
            showHidden = state.settings.showHidden,
            onApply = { field, order -> state.setSort(field, order) },
            onToggleView = { state.toggleViewMode() },
            onToggleHidden = { state.toggleHidden() },
            onDismiss = { showSort = false },
        )
    }

    if (showNewSheet) {
        ActionSheet(
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.new_folder),
                    icon = Icons.Filled.CreateNewFolder,
                    onClick = { showCreateFolder = true },
                ),
                SheetAction(
                    label = stringResource(R.string.new_file),
                    icon = Icons.Filled.NoteAdd,
                    onClick = { showCreateFile = true },
                ),
                SheetAction(
                    label = stringResource(R.string.paste),
                    icon = Icons.Filled.FolderZip,
                    enabled = state.clipboard != null,
                    onClick = { state.paste(screen.path) },
                ),
            ),
            onDismiss = { showNewSheet = false },
        )
    }

    if (showMore) {
        ActionSheet(
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.select_all),
                    icon = Icons.Filled.SelectAll,
                    enabled = items.isNotEmpty(),
                    onClick = { selected = items.map { it.path }.toSet() },
                ),
                SheetAction(
                    label = stringResource(R.string.show_hidden),
                    icon = Icons.Filled.Info,
                    onClick = { state.toggleHidden() },
                ),
                SheetAction(
                    label = if (state.isBookmarked(screen.path)) stringResource(R.string.remove_bookmark)
                    else stringResource(R.string.add_bookmark),
                    icon = if (state.isBookmarked(screen.path)) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
                    onClick = {
                        if (state.isBookmarked(screen.path)) state.removeBookmark(screen.path)
                        else state.addBookmark(screen.path)
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.analyze_storage),
                    icon = Icons.Filled.Storage,
                    onClick = { state.navigate(Screen.Analyzer(screen.path)) },
                ),
                SheetAction(
                    label = stringResource(R.string.cleaner_title),
                    icon = Icons.Filled.CleaningServices,
                    onClick = { state.navigate(Screen.Cleaner(screen.path)) },
                ),
                SheetAction(
                    label = stringResource(R.string.refresh),
                    icon = Icons.Filled.Refresh,
                    onClick = { state.refresh() },
                ),
            ),
            onDismiss = { showMore = false },
        )
    }

    if (showSelectionMore) {
        val single = selectedItems.singleOrNull()
        ActionSheet(
            title = stringResource(R.string.more),
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.rename),
                    icon = Icons.Filled.DriveFileMove,
                    enabled = single?.path?.isNotBlank() == true,
                    onClick = { showRename = true },
                ),
                SheetAction(
                    label = stringResource(R.string.share),
                    icon = Icons.Filled.Share,
                    enabled = selectedItems.isNotEmpty(),
                    onClick = { state.share(selectedItems) },
                ),
                SheetAction(
                    label = stringResource(R.string.zip),
                    icon = Icons.Filled.FolderZip,
                    enabled = selectedItems.isNotEmpty(),
                    onClick = {
                        zipItems = selectedItems
                        showZipOptions = true
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.unzip),
                    icon = Icons.Filled.FolderOpen,
                    enabled = single?.isArchive == true,
                    onClick = { single?.let { state.unzip(it, screen.path) } },
                ),
                SheetAction(
                    label = stringResource(R.string.properties),
                    icon = Icons.Filled.Info,
                    enabled = single != null,
                    onClick = { propertiesItem = single },
                ),
                SheetAction(
                    label = stringResource(R.string.open_with),
                    icon = Icons.Filled.OpenInNew,
                    enabled = single != null && !single.isDirectory,
                    onClick = { single?.let { state.openWith(it) } },
                ),
                SheetAction(
                    label = stringResource(R.string.file_hash),
                    icon = Icons.Filled.Fingerprint,
                    enabled = single != null && !single.isDirectory && single.path.isNotBlank(),
                    onClick = { hashItem = single },
                ),
                SheetAction(
                    label = stringResource(R.string.marker_title),
                    icon = Icons.Filled.Tag,
                    enabled = single != null,
                    onClick = { markerItem = single },
                ),
                SheetAction(
                    label = stringResource(R.string.invert_selection),
                    icon = Icons.Filled.SelectAll,
                    onClick = {
                        selected = items.map { it.path }.toSet() - selected
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.delete),
                    icon = Icons.Filled.Delete,
                    destructive = true,
                    onClick = { showDelete = true },
                ),
            ),
            onDismiss = { showSelectionMore = false },
        )
    }

    if (showCreateFolder) {
        NameDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            existingNames = items.map { it.name }.toSet(),
            onDismiss = { showCreateFolder = false },
            onConfirm = { name -> state.createFolder(screen.path, name) },
        )
    }

    if (showCreateFile) {
        NameDialog(
            title = stringResource(R.string.new_file),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            existingNames = items.map { it.name }.toSet(),
            onDismiss = { showCreateFile = false },
            onConfirm = { name -> state.createFile(screen.path, name) },
        )
    }

    if (showRename) {
        val item = selectedItems.singleOrNull()
        if (item != null) {
            NameDialog(
                title = stringResource(R.string.rename),
                initial = item.name,
                confirmLabel = stringResource(R.string.ok),
                existingNames = items.map { it.name }.toSet() - item.name,
                onDismiss = { showRename = false },
                onConfirm = { name ->
                    state.rename(item, name)
                    selected = emptySet()
                },
            )
        }
    }

    if (showDelete) {
        DeleteConfirmDialog(
            count = selectedItems.size,
            useTrash = state.settings.useTrash,
            onDismiss = { showDelete = false },
            onConfirm = {
                state.delete(selectedItems)
                selected = emptySet()
            },
        )
    }

    propertiesItem?.let { item ->
        PropertiesDialog(item, state.repository) { propertiesItem = null }
    }

    hashItem?.let { item ->
        if (item.path.isNotBlank()) {
            HashDialog(
                name = item.name,
                totalBytes = item.size,
                openStream = { FileInputStream(File(item.path)) },
                onDismiss = { hashItem = null },
            )
        }
    }

    markerItem?.let { item ->
        MarkerDialog(
            title = item.name,
            initial = state.markerFor(item.path),
            onSave = { state.setMarker(item.path, it) },
            onDismiss = { markerItem = null },
        )
    }

    if (showZipOptions) {
        ZipOptionsDialog(
            defaultName = if (zipItems.size == 1) zipItems.first().name.substringBeforeLast('.') + ".zip" else "archive.zip",
            onConfirm = { name, password, splitMb ->
                state.zipWithOptions(zipItems, screen.path, name, password, splitMb)
            },
            onDismiss = { showZipOptions = false },
        )
    }
}

private fun toggle(selected: Set<String>, path: String): Set<String> =
    if (path in selected) selected - path else selected + path
