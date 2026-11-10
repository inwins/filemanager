package io.github.inwins.filebrowser.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.github.inwins.filebrowser.AppState
import io.github.inwins.filebrowser.R
import io.github.inwins.filebrowser.data.ThemeMode
import io.github.inwins.filebrowser.util.findActivity

@Composable
fun AppRoot(state: AppState) {
    val snackbarHostState = remember { SnackbarHostState() }
    val darkTheme = when (state.settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        state.message = null
        snackbarHostState.showSnackbar(text)
    }

    if (!state.permissionGranted) {
        PermissionScreen(state)
        state.operation?.let { OperationOverlay(it, state::cancelOperation) }
        return
    }
    BackHandler(enabled = state.backStack.size > 1) {
        state.back()
    }
    val current = state.current
    val showBottomBar = state.backStack.size == 1 &&
        (current is Screen.Home || current is Screen.Browser || current is Screen.Categories ||
            current is Screen.Network || current is Screen.Settings)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
            val showMini = state.audioPlayer.current != null && current !is Screen.AudioPlayer
            if (showMini) {
                MiniAudioBar(
                    state = state,
                    applyNavigationInset = !showBottomBar,
                    onOpen = { state.navigate(Screen.AudioPlayer) },
                )
            }
            if (showBottomBar) {
                val selected = when (current) {
                    is Screen.Home -> 0
                    is Screen.Browser -> 1
                    is Screen.Categories -> 2
                    is Screen.Network, is Screen.RemoteBrowser -> 3
                    is Screen.Settings -> 4
                    else -> -1
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    BottomItem(
                        label = stringResource(R.string.tab_home),
                        icon = Icons.Filled.Home,
                        selected = selected == 0,
                        onClick = { state.goTab(0) },
                    )
                    BottomItem(
                        label = stringResource(R.string.tab_files),
                        icon = Icons.Filled.FolderOpen,
                        selected = selected == 1,
                        onClick = { state.goTab(1) },
                    )
                    BottomItem(
                        label = stringResource(R.string.tab_categories),
                        icon = Icons.Filled.Apps,
                        selected = selected == 2,
                        onClick = { state.goTab(2) },
                    )
                    BottomItem(
                        label = stringResource(R.string.tab_network),
                        icon = Icons.Filled.Cloud,
                        selected = selected == 3,
                        onClick = { state.goTab(3) },
                    )
                    BottomItem(
                        label = stringResource(R.string.tab_settings),
                        icon = Icons.Filled.Settings,
                        selected = selected == 4,
                        onClick = { state.goTab(4) },
                    )
                }
            }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (current) {
                is Screen.Home -> HomeScreen(state)
                is Screen.Browser -> BrowserScreen(state, current)
                is Screen.Categories -> CategoriesScreen(state)
                is Screen.Category -> CategoryScreen(state, current.kind)
                is Screen.Apps -> AppsScreen(state)
                is Screen.Search -> SearchScreen(state, current.root)
                is Screen.Settings -> SettingsScreen(state)
                is Screen.Analyzer -> AnalyzerScreen(state, current.path)
                is Screen.ImageViewer -> ImageViewerScreen(state, current)
                is Screen.TextEditor -> TextEditorScreen(state, current.path)
                is Screen.Reader -> ReaderScreen(state, current.path)
                is Screen.Changelog -> ChangelogScreen(state)
                is Screen.Network -> NetworkScreen(state)
                is Screen.LanTransfer -> LanTransferScreen(state, current.path)
                is Screen.LanPeer -> LanPeerScreen(state, current)
                is Screen.RemoteBrowser -> RemoteBrowserScreen(state, current)
                is Screen.AudioPlayer -> AudioPlayerScreen(state)
                is Screen.ArchivePreview -> ArchivePreviewScreen(state, current.path)
                is Screen.Cleaner -> CleanerScreen(state, current.path)
                is Screen.Trash -> TrashScreen(state)
                is Screen.VideoPlayer -> VideoPlayerScreen(state, current.path)
            }
        }
    }
    state.operation?.let { OperationOverlay(it, state::cancelOperation) }
}

@Composable
private fun MiniAudioBar(
    state: AppState,
    applyNavigationInset: Boolean,
    onOpen: () -> Unit,
) {
    val player = state.audioPlayer
    Surface(
        tonalElevation = 4.dp,
        modifier = if (applyNavigationInset) Modifier.navigationBarsPadding() else Modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    player.title.ifBlank { player.current?.name ?: "" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    player.artist.ifBlank { stringResource(R.string.audio_unknown_artist) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { player.toggle() }) {
                Icon(
                    if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (player.isPlaying) R.string.audio_pause else R.string.audio_play
                    ),
                )
            }
            IconButton(onClick = { player.stop() }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.audio_close))
            }
        }
    }
}

@Composable
private fun RowScope.BottomItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

@Composable
private fun PermissionScreen(state: AppState) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(84.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.permission_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { state.requestPermission() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.grant_access))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { state.refreshPermission() }) {
                Text(stringResource(R.string.check_again))
            }
            TextButton(onClick = { (context as? Activity)?.finish() }) {
                Text(stringResource(R.string.exit_app))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.permission_settings_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
