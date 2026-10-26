package com.example.filebrowser.ui

import com.example.filebrowser.data.CategoryKind

sealed class Screen {
    data object Home : Screen()
    data class Browser(val path: String) : Screen()
    data object Categories : Screen()
    data class Category(val kind: CategoryKind) : Screen()
    data object Apps : Screen()
    data class Search(val root: String) : Screen()
    data object Settings : Screen()
    data class Analyzer(val path: String) : Screen()
    data class ImageViewer(val models: List<String>, val index: Int) : Screen()
    data class TextEditor(val path: String) : Screen()
    data class Reader(val path: String) : Screen()
    data object Changelog : Screen()
    data object Network : Screen()
    data class RemoteBrowser(val profileId: String, val path: String) : Screen()
    data object AudioPlayer : Screen()
    data class ArchivePreview(val path: String) : Screen()
    data class Cleaner(val path: String) : Screen()
    data object Trash : Screen()
    data class VideoPlayer(val path: String) : Screen()
}
