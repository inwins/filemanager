package com.example.filebrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.filebrowser.AppState
import com.example.filebrowser.R
import com.example.filebrowser.data.CacheEntry
import com.example.filebrowser.data.DuplicateGroup
import com.example.filebrowser.data.FileItem
import com.example.filebrowser.data.LargeFile
import com.example.filebrowser.data.StorageCleaner
import com.example.filebrowser.util.Format
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private enum class CleanerTab { DUPLICATES, LARGE, CACHE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(state: AppState, root: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(CleanerTab.DUPLICATES) }
    var duplicates by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var largeFiles by remember { mutableStateOf<List<LargeFile>>(emptyList()) }
    var caches by remember { mutableStateOf<List<CacheEntry>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var scanned by remember { mutableIntStateOf(0) }
    var scanTick by remember { mutableIntStateOf(0) }
    var confirmDeleteDuplicates by remember { mutableStateOf(false) }
    var deleteLarge by remember { mutableStateOf<LargeFile?>(null) }
    var cleaning by remember { mutableStateOf(false) }
    var cleaningFreed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(tab, root, scanTick, state.refreshTick) {
        scanning = true
        scanned = 0
        val job = coroutineContext[Job]
        when (tab) {
            CleanerTab.DUPLICATES -> {
                duplicates = withContext(Dispatchers.IO) {
                    StorageCleaner.findDuplicates(
                        File(root),
                        onProgress = { scanned = it },
                        shouldContinue = { job?.isActive == true },
                    )
                }
            }
            CleanerTab.LARGE -> {
                largeFiles = withContext(Dispatchers.IO) {
                    StorageCleaner.findLargeFiles(
                        File(root),
                        onProgress = { scanned = it },
                        shouldContinue = { job?.isActive == true },
                    )
                }
            }
            CleanerTab.CACHE -> {
                caches = withContext(Dispatchers.IO) { StorageCleaner.cacheEntries(context) }
            }
        }
        scanning = false
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
                    Text(stringResource(R.string.cleaner_title))
                    Text(
                        root,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                IconButton(onClick = { scanTick++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tab == CleanerTab.DUPLICATES,
                onClick = { tab = CleanerTab.DUPLICATES },
                label = { Text(stringResource(R.string.cleaner_duplicates)) },
            )
            FilterChip(
                selected = tab == CleanerTab.LARGE,
                onClick = { tab = CleanerTab.LARGE },
                label = { Text(stringResource(R.string.cleaner_large)) },
            )
            FilterChip(
                selected = tab == CleanerTab.CACHE,
                onClick = { tab = CleanerTab.CACHE },
                label = { Text(stringResource(R.string.cleaner_cache)) },
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                scanning -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.cleaner_scanning, scanned))
                }
                tab == CleanerTab.DUPLICATES -> DuplicatesPane(
                    groups = duplicates,
                    onDelete = { confirmDeleteDuplicates = true },
                )
                tab == CleanerTab.LARGE -> LargePane(
                    files = largeFiles,
                    onOpen = { state.openItem(it.file.fileItem()) },
                    onDelete = { deleteLarge = it },
                )
                else -> CachePane(
                    entries = caches,
                    onClean = {
                        scope.launch {
                            cleaning = true
                            cleaningFreed = withContext(Dispatchers.IO) {
                                StorageCleaner.cleanCaches(context)
                            }
                            cleaning = false
                            state.showMessage(
                                context.getString(R.string.cleaner_cleaned, Format.size(cleaningFreed))
                            )
                            scanTick++
                        }
                    },
                )
            }
        }
    }

    if (confirmDeleteDuplicates) {
        val deleteFiles = duplicates.flatMap { group -> group.files.drop(1) }
        val reclaimable = duplicates.sumOf { it.reclaimable }
        AlertDialog(
            onDismissRequest = { confirmDeleteDuplicates = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cleaner_delete_duplicates_message,
                        deleteFiles.size,
                        Format.size(reclaimable),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteDuplicates = false
                    state.delete(deleteFiles.map { it.fileItem() })
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDuplicates = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    deleteLarge?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteLarge = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(item.file.absolutePath) },
            confirmButton = {
                TextButton(onClick = {
                    state.delete(listOf(item.file.fileItem()))
                    deleteLarge = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteLarge = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (cleaning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.cleaner_cleaning)) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }
}

@Composable
private fun DuplicatesPane(groups: List<DuplicateGroup>, onDelete: () -> Unit) {
    if (groups.isEmpty()) {
        EmptyState(Icons.Filled.Warning, stringResource(R.string.cleaner_no_duplicates), Modifier.fillMaxSize())
        return
    }
    val reclaimable = groups.sumOf { it.reclaimable }
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.cleaner_duplicates_found, groups.size, Format.size(reclaimable)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(groups, key = { it.files.first().absolutePath }) { group ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            group.files.first().name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                R.string.cleaner_duplicate_group,
                                group.files.size,
                                Format.size(group.size),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.cleaner_delete_duplicates))
        }
    }
}

@Composable
private fun LargePane(
    files: List<LargeFile>,
    onOpen: (LargeFile) -> Unit,
    onDelete: (LargeFile) -> Unit,
) {
    if (files.isEmpty()) {
        EmptyState(Icons.Filled.Warning, stringResource(R.string.cleaner_no_large), Modifier.fillMaxSize())
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(files, key = { it.file.absolutePath }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileLeadingIcon(item.file.fileItem(), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        item.file.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(Format.size(item.size), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun CachePane(entries: List<CacheEntry>, onClean: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            EmptyState(Icons.Filled.Warning, stringResource(R.string.cleaner_no_cache), Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.dir.absolutePath }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entry.dir.absolutePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Text(Format.size(entry.size), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Button(
            onClick = onClean,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = entries.isNotEmpty(),
        ) {
            Text(stringResource(R.string.cleaner_clean_cache))
        }
    }
}

private fun File.fileItem(): FileItem = FileItem(
    path = absolutePath,
    name = name,
    isDirectory = isDirectory,
    size = if (isDirectory) 0L else length(),
    lastModified = lastModified(),
    mime = com.example.filebrowser.util.MimeTypes.fromName(name),
)
