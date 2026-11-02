package com.fares.saveme.feature.sharedbudgets.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.sharedbudgets.data.dto.LimitStepDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetEntryDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetLineDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetMemberDto
import com.fares.saveme.feature.sharedbudgets.data.dto.StartDayStepDto
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetDao
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntryEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetLineEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetMemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock

/**
 * The device's copy of the Shared Budgets the person belongs to. No decisions here (P-10.6): reads give rows and the domain
 * decides what to do with them. Everything arrives through feeds (the budget's own, and the person's membership rows on their
 * user feed) and the sync engine's answers. An ended record is removed, never kept: a budget the person no longer belongs to
 * leaves the phone with its lines, members and entries (backend ADR-0027 rule 9).
 *
 * @param hasQueuedChange whether the sync queue still holds a change for a budget; such a budget is never removed by a read.
 */
class SharedBudgetRepository(
    private val dao: SharedBudgetDao,
    private val scopes: SyncScopeDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    fun observeMine(userId: String): Flow<List<SharedBudgetEntity>> = dao.observeMine(userId)

    fun observe(id: String): Flow<SharedBudgetEntity?> = dao.observe(id)

    fun observeMembers(budgetId: String): Flow<List<SharedBudgetMemberEntity>> = dao.observeMembers(budgetId)

    fun observeLines(budgetId: String): Flow<List<SharedBudgetLineEntity>> = dao.observeLines(budgetId)

    fun observeOwnMembers(userId: String): Flow<List<SharedBudgetMemberEntity>> = dao.observeOwnMembers(userId)

    fun observeEntriesSince(from: String): Flow<List<SharedBudgetEntryEntity>> = dao.observeEntriesSince(from)

    fun observeAllLines(): Flow<List<SharedBudgetLineEntity>> = dao.observeAllLines()

    fun observeEntries(budgetId: String, from: String, to: String): Flow<List<SharedBudgetEntryEntity>> = dao.observeEntries(budgetId, from, to)

    suspend fun get(id: String): SharedBudgetEntity? = dao.get(id)

    suspend fun getLine(id: String): SharedBudgetLineEntity? = dao.line(id)

    suspend fun lines(budgetId: String): List<SharedBudgetLineEntity> = dao.lines(budgetId)

    /**
     * A budget made on this phone before the server has seen it: `version = 0`, listed to its owner, held by the sync queue.
     * The owner's own membership arrives from the server with the first answer.
     */
    suspend fun insertPlaceholder(id: String, ownerId: String, name: String, currency: String, startDay: Int, limitMinor: Long?, timezone: String) {
        val now = clock.now().toString()
        dao.upsert(
            SharedBudgetEntity(
                id = id, ownerId = ownerId, name = name, currency = currency, startDay = startDay, timezone = timezone, limitMinor = limitMinor,
                archivedAt = null, limitHistory = "[]", startDayHistory = "[]", version = 0, changeSeq = 0, createdAt = now, updatedAt = now, refreshedAt = now,
            ),
        )
    }

    /** Removes a placeholder that never reached the server; nothing else holds data for it. */
    suspend fun removePlaceholder(id: String) {
        if (dao.get(id)?.version == 0L) dao.deleteBudgetWithChildren(id)
    }

    /** Writes the server's answer to this phone's own write; a tombstone removes the budget with everything under it. */
    suspend fun storeServerCopy(dto: SharedBudgetDto): Boolean {
        if (dto.deletedAt != null) {
            dao.deleteBudgetWithChildren(dto.id)
            return true
        }
        dao.upsert(dto.toEntity(clock.now().toString()))
        return false
    }

    /** A budget from its own feed: when its version is the same or newer; a deletion removes it unless a change to it waits. */
    suspend fun storeFeedCopy(dto: SharedBudgetDto): Boolean {
        if (dto.deletedAt != null) {
            if (hasQueuedChange(dto.id)) return false
            dao.deleteBudgetWithChildren(dto.id)
            return true
        }
        val stored = dao.get(dto.id)
        if (stored != null && stored.version > dto.version) return false
        dao.upsert(dto.toEntity(clock.now().toString()))
        return true
    }

    /** A member from the budget's feed; an ended membership removes only that member. */
    suspend fun storeMember(dto: SharedBudgetMemberDto) {
        if (dto.deletedAt != null) {
            dao.deleteMember(dto.id)
            return
        }
        val stored = dao.member(dto.id)
        if (stored != null && stored.version > dto.version) return
        dao.upsertMember(dto.toEntity())
    }

    /**
     * The person's own membership from their user feed. A live one keeps the budget followed; an ended one removes the budget
     * and everything of it from the phone, except a budget made on this phone and not yet accepted, and the budget record
     * itself while a change to it waits.
     */
    suspend fun storeOwnMembership(dto: SharedBudgetMemberDto) {
        if (dto.deletedAt == null) {
            storeMember(dto)
            return
        }
        dao.deleteMember(dto.id)
        accessLost(dto.sharedBudgetId)
    }

    suspend fun storeLine(dto: SharedBudgetLineDto) {
        if (dto.deletedAt != null) {
            dao.deleteLine(dto.id)
            return
        }
        val stored = dao.line(dto.id)
        if (stored != null && stored.version > dto.version) return
        dao.upsertLine(dto.toEntity())
    }

    suspend fun storeEntry(dto: SharedBudgetEntryDto) {
        if (dto.deletedAt != null) {
            dao.deleteEntry(dto.id)
            return
        }
        val stored = dao.entry(dto.id)
        if (stored != null && stored.version > dto.version) return
        dao.upsertEntry(dto.toEntity())
    }

    /** After a complete read from the start of one budget's feed: the budget not returned is gone, as a `404` says. */
    suspend fun sweepBudget(budgetId: String, seenIds: Set<String>) {
        val stored = dao.get(budgetId) ?: return
        if (stored.version > 0 && budgetId !in seenIds && !hasQueuedChange(budgetId)) accessLost(budgetId)
    }

    suspend fun sweepMembers(budgetId: String, seenIds: Set<String>) {
        dao.members(budgetId).filter { it.id !in seenIds }.forEach { dao.deleteMember(it.id) }
    }

    suspend fun sweepLines(budgetId: String, seenIds: Set<String>) {
        dao.lines(budgetId).filter { it.id !in seenIds }.forEach { dao.deleteLine(it.id) }
    }

    suspend fun sweepEntries(budgetId: String, seenIds: Set<String>) {
        dao.entries(budgetId).filter { it.id !in seenIds }.forEach { dao.deleteEntry(it.id) }
    }

    /** Budgets to read: the server's own, plus every live own membership. */
    suspend fun followedIds(userId: String): List<String> =
        (dao.getAll().filter { it.version > 0 }.map { it.id } + dao.ownBudgetIds(userId)).distinct()

    suspend fun isAccepted(budgetId: String, userId: String): Boolean =
        (dao.get(budgetId)?.version ?: 0L) > 0 || dao.ownMember(budgetId, userId) != null

    suspend fun heldIds(): List<String> = dao.getAll().filter { it.version > 0 }.map { it.id }

    /** The person no longer belongs: the budget, its position and everything under it go (a placeholder is kept). */
    suspend fun accessLost(budgetId: String) {
        val row = dao.get(budgetId)
        if (row != null && row.version == 0L) return
        scopes.clearFeed(SyncScope(ScopeKind.SharedBudget, budgetId).key)
        if (row != null && hasQueuedChange(budgetId)) return
        dao.deleteBudgetWithChildren(budgetId)
    }

    suspend fun clear() = dao.deleteEverything()
}

