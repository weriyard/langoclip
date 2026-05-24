package com.floatingclipboard.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.floatingclipboard.R
import com.floatingclipboard.data.AppLocale
import com.floatingclipboard.data.Provider

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

            SourceLegend()

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

/**
 * Legend for the two-letter source codes shown in Examples → ZNACZENIE/ZASTOSOWANIE headers.
 * Lives in Settings so the per-sense chips stay tiny and uncluttered.
 */
@Composable
private fun SourceLegend() {
    Text(
        "Skróty źródeł (Przykłady)",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        "W zakładce Przykłady, obok ZNACZENIE i ZASTOSOWANIE pokazane są skróty informujące skąd pochodzi tekst EN i tłumaczenie PL.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val entries = listOf(
        "DA" to "dictionaryapi.dev (słownik EN online)",
        "KA" to "kaikki / Wiktionary (lokalna baza w aplikacji)",
        "HA" to "Haiku — tłumaczenie EN → PL",
        "HG" to "Haiku — wygenerowane (gdy słownik nie miał przykładu)",
        "—"  to "brak danych / jeszcze nie pobrane",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 4.dp),
    ) {
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
                setClassName(context.packageName, "com.floatingclipboard.MainActivity")
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        )
        .build()
    sm.requestPinShortcut(info, null)
}
