package io.github.inwins.filebrowser.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.inwins.filebrowser.AppState
import io.github.inwins.filebrowser.R
import io.github.inwins.filebrowser.data.FileItem
import io.github.inwins.filebrowser.data.FileOps
import io.github.inwins.filebrowser.util.Format
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(state: AppState, path: String) {
    var entries by remember { mutableStateOf<List<Pair<FileItem, Long>>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var scanned by remember { mutableStateOf(0) }

    LaunchedEffect(path, state.refreshTick, state.settings.showHidden) {
        loading = true
        entries = emptyList()
        total = 0
        scanned = 0
        val job = coroutineContext[Job]
        val result = withContext(Dispatchers.IO) {
            val dir = File(path)
            val children = dir.listFiles()
                ?.filter { state.settings.showHidden || !it.name.startsWith(".") }
                .orEmpty()
            val list = children.map { child ->
                val size = if (child.isDirectory) {
                    FileOps.folderSize(
                        child,
                        onFile = { scanned++ },
                        shouldContinue = { job?.isActive == true },
                    )
                } else {
                    scanned++
                    child.length()
                }
                state.repository.toItem(child) to size
            }
            list to list.sumOf { it.second }
        }
        entries = result.first.sortedByDescending { it.second }
        total = result.second
        loading = false
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
                    Text(stringResource(R.string.analyzer_title))
                    Text(
                        path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                IconButton(onClick = { state.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            },
        )

        if (loading) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.analyzing_files, scanned),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.analyzer_total, Format.size(total)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.items_count, entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(Modifier.weight(1f)) {
            if (!loading && entries.isEmpty()) {
                EmptyState(Icons.Filled.FolderOpen, stringResource(R.string.analyzer_empty), Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(entries, key = { it.first.path }) { (item, size) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.isDirectory) state.navigate(Screen.Analyzer(item.path))
                                    else state.openItem(item)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FileLeadingIcon(item, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        Format.size(size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (total <= 0) 0f else (size.toDouble() / total).toFloat().coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (item.isDirectory) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
