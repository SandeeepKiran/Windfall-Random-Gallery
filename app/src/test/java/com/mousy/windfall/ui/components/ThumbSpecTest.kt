package com.mousy.windfall.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThumbSpecTest {

    @Test
    fun `a landscape system thumbnail is scaled so its short side fills the tile`() {
        // What Android typically returns: a 571 x 428 thumbnail for a 4:3 photo.
        assertEquals(342 to 256, ThumbSpec.fillSize(571, 428, 256, 256))
    }

    @Test
    fun `a portrait thumbnail keeps its shape`() {
        assertEquals(256 to 455, ThumbSpec.fillSize(360, 640, 256, 256))
    }

    @Test
    fun `a small thumbnail is scaled up to cover the tile`() {
        assertEquals(341 to 256, ThumbSpec.fillSize(200, 150, 256, 256))
    }

    @Test
    fun `a square thumbnail becomes exactly the tile`() {
        assertEquals(256 to 256, ThumbSpec.fillSize(600, 600, 256, 256))
    }

    @Test
    fun `a broken size falls back to the tile`() {
        assertEquals(256 to 256, ThumbSpec.fillSize(0, 0, 256, 256))
    }

    @Test
    fun `any size covers the tile, with one side matching it`() {
        // The shape Coil's cache check accepts for a FILL request: both sides at least the tile,
        // and the side that governs the scale within a pixel of it.
        val random = Random(1)
        repeat(2_000) {
            val w = random.nextInt(1, 4_000)
            val h = random.nextInt(1, 4_000)
            val box = listOf(128, 192, 256, 384, 512).random(random)
            val (fw, fh) = ThumbSpec.fillSize(w, h, box, box)
            assertTrue("$w x $h -> $fw x $fh for $box", fw >= box && fh >= box)
            assertTrue("$w x $h -> $fw x $fh for $box", minOf(fw - box, fh - box) <= 1)
        }
    }

    @Test
    fun `sizes snap to the shared buckets`() {
        assertEquals(128, ThumbSpec.bucketFor(100))
        assertEquals(128, ThumbSpec.bucketFor(128))
        assertEquals(192, ThumbSpec.bucketFor(129))
        assertEquals(512, ThumbSpec.bucketFor(900))
    }

    @Test
    fun `grid and viewer name the same cached thumbnail`() {
        assertEquals("12_content://x_t256", ThumbSpec.thumbKey("12_content://x", 256))
    }
}
