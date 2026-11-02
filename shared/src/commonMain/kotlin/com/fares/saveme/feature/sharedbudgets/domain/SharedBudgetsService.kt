package com.fares.saveme.feature.sharedbudgets.domain

import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetRepository
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** What the screens read: every figure is computed here from the phone's copy, never asked of the server (P-04.1). */
interface SharedBudgetsService {
    /** The budgets the person belongs to, their current period's figures, by creation. */
    fun observeList(today: () -> LocalDate): Flow<List<SharedBudgetProgress>>

    /** One budget with its lines, members and per-member totals; null when it is not on the phone (any more). */
    fun observeDetail(id: String, today: () -> LocalDate): Flow<SharedBudgetProgress?>
}

class DefaultSharedBudgetsService(
    private val repository: SharedBudgetRepository,
    private val accountId: () -> String?,
) : SharedBudgetsService {
    override fun observeList(today: () -> LocalDate): Flow<List<SharedBudgetProgress>> {
        val me = accountId() ?: return emptyFlow()
        // A period is a month at most, so two months back always covers each budget's current one.
        val from = today().minus(DatePeriod(months = 2)).toString()
        return combine(repository.observeMine(me), repository.observeEntriesSince(from)) { budgets, entries ->
            val day = today()
            budgets.map { budget ->
                SharedBudgetMath.progress(budget, emptyList(), entries.filter { it.sharedBudgetId == budget.id }, emptyList(), me, day)
            }
        }
    }

    override fun observeDetail(id: String, today: () -> LocalDate): Flow<SharedBudgetProgress?> {
        val me = accountId() ?: return emptyFlow()
        val from = today().minus(DatePeriod(months = 2)).toString()
        return combine(
            repository.observe(id),
            repository.observeLines(id),
            repository.observeMembers(id),
            repository.observeEntriesSince(from).map { all -> all.filter { it.sharedBudgetId == id } },
        ) { budget, lines, members, entries ->
            budget?.let { SharedBudgetMath.progress(it, lines, entries, members, me, today()) }
        }
    }
}
