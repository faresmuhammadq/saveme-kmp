package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.scheduledLine
import com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The report rules of ADR-0022 on the phone (spec 014 FR-003 to FR-005, FR-016). */
class ReportCalculatorTest {

    private val from = date("2026-10-01")
    private val to = date("2026-10-31")

    private fun summary(
        covered: Set<String>,
        lines: List<com.fares.saveme.feature.transactions.domain.LedgerLine>,
        openings: Map<String, Long> = emptyMap(),
        before: Map<String, Long> = emptyMap(),
    ) = ReportCalculator.summary("EGP", covered, openings, from, to, lines, before)

    @Test
    fun aWalletReportCountsIncomeExpensesAndTransfersByDirection() {
        val figures = summary(
            setOf("w1"),
            listOf(
                line("a", "income", "2026-10-02", 1_000_000),
                line("b", "expense", "2026-10-03", 350_000),
                line("c", "transfer", "2026-10-04", 200_000, wallet = "w1", counter = "w9"),
                line("d", "transfer", "2026-10-05", 50_000, wallet = "w9", counter = "w1"),
            ),
            openings = mapOf("w1" to 80_000),
        )
        assertEquals(1_000_000, figures.income)
        assertEquals(350_000, figures.expenses)
        assertEquals(650_000, figures.net)
        assertEquals(50_000, figures.transfersIn)
        assertEquals(200_000, figures.transfersOut)
        assertEquals(80_000, figures.balanceStart)
        assertEquals(80_000 + 1_000_000 - 350_000 + 50_000 - 200_000, figures.balanceEnd)
    }

    @Test
    fun anAdjustmentIsNeitherIncomeNorExpenseAndTheBalanceStillAddsUp() {
        val figures = summary(
            setOf("w1"),
            listOf(
                line("a", "expense", "2026-10-03", 3_000),
                line("b", "adjustment_in", "2026-10-06", 2_500),
                line("c", "adjustment_out", "2026-10-07", 700),
                line("d", "adjustment_in", "2026-09-20", 400),
            ),
            openings = mapOf("w1" to 10_000),
        )
        assertEquals(0, figures.income)
        assertEquals(3_000, figures.expenses)
        assertEquals(0, figures.transfersIn)
        assertEquals(0, figures.transfersOut)
        assertEquals(2_500, figures.adjustmentsIn)
        assertEquals(700, figures.adjustmentsOut)
        assertEquals(10_400, figures.balanceStart)
        assertEquals(10_400 - 3_000 + 2_500 - 700, figures.balanceEnd)
    }

    @Test
    fun aKindThisAppDoesNotKnowAndALineOutsideTheRangeCountNothing() {
        val figures = summary(
            setOf("w1"),
            listOf(
                line("x", "refund", "2026-10-02", 999),
                line("late", "expense", "2026-11-01", 500),
                line("in", "expense", "2026-10-31", 70),
            ),
        )
        assertEquals(70, figures.expenses)
        assertEquals(0, figures.balanceStart)
        assertEquals(-70, figures.balanceEnd)
    }

    @Test
    fun aLineBeforeTheRangeRollsIntoTheOpeningBalanceOnly() {
        val figures = summary(
            setOf("w1"),
            listOf(line("old", "income", "2026-09-30", 400), line("in", "expense", "2026-10-01", 100)),
            before = mapOf("w1" to 1_000),
        )
        assertEquals(0, figures.income)
        assertEquals(1_400, figures.balanceStart)
        assertEquals(1_300, figures.balanceEnd)
    }

    @Test
    fun aTransferBetweenTwoCoveredWalletsAppearsNowhere() {
        val lines = listOf(line("t", "transfer", "2026-10-04", 300, wallet = "w1", counter = "w2"))
        val both = summary(setOf("w1", "w2"), lines, openings = mapOf("w1" to 1_000, "w2" to 0))
        assertEquals(0, both.transfersIn)
        assertEquals(0, both.transfersOut)
        assertEquals(1_000, both.balanceStart)
        assertEquals(1_000, both.balanceEnd)
    }

    @Test
    fun aTransferToOrFromAnUncoveredWalletCountsInItsDirectionEvenWhenThatWalletWasDeleted() {
        val lines = listOf(
            line("out", "transfer", "2026-10-04", 300, wallet = "w1", counter = "w9"),
            line("in", "transfer", "2026-10-05", 120, wallet = "w9", counter = "w1"),
            line("fromDeleted", "transfer", "2026-10-06", 40, wallet = "gone", counter = "w2"),
        )
        val figures = summary(setOf("w1", "w2"), lines)
        assertEquals(300, figures.transfersOut)
        assertEquals(160, figures.transfersIn)
        assertEquals(-300 + 120 + 40, figures.balanceEnd)
    }

    @Test
    fun aLiabilityWalletKeepsItsNegativeBalances() {
        val figures = summary(setOf("card"), listOf(line("a", "expense", "2026-10-05", 420_000, wallet = "card")), openings = mapOf("card" to -300_000))
        assertEquals(-300_000, figures.balanceStart)
        assertEquals(-720_000, figures.balanceEnd)
        assertEquals(-420_000, figures.net)
    }

