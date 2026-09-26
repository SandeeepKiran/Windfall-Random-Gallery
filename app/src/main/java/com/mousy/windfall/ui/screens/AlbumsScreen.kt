package com.mousy.windfall.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mousy.windfall.data.media.MediaRepository
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.ui.components.EmptyFoldersState
import com.mousy.windfall.ui.components.MediaGrid

@Composable
fun AlbumsScreen(
    albums: List<MediaRepository.FolderInfo>,
    albumOpen: String?,
    albumItems: List<MediaItem>,
    columns: Int,
    noFolders: Boolean,
    favouriteKeys: Set<String>,
    selectedKeys: Set<String>,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbum: () -> Unit,
    onShuffle: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemDoubleTap: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    onSetColumns: (Int) -> Unit,
    onGoSettings: () -> Unit,
    thumbnailPadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val albumColumns = if (landscape) 4 else 2

    if (albumOpen != null) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCloseAlbum) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(albumOpen.substringAfterLast('/'), style = MaterialTheme.typography.titleLarge)
                    Text("${albumItems.size} items", style = MaterialTheme.typography.labelMedium)
                }
                FilledIconButton(onClick = onShuffle) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                }
            }
            if (albumItems.isEmpty()) {
                Text(
                    "This album has no media in the selected file types.",
                    modifier = Modifier.padding(40.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MediaGrid(
                    items = albumItems,
                    columns = columns,
                    gridMode = com.mousy.windfall.data.model.GridMode.SCROLL,
                    favouriteKeys = favouriteKeys,
                    selectedKeys = selectedKeys,
                    onItemClick = onItemClick,
                    onItemDoubleTap = onItemDoubleTap,
                    onItemLongPress = onItemLongPress,
                    onSwipeShuffle = {},
                    onSetColumns = onSetColumns,
                    thumbnailPadding = thumbnailPadding,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize().padding(horizontal = if (landscape) 12.dp else 16.dp)) {
            Text(
                "Albums",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 14.dp),
            )
            if (noFolders) {
                EmptyFoldersState(onChooseFolders = onGoSettings)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(albumColumns),
                    horizontalArrangement = Arrangement.spacedBy(if (landscape) 10.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (landscape) 10.dp else 16.dp),
                ) {
                    items(albums, key = { it.path }) { album ->
                        Column(
                            modifier = Modifier.clickable { onOpenAlbum(album.path) },
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (landscape) 1.15f else 1f),
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = 2.dp,
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        null,
                                        modifier = Modifier.size(if (landscape) 36.dp else 48.dp),
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            Text(
                                album.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                album.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (landscape) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${album.mediaCount}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
