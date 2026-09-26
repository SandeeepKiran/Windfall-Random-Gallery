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
        // Older / low-RAM devices (like an Android 11 phone with a small heap) get a smaller
        // memory cache: the OS thumbnail cache (disk) does the heavy lifting there instead.
        val activityManager = getSystemService(ActivityManager::class.java)
        val lowRam = activityManager?.isLowRamDevice == true ||
            (activityManager?.memoryClass ?: 256) <= 192
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
                // Thumbnails are the whole product, so this app spends more of its heap on them
                // than Coil's default. At a ~440KB decoded thumbnail this holds a few hundred,
                // which is what keeps swipe-back instant instead of re-decoding.
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = if (lowRam) 0.25 else 0.45)
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
}
