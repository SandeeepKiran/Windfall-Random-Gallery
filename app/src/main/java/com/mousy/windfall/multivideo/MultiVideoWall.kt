package com.mousy.windfall.multivideo

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveFromQueue
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** How long the controls stay after the last touch. */
private const val CONTROLS_HIDE_MS = 3_000L
private const val SEEK_STEP_MS = 10_000L
private const val FLASH_MS = 600L

/** Below this width the control buttons wrap into two rows of four, and the video bar takes two rows. */
private val CompactWidth = 600.dp

private val OverlayColor = Color.Black.copy(alpha = 0.62f)

/** What the activity is asked for while the wall is open. */
fun WallRotation.activityOrientation(): Int = when (this) {
    // Either landscape side, whichever way up the phone is held.
    WallRotation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    WallRotation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    // Follows the phone even with its rotation lock on: the user asked for it here.
    WallRotation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
}

/**
 * The wall: the chosen videos tiled edge to edge, and nothing else. A tap shows the controls;
 * they fade [CONTROLS_HIDE_MS] after the last touch and only the videos are left. Back, or the
 * Exit button, always leads out.
 */
@Composable
fun MultiVideoWall(viewModel: MultiVideoViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val players by viewModel.players.collectAsStateWithLifecycle()
    val view = LocalView.current

    // Players run only while the wall is visible; in the background the decoders go back to
    // the phone and each video resumes where it was.
    LifecycleStartEffect(Unit) {
        viewModel.onWallShowing(true)
        onStopOrDispose { viewModel.onWallShowing(false) }
    }
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // The video picker, drawn over the wall, has its own Back handler, which wins while it is open.
    BackHandler(onBack = viewModel::closeWall)

    // Shown on the way in, so the controls are seen once before they hide.
    var controlsVisible by remember { mutableStateOf(true) }
    var showHint by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var layoutMenuOpen by remember { mutableStateOf(false) }
    var focusedSlot by remember {
        mutableIntStateOf(state.shownSlots.indexOfFirst { it != null }.coerceAtLeast(0))
    }
    val keepControls: () -> Unit = {
        controlsVisible = true
        touches++
    }
    LaunchedEffect(controlsVisible, touches, layoutMenuOpen) {
        if (controlsVisible && !layoutMenuOpen) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
            showHint = false
        }
    }

    val count = state.layout.count
    val focused = focusedSlot.coerceIn(0, count - 1)

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val wide = maxWidth > maxHeight
        val compact = maxWidth < CompactWidth
        val (columns, rows) = WallGrid.columnsAndRows(count, wide)

        TileGrid(columns = columns, rows = rows, modifier = Modifier.fillMaxSize()) {
            for (slot in 0 until count) {
                // Keyed by slot, so a tile (and its video view) stays the same one when the
                // layout or the rotation moves it: the video carries on without a black flash.
                key(slot) {
                    val row = slot / columns
                    val video = state.slots[slot]
                    WallTile(
                        slotNumber = slot + 1,
                        video = video,
                        player = players.getOrNull(slot),
                        fill = state.fill,
                        audible = state.audible(slot),
                        controlsVisible = controlsVisible,
                        // Toward the middle of the screen, clear of the control bars at the top
                        // and the bottom.
                        chipAlignment = when {
                            rows == 1 -> Alignment.Center
                            row == 0 -> Alignment.BottomCenter
                            row == rows - 1 -> Alignment.TopCenter
                            else -> Alignment.Center
                        },
                        highlighted = controlsVisible && count > 1 && slot == focused,
                        onTap = {
                            when {
                                video == null && controlsVisible ->
                                    viewModel.openPicker(PickerTarget.single(slot))
                                !controlsVisible || slot != focused -> {
                                    focusedSlot = slot
                                    keepControls()
                                }
                                else -> controlsVisible = false
                            }
                        },
                        onSeekBy = { viewModel.seekBy(slot, it) },
                        onTogglePlay = { viewModel.togglePlay(slot) },
                        onToggleSound = {
                            keepControls()
                            viewModel.toggleSound(slot)
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            WallTopControls(
                state = state,
                compact = compact,
                showHint = showHint,
                layoutMenuOpen = layoutMenuOpen,
                onLayoutMenuOpenChange = {
                    layoutMenuOpen = it
                    keepControls()
                },
                onExit = viewModel::closeWall,
                onPlayAll = {
                    keepControls()
                    viewModel.playAll()
                },
                onPauseAll = {
                    keepControls()
                    viewModel.pauseAll()
                },
                onRestartAll = {
                    keepControls()
                    viewModel.restartAll()
                },
                onToggleWallSound = {
                    keepControls()
                    viewModel.toggleWallSound()
                },
                onSetLayout = {
                    keepControls()
                    viewModel.setLayout(it)
                },
                onToggleFill = {
                    keepControls()
                    viewModel.toggleFill()
                },
                onCycleRotation = {
                    keepControls()
                    viewModel.cycleRotation()
                },
            )
        }

        val focusedVideo = state.slots[focused]
        val focusedPlayer = players.getOrNull(focused)
        AnimatedVisibility(
            visible = controlsVisible && focusedVideo != null && focusedPlayer != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (focusedVideo != null && focusedPlayer != null) {
                WallVideoBar(
                    slotNumber = focused + 1,
                    video = focusedVideo,
                    player = focusedPlayer,
                    audible = state.audible(focused),
                    compact = compact,
                    onTouch = keepControls,
                    onTogglePlay = { viewModel.togglePlay(focused) },
                    onSeekBy = { viewModel.seekBy(focused, it) },
                    onSeekTo = { viewModel.seekTo(focused, it) },
                    onToggleSound = { viewModel.toggleSound(focused) },
                    onReplace = { viewModel.openPicker(PickerTarget.single(focused)) },
                    onRemove = { viewModel.clearSlot(focused) },
                )
            }
        }
    }
}

/**
 * Tiles side by side with no gaps, filled row by row: the n-th child goes in the n-th cell.
 * One parent for all tiles (not a Row per row) is what lets a tile keep its identity when the
 * grid changes shape. Edges are rounded per cell, so the tiles always cover every pixel.
 */
@Composable
private fun TileGrid(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        fun edge(index: Int, total: Int, parts: Int) = (index.toLong() * total / parts).toInt()
        val placeables = measurables.mapIndexed { index, measurable ->
            val column = index % columns
            val row = index / columns
            measurable.measure(
                Constraints.fixed(
                    width = edge(column + 1, width, columns) - edge(column, width, columns),
                    height = edge(row + 1, height, rows) - edge(row, height, rows),
                ),
            )
        }
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(
                    x = edge(index % columns, width, columns),
                    y = edge(index / columns, height, rows),
                )
            }
        }
    }
}

