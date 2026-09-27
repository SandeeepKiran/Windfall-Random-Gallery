package com.mousy.windfall.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomSampleTest {

    private val pool = (1..500).map { "k$it" }

    @Test
    fun `the same seed gives the same order`() {
        assertEquals(seededSample(pool, 42, 100), seededSample(pool, 42, 100))
    }

    @Test
    fun `different seeds give different orders`() {
        assertNotEquals(seededSample(pool, 1, 100), seededSample(pool, 2, 100))
    }

    @Test
    fun `asking for more keeps the first items as they were`() {
        // The next-launch warm-up deals one page and relies on it being the full deal's page 1.
        assertEquals(seededSample(pool, 42, 50), seededSample(pool, 42, 300).take(50))
    }

    @Test
    fun `a full deal holds every item exactly once`() {
        val all = seededSample(pool, 9, pool.size)
        assertEquals(pool.size, all.size)
        assertEquals(pool.toSet(), all.toSet())
    }

    @Test
    fun `an empty pool deals nothing`() {
        assertTrue(seededSample(emptyList<String>(), 1, 10).isEmpty())
    }

    @Test
    fun `the order depends on the whole pool`() {
        // Why ShuffleDeck exists: one extra item re-deals every position of a plain shuffle.
        assertNotEquals(seededSample(pool, 42, 100), seededSample(pool + "extra", 42, 100))
    }

    @Test
    fun `mixed sample asking for more keeps the first items`() {
        val regular = (1..400).map { "r$it" }
        val boosted = (1..40).map { "b$it" }
        val small = seededMixedSample(regular, boosted, 5, 60, SamplingDefaults.FAVOURITE_RATE)
        val big = seededMixedSample(regular, boosted, 5, 300, SamplingDefaults.FAVOURITE_RATE)
        assertEquals(small, big.take(60))
    }

    @Test
    fun `mixed full deal holds every item exactly once`() {
        val regular = (1..400).map { "r$it" }
        val boosted = (1..40).map { "b$it" }
        val all = seededMixedSample(regular, boosted, 5, 440, SamplingDefaults.FAVOURITE_RATE)
        assertEquals(440, all.size)
        assertEquals((regular + boosted).toSet(), all.toSet())
    }

    @Test
    fun `favourites surface far more often than their share`() {
        val regular = (1..5000).map { "r$it" }
        val boosted = (1..50).map { "b$it" } // 1% of the pool
        val firstThousand = seededMixedSample(regular, boosted, 77, 1000, SamplingDefaults.FAVOURITE_RATE)
        val favourites = firstThousand.count { it.startsWith("b") }
        // About 40 expected at the 4% boost; their 1% share alone would give about 10.
        assertTrue("got $favourites favourites", favourites in 20..60)
    }
}
