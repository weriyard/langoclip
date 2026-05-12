package com.floatingclipboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.floatingclipboard.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatingclipboard.data.Tab
import com.floatingclipboard.data.TabId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsListSheet(
    tabs: List<Tab>,
    selectedId: TabId,
    onSelect: (TabId) -> Unit,
    onClose: (TabId) -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeableCount = tabs.count { it.isCloseable }

    fun dismissThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tabs_title_count, tabs.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (closeableCount > 0) {
                    TextButton(onClick = {
                        onCloseAll()
                        dismissThen { }
                    }) {
                        Text(stringResource(R.string.tabs_close_all))
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(tabs, key = { it.id.value }) { tab ->
                    TabsListItem(
                        tab = tab,
                        isSelected = tab.id == selectedId,
                        onClick = {
                            onSelect(tab.id)
                            dismissThen { }
                        },
                        onClose = { onClose(tab.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabsListItem(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconFor(tab),
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val subtitle = when (tab) {
                is Tab.Paste -> stringResource(R.string.tab_subtitle_paste)
                is Tab.Explain -> stringResource(R.string.tab_subtitle_explain)
                is Tab.Examples -> if (tab.variant > 0)
                    stringResource(R.string.tab_subtitle_examples_variant, tab.variant + 1)
                else stringResource(R.string.tab_subtitle_examples)
                is Tab.WordTranslation -> "Tłumaczenie słowa"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (tab.isCloseable) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.tabs_close_one),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun iconFor(tab: Tab): ImageVector = when (tab) {
    is Tab.Paste -> Icons.Default.ContentPaste
    is Tab.Explain -> Icons.AutoMirrored.Filled.MenuBook
    is Tab.Examples -> Icons.Default.Translate
    is Tab.WordTranslation -> Icons.Default.Translate
}

