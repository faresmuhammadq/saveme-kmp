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

/**
 * One of the eight colours a wallet can be tagged with (spec 006, research R-13). [key] is the
 * uppercase `#RRGGBB` the server stores without interpreting; [swatch] is the colour itself,
 * [tint] draws the icon on the [container] tile. A key not listed here falls back to the default tile.
 */
@Immutable
data class WalletAccent(val key: String, val swatch: Color, val tint: Color, val container: Color)

/** Built from the existing tonal families (primary, positive, pending, error, ink) plus violet, teal and rose. */
object WalletAccents {
    val all: List<WalletAccent> = listOf(
        WalletAccent("#4C6FD6", Color(0xFF4C6FD6), Color(0xFF3553B0), Color(0xFFE9EEFC)),
        WalletAccent("#2C7A5B", Color(0xFF2C7A5B), Color(0xFF1F5C43), Color(0xFFE6F3EC)),
        WalletAccent("#8F5E12", Color(0xFF8F5E12), Color(0xFF7A4F0C), Color(0xFFFBF1DE)),
        WalletAccent("#A9503A", Color(0xFFA9503A), Color(0xFF8A3E2B), Color(0xFFF7E9E3)),
        WalletAccent("#6B52B8", Color(0xFF6B52B8), Color(0xFF53409A), Color(0xFFEEE9F8)),
        WalletAccent("#1F7F8C", Color(0xFF1F7F8C), Color(0xFF175F69), Color(0xFFE2F1F3)),
        WalletAccent("#B04375", Color(0xFFB04375), Color(0xFF8D3360), Color(0xFFF8E6EE)),
        WalletAccent("#5B6472", Color(0xFF5B6472), Color(0xFF414955), Color(0xFFEBEEF3)),
    )

    fun find(key: String?): WalletAccent? = key?.let { k -> all.firstOrNull { it.key.equals(k, ignoreCase = true) } }
}
