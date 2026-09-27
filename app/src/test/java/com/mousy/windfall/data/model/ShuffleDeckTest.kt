package com.mousy.windfall.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promise behind "swiping back shows the page you saw": once dealt, a deck's order only
 * changes where the user hasn't looked yet, whatever happens to the pool of media.
 */
class ShuffleDeckTest {

    private fun deck() = ShuffleDeck<String> { it }

    /** The deal the app uses for Favourites and Recent: a seeded shuffle of the whole pool. */
    private fun deal(seed: Long): (List<String>) -> List<String> = { pool -> seededSample(pool, seed, pool.size) }

    private fun items(range: IntRange) = range.map { "item$it" }

    @Test
    fun `a fresh deck deals with the given deal`() {
        val pool = items(1..50)
        val arranged = deck().arrange(pool, seed = 7, frontier = 0, deal = deal(7))
        assertEquals(seededSample(pool, 7, pool.size), arranged)
    }

    @Test
    fun `nothing changed gives back the very same list`() {
        val deck = deck()
        val pool = items(1..50)
        val first = deck.arrange(pool, 7, 0, deal(7))
        val second = deck.arrange(pool, 7, 0, deal(7))
        assertSame(first, second)
    }

    @Test
    fun `new media never moves anything before the frontier`() {
        val deck = deck()
        val before = deck.arrange(items(1..100), 7, 0, deal(7))
        val after = deck.arrange(items(1..110), 7, frontier = 40, deal = deal(7))
        assertEquals(before.take(40), after.take(40))
        assertEquals(items(1..110).toSet(), after.toSet())
    }

    @Test
    fun `new media keeps the old items in their order`() {
        val deck = deck()
        val before = deck.arrange(items(1..100), 7, 0, deal(7))
        val after = deck.arrange(items(1..130), 7, frontier = 10, deal = deal(7))
        assertEquals(before, after.filter { it in before })
    }

    @Test
    fun `with everything seen, new media goes to the end`() {
        val deck = deck()
        val before = deck.arrange(items(1..30), 7, 0, deal(7))
        val after = deck.arrange(items(1..35), 7, frontier = 30, deal = deal(7))
        assertEquals(before, after.take(30))
        assertEquals(items(31..35).toSet(), after.drop(30).toSet())
    }

    @Test
    fun `new media is mixed through the unseen part, not bunched up`() {
        val deck = deck()
        deck.arrange(items(1..1000), 7, 0, deal(7))
        val after = deck.arrange(items(1..1100), 7, frontier = 50, deal = deal(7))
        val newcomerPositions = after.withIndex()
            .filter { it.value.removePrefix("item").toInt() > 1000 }
            .map { it.index }
        assertTrue(newcomerPositions.all { it >= 50 })
        // Spread out: plenty land in the first half of the unseen part (about 50 expected).
        assertTrue(newcomerPositions.count { it < 550 } > 20)
    }

    @Test
    fun `a removed item leaves without disturbing the rest`() {
        val deck = deck()
        val before = deck.arrange(items(1..50), 7, 0, deal(7))
        val removed = before[10]
        val after = deck.arrange(items(1..50) - removed, 7, 20, deal(7))
        assertEquals(before - removed, after)
    }

    @Test
    fun `an item that comes back (Undo) returns to its old place`() {
        val deck = deck()
        val pool = items(1..50)
        val before = deck.arrange(pool, 7, 0, deal(7))
        val hidden = before[10]
        deck.arrange(pool - hidden, 7, 20, deal(7))
        assertEquals(before, deck.arrange(pool, 7, 20, deal(7)))
    }

    @Test
    fun `a new seed deals again`() {
        val deck = deck()
        val pool = items(1..50)
        val first = deck.arrange(pool, 7, 0, deal(7))
        val second = deck.arrange(pool, 8, 0, deal(8))
        assertEquals(seededSample(pool, 8, pool.size), second)
        assertNotEquals(first, second)
    }

    @Test
    fun `a deck with nothing left to keep deals again`() {
        val deck = deck()
        deck.arrange(emptyList(), 7, 0, deal(7)) // e.g. no cached library at launch
        val pool = items(1..40)
        assertEquals(seededSample(pool, 7, pool.size), deck.arrange(pool, 7, 0, deal(7)))
    }

    @Test
    fun `reconciling is deterministic`() {
        fun run(): List<String> {
            val deck = deck()
            deck.arrange(items(1..200), 3, 0, deal(3))
            return deck.arrange(items(1..260), 3, 30, deal(3))
        }
        assertEquals(run(), run())
    }

    @Test
    fun `nothing is lost or duplicated across a run of changes`() {
        val deck = deck()
        var pool = items(1..300)
        deck.arrange(pool, 11, 0, deal(11))
        pool = pool - items(20..40).toSet() + items(301..350)
        deck.arrange(pool, 11, 60, deal(11))
        pool = pool + items(20..30) - items(301..310).toSet()
        val final = deck.arrange(pool, 11, 90, deal(11))
        assertEquals(pool.size, final.size)
        assertEquals(pool.toSet(), final.toSet())
    }
}
