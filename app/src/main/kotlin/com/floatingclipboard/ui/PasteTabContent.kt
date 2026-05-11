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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.floatingclipboard.actions.Action
import com.floatingclipboard.actions.ActionResult
import com.floatingclipboard.data.Tab

/**
 * Widok zakładki Schowek (pierwsza, niezamykalna). Edycja tekstu + akcje + Translate inline.
 * Wytłumacz NIE wyświetla wyniku tutaj — tworzy nową zakładkę przez [onExplain].
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
    // Auto-fill schowkiem TYLKO gdy pole jest puste — nie nadpisuje istniejącego stanu np. po
    // powrocie z innej zakładki.
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
        Text("Treść ze schowka", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = tab.text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            label = { Text("Wklejone automatycznie") },
            trailingIcon = {
                if (tab.text.isNotEmpty()) {
                    IconButton(onClick = onClearAll) {
                        Icon(Icons.Default.Clear, contentDescription = "Wyczyść tekst i wynik")
                    }
                }
            },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canRun = tab.text.isNotBlank() && tab.actionState !is ActionState.Loading
            Button(onClick = onTranslate, enabled = canRun) { Text(Action.TRANSLATE.displayName) }
            Button(onClick = onExplain, enabled = canRun) { Text(Action.EXPLAIN_SENTENCE.displayName) }
            if (tab.actionState !is ActionState.Idle) {
                IconButton(
                    onClick = onClearResult,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Wyczyść wynik",
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
 * Renderuje TYLKO wyniki Translate (inline w Schowku). Dla Explain wraca Unit — wynik
 * jest w osobnej zakładce, nie tutaj.
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
                    Text("Przetwarzam…")
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
                    Text(Action.TRANSLATE.displayName, style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(text) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onCopy(text) }) { Text("Kopiuj wynik") }
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
