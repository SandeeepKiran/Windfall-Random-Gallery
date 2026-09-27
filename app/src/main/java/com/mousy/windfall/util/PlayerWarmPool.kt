package com.mousy.windfall.util

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters

/**
 * Keeps up to [MAX_WARM] fully PREPARED ExoPlayers for the videos the user is about to swipe
 * to — the short-form-feed (TikTok) trick. "Prepared" means codec initialised and the first
 * seconds buffered, which is exactly the visible delay when a video page opens; doing that
 * work while the previous item is still on screen makes the next video start instantly.
 *
 * Main-thread only (an ExoPlayer requirement). Players handed out via [acquire] belong to the
 * caller, which must release them; players still in the pool die via eviction / [releaseAll].
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
object PlayerWarmPool {
    private const val MAX_WARM = 2

    /** Fast-start buffer tuning for local files: tiny buffers, time over size. */
    private const val MIN_BUFFER_MS = 2_000
    private const val MAX_BUFFER_MS = 8_000
    private const val PLAYBACK_BUFFER_MS = 500
    private const val REBUFFER_MS = 1_000

    private val warm = LinkedHashMap<String, ExoPlayer>()

    /**
     * Every player in the app comes from here so they all share the same tuning: fast-start
     * buffers + frame-EXACT seeking (the default snaps to keyframes, which is why seek bars in
     * stock players feel imprecise).
     *
     * [decoderFallback]: when the phone's video decoder can't start, try the next one (often the
     * slower software decoder) instead of failing. Multi-Video needs this: with several videos
     * playing at once, the hardware decoders can all be in use.
     */
    fun newPlayer(context: Context, decoderFallback: Boolean = false): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, PLAYBACK_BUFFER_MS, REBUFFER_MS)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        // Application context: warm players live in this process-wide pool and must never
        // hold on to an Activity.
        val appContext = context.applicationContext
        val renderers = DefaultRenderersFactory(appContext).setEnableDecoderFallback(decoderFallback)
        return ExoPlayer.Builder(appContext, renderers)
            .setLoadControl(loadControl)
            .build()
            .apply { setSeekParameters(SeekParameters.EXACT) }
    }

    /** Prepare [uri] in the background if it isn't already warm. Cheap to call repeatedly. */
    fun prewarm(context: Context, uri: Uri) {
        val key = uri.toString()
        if (warm.containsKey(key)) return
        warm[key] = newPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(uri))
            playWhenReady = false
            prepare()
        }
        while (warm.size > MAX_WARM) {
            val eldest = warm.keys.first()
            warm.remove(eldest)?.release()
        }
    }

    /** Hands over a prepared player when one exists; otherwise builds and prepares one cold. */
    fun acquire(context: Context, uri: Uri): ExoPlayer {
        warm.remove(uri.toString())?.let { return it }
        return newPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    /** Drop everything still warming — called when the fullscreen viewer goes away. */
    fun releaseAll() {
        warm.values.forEach { it.release() }
        warm.clear()
    }
}

/**
 * Session-only "resume where I left off" per video. In-memory on purpose: app restarts are
 * supposed to feel brand new (fresh shuffle), so playback positions aren't persisted either.
 */
object VideoPositionMemory {
    private const val NEAR_END_MS = 1_500L
    private val positions = LruCache<String, Long>(64)

    fun store(key: String, positionMs: Long, durationMs: Long) {
        when {
            positionMs <= 0L -> positions.remove(key)
            // Finished (or as good as): next open should restart from the top.
            durationMs > 0L && positionMs >= durationMs - NEAR_END_MS -> positions.remove(key)
            else -> positions.put(key, positionMs)
        }
    }

    fun recall(key: String): Long = positions.get(key) ?: 0L
}
