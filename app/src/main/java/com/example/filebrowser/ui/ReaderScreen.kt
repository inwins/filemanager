package com.example.filebrowser.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.filebrowser.AppState
import com.example.filebrowser.R
import com.example.filebrowser.data.ReaderSettings
import com.example.filebrowser.data.ReaderTheme
import com.example.filebrowser.data.TextBookmark
import com.example.filebrowser.data.TtsController
import com.example.filebrowser.data.TtsEvent
import com.example.filebrowser.data.TxtBook
import com.example.filebrowser.util.Format
import com.example.filebrowser.util.TxtParser
import com.example.filebrowser.util.findActivity
import com.example.filebrowser.util.isDarkColor
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TtsStatus { IDLE, PLAYING, PAUSED }

private data class ReaderPalette(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val surface: Color,
    val dark: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(state: AppState, path: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val file = remember(path) { File(path) }
    val defaultTitle = stringResource(R.string.reader_default_chapter)
    val prefaceTitle = stringResource(R.string.reader_preface)
    val settings = state.readerSettings
    val palette = readerPalette(settings.theme)

    var book by remember(path) { mutableStateOf<TxtBook?>(null) }
    var loading by remember(path) { mutableStateOf(true) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var bookmarks by remember(path) { mutableStateOf(state.prefs.loadReaderBookmarks(path)) }
    var showChapters by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var ttsStatus by remember { mutableStateOf(TtsStatus.IDLE) }
    var ttsBlock by remember { mutableStateOf(-1) }
    var ttsChapter by remember { mutableStateOf(0) }
    var chapterFinished by remember { mutableIntStateOf(0) }
    var ttsError by remember { mutableStateOf(false) }
    var ttsHintShown by remember { mutableStateOf(false) }
    var currentBlock by remember { mutableIntStateOf(0) }
    var restored by remember(path) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val savedOffset = remember(path) { state.prefs.loadReadingProgress(path) }

    val controller = remember {
        TtsController(context) { event ->
            when (event) {
                is TtsEvent.Ready -> Unit
                is TtsEvent.Started -> {
                    ttsBlock = event.blockIndex
                    val currentBook = book
                    if (currentBook != null) {
                        ttsChapter = currentBook.chapterForBlock(event.blockIndex)
                        val visible = listState.layoutInfo.visibleItemsInfo
                        val first = visible.firstOrNull()?.index ?: 0
                        val last = visible.lastOrNull()?.index ?: 0
                        if (event.blockIndex < first || event.blockIndex > last) {
                            scope.launch { listState.animateScrollToItem(event.blockIndex) }
                        }
                    }
                }
                is TtsEvent.ChapterFinished -> chapterFinished++
                is TtsEvent.Error -> {
                    ttsStatus = TtsStatus.IDLE
                    ttsBlock = -1
                    ttsError = true
                }
            }
        }
    }

    fun startReading(chapterIndex: Int, startBlock: Int) {
        val currentBook = book ?: return
        val chapter = currentBook.chapters.getOrNull(chapterIndex) ?: return
        val chunks = TxtParser.ttsChunks(currentBook, chapterIndex, startBlock)
        if (chunks.isEmpty()) {
            state.showMessage(context.getString(R.string.reader_empty))
            return
        }
        ttsChapter = chapter.index
        ttsStatus = TtsStatus.PLAYING
        ttsBlock = chunks.first().blockIndex
        if (!ttsHintShown) {
            ttsHintShown = true
            state.showMessage(context.getString(R.string.reader_tts_hint))
        }
        val sample = currentBook.blocks.getOrNull(ttsBlock)?.let { currentBook.blockText(it) }.orEmpty()
        controller.speak(chunks, state.readerSettings.ttsRate, localeFor(sample))
    }

    fun toggleTts() {
        when (ttsStatus) {
            TtsStatus.PLAYING -> {
                controller.stop()
                ttsStatus = TtsStatus.PAUSED
            }
            TtsStatus.PAUSED, TtsStatus.IDLE -> {
                val currentBook = book ?: return
                val startBlock = listState.firstVisibleItemIndex
                    .coerceIn(0, currentBook.blocks.lastIndex.coerceAtLeast(0))
                currentBlock = startBlock
                startReading(currentBook.chapterForBlock(startBlock), startBlock)
            }
        }
    }

    fun addBookmark() {
        val currentBook = book ?: return
        val block = currentBook.blocks.getOrNull(currentBlock) ?: return
        if (bookmarks.any { it.offset == block.start }) {
            state.showMessage(context.getString(R.string.reader_bookmark_exists))
            return
        }
        val chapter = currentBook.chapters.getOrNull(block.chapterIndex)
        val bookmark = TextBookmark(
            id = UUID.randomUUID().toString(),
            chapterIndex = block.chapterIndex,
            chapterTitle = chapter?.title.orEmpty(),
            offset = block.start,
            preview = currentBook.blockText(block).take(80).replace('\n', ' '),
            createdAt = System.currentTimeMillis(),
        )
        bookmarks = (bookmarks + bookmark).sortedBy { it.offset }
        state.prefs.saveReaderBookmarks(path, bookmarks)
        state.showMessage(context.getString(R.string.reader_bookmark_added))
    }

    fun removeBookmark(bookmark: TextBookmark) {
        bookmarks = bookmarks.filterNot { it.id == bookmark.id }
        state.prefs.saveReaderBookmarks(path, bookmarks)
        state.showMessage(context.getString(R.string.reader_bookmark_removed))
    }

    fun jumpToOffset(offset: Int) {
        val currentBook = book ?: return
        scope.launch { listState.animateScrollToItem(currentBook.blockIndexForOffset(offset)) }
    }

    fun jumpToChapter(chapterIndex: Int) {
        val chapter = book?.chapters?.getOrNull(chapterIndex) ?: return
        scope.launch { listState.animateScrollToItem(chapter.firstBlock) }
    }

    fun openTtsSettings() {
        val intents = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return
        }
        state.showMessage(context.getString(R.string.reader_tts_failed))
    }

    LaunchedEffect(path) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = state.repository.readText(file, 30L * 1024 * 1024)
                TxtParser.parse(text, defaultTitle, prefaceTitle)
            }
        }
        result.onSuccess {
            book = it
            loading = false
        }.onFailure {
            error = when (it.message) {
                "too large" -> context.getString(R.string.reader_too_large)
                "binary" -> context.getString(R.string.editor_binary)
                else -> context.getString(R.string.operation_failed)
            }
            loading = false
        }
    }

    LaunchedEffect(book, restored) {
        val currentBook = book ?: return@LaunchedEffect
        if (!restored) {
            val index = currentBook.blockIndexForOffset(savedOffset)
            if (index > 0) listState.scrollToItem(index)
            currentBlock = index
            restored = true
        }
    }

    LaunchedEffect(listState, book) {
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { index ->
            currentBlock = index
            delay(900)
            val currentBook = book ?: return@collectLatest
            currentBook.blocks.getOrNull(index)?.let {
                state.prefs.saveReadingProgress(path, it.start)
            }
        }
    }

    LaunchedEffect(chapterFinished) {
        if (chapterFinished == 0) return@LaunchedEffect
        val currentBook = book ?: return@LaunchedEffect
        if (state.readerSettings.ttsAutoNext && ttsChapter + 1 < currentBook.chapters.size) {
            val next = currentBook.chapters[ttsChapter + 1]
            startReading(next.index, next.firstBlock)
            listState.animateScrollToItem(next.firstBlock)
        } else {
            ttsStatus = TtsStatus.IDLE
            ttsBlock = -1
        }
    }

    val latestBook by rememberUpdatedState(book)
    val latestBlock by rememberUpdatedState(currentBlock)
    DisposableEffect(controller) {
        onDispose {
            latestBook?.blocks?.getOrNull(latestBlock)?.let {
                state.prefs.saveReadingProgress(path, it.start)
            }
            controller.shutdown()
        }
    }

    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !palette.dark
            isAppearanceLightNavigationBars = !palette.dark
        }
    }

    val currentChapterIndex = book?.chapterForBlock(currentBlock) ?: 0
    val currentChapter = book?.chapters?.getOrNull(currentChapterIndex)
    val percent = book?.percentForBlock(currentBlock) ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .navigationBarsPadding(),
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = {
                    controller.stop()
                    state.back()
                }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = {
                Column {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = when {
                        loading -> stringResource(R.string.reader_loading)
                        error != null -> stringResource(R.string.reader_open_failed)
                        currentChapter != null -> stringResource(
                            R.string.reader_chapter_progress,
                            percent,
                            currentChapterIndex + 1,
                            book?.chapters?.size ?: 0,
                        )
                        else -> ""
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondary,
                        maxLines = 1,
                    )
                }
            },
            actions = {
                IconButton(onClick = { toggleTts() }) {
                    Icon(
                        when (ttsStatus) {
                            TtsStatus.PLAYING -> Icons.Filled.Pause
                            TtsStatus.PAUSED -> Icons.Filled.PlayArrow
                            TtsStatus.IDLE -> Icons.Filled.VolumeUp
                        },
                        contentDescription = stringResource(R.string.reader_tts),
                    )
                }
                IconButton(onClick = { showBookmarks = true }) {
                    Icon(Icons.Filled.Bookmark, contentDescription = stringResource(R.string.reader_bookmarks))
                }
                IconButton(onClick = { showChapters = true }) {
                    Icon(Icons.Filled.List, contentDescription = stringResource(R.string.reader_chapters))
                }
                IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = palette.surface,
                titleContentColor = palette.text,
                navigationIconContentColor = palette.text,
                actionIconContentColor = palette.text,
            ),
        )

        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.accent)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.reader_loading), color = palette.secondary)
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(error ?: "", color = palette.text, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { state.navigate(Screen.TextEditor(path)) }) {
                            Text(stringResource(R.string.reader_edit))
                        }
                    }
                }
                book == null || book!!.blocks.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.reader_empty), color = palette.secondary)
                }
                else -> {
                    val currentBook = book!!
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(currentBook.blocks, key = { it.index }) { block ->
                            val text = currentBook.blockText(block)
                            if (block.heading) {
                                Text(
                                    text,
                                    color = palette.text,
                                    fontSize = (settings.fontSize + 5).sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = ((settings.fontSize + 5) * 1.4f).sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 18.dp, bottom = 6.dp),
                                )
                                HorizontalDivider(color = palette.secondary.copy(alpha = 0.18f))
                            } else {
                                val highlight = ttsStatus != TtsStatus.IDLE && block.index == ttsBlock
                                Text(
                                    text,
                                    color = palette.text,
                                    fontSize = settings.fontSize.sp,
                                    lineHeight = (settings.fontSize * settings.lineHeight).sp,
                                    textAlign = TextAlign.Justify,
                                    style = androidx.compose.ui.text.TextStyle(
                                        textIndent = TextIndent(firstLine = 2.em),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (highlight) palette.accent.copy(alpha = 0.14f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 2.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        ReaderBottomBar(
            palette = palette,
            percent = percent,
            chapterTitle = currentChapter?.title.orEmpty(),
            chapterIndex = currentChapterIndex,
            chapterCount = book?.chapters?.size ?: 0,
            ttsStatus = ttsStatus,
            onToggleTts = { toggleTts() },
            onStopTts = {
                controller.stop()
                ttsStatus = TtsStatus.IDLE
                ttsBlock = -1
            },
        )
    }

    if (showChapters) {
        ChaptersSheet(
            book = book,
            currentChapter = currentChapterIndex,
            palette = palette,
            onJump = { index ->
                jumpToChapter(index)
                showChapters = false
            },
            onDismiss = { showChapters = false },
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            palette = palette,
            onAdd = {
                addBookmark()
                showBookmarks = false
            },
            onJump = { bookmark ->
                jumpToOffset(bookmark.offset)
                showBookmarks = false
            },
            onDelete = { removeBookmark(it) },
            onDismiss = { showBookmarks = false },
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = state.readerSettings,
            onUpdate = { state.updateReaderSettings(it) },
            onEdit = {
                controller.stop()
                ttsStatus = TtsStatus.IDLE
                ttsBlock = -1
                showSettings = false
                state.navigate(Screen.TextEditor(path))
            },
            onRestartTts = {
                if (ttsStatus == TtsStatus.PLAYING) {
                    startReading(ttsChapter, ttsBlock.coerceAtLeast(0))
                }
            },
            onOpenTtsSettings = { openTtsSettings() },
            onDismiss = { showSettings = false },
        )
    }

    if (showMore) {
        ActionSheet(
            title = file.name,
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.reader_settings),
                    icon = Icons.Filled.Settings,
                    onClick = { showSettings = true },
                ),
                SheetAction(
                    label = stringResource(R.string.reader_edit),
                    icon = Icons.Filled.Edit,
                    onClick = {
                        controller.stop()
                        ttsStatus = TtsStatus.IDLE
                        ttsBlock = -1
                        state.navigate(Screen.TextEditor(path))
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.reader_add_bookmark),
                    icon = Icons.Filled.BookmarkAdd,
                    onClick = { addBookmark() },
                ),
            ),
            onDismiss = { showMore = false },
        )
    }

    if (ttsError) {
        AlertDialog(
            onDismissRequest = { ttsError = false },
            title = { Text(stringResource(R.string.reader_tts_failed)) },
            text = { Text(stringResource(R.string.reader_tts_error_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    ttsError = false
                    openTtsSettings()
                }) { Text(stringResource(R.string.reader_tts_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { ttsError = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun ReaderBottomBar(
    palette: ReaderPalette,
    percent: Int,
    chapterTitle: String,
    chapterIndex: Int,
    chapterCount: Int,
    ttsStatus: TtsStatus,
    onToggleTts: () -> Unit,
    onStopTts: () -> Unit,
) {
    Surface(color = palette.surface) {
        Column {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = palette.accent,
                trackColor = palette.text.copy(alpha = 0.1f),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        chapterTitle,
                        color = palette.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.reader_bottom_progress,
                            percent,
                            chapterIndex + 1,
                            chapterCount,
                        ),
                        color = palette.secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (ttsStatus != TtsStatus.IDLE) {
                    IconButton(onClick = onToggleTts) {
                        Icon(
                            if (ttsStatus == TtsStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (ttsStatus == TtsStatus.PLAYING) R.string.reader_pause
                                else R.string.reader_resume
                            ),
                            tint = palette.accent,
                        )
                    }
                    IconButton(onClick = onStopTts) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.reader_stop),
                            tint = palette.secondary,
                        )
                    }
                } else {
                    IconButton(onClick = onToggleTts) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.reader_tts),
                            tint = palette.accent,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersSheet(
    book: TxtBook?,
    currentChapter: Int,
    palette: ReaderPalette,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.reader_chapters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.reader_chapter_count, book?.chapters?.size ?: 0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(book?.chapters.orEmpty(), key = { it.index }) { chapter ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (chapter.index == currentChapter) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onJump(chapter.index) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (chapter.index + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(40.dp),
                        )
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<TextBookmark>,
    palette: ReaderPalette,
    onAdd: () -> Unit,
    onJump: (TextBookmark) -> Unit,
    onDelete: (TextBookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.reader_bookmarks),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAdd) {
                    Text(stringResource(R.string.reader_add_bookmark))
                }
            }
            if (bookmarks.isEmpty()) {
                Text(
                    stringResource(R.string.reader_no_bookmarks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onJump(bookmark) }
                                .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bookmark.chapterTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    bookmark.preview.ifBlank { bookmark.chapterTitle },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    Format.date(bookmark.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(bookmark) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.reader_delete_bookmark),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onUpdate: (ReaderSettings) -> Unit,
    onEdit: () -> Unit,
    onRestartTts: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
        Text(
            stringResource(R.string.reader_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Text(stringResource(R.string.reader_theme), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { onUpdate(settings.copy(theme = theme)) },
                    label = { Text(readerThemeLabel(theme)) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingSlider(
            title = stringResource(R.string.reader_font_size),
            value = settings.fontSize,
            valueRange = 14f..30f,
            steps = 15,
            label = "${settings.fontSize.toInt()} sp",
            onChange = { onUpdate(settings.copy(fontSize = it)) },
        )
        SettingSlider(
            title = stringResource(R.string.reader_line_spacing),
            value = settings.lineHeight,
            valueRange = 1.2f..2.4f,
            steps = 11,
            label = String.format(Locale.US, "%.1f", settings.lineHeight),
            onChange = { onUpdate(settings.copy(lineHeight = it)) },
        )
        SettingSlider(
            title = stringResource(R.string.reader_tts_rate),
            value = settings.ttsRate,
            valueRange = 0.4f..2.5f,
            steps = 20,
            label = String.format(Locale.US, "%.1fx", settings.ttsRate),
            onChange = { onUpdate(settings.copy(ttsRate = it)) },
            onFinished = onRestartTts,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.reader_tts_auto_next),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = settings.ttsAutoNext,
                onCheckedChange = { onUpdate(settings.copy(ttsAutoNext = it)) },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { onUpdate(ReaderSettings()) }) {
                Text(stringResource(R.string.reader_reset))
            }
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.reader_edit))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
        TextButton(
            onClick = onOpenTtsSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reader_tts_settings))
        }
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String,
    onChange: (Float) -> Unit,
    onFinished: (() -> Unit)? = null,
) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onFinished,
        )
    }
}

@Composable
private fun readerThemeLabel(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.SYSTEM -> stringResource(R.string.reader_theme_system)
    ReaderTheme.PAPER -> stringResource(R.string.reader_theme_paper)
    ReaderTheme.SEPIA -> stringResource(R.string.reader_theme_sepia)
    ReaderTheme.EYE -> stringResource(R.string.reader_theme_eye)
    ReaderTheme.DARK -> stringResource(R.string.reader_theme_dark)
}

@Composable
private fun readerPalette(theme: ReaderTheme): ReaderPalette {
    return when (theme) {
        ReaderTheme.SYSTEM -> ReaderPalette(
            background = MaterialTheme.colorScheme.background,
            text = MaterialTheme.colorScheme.onBackground,
            secondary = MaterialTheme.colorScheme.onSurfaceVariant,
            accent = MaterialTheme.colorScheme.primary,
            surface = MaterialTheme.colorScheme.surface,
            dark = MaterialTheme.colorScheme.background.isDarkColor(),
        )
        ReaderTheme.PAPER -> ReaderPalette(
            background = Color(0xFFF8F4EA),
            text = Color(0xFF2E2A24),
            secondary = Color(0xFF746A5A),
            accent = Color(0xFF1565C0),
            surface = Color(0xFFEFE8D8),
            dark = false,
        )
        ReaderTheme.SEPIA -> ReaderPalette(
            background = Color(0xFFEEDDC2),
            text = Color(0xFF4A3B28),
            secondary = Color(0xFF7A6542),
            accent = Color(0xFF8D5A2B),
            surface = Color(0xFFE3CFAE),
            dark = false,
        )
        ReaderTheme.EYE -> ReaderPalette(
            background = Color(0xFFCCE8CF),
            text = Color(0xFF1E3A22),
            secondary = Color(0xFF4F6B53),
            accent = Color(0xFF2E7D32),
            surface = Color(0xFFB7DCBF),
            dark = false,
        )
        ReaderTheme.DARK -> ReaderPalette(
            background = Color(0xFF101114),
            text = Color(0xFFD6D6D6),
            secondary = Color(0xFF9A9A9A),
            accent = Color(0xFF7FB3FF),
            surface = Color(0xFF1B1D21),
            dark = true,
        )
    }
}

private fun localeFor(text: String): Locale {
    val hasChinese = text.any { it.code in 0x4E00..0x9FFF }
    return if (hasChinese) Locale.SIMPLIFIED_CHINESE else Locale.getDefault()
}
