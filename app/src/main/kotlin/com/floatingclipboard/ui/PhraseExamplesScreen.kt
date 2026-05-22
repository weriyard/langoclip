package com.floatingclipboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.floatingclipboard.R
import com.floatingclipboard.actions.Example
import com.floatingclipboard.actions.WordSense
import com.floatingclipboard.data.Tab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamplesTabContent(
    tab: Tab.Examples,
    onRegenerate: () -> Unit,
    onOpenChat: (word: String, meaningEn: String, meaningPl: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    val isRefreshing = tab.state is ExamplesState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRegenerate,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tab.phrase,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        // Base form from LLM (e.g. "drew on" → "draw on") — shown when available and different
                        val baseForm = when (val s = tab.sensesState) {
                            is SensesState.Success -> s.baseForm
                            is SensesState.Loading -> s.baseForm
                            else -> null
                        }
                        if (!baseForm.isNullOrBlank() && !baseForm.equals(tab.phrase, ignoreCase = true)) {
                            Text(
                                text = "→ $baseForm",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            )
                        }
                        if (tab.translation.isNotBlank()) {
                            Text(
                                text = tab.translation,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Thin,
                                fontFamily = FontFamily.SansSerif,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onRegenerate, enabled = !isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.examples_regenerate))
                    }
                }
                if (tab.variant > 0) {
                    Text(
                        text = stringResource(R.string.tab_subtitle_examples_variant, tab.variant + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider()
            SensesSection(
                state = tab.sensesState,
                onOpenChat = { meaningEn, meaningPl ->
                    onOpenChat(tab.phrase, meaningEn, meaningPl)
                },
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.examples_generate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val s = tab.state) {
                is ExamplesState.Loading -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            text = if (s.partial.isEmpty()) stringResource(R.string.examples_loading)
                            else stringResource(R.string.examples_loading_progress, s.partial.size),
                            fontFamily = FontFamily.SansSerif,
                        )
                    }
                    s.partial.forEachIndexed { index, example ->
                        ExampleItem(index = index + 1, example = example)
                    }
                }

                is ExamplesState.Error -> Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.SansSerif,
                )

                is ExamplesState.Success -> SelectionContainer {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        s.data.examples.forEachIndexed { index, example ->
                            ExampleItem(index = index + 1, example = example)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensesSection(
    state: SensesState,
    onOpenChat: (meaningEn: String, meaningPl: String) -> Unit,
) {
    when (state) {
        is SensesState.Idle -> Unit
        is SensesState.Loading -> {
            val senses = state.partial
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.senses_loading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (senses.isNotEmpty()) SensesList(senses, onOpenChat)
            }
        }
        is SensesState.Success -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.senses_section_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SensesList(state.senses, onOpenChat)
        }
        is SensesState.Error -> Unit  // silent — senses are supplementary
    }
}

@Composable
private fun SensesList(
    senses: List<WordSense>,
    onOpenChat: (meaningEn: String, meaningPl: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        senses.forEachIndexed { i, sense ->
            SenseRow(
                sense = sense,
                onOpenChat = { onOpenChat(sense.meaning, sense.meaningTranslation) },
            )
            if (i < senses.size - 1) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun SenseRow(sense: WordSense, onOpenChat: () -> Unit) {
    val color = colorForPartOfSpeech(sense.partOfSpeech)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // POS chip — single anchor for the whole sense.
        Surface(
            color = color.copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = sense.partOfSpeech.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        // ── Meaning block ────────────────────────────────────────────────────────
        SectionBlock(
            title = "ZNACZENIE",
            sourceLabel = meaningSourceLabel(sense),
            primaryEn = sense.meaning,
            primaryPl = sense.meaningTranslation,
            primaryItalic = false,
        )
        // ── Example block — skipped when nothing to show ─────────────────────────
        if (sense.example.isNotBlank()) {
            SectionBlock(
                title = "ZASTOSOWANIE",
                sourceLabel = exampleSourceLabel(sense),
                primaryEn = "“${sense.example}”",
                primaryPl = sense.exampleTranslation,
                primaryItalic = true,
                trailingAction = {
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Otwórz chat o tym słowie",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}

/**
 * Section inside a single sense (meaning OR example). Header on the left, source attribution on
 * the right; EN line below, PL translation under it (skipped when empty so a half-done sense
 * doesn't show a stray "→ ").
 */
@Composable
private fun SectionBlock(
    title: String,
    sourceLabel: String,
    primaryEn: String,
    primaryPl: String,
    primaryItalic: Boolean,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
            )
            if (trailingAction != null) {
                trailingAction()
            }
        }
        Text(
            text = primaryEn,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (primaryItalic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
        if (primaryPl.isNotBlank()) {
            Text(
                text = "→ $primaryPl",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// Source codes used in SenseRow chips — see SettingsScreen for the legend rendered to the user.
// DA = dictionaryapi.dev, KA = kaikki (local Wiktionary DB),
// HA = Haiku translation, HG = Haiku generated, — = pending / unavailable.
private fun meaningSourceLabel(sense: WordSense): String {
    val pl = if (sense.meaningTranslation.isNotBlank()) "HA" else "—"
    return "EN: DA · PL: $pl"
}

private fun exampleSourceLabel(sense: WordSense): String {
    val en = when (sense.exampleSource) {
        com.floatingclipboard.actions.ExampleSource.API -> "DA"
        com.floatingclipboard.actions.ExampleSource.KAIKKI -> "KA"
        com.floatingclipboard.actions.ExampleSource.GENERATED -> "HG"
        com.floatingclipboard.actions.ExampleSource.NONE -> "—"
    }
    val pl = if (sense.exampleTranslation.isNotBlank()) "HA" else "—"
    return "EN: $en · PL: $pl"
}

private fun highlightedEnglish(
    english: String,
    span: String,
    highlightColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (span.isBlank() || english.isBlank()) return AnnotatedString(english)
    val style = SpanStyle(color = highlightColor, fontWeight = FontWeight.ExtraBold)

    val positions = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val idx = english.indexOf(span, startIndex = from)
        if (idx < 0) break
        positions.add(idx until (idx + span.length))
        from = idx + span.length
    }
    if (positions.isEmpty()) {
        val lower = english.lowercase()
        val needle = span.lowercase()
        var s = 0
        while (true) {
            val idx = lower.indexOf(needle, startIndex = s)
            if (idx < 0) break
            positions.add(idx until (idx + needle.length))
            s = idx + needle.length
        }
    }
    if (positions.isEmpty()) return AnnotatedString(english)

    return buildAnnotatedString {
        var cursor = 0
        positions.sortedBy { it.first }.forEach { range ->
            if (range.first > cursor) append(english.substring(cursor, range.first))
            withStyle(style) { append(english.substring(range.first, range.last + 1)) }
            cursor = range.last + 1
        }
        if (cursor < english.length) append(english.substring(cursor))
    }
}

@Composable
private fun ExampleItem(index: Int, example: Example) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(primary.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.examples_label_one, index),
                style = MaterialTheme.typography.labelMedium,
                color = primary,
                fontFamily = FontFamily.SansSerif,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = highlightedEnglish(example.english, example.highlightedSpan, primary),
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (example.translation.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = example.translation,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Thin,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (example.usageNote.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.examples_usage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.SansSerif,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = example.usageNote,
                            fontFamily = FontFamily.SansSerif,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
