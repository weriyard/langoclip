package com.floatingclipboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                        verticalArrangement = Arrangement.spacedBy(20.dp),
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
                        // Karty pojawiają się jedna po drugiej w trakcie streamowania.
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
                            verticalArrangement = Arrangement.spacedBy(20.dp),
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

@Composable
private fun ExampleItem(index: Int, example: Example) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.SansSerif,
        )
        Text(
            text = example.english,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (example.translation.isNotBlank()) {
            Text(
                text = example.translation,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Thin,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (example.usageNote.isNotBlank()) {
            Text(
                text = example.usageNote,
                fontFamily = FontFamily.SansSerif,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
