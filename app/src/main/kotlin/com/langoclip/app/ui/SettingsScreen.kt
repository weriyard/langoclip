package com.langoclip.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.langoclip.app.R
import com.langoclip.app.data.AppLocale
import com.langoclip.app.data.Provider
import com.langoclip.app.llm.OpenRouterModelHint

private val SUGGESTED_LANGUAGES = listOf("polski", "English", "Deutsch", "Français", "Español", "Italiano", "ukraiński")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onEnableBubble: () -> Unit,
    onDisableBubble: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val saved by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var provider by remember(saved) { mutableStateOf(saved.provider) }
    var geminiKey by remember(saved) { mutableStateOf(saved.geminiApiKey) }
    var openAiKey by remember(saved) { mutableStateOf(saved.openAiApiKey) }
    var anthropicKey by remember(saved) { mutableStateOf(saved.anthropicApiKey) }
    var openRouterKey by remember(saved) { mutableStateOf(saved.openRouterApiKey) }
    var geminiModel by remember(saved) { mutableStateOf(saved.geminiModel) }
    var openAiModel by remember(saved) { mutableStateOf(saved.openAiModel) }
    var anthropicModel by remember(saved) { mutableStateOf(saved.anthropicModel) }
    var openRouterModel by remember(saved) { mutableStateOf(saved.openRouterModel) }
    var targetLanguage by remember(saved) { mutableStateOf(saved.targetLanguage) }

    var keyVisible by remember { mutableStateOf(false) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var langMenuOpen by remember { mutableStateOf(false) }
    var appLangMenuOpen by remember { mutableStateOf(false) }

    val activeKey = when (provider) {
        Provider.GEMINI -> geminiKey
        Provider.OPENAI -> openAiKey
        Provider.ANTHROPIC -> anthropicKey
        Provider.OPENROUTER -> openRouterKey
    }
    val activeModel = when (provider) {
        Provider.GEMINI -> geminiModel
        Provider.OPENAI -> openAiModel
        Provider.ANTHROPIC -> anthropicModel
        Provider.OPENROUTER -> openRouterModel
    }
    val onActiveKeyChange: (String) -> Unit = { value ->
        when (provider) {
            Provider.GEMINI -> geminiKey = value
            Provider.OPENAI -> openAiKey = value
            Provider.ANTHROPIC -> anthropicKey = value
            Provider.OPENROUTER -> openRouterKey = value
        }
    }
    val onActiveModelChange: (String) -> Unit = { value ->
        when (provider) {
            Provider.GEMINI -> geminiModel = value
            Provider.OPENAI -> openAiModel = value
            Provider.ANTHROPIC -> anthropicModel = value
            Provider.OPENROUTER -> openRouterModel = value
        }
    }

    val hasChanges = provider != saved.provider ||
            geminiKey != saved.geminiApiKey ||
            openAiKey != saved.openAiApiKey ||
            anthropicKey != saved.anthropicApiKey ||
            openRouterKey != saved.openRouterApiKey ||
            geminiModel != saved.geminiModel ||
            openAiModel != saved.openAiModel ||
            anthropicModel != saved.anthropicModel ||
            openRouterModel != saved.openRouterModel ||
            targetLanguage != saved.targetLanguage

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_provider_header), style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = providerMenuOpen,
                onExpandedChange = { providerMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text(stringResource(R.string.settings_provider_active)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuOpen) },
                )
                ExposedDropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false },
                ) {
                    Provider.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                provider = option
                                providerMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.settings_api_key_header, provider.displayName),
                style = MaterialTheme.typography.titleMedium,
            )
            val usingDefault = when (provider) {
                Provider.GEMINI -> saved.isUsingDefaultGeminiKey
                Provider.OPENAI -> saved.isUsingDefaultOpenAiKey
                Provider.ANTHROPIC -> saved.isUsingDefaultAnthropicKey
                Provider.OPENROUTER -> saved.isUsingDefaultOpenRouterKey
            }
            if (usingDefault && activeKey.isNotBlank()) {
                Text(
                    stringResource(R.string.settings_api_key_using_default),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (usingDefault && activeKey.isBlank()) {
                Text(
                    stringResource(R.string.settings_api_key_missing, provider.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = activeKey,
                onValueChange = onActiveKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_key_label)) },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.show_hide_password),
                        )
                    }
                },
            )
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(provider.apiKeyConsoleUrl))
                    )
                },
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.settings_where_to_get_key))
            }

            if (provider == Provider.OPENROUTER) {
                OpenRouterModelSection(
                    onlyFree = saved.onlyFreeOpenRouter,
                    onToggleOnlyFree = { viewModel.setOnlyFreeOpenRouter(it) },
                    ttftSec = saved.openRouterTtftTimeoutSec,
                    onTtftChange = { viewModel.setOpenRouterTtftTimeoutSec(it) },
                )
            } else {
                Text(
                    stringResource(R.string.settings_model_header, provider.displayName),
                    style = MaterialTheme.typography.titleMedium,
                )
                ExposedDropdownMenuBox(
                    expanded = modelMenuOpen,
                    onExpandedChange = { modelMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = activeModel,
                        onValueChange = onActiveModelChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_model_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuOpen) },
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                    ) {
                        provider.models.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onActiveModelChange(option)
                                    modelMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.settings_target_language_header), style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = langMenuOpen,
                onExpandedChange = { langMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = targetLanguage,
                    onValueChange = { targetLanguage = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_target_language_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langMenuOpen) },
                )
                ExposedDropdownMenu(
                    expanded = langMenuOpen,
                    onDismissRequest = { langMenuOpen = false },
                ) {
                    SUGGESTED_LANGUAGES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                targetLanguage = option
                                langMenuOpen = false
                            },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.save(
                        provider = provider,
                        geminiApiKey = geminiKey,
                        openAiApiKey = openAiKey,
                        anthropicApiKey = anthropicKey,
                        openRouterApiKey = openRouterKey,
                        geminiModel = geminiModel,
                        openAiModel = openAiModel,
                        anthropicModel = anthropicModel,
                        openRouterModel = openRouterModel,
                        targetLanguage = targetLanguage,
                    )
                },
                enabled = hasChanges,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_save)) }
            if (provider == Provider.GEMINI && !saved.isUsingDefaultGeminiKey) {
                OutlinedButton(
                    onClick = { viewModel.resetGeminiApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_reset_gemini)) }
            }
            if (provider == Provider.OPENAI && !saved.isUsingDefaultOpenAiKey) {
                OutlinedButton(
                    onClick = { viewModel.resetOpenAiApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_reset_openai)) }
            }
            if (provider == Provider.ANTHROPIC && !saved.isUsingDefaultAnthropicKey) {
                OutlinedButton(
                    onClick = { viewModel.resetAnthropicApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_reset_anthropic)) }
            }
            if (provider == Provider.OPENROUTER && !saved.isUsingDefaultOpenRouterKey) {
                OutlinedButton(
                    onClick = { viewModel.resetOpenRouterApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Resetuj klucz OpenRouter") }
            }

            // === App language ===
            Text(stringResource(R.string.settings_language_header), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_language_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val appLocaleLabel = when (saved.appLocale) {
                AppLocale.SYSTEM -> stringResource(R.string.settings_language_system)
                AppLocale.POLISH -> stringResource(R.string.settings_language_polish)
                AppLocale.ENGLISH -> stringResource(R.string.settings_language_english)
            }
            ExposedDropdownMenuBox(
                expanded = appLangMenuOpen,
                onExpandedChange = { appLangMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = appLocaleLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text(stringResource(R.string.settings_language_header)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appLangMenuOpen) },
                )
                ExposedDropdownMenu(
                    expanded = appLangMenuOpen,
                    onDismissRequest = { appLangMenuOpen = false },
                ) {
                    AppLocale.entries.forEach { locale ->
                        val label = when (locale) {
                            AppLocale.SYSTEM -> stringResource(R.string.settings_language_system)
                            AppLocale.POLISH -> stringResource(R.string.settings_language_polish)
                            AppLocale.ENGLISH -> stringResource(R.string.settings_language_english)
                        }
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setAppLocale(locale)
                                appLangMenuOpen = false
                            },
                        )
                    }
                }
            }

            Text(stringResource(R.string.settings_bubble_header), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_bubble_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onEnableBubble, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_bubble_enable))
                }
                OutlinedButton(onClick = onDisableBubble, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_bubble_disable))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_autostart_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_autostart_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = saved.autoStartBubble,
                    onCheckedChange = { viewModel.setAutoStartBubble(it) },
                )
            }

            Text(stringResource(R.string.settings_diagnostics_header), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_diagnostics_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenLogs,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_show_logs)) }

            // === Local DB diagnostics ===
            val dbStats by viewModel.localDbStats.collectAsStateWithLifecycle()
            Text("Lokalne bazy", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sprawdza czy bundled assety SQLite się załadowały. en_lemmas normalizuje formy fleksyjne (running→run) dla cache lookupów. en_examples uzupełnia przykłady gdy dictionaryapi.dev ich nie ma.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LocalDbStatRow(label = "en_lemmas.db", count = dbStats.lemmaCount)
            LocalDbStatRow(label = "en_examples.db", count = dbStats.exampleCount)

            // === Cache ===
            Text("Cache", style = MaterialTheme.typography.titleMedium)
            Text(
                "Wyczyść cache tłumaczeń i odpowiedzi LLM. Następne zapytania zostaną wysłane na świeżo do modelu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var clearConfirmOpen by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { clearConfirmOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Wyczyść cache") }
            if (clearConfirmOpen) {
                AlertDialog(
                    onDismissRequest = { clearConfirmOpen = false },
                    title = { Text("Wyczyścić cache?") },
                    text = { Text("Usunie wszystkie zapamiętane tłumaczenia słów i odpowiedzi LLM. Operacja nieodwracalna.") },
                    confirmButton = {
                        TextButton(onClick = {
                            clearConfirmOpen = false
                            viewModel.clearCache { removed ->
                                Toast.makeText(
                                    context,
                                    if (removed > 0) "Wyczyszczono $removed wpisów" else "Cache był pusty",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }) { Text("Wyczyść") }
                    },
                    dismissButton = {
                        TextButton(onClick = { clearConfirmOpen = false }) { Text("Anuluj") }
                    },
                )
            }

            SourceLegend()
            LogColorLegend()

            Text(stringResource(R.string.settings_shortcuts_header), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_shortcuts_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val shortcutUnsupportedMsg = stringResource(R.string.shortcut_unsupported)
            OutlinedButton(
                onClick = { pinShortcutToHome(context, shortcutUnsupportedMsg) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_pin_to_home)) }
        }
    }
}

