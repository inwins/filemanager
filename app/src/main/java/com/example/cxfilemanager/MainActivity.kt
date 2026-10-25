package com.example.cxfilemanager

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.cxfilemanager.ui.AppRoot
import com.example.cxfilemanager.ui.theme.CxFileManagerTheme

class MainActivity : ComponentActivity() {
    private lateinit var appState: AppState

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            appState.refreshPermission()
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            appState.refreshPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appState = (application as CxFileApp).appState
        appState.requestPermissionAction = { requestStoragePermission() }
        setContent {
            CxFileManagerTheme(settings = appState.settings) {
                AppRoot(appState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appState.isInitialized) appState.refreshPermission()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            val result = runCatching { settingsLauncher.launch(appIntent) }
            if (result.isFailure) {
                val allIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                runCatching { settingsLauncher.launch(allIntent) }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            )
        }
    }
}
