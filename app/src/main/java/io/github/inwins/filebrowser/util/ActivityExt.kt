package io.github.inwins.filebrowser.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Color.isDarkColor(): Boolean = luminance() < 0.45f
