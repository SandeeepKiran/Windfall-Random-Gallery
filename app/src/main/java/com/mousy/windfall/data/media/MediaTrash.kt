package com.mousy.windfall.data.media

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import com.mousy.windfall.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Moves media to Android's own trash, and back. A trashed file disappears from every gallery
 * but can be restored for about 30 days; after that Android deletes it for good.
 *
 * Android shows its own confirmation for every request (an app can't trash files silently),
 * so this class only builds the requests. The UI launches them and reports the answer.
 */
class MediaTrash(private val context: Context) {

    /**
     * The MediaStore address the trash works with, or null when MediaStore doesn't know the
     * file. Files from added (SAF) folders usually have a MediaStore address too.
     */
    suspend fun trashableUri(item: MediaItem): Uri? = withContext(Dispatchers.IO) {
        if (item.uri.authority == MediaStore.AUTHORITY) return@withContext item.uri
        runCatching { MediaStore.getMediaUri(context, item.uri) }.getOrNull()
    }

    fun trashRequest(uris: Collection<Uri>): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender

    fun restoreRequest(uris: Collection<Uri>): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, uris, false).intentSender
}
