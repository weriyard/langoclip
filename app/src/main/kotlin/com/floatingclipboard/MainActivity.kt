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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.floatingclipboard.actions.Action
import com.floatingclipboard.actions.ActionResult
import com.floatingclipboard.actions.BreakdownItem
import com.floatingclipboard.ui.ActionState
import com.floatingclipboard.ui.PasteViewModel
import com.floatingclipboard.ui.PhraseExamplesScreen
import com.floatingclipboard.ui.SettingsScreen
import com.floatingclipboard.ui.colorForPartOfSpeech
import com.floatingclipboard.ui.label
import com.floatingclipboard.ui.theme.AppTheme

private sealed interface Screen {
    data object Paste : Screen
    data object Settings : Screen
    data class Examples(val phrase: String, val translation: String) : Screen
}

class MainActivity : ComponentActivity() {

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) startBubble()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignorujemy wynik — bez notyfikacji service też wystartuje, tylko user nic nie zobaczy w shade */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                AppNavigation(
                    readClipboard = ::readClipboard,
                    writeClipboard = ::writeClipboard,
                    onEnableBubble = ::ensureBubble,
                    onDisableBubble = ::stopBubble,
                )
            }
        }
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
}

@Composable
private fun AppNavigation(
    readClipboard: () -> String,
    writeClipboard: (String) -> Unit,
    onEnableBubble: () -> Unit,
    onDisableBubble: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Paste) }
    when (val current = screen) {
        Screen.Paste -> PasteScreen(
            readClipboard = readClipboard,
            writeClipboard = writeClipboard,
            onOpenSettings = { screen = Screen.Settings },
            onShowExamples = { phrase, translation -> screen = Screen.Examples(phrase, translation) },
        )
        Screen.Settings -> {
            BackHandler { screen = Screen.Paste }
            SettingsScreen(
                onBack = { screen = Screen.Paste },
                onEnableBubble = onEnableBubble,
                onDisableBubble = onDisableBubble,
            )
        }
        is Screen.Examples -> {
            BackHandler { screen = Screen.Paste }
            PhraseExamplesScreen(
                phrase = current.phrase,
                translation = current.translation,
                onBack = { screen = Screen.Paste },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteScreen(
    readClipboard: () -> String,
    writeClipboard: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onShowExamples: (String, String) -> Unit,
    viewModel: PasteViewModel = viewModel(factory = PasteViewModel.Factory),
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val actionState by viewModel.state.collectAsStateWithLifecycle()
    val providerLabel by viewModel.providerLabel.collectAsStateWithLifecycle()

    // Auto-fill schowkiem TYLKO gdy pole jest puste — nie stomp usera ani istniejącego stanu
    // (powrót z ekranu Examples wraca tu z niepustym text/actionState i nie chcemy ich kasować).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && viewModel.text.value.isBlank()) {
                viewModel.setText(readClipboard())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Treść ze schowka", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { viewModel.setText(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                label = { Text("Wklejone automatycznie") },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Wyczyść tekst i wynik",
                            )
                        }
                    }
                },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val canRun = text.isNotBlank() && actionState !is ActionState.Loading
                Button(
                    onClick = { viewModel.runAction(Action.TRANSLATE) },
                    enabled = canRun,
                ) { Text(Action.TRANSLATE.displayName) }
                Button(
                    onClick = { viewModel.runAction(Action.EXPLAIN_SENTENCE) },
                    enabled = canRun,
                ) { Text(Action.EXPLAIN_SENTENCE.displayName) }
                if (actionState !is ActionState.Idle) {
                    IconButton(
                        onClick = { viewModel.clear() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Wyczyść wynik",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ActionResultPanel(
                state = actionState,
                onCopy = { writeClipboard(it) },
                onRetry = { (actionState as? ActionState.Error)?.let { viewModel.runAction(it.action) } },
                onShowExamples = onShowExamples,
            )
        }
    }
}

@Composable
private fun ActionResultPanel(
    state: ActionState,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    onShowExamples: (String, String) -> Unit,
) {
    when (state) {
        ActionState.Idle -> Unit

        is ActionState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Przetwarzam…")
            }
            // Tekst streamowany w trakcie generowania (tylko TRANSLATE).
            state.partialText?.takeIf { it.isNotBlank() }?.let { partial ->
                SelectionContainer {
                    Text(
                        text = partial,
                        fontFamily = FontFamily.SansSerif,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is ActionState.Success -> when (val result = state.result) {
            is ActionResult.Text -> TextResultCard(
                action = state.action,
                text = result.text,
                onCopy = onCopy,
            )
            is ActionResult.Breakdown -> BreakdownResult(
                items = result.items,
                onShowExamples = onShowExamples,
            )
        }

        is ActionState.Error -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
            }
        }
    }
}

@Composable
private fun TextResultCard(
    action: Action,
    text: String,
    onCopy: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(action.displayName, style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(text) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCopy(text) }) { Text("Kopiuj wynik") }
            }
        }
    }
}

@Composable
private fun BreakdownResult(
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
        // Wyraz + (część mowy) inline + ikonka "otwórz przykłady" po prawej.
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
                    contentDescription = "Pokaż przykłady użycia",
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
