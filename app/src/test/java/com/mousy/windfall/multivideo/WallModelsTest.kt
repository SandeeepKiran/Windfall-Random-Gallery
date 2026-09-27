package com.mousy.windfall.multivideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallModelsTest {

    private fun video(name: String) = WallVideo(uri = "content://test/$name", name = name)

    private val a = video("a")
    private val b = video("b")
    private val c = video("c")
    private val d = video("d")

    private fun wallOf(vararg videos: WallVideo, layout: WallLayout = WallLayout.FOUR) =
        MultiVideoState(layout = layout).withVideos(PickerTarget.wholeWall(layout), videos.toList())

    // --- Tiling ---------------------------------------------------------------------------

    @Test
    fun `four videos are two by two whichever way the phone faces`() {
        assertEquals(2 to 2, WallGrid.columnsAndRows(4, wide = true))
        assertEquals(2 to 2, WallGrid.columnsAndRows(4, wide = false))
    }

    @Test
    fun `two videos sit side by side in landscape and stack in portrait`() {
        assertEquals(2 to 1, WallGrid.columnsAndRows(2, wide = true))
        assertEquals(1 to 2, WallGrid.columnsAndRows(2, wide = false))
    }

    @Test
    fun `every layout has exactly one tile per video`() {
        for (layout in WallLayout.entries) {
            for (wide in listOf(true, false)) {
                val (columns, rows) = WallGrid.columnsAndRows(layout.count, wide)
                assertEquals("$layout wide=$wide", layout.count, columns * rows)
            }
        }
    }

    @Test
    fun `videos fill the wall in the order chosen, top-left first`() {
        val state = wallOf(a, b, c, d)
        assertEquals(listOf(a.uri, b.uri, c.uri, d.uri), state.shownSlots.map { it?.uri })
    }

    @Test
    fun `fewer videos than tiles leave the last tiles empty`() {
        val state = wallOf(a, b, c)
        assertEquals(c.uri, state.slots[2]?.uri)
        assertNull(state.slots[3])
        assertTrue(state.hasVideos)
    }

    // --- Sound ----------------------------------------------------------------------------

    @Test
    fun `only the first new video has sound`() {
        val state = wallOf(a, b, c, d)
        assertEquals(listOf(true, false, false, false), (0..3).map { state.audible(it) })
    }

    @Test
    fun `a replaced video keeps quiet when another tile already has sound`() {
        val state = wallOf(a, b).withVideos(PickerTarget.single(1), listOf(c))
        assertTrue(state.audible(0))
        assertFalse(state.audible(1))
    }

    @Test
    fun `replacing the video that had sound passes the sound to the new one`() {
        val state = wallOf(a, b).withVideos(PickerTarget.single(0), listOf(c))
        assertEquals(c.uri, state.slots[0]?.uri)
        assertTrue(state.audible(0))
    }

    @Test
    fun `turning one video's sound on lifts mute all`() {
        val state = wallOf(a, b).copy(allMuted = true).withSoundToggled(1)
        assertFalse(state.allMuted)
        assertTrue(state.audible(1))
    }

    @Test
    fun `mute all silences the wall and brings the same sound back`() {
        val muted = wallOf(a, b).withWallSoundToggled()
        assertFalse(muted.anyAudible)
        val back = muted.withWallSoundToggled()
        assertTrue(back.audible(0))
        assertFalse(back.audible(1))
    }

    @Test
    fun `sound on gives the first video sound when none was set to have it`() {
        val allQuiet = wallOf(a, b).withSoundToggled(0)
        assertFalse(allQuiet.anyAudible)
        assertTrue(allQuiet.withWallSoundToggled().audible(0))
    }

    @Test
    fun `a hidden tile is never heard`() {
        val state = wallOf(a, b).withSoundToggled(1).copy(layout = WallLayout.ONE)
        assertFalse(state.audible(1))
    }

    // --- Changing the wall ----------------------------------------------------------------

    @Test
    fun `choosing the whole wall again clears the old videos`() {
        val state = wallOf(a, b, c, d).withVideos(PickerTarget.wholeWall(WallLayout.FOUR), listOf(b))
        assertEquals(b.uri, state.slots[0]?.uri)
        assertTrue(state.slots.drop(1).all { it == null })
    }

    @Test
    fun `a smaller layout keeps the hidden videos for later`() {
        val state = wallOf(a, b, c, d).copy(layout = WallLayout.TWO)
        assertEquals(2, state.shownSlots.size)
        assertEquals(d.uri, state.copy(layout = WallLayout.FOUR).slots[3]?.uri)
    }

    @Test
    fun `swapping trades two tiles and carries their sound along`() {
        val state = wallOf(a, b).withSlotsSwapped(0, 1)
        assertEquals(listOf(b.uri, a.uri), state.shownSlots.take(2).map { it?.uri })
        assertTrue(state.audible(1))
    }

    @Test
    fun `swapping with an empty tile moves the video there`() {
        val state = wallOf(a).withSlotsSwapped(0, 3)
        assertNull(state.slots[0])
        assertEquals(a.uri, state.slots[3]?.uri)
    }

    @Test
    fun `removing a video empties only its tile`() {
        val state = wallOf(a, b, c).withSlotCleared(1)
        assertNull(state.slots[1])
        assertEquals(listOf(a.uri, null, c.uri), state.shownSlots.take(3).map { it?.uri })
    }

    @Test
    fun `picks past the target's tiles are dropped`() {
        val state = MultiVideoState().withVideos(PickerTarget.single(2), listOf(a, b))
        assertEquals(a.uri, state.slots[2]?.uri)
        assertEquals(1, state.slots.count { it != null })
    }

    @Test
    fun `an empty wall has nothing to start`() {
        assertFalse(MultiVideoState().hasVideos)
        assertFalse(wallOf(a).withSlotCleared(0).hasVideos)
    }

    // --- Saved settings -------------------------------------------------------------------

    @Test
    fun `saved settings come back as saved`() {
        val saved = WallSettings.decode("SIX", "PORTRAIT", true)
        assertEquals(WallSettings(WallLayout.SIX, WallRotation.PORTRAIT, fill = true), saved)
    }

    @Test
    fun `nothing saved yet gives the defaults`() {
        assertEquals(WallSettings(), WallSettings.decode(null, null, null))
    }

    @Test
    fun `a name this version doesn't know falls back to the default`() {
        val saved = WallSettings.decode("NINE", "SIDEWAYS", false)
        assertEquals(WallLayout.FOUR, saved.layout)
        assertEquals(WallRotation.LANDSCAPE, saved.rotation)
    }

    @Test
    fun `applying saved settings keeps the chosen videos`() {
        val state = wallOf(a, b).withWallSettings(WallSettings(WallLayout.TWO, WallRotation.AUTO, fill = true))
        assertEquals(WallLayout.TWO, state.layout)
        assertTrue(state.fill)
        assertEquals(listOf(a.uri, b.uri), state.shownSlots.map { it?.uri })
        assertEquals(state.wallSettings(), WallSettings(WallLayout.TWO, WallRotation.AUTO, fill = true))
    }

    // --- Small helpers --------------------------------------------------------------------

    @Test
    fun `rotation cycles landscape, portrait, auto`() {
        assertEquals(WallRotation.PORTRAIT, WallRotation.LANDSCAPE.next())
        assertEquals(WallRotation.AUTO, WallRotation.PORTRAIT.next())
        assertEquals(WallRotation.LANDSCAPE, WallRotation.AUTO.next())
    }

    @Test
    fun `clock shows minutes and seconds, and hours only when needed`() {
        assertEquals("0:00", WallClock.format(0))
        assertEquals("0:07", WallClock.format(7_400))
        assertEquals("12:34", WallClock.format(754_000))
        assertEquals("1:02:03", WallClock.format(3_723_000))
        assertEquals("0:00", WallClock.format(-5))
    }
}
