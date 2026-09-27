package com.mousy.windfall.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavWindowTest {

    @Test
    fun `every option survives encode and decode`() {
        for (window in FavWindow.options) assertEquals(window, FavWindow.decode(window.encode()))
    }

    @Test
    fun `legacy numeric days still decode`() {
        assertEquals(FavWindow.Days(30), FavWindow.decode("30"))
    }

    @Test
    fun `garbage decodes to all time`() {
        assertEquals(FavWindow.ALL, FavWindow.decode("banana"))
        assertEquals(FavWindow.ALL, FavWindow.decode(null))
    }

    @Test
    fun `days are clamped to ten years`() {
        assertEquals(FavWindow.Days(3650), FavWindow.decode("days:99999"))
    }

    @Test
    fun `cycling through every option comes back round`() {
        var window: FavWindow = FavWindow.ALL
        repeat(FavWindow.options.size) { window = FavWindow.cycle(window) }
        assertEquals(FavWindow.ALL, window)
    }

    @Test
    fun `a window includes its last day and nothing older`() {
        assertTrue(FavWindow.Days(7).matches(7))
        assertFalse(FavWindow.Days(7).matches(8))
        assertTrue(FavWindow.ALL.matches(100_000))
    }
}
