package com.example.filebrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.filebrowser.AppState
import com.example.filebrowser.BuildConfig
import com.example.filebrowser.R
import com.example.filebrowser.data.ThemeMode
import com.example.filebrowser.data.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState) {
    val settings = state.settings
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_settings)) })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            item { SettingsSection(stringResource(R.string.settings_appearance)) }
            item {
                SettingsSelector(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.theme),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { state.updateSettings(settings.copy(themeMode = mode)) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSwitch(
                    icon = Icons.Filled.ColorLens,
                    title = stringResource(R.string.dynamic_color),
                    checked = settings.dynamicColor,
                    onCheckedChange = { state.updateSettings(settings.copy(dynamicColor = it)) },
                )
            }

            item { SettingsSection(stringResource(R.string.browsing)) }
            item {
                SettingsSelector(
                    icon = Icons.Filled.List,
                    title = stringResource(R.string.default_view),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.viewMode == ViewMode.LIST,
                            onClick = { state.updateSettings(settings.copy(viewMode = ViewMode.LIST)) },
                            label = { Text(stringResource(R.string.view_list)) },
                        )
                        FilterChip(
                            selected = settings.viewMode == ViewMode.GRID,
                            onClick = { state.updateSettings(settings.copy(viewMode = ViewMode.GRID)) },
                            label = { Text(stringResource(R.string.view_grid)) },
                        )
                    }
                }
            }
            item {
                SettingsSwitch(
                    icon = Icons.Filled.Visibility,
                    title = stringResource(R.string.show_hidden),
                    checked = settings.showHidden,
                    onCheckedChange = { state.updateSettings(settings.copy(showHidden = it)) },
                )
            }

            item { SettingsSection(stringResource(R.string.storage)) }
            item {
                SettingsItem(
                    icon = Icons.Filled.Security,
                    title = stringResource(R.string.root_access),
                    subtitle = when {
                        state.rootChecking -> stringResource(R.string.root_checking)
                        state.rootEnabled && state.rootAvailable -> stringResource(R.string.root_available)
                        state.rootEnabled -> stringResource(R.string.root_unavailable)
                        else -> stringResource(R.string.root_not_enabled)
                    },
                    trailing = {
                        Switch(
                            checked = state.rootEnabled,
                            onCheckedChange = { state.enableRootAccess(it) },
                        )
                    },
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Filled.Security,
                    title = stringResource(R.string.all_files_access),
                    subtitle = if (state.permissionGranted) stringResource(R.string.granted)
                    else stringResource(R.string.not_granted),
                    onClick = { state.requestPermission() },
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.analyze_storage),
                    subtitle = state.repository.defaultRoot(),
                    onClick = { state.navigate(Screen.Analyzer(state.repository.defaultRoot())) },
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.root_browse),
                    subtitle = "/",
                    onClick = { state.navigate(Screen.Browser("/")) },
                )
            }

            item { SettingsSection(stringResource(R.string.recent_history)) }
            item {
                SettingsItem(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.clear_recent),
                    subtitle = stringResource(R.string.items_count, state.recents.size),
                    onClick = { state.clearRecents() },
                )
            }

            item { SettingsSection(stringResource(R.string.about)) }
            item {
                SettingsItem(
                    icon = Icons.Filled.Article,
                    title = stringResource(R.string.changelog),
                    subtitle = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    onClick = { state.navigate(Screen.Changelog) },
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    onClick = null,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.about_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.open_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsSelector(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(18.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
