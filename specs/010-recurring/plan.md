# Implementation Plan: Recurring Transactions (client)

**Branch**: `010-recurring` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-recurring/spec.md`

## Summary

A new bounded context `feature.recurring` (data, domain, ui, di) lets a person create recurring
rules (custom, gam'eya, installment) offline-first, preview their dates with the backend's schedule
on the phone, and confirm, skip or edit the server-materialised occurrences, which reach the phone
through the spec 009 wallet feed. A confirmation is one queued occurrence write carrying a client
UUIDv7 transaction id, shown at once as a phone-only "pending confirmation" transaction row that
the server's copy replaces in place (research R-07). Two generic additions to `core.sync`: a
handler may accept a clash without asking (a confirmed occurrence, R-06), and a prerequisite can
hold a change until another record's change is settled ("Change from a date", R-09). Due items
appear on the wallet's transactions screen, on Home and on the recurring screen; the transaction
editor gains "Make recurring" and a "Confirm … instead" offer against duplicates. Room 6 → 7
adds two tables and a column and clears every wallet scope's feed position once.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, MVI base, Ktor 3.6.0,
kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite,
Navigation 3, Kermit). **No new dependency.**

**Storage**: Room `saveme.db` v7: `recurring_rule`, `occurrence`, `transactions.pending_confirmation`;
wallet scopes' feed positions cleared once (data-model §4).

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor `MockEngine` (contract tests from the
recurring.yaml examples), fakes in `commonTest`; Room DAO, migration, sign-out wipe, graph and
layering tests and Roborazzi in `androidHostTest`; captured live-backend answers
(`CapturedRecurringResponses.kt`). No emulator, Maestro or device run (owner policy); Maestro
flows written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: recurring screen with 50 rules / 2,000 occurrences renders from Room within
1 s (SC-008; indexed queries, derivation in one pass); preview recomputed per keystroke in < 5 ms
(pure date arithmetic, ≤ 120 rounds); applying a 200-row feed page of occurrences < 200 ms on host.

**Constraints**: P-04 (all rules), ADR-0018 (never create an occurrence, one confirmation one
transaction id), backend schedule parity (SC-002), no balance flicker (SC-005), 60 s feed floors
of spec 009, never another account's data.

**Scale/Scope**: 1 migration, 2 entities + DAOs, 2 sync handlers, 2 feed appliers, 1 API, 1
service + schedule domain, 4 MVI screens, 4 design-system components, 5 hook bindings, 2 engine
additions, changes to transactions (row column, postings, list, editor), wallet detail and Home;
~140 strings EN/AR.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails. Recurring rules are MVP (brief C7; "bill
reminders beyond recurring occurrences" is the guarded item, and none is added); no new
dependency; no entitlement gate exists for recurring on the backend.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code: `RecurringRule`, `Occurrence`, `preset` `gameya`/`installment`, `payout`, `materialise` (internal `catchUp`); UI words in spec §2 and contracts/ui-and-e2e.md Copy; string test rejects "schedule/repeat/recurrence/instance/run" (R-14) |
| P-02 API contract fidelity | PASS | Shapes from recurring.yaml v1.7 (contracts/recurring-api-client.md); writes carry exactly `RecurringRuleWrite` / `OccurrenceWrite` with explicit nulls, read-only members never sent (FR-034); unknown members/enums tolerated (R-15); branches on `code` + pointer only (`RecurringErrorCopy`, R-05/R-06 on `VERSION_CONFLICT` + `current.status`); headers via the shared client; retries via `settle()` |
| P-03 Money | PASS | `amount_minor` Long in entities, DTOs, payloads; `Allocation.split` integer arithmetic (R-08); fraction digits from the currency through `AmountInput`/`MoneyText`; expenses neutral ink, U+2212 |
| P-04 Offline-first | PASS | Rule 1: all screens from Room (`RecurringService` flows). Rule 2: create/edit/delete/confirm/skip queue locally (R-04, R-05). Rule 3: rule ids and confirmation transaction ids UUIDv7 on the phone. Rule 4: base version on every write; clashes to the person, or accepted from the server when confirmed (R-06, accepting never overwrites the server). Rule 5: rule deletion is a queued tombstone; occurrences are never deleted by the phone |
| P-05 Time | PASS | `start_date`/`end_date`/`date` as `LocalDate`; instants UTC; the rule's IANA zone used only to derive "today"/due for display (R-08), never written |
| P-06 Localisation & RTL | PASS | ~140 strings EN/AR, plurals; Western digits in dates and amounts; RTL screenshots of the four components and screens; copy chosen by code (FR-035) |
| P-07 Design system only | PASS | `OccurrenceRow`, `RuleRow`, `DueCard`, `PreviewDateList` added to `designsystem` with catalogue + screenshots first (R-13); screens use existing components otherwise |
| P-08 Accessibility | PASS | States as icon + text (P-08.3); 48 dp targets; content descriptions on icon actions; 200% font screenshots |
| P-09 Security & privacy | PASS | No amounts/texts/payloads in logs (R-17, log-capture test); nav keys with prefill redact `toString`; sign-out clears both tables and queue (FR-033) |
| P-10 Shared-first architecture | PASS | All in `commonMain`; `recurring.data` imports no domain/ui/other feature; lower features expose hooks (`RecurringLinks`, `OccurrenceTransactions`, `DueSummary`, union `PendingCategoryUses`, `WalletDeletionParticipant`) and never import recurring (R-10); `core.sync` additions are feature-free (`acceptsClash`, `RecordKey.untilSettled`); `RecurringLayeringTest` |
| P-11 Naming | PASS | `feature.recurring`, `RecurringService`, `RuleDates`, `OccurrenceSyncHandler`; no codename |
| P-12 Entitlements & billing | N-A | No plan feature gates recurring rules (backend spec 014 default 17); `FEATURE_NOT_ENTITLED` would be refused generically |
| P-13 Observability | PASS | Logs per send/answer/accepted clash/catch-up with ids, codes, request ids; unexpected errors show the request id ("Reference: …") |
| P-14 Quality gates | PASS | Spec §4 invariants each with a failing-when-broken test; contract tests for A1–A4 incl. each handled code; migration 6→7 test; screenshots for new components; no new dependency |
| P-15 Scope guard | PASS | No reminders/notifications (C16), no bank sync, no FX; transfers same-currency only (FR-008) |
| P-16 MVI presentation | PASS | Four screens with Contract/store/Route/Screen (R-12); stores call `RecurringService` / transactions domain only; reducer and store tests (P-16.9); changed stores (transactions list, editor, wallet detail, Home) keep one store each and gain intents/state/effects with tests |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/010-recurring/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── recurring-api-client.md
│   ├── recurring-domain.md
│   └── ui-and-e2e.md
├── checklists/
│   ├── requirements.md
│   └── recurring.md     # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/
├── schemas/…AppDatabase/7.json                         # exported v7
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/database/                              # AppDatabase v7 (+2 entities, 2 DAOs), MIGRATION_6_7, DatabaseCleaner
    │   ├── core/sync/                                  # SyncHandler.acceptsClash, RecordKey.untilSettled, SyncEngine.settle/isHeld
    │   ├── feature/recurring/
    │   │   ├── data/                                   # RecurringApi, dto/, local/ (RecurringRuleEntity/Dao, OccurrenceEntity/Dao),
    │   │   │                                           #   RulePayload, OccurrencePayload, RecurringRepository, OccurrenceRepository,
    │   │   │                                           #   RecurringRuleSyncHandler, OccurrenceSyncHandler, RecurringRuleFeedApplier, OccurrenceFeedApplier
    │   │   ├── domain/                                 # RecurringService, schedule/ (RuleDates, Allocation, RuleToday, OccurrenceState, TodayTicker),
    │   │   │                                           #   model/, RecurringLinksImpl, RecurringDueSummary, RecurringCategoryUses, RecurringWalletCleanup
    │   │   ├── ui/                                     # list/, rule/, editor/, occurrence/ (Contract, ViewModel, Route, Screen each), RecurringErrorCopy, CadenceText
    │   │   └── di/RecurringModule.kt
    │   ├── feature/transactions/                       # entity column, OccurrenceTransactions, RecurringLinks (+ default), postings, list due section, editor offer + make recurring
    │   ├── feature/categories/di/                      # PendingCategoryUses union
    │   ├── feature/auth/domain + ui/home/              # DueSummary (+ default), Home due card
    │   ├── feature/wallets/ui/detail/                  # "Recurring" row
    │   ├── designsystem/component/Recurring.kt         # OccurrenceRow, RuleRow, DueCard, PreviewDateList; catalog/RecurringCatalog.kt
    │   └── navigation/                                 # RecurringKey, RuleDetailKey, RuleEditorKey, OccurrenceKey; AppNavHost wiring
    ├── commonMain/composeResources/values*/strings.xml # + Recurring block (EN, AR)
    ├── commonTest/…/feature/recurring/                 # schedule, allocation, due, reducers, stores, service, handlers, appliers, contract, captured
    ├── commonTest/…/core/sync/                         # SyncEngineAcceptClashTest, SyncEngineUntilSettledTest
    └── androidHostTest/…                               # Migration6To7Test, DAO tests, SignOutWipeTest, AppGraphTest, RecurringLayeringTest, screenshots
.maestro/010-recurring/                                 # written, deferred: E2E on owner's device
```

