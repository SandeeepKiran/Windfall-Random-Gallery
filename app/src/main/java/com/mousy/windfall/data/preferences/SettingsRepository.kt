package com.mousy.windfall.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mousy.windfall.data.model.AccentColor
import com.mousy.windfall.data.model.AppSettings
import com.mousy.windfall.data.model.AppTab
import com.mousy.windfall.data.model.FavWindow
import com.mousy.windfall.data.model.FileTypeFilter
import com.mousy.windfall.data.model.GridMode
import com.mousy.windfall.data.model.SlideshowSpeeds
import com.mousy.windfall.data.model.TabFeatures
import com.mousy.windfall.data.model.ThemeMode
import com.mousy.windfall.data.model.sanitized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A settings file damaged on disk used to throw on every read, so the app crashed at every
 * launch until its data was cleared. It now starts again from defaults (favourites included,
 * which is the unavoidable cost). Plain read errors are NOT turned into defaults: the ViewModel
 * saves whole snapshots, so a spurious empty read would be written over the real settings.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "windfall_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        settingsFromPrefs(prefs)
    }

    /** Writes the whole of [settings]; the ViewModel's single writer is the only caller. */
    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs -> writePrefs(prefs, settings) }
    }

    fun exportJson(settings: AppSettings): String = SettingsJson.encode(settings)

    /**
     * [base] with an exported settings file applied on top, or null if [json] isn't one.
     * Only what the file mentions changes, and favourites are added, never removed.
     */
    fun mergeImport(base: AppSettings, json: String): AppSettings? = SettingsJson.merge(base, json)

    private fun writePrefs(prefs: androidx.datastore.preferences.core.MutablePreferences, updated: AppSettings) {
        prefs[Keys.THEME_DARK] = updated.themeMode == ThemeMode.DARK
        prefs[Keys.AMOLED] = updated.amoled
        prefs[Keys.ACCENT] = updated.accent.key
        prefs[Keys.COLUMNS] = updated.columns
        prefs[Keys.GRID_SCROLL] = updated.gridMode == GridMode.SCROLL
        prefs[Keys.SELECTED_FOLDERS] = updated.selectedFolders
        prefs[Keys.SAF_TREE_URIS] = updated.safTreeUris
        prefs[Keys.FILE_TYPES] = encodeFileTypes(updated.fileTypes)
        prefs[Keys.DISCOVERED_TYPE_COUNTS] = encodeCounts(updated.discoveredFileTypeCounts)
        prefs[Keys.TYPE_COUNTS_SCANNED_AT] = updated.fileTypeCountsScannedAtMs
        prefs[Keys.FAV_IDS] = updated.favIds
        prefs[Keys.DONT_LOOP] = updated.dontLoop
        prefs[Keys.DISABLE_SWIPE_DELETE] = updated.disableSwipeDelete
        prefs[Keys.DISABLE_DELETE_OPTIONS] = updated.disableDeleteOptions
        prefs[Keys.DISABLE_EDIT_DELETE] = updated.disableEditDelete
        prefs[Keys.HAPTICS] = updated.hapticsEnabled
        prefs[Keys.THUMBNAIL_PADDING] = updated.thumbnailPadding
        prefs[Keys.COPY_FAVS] = updated.copyFavs
        prefs[Keys.COPY_FAV_PATH] = updated.copyFavPath
        prefs[Keys.COPY_FAV_TREE_URI] = updated.copyFavTreeUri
        prefs[Keys.FAV_COPY_URIS] = updated.favCopyUris
        prefs[Keys.SHOW_ALL_FAVOURITES] = updated.showAllFavourites
        prefs[Keys.HIDDEN_FOLDERS] = encodeHiddenFolders(updated.hiddenFolders)
        prefs[Keys.TAB_FEATURE_MV] = updated.tabFeatures.multivideo
        prefs[Keys.TAB_FEATURE_ALBUM] = updated.tabFeatures.album
        prefs[Keys.TAB_ORDER] = updated.tabOrder.joinToString(",") { it.key }
        prefs[Keys.TAB_HIDDEN] = updated.tabHidden.joinToString(",") { it.key }
        prefs[Keys.SPEED_IDX] = updated.speedIdx
        prefs[Keys.CUSTOM_MS] = updated.customMs
        prefs[Keys.RECENT_WINDOW] = updated.recentWindow.asRecentDays() ?: updated.recentWindowDays
        prefs[Keys.FAV_WINDOW] = FavWindow.normalize(updated.favWindow).encode()
        prefs[Keys.RECENT_WINDOW_ENC] = FavWindow.normalize(updated.recentWindow).encode()
        prefs[Keys.FAV_TYPE_PHOTO] = updated.favTypes.photo
        prefs[Keys.FAV_TYPE_VIDEO] = updated.favTypes.video
        prefs[Keys.FAV_TYPE_GIF] = updated.favTypes.gif
        prefs[Keys.FAV_TYPE_AUDIO] = updated.favTypes.audio
        prefs[Keys.RECENT_TYPE_PHOTO] = updated.recentTypes.photo
        prefs[Keys.RECENT_TYPE_VIDEO] = updated.recentTypes.video
        prefs[Keys.RECENT_TYPE_GIF] = updated.recentTypes.gif
        prefs[Keys.RECENT_TYPE_AUDIO] = updated.recentTypes.audio
        prefs[Keys.SHUFFLE_SEEDS] = updated.shuffleSeeds.joinToString(",")
        prefs[Keys.SHUFFLE_SEED_INDEX] = updated.shuffleSeedIndex
        prefs[Keys.NEXT_LAUNCH_SEED] = updated.nextLaunchSeed
        prefs[Keys.FARM_FINGERPRINT] = updated.farmFingerprint
        prefs[Keys.FARM_POSITION] = updated.farmPosition
        // Purge the pre-seed history blob: it stored up to 40 x 10k keys as one string.
        if (prefs.contains(Keys.LEGACY_SHUFFLE_HISTORY)) prefs.remove(Keys.LEGACY_SHUFFLE_HISTORY)
        if (prefs.contains(Keys.LEGACY_SHUFFLE_HISTORY_INDEX)) prefs.remove(Keys.LEGACY_SHUFFLE_HISTORY_INDEX)
        // The adaptive gallery sample (and its viewing average) is gone; see RandomSample.kt.
        if (prefs.contains(Keys.LEGACY_AVG_VIEWED_PER_SESSION)) prefs.remove(Keys.LEGACY_AVG_VIEWED_PER_SESSION)
    }

    private fun settingsFromPrefs(prefs: Preferences): AppSettings {
        val features = TabFeatures(
            multivideo = prefs[Keys.TAB_FEATURE_MV] ?: false,
            album = prefs[Keys.TAB_FEATURE_ALBUM] ?: false,
        )
        val legacyFavTypes = FileTypeFilter(
            photo = prefs[Keys.FAV_TYPE_PHOTO] ?: true,
            video = prefs[Keys.FAV_TYPE_VIDEO] ?: true,
            gif = prefs[Keys.FAV_TYPE_GIF] ?: true,
            audio = prefs[Keys.FAV_TYPE_AUDIO] ?: true,
        )
        val favWindow = FavWindow.normalize(
            if (prefs.contains(Keys.FAV_WINDOW)) {
                FavWindow.decode(prefs[Keys.FAV_WINDOW])
            } else {
                decodeFavWindowLegacy(null, prefs[Keys.RECENT_WINDOW])
            },
        )
        val recentWindow = FavWindow.normalize(
            when {
                prefs.contains(Keys.RECENT_WINDOW_ENC) ->
                    FavWindow.decode(prefs[Keys.RECENT_WINDOW_ENC])
                prefs[Keys.RECENT_WINDOW] != null ->
                    FavWindow.fromRecentDays(prefs[Keys.RECENT_WINDOW] ?: 30)
                else -> FavWindow.Days(30)
            },
        )
        val recentTypes = if (prefs.contains(Keys.RECENT_TYPE_PHOTO)) {
            FileTypeFilter(
                photo = prefs[Keys.RECENT_TYPE_PHOTO] ?: true,
                video = prefs[Keys.RECENT_TYPE_VIDEO] ?: true,
                gif = prefs[Keys.RECENT_TYPE_GIF] ?: true,
                audio = prefs[Keys.RECENT_TYPE_AUDIO] ?: true,
            )
        } else {
            // Migrate: previously shared with fav types
            legacyFavTypes
        }
        val disableDelete = prefs[Keys.DISABLE_DELETE_OPTIONS]
            ?: (prefs[Keys.DISABLE_EDIT_DELETE] ?: false)

        return AppSettings(
            themeMode = if (prefs[Keys.THEME_DARK] != false) ThemeMode.DARK else ThemeMode.LIGHT,
            amoled = prefs[Keys.AMOLED] ?: false,
            accent = AccentColor.fromKey(prefs[Keys.ACCENT] ?: AccentColor.DEFAULT.key),
            columns = prefs[Keys.COLUMNS] ?: 3,
            gridMode = if (prefs[Keys.GRID_SCROLL] == true) GridMode.SCROLL else GridMode.SWIPE,
            selectedFolders = prefs[Keys.SELECTED_FOLDERS] ?: emptySet(),
            safTreeUris = prefs[Keys.SAF_TREE_URIS] ?: emptySet(),
            fileTypes = decodeFileTypes(prefs[Keys.FILE_TYPES]),
            discoveredFileTypeCounts = decodeCounts(prefs[Keys.DISCOVERED_TYPE_COUNTS]),
            fileTypeCountsScannedAtMs = prefs[Keys.TYPE_COUNTS_SCANNED_AT] ?: 0L,
            favIds = prefs[Keys.FAV_IDS] ?: emptySet(),
            dontLoop = prefs[Keys.DONT_LOOP] ?: false,
            disableSwipeDelete = prefs[Keys.DISABLE_SWIPE_DELETE] ?: true,
            disableDeleteOptions = disableDelete,
            disableEditDelete = prefs[Keys.DISABLE_EDIT_DELETE] ?: false,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            thumbnailPadding = prefs[Keys.THUMBNAIL_PADDING] ?: false,
            copyFavs = prefs[Keys.COPY_FAVS] ?: false,
            copyFavPath = prefs[Keys.COPY_FAV_PATH] ?: "",
            copyFavTreeUri = prefs[Keys.COPY_FAV_TREE_URI] ?: "",
            favCopyUris = prefs[Keys.FAV_COPY_URIS] ?: emptySet(),
            showAllFavourites = prefs[Keys.SHOW_ALL_FAVOURITES] ?: false,
            hiddenFolders = decodeHiddenFolders(prefs[Keys.HIDDEN_FOLDERS]),
            tabFeatures = features,
            tabOrder = decodeTabOrder(prefs[Keys.TAB_ORDER]),
            tabHidden = decodeTabHidden(
                raw = prefs[Keys.TAB_HIDDEN],
                features = features,
                hadExplicitHidden = prefs.contains(Keys.TAB_HIDDEN),
            ),
            speedIdx = prefs[Keys.SPEED_IDX] ?: 2,
            customMs = prefs[Keys.CUSTOM_MS] ?: 8_000L,
            recentWindowDays = prefs[Keys.RECENT_WINDOW] ?: recentWindow.asRecentDays() ?: 30,
            favWindow = favWindow,
            favTypes = legacyFavTypes,
            recentWindow = recentWindow,
            recentTypes = recentTypes,
            shuffleSeeds = decodeSeeds(prefs[Keys.SHUFFLE_SEEDS]),
            shuffleSeedIndex = prefs[Keys.SHUFFLE_SEED_INDEX] ?: 0,
            nextLaunchSeed = prefs[Keys.NEXT_LAUNCH_SEED] ?: 0L,
            farmFingerprint = prefs[Keys.FARM_FINGERPRINT] ?: "",
            farmPosition = prefs[Keys.FARM_POSITION] ?: 0,
        ).sanitized()
    }

    private object Keys {
        val THEME_DARK = booleanPreferencesKey("theme_dark")
        val AMOLED = booleanPreferencesKey("amoled")
        val ACCENT = stringPreferencesKey("accent")
        val COLUMNS = intPreferencesKey("columns")
        val GRID_SCROLL = booleanPreferencesKey("grid_scroll")
        val SELECTED_FOLDERS = stringSetPreferencesKey("selected_folders")
        val SAF_TREE_URIS = stringSetPreferencesKey("saf_tree_uris")
        val FILE_TYPES = stringPreferencesKey("file_types")
        val DISCOVERED_TYPE_COUNTS = stringPreferencesKey("discovered_type_counts")
        val FAV_IDS = stringSetPreferencesKey("fav_ids")
        val DONT_LOOP = booleanPreferencesKey("dont_loop")
        val DISABLE_SWIPE_DELETE = booleanPreferencesKey("disable_swipe_delete")
        val DISABLE_DELETE_OPTIONS = booleanPreferencesKey("disable_delete_options")
        val DISABLE_EDIT_DELETE = booleanPreferencesKey("disable_edit_delete")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val THUMBNAIL_PADDING = booleanPreferencesKey("thumbnail_padding")
        val COPY_FAVS = booleanPreferencesKey("copy_favs")
        val COPY_FAV_PATH = stringPreferencesKey("copy_fav_path")
        val COPY_FAV_TREE_URI = stringPreferencesKey("copy_fav_tree_uri")
        val FAV_COPY_URIS = stringSetPreferencesKey("fav_copy_uris")
        val SHOW_ALL_FAVOURITES = booleanPreferencesKey("show_all_favourites")
        val HIDDEN_FOLDERS = stringPreferencesKey("hidden_folders")
        val TAB_FEATURE_MV = booleanPreferencesKey("tab_feature_mv")
        val TAB_FEATURE_ALBUM = booleanPreferencesKey("tab_feature_album")
        val TAB_ORDER = stringPreferencesKey("tab_order")
        val TAB_HIDDEN = stringPreferencesKey("tab_hidden")
        val SPEED_IDX = intPreferencesKey("speed_idx")
        val CUSTOM_MS = longPreferencesKey("custom_ms")
        val RECENT_WINDOW = intPreferencesKey("recent_window")
        val FAV_WINDOW = stringPreferencesKey("fav_window")
        val RECENT_WINDOW_ENC = stringPreferencesKey("recent_window_enc")
        val FAV_TYPE_PHOTO = booleanPreferencesKey("fav_type_photo")
        val FAV_TYPE_VIDEO = booleanPreferencesKey("fav_type_video")
        val FAV_TYPE_GIF = booleanPreferencesKey("fav_type_gif")
        val FAV_TYPE_AUDIO = booleanPreferencesKey("fav_type_audio")
        val RECENT_TYPE_PHOTO = booleanPreferencesKey("recent_type_photo")
        val RECENT_TYPE_VIDEO = booleanPreferencesKey("recent_type_video")
        val RECENT_TYPE_GIF = booleanPreferencesKey("recent_type_gif")
        val RECENT_TYPE_AUDIO = booleanPreferencesKey("recent_type_audio")
        val TYPE_COUNTS_SCANNED_AT = longPreferencesKey("type_counts_scanned_at")
        val SHUFFLE_SEEDS = stringPreferencesKey("shuffle_seeds")
        val SHUFFLE_SEED_INDEX = intPreferencesKey("shuffle_seed_index")
        val LEGACY_AVG_VIEWED_PER_SESSION = floatPreferencesKey("avg_viewed_per_session")
        val NEXT_LAUNCH_SEED = longPreferencesKey("next_launch_seed")
        val FARM_FINGERPRINT = stringPreferencesKey("farm_fingerprint")
        val FARM_POSITION = intPreferencesKey("farm_position")
        val LEGACY_SHUFFLE_HISTORY = stringPreferencesKey("shuffle_history")
        val LEGACY_SHUFFLE_HISTORY_INDEX = intPreferencesKey("shuffle_history_index")
    }

    private fun decodeSeeds(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }
    }

    private fun decodeTabOrder(raw: String?): List<AppTab> {
        val parsed = if (raw.isNullOrBlank()) {
            AppTab.defaultOrder
        } else {
            raw.split(",").mapNotNull { AppTab.fromKey(it.trim()) }
                .ifEmpty { AppTab.defaultOrder }
        }.toMutableList()
        AppTab.defaultOrder.forEach { tab ->
            if (tab !in parsed) {
                val si = parsed.indexOf(AppTab.SETTINGS)
                if (si >= 0) parsed.add(si, tab) else parsed.add(tab)
            }
        }
        return parsed
    }

    private fun decodeTabHidden(
        raw: String?,
        features: TabFeatures,
        hadExplicitHidden: Boolean,
    ): Set<AppTab> {
        if (!hadExplicitHidden || raw == null) {
            val hidden = AppTab.defaultHidden.toMutableSet()
            if (features.multivideo) hidden.remove(AppTab.MULTIVIDEO)
            if (features.album) hidden.remove(AppTab.ALBUM)
            return hidden
        }
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { AppTab.fromKey(it.trim()) }.toSet()
    }

    private fun decodeFavWindowLegacy(raw: String?, recentDays: Int? = null): FavWindow = when {
        raw == null -> {
            if (recentDays != null && recentDays in SlideshowSpeeds.recentWindows) {
                FavWindow.fromRecentDays(recentDays)
            } else {
                FavWindow.ALL
            }
        }
        else -> FavWindow.decode(raw)
    }

    private fun encodeFileTypes(map: Map<String, Boolean>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeFileTypes(raw: String?): Map<String, Boolean> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            part.substring(0, idx) to (part.substring(idx + 1).toBooleanStrictOrNull() ?: true)
        }.toMap()
    }

    private fun encodeCounts(map: Map<String, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeCounts(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val n = part.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
            part.substring(0, idx) to n
        }.toMap()
    }

    private fun encodeHiddenFolders(map: Map<String, Boolean>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeHiddenFolders(raw: String?): Map<String, Boolean> {
        if (raw.isNullOrBlank()) return AppSettings.defaultHiddenFolders()
        return raw.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            part.substring(0, idx) to (part.substring(idx + 1).toBooleanStrictOrNull() ?: false)
        }.toMap().ifEmpty { AppSettings.defaultHiddenFolders() }
    }
}

