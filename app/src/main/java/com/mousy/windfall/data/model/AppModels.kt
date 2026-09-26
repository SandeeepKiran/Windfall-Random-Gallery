package com.mousy.windfall.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(val label: String, val icon: ImageVector, val locked: Boolean = false) {
    FAV("Favourites", Icons.Default.Favorite),
    RECENT("Recent", Icons.Default.Schedule),
    GALLERY("Gallery", Icons.Default.Shuffle, locked = true),
    SLIDESHOW("Slideshow", Icons.Default.Slideshow),
    ALBUM("Albums", Icons.Default.PhotoAlbum),
    MULTIVIDEO("Videos", Icons.Default.VideoLibrary),
    SETTINGS("More", Icons.Default.MoreHoriz, locked = true);

    /** Compact nav label for NavigationBar / suite (icon-first but not empty). */
    val shortLabel: String
        get() = when (this) {
            FAV -> "Favs"
            RECENT -> "Recent"
            GALLERY -> "Gallery"
            SLIDESHOW -> "Show"
            ALBUM -> "Albums"
            MULTIVIDEO -> "Videos"
            SETTINGS -> "More"
        }

    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: String): AppTab? = entries.find { it.key == key }

        /**
         * Default strip ending ... Slideshow  /  Albums  /  Videos  /  More.
         * Gallery / More stay locked; Albums & Videos stay hidden by default.
         */
        val defaultOrder: List<AppTab> = listOf(
            FAV, RECENT, GALLERY, SLIDESHOW, ALBUM, MULTIVIDEO, SETTINGS,
        )

        /** Optional tabs start unchecked in Settings tab-order UI. */
        val defaultHidden: Set<AppTab> = setOf(MULTIVIDEO, ALBUM)
    }
}

enum class GridMode {
    SWIPE, SCROLL;

    val icon get() = when (this) {
        SWIPE -> Icons.Default.Shuffle
        SCROLL -> Icons.AutoMirrored.Filled.ViewList
    }
}

enum class ThemeMode { LIGHT, DARK }

enum class AccentColor(val key: String, val label: String, val pastelHex: Long) {
    ROSE("rose", "Rose", 0xFFE9A8BD),
    LAVENDER("lavender", "Lavender", 0xFFC9B7F5),
    MINT("mint", "Mint", 0xFF8FF0C4),
    PEACH("peach", "Peach", 0xFFFFCBA8),
    SKY("sky", "Sky", 0xFFA9D9FF),
    SAND("sand", "Sand", 0xFFECD79A);

    companion object {
        val DEFAULT: AccentColor = SAND

        fun fromKey(key: String): AccentColor = entries.find { it.key == key } ?: DEFAULT
    }
}

data class FileTypeFilter(
    val photo: Boolean = true,
    val video: Boolean = true,
    val gif: Boolean = true,
    val audio: Boolean = true,
)

data class TabFeatures(
    val multivideo: Boolean = false,
    val album: Boolean = false,
)

data class MultiVideoCell(
    val index: Int,
    val mediaId: Long? = null,
    val uri: String? = null,
    val displayName: String? = null,
    val isAudio: Boolean = false,
    val playing: Boolean = false,
    val muted: Boolean = false,
    val progress: Float = 0f,
)

data class MultiVideoState(
    val count: Int = 2,
    val muteAll: Boolean = false,
    val landscape: Boolean = false,
    val overlayVisible: Boolean = true,
    val chromeVisible: Boolean = true,
    val pickerIndex: Int? = null,
    val cells: List<MultiVideoCell> = List(4) { MultiVideoCell(index = it) },
)

