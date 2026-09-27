package com.mousy.windfall.data.model

import kotlin.random.Random

/**
 * One list's random order, kept stable for as long as its seed stays the same.
 *
 * WHY THIS EXISTS: the pool of media changes under the user all the time. The cold-start scan
 * finds photos taken since the last session, Undo brings a hidden item back, a folder or a file
 * type gets switched on. The order used to be recomputed from (seed + whole pool), and a shuffle
 * of a different pool is a different shuffle, so any of those events re-dealt EVERY page,
 * including pages already seen. Swiping back then showed a page the user had never seen.
 *
 * A deck is dealt once per seed and afterwards only reconciled:
 *  - Items that leave the pool are skipped but keep their place, so an Undo puts them back
 *    exactly where they were.
 *  - Items new to the deck are slotted in at random positions after `frontier`, the part of
 *    the order the user hasn't reached yet, so nothing already seen moves.
 *
 * Not thread-safe: each deck belongs to the one pipeline that arranges its list.
 */
class ShuffleDeck<T>(private val keyOf: (T) -> String) {

    private var dealtSeed: Long? = null

    /** Every key this deal has placed, in order, including keys that are currently absent. */
    private var order: List<String> = emptyList()
    private var placed: Set<String> = emptySet()

    /** Returned again when nothing changed, so observers of the list see the same instance. */
    private var lastResult: List<T> = emptyList()

    /**
     * Returns [pool] in this deck's order.
     *
     * @param frontier how many leading items the user may already have seen; newcomers are only
     *   ever placed after that many present items.
     * @param deal produces a fresh random order of the pool; used for a new [seed], or when
     *   nothing of the old deal is left to preserve.
     */
    fun arrange(pool: List<T>, seed: Long, frontier: Int, deal: (List<T>) -> List<T>): List<T> {
        val byKey = HashMap<String, T>(pool.size * 2)
        for (item in pool) byKey[keyOf(item)] = item

        if (seed != dealtSeed || order.none { it in byKey }) {
            val dealt = deal(pool)
            dealtSeed = seed
            order = dealt.map(keyOf)
            placed = order.toHashSet()
            return remember(dealt)
        }

        val newcomers = pool.map(keyOf).filter { it !in placed }
        if (newcomers.isNotEmpty()) slotIntoFuture(newcomers, frontier, byKey, seed)
        return remember(order.mapNotNull { byKey[it] })
    }

    /** Inserts [newKeys] at random positions after the first [frontier] present items. */
    private fun slotIntoFuture(
        newKeys: List<String>,
        frontier: Int,
        present: Map<String, T>,
        seed: Long,
    ) {
        // Index in `order` where the unseen part starts: just past the frontier-th present item.
        var futureStart = if (frontier <= 0) 0 else order.size
        if (frontier > 0) {
            var seen = 0
            for (i in order.indices) {
                if (order[i] in present && ++seen == frontier) {
                    futureStart = i + 1
                    break
                }
            }
        }

        // Seeded from the deck's state, so the same situation always settles the same way.
        val random = Random(seed xor (order.size.toLong() shl 32) xor newKeys.size.toLong())
        // Shuffle first: newcomers arrive in scan order (newest first), and newcomers sharing a
        // gap would otherwise sit in date order instead of random order.
        val gaps = order.size - futureStart + 1
        val slotted = newKeys.shuffled(random)
            .map { key -> random.nextInt(gaps) to key }
            .sortedBy { it.first } // stable sort, so the shuffle survives within a gap

        val merged = ArrayList<String>(order.size + newKeys.size)
        for (i in 0 until futureStart) merged += order[i]
        var next = 0
        for (gap in 0 until gaps) {
            while (next < slotted.size && slotted[next].first == gap) merged += slotted[next++].second
            if (gap < gaps - 1) merged += order[futureStart + gap]
        }
        order = merged
        placed = placed + newKeys
    }

    private fun remember(result: List<T>): List<T> {
        val unchanged = result.size == lastResult.size &&
            result.indices.all { result[it] === lastResult[it] }
        if (!unchanged) lastResult = result
        return lastResult
    }
}
