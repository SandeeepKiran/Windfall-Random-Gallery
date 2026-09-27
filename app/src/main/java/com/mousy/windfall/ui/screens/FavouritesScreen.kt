package com.mousy.windfall.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
fun FavouritesScreen(
    items: List<MediaItem>,
    columns: Int,
    noFolders: Boolean,
    favouriteKeys: Set<String>,
    selectedKeys: Set<String>,
    favTypes: FileTypeFilter,
    favWindow: FavWindow,
    favTypeMenuOpen: Boolean,
    showAllFolders: Boolean,
    onToggleAllFolders: () -> Unit,
    onToggleFavTypeMenu: () -> Unit,
    onToggleFavType: (String) -> Unit,
    onSelectFavWindow: (FavWindow) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemDoubleTap: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    onSetColumns: (Int) -> Unit,
    onGoSettings: () -> Unit,
    /** Shared with the Gallery tab; only the Gallery exposes the toggle. */
    gridMode: GridMode = GridMode.SCROLL,
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
            title = "Favourites",
            types = favTypes,
            window = favWindow,
            typeMenuOpen = favTypeMenuOpen,
            onToggleTypeMenu = onToggleFavTypeMenu,
            onToggleType = onToggleFavType,
            onSelectWindow = onSelectFavWindow,
            allFoldersEnabled = showAllFolders,
            onToggleAllFolders = onToggleAllFolders,
        )

        when {
            noFolders -> EmptyFoldersState(onChooseFolders = onGoSettings)
            items.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 2.dp) {
                    Icon(
                        Icons.Default.Favorite,
                        null,
                        modifier = Modifier
                            .padding(24.dp)
                            .size(52.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Double-tap a photo in the gallery to add it, or loosen the filters above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
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
