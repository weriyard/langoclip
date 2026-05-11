package com.floatingclipboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.floatingclipboard.data.SettingsRepository
import com.floatingclipboard.data.Tab as DataTab
import com.floatingclipboard.ui.ExamplesTabContent
import com.floatingclipboard.ui.ExplainTabContent
import com.floatingclipboard.ui.LogsScreen
import com.floatingclipboard.ui.PasteTabContent
import com.floatingclipboard.ui.SettingsScreen
import com.floatingclipboard.ui.TabBar
import com.floatingclipboard.ui.TabsListSheet
import com.floatingclipboard.ui.TabsViewModel
import com.floatingclipboard.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface Overlay {
    data object None : Overlay
    data object Settings : Overlay
    data object Logs : Overlay
}

class MainActivity : ComponentActivity() {

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) startBubble()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignored — bez notyfikacji service też wystartuje */ }

    // Mutable state pod incoming share/process-text intents. Compose obserwuje przez
    // androidx.compose.runtime.mutableStateOf — VM zżera content przez LaunchedEffect.
    private val sharedTextState = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        maybeAutoStartBubble()
        consumeIncomingText(intent)
        setContent {
            AppTheme {
                AppRoot(
                    readClipboard = ::readClipboard,
                    writeClipboard = ::writeClipboard,
                    onEnableBubble = ::ensureBubble,
                    onDisableBubble = ::stopBubble,
                    sharedTextState = sharedTextState,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop launchMode → ponowne uruchomienie z innego share trafia tu.
        consumeIncomingText(intent)
    }

    /**
     * Wyciąga tekst z [Intent.ACTION_SEND] (Share sheet) lub [Intent.ACTION_PROCESS_TEXT]
     * (zaznaczenie tekstu → menu kontekstowe). Sets state — Compose pickuje przez LaunchedEffect.
     */
    private fun consumeIncomingText(intent: Intent?) {
        intent ?: return
        val text: CharSequence? = when (intent.action) {
            Intent.ACTION_SEND -> if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }
        text?.toString()?.takeIf { it.isNotBlank() }?.let { sharedTextState.value = it }
    }

    private fun readClipboard(): String {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
    }

    private fun writeClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Floating Clipboard", text))
    }

    private fun ensureBubble() {
        if (!Settings.canDrawOverlays(this)) {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startBubble()
        }
    }

    private fun startBubble() {
        val intent = Intent(this, BubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBubble() {
        stopService(Intent(this, BubbleService::class.java))
    }

    private fun maybeAutoStartBubble() {
        if (!Settings.canDrawOverlays(this)) return
        lifecycleScope.launch {
            val settings = SettingsRepository(applicationContext).settings.first()
            if (settings.autoStartBubble) startBubble()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    readClipboard: () -> String,
    writeClipboard: (String) -> Unit,
    onEnableBubble: () -> Unit,
    onDisableBubble: () -> Unit,
    sharedTextState: androidx.compose.runtime.MutableState<String?>,
    viewModel: TabsViewModel = viewModel(factory = TabsViewModel.Factory),
) {
    var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }

    // Pickujemy incoming text z share/process-text intents, raz na każdą wartość.
    val sharedText = sharedTextState.value
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            viewModel.receiveSharedText(sharedText)
            // Closing overlay if open (np. Settings) żeby user widział wynik.
            overlay = Overlay.None
            sharedTextState.value = null
        }
    }

    when (overlay) {
        Overlay.Settings -> {
            BackHandler { overlay = Overlay.None }
            SettingsScreen(
                onBack = { overlay = Overlay.None },
                onEnableBubble = onEnableBubble,
                onDisableBubble = onDisableBubble,
                onOpenLogs = { overlay = Overlay.Logs },
            )
        }
        Overlay.Logs -> {
            BackHandler { overlay = Overlay.Settings }
            LogsScreen(onBack = { overlay = Overlay.Settings })
        }
        Overlay.None -> TabbedShell(
            viewModel = viewModel,
            readClipboard = readClipboard,
            writeClipboard = writeClipboard,
            onOpenSettings = { overlay = Overlay.Settings },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabbedShell(
    viewModel: TabsViewModel,
    readClipboard: () -> String,
    writeClipboard: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tabs by viewModel.tabsList.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val providerLabel by viewModel.providerLabel.collectAsStateWithLifecycle()
    var showTabsList by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Floating Clipboard")
                            if (providerLabel.isNotBlank()) {
                                Text(
                                    text = providerLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showTabsList = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Lista zakładek (${tabs.size})",
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                        }
                    },
                )
                TabBar(
                    tabs = tabs,
                    selectedId = selectedId,
                    onSelect = viewModel::select,
                    onClose = viewModel::close,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
            when (val t = selectedTab) {
                is DataTab.Paste -> PasteTabContent(
                    tab = t,
                    readClipboard = readClipboard,
                    writeClipboard = writeClipboard,
                    onTextChange = viewModel::setPasteText,
                    onTranslate = viewModel::translateInPaste,
                    onExplain = viewModel::explainAsNewTab,
                    onClearAll = {
                        viewModel.clearPaste()
                    },
                    onClearResult = viewModel::clearPasteResult,
                )
                is DataTab.Explain -> ExplainTabContent(
                    tab = t,
                    onShowExamples = viewModel::showExamplesAsNewTab,
                    onRetry = {
                        // Retry: ponów explain dla tego samego sourceText'u jako nowa zakładka.
                        // Zamiast nadpisywać current state — wygodniej dla usera (zachowuje błąd).
                        // Można też w przyszłości dodać re-fetch w miejscu.
                    },
                )
                is DataTab.Examples -> ExamplesTabContent(
                    tab = t,
                    onRegenerate = { viewModel.regenerateExamples(t.id) },
                )
                null -> Unit
            }
        }
    }

    if (showTabsList) {
        TabsListSheet(
            tabs = tabs,
            selectedId = selectedId,
            onSelect = viewModel::select,
            onClose = viewModel::close,
            onCloseAll = viewModel::closeAllExceptPaste,
            onDismiss = { showTabsList = false },
        )
    }
}
