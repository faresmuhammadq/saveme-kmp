package com.fares.saveme.designsystem

import androidx.compose.ui.graphics.Color
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.designsystem.tokens.WalletAccents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryColorsTest {
    /** The colours of backend `PlatformCategorySeeder`, in order (26 entries, some repeat). */
    private val platformColors = listOf(
        "#2E7D32", "#EF6C00", "#1565C0", "#455A64", "#6D4C41", "#F9A825", "#00838F", "#C62828", "#283593", "#AD1457",
        "#5D4037", "#8E24AA", "#7B1FA2", "#3949AB", "#0277BD", "#F06292", "#D81B60", "#00897B", "#757575", "#2E7D32",
        "#1B5E20", "#00695C", "#4527A0", "#C2185B", "#0097A7", "#616161",
    )

    @Test
    fun aValidUppercaseColourIsParsed() {
        val color = CategoryColors.of("#1F7F8C")
        assertEquals(Color(0xFF1F7F8C), color.swatch)
        assertTrue(CategoryColors.isValid("#1F7F8C"))
    }

    @Test
    fun lowercaseShortNullAndGarbageShowTheNeutralTile() {
        listOf("#1f7f8c", "#FFF", null, "red", "", "#12345G", "1F7F8C", "#1F7F8C0").forEach {
            assertEquals(CategoryColors.neutral, CategoryColors.of(it), "$it")
            assertFalse(CategoryColors.isValid(it), "$it")
        }
    }

    @Test
    fun theIconTintReachesThreeToOneOnItsContainerForEveryPlatformColourAndAccent() {
        assertEquals(26, platformColors.size)
        (platformColors + WalletAccents.all.map { it.key }).forEach { hex ->
            val color = CategoryColors.of(hex)
            val ratio = CategoryColors.contrast(color.tint, color.container)
            assertTrue(ratio >= 3.0, "$hex: $ratio")
        }
        val neutral = CategoryColors.neutral
        assertTrue(CategoryColors.contrast(neutral.tint, neutral.container) >= 3.0)
    }

    @Test
    fun aLightColourIsDarkenedAndADarkOneIsKept() {
        val yellow = CategoryColors.of("#F9A825")
        assertTrue(yellow.tint != yellow.swatch)
        val green = CategoryColors.of("#1B5E20")
        assertEquals(green.swatch, green.tint)
    }
}
