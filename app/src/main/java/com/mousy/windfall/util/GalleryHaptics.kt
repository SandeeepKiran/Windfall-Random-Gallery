package com.mousy.windfall.util

import android.view.HapticFeedbackConstants
import android.view.View

/** Light haptic helpers gated by the Settings toggle (passed in by callers). */
object GalleryHaptics {
    fun tick(view: View, enabled: Boolean) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** CONFIRM exists from API 30, which is this app's minSdk, so no fallback is needed. */
    fun confirm(view: View, enabled: Boolean) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}
