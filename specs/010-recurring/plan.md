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
