package com.floatingclipboard.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RawOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.floatingclipboard.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.floatingclipboard.data.LogEntry
import com.floatingclipboard.data.LogLevel
import com.floatingclipboard.data.LogStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { LogStore.getInstance(context) }
    val entries by store.entries.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var rawDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title_count, entries.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { rawDialogOpen = true }) {
                        Icon(
                            Icons.Default.RawOn,
                            contentDescription = stringResource(R.string.logs_show_raw),
                        )
                    }
                    IconButton(onClick = { shareLogs(context, store.snapshot()) }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.logs_share),
                        )
                    }
                    IconButton(onClick = { store.clear() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.logs_clear),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.logs_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries) { entry -> LogRow(entry) }
            }
        }
    }

    if (rawDialogOpen) {
        val emptyMsg = stringResource(R.string.logs_raw_empty)
        val raw = remember(rawDialogOpen) { store.readLastRaw() ?: emptyMsg }
        AlertDialog(
            onDismissRequest = { rawDialogOpen = false },
            title = { Text(stringResource(R.string.logs_raw_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = raw,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    shareLogs(context, raw)
                    rawDialogOpen = false
                }) { Text(stringResource(R.string.logs_raw_share_button)) }
            },
            dismissButton = {
                TextButton(onClick = { rawDialogOpen = false }) {
                    Text(stringResource(R.string.logs_raw_close_button))
                }
            },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    // Errors and warnings dominate; everything else picks a colour from its tag so the eye can
    // group lines of the same conversation (LLM / Senses / OpenRouter / Chat …).
    val tagColor = when {
        entry.level == LogLevel.E -> MaterialTheme.colorScheme.error
        entry.level == LogLevel.W -> Color(0xFFE69500)
        entry.tag.equals("LLM", ignoreCase = true) -> MaterialTheme.colorScheme.primary
        entry.tag.equals("Senses", ignoreCase = true) -> Color(0xFF2E7D32)         // green 800
        entry.tag.equals("OpenRouter", ignoreCase = true) -> Color(0xFF7B4F9E)     // purple 700
        entry.tag.equals("Chat", ignoreCase = true) -> Color(0xFF1565C0)           // blue 800
        entry.tag.equals("TabsViewModel", ignoreCase = true) -> Color(0xFF5D4037)  // brown 700
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val messageColor = when (entry.level) {
        LogLevel.W -> Color(0xFFE69500)
        LogLevel.E -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Coloured left accent bar — quick visual grouping by tag without reading the label.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(3.dp)
                .androidx_height_intrinsic_min()
                .background(tagColor, MaterialTheme.shapes.extraSmall),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date(entry.timestampMs)),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = entry.level.name,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = tagColor,
                )
                Text(
                    text = entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = tagColor,
                    fontFamily = FontFamily.SansSerif,
                )
            }
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = messageColor,
            )
        }
    }
}

// Fixed minimum so the accent bar reads as a "row separator" even for single-line messages.
private fun Modifier.androidx_height_intrinsic_min(): Modifier =
    this.heightIn(min = 20.dp)

private fun shareLogs(context: Context, snapshot: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Floating Clipboard logs")
        putExtra(Intent.EXTRA_TEXT, snapshot)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_logs_chooser))
    )
}