data class SnackMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = false,
    val accent: AccentColor = AccentColor.DEFAULT,
    val columns: Int = 3,
    val gridMode: GridMode = GridMode.SWIPE,
    val selectedFolders: Set<String> = emptySet(),
    val safTreeUris: Set<String> = emptySet(),
    val fileTypes: Map<String, Boolean> = emptyMap(),
    /** Extension - count of files seen in last scan (for Settings UI). */
    val discoveredFileTypeCounts: Map<String, Int> = emptyMap(),
    /** Wall-clock time of the scan that produced [discoveredFileTypeCounts]; 0 = never. */
    val fileTypeCountsScannedAtMs: Long = 0L,
    val favIds: Set<String> = emptySet(),
    val dontLoop: Boolean = false,
    val disableSwipeDelete: Boolean = true,
    /** Hides delete UI and blocks all delete actions app-wide. */
    val disableDeleteOptions: Boolean = false,
    /** Legacy alias kept for import/export compatibility. */
    val disableEditDelete: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** Padding + rounded corners on gallery thumbnails; off gives an edge-to-edge grid. */
    val thumbnailPadding: Boolean = false,
    val copyFavs: Boolean = false,
    val copyFavPath: String = "",
    val copyFavTreeUri: String = "",
    /** Favourites tab ignores the folder selection and shows favourites from anywhere. */
    val showAllFavourites: Boolean = false,
    val hiddenFolders: Map<String, Boolean> = defaultHiddenFolders(),
    val tabFeatures: TabFeatures = TabFeatures(),
    val tabOrder: List<AppTab> = AppTab.defaultOrder,
    val tabHidden: Set<AppTab> = AppTab.defaultHidden,
    val speedIdx: Int = 2,
    val customMs: Long = 8_000L,
    /** Legacy day count; kept for migration. Prefer [recentWindow]. */
    val recentWindowDays: Int = 30,
    /** Favourites date window (independent of Recents). */
    val favWindow: FavWindow = FavWindow.ALL,
    /** Favourites media-type filter (independent of Recents). */
    val favTypes: FileTypeFilter = FileTypeFilter(),
    /** Recents date window (independent of Favourites). */
    val recentWindow: FavWindow = FavWindow.Days(30),
    /** Recents media-type filter (independent of Favourites). */
    val recentTypes: FileTypeFilter = FileTypeFilter(),
    /**
     * Legacy (pre-continuity): shuffle *history* seeds. Swipe paging now moves a cursor through
     * ONE seeded order per session, so history is no longer stored. Fields kept only so old
     * installs and settings import/export don't break.
     */
    val shuffleSeeds: List<Long> = emptyList(),
    val shuffleSeedIndex: Int = 0,
    /**
     * Seed prepared for the NEXT app launch. Every open starts a fresh random order, but that
     * order is decided (and its first page pre-decoded) by the *previous* session, so a cold
     * start lands on thumbnails that are already in the caches. 0 = nothing prepared yet.
     */
    val nextLaunchSeed: Long = 0L,
    /**
     * Idle thumbnail-farmer checkpoint: [farmPosition] items of the library identified by
     * [farmFingerprint] already have OS thumbnails generated. Lets the sweep resume across
     * sessions instead of restarting.
     */
    val farmFingerprint: String = "",
    val farmPosition: Int = 0,
    /**
     * Exponential moving average of how many items get viewed per session. Drives how big
     * a random slice of the library the gallery prepares up front.
     */
    val avgViewedPerSession: Float = SamplingDefaults.INITIAL_AVG_VIEWED,
) {
    /** Effective “delete disabled” - either dedicated or legacy toggle. */
    val deletesDisabled: Boolean get() = disableDeleteOptions || disableEditDelete

    companion object {
        fun defaultHiddenFolders(): Map<String, Boolean> = mapOf(
            ".thumbnails" to false,
            ".hidden_pics" to false,
            "Android/media" to false,
            ".Trash" to false,
        )

        fun defaults(): AppSettings = AppSettings()
    }
}

/**
 * Date window for Favourites / Recents filters.
 *
 * Important: [options] is lazy - a sealed-class companion that eagerly lists
 * nested objects can leave null entries (init-order), which crashed the calendar menu.
 */
sealed class FavWindow {
    data object ALL : FavWindow()
    data class Days(val days: Int) : FavWindow()

    fun label(): String = when (this) {
        is ALL -> "All time"
        is Days -> when (days) {
            365 -> "1 year"
            else -> "$days days"
        }
    }

    /** Compact label for filter buttons (avoids overflow on narrow screens). */
    fun shortLabel(): String = when (this) {
        is ALL -> "All time"
        is Days -> when (days) {
            365 -> "1y"
            else -> "${days}d"
        }
    }

