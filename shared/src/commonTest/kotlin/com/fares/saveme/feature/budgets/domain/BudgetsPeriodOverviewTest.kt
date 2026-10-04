package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.domain.model.PendingStartDay
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.expense
import com.fares.saveme.feature.budgets.profileView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** Periods follow the server's profile, including a pending change and the day turning (FR-009, FR-010, US5). */
class BudgetsPeriodOverviewTest {

    private fun period(overview: com.fares.saveme.feature.budgets.domain.model.BudgetsOverview) =
        assertIs<PeriodState.Known>(overview.period).period

    @Test
    fun aPendingChangeKeepsTheStoredServerPeriodAndSaysWhenTheNewDayApplies() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.now.value = Instant.parse("2026-10-10T10:00:00Z")
        env.profile.value = profileView(startDay = 1, from = LocalDate(2026, 11, 1))
        env.seed(budgetJson("b1"))
        env.seedProgress("b1", start = "2026-09-25", end = "2026-10-31", spent = 1_000, local = 0)
        env.spending.set(expense("e1", "2026-09-26", 700))
        val overview = env.service.observeOverview().first()
        val p = period(overview)
        assertEquals(LocalDate(2026, 9, 25), p.start)
        assertEquals(LocalDate(2026, 10, 31), p.end)
        assertEquals(PendingStartDay(1, LocalDate(2026, 11, 1)), overview.pendingStartDay)
        assertEquals(700, overview.active.single().figures!!.spentMinor)
        assertEquals(21, overview.daysLeft)
    }

    @Test
    fun withoutAStoredPeriodTheBudgetShowsItsLimitAndNoFigures() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.now.value = Instant.parse("2026-10-10T10:00:00Z")
        env.profile.value = profileView(startDay = 1, from = LocalDate(2026, 11, 1))
        env.seed(budgetJson("b1", amount = 600_000))
        val overview = env.service.observeOverview().first()
        assertEquals(PeriodState.Unknown, overview.period)
        assertNull(overview.daysLeft)
        assertNull(overview.active.single().figures)
        assertEquals(600_000, overview.active.single().limitMinor)
        assertEquals(PendingStartDay(1, LocalDate(2026, 11, 1)), overview.pendingStartDay)
    }

    @Test
    fun aStartDayEditWaitingOnThisPhoneChangesNothing() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.profile.value = profileView(startDay = 1).copy(pending = ProfileDraft("Sara", "en", "Africa/Cairo", "EGP", 25))
        val p = period(env.service.observeOverview().first())
        assertEquals(LocalDate(2026, 10, 1), p.start, "periods keep following the server's start day")
        assertEquals(1, env.service.observeStartDay().first())
    }

    @Test
    fun onceTheAppliesFromDateIsReachedTheNewPeriodsApplyWithoutAnyAction() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.profile.value = profileView(startDay = 15, from = LocalDate(2026, 10, 15))
        env.now.value = Instant.parse("2026-10-14T10:00:00Z")
        assertEquals(PeriodState.Unknown, env.service.observeOverview().first().period)
        env.now.value = Instant.parse("2026-10-15T10:00:00Z")
        val overview = env.service.observeOverview().first()
        assertEquals(LocalDate(2026, 10, 15), period(overview).start)
        assertNull(overview.pendingStartDay)
    }

    @Test
    fun todayIsTakenInTheProfilesTimeZoneAndTheDevicesUntilTheProfileIsKnown() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        // 22:30 UTC on 2 Oct is still 2 Oct in New York but already 3 Oct in Cairo.
        env.now.value = Instant.parse("2026-10-02T22:30:00Z")
        env.profile.value = profileView(zone = "America/New_York")
        assertEquals(LocalDate(2026, 10, 2), env.service.observeOverview().first().today)
        env.profile.value = null
        assertEquals(LocalDate(2026, 10, 3), env.service.observeOverview().first().today)
    }

    @Test
    fun theOverviewMovesToTheNewPeriodWhenTheDayTurnsInTheProfileZone() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1"))
        env.spending.set(expense("e1", "2026-10-31", 1_000), expense("e2", "2026-11-01", 400))
        env.now.value = Instant.parse("2026-10-31T21:59:00Z") // 23:59 in Cairo (UTC+2 once summer time ended)
        val before = env.service.observeOverview().first()
        assertEquals(LocalDate(2026, 10, 1), period(before).start)
        assertEquals(0, before.daysLeft)
        assertEquals(1_000, before.active.single().figures!!.spentMinor)
        env.now.value = Instant.parse("2026-10-31T22:00:00Z") // midnight in Cairo
        val after = env.service.observeOverview().first()
        assertEquals(LocalDate(2026, 11, 1), period(after).start)
        assertEquals(400, after.active.single().figures!!.spentMinor)
    }
}
