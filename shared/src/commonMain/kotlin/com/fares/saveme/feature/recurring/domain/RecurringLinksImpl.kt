package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.auth.domain.DueGlimpse
import com.fares.saveme.feature.auth.domain.DueSummary
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.domain.model.ConfirmDraft
import com.fares.saveme.feature.recurring.domain.model.OccurrenceResult
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.decodeOccurrencePayload
import com.fares.saveme.feature.transactions.domain.ConfirmFromDraftResult
import com.fares.saveme.feature.transactions.domain.ConfirmationStatus
import com.fares.saveme.feature.transactions.domain.DueItem
import com.fares.saveme.feature.transactions.domain.DueState
import com.fares.saveme.feature.transactions.domain.RecurringLinks
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.math.abs

internal fun OccurrenceView.toDueItem() = DueItem(
    id = id, walletId = occurrence.walletId, ruleId = occurrence.ruleId, description = shownDescription ?: ruleDescription,
    date = shownDate, amountMinor = shownAmountMinor, currency = currency, kindWire = occurrence.kindWire,
    state = when (state) {
        OccurrenceState.Missed -> DueState.Missed
        OccurrenceState.Due -> DueState.Due
        else -> DueState.Upcoming
    },
    isPayout = isPayout,
)

/** An occurrence this close to a typed entry may be the same payment (FR-031). */
private const val MATCH_DAYS = 7

private const val MATCH_LIMIT = 3

/** What transactions needs from recurring without importing it (research R-10). */
class RecurringLinksImpl(
    private val service: RecurringService,
    private val engine: () -> SyncEngine,
) : RecurringLinks {
    override fun observeDue(walletId: String): Flow<List<DueItem>> =
        service.observeOccurrences(walletId).map { lists -> lists.due.map { it.toDueItem() } }

    /**
     * Received, still scheduled occurrences of the wallet (a waiting confirmation or skip already counts as resolved) that
     * the person can write, whose rule is not ended, of the same kind and category (destination for a transfer), dated within a
     * week of [date]; nearest first, at most three (FR-031).
     */
    override fun observeMatches(walletId: String, kindWire: String, categoryId: String?, counterWalletId: String?, date: LocalDate): Flow<List<DueItem>> =
        combine(service.observeOccurrences(walletId), service.observeRules(walletId)) { lists, ruleViews ->
            val live = ruleViews.filter { it.rule.status == RuleStatus.Active || it.rule.status == RuleStatus.Paused }.map { it.id }.toSet()
            val transfer = TransactionKind.fromWire(kindWire) == TransactionKind.Transfer
            (lists.due + lists.upcoming)
                .filter { it.canWrite && it.occurrence.ruleId in live && it.occurrence.kindWire == kindWire }
                .filter { if (transfer) counterWalletId != null && it.occurrence.counterWalletId == counterWalletId else categoryId != null && it.shownCategoryId == categoryId }
                .filter { abs(it.shownDate.toEpochDays() - date.toEpochDays()) <= MATCH_DAYS }
                .sortedWith(compareBy<OccurrenceView> { abs(it.shownDate.toEpochDays() - date.toEpochDays()) }.thenBy { it.shownDate }.thenBy { it.occurrence.ordinal }.thenBy { it.id })
                .take(MATCH_LIMIT)
                .map { it.toDueItem() }
        }

    /**
     * Confirms the occurrence with the draft's amount, date and texts; a text the person left empty keeps the occurrence's
     * own, so the produced transaction keeps its title. Nothing is recorded as a transaction by the caller.
     */
    override suspend fun confirmFromDraft(occurrenceId: String, draft: TransactionDraft): ConfirmFromDraftResult {
        val view = service.observeOccurrence(occurrenceId).first() ?: return ConfirmFromDraftResult.NotAvailable
        val confirm = ConfirmDraft(
            date = draft.date,
            amountMinor = draft.amountMinor,
            categoryId = draft.categoryId ?: view.shownCategoryId,
            description = draft.description?.takeIf { it.isNotBlank() } ?: view.shownDescription,
            memo = draft.memo?.takeIf { it.isNotBlank() } ?: view.shownMemo,
            counterparty = draft.counterparty?.takeIf { it.isNotBlank() } ?: view.shownCounterparty,
        )
        return when (service.confirm(occurrenceId, confirm)) {
            OccurrenceResult.Done -> ConfirmFromDraftResult.Confirmed(occurrenceId)
            OccurrenceResult.NotVerified -> ConfirmFromDraftResult.NotVerified
            OccurrenceResult.NotAllowed -> ConfirmFromDraftResult.NotAllowed
            else -> ConfirmFromDraftResult.NotAvailable
        }
    }

    override fun observeRuleOnPhone(ruleId: String): Flow<Boolean> = service.observeRule(ruleId).map { it != null && !it.hidden }

    override suspend fun ruleAuthorOf(ruleId: String): String? = service.observeRule(ruleId).first()?.rule?.createdBy

    override fun observeConfirmationStatus(transactionId: String): Flow<ConfirmationStatus?> =
        engine().observeAll(OccurrenceSyncHandler.RECORD_TYPE).map { changes ->
            val change = changes.firstOrNull { !it.isDelete && decodeOccurrencePayload(it.payloadJson)?.transactionId == transactionId }
            when (change?.state) {
                null -> null
                ChangeState.Waiting, ChangeState.Sending -> ConfirmationStatus.Waiting
                ChangeState.Clash -> ConfirmationStatus.Clash
                ChangeState.Refused -> ConfirmationStatus.Refused
            }
        }
}

/** Home's count: due and missed occurrences in wallets where the person is owner or editor (FR-019b). */
class RecurringDueSummary(private val service: RecurringService) : DueSummary {
    override fun observeDueCount(): Flow<Int> = service.observeOccurrences(null).map { lists -> lists.due.count { it.canWrite } }

    override fun observeFirstDue(): Flow<DueGlimpse?> = service.observeOccurrences(null).map { lists ->
        lists.due.firstOrNull { it.canWrite }?.let { DueGlimpse(it.ruleDescription?.takeIf { text -> text.isNotBlank() }, it.shownDate) }
    }
}
