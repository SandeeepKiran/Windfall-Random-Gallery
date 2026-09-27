package com.mousy.windfall.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.Uri as CoilUri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import com.mousy.windfall.ui.components.ThumbSpec
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * WHY THIS EXISTS — the single biggest thumbnail speed-up in the app.
 *
 * Android already maintains a persistent, system-wide thumbnail cache for everything in
 * MediaStore: pre-generated previews served by ContentResolver.loadThumbnail() in a few
 * milliseconds. Coil ignores it by default — it opens the ORIGINAL file and decodes a
 * multi-megabyte photo (~30–150 ms), or spins up MediaMetadataRetriever for a video frame
 * (~100–500 ms). And because Coil's disk cache only persists *network* payloads, local media
 * pays that full decode again on every cold start.
 *
 * This fetcher routes small (grid-bucket-sized) requests to the system thumbnail cache first:
 *  - MediaStore items -> ContentResolver.loadThumbnail()
 *  - SAF documents    -> DocumentsContract.getDocumentThumbnail()
 *
 * Returning null (from [Factory.create] or [fetch]) hands the request straight back to Coil's
 * normal pipeline, so any failure silently falls back to the old decode-the-original path.
 *
 * Bonus: warming a thumbnail through this fetcher makes the OS generate and persist it, which
 * is what lets "pre-decode next launch's first page" survive an app restart.
 */
class MediaStoreThumbFetcher(
    private val context: Context,
    private val uri: android.net.Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val w = options.size.width.pxOrElse { 0 }
        val h = options.size.height.pxOrElse { 0 }
        if (w <= 0 || h <= 0) return null
        val resolver = context.contentResolver
        // Ask for twice the tile. loadThumbnail() shrinks by the whole factor that FITS the box,
        // so a request for exactly the tile leaves the short side of any non-square photo below
        // the tile (256 x 256 came back as ~286 x 214). Coil then judged the cached copy too small
        // and fetched it again every time the tile reappeared: the "re-rendering" while scrolling.
        val ask = w * OVERSAMPLE to h * OVERSAMPLE
        val bitmap = try {
            withCancellationSignal { signal ->
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.getDocumentThumbnail(resolver, uri, Point(ask.first, ask.second), signal)
                } else {
                    resolver.loadThumbnail(uri, Size(ask.first, ask.second), signal)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // A tile that scrolled away mustn't fall through to a full decode of the original.
            currentCoroutineContext().ensureActive()
            // Corrupt file, pending item, no system thumb… let Coil decode the original.
            null
        } ?: return null
        return ImageFetchResult(
            image = bitmap.fittedTo(w, h).asImage(),
            isSampled = true, // a downscaled preview, never the full-resolution image
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content") return null
            // Only intercept small requests — the fullscreen viewer must keep decoding the
            // real file at screen resolution. Unbounded sizes fall through too.
            val w = options.size.width.pxOrElse { Int.MAX_VALUE }
            val h = options.size.height.pxOrElse { Int.MAX_VALUE }
            if (w > MAX_THUMB_REQUEST_PX || h > MAX_THUMB_REQUEST_PX) return null
            val context = options.context
            val androidUri = android.net.Uri.parse(data.toString())
            val eligible = androidUri.authority == MediaStore.AUTHORITY ||
                runCatching { DocumentsContract.isDocumentUri(context, androidUri) }.getOrDefault(false)
            if (!eligible) return null
            return MediaStoreThumbFetcher(context, androidUri, options)
        }

        private companion object {
            /** Largest ThumbSpec bucket; anything bigger is a viewer-grade decode. */
            const val MAX_THUMB_REQUEST_PX = 512
        }
    }

    private companion object {
        const val OVERSAMPLE = 2
    }
}

/**
 * Rescales to exactly cover a [width]×[height] tile (see [ThumbSpec.fillSize]) so Coil's cache
 * accepts it on the next lookup — and so the cache holds 2–3x more tiles than the raw system
 * thumbnails, which are larger than a grid tile.
 */
private fun Bitmap.fittedTo(width: Int, height: Int): Bitmap {
    val (targetW, targetH) = ThumbSpec.fillSize(this.width, this.height, width, height)
    if (kotlin.math.abs(this.width - targetW) <= 1 && kotlin.math.abs(this.height - targetH) <= 1) {
        return this
    }
    val scaled = Bitmap.createScaledBitmap(this, targetW, targetH, true)
    // The system copy is ours alone (loadThumbnail hands back a fresh bitmap); free it now
    // rather than waiting for the GC.
    if (scaled !== this) recycle()
    return scaled
}

/**
 * Runs a blocking platform call with a [CancellationSignal] that fires when this coroutine is
 * cancelled. Coil cancels a tile's request when the tile scrolls off screen, but the Binder call
 * underneath kept going to the end, so a fast fling queued up dozens of thumbnails nobody would
 * see and delayed the ones on screen.
 */
private suspend fun <T> withCancellationSignal(block: (CancellationSignal) -> T): T = coroutineScope {
    val signal = CancellationSignal()
    // Only waits. On cancellation it wakes on another thread and cancels the call in flight.
    val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            signal.cancel()
        }
    }
    try {
        block(signal)
    } finally {
        canceller.cancel()
    }
}

/**
 * Ask the system to produce (and persist) a thumbnail for [uri], dropping the bitmap.
 * Used by the idle thumbnail farmer — the point is the side effect on the OS disk cache,
 * so it deliberately touches no app memory cache at all.
 */
fun warmSystemThumbnail(context: Context, uri: android.net.Uri, px: Int): Boolean = try {
    val resolver = context.contentResolver
    if (DocumentsContract.isDocumentUri(context, uri)) {
        val thumb = DocumentsContract.getDocumentThumbnail(resolver, uri, Point(px, px), null)
        thumb?.recycle()
        thumb != null
    } else {
        resolver.loadThumbnail(uri, Size(px, px), null).recycle()
        true
    }
} catch (_: Throwable) {
    false
}
