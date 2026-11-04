package com.mousy.windfall.ui.components

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared sizing and cache keys for media thumbnails.
 *
 * Grid cells and the fullscreen viewer both need to name the same cached bitmap — the viewer
 * uses the grid's thumbnail as its instant placeholder — so the request size is quantised into
 * a handful of buckets. Without buckets every column count would produce a different cache key
 * and the viewer could never find what the grid had already decoded.
 */
object ThumbSpec {
    private val buckets = intArrayOf(128, 192, 256, 384, 512)

    /** A window this wide on its short side is a tablet, a Chromebook or a desktop window. */
    const val LARGE_SCREEN_MIN_DP = 600

    /** Wide windows: one more column for each step of this many dp above [WIDE_BASE_DP]. */
    private const val WIDE_STEP_DP = 200
    private const val WIDE_BASE_DP = 600
    private const val WIDE_MAX_COLUMNS = 12

    /** Phones: the row width that thumbnails are sized for (as before tablet support). */
    private const val PHONE_ROW_DP = 360

    fun isLargeScreen(smallestWidthDp: Int): Boolean = smallestWidthDp >= LARGE_SCREEN_MIN_DP

    /**
     * Columns on screen for the user's [columns] setting. On a phone, landscape packs in more
     * columns, so cells (and their thumbnails) get smaller. On a large screen, the count grows
     * with the window width: 800 dp gets 1 more, 1280 dp gets 3 more. Phones keep the old rule.
     */
    fun effectiveColumns(columns: Int, landscape: Boolean, widthDp: Int = 0, smallestWidthDp: Int = 0): Int {
        val base = columns.coerceIn(1, 6)
        if (isLargeScreen(smallestWidthDp)) {
            val extra = ((widthDp - WIDE_BASE_DP) / WIDE_STEP_DP).coerceAtLeast(0)
            return (base + extra).coerceIn(1, WIDE_MAX_COLUMNS)
        }
        return if (landscape) (base + 2).coerceIn(3, 8) else base
    }

    fun bucketFor(px: Int): Int = buckets.firstOrNull { px <= it } ?: buckets.last()

    fun gridBucket(
        columns: Int,
        landscape: Boolean,
        density: Density,
        widthDp: Int = 0,
        smallestWidthDp: Int = 0,
    ): Int {
        val cols = effectiveColumns(columns, landscape, widthDp, smallestWidthDp)
        // Large screens have wider cells: size for the real row, or thumbnails look soft.
        val rowDp = if (isLargeScreen(smallestWidthDp)) widthDp else PHONE_ROW_DP
        val px = with(density) { ((rowDp.dp / cols).coerceIn(96.dp, 400.dp)).roundToPx() }
        return bucketFor(px)
    }

    /**
     * Size that exactly covers a [boxWidth]×[boxHeight] tile with the aspect ratio kept: the
     * short side matches the box and the long side overflows (a centre crop hides it).
     *
     * Coil only reuses a cached thumbnail when the governing side matches the request within a
     * pixel. System thumbnails come back at whatever size the OS picked, so they are rescaled to
     * this before caching; otherwise every cached tile looked "wrong size" and was fetched again.
     */
    fun fillSize(srcWidth: Int, srcHeight: Int, boxWidth: Int, boxHeight: Int): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return boxWidth to boxHeight
        val scale = maxOf(boxWidth.toDouble() / srcWidth, boxHeight.toDouble() / srcHeight)
        val width = (srcWidth * scale).roundToInt().coerceAtLeast(boxWidth)
        val height = (srcHeight * scale).roundToInt().coerceAtLeast(boxHeight)
        return width to height
    }

    fun thumbKey(stableKey: String, bucketPx: Int): String = "${stableKey}_t$bucketPx"

    fun fullKey(stableKey: String, widthPx: Int, heightPx: Int): String =
        "${stableKey}_v${widthPx}x$heightPx"
}