**Structure Decision**: a feature package above transactions (R-01); every dependency from lower
features goes through hooks they own (R-10); the only `core` changes are two defaulted members.

## Implementation phases (input to /speckit-tasks)

1. **Setup**: entities, DAOs, `MIGRATION_6_7` (incl. wallet position reset and `pending_confirmation`), AppDatabase v7, exported schema, cleaner; migration test chain.
2. **Foundation**: engine additions + tests; DTOs, `RecurringApi` + contract tests; payloads; repositories; both sync handlers; both feed appliers; schedule domain (`RuleDates`, `Allocation`, `RuleToday`, `OccurrenceState`, `TodayTicker`); `OccurrenceTransactions` and postings changes in transactions; hook interfaces with defaults; Koin module; `AppGraphTest`; design-system components + catalogue + goldens; strings.
3. **US1 Set up**: service create + preview; rule editor (custom); recurring list; wallet detail row; "Make recurring"; navigation.
4. **US2 Due & confirm**: due derivation; occurrence screen (confirm, skip, undo, edit one); pending confirmation; transactions list due section; Home due card; clash acceptance and choices; refusal copy.
5. **US3 Change**: rule detail; save with frozen members; change from a date (hold + cascade); pause/resume/end/delete.
6. **US4 Presets**: gam'eya and installment in the editor and preview.
7. **US5 No double**: `RecurringLinks.observeMatches` + editor offer + accept path.
8. **US6 Catch up & shared**: `catchUp`; roles; wallet cleanup participant; category uses.
9. **Polish**: log-capture test; live contract QA + captured fixtures; Maestro flows (deferred); contract recheck; README (Room v7); `summary.md` (parent).