    fun matches(ageDays: Int): Boolean = when (this) {
        is ALL -> true
        is Days -> {
            val limit = days.coerceIn(1, 3650)
            val age = ageDays.coerceAtLeast(0)
            age <= limit
        }
    }

    fun sameAs(other: FavWindow): Boolean = when (this) {
        is ALL -> other is ALL
        is Days -> other is Days && other.days == days
    }

    /** Day count for legacy Recents sync; null means "all time". */
    fun asRecentDays(): Int? = when (this) {
        is ALL -> null
        is Days -> days.coerceIn(1, 3650)
    }

    /** Stable encode key used in DataStore. */
    fun encode(): String = when (this) {
        is ALL -> "all"
        is Days -> "days:${days.coerceIn(1, 3650)}"
    }

    companion object {
        /**
         * Lazy so nested `ALL` / `Days` are fully initialized before the list is built.
         * Eager `listOf(ALL, ...)` in this companion previously produced null entries at runtime.
         */
        val options: List<FavWindow> by lazy {
            listOf(
                FavWindow.ALL,
                FavWindow.Days(7),
                FavWindow.Days(14),
                FavWindow.Days(30),
                FavWindow.Days(60),
                FavWindow.Days(90),
                FavWindow.Days(365),
            )
        }

        /** Map any window onto a canonical instance; null-safe against corrupt settings. */
        fun normalize(window: FavWindow?): FavWindow {
            val w = window ?: return FavWindow.ALL
            return when (w) {
                is ALL -> FavWindow.ALL
                is Days -> options.filterIsInstance<Days>().find { it.days == w.days }
                    ?: FavWindow.Days(w.days.coerceIn(1, 3650))
            }
        }

        fun fromRecentDays(days: Int): FavWindow {
            val d = days.coerceIn(1, 3650)
            return options.filterIsInstance<Days>().find { it.days == d } ?: FavWindow.Days(d)
        }

        fun decode(raw: String?): FavWindow {
            if (raw.isNullOrBlank()) return FavWindow.ALL
            return when {
                raw.equals("all", ignoreCase = true) -> FavWindow.ALL
                raw.startsWith("days:", ignoreCase = true) -> {
                    val days = raw.substringAfter(':').toIntOrNull()?.coerceIn(1, 3650) ?: 30
                    fromRecentDays(days)
                }
                // Numeric legacy
                raw.toIntOrNull() != null -> fromRecentDays(raw.toInt())
                else -> FavWindow.ALL
            }
        }

        fun cycle(current: FavWindow?): FavWindow {
            val canonical = normalize(current)
            val idx = options.indexOfFirst { it.sameAs(canonical) }.takeIf { it >= 0 } ?: 0
            return options.getOrElse((idx + 1) % options.size) { FavWindow.ALL }
        }
    }
}

/** Repair null/corrupt window fields so [AppSettings.copy] never NPEs. */
fun AppSettings.sanitized(): AppSettings {
    val safeFav = (favWindow as FavWindow?) ?: FavWindow.ALL
    val safeRecent = (recentWindow as FavWindow?) ?: FavWindow.Days(30)
    return copy(
        favWindow = FavWindow.normalize(safeFav),
        recentWindow = FavWindow.normalize(safeRecent),
    )
}

object SlideshowSpeeds {
    data class Speed(val label: String, val ms: Long)

    val speeds: List<Speed> = listOf(
        Speed("1s", 1_000),
        Speed("2s", 2_000),
        Speed("5s", 5_000),
        Speed("10s", 10_000),
        Speed("15s", 15_000),
        Speed("30s", 30_000),
        Speed("1min", 60_000),
        Speed("5min", 300_000),
        Speed("Custom", 8_000),
        Speed("Off", 0),
    )

    const val CUSTOM_INDEX = 8
    const val OFF_INDEX = 9

    val recentWindows: List<Int> = listOf(7, 14, 30, 60, 90, 365)

    val supportedExtensions: Set<String> = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "mp4", "mp3", "m4a", "aac", "wav", "ogg", "flac",
    )

    val audioExtensions: Set<String> = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus")
}
