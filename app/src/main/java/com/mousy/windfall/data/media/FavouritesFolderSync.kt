package com.mousy.windfall.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.mousy.windfall.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DEVICE-ONLY: Keeps copies of favourites in a user-chosen SAF folder — copies on add, removes
 * the copy on un-favourite. Never modifies original gallery files.
 *
 * SAFETY RULE: this class only ever deletes a document it created itself, identified by the
 * exact address [copyFavourite] returned. It used to delete "the file in the folder with this
 * name", and the folder is the user's choice: pointed at a folder that also holds originals
 * (DCIM/Camera, say), hearting a photo could delete the original.
 */
class FavouritesFolderSync(private val context: Context) {

    /**
     * Copies [item] into the folder. Returns the new copy's address, or null when the folder
     * already has a file of that name (nothing is overwritten, and that file is not ours).
     */
    suspend fun copyFavourite(
        treeUri: String,
        item: MediaItem,
    ): Result<Uri?> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                ?: error("Invalid tree URI")
            if (tree.findFile(item.displayName) != null) return@runCatching null
            val mime = item.mimeType.ifBlank { "application/octet-stream" }
            val dest = tree.createFile(mime, item.displayName)
                ?: error("Unable to create file in favourites folder")
            try {
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(dest.uri)?.use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Unable to read source media")
            } catch (t: Throwable) {
                // Don't leave a half-written copy behind.
                dest.delete()
                throw t
            }
            dest.uri
        }
    }

    /** Deletes one copy this class created, by the address [copyFavourite] returned. */
    suspend fun removeCopy(copyUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!DocumentsContract.deleteDocument(context.contentResolver, copyUri)) {
                error("Unable to remove the copy")
            }
        }
    }

    /** Copies every favourite that isn't in the folder yet; returns the copies it made. */
    suspend fun copyAll(
        treeUri: String,
        favourites: List<MediaItem>,
    ): List<Uri> = favourites.mapNotNull { item -> copyFavourite(treeUri, item).getOrNull() }
}
