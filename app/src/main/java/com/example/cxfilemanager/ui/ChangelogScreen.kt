package com.example.cxfilemanager.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cxfilemanager.AppState
import com.example.cxfilemanager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(state: AppState) {
    var content by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        content = withContext(Dispatchers.IO) {
            runCatching {
                state.appContext.resources.openRawResource(R.raw.changelog)
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrDefault("")
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = { Text(stringResource(R.string.changelog)) },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            content.lineSequence().forEach { line ->
                when {
                    line.isBlank() -> Text("", style = MaterialTheme.typography.bodyMedium)
                    line.matches(Regex("^v[0-9].*")) -> Text(
                        line,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                    )
                    else -> Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