    @Test
    fun theBalanceIdentityHoldsOverRandomLedgers() {
        val random = Random(2026)
        val wallets = listOf("w1", "w2", "w3", "w9")
        repeat(500) {
            val lines = (0 until random.nextInt(0, 40)).map { i ->
                val kind = listOf("income", "expense", "transfer")[random.nextInt(3)]
                val day = random.nextInt(-20, 45)
                val on = date("2026-10-01").let { kotlinx.datetime.LocalDate.fromEpochDays(it.toEpochDays() + day) }.toString()
                val source = wallets[random.nextInt(wallets.size)]
                val destination = wallets.filter { it != source }[random.nextInt(wallets.size - 1)]
                line("l$i", kind, on, random.nextLong(1, 100_000), wallet = source, counter = if (kind == "transfer") destination else null)
            }
            val covered = wallets.take(random.nextInt(1, 4)).toSet()
            val openings = covered.associateWith { random.nextLong(-50_000, 500_000) }
            val figures = summary(covered, lines, openings)
            assertEquals(
                figures.balanceStart + figures.income - figures.expenses + figures.transfersIn - figures.transfersOut,
                figures.balanceEnd,
            )
            assertEquals(figures.income - figures.expenses, figures.net)
        }
    }

    @Test
    fun anOverflowIsTooLargeNotAWrongNumber() {
        assertFailsWith<TooLarge> {
            summary(setOf("w1"), listOf(line("a", "income", "2026-10-02", Long.MAX_VALUE), line("b", "income", "2026-10-03", 1)))
        }
    }

    @Test
    fun plannedSumsUseTheCoveredTransferRuleAndTheReportCurrencyOnly() {
        val planned = ReportCalculator.planned(
            "EGP", setOf("w1", "w2"), from, to,
            listOf(
                scheduledLine("rent", "expense", "2026-10-28", 500_000),
                scheduledLine("pay", "income", "2026-10-25", 100_000, wallet = "w2"),
                scheduledLine("internal", "transfer", "2026-10-26", 70_000, wallet = "w1", counter = "w2"),
                scheduledLine("out", "transfer", "2026-10-27", 30_000, wallet = "w1", counter = "w9"),
                scheduledLine("in", "transfer", "2026-10-27", 20_000, wallet = "w9", counter = "w2"),
                scheduledLine("usd", "expense", "2026-10-28", 999, currency = "USD"),
                scheduledLine("late", "expense", "2026-11-02", 999),
            ),
        )
        assertEquals(100_000, planned.income)
        assertEquals(500_000, planned.expenses)
        assertEquals(30_000, planned.transfersOut)
        assertEquals(20_000, planned.transfersIn)
    }

    @Test
    fun theTrendBucketsEachPeriodAndLeavesTransfersOut() {
        val spans = listOf(
            BudgetPeriodSpan(date("2026-08-01"), date("2026-08-31")),
            BudgetPeriodSpan(date("2026-09-01"), date("2026-09-30")),
            BudgetPeriodSpan(date("2026-10-01"), date("2026-10-31")),
        )
        val points = ReportCalculator.trend(
            "EGP", setOf("w1"), spans,
            listOf(
                line("a", "income", "2026-09-05", 1_000_000),
                line("b", "expense", "2026-09-06", 1_200_000),
                line("c", "income", "2026-10-05", 1_000_000),
                line("d", "expense", "2026-10-06", 350_000),
                line("t", "transfer", "2026-10-07", 777, wallet = "w1", counter = "w9"),
                line("elsewhere", "expense", "2026-10-07", 55, wallet = "w7"),
            ),
            scheduled = null,
        )
        assertEquals(listOf(0L, 1_000_000L, 1_000_000L), points.map { it.incomeMinor })
        assertEquals(listOf(0L, 1_200_000L, 350_000L), points.map { it.expensesMinor })
        assertEquals(listOf(0L, -200_000L, 650_000L), points.map { it.netMinor })
        assertEquals(listOf(null, null, null), points.map { it.plannedExpensesMinor })
    }

    @Test
    fun theTrendCarriesScheduledSumsPerPeriodWhenAsked() {
        val spans = listOf(BudgetPeriodSpan(date("2026-09-25"), date("2026-10-24")), BudgetPeriodSpan(date("2026-10-25"), date("2026-11-24")))
        val points = ReportCalculator.trend(
            "EGP", setOf("w1"), spans, emptyList(),
            listOf(scheduledLine("rent", "expense", "2026-10-28", 500_000), scheduledLine("pay", "income", "2026-10-26", 9)),
        )
        assertEquals(listOf(0L, 500_000L), points.map { it.plannedExpensesMinor })
        assertEquals(listOf(0L, 9L), points.map { it.plannedIncomeMinor })
    }

    @Test
    fun theTrendAddsTheSumsOfTheDaysAroundTheRangeToTheRowsInIt() {
        val spans = listOf(BudgetPeriodSpan(date("2026-09-01"), date("2026-09-30")), BudgetPeriodSpan(date("2026-10-01"), date("2026-10-31")))
        val points = ReportCalculator.trend(
            "EGP", setOf("w1"), spans, listOf(line("a", "expense", "2026-10-05", 100)), null,
            daily = listOf(
                com.fares.saveme.feature.transactions.domain.DaySum(date("2026-09-05"), 1_000, 300),
                com.fares.saveme.feature.transactions.domain.DaySum(date("2026-09-06"), 0, 20),
                com.fares.saveme.feature.transactions.domain.DaySum(date("2026-08-31"), 5, 5),
            ),
        )
        assertEquals(listOf(1_000L, 0L), points.map { it.incomeMinor })
        assertEquals(listOf(320L, 100L), points.map { it.expensesMinor })
    }
}
