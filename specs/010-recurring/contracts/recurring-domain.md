# Contract: Recurring domain API, hooks and sync-engine additions

## 1. `RecurringService` (`feature.recurring.domain`)

```kotlin
interface RecurringService {
    /** Rules of one wallet (or all the person's wallets when null), with sync marks, next date and frozen members. */
    fun observeRules(walletId: String?): Flow<List<RuleView>>
    fun observeRule(ruleId: String): Flow<RuleView?>

    /** Occurrences held on the phone, overlays applied, states derived with the rule's today (ticks each minute). */
    fun observeOccurrences(walletId: String?, ruleId: String? = null): Flow<OccurrenceLists>   // due (missed first), upcoming, history
    fun observeOccurrence(occurrenceId: String): Flow<OccurrenceView?>

    /** Phone-side preview (FR-006); pure, for the editor and unsynced rules. */
    fun preview(draft: RuleDraft, limit: Int = 5): SchedulePreview

    suspend fun create(walletId: String, draft: RuleDraft): RuleResult           // Created(id) | NotVerified | NotAllowed | Invalid(fields)
    suspend fun save(ruleId: String, draft: RuleDraft, baseVersion: Long): RuleResult
    suspend fun changeFrom(ruleId: String, from: LocalDate, draft: RuleDraft, baseVersion: Long): RuleResult  // R-09 split
    suspend fun setStatus(ruleId: String, status: RuleStatus, baseVersion: Long): RuleResult             // pause / resume / end
    suspend fun delete(ruleId: String): RuleResult

    suspend fun confirm(occurrenceId: String, draft: ConfirmDraft): OccurrenceResult   // queues + places the pending transaction (R-07); again while waiting = change it, same transaction id
    suspend fun skip(occurrenceId: String): OccurrenceResult
    suspend fun unskip(occurrenceId: String): OccurrenceResult
    suspend fun edit(occurrenceId: String, draft: ConfirmDraft): OccurrenceResult
    suspend fun cancelConfirmation(occurrenceId: String)   // discards the waiting confirm; drops the pending transaction (FR-021)
    suspend fun dismissNotice(occurrenceId: String)

    suspend fun catchUp(ruleId: String): CatchUpOutcome   // Done | NeedsConnection | NotAllowed | NotVerified | Failed(requestId)

    // Clash and refusal choices (engine passthrough with side effects of R-07/R-09)
    suspend fun keepMine(recordType: RecurringRecord, id: String)
    suspend fun useTheirs(recordType: RecurringRecord, id: String)   // also "Accept the deletion"
    suspend fun tryAgain(recordType: RecurringRecord, id: String)
    suspend fun discard(recordType: RecurringRecord, id: String)
}
```

Rules enforced in the service (each with a test): verified address (FR-002), role Owner/Editor
and active wallet for create (FR-002), local validation (FR-005), frozen members (FR-013),
split date bounds (FR-014), ended rule read-only (FR-015), unknown values read-only (R-15),
never-sent rule delete removes locally (FR-016), confirm only on a server occurrence (FR-017),
confirmed occurrence read-only (FR-026), transaction id generated once per confirmation (FR-024).

## 2. Hooks owned by other features (bound by `recurringModule`)

| Owner package | Interface | Default | Recurring implementation |
|---|---|---|---|
| `transactions.domain` | `RecurringLinks` — `observeDue(walletId)`, `observeMatches(walletId, kind, categoryId, counterWalletId, date)`, `confirmFromDraft(occurrenceId, draft): ConfirmFromDraftResult`, `observeConfirmationStatus(transactionId)` | `NoRecurringLinks` (empty flows, `NotAvailable`) | `RecurringLinksImpl` |
| `transactions.domain` | `OccurrenceTransactions` — `placeConfirmation(...)`, `updateConfirmation(...)`, `dropConfirmation(transactionId)` | implemented by transactions itself (R-07); called by recurring | — |
| `auth.domain` | `DueSummary.observeDueCount(): Flow<Int>` | `NoDueSummary` (0) | `RecurringDueSummary` |
| `categories.domain` | `PendingCategoryUses` (now a union of all bindings) | transactions' + recurring's | `RecurringCategoryUses` |
| `wallets.domain` | `WalletDeletionParticipant` | — | `RecurringWalletCleanup` |

Matching rule for `observeMatches` (FR-031): occurrences held on the phone, same wallet, status
scheduled (after overlays, not pending confirm), same kind, same `category_id` (income/expense) or
same `counter_wallet_id` (transfer), `|date − draftDate| ≤ 7 days`, rule not ended, person can
write; ordered by distance, at most 3.

## 3. `transactions` changes

- `TransactionEntity.pendingConfirmation`; `TransactionPostings` counts pending-confirmation rows (full effect, both wallets of a transfer) in `deltaFor` and `hasPending`.
- `TransactionRepository.storeFeedCopy` / `storeServerCopy`: replacing a pending-confirmation row moves its effect into the carry (source / counter) when the stored wallet copy does not yet cover the transaction's `change_seq`.
- `TransactionsService.observeList` lists pending-confirmation rows like any transaction (status from `RecurringLinks.observeConfirmationStatus`); `observeTransaction` reports `pendingConfirmation = true` so the route opens the occurrence instead of the editor.
- The editor: "Make recurring" (create mode, writers) → `Effect.MakeRecurring(RulePrefill)`; the match offer → `RecurringLinks.confirmFromDraft(occurrenceId, draft)`, then `Effect.OpenOccurrence(occurrenceId)` replacing the editor.
- Transactions list: `State.due: List<DueItem>` rendered above the day groups; `Intent.OpenDue(id)` → `Effect.OpenOccurrence(id)`; due items never in totals, search or filters.

## 4. Sync engine additions (`core.sync`)

```kotlin
interface SyncHandler {
    // … existing members …
    /** True: this clash needs no choice; the engine applies [current], drops the change and calls onDiscarded (R-06). */
    suspend fun acceptsClash(change: PendingChange, current: JsonElement): Boolean = false
}

data class RecordKey(val recordType: String, val recordId: String, val untilSettled: Boolean = false)
```

- `settle()`: `VERSION_CONFLICT` + `current` + `acceptsClash == true` → `applyServerCopy(current)`, delete row, `onDiscarded`, log "clash accepted". Applies to per-record sends and batch items alike.
- `isHeld()`: a key with `untilSettled` holds while **any** queue row exists for it (any state), even when that row is earlier in the same request; keys without it keep spec 009's rule (create waiting and not earlier in the request).
- Parity: every existing engine test passes unchanged (defaults keep today's behaviour).

## 5. Feed appliers (`recurring.data`, wallet scope)

| Row type | Applier | Apply | Sweep (after a read from the start) |
|---|---|---|---|
| `recurring_rule` | `RecurringRuleFeedApplier` | version guard (older ignored, equal applied); tombstone removes the rule (and its occurrences without waiting changes) unless a change to it waits | remove the wallet's synced rules not seen, except with waiting changes or placeholders |
| `occurrence` | `OccurrenceFeedApplier` | version guard; tombstone removes unless a change waits; a confirmed copy whose `transaction_id` differs from a local pending confirmation of that occurrence drops the pending row (R-07) | remove the wallet's occurrences not seen, except with waiting changes |

`AppGraphTest` expects ten appliers (eight from spec 009 + two).
