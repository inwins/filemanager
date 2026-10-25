package com.example.filebrowser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.filebrowser.AppState
import com.example.filebrowser.R
import com.example.filebrowser.data.FileItem
import com.example.filebrowser.data.RemoteEntry
import com.example.filebrowser.data.RemoteCancelledException
import com.example.filebrowser.data.RemoteFileSystem
import com.example.filebrowser.data.RemoteFileSystems
import com.example.filebrowser.data.RemoteTransfer
import com.example.filebrowser.data.RemoteProfile
import com.example.filebrowser.data.RemoteAuthMethod
import com.example.filebrowser.data.RemoteProtocol
import com.example.filebrowser.stream.RemoteStreamServer
import com.example.filebrowser.stream.RemoteStreamService
import com.example.filebrowser.stream.StreamRegistry
import com.example.filebrowser.util.MimeTypes
import com.example.filebrowser.util.Format
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class RemoteTask(
    val title: String,
    val total: Long,
    val done: Long,
    val detail: String,
    val cancellable: Boolean,
) {
    val fraction: Float?
        get() = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(state: AppState) {
    var showDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<RemoteProfile?>(null) }
    var menuProfile by remember { mutableStateOf<RemoteProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<RemoteProfile?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_network)) },
            actions = {
                IconButton(onClick = {
                    editingProfile = null
                    showDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.network_add))
                }
            },
        )
        Box(Modifier.weight(1f)) {
            if (state.remoteProfiles.isEmpty()) {
                EmptyState(
                    Icons.Filled.Cloud,
                    stringResource(R.string.network_empty),
                    Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                ) {
                    items(state.remoteProfiles, key = { it.id }) { profile ->
                        RemoteProfileCard(
                            profile = profile,
                            onClick = {
                                state.navigate(
                                    Screen.RemoteBrowser(profile.id, profile.rootPath.ifBlank { "/" })
                                )
                            },
                            onMore = { menuProfile = profile },
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = {
                    editingProfile = null
                    showDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.network_add)) },
            )
        }
    }

    if (showDialog) {
        RemoteProfileDialog(
            initial = editingProfile,
            onSave = { state.saveRemoteProfile(it) },
            onDismiss = { showDialog = false },
        )
    }

    menuProfile?.let { profile ->
        ActionSheet(
            title = profile.name,
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.network_edit),
                    icon = Icons.Filled.Edit,
                    onClick = {
                        editingProfile = profile
                        showDialog = true
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.network_delete),
                    icon = Icons.Filled.Delete,
                    destructive = true,
                    onClick = { deleteProfile = profile },
                ),
            ),
            onDismiss = { menuProfile = null },
        )
    }

    deleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfile = null },
            title = { Text(stringResource(R.string.network_delete)) },
            text = { Text(stringResource(R.string.network_delete_confirm, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteRemoteProfile(profile.id)
                    deleteProfile = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteProfile = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RemoteProfileCard(profile: RemoteProfile, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (profile.protocol == RemoteProtocol.SMB) Icons.Filled.FolderShared else Icons.Filled.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${profile.protocol} · ${profile.host}:${profile.port}" +
                    if (profile.protocol == RemoteProtocol.SMB) "/${profile.share}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                profile.rootPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteProfileDialog(
    initial: RemoteProfile?,
    onSave: (RemoteProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var protocol by remember(initial) { mutableStateOf(initial?.protocol ?: RemoteProtocol.SMB) }
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember(initial) { mutableStateOf(initial?.host.orEmpty()) }
    var port by remember(initial) { mutableStateOf((initial?.port ?: 445).toString()) }
    var username by remember(initial) { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember(initial) { mutableStateOf(initial?.password.orEmpty()) }
    var domain by remember(initial) { mutableStateOf(initial?.domain.orEmpty()) }
    var share by remember(initial) { mutableStateOf(initial?.share.orEmpty()) }
    var rootPath by remember(initial) { mutableStateOf(initial?.rootPath ?: "/") }
    var authMethod by remember(initial) { mutableStateOf(initial?.authMethod ?: RemoteAuthMethod.PASSWORD) }
    var privateKeyPath by remember(initial) { mutableStateOf(initial?.privateKeyPath.orEmpty()) }
    var keyPassphrase by remember(initial) { mutableStateOf(initial?.keyPassphrase.orEmpty()) }
    var hostKeyFingerprint by remember(initial) { mutableStateOf(initial?.hostKeyFingerprint.orEmpty()) }
    var guest by remember(initial) { mutableStateOf(initial?.guest == true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val hostRequired = stringResource(R.string.network_host_required)
    val invalidPort = stringResource(R.string.network_invalid_port)
    val shareRequired = stringResource(R.string.network_share_required)
    val keyRequired = stringResource(R.string.network_key_required)
    val keyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: "private_key"
            val dir = File(context.filesDir, "remote_keys").apply { mkdirs() }
            val destination = File(
                dir,
                displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "private_key" },
            )
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }.getOrDefault(false)
            if (copied) privateKeyPath = destination.absolutePath
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(if (initial == null) R.string.network_add else R.string.network_edit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteProtocol.entries.forEach { value ->
                    FilterChip(
                        selected = protocol == value,
                        onClick = {
                            if (protocol != value) {
                                if (port == "445" || port == "22") {
                                    port = if (value == RemoteProtocol.SMB) "445" else "22"
                                }
                                protocol = value
                            }
                        },
                        label = { Text(value.name) },
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.network_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.network_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.network_port)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (protocol == RemoteProtocol.SMB) {
                OutlinedTextField(
                    value = share,
                    onValueChange = { share = it },
                    label = { Text(stringResource(R.string.network_share)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text(stringResource(R.string.network_domain)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (protocol == RemoteProtocol.SFTP) {
                Text(
                    stringResource(R.string.network_auth),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteAuthMethod.entries.forEach { method ->
                        FilterChip(
                            selected = authMethod == method,
                            onClick = { authMethod = method },
                            label = {
                                Text(
                                    when (method) {
                                        RemoteAuthMethod.PASSWORD -> stringResource(R.string.network_password)
                                        RemoteAuthMethod.KEY -> stringResource(R.string.network_key)
                                    }
                                )
                            },
                        )
                    }
                }
            }
            if (protocol == RemoteProtocol.SMB) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.network_guest),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = guest, onCheckedChange = { guest = it })
                }
            }
            if (!(protocol == RemoteProtocol.SMB && guest)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.network_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (protocol == RemoteProtocol.SFTP && authMethod == RemoteAuthMethod.KEY) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (privateKeyPath.isBlank()) stringResource(R.string.network_no_key)
                            else File(privateKeyPath).name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { keyLauncher.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.network_select_key))
                        }
                    }
                    OutlinedTextField(
                        value = keyPassphrase,
                        onValueChange = { keyPassphrase = it },
                        label = { Text(stringResource(R.string.network_key_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.network_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (protocol == RemoteProtocol.SFTP) {
                OutlinedTextField(
                    value = hostKeyFingerprint,
                    onValueChange = { hostKeyFingerprint = it },
                    label = { Text(stringResource(R.string.network_host_fingerprint)) },
                    placeholder = { Text("SHA256:...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = rootPath,
                onValueChange = { rootPath = it },
                label = { Text(stringResource(R.string.network_root_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Button(
                onClick = {
                    val parsedPort = port.toIntOrNull()
                    error = when {
                        host.isBlank() -> hostRequired
                        parsedPort == null || parsedPort !in 1..65535 -> invalidPort
                        protocol == RemoteProtocol.SMB && share.isBlank() -> shareRequired
                        protocol == RemoteProtocol.SFTP &&
                            authMethod == RemoteAuthMethod.KEY && privateKeyPath.isBlank() -> keyRequired
                        else -> null
                    }
                    if (error == null && parsedPort != null) {
                        onSave(
                            RemoteProfile(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.ifBlank { host },
                                protocol = protocol,
                                host = host.trim(),
                                port = parsedPort,
                                username = username.trim(),
                                password = password,
                                domain = domain.trim(),
                                share = share.trim(),
                                rootPath = rootPath.trim().ifBlank { "/" },
                                authMethod = authMethod,
                                privateKeyPath = privateKeyPath,
                                keyPassphrase = keyPassphrase,
                                hostKeyFingerprint = hostKeyFingerprint.trim(),
                                guest = protocol == RemoteProtocol.SMB && guest,
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteBrowserScreen(state: AppState, screen: Screen.RemoteBrowser) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = state.remoteProfile(screen.profileId)
    if (profile == null) {
        LaunchedEffect(Unit) { state.back() }
        return
    }

    var fs by remember(profile.id) { mutableStateOf<RemoteFileSystem?>(null) }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var currentPath by remember { mutableStateOf(screen.path.ifBlank { "/" }) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var connectTick by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var task by remember { mutableStateOf<RemoteTask?>(null) }
    var cancelRequested by remember { mutableStateOf(false) }
    var menuEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var renameEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var propertiesEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelected by remember { mutableStateOf(false) }
    var showSelectionMore by remember { mutableStateOf(false) }
    var hashEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var markerEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var thumbUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun goUp() {
        if (currentPath == "/") {
            state.back()
        } else {
            currentPath = RemoteFileSystems.parent(currentPath)
        }
    }

    BackHandler {
        if (selected.isNotEmpty()) selected = emptySet() else goUp()
    }
    LaunchedEffect(currentPath) { selected = emptySet() }

    fun beginTask(title: String, total: Long, detail: String, cancellable: Boolean = total > 0) {
        cancelRequested = false
        task = RemoteTask(title, total, 0, detail, cancellable)
    }

    fun finishTask(message: String?) {
        task = null
        cancelRequested = false
        if (message != null) state.showMessage(message)
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val system = fs
        if (system != null && uris.isNotEmpty()) {
            scope.launch {
                beginTask(context.getString(R.string.network_uploading), 0, context.getString(R.string.network_preparing), cancellable = true)
                val tempDir = File(context.cacheDir, "remote_upload").apply { mkdirs() }
                val prepared = mutableListOf<Pair<File, String>>()
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        if (cancelRequested) break
                        val original = state.repository.displayName(uri)
                            ?: "upload_${System.currentTimeMillis()}"
                        val temp = File(tempDir, "${System.currentTimeMillis()}_$original")
                        if (state.repository.copyUriToFile(uri, temp)) {
                            prepared.add(temp to original)
                        }
                    }
                }
                if (cancelRequested) {
                    prepared.forEach { it.first.delete() }
                    finishTask(context.getString(R.string.operation_cancelled))
                    return@launch
                }
                if (prepared.isEmpty()) {
                    finishTask(context.getString(R.string.network_upload_failed))
                    return@launch
                }
                val total = prepared.sumOf { it.first.length() }
                task = task?.copy(total = total)
                var success = 0
                var uploaded = 0L
                withContext(Dispatchers.IO) {
                    for ((temp, original) in prepared) {
                        if (cancelRequested) break
                        task = task?.copy(detail = original)
                        val base = uploaded
                        val destName = uniqueRemoteName(system, currentPath, original)
                        val ok = try {
                            system.upload(
                                temp,
                                RemoteFileSystems.join(currentPath, destName),
                                onBytes = { done ->
                                    task = task?.copy(done = (base + done).coerceAtMost(total))
                                },
                                shouldContinue = { !cancelRequested },
                            )
                            true
                        } catch (_: RemoteCancelledException) {
                            false
                        } catch (_: Exception) {
                            false
                        }
                        if (ok) success++
                        uploaded += temp.length()
                        task = task?.copy(done = uploaded.coerceAtMost(total))
                        temp.delete()
                    }
                }
                prepared.forEach { it.first.delete() }
                val cancelled = cancelRequested
                refreshTick++
                finishTask(
                    if (cancelled) context.getString(R.string.operation_cancelled)
                    else context.getString(
                        if (success > 0) R.string.network_uploaded else R.string.network_upload_failed,
                        success,
                    )
                )
            }
        }
    }

    fun openEntry(entry: RemoteEntry) {
        if (entry.isDirectory) {
            currentPath = entry.path
            return
        }
        val system = fs ?: return
        val isAudio = entry.toFileItem().isAudio
        scope.launch {
            beginTask(context.getString(R.string.network_downloading), entry.size, entry.name)
            val cacheDir = File(
                File(context.cacheDir, "remote/${profile.id.hashCode()}"),
                abs(entry.path.hashCode()).toString(),
            ).apply { mkdirs() }
            val dest = File(cacheDir, entry.name)
            var lastReported = -1L
            val step = if (entry.size > 0) (entry.size / 100).coerceAtLeast(32 * 1024) else 64 * 1024
            val ok = withContext(Dispatchers.IO) {
                try {
                    system.download(
                        entry.path,
                        dest,
                        entry.size,
                        onBytes = { done ->
                            if (done - lastReported >= step || done >= entry.size) {
                                lastReported = done
                                task = task?.copy(done = done)
                            }
                        },
                        shouldContinue = { !cancelRequested },
                    )
                    true
                } catch (_: RemoteCancelledException) {
                    false
                } catch (_: Exception) {
                    false
                }
            }
            val cancelled = cancelRequested
            if (!ok || cancelled) {
                dest.delete()
                finishTask(
                    context.getString(
                        if (cancelled) R.string.operation_cancelled else R.string.network_download_failed
                    )
                )
                return@launch
            }
            if (isAudio) {
                val base = entry.name.substringBeforeLast('.')
                val parentPath = RemoteFileSystems.parent(entry.path)
                val lrc = runCatching {
                    system.list(parentPath).firstOrNull {
                        !it.isDirectory &&
                            it.name.endsWith(".lrc", ignoreCase = true) &&
                            it.name.substringBeforeLast('.').equals(base, ignoreCase = true)
                    }
                }.getOrNull()
                if (lrc != null && !cancelRequested) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            system.download(
                                lrc.path,
                                File(cacheDir, "$base.lrc"),
                                -1L,
                                shouldContinue = { !cancelRequested },
                            )
                        }
                    }
                }
            }
            finishTask(null)
            val local = state.repository.toItem(dest).copy(name = entry.name)
            state.openItem(local, addToRecent = false)
        }
    }

    fun downloadToDevice(entry: RemoteEntry) {
        val system = fs ?: return
        scope.launch {
            beginTask(context.getString(R.string.network_downloading), entry.size, entry.name)
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dest = com.example.filebrowser.data.FileOps.uniqueFile(File(dir, entry.name))
            var lastReported = -1L
            val step = if (entry.size > 0) (entry.size / 100).coerceAtLeast(32 * 1024) else 64 * 1024
            val ok = withContext(Dispatchers.IO) {
                try {
                    system.download(
                        entry.path,
                        dest,
                        entry.size,
                        onBytes = { done ->
                            if (done - lastReported >= step || done >= entry.size) {
                                lastReported = done
                                task = task?.copy(done = done)
                            }
                        },
                        shouldContinue = { !cancelRequested },
                    )
                    true
                } catch (_: RemoteCancelledException) {
                    false
                } catch (_: Exception) {
                    false
                }
            }
            val cancelled = cancelRequested
            if (ok && !cancelled) {
                state.repository.scan(listOf(dest))
                finishTask(context.getString(R.string.network_downloaded, dest.name))
            } else {
                dest.delete()
                finishTask(
                    context.getString(
                        if (cancelled) R.string.operation_cancelled else R.string.network_download_failed
                    )
                )
            }
        }
    }

    fun toggleSelection(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun startStream(entry: RemoteEntry, copyUrl: Boolean) {
        if (entry.isDirectory) return
        val media = entry.toFileItem()
        if (!media.isAudio && !media.isVideo) return
        val mime = media.mime ?: if (media.isVideo) "video/*" else if (media.isAudio) "audio/*" else "*/*"
        val token = StreamRegistry.register(
            profileId = profile.id,
            remotePath = entry.path,
            name = entry.name,
            mime = mime,
            size = entry.size,
        )
        val port = runCatching { RemoteStreamServer.start(context) }.getOrElse {
            state.showMessage(context.getString(R.string.stream_start_failed))
            return
        }
        RemoteStreamService.start(context)
        val url = "http://127.0.0.1:$port/stream/$token"
        if (copyUrl) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(entry.name, url))
            state.showMessage(context.getString(R.string.stream_url_copied))
        } else {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(url), mime)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching {
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.stream_chooser))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                state.showMessage(context.getString(R.string.open_failed))
            }
        }
    }

    fun deleteSelected(items: List<RemoteEntry>) {
        val system = fs ?: return
        if (items.isEmpty()) return
        scope.launch {
            beginTask(
                context.getString(R.string.delete_progress),
                0,
                context.getString(R.string.items_count, items.size),
                cancellable = true,
            )
            var success = 0
            withContext(Dispatchers.IO) {
                for (entry in items) {
                    if (cancelRequested) break
                    task = task?.copy(detail = entry.name)
                    if (runCatching { system.delete(entry.path, entry.isDirectory) }.isSuccess) {
                        success++
                    }
                }
            }
            val cancelled = cancelRequested
            refreshTick++
            finishTask(
                context.getString(
                    when {
                        cancelled -> R.string.operation_cancelled
                        success > 0 -> R.string.network_deleted
                        else -> R.string.network_delete_failed
                    }
                )
            )
        }
    }

    fun pasteRemoteToRemote(remote: com.example.filebrowser.data.RemoteClipboard) {
        val target = fs ?: return
        val sourceProfile = state.remoteProfile(remote.profileId)
        if (sourceProfile == null) {
            state.showMessage(context.getString(R.string.network_connection_failed))
            return
        }
        val moving = remote.op == com.example.filebrowser.data.ClipboardOp.MOVE
        val total = remote.items.sumOf { if (it.isDirectory) 0L else it.size }
        scope.launch {
            beginTask(
                context.getString(if (moving) R.string.move_progress else R.string.copy_progress),
                total,
                context.getString(R.string.items_count, remote.items.size),
            )
            val source = withContext(Dispatchers.IO) {
                runCatching { RemoteFileSystems.open(sourceProfile) }.getOrNull()
            }
            if (source == null) {
                finishTask(context.getString(R.string.network_connection_failed))
                return@launch
            }
            var success = 0
            var base = 0L
            try {
                withContext(Dispatchers.IO) {
                    for (entry in remote.items) {
                        if (cancelRequested) break
                        task = task?.copy(detail = entry.name)
                        val entryBase = base
                        val ok = RemoteTransfer.remoteToRemote(
                            source = source,
                            entry = entry,
                            target = target,
                            targetDir = currentPath,
                            onProgress = { done ->
                                task = task?.copy(
                                    done = (entryBase + done).coerceAtMost(
                                        if (total > 0) total else Long.MAX_VALUE
                                    )
                                )
                            },
                            shouldContinue = { !cancelRequested },
                        )
                        if (ok) {
                            success++
                            if (moving) runCatching { source.delete(entry.path, entry.isDirectory) }
                        }
                        base += if (entry.isDirectory) 0 else entry.size
                    }
                }
            } finally {
                runCatching { source.close() }
            }
            val cancelled = cancelRequested
            if (moving && success > 0) state.clearClipboard()
            refreshTick++
            finishTask(
                context.getString(
                    when {
                        cancelled -> R.string.operation_cancelled
                        success > 0 -> if (moving) R.string.moved else R.string.copied
                        else -> R.string.operation_failed
                    },
                    success,
                )
            )
        }
    }

    fun pasteLocalToRemote(clip: com.example.filebrowser.data.Clipboard) {
        val target = fs ?: return
        val items = clip.items.filter { it.path.isNotBlank() }
        if (items.isEmpty()) {
            state.showMessage(context.getString(R.string.nothing_to_paste))
            return
        }
        val moving = clip.op == com.example.filebrowser.data.ClipboardOp.MOVE
        val total = items.sumOf { if (it.isDirectory) 0L else File(it.path).length() }
        scope.launch {
            beginTask(
                context.getString(if (moving) R.string.move_progress else R.string.copy_progress),
                total,
                context.getString(R.string.items_count, items.size),
            )
            var success = 0
            var base = 0L
            withContext(Dispatchers.IO) {
                for (item in items) {
                    if (cancelRequested) break
                    val source = File(item.path)
                    if (!source.exists()) continue
                    task = task?.copy(detail = source.name)
                    val entryBase = base
                    val ok = RemoteTransfer.localToRemote(
                        source = source,
                        target = target,
                        targetDir = currentPath,
                        onProgress = { done ->
                            task = task?.copy(
                                done = (entryBase + done).coerceAtMost(
                                    if (total > 0) total else Long.MAX_VALUE
                                )
                            )
                        },
                        shouldContinue = { !cancelRequested },
                    )
                    if (ok) {
                        success++
                        if (moving) com.example.filebrowser.data.FileOps.deleteRecursive(source)
                    }
                    base += if (source.isDirectory) 0 else source.length()
                }
            }
            val cancelled = cancelRequested
            if (moving && success > 0) state.clearClipboard()
            refreshTick++
            finishTask(
                context.getString(
                    when {
                        cancelled -> R.string.operation_cancelled
                        success > 0 -> if (moving) R.string.moved else R.string.copied
                        else -> R.string.operation_failed
                    },
                    success,
                )
            )
        }
    }

    fun pasteHere() {
        val remote = state.remoteClipboard
        if (remote != null) {
            pasteRemoteToRemote(remote)
            return
        }
        val local = state.clipboard
        if (local != null) {
            pasteLocalToRemote(local)
            return
        }
        state.showMessage(context.getString(R.string.nothing_to_paste))
    }

    fun createFolder(name: String) {
        val system = fs ?: return
        scope.launch {
            beginTask(context.getString(R.string.processing), 0, name, cancellable = false)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    system.mkdir(RemoteFileSystems.join(currentPath, name))
                }.isSuccess
            }
            refreshTick++
            finishTask(
                context.getString(
                    if (ok) R.string.network_folder_created else R.string.network_mkdir_failed
                )
            )
        }
    }

    fun deleteRemote(entry: RemoteEntry) {
        val system = fs ?: return
        scope.launch {
            beginTask(context.getString(R.string.delete_progress), 0, entry.name, cancellable = false)
            val ok = withContext(Dispatchers.IO) {
                runCatching { system.delete(entry.path, entry.isDirectory) }.isSuccess
            }
            refreshTick++
            finishTask(
                context.getString(if (ok) R.string.network_deleted else R.string.network_delete_failed)
            )
        }
    }

    fun renameRemote(entry: RemoteEntry, newName: String) {
        val system = fs ?: return
        val target = RemoteFileSystems.join(RemoteFileSystems.parent(entry.path), newName)
        scope.launch {
            beginTask(context.getString(R.string.rename_progress), 0, entry.name, cancellable = false)
            val ok = withContext(Dispatchers.IO) {
                runCatching { system.rename(entry.path, target) }.isSuccess
            }
            refreshTick++
            finishTask(
                context.getString(if (ok) R.string.network_renamed else R.string.network_rename_failed)
            )
        }
    }

    LaunchedEffect(entries) {
        if (entries.isEmpty()) return@LaunchedEffect
        val media = entries.filter {
            !it.isDirectory && (it.toFileItem().isImage || it.toFileItem().isVideo)
        }
        if (media.isEmpty()) {
            thumbUrls = emptyMap()
            return@LaunchedEffect
        }
        val port = runCatching { RemoteStreamServer.start(context) }.getOrNull()
        if (port == null) return@LaunchedEffect
        thumbUrls = media.associate { entry ->
            val item = entry.toFileItem()
            val mime = item.mime ?: if (item.isVideo) "video/*" else "image/*"
            val token = StreamRegistry.register(
                profileId = profile.id,
                remotePath = entry.path,
                name = entry.name,
                mime = mime,
                size = entry.size,
            )
            entry.path to "http://127.0.0.1:$port/stream/$token"
        }
    }

    LaunchedEffect(profile.id, connectTick) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { RemoteFileSystems.open(profile) }
        }
        result.onSuccess { fs = it }.onFailure {
            error = it.message ?: context.getString(R.string.network_connection_failed)
            loading = false
        }
    }

    val currentFs = fs
    DisposableEffect(currentFs) {
        onDispose { currentFs?.close() }
    }

    LaunchedEffect(fs, currentPath, refreshTick) {
        val system = fs ?: return@LaunchedEffect
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { system.list(currentPath) }
        }
        result.onSuccess {
            entries = it
            loading = false
        }.onFailure {
            error = it.message ?: context.getString(R.string.network_connection_failed)
            loading = false
        }
    }

    val selectedEntries = entries.filter { it.path in selected }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        if (selected.isNotEmpty()) {
            SelectionActionBar(
                count = selected.size,
                onClose = { selected = emptySet() },
                onCopy = {
                    state.setRemoteClipboard(
                        com.example.filebrowser.data.ClipboardOp.COPY,
                        profile.id,
                        selectedEntries,
                    )
                    selected = emptySet()
                },
                onCut = {
                    state.setRemoteClipboard(
                        com.example.filebrowser.data.ClipboardOp.MOVE,
                        profile.id,
                        selectedEntries,
                    )
                    selected = emptySet()
                },
                onDelete = { showDeleteSelected = true },
                onMore = { showSelectionMore = true },
            )
        } else {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { goUp() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = {
                Column {
                    Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${profile.protocol} · ${profile.host}:${profile.port}" +
                            if (profile.protocol == RemoteProtocol.SMB) "/${profile.share}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                IconButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.UploadFile, contentDescription = stringResource(R.string.network_upload))
                }
                IconButton(onClick = { refreshTick++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                }
                IconButton(onClick = { showNewFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
                }
            },
        )
        }
        RemoteBreadcrumb(
            path = currentPath,
            rootLabel = if (profile.protocol == RemoteProtocol.SMB) profile.share.ifBlank { "/" }
            else stringResource(R.string.network_root),
            onNavigate = { currentPath = it },
        )

        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.network_loading))
                    }
                }
                error != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        error ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { connectTick++ }) {
                        Text(stringResource(R.string.network_retry))
                    }
                }
                entries.isEmpty() -> EmptyState(
                    Icons.Filled.Folder,
                    stringResource(R.string.empty_folder),
                    Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(entries, key = { it.path }) { entry ->
                        val item = entry.toFileItem()
                        FileRow(
                            item = item.copy(thumbnailUrl = thumbUrls[entry.path]),
                            marker = state.markerForRemote(profile.id, entry.path),
                            showPath = false,
                            selectionMode = selected.isNotEmpty(),
                            selected = entry.path in selected,
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    toggleSelection(entry.path)
                                } else if (entry.isDirectory) {
                                    currentPath = entry.path
                                } else {
                                    menuEntry = entry
                                }
                            },
                            onLongClick = { toggleSelection(entry.path) },
                        )
                    }
                }
            }
        }
        val clipCount = state.remoteClipboard?.items?.size ?: state.clipboard?.items?.size ?: 0
        if (selected.isEmpty() && clipCount > 0) {
            ClipboardBar(
                count = clipCount,
                onPaste = { pasteHere() },
                onClear = { state.clearClipboard() },
            )
        }
    }

    menuEntry?.let { entry ->
        RemoteEntryActionsDialog(
            entry = entry,
            onOpen = { openEntry(entry) },
            onStream = { startStream(entry, copyUrl = false) },
            onCopyStream = { startStream(entry, copyUrl = true) },
            onHash = { hashEntry = entry },
            onMarker = { markerEntry = entry },
            onDownload = { downloadToDevice(entry) },
            onRename = { renameEntry = entry },
            onProperties = { propertiesEntry = entry },
            onDelete = { deleteEntry = entry },
            onDismiss = { menuEntry = null },
        )
    }

    if (showNewFolder) {
        NameDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showNewFolder = false },
            onConfirm = { createFolder(it) },
        )
    }

    renameEntry?.let { entry ->
        NameDialog(
            title = stringResource(R.string.rename),
            initial = entry.name,
            confirmLabel = stringResource(R.string.ok),
            onDismiss = { renameEntry = null },
            onConfirm = { renameRemote(entry, it) },
        )
    }

    deleteEntry?.let { entry ->
        DeleteConfirmDialog(
            count = 1,
            onDismiss = { deleteEntry = null },
            onConfirm = { deleteRemote(entry) },
        )
    }

    propertiesEntry?.let { entry ->
        RemotePropertiesDialog(profile, entry) { propertiesEntry = null }
    }

    markerEntry?.let { entry ->
        val key = state.remoteMarkerKey(profile.id, entry.path)
        MarkerDialog(
            title = entry.name,
            initial = state.markerFor(key),
            onSave = { state.setMarker(key, it) },
            onDismiss = { markerEntry = null },
        )
    }

    hashEntry?.let { entry ->
        HashDialog(
            name = entry.name,
            totalBytes = entry.size,
            openStream = {
                val system = fs ?: throw IllegalStateException("Not connected")
                system.openStream(entry.path)
            },
            onDismiss = { hashEntry = null },
        )
    }

    if (showDeleteSelected) {
        DeleteConfirmDialog(
            count = selectedEntries.size,
            onDismiss = { showDeleteSelected = false },
            onConfirm = {
                deleteSelected(selectedEntries)
                selected = emptySet()
            },
        )
    }

    if (showSelectionMore) {
        val single = selectedEntries.singleOrNull()
        AlertDialog(
            onDismissRequest = { showSelectionMore = false },
            title = { Text(stringResource(R.string.more)) },
            text = {
                Column {
                    RemoteActionRow(Icons.Filled.SelectAll, stringResource(R.string.select_all)) {
                        selected = entries.map { it.path }.toSet()
                        showSelectionMore = false
                    }
                    RemoteActionRow(Icons.Filled.SelectAll, stringResource(R.string.invert_selection)) {
                        selected = entries.map { it.path }.toSet() - selected
                        showSelectionMore = false
                    }
                    RemoteActionRow(
                        Icons.Filled.Edit,
                        stringResource(R.string.rename),
                        enabled = single != null,
                    ) {
                        if (single != null) {
                            renameEntry = single
                            selected = emptySet()
                        }
                        showSelectionMore = false
                    }
                    RemoteActionRow(
                        Icons.Filled.Download,
                        stringResource(R.string.network_download),
                        enabled = single != null && !single.isDirectory,
                    ) {
                        if (single != null) downloadToDevice(single)
                        showSelectionMore = false
                    }
                    RemoteActionRow(
                        Icons.Filled.Info,
                        stringResource(R.string.properties),
                        enabled = single != null,
                    ) {
                        if (single != null) propertiesEntry = single
                        showSelectionMore = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectionMore = false }) {
                    Text(stringResource(R.string.close))
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
                    if (current.detail.isNotBlank()) {
                        Text(
                            current.detail,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val fraction = current.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (current.total > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Format.size(current.done)} / ${Format.size(current.total)} · " +
                                "${(fraction ?: 0f).times(100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                if (current.cancellable) {
                    TextButton(onClick = {
                        cancelRequested = true
                        task = task?.copy(detail = context.getString(R.string.operation_cancelling))
                    }) {
                        Text(stringResource(R.string.cancel_operation))
                    }
                }
            },
        )
    }
}

@Composable
private fun RemoteEntryActionsDialog(
    entry: RemoteEntry,
    onOpen: () -> Unit,
    onStream: () -> Unit,
    onCopyStream: () -> Unit,
    onHash: () -> Unit,
    onMarker: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val media = entry.toFileItem()
    val isMedia = !entry.isDirectory && (media.isAudio || media.isVideo)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                RemoteActionRow(Icons.Filled.Folder, stringResource(R.string.open)) {
                    onDismiss()
                    onOpen()
                }
                RemoteActionRow(Icons.Filled.PlayCircle, stringResource(R.string.stream_play_other), enabled = isMedia) {
                    onDismiss()
                    onStream()
                }
                RemoteActionRow(Icons.Filled.ContentCopy, stringResource(R.string.stream_copy_url), enabled = isMedia) {
                    onDismiss()
                    onCopyStream()
                }
                RemoteActionRow(Icons.Filled.Download, stringResource(R.string.network_download), enabled = !entry.isDirectory) {
                    onDismiss()
                    onDownload()
                }
                RemoteActionRow(Icons.Filled.Fingerprint, stringResource(R.string.file_hash), enabled = !entry.isDirectory) {
                    onDismiss()
                    onHash()
                }
                RemoteActionRow(Icons.Filled.Tag, stringResource(R.string.marker_title)) {
                    onDismiss()
                    onMarker()
                }
                RemoteActionRow(Icons.Filled.Edit, stringResource(R.string.rename)) {
                    onDismiss()
                    onRename()
                }
                RemoteActionRow(Icons.Filled.Info, stringResource(R.string.properties)) {
                    onDismiss()
                    onProperties()
                }
                RemoteActionRow(
                    Icons.Filled.Delete,
                    stringResource(R.string.delete),
                    destructive = true,
                ) {
                    onDismiss()
                    onDelete()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun RemoteActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun RemoteBreadcrumb(path: String, rootLabel: String, onNavigate: (String) -> Unit) {
    val parts = remember(path, rootLabel) {
        val list = mutableListOf(rootLabel to "/")
        var current = ""
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { part ->
            current = "$current/$part"
            list.add(part to current)
        }
        list
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(parts, key = { it.second }) { (label, target) ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (target == path) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigate(target) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                maxLines = 1,
            )
            if (target != path) {
                Text(
                    "/",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun RemotePropertiesDialog(
    profile: RemoteProfile,
    entry: RemoteEntry,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(stringResource(R.string.network_storage), profile.name)
                InfoRow(stringResource(R.string.network_protocol), profile.protocol.name)
                InfoRow(stringResource(R.string.network_host), "${profile.host}:${profile.port}")
                if (profile.protocol == RemoteProtocol.SMB) {
                    InfoRow(stringResource(R.string.network_share), profile.share)
                }
                InfoRow(stringResource(R.string.network_remote_path), entry.path)
                InfoRow(
                    stringResource(R.string.file_size),
                    if (entry.isDirectory) stringResource(R.string.folder)
                    else Format.size(entry.size),
                )
                InfoRow(stringResource(R.string.file_modified), Format.date(entry.lastModified))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

private fun uniqueRemoteName(fs: RemoteFileSystem, dir: String, name: String): String {
    val first = RemoteFileSystems.join(dir, name)
    if (!fs.exists(first)) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var index = 1
    while (true) {
        val candidate = "$base ($index)$ext"
        if (!fs.exists(RemoteFileSystems.join(dir, candidate))) return candidate
        index++
    }
}
