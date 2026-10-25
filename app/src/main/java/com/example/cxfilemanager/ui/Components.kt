package com.example.cxfilemanager.ui

import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cxfilemanager.R
import com.example.cxfilemanager.data.CategoryKind
import com.example.cxfilemanager.data.FileItem
import com.example.cxfilemanager.data.FileRepository
import com.example.cxfilemanager.data.OperationState
import com.example.cxfilemanager.data.SortField
import com.example.cxfilemanager.data.SortOrder
import com.example.cxfilemanager.data.SortSpec
import com.example.cxfilemanager.data.StorageItem
import com.example.cxfilemanager.data.ViewMode
import com.example.cxfilemanager.util.Format
import com.example.cxfilemanager.util.MimeTypes
import com.example.cxfilemanager.util.Paths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class FileVisual(val icon: ImageVector, val tint: Color)

fun Modifier.conditionalNavigationBarsPadding(apply: Boolean): Modifier =
    if (apply) navigationBarsPadding() else this

@Composable
fun fileVisual(item: FileItem): FileVisual {
    val errorColor = MaterialTheme.colorScheme.error
    return when {
        item.isDirectory -> FileVisual(Icons.Filled.Folder, MaterialTheme.colorScheme.primary)
        item.isImage -> FileVisual(Icons.Filled.Image, Color(0xFF00897B))
        item.isVideo -> FileVisual(Icons.Filled.Movie, Color(0xFFE53935))
        item.isAudio -> FileVisual(Icons.Filled.MusicNote, Color(0xFF8E24AA))
        item.isApk -> FileVisual(Icons.Filled.Android, Color(0xFF43A047))
        item.extension == "pdf" -> FileVisual(Icons.Filled.PictureAsPdf, errorColor)
        item.isArchive -> FileVisual(Icons.Filled.Archive, Color(0xFFF57C00))
        item.extension in setOf("doc", "docx", "odt", "rtf", "epub") ->
            FileVisual(Icons.Filled.Description, Color(0xFF1E88E5))
        item.extension in setOf("xls", "xlsx", "ods", "csv") ->
            FileVisual(Icons.Filled.TableChart, Color(0xFF2E7D32))
        item.extension in setOf("ppt", "pptx", "odp") ->
            FileVisual(Icons.Filled.Slideshow, Color(0xFFEF6C00))
        item.extension in setOf("kt", "java", "py", "c", "h", "cpp", "js", "ts", "json", "xml", "html", "css", "sh") ->
            FileVisual(Icons.Filled.Code, Color(0xFF5E35B1))
        item.isText -> FileVisual(Icons.Filled.TextSnippet, Color(0xFF546E7A))
        else -> FileVisual(Icons.Filled.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FileLeadingIcon(item: FileItem, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val visual = fileVisual(item)
    if (item.isImage && !item.remote && (item.path.isNotBlank() || item.contentUri != null)) {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.path.ifBlank { item.contentUri },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.isVideo) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size / 2),
                )
            }
        }
        return
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(visual.tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.tint, modifier = Modifier.size(size * 0.55f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showPath: Boolean = false,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileLeadingIcon(item)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val second = subtitle ?: buildString {
                if (item.isDirectory) {
                    append(stringResource(R.string.folder))
                    item.childCount?.let { append(" · "); append(stringResource(R.string.items_count, it)) }
                } else {
                    append(Format.size(item.size))
                    if (item.lastModified > 0) {
                        append(" · ")
                        append(Format.shortDate(item.lastModified))
                    }
                }
            }
            Text(
                second,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showPath && item.path.isNotBlank()) {
                Text(
                    item.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else if (item.isDirectory) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.isImage && !item.remote && (item.path.isNotBlank() || item.contentUri != null)) {
                AsyncImage(
                    model = item.path.ifBlank { item.contentUri },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val visual = fileVisual(item)
                Icon(
                    visual.icon,
                    contentDescription = null,
                    tint = visual.tint,
                    modifier = Modifier.size(42.dp),
                )
            }
            if (item.isVideo) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
            if (selectionMode && selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!item.isDirectory) {
            Text(
                Format.size(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StorageCard(storage: StorageItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(16.dp),
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
                    if (storage.removable) Icons.Filled.Download else Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        storage.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.free_space, Format.size(storage.freeBytes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = { storage.usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        storage.usedFraction > 0.92f -> MaterialTheme.colorScheme.error
                        storage.usedFraction > 0.8f -> Color(0xFFF57C00)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(
                        R.string.used_of,
                        Format.size(storage.usedBytes),
                        Format.size(storage.totalBytes),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CategoryTile(
    kind: CategoryKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, color, label) = when (kind) {
        CategoryKind.IMAGES -> Triple(Icons.Filled.PhotoLibrary, Color(0xFF00897B), stringResource(R.string.cat_images))
        CategoryKind.VIDEOS -> Triple(Icons.Filled.VideoLibrary, Color(0xFFE53935), stringResource(R.string.cat_videos))
        CategoryKind.AUDIO -> Triple(Icons.Filled.LibraryMusic, Color(0xFF8E24AA), stringResource(R.string.cat_audio))
        CategoryKind.DOCUMENTS -> Triple(Icons.Filled.Article, Color(0xFF1E88E5), stringResource(R.string.cat_documents))
        CategoryKind.DOWNLOADS -> Triple(Icons.Filled.Download, Color(0xFF43A047), stringResource(R.string.cat_downloads))
        CategoryKind.APKS -> Triple(Icons.Filled.Android, Color(0xFF7CB342), stringResource(R.string.cat_apks))
        CategoryKind.ARCHIVES -> Triple(Icons.Filled.FolderZip, Color(0xFFF57C00), stringResource(R.string.cat_archives))
        CategoryKind.RECENT -> Triple(Icons.Filled.History, Color(0xFF546E7A), stringResource(R.string.cat_recent))
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun CategoryGrid(
    onCategory: (CategoryKind) -> Unit,
    onApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = listOf(
        CategoryKind.IMAGES,
        CategoryKind.VIDEOS,
        CategoryKind.AUDIO,
        CategoryKind.DOCUMENTS,
        CategoryKind.DOWNLOADS,
        CategoryKind.APKS,
        CategoryKind.ARCHIVES,
        CategoryKind.RECENT,
    )
    Column(modifier.padding(horizontal = 12.dp)) {
        kinds.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { kind ->
                    CategoryTile(kind, onClick = { onCategory(kind) }, modifier = Modifier.weight(1f))
                }
                if (row.size < 4) {
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onApps)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF5C6BC0).copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = null, tint = Color(0xFF5C6BC0), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.cat_apps), style = MaterialTheme.typography.labelMedium)
            }
            repeat(3) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun QuickAccessTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun PathBreadcrumb(path: String, onNavigate: (String) -> Unit) {
    val primary = Environment.getExternalStorageDirectory().absolutePath
    val rootLabel = stringResource(R.string.root_browse)
    val internalLabel = stringResource(R.string.storage_internal)
    val crumbs = remember(path, primary, rootLabel, internalLabel) {
        val raw = Paths.breadcrumbs(path)
        when {
            path == "/" -> listOf(rootLabel to "/")
            path == primary -> listOf(internalLabel to primary)
            path.startsWith("$primary/") -> listOf(internalLabel to primary) +
                raw.filter { it.second != primary && it.second.length > primary.length }
            else -> raw.map { (label, crumb) ->
                if (crumb == "/") rootLabel to "/" else label to crumb
            }
        }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(crumbs) { (label, crumbPath) ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (crumbPath == path) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigate(crumbPath) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                maxLines = 1,
            )
            if (crumbPath != path) {
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
fun SelectionActionBar(
    count: Int,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.done))
            }
            Text(
                stringResource(R.string.selected_count, count),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy))
            }
            IconButton(onClick = onCut) {
                Icon(Icons.Filled.ContentCut, contentDescription = stringResource(R.string.cut))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
            }
        }
    }
}

@Composable
fun ClipboardBar(count: Int, onPaste: () -> Unit, onClear: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.clipboard_count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = onPaste) { Text(stringResource(R.string.paste)) }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    existingNames: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val invalid = text.isNotBlank() && (!Paths.safeName(text) || text in existingNames)
    val error = when {
        text.isNotBlank() && !Paths.safeName(text) -> stringResource(R.string.invalid_name)
        text in existingNames -> stringResource(R.string.name_exists)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = invalid,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()); onDismiss() },
                enabled = text.isNotBlank() && !invalid,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun DeleteConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_confirm_message, count))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.delete_permanent_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String? = null,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = action.enabled) {
                            onDismiss()
                            action.onClick()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        tint = if (action.destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            action.destructive -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    spec: SortSpec,
    viewMode: ViewMode,
    showHidden: Boolean,
    onApply: (SortField, SortOrder) -> Unit,
    onToggleView: () -> Unit,
    onToggleHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var field by remember { mutableStateOf(spec.field) }
    var order by remember { mutableStateOf(spec.order) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.sort_dialog),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
            listOf(
                SortField.NAME to stringResource(R.string.sort_name),
                SortField.SIZE to stringResource(R.string.sort_size),
                SortField.DATE to stringResource(R.string.sort_date),
                SortField.TYPE to stringResource(R.string.sort_type),
            ).forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { field = value }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = field == value, onClick = { field = value })
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { order = SortOrder.ASC }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = order == SortOrder.ASC, onClick = { order = SortOrder.ASC })
                Text(stringResource(R.string.ascending), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { order = SortOrder.DESC }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = order == SortOrder.DESC, onClick = { order = SortOrder.DESC })
                Text(stringResource(R.string.descending), style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleView() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (viewMode == ViewMode.LIST) stringResource(R.string.view_grid) else stringResource(R.string.view_list),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (viewMode == ViewMode.LIST) Icons.Filled.Apps else Icons.Filled.Article,
                    contentDescription = null,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleHidden() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.show_hidden),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Checkbox(checked = showHidden, onCheckedChange = null)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    onApply(field, order)
                    onDismiss()
                }) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesDialog(item: FileItem, repository: FileRepository, onDismiss: () -> Unit) {
    var folderSize by remember(item.path) { mutableStateOf<Long?>(if (item.isDirectory) null else item.size) }
    LaunchedEffect(item.path, item.isDirectory) {
        if (item.isDirectory && item.path.isNotBlank()) {
            val job = coroutineContext[Job]
            folderSize = withContext(Dispatchers.IO) {
                repository.folderSize(File(item.path), shouldContinue = { job?.isActive == true })
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(stringResource(R.string.file_type), MimeTypes.typeLabel(item, stringResource(R.string.folder), stringResource(R.string.file)))
                InfoRow(
                    stringResource(R.string.file_size),
                    when {
                        item.isDirectory -> folderSize?.let { Format.size(it) } ?: stringResource(R.string.folder_size)
                        else -> Format.size(item.size)
                    },
                )
                InfoRow(stringResource(R.string.file_modified), Format.date(item.lastModified))
                if (item.path.isNotBlank()) {
                    val permissions = item.permissions
                    if (permissions != null) {
                        InfoRow(stringResource(R.string.file_permissions), permissions)
                    } else {
                        val file = File(item.path)
                        InfoRow(
                            stringResource(R.string.file_permissions),
                            if (file.canWrite()) stringResource(R.string.writable)
                            else stringResource(R.string.read_only),
                        )
                    }
                    InfoRow(stringResource(R.string.file_path), item.path)
                } else if (item.contentUri != null) {
                    InfoRow(stringResource(R.string.file_path), item.contentUri ?: "")
                }
                if (item.isHidden) InfoRow(stringResource(R.string.hidden), stringResource(R.string.hidden))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun OperationOverlay(operation: OperationState, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(operation.title) },
        text = {
            Column {
                if (operation.detail.isNotBlank()) {
                    Text(
                        operation.detail,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                val progress = operation.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel_operation)) }
        },
    )
}

@Composable
fun RefreshIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
    }
}

@Composable
fun SelectAllIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
    }
}
