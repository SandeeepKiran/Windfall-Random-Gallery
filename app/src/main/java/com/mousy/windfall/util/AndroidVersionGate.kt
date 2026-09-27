package com.mousy.windfall.util

import android.os.Build

object AndroidVersionGate {
    val isAndroid16Plus: Boolean get() = Build.VERSION.SDK_INT >= 36
    val isAndroid15Plus: Boolean get() = Build.VERSION.SDK_INT >= 35
    val isAndroid14Plus: Boolean get() = Build.VERSION.SDK_INT >= 34
    val isAndroid13Plus: Boolean get() = Build.VERSION.SDK_INT >= 33
    val isAndroid12Plus: Boolean get() = Build.VERSION.SDK_INT >= 31

    /** Photo Picker is available from API 33; prefer on 16+ with partial access. */
    val supportsPhotoPicker: Boolean get() = isAndroid13Plus

    /** Partial / visual user-selected media access (API 34+). */
    val supportsPartialMediaAccess: Boolean get() = isAndroid14Plus

    /** READ_MEDIA_VISUAL_USER_SELECTED introduced for Android 14+. */
    val supportsVisualUserSelected: Boolean get() = isAndroid14Plus
}
