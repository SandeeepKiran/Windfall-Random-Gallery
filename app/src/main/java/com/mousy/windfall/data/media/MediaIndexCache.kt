package com.mousy.windfall.data.media

import android.content.Context
import android.net.Uri
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.data.model.MediaType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * INSTANT COLD START: a flat-file snapshot of the last completed library scan.
 *
 * On launch the gallery renders from this snapshot immediately (its thumbnails are already in
 * the OS thumbnail cache, so the first grid is fully visible in well under a second), while
 * the real MediaStore/SAF scan runs behind it and reconciles. The header line stores the scan
 * key (folders + filters); if settings changed since the snapshot, it's discarded.
 *
 * Deliberately a hand-rolled TSV instead of Room/serialization: zero new dependencies, one
 * sequential read, and the model is a flat data class. ~10k items ≈ a small number of MB.
 */
class MediaIndexCache(private val context: Context) {

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    suspend fun load(expectedScanKey: String): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            val f = file
            if (!f.exists()) return@runCatching emptyList()
            f.bufferedReader().useLines { lines ->
                val iterator = lines.iterator()
                if (!iterator.hasNext()) return@useLines emptyList()
                val header = iterator.next()
                // Different folder selection / filters -> snapshot is for a different library.
                if (header != "$VERSION|$expectedScanKey") return@useLines emptyList()
                val items = ArrayList<MediaItem>(4096)
                while (iterator.hasNext()) {
                    parseLine(iterator.next())?.let(items::add)
                }
                items
            }
        }.getOrElse { emptyList() }
    }

    suspend fun save(scanKey: String, items: List<MediaItem>) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.bufferedWriter().use { out ->
                out.write("$VERSION|$scanKey")
                out.newLine()
                for (item in items) {
                    out.write(encodeLine(item))
                    out.newLine()
                }
            }
            // Atomic swap so a mid-write kill can't leave a half snapshot.
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun encodeLine(item: MediaItem): String = listOf(
        item.id.toString(),
        item.uri.toString(), // valid URIs cannot contain raw tabs/newlines
        item.displayName.sanitize(),
        item.mimeType.sanitize(),
        item.extension.sanitize(),
        item.mediaType.name,
        item.folderPath.sanitize(),
        item.dateTakenMs.toString(),
        item.dateAddedMs.toString(),
        item.sizeBytes.toString(),
        item.width.toString(),
        item.height.toString(),
        item.durationMs.toString(),
    ).joinToString("\t")

    private fun parseLine(line: String): MediaItem? {
        val parts = line.split('\t')
        if (parts.size != 13) return null
        return runCatching {
            MediaItem(
                id = parts[0].toLong(),
                uri = Uri.parse(parts[1]),
                displayName = parts[2],
                mimeType = parts[3],
                extension = parts[4],
                mediaType = MediaType.valueOf(parts[5]),
                folderPath = parts[6],
                dateTakenMs = parts[7].toLong(),
                dateAddedMs = parts[8].toLong(),
                sizeBytes = parts[9].toLong(),
                width = parts[10].toInt(),
                height = parts[11].toInt(),
                durationMs = parts[12].toLong(),
            )
        }.getOrNull()
    }

    private fun String.sanitize(): String = replace('\t', ' ').replace('\n', ' ')

    private companion object {
        const val FILE_NAME = "media_index.tsv"
        /** v2: files in added (SAF) folders got stable ids, so v1 snapshots hold stale keys. */
        const val VERSION = "v2"
    }
}
