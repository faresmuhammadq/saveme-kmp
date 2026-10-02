package com.fares.saveme.designsystem

import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.CategoryIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class CategoryIconsTest {
    /** The icon keys of backend `PlatformCategorySeeder`, deduplicated and sorted. */
    private val seederKeys = listOf(
        "cart", "restaurant", "bus", "fuel", "home", "bolt", "phone", "health", "school", "shirt", "sofa", "care", "ticket",
        "repeat", "plane", "child", "gift", "heart", "dots", "wallet", "store", "laptop", "chart", "undo",
    )

    @Test
    fun theKeyListEqualsTheSeedersIconKeys() {
        assertEquals(24, CategoryIcons.keys.size)
        assertEquals(seederKeys.sorted(), CategoryIcons.keys.sorted())
    }

    @Test
    fun everyKeyResolves() {
        CategoryIcons.keys.forEach { assertNotNull(CategoryIcons.find(it), it) }
    }

    @Test
    fun unknownAndNullFallBackToTheNeutralIcon() {
        assertNull(CategoryIcons.find("rocket"))
        assertSame(AppIcons.More, CategoryIcons.of("rocket"))
        assertSame(AppIcons.More, CategoryIcons.of(null))
    }

    @Test
    fun noIconIsNamedWithAGlossaryBannedWord() {
        val banned = listOf("tag", "label", "bucket", "record", "entry")
        CategoryIcons.keys.forEach { key -> CategoryIcons.of(key).name.lowercase().let { name -> banned.forEach { assertEquals(false, name.contains(it), "$name") } } }
    }
}
