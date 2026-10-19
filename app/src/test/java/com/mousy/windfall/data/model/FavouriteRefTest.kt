package com.mousy.windfall.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouriteRefTest {

    @Test
    fun `a reference survives encode and decode`() {
        val ref = FavouriteRef("dcim/camera", "IMG_0001.jpg", 2_345_678L)
        assertEquals(ref, FavouriteRef.decode(ref.encode()))
    }

    @Test
    fun `the same folder written differently gives the same key`() {
        val a = FavouriteRef("DCIM/Camera/", "IMG_0001.jpg", 10L)
        val b = FavouriteRef("/dcim/camera", "IMG_0001.jpg", 10L)
        assertEquals(a.encode(), b.encode())
    }

    @Test
    fun `same name with a different size or folder is a different file`() {
        val base = FavouriteRef("DCIM/Camera", "IMG_0001.jpg", 10L)
        assertNotEquals(base.encode(), base.copy(sizeBytes = 11L).encode())
        assertNotEquals(base.encode(), base.copy(folder = "Pictures").encode())
        // File names stay case-sensitive: IMG and img can be two files.
        assertNotEquals(base.encode(), base.copy(name = "img_0001.jpg").encode())
    }

    @Test
    fun `files of unknown size are not matched`() {
        assertFalse(FavouriteRef.isMatchable(FavouriteRef("DCIM", "a.jpg", 0L)))
        assertTrue(FavouriteRef.isMatchable(FavouriteRef("DCIM", "a.jpg", 1L)))
    }

    @Test
    fun `damaged entries decode to null`() {
        assertNull(FavouriteRef.decode(""))
        assertNull(FavouriteRef.decode("not a reference"))
        assertNull(FavouriteRef.decode("abc\u001Fdcim\u001Fa.jpg"))
        assertNull(FavouriteRef.decode("10\u001Fdcim\u001F "))
    }
}
