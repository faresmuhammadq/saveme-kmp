package com.fares.saveme.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.fares.saveme.designsystem.tokens.AppColors
import com.fares.saveme.designsystem.tokens.AppShapes
import com.fares.saveme.designsystem.tokens.AppTypography
import com.fares.saveme.designsystem.tokens.Elevation
import com.fares.saveme.designsystem.tokens.LocalMoneyColors
import com.fares.saveme.designsystem.tokens.MoneyColors
import com.fares.saveme.designsystem.tokens.Sizes
import com.fares.saveme.designsystem.tokens.Spacing
import com.fares.saveme.designsystem.tokens.appTypography
import com.fares.saveme.designsystem.tokens.arabicFontFamily
import com.fares.saveme.designsystem.tokens.latinFontFamily
import com.fares.saveme.designsystem.tokens.toColorScheme
import com.fares.saveme.designsystem.tokens.toMaterial

val LocalAppColors = staticCompositionLocalOf { AppColors() }
val LocalAppTypography = staticCompositionLocalOf<AppTypography> { error("AppTheme not applied") }
val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalAppShapes = staticCompositionLocalOf { AppShapes() }
val LocalElevation = staticCompositionLocalOf { Elevation() }
val LocalSizes = staticCompositionLocalOf { Sizes() }

/** Picks Arabic type rules when the layout is right-to-left (P-06.4). Light only (FR-009). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = remember { AppColors() }
    val shapes = remember { AppShapes() }
    val latin = latinFontFamily()
    val arabic = arabicFontFamily()
    val isArabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    val typography = remember(latin, arabic, isArabic) { appTypography(latin, arabic, isArabic) }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalMoneyColors provides MoneyColors(),
        LocalAppTypography provides typography,
        LocalSpacing provides Spacing(),
        LocalAppShapes provides shapes,
        LocalElevation provides Elevation(),
        LocalSizes provides Sizes(),
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = typography.toMaterial(),
            shapes = Shapes(
                extraSmall = shapes.control,
                small = shapes.control,
                medium = shapes.tile,
                large = shapes.card,
                extraLarge = shapes.hero,
            ),
            content = content,
        )
    }
}

object AppTheme {
    val colors: AppColors @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val money: MoneyColors @Composable @ReadOnlyComposable get() = LocalMoneyColors.current
    val type: AppTypography @Composable @ReadOnlyComposable get() = LocalAppTypography.current
    val spacing: Spacing @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val shapes: AppShapes @Composable @ReadOnlyComposable get() = LocalAppShapes.current
    val elevation: Elevation @Composable @ReadOnlyComposable get() = LocalElevation.current
    val sizes: Sizes @Composable @ReadOnlyComposable get() = LocalSizes.current
}
