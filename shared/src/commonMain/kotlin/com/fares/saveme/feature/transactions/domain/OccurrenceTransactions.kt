package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/** What a recurring confirmation shows as a transaction while the server has not answered (spec 010 R-07). */
data class PendingConfirmation(
    /** The final id of the transaction the server will record; the server's copy replaces the row in place. */
    val transactionId: String,
    val walletId: String,
    val counterWalletId: String?,
    /** `income`, `expense` or `transfer`. */
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val categoryId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val ruleId: String,
    val occurrenceId: String,
) {
    /** Amounts, notes and people are the person's own; they are never printed (P-09.2). */
    override fun toString() = "PendingConfirmation($transactionId)"
}

/**
 * The transactions side of a recurring confirmation (spec 010 research R-07), implemented by
 * transactions and called by recurring. The row is phone-only (`pending_confirmation`, `version = 0`),
 * counts in the balance at once and is never queued as a transaction change (ADR-0018 rule 6).
 */
interface OccurrenceTransactions {
    /** Inserts the row; it counts in the wallets' balances. */
    suspend fun placeConfirmation(confirmation: PendingConfirmation)

    /** Rewrites the row while the confirmation waits (the person edits it); keeps the id. */
    suspend fun updateConfirmation(confirmation: PendingConfirmation)

    /** Deletes the row if it is still phone-only (`version = 0`); a server copy is left alone. */
    suspend fun dropConfirmation(transactionId: String)

    /**
     * Deletes the phone-only confirmation row of an occurrence (`pending_confirmation`, `version = 0`),
     * unless its id is [exceptTransactionId] (the server confirmed under that id). Used when the
     * confirmation was discarded or lost a clash, and when another device confirmed under another id.
     */
    suspend fun dropConfirmationOf(occurrenceId: String, exceptTransactionId: String? = null)

    /**
     * After a complete read of [walletId]: a confirmation row still phone-only whose id is in
     * [confirmedTransactionIds] (the occurrence is confirmed with it) never got its transaction from the server,
     * so it was deleted meanwhile; the row goes.
     */
    suspend fun sweepConfirmations(walletId: String, confirmedTransactionIds: Set<String>)
}

/** The real [OccurrenceTransactions]: a phone-only row in the `transactions` table, counted by [TransactionPostings]. */
class DefaultOccurrenceTransactions(
    private val repository: TransactionRepository,
    private val accountId: () -> String?,
    private val clock: Clock = Clock.System,
) : OccurrenceTransactions {
    override suspend fun placeConfirmation(confirmation: PendingConfirmation) {
        val account = accountId() ?: return
        val now = clock.now().toString()
        repository.placeConfirmation(
            TransactionEntity(
                id = confirmation.transactionId, accountId = account, walletId = confirmation.walletId,
                counterWalletId = confirmation.counterWalletId, kind = confirmation.kind, date = confirmation.date.toString(),
                amountMinor = confirmation.amountMinor, currency = confirmation.currency, categoryId = confirmation.categoryId,
                description = confirmation.description, memo = confirmation.memo, counterparty = confirmation.counterparty,
                cleared = false, createdBy = account, recurringRuleId = confirmation.ruleId, occurrenceId = confirmation.occurrenceId,
                version = 0, changeSeq = 0, createdAt = now, updatedAt = now, deletedAt = null, refreshedAt = now,
                pendingConfirmation = true,
            ),
        )
    }

    override suspend fun updateConfirmation(confirmation: PendingConfirmation) {
        val old = repository.get(confirmation.transactionId)
        // Only a row that is still phone-only changes; a server copy is not rewritten.
        if (old == null || (old.pendingConfirmation && old.version == 0L)) placeConfirmation(confirmation)
    }

    override suspend fun dropConfirmation(transactionId: String) = repository.dropConfirmation(transactionId)

    override suspend fun dropConfirmationOf(occurrenceId: String, exceptTransactionId: String?) =
        repository.dropConfirmationOf(occurrenceId, exceptTransactionId)

    override suspend fun sweepConfirmations(walletId: String, confirmedTransactionIds: Set<String>) =
        repository.sweepConfirmations(walletId, confirmedTransactionIds)
}