internal val limitSteps = ListSerializer(LimitStepDto.serializer())
internal val startDaySteps = ListSerializer(StartDayStepDto.serializer())

internal fun SharedBudgetDto.toEntity(refreshedAt: String) = SharedBudgetEntity(
    id = id, ownerId = ownerId, name = name, currency = currency, startDay = startDay, timezone = timezone, limitMinor = limitMinor,
    archivedAt = archivedAt, limitHistory = AppJson.encodeToString(limitSteps, limitHistory),
    startDayHistory = AppJson.encodeToString(startDaySteps, startDayHistory), version = version, changeSeq = changeSeq,
    createdAt = createdAt, updatedAt = updatedAt, refreshedAt = refreshedAt,
)

internal fun SharedBudgetMemberDto.toEntity() = SharedBudgetMemberEntity(
    id = id, sharedBudgetId = sharedBudgetId, userId = userId, displayName = displayName, role = role, readOnly = readOnly,
    version = version, changeSeq = changeSeq, updatedAt = updatedAt,
)

internal fun SharedBudgetLineDto.toEntity() = SharedBudgetLineEntity(
    id = id, sharedBudgetId = sharedBudgetId, categoryId = categoryId, limitMinor = limitMinor, name = name, icon = icon, color = color,
    limitHistory = AppJson.encodeToString(limitSteps, limitHistory), version = version, changeSeq = changeSeq, updatedAt = updatedAt,
)

internal fun SharedBudgetEntryDto.toEntity() = SharedBudgetEntryEntity(
    id = id, sharedBudgetId = sharedBudgetId, createdBy = createdBy, categoryId = categoryId, date = date, amountMinor = amountMinor,
    description = description, detachedAt = detachedAt, version = version, changeSeq = changeSeq, updatedAt = updatedAt,
)
