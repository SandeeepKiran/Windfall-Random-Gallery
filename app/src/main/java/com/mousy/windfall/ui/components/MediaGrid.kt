package com.mousy.windfall.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.graphics.painter.ColorPainter
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import coil3.video.preferVideoFrameEmbeddedThumbnailKey
import coil3.video.videoFrameMillis
import com.mousy.windfall.data.model.GridMode
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.data.model.MediaType
import com.mousy.windfall.ui.theme.FavouriteHeart
import com.mousy.windfall.util.GalleryHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val LONG_PRESS_MS = 2_500L
private const val DOUBLE_TAP_MS = 280L
private const val MIN_COLUMNS = 1
private const val MAX_COLUMNS = 6
/** Fraction of a page the finger must travel for a swipe to commit without a fling. */
private const val COMMIT_FRACTION = 0.28f
/** px/s — a quick flick commits the page even on a short drag. */
private const val FLING_COMMIT_VELOCITY = 1_100f
/** Roughly a screen and a half of tiles decoded below the fold while scrolling. */
private const val SCROLL_PREFETCH_AHEAD = 30

/**
 * Page settle: critically damped so the strip glides to rest without bounce or cut.
 * Stiffness sits between MediumLow (too floaty — input is guarded during a commit settle,
 * so a long settle reads as dead touch) and Medium (too abrupt for a full-page travel).
 */
private val PageSettleSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 800f,
)

