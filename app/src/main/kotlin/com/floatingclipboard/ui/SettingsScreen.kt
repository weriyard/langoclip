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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.floatingclipboard.R
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
    var geminiModel by remember(saved) { mutableStateOf(saved.geminiModel) }
    var openAiModel by remember(saved) { mutableStateOf(saved.openAiModel) }
    var anthropicModel by remember(saved) { mutableStateOf(saved.anthropicModel) }
    var targetLanguage by remember(saved) { mutableStateOf(saved.targetLanguage) }

    var keyVisible by remember { mutableStateOf(false) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var langMenuOpen by remember { mutableStateOf(false) }

    val activeKey = when (provider) {
        Provider.GEMINI -> geminiKey
        Provider.OPENAI -> openAiKey
        Provider.ANTHROPIC -> anthropicKey
    }
    val activeModel = when (provider) {
        Provider.GEMINI -> geminiModel
        Provider.OPENAI -> openAiModel
        Provider.ANTHROPIC -> anthropicModel
    }
    val onActiveKeyChange: (String) -> Unit = { value ->
        when (provider) {
            Provider.GEMINI -> geminiKey = value
            Provider.OPENAI -> openAiKey = value
            Provider.ANTHROPIC -> anthropicKey = value
        }
    }
    val onActiveModelChange: (String) -> Unit = { value ->
        when (provider) {
            Provider.GEMINI -> geminiModel = value
            Provider.OPENAI -> openAiModel = value
            Provider.ANTHROPIC -> anthropicModel = value
        }
    }

    val hasChanges = provider != saved.provider ||
            geminiKey != saved.geminiApiKey ||
            openAiKey != saved.openAiApiKey ||
            anthropicKey != saved.anthropicApiKey ||
            geminiModel != saved.geminiModel ||
            openAiModel != saved.openAiModel ||
            anthropicModel != saved.anthropicModel ||
            targetLanguage != saved.targetLanguage

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz")
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
            Text("Provider LLM", style = MaterialTheme.typography.titleMedium)
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
                    label = { Text("Aktywny provider") },
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

            Text("API key (${provider.displayName})", style = MaterialTheme.typography.titleMedium)
            val usingDefault = when (provider) {
                Provider.GEMINI -> saved.isUsingDefaultGeminiKey
                Provider.OPENAI -> saved.isUsingDefaultOpenAiKey
                Provider.ANTHROPIC -> saved.isUsingDefaultAnthropicKey
            }
            if (usingDefault && activeKey.isNotBlank()) {
                Text(
                    "Używasz domyślnego klucza z konfiguracji buildu. Możesz go zastąpić własnym.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (usingDefault && activeKey.isBlank()) {
                Text(
                    "Brak klucza dla ${provider.displayName} — wklej swój żeby używać tego providera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = activeKey,
                onValueChange = onActiveKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Klucz API") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "Ukryj" else "Pokaż",
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
                Text("Skąd wziąć klucz?")
            }

            Text("Model (${provider.displayName})", style = MaterialTheme.typography.titleMedium)
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
                    label = { Text("Model") },
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

            Text("Język docelowy (dla tłumaczenia)", style = MaterialTheme.typography.titleMedium)
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
                    label = { Text("Język") },
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
                        geminiModel = geminiModel,
                        openAiModel = openAiModel,
                        anthropicModel = anthropicModel,
                        targetLanguage = targetLanguage,
                    )
                },
                enabled = hasChanges,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Zapisz")
            }
            if (provider == Provider.GEMINI && !saved.isUsingDefaultGeminiKey) {
                OutlinedButton(
                    onClick = { viewModel.resetGeminiApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Przywróć domyślny klucz Gemini")
                }
            }
            if (provider == Provider.OPENAI && !saved.isUsingDefaultOpenAiKey) {
                OutlinedButton(
                    onClick = { viewModel.resetOpenAiApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Przywróć domyślny klucz OpenAI")
                }
            }
            if (provider == Provider.ANTHROPIC && !saved.isUsingDefaultAnthropicKey) {
                OutlinedButton(
                    onClick = { viewModel.resetAnthropicApiKey() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Przywróć domyślny klucz Anthropic")
                }
            }

            Text("Pływająca ikona", style = MaterialTheme.typography.titleMedium)
            Text(
                "Bąbel zawsze na wierzchu — kliknięcie otwiera tę aplikację z aktualnym schowkiem. Wymaga zgody „Display over other apps”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onEnableBubble, modifier = Modifier.weight(1f)) { Text("Włącz") }
                OutlinedButton(onClick = onDisableBubble, modifier = Modifier.weight(1f)) { Text("Wyłącz") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-start przy uruchomieniu aplikacji", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Bąbel włącza się sam gdy otwierasz aplikację (wymaga zgody overlay).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = saved.autoStartBubble,
                    onCheckedChange = { viewModel.setAutoStartBubble(it) },
                )
            }

            Text("Diagnostyka", style = MaterialTheme.typography.titleMedium)
            Text(
                "Logi wewnątrz aplikacji — wywołania LLM, czasy odpowiedzi, błędy parsowania.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenLogs,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pokaż logi")
            }

            Text("Skróty", style = MaterialTheme.typography.titleMedium)
            Text(
                "Jeśli nie możesz znaleźć aplikacji na ekranie, kliknij poniżej — pojawi się systemowy dialog do dodania skrótu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { pinShortcutToHome(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Przypnij ikonę na ekran główny")
            }
        }
    }
}

private fun pinShortcutToHome(context: Context) {
    val sm = context.getSystemService(ShortcutManager::class.java)
    if (sm == null || !sm.isRequestPinShortcutSupported) {
        Toast.makeText(
            context,
            "Twój launcher nie wspiera automatycznego przypinania — przeciągnij ikonę z app drawera ręcznie",
            Toast.LENGTH_LONG,
        ).show()
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
