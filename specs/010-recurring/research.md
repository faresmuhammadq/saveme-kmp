# Research: Recurring Transactions (client)

Phase 0 of the plan. Every unknown in the Technical Context is resolved here; each decision is an
assumed default (owner away with delegated authority) unless the spec's clarifications fixed it.
Paths are relative to `shared/src/commonMain/kotlin/com/fares/saveme/`.

## R-01 Package and boundaries

**Decision**: one new bounded context `feature.recurring` with `data`, `domain`, `ui`, `di`.
`recurring.domain` imports `core`, `auth.domain` (verified address, profile time zone through the
existing account API), `wallets.domain` (wallet views, roles, `WalletDeletionParticipant`,
`AmountInput`), `categories.domain` (choices, displays, `PendingCategoryUses`) and
`transactions.domain` (destination choices, the occurrence-transaction API of R-07, the hooks of
R-10). `recurring.data` imports no `domain`, no `ui` and no other feature (payloads and field
names live in `data`, as specs 007–008). `transactions`, `wallets`, `categories` and `auth` never
import `recurring`: what they need from it goes through hook interfaces they own (R-10).
**Rationale**: P-10.3, P-10.4, P-10.6; one-way graph (recurring sits above transactions). The
backend keeps rules in Wallets & Ledger, but the client already split wallets and transactions.
**Alternatives**: inside `feature.transactions` — rejected: four new screens, two tables and two
sync handlers would double a package that C8/C13 consume separately.

## R-02 Room v7: two tables, one migration that also forgets wallet positions

**Decision**: tables `recurring_rule` and `occurrence` with every wire field plus `account_id`,
`refreshed_at`; `recurring_rule.follows_rule_id` (phone-only, R-09) and
`occurrence.local_notice` (phone-only, R-06). Indexes: `recurring_rule(wallet_id, status)`,
`occurrence(wallet_id, date)`, `occurrence(recurring_rule_id, date)`, `occurrence(transaction_id)`.
`MIGRATION_6_7` creates both and runs
`UPDATE sync_scope SET feed_cursor = NULL, feed_read_at = NULL WHERE scope LIKE 'wallet:%'`
once (FR-030). Schema `7.json` exported; `DatabaseCleaner` clears both tables.
**Rationale**: spec 009 skipped these rows and advanced positions, so only a read from the start
can bring existing rules and occurrences (spec 009 assumed default 9). Keeping the copy while the
scope is re-read is spec 009's FR-007 behaviour, so nothing empties on screen; the wallet list's
"Updated …" falls back to the legacy stamp until each read completes (spec 009 implementation
note).
**Alternatives**: storing skipped rows raw in 009 — already rejected there; clearing the user
scope too — unnecessary (rules and occurrences ride wallet feeds only).

## R-03 Local row = server copy; pending content overlays it (specs 006–008)

**Decision**: a row is what the server last said. A rule create inserts a placeholder row
(`version = 0`); queued content (`RulePayload`, `OccurrencePayload` in `data`) overlays the row in
every view. Occurrences never get a placeholder row (only the server creates them, R-05). A
tombstone (feed row or answer with `deleted_at`) removes the row unless a change to it waits
(spec 009 deletion rule); a rule's tombstone also removes its occurrences without waiting changes.
**Rationale**: one rule across features; the engine's clash and refusal states hang off the queue.

## R-04 `RecurringRuleSyncHandler` (record type `recurring_rule`, `canRestore = false`)

