package com.fares.saveme.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extended status palette, `LocalMoneyColors` on the board (spec FR-002). */
@Immutable
data class MoneyColors(
    val positive: Color = Color(0xFF2C7A5B),
    val positiveContainer: Color = Color(0xFFE6F3EC),
    val pending: Color = Color(0xFF8F5E12),
    val pendingContainer: Color = Color(0xFFFBF1DE),
    val over: Color = Color(0xFFA9503A),
    val overContainer: Color = Color(0xFFF7E9E3),
)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors() }
