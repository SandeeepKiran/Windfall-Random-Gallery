package com.mousy.windfall.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mousy.windfall.data.model.GridMode
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.ui.components.EmptyFoldersState
import com.mousy.windfall.ui.components.MediaGrid
import java.text.NumberFormat

@Composable
fun GalleryScreen(
    items: List<MediaItem>,
    columns: Int,
    gridMode: GridMode,
    noFolders: Boolean,
    favouriteKeys: Set<String>,
    selectedKeys: Set<String>,
    onToggleGridMode: () -> Unit,
    onCycleColumns: () -> Unit,
    onShuffle: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemDoubleTap: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    onSwipeShuffle: (Int) -> Unit,
    onSetColumns: (Int) -> Unit,
    thumbnailPadding: Boolean = true,
    hapticsEnabled: Boolean = true,
    /** Everything matching the current filters; [items] is a random slice of it. */
    totalCount: Int = items.size,
    /** First item of the currently visible page — a window into [items] (swipe mode). */
    pageStart: Int = 0,
    /** Reports (page capacity, thumb bucket px) up to the ViewModel. */
    onPageGeometryChanged: (Int, Int) -> Unit = { _, _ -> },
    onReachedEnd: (Int) -> Unit = {},
    onGoSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Gallery", style = MaterialTheme.typography.titleLarge)
                if (totalCount > 0) {
                    Text(
                        "${formatCount(totalCount)} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleGridMode) {
                Icon(
                    if (gridMode == GridMode.SWIPE) Icons.Default.Swipe else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "Grid mode",
                )
            }
            IconButton(onClick = onCycleColumns) {
                Icon(Icons.Default.GridView, contentDescription = "Columns")
            }
            FilledIconButton(onClick = onShuffle) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
            }
        }

        when {
            noFolders -> EmptyFoldersState(onChooseFolders = onGoSettings)
            items.isEmpty() -> Text(
                "No media of the selected file types. Adjust File Types in settings.",
                modifier = Modifier.padding(40.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> MediaGrid(
                items = items,
                columns = columns,
                gridMode = gridMode,
                pageStart = pageStart,
                onPageGeometryChanged = onPageGeometryChanged,
                favouriteKeys = favouriteKeys,
                selectedKeys = selectedKeys,
                onItemClick = onItemClick,
                onItemDoubleTap = onItemDoubleTap,
                onItemLongPress = onItemLongPress,
                onSwipeShuffle = onSwipeShuffle,
                onSetColumns = onSetColumns,
                thumbnailPadding = thumbnailPadding,
                hapticsEnabled = hapticsEnabled,
                onReachedEnd = onReachedEnd,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)
