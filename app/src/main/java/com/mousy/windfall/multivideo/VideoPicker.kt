package com.mousy.windfall.multivideo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.ui.components.ThumbSpec
import com.mousy.windfall.ui.components.gridThumbRequest

/** Smallest thumbnail cell; the grid fits as many columns as that allows. */
private val PickerCellMin = 116.dp

/**
 * Chooses videos by their pictures, not their file names (downloads rarely have useful names).
 * Three sources:
 *  - Windfall's own list: the videos in the folders chosen in Settings, newest first.
 *  - Android's photo picker: every video on the phone. Needs no permission.
 *  - Other apps: Android asks which app to use (Gallery, Google Photos, Files...).
 *
 * Several videos land in the order they were tapped: the first goes top-left.
 */
@Composable
fun VideoPicker(
    target: PickerTarget,
    libraryVideos: List<MediaItem>,
    onChooseLibrary: (List<MediaItem>) -> Unit,
    onChooseUris: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val single = target.maxCount <= 1
    val selected = remember(target) { mutableStateListOf<MediaItem>() }

    val photoPickerOne = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) onChooseUris(listOf(uri))
    }
    val photoPickerMany = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MultiVideoState.MAX_SLOTS),
    ) { uris ->
        if (uris.isNotEmpty()) onChooseUris(uris)
    }
    val otherApps = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris = result.data?.chosenUris().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && uris.isNotEmpty()) onChooseUris(uris)
    }

    val openPhotoPicker = {
        if (single) {
            photoPickerOne.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
        } else {
            photoPickerMany.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(PickVisualMedia.VideoOnly)
                    .setMaxItems(target.maxCount)
                    // Numbers the picks and returns them in that order, where the picker can.
                    .setOrderedSelection(true)
                    .build(),
            )
        }
    }
    val openOtherApps = {
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, !single)
        }
        // A chooser, so Android asks which app every time instead of silently using one.
        runCatching { otherApps.launch(Intent.createChooser(getContent, "Choose videos with")) }
        Unit
    }

    BackHandler(onBack = onDismiss)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (single) "Choose a video" else "Choose up to ${target.maxCount} videos",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (!single) {
                        Text(
                            "Tap them in order. Number 1 goes top-left.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!single) {
                    Button(
                        onClick = { onChooseLibrary(selected.toList()) },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}")
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = openPhotoPicker, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Photo picker")
                }
                FilledTonalButton(onClick = openOtherApps, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Other apps…")
                }
            }

            Text(
                "In your Windfall folders · ${videoCountLabel(libraryVideos.size)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (libraryVideos.isEmpty()) {
                Text(
                    "No videos in the folders chosen in Settings. Use Photo picker or Other apps to " +
                        "choose from anywhere on the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            } else {
                LibraryVideoGrid(
                    videos = libraryVideos,
                    selected = selected,
                    onTap = { item ->
                        val order = selected.indexOf(item)
                        when {
                            single -> onChooseLibrary(listOf(item))
                            order >= 0 -> selected.removeAt(order)
                            selected.size < target.maxCount -> selected.add(item)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryVideoGrid(
    videos: List<MediaItem>,
    selected: List<MediaItem>,
    onTap: (MediaItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = (maxWidth / PickerCellMin).toInt().coerceIn(3, 8)
        val density = LocalDensity.current
        val cellWidth = maxWidth / columns
        val thumbPx = remember(cellWidth, density) {
            ThumbSpec.bucketFor(with(density) { cellWidth.roundToPx() })
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(videos, key = { it.stableKey }) { item ->
                PickerTile(
                    item = item,
                    thumbPx = thumbPx,
                    order = selected.indexOf(item) + 1,
                    onClick = { onTap(item) },
                )
            }
        }
    }
}

/** A video's first frame, its length, and its place in the order once picked ([order] 0 = not picked). */
@Composable
private fun PickerTile(item: MediaItem, thumbPx: Int, order: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
    // The gallery grid's own request, so thumbnails it already made come straight from the cache.
    val request = remember(item.stableKey, thumbPx) { gridThumbRequest(context, item, thumbPx) }
    Box(
        Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .semantics { if (order > 0) stateDescription = "Chosen as number $order" },
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            placeholder = placeholder,
            error = placeholder,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.durationMs > 0L) {
            Text(
                WallClock.format(item.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (order > 0) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$order",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Every address an app handed back: several come in the clip data, a single one as the data. */
private fun Intent.chosenUris(): List<Uri> {
    val clip = clipData
    if (clip != null && clip.itemCount > 0) {
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }
    return listOfNotNull(data)
}
