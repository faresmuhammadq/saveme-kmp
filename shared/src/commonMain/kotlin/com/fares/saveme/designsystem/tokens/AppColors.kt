package com.fares.saveme.designsystem.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour roles from the *Mobile foundations* board (spec FR-001). Light theme only (FR-009). */
@Immutable
data class AppColors(
    val primary: Color = Color(0xFF4C6FD6),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val primaryContainer: Color = Color(0xFFE9EEFC),
    val onPrimaryContainer: Color = Color(0xFF3553B0),
    val primaryPressed: Color = Color(0xFF3553B0),
    val background: Color = Color(0xFFEDF0F5),
    val surface: Color = Color(0xFFFFFFFF),
    val sunken: Color = Color(0xFFF4F6FA),
    val sunken2: Color = Color(0xFFE9EDF3),
    val outline: Color = Color(0xFF8A93A2),
    val outlineVariant: Color = Color(0xFFE3E7EE),
    val ink: Color = Color(0xFF161A22),
    val ink2: Color = Color(0xFF5B6472),
    val caption: Color = Color(0xFF6F7886),
    val error: Color = Color(0xFFA9503A),
    val errorContainer: Color = Color(0xFFF7E9E3),
)

fun AppColors.toColorScheme(): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = primary,
    onSecondary = onPrimary,
    secondaryContainer = primaryContainer,
    onSecondaryContainer = onPrimaryContainer,
    background = background,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    surfaceVariant = sunken,
    onSurfaceVariant = ink2,
    surfaceContainerLowest = surface,
    surfaceContainerLow = surface,
    surfaceContainer = sunken,
    surfaceContainerHigh = sunken2,
    surfaceContainerHighest = sunken2,
    outline = outline,
    outlineVariant = outlineVariant,
    error = error,
    onError = onPrimary,
    errorContainer = errorContainer,
    onErrorContainer = error,
    scrim = ink,
)
