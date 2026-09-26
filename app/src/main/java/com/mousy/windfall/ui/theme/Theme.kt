package com.mousy.windfall.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mousy.windfall.data.model.AccentColor
import com.mousy.windfall.data.model.ThemeMode

@Composable
fun WindfallTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    amoled: Boolean = false,
    accent: AccentColor = AccentColor.DEFAULT,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val accentScheme = accentSchemeFor(accent, darkTheme)
    val surfaces = surfaceColors(darkTheme, amoled && darkTheme)

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.copy(
                primary = accentScheme.primary,
                onPrimary = accentScheme.onPrimary,
                primaryContainer = accentScheme.primaryContainer,
                onPrimaryContainer = accentScheme.onPrimaryContainer,
                secondaryContainer = accentScheme.secondaryContainer,
                onSecondaryContainer = accentScheme.onSecondaryContainer,
                surface = surfaces.surface,
                surfaceContainerLow = surfaces.surfaceContainerLow,
                surfaceContainer = surfaces.surfaceContainer,
                surfaceContainerHigh = surfaces.surfaceContainerHigh,
                surfaceContainerHighest = surfaces.surfaceContainerHighest,
            )
        }
        darkTheme -> darkColorScheme(
            primary = accentScheme.primary,
            onPrimary = accentScheme.onPrimary,
            primaryContainer = accentScheme.primaryContainer,
            onPrimaryContainer = accentScheme.onPrimaryContainer,
            secondaryContainer = accentScheme.secondaryContainer,
            onSecondaryContainer = accentScheme.onSecondaryContainer,
            surface = surfaces.surface,
            surfaceContainerLow = surfaces.surfaceContainerLow,
            surfaceContainer = surfaces.surfaceContainer,
            surfaceContainerHigh = surfaces.surfaceContainerHigh,
            surfaceContainerHighest = surfaces.surfaceContainerHighest,
            onSurface = Color(0xFFE6E1E6),
            onSurfaceVariant = Color(0xFFCAC4CF),
            outline = Color(0xFF948F99),
            outlineVariant = Color(0xFF48454E),
            error = Color(0xFFFFB4AB),
        )
        else -> lightColorScheme(
            primary = accentScheme.primary,
            onPrimary = accentScheme.onPrimary,
            primaryContainer = accentScheme.primaryContainer,
            onPrimaryContainer = accentScheme.onPrimaryContainer,
            secondaryContainer = accentScheme.secondaryContainer,
            onSecondaryContainer = accentScheme.onSecondaryContainer,
            surface = surfaces.surface,
            surfaceContainerLow = surfaces.surfaceContainerLow,
            surfaceContainer = surfaces.surfaceContainer,
            surfaceContainerHigh = surfaces.surfaceContainerHigh,
            surfaceContainerHighest = surfaces.surfaceContainerHighest,
            onSurface = Color(0xFF1C1B1E),
            onSurfaceVariant = Color(0xFF48454E),
            outline = Color(0xFF79767D),
            outlineVariant = Color(0xFFC9C5CF),
            error = Color(0xFFBA1A1A),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private data class SurfaceColors(
    val surface: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
)

private fun surfaceColors(dark: Boolean, amoled: Boolean): SurfaceColors = when {
    amoled -> SurfaceColors(
        surface = Color.Black,
        surfaceContainerLow = Color(0xFF080809),
        surfaceContainer = Color(0xFF0E0E10),
        surfaceContainerHigh = Color(0xFF161618),
        surfaceContainerHighest = Color(0xFF202024),
    )
    dark -> SurfaceColors(
        surface = SurfaceDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
    )
    else -> SurfaceColors(
        surface = SurfaceLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
    )
}

private fun accentSchemeFor(accent: AccentColor, dark: Boolean): AccentScheme = when (accent) {
    AccentColor.ROSE -> AccentPalettes.rose(dark)
    AccentColor.LAVENDER -> AccentPalettes.lavender(dark)
    AccentColor.MINT -> AccentPalettes.mint(dark)
    AccentColor.PEACH -> AccentPalettes.peach(dark)
    AccentColor.SKY -> AccentPalettes.sky(dark)
    AccentColor.SAND -> AccentPalettes.sand(dark)
}
