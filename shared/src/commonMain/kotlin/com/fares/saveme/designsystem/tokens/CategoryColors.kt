package com.fares.saveme.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** How one category colour is drawn: the [swatch] itself, a light [container] tile and an icon [tint] on it. */
@Immutable
data class CategoryColor(val swatch: Color, val container: Color, val tint: Color)

/**
 * Turns the `#RRGGBB` string a category carries into a tile (spec 007, research R-13, P-07.2:
 * feature code passes the string and never builds a `Color` from it). The container is the colour
 * at low strength over white; the tint is the colour darkened until it reaches 3:1 against the
 * container (P-08, FR-025). Anything that is not uppercase `#RRGGBB` shows the neutral tile.
 */
object CategoryColors {
    private val hex = Regex("^#[0-9A-F]{6}$")

    val neutral = CategoryColor(Color(0xFF5B6472), Color(0xFFEBEEF3), Color(0xFF414955))

    private const val MIN_CONTRAST = 3.0
    private const val CONTAINER_STRENGTH = 0.14f

    fun isValid(value: String?): Boolean = value != null && hex.matches(value)

    fun of(value: String?): CategoryColor {
        if (!isValid(value)) return neutral
        val swatch = Color(0xFF000000 or value!!.substring(1).toLong(16))
        val container = mix(Color.White, swatch, CONTAINER_STRENGTH)
        var tint = swatch
        var step = 0
        while (contrast(tint, container) < MIN_CONTRAST && step < 20) {
            step++
            tint = mix(swatch, Color.Black, step * 0.05f)
        }
        return CategoryColor(swatch, container, tint)
    }

    /** WCAG contrast ratio of two colours. */
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun mix(from: Color, to: Color, amount: Float) = Color(
        red = from.red + (to.red - from.red) * amount,
        green = from.green + (to.green - from.green) * amount,
        blue = from.blue + (to.blue - from.blue) * amount,
        alpha = 1f,
    )
}
