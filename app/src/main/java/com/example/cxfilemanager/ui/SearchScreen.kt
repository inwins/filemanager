package com.example.cxfilemanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cxfilemanager.AppState
import com.example.cxfilemanager.R
import com.example.cxfilemanager.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(state: AppState, root: String) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    LaunchedEffect(query, root, state.settings.showHidden) {
        val text = query.trim()
        if (text.isEmpty()) {
            results = emptyList()
            searching = false
            scanned = 0
            return@LaunchedEffect
        }
        delay(280)
        searching = true
        scanned = 0
        val job = coroutineContext[Job]
        val found = withContext(Dispatchers.IO) {
            state.repository.search(
                root = root,
                query = text,
                showHidden = state.settings.showHidden,
                limit = 500,
                onProgress = { scanned = it },
                shouldContinue = { job?.isActive == true },
            )
        }
        results = found
        searching = false
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { state.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            title = { Text(stringResource(R.string.search)) },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; keyboard?.hide() }) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusRequester(focusRequester),
        )
        Text(
            stringResource(R.string.search_scope, root),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (searching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.analyzing_files, scanned),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.type_to_search),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                searching && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !searching && results.isEmpty() -> EmptyState(
                    Icons.Filled.FolderOpen,
                    stringResource(R.string.search_empty),
                    Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (results.size >= 500) {
                        item {
                            Text(
                                stringResource(R.string.search_limit, 500),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(results, key = { it.path.ifBlank { it.contentUri ?: it.name } }) { item ->
                        FileRow(
                            item = item,
                            selected = false,
                            selectionMode = false,
                            onClick = { state.openItem(item, results) },
                            onLongClick = { },
                            showPath = true,
                        )
                    }
                }
            }
        }
    }
}
