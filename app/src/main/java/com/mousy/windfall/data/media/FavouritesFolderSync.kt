package com.mousy.windfall.data.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mousy.windfall.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DEVICE-ONLY: Syncs favourites to a user-chosen SAF folder — copies on add, deletes copy on remove.
 * Never modifies original gallery files.
 */
class FavouritesFolderSync(private val context: Context) {

    suspend fun syncFavouriteAdded(
        treeUri: String,
        item: MediaItem,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                ?: error("Invalid tree URI")
            val existing = tree.findFile(item.displayName)
            if (existing != null) return@runCatching
            val mime = item.mimeType.ifBlank { "application/octet-stream" }
            val dest = tree.createFile(mime, item.displayName)
                ?: error("Unable to create file in favourites folder")
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                context.contentResolver.openOutputStream(dest.uri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read source media")
        }
    }

    suspend fun syncFavouriteRemoved(
        treeUri: String,
        displayName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                ?: error("Invalid tree URI")
            tree.findFile(displayName)?.delete()
            Unit
        }
    }

    suspend fun syncAll(
        treeUri: String,
        favourites: List<MediaItem>,
        previousNames: Set<String>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val currentNames = favourites.map { it.displayName }.toSet()
            val removed = previousNames - currentNames
            removed.forEach { name -> syncFavouriteRemoved(treeUri, name) }
            favourites.forEach { item -> syncFavouriteAdded(treeUri, item) }
        }
    }
}
