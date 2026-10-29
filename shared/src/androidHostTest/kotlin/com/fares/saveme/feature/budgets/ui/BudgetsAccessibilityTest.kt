package com.fares.saveme.feature.budgets.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.CategoryChoiceList
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.ui.home.HomeScreen
import com.fares.saveme.feature.auth.ui.home.HomeState
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorScreen
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorState
import com.fares.saveme.feature.budgets.ui.editor.budgetCategoryGroups
import com.fares.saveme.feature.budgets.ui.list.BudgetsScreen
import com.fares.saveme.feature.budgets.ui.list.BudgetsState
import com.fares.saveme.feature.budgets.ui.list.PeriodUi
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** P-08, FR-015: the share and the words are always written, choices are announced, targets are 48dp, nothing clips at 2x font. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class BudgetsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(id: String, name: String?, spent: Long?, limit: Long, status: BudgetStatusUi? = null, waiting: Boolean = false, others: List<String> = emptyList()) =
        BudgetRowUi(id, name, "EGP", limit, spent?.let { RowFigures(it, null, others, waiting) }, false, status)

    private val list = BudgetsState(
        period = PeriodUi(LocalDate(2026, 9, 25), LocalDate(2026, 10, 24), 22),
        active = listOf(
            row("a", "Transport", 61_000, 120_000), // 51%
            row("b", "Groceries", 342_000, 400_000, BudgetStatusUi.Waiting, waiting = true), // 86%
            row("c", "Dining out", 154_000, 120_000, BudgetStatusUi.Changed, others = listOf("USD")), // 128%
            row("d", null, null, 900_000),
        ),
        archived = listOf(row("old", "Holidays", 30_000, 60_000)),
        loaded = true,
    )

    @Test
    fun theShareAndTheWordsAreWrittenInEveryState() {
        compose.setContent { AppTheme { BudgetsScreen(list, {}) } }
        compose.onNodeWithText("51%", substring = true).assertIsDisplayed()
        compose.onNodeWithText("86%", substring = true).assertIsDisplayed()
        compose.onNodeWithText("128%", substring = true).assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("over", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("left", substring = true)).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Changed on another device").assertIsDisplayed()
        compose.onNodeWithText("Includes changes waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Also spent in USD (not counted)").assertIsDisplayed()
        compose.onNodeWithText("Progress appears after sync").assertIsDisplayed()
        compose.onNodeWithText("All expenses").assertIsDisplayed()
        compose.onNodeWithText("25 Sep – 24 Oct · 22 days left").assertIsDisplayed()
    }

    @Test
    fun noAlarmWordingAppearsEvenWhenOverTheLimit() {
        compose.setContent { AppTheme { BudgetsScreen(list, {}) } }
        listOf("!", "Warning", "Danger", "Overspent", "exceeded").forEach { word ->
            assertTrue("'$word' on the budgets screen", compose.onAllNodes(hasText(word, substring = true)).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun theDisabledChoiceIsAnnouncedAsDisabledWithItsReason() {
        val choices = BudgetChoices(
            null, listOf(CategoryDisplay("c1", "Groceries", "cart", "#2E7D32", "expense", CategorySource.Own, false)), emptyList(),
            "EGP", listOf("EGP"), "EGP", setOf("c1"), 25,
        )
        compose.setContent { AppTheme { CategoryChoiceList(budgetCategoryGroups(choices), null, {}, "none") } }
        compose.onNodeWithText("Already budgeted").assertIsDisplayed()
        val disabled = compose.onAllNodes(SemanticsMatcher("disabled") { it.config.getOrNull(SemanticsProperties.Disabled) != null }, useUnmergedTree = false)
        assertTrue("the already budgeted row is announced as disabled", disabled.fetchSemanticsNodes().isNotEmpty())
        // "All expenses" stays selectable.
        assertTrue(compose.onAllNodes(hasText("All expenses").and(hasClickAction())).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theAddActionIsDescribedAndEveryTargetIsAtLeast48dp() {
        compose.setContent { AppTheme { BudgetsScreen(list, {}) } }
        assertTrue(compose.onAllNodes(hasContentDescription("Add budget")).fetchSemanticsNodes().isNotEmpty())
        assertTouchTargets()
    }

    @Test
    fun editorTargetsAreAtLeast48dpAndItsFieldsAreNamed() {
        compose.setContent { AppTheme { BudgetEditorScreen(BudgetEditorState(loaded = true, currency = "EGP", startDay = 25), {}) } }
        compose.onNodeWithText("Category").assertIsDisplayed()
        compose.onNodeWithText("Monthly, starting on day 25", substring = true).assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theHomeCardWritesTheShareAndOffersSeeAll() {
        val home = HomeState(email = "sara@example.com", budgets = listOf(BudgetGlimpse("a", "Groceries", 342_000, 400_000, "EGP", true)))
        compose.setContent { AppTheme { HomeScreen(home, {}) } }
        compose.onNodeWithTag("home.budgetsCard").assertIsDisplayed()
        compose.onNodeWithText("86%", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("See all").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun nothingClipsAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { BudgetsScreen(list, {}) } }
        compose.onNodeWithText("86%", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theEditorAndTheHomeCardStayUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { BudgetEditorScreen(BudgetEditorState(loaded = true, currency = "EGP", limitText = "6000", categoryChosen = true), {}) } }
        compose.onNodeWithText("Set budget").assertIsDisplayed()
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
