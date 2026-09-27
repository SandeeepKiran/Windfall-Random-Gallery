package com.mousy.windfall.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline-friendly diagnostics: dump recent logcat lines to a cache file and share via Intent.
 */
object LogCapture {
    private const val MAX_LINES = 2_500
    private const val FILE_PREFIX = "windfall_log_"
    private const val KEEP_RECENT_LOGS = 5

    /** Folder under the private cache; `res/xml/file_paths.xml` shares exactly this. */
    private const val LOG_DIR = "logs"

    /**
     * Document addresses and storage paths name the user's folders and files (system and
     * player errors print them). A log is meant for sharing, so they're masked.
     */
    private val contentUri = Regex("""content://\S+""")
    private val storagePath = Regex("""/(storage|sdcard|mnt)/\S+""")

    /** Runs `logcat` and writes a file: call it off the main thread. */
    fun captureToCache(context: Context): Result<File> = runCatching {
        val now = Date()
        // Timestamped so successive shares are distinguishable instead of overwriting.
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
        val readableStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(now)
        val dir = File(context.cacheDir, LOG_DIR).apply { mkdirs() }
        // Logs used to sit loose in the cache root; clear any left from older versions.
        context.cacheDir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }
        val out = File(dir, "$FILE_PREFIX$fileStamp.txt")
        val header = buildString {
            appendLine("Windfall diagnostics")
            appendLine("Captured: $readableStamp")
            appendLine("Package: ${context.packageName}")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine("--- logcat (tail) ---")
        }
        val body = dumpLogcat()
            .replace(contentUri, "content://…")
            .replace(storagePath, "/$1/…")
        out.writeText(header + body)
        prunePreviousLogs(dir, keep = out)
        out
    }

    private fun prunePreviousLogs(dir: File, keep: File) {
        runCatching {
            dir
                .listFiles { f -> f.name.startsWith(FILE_PREFIX) && f != keep }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEEP_RECENT_LOGS - 1)
                ?.forEach { it.delete() }
        }
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Windfall log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun dumpLogcat(): String {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", MAX_LINES.toString(), "*:W"),
        )
        return try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }.ifBlank { "(no logcat output — grant permission or run after a crash)" }
        } finally {
            process.destroy()
        }
    }
}
