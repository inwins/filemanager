package com.example.filebrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filebrowser.AppState
import com.example.filebrowser.R
import com.example.filebrowser.data.AudioPlayer
import com.example.filebrowser.util.Lyrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(state: AppState) {
    val player = state.audioPlayer
    var showLyrics by remember { mutableStateOf(false) }
    BackHandler { state.back() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = { Text(stringResource(R.string.audio_now_playing)) },
            actions = {
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        Icons.Filled.Lyrics,
                        contentDescription = stringResource(R.string.lyrics),
                        tint = if (showLyrics) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    player.stop()
                    state.back()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.audio_close))
                }
            },
        )

        val current = player.current
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.audio_no_track),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (showLyrics) {
                LyricsPane(
                    player = player,
                    onShowCover = { showLyrics = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                )
            } else {
                CoverPane(
                    player = player,
                    current = current,
                    onShowLyrics = { showLyrics = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                )
            }
        }

        PlayerControls(player)
    }
}

@Composable
private fun CoverPane(
    player: AudioPlayer,
    current: com.example.filebrowser.data.FileItem,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        val bitmap = player.artwork
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(248.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(248.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(100.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            player.title.ifBlank { current.name.substringBeforeLast('.') },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            player.artist.ifBlank { stringResource(R.string.audio_unknown_artist) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (player.album.isNotBlank()) {
            Text(
                player.album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }

        val currentLine = player.lyrics
            ?.takeIf { it.synced }
            ?.let { it.lines.getOrNull(it.indexAt(player.positionMs))?.text }
            ?.takeIf { it.isNotBlank() }
        if (currentLine != null) {
            Text(
                currentLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowLyrics)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.audio_playlist),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(player.queue, key = { it.path.ifBlank { it.name } + it.lastModified }) { item ->
                val index = player.queue.indexOf(item)
                val selected = index == player.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { player.playAt(index) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item.name.substringBeforeLast('.'),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsPane(
    player: AudioPlayer,
    onShowCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lyrics = player.lyrics
    val currentIndex = lyrics
        ?.takeIf { it.synced }
        ?.indexAt(player.positionMs)
        ?: -1
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowCover)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                player.title.ifBlank { player.current?.name?.substringBeforeLast('.') ?: "" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                player.artist.ifBlank { stringResource(R.string.audio_unknown_artist) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        when {
            player.lyricsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            lyrics == null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.lyrics_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            !lyrics.synced -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                itemsIndexed(lyrics.lines) { _, line ->
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(currentIndex) {
                    if (currentIndex >= 0) listState.animateScrollToItem(currentIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 120.dp),
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val active = index == currentIndex
                        Text(
                            line.text.ifBlank { "♪" },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = if (active) 20.sp else 16.sp,
                            ),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(player: AudioPlayer) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
    ) {
        val duration = player.durationMs.coerceAtLeast(1L)
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val position = if (dragging) dragValue.toLong() else player.positionMs
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                player.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = { player.previous() }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.audio_previous),
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            FilledIconButton(
                onClick = { player.toggle() },
                modifier = Modifier.size(68.dp),
            ) {
                Icon(
                    if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (player.isPlaying) R.string.audio_pause else R.string.audio_play
                    ),
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { player.next() }) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.audio_next),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
