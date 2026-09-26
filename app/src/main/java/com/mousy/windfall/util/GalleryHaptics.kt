package com.mousy.windfall.util

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Light haptic helpers gated by the Settings toggle (passed in by callers). */
object GalleryHaptics {
    fun tick(view: View, enabled: Boolean) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(view: View, enabled: Boolean) {
        if (!enabled) return
        val type = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(type)
    }

    fun reject(view: View, enabled: Boolean) {
        if (!enabled) return
        val type = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(type)
    }
}

@Composable
fun rememberHapticPerformer(enabled: Boolean): (HapticKind) -> Unit {
    val view = LocalView.current
    return remember(enabled, view) {
        { kind ->
            when (kind) {
                HapticKind.TICK -> GalleryHaptics.tick(view, enabled)
                HapticKind.CONFIRM -> GalleryHaptics.confirm(view, enabled)
                HapticKind.REJECT -> GalleryHaptics.reject(view, enabled)
            }
        }
    }
}

enum class HapticKind { TICK, CONFIRM, REJECT }
