package com.fares.saveme.feature.recurring.data

import com.fares.saveme.feature.recurring.data.dto.OccurrenceDto
import com.fares.saveme.feature.recurring.data.local.OccurrenceDao
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * The device's copy of occurrences. Only the server creates them (ADR-0018), so there are no
 * placeholders: a row is what the server last said, and the queue overlays it (research R-03).
 *
 * @param hasQueuedChange whether the sync queue holds a change for an occurrence.
 */
class OccurrenceRepository(
    private val occurrences: OccurrenceDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    fun observeForWallet(accountId: String, walletId: String): Flow<List<OccurrenceEntity>> = occurrences.observeForWallet(accountId, walletId)

    fun observeAll(accountId: String): Flow<List<OccurrenceEntity>> = occurrences.observeAll(accountId)

    fun observeForRule(ruleId: String): Flow<List<OccurrenceEntity>> = occurrences.observeForRule(ruleId)

    /** The rows reports read (spec 014 R-05): still scheduled in the range for the wallets, or named by a waiting change. */
    fun observeScheduledFor(accountId: String, walletIds: Set<String>, from: String, to: String, waitingIds: List<String>): Flow<List<OccurrenceEntity>> =
        occurrences.observeScheduledFor(accountId, walletIds.toList(), from, to, waitingIds)

    fun observe(id: String): Flow<OccurrenceEntity?> = occurrences.observe(id)

    suspend fun get(id: String): OccurrenceEntity? = occurrences.get(id)

    suspend fun forRule(ruleId: String): List<OccurrenceEntity> = occurrences.getForRule(ruleId)

    suspend fun forWallet(walletId: String): List<OccurrenceEntity> = occurrences.getForWallet(walletId)

    suspend fun byTransactionId(transactionId: String): List<OccurrenceEntity> = occurrences.getByTransactionId(transactionId)

    /**
     * Writes a server copy when its version is the same or newer, keeping the phone-only notice. A tombstone
     * removes the row; with [respectQueued] (the feed) it is ignored while a change to it waits. When
     * [noticeAlreadyConfirmed] is set the row also takes the "already confirmed" notice (an accepted clash,
     * research R-06). Returns true when anything changed.
     */
    suspend fun storeCopy(dto: OccurrenceDto, respectQueued: Boolean, noticeAlreadyConfirmed: Boolean = false): Boolean {
        val old = occurrences.get(dto.id)
        if (old != null && old.version > dto.version) return false
        if (dto.deletedAt != null) {
            if (respectQueued && hasQueuedChange(dto.id)) return false
            occurrences.delete(dto.id)
            return old != null
        }
        occurrences.upsert(
            dto.toEntity(accountId().orEmpty(), clock.now().toString())
                .copy(localNotice = if (noticeAlreadyConfirmed) NOTICE_ALREADY_CONFIRMED else old?.localNotice),
        )
        return true
    }

    /** The notice is cleared when the person opens the occurrence. */
    suspend fun clearNotice(id: String) = occurrences.setNotice(id, null)

    suspend fun remove(id: String) = occurrences.delete(id)

    /** Puts back a row the phone no longer holds; a row that came back meanwhile is left as it is. */
    suspend fun reinstate(entity: OccurrenceEntity) {
        if (occurrences.get(entity.id) == null) occurrences.upsert(entity)
    }

    /**
     * After a complete read of [walletId] from the start: an occurrence with no waiting change that was not
     * returned is removed (FR-007).
     */
    suspend fun sweepWallet(walletId: String, seenIds: Set<String>) {
        occurrences.getForWallet(walletId)
            .filter { it.id !in seenIds && !hasQueuedChange(it.id) }
            .forEach { occurrences.delete(it.id) }
    }

    suspend fun clear() = occurrences.deleteAll()

    companion object {
        const val NOTICE_ALREADY_CONFIRMED = "already_confirmed"
    }
}

internal fun OccurrenceDto.toEntity(accountId: String, refreshedAt: String) = OccurrenceEntity(
    id = id, accountId = accountId, walletId = walletId, recurringRuleId = recurringRuleId, ordinal = ordinal,
    isPayout = isPayout, scheduledDate = scheduledDate, date = date, status = status, kind = kind, amountMinor = amountMinor,
    currency = currency, categoryId = categoryId, counterWalletId = counterWalletId, description = description, memo = memo,
    counterparty = counterparty, edited = edited, transactionId = transactionId, autoConfirmRefusal = autoConfirmRefusal,
    version = version, changeSeq = changeSeq, createdAt = createdAt, updatedAt = updatedAt, refreshedAt = refreshedAt,
)
