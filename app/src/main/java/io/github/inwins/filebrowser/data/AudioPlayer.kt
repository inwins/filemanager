package io.github.inwins.filebrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.inwins.filebrowser.util.LrcParser
import io.github.inwins.filebrowser.util.Lyrics
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioPlayer(
    context: Context,
    private val onError: (String) -> Unit,
) {
    val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    var queue by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var current by mutableStateOf<FileItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var artwork by mutableStateOf<Bitmap?>(null)
        private set
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var metadataJob: Job? = null
    private var lyricsJob: Job? = null
    private var ticker: Job? = null

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exo.duration.coerceAtLeast(0)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exo.currentMediaItemIndex
                current = queue.getOrNull(currentIndex)
                current?.let {
                    loadMetadata(it)
                    loadLyrics(it)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(error.message ?: "Playback failed")
            }
        })
        ticker = scope.launch {
            while (isActive) {
                if (exo.mediaItemCount > 0) {
                    positionMs = exo.currentPosition.coerceAtLeast(0)
                    val duration = exo.duration
                    if (duration > 0) durationMs = duration
                }
                delay(400)
            }
        }
    }

    fun play(items: List<FileItem>, startIndex: Int) {
        val playable = items.filter {
            it.isAudio && (it.path.isNotBlank() || it.contentUri != null)
        }
        if (playable.isEmpty()) {
            onError("No playable audio")
            return
        }
        val item = items.getOrNull(startIndex)
        val index = playable.indexOfFirst { it.path == item?.path }.coerceAtLeast(0)
        queue = playable
        current = playable[index]
        currentIndex = index
        val mediaItems = playable.map { entry ->
            val uri = if (entry.path.isNotBlank()) Uri.fromFile(File(entry.path))
            else Uri.parse(entry.contentUri ?: "")
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(entry.name.substringBeforeLast('.'))
                        .build()
                )
                .build()
        }
        exo.setMediaItems(mediaItems, index, 0L)
        exo.prepare()
        exo.playWhenReady = true
        loadMetadata(playable[index])
        loadLyrics(playable[index])
    }

    fun toggle() {
        if (!exo.isPlaying) {
            if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
            exo.play()
        } else {
            exo.pause()
        }
    }

    fun next() {
        if (exo.hasNextMediaItem()) exo.seekToNextMediaItem()
    }

    fun previous() {
        if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem()
    }

    fun seekTo(position: Long) {
        exo.seekTo(position.coerceAtLeast(0))
        positionMs = position
    }

    fun playAt(index: Int) {
        if (index in queue.indices) {
            exo.seekTo(index, 0L)
            exo.playWhenReady = true
        }
    }

    fun stop() {
        exo.stop()
        exo.clearMediaItems()
        queue = emptyList()
        current = null
        currentIndex = -1
        isPlaying = false
        isBuffering = false
        positionMs = 0
        durationMs = 0
        title = ""
        artist = ""
        album = ""
        artwork = null
        lyrics = null
        lyricsLoading = false
        metadataJob?.cancel()
        lyricsJob?.cancel()
    }

    private fun loadLyrics(item: FileItem) {
        lyricsJob?.cancel()
        lyrics = null
        lyricsLoading = true
        val audioFile = if (item.path.isNotBlank()) File(item.path) else null
        if (audioFile == null) {
            lyricsLoading = false
            return
        }
        lyricsJob = scope.launch(Dispatchers.IO) {
            val file = findLyricsFile(audioFile)
            if (file != null) {
                val parsed = runCatching { LrcParser.parse(readLyricsText(file)) }.getOrNull()
                lyrics = parsed
            }
            lyricsLoading = false
        }
    }

    private fun findLyricsFile(audio: File): File? {
        val directory = audio.parentFile ?: return null
        val base = audio.nameWithoutExtension
        return directory.listFiles()?.firstOrNull { candidate ->
            !candidate.isDirectory &&
                candidate.extension.equals("lrc", ignoreCase = true) &&
                candidate.nameWithoutExtension.equals(base, ignoreCase = true)
        }
    }

    private fun readLyricsText(file: File): String {
        val bytes = file.readBytes()
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        val invalid = utf8.count { it == '\uFFFD' }.toDouble() / utf8.length.coerceAtLeast(1)
        return if (invalid > 0.01) String(bytes, Charset.forName("GBK")) else utf8
    }

    private fun loadMetadata(item: FileItem) {
        metadataJob?.cancel()
        title = item.name.substringBeforeLast('.')
        artist = ""
        album = ""
        artwork = null
        if (item.path.isBlank()) return
        metadataJob = scope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(item.path)
                val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val picture = retriever.embeddedPicture
                val bitmap = picture?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
                if (metaTitle?.isNotBlank() == true) title = metaTitle
                artist = metaArtist.orEmpty()
                album = metaAlbum.orEmpty()
                artwork = bitmap
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }
        }
    }
}
