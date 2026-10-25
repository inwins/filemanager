package com.example.filebrowser

import android.app.Application

class FileBrowserApp : Application() {
    lateinit var appState: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        appState = AppState(this)
    }
}