@Composable
private fun LocalDbStatRow(label: String, count: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        val (statusText, color) = when {
            count == null -> "✗ brak" to MaterialTheme.colorScheme.error
            count == 0 -> "✗ pusty (0)" to MaterialTheme.colorScheme.error
            else -> "✓ ${"%,d".format(count)} wpisów" to MaterialTheme.colorScheme.primary
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/**
 * OpenRouter-specific model section. The actual model is picked automatically by
 * OpenRouterClient walking a candidate list (free or paid, driven by [onlyFree]); the dropdown
 * the other providers expose would be misleading here, so we replace it with a toggle and a
 * live "currently used" hint sourced from OpenRouterModelHint.
 */
@Composable
private fun OpenRouterModelSection(
    onlyFree: Boolean,
    onToggleOnlyFree: (Boolean) -> Unit,
    ttftSec: Int,
    onTtftChange: (Int) -> Unit,
) {
    val currentlyUsed by OpenRouterModelHint.current.collectAsStateWithLifecycle()
    val tryingNow by OpenRouterModelHint.trying.collectAsStateWithLifecycle()
    SettingsSectionHeader("Model OpenRouter")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Tylko darmowe modele", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (onlyFree)
                        "Aplikacja przełącza między darmowymi modelami :free; gdy jeden się wyczerpie próbuje następnego."
                    else
                        "Najtańszy płatny model (Gemini 2.5 Flash / Lite). Idzie z kredytów OpenRouter — bardzo tanio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = onlyFree, onCheckedChange = onToggleOnlyFree)
        }
        val statusText = when {
            tryingNow != null -> "↻ Próbuję ${tryingNow!!.model} (${tryingNow!!.attempt}/${tryingNow!!.total})"
            currentlyUsed != null -> currentlyUsed
            else -> null
        }
        if (statusText != null) {
            StatusPill(statusText, accentLive = tryingNow != null)
        }
    }

    SettingsCard {
        var ttftInput by remember(ttftSec) { mutableStateOf(ttftSec.toString()) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Timeout pierwszego chunku", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Jeśli model nie zacznie odpowiadać w X sekund, przechodzimy do następnego. (5–120 s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = ttftInput,
                onValueChange = { v ->
                    ttftInput = v.filter { it.isDigit() }.take(3)
                    ttftInput.toIntOrNull()?.let { onTtftChange(it.coerceIn(5, 120)) }
                },
                modifier = Modifier.widthIn(min = 80.dp, max = 100.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                suffix = { Text("s") },
            )
        }
    }
}

