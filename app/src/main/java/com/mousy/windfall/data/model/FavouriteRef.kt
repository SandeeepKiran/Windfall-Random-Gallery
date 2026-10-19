package com.mousy.windfall.data.model

import java.util.Locale

/**
 * A favourite described by its folder, file name and size.
 *
 * Favourites are saved by media ID, and a new phone gives each file a new ID. A settings file
 * therefore also lists each favourite in this form. On import, a favourite that the app cannot
 * find by ID is matched again by folder, name and size. The size stops two different files with
 * the same name from being mixed up. This match only adds hearts, it never deletes a file, so a
 * match by name is safe here (compare the Favourites-folder rule in AGENTS.md).
 */
data class FavouriteRef(val folder: String, val name: String, val sizeBytes: Long) {

    /** One string for DataStore. Always use this form to compare two references. */
    fun encode(): String = "$sizeBytes$SEP${normalizeFolder(folder)}$SEP$name"

    companion object {
        /** The ASCII unit separator. It does not occur in folder or file names. */
        private const val SEP = '\u001F'

        /** Size, folder and name. */
        private const val ENCODED_PARTS = 3

        /** Upper limit for references kept from imports, so a strange file cannot fill the settings. */
        const val MAX_PENDING = 20_000

        fun of(item: MediaItem): FavouriteRef =
            FavouriteRef(normalizeFolder(item.folderPath), item.displayName, item.sizeBytes)

        /** Null for a damaged or unknown entry. */
        fun decode(raw: String): FavouriteRef? {
            val parts = raw.split(SEP)
            val size = parts.firstOrNull()?.toLongOrNull()
            val valid = parts.size == ENCODED_PARTS && parts.last().isNotBlank()
            return size?.takeIf { valid }?.let { FavouriteRef(parts[1], parts[2], it) }
        }

        /** "DCIM/Camera/", "/dcim/camera" and "DCIM/Camera" are the same folder. */
        fun normalizeFolder(path: String): String = path.trim().trim('/').lowercase(Locale.ROOT)

        /**
         * True when a reference can be matched safely. A file of unknown size (0) is not
         * matched, because then only the name would be compared.
         */
        fun isMatchable(ref: FavouriteRef): Boolean = ref.sizeBytes > 0L && ref.name.isNotBlank()
    }
}
