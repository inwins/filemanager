package io.github.inwins.filebrowser

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class FileBrowserApp : Application(), ImageLoaderFactory {
    lateinit var appState: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        appState = AppState(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
}