/** Flings above this are clamped so a hard flick can't overshoot past the incoming page. */
private const val MAX_SETTLE_VELOCITY = 4_000f

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    gridMode: GridMode,
    favouriteKeys: Set<String>,
    selectedKeys: Set<String>,
    onItemClick: (MediaItem) -> Unit,
    onItemDoubleTap: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    onSwipeShuffle: (Int) -> Unit,
    onSetColumns: (Int) -> Unit,
    thumbnailPadding: Boolean = true,
    hapticsEnabled: Boolean = true,
    onReachedEnd: (Int) -> Unit = {},
    /** First item of the visible page — a window into [items] (swipe mode only). */
    pageStart: Int = 0,
    /** Reports (page capacity, thumb bucket px) so the ViewModel can stride and warm caches. */
    onPageGeometryChanged: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Landscape: denser columns so more than ~one row is visible.
    val cols = remember(columns, landscape) { ThumbSpec.effectiveColumns(columns, landscape) }
    // Read live inside the long-lived pinch handler without restarting it.
    val currentColumns by rememberUpdatedState(columns)
    val setColumns by rememberUpdatedState(onSetColumns)
    val density = LocalDensity.current
    val thumbPx = remember(columns, landscape, density) {
        ThumbSpec.gridBucket(columns, landscape, density)
    }
    val gridState = rememberLazyGridState()

    // Pinch with 2+ fingers only — does not consume single-finger scroll/swipe.
    // Keyed on Unit so a column change mid-pinch doesn't restart the gesture; the target is
    // derived from the spread since the fingers went down, which is what lets one continuous
    // pinch cross several column counts.
    val pinchModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var startSpread = 0f
            var baseColumns = currentColumns
            var lastEmitted = currentColumns
            do {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val spread = pressed
                        .map { it.position }
                        .let { pts ->
                            val c = Offset(
                                pts.map { it.x }.average().toFloat(),
                                pts.map { it.y }.average().toFloat(),
                            )
                            pts.map { (it - c).getDistance() }.average().toFloat()
                        }
                    if (startSpread <= 0f) {
                        startSpread = spread
                        baseColumns = currentColumns
                        lastEmitted = currentColumns
                    } else if (spread > 0f) {
                        // Fingers apart → bigger tiles → fewer columns.
                        val scale = spread / startSpread
                        val target = (baseColumns / scale)
                            .roundToInt()
                            .coerceIn(MIN_COLUMNS, MAX_COLUMNS)
                        if (target != lastEmitted) {
                            lastEmitted = target
                            setColumns(target)
                        }
                        if (abs(scale - 1f) > 0.02f) {
                            pressed.fastForEach {
                                if (it.positionChanged()) it.consume()
                            }
                        }
                    }
                } else {
                    startSpread = 0f
                }
            } while (event.changes.fastAny { it.pressed })
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Pre-toggle (padded-on) values: 4dp gap, light content pad — no extra per-cell inset.
        val hSpacing = if (thumbnailPadding) 4.dp else 0.dp
        val vSpacing = if (thumbnailPadding) 4.dp else 0.dp
        val hPad = if (thumbnailPadding) 12.dp else 0.dp
        val cellWidth = (maxWidth - hPad - hSpacing * (cols - 1).coerceAtLeast(0)) / cols
        val cellHeight = cellWidth // square cells
        val availableHeight = maxHeight
        val boundedHeight = availableHeight.value.isFinite() && availableHeight > 0.dp

        // Items per page from real geometry; reported up so swipes stride by exactly one page
        // and cache-warming uses the same thumbnail bucket the grid will actually request.
        val pageCapacity = if (boundedHeight) {
            val rowPitch = cellHeight + vSpacing
            val fullRows = with(density) {
                val usable = (availableHeight - if (thumbnailPadding) 8.dp else 0.dp).toPx().coerceAtLeast(0f)
                val pitch = rowPitch.toPx().coerceAtLeast(1f)
                (usable / pitch).toInt().coerceAtLeast(1)
            }
            (fullRows * cols).coerceAtLeast(cols)
        } else {
            cols
        }
        LaunchedEffect(pageCapacity, thumbPx) { onPageGeometryChanged(pageCapacity, thumbPx) }

        val context = LocalContext.current

        if (gridMode == GridMode.SWIPE && boundedHeight) {
            SwipePagedGrid(
                items = items,
                pageStart = pageStart,
                pageCapacity = pageCapacity,
                cols = cols,
                thumbPx = thumbPx,
                hSpacing = hSpacing,
                vSpacing = vSpacing,
                thumbnailPadding = thumbnailPadding,
                hapticsEnabled = hapticsEnabled,
                favouriteKeys = favouriteKeys,
                selectedKeys = selectedKeys,
                onItemClick = onItemClick,
                onItemDoubleTap = onItemDoubleTap,
                onItemLongPress = onItemLongPress,
                onSwipeShuffle = onSwipeShuffle,
                modifier = Modifier
                    .fillMaxSize()
                    .then(pinchModifier),
            )
        } else {
            LaunchedEffect(gridState, items, thumbPx) {
                val loader = SingletonImageLoader.get(context)
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .distinctUntilChanged()
                    .collect { lastVisible ->
                        onReachedEnd(lastVisible)
                        // Decode the rows just below the fold so scrolling doesn't reveal
                        // empty tiles that only start loading once they're on screen.
                        val from = lastVisible + 1
                        val to = (lastVisible + SCROLL_PREFETCH_AHEAD)
                            .coerceAtMost(items.lastIndex)
                        for (i in from..to) {
                            val next = items[i]
                            if (next.mediaType == MediaType.AUDIO) continue
                            loader.enqueue(gridThumbRequest(context, next, thumbPx))
                        }
                    }
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(cols),
                modifier = Modifier
                    .fillMaxSize()
                    .then(pinchModifier),
                contentPadding = if (thumbnailPadding) {
                    PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                } else {
                    PaddingValues(0.dp)
                },
                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                verticalArrangement = Arrangement.spacedBy(vSpacing),
            ) {
                items(items, key = { it.stableKey }) { item ->
                    MediaGridCell(
                        item = item,
                        thumbPx = thumbPx,
                        isFavourite = favouriteKeys.contains(item.stableKey),
                        isSelected = selectedKeys.contains(item.stableKey),
                        rounded = thumbnailPadding,
                        onClick = { onItemClick(item) },
                        onDoubleTap = { onItemDoubleTap(item) },
                        onLongPress = { onItemLongPress(item) },
                    )
                }
            }
        }
    }
}

/**
 * SWIPE mode as a real four-direction pager. The previous and next windows of the shuffled
 * list are composed and parked physically adjacent to the visible page; the whole strip tracks
 * the finger 1:1 and spring-settles with the finger's velocity, so the incoming page is
 * attached to the outgoing one — no gap, no fade-through-background.
 *
 * The data handoff is the other half of the trick: the strip keeps rendering around its own
 * [stripBase] while the settle runs, and only adopts the ViewModel's new cursor after motion
 * stops. Because the adopted current page renders exactly the pixels the neighbour just
 * showed (same items, same cached thumbs), the swap is invisible — recomposition happens on a
 * frame where nothing moves, instead of mid-animation like before.
 */
