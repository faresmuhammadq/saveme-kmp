package com.fares.saveme.feature.sharedbudgets.domain

import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetRepository
import com.fares.saveme.feature.sharedbudgets.domain.model.EditorBudget
import com.fares.saveme.feature.sharedbudgets.domain.model.EditorLine
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    fun observeDetail(id: String, today: () -> LocalDate, periodsBack: Int = 0): Flow<SharedBudgetProgress?>

    /** One budget as the editor edits it, with its category lines; null when it is not on the phone. */
    fun observeEditor(id: String): Flow<EditorBudget?>
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDetail(id: String, today: () -> LocalDate, periodsBack: Int): Flow<SharedBudgetProgress?> {
        val me = accountId() ?: return emptyFlow()
        return repository.observe(id).flatMapLatest { budget ->
            if (budget == null) {
                flowOf(null)
            } else {
                val now = today()
                val anchor = SharedBudgetMath.anchorFor(now, periodsBack, budget)
                val period = SharedBudgetMath.periodOn(anchor, budget)
                combine(
                    repository.observeLines(id),
                    repository.observeMembers(id),
                    repository.observeEntries(id, period.start.toString(), period.end.toString()),
                ) { lines, members, entries -> SharedBudgetMath.progress(budget, lines, entries, members, me, now, anchor) }
            }
        }
    }

    override fun observeEditor(id: String): Flow<EditorBudget?> {
        val me = accountId() ?: return emptyFlow()
        return combine(repository.observe(id), repository.observeLines(id)) { budget, lines ->
            budget?.let {
                EditorBudget(
                    id = it.id, name = it.name, currency = it.currency, startDay = it.startDay, limitMinor = it.limitMinor, version = it.version,
                    isOwner = it.ownerId == me, archived = it.archivedAt != null,
                    lines = lines.map { line -> EditorLine(line.id, line.categoryId, line.name.orEmpty(), line.icon, line.limitMinor) }
                        .sortedBy { line -> line.name.lowercase() },
                )
            }
        }
    }
}