## External dependencies (not in this repo)

Backend B7 built (`c0e9844`); contract recorded at `saveme-backend@2a3b414` (working tree has
uncommitted spec 017 sync follow-ups, outside recurring.yaml). Backend contract gaps (spec §9):
no confirm before the phone has received the occurrence; no after-the-fact link of a hand-recorded
transaction; no atomic "change from date"; transfer rules not on the destination's feed.

## Complexity Tracking

None.

## Implementation notes (fork A)

Storage, engine additions, wire client, handlers, appliers (T002, T007–T014, T016, T019–T024, T027, T028).

- **`acceptsClash` is `suspend`** (spec text: plain function): keeps the door open for handlers that read the queue; default `false`, so every existing engine test passes unchanged. Accepted clash = `applyServerCopy(current)`, queue row deleted, `onDiscarded(id)`, log "clash accepted", per record and as a batch item (both go through `settle`).
- **`RecordKey.untilSettled`** holds a change while the record has any queue row (any state, even earlier in the same request). In the per-record path an `untilSettled` prerequisite that was just accepted earlier in the same run lets the follower go in that run; only batch requests defer it to the next run (the batch path never excuses it).
- **The "already confirmed" notice** is set by `OccurrenceSyncHandler.applyServerCopy` (not by `acceptsClash`, which stays a pure question) when the copy is confirmed under another transaction id than the waiting confirm; `applySentAnswer` and the feed never set it, and both keep an existing notice.
- **Dropping the phone-only transaction** uses a fourth `OccurrenceTransactions` member, `dropConfirmationOf(occurrenceId, exceptTransactionId)`, because the queue row (and its payload) is already gone when `onDiscarded` runs. Used by `onDiscarded` (no exception), by an accepted/used copy that is confirmed (except the server's id) and by the occurrence applier when a feed copy turns an occurrence into `confirmed`. `NoOccurrenceTransactions` is bound in `transactionsModule` until T026 replaces it.
- **Handlers take lambdas** (`walletCurrency`, `discardRule`, `queuedPayload`, `dropConfirmation`) so `recurring.data` imports no domain/ui/other feature; the rule discard cascade is launched on its own scope in `RecurringModule`.
- **Tombstones**: the feed applier ignores a tombstone while a change to the row waits; handler `applyServerCopy` (use theirs / accept the deletion / sent delete answer) removes unconditionally. A rule tombstone removes its occurrences except those with a waiting change.
- **Room**: `pending_confirmation` declared with `defaultValue = "0"` to match the `ALTER TABLE ... DEFAULT 0` of the migration. `7.json` exported by the build. Existing migration tests (2→3 … 5→6, chain) now register `MIGRATION_6_7`; the chain test is renamed `…EndsAtV7…`. `AppGraphTest` expects ten appliers and a new test checks the two handler types; the T033 assertions on hooks/participants remain for later forks.
- **Test fixtures**: `RecurringFixtures.kt` (fake DAOs, `ruleJson`/`occurrenceJson`, `RecurringTestEnv` over the data layer) in `commonTest/.../feature/recurring/`.

## Implementation notes (fork B)

Schedule domain, transactions-side pending confirmation, models, hooks, service read side, navigation keys (T004–T006, T015, T018, T025, T026, T029–T033, T036).

- **Schedule** (`recurring.domain.schedule`): `RuleDates.dates(frequency, interval, anchorDay, start, end, count, limit, from = null)` returns `Round(ordinal, date)`; an optional `from` skips earlier rounds but keeps their numbers. `Allocation` (`split` returns null when the total gives a round less than one minor unit; `payout` returns null above 2^53 − 1), `RuleToday.of(ruleZone, profileZone, deviceZone, now)` (a zone id that does not exist reads as UTC; a null rule zone falls to the profile zone, then the device's), `OccurrenceStates.deriveAll/derive` over `StateSlot` (the caller applies pending-skip/confirm overlays first; a payout is missed only when a later non-payout round is also due), `TodayTicker.ticks()` (instant now, then at each minute boundary). Date arithmetic is bounded to 1900–2999 like the server.
- **Models** follow the transactions pattern: wire strings kept next to computed enums with an `Unknown` entry (`Frequency`, `Preset`, `RuleStatus`, `OccurrenceStatus`, `AmountBasis`, `AutoConfirmRefusal`); `OccurrenceState` has an `Unknown` entry (unknown status → history list, read-only). `RecurringSyncState` adds `Held(onRuleId)`; `Refused` carries `code`, `requestId` and the plain-name `fields` map (the engine keeps no pointer, only the handlers' `refusalFields` mapping). `RulePrefill` is `@Serializable` (it lives in `RuleEditorKey`) and redacts `toString`.
- **Pending confirmation (R-07)**: `DefaultOccurrenceTransactions` replaces the placeholder (removed `NoOccurrenceTransactions`). `TransactionDao.observeRelevant` and a new `observeCarriedOrPending` include `pending_confirmation = 1` rows; `getPendingForOccurrence` serves `dropConfirmationOf`. `TransactionPostings` adds the whole effect of every `pending_confirmation`, `version = 0` row (both wallets of a transfer) to `deltaFor` and counts it in `hasPending`. `storeFeedCopy`/`storeServerCopy` replacing such a row move its effect to the carry (source part zero when the stored wallet copy already covers the transaction's `change_seq`, destination part waits for its wallet's read) and clear the flag. A fifth interface member `sweepConfirmations(walletId, confirmedTransactionIds)` was added; `OccurrenceFeedApplier.sweep` calls it (new optional lambda) with the transaction ids of the wallet's confirmed occurrences — a row still phone-only at the end of a complete read never got its transaction. The "TransactionRepositoryConfirmationTest" of T015 is folded into `PendingConfirmationTest` (domain), which exercises the repository through the real postings and wallet service.
- **Hooks (R-10)**: `transactions.domain.RecurringLinks` (+ `DueItem`, `DueState`, `ConfirmationStatus`, `ConfirmFromDraftResult`, `NoRecurringLinks`), `auth.domain.DueSummary` (+ `NoDueSummary`), `categories.domain.UnionCategoryUses`. Koin: defaults are bound in the owning modules; the recurring module rebinds them (latest definition wins). The two `PendingCategoryUses` sources are secondary-bound (`single { … } bind PendingCategoryUses::class`) and `CategoriesModule` unions `getAll()`; `AppGraphTest` now expects two sources.
- **`RecurringLinksImpl`**: `observeDue` and `observeConfirmationStatus` complete; `observeMatches` returns an empty flow and `confirmFromDraft` answers `NotAvailable` until T078 (US5). `RecurringDueSummary` counts due items of writable wallets.
- **`DefaultRecurringService`**: the read side (`observeRules/observeRule/observeOccurrences/observeOccurrence`, `preview`) is complete; every write member is `TODO("Txxx")` for the story that owns it (create T041, confirm/skip/unskip/edit/cancel/notice/clash choices T054, save/changeFrom/setStatus/delete T065, catch-up T084). The constructor exposes `rules`, `occurrences`, `engine`, `wallets`, `occurrenceTransactions`, `authState`, `runner`, `profileZone`, `deviceZone`, `ticker`, `clock`, `newId` as `internal` members for the write side. Preset rules (T072) extend `preview`, which already handles gam'eya payout and fixed rounds. A rule's `readOnly` = unknown values, ended, or the person cannot write (viewer or archived wallet).
- **Navigation**: `RecurringKey`, `RuleDetailKey`, `RuleEditorKey` (redacting `toString`), `OccurrenceKey` in `Routes.kt`; `AppNavHost` entries come with the screens.

## Implementation notes (fork C)

Strings, copy mapping, design-system pieces (T003, T017, T034, T035, T087).

- **Strings**: one block `<!-- Recurring (spec 010) -->` at the end of both `strings.xml` (196 keys, all prefixed `recurring_`; later forks may append inside it). Naming: `recurring_<area>_<thing>` with areas `section_`, `state_`, `status_` (rule status and sync marks), `cadence_` (plurals), `field_`, `preset_`, `frequency_`, `unit_`, `end_`, `basis_`, `error_` (field problems), `reason_` (refusals by code and pointer), `auto_refusal_`, `action_`, `catch_up_`, `cd_` (descriptions), `catalog_`. Arabic plurals carry zero/one/two/few/many/other. Shared keys are reused instead of duplicated where the wording is the transaction's own (`tx_field_*`, `tx_save`, `wallet_keep_mine`, `wallet_use_theirs`, `action_discard`, `action_retry`, `action_accept_deletion`, `tx_from_recurring`, `tx_error_amount_range`). The block sits after the transactions block, so the transactions string test's banned words (record, entry, label, version, token, scope, حساب, سجل, قيد, نسخة …) apply to it too; the English copy avoids them ("added", "settings", "the rule's author").
- **Weekday names** come from `recurring_weekdays` (Monday = index 0, `DayOfWeek.ordinal`); month names from the existing `month_short`. Yearly cadence reads "Every year on 29 Feb".
- **`RecurringErrorCopy.kt`**: `recurringRefusalKind(code, fields, isTransfer)` (code, then pointed fields; an occurrence `/status` with `INVALID_ENUM_VALUE` reads as "destination gone" only for a transfer, else as "confirmation refused"), `RecurringRefusalKind.messageRes(isOccurrence)`, `RecurringSyncState.Refused.text(isOccurrence, currency, isTransfer)`, `RuleFieldProblem.messageRes/text(field)`, `serverFieldMessageRes(code, field)`, `ConfirmField.asRuleField()`, `AutoConfirmRefusal.messageRes()`, `labelRes()` for `OccurrenceState`, `RuleStatus`, `Frequency`, `Preset` (null for unknown), and `RecurringSyncState.statusLine(): WalletCardStatus?`. `CadenceText.kt`: `cadenceText(frequency, interval, anchorDay, startDate)`, `cadenceEndText(endDate, count)`, `Frequency.cadenceRes()`.
- **Design system** (`designsystem/component/Recurring.kt`, all text pre-resolved by the caller like `TransactionRow`): `OccurrenceRow(dateText, title, amountMinor, currency, kind, state: OccurrenceVisualState, stateLabel, payoutLabel?, status: WalletCardStatus?, onClick?, directionLabel?)`, `RuleRow(title, cadence, nextText?, amountMinor, currency, kind, pill: RuleStatePill, pillLabel, amountNote?, status?, onClick?)`, `DueCard(countText, firstTitle, firstDate, contentDescription?, onClick?)`, `PreviewDateList(items: List<PreviewDateItem(dateText, amountMinor, kind, tag?)>, currency, label, caption?, emptyText?)`. State is icon + words; the leading tile is hidden above font scale 1.3 so text keeps its width. Catalogue `RecurringCatalog.kt` (`RecurringSection` = occurrences + rules sections, registered in `CatalogSections`).
- **Screenshots**: new goldens `recurring_occurrences_{en,ar,en_fs2}.png` and `recurring_rules_{en,ar,en_fs2}.png`, inspected. No existing golden was re-recorded. Pre-existing, unrelated: the `transactions` section goldens (`transactions_en/ar/en_fs2`) fail verification on 2026-10-04 because the date picker highlights "today" (4 Oct); they pass on other days. Not touched (adding `RecurringSection` to `CatalogSections` changed no existing golden: verify showed no other diff).
- **Arabic review (T087)**: words as in the spec glossary (قاعدة متكررة, جمعية, قسط, الدفعة/دفعة, القبض, مستحقة, فائتة, تأكيد تلقائي); added for owner review: "دفعة القبض" (payout round), "عدد الدفعات" (number of rounds), "التكرار" (how often), "الموعد الأصلي" (originally), "إنهاء/استئناف/إيقاف مؤقت" (end/resume/pause). Western digits throughout; amounts render LTR inside RTL rows (checked in the screenshots).

## Implementation notes (fork D)

Domain write side of `DefaultRecurringService` and the duplicate-guard hook; no UI. Assumed defaults and deviations:

1. **Constructor** gains `api: RecurringApi` (for `catchUp`, the only direct call; `recurringModule` passes it). Every other write is local-first plus a queue entry.
2. **Presets and "Change from a date"**: `changeFrom` is offered for custom, per-round rules only. The backend gives a preset no `end_date` and a total-basis rule no way to shed its count, so the old rule cannot "end the day before"; for those rules `changeFrom` answers `ReadOnly` (the editor should show the frozen reason without the split option). A split of a never-sent rule is a plain edit (R-09). The old rule's end replaces its count (`occurrence_count = null`, `end_date = D - 1`) because the backend refuses both together; the new rule takes the editor's own end or count.
3. **Cleaning before validating** (`RuleValidation.clean`): presets fix monthly/1/no end date (gam'eya also expense, no destination), payout members exist only for gam'eya, a category exists only off transfers and a destination only on transfers, monthly/yearly rules get the start date's day as anchor day when none was chosen and daily/weekly rules get none (the backend refuses `anchor_day` there). Category is required on income/expense and destination on transfer, as in transactions. Money digits are not re-checked in the service (the editor parses the text with `AmountInput`; the service takes minor units, range 1..2^53-1).
4. **Frozen members** compare the draft with the *server's* copy of the rule; a waiting skip or confirmation counts as resolved (same overlay as the view). An end or count that cuts off a resolved round is `CutsResolvedRound`; `Frozen` is reported on the field. The split date must be after the last resolved round and on or after the old start (`FromOutOfRange` at `RuleField.From`).
5. **Occurrence writes**: confirm/skip/edit load the server row and overlay the waiting change; writing values equal to the server's discards the waiting change instead of queuing (undoing an unsent skip sends nothing). A waiting confirmation is not turned into a skip (`ReadOnly`; cancel it first) and an edit while one waits is a change of the confirmation (same transaction id). A transfer occurrence is `NotAllowed` when the counter wallet is on the phone and the person cannot write it. Confirm requires a category off transfers (the occurrence may have lost its own).
6. **Delete of a never-sent rule** drops the queue row, the placeholder and discards rules that continue it (outside the lock the handler's lambda does the same for a discarded old rule). `cancelConfirmation` discards the write and explicitly drops the phone-only transaction as well.
7. **Hooks**: `observeMatches` = occurrences of `observeOccurrences(wallet)` due or upcoming (waiting confirm/skip already excluded), writable by the person, whose rule is active or paused (not ended/unknown), same kind, same category (destination for transfers), within 7 days, nearest first then date and round, at most 3. `confirmFromDraft` takes the draft's amount, date and category, and its texts when not blank, else the occurrence's own (so the produced transaction keeps its title); results map `Done`→`Confirmed`, `NotVerified`, `NotAllowed`, else `NotAvailable`.
8. **catchUp** results: 2xx/404/409 then forced wallet read (`Done`, or `NeedsConnection`/`Failed(null)` from the read); offline/timeout `NeedsConnection` with no read; `EMAIL_NOT_VERIFIED` `NotVerified`; `FORBIDDEN` `NotAllowed`; anything else `Failed(requestId)`. Not offered (answers `NotAllowed`, no call) for an ended, unsent or unknown-valued rule, a viewer, an archived wallet or an unknown rule.
9. **Tests**: `RecurringServiceEnv` (commonTest) combines wallets, transactions and recurring stacks over one engine; its runner only counts uploads, so a test drains the engine itself. The log-capture test covers create, confirm, accepted clash, refusal and catch-up. `RecurringLayeringTest` tolerates an absent `ui` directory until the screens land, and exempts `core.database` (the Room registry names every feature's entities).
10. Still open for other forks: T052/T053 (list and Home view-model tests; the domain halves are in `RecurringDueTest`), T072/T084/T085 (their editor/rule-detail/transaction-editor UI parts).

## Implementation notes (fork E)

UI for the recurring list, rule editor (create, edit, from a date, read-only, presets) and occurrence screens; rule detail, entry points in
transactions/wallet detail/Home and `RuleDetailKey` are fork F's.

- `RecurringService.refresh(walletId: String?, force)` was added (interface and `DefaultRecurringService`): reads the wallet's feed, or every active wallet's when null; a forced read first sends what waits. The list store takes it as a lambda.
- Shared UI helpers in `recurring.ui`: `RecurringBackBar`, `OccurrenceListRow`, `RuleListRow` (public, for rule detail), `CategoryPickField`, `RecurringCategorySheet`, `ChoiceChips`, `kindLabelRes`.
- Rule type, amount basis and end mode use wrapping `ChoiceChips`, not the fixed-height `SegmentedControl`: the long words ("Installment", "Each installment", "After a number of times") clipped at font scale 2.0 in the first golden. The kind control (short words) stays a `SegmentedControl`. Tags are unchanged (`ruleEditor.preset.<…>`, `ruleEditor.basis.<per|total>`, `ruleEditor.end.<never|date|count>`).
- Assumed defaults: the occurrence screen closes after a successful confirm or edit save (the list shows the waiting state) but stays after skip and undo; "Confirm" is offered on any scheduled or skipped occurrence of a writable wallet (the service decides), not only on due ones; "Change from a date" in the editor is offered only for custom per-round rules (service rule) and only when a member is frozen, and it replaces the editor with the from-date editor; in from-date mode the new rule's day follows the split date until typed and the start field is hidden; a refused rule's server field codes become field problems in the editor (`REQUIRED` required, `OUT_OF_RANGE` range, others "check this field"); the all-wallets list groups rules under the wallet name and sends "Add recurring" with a null wallet (the editor then takes the default writable wallet).
- Koin: the three screens' ViewModels are declared in `recurringModule` (`RecurringArgs`, `RuleEditorArgs`, `OccurrenceArgs` as parameters; `ProfileToday` from the transactions DI supplies "today").
- `AppNavHost`: entries for `RecurringKey`, `RuleEditorKey` (`OpenFromDate` pops and pushes the from-date key) and `OccurrenceKey`; the list's rule rows and the occurrence's "Open the rule" push `RuleDetailKey`, whose entry fork F adds.
- Test tags created: `recurring.list/add/empty/refreshFailed/due.<i>/upcoming.<i>/rule.<i>`; `ruleEditor.*` (preset, wallet, kind, amount, basis, category, destination, rounds, payoutRound, payoutCategory, frequency, interval, day, start, end.<…>, endDate, count, note, details, person, autoConfirm, from, preview, save, changeFrom, frozen, readOnly, noWallet, createWallet, destinationsEmpty); `occurrence.*` (state, notice, refusal, waiting, status, confirm, skip, unskip, edit, cancelConfirm, openTransaction, openRule, keepMine, useTheirs, acceptDeletion, tryAgain, discard, form.date/amount/category/note/details/person/save/cancel).
- Goldens: 33 screens x 3 in `screen_recurring_*` (list 5, editor 14, occurrence 14); all new.

## Implementation notes (fork F)

Rule detail, the entry points in transactions/wallets/Home, the remaining goldens, the Maestro flows and the accessibility test.

- **Rule detail** (`recurring.ui.rule`): header is `RuleListRow`; lines for end/count, automatic confirmation, author, wallet and the read-only reason; sync status with clash/refusal choices (`rule.syncStatus`, `rule.keepMine`, `rule.useTheirs`, `rule.acceptDeletion`, `rule.tryAgain`, `rule.discard`); phone preview only while the server has sent no dates for the rule (`rule.preview`), else "Upcoming rounds update after sync" while an edit waits; dates grouped Due / Upcoming / Earlier (`rule.occurrence.<i>`); actions by role and status. Assumed defaults: "Change from a date" is offered only to custom, per-round rules the server already holds (the service refuses the rest); "Check for new dates" is offered to writers on active or paused rules the server holds and shows its answer in place (`rule.catchUpNote`); pause/resume/end/delete act at once after their confirmation (end and delete ask first); a rule that vanishes closes the screen with "gone" unless the person deleted it. Koin `viewModel` with `RuleDetailArgs`; `AppNavHost` has the `RuleDetailKey` entry.
- **Hooks only**: transactions never imports recurring. `RecurringLinks` gained `observeRuleOnPhone` (default `false`) and transactions defines `MakeRecurringDraft`; `AppNavHost` turns it into `RulePrefill` and replaces the editor (pop, then push). `TransactionView` gained `occurrenceId`, `recurringRuleId` and `pendingConfirmation` (defaulted). `DueSummary` gained `observeFirstDue()` (default `null`) and `DueGlimpse`, so Home's card can name the most urgent item.
- **Transactions list**: "Due" section above the days (at most 3, "See all (n)" to the wallet's recurring screen) and a "Recurring" link (`transactions.recurring`) that is always there; a pending-confirmation row shows "Waiting to sync" (or the occurrence's clash/refusal mark, from `observeConfirmationStatus`), says "From a recurring rule" and opens its occurrence. Due items are not in totals, search or filters.
- **Transaction editor**: "Make recurring" (create mode, writers; blank amount hands over 0, which the rule editor shows as empty), the "Confirm … instead" offer (at most 3, follows wallet/kind/category-or-destination/date; needs a valid amount), and "Open the rule" on a produced transaction when the rule is on the phone.
- **Wallet detail** row `walletDetail.recurring` is shown to every role (viewers can read). **Home** card `home.due` shows when the count of due items in writable wallets is above zero.
- **Goldens changed on purpose**: `screen_transactions_editor_create_*` (empty, filled, errors, transfer, transfer_none: "Make recurring" button), `screen_transactions_list_*` (chips, empty, empty_viewer, liability, loaded, no_matches, offline, refresh_failed, search: "Recurring" link), `screen_wallets_detail_*` (archived, clash, credit_card, delete_clash, deleted_elsewhere, not_saved, owner, viewer, waiting: "Recurring" row), each in en/ar/en_fs2 (69 files). New: `screen_recurring_rule_*` (14 states), `screen_transactions_list_due`, `list_pending_confirmation`, `editor_create_match_offer`, `editor_open_rule`, `screen_auth_home_due`.
- **Maestro**: 12 flows (19 files with parts) under `.maestro/010-recurring/`, each starting `# deferred: E2E on owner's device`; `api.js` gained `createRule` (21 members, `START_IN_DAYS`), `deleteRule`, `materialise`, `listOccurrences`, `confirmOccurrence`; subflows `common/open-recurring.yaml`, `common/seed-rule.yaml`. Static review (T097): every selector was checked against a test tag in the code; dialog buttons are tapped by text; `network-off`/`network-on` parts are in `# e2e-between:` headers; `RULE_ID` falls back to the rule of the run.
- **Pre-existing, not touched**: the three `ComponentScreenshotTest [transactions]` goldens fail on any day other than the one they were recorded on (the date picker highlights today).

## Implementation notes (fork G)

### Spec §4 invariants → tests (T092)
| Invariant | Tests |
|---|---|
| Screens render from the phone's copy only | `RecurringLayeringTest`, `RecurringDaoTest`, `RecurringFeedAppliersTest` |
| Offline writes queue with base version | `RecurringServiceCreateTest`, `RecurringServiceChangeTest`, `RecurringServiceOccurrenceTest` |
| Phone never creates/deletes an occurrence | `OccurrenceSyncHandlerTest` (`batchItemsAreAlwaysUpsertsWithTheWallet`, `recordTypeAndNoRestore`) |
| One confirmation = one occurrence write; clash drops the local transaction | `OccurrenceSyncHandlerTest`, `OccurrenceClashTest`, `PendingConfirmationTest` |
| Occurrence never counts in a balance; pending confirmation counts once | `PendingConfirmationTest` (`theBalanceCountsItOnceAndBothWalletsOfATransfer`, `confirmThenAcceptedThen…NeverDropsOrDoublesTheBalance`) |
| Preview dates equal the server's schedule | `RuleDatesTest` |
| Split sums exactly | `AllocationTest`, `RecurringSplitTest` |
| Due/missed derived in the rule's zone | `RuleTodayTest`, `RecurringDueTest` |
| Frozen members | `RecurringServiceChangeTest`, `RuleEditorTest` |
| No overlapping rules after a split | `RecurringSplitTest`, `RecurringRuleSyncHandlerTest` |
| Writes carry declared members with explicit nulls | `RecurringApiContractTest`, `RecurringRuleSyncHandlerTest` |
| Failures branch on code and pointer | `RecurringApiContractTest` (`occurrenceRefusalsKeepTheirPointers`), `OccurrenceSyncHandlerTest` (`refusalFieldsMap…`) |
| Wallet removal and sign-out clear rules/occurrences | `RecurringWalletCleanupTest`, `SignOutWipeTest` |
| Migration 6 → 7 | `Migration6To7Test` |
| No amounts/texts in logs | `RecurringLoggingTest` |
| Match offer only for received, unconfirmed, matching occurrences | `RecurringMatchesTest` |

### Contract recheck (T098)
`docs/openapi/paths/recurring.yaml` has no change since `c0e9844` (`git diff c0e9844 HEAD` empty). The later components commit `e4dc4c6` (sharing) touches nothing on recurring or occurrences. No drift. The `speckit-contract-check` command was not available; the check was a diff plus the contract tests.

### Date-dependent goldens
`ComponentScreenshotTest [transactions]` (en, ar, en_fs2) failed on any day but 2026-10-02 because Material's date picker marks today from the system clock (spec 008 code, also failing on a clean `main`: neither `Dates.kt`, `TransactionsCatalog.kt` nor the goldens differ from `main`). Fixed in QA: `LocalPickerToday` (a CompositionLocal, default `null` = system clock) in `designsystem/component/Dates.kt`; when a day is pinned the picker suppresses its today marker, and the catalogue pins 2 Oct 2026. The goldens were byte-identical after re-recording, so none changed. No other golden depends on the clock (qa-report.md section 6).
