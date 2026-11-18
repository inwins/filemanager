package io.github.inwins.filebrowser.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioPlayerTest {
    @Test
    fun initializesStreamingCacheAndStops() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val player = AudioPlayer(context) {}
        assertFalse(player.isPlaying)
        assertTrue(File(context.cacheDir, "audio_stream_cache").exists())
        player.stop()
        assertTrue(player.queue.isEmpty())
    }
}
