package com.mousy.windfall.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Size
import coil3.video.preferVideoFrameEmbeddedThumbnailKey
import coil3.video.videoFrameMillis
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.data.model.MediaType
import com.mousy.windfall.data.model.SlideshowSpeeds
import com.mousy.windfall.ui.theme.FavouriteHeart
import com.mousy.windfall.util.GalleryHaptics
import com.mousy.windfall.util.PlayerWarmPool
import com.mousy.windfall.util.VideoPositionMemory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val MenuShape = RoundedCornerShape(20.dp)
private const val CHROME_AUTO_HIDE_MS = 5_000L

/**
 * Chrome enter/exit durations, shared by the top bar, the media control bar, and matched by
 * the bottom tab bar in MainScaffold — everything vanishes as ONE coordinated motion.
 */
const val CHROME_ANIM_IN_MS = 180
const val CHROME_ANIM_OUT_MS = 150
/**
 * Decode headroom above the screen size. Full-resolution decodes of a 12MP photo were the
 * cause of the black flash on swipe; 1.5x still leaves detail for pinch-zoom.
 */
private const val VIEWER_DECODE_SCALE = 1.5f

/**
 * Fullscreen viewer. Top/bottom chrome is an overlay (fade only) so media stays full-bleed
 * and does not re-layout when chrome appears or vanishes.
 */
