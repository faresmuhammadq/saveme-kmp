package com.fares.saveme.feature.recurring.data

import com.fares.saveme.feature.recurring.data.dto.RecurringRuleDto
import com.fares.saveme.feature.recurring.data.local.OccurrenceDao
import com.fares.saveme.feature.recurring.data.local.RecurringRuleDao
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/** A rule made on this phone, before the server has answered (research R-03). */
data class NewRule(
    val id: String,
    val accountId: String,
    val walletId: String,
    val preset: String,
    val status: String,
    val kind: String,
    val amountMinor: Long,
    val amountBasis: String,
    val currency: String,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val autoConfirm: Boolean,
    val frequency: String,
    val interval: Int,
    val anchorDay: Int?,
    val startDate: String,
    val endDate: String?,
    val occurrenceCount: Int?,
    val payoutRound: Int?,
    val payoutCategoryId: String?,
    val followsRuleId: String?,
) {
    override fun toString() = "NewRule($id)"
}

/**
 * The device's copy of recurring rules, kept current from the wallets' change feeds and the engine's
 * answers (research R-03). No decisions here (P-10.6). The server copy is what the server last said; the
 * queue overlays it.
 *
 * @param hasQueuedChange whether the sync queue holds a change for a rule.
 * @param hasQueuedOccurrenceChange the same for an occurrence, so a rule's tombstone keeps occurrences that
 *   still have a change waiting.
 */
class RecurringRepository(
    private val rules: RecurringRuleDao,
    private val occurrences: OccurrenceDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val hasQueuedOccurrenceChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    fun observeForWallet(accountId: String, walletId: String): Flow<List<RecurringRuleEntity>> = rules.observeForWallet(accountId, walletId)

    fun observeAll(accountId: String): Flow<List<RecurringRuleEntity>> = rules.observeAll(accountId)

    fun observe(id: String): Flow<RecurringRuleEntity?> = rules.observe(id)

    suspend fun get(id: String): RecurringRuleEntity? = rules.get(id)

    suspend fun forWallet(walletId: String): List<RecurringRuleEntity> = rules.getForWallet(walletId)

    /** The unsent rules that continue [ruleId] (research R-09). */
    suspend fun following(ruleId: String): List<RecurringRuleEntity> = rules.getFollowing(ruleId)

    /** Writes the phone's own rule with `version = 0`; rewriting an unsent rule keeps its creation time. */
    suspend fun putPlaceholder(new: NewRule) {
        val now = clock.now().toString()
        val old = rules.get(new.id)
        rules.upsert(
            RecurringRuleEntity(
                id = new.id, accountId = new.accountId, walletId = new.walletId, preset = new.preset, status = new.status,
                kind = new.kind, amountMinor = new.amountMinor, amountBasis = new.amountBasis, currency = new.currency,
                categoryId = new.categoryId, counterWalletId = new.counterWalletId, description = new.description,
                memo = new.memo, counterparty = new.counterparty, autoConfirm = new.autoConfirm, frequency = new.frequency,
                interval = new.interval, anchorDay = new.anchorDay, startDate = new.startDate, endDate = new.endDate,
                occurrenceCount = new.occurrenceCount, payoutRound = new.payoutRound, payoutCategoryId = new.payoutCategoryId,
                timezone = null, createdBy = null, version = 0, changeSeq = 0, createdAt = old?.createdAt ?: now,
                updatedAt = now, refreshedAt = now, followsRuleId = new.followsRuleId,
            ),
        )
    }

    /**
     * Writes a server copy when its version is the same or newer. A tombstone removes the rule and the
     * occurrences without a waiting change; with [respectQueued] (the feed) it is ignored while a change to
     * the rule waits. Returns true when anything changed.
     */
    suspend fun storeCopy(dto: RecurringRuleDto, respectQueued: Boolean): Boolean {
        val old = rules.get(dto.id)
        if (old != null && old.version > dto.version) return false
        if (dto.deletedAt != null) {
            if (respectQueued && hasQueuedChange(dto.id)) return false
            remove(dto.id)
            return old != null
        }
        rules.upsert(dto.toEntity(accountId().orEmpty(), clock.now().toString()))
        return true
    }

    /** Removes a rule and every occurrence of it that has no waiting change. */
    suspend fun remove(id: String) {
        for (occurrence in occurrences.getForRule(id)) {
            if (!hasQueuedOccurrenceChange(occurrence.id)) occurrences.delete(occurrence.id)
        }
        rules.delete(id)
    }

    /** Removes a rule that never reached the server; nothing else holds data for it. */
    suspend fun removePlaceholder(id: String) {
        if (rules.get(id)?.version == 0L) rules.delete(id)
    }

    /** Puts back a row the phone no longer holds; a row that came back meanwhile is left as it is. */
    suspend fun reinstate(entity: RecurringRuleEntity) {
        if (rules.get(entity.id) == null) rules.upsert(entity)
    }

    /** Removes every rule and occurrence of a wallet the person no longer has. */
    suspend fun clearWallet(walletId: String) {
        occurrences.deleteForWallet(walletId)
        rules.deleteForWallet(walletId)
    }

    /**
     * After a complete read of [walletId] from the start: a rule that reached the server, has no waiting
     * change and was not returned is removed (FR-007). Placeholders stay.
     */
    suspend fun sweepWallet(walletId: String, seenIds: Set<String>) {
        rules.getForWallet(walletId)
            .filter { it.id !in seenIds && it.version > 0 && !hasQueuedChange(it.id) }
            .forEach { remove(it.id) }
    }

    suspend fun clear() {
        occurrences.deleteAll()
        rules.deleteAll()
    }
}

internal fun RecurringRuleDto.toEntity(accountId: String, refreshedAt: String) = RecurringRuleEntity(
    id = id, accountId = accountId, walletId = walletId, preset = preset, status = status, kind = kind, amountMinor = amountMinor,
    amountBasis = amountBasis, currency = currency, categoryId = categoryId, counterWalletId = counterWalletId,
    description = description, memo = memo, counterparty = counterparty, autoConfirm = autoConfirm, frequency = frequency,
    interval = interval, anchorDay = anchorDay, startDate = startDate, endDate = endDate, occurrenceCount = occurrenceCount,
    payoutRound = payoutRound, payoutCategoryId = payoutCategoryId, timezone = timezone, createdBy = createdBy,
    version = version, changeSeq = changeSeq, createdAt = createdAt, updatedAt = updatedAt, refreshedAt = refreshedAt,
    followsRuleId = null,
)
