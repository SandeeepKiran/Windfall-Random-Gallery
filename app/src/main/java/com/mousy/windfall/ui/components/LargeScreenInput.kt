package com.mousy.windfall.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Keyboard and mouse input for tablets with a keyboard, Chromebooks and desktop windows.
 * Touch input does not come through here.
 */

/** A wheel or trackpad scroll that comes after this much quiet time turns one more page. */
private const val WHEEL_QUIET_MS = 250L

/** Page direction for a keyboard key: 1 = next, -1 = previous, null = not a page key. */
fun Key.pageDirection(): Int? = when (this) {
    Key.DirectionRight, Key.DirectionDown, Key.PageDown -> 1
    Key.DirectionLeft, Key.DirectionUp, Key.PageUp -> -1
    else -> null
}

/**
 * Turns a mouse wheel or a two-finger trackpad scroll into page turns: down = next (1),
 * up = previous (-1). A trackpad sends a long burst of small scroll events, and a burst must
 * turn only one page. So a scroll turns a page only after [WHEEL_QUIET_MS] with no scroll.
 * The scroll is consumed, so the views under it do not also scroll.
 *
 * [onTurn] is kept from the first call. Give a lambda that reads current state
 * (`rememberUpdatedState`), not one that holds old values.
 */
fun Modifier.onWheelTurn(onTurn: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var lastScrollMs = 0L
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull()
            val dy = change?.scrollDelta?.y ?: 0f
            if (event.type == PointerEventType.Scroll && change != null && dy != 0f) {
                change.consume()
                val now = change.uptimeMillis
                val quiet = now - lastScrollMs >= WHEEL_QUIET_MS
                lastScrollMs = now
                if (quiet) onTurn(if (dy > 0f) 1 else -1)
            }
        }
    }
}
