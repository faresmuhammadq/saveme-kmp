package com.fares.saveme.feature.recurring.ui

import com.fares.saveme.feature.recurring.domain.model.FrozenMembers
import com.fares.saveme.feature.recurring.domain.model.Occurrence
import com.fares.saveme.feature.recurring.domain.model.OccurrencePending
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.Rule
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.toDraft
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

val uiNow: Instant = Instant.parse("2026-10-01T00:00:00Z")
val uiToday: LocalDate = LocalDate(2026, 10, 3)

fun testRule(
    id: String = "r1",
    walletId: String = "w1",
    preset: String = "custom",
    status: String = "active",
    kind: String = "expense",
    amountMinor: Long = 450_000,
    basis: String = "per_occurrence",
    categoryId: String? = "c1",
    counterWalletId: String? = null,
    description: String? = "Rent",
    frequency: String = "monthly",
    interval: Int = 1,
    anchorDay: Int? = 1,
    startDate: LocalDate = LocalDate(2026, 9, 1),
    endDate: LocalDate? = null,
    count: Int? = null,
    payoutRound: Int? = null,
    payoutCategoryId: String? = null,
    version: Long = 3,
) = Rule(
    id, walletId, preset, status, kind, amountMinor, basis, "EGP", categoryId, counterWalletId, description, null, null, false,
    frequency, interval, anchorDay, startDate, endDate, count, payoutRound, payoutCategoryId, "Africa/Cairo", "u1", version, uiNow, uiNow,
)

fun testRuleView(
    rule: Rule = testRule(),
    shown: RuleDraft = rule.toDraft(),
    syncState: RecurringSyncState = RecurringSyncState.None,
    readOnly: Boolean = false,
    frozen: FrozenMembers = FrozenMembers(),
    role: WalletRole? = WalletRole.Owner,
    nextDate: LocalDate? = LocalDate(2026, 11, 1),
) = RuleView(
    rule = rule, shown = shown, hasPendingChange = false, pendingDelete = false, syncState = syncState, nextDate = nextDate,
    today = uiToday, readOnly = readOnly, frozen = frozen, myRole = role, walletName = "Cash", hasOccurrences = true,
)

fun testOccurrence(
    id: String = "o1",
    ruleId: String = "r1",
    walletId: String = "w1",
    status: String = "scheduled",
    kind: String = "expense",
    date: LocalDate = LocalDate(2026, 10, 1),
    amountMinor: Long = 450_000,
    categoryId: String? = "c1",
    counterWalletId: String? = null,
    transactionId: String? = null,
    refusal: String? = null,
    notice: String? = null,
    payout: Boolean = false,
) = Occurrence(
    id, walletId, ruleId, 2, payout, date, date, status, kind, amountMinor, "EGP", categoryId, counterWalletId, "Rent", null, null,
    false, transactionId, refusal, 2, uiNow, notice,
)

fun testOccurrenceView(
    occurrence: Occurrence = testOccurrence(),
    state: OccurrenceState = OccurrenceState.Due,
    pending: OccurrencePending? = null,
    syncState: RecurringSyncState = RecurringSyncState.None,
    canWrite: Boolean = true,
) = OccurrenceView(
    occurrence = occurrence, pending = pending, state = state, ruleDescription = "Rent", syncState = syncState,
    shownDate = pending?.date ?: occurrence.date, shownAmountMinor = pending?.amountMinor ?: occurrence.amountMinor,
    shownCategoryId = if (pending != null) pending.categoryId else occurrence.categoryId,
    shownDescription = if (pending != null) pending.description else occurrence.description,
    shownMemo = if (pending != null) pending.memo else occurrence.memo,
    shownCounterparty = if (pending != null) pending.counterparty else occurrence.counterparty,
    currency = occurrence.currency, canWrite = canWrite, notice = occurrence.localNotice,
)