**Decision**:
- **Create / save**: `PUT /wallets/{wallet_id}/recurring-rules/{rid}` with every `RecurringRuleWrite` member (explicit `null`s), `version = base` (0 = create). `preset`, `kind`, `counter_wallet_id` and, for an existing rule, `currency` are always the stored ones; a never-sent rule sends the wallet's present currency (FR-007).
- **Delete**: `DELETE` with body `{version: base}`; a never-sent rule is removed locally with nothing queued (FR-016).
- **Batch**: `batchItem` returns `recurring_rule` upsert/delete with `wallet_id` (sync.yaml: required for this type).
- **Prerequisites**: the wallet (create), the category and payout category (create), the destination wallet (create); for a rule with `follows_rule_id`, `RecordKey("recurring_rule", followsId, untilSettled = true)` (R-09).
- **Tombstone clash**: `canRestore = false` → only "Accept the deletion" (rule deletion is final).
- **`applyServerCopy`** stores the rule; a tombstone removes it and its occurrences without waiting changes; `onDiscarded` removes a placeholder and cascades the split (R-09).
- **`refusalFields`**: `/amount_minor`→`amount`, `/amount_basis`, `/category_id`→`category`, `/payout_category_id`→`payoutCategory`, `/counter_wallet_id`→`destination`, `/currency`, `/frequency`, `/interval`, `/anchor_day`→`day`, `/start_date`→`start`, `/end_date`→`end`, `/occurrence_count`→`count`, `/payout_round`→`payoutRound`, `/status`, `/preset`, `/kind`, `/description`→`note`, `/memo`→`details`, `/counterparty`→`person`.
**Rationale**: the spec 004 contract; mirrors `TransactionSyncHandler` without restore.

## R-05 `OccurrenceSyncHandler` (record type `occurrence`, `canRestore = false`)

**Decision**:
- **Send**: `PUT /wallets/{wallet_id}/occurrences/{oid}` with the nine `OccurrenceWrite` members and `version = base` (always ≥ 1: the phone only queues changes to server occurrences). Never `DELETE`; `enqueueDelete` is never called for this type; `batchItem` is always `upsert`.
- **Confirm** carries the payload's `transaction_id` (a UUIDv7 generated once when the person confirms, kept across retries, "Keep mine" and edits of the pending confirmation, FR-024); skip and un-skip send `null`.
- **Prerequisites**: the edited category when it is a create waiting on this phone.
- **Clash with a confirmed occurrence** → silent accept (R-06). Any other clash → the engine's ordinary clash; a tombstone → "Accept the deletion" only.
- **`applySentAnswer`** stores the occurrence copy (it never carries balance; R-07 owns that).
- **`onDiscarded`** drops the pending confirmation's transaction row (R-07) when the discarded payload was a confirm.
- **`refusalFields`**: `/status`, `/date`, `/amount_minor`→`amount`, `/category_id`→`category`, `/transaction_id`→`status` (shown as a confirmation refusal), `/description`→`note`, `/memo`→`details`, `/counterparty`→`person`.

## R-06 Engine addition 1: a handler may accept a clash without asking (FR-024)

**Decision**: `SyncHandler.acceptsClash(change: PendingChange, current: JsonElement): Boolean`
(default `false`). In `settle()`, a `VERSION_CONFLICT` with `current` for which the handler answers
`true` is treated as `useTheirs`: `applyServerCopy(current)`, the queue row is deleted,
`onDiscarded(recordId)` runs, and it is logged as "clash accepted". `OccurrenceSyncHandler`
answers `true` when `current.status == "confirmed"` (whatever the phone's change was: a confirmed
occurrence never changes, so no choice is meaningful) and sets the row's
`local_notice = "already_confirmed"`, cleared when the person opens the occurrence.
**Rationale**: backend recurring.yaml "drop the local transaction, never resend under a new id";
P-04.4 forbids silently overwriting the server, not accepting it. One generic hook keeps
`core.sync` feature-free (P-10.7).
**Alternatives**: a clash with only an "OK" choice — rejected: an extra tap for something the
person cannot change, and the pending transaction would count in the balance until they tap.

## R-07 Pending confirmation = a phone-only transaction row (FR-021, FR-022)

