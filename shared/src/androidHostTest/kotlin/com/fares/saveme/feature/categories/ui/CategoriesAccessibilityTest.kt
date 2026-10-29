package com.fares.saveme.feature.categories.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceList
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorScreen
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorState
import com.fares.saveme.feature.categories.ui.list.CategoriesScreen
import com.fares.saveme.feature.categories.ui.list.CategoriesState
import com.fares.saveme.feature.categories.ui.list.CategoryCaptionUi
import com.fares.saveme.feature.categories.ui.list.CategoryRowUi
import com.fares.saveme.feature.categories.ui.list.CategoryStatusUi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** FR-025, P-08: marks and captions are text, choices are announced, targets are 48dp, nothing clips at 2x font. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class CategoriesAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val list = CategoriesState(
        own = listOf(
            CategoryRowUi("1", "Coffee", "restaurant", "#1F7F8C", CategoryCaptionUi.None, CategoryStatusUi.Waiting, true),
            CategoryRowUi("2", "Gym", "health", "#C62828", CategoryCaptionUi.None, CategoryStatusUi.Changed, true),
            CategoryRowUi("3", "Books", "school", "#283593", CategoryCaptionUi.None, CategoryStatusUi.NotSaved, true),
            CategoryRowUi("4", "Typo", null, null, CategoryCaptionUi.None, CategoryStatusUi.NotDeleted, true),
        ),
        defaults = listOf(CategoryRowUi("d1", "Groceries", "cart", "#2E7D32", CategoryCaptionUi.Default, null, false)),
        archived = listOf(CategoryRowUi("5", "Old", "ticket", "#7B1FA2", CategoryCaptionUi.Archived, null, true)),
        loaded = true,
    )

    @Test
    fun syncMarksDefaultAndArchivedAreText() {
        compose.setContent { AppTheme { CategoriesScreen(list, {}) } }
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Changed on another device.").assertIsDisplayed()
        compose.onNodeWithText("Not saved.").assertIsDisplayed()
        compose.onNodeWithText("Not deleted: this category is in use.").assertIsDisplayed()
        compose.onNodeWithText("Defaults").assertIsDisplayed()
        compose.onAllNodes(androidx.compose.ui.test.hasText("Archived")).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun editorChoicesAreAnnouncedAndEveryTargetIsAtLeast48dp() {
        compose.setContent { AppTheme { CategoryEditorScreen(CategoryEditorState(loaded = true, iconKey = "cart", colorHex = "#1F7F8C"), {}) } }
        fun assertAnnounced(vararg labels: String) = labels.forEach { label ->
            assertTrue("no choice announced as $label", compose.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().isNotEmpty())
        }
        assertAnnounced("Cart", "Restaurant", "Refund", "Lightning bolt")
        assertTouchTargets()
        compose.onNodeWithTag("categoryEditor.tab.color").performClick()
        assertAnnounced("Blue", "Green", "Slate", "None")
        assertTouchTargets()
    }

    @Test
    fun listTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { CategoriesScreen(list, {}) } }
        assertTouchTargets()
    }

    @Test
    fun choiceListTargetsAreAtLeast48dp() {
        compose.setContent {
            AppTheme {
                CategoryChoiceList(
                    listOf(CategoryChoiceGroup("Your categories", listOf(CategoryChoiceItem("a", "Coffee", "restaurant", "#1F7F8C")), "categoryChoice.own")),
                    "a", {}, "No categories yet.",
                )
            }
        }
        assertTouchTargets()
    }

    @Test
    fun nothingClipsAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { CategoriesScreen(list, {}) } }
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Changed on another device.").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun editorStaysUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { CategoryEditorScreen(CategoryEditorState(loaded = true, name = "Coffee"), {}) } }
        compose.onNodeWithText("Create category").assertIsDisplayed()
        assertTouchTargets()
    }

    private fun assertTouchTargets() {
        val minPx = with(compose.density) { 48.dp.toPx() }
        val nodes = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected clickable nodes", nodes.isNotEmpty())
        nodes.forEach { node ->
            val bounds = node.touchBoundsInRoot
            assertTrue("touch width ${bounds.width} < $minPx for ${node.config}", bounds.width >= minPx - 0.5f)
            assertTrue("touch height ${bounds.height} < $minPx for ${node.config}", bounds.height >= minPx - 0.5f)
        }
    }
}
