package io.github.inwins.filebrowser.ui

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.inwins.filebrowser.AppState
import io.github.inwins.filebrowser.R
import io.github.inwins.filebrowser.data.FileItem
import io.github.inwins.filebrowser.data.FileOps
import io.github.inwins.filebrowser.lan.LanClient
import io.github.inwins.filebrowser.lan.LanEntry
import io.github.inwins.filebrowser.lan.LanPeer
import io.github.inwins.filebrowser.util.Format
import io.github.inwins.filebrowser.util.MimeTypes
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PeerTask(
    val title: String,
    val detail: String,
    val progress: Float?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanPeerScreen(state: AppState, screen: Screen.LanPeer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val peer = remember(screen) {
        LanPeer(
            id = screen.host + ":" + screen.port,
            name = screen.name,
            host = screen.host,
            port = screen.port,
            token = screen.token,
        )
    }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<LanEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var task by remember { mutableStateOf<PeerTask?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }

    fun showError(message: String) {
        state.message = message
    }

    val uploadUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var done = 0
                uris.forEachIndexed { index, uri ->
                    val name = queryName(context, uri) ?: "file"
                    val size = querySize(context, uri)
                    val title = context.getString(R.string.lan_peer_uploading, index + 1, uris.size)
                    task = PeerTask(title, name, 0f)
                    try {
                        if (size >= 0) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                withContext(Dispatchers.IO) {
                                    LanClient.upload(peer, path, name, size, input) { sent, total ->
                                        task = PeerTask(
                                            title,
                                            name,
                                            if (total > 0) (sent.toDouble() / total).toFloat().coerceIn(0f, 1f) else null,
                                        )
                                    }
                                }
                            } ?: throw IOException("Cannot open file")
                        } else {
                            val temp = File(context.cacheDir, "lan_upload_${System.nanoTime()}")
                            try {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    temp.outputStream().use { output -> input.copyTo(output) }
                                } ?: throw IOException("Cannot open file")
                                withContext(Dispatchers.IO) {
                                    temp.inputStream().use { input ->
                                        LanClient.upload(peer, path, name, temp.length(), input) { sent, total ->
                                            task = PeerTask(
                                                title,
                                                name,
                                                if (total > 0) (sent.toDouble() / total).toFloat().coerceIn(0f, 1f) else null,
                                            )
                                        }
                                    }
                                }
                            } finally {
                                temp.delete()
                            }
                        }
                        done++
                    } catch (error: Exception) {
                        showError(
                            context.getString(
                                R.string.lan_peer_upload_failed,
                                error.message ?: error.javaClass.simpleName,
                            )
                        )
                        return@launch
                    }
                }
                task = null
                if (done > 0) {
                    showError(context.getString(R.string.lan_peer_upload_done, done))
                    refresh++
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> uploadUris(uris) }

    LaunchedEffect(path, refresh) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { LanClient.list(peer, path) }
        }
        result.onSuccess {
            entries = it.sortedWith(
                compareByDescending<LanEntry> { it.directory }.thenBy { it.name.lowercase() }
            )
            loading = false
        }.onFailure {
            error = it.message ?: it.javaClass.simpleName
            loading = false
        }
    }

    fun playAudio(entry: LanEntry) {
        val cacheKey = "lan:${peer.id}:${entry.path}:${entry.size}:${entry.modified}"
        val media = FileItem(
            path = "",
            name = entry.name,
            isDirectory = false,
            size = entry.size,
            lastModified = entry.modified,
            mime = MimeTypes.fromName(entry.name),
            remote = true,
            streamUrl = LanClient.fileUrl(peer, entry.path),
            streamCacheKey = cacheKey,
        )
        state.playAudio(media, listOf(media))
        state.launchBackground {
            val base = entry.name.substringBeforeLast('.')
            val parent = if (entry.path.contains('/')) entry.path.substringBeforeLast('/') else ""
            val lrc = withContext(Dispatchers.IO) {
                runCatching {
                    LanClient.list(peer, parent).firstOrNull {
                        !it.directory &&
                            it.name.endsWith(".lrc", ignoreCase = true) &&
                            it.name.substringBeforeLast('.').equals(base, ignoreCase = true)
                    }
                }.getOrNull()
            } ?: return@launchBackground
            val directory = File(
                File(context.cacheDir, "lan_lyrics/${peer.id.hashCode()}"),
                abs(entry.path.hashCode()).toString(),
            ).apply { mkdirs() }
            val destination = File(directory, "$base.lrc")
            val ok = withContext(Dispatchers.IO) {
                runCatching { LanClient.download(peer, lrc.path, destination) }.isSuccess
            }
            if (ok) state.audioPlayer.attachLyrics(cacheKey, destination.path)
        }
    }

    fun download(entry: LanEntry) {
        scope.launch {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "LanTransferReceived",
            ).apply { mkdirs() }
            val destination = FileOps.uniqueFile(File(directory, entry.name))
            task = PeerTask(
                context.getString(R.string.lan_peer_downloading),
                entry.name,
                0f,
            )
            try {
                withContext(Dispatchers.IO) {
                    LanClient.download(peer, entry.path, destination) { received, total ->
                        task = PeerTask(
                            context.getString(R.string.lan_peer_downloading),
                            entry.name,
                            if (total > 0) (received.toDouble() / total).toFloat().coerceIn(0f, 1f) else null,
                        )
                    }
                }
                runCatching {
                    MediaScannerConnection.scanFile(context, arrayOf(destination.path), null, null)
                }
                showError(context.getString(R.string.lan_peer_saved, destination.path))
            } catch (error: Exception) {
                destination.delete()
                showError(
                    context.getString(
                        R.string.lan_peer_download_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                )
            } finally {
                task = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = {
                Column {
                    Text(screen.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.lan_transfer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.UploadFile, contentDescription = stringResource(R.string.lan_peer_upload))
                }
                IconButton(onClick = { showNewFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.lan_peer_new_folder))
                }
                IconButton(onClick = { refresh++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.lan_peer_refresh))
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    path = if (path.contains('/')) path.substringBeforeLast('/') else ""
                },
                enabled = path.isNotBlank(),
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.lan_peer_up))
            }
            Text(
                "/" + path,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.lan_peer_download_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.lan_peer_load_failed), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(6.dp))
                Text(error ?: "", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { refresh++ }) {
                    Text(stringResource(R.string.lan_peer_refresh))
                }
            }
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.lan_peer_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.path.ifBlank { it.name } }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                when {
                                    entry.directory -> path = entry.path
                                    entry.isAudio() -> playAudio(entry)
                                    else -> download(entry)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (entry.directory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = if (entry.directory) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (entry.directory) stringResource(R.string.folder)
                                else "${Format.size(entry.size)} · ${Format.shortDate(entry.modified)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        NameDialog(
            title = stringResource(R.string.lan_peer_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            existingNames = entries.map { it.name }.toSet(),
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { LanClient.mkdir(peer, path, name) }
                        showError(context.getString(R.string.lan_peer_folder_created))
                        refresh++
                    } catch (error: Exception) {
                        showError(
                            context.getString(
                                R.string.lan_peer_create_failed,
                                error.message ?: error.javaClass.simpleName,
                            )
                        )
                    }
                }
            },
        )
    }

    task?.let { current ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(current.title) },
            text = {
                Column {
                    Text(current.detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    if (current.progress != null) {
                        LinearProgressIndicator(
                            progress = { current.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.lan_peer_progress, (current.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private fun queryName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
}

private fun querySize(context: Context, uri: Uri): Long {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
    }.getOrDefault(-1L)
}

private fun LanEntry.isAudio(): Boolean {
    val mime = MimeTypes.fromName(name)
    return mime?.startsWith("audio/") == true ||
        name.substringAfterLast('.', "").lowercase() in FileItem.AUDIO_EXTS
}
