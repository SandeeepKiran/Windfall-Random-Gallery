package com.mousy.windfall.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mousy.windfall.data.model.FavWindow
import com.mousy.windfall.data.model.FileTypeFilter

/**
 * Shared type + date filter bar for Recents and Favourites.
 * Preferences are independent per screen; only the UI chrome is shared.
 * Date filter uses a dropdown (not a cycle) so Days(365) is selected safely.
 */
@Composable
fun MediaListFilterBar(
    title: String,
    types: FileTypeFilter,
    window: FavWindow?,
    typeMenuOpen: Boolean,
    onToggleTypeMenu: () -> Unit,
    onToggleType: (String) -> Unit,
    onSelectWindow: (FavWindow) -> Unit,
    /** Favourites only: include items from folders that aren't currently selected. */
    allFoldersEnabled: Boolean? = null,
    onToggleAllFolders: () -> Unit = {},
    /** A Shuffle button at the end of the bar, like the Gallery's; none when null. */
    onShuffle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var dateMenuOpen by remember { mutableStateOf(false) }
    val safeWindow = remember(window) { FavWindow.normalize(window) }
    val dateOptions = remember { FavWindow.options.filterNotNull() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (allFoldersEnabled != null) {
            IconButton(onClick = onToggleAllFolders) {
                Icon(
                    if (allFoldersEnabled) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = if (allFoldersEnabled) {
                        "Showing favourites from all folders"
                    } else {
                        "Showing favourites from selected folders only"
                    },
                    tint = if (allFoldersEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Box {
            OutlinedButton(onClick = onToggleTypeMenu, contentPadding = CompactButtonPadding) {
                Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp))
                Text(typeFilterLabel(types), modifier = Modifier.padding(start = 4.dp))
            }
            DropdownMenu(expanded = typeMenuOpen, onDismissRequest = onToggleTypeMenu) {
                DropdownMenuItem(
                    text = { Text("Photos") },
                    trailingIcon = { if (types.photo) Text("✓") },
                    onClick = { onToggleType("photo") },
                )
                DropdownMenuItem(
                    text = { Text("Videos") },
                    trailingIcon = { if (types.video) Text("✓") },
                    onClick = { onToggleType("video") },
                )
                DropdownMenuItem(
                    text = { Text("GIFs") },
                    trailingIcon = { if (types.gif) Text("✓") },
                    onClick = { onToggleType("gif") },
                )
                DropdownMenuItem(
                    text = { Text("Audio") },
                    trailingIcon = { if (types.audio) Text("✓") },
                    onClick = { onToggleType("audio") },
                )
            }
        }
        Box(modifier = Modifier.padding(start = 6.dp)) {
            OutlinedButton(onClick = { dateMenuOpen = true }, contentPadding = CompactButtonPadding) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                Text(safeWindow.shortLabel(), modifier = Modifier.padding(start = 4.dp))
            }
            DropdownMenu(expanded = dateMenuOpen, onDismissRequest = { dateMenuOpen = false }) {
                dateOptions.forEach { option ->
                    val canonical = FavWindow.normalize(option)
                    DropdownMenuItem(
                        text = { Text(canonical.label()) },
                        trailingIcon = {
                            if (canonical.sameAs(safeWindow)) Text("✓")
                        },
                        onClick = {
                            dateMenuOpen = false
                            onSelectWindow(canonical)
                        },
                    )
                }
            }
        }
        if (onShuffle != null) {
            FilledIconButton(onClick = onShuffle, modifier = Modifier.padding(start = 6.dp)) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
            }
        }
    }
}

/**
 * Narrower than a stock button's 24 dp sides, so the bar still fits a 360 dp-wide phone once the
 * Shuffle button joins it.
 */
private val CompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

fun typeFilterLabel(filter: FileTypeFilter): String {
    val count = listOf(filter.photo, filter.video, filter.gif, filter.audio).count { it }
    return if (count == 4) "All types" else "$count types"
}
