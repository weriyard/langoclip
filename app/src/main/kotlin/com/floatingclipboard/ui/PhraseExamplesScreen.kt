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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.floatingclipboard.actions.Example

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhraseExamplesScreen(
    phrase: String,
    translation: String,
    onBack: () -> Unit,
    viewModel: PhraseExamplesViewModel = viewModel(
        key = "examples-$phrase",
        factory = PhraseExamplesViewModel.Factory,
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()
    val isRefreshing = state is ExamplesState.Loading

    LaunchedEffect(phrase) { viewModel.load(phrase) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val variantSuffix = (state as? ExamplesState.Success)?.variant?.takeIf { it > 0 }
                    Text(if (variantSuffix != null) "Przykłady użycia · zestaw ${variantSuffix + 1}" else "Przykłady użycia")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.regenerate() },
                        enabled = !isRefreshing,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Wygeneruj nowy zestaw")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.regenerate() },
            state = pullState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = phrase,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (translation.isNotBlank()) {
                        Text(
                            text = translation,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Thin,
                            fontFamily = FontFamily.SansSerif,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    text = "Pociągnij w dół albo kliknij ↻ żeby wygenerować inne przykłady.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (val s = state) {
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
                                text = if (s.partial.isEmpty()) "Generuję przykłady…"
                                else "Generuję przykłady… (${s.partial.size}/5)",
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
}

/**
 * Buduje AnnotatedString z podświetloną frazą. Próba 1: case-sensitive match `span`. Próba 2:
 * case-insensitive (Sonnet czasem zmienia kapitalizację w zdaniu). Wszystkie wystąpienia są
 * podświetlone — fraza może pojawić się więcej niż raz w jednym zdaniu.
 */
private fun highlightedEnglish(
    english: String,
    span: String,
    highlightColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (span.isBlank() || english.isBlank()) return AnnotatedString(english)
    val style = SpanStyle(
        color = highlightColor,
        fontWeight = FontWeight.ExtraBold,
    )

    // Find all match positions (try case-sensitive first, fallback to case-insensitive).
    val positions = mutableListOf<IntRange>()
    var search = english
    var offset = 0
    while (true) {
        val idx = search.indexOf(span)
        if (idx < 0) break
        positions.add((offset + idx) until (offset + idx + span.length))
        val next = idx + span.length
        if (next >= search.length) break
        search = search.substring(next)
        offset += next
    }
    if (positions.isEmpty()) {
        // Fallback: case-insensitive.
        val lower = english.lowercase()
        val needle = span.lowercase()
        var from = 0
        while (true) {
            val idx = lower.indexOf(needle, startIndex = from)
            if (idx < 0) break
            positions.add(idx until (idx + needle.length))
            from = idx + needle.length
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
                text = "Przykład $index",
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
                            text = "Użycie",
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
