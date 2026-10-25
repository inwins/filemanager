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
    private var total = 0
    private var done = 0
    private val handler = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                ready = true
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val parsed = parseId(utteranceId) ?: return
                        if (parsed.first != generation) return
                        cancelWatchdog()
                        listener(TtsEvent.Started(parsed.third))
                    }

                    override fun onDone(utteranceId: String?) {
                        val parsed = parseId(utteranceId) ?: return
                        if (parsed.first != generation) return
                        done++
                        if (total > 0 && done >= total) {
                            total = 0
                            listener(TtsEvent.ChapterFinished)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val parsed = parseId(utteranceId) ?: return
                        if (parsed.first != generation) return
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

    fun speak(chunks: List<TtsChunk>, rate: Float, locale: Locale) {
        if (chunks.isEmpty()) return
        val action = { startSpeaking(chunks, rate, locale) }
        if (ready) action() else pending = action
    }

    private fun startSpeaking(chunks: List<TtsChunk>, rate: Float, locale: Locale) {
        val engine = tts
        if (engine == null) {
            listener(TtsEvent.Error(null))
            return
        }
        engine.stop()
        generation++
        val currentGeneration = generation
        total = chunks.size
        done = 0
        engine.setSpeechRate(rate.coerceIn(0.4f, 2.5f))
        engine.setPitch(1f)
        val language = engine.setLanguage(locale)
        if (language == TextToSpeech.LANG_MISSING_DATA ||
            language == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            val fallback = engine.setLanguage(Locale.getDefault())
            if (fallback == TextToSpeech.LANG_MISSING_DATA ||
                fallback == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                total = 0
                listener(TtsEvent.Error("language"))
                return
            }
        }
        var failed = false
        for ((index, chunk) in chunks.withIndex()) {
            if (failed) break
            val id = "cx:$currentGeneration:$index:${chunk.blockIndex}"
            val queue = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = engine.speak(chunk.text, queue, null, id)
            if (result == TextToSpeech.ERROR) {
                failed = true
                total = 0
                listener(TtsEvent.Error(null))
            }
        }
        if (!failed) scheduleWatchdog(currentGeneration)
    }

    fun stop() {
        generation++
        total = 0
        done = 0
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

    private fun scheduleWatchdog(generation: Int) {
        cancelWatchdog()
        val runnable = Runnable {
            if (generation == this.generation && total > 0 && done == 0) {
                tts?.stop()
                listener(TtsEvent.Error("no-start"))
            }
        }
        watchdog = runnable
        handler.postDelayed(runnable, 6000)
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
