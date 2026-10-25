package com.example.cxfilemanager.ui

import com.example.cxfilemanager.data.CategoryKind

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
}
