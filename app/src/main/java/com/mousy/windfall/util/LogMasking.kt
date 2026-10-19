package com.mousy.windfall.util

/**
 * Removes personal names from a log before it is shared. A crash log usually goes to a
 * stranger (a GitHub issue), so it must not show the user's file names, folder names or
 * email addresses. Pure Kotlin, so the unit tests can check it on the JVM.
 *
 * The file extension stays, because "a .heic file failed" helps with the bug. The top folder
 * (DCIM, Pictures, …) stays for the same reason. Names can contain spaces, so a path is masked
 * to the end of the line (or a quote), and a file name can have up to four words. Too much
 * masking is better than a name in a public bug report.
 */
object LogMasking {
    /** The rest of a path: everything to the end of the line or a quote. */
    private const val PATH_REST = """[^\n"'<>|]*"""

    /** One word of a file name. */
    private const val WORD = """[^\s/\\"'<>:|*?=]+"""

    private val contentUri = Regex("""content://\S+""")
    private val storagePath = Regex("""/(storage|sdcard|mnt)/$PATH_REST""")

    /** Storage-volume document IDs, for example "primary:Pictures/Private" or "1A2B-3C4D:DCIM". */
    private val volumeDocId = Regex("""\b(primary|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}):$PATH_REST""")

    /** Relative media folders as MediaStore gives them, for example "DCIM/Camera/". */
    private val mediaFolder = Regex(
        """\b(DCIM|Pictures|Movies|Music|Download|Downloads|Documents|Recordings|Podcasts|""" +
            """Ringtones|Alarms|Notifications|Audiobooks)/$PATH_REST""",
    )

    private val email = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")

    /**
     * A file name (up to four words) with a media, archive or settings extension. The name goes,
     * the extension stays. Not when a dot and a letter follow: "org.json.JSONException" and
     * "java.util.zip.ZipFile" are class names, not files.
     */
    private val fileName = Regex(
        """$WORD(?: $WORD){0,3}\.(jpe?g|png|gif|webp|heic|heif|avif|bmp|dng|raw|tiff?|""" +
            """mp4|mkv|mov|webm|3gp|avi|m4v|mp3|m4a|aac|flac|ogg|opus|wav|amr|zip|json)\b(?!\.\w)""",
        RegexOption.IGNORE_CASE,
    )

    fun mask(text: String): String = text
        .replace(contentUri, "content://…")
        .replace(storagePath, "/$1/…")
        .replace(volumeDocId, "$1:…")
        .replace(mediaFolder, "$1/…")
        .replace(email, "…@…")
        .replace(fileName) { "….${it.groupValues[1]}" }
}
