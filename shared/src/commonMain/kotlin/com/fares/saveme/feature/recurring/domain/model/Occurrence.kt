package com.fares.saveme.feature.recurring.domain.model

import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** An occurrence as the server last said it; only the server creates one (ADR-0018). */
data class Occurrence(
    val id: String,
    val walletId: String,
    val ruleId: String,
    val ordinal: Int,
    val isPayout: Boolean,
    val scheduledDate: LocalDate,
    val date: LocalDate,
    val statusWire: String,
    val kindWire: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val edited: Boolean,
    val transactionId: String?,
    val autoConfirmRefusalWire: String?,
    val version: Long,
    val updatedAt: Instant,
    /** Phone-only: `already_confirmed` after an accepted clash (research R-06). */
    val localNotice: String?,
) {
    val status: OccurrenceStatus get() = OccurrenceStatus.fromWire(statusWire)
    val kind: TransactionKind get() = TransactionKind.fromWire(kindWire)
    val autoConfirmRefusal: AutoConfirmRefusal? get() = autoConfirmRefusalWire?.let(AutoConfirmRefusal::fromWire)

    override fun toString() = "Occurrence($id, version=$version)"
}

/**
 * What the person enters to confirm or edit one occurrence (FR-021, FR-023): date, amount, category, texts; kind,
 * currency and destination are fixed.
 */
data class ConfirmDraft(
    val date: LocalDate,
    val amountMinor: Long,
    val categoryId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
) {
    override fun toString() = "ConfirmDraft"
}

/** The content a waiting occurrence change holds, decoded for the screens. */
data class OccurrencePending(
    val status: OccurrenceStatus,
    val date: LocalDate,
    val amountMinor: Long,
    val categoryId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    /** Set while a confirmation waits: the id of the transaction it will record. */
    val transactionId: String?,
) {
    val isConfirmation: Boolean get() = status == OccurrenceStatus.Confirmed
    override fun toString() = "OccurrencePending($status)"
}

/** The occurrences of one view, split the way the screens list them (contract: due with missed first, upcoming, history). */
data class OccurrenceLists(
    val due: List<OccurrenceView> = emptyList(),
    val upcoming: List<OccurrenceView> = emptyList(),
    val history: List<OccurrenceView> = emptyList(),
) {
    val isEmpty: Boolean get() = due.isEmpty() && upcoming.isEmpty() && history.isEmpty()
}
