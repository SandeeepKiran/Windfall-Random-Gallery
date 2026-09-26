package com.mousy.windfall.data.model

import android.net.Uri

enum class MediaType {
    PHOTO, VIDEO, GIF, AUDIO, OTHER
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val mediaType: MediaType,
    val folderPath: String,
    val dateTakenMs: Long,
    val dateAddedMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0L,
) {
    /**
     * Computed once per item rather than per read: filtering a 10k library touches this
     * key millions of times, and a `get()` would rebuild the string on every touch.
     */
    val stableKey: String = "${id}_${uri}"

    /** Best-known timestamp, precomputed so sorting doesn't re-derive it per comparison. */
    val recencyMs: Long = when {
        dateAddedMs > 0L -> dateAddedMs
        dateTakenMs > 0L -> dateTakenMs
        else -> 0L
    }

    fun ageDays(nowMs: Long = System.currentTimeMillis()): Int {
        // unknown date → fail closed for day filters
        val ref = recencyMs.takeIf { it > 0L } ?: return Int.MAX_VALUE / 4
        val diffMs = (nowMs - ref).coerceAtLeast(0L)
        val days = diffMs / 86_400_000L
        // Guard against toInt() overflow on pathological timestamps
        return days.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