/**
 * Section card — soft surface container that groups related Settings rows. Replaces the
 * previous loose Text+Switch stacks; the grouping by colour + corner makes it obvious
 * which controls belong together.
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Small uppercase section label, all-caps + tracked letters, sits above SettingsCard groups. */
@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/**
 * Compact monospaced status pill — sits inside the SettingsCard to show live routing state.
 * When [accentLive] is true a small primary-coloured dot leads to signal that work is in
 * flight; otherwise a neutral dot indicates the current snapshot.
 */
@Composable
private fun StatusPill(text: String, accentLive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (accentLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Legend for the two-letter source codes shown in Examples → ZNACZENIE/ZASTOSOWANIE headers.
 * Lives in Settings so the per-sense chips stay tiny and uncluttered.
 */
@Composable
private fun SourceLegend() {
    SettingsSectionHeader("Skróty źródeł (Przykłady)")
    SettingsCard {
        Text(
            "Obok ZNACZENIE i ZASTOSOWANIE pokazane są skróty informujące skąd pochodzi tekst EN i tłumaczenie PL.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val entries = listOf(
            "DA" to "dictionaryapi.dev (słownik EN online)",
            "KA" to "kaikki / Wiktionary (lokalna baza w aplikacji)",
            "TR" to "tłumaczenie EN → PL (LLM aktywnego providera)",
            "GN" to "przykład wygenerowany (LLM — gdy słownik nie miał)",
            "—"  to "brak danych / jeszcze nie pobrane",
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEach { (code, desc) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .widthIn(min = 28.dp),
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Legend for the coloured accent bar shown next to each log entry in LogsScreen. The bar colour
 * groups log lines by subsystem so a debugging session is easier to scan visually.
 */
@Composable
private fun LogColorLegend() {
    SettingsSectionHeader("Kolory logów")
    SettingsCard {
        Text(
            "Pasek koloru po lewej każdej linii w logach grupuje wpisy po tagu — czyli po podsystemie który wygenerował log.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val entries = listOf(
            Triple("LLM",          MaterialTheme.colorScheme.primary, "wywołania API (CALL/TTFT/DONE/TOKENS), parser JSON, cache hit/miss"),
            Triple("Senses",       androidx.compose.ui.graphics.Color(0xFF2E7D32), "rozkład znaczeń (dictionaryapi, kaikki HIT/MISS, per-sense translate)"),
            Triple("OpenRouter",   androidx.compose.ui.graphics.Color(0xFF7B4F9E), "fallback chain — próby kolejnych modeli z listy"),
            Triple("Chat",         androidx.compose.ui.graphics.Color(0xFF1565C0), "rozmowa w Chat tab (multi-turn)"),
            Triple("Lemma",        androidx.compose.ui.graphics.Color(0xFF00838F), "en_lemmas DB lookup (np. running → run)"),
            Triple("TabsViewModel",androidx.compose.ui.graphics.Color(0xFF5D4037), "operacje na zakładkach, otwarcia, anulowania"),
            Triple("ostrzeżenie",  androidx.compose.ui.graphics.Color(0xFFE69500), "level=W — coś dziwnego, ale nieblokujące"),
            Triple("błąd",         MaterialTheme.colorScheme.error, "level=E — wywołanie padło, parse failed, etc."),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEach { (tag, color, desc) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(width = 3.dp, height = 18.dp)
                            .background(color, MaterialTheme.shapes.extraSmall),
                    )
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .widthIn(min = 100.dp),
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun pinShortcutToHome(context: Context, unsupportedMessage: String) {
    val sm = context.getSystemService(ShortcutManager::class.java)
    if (sm == null || !sm.isRequestPinShortcutSupported) {
        Toast.makeText(context, unsupportedMessage, Toast.LENGTH_LONG).show()
        return
    }
    val info = ShortcutInfo.Builder(context, "fc_main")
        .setShortLabel(context.getString(R.string.app_name))
        .setLongLabel(context.getString(R.string.app_name))
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(context.packageName, "com.langoclip.app.MainActivity")
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        )
        .build()
    sm.requestPinShortcut(info, null)
}
