package com.langoclip.app.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.langoclip.app.R
import com.langoclip.app.data.Tab
import com.langoclip.app.translation.TranslationResult
import com.langoclip.app.translation.TranslationSource

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
                    Text(stringResource(R.string.wordtab_translating, tab.token))
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
            Text(stringResource(R.string.wordtab_translation_label), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(result.translation, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    // Definitions
    if (result.definitionsPl.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.wordtab_meanings), style = MaterialTheme.typography.labelMedium,
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
            Text(stringResource(R.string.wordtab_examples), style = MaterialTheme.typography.labelMedium,
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

    // English source from dictionaryapi.dev
    if (result.definitionsEn.isNotEmpty() || result.examplesEn.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.wordtab_source_en),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.definitionsEn.forEachIndexed { i, def ->
                SelectionContainer {
                    Text("${i + 1}. $def", style = MaterialTheme.typography.bodySmall)
                }
            }
            result.examplesEn.forEach { ex ->
                SelectionContainer {
                    Text(
                        text = ex,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Actions
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onShowExamples(result.baseForm.ifBlank { result.lemma }, result.translation) }) {
            Text(stringResource(R.string.wordtab_more_examples))
        }
    }

    // Source badge
    val sourceLabel = when (result.source) {
        TranslationSource.CACHE  -> "cache"
        TranslationSource.LOCAL  -> stringResource(R.string.wordtab_source_local)
        TranslationSource.HAIKU  -> "Claude Haiku"
        TranslationSource.SONNET -> "Claude Sonnet"
    }
    Text(
        text = sourceLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )
}
