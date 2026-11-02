package com.fares.saveme.feature.sharedbudgets

import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntryEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetLineEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetMemberEntity
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetMath
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun budget(
    startDayHistory: String = """[{"start_day":1,"applies_from":null}]""",
    limitHistory: String = """[{"applies_from":"2026-09-01","limit_minor":2000000}]""",
) = SharedBudgetEntity(
    id = "b1", ownerId = "me", name = "Home", currency = "EGP", startDay = 1, timezone = "Africa/Cairo", limitMinor = 2_000_000,
    archivedAt = null, limitHistory = limitHistory, startDayHistory = startDayHistory, version = 3, changeSeq = 9,
    createdAt = "t", updatedAt = "t", refreshedAt = "2026-10-10T10:00:00Z",
)

private fun line(category: String, limit: Long? = null) = SharedBudgetLineEntity(
    id = "l-$category", sharedBudgetId = "b1", categoryId = category, limitMinor = limit, name = category.uppercase(), icon = null, color = null,
    limitHistory = "[]", version = 1, changeSeq = 1, updatedAt = "t",
)

private fun entry(id: String, category: String, date: String, amount: Long, by: String = "me", detached: Boolean = false) = SharedBudgetEntryEntity(
    id = id, sharedBudgetId = "b1", createdBy = by, categoryId = category, date = date, amountMinor = amount, description = null,
    detachedAt = if (detached) "2026-10-05T00:00:00Z" else null, version = 1, changeSeq = 1, updatedAt = "t",
)

private fun member(user: String, name: String) = SharedBudgetMemberEntity(
    id = "m-$user", sharedBudgetId = "b1", userId = user, displayName = name, role = "member", readOnly = false, version = 1, changeSeq = 1, updatedAt = "t",
)

class SharedBudgetMathTest {
    private val today = LocalDate(2026, 10, 10)

    @Test
    fun onlyEntriesInThePeriodCountAndAnEntryOfAFormerMemberStillCounts() {
        val result = SharedBudgetMath.progress(
            budget(), listOf(line("c1")),
            listOf(
                entry("e1", "c1", "2026-10-02", 1_000),
                entry("e2", "c1", "2026-09-30", 9_999), // last period
                entry("e3", "c1", "2026-10-09", 500, by = "gone", detached = true),
            ),
            listOf(member("me", "Fares")), "me", today,
        )
        assertEquals(1_500, result.spentMinor)
        assertEquals(listOf("me" to 1_000L, "gone" to 500L), result.members.map { it.userId to it.spentMinor })
        assertNull(result.members.last().name, "someone who left has no name")
        assertEquals(LocalDate(2026, 10, 1), result.period.start)
    }

    @Test
    fun otherAndRemovedCategoriesFallIntoTheUnlistedBucket() {
        val result = SharedBudgetMath.progress(
            budget(), listOf(line("c1", limit = 500_000)),
            listOf(entry("e1", "c1", "2026-10-02", 100), entry("e2", "other", "2026-10-03", 250)),
            emptyList(), "me", today,
        )
        assertEquals(250, result.unlistedMinor)
        assertEquals(100, result.lines.single().spentMinor)
        assertEquals(500_000L, result.lines.single().limitMinor)
    }

    @Test
    fun aLimitChangeAppliesFromItsDateAndEarlierDaysKeepTheOldOne() {
        val history = """[{"applies_from":"2026-09-01","limit_minor":2000000},{"applies_from":"2026-10-01","limit_minor":3000000}]"""
        assertEquals(3_000_000L, SharedBudgetMath.progress(budget(limitHistory = history), emptyList(), emptyList(), emptyList(), "me", today).limitMinor)
        val steps = listOf(
            com.fares.saveme.feature.sharedbudgets.data.dto.LimitStepDto("2026-09-01", 2_000_000),
            com.fares.saveme.feature.sharedbudgets.data.dto.LimitStepDto("2026-10-01", 3_000_000),
        )
        assertEquals(2_000_000L, SharedBudgetMath.limitOn(LocalDate(2026, 9, 20), steps, null))
    }

    @Test
    fun aPendingStartDayChangeOnlyTakesOverFromItsDate() {
        val history = """[{"start_day":1,"applies_from":null},{"start_day":15,"applies_from":"2026-11-01"}]"""
        assertEquals(LocalDate(2026, 10, 1), SharedBudgetMath.periodOn(today, budget(startDayHistory = history)).start)
        assertEquals(LocalDate(2026, 11, 15), SharedBudgetMath.periodOn(LocalDate(2026, 11, 20), budget(startDayHistory = history)).start)
    }

    @Test
    fun theProjectionIsRoundedUpAndHiddenWhileNothingIsSpent() {
        val none = SharedBudgetMath.progress(budget(), emptyList(), emptyList(), emptyList(), "me", today)
        assertNull(none.projectedMinor)
        val some = SharedBudgetMath.progress(budget(), emptyList(), listOf(entry("e1", "c1", "2026-10-03", 1_000)), emptyList(), "me", today)
        assertEquals(3_100L, some.projectedMinor) // ceil(1000 * 31 / 10)
    }
}

class SharedBudgetPastPeriodTest {
    private val today = LocalDate(2026, 10, 10)

    @Test
    fun anEarlierPeriodShowsItsOwnEntriesAndTheLimitItHadWithNoProjection() {
        val history = """[{"applies_from":"2026-09-01","limit_minor":2000000},{"applies_from":"2026-10-01","limit_minor":3000000}]"""
        val b = budget(limitHistory = history).copy(createdAt = "2026-08-15T10:00:00Z")
        val anchor = SharedBudgetMath.anchorFor(today, 1, b)
        assertEquals(LocalDate(2026, 9, 30), anchor)
        val result = SharedBudgetMath.progress(
            b, emptyList(),
            listOf(entry("old", "c1", "2026-09-12", 700, by = "gone"), entry("new", "c1", "2026-10-02", 100)),
            emptyList(), "me", today, anchor,
        )
        assertEquals(700, result.spentMinor)
        assertEquals(2_000_000L, result.limitMinor)
        assertNull(result.projectedMinor)
        assertEquals(false, result.isCurrent)
        assertEquals(LocalDate(2026, 9, 1), result.period.start)
        assertEquals(true, result.canGoBack)
    }

    @Test
    fun theEarliestPeriodCannotGoFurtherBack() {
        val b = budget().copy(createdAt = "2026-10-03T10:00:00Z")
        assertEquals(false, SharedBudgetMath.progress(b, emptyList(), emptyList(), emptyList(), "me", today).canGoBack)
    }

    @Test
    fun expensesAreListedNewestFirstWithAuthorAndNoWalletData() {
        val result = SharedBudgetMath.progress(
            budget(), listOf(line("c1")),
            listOf(entry("a", "c1", "2026-10-02", 100), entry("b", "c1", "2026-10-05", 200, by = "sara"), entry("c", "zz", "2026-10-05", 50)),
            listOf(member("sara", "Sara")), "me", today,
        )
        assertEquals(listOf("b", "c", "a"), result.entries.map { it.id })
        assertEquals("Sara", result.entries.first().authorName)
        assertEquals(true, result.entries.last().isMine)
        assertEquals("", result.entries[1].categoryName) // no line names it: shown as Other
    }
}
