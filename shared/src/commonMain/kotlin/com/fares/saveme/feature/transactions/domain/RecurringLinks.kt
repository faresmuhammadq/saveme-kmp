package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/** Where a due item stands (spec 010 FR-018). */
enum class DueState { Due, Missed, Upcoming }

/** One occurrence as the transactions screens show it; never a transaction, never in a balance or total (FR-020). */
data class DueItem(
    val id: String,
    val walletId: String,
    val ruleId: String,
    val description: String?,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    /** `income`, `expense` or `transfer`. */
    val kindWire: String,
    val state: DueState,
    val isPayout: Boolean = false,
) {
    override fun toString() = "DueItem($id)"
}

/** Where the phone-only transaction of a waiting confirmation stands, for its row (research R-07). */
enum class ConfirmationStatus { Waiting, Clash, Refused }

sealed interface ConfirmFromDraftResult {
    data class Confirmed(val occurrenceId: String) : ConfirmFromDraftResult
    data object NotVerified : ConfirmFromDraftResult
    data object NotAllowed : ConfirmFromDraftResult
    data object NotAvailable : ConfirmFromDraftResult
}

/**
 * What "Make recurring" hands over: the editor's form as typed (amount 0 when blank). The navigation layer turns it into
 * the recurring editor's prefill, so transactions never imports recurring (research R-10). Redacted in [toString] (P-09.2).
 */
data class MakeRecurringDraft(
    val walletId: String,
    val kind: com.fares.saveme.feature.transactions.domain.model.TransactionKind,
    val amountMinor: Long,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val date: LocalDate,
) {
    override fun toString() = "MakeRecurringDraft($walletId)"
}

/**
 * What transactions needs from recurring without importing it (spec 010 research R-10, P-10.4). Transactions binds
 * [NoRecurringLinks]; the recurring module rebinds it.
 */
interface RecurringLinks {
    /** Due and missed occurrences of one wallet, missed first then oldest (FR-019a). */
    fun observeDue(walletId: String): Flow<List<DueItem>>

    /** Received, unconfirmed occurrences of the wallet that a hand entry may duplicate (FR-031). */
    fun observeMatches(walletId: String, kindWire: String, categoryId: String?, counterWalletId: String?, date: LocalDate): Flow<List<DueItem>>

    /** "Confirm … instead": confirms the occurrence with the draft's amount, date and texts; nothing is recorded as a transaction. */
    suspend fun confirmFromDraft(occurrenceId: String, draft: TransactionDraft): ConfirmFromDraftResult

    /** The occurrence status of a phone-only confirmation row; null when it has none. */
    fun observeConfirmationStatus(transactionId: String): Flow<ConfirmationStatus?>

    /** True while the recurring rule is on this phone, so a produced transaction can offer "Open the rule" (FR-038). */
    fun observeRuleOnPhone(ruleId: String): Flow<Boolean> = flowOf(false)
}

/** Until recurring is bound: nothing is due and nothing matches. */
object NoRecurringLinks : RecurringLinks {
    override fun observeDue(walletId: String): Flow<List<DueItem>> = flowOf(emptyList())
    override fun observeMatches(walletId: String, kindWire: String, categoryId: String?, counterWalletId: String?, date: LocalDate): Flow<List<DueItem>> = flowOf(emptyList())
    override suspend fun confirmFromDraft(occurrenceId: String, draft: TransactionDraft): ConfirmFromDraftResult = ConfirmFromDraftResult.NotAvailable
    override fun observeConfirmationStatus(transactionId: String): Flow<ConfirmationStatus?> = flowOf(null)
}
