package com.example.cxfilemanager.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsController(
    context: Context,
    private val listener: (TtsEvent) -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var generation = 0
    private var chunks: List<TtsChunk> = emptyList()
    private var index = 0
    private var rate = 1f
    private var locale: Locale = Locale.getDefault()
    private val handler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null
    private var chunkStarted = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                ready = true
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val parsed = parseId(utteranceId) ?: return
                        if (parsed.first != generation) return
                        chunkStarted = true
                        cancelWatchdog()
                        val current = chunks.getOrNull(index)
                        if (current != null) {
                            scheduleCompletionWatchdog(current.text)
                            listener(TtsEvent.Started(current.blockIndex))
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        val parsed = parseId(utteranceId) ?: return
                        if (parsed.first != generation) return
                        cancelWatchdog()
                        index++
                        speakCurrent()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val parsed = parseId(utteranceId) ?: return
                        if (parsed.first != generation) return
                        cancelWatchdog()
                        listener(TtsEvent.Error(null))
                    }
                })
                listener(TtsEvent.Ready)
                val action = pending
                pending = null
                action?.invoke()
            } else {
                listener(TtsEvent.Error("init"))
            }
        }
    }

    fun speak(newChunks: List<TtsChunk>, rate: Float, locale: Locale) {
        if (newChunks.isEmpty()) return
        val action = { startSpeaking(newChunks, rate, locale) }
        if (ready) action() else pending = action
    }

    private fun startSpeaking(newChunks: List<TtsChunk>, newRate: Float, newLocale: Locale) {
        val engine = tts
        if (engine == null) {
            listener(TtsEvent.Error(null))
            return
        }
        engine.stop()
        cancelWatchdog()
        generation++
        chunks = newChunks
        index = 0
        rate = newRate.coerceIn(0.4f, 2.5f)
        locale = newLocale
        engine.setSpeechRate(rate)
        engine.setPitch(1f)
        val language = engine.setLanguage(locale)
        if (language == TextToSpeech.LANG_MISSING_DATA ||
            language == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val fallback = engine.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA ||
                fallback == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                listener(TtsEvent.Error("language"))
                return
            }
        }
        speakCurrent()
    }

    private fun speakCurrent() {
        val engine = tts
        if (engine == null) {
            listener(TtsEvent.Error(null))
            return
        }
        if (index >= chunks.size) {
            cancelWatchdog()
            listener(TtsEvent.ChapterFinished)
            return
        }
        val chunk = chunks[index]
        val currentGeneration = generation
        chunkStarted = false
        val id = "cx:$currentGeneration:$index:${chunk.blockIndex}"
        val result = engine.speak(chunk.text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            listener(TtsEvent.Error(null))
            return
        }
        scheduleStartWatchdog(currentGeneration, index, chunk.text)
    }

    private fun scheduleStartWatchdog(generation: Int, chunkIndex: Int, text: String) {
        cancelWatchdog()
        val runnable = Runnable {
            if (generation == this.generation && index == chunkIndex && !chunkStarted) {
                tts?.stop()
                listener(TtsEvent.Error("no-start"))
            }
        }
        watchdog = runnable
        val delay = (5000L + text.length * 120L).coerceAtMost(30000L)
        handler.postDelayed(runnable, delay)
    }

    private fun scheduleCompletionWatchdog(text: String) {
        cancelWatchdog()
        val runnable = Runnable {
            tts?.stop()
            listener(TtsEvent.Error("timeout"))
        }
        watchdog = runnable
        val delay = (8000L + (text.length * 300L / rate).toLong()).coerceIn(10000L, 120000L)
        handler.postDelayed(runnable, delay)
    }

    fun stop() {
        generation++
        chunks = emptyList()
        index = 0
        pending = null
        cancelWatchdog()
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun cancelWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    private fun parseId(id: String?): Triple<Int, Int, Int>? {
        if (id == null) return null
        val parts = id.split(':')
        if (parts.size != 4) return null
        val generation = parts[1].toIntOrNull() ?: return null
        val index = parts[2].toIntOrNull() ?: return null
        val block = parts[3].toIntOrNull() ?: return null
        return Triple(generation, index, block)
    }
}
