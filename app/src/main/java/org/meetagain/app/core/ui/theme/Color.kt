package org.meetagain.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The brand-pinned scheme: key roles are brand values, the rest come from the brand blue's tonal palettes.
// Tertiary repeats secondary because the brand has no third hue.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2447D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF001158),
    inversePrimary = Color(0xFFB9C3FF),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF171A2C),
    tertiary = Color(0xFF5A5D72),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE1F9),
    onTertiaryContainer = Color(0xFF171A2C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF10141F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10141F),
    surfaceVariant = Color(0xFFDFE2F2),
    onSurfaceVariant = Color(0xFF434653),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7FC),
    surfaceContainer = Color(0xFFEDF1FA),
    surfaceContainerHigh = Color(0xFFE5E7F8),
    surfaceContainerHighest = Color(0xFFDFE2F2),
    inverseSurface = Color(0xFF2C303C),
    inverseOnSurface = Color(0xFFEEF0FF),
    outline = Color(0xFF737785),
    outlineVariant = Color(0xFFC3C6D5)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF78A2FF),
    onPrimary = Color(0xFF10141F),
    primaryContainer = Color(0xFF0032C3),
    onPrimaryContainer = Color(0xFFDEE1FF),
    inversePrimary = Color(0xFF2447D6),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFFC3C5DD),
    onTertiary = Color(0xFF2C2F42),
    tertiaryContainer = Color(0xFF434659),
    onTertiaryContainer = Color(0xFFDFE1F9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10141F),
    onBackground = Color(0xFFE3E8F2),
    surface = Color(0xFF10141F),
    onSurface = Color(0xFFE3E8F2),
    surfaceVariant = Color(0xFF313441),
    onSurfaceVariant = Color(0xFFC3C6D5),
    surfaceContainerLowest = Color(0xFF0B0E17),
    surfaceContainerLow = Color(0xFF171B27),
    surfaceContainer = Color(0xFF1B1F2B),
    surfaceContainerHigh = Color(0xFF262A36),
    surfaceContainerHighest = Color(0xFF313441),
    inverseSurface = Color(0xFFE3E8F2),
    inverseOnSurface = Color(0xFF2C303C),
    outline = Color(0xFF8D909F),
    outlineVariant = Color(0xFF434653)
)
