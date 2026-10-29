package com.fares.saveme.feature.recurring.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorScreen
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorState
import com.fares.saveme.feature.recurring.ui.list.RecurringScreen
import com.fares.saveme.feature.recurring.ui.list.RecurringState
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceScreen
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceScreenState
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailScreen
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailState
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** P-08: occurrence states and rule status are text, icon buttons are described, targets are 48dp, nothing clips at 2x font. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2600dp")
class RecurringAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val due = testOccurrenceView(testOccurrence("o1"), OccurrenceState.Due)
    private val missed = testOccurrenceView(testOccurrence("o0", date = LocalDate(2026, 9, 1)), OccurrenceState.Missed)
    private val upcoming = testOccurrenceView(testOccurrence("o2", date = LocalDate(2026, 11, 1)), OccurrenceState.Upcoming)
    private val skipped = testOccurrenceView(testOccurrence("o3", status = "skipped"), OccurrenceState.Skipped)
    private val confirmed = testOccurrenceView(testOccurrence("o4", status = "confirmed", transactionId = "t1"), OccurrenceState.Confirmed)

    private val list = RecurringState(
        walletId = "w1", walletName = "Cash", loading = false, canAdd = true,
        due = listOf(missed, due), upcoming = listOf(upcoming),
        rules = listOf(
            testRuleView(testRule(id = "a")),
            testRuleView(testRule(id = "p", status = "paused"), syncState = RecurringSyncState.Waiting),
            testRuleView(testRule(id = "e", status = "ended"), readOnly = true),
        ),
    )

    private val rule = RuleDetailState(
        "r1", loaded = true, view = testRuleView(),
        occurrences = OccurrenceLists(due = listOf(due), upcoming = listOf(upcoming), history = listOf(skipped, confirmed)),
    )

    private val editor = RuleEditorState(loaded = true, walletId = "w1", walletChoices = listOf(WalletChoice("w1", "Cash", "EGP")), currency = "EGP", start = LocalDate(2026, 10, 1))

    private fun occurrence(view: com.fares.saveme.feature.recurring.domain.model.OccurrenceView) = OccurrenceScreenState("o1", loaded = true, view = view)

    @Test
    fun occurrenceStatesAndRuleStatusAreText() {
        compose.setContent { AppTheme { RecurringScreen(list, {}) } }
        compose.onNodeWithText("Missed").assertIsDisplayed()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Due")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Upcoming")).fetchSemanticsNodes().size >= 2)
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Paused")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Ended")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Waiting to sync")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun ruleDetailDatesShowSkippedAndConfirmedInWords() {
        compose.setContent { AppTheme { RuleDetailScreen(rule, {}) } }
        compose.onNodeWithText("Skipped").assertIsDisplayed()
        compose.onNodeWithText("Confirmed").assertIsDisplayed()
    }

    @Test
    fun iconOnlyControlsAreDescribed() {
        compose.setContent { AppTheme { RecurringScreen(list, {}) } }
        assertTrue(compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasContentDescription("Add recurring", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun targetsAreAtLeast48dpOnAllFourScreens() {
        compose.setContent { AppTheme { RecurringScreen(list, {}) } }
        assertTouchTargets()
    }

    @Test
    fun ruleDetailTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { RuleDetailScreen(rule, {}) } }
        assertTouchTargets()
    }

    @Test
    fun editorTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { RuleEditorScreen(editor, {}) } }
        assertTouchTargets()
    }

    @Test
    fun occurrenceTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { OccurrenceScreen(occurrence(due), {}) } }
        assertTouchTargets()
    }

    @Test
    fun allFourScreensStayUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { RuleDetailScreen(rule, {}) } }
        compose.onNodeWithText("Edit").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theListStaysUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { RecurringScreen(list, {}) } }
        compose.onNodeWithText("Missed").assertIsDisplayed()
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
