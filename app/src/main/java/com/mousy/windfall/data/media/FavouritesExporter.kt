package com.mousy.windfall.data.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.data.model.SlideshowSpeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * DEVICE-ONLY: Copies favourite media into a zip archive — never moves originals.
 * Also imports a favourites zip (media copies and/or settings JSON inside).
 */
class FavouritesExporter(private val context: Context) {

    data class ImportResult(
        /** Media files written into the Favourites folder. */
        val filesCopied: Int = 0,
        val settingsJson: String? = null,
        /** Names of every media entry in the zip, copied or not; used to mark favourites. */
        val displayNames: List<String> = emptyList(),
    )

    /**
     * Zips [favourites] into the app's private `cache/exports` folder and returns a shareable
     * address for the share sheet, where the user picks where it goes. Private cache, so
     * nothing lingers where USB or a backup could reach it, and only the newest zip is kept.
     */
    suspend fun exportFavouritesZip(favourites: List<MediaItem>): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (favourites.isEmpty()) error("No favourites to export")
            val outputDir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            outputDir.listFiles()?.forEach { it.delete() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(outputDir, "windfall_favourites_$stamp.zip")
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                    val used = HashSet<String>()
                    favourites.forEach { item ->
                        // Two favourites can share a file name; a zip can't hold both under it.
                        zip.putNextEntry(ZipEntry(uniqueName(sanitizeEntryName(item.displayName), used)))
                        context.contentResolver.openInputStream(item.uri)?.use { input ->
                            BufferedInputStream(input).copyTo(zip)
                        }
                        zip.closeEntry()
                    }
                }
            } catch (t: Throwable) {
                zipFile.delete()
                throw t
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile,
            )
        }
    }

    /**
     * DEVICE-ONLY: Restore a favourites zip.
     *
     * Media is copied into [favTreeUri] when set. Without a Favourites folder the files are
     * NOT extracted: they used to go to a private folder nothing ever showed, so an import
     * could quietly fill the phone. Their names still mark matching photos as favourites.
     *
     * A zip is untrusted input, so every limit is explicit: entry count, bytes per file, total
     * bytes against free space, media types only, and a small settings file.
     */
    suspend fun importFavouritesZip(
        zipUri: Uri,
        favTreeUri: String?,
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            var filesCopied = 0
            var settingsJson: String? = null
            val names = mutableListOf<String>()
            val tree = favTreeUri?.takeIf { it.isNotBlank() }?.let {
                DocumentFile.fromTreeUri(context, Uri.parse(it))
            }
            // Leave room for the phone to keep working.
            var budget = context.filesDir.usableSpace - FREE_SPACE_RESERVE_BYTES
            var entries = 0

            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (++entries > MAX_ENTRIES) error("That zip has too many files")
                        // Last path segment only: a zip can't steer files outside the folder.
                        val rawName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                        if (!entry.isDirectory && rawName.isNotBlank() && !rawName.startsWith(".")) {
                            val ext = rawName.substringAfterLast('.', "").lowercase(Locale.US)
                            when {
                                ext == "json" -> settingsJson = zip.readCapped(MAX_SETTINGS_BYTES)
                                    ?.toString(Charsets.UTF_8)
                                    ?: settingsJson
                                ext in MEDIA_EXTENSIONS -> {
                                    val safe = sanitizeEntryName(rawName)
                                    names += safe
                                    if (tree != null && tree.findFile(safe) == null) {
                                        val dest = tree.createFile(guessMime(ext), safe)
                                            ?: error("Unable to create $safe in favourites folder")
                                        val written = try {
                                            context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                                                zip.copyCapped(out, minOf(MAX_FILE_BYTES, budget))
                                            } ?: error("Unable to write $safe")
                                        } catch (t: Throwable) {
                                            dest.delete()
                                            throw t
                                        }
                                        if (written < 0) {
                                            dest.delete()
                                            error("Stopped at $safe: not enough free space")
                                        }
                                        budget -= written
                                        filesCopied++
                                    }
                                }
                                // Anything else in the zip is ignored.
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Unable to open zip")

            ImportResult(filesCopied = filesCopied, settingsJson = settingsJson, displayNames = names)
        }
    }

    private fun sanitizeEntryName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 2
        while (!used.add("$stem ($n)$ext")) n++
        return "$stem ($n)$ext"
    }

    /** Copies at most [limit] bytes; returns the count, or -1 if the entry was bigger. */
    private fun InputStream.copyCapped(out: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) return -1
            out.write(buffer, 0, read)
        }
    }

    /** Reads at most [limit] bytes; null if there were more. */
    private fun InputStream.readCapped(limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        return if (copyCapped(out, limit.toLong()) < 0) null else out.toByteArray()
    }

    private fun guessMime(ext: String): String = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heif"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    private companion object {
        /** Folder under the private cache; `res/xml/file_paths.xml` shares exactly this. */
        const val EXPORT_DIR = "exports"
        const val MAX_ENTRIES = 20_000
        const val MAX_FILE_BYTES = 4L * 1024 * 1024 * 1024
        const val MAX_SETTINGS_BYTES = 1_000_000
        const val FREE_SPACE_RESERVE_BYTES = 500L * 1024 * 1024
        val MEDIA_EXTENSIONS = SlideshowSpeeds.supportedExtensions +
            SlideshowSpeeds.audioExtensions + setOf("heic", "heif", "mkv", "webm", "mov", "3gp")
    }
}
