package com.floatingclipboard.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.ui.res.stringResource
import com.floatingclipboard.R
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.floatingclipboard.data.Tab as DataTab
import com.floatingclipboard.data.TabId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabBar(
    tabs: List<DataTab>,
    selectedId: TabId,
    onSelect: (TabId) -> Unit,
    onClose: (TabId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 4.dp,
        indicator = { positions ->
            if (selectedIndex < positions.size) {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == selectedId
            Tab(
                selected = selected,
                onClick = { onSelect(tab.id) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (tab.isCloseable) {
                            IconButton(
                                onClick = { onClose(tab.id) },
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.tabs_close_one),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}
