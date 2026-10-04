package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.FigureSource
import com.fares.saveme.feature.budgets.domain.model.SpendingSums
import com.fares.saveme.feature.budgets.domain.model.StoredPeriod
import com.fares.saveme.feature.budgets.domain.model.StoredProgress
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which figure is shown (FR-014 a-d, research R-06). */
class ProgressSelectionTest {

    private val period = BudgetPeriod(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), 31, 2)

    private fun stored(
        spent: Long = 50_000,
        local: Long = 45_000,
        others: List<String> = emptyList(),
        localOthers: List<String> = emptyList(),
        start: LocalDate = period.start,
        end: LocalDate = period.end,
        limit: Long = 600_000,
        currency: String = "EGP",
    ) = StoredProgress(StoredPeriod(start, end, 31), currency, limit, spent, others, local, localOthers)

    private fun sums(spent: Long, waiting: Boolean = false, others: List<String> = emptyList()) = SpendingSums(spent, others, waiting)

    private fun select(
        stored: StoredProgress? = stored(),
        phone: SpendingSums = sums(45_000),
        synced: SpendingSums = sums(45_000),
        limit: Long = 600_000,
    ) = ProgressSelection.select(stored, period, "EGP", limit, phone, synced)

    @Test
    fun theServersFiguresAreShownWhileNothingChanged() {
        val selection = select()
        assertEquals(FigureSource.Server, selection.source)
        assertEquals(50_000, selection.spentMinor, "covers spending the phone has not received")
        assertEquals(false, selection.includesWaiting)
    }

    @Test
    fun withoutAStoredReadThePhonesOwnFiguresAreShown() {
        val selection = select(stored = null)
        assertEquals(FigureSource.Phone, selection.source)
        assertEquals(45_000, selection.spentMinor)
    }

    @Test
    fun aWaitingChangeThatMatchesSwitchesToThePhonesFiguresAndSaysSo() {
        val selection = select(phone = sums(45_200, waiting = true))
        assertEquals(FigureSource.Phone, selection.source)
        assertEquals(45_200, selection.spentMinor)
        assertEquals(true, selection.includesWaiting)
    }

    @Test
    fun aStoredPeriodThatIsNotTheCurrentOneIsNeverShown() {
        assertEquals(FigureSource.Phone, select(stored = stored(start = LocalDate(2026, 9, 1), end = LocalDate(2026, 9, 30))).source)
        assertEquals(FigureSource.Phone, select(stored = stored(end = LocalDate(2026, 10, 30))).source)
    }

    @Test
    fun aChangedLimitOrCurrencySwitchesToThePhone() {
        assertEquals(FigureSource.Phone, select(limit = 700_000).source)
        assertEquals(FigureSource.Phone, select(stored = stored(limit = 500_000)).source)
        assertEquals(FigureSource.Phone, select(stored = stored(currency = "USD")).source)
    }

    @Test
    fun aSyncedChangeSinceTheReadSwitchesToThePhone() {
        val selection = select(synced = sums(46_000), phone = sums(46_000))
        assertEquals(FigureSource.Phone, selection.source)
        assertEquals(46_000, selection.spentMinor)
        assertEquals(FigureSource.Phone, select(stored = stored(localOthers = listOf("USD")), synced = sums(45_000)).source)
    }

    @Test
    fun acceptingAWaitingChangeNeverJumpsBackToTheOlderServerFigure() {
        // Read at 45,000 synced (server 50,000). A 200 expense waits: the phone shows 45,200 with the note ...
        val waiting = select(phone = sums(45_200, waiting = true), synced = sums(45_000))
        assertEquals(45_200, waiting.spentMinor)
        // ... the server accepts it: nothing waits, the synced-only sum grew, so the phone figure stays 45,200.
        val accepted = select(phone = sums(45_200), synced = sums(45_200))
        assertEquals(FigureSource.Phone, accepted.source)
        assertEquals(45_200, accepted.spentMinor)
        assertEquals(false, accepted.includesWaiting)
        // The next read anchors again: server 50,200 with the new synced-only sum, shown.
        val reread = select(stored = stored(spent = 50_200, local = 45_200), phone = sums(45_200), synced = sums(45_200))
        assertEquals(FigureSource.Server, reread.source)
        assertEquals(50_200, reread.spentMinor)
    }

    @Test
    fun otherCurrenciesFollowTheChosenSource() {
        val server = select(stored = stored(others = listOf("USD")), phone = sums(45_000, others = listOf("KWD")))
        assertEquals(listOf("USD"), server.otherCurrencies)
        val phone = select(stored = null, phone = sums(45_000, others = listOf("KWD")))
        assertEquals(listOf("KWD"), phone.otherCurrencies)
    }
}
