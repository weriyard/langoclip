package com.floatingclipboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.floatingclipboard.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.floatingclipboard.actions.Action
import com.floatingclipboard.actions.ActionResult
import com.floatingclipboard.data.Tab

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
    onClearAll: () -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-fill from the clipboard ONLY when the field is empty — doesn't overwrite existing
    // state, e.g. after returning from another tab.
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

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.paste_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = tab.text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            label = { Text(stringResource(R.string.paste_field_label)) },
            trailingIcon = {
                if (tab.text.isNotEmpty()) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.paste_clear_text_and_result),
                        )
                    }
                }
            },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canRun = tab.text.isNotBlank() && tab.actionState !is ActionState.Loading
            Button(onClick = onTranslate, enabled = canRun) {
                Text(stringResource(R.string.action_translate))
            }
            Button(onClick = onExplain, enabled = canRun) {
                Text(stringResource(R.string.action_explain))
            }
            if (tab.actionState !is ActionState.Idle) {
                IconButton(
                    onClick = onClearResult,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.paste_clear_result),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
