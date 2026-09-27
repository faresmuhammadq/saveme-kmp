package com.fares.saveme.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing steps and layout constants (spec FR-004). */
@Immutable
data class Spacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val s: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val gutter: Dp = 16.dp,
    val sectionGap: Dp = 24.dp,
)

@Immutable
data class AppShapes(
    val control: RoundedCornerShape = RoundedCornerShape(10.dp),
    val tile: RoundedCornerShape = RoundedCornerShape(14.dp),
    val card: RoundedCornerShape = RoundedCornerShape(18.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    val hero: RoundedCornerShape = RoundedCornerShape(22.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(50),
)

/** One drop shadow: offset y, blur, colour. Raised stacks two. */
@Immutable
data class Shadow(val offsetY: Dp, val blur: Dp, val color: Color)

@Immutable
data class Elevation(
    val flat: List<Shadow> = emptyList(),
    val raised: List<Shadow> = listOf(
        Shadow(1.dp, 2.dp, Color(0x0D141E37)),
        Shadow(1.dp, 3.dp, Color(0x0F141E37)),
    ),
    val overlay: List<Shadow> = listOf(Shadow(12.dp, 32.dp, Color(0x24141E37))),
)

/** Component sizes; every touch target is at least [touchTarget] (FR-005, P-08.1). */
@Immutable
data class Sizes(
    val touchTarget: Dp = 48.dp,
    val button: Dp = 52.dp,
    val field: Dp = 52.dp,
    val fab: Dp = 56.dp,
    val topBar: Dp = 64.dp,
    val row: Dp = 64.dp,
    val rowTwoLine: Dp = 72.dp,
    val navBar: Dp = 80.dp,
    val chip: Dp = 36.dp,
    val navIndicatorWidth: Dp = 56.dp,
    val navIndicatorHeight: Dp = 30.dp,
    val icon: Dp = 24.dp,
    val iconTile: Dp = 40.dp,
)