@Composable
private fun SwipePagedGrid(
    items: List<MediaItem>,
    pageStart: Int,
    pageCapacity: Int,
    cols: Int,
    thumbPx: Int,
    hSpacing: Dp,
    vSpacing: Dp,
    thumbnailPadding: Boolean,
    hapticsEnabled: Boolean,
    favouriteKeys: Set<String>,
    selectedKeys: Set<String>,
    onItemClick: (MediaItem) -> Unit,
    onItemDoubleTap: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    onSwipeShuffle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var stripBase by remember { mutableIntStateOf(pageStart) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragAxis by remember { mutableIntStateOf(0) } // 0 none, 1 horizontal, 2 vertical
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var commitInFlight by remember { mutableStateOf(false) }
    val latestPageStart by rememberUpdatedState(pageStart)
    val latestShuffle by rememberUpdatedState(onSwipeShuffle)

    // External cursor changes while idle (shuffle button, tab restore) move the strip directly.
    LaunchedEffect(pageStart) {
        if (!commitInFlight && settleJob?.isActive != true && pageStart != stripBase) {
            stripBase = pageStart
            offsetX = 0f
            offsetY = 0f
            dragAxis = 0
        }
    }

    val capacity = pageCapacity.coerceAtLeast(1)
    val base = stripBase.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val hasPrev = base > 0
    val prevStart = (base - capacity).coerceAtLeast(0)
    val nextStart = base + capacity
    val currentItems = remember(items, base, capacity) { windowOf(items, base, capacity) }
    val nextItems = remember(items, nextStart, capacity) {
        // Past the end the ViewModel wraps complete lists back to the first window.
        if (nextStart < items.size) windowOf(items, nextStart, capacity) else windowOf(items, 0, capacity)
    }
    // With no previous page, a back swipe wraps to the TAIL window of the list (the
    // ViewModel commits the same cursor), so that window waits on the back side and the
    // post-settle handoff renders identical pixels. Lists that fit on one page keep the next
    // window there (the ViewModel won't move the cursor for those).
    val backWrapStart = if (items.size > capacity) {
        (items.size - capacity).coerceAtLeast(0)
    } else {
        nextStart
    }
    val prevItems = remember(items, prevStart, capacity, hasPrev, backWrapStart, nextItems) {
        when {
            hasPrev -> windowOf(items, prevStart, capacity)
            items.size > capacity -> windowOf(items, backWrapStart, capacity)
            else -> nextItems
        }
    }
    // Sign of the drag, recomposed only when it flips: with no previous page the single "next"
    // window subtree parks on whichever side the finger is pulling from.
    val backPulling by remember { derivedStateOf { offsetX > 0.5f || offsetY > 0.5f } }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(hapticsEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The brief window between a committed settle and the cursor handoff is not
                    // interruptible — grabbing it would desync the strip from the ViewModel.
                    if (commitInFlight) return@awaitEachGesture
                    settleJob?.cancel() // a spring-back can be caught mid-flight
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    var totalX = 0f
                    var totalY = 0f
                    var dragging = dragAxis != 0 // caught mid-flight → keep the axis
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) break // hand over to pinch
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalX += dx
                            totalY += dy
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!dragging) {
                                val pastX = abs(totalX) > viewConfiguration.touchSlop
                                val pastY = abs(totalY) > viewConfiguration.touchSlop
                                when {
                                    pastX && abs(totalX) > abs(totalY) * 1.15f -> {
                                        dragging = true
                                        dragAxis = 1
                                    }
                                    pastY && abs(totalY) > abs(totalX) * 1.15f -> {
                                        dragging = true
                                        dragAxis = 2
                                    }
                                }
                            }
                            if (dragging) {
                                // 1:1 with the finger — both directions always have a page
                                // waiting (backwards deals forward when no history exists).
                                if (dragAxis == 1) {
                                    offsetX = (offsetX + dx).coerceIn(-width, width)
                                } else {
                                    offsetY = (offsetY + dy).coerceIn(-height, height)
                                }
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.fastAny { it.pressed })

                    if (!dragging) return@awaitEachGesture
                    val axis = dragAxis
                    val extent = if (axis == 1) width else height
                    val off = if (axis == 1) offsetX else offsetY
                    val velocity = tracker.calculateVelocity()
                    val vel = (if (axis == 1) velocity.x else velocity.y)
                        .coerceIn(-MAX_SETTLE_VELOCITY, MAX_SETTLE_VELOCITY)
                    val dir = when {
                        vel < -FLING_COMMIT_VELOCITY -> 1
                        vel > FLING_COMMIT_VELOCITY -> -1
                        off < -extent * COMMIT_FRACTION -> 1
                        off > extent * COMMIT_FRACTION -> -1
                        else -> 0
                    }
                    val commit = dir != 0
                    settleJob = scope.launch {
                        if (commit) {
                            commitInFlight = true
                            // Dispatch now so the page after next loads behind the settle.
                            latestShuffle(dir)
                            GalleryHaptics.tick(view, hapticsEnabled)
                            animate(off, -dir * extent, vel, PageSettleSpec) { value, _ ->
                                if (axis == 1) offsetX = value else offsetY = value
                            }
                            // Adopt whatever cursor the ViewModel chose (handles wrap-around
                            // and end-of-bag reshuffles) and recentre in the same frame —
                            // atomic, so old offset and new data never draw together.
                            stripBase = latestPageStart
                            offsetX = 0f
                            offsetY = 0f
                            dragAxis = 0
                            commitInFlight = false
                        } else {
                            animate(off, 0f, vel, PageSettleSpec) { value, _ ->
                                if (axis == 1) offsetX = value else offsetY = value
                            }
                            offsetX = 0f
                            offsetY = 0f
                            dragAxis = 0
                        }
                    }
                }
            },
    ) {
        // Pages are keyed by WINDOW START, not by role. After a commit the old "next" subtree
        // (same key) is moved into the current slot instead of being rebuilt, so its images
        // never recompose — this is what removes the one-frame blink on the handoff. With no
        // previous page there is only ONE next-window subtree; it swaps sides with the pull
        // direction (same key either way, so it moves rather than reloads).
        val slots = if (hasPrev) {
            listOf(
                Triple(-1, prevStart, prevItems),
                Triple(0, base, currentItems),
                Triple(1, nextStart, nextItems),
            )
        } else if (backPulling) {
            listOf(
                Triple(-1, backWrapStart, prevItems),
                Triple(0, base, currentItems),
            )
        } else {
            listOf(
                Triple(0, base, currentItems),
                Triple(1, nextStart, nextItems),
            )
        }
        slots.forEach { (role, windowStart, pageItems) ->
            key(windowStart) {
                SwipeGridPage(
                    items = pageItems,
                    cols = cols,
                    thumbPx = thumbPx,
                    hSpacing = hSpacing,
                    vSpacing = vSpacing,
                    thumbnailPadding = thumbnailPadding,
                    favouriteKeys = favouriteKeys,
                    selectedKeys = selectedKeys,
                    onItemClick = onItemClick,
                    onItemDoubleTap = onItemDoubleTap,
                    onItemLongPress = onItemLongPress,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Offsets are read here, inside the layer block, so finger motion
                            // only re-draws layers — no recomposition or relayout per frame.
                            if (dragAxis == 2) {
                                translationX = 0f
                                translationY = offsetY + role * size.height
                            } else {
                                translationX = offsetX + role * size.width
                                translationY = 0f
                            }
                        },
                )
            }
        }
    }
}

