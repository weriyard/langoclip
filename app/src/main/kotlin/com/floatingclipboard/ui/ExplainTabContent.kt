package com.floatingclipboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.floatingclipboard.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.floatingclipboard.actions.ActionResult
import com.floatingclipboard.actions.BreakdownItem
import com.floatingclipboard.data.Tab

/**
 * Widok zakładki Explain — snapshot oryginalnego tekstu (collapsed na górze) + breakdown wynik.
 * Bez pola edytowalnego; ten ekran jest read-only.
 */
@Composable
fun ExplainTabContent(
    tab: Tab.Explain,
    onShowExamples: (phrase: String, translation: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Snippet oryginalnego tekstu na samej górze — przypomina o kontekście.
        Text(
            text = stringResource(R.string.explain_source_text),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = tab.sourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
            )
        }

        ResultPanel(state = tab.state, onShowExamples = onShowExamples, onRetry = onRetry)
    }
}

@Composable
private fun ResultPanel(
    state: ActionState,
    onShowExamples: (String, String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ActionState.Idle -> Unit

        is ActionState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.loading_processing))
            }
            state.partialBreakdown?.takeIf { it.isNotEmpty() }?.let { items ->
                BreakdownList(items, onShowExamples)
            }
        }

        is ActionState.Success -> when (val r = state.result) {
            is ActionResult.Breakdown -> BreakdownList(r.items, onShowExamples)
            is ActionResult.Text -> SelectionContainer { Text(r.text) }
        }

        is ActionState.Error -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun BreakdownList(
    items: List<BreakdownItem>,
    onShowExamples: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { item -> BreakdownItemRow(item, onShowExamples) }
    }
}

@Composable
private fun BreakdownItemRow(
    item: BreakdownItem,
    onShowExamples: (String, String) -> Unit,
) {
    val color = colorForPartOfSpeech(item.partOfSpeech)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.original,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        fontFamily = FontFamily.SansSerif,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "  (${item.partOfSpeech.label})",
                        color = color,
                        fontFamily = FontFamily.SansSerif,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(
                onClick = { onShowExamples(item.original, item.translation) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.examples_show_for_phrase),
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (item.translation.isNotBlank()) {
            Text(
                text = item.translation,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Thin,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (item.explanation.isNotBlank()) {
            Text(
                text = item.explanation,
                fontFamily = FontFamily.SansSerif,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
