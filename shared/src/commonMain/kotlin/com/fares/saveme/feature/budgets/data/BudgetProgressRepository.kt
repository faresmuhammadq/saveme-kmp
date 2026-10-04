package com.fares.saveme.feature.budgets.data

import com.fares.saveme.feature.budgets.data.dto.BudgetProgressDto
import com.fares.saveme.feature.budgets.data.local.BudgetProgressDao
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import kotlinx.coroutines.flow.Flow

/** The stored copy of the server's progress figures (research R-06, R-09). No decisions here (P-10.6). */
class BudgetProgressRepository(private val progress: BudgetProgressDao) {
    fun observeAll(accountId: String): Flow<List<BudgetProgressEntity>> = progress.observeAll(accountId)

    suspend fun get(budgetId: String): BudgetProgressEntity? = progress.get(budgetId)

    /** Stores one row (a per-budget read). */
    suspend fun store(row: BudgetProgressEntity) = progress.upsertAll(listOf(row))

    /** A completed walk (research R-09): replaces the rows of every non-archived budget and keeps the archived ones'. */
    suspend fun replaceWalk(accountId: String, rows: List<BudgetProgressEntity>, archivedIds: List<String>) =
        progress.replaceWalk(accountId, rows, archivedIds)

    suspend fun clear() = progress.deleteAll()
}

/**
 * The row to store for one progress answer: the server's figures and period as read, and the phone's synced-only
 * figures at that moment (research R-06). Only what the phone cannot re-derive is kept (data-model §2).
 */
fun BudgetProgressDto.toStored(accountId: String, localSpentMinor: Long, localOtherCurrencies: List<String>, readAt: String) =
    BudgetProgressEntity(
        budgetId = budgetId, accountId = accountId, periodStart = period.start, periodEnd = period.end,
        daysInPeriod = period.daysInPeriod, limitMinor = limit.amountMinor, spentMinor = spent.amountMinor,
        currency = limit.currency, otherCurrencies = otherCurrencies.sorted().joinToString(","),
        localSpentMinor = localSpentMinor, localOtherCurrencies = localOtherCurrencies.sorted().joinToString(","),
        readAt = readAt,
    )