**Decision**: `transactions.domain` gains `OccurrenceTransactions` (implemented in transactions,
called by recurring):
- `placeConfirmation(transactionId, walletId, counterWalletId, kind, amountMinor, currency, date, categoryId, texts, ruleId, occurrenceId)` inserts a `transactions` row with `version = 0`, both links set, `pending_confirmation = 1` (new column via the same migration 6→7), **not** queued as a transaction change;
- `updateConfirmation(...)` rewrites it while the confirmation waits (the person edits it);
- `dropConfirmation(transactionId)` deletes it if still `version = 0`.
`TransactionPostings` adds, per wallet, the full effect of each `pending_confirmation` row (both
wallets for a transfer) to `deltaFor` and `hasPending`. `TransactionRepository.storeFeedCopy` /
`storeServerCopy`: when the stored row is a `pending_confirmation` placeholder, the server copy
replaces it and its effect moves into the carry (source/counter), cleared by the existing
`WalletCopyParticipant` when a wallet copy at or past the transaction's `change_seq` is stored
(spec 009 QA fix 1) — so the balance neither drops nor doubles (SC-005). Sweep never removes a
`version = 0` row (unchanged); additionally the occurrence applier, on a server copy that is
`confirmed` with a `transaction_id` different from a local placeholder for the same occurrence,
drops that placeholder (another device or the server confirmed under another id). A placeholder
whose occurrence's server copy is confirmed with the same id but whose transaction never arrives
after a complete wallet read (the transaction was deleted meanwhile) is dropped by the
transactions sweep extension (`sweepConfirmations(walletId, seenIds)`).
The list shows the row like any transaction with "Waiting to sync", "From a recurring rule", and
the occurrence's clash/refusal status (the status is looked up through the hook of R-10, not the
transactions queue). Opening it opens the occurrence screen, not the transaction editor, until the
server copy arrives.
**Rationale**: the balance and list must reflect a confirmation at once (P-04.2, US2-S2), while the
transaction itself is never sent (ADR-0018 rule 6). Using the transaction's final id makes the
server copy replace the placeholder in place (same primary key).
**Alternatives**: computing the effect from the occurrence queue inside recurring and adding a
second `PendingPostings` — rejected: `PendingPostings` is a single binding with the carry logic in
transactions; two sources would race (spec 008 R-09's double-count lesson). Queuing a transaction
create — rejected by ADR-0018 rule 6 (it would be unlinked).

## R-08 Schedule, amounts and due on the phone (FR-006, FR-018)

**Decision**: pure `recurring.domain.schedule`:
- `RuleDates.dates(frequency, interval, anchorDay, start, end, count, limit)`: daily `start + k·interval` days; weekly `start + 7k·interval`; monthly month `start.month + k·interval` on `min(anchorDay, lastDay)`; yearly year `start.year + k·interval` in `start.month` on `min(anchorDay, lastDay)`; each from `k`, never chained; dates before `start` dropped (a monthly anchor before the start day in the first month); stop at `end` or after `count` rounds. Ported from backend `RuleDates` and checked against a table of server answers captured in QA.
- `Allocation.split(totalMinor, parts)`: `base = total / parts`, the first `total % parts` rounds get `base + 1`; `payout = Σ rounds`, refused locally above 2^53 − 1.
- `RuleToday.of(zoneId, now)`: the date in the rule's IANA zone (the server copy's `timezone`; for a never-accepted rule the profile's time zone, falling back to the device's; an unknown zone falls back to UTC).
- `OccurrenceState.derive(occ, siblings, today)`: `Confirmed` / `Skipped` / `Due` (scheduled, `date ≤ today`) / `Missed` (due and a later non-payout sibling is also due) / `Upcoming`; with overlays (pending skip, pending confirm) applied first.
- A `TodayTicker` (domain, `Clock` + `delay` to the next minute) re-derives due states while a screen is collected, so midnight changes the lists without a write (ADR-0018 rule 8).
**Rationale**: the brief asks for the server's month-end rule; ADR-0018 rules 8–9.
**Alternatives**: asking the server for a preview — no such endpoint; and it would not work offline.

## R-09 "Change from a date" (FR-014): engine addition 2, a hold until settled

