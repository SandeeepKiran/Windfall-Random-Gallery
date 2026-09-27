package com.mousy.windfall.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageWindowTest {

    @Test
    fun `next moves one page forward`() {
        assertEquals(24, PageWindow.next(0, 100, 24))
    }

    @Test
    fun `next from the last page wraps to page 1`() {
        assertEquals(0, PageWindow.next(96, 100, 24))
    }

    @Test
    fun `previous moves one page back`() {
        assertEquals(24, PageWindow.previous(48, 100, 24))
    }

    @Test
    fun `previous from part-way stops at the start`() {
        assertEquals(0, PageWindow.previous(10, 100, 24))
    }

    @Test
    fun `previous from page 1 wraps to the tail`() {
        assertEquals(76, PageWindow.previous(0, 100, 24))
    }

    @Test
    fun `a single page has no neighbours`() {
        assertNull(PageWindow.next(0, 24, 24))
        assertNull(PageWindow.previous(0, 24, 24))
        assertNull(PageWindow.next(0, 5, 24))
    }

    @Test
    fun `zero capacity is treated as one`() {
        assertEquals(1, PageWindow.next(0, 3, 0))
    }
}
