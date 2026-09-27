package com.mousy.windfall.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.data.model.MediaType
import com.mousy.windfall.data.model.SlideshowSpeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * DEVICE-ONLY: Scans real on-device media via MediaStore and SAF tree URIs.
 * Requires storage permissions and user-selected folder access.
 */
class MediaRepository(private val context: Context) {

    data class FolderInfo(
        val path: String,
        val displayName: String,
        val group: String,
        val mediaCount: Int,
    )

    suspend fun scanMedia(
        selectedFolders: Set<String>,
        safTreeUris: Set<String>,
        hiddenFolders: Map<String, Boolean>,
        fileTypeFilters: Map<String, Boolean>,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val storeFolders = mediaStoreFolderKeys(selectedFolders)
        val items = mutableListOf<MediaItem>()
        // Only query MediaStore when at least one real folder is selected.
        if (storeFolders.isNotEmpty()) {
            items += queryMediaStore(storeFolders, hiddenFolders, fileTypeFilters, discoverOnly = false)
        }
        if (safTreeUris.isNotEmpty()) {
            items += scanSafTrees(safTreeUris, fileTypeFilters)
        }
        items.distinctBy { it.stableKey }
    }

    /** DEVICE-ONLY: Scan a single SAF tree (e.g. favourites copy folder). */
    suspend fun scanSafTree(
        treeUri: String,
        fileTypeFilters: Map<String, Boolean> = emptyMap(),
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (treeUri.isBlank()) emptyList()
        else scanSafTrees(setOf(treeUri), fileTypeFilters)
    }

    /**
     * Folder list for the Settings picker. Counts rows straight off the cursor instead of
     * materialising a [MediaItem] per file — this walks the entire device library, so
     * building objects here was the single most expensive thing the app did at startup.
     */
    suspend fun discoverFolders(
        hiddenFolders: Map<String, Boolean>,
    ): List<FolderInfo> = withContext(Dispatchers.IO) {
        val counts = HashMap<String, Int>()
        for (collection in mediaCollections) {
            currentCoroutineContext().ensureActive()
            countFolderRows(collection, hiddenFolders, counts)
        }
        counts.entries
            .map { (path, count) ->
                FolderInfo(
                    path = path,
                    displayName = path.substringAfterLast('/').ifBlank { path },
                    group = folderGroup(path),
                    mediaCount = count,
                )
            }
            .sortedBy { it.path }
    }

    /**
     * Resolves favourite keys anywhere on the device, ignoring the folder selection. Used by the
     * "show favourites from all folders" option; builds a [MediaItem] only for matching rows.
     */
    suspend fun scanFavouritesByKey(
        favouriteKeys: Set<String>,
        hiddenFolders: Map<String, Boolean>,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (favouriteKeys.isEmpty()) return@withContext emptyList()
        val found = mutableListOf<MediaItem>()
        for (collection in mediaCollections) {
            currentCoroutineContext().ensureActive()
            found += queryCollection(
                collection = collection,
                selectedFolders = emptySet(),
                hiddenFolders = hiddenFolders,
                fileTypeFilters = emptyMap(),
                discoverOnly = true,
                includeUnsupported = true,
                keyFilter = favouriteKeys,
            )
        }
        found.distinctBy { it.stableKey }
    }

    /**
     * Count every extension under selected folders (including non-playable types)
     * for the Settings file-type list. Does not apply enabled/disabled filters.
     */
    suspend fun discoverExtensionCounts(
        selectedFolders: Set<String>,
        safTreeUris: Set<String>,
        hiddenFolders: Map<String, Boolean>,
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val counts = HashMap<String, Int>()
        val storeFolders = mediaStoreFolderKeys(selectedFolders)
        if (storeFolders.isNotEmpty()) {
            countExtensionRows(storeFolders, hiddenFolders, counts)
        }
        for (treeUri in safTreeUris) {
            currentCoroutineContext().ensureActive()
            walkSafTree(treeUri) { entry ->
                val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
                if (ext.isNotBlank()) counts[ext] = (counts[ext] ?: 0) + 1
            }
        }
        counts
    }

