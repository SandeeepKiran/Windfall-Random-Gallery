package com.mousy.windfall.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mousy.windfall.data.model.FavWindow
import com.mousy.windfall.data.model.FileTypeFilter
import com.mousy.windfall.data.model.GridMode
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.ui.components.EmptyFoldersState
import com.mousy.windfall.ui.components.MediaGrid
import com.mousy.windfall.ui.components.MediaListFilterBar

@Composable
fun RecentScreen(
    items: List<MediaItem>,
    columns: Int,
    noFolders: Boolean,
    favouriteKeys: Set<String>,
    selectedKeys: Set<String>,
    listTypes: FileTypeFilter,
    listWindow: FavWindow,
    typeMenuOpen: Boolean,
    onToggleTypeMenu: () -> Unit,
    onToggleType: (String) -> Unit,
    onSelectWindow: (FavWindow) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemDoubleTap: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    onSetColumns: (Int) -> Unit,
    onGoSettings: () -> Unit,
    /** Shared with the Gallery tab; only the Gallery exposes the toggle. */
    gridMode: GridMode = GridMode.SCROLL,
    /** Deals Recent a new random order. */
    onShuffle: () -> Unit = {},
    onSwipeShuffle: (Int) -> Unit = {},
    /** Window position when swipe-paging this tab's list. */
    pageStart: Int = 0,
    onPageGeometryChanged: (Int, Int) -> Unit = { _, _ -> },
    /** Scroll mode: (top-left item, last visible item, settled). */
    onScrolled: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    hapticsEnabled: Boolean = true,
    thumbnailPadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        MediaListFilterBar(
            title = "Recent",
            types = listTypes,
            window = listWindow,
            typeMenuOpen = typeMenuOpen,
            onToggleTypeMenu = onToggleTypeMenu,
            onToggleType = onToggleType,
            onSelectWindow = onSelectWindow,
            // Scroll mode lists Recent newest first, so there is nothing to shuffle there.
            onShuffle = if (gridMode == GridMode.SWIPE) onShuffle else null,
        )

        when {
            noFolders -> EmptyFoldersState(onChooseFolders = onGoSettings)
            items.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp, start = 40.dp, end = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 2.dp) {
                    Icon(
                        Icons.Default.HistoryToggleOff,
                        null,
                        modifier = Modifier
                            .padding(24.dp)
                            .size(52.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    "No recently added files found in the selected paths",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            else -> MediaGrid(
                items = items,
                columns = columns,
                gridMode = gridMode,
                favouriteKeys = favouriteKeys,
                selectedKeys = selectedKeys,
                onItemClick = onItemClick,
                onItemDoubleTap = onItemDoubleTap,
                onItemLongPress = onItemLongPress,
                onSwipeShuffle = onSwipeShuffle,
                onSetColumns = onSetColumns,
                thumbnailPadding = thumbnailPadding,
                hapticsEnabled = hapticsEnabled,
                pageStart = pageStart,
                onPageGeometryChanged = onPageGeometryChanged,
                onScrolled = onScrolled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