@Composable
fun FullscreenViewer(
    item: MediaItem?,
    index: Int,
    count: Int,
    /** Random access into the viewer's list — the pager composes neighbours ahead of a swipe. */
    itemAt: (Int) -> MediaItem?,
    isPlaying: Boolean,
    chromeVisible: Boolean,
    menuOpen: Boolean,
    speedMenuOpen: Boolean,
    speedIndex: Int,
    customMs: Long,
    isFavourite: Boolean,
    disableSwipeDelete: Boolean,
    deleteEnabled: Boolean = true,
    slideshowMode: Boolean = true,
    muted: Boolean = false,
    loopVideos: Boolean = true,
    hapticsEnabled: Boolean = true,
    onClose: () -> Unit,
    onToggleChrome: () -> Unit,
    /** A user swipe settled on an absolute index. */
    onJumpTo: (Int) -> Unit,
    onSwipeUpDelete: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleMenu: () -> Unit,
    onToggleSpeedMenu: () -> Unit,
    onSpeedSelected: (Int) -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onToggleMute: () -> Unit = {},
    onVideoEnded: () -> Unit = {},
    onUserInteracted: () -> Unit = {},
    chromeAutoHideNonce: Int = 0,
    prefetch: List<MediaItem> = emptyList(),
    /** Items 2-3 swipes away — warmed at thumb resolution after a 1 s dwell. */
    farPrefetch: List<MediaItem> = emptyList(),
    gridThumbBucketPx: Int = 256,
    controlsBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val decodeSize = rememberViewerDecodeSize()
    // Session-sticky playback prefs: hold across item swipes, reset when the viewer closes.
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var loopOverride by remember { mutableStateOf<Boolean?>(null) }

    // Players prewarmed for neighbours die with the viewer.
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        onDispose {
            PlayerWarmPool.releaseAll()
            // The video brightness gesture overrides the whole window; hand brightness back to
            // the system on the way out, or the gallery stayed at the video's level.
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // Auto-hide chrome after inactivity while visible
    LaunchedEffect(chromeVisible, item?.stableKey, menuOpen, speedMenuOpen, chromeAutoHideNonce) {
        if (!chromeVisible || menuOpen || speedMenuOpen) return@LaunchedEffect
        delay(CHROME_AUTO_HIDE_MS)
        onToggleChrome()
    }

    // Warm the neighbours: images get decoded ahead of the swipe; videos get a fully PREPARED
    // player (codec up, first seconds buffered), so swiping to them starts playback instantly.
    LaunchedEffect(item?.stableKey, decodeSize) {
        if (prefetch.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        prefetch.forEach { neighbour ->
            when (neighbour.mediaType) {
                MediaType.AUDIO -> Unit
                MediaType.VIDEO -> PlayerWarmPool.prewarm(context, neighbour.uri)
                else -> loader.enqueue(neighbour.viewerRequest(context, decodeSize, gridThumbBucketPx))
            }
        }
    }

    // DWELL LOOK-AHEAD: if the user has stared at this item for a second, the CPU is idle —
    // quietly warm the THUMBNAILS of items 2-3 swipes away. Thumb-res only: they're the
    // viewer's instant placeholders, and full-res that far out would waste RAM on old phones.
    LaunchedEffect(item?.stableKey, farPrefetch) {
        if (farPrefetch.isEmpty()) return@LaunchedEffect
        delay(1_000)
        val loader = SingletonImageLoader.get(context)
        farPrefetch.forEach { far ->
            if (far.mediaType != MediaType.AUDIO) {
                loader.enqueue(gridThumbRequest(context, far, gridThumbBucketPx))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Media layer — always full-bleed; never padded by chrome.
        // A real pager: the neighbour page is composed and physically attached to this one, it
        // tracks the finger 1:1, and a release settles with the finger's velocity. Neighbours
        // are already warm (images decoded, video players prepared), so the incoming page shows
        // content immediately instead of fading through black.
        val latestCount by rememberUpdatedState(count)
        val pagerState = rememberPagerState(
            initialPage = index.coerceIn(0, (count - 1).coerceAtLeast(0)),
        ) { latestCount }
        val latestIndex by rememberUpdatedState(index)
        val latestJumpTo by rememberUpdatedState(onJumpTo)

        // Pager → ViewModel: report where a user swipe settled.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                if (page != latestIndex) latestJumpTo(page)
            }
        }
        // ViewModel → pager: slideshow advances and delete-shifts move the pager.
        LaunchedEffect(index, count) {
            if (count <= 0) return@LaunchedEffect
            val page = index.coerceIn(0, count - 1)
            if (page != pagerState.currentPage && !pagerState.isScrollInProgress) {
                // Wrap-around (last → first) shouldn't visibly rewind through every page.
                if (abs(page - pagerState.currentPage) > 1) {
                    pagerState.scrollToPage(page)
                } else {
                    pagerState.animateScrollToPage(page)
                }
            }
        }

        // Vertical swipe navigation: down shows the previous media, up the next — the same
        // pager animation as a horizontal swipe, just triggered from the vertical axis.
        val pagerScope = rememberCoroutineScope()
        val onSwipeVertical: (Int) -> Unit = { delta ->
            val target = pagerState.currentPage + delta
            if (target in 0 until latestCount) {
                pagerScope.launch { pagerState.animateScrollToPage(target) }
            }
        }

        HorizontalPager(
            state = pagerState,
            key = { page -> itemAt(page)?.stableKey ?: page },
            beyondViewportPageCount = 1,
            pageSpacing = 0.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageItem = itemAt(page)
            // Only the settled page owns a player: neighbours render a static frame, so
            // nothing plays audio off-screen and a swipe only ever moves cheap bitmaps.
            val isSettled = pagerState.settledPage == page
            when (pageItem?.mediaType) {
                MediaType.VIDEO, MediaType.AUDIO -> when {
                    isSettled -> MediaPlayerSurface(
                        item = pageItem,
                        muted = muted,
                        loopVideos = loopOverride ?: loopVideos,
                        onToggleLoop = { loopOverride = !(loopOverride ?: loopVideos) },
                        playbackSpeed = playbackSpeed,
                        onSpeedChange = { playbackSpeed = it },
                        hapticsEnabled = hapticsEnabled,
                        slideshowMode = slideshowMode,
                        controlsVisible = chromeVisible,
                        onToggleChrome = {
                            onUserInteracted()
                            onToggleChrome()
                        },
                        onSwipeUpDelete = onSwipeUpDelete,
                        disableSwipeDelete = disableSwipeDelete,
                        onSwipeVertical = onSwipeVertical,
                        onToggleMute = onToggleMute,
                        onTogglePlayLocal = { onUserInteracted() },
                        onVideoEnded = onVideoEnded,
                        onSeekInteracted = onUserInteracted,
                        controlsBottomPadding = controlsBottomPadding,
                    )
                    pageItem.mediaType == MediaType.AUDIO ->
                        AudioArtwork(item = pageItem, modifier = Modifier.fillMaxSize())
                    else -> VideoFramePage(
                        item = pageItem,
                        decodeSize = decodeSize,
                        gridThumbBucketPx = gridThumbBucketPx,
                    )
                }
                null -> Box(modifier = Modifier.fillMaxSize())
                else -> ZoomableImageSurface(
                    item = pageItem,
                    decodeSize = decodeSize,
                    gridThumbBucketPx = gridThumbBucketPx,
                    onToggleChrome = {
                        onUserInteracted()
                        onToggleChrome()
                    },
                    onSwipeUpDelete = onSwipeUpDelete,
                    disableSwipeDelete = disableSwipeDelete,
                    onSwipeVertical = onSwipeVertical,
                )
            }
        }

        // Top chrome overlay — slides up and fades, mirroring the bottom chrome's motion
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(CHROME_ANIM_IN_MS)) +
                slideInVertically(tween(CHROME_ANIM_IN_MS)) { -it },
            exit = fadeOut(tween(CHROME_ANIM_OUT_MS)) +
                slideOutVertically(tween(CHROME_ANIM_OUT_MS)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.62f), Color.Transparent)))
                    // Zero while system bars are hidden; keeps chrome clear if they reappear.
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = item?.let { formatDate(it.dateTakenMs, it.dateAddedMs) } ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (slideshowMode) {
                    IconButton(onClick = {
                        onUserInteracted()
                        onTogglePlay()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause slideshow" else "Play slideshow",
                            tint = Color.White,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 56.dp)
                            .clickable(onClick = {
                                onUserInteracted()
                                onToggleSpeedMenu()
                            })
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Speed, null, tint = Color.White)
                        Text(
                            speedLabel(speedIndex, customMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 4.dp),
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = {
                    onUserInteracted()
                    onToggleFavourite()
                }) {
                    Icon(
                        // Outline when not favourited: filled-vs-outline stays readable even in
                        // system greyscale mode, where pink-vs-grey does not.
                        if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                        tint = if (isFavourite) FavouriteHeart else Color.White.copy(alpha = 0.85f),
                    )
                }
                IconButton(onClick = {
                    onUserInteracted()
                    onToggleMenu()
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 8.dp),
        ) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onToggleMenu,
                shape = MenuShape,
            ) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = { onShare(); onToggleMenu() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    enabled = deleteEnabled,
                    onClick = {
                        if (deleteEnabled) {
                            onDelete()
                            onToggleMenu()
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Details") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { onDetails(); onToggleMenu() },
                )
            }
        }

        if (slideshowMode) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                DropdownMenu(
                    expanded = speedMenuOpen,
                    onDismissRequest = onToggleSpeedMenu,
                    shape = MenuShape,
                ) {
                    SlideshowSpeeds.speeds.forEachIndexed { i, speed ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (i) {
                                        SlideshowSpeeds.CUSTOM_INDEX -> "Custom…"
                                        SlideshowSpeeds.OFF_INDEX -> "Off"
                                        else -> speed.label
                                    },
                                )
                            },
                            onClick = { onSpeedSelected(i) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Screen-sized decode target, so the viewer never asks Coil for a full-resolution bitmap. */
@Composable
private fun rememberViewerDecodeSize(): Size {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(config.screenWidthDp, config.screenHeightDp, density) {
        with(density) {
            val w = (config.screenWidthDp.dp.toPx() * VIEWER_DECODE_SCALE).roundToInt()
            val h = (config.screenHeightDp.dp.toPx() * VIEWER_DECODE_SCALE).roundToInt()
            Size(w.coerceAtLeast(1), h.coerceAtLeast(1))
        }
    }
}

/**
 * Full-view request for an item. The grid's cached thumbnail is named as the placeholder so
 * something appears on the very first frame instead of black while the large decode runs.
 */
private fun MediaItem.viewerRequest(
    context: android.content.Context,
    decodeSize: Size,
    gridThumbBucketPx: Int,
): ImageRequest {
    val cacheKey = ThumbSpec.fullKey(stableKey, decodeSize.width.pxOrZero(), decodeSize.height.pxOrZero())
    return ImageRequest.Builder(context)
        .data(uri)
        .size(decodeSize)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .placeholderMemoryCacheKey(MemoryCache.Key(ThumbSpec.thumbKey(stableKey, gridThumbBucketPx)))
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .apply {
            if (mediaType == MediaType.VIDEO) {
                videoFrameMillis(0)
                preferVideoFrameEmbeddedThumbnailKey(true)
            }
        }
        .build()
}

private fun Dimension.pxOrZero(): Int = (this as? Dimension.Pixels)?.px ?: 0

/** Static first frame for a video page that isn't settled yet — the player attaches on settle. */
@Composable
private fun VideoFramePage(
    item: MediaItem,
    decodeSize: Size,
    gridThumbBucketPx: Int,
) {
    val context = LocalContext.current
    val model = remember(item.stableKey, decodeSize, gridThumbBucketPx) {
        item.viewerRequest(context, decodeSize, gridThumbBucketPx)
    }
    AsyncImage(
        model = model,
        contentDescription = item.displayName,
        contentScale = ContentScale.Fit,
        placeholder = remember { ColorPainter(Color.Black) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ZoomableImageSurface(
    item: MediaItem,
    decodeSize: Size,
    gridThumbBucketPx: Int,
    onToggleChrome: () -> Unit,
    onSwipeUpDelete: () -> Unit,
    disableSwipeDelete: Boolean,
    onSwipeVertical: (Int) -> Unit = {},
) {
    var scale by remember(item.stableKey) { mutableFloatStateOf(1f) }
    var offset by remember(item.stableKey) { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    val placeholder = remember { ColorPainter(Color.Black) }
    val imageModel = remember(item.uri, item.stableKey, decodeSize, gridThumbBucketPx) {
        item.viewerRequest(context, decodeSize, gridThumbBucketPx)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.stableKey) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            offset = Offset(
                                (cx - tapOffset.x) * (scale - 1f),
                                (cy - tapOffset.y) * (scale - 1f),
                            )
                        }
                    },
                )
            }
            .pointerInput(item.stableKey, disableSwipeDelete) {
                // A one-finger drag is sorted by its first movement: mostly up or down is a swipe
                // to the next or previous item, mostly sideways is left to the pager. Sorting
                // happens before the pager's own threshold, and an up/down drag is then kept from
                // the pager. Before, a slightly slanted swipe became a sideways page drag that
                // snapped back, so swiping up or down seemed to do nothing.
                val decideAfter = viewConfiguration.touchSlop * DIRECTION_DECIDE_FRACTION
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalPan = Offset.Zero
                    var multipoint = false
                    var vertical: Boolean? = null
                    val velocity = VelocityTracker()
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            multipoint = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            if (newScale > 1f) {
                                val maxX = (size.width * (newScale - 1f)) / 2f
                                val maxY = (size.height * (newScale - 1f)) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            } else {
                                offset = Offset.Zero
                            }
                            scale = newScale
                            pressed.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressed.size == 1 && scale > 1.01f) {
                            val pan = event.calculatePan()
                            val maxX = (size.width * (scale - 1f)) / 2f
                            val maxY = (size.height * (scale - 1f)) / 2f
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            pressed.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressed.size == 1 && !multipoint) {
                            val change = pressed.first()
                            totalPan += change.positionChange()
                            velocity.addPosition(change.uptimeMillis, change.position)
                            if (vertical == null && totalPan.getDistance() > decideAfter) {
                                vertical = abs(totalPan.y) > abs(totalPan.x)
                            }
                            if (vertical == true) change.consume()
                        }
                    } while (event.changes.any { it.pressed })

                    if (vertical == true && !multipoint && scale <= 1.01f) {
                        val dy = totalPan.y
                        if (isNavigationSwipe(dy, velocity.calculateVelocity().y)) {
                            when {
                                // Flick-to-delete keeps priority while it's switched on.
                                dy < 0 && !disableSwipeDelete -> onSwipeUpDelete()
                                dy < 0 -> onSwipeVertical(1) // up = next media
                                else -> onSwipeVertical(-1) // down = previous media
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

/**
 * Video/audio with custom Material 3 control bar (mute, scrubber, play/pause, immersive).
 * Uses media3-ui-compose ContentFrame; chrome stays Compose overlay so layout stays stable.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaPlayerSurface(
    item: MediaItem,
    muted: Boolean,
    loopVideos: Boolean,
    slideshowMode: Boolean,
    controlsVisible: Boolean,
    playbackSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
    onToggleLoop: () -> Unit = {},
    hapticsEnabled: Boolean = true,
    onToggleChrome: () -> Unit,
    onSwipeUpDelete: () -> Unit,
    disableSwipeDelete: Boolean,
    onSwipeVertical: (Int) -> Unit = {},
    onToggleMute: () -> Unit,
    onTogglePlayLocal: () -> Unit,
    onVideoEnded: () -> Unit,
    onSeekInteracted: () -> Unit,
    controlsBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(item.stableKey) { mutableStateOf(true) }
    var positionMs by remember(item.stableKey) { mutableLongStateOf(0L) }
    var durationMs by remember(item.stableKey) { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var speedMenuOpenLocal by remember { mutableStateOf(false) }
    // Gesture feedback: hold-for-2x, double-tap seek flash, brightness/volume level bar.
    var holdSpeed by remember(item.stableKey) { mutableStateOf(false) }
    var seekFlash by remember { mutableIntStateOf(0) }
    var seekFlashNonce by remember { mutableIntStateOf(0) }
    var levelKind by remember { mutableIntStateOf(0) } // 0 none, 1 brightness, 2 volume
    var levelValue by remember { mutableFloatStateOf(0f) }
    var levelNonce by remember { mutableIntStateOf(0) }

    // Survives process death / activity recreation so playback resumes where it left off
    // rather than restarting. (Rotation itself is handled in-process via configChanges.)
    var resumePositionMs by rememberSaveable(item.stableKey) { mutableStateOf(0L) }
    var resumePlayWhenReady by rememberSaveable(item.stableKey) { mutableStateOf(true) }

    // A looping video never reaches STATE_ENDED, so in a slideshow it would play forever and
    // the show would never move on. Looping is a view-mode behaviour only.
    val repeatVideo = loopVideos && !slideshowMode

    // minSdk 30: LifecycleStartEffect (onStart/onStop) is the recommended player gate.
    // The player comes from PlayerWarmPool: if this video was a prefetched neighbour it's
    // already prepared (codec up, buffer filled) and renders its first frame immediately.
    LifecycleStartEffect(item.uri, item.stableKey, slideshowMode) {
        val exo = PlayerWarmPool.acquire(context, item.uri).apply {
            val resume = if (resumePositionMs > 0L) resumePositionMs
                else VideoPositionMemory.recall(item.stableKey)
            if (resume > 0L) seekTo(resume)
            playWhenReady = resumePlayWhenReady
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && exo.repeatMode == Player.REPEAT_MODE_OFF) {
                    onVideoEnded()
                }
            }
        }
        exo.addListener(listener)
        player = exo
        onStopOrDispose {
            resumePositionMs = exo.currentPosition.coerceAtLeast(0L)
            resumePlayWhenReady = exo.playWhenReady
            // Coming back to this video later in the session resumes where it stopped.
            VideoPositionMemory.store(item.stableKey, exo.currentPosition, exo.duration)
            exo.removeListener(listener)
            exo.release()
            if (player === exo) player = null
        }
    }

    LaunchedEffect(muted, player) {
        player?.volume = if (muted) 0f else 1f
    }

    // Loop and speed react live, without tearing the player down.
    LaunchedEffect(repeatVideo, player) {
        player?.repeatMode = if (repeatVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(playbackSpeed, player) {
        if (!holdSpeed) player?.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(seekFlashNonce) {
        if (seekFlash != 0) {
            delay(600)
            seekFlash = 0
        }
    }
    LaunchedEffect(levelNonce) {
        if (levelKind != 0) {
            delay(900)
            levelKind = 0
        }
    }

    LaunchedEffect(player) {
        val exo = player ?: return@LaunchedEffect
        while (isActive) {
            if (!scrubbing) {
                positionMs = exo.currentPosition.coerceAtLeast(0L)
                durationMs = exo.duration.coerceAtLeast(0L)
                playing = exo.isPlaying
            }
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.stableKey, hapticsEnabled) {
                // YouTube-style taps: double-tap edges = ±10 s, double-tap centre = play/pause,
                // hold anywhere = 2× speed while held, single tap = chrome.
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = { offset ->
                        val exo = player ?: return@detectTapGestures
                        val w = size.width.toFloat()
                        when {
                            offset.x < w * 0.35f -> {
                                exo.seekTo((exo.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                                seekFlash = -1
                                seekFlashNonce++
                                onSeekInteracted()
                            }
                            offset.x > w * 0.65f -> {
                                val limit = exo.duration.coerceAtLeast(0L)
                                exo.seekTo((exo.currentPosition + SEEK_STEP_MS).coerceAtMost(limit))
                                seekFlash = 1
                                seekFlashNonce++
                                onSeekInteracted()
                            }
                            else -> {
                                if (exo.isPlaying) exo.pause() else exo.play()
                                onTogglePlayLocal()
                            }
                        }
                    },
                    onLongPress = {
                        val exo = player ?: return@detectTapGestures
                        holdSpeed = true
                        exo.setPlaybackSpeed(HOLD_SPEED)
                        GalleryHaptics.tick(view, hapticsEnabled)
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (holdSpeed) {
                            holdSpeed = false
                            player?.setPlaybackSpeed(playbackSpeed)
                        }
                    },
                )
            }
            .pointerInput(item.stableKey, disableSwipeDelete) {
                // One-finger drags are sorted by their first movement, as on photos (see
                // ZoomableImageSurface): sideways belongs to the pager, up/down stays here. An
                // up/down swipe quicker than FLICK_MAX_MS shows the next/previous item (or
                // deletes, on an upward flick while swipe-to-delete is on); a slower drag is
                // brightness (left half) or media volume (right half).
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val decideAfter = viewConfiguration.touchSlop * DIRECTION_DECIDE_FRACTION
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalPan = Offset.Zero
                    var vertical: Boolean? = null
                    val velocity = VelocityTracker()
                    var adjusting = 0 // 0 none, 1 brightness, 2 volume
                    var lastUptime = down.uptimeMillis
                    val startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val startBrightness = windowBrightness(activity)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size == 1) {
                            val change = pressed.first()
                            lastUptime = change.uptimeMillis
                            totalPan += change.positionChange()
                            velocity.addPosition(change.uptimeMillis, change.position)
                            if (vertical == null && totalPan.getDistance() > decideAfter) {
                                vertical = abs(totalPan.y) > abs(totalPan.x)
                            }
                            if (vertical != true) continue
                            // Kept from the pager, which would otherwise start a sideways drag.
                            change.consume()
                            val slowDrag = change.uptimeMillis - down.uptimeMillis > FLICK_MAX_MS
                            if (adjusting == 0 && slowDrag && abs(totalPan.y) > 60f) {
                                adjusting = if (down.position.x < size.width / 2f) 1 else 2
                            }
                            if (adjusting != 0) {
                                val frac = -totalPan.y / (size.height * 0.8f)
                                if (adjusting == 2) {
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val v = (startVolume + frac * max).roundToInt().coerceIn(0, max)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                    levelKind = 2
                                    levelValue = if (max > 0) v.toFloat() / max else 0f
                                    levelNonce++
                                } else {
                                    val b = (startBrightness + frac).coerceIn(0.05f, 1f)
                                    setWindowBrightness(activity, b)
                                    levelKind = 1
                                    levelValue = b
                                    levelNonce++
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (adjusting == 0 && vertical == true) {
                        val quickFlick = lastUptime - down.uptimeMillis <= FLICK_MAX_MS
                        val dy = totalPan.y
                        if (quickFlick && isNavigationSwipe(dy, velocity.calculateVelocity().y)) {
                            when {
                                // Flick-to-delete keeps priority while it's switched on.
                                dy < 0 && !disableSwipeDelete -> onSwipeUpDelete()
                                dy < 0 -> onSwipeVertical(1) // up = next media
                                else -> onSwipeVertical(-1) // down = previous media
                            }
                        }
                    }
                }
            },
    ) {
        if (item.mediaType == MediaType.AUDIO) {
            // Audio has no video surface to draw, so the viewer was just black.
            AudioArtwork(item = item, modifier = Modifier.fillMaxSize())
        } else {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Gesture feedback overlays — quiet pills, no layout shift.
        if (holdSpeed) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
            ) {
                Text(
                    "2× speed",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        if (seekFlash != 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(if (seekFlash < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 28.dp),
            ) {
                Text(
                    if (seekFlash < 0) "−10 s" else "+10 s",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        if (levelKind != 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(if (levelKind == 1) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 20.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(10.dp),
                ) {
                    Icon(
                        if (levelKind == 1) Icons.Default.LightMode else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(4.dp)
                            .height(96.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight(levelValue.coerceIn(0.02f, 1f))
                                .background(Color.White, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }

        // M3 tonal media control bar — slides down and fades in step with the tab bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(CHROME_ANIM_IN_MS)) +
                slideInVertically(tween(CHROME_ANIM_IN_MS)) { it },
            exit = fadeOut(tween(CHROME_ANIM_OUT_MS)) +
                slideOutVertically(tween(CHROME_ANIM_OUT_MS)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // Sit above the overlaid tab bar instead of behind it.
                .padding(bottom = controlsBottomPadding),
        ) {
            val scheme = MaterialTheme.colorScheme
            Surface(
                color = scheme.surfaceContainerHighest.copy(alpha = 0.92f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    val progress = if (durationMs > 0) {
                        (if (scrubbing) scrubValue else positionMs.toFloat() / durationMs.toFloat())
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    // Slim scrubber: stock M3 slider track/handle is oversized for a media bar,
                    // so the track is thinned to 4dp and the handle shortened. Touch target is
                    // unchanged — only the visuals shrink.
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = scheme.primary,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = scheme.surfaceVariant,
                    )
                    val sliderInteraction = remember { MutableInteractionSource() }
                    Slider(
                        value = progress,
                        onValueChange = {
                            scrubbing = true
                            scrubValue = it
                            onSeekInteracted()
                        },
                        onValueChangeFinished = {
                            val exo = player
                            if (exo != null && durationMs > 0) {
                                exo.seekTo((scrubValue * durationMs).toLong())
                            }
                            scrubbing = false
                        },
                        interactionSource = sliderInteraction,
                        colors = sliderColors,
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = sliderInteraction,
                                colors = sliderColors,
                                thumbSize = DpSize(5.dp, 22.dp),
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = sliderColors,
                                modifier = Modifier.height(4.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${formatMs(if (scrubbing) (scrubValue * durationMs).toLong() else positionMs)} / ${formatMs(durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                onTogglePlayLocal()
                                val exo = player ?: return@IconButton
                                if (exo.isPlaying) exo.pause() else exo.play()
                                playing = exo.isPlaying
                            }) {
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = scheme.onSurface,
                                )
                            }
                            IconButton(onClick = {
                                onSeekInteracted()
                                onToggleMute()
                            }) {
                                Icon(
                                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (muted) "Unmute" else "Mute",
                                    tint = scheme.onSurface,
                                )
                            }
                            if (!slideshowMode) {
                                IconButton(onClick = {
                                    onSeekInteracted()
                                    onToggleLoop()
                                }) {
                                    Icon(
                                        Icons.Default.Repeat,
                                        contentDescription = if (repeatVideo) "Loop on" else "Loop off",
                                        tint = if (repeatVideo) scheme.primary else scheme.onSurface,
                                    )
                                }
                            }
                            Box {
                                Text(
                                    formatSpeed(playbackSpeed),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = scheme.onSurface,
                                    modifier = Modifier
                                        .clickable {
                                            onSeekInteracted()
                                            speedMenuOpenLocal = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                )
                                DropdownMenu(
                                    expanded = speedMenuOpenLocal,
                                    onDismissRequest = { speedMenuOpenLocal = false },
                                    shape = MenuShape,
                                ) {
                                    PLAYBACK_SPEEDS.forEach { speed ->
                                        DropdownMenuItem(
                                            text = { Text(formatSpeed(speed)) },
                                            trailingIcon = if (speed == playbackSpeed) {
                                                { Icon(Icons.Default.Speed, null, modifier = Modifier.size(16.dp)) }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                onSpeedChange(speed)
                                                speedMenuOpenLocal = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onToggleChrome) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "Hide controls",
                                    tint = scheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cover art for audio items. Reads the embedded picture with [MediaMetadataRetriever] (Coil has
 * no audio decoder) and falls back to a tinted note card when a track has no artwork.
 */
@Composable
private fun AudioArtwork(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var art by remember(item.stableKey) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(item.stableKey) {
        art = withContext(Dispatchers.IO) { loadEmbeddedArtwork(context, item.uri) }
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    scheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    Color.Black,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val cover = art
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = scheme.surfaceContainerHighest,
                tonalElevation = 6.dp,
                modifier = Modifier.size(240.dp),
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = item.displayName,
                            tint = scheme.primary,
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }
            }
            Text(
                text = item.displayName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

private fun loadEmbeddedArtwork(context: android.content.Context, uri: android.net.Uri): ImageBitmap? =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_ARTWORK_PX)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }
    }.getOrNull()

private const val MAX_ARTWORK_PX = 1_024

private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
    var sample = 1
    var w = width
    var h = height
    while (w / 2 >= target && h / 2 >= target) {
        w /= 2
        h /= 2
        sample *= 2
    }
    return sample
}

private const val SEEK_STEP_MS = 10_000L
private const val HOLD_SPEED = 2f

/**
 * A one-finger drag's direction is settled once it has moved this share of the touch slop: early
 * enough that an up/down swipe is claimed before the pager would start a sideways drag.
 */
private const val DIRECTION_DECIDE_FRACTION = 0.6f

/** On a video, an up/down swipe quicker than this changes item; a slower drag adjusts brightness or volume. */
private const val FLICK_MAX_MS = 300L

/** An up/down swipe changes item after this distance, or a shorter one moving this fast. */
private val NAV_SWIPE_DISTANCE = 56.dp
private val NAV_FLING_DISTANCE = 24.dp
private val NAV_FLING_SPEED_PER_SECOND = 800.dp

private fun Density.isNavigationSwipe(dy: Float, speedY: Float): Boolean {
    val distance = abs(dy)
    return distance >= NAV_SWIPE_DISTANCE.toPx() ||
        (distance >= NAV_FLING_DISTANCE.toPx() && abs(speedY) >= NAV_FLING_SPEED_PER_SECOND.toPx())
}
private val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"

private fun windowBrightness(activity: Activity?): Float {
    val current = activity?.window?.attributes?.screenBrightness ?: return 0.6f
    // BRIGHTNESS_OVERRIDE_NONE is -1 (system-controlled): start from a sensible midpoint.
    return if (current in 0f..1f) current else 0.6f
}

private fun setWindowBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    window.attributes = window.attributes.apply { screenBrightness = value }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(m, s)
}

private fun speedLabel(speedIndex: Int, customMs: Long): String = when {
    speedIndex == SlideshowSpeeds.OFF_INDEX -> "Off"
    speedIndex == SlideshowSpeeds.CUSTOM_INDEX -> "${customMs / 1000}s"
    else -> SlideshowSpeeds.speeds.getOrNull(speedIndex)?.label ?: "5s"
}

private fun formatDate(takenMs: Long, addedMs: Long): String {
    val ms = if (takenMs > 0) takenMs else addedMs
    if (ms <= 0) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))
}