**Decision**: the service queues (1) a save of the old rule with `end_date = D − 1` and (2) a
create of the new rule (`follows_rule_id = old id`, phone-only). `RecordKey` gains
`untilSettled: Boolean = false`; `SyncEngine.isHeld` holds a change whose `untilSettled`
prerequisite has **any** queued change (any state), **even if it is earlier in the same request**,
so the new rule goes in a later request after the old rule's answer. When the old rule's change is
discarded (person's "Discard", "Use the other device's", "Accept the deletion"),
`RecurringRuleSyncHandler.onDiscarded` launches (outside the engine lock, the spec 008 deadlock
lesson) a discard of every waiting rule whose `follows_rule_id` is that id; the screen says "This
change was cancelled with the change to <rule>". `D` is validated locally: after the last resolved
round's date on the phone, on or after the old start; the server still decides
(`OUT_OF_RANGE /end_date`). A split on a never-sent rule just edits the placeholder (nothing
resolved yet).
**Rationale**: two overlapping rules would double every later payment; same-batch co-sending is
not safe because batch items are applied independently (a refused end would still let the new
rule in).
**Alternatives**: one atomic server call — does not exist (backend contract gap 3).

## R-10 Hooks owned by lower features, implemented by recurring

**Decision**:
- `transactions.domain.RecurringLinks` (default no-op binding in transactions, overridden by recurring's module): `observeDue(walletId): Flow<List<DueItem>>` (FR-019a), `observeMatches(walletId, kind, categoryId, counterWalletId, date): Flow<List<DueItem>>` (FR-031), `confirmFromDraft(occurrenceId, draft): ConfirmResult` (FR-031 accept path), `observeConfirmationStatus(transactionId): Flow<ConfirmationStatus?>` (R-07 list status). `DueItem` is a transactions-domain model (id, walletId, ruleId, description, date, amount, kind, state Due/Missed/Upcoming).
- `auth.domain.DueSummary` (default `flowOf(0)`) with `observeDueCount()` for Home's card (FR-019b).
- `categories.domain.PendingCategoryUses`: the single binding becomes a union of all bound sources (`getAll<PendingCategoryUses>()` combined in `CategoriesModule`); recurring binds `RecurringCategoryUses` (categories of waiting rule and occurrence changes).
- `wallets.domain.WalletDeletionParticipant`: recurring binds `RecurringWalletCleanup` (rules, occurrences and their queued changes of the wallet; queue discards launched, R-09 lesson).
- Navigation: `TransactionsRoute` / `TransactionEditorRoute` / `WalletDetailRoute` / Home get new callbacks (open due item, make recurring, open recurring); `AppNavHost` maps them to recurring keys.
**Rationale**: the existing hook pattern (`PendingPostings`, `PendingCategoryUses`) keeps the
graph one-way (P-10.4, P-16.6: stores call domain only).
**Alternatives**: transactions UI importing `recurring.domain` — rejected: a feature cycle.

## R-11 Catch up (FR-028)

**Decision**: `RecurringApi.materialise(walletId, ruleId)` (POST, no body, 30 s default timeout),
called by `RecurringService.catchUp(ruleId)` only when the device reports a connection; on 200,
404 or 409 it calls `SyncRunner.refresh(walletScope, force = true)`; offline/timeout → `NeedsConnection`;
403 → copy by code; 5xx → "try later" with the request id. Not queued, not retried automatically,
never called in the background.
**Rationale**: brief "don't materialise on the phone; ask the server, then pull".

## R-12 Screens (P-16) and navigation

**Decision**: four MVI screens in `recurring.ui`, each `Contract.kt` + store + `…Route` + `…Screen`:
1. `recurring/list` — `RecurringKey(walletId: String?)`: sections Due, Upcoming (next 35 days held), Rules (active, paused, ended); wallet mode adds "Add recurring" (writers, active wallet); all-wallets mode groups by wallet.
2. `recurring/rule` — `RuleDetailKey(ruleId)`: summary (cadence text, next date, amount or split, author, status, sync status and clash/refusal choices), its occurrences (held), actions Edit, Change from a date, Pause/Resume, End, Delete, Check for new dates.
3. `recurring/editor` — `RuleEditorKey(walletId: String?, ruleId: String?, fromDate: Boolean = false, prefill: RulePrefill? = null)`; preset segmented control, fields per FR-004/FR-010, live preview (FR-006), frozen fields (FR-013); `RulePrefill.toString()` redacts amounts and texts (P-09.2).
4. `recurring/occurrence` — `OccurrenceKey(occurrenceId, confirm: Boolean = false)`: details, state, refusal/notice, actions Confirm (form), Skip / Undo skip, Edit this one, Open transaction; clash/refusal choices.
Transactions list: "Due" section above the day groups (rendered from `RecurringLinks.observeDue`); editor: "Make recurring" (create mode) and the match offer (FR-031). Wallet detail: "Recurring" row. Home: "Due" card when count > 0.
**Rationale**: specs 006–008 screen pattern; one store per screen.

## R-13 Design system additions (P-07)

**Decision**: `OccurrenceRow` (date, title, amount via `MoneyText`, state label + icon: Upcoming,
Due, Missed, Skipped, Confirmed, Payout tag; optional `WalletStatusLine`-style sync status),
`RuleRow` (title, cadence line, next date, amount, Active/Paused/Ended pill, sync status),
`DueCard` (count + first due item, chevron) and `PreviewDateList` (compact dated amounts with a
"Preview" label). States never by colour alone (icon + text, P-08.3). Catalogue section
`RecurringCatalog`; screenshots EN LTR, AR RTL, font 2.0. Reuses `AppIcons.Cycle` (spec 007).
**Rationale**: P-07.3 requires the library first.

## R-14 Copy and words (P-01, P-06)

**Decision**: about 140 English/Arabic strings in a `recurring_*` namespace. Glossary words of
spec §2; banned synonyms checked by a string-parity test extension (no "schedule", "repeat",
"recurrence", "instance", "run" in `recurring_*` values, English). Cadence text built from
resources with plurals ("Every month on day 1", "كل شهر يوم 1"; "Every 2 weeks on Monday").
Refusal copy by code + pointer in `RecurringErrorCopy` (FR-035), including
`auto_confirm_refusal` reasons (FR-027).

## R-15 Unknown values (P-02.3, FR-034)

**Decision**: DTO enums are strings; an unknown `frequency`, `preset`, `status`, occurrence
`status` or `auto_confirm_refusal` decodes, shows a generic label ("Other" / reason "Unavailable"),
excludes the rule from preview, and makes the rule and its occurrences read-only on this phone
(nothing is sent that the phone does not understand).

## R-16 Testing (owner policy: no device)

**Decision**: commonTest: schedule/allocation/due tables, reducers and stores (P-16.9), services
over fakes, handlers (request shapes, prerequisites, silent clash), engine additions (accept clash,
until-settled hold incl. same-request), appliers (version guard, tombstone, sweep), contract tests
on `MockEngine` from the recurring.yaml examples (every code of spec §5), `OccurrenceTransactions`
+ `TransactionPostings` sequences (confirm → send → feed → wallet copy). androidHostTest:
`Migration6To7Test` (+ chain), DAO tests, `SignOutWipeTest`, `AppGraphTest`, `RecurringLayeringTest`,
Roborazzi goldens. Live contract QA with curl against the local backend; real answers into
`CapturedRecurringResponses.kt`, replayed by `RecurringCapturedResponsesTest` through the real
client, engine, handlers and appliers (incl. a race: phone confirm after `materialise`
auto-confirmed). Maestro flows written under `.maestro/010-recurring/` with
`# deferred: E2E on owner's device`.

## R-17 Logging (P-09.2, P-13)

**Decision**: one log line per send/answer (type, id, outcome, code, request id), per accepted
clash, per materialise call (rule id, outcome); never amounts, texts, dates of a person's
payments or payloads. A log-capture test covers confirm, refusal and catch-up.
