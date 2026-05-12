package com.floatingclipboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.floatingclipboard.data.Tab
import com.floatingclipboard.translation.TranslationResult
import com.floatingclipboard.translation.TranslationSource

@Composable
fun WordTranslationTabContent(
    tab: Tab.WordTranslation,
    onShowExamples: (phrase: String, translation: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = tab.state) {
            is WordTranslationState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Tłumaczenie \"${tab.token}\"...")
                }
            }

            is WordTranslationState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is WordTranslationState.Success -> WordTranslationResult(
                token = tab.token,
                result = s.result,
                onShowExamples = onShowExamples,
            )
        }
    }
}

@Composable
private fun WordTranslationResult(
    token: String,
    result: TranslationResult,
    onShowExamples: (String, String) -> Unit,
) {
    // Header: inflected form + lemma (if different)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = token,
            style = MaterialTheme.typography.headlineSmall,
        )
        if (result.lemma != token.lowercase()) {
            Text(
                text = "→ ${result.lemma}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Part of speech + base form row
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (result.partOfSpeech.isNotBlank()) {
            AssistChip(
                onClick = {},
                label = { Text(result.partOfSpeech, style = MaterialTheme.typography.labelSmall) },
            )
        }
        if (result.baseForm.isNotBlank() && result.baseForm != result.lemma) {
            AssistChip(
                onClick = {},
                label = { Text(result.baseForm, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }

    // Main translation
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tłumaczenie", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(result.translation, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    // Definitions
    if (result.definitionsPl.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Znaczenia", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.definitionsPl.forEachIndexed { i, def ->
                SelectionContainer {
                    Text("${i + 1}. $def", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // Examples
    if (result.examplesPl.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Przykłady", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.examplesPl.forEach { ex ->
                SelectionContainer {
                    Text(
                        text = ex,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    )
                }
            }
        }
    }

    // Actions
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onShowExamples(result.baseForm.ifBlank { result.lemma }, result.translation) }) {
            Text("Więcej przykładów")
        }
    }

    // Source badge
    val sourceLabel = when (result.source) {
        TranslationSource.CACHE  -> "cache"
        TranslationSource.LOCAL  -> "model lokalny"
        TranslationSource.HAIKU  -> "Claude Haiku"
        TranslationSource.SONNET -> "Claude Sonnet"
    }
    Text(
        text = sourceLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}
