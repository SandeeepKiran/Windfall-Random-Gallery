package com.mousy.windfall.ui.theme

import androidx.compose.ui.graphics.Color

val FavouriteHeart = Color(0xFFF38282)

// Surface tokens from wireframe CSS
val SurfaceDark = Color(0xFF141316)
val SurfaceContainerLowDark = Color(0xFF1C1B1E)
val SurfaceContainerDark = Color(0xFF211F23)
val SurfaceContainerHighDark = Color(0xFF2B292E)
val SurfaceContainerHighestDark = Color(0xFF363239)

val SurfaceLight = Color(0xFFFDFBFF)
val SurfaceContainerLowLight = Color(0xFFF5F0F7)
val SurfaceContainerLight = Color(0xFFEFE9F1)
val SurfaceContainerHighLight = Color(0xFFE9E3EC)
val SurfaceContainerHighestLight = Color(0xFFE3DDE6)

data class AccentScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

object AccentPalettes {
    fun rose(dark: Boolean) = if (dark) {
        AccentScheme(
            primary = Color(0xFFFFB1C7),
            onPrimary = Color(0xFF54182F),
            primaryContainer = Color(0xFF6F3046),
            onPrimaryContainer = Color(0xFFFFD9E2),
            secondaryContainer = Color(0xFF5A3F46),
            onSecondaryContainer = Color(0xFFFFD9E2),
        )
    } else {
        AccentScheme(
            primary = Color(0xFF8C4A60),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD9E2),
            onPrimaryContainer = Color(0xFF3B0721),
            secondaryContainer = Color(0xFFFFD9E2),
            onSecondaryContainer = Color(0xFF2B151C),
        )
    }

    fun lavender(dark: Boolean) = if (dark) {
        AccentScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
        )
    } else {
        AccentScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B),
        )
    }

    fun mint(dark: Boolean) = if (dark) {
        AccentScheme(
            primary = Color(0xFF6FDBA8),
            onPrimary = Color(0xFF00391F),
            primaryContainer = Color(0xFF005230),
            onPrimaryContainer = Color(0xFF8FF8C4),
            secondaryContainer = Color(0xFF3C4B41),
            onSecondaryContainer = Color(0xFFB9CCBE),
        )
    } else {
        AccentScheme(
            primary = Color(0xFF006D43),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF8FF8C4),
            onPrimaryContainer = Color(0xFF00210F),
            secondaryContainer = Color(0xFFD5E8D8),
            onSecondaryContainer = Color(0xFF0F1F13),
        )
    }

    fun peach(dark: Boolean) = if (dark) {
        AccentScheme(
            primary = Color(0xFFFFB68C),
            onPrimary = Color(0xFF532200),
            primaryContainer = Color(0xFF713600),
            onPrimaryContainer = Color(0xFFFFDBC8),
            secondaryContainer = Color(0xFF5A4034),
            onSecondaryContainer = Color(0xFFFFDBC8),
        )
    } else {
        AccentScheme(
            primary = Color(0xFF8F4C00),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDBC8),
            onPrimaryContainer = Color(0xFF2E1500),
            secondaryContainer = Color(0xFFF5DED0),
            onSecondaryContainer = Color(0xFF2B1709),
        )
    }

    fun sky(dark: Boolean) = if (dark) {
        AccentScheme(
            primary = Color(0xFF8CCEFF),
            onPrimary = Color(0xFF00344F),
            primaryContainer = Color(0xFF004B6F),
            onPrimaryContainer = Color(0xFFCBE6FF),
            secondaryContainer = Color(0xFF3C4858),
            onSecondaryContainer = Color(0xFFD3E4F5),
        )
    } else {
        AccentScheme(
            primary = Color(0xFF00639B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCBE6FF),
            onPrimaryContainer = Color(0xFF001D33),
            secondaryContainer = Color(0xFFD3E4F5),
            onSecondaryContainer = Color(0xFF0C1D2E),
        )
    }

    fun sand(dark: Boolean) = if (dark) {
        AccentScheme(
            primary = Color(0xFFE4C36C),
            onPrimary = Color(0xFF3D2F00),
            primaryContainer = Color(0xFF584400),
            onPrimaryContainer = Color(0xFFFFE08C),
            secondaryContainer = Color(0xFF4E4739),
            onSecondaryContainer = Color(0xFFEFE0C0),
        )
    } else {
        AccentScheme(
            primary = Color(0xFF6F5D00),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFEE08C),
            onPrimaryContainer = Color(0xFF221B00),
            secondaryContainer = Color(0xFFEAE2CE),
            onSecondaryContainer = Color(0xFF211B08),
        )
    }
}
