package com.mousy.windfall.multivideo

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import coil3.video.videoFrameMillis
import com.mousy.windfall.ui.components.ThumbSpec
import kotlin.math.max
import kotlin.math.min

/** Tallest the preview gets when the wall is set to portrait, so the buttons stay in view. */
private val TallPreviewMaxHeight = 380.dp

/**
 * The Videos tab: choose how many videos, which way the screen faces, and which video goes in
 * which tile, then start the wall. The preview is the wall in miniature, tile for tile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiVideoSetupScreen(viewModel: MultiVideoViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A tile being moved: the next tile tapped trades places with it.
    var movingSlot by remember { mutableStateOf<Int?>(null) }
    // A new layout can hide the tile being moved; start over rather than swap with a hidden one.
    LaunchedEffect(state.layout) { movingSlot = null }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Multi-Video", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Watch several videos at once, tiled edge to edge with nothing else on screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Videos on screen", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WallLayout.entries.forEachIndexed { index, layout ->
                    SegmentedButton(
                        selected = state.layout == layout,
                        onClick = { viewModel.setLayout(layout) },
                        shape = SegmentedButtonDefaults.itemShape(index, WallLayout.entries.size),
                        icon = {},
                    ) { Text("${layout.count}") }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Screen", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WallRotation.entries.forEachIndexed { index, rotation ->
                    SegmentedButton(
                        selected = state.rotation == rotation,
                        onClick = { viewModel.setRotation(rotation) },
                        shape = SegmentedButtonDefaults.itemShape(index, WallRotation.entries.size),
                        icon = {},
                    ) { Text(rotation.label) }
                }
            }
        }

        WallPreview(
            state = state,
            movingSlot = movingSlot,
            onChoose = { slot -> viewModel.openPicker(PickerTarget.single(slot)) },
            onStartMove = { slot -> movingSlot = slot },
            onPlaceMoving = { slot ->
                movingSlot?.let { from -> viewModel.swapSlots(from, slot) }
                movingSlot = null
            },
            onToggleSound = viewModel::toggleSound,
            onRemove = viewModel::clearSlot,
        )

        Text(
            when (val moving = movingSlot) {
                null -> "Tap a tile to choose or change its video. Tile 1 is the top-left one."
                else -> "Tap the tile where video ${moving + 1} should go. Tap it again to leave it."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (movingSlot != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { viewModel.openPicker(PickerTarget.wholeWall(state.layout)) },
                modifier = Modifier.weight(1f),
            ) { Text("Choose videos") }
            Button(
                onClick = viewModel::openWall,
                enabled = state.hasVideos,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("On the wall", style = MaterialTheme.typography.titleSmall)
            listOf(
                "Tap a video to show the controls. They hide again after 3 seconds.",
                "Double-tap the left or right side of a video to skip 10 seconds. " +
                    "Double-tap the middle to pause it.",
                "Tap the number on a video to turn its sound on or off.",
                "Press Back, or tap Exit, to come back here.",
            ).forEach { tip ->
                Text(
                    "•  $tip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WallPreview(
    state: MultiVideoState,
    movingSlot: Int?,
    onChoose: (Int) -> Unit,
    onStartMove: (Int) -> Unit,
    onPlaceMoving: (Int) -> Unit,
    onToggleSound: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val longSide = max(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
    val shortSide = min(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
        .coerceAtLeast(1f)
    val wide = when (state.rotation) {
        WallRotation.LANDSCAPE -> true
        WallRotation.PORTRAIT -> false
        WallRotation.AUTO -> configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
    // Width over height of the phone's screen, turned the way the wall will face.
    val aspect = if (wide) longSide / shortSide else shortSide / longSide

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width: Dp = if (wide) maxWidth else min(maxWidth, TallPreviewMaxHeight * aspect)
        val height: Dp = width / aspect
        val (columns, rows) = WallGrid.columnsAndRows(state.layout.count, wide)
        val density = LocalDensity.current
        val thumbPx = ThumbSpec.bucketFor(with(density) { (width / columns).roundToPx() })

        Column(
            Modifier
                .size(width, height)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        ) {
            for (row in 0 until rows) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    for (column in 0 until columns) {
                        val slot = row * columns + column
                        PreviewTile(
                            slotNumber = slot + 1,
                            video = state.slots[slot],
                            audible = state.audible(slot),
                            thumbPx = thumbPx,
                            isMoving = movingSlot == slot,
                            moveInProgress = movingSlot != null,
                            onChoose = { onChoose(slot) },
                            onStartMove = { onStartMove(slot) },
                            onPlaceMoving = { onPlaceMoving(slot) },
                            onToggleSound = { onToggleSound(slot) },
                            onRemove = { onRemove(slot) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewTile(
    slotNumber: Int,
    video: WallVideo?,
    audible: Boolean,
    thumbPx: Int,
    isMoving: Boolean,
    moveInProgress: Boolean,
    onChoose: () -> Unit,
    onStartMove: () -> Unit,
    onPlaceMoving: () -> Unit,
    onToggleSound: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier
            .border(0.5.dp, Color.White.copy(alpha = 0.25f))
            .combinedClickable(
                onClick = {
                    when {
                        moveInProgress -> onPlaceMoving()
                        video == null -> onChoose()
                        else -> menuOpen = true
                    }
                },
                onLongClick = { if (video != null && !moveInProgress) onStartMove() },
            ),
    ) {
        if (video != null) {
            val request = remember(video.uri, thumbPx) {
                ImageRequest.Builder(context)
                    .data(video.uri.toUri())
                    .size(thumbPx)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .videoFrameMillis(0)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = video.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Text("Add", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            }
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("$slotNumber", style = MaterialTheme.typography.labelMedium, color = Color.White)
            if (audible) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Has sound",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (isMoving) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary),
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Choose another video") },
                onClick = {
                    menuOpen = false
                    onChoose()
                },
            )
            DropdownMenuItem(
                text = { Text(if (audible) "Mute" else "Sound on") },
                leadingIcon = {
                    Icon(
                        if (audible) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleSound()
                },
            )
            DropdownMenuItem(
                text = { Text("Move to another tile") },
                onClick = {
                    menuOpen = false
                    onStartMove()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}
