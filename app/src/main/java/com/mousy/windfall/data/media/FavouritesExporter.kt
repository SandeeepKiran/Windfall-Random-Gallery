package com.mousy.windfall.data.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mousy.windfall.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
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
        val mediaCount: Int = 0,
        val settingsJson: String? = null,
        val displayNames: List<String> = emptyList(),
    )

    suspend fun exportFavouritesZip(
        favourites: List<MediaItem>,
        outputDir: File = context.getExternalFilesDir(null) ?: context.filesDir,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (favourites.isEmpty()) error("No favourites to export")
            outputDir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val zipFile = File(outputDir, "windfall_favourites_$stamp.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                favourites.forEach { item ->
                    val entryName = sanitizeEntryName(item.displayName)
                    zip.putNextEntry(ZipEntry(entryName))
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        BufferedInputStream(input).copyTo(zip)
                    } ?: zip.write(byteArrayOf())
                    zip.closeEntry()
                }
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
     * Extracts media into [favTreeUri] when set, otherwise app files `imported_favourites/`.
     * Also reads any `.json` entry as settings backup.
     */
    suspend fun importFavouritesZip(
        zipUri: Uri,
        favTreeUri: String?,
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            var mediaCount = 0
            var settingsJson: String? = null
            val names = mutableListOf<String>()
            val tree = favTreeUri?.takeIf { it.isNotBlank() }?.let {
                DocumentFile.fromTreeUri(context, Uri.parse(it))
            }
            val fallbackDir = File(context.filesDir, "imported_favourites").also { it.mkdirs() }

            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val rawName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                        if (!entry.isDirectory && rawName.isNotBlank() && !rawName.startsWith(".")) {
                            val lower = rawName.lowercase(Locale.US)
                            when {
                                lower.endsWith(".json") -> {
                                    settingsJson = zip.readBytes().toString(Charsets.UTF_8)
                                }
                                else -> {
                                    val safe = sanitizeEntryName(rawName)
                                    if (tree != null) {
                                        val existing = tree.findFile(safe)
                                        if (existing == null) {
                                            val mime = guessMime(safe)
                                            val dest = tree.createFile(mime, safe)
                                                ?: error("Unable to create $safe in favourites folder")
                                            context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                                                zip.copyTo(out)
                                            }
                                        } else {
                                            // Skip duplicate; still count as restored name
                                            zip.drain()
                                        }
                                    } else {
                                        val dest = File(fallbackDir, safe)
                                        if (!dest.exists()) {
                                            FileOutputStream(dest).use { out -> zip.copyTo(out) }
                                        } else {
                                            zip.drain()
                                        }
                                    }
                                    names += safe
                                    mediaCount++
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Unable to open zip")

            ImportResult(mediaCount = mediaCount, settingsJson = settingsJson, displayNames = names)
        }
    }

    /** DEVICE-ONLY: Copy a single Uri to a destination file (copy, never move). */
    suspend fun copyUriToFile(source: Uri, dest: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Unable to open source")
            Unit
        }
    }

    private fun sanitizeEntryName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private fun java.util.zip.ZipInputStream.drain() {
        val buf = ByteArray(8_192)
        while (read(buf) != -1) {
            // discard
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }
}