private enum class TileFlash { BACK, FORWARD, PLAY, PAUSE }

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun WallTile(
    slotNumber: Int,
    video: WallVideo?,
    player: Player?,
    fill: Boolean,
    audible: Boolean,
    controlsVisible: Boolean,
    chipAlignment: Alignment,
    highlighted: Boolean,
    onTap: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var failed by remember(player) { mutableStateOf(player?.playerError != null) }
    var paused by remember(player) { mutableStateOf(player?.playWhenReady == false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerErrorChanged(error: PlaybackException?) {
                failed = error != null
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    var flash by remember { mutableStateOf<TileFlash?>(null) }
    var flashCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashCount) {
        if (flash != null) {
            delay(FLASH_MS)
            flash = null
        }
    }

    // The gesture block below lives as long as the tile does, so it must read the newest
    // callbacks, not the ones from the first frame.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSeekBy by rememberUpdatedState(onSeekBy)
    val currentOnTogglePlay by rememberUpdatedState(onTogglePlay)
    val hasVideo = video != null

    Box(
        modifier
            .clipToBounds()
            .pointerInput(hasVideo) {
                // Like the viewer: double-tap the left or right third to skip, the middle to
                // pause. An empty tile has no double-tap, so its single tap needn't wait for one.
                val onDoubleTap: ((Offset) -> Unit)? = if (!hasVideo) null else ({ offset ->
                    val third = size.width / 3f
                    flash = when {
                        offset.x < third -> {
                            currentOnSeekBy(-SEEK_STEP_MS)
                            TileFlash.BACK
                        }
                        offset.x > third * 2 -> {
                            currentOnSeekBy(SEEK_STEP_MS)
                            TileFlash.FORWARD
                        }
                        else -> {
                            val willPlay = paused
                            currentOnTogglePlay()
                            if (willPlay) TileFlash.PLAY else TileFlash.PAUSE
                        }
                    }
                    flashCount++
                })
                detectTapGestures(onTap = { currentOnTap() }, onDoubleTap = onDoubleTap)
            },
    ) {
        if (video != null) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                // TextureView, not the lighter SurfaceView: it scales whatever picture the
                // decoder hands over to the tile, and a cropped (Fill) video can be clipped to
                // its tile. With SurfaceView, a playing video that landed in a new tile kept a
                // stretched, cut-off picture (seen on the emulator's decoder).
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
            )
            if (failed) {
                TileNotice(
                    icon = Icons.Default.ErrorOutline,
                    text = "Can't play this video",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (controlsVisible) {
                TileStatusChip(
                    slotNumber = slotNumber,
                    audible = audible,
                    paused = paused,
                    onToggleSound = onToggleSound,
                    modifier = Modifier
                        .align(chipAlignment)
                        .padding(12.dp),
                )
            }
        } else if (controlsVisible) {
            TileNotice(
                icon = Icons.Default.Add,
                text = "Add a video",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        flash?.let { kind ->
            val (icon, alignment) = when (kind) {
                TileFlash.BACK -> Icons.Default.Replay10 to Alignment.CenterStart
                TileFlash.FORWARD -> Icons.Default.Forward10 to Alignment.CenterEnd
                TileFlash.PLAY -> Icons.Default.PlayArrow to Alignment.Center
                TileFlash.PAUSE -> Icons.Default.Pause to Alignment.Center
            }
            Surface(
                color = OverlayColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(alignment)
                    .padding(horizontal = 20.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(28.dp))
            }
        }

        if (highlighted) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(2.dp, MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Tile number, paused or not, and whether it has sound. Tapping it turns the sound on or off. */
@Composable
private fun TileStatusChip(
    slotNumber: Int,
    audible: Boolean,
    paused: Boolean,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggleSound,
        color = OverlayColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$slotNumber", style = MaterialTheme.typography.titleSmall)
            if (paused) Icon(Icons.Default.Pause, contentDescription = "Paused", modifier = Modifier.size(20.dp))
            Icon(
                if (audible) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (audible) "Sound on, tap to mute" else "Muted, tap for sound",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TileNotice(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
        Text(
            text,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallTopControls(
    state: MultiVideoState,
    compact: Boolean,
    showHint: Boolean,
    layoutMenuOpen: Boolean,
    onLayoutMenuOpenChange: (Boolean) -> Unit,
    onExit: () -> Unit,
    onPlayAll: () -> Unit,
    onPauseAll: () -> Unit,
    onRestartAll: () -> Unit,
    onToggleWallSound: () -> Unit,
    onSetLayout: (WallLayout) -> Unit,
    onToggleFill: () -> Unit,
    onCycleRotation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(color = OverlayColor, contentColor = Color.White, shape = RoundedCornerShape(28.dp)) {
            // Every button keeps its name, even on a narrow screen: there they wrap into two
            // rows of four instead.
            FlowRow(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                maxItemsInEachRow = if (compact) 4 else Int.MAX_VALUE,
            ) {
                ControlButton(Icons.AutoMirrored.Filled.ArrowBack, "Exit", onExit)
                ControlButton(Icons.Default.PlayArrow, "Play all", onPlayAll)
                ControlButton(Icons.Default.Pause, "Pause all", onPauseAll)
                ControlButton(Icons.Default.Replay, "Restart all", onRestartAll)
                val soundOn = state.anyAudible
                ControlButton(
                    icon = if (soundOn) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    label = if (soundOn) "Mute all" else "Sound on",
                    onClick = onToggleWallSound,
                )
                Box {
                    ControlButton(Icons.Default.GridView, "Layout") { onLayoutMenuOpenChange(true) }
                    DropdownMenu(
                        expanded = layoutMenuOpen,
                        onDismissRequest = { onLayoutMenuOpenChange(false) },
                    ) {
                        WallLayout.entries.forEach { layout ->
                            DropdownMenuItem(
                                text = { Text(videoCountLabel(layout.count)) },
                                trailingIcon = if (layout == state.layout) {
                                    { Icon(Icons.Default.Check, contentDescription = "Current") }
                                } else {
                                    null
                                },
                                onClick = {
                                    onSetLayout(layout)
                                    onLayoutMenuOpenChange(false)
                                },
                            )
                        }
                    }
                }
                ControlButton(
                    icon = if (state.fill) Icons.Default.FitScreen else Icons.Default.Crop,
                    label = if (state.fill) "Show whole" else "Fill tiles",
                    onClick = onToggleFill,
                )
                ControlButton(
                    icon = when (state.rotation) {
                        WallRotation.LANDSCAPE -> Icons.Default.ScreenLockLandscape
                        WallRotation.PORTRAIT -> Icons.Default.ScreenLockPortrait
                        WallRotation.AUTO -> Icons.Default.ScreenRotation
                    },
                    label = state.rotation.label,
                    onClick = onCycleRotation,
                )
            }
        }
        if (showHint) {
            Surface(
                color = OverlayColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    "Tap a video for its controls. Double-tap its sides to skip 10 seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** An icon with its name under it: the name says what the icon alone might not. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .widthIn(min = 68.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Controls for the one video last tapped: like a normal player's, for that tile only. */
@Composable
private fun WallVideoBar(
    slotNumber: Int,
    video: WallVideo,
    player: Player,
    audible: Boolean,
    compact: Boolean,
    onTouch: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleSound: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var positionMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember(player) { mutableLongStateOf(0L) }
    var playing by remember(player) { mutableStateOf(player.playWhenReady) }
    var scrubbing by remember(player) { mutableStateOf(false) }
    var scrubFraction by remember(player) { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (isActive) {
            if (!scrubbing) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                // Unknown durations are negative (C.TIME_UNSET).
                durationMs = player.duration.coerceAtLeast(0L)
            }
            playing = player.playWhenReady
            delay(250)
        }
    }
    val fraction = when {
        scrubbing -> scrubFraction
        durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPositionMs = if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs

    val playButton: @Composable () -> Unit = {
        IconButton(onClick = {
            onTouch()
            onTogglePlay()
            playing = !playing
        }) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
            )
        }
    }
    val skipButtons: @Composable () -> Unit = {
        IconButton(onClick = {
            onTouch()
            onSeekBy(-SEEK_STEP_MS)
        }) { Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds") }
        IconButton(onClick = {
            onTouch()
            onSeekBy(SEEK_STEP_MS)
        }) { Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds") }
    }
    val soundButton: @Composable () -> Unit = {
        IconButton(onClick = {
            onTouch()
            onToggleSound()
        }) {
            Icon(
                if (audible) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (audible) "Mute this video" else "Sound on for this video",
            )
        }
    }
    val seekRow: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Text(WallClock.format(shownPositionMs), style = MaterialTheme.typography.labelMedium)
            WallSeekBar(
                fraction = fraction,
                onChange = {
                    onTouch()
                    scrubbing = true
                    scrubFraction = it
                },
                onFinished = {
                    onSeekTo((scrubFraction * durationMs).toLong())
                    scrubbing = false
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(WallClock.format(durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }

    Surface(
        color = OverlayColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$slotNumber · ${video.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Spelled out: a bare cross here would read as "close the controls".
                BarTextButton(Icons.Default.VideoLibrary, "Change", onReplace)
                BarTextButton(Icons.Default.RemoveFromQueue, "Remove", onRemove)
            }
            if (compact) {
                seekRow(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    playButton()
                    skipButtons()
                    Spacer(Modifier.weight(1f))
                    soundButton()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    playButton()
                    skipButtons()
                    seekRow(Modifier.weight(1f).padding(horizontal = 8.dp))
                    soundButton()
                }
            }
        }
    }
}

@Composable
private fun BarTextButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** The viewer's slim seek bar: thin track and handle, full-size touch area. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallSeekBar(
    fraction: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    )
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = fraction,
        onValueChange = onChange,
        onValueChangeFinished = onFinished,
        interactionSource = interaction,
        colors = colors,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interaction,
                colors = colors,
                thumbSize = DpSize(5.dp, 22.dp),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                modifier = Modifier.height(4.dp),
            )
        },
        modifier = modifier,
    )
}

internal fun videoCountLabel(count: Int): String = if (count == 1) "1 video" else "$count videos"
