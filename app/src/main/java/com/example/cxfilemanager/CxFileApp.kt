package com.example.cxfilemanager

import android.app.Application

class CxFileApp : Application() {
    lateinit var appState: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        appState = AppState(this)
    }
}
