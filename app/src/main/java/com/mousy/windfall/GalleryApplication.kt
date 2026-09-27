package com.mousy.windfall

import android.app.ActivityManager
import android.app.Application
import android.content.pm.ApplicationInfo
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.util.DebugLogger
import coil3.video.VideoFrameDecoder
import com.mousy.windfall.data.media.MediaStoreThumbFetcher
import com.mousy.windfall.util.AppVisibility
import okio.Path.Companion.toOkioPath

/**
 * Configures Coil memory + disk caches, video frame decoding, and default crossfade.
 */
class GalleryApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // Lets idle background work (thumbnail farmer) stand down when the app isn't visible.
        AppVisibility.register(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val appContext = this
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return ImageLoader.Builder(context)
            // Debug builds log every hit/miss, which is how thumbnail-cache regressions get
            // diagnosed instead of guessed at.
            .apply { if (debuggable) logger(DebugLogger()) }
            .crossfade(true)
            .components {
                // System thumbnail cache first (milliseconds, persists across restarts); any
                // request it can't serve falls through to Coil's normal pipeline below.
                add(MediaStoreThumbFetcher.Factory())
                add(VideoFrameDecoder.Factory())
                // GIFs animate (minSdk 30 >= 28, so the platform ImageDecoder handles them).
                add(AnimatedImageDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(memoryCacheBytes())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("coil_image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024L * 1024L)
                    .build()
            }
            .build()
    }

    /**
     * How much decoded-image memory to keep. Thumbnails are the whole product, so this is
     * generous: 3% of the phone's RAM, about 15–20 screens of scroll-mode tiles on a 6–8 GB phone.
     *
     * The old rule took a share of the Java heap limit, but bitmaps don't live on the Java heap,
     * and it also treated any phone with a 192 MB heap limit (common on mid-range phones) as
     * low-RAM. On such a phone the cache held about 60 tiles, so scrolling back a few screens
     * fetched them all again. Coil empties the cache when the app goes to the background, so the
     * bigger budget costs nothing while the app isn't on screen.
     */
    private fun memoryCacheBytes(): Long {
        val activityManager = getSystemService(ActivityManager::class.java)
        val heapBytes = (activityManager?.memoryClass ?: 192) * MB
        val totalRam = ActivityManager.MemoryInfo()
            .also { activityManager?.getMemoryInfo(it) }
            .totalMem
        // Android Go phones and anything under 3 GB stay modest; the OS thumbnail cache (on
        // disk) does the heavy lifting there.
        if (activityManager?.isLowRamDevice == true || totalRam < 3 * GB) {
            return (heapBytes * 0.25).toLong()
        }
        return (totalRam * 0.03).toLong().coerceIn((heapBytes * 0.45).toLong(), 512 * MB)
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val GB = 1024L * MB
    }
}