/**
 * Settings export / import format, built on Android's own org.json so values are escaped
 * properly (the hand-rolled writer broke on a folder name containing a quote).
 *
 * Import MERGES: [merge] changes only what the file mentions. The old decoder rebuilt settings
 * from the file alone, so importing reset folders, file types, hidden folders and tabs to
 * their defaults, and a file with no favourites list wiped every favourite.
 */
internal object SettingsJson {
    /** Marks a file as ours. Files exported before the marker existed pass on their keys. */
    private const val FORMAT = "windfall-settings"
    private val KNOWN_KEYS = setOf(
        "theme", "amoled", "accent", "columns", "gridMode", "selectedFolders", "speedIdx",
        "customMs", "recentWindow", "favWindow", "recentWindowEnc", "haptics",
        "thumbnailPadding", "disableDeleteOptions", "favIds",
    )

    fun encode(s: AppSettings): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", 1)
        put("theme", s.themeMode.name.lowercase())
        put("amoled", s.amoled)
        put("accent", s.accent.key)
        put("columns", s.columns)
        put("gridMode", s.gridMode.name.lowercase())
        put("selectedFolders", JSONArray(s.selectedFolders.sorted()))
        put("speedIdx", s.speedIdx)
        put("customMs", s.customMs)
        put("recentWindow", s.recentWindowDays)
        put("favWindow", s.favWindow.encode())
        put("recentWindowEnc", s.recentWindow.encode())
        put("haptics", s.hapticsEnabled)
        put("thumbnailPadding", s.thumbnailPadding)
        put("disableDeleteOptions", s.disableDeleteOptions)
        put("favIds", JSONArray(s.favIds.sorted()))
    }.toString(2)

    /** [base] with the file's settings applied, or null if [json] isn't a settings file. */
    fun merge(base: AppSettings, json: String): AppSettings? {
        val o = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val ours = o.optString("format") == FORMAT || o.keys().asSequence().any { it in KNOWN_KEYS }
        if (!ours) return null

        fun present(key: String) = o.has(key) && !o.isNull(key)
        fun string(key: String): String? = if (present(key)) o.optString(key) else null
        fun bool(key: String): Boolean? = if (present(key)) o.opt(key) as? Boolean else null
        fun number(key: String): Number? = if (present(key)) o.opt(key) as? Number else null
        fun strings(key: String): Set<String>? = (o.opt(key) as? JSONArray)?.let { array ->
            (0 until array.length()).mapNotNull { array.opt(it) as? String }.filter { it.isNotBlank() }.toSet()
        }

        val recentWindow = string("recentWindowEnc")?.let(FavWindow::decode)
            ?: number("recentWindow")?.toInt()?.let(FavWindow::fromRecentDays)
        val deletesOff = bool("disableDeleteOptions")
        return base.copy(
            themeMode = when (string("theme")) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> base.themeMode
            },
            amoled = bool("amoled") ?: base.amoled,
            accent = string("accent")?.let(AccentColor::fromKey) ?: base.accent,
            columns = number("columns")?.toInt()?.coerceIn(1, 6) ?: base.columns,
            gridMode = when (string("gridMode")) {
                "scroll" -> GridMode.SCROLL
                "swipe" -> GridMode.SWIPE
                else -> base.gridMode
            },
            selectedFolders = strings("selectedFolders") ?: base.selectedFolders,
            speedIdx = number("speedIdx")?.toInt()?.coerceIn(0, SlideshowSpeeds.speeds.lastIndex)
                ?: base.speedIdx,
            customMs = number("customMs")?.toLong()?.coerceIn(1_000L, 3_600_000L) ?: base.customMs,
            recentWindow = recentWindow?.let(FavWindow::normalize) ?: base.recentWindow,
            recentWindowDays = recentWindow?.let { it.asRecentDays() ?: 365 } ?: base.recentWindowDays,
            favWindow = string("favWindow")?.let { FavWindow.normalize(FavWindow.decode(it)) }
                ?: base.favWindow,
            hapticsEnabled = bool("haptics") ?: base.hapticsEnabled,
            thumbnailPadding = bool("thumbnailPadding") ?: base.thumbnailPadding,
            // The two delete toggles are kept in step, as the Settings screen does.
            disableDeleteOptions = deletesOff ?: base.disableDeleteOptions,
            disableEditDelete = deletesOff ?: base.disableEditDelete,
            // Adding is safe; an import never takes a favourite away.
            favIds = base.favIds + (strings("favIds") ?: emptySet()),
        ).sanitized()
    }
}
