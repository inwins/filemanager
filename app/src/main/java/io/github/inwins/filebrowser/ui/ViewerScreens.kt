package io.github.inwins.filebrowser.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.inwins.filebrowser.AppState
import io.github.inwins.filebrowser.R
import io.github.inwins.filebrowser.data.FileItem
import io.github.inwins.filebrowser.data.FileRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(state: AppState, screen: Screen.ImageViewer) {
    if (screen.models.isEmpty()) {
        LaunchedEffect(Unit) { state.back() }
        return
    }
    val pagerState = rememberPagerState(initialPage = screen.index.coerceIn(0, screen.models.lastIndex)) {
        screen.models.size
    }
    val current = screen.models[pagerState.currentPage.coerceIn(0, screen.models.lastIndex)]

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding(),
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                }
            },
            title = {
                Column {
                    Text(
                        nameOfModel(current),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${pagerState.currentPage + 1} / ${screen.models.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    state.share(listOf(modelToItem(current)))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share), tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.45f)),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            ZoomableImage(model = screen.models[page])
        }
    }
}

@Composable
private fun ZoomableImage(model: String) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = if (model.startsWith("content://")) Uri.parse(model) else File(model),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                )
                .pointerInput(model) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        offset = if (newScale <= 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(model) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
        )
    }
}

private fun nameOfModel(model: String): String {
    if (model.startsWith("content://")) {
        return model.substringAfterLast('/').substringAfterLast(':')
    }
    return File(model).name.ifBlank { model }
}

private fun modelToItem(model: String): FileItem {
    val isContent = model.startsWith("content://")
    val name = nameOfModel(model)
    val file = if (!isContent) File(model) else null
    return FileItem(
        path = if (isContent) "" else model,
        name = name,
        isDirectory = false,
        size = file?.length() ?: 0L,
        lastModified = file?.lastModified() ?: 0L,
        mime = io.github.inwins.filebrowser.util.MimeTypes.fromName(name),
        contentUri = if (isContent) model else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(state: AppState, path: String) {
    val file = remember(path) { File(path) }
    var text by remember(path) { mutableStateOf("") }
    var original by remember(path) { mutableStateOf("") }
    var loading by remember(path) { mutableStateOf(true) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dirty = !loading && error == null && text != original

    LaunchedEffect(path) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { state.repository.readText(file) }
        }
        result.onSuccess {
            text = it
            original = it
            loading = false
        }.onFailure {
            error = when ((it.message ?: "")) {
                "too large" -> state.appContext.getString(R.string.editor_too_large)
                "binary" -> state.appContext.getString(R.string.editor_binary)
                else -> state.appContext.getString(R.string.operation_failed)
            }
            loading = false
        }
    }

    fun leave() {
        if (dirty) showDiscard = true else state.back()
    }

    BackHandler(enabled = dirty) { showDiscard = true }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { leave() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = {
                Column {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (dirty) "● " else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            actions = {
                IconButton(
                    enabled = dirty && !saving,
                    onClick = {
                        saving = true
                        state.showMessage(state.appContext.getString(R.string.processing))
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching { state.repository.saveText(file, text) }.isSuccess
                            }
                            original = if (ok) text else original
                            saving = false
                            state.showMessage(
                                state.appContext.getString(if (ok) R.string.saved else R.string.operation_failed)
                            )
                        }
                    },
                ) {
                    Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                }
                IconButton(onClick = { state.openWith(modelToItem(path)) }) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.open_with))
                }
            },
        )
        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
                else -> BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    state.back()
                }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
