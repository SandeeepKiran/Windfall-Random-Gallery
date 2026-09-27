package com.mousy.windfall.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemKeyTest {

    private val safUri = "content://com.android.externalstorage.documents/tree/primary%3APics/document/primary%3APics%2FIMG_0001.jpg"

    @Test
    fun `a key from an added folder gives back its document address`() {
        // The address itself contains underscores; only the first one separates the id.
        assertEquals(safUri, MediaItem.uriOfSafKey("-123_$safUri"))
    }

    @Test
    fun `MediaStore keys are not added-folder keys`() {
        assertNull(MediaItem.uriOfSafKey("456_content://media/external/images/media/456"))
    }

    @Test
    fun `malformed keys are ignored`() {
        assertNull(MediaItem.uriOfSafKey(""))
        assertNull(MediaItem.uriOfSafKey("_x"))
        assertNull(MediaItem.uriOfSafKey("abc_x"))
        assertNull(MediaItem.uriOfSafKey("no-separator"))
    }
}
