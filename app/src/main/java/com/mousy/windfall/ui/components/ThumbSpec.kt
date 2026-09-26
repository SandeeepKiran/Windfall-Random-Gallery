package com.mousy.windfall.ui.components

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

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

    /** Landscape packs in more columns, so cells (and their thumbnails) get smaller. */
    fun effectiveColumns(columns: Int, landscape: Boolean): Int {
        val base = columns.coerceIn(1, 6)
        return if (landscape) (base + 2).coerceIn(3, 8) else base
    }

    fun bucketFor(px: Int): Int = buckets.firstOrNull { px <= it } ?: buckets.last()

    fun gridBucket(columns: Int, landscape: Boolean, density: Density): Int {
        val cols = effectiveColumns(columns, landscape)
        val px = with(density) { ((360.dp / cols).coerceIn(96.dp, 400.dp)).roundToPx() }
        return bucketFor(px)
    }

    fun thumbKey(stableKey: String, bucketPx: Int): String = "${stableKey}_t$bucketPx"

    fun fullKey(stableKey: String, widthPx: Int, heightPx: Int): String =
        "${stableKey}_v${widthPx}x$heightPx"
}
