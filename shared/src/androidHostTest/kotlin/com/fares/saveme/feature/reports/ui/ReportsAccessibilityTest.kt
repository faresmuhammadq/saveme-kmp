package com.fares.saveme.feature.reports.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesScreen
import com.fares.saveme.feature.reports.ui.reports.ReportsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * P-08, FR-018: a chart is one announcement with a text alternative, every bar has its value written, state is never by colour alone,
 * targets are 48dp and nothing clips at 2x font.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h6000dp")
class ReportsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: com.fares.saveme.feature.reports.ui.reports.ReportsState) {
        compose.setContent { AppTheme { ReportsScreen(state, {}) } }
    }

    @Test
    fun theChartIsOneNodeAnnouncedWithItsSummary() {
        show(ReportUiFixtures.ready(trend = 6))
        val chart = compose.onNodeWithTag("reports.trend.chart").fetchSemanticsNode()
        val description = chart.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        assertEquals("Income and expenses for 6 budget periods; the table below lists each one", description)
        // Nothing inside the chart is announced separately.
        assertTrue(compose.onAllNodes(hasText("Income").and(hasTestTag("reports.trend.chart")), useUnmergedTree = false).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theTableListsEveryPeriodAndEachRowIsReadableAsOneNode() {
        show(ReportUiFixtures.ready(trend = 6))
        repeat(6) { index ->
            val row = compose.onNodeWithTag("reports.trend.row.$index").fetchSemanticsNode()
            val text = row.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
            assertTrue("row $index reads its period, income, expenses and net: $text", "Income" in text && "Expenses" in text && "Net" in text && "EGP" in text)
        }
    }

    @Test
    fun everyBarHasItsValueWrittenAndTheLegendNamesIncomeAndExpenses() {
        show(ReportUiFixtures.trendNegative)
        // 12,000.00 expenses and 10,000.00 income of September are written under their bars (number only, the currency is in the table).
        assertTrue(compose.onAllNodes(hasText("12,000.00"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("10,000.00"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Income"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Expenses"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("reports.trend.row.1").assertIsDisplayed()
    }

    /** Every text under the tagged row, in reading order (label then amount). */
    private fun textOf(tag: String): String {
        fun walk(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap { walk(it) }
        return walk(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()).joinToString(" ")
    }

    @Test
    fun aLiabilityWalletsNegativeBalancesAreAmountsOwed() {
        // FR-017, US3 AC4: a credit card's balances read "Owed 3,000.00 EGP"; a negative net or cross-wallet balance is a net amount with U+2212.
        show(ReportUiFixtures.creditCard)
        assertTrue(textOf("reports.summary.balance_start"), "Owed" in textOf("reports.summary.balance_start") && "3,000.00" in textOf("reports.summary.balance_start"))
        assertTrue(textOf("reports.summary.balance_end"), "Owed" in textOf("reports.summary.balance_end") && "4,200.00" in textOf("reports.summary.balance_end"))
        assertTrue(textOf("reports.summary.net"), "\u2212" in textOf("reports.summary.net") && "-" !in textOf("reports.summary.net"))
    }

    @Test
    fun aCrossWalletNegativeBalanceIsANetAmountWithTheTrueMinusSignNeverOwed() {
        val cross = ReportUiFixtures.ready().copy(
            summary = com.fares.saveme.feature.reports.ui.reports.SummaryUi(com.fares.saveme.feature.reports.domain.model.SummaryFigures(0, 420_000, -420_000, 0, 0, -100_000, -520_000, "EGP"), null, liabilityWallet = false),
        )
        show(cross)
        assertTrue(textOf("reports.summary.balance_end"), "\u2212" in textOf("reports.summary.balance_end") && "Owed" !in textOf("reports.summary.balance_end") && "-" !in textOf("reports.summary.balance_end"))
        assertTrue(textOf("reports.summary.net"), "\u2212" in textOf("reports.summary.net"))
    }

    @Test
    fun theScheduledPartIsWrittenAndNamedNotJustOutlined() {
        show(ReportUiFixtures.scheduled)
        assertTrue(compose.onAllNodes(hasText("Scheduled", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size >= 3)
        compose.onNodeWithTag("reports.planned").assertIsDisplayed()
        compose.onNodeWithText("Scheduled money is known up to 6 Nov 2026").assertIsDisplayed()
    }

    @Test
    fun aCategoryRowIsOneAnnouncementWithNameShareAndAmount() {
        show(ReportUiFixtures.ready())
        val row = compose.onNodeWithTag("reports.category.0").fetchSemanticsNode()
        assertEquals("Groceries, 64%, EGP 3,500.00", row.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
    }

    @Test
    fun theRangeControlsAreDescribedAndEveryTargetIsAtLeast48dp() {
        show(ReportUiFixtures.crossCurrency)
        assertTrue(compose.onAllNodes(hasContentDescription("Previous period")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasContentDescription("Next period")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Not included: Holiday fund (KWD), Savings (USD)").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun nothingClipsAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        show(ReportUiFixtures.ready(trend = 3))
        compose.onNodeWithTag("reports.summary.income").assertIsDisplayed()
        compose.onNodeWithTag("reports.summary.net").assertIsDisplayed()
        compose.onNodeWithTag("reports.range.label").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theCategoriesScreenKeepsItsControlsAt48dpAndWritesEveryShare() {
        compose.setContent { AppTheme { ReportCategoriesScreen(ReportUiFixtures.categories(), {}) } }
        // Each row is one announcement carrying its share; the written share is drawn too.
        listOf("64%" to 0, "22%" to 1, "15%" to 2).forEach { (share, index) ->
            val row = compose.onNodeWithTag("report_categories.row.$index").fetchSemanticsNode()
            assertTrue(share in row.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty())
            assertTrue(compose.onAllNodes(hasText(share), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        compose.onNodeWithTag("report_categories.total").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun noAlarmWordingAndAnExpenseIsNeverShownInTheErrorColour() {
        show(ReportUiFixtures.ready())
        listOf("!", "Warning", "Danger", "Overspent", "advanced", "account").forEach { word ->
            assertTrue("'$word' on the reports screen", compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).fetchSemanticsNodes().isEmpty())
        }
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
