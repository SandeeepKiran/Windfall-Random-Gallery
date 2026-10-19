package com.mousy.windfall.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogMaskingTest {

    @Test
    fun `file names are hidden but the extension stays`() {
        val masked = LogMasking.mask("E/Player: Unable to decode IMG_1234.HEIC and my holiday.mp4")
        assertFalse(masked.contains("IMG_1234"))
        assertFalse(masked.contains("holiday"))
        assertFalse(masked.contains(" my "))
        assertTrue(masked.contains("….HEIC"))
        assertTrue(masked.contains("….mp4"))
    }

    @Test
    fun `relative media folders keep only the top folder, spaces included`() {
        assertEquals("open DCIM/…", LogMasking.mask("open DCIM/Camera/2026/ failed"))
        assertEquals("scan Pictures/…", LogMasking.mask("scan Pictures/Private Stuff"))
        assertEquals("file \"Movies/…\" missing", LogMasking.mask("file \"Movies/Our Trip/a b.mp4\" missing"))
    }

    @Test
    fun `storage volume document ids are hidden`() {
        assertEquals("doc primary:…", LogMasking.mask("doc primary:Pictures/Private Stuff"))
        assertEquals("doc 1A2B-3C4D:…", LogMasking.mask("doc 1A2B-3C4D:DCIM/Trip"))
    }

    @Test
    fun `addresses and paths with spaces are hidden`() {
        val line = "uri=content://media/external/images/media/42 path=/storage/emulated/0/DCIM/Ex Trip/our day.mp4"
        assertEquals("uri=content://… path=/storage/…", LogMasking.mask(line))
    }

    @Test
    fun `emails are hidden`() {
        assertEquals("account …@… signed in", LogMasking.mask("account someone@example.com signed in"))
    }

    @Test
    fun `class names in stack traces are not changed`() {
        val trace = "Caused by: org.json.JSONException at java.util.zip.ZipInputStream.getNextEntry " +
            "OMX.google.raw.decoder"
        assertEquals(trace, LogMasking.mask(trace))
    }

    @Test
    fun `ordinary log text is not changed`() {
        val line = "W/GalleryVM: Saving settings failed java.io.IOException: No space left (Foo.kt:42)"
        assertEquals(line, LogMasking.mask(line))
    }
}
