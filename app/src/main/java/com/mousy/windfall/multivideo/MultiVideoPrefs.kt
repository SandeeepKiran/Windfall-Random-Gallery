package com.mousy.windfall.multivideo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** The Videos-screen settings that outlive the app: how many videos, which way, Fit or Fill. */
data class WallSettings(
    val layout: WallLayout = WallLayout.FOUR,
    val rotation: WallRotation = WallRotation.LANDSCAPE,
    val fill: Boolean = false,
) {
    companion object {
        /** A missing or unknown name (saved by an older or newer version) falls back to the default. */
        fun decode(layout: String?, rotation: String?, fill: Boolean?): WallSettings {
            val defaults = WallSettings()
            return WallSettings(
                layout = WallLayout.entries.firstOrNull { it.name == layout } ?: defaults.layout,
                rotation = WallRotation.entries.firstOrNull { it.name == rotation } ?: defaults.rotation,
                fill = fill ?: defaults.fill,
            )
        }
    }
}

fun MultiVideoState.wallSettings() = WallSettings(layout, rotation, fill)

fun MultiVideoState.withWallSettings(settings: WallSettings) =
    copy(layout = settings.layout, rotation = settings.rotation, fill = settings.fill)

// A file of its own, apart from the gallery's settings, so Multi-Video stays self-contained. A
// damaged file starts again from the defaults instead of failing every read.
private val Context.multiVideoStore: DataStore<Preferences> by preferencesDataStore(
    name = "windfall_multivideo",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

internal class MultiVideoPrefs(context: Context) {
    private val store = context.applicationContext.multiVideoStore

    suspend fun load(): WallSettings {
        val prefs = runCatching { store.data.first() }.getOrNull() ?: return WallSettings()
        return WallSettings.decode(prefs[LAYOUT], prefs[ROTATION], prefs[FILL])
    }

    suspend fun save(settings: WallSettings) {
        // A failed write only loses a view preference; never worth crashing for.
        runCatching {
            store.edit { prefs ->
                prefs[LAYOUT] = settings.layout.name
                prefs[ROTATION] = settings.rotation.name
                prefs[FILL] = settings.fill
            }
        }
    }

    private companion object {
        val LAYOUT = stringPreferencesKey("layout")
        val ROTATION = stringPreferencesKey("rotation")
        val FILL = booleanPreferencesKey("fill")
    }
}
