package com.mousy.windfall.data.model

import kotlin.math.roundToInt
import kotlin.random.Random

object SamplingDefaults {
    /** Assumed items-per-session before we've learned anything about this user. */
    const val INITIAL_AVG_VIEWED = 800f
    /** Never prepare fewer than this, even for a user who only ever glances at the app. */
    const val MIN_SAMPLE = 600
    /** Prepare this much headroom above the user's typical session. */
    const val SAMPLE_HEADROOM = 1.5f
    /** Weight of the newest session when folding it into the moving average. */
    const val EMA_ALPHA = 0.2f
    /**
     * Sessions shorter than this don't count. Opening one photo and backing out isn't evidence
     * of how much the user browses, and letting it through dragged the average toward zero.
     */
    const val MIN_SESSION_FOR_AVERAGE = 15
    /** Extend the working set once the user has seen this fraction of it. */
    const val EXTEND_AT_FRACTION = 0.8f

    /** Roughly a 1-in-25 chance that any given gallery slot is a favourite. */
    const val FAVOURITE_RATE = 0.04f

    /**
     * Fallback "one page" size used before the grid has reported its real geometry — sizes the
     * next-launch warm-up batch. Live neighbour-page prefetch uses the measured capacity.
     */
    const val PREFETCH_PAGE = 24

    fun sampleSizeFor(avgViewed: Float, totalCount: Int): Int {
        if (totalCount <= MIN_SAMPLE) return totalCount
        val target = (avgViewed.coerceAtLeast(1f) * SAMPLE_HEADROOM).roundToInt()
        return target.coerceIn(MIN_SAMPLE, totalCount)
    }

    fun updatedAverage(previous: Float, sessionCount: Int): Float {
        if (sessionCount < MIN_SESSION_FOR_AVERAGE) return previous
        val prev = previous.takeIf { it.isFinite() && it > 0f } ?: INITIAL_AVG_VIEWED
        return prev * (1f - EMA_ALPHA) + sessionCount * EMA_ALPHA
    }
}

/**
 * Draws a reproducible random slice of [keys] from a single [seed].
 *
 * This is a partial Fisher-Yates: only [count] swaps are performed, so taking 1,000 of
 * 10,000 keys costs 1,000 swaps instead of shuffling the whole library. Because the
 * shuffle is driven purely by the seed, asking for a larger [count] later returns the
 * same items in the same order plus the next ones — which is what makes "load more"
 * repeat-free and what lets swipe-back history store one Long per page.
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
