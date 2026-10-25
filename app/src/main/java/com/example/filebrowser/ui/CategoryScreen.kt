package com.example.filebrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.filebrowser.AppState
import com.example.filebrowser.R
import com.example.filebrowser.data.AppItem
import com.example.filebrowser.data.CategoryKind
import com.example.filebrowser.data.ClipboardOp
import com.example.filebrowser.data.FileItem
import com.example.filebrowser.data.FileOps
import com.example.filebrowser.data.SortField
import com.example.filebrowser.data.SortOrder
import com.example.filebrowser.data.SortSpec
import com.example.filebrowser.data.ViewMode
import com.example.filebrowser.util.Format
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_categories)) },
            actions = {
                IconButton(onClick = { state.navigate(Screen.Search(state.repository.defaultRoot())) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                }
            },
        )
        CategoryGrid(
            onCategory = { state.navigate(Screen.Category(it)) },
            onApps = { state.navigate(Screen.Apps) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun categoryTitle(kind: CategoryKind): String = when (kind) {
    CategoryKind.IMAGES -> stringResource(R.string.cat_images)
    CategoryKind.VIDEOS -> stringResource(R.string.cat_videos)
    CategoryKind.AUDIO -> stringResource(R.string.cat_audio)
    CategoryKind.DOCUMENTS -> stringResource(R.string.cat_documents)
    CategoryKind.DOWNLOADS -> stringResource(R.string.cat_downloads)
    CategoryKind.APKS -> stringResource(R.string.cat_apks)
    CategoryKind.ARCHIVES -> stringResource(R.string.cat_archives)
    CategoryKind.RECENT -> stringResource(R.string.cat_recent)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(state: AppState, kind: CategoryKind) {
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSelectionMore by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var propertiesItem by remember { mutableStateOf<FileItem?>(null) }
    var archiveItem by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(kind, state.settings.showHidden, state.refreshTick) {
        loading = true
        val sortSpec = when (kind) {
            CategoryKind.RECENT, CategoryKind.IMAGES, CategoryKind.VIDEOS ->
                SortSpec(SortField.DATE, SortOrder.DESC)
            else -> state.settings.sortSpec
        }
        items = withContext(Dispatchers.IO) {
            FileOps.sort(
                state.repository.queryCategory(kind, state.settings.showHidden),
                sortSpec,
            )
        }
        loading = false
    }

    LaunchedEffect(items) {
        selected = selected.intersect(items.map { it.path.ifBlank { it.contentUri ?: it.name } }.toSet())
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val keyOf: (FileItem) -> String = { it.path.ifBlank { it.contentUri ?: it.name } }
    val selectedItems = items.filter { keyOf(it) in selected }
    val grid = kind == CategoryKind.IMAGES || kind == CategoryKind.VIDEOS

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
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
                    Column {
                        Text(categoryTitle(kind))
                        Text(
                            stringResource(R.string.items_count, items.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { state.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                items.isEmpty() -> EmptyState(
                    Icons.Filled.FolderOpen,
                    stringResource(R.string.empty_folder),
                    Modifier.fillMaxSize(),
                )
                grid -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(items, key = { keyOf(it) }) { item ->
                            FileGridItem(
                                item = item,
                                selected = keyOf(item) in selected,
                                selectionMode = selected.isNotEmpty(),
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = toggleKey(selected, keyOf(item))
                                    } else if (item.isArchive) {
                                        archiveItem = item
                                    } else {
                                        state.openItem(item, items)
                                    }
                                },
                                onLongClick = { selected = toggleKey(selected, keyOf(item)) },
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(items, key = { keyOf(it) }) { item ->
                            FileRow(
                                item = item,
                                selected = keyOf(item) in selected,
                                selectionMode = selected.isNotEmpty(),
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = toggleKey(selected, keyOf(item))
                                    } else if (item.isArchive && item.path.isNotBlank()) {
                                        archiveItem = item
                                    } else {
                                        state.openItem(item, items)
                                    }
                                },
                                onLongClick = { selected = toggleKey(selected, keyOf(item)) },
                            )
                        }
                    }
                }
            }
        }
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
                    enabled = selectedItems.any { it.path.isNotBlank() },
                    onClick = {
                        val dir = selectedItems.firstOrNull()?.path?.let { File(it).parent }
                        if (dir != null) state.zip(selectedItems, dir)
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.unzip),
                    icon = Icons.Filled.FolderOpen,
                    enabled = single?.isArchive == true && single.path.isNotBlank(),
                    onClick = {
                        single?.let { item ->
                            state.unzip(item, File(item.path).parent ?: state.repository.defaultRoot())
                        }
                    },
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
                    label = stringResource(R.string.invert_selection),
                    icon = Icons.Filled.SelectAll,
                    onClick = {
                        selected = items.map(keyOf).toSet() - selected
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

    if (showRename) {
        val item = selectedItems.singleOrNull()
        if (item != null && item.path.isNotBlank()) {
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

    archiveItem?.let { item ->
        ActionSheet(
            title = item.name,
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.unzip),
                    icon = Icons.Filled.FolderZip,
                    enabled = item.path.isNotBlank(),
                    onClick = {
                        val parent = File(item.path).parent ?: state.repository.defaultRoot()
                        state.unzip(item, parent)
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.open_with),
                    icon = Icons.Filled.OpenInNew,
                    onClick = { state.openWith(item) },
                ),
                SheetAction(
                    label = stringResource(R.string.properties),
                    icon = Icons.Filled.Info,
                    onClick = { propertiesItem = item },
                ),
            ),
            onDismiss = { archiveItem = null },
        )
    }
}

private fun toggleKey(selected: Set<String>, key: String): Set<String> =
    if (key in selected) selected - key else selected + key

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(state: AppState) {
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showOptions by remember { mutableStateOf(false) }
    var sheetApp by remember { mutableStateOf<AppItem?>(null) }

    LaunchedEffect(state.includeSystemApps, state.refreshTick) {
        loading = true
        apps = withContext(Dispatchers.IO) { state.repository.installedApps(state.includeSystemApps) }
        loading = false
    }

    val filtered = apps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = {
                Column {
                    Text(stringResource(R.string.cat_apps))
                    Text(
                        stringResource(R.string.app_count, apps.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { state.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
                IconButton(onClick = { showOptions = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                }
            },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.app_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                filtered.isEmpty() -> EmptyState(
                    Icons.Filled.Android,
                    stringResource(R.string.no_apps),
                    Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            onClick = { state.launchApp(app) },
                            onMore = { sheetApp = app },
                        )
                    }
                }
            }
        }
    }

    if (showOptions) {
        ActionSheet(
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.system_apps),
                    icon = Icons.Filled.Apps,
                    onClick = { state.includeSystemApps = !state.includeSystemApps },
                ),
            ),
            onDismiss = { showOptions = false },
        )
    }

    sheetApp?.let { app ->
        ActionSheet(
            title = app.label,
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.open),
                    icon = Icons.Filled.OpenInNew,
                    onClick = { state.launchApp(app) },
                ),
                SheetAction(
                    label = stringResource(R.string.app_info),
                    icon = Icons.Filled.Info,
                    onClick = { state.showAppInfo(app) },
                ),
                SheetAction(
                    label = stringResource(R.string.share_apk),
                    icon = Icons.Filled.Share,
                    onClick = { state.shareApk(app) },
                ),
                SheetAction(
                    label = stringResource(R.string.uninstall),
                    icon = Icons.Filled.Delete,
                    enabled = !app.isSystem,
                    destructive = true,
                    onClick = { state.uninstallApp(app) },
                ),
            ),
            onDismiss = { sheetApp = null },
        )
    }
}

@Composable
private fun AppRow(app: AppItem, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${app.packageName} · ${Format.size(app.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
        }
    }
}
