package com.mousy.windfall.data.model

import kotlin.random.Random

/*
 * The gallery used to deal only an adaptive slice of the library (sized from a moving average
 * of items viewed per session) and widen it as the user browsed. That existed because dealing
 * was once expensive; a full seeded deal of 10k items is now well under a millisecond, and a
 * slice that grows mid-session fought [ShuffleDeck]'s promise that seen pages never move. The
 * gallery now deals the whole library once per shuffle.
 */
object SamplingDefaults {
    /** Roughly a 1-in-25 chance that any given gallery slot is a favourite. */
    const val FAVOURITE_RATE = 0.04f

    /**
     * Fallback "one page" size used before the grid has reported its real geometry — sizes the
     * next-launch warm-up batch. Live neighbour-page prefetch uses the measured capacity.
     */
    const val PREFETCH_PAGE = 24
}

/**
 * Draws a reproducible random slice of [keys] from a single [seed].
 *
 * This is a partial Fisher-Yates: only [count] swaps are performed, so taking 1,000 of
 * 10,000 keys costs 1,000 swaps instead of shuffling the whole library. Because the
 * shuffle is driven purely by the seed, asking for a larger [count] later returns the
 * same items in the same order plus the next ones. The next-launch warm-up relies on that:
 * it deals one page and gets exactly the first page of the full deal.
 *
 * The order depends on the WHOLE of [items]: add or remove one element and every position
 * changes. Lists that must survive pool changes go through [ShuffleDeck].
 */
fun <T> seededSample(items: List<T>, seed: Long, count: Int): List<T> {
    val total = items.size
    if (total == 0) return emptyList()
    val take = count.coerceIn(1, total)

    val random = Random(seed)
    val order = IntArray(total) { it }
    val result = ArrayList<T>(take)
    for (i in 0 until take) {
        val j = i + random.nextInt(total - i)
        val pick = order[j]
        order[j] = order[i]
        order[i] = pick
        result.add(items[pick])
    }
    return result
}

/**
 * Like [seededSample], but gives [boosted] items a [boostedRate] chance of taking each slot so
 * favourites surface far more often than their share of the library would suggest. Both pools are
 * drawn without replacement, so nothing repeats within a draw, and the whole thing stays
 * reproducible from [seed].
 */
fun <T> seededMixedSample(
    regular: List<T>,
    boosted: List<T>,
    seed: Long,
    count: Int,
    boostedRate: Float,
): List<T> {
    if (boosted.isEmpty()) return seededSample(regular, seed, count)
    if (regular.isEmpty()) return seededSample(boosted, seed, count)

    val total = regular.size + boosted.size
    val take = count.coerceIn(1, total)
    val random = Random(seed)
    val regularOrder = IntArray(regular.size) { it }
    val boostedOrder = IntArray(boosted.size) { it }
    var takenRegular = 0
    var takenBoosted = 0
    val result = ArrayList<T>(take)

    repeat(take) {
        val boostedLeft = takenBoosted < boosted.size
        val regularLeft = takenRegular < regular.size
        val pickBoosted = boostedLeft && (!regularLeft || random.nextFloat() < boostedRate)
        if (pickBoosted) {
            val j = takenBoosted + random.nextInt(boosted.size - takenBoosted)
            val pick = boostedOrder[j]
            boostedOrder[j] = boostedOrder[takenBoosted]
            boostedOrder[takenBoosted] = pick
            result.add(boosted[pick])
            takenBoosted++
        } else if (regularLeft) {
            val j = takenRegular + random.nextInt(regular.size - takenRegular)
            val pick = regularOrder[j]
            regularOrder[j] = regularOrder[takenRegular]
            regularOrder[takenRegular] = pick
            result.add(regular[pick])
            takenRegular++
        }
    }
    return result
}

fun newShuffleSeed(): Long = Random.nextLong()
