package com.example.cxfilemanager.ui

import android.os.Environment
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cxfilemanager.AppState
import com.example.cxfilemanager.R
import com.example.cxfilemanager.data.FileItem
import com.example.cxfilemanager.data.RemoteEntry
import com.example.cxfilemanager.data.RemoteFileSystem
import com.example.cxfilemanager.data.RemoteFileSystems
import com.example.cxfilemanager.data.RemoteProfile
import com.example.cxfilemanager.data.RemoteProtocol
import com.example.cxfilemanager.util.Format
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var error by remember { mutableStateOf<String?>(null) }
    val hostRequired = stringResource(R.string.network_host_required)
    val invalidPort = stringResource(R.string.network_invalid_port)
    val shareRequired = stringResource(R.string.network_share_required)

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
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.network_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.network_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
    var busyText by remember { mutableStateOf<String?>(null) }
    var menuEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var renameEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var propertiesEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }

    fun goUp() {
        if (currentPath == "/") {
            state.back()
        } else {
            currentPath = RemoteFileSystems.parent(currentPath)
        }
    }

    BackHandler { goUp() }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val system = fs
        if (system != null && uris.isNotEmpty()) {
            scope.launch {
                busyText = context.getString(R.string.network_uploading)
                var success = 0
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        val original = state.repository.displayName(uri)
                            ?: "upload_${System.currentTimeMillis()}"
                        val tempDir = File(context.cacheDir, "remote_upload").apply { mkdirs() }
                        val temp = File(tempDir, "${System.currentTimeMillis()}_$original")
                        if (state.repository.copyUriToFile(uri, temp)) {
                            val name = uniqueRemoteName(system, currentPath, original)
                            val ok = runCatching {
                                system.upload(temp, RemoteFileSystems.join(currentPath, name))
                            }.isSuccess
                            if (ok) success++
                            temp.delete()
                        }
                    }
                }
                busyText = null
                refreshTick++
                state.showMessage(
                    context.getString(
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
        scope.launch {
            busyText = context.getString(R.string.network_downloading)
            val cacheDir = File(
                File(context.cacheDir, "remote/${profile.id.hashCode()}"),
                kotlin.math.abs(entry.path.hashCode()).toString(),
            ).apply { mkdirs() }
            val dest = File(cacheDir, entry.name)
            val ok = withContext(Dispatchers.IO) {
                runCatching { system.download(entry.path, dest) }.isSuccess
            }
            busyText = null
            if (ok) {
                val local = state.repository.toItem(dest).copy(name = entry.name)
                state.openItem(local, addToRecent = false)
            } else {
                state.showMessage(context.getString(R.string.network_download_failed))
            }
        }
    }

    fun downloadToDevice(entry: RemoteEntry) {
        val system = fs ?: return
        scope.launch {
            busyText = context.getString(R.string.network_downloading)
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dest = com.example.cxfilemanager.data.FileOps.uniqueFile(File(dir, entry.name))
            val ok = withContext(Dispatchers.IO) {
                runCatching { system.download(entry.path, dest) }.isSuccess
            }
            busyText = null
            if (ok) {
                state.repository.scan(listOf(dest))
                state.showMessage(context.getString(R.string.network_downloaded, dest.name))
            } else {
                state.showMessage(context.getString(R.string.network_download_failed))
            }
        }
    }

    fun createFolder(name: String) {
        val system = fs ?: return
        scope.launch {
            busyText = context.getString(R.string.processing)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    system.mkdir(RemoteFileSystems.join(currentPath, name))
                }.isSuccess
            }
            busyText = null
            refreshTick++
            state.showMessage(
                context.getString(
                    if (ok) R.string.network_folder_created else R.string.network_mkdir_failed
                )
            )
        }
    }

    fun deleteRemote(entry: RemoteEntry) {
        val system = fs ?: return
        scope.launch {
            busyText = context.getString(R.string.delete_progress)
            val ok = withContext(Dispatchers.IO) {
                runCatching { system.delete(entry.path, entry.isDirectory) }.isSuccess
            }
            busyText = null
            refreshTick++
            state.showMessage(
                context.getString(if (ok) R.string.network_deleted else R.string.network_delete_failed)
            )
        }
    }

    fun renameRemote(entry: RemoteEntry, newName: String) {
        val system = fs ?: return
        val target = RemoteFileSystems.join(RemoteFileSystems.parent(entry.path), newName)
        scope.launch {
            busyText = context.getString(R.string.rename_progress)
            val ok = withContext(Dispatchers.IO) {
                runCatching { system.rename(entry.path, target) }.isSuccess
            }
            busyText = null
            refreshTick++
            state.showMessage(
                context.getString(if (ok) R.string.network_renamed else R.string.network_rename_failed)
            )
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

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
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
                            item = item,
                            selected = false,
                            selectionMode = false,
                            showPath = false,
                            onClick = { openEntry(entry) },
                            onLongClick = { menuEntry = entry },
                        )
                    }
                }
            }
        }
    }

    menuEntry?.let { entry ->
        ActionSheet(
            title = entry.name,
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.open),
                    icon = Icons.Filled.Folder,
                    enabled = entry.isDirectory,
                    onClick = { openEntry(entry) },
                ),
                SheetAction(
                    label = stringResource(R.string.network_download),
                    icon = Icons.Filled.Download,
                    enabled = !entry.isDirectory,
                    onClick = { downloadToDevice(entry) },
                ),
                SheetAction(
                    label = stringResource(R.string.rename),
                    icon = Icons.Filled.Edit,
                    onClick = { renameEntry = entry },
                ),
                SheetAction(
                    label = stringResource(R.string.properties),
                    icon = Icons.Filled.Info,
                    onClick = { propertiesEntry = entry },
                ),
                SheetAction(
                    label = stringResource(R.string.delete),
                    icon = Icons.Filled.Delete,
                    destructive = true,
                    onClick = { deleteEntry = entry },
                ),
            ),
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

    busyText?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {},
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
