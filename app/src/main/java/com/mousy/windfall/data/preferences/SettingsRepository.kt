package com.mousy.windfall.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import com.mousy.windfall.data.model.SamplingDefaults
import com.mousy.windfall.data.model.SlideshowSpeeds
import com.mousy.windfall.data.model.TabFeatures
import com.mousy.windfall.data.model.ThemeMode
import com.mousy.windfall.data.model.sanitized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "windfall_settings")

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        settingsFromPrefs(prefs)
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = settingsFromPrefs(prefs)
            val updated = transform(current)
            writePrefs(prefs, updated)
        }
    }

    suspend fun resetToDefaults(keepFavourites: Boolean = true): AppSettings {
        val previous = settingsFlow.first()
        val defaults = AppSettings.defaults().let { d ->
            if (keepFavourites) d.copy(favIds = previous.favIds) else d
        }
        context.dataStore.edit { prefs ->
            prefs.clear()
            writePrefs(prefs, defaults)
        }
        return defaults
    }

    suspend fun exportJson(settings: AppSettings): String = SettingsJson.encode(settings)

    suspend fun importJson(json: String): AppSettings {
        val imported = SettingsJson.decode(json)
        update { imported }
        return imported
    }

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
        prefs[Keys.AVG_VIEWED_PER_SESSION] = updated.avgViewedPerSession
        prefs[Keys.NEXT_LAUNCH_SEED] = updated.nextLaunchSeed
        prefs[Keys.FARM_FINGERPRINT] = updated.farmFingerprint
        prefs[Keys.FARM_POSITION] = updated.farmPosition
        // Purge the pre-seed history blob: it stored up to 40 x 10k keys as one string.
        if (prefs.contains(Keys.LEGACY_SHUFFLE_HISTORY)) prefs.remove(Keys.LEGACY_SHUFFLE_HISTORY)
        if (prefs.contains(Keys.LEGACY_SHUFFLE_HISTORY_INDEX)) prefs.remove(Keys.LEGACY_SHUFFLE_HISTORY_INDEX)
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
            avgViewedPerSession = prefs[Keys.AVG_VIEWED_PER_SESSION]
                ?: SamplingDefaults.INITIAL_AVG_VIEWED,
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
        val AVG_VIEWED_PER_SESSION = floatPreferencesKey("avg_viewed_per_session")
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

private object SettingsJson {
    fun encode(s: AppSettings): String = buildString {
        append("{")
        append("\"theme\":\"${s.themeMode.name.lowercase()}\",")
        append("\"amoled\":${s.amoled},")
        append("\"accent\":\"${s.accent.key}\",")
        append("\"columns\":${s.columns},")
        append("\"gridMode\":\"${s.gridMode.name.lowercase()}\",")
        append("\"selectedFolders\":${s.selectedFolders.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }},")
        append("\"speedIdx\":${s.speedIdx},")
        append("\"customMs\":${s.customMs},")
        append("\"recentWindow\":${s.recentWindowDays},")
        append("\"favWindow\":\"${s.favWindow.encode()}\",")
        append("\"recentWindowEnc\":\"${s.recentWindow.encode()}\",")
        append("\"haptics\":${s.hapticsEnabled},")
        append("\"thumbnailPadding\":${s.thumbnailPadding},")
        append("\"disableDeleteOptions\":${s.disableDeleteOptions},")
        append("\"favIds\":${s.favIds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}")
        append("}")
    }

    fun decode(json: String): AppSettings {
        fun extractString(key: String): String? {
            val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return pattern.find(json)?.groupValues?.getOrNull(1)
        }
        fun extractBool(key: String): Boolean? =
            "\"$key\"\\s*:\\s*(true|false)".toRegex().find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
        fun extractInt(key: String): Int? =
            "\"$key\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull()

        val favWindow = FavWindow.normalize(FavWindow.decode(extractString("favWindow")))
        val recentWindow = FavWindow.normalize(
            FavWindow.decode(extractString("recentWindowEnc"))
                .takeUnless { extractString("recentWindowEnc") == null }
                ?: FavWindow.fromRecentDays(extractInt("recentWindow") ?: 30),
        )

        val favIds = "\"favIds\"\\s*:\\s*\\[([^\\]]*)\\]".toRegex().find(json)?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim().trim('"') }
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()

        return AppSettings(
            themeMode = if (extractString("theme") == "light") ThemeMode.LIGHT else ThemeMode.DARK,
            amoled = extractBool("amoled") ?: false,
            accent = AccentColor.fromKey(extractString("accent") ?: AccentColor.DEFAULT.key),
            columns = extractInt("columns") ?: 3,
            gridMode = if (extractString("gridMode") == "scroll") GridMode.SCROLL else GridMode.SWIPE,
            favIds = favIds,
            speedIdx = extractInt("speedIdx") ?: 2,
            customMs = extractInt("customMs")?.toLong() ?: 8_000L,
            recentWindowDays = extractInt("recentWindow") ?: 30,
            favWindow = favWindow,
            recentWindow = recentWindow,
            hapticsEnabled = extractBool("haptics") ?: true,
            thumbnailPadding = extractBool("thumbnailPadding") ?: true,
            disableDeleteOptions = extractBool("disableDeleteOptions") ?: false,
        )
    }
}
