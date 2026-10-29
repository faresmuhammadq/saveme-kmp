package com.fares.saveme.feature.transactions.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.RefusalReason
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.ui.editor.EditorSync
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorScreen
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorState
import com.fares.saveme.feature.transactions.ui.list.DayUi
import com.fares.saveme.feature.transactions.ui.list.RowStatus
import com.fares.saveme.feature.transactions.ui.list.RowUi
import com.fares.saveme.feature.transactions.ui.list.TransactionsScreen
import com.fares.saveme.feature.transactions.ui.list.TransactionsState
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** FR-035, P-08: marks and kinds are text, money direction is announced, targets are 48dp, nothing clips at 2x font. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class TransactionsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val groceries = CategoryDisplay("c1", "Groceries", "cart", "#2E7D32", "expense", CategorySource.Own, false)
    private val today = LocalDate(2026, 10, 2)

    private fun row(id: String, kind: TransactionKind, direction: Direction, status: RowStatus) =
        RowUi(id, kind, direction, null, groceries, null, 4_550, status)

    private val list = TransactionsState(
        walletId = "w1", walletName = "Cash", currency = "EGP", balanceMinor = 234_000, loading = false, canAdd = true, totalCount = 4, today = today,
        days = listOf(
            DayUi(
                today, -9_100,
                listOf(
                    row("1", TransactionKind.Expense, Direction.Out, RowStatus.Waiting),
                    row("2", TransactionKind.Income, Direction.In, RowStatus.Changed),
                    row("3", TransactionKind.Expense, Direction.Out, RowStatus.NotSaved),
                ),
            ),
        ),
    )

    private val editor = TransactionEditorState(
        loaded = true, walletId = "w1", currency = "EGP", date = today, walletChoices = listOf(WalletChoice("w1", "Cash", "EGP"), WalletChoice("w2", "Bank", "EGP")),
        amountText = "45.50", categoryId = "c1", category = groceries, moreOpen = true, addedBy = AddedBy.Member("Omar"),
    )

    @Test
    fun syncMarksAndKindsAreText() {
        compose.setContent { AppTheme { TransactionsScreen(list, {}) } }
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Changed on another device.").assertIsDisplayed()
        compose.onNodeWithText("Not saved.").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Expense")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Today")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun moneyDirectionIsAnnouncedInWordsAndNotOnlyBySign() {
        compose.setContent { AppTheme { TransactionsScreen(list, {}) } }
        assertTrue(compose.onAllNodes(hasContentDescription("Money out", substring = true)).fetchSemanticsNodes().size >= 2)
        assertTrue(compose.onAllNodes(hasContentDescription("Money in", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun iconOnlyControlsAreDescribed() {
        compose.setContent { AppTheme { TransactionsScreen(list.copy(searchText = "x"), {}) } }
        assertTrue(compose.onAllNodes(hasContentDescription("Clear search")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun listTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { TransactionsScreen(list.copy(searchText = "x"), {}) } }
        assertTouchTargets()
    }

    @Test
    fun editorTargetsAreAtLeast48dpAndTheStatusIsText() {
        compose.setContent { AppTheme { TransactionEditorScreen(editor.copy(sync = EditorSync.Refused(RefusalReason(RefusalKind.NotVerified))), {}) } }
        compose.onNodeWithText("Verify your email address to save this transaction.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theEditorStaysUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { TransactionEditorScreen(editor.copy(canDelete = true, transactionId = "t1"), {}) } }
        compose.onNodeWithText("Save").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theListStaysUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { TransactionsScreen(list, {}) } }
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Changed on another device.").assertIsDisplayed()
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
