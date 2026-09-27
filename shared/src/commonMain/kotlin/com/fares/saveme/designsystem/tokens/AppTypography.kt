package com.fares.saveme.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Type scale from the *Mobile foundations* board, sizes in sp (spec FR-003). */
@Immutable
data class AppTypography(
    val numeralHero: TextStyle,
    val numeralL: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val itemTitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    /** Whether [overline] text should be upper-cased; never in Arabic (P-06.4). */
    val overlineUppercase: Boolean,
)

private const val TABULAR = "tnum"

/**
 * Latin: the board's values. Arabic: line height 1.6, no letter-spacing, no uppercase overline,
 * 800 drops to 700. Numerals always use the Latin face with tabular digits.
 */
fun appTypography(latin: FontFamily, arabic: FontFamily, isArabic: Boolean): AppTypography {
    val text = if (isArabic) arabic else latin
    fun style(size: Int, weight: FontWeight, tracking: Double = 0.0, lineHeight: Double? = null, family: FontFamily = text) =
        TextStyle(
            fontFamily = family,
            fontSize = size.sp,
            fontWeight = if (isArabic && family == arabic && weight == FontWeight.ExtraBold) FontWeight.Bold else weight,
            letterSpacing = if (isArabic && family == arabic) 0.em else tracking.em,
            lineHeight = when {
                isArabic && family == arabic -> (size * 1.6).sp
                lineHeight != null -> (size * lineHeight).sp
                else -> TextStyle.Default.lineHeight
            },
            fontFeatureSettings = TABULAR,
        )
    return AppTypography(
        numeralHero = style(40, FontWeight.ExtraBold, -0.02, 1.05, family = latin),
        numeralL = style(24, FontWeight.ExtraBold, -0.02, family = latin),
        headline = style(26, FontWeight.ExtraBold, -0.02),
        title = style(18, FontWeight.Bold),
        itemTitle = style(16, FontWeight.SemiBold),
        body = style(15, FontWeight.Medium, lineHeight = 1.5),
        label = style(14, FontWeight.SemiBold),
        caption = style(12, FontWeight.SemiBold),
        overline = style(11, FontWeight.Bold, if (isArabic) 0.0 else 0.14),
        overlineUppercase = !isArabic,
    )
}

fun AppTypography.toMaterial(): Typography = Typography(
    displayLarge = numeralHero,
    displayMedium = numeralL,
    headlineLarge = headline,
    headlineMedium = headline,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = itemTitle,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = label,
    labelMedium = caption,
    labelSmall = overline,
)
