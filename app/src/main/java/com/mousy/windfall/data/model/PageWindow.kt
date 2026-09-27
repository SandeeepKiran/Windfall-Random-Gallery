package com.mousy.windfall.data.model

/**
 * Where a page swipe lands, for a list of [size] items shown [capacity] at a time.
 *
 * Single source of truth for both sides of a swipe: the grid parks exactly these windows beside
 * the current page, and the ViewModel moves its cursor to exactly the same place. If the two
 * ever disagreed, the page would visibly jump the moment a swipe settled.
 */
object PageWindow {

    /** Forward: the next window; past the end wraps to page 1. Null when there is only one page. */
    fun next(start: Int, size: Int, capacity: Int): Int? {
        val step = capacity.coerceAtLeast(1)
        return when {
            start + step < size -> start + step
            size > step -> 0
            else -> null
        }
    }

    /**
     * Back: the previous window. From page 1 it wraps to the TAIL of the list, which is unseen
     * random content, so going backwards is as endless as going forwards. Null when there is
     * only one page.
     */
    fun previous(start: Int, size: Int, capacity: Int): Int? {
        val step = capacity.coerceAtLeast(1)
        return when {
            start > 0 -> (start - step).coerceAtLeast(0)
            size > step -> size - step
            else -> null
        }
    }
}
