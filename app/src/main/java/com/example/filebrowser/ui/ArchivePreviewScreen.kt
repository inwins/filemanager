package com.example.filebrowser.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.filebrowser.AppState
import com.example.filebrowser.R
import com.example.filebrowser.data.ArchiveEntry
import com.example.filebrowser.data.ArchiveOps
import com.example.filebrowser.data.FileItem
import com.example.filebrowser.data.FileOps
import com.example.filebrowser.util.Format
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePreviewScreen(state: AppState, path: String) {
    val file = remember(path) { File(path) }
    val scope = rememberCoroutineScope()
    var entries by remember(path) { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var loading by remember(path) { mutableStateOf(true) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var password by remember(path) { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var showPassword by remember { mutableStateOf(false) }
    var showExtract by remember { mutableStateOf(false) }
    var extractPassword by remember { mutableStateOf("") }
    var extracting by remember { mutableStateOf(false) }
    var extractDone by remember { mutableLongStateOf(0L) }
    var extractTotal by remember { mutableLongStateOf(0L) }
    var cancelRequested by remember { mutableStateOf(false) }
    var extractTarget by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(path, password, reload) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { ArchiveOps.list(file, password) }
        }
        result.onSuccess {
            entries = it
            loading = false
        }.onFailure {
            error = it.message ?: state.appContext.getString(R.string.operation_failed)
            entries = emptyList()
            loading = false
        }
    }

    fun startExtract(target: File, pwd: String) {
        extractTarget = target
        extractPassword = pwd
        password = pwd
        extracting = true
        cancelRequested = false
        extractDone = 0L
        extractTotal = entries.sumOf { it.size }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ArchiveOps.extract(
                        file = file,
                        destDir = target,
                        password = pwd,
                        onProgress = { extractDone = it },
                        shouldContinue = { !cancelRequested },
                    )
                }.getOrDefault(false)
            }
            val cancelled = cancelRequested
            extracting = false
            cancelRequested = false
            if (ok) {
                state.showMessage(state.appContext.getString(R.string.archive_extracted, target.name))
            } else {
                state.showMessage(
                    state.appContext.getString(
                        if (cancelled) R.string.operation_cancelled else R.string.archive_extract_failed
                    )
                )
            }
            reload++
        }
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
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.archive_entries, entries.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { showPassword = true }) {
                    Icon(Icons.Filled.LockOpen, contentDescription = stringResource(R.string.archive_password))
                }
                IconButton(onClick = {
                    extractPassword = password
                    showExtract = true
                }) {
                    Icon(Icons.Filled.FolderZip, contentDescription = stringResource(R.string.archive_extract_all))
                }
            },
        )

        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, maxLines = 5)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showPassword = true }) {
                        Text(stringResource(R.string.archive_password))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { reload++ }) {
                        Text(stringResource(R.string.network_retry))
                    }
                }
                entries.isEmpty() -> EmptyState(
                    Icons.Filled.FolderZip,
                    stringResource(R.string.archive_empty),
                    Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(entries, key = { it.name }) { entry ->
                        val item = FileItem(
                            path = entry.name,
                            name = entry.name,
                            isDirectory = entry.isDirectory,
                            size = entry.size,
                            lastModified = 0L,
                        )
                        FileRow(
                            item = item,
                            selected = false,
                            selectionMode = false,
                            showPath = false,
                            onClick = {},
                            onLongClick = {},
                            subtitle = if (entry.isDirectory) stringResource(R.string.folder)
                            else Format.size(entry.size),
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                extractPassword = password
                showExtract = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = !loading && !extracting && entries.isNotEmpty(),
        ) {
            Text(stringResource(R.string.archive_extract_all))
        }
    }

    if (showPassword) {
        PasswordDialog(
            title = stringResource(R.string.archive_password),
            value = password,
            onDismiss = { showPassword = false },
            onConfirm = {
                password = it
                showPassword = false
                reload++
            },
        )
    }

    if (showExtract) {
        AlertDialog(
            onDismissRequest = { showExtract = false },
            title = { Text(stringResource(R.string.archive_extract_all)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = extractPassword,
                        onValueChange = { extractPassword = it },
                        label = { Text(stringResource(R.string.archive_password_optional)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.archive_extract_hint,
                            FileOps.uniqueFile(
                                File(file.parentFile, file.name.substringBeforeLast('.'))
                            ).name,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = FileOps.uniqueFile(File(file.parentFile, file.name.substringBeforeLast('.')))
                    showExtract = false
                    startExtract(target, extractPassword)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtract = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (extracting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.archive_extracting)) },
            text = {
                Column {
                    val fraction = if (extractTotal > 0) {
                        (extractDone.toDouble() / extractTotal).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    if (extractTotal > 0) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Format.size(extractDone)} / ${Format.size(extractTotal)} · ${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        extractTarget?.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelRequested = true
                }) {
                    Text(stringResource(R.string.cancel_operation))
                }
            },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.archive_password_optional)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
