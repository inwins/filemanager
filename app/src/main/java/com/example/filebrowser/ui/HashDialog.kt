package com.example.filebrowser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.filebrowser.R
import com.example.filebrowser.util.Format
import com.example.filebrowser.util.HashResult
import com.example.filebrowser.util.Hasher
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Composable
fun HashDialog(
    name: String,
    totalBytes: Long,
    openStream: () -> InputStream,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<HashResult?>(null) }
    var progress by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(name) {
        val job = coroutineContext[Job]
        val hashes = withContext(Dispatchers.IO) {
            runCatching {
                Hasher.hash(
                    input = openStream(),
                    onProgress = { progress = it },
                    shouldContinue = { job?.isActive == true },
                )
            }.getOrNull()
        }
        if (hashes == null) failed = true else result = hashes
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hash_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                val hashes = result
                if (hashes != null) {
                    HashRow(stringResource(R.string.hash_md5), hashes.md5)
                    HashRow(stringResource(R.string.hash_sha1), hashes.sha1)
                    HashRow(stringResource(R.string.hash_sha256), hashes.sha256)
                } else if (failed) {
                    Text(
                        stringResource(R.string.hash_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = {
                            if (totalBytes > 0) (progress.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
                            else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.hash_computing) + " " +
                            Format.size(progress) +
                            if (totalBytes > 0) " / ${Format.size(totalBytes)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (result != null) {
                TextButton(onClick = {
                    val hashes = result ?: return@TextButton
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText(
                            name,
                            "MD5: ${hashes.md5}\nSHA-1: ${hashes.sha1}\nSHA-256: ${hashes.sha256}",
                        )
                    )
                }) {
                    Text(stringResource(R.string.copy))
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun HashRow(label: String, value: String) {
    Spacer(Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        value,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}
