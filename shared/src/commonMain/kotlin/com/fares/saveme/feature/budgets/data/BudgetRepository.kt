package com.fares.saveme.feature.budgets.data

import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import com.fares.saveme.feature.budgets.data.local.BudgetDao
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * The device's copy of the person's own budgets. No decisions here (P-10.6): reads give rows and the domain decides what
 * to do with them. Budgets reach the phone only through the user feed (spec 009) and the sync engine's answers.
 *
 * @param hasQueuedChange whether the sync queue still holds a change for a budget; such a budget is never removed by a
 *   read (FR-019).
 */
class BudgetRepository(
    private val budgets: BudgetDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    fun observeAll(accountId: String): Flow<List<BudgetEntity>> = budgets.observeAll(accountId)

    fun observe(id: String): Flow<BudgetEntity?> = budgets.observe(id)

    suspend fun get(id: String): BudgetEntity? = budgets.get(id)

    suspend fun getAll(): List<BudgetEntity> = budgets.getAll()

    /** A budget made on this phone: version 0, until the server answers. */
    suspend fun insertPlaceholder(id: String, accountId: String, categoryId: String?, currency: String, amountMinor: Long) {
        val now = clock.now().toString()
        budgets.upsert(
            BudgetEntity(
                id = id, accountId = accountId, categoryId = categoryId, periodType = MONTHLY, amountMinor = amountMinor,
                currency = currency, archivedAt = null, version = 0, changeSeq = 0, createdAt = now, updatedAt = now,
                refreshedAt = now,
            ),
        )
    }

    /** Writes the server's answer to this phone's own write or a clash's "use theirs" copy; a tombstone removes the budget. */
    suspend fun storeServerCopy(dto: BudgetDto): Boolean {
        if (dto.deletedAt != null) {
            budgets.deleteWithProgress(dto.id)
            return true
        }
        budgets.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        return false
    }

    /** Removes a budget (and its stored progress). */
    suspend fun remove(id: String) = budgets.deleteWithProgress(id)

    /** Removes a placeholder that never reached the server; nothing else holds data for it. */
    suspend fun removePlaceholder(id: String) {
        if (budgets.get(id)?.version == 0L) budgets.deleteWithProgress(id)
    }

    suspend fun clear() = budgets.deleteAll()

    /**
     * Applies a budget from the person's change feed (spec 009 FR-004, FR-005, FR-007): when its version is the same or
     * newer; a deletion removes it (with its stored progress) unless a change to it waits (it stays until the person
     * settles that change). Returns true when anything changed.
     */
    suspend fun storeFeedCopy(dto: BudgetDto): Boolean {
        if (dto.deletedAt != null) {
            if (hasQueuedChange(dto.id)) return false
            budgets.deleteWithProgress(dto.id)
            return true
        }
        val stored = budgets.get(dto.id)
        if (stored != null && stored.version > dto.version) return false
        budgets.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        return true
    }

    /**
     * After a complete read of the person's scope from the start: a synced budget the server did not return and that has
     * no waiting change is removed. A never-synced placeholder is always kept (FR-019).
     */
    suspend fun sweepOwn(seenIds: Set<String>) {
        budgets.getAll()
            .filter { it.id !in seenIds && it.version > 0 && !hasQueuedChange(it.id) }
            .forEach { budgets.deleteWithProgress(it.id) }
    }

    companion object {
        const val MONTHLY = "monthly"
    }
}

internal fun BudgetDto.toEntity(accountId: String, refreshedAt: String) = BudgetEntity(
    id = id, accountId = accountId, categoryId = categoryId, periodType = periodType, amountMinor = amountMinor,
    currency = currency, archivedAt = archivedAt, version = version, changeSeq = changeSeq, createdAt = createdAt,
    updatedAt = updatedAt, refreshedAt = refreshedAt,
)
