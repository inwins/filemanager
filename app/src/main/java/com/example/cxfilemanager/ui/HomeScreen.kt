package com.example.cxfilemanager.ui

import android.os.Environment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cxfilemanager.AppState
import com.example.cxfilemanager.R
import com.example.cxfilemanager.data.CategoryKind
import com.example.cxfilemanager.data.FileItem
import com.example.cxfilemanager.data.RecentEntry
import com.example.cxfilemanager.data.StorageItem
import com.example.cxfilemanager.util.Format
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: AppState) {
    var storages by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    LaunchedEffect(state.refreshTick, state.permissionGranted) {
        storages = withContext(Dispatchers.IO) { state.repository.storageVolumes() }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
            actions = {
                IconButton(onClick = { state.navigate(Screen.Search(state.repository.defaultRoot())) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(onClick = { state.goTab(4) }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings))
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    storages.forEach { storage ->
                        StorageCard(storage) { state.navigate(Screen.Browser(storage.path)) }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.quick_access)) }
            item { QuickAccess(state) }

            item { SectionHeader(stringResource(R.string.categories)) }
            item {
                CategoryGrid(
                    onCategory = { state.navigate(Screen.Category(it)) },
                    onApps = { state.navigate(Screen.Apps) },
                )
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.bookmarks),
                    action = if (state.bookmarks.isEmpty()) null else stringResource(R.string.clear),
                    onAction = { state.clearBookmarks() },
                )
            }
            if (state.bookmarks.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_bookmarks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            } else {
                items(state.bookmarks, key = { it.path }) { bookmark ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { state.navigate(Screen.Browser(bookmark.path)) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bookmark.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                bookmark.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { state.removeBookmark(bookmark.path) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.recent),
                    action = if (state.recents.isEmpty()) null else stringResource(R.string.clear),
                    onAction = { state.clearRecents() },
                )
            }
            if (state.recents.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_recent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            } else {
                items(state.recents.take(10), key = { it.path + it.openedAt }) { entry ->
                    RecentRow(state, entry)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAccess(state: AppState) {
    val root = Environment.getExternalStorageDirectory()
    val dirs = listOf(
        Triple(stringResource(R.string.quick_downloads), Icons.Filled.Download, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)),
        Triple(stringResource(R.string.quick_dcim), Icons.Filled.PhotoLibrary, File(root, "DCIM")),
        Triple(stringResource(R.string.quick_pictures), Icons.Filled.Image, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)),
        Triple(stringResource(R.string.quick_documents), Icons.Filled.Description, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)),
        Triple(stringResource(R.string.quick_music), Icons.Filled.MusicNote, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)),
        Triple(stringResource(R.string.quick_movies), Icons.Filled.Movie, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)),
        Triple(stringResource(R.string.quick_android), Icons.Filled.Android, File(root, "Android")),
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dirs.filter { it.third.exists() }.forEach { (label, icon, dir) ->
            QuickAccessTile(label = label, icon = icon, onClick = { state.navigate(Screen.Browser(dir.absolutePath)) })
        }
        if (state.rootEnabled && state.rootAvailable) {
            QuickAccessTile(
                label = stringResource(R.string.root_browse),
                icon = Icons.Filled.Security,
                onClick = { state.navigate(Screen.Browser("/")) },
            )
        }
    }
}

@Composable
private fun RecentRow(state: AppState, entry: RecentEntry) {
    val file = remember(entry.path) { File(entry.path) }
    if (!file.exists()) return
    val item = remember(entry.path, file.length(), file.lastModified()) {
        FileItem(
            path = file.absolutePath,
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isDirectory) 0 else file.length(),
            lastModified = file.lastModified(),
            mime = com.example.cxfilemanager.util.MimeTypes.fromName(file.name),
        )
    }
    FileRow(
        item = item,
        selected = false,
        selectionMode = false,
        onClick = { state.openItem(item) },
        onLongClick = { },
        subtitle = Format.date(entry.openedAt),
        showPath = true,
    )
}
