package com.mousy.windfall.data.preferences

import com.mousy.windfall.data.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsJsonTest {

    private val current = AppSettings(
        selectedFolders = setOf("DCIM/Camera", "Pictures"),
        fileTypes = mapOf("jpg" to true, "heic" to false),
        hiddenFolders = mapOf("Private" to false),
        favIds = setOf("1_a", "2_b"),
        columns = 4,
    )

    @Test
    fun `exporting then importing changes nothing`() {
        assertEquals(current, SettingsJson.merge(current, SettingsJson.encode(current)))
    }

    @Test
    fun `an import keeps everything the file doesn't mention`() {
        val merged = SettingsJson.merge(current, """{"columns": 2}""")!!
        assertEquals(2, merged.columns)
        assertEquals(current.selectedFolders, merged.selectedFolders)
        assertEquals(current.fileTypes, merged.fileTypes)
        assertEquals(current.hiddenFolders, merged.hiddenFolders)
        assertEquals(current.favIds, merged.favIds)
    }

    @Test
    fun `an import adds favourites and never removes them`() {
        val merged = SettingsJson.merge(current, """{"favIds": ["3_c"]}""")!!
        assertEquals(setOf("1_a", "2_b", "3_c"), merged.favIds)
    }

    @Test
    fun `an import brings back the folder selection`() {
        val merged = SettingsJson.merge(AppSettings(), SettingsJson.encode(current))!!
        assertEquals(current.selectedFolders, merged.selectedFolders)
    }

    @Test
    fun `files that aren't settings are refused`() {
        assertNull(SettingsJson.merge(current, "hello"))
        assertNull(SettingsJson.merge(current, "[1, 2, 3]"))
        assertNull(SettingsJson.merge(current, """{"name": "shopping list"}"""))
    }

    @Test
    fun `quotes in a folder name survive the round trip`() {
        val tricky = current.copy(selectedFolders = setOf("Pictures/My \"best\" shots"))
        val merged = SettingsJson.merge(AppSettings(), SettingsJson.encode(tricky))!!
        assertEquals(tricky.selectedFolders, merged.selectedFolders)
    }

    @Test
    fun `numbers out of range are clamped`() {
        val merged = SettingsJson.merge(current, """{"columns": 99, "speedIdx": -5, "customMs": 5}""")!!
        assertEquals(6, merged.columns)
        assertEquals(0, merged.speedIdx)
        assertEquals(1_000L, merged.customMs)
    }

    @Test
    fun `values of the wrong type are ignored`() {
        val merged = SettingsJson.merge(current, """{"columns": "three", "amoled": "yes"}""")!!
        assertEquals(current.columns, merged.columns)
        assertEquals(current.amoled, merged.amoled)
    }
}
