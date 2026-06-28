package com.langoclip.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.langoclip.app.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.langoclip.app.actions.Action
import com.langoclip.app.actions.ActionResult
import com.langoclip.app.data.Tab

/**
 * Paste tab view (first, non-closeable). Text editing + actions + inline Translate.
 * Explain does NOT show its result here — it creates a new tab via [onExplain].
 */
@Composable
fun PasteTabContent(
    tab: Tab.Paste,
    readClipboard: () -> String,
    writeClipboard: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onExplain: () -> Unit,
    onTranslateWord: (word: String, sentence: String) -> Unit,
    onClearAll: () -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tfValue by remember { mutableStateOf(TextFieldValue(tab.text)) }
    // Sync when tab.text changes externally (e.g. clipboard auto-fill resets text).
    LaunchedEffect(tab.text) {
        if (tfValue.text != tab.text) tfValue = TextFieldValue(tab.text)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && tab.text.isBlank()) {
                onTextChange(readClipboard())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Derive selected single word from current selection (null if multi-word or no selection).
    val selectedWord = remember(tfValue.selection, tfValue.text) {
        val sel = tfValue.selection
        if (sel.collapsed) null
        else {
            val s = tfValue.text.substring(
                sel.start.coerceAtLeast(0),
                sel.end.coerceAtMost(tfValue.text.length),
            ).trim()
            if (s.isNotBlank() && ' ' !in s) s else null
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Hero — larger, friendlier title than the old labelSmall section header.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.paste_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Wklej angielski tekst lub zaznacz słowo żeby je przetłumaczyć.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Text input as a Card so it visually anchors the screen instead of looking like a
        // utility input. Empty state shows a multi-line placeholder explaining what to do.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                if (tfValue.text.isEmpty()) {
                    Text(
                        text = "Wklej tutaj angielski tekst…\n\nApka rozbierze go na czynniki: konstrukcje czasowe, idiomy, znaczenia poszczególnych słów.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.heightIn(min = 140.dp),
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = tfValue,
                    onValueChange = { newValue ->
                        tfValue = newValue
                        if (newValue.text != tab.text) onTextChange(newValue.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.primary
                    ),
                )
                if (tab.text.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.paste_clear_text_and_result),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Action buttons — bigger, more affordance. Primary (Translate) takes weight=1, secondary
        // (Explain) too; word-translate sits on its own row when present.
        val canRun = tab.text.isNotBlank() && tab.actionState !is ActionState.Loading
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onTranslate,
                enabled = canRun,
                shape = MaterialTheme.shapes.medium,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_translate),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            androidx.compose.material3.OutlinedButton(
                onClick = onExplain,
                enabled = canRun,
                shape = MaterialTheme.shapes.medium,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.action_explain),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        if (selectedWord != null) {
            androidx.compose.material3.FilledTonalButton(
                onClick = { onTranslateWord(selectedWord, tab.text) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                Text(
                    text = "Przetłumacz: \"$selectedWord\"  →",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        if (tab.actionState !is ActionState.Idle) {
            // Clear-result chip — only visible when there's something to clear.
            androidx.compose.material3.TextButton(
                onClick = onClearResult,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.paste_clear_result))
            }
        }

        TranslatePanel(
            state = tab.actionState,
            onCopy = writeClipboard,
            onRetry = onTranslate,
        )
    }
}

/**
 * Renders ONLY the Translate results (inline in the Paste tab). For Explain returns Unit — the
 * result lives in a separate tab, not here.
 */
@Composable
private fun TranslatePanel(
    state: ActionState,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ActionState.Idle -> Unit

        is ActionState.Loading -> {
            if (state.action != Action.TRANSLATE) return
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.loading_processing))
                }
                state.partialText?.takeIf { it.isNotBlank() }?.let { partial ->
                    SelectionContainer {
                        Text(
                            text = partial,
                            fontFamily = FontFamily.SansSerif,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is ActionState.Success -> {
            if (state.action != Action.TRANSLATE) return
            val text = (state.result as? ActionResult.Text)?.text ?: return
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.action_translate), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(text) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onCopy(text) }) {
                            Text(stringResource(R.string.action_copy_result))
                        }
                    }
                }
            }
        }

        is ActionState.Error -> {
            if (state.action != Action.TRANSLATE) return
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
                }
            }
        }
    }
}
