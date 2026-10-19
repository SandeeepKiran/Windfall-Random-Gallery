package com.mousy.windfall.data.preferences

import com.mousy.windfall.data.model.AppSettings
import com.mousy.windfall.data.model.FavouriteRef
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

    @Test
    fun `favourite references survive export and import`() {
        val refs = listOf(
            FavouriteRef("dcim/camera", "IMG_0001.jpg", 1_000L),
            FavouriteRef("pictures/trip", "beach \"one\".png", 2_000L),
        )
        val json = SettingsJson.encode(current, refs.map { null to it })
        assertEquals(refs.toSet(), SettingsJson.favouriteRefs(json).toSet())
        // The references do not change the merged settings themselves.
        assertEquals(current, SettingsJson.merge(current, json))
    }

    @Test
    fun `a favourite with a reference is not imported by its old media id`() {
        // On a new phone, media ID 1 can be a different photo. Only the reference may restore it.
        val oldPhone = AppSettings(favIds = setOf("1_a", "9_z"))
        val json = SettingsJson.encode(oldPhone, listOf("1_a" to FavouriteRef("dcim", "IMG_0001.jpg", 10L)))
        val merged = SettingsJson.merge(AppSettings(), json)!!
        assertEquals(setOf("9_z"), merged.favIds)
        assertEquals(listOf(FavouriteRef("dcim", "IMG_0001.jpg", 10L)), SettingsJson.favouriteRefs(json))
    }

    @Test
    fun `older files and damaged references give no references`() {
        assertEquals(emptyList<FavouriteRef>(), SettingsJson.favouriteRefs("""{"columns": 2}"""))
        assertEquals(emptyList<FavouriteRef>(), SettingsJson.favouriteRefs("not json"))
        // No size, a size of 0, and an entry that is not an object.
        val damaged = """{"favRefs": [{"folder": "dcim", "name": "a.jpg"},""" +
            """ {"folder": "dcim", "name": "b.jpg", "size": 0}, 7]}"""
        assertEquals(emptyList<FavouriteRef>(), SettingsJson.favouriteRefs(damaged))
    }
}