private fun windowOf(items: List<MediaItem>, start: Int, capacity: Int): List<MediaItem> {
    if (items.isEmpty()) return emptyList()
    val from = start.coerceIn(0, items.size)
    val to = (from + capacity).coerceAtMost(items.size)
    return items.subList(from, to)
}

/** One fixed page of tiles. Non-lazy: a page is always fully visible, laziness only adds cost. */
@Composable
private fun SwipeGridPage(
    items: List<MediaItem>,
    cols: Int,
    thumbPx: Int,
    hSpacing: Dp,
    vSpacing: Dp,
    thumbnailPadding: Boolean,
    favouriteKeys: Set<String>,
    selectedKeys: Set<String>,
    onItemClick: (MediaItem) -> Unit,
    onItemDoubleTap: (MediaItem) -> Unit,
    onItemLongPress: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (thumbnailPadding) 6.dp else 0.dp,
                    vertical = if (thumbnailPadding) 4.dp else 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(vSpacing),
        ) {
            items.chunked(cols).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(hSpacing),
                ) {
                    row.forEach { item ->
                        key(item.stableKey) {
                            Box(modifier = Modifier.weight(1f)) {
                                MediaGridCell(
                                    item = item,
                                    thumbPx = thumbPx,
                                    isFavourite = favouriteKeys.contains(item.stableKey),
                                    isSelected = selectedKeys.contains(item.stableKey),
                                    rounded = thumbnailPadding,
                                    onClick = { onItemClick(item) },
                                    onDoubleTap = { onItemDoubleTap(item) },
                                    onLongPress = { onItemLongPress(item) },
                                )
                            }
                        }
                    }
                    repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MediaGridCell(
    item: MediaItem,
    thumbPx: Int,
    isFavourite: Boolean,
    isSelected: Boolean,
    rounded: Boolean = true,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val request = remember(item.uri, item.stableKey, thumbPx, item.mediaType) {
        gridThumbRequest(context, item, thumbPx)
    }
    // Pre-toggle look: 6dp corners, no extra per-cell padding (gap comes from grid spacing).
    val shape = if (rounded) RoundedCornerShape(6.dp) else RoundedCornerShape(0.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            )
            .pointerInput(item.stableKey, touchSlop) {
                val slop = touchSlop * 2.5f
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    // Hold ~2.5s → multi-select; movement beyond slop cancels tap
                    val up = withTimeoutOrNull(LONG_PRESS_MS) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            val dist = (change.position - down.position).getDistance()
                            if (dist > slop) {
                                moved = true
                                return@withTimeoutOrNull null
                            }
                            if (change.changedToUp()) return@withTimeoutOrNull change
                            if (!change.pressed) return@withTimeoutOrNull change
                        }
                        @Suppress("UNREACHABLE_CODE")
                        null
                    }
                    if (up == null && !moved) {
                        onLongPress()
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    if (moved) {
                        // Scroll/swipe in progress — never open the photo
                        return@awaitEachGesture
                    }
                    val secondDown = withTimeoutOrNull(DOUBLE_TAP_MS) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (secondDown != null) {
                        var secondMoved = false
                        withTimeoutOrNull(LONG_PRESS_MS) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == secondDown.id } ?: continue
                                if ((change.position - secondDown.position).getDistance() > slop) {
                                    secondMoved = true
                                    return@withTimeoutOrNull null
                                }
                                if (change.changedToUp() || !change.pressed) return@withTimeoutOrNull change
                            }
                            @Suppress("UNREACHABLE_CODE")
                            null
                        }
                        if (!secondMoved) onDoubleTap()
                    } else {
                        onClick()
                    }
                }
            },
    ) {
        if (item.mediaType == MediaType.AUDIO) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AudioFile,
                    contentDescription = item.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        } else {
            AsyncImage(
                model = request,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(22.dp),
            )
        }
        if (isFavourite) {
            // Black heart underneath reads as a border, so the pink one stays visible on any
            // thumbnail — and stays distinguishable even in system greyscale mode.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(23.dp),
                )
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = FavouriteHeart,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        when (item.mediaType) {
            MediaType.VIDEO -> BadgeIcon(Icons.Default.PlayCircle, Modifier.align(Alignment.BottomEnd))
            MediaType.GIF -> BadgeIcon(Icons.Default.Gif, Modifier.align(Alignment.BottomEnd))
            MediaType.AUDIO -> BadgeIcon(Icons.Default.AudioFile, Modifier.align(Alignment.BottomEnd))
            else -> Unit
        }
    }
}

/**
 * The exact request a grid cell will make. Shared so neighbouring random sets can be warmed
 * with matching cache keys — prefetching under a different key would decode twice and still
 * leave the grid blank on swipe.
 */
fun gridThumbRequest(
    context: android.content.Context,
    item: MediaItem,
    thumbPx: Int,
): ImageRequest {
    val cacheKey = ThumbSpec.thumbKey(item.stableKey, thumbPx)
    return ImageRequest.Builder(context)
        .data(item.uri)
        .size(Size(thumbPx, thumbPx))
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .apply {
            if (item.mediaType == MediaType.VIDEO) {
                videoFrameMillis(0)
                preferVideoFrameEmbeddedThumbnailKey(true)
            }
        }
        .build()
}

@Composable
private fun BadgeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(5.dp),
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .padding(2.dp)
                .size(19.dp),
        )
    }
}