    private suspend fun queryMediaStore(
        selectedFolders: Set<String>,
        hiddenFolders: Map<String, Boolean>,
        fileTypeFilters: Map<String, Boolean>,
        discoverOnly: Boolean = false,
        includeUnsupported: Boolean = false,
    ): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        for (collection in mediaCollections) {
            result += queryCollection(
                collection,
                selectedFolders,
                hiddenFolders,
                fileTypeFilters,
                discoverOnly,
                includeUnsupported,
            )
        }
        return result
    }

    private suspend fun queryCollection(
        collection: Uri,
        selectedFolders: Set<String>,
        hiddenFolders: Map<String, Boolean>,
        fileTypeFilters: Map<String, Boolean>,
        discoverOnly: Boolean,
        includeUnsupported: Boolean = false,
        keyFilter: Set<String>? = null,
    ): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
        )

        // Pre-lowered once: this comparison runs for every row of the device library, and
        // re-normalising the selection per row was a large slice of a folder change.
        val selectedLower = selectedFolders.map { normalizeFolderPath(it).lowercase(Locale.US) }
        val hidden = hiddenSegments(hiddenFolders)
        val items = mutableListOf<MediaItem>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val relCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

            var row = 0
            while (cursor.moveToNext()) {
                if (++row % CANCEL_CHECK_ROWS == 0) currentCoroutineContext().ensureActive()
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: continue
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val relativePath = cursor.getString(relCol)?.let { normalizeFolderPath(it) } ?: run {
                    val dataPath = cursor.getString(dataCol) ?: return@run ""
                    normalizeFolderPath(inferRelativePath(dataPath))
                }
                if (relativePath.isBlank()) continue
                val pathLower = relativePath.lowercase(Locale.US)
                if (isHiddenPath(pathLower, hidden)) continue

                if (!discoverOnly) {
                    if (selectedLower.isEmpty()) continue
                    if (!matchesSelection(pathLower, selectedLower)) continue
                }

                val ext = displayName.substringAfterLast('.', "").lowercase(Locale.US)
                if (fileTypeFilters.isNotEmpty() && fileTypeFilters.containsKey(ext) && fileTypeFilters[ext] == false) {
                    continue
                }
                if (!includeUnsupported && fileTypeFilters.isEmpty() && ext.isNotEmpty() &&
                    ext !in SlideshowSpeeds.supportedExtensions
                ) {
                    continue
                }

                val uri = ContentUris.withAppendedId(collection, id)
                if (keyFilter != null && "${id}_$uri" !in keyFilter) continue
                val mediaType = classifyMedia(mime, ext)
                items += MediaItem(
                    id = id,
                    uri = uri,
                    displayName = displayName,
                    mimeType = mime,
                    extension = ext,
                    mediaType = mediaType,
                    folderPath = relativePath,
                    dateTakenMs = cursor.getLong(takenCol).let { if (it > 0) it else 0L },
                    dateAddedMs = cursor.getLong(addedCol) * 1_000L,
                    sizeBytes = cursor.getLong(sizeCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    durationMs = cursor.getLong(durCol).coerceAtLeast(0L),
                )
            }
        }
        return items
    }

    /** Two-column cursor over one collection, tallying rows per folder. */
    private suspend fun countFolderRows(
        collection: Uri,
        hiddenFolders: Map<String, Boolean>,
        into: MutableMap<String, Int>,
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATA,
        )
        val hidden = hiddenSegments(hiddenFolders)
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val relCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            var row = 0
            while (cursor.moveToNext()) {
                if (++row % CANCEL_CHECK_ROWS == 0) currentCoroutineContext().ensureActive()
                val path = folderPathOf(cursor.getString(relCol), cursor.getString(dataCol))
                if (path.isBlank() || isHiddenPath(path.lowercase(Locale.US), hidden)) continue
                into[path] = (into[path] ?: 0) + 1
            }
        }
    }

    /**
     * Tallies rows per file extension.
     *
     * Queries the *Files* collection rather than the three media collections: the point of this
     * list is to surface stray types, and anything that isn't an image/video/audio row only
     * appears here.
     */
    private suspend fun countExtensionRows(
        selectedNormalized: Set<String>,
        hiddenFolders: Map<String, Boolean>,
        into: MutableMap<String, Int>,
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATA,
        )
        val hidden = hiddenSegments(hiddenFolders)
        val selectedLower = selectedNormalized.map { it.lowercase(Locale.US) }
        context.contentResolver.query(filesCollection, projection, null, null, null)?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val relCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            var row = 0
            while (cursor.moveToNext()) {
                if (++row % CANCEL_CHECK_ROWS == 0) currentCoroutineContext().ensureActive()
                val path = folderPathOf(cursor.getString(relCol), cursor.getString(dataCol))
                if (path.isBlank()) continue
                val pathLower = path.lowercase(Locale.US)
                if (isHiddenPath(pathLower, hidden)) continue
                if (!matchesSelection(pathLower, selectedLower)) continue
                val name = cursor.getString(nameCol) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                if (ext.isBlank()) continue
                into[ext] = (into[ext] ?: 0) + 1
            }
        }
    }

    /** DEVICE-ONLY: Walk SAF document trees the user granted via OpenDocumentTree. */
    private suspend fun scanSafTrees(
        treeUris: Set<String>,
        fileTypeFilters: Map<String, Boolean>,
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        for (treeUriStr in treeUris) {
            walkSafTree(treeUriStr) { entry ->
                val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
                if (fileTypeFilters.isNotEmpty() && fileTypeFilters[ext] == false) return@walkSafTree
                val mime = entry.mimeType.ifBlank { guessMime(ext) }
                items += MediaItem(
                    id = safItemId(entry.uri),
                    uri = entry.uri,
                    displayName = entry.name,
                    mimeType = mime,
                    extension = ext,
                    mediaType = classifyMedia(mime, ext),
                    folderPath = entry.folderPath,
                    dateTakenMs = entry.lastModified,
                    dateAddedMs = entry.lastModified,
                    sizeBytes = entry.size,
                    width = 0,
                    height = 0,
                    durationMs = 0L,
                )
            }
        }
        return items
    }

    /**
     * A stable id for a document in an added (SAF) folder, taken from its own address. These ids
     * used to count down in walk order, so adding one file to a folder gave every file after it
     * a new key: favourites of those files were lost and the gallery re-dealt. Always negative,
     * so it can never collide with a MediaStore id.
     */
    private fun safItemId(uri: Uri): Long {
        var hash = FNV_OFFSET
        for (c in uri.toString()) hash = (hash xor c.code.toLong()) * FNV_PRIME
        return -(hash and Long.MAX_VALUE) - 1
    }

    private data class SafEntry(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
        val folderPath: String,
    )

    /**
     * Iterative tree walk that reads every child's metadata from the directory cursor.
     *
     * `DocumentFile.listFiles()` looks cheap but each `name`/`type`/`length()` read fires
     * its own ContentResolver query, so a 5k-file tree became ~20k IPC round trips. One
     * cursor per directory carries all of it.
     */
    private suspend fun walkSafTree(treeUriStr: String, onFile: (SafEntry) -> Unit) {
        val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return
        val rootName = safDisplayName(treeUri, rootId) ?: treeUri.lastPathSegment ?: "SAF"

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        val pending = ArrayDeque<Pair<String, String>>()
        pending.addLast(rootId to rootName)
        var visited = 0
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (++visited > MAX_SAF_DIRECTORIES) return
            val (docId, folderPath) = pending.removeLast()
            val childrenUri = runCatching {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            }.getOrNull() ?: continue

            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)
            }.getOrNull()?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                var row = 0
                while (cursor.moveToNext()) {
                    if (++row % CANCEL_CHECK_ROWS == 0) currentCoroutineContext().ensureActive()
                    val childId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.addLast(childId to "$folderPath/$name")
                    } else {
                        onFile(
                            SafEntry(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                                name = name,
                                mimeType = mime,
                                size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol),
                                lastModified = if (cursor.isNull(modCol)) 0L else cursor.getLong(modCol),
                                folderPath = folderPath,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun safDisplayName(treeUri: Uri, docId: String): String? = runCatching {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun folderPathOf(relativePath: String?, dataPath: String?): String {
        val fromRelative = relativePath?.takeIf { it.isNotBlank() }?.let { normalizeFolderPath(it) }
        if (!fromRelative.isNullOrBlank()) return fromRelative
        val data = dataPath?.takeIf { it.isNotBlank() } ?: return ""
        return normalizeFolderPath(inferRelativePath(data))
    }

    /** Pre-lowercased segments so the per-row check doesn't re-lowercase the settings map. */
    private fun hiddenSegments(hiddenFolders: Map<String, Boolean>): List<String> =
        hiddenFolders.entries
            .filter { !it.value }
            .map { it.key.lowercase(Locale.US) }

    /** [pathLower] must already be normalised and lowercased. */
    private fun isHiddenPath(pathLower: String, hiddenSegments: List<String>): Boolean {
        if (hiddenSegments.isEmpty()) return false
        return hiddenSegments.any { pathLower.contains(it) }
    }

    /** Both sides already normalised and lowercased — no allocation per row. */
    private fun matchesSelection(pathLower: String, selectedLower: List<String>): Boolean {
        for (sel in selectedLower) {
            if (pathLower == sel) return true
            if (pathLower.length > sel.length &&
                pathLower[sel.length] == '/' &&
                pathLower.startsWith(sel)
            ) {
                return true
            }
        }
        return false
    }

    private fun inferRelativePath(dataPath: String): String {
        val markers = listOf("/Pictures/", "/DCIM/", "/Movies/", "/Download/", "/Downloads/")
        for (marker in markers) {
            val idx = dataPath.indexOf(marker, ignoreCase = true)
            if (idx >= 0) {
                val sub = dataPath.substring(idx + 1)
                return sub.substringBeforeLast('/')
            }
        }
        return dataPath.substringBeforeLast('/')
    }

    private fun classifyMedia(mime: String, ext: String): MediaType = when {
        mime.startsWith("video/") || ext == "mp4" || ext == "mkv" || ext == "webm" -> MediaType.VIDEO
        ext == "gif" || mime.equals("image/gif", ignoreCase = true) -> MediaType.GIF
        mime.startsWith("audio/") || ext in SlideshowSpeeds.audioExtensions -> MediaType.AUDIO
        mime.startsWith("image/") -> MediaType.PHOTO
        else -> MediaType.OTHER
    }

    private fun guessMime(ext: String): String = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    companion object {
        private const val CANCEL_CHECK_ROWS = 512
        private const val MAX_SAF_DIRECTORIES = 4_000

        /** 64-bit FNV-1a, for [safItemId]. */
        private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        private const val FNV_PRIME = 0x100000001b3L

        private val mediaCollections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        )

        /** Everything MediaStore will show us, including non-playable file types. */
        private val filesCollection: Uri =
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        private val knownRoots = setOf(
            "pictures", "dcim", "movies", "download", "downloads", "myfiles", "documents",
        )

        /** Hoisted: [normalizeFolderPath] runs once per media row during a scan. */
        private val duplicateSlashes = Regex("/+")

        /** Keys used for MediaStore folder checkboxes (exclude SAF synthetic keys). */
        fun mediaStoreFolderKeys(selectedFolders: Set<String>): Set<String> =
            selectedFolders
                .filter { !it.startsWith("SAF:", ignoreCase = true) }
                .map { normalizeFolderPath(it) }
                .filter { it.isNotBlank() }
                .toSet()

        fun normalizeFolderPath(path: String): String {
            val trimmed = path.trim().trim('/')
            // Fast path: nearly every MediaStore row is already clean, and the regex was
            // allocating a matcher for each one of them.
            if (trimmed.indexOf('\\') < 0 && !trimmed.contains("//")) return trimmed
            return trimmed.replace('\\', '/').replace(duplicateSlashes, "/").trim('/')
        }

        fun folderMatchesSelection(folderPath: String, selectedNormalized: Set<String>): Boolean {
            if (selectedNormalized.isEmpty()) return false
            val path = normalizeFolderPath(folderPath).lowercase(Locale.US)
            if (path.isBlank()) return false
            return selectedNormalized.any { sel ->
                val s = sel.lowercase(Locale.US)
                path == s || path.startsWith("$s/")
            }
        }

        fun folderGroup(path: String): String {
            val root = normalizeFolderPath(path).substringBefore('/', path)
            return if (root.lowercase(Locale.US) in knownRoots) root else "Other (found)"
        }
    }
}
