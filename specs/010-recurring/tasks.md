---
description: "Task list for 010-recurring"
---

# Tasks: Recurring Transactions (client)

**Input**: Design documents from `specs/010-recurring/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (recurring-api-client.md, recurring-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `34e878d` (specs 004–009: engine, entitlements, wallets, categories, transactions, wallet feeds + batch upload; Room v6)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per component and screen; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/010-recurring/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Host QA (Phase 9) replays every call against the live local backend with `curl` and turns the real answers into fixtures, as specs 007–009 did.

**Organization**: Phase 2 builds storage, the two engine additions, the wire client, handlers, appliers, the schedule domain, the transactions-side pending confirmation, the hooks and the design-system pieces. US1 (set up + upcoming) is the MVP; US2 confirm/skip/due; US3 changes and the split; US4 presets; US5 the duplicate guard; US6 catch-up and shared wallets.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US6 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); recurring feature `cm/feature/recurring/…` (`fr/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); recurring tests `ct/feature/recurring/…` (`rt/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Transactions feature `cm/feature/transactions/…` (`ft/…`), tests `ct/feature/transactions/…` (`tt/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: recurring ui → recurring domain → recurring data; `fr/data` imports no domain, ui or other feature; transactions, wallets, categories and auth never import `feature.recurring`), naming (P-11).
- Glossary (P-01): Recurring Rule, Occurrence, gam'eya, installment, payout; no identifier or copy uses "schedule", "repeat", "recurrence" (for the rule) or "instance", "run" (for an occurrence); nothing user-facing says "version", "tombstone", "materialise", "scope" or "counter wallet".
- Money (P-03): `Long` minor units only; integer allocation; fraction digits from the currency; U+2212 for money out.
- Dates (P-05): `LocalDate` for every calendar date; "today" from the rule's zone only for display/derivation, never written.
- Never log amounts, notes, details, people, dates of payments or payloads; ids, outcomes, codes, request ids only (P-09.2).
- **MVI screen recipe (P-16)** as specs 001–009: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas, `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin**: handlers and repositories resolve `SyncEngine` lazily (spec 006 lesson); queue discards from `onDiscarded` or participants run in a launched job (spec 008 deadlock lesson); `AppGraphTest` stays green.
- **Merge hotspots**: `AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt`, `SyncEngine.kt`, `SyncHandler.kt`, `BatchItem.kt`, `TransactionEntity.kt`, `TransactionRepository.kt`, `TransactionPostings.kt`, transactions list/editor, wallet detail, Home, `CategoriesModule.kt`, `AppModules.kt`, `Routes.kt`, `AppNavHost.kt`, catalogue: additive only. New strings in one append-only block `<!-- Recurring (spec 010) -->` at the end of each `strings.xml`.
- Every interactive element gets the `testTag` of contracts/ui-and-e2e.md; dialog buttons are tapped by text in flows.

---

## Phase 1: Setup

**Purpose**: confirm prerequisites, create the package, strings.

- [ ] T001 Confirm on `010-recurring` (from `main` `34e878d`): `AppDatabase` version 6 with `MIGRATION_5_6` and `shared/schemas/…/6.json`; `FeedApplier`, `FeedPuller`, `SyncRunner`, `BatchItem`/`RecordKey`, `SyncHandler.batchItem`/`prerequisites`/`applySentAnswer`/`onDiscarded` in `cm/core/sync/`; `TransactionEntity.recurringRuleId`/`occurrenceId`, `TransactionPostings`, `WalletCopyParticipant`, `DestinationChoices` in transactions/wallets; `PendingCategoryUses`, `WalletDeletionParticipant`; `ht/core/di/AppGraphTest.kt` asserting eight appliers; `.maestro/common/api.js`. If any is missing, stop and report
- [ ] T002 Create `fr/di/RecurringModule.kt` with an empty `val recurringModule = module { }` and add it to the feature module list in `cm/AppModules.kt` (hotspot: one list element)
- [ ] T003 [P] Add the string block `<!-- Recurring (spec 010) -->` with every key from contracts/ui-and-e2e.md "Copy" (about 140, plurals for cadence) to the end of `values/strings.xml` and `values-ar/strings.xml` (glossary words of spec §2; Western digits; research R-14)

---

## Phase 2: Foundational (blocking)

**Purpose**: Room v7, engine additions, wire client, handlers, appliers, schedule domain, pending confirmation in transactions, hooks, design system, navigation keys.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [ ] T004 [P] Schedule tests in `rt/domain/schedule/RuleDatesTest.kt` (table-driven, research R-08): daily/weekly/monthly/yearly × interval 1–3; monthly day 31 from 31 Jan → 28/29 Feb, 31 Mar, 30 Apr (from the start, never chained); day 29–30 in February; yearly 29 Feb → 28 Feb in non-leap years; anchor before the start day in the first month dropped; stop at `end_date` and after `occurrence_count`; unknown frequency → empty (SC-002, FR-006)
- [ ] T005 [P] Allocation and payout tests in `rt/domain/schedule/AllocationTest.kt`: 1,000,000 / 12 → four 83,334 then eight 83,333, sum exact; KWD and JPY; total < count refused; gam'eya payout = Σ rounds; payout above 2^53 − 1 refused (FR-005, FR-010)
- [ ] T006 [P] Due derivation tests in `rt/domain/schedule/OccurrenceStateTest.kt` and `RuleTodayTest.kt`: due = scheduled ∧ `date ≤ today`; missed = due ∧ a later non-payout sibling due; payout never makes another missed; overlays (pending skip, pending confirm) applied first; today in `Africa/Cairo` vs device zone at 23:30 UTC; unknown zone → UTC; never-accepted rule uses the profile zone (FR-018, ADR-0018 rules 8–9)
- [ ] T007 [P] Engine tests in `ct/core/sync/SyncEngineAcceptClashTest.kt`: a handler answering `acceptsClash = true` → server copy applied, queue row deleted, `onDiscarded` called, no `Clash` state, both per record and as a batch item; default `false` keeps today's clash (research R-06)
- [ ] T008 [P] Engine tests in `ct/core/sync/SyncEngineUntilSettledTest.kt`: a change with `RecordKey(untilSettled = true)` is held while any queue row of that record exists in any state (Waiting, Sending, Clash, Refused), **including when it is earlier in the same request**, and is sent in the next run after it is accepted; keys without the flag keep spec 009's rule; no try is counted while held (research R-09)
- [ ] T009 [P] Contract tests in `rt/data/RecurringApiContractTest.kt` (Ktor `MockEngine`, recurring.yaml examples) for every row of contracts/recurring-api-client.md: A1 bodies of `createRent`/`createInstallment`/`endBeforeRaise` with all 21 members and explicit nulls, no `timezone`/`created_by`/`wallet_id`; A1 201/200 decoding with an unknown member and unknown enum strings; 409 live and tombstone; each 422 code at its pointer (`IMMUTABLE_FIELD`, `CURRENCY_MISMATCH@/currency` and `@/counter_wallet_id`, `INVALID_ENUM_VALUE`, `REQUIRED`, `OUT_OF_RANGE`, `UNKNOWN_FIELD`), 422 `INVALID_ID`; 403 `EMAIL_NOT_VERIFIED` (publishes the app-wide event) and `FORBIDDEN`; 404; A2 `DELETE` **with a JSON body** `{version}`; A3 `confirmWithActualAmount` and `skip` bodies (nine members), 409 with confirmed/scheduled/tombstone `current`, 422 at `/status`, `/transaction_id`, `/category_id`; A4 POST without body, 200/403/404/409; offline; 401 (P-14.3)
- [ ] T010 [P] Handler tests in `rt/data/RecurringRuleSyncHandlerTest.kt`: create PUT version 0 with the wallet's present currency; save sends the **stored** preset, kind, counter wallet and currency; delete with base; `batchItem` upsert/delete with `wallet_id`; prerequisites = wallet, category, payout category, destination (creates) + `untilSettled` key for `follows_rule_id`; `canRestore == false`; tombstone copy removes the rule and its occurrences without waiting changes; `onDiscarded` removes a placeholder and launches the discard of rules following it; `refusalFields` maps all pointers of research R-04
- [ ] T011 [P] Handler tests in `rt/data/OccurrenceSyncHandlerTest.kt`: PUT only (never `DELETE`, `batchItem` always upsert with `wallet_id`), version ≥ 1, confirm carries the payload's `transaction_id`, skip/un-skip `null`; `acceptsClash` true only when `current.status == "confirmed"` and sets `local_notice = already_confirmed`; `onDiscarded` of a confirm drops the pending transaction through `OccurrenceTransactions`; `refusalFields`; `canRestore == false`
- [ ] T012 [P] Applier tests in `rt/data/RecurringFeedAppliersTest.kt`: version guard (older ignored, equal applied); tombstone removes unless a change waits; a rule tombstone removes its occurrences; sweep after a read from the start removes unseen synced rows except waiting/placeholders; an occurrence copy confirmed with a different `transaction_id` drops the local pending confirmation of that occurrence (research R-07)
- [ ] T013 [P] DAO tests in `ht/feature/recurring/data/RecurringDaoTest.kt` (Robolectric, in-memory Room): rules by account/wallet/status; occurrences by wallet ordered by `date`, by rule, by `transaction_id`; per-account isolation; `deleteAll`; 2,000-occurrence query under 1 s (SC-008)
- [ ] T014 [P] Migration test `ht/core/database/Migration6To7Test.kt` (modelled on `Migration5To6Test`): a v6 file with rows in every table and `sync_scope` rows `user:<id>` and `wallet:<id>` with positions migrates to v7; every row survives; `recurring_rule`, `occurrence` and their indexes exist and Room validates; `transactions.pending_confirmation` = 0 on old rows; wallet scopes have `feed_cursor` and `feed_read_at` NULL; the user scope keeps its position; register `MIGRATION_6_7` in the migration chain tests (FR-030)
- [ ] T015 [P] Pending-confirmation tests in `tt/domain/PendingConfirmationTest.kt` and `tt/data/TransactionRepositoryConfirmationTest.kt` (research R-07): `placeConfirmation` inserts a `version = 0`, `pending_confirmation = 1` row with both links and no transaction queue row; `TransactionPostings.deltaFor` counts it once (both wallets of a transfer), `hasPending` true; `updateConfirmation` changes values, keeps the id; `dropConfirmation`; the feed/server copy of the same id replaces it and moves the effect to the carry; a wallet copy covering its `change_seq` clears the carry — the balance sequence confirm → accepted → transaction row → wallet row never drops or doubles (SC-005); sweep never removes a pending row; `sweepConfirmations` drops a pending row whose occurrence is confirmed with that id after a complete read without the transaction
- [ ] T016 [P] Extend `ht/feature/account/SignOutWipeTest.kt`: sign-out empties `recurring_rule` and `occurrence` (FR-033)
- [ ] T017 [P] Copy tests: `rt/ui/RecurringCopyTest.kt` (every refusal code + pointer of spec §5 and every `auto_confirm_refusal` maps to a recurring key; cadence text for every frequency/interval in both languages) and `ht/feature/recurring/ui/RecurringStringsTest.kt` modelled on `TransactionStringsTest` (every key in both languages; no "schedule", "repeat", "recurrence", "instance", "run", "version", "tombstone", "materialise" in English values; Western digits) (FR-035, FR-037)
- [ ] T018 [P] Hook tests in `rt/domain/RecurringCategoryUsesTest.kt` (categories of waiting rule and occurrence payloads incl. payout category) and `ct/feature/categories/domain/PendingCategoryUsesUnionTest.kt` (the categories service sees the union of transactions' and recurring's uses) (FR-032)

### Implementation

- [ ] T019 [P] DTOs in `fr/data/dto/RecurringDtos.kt` (`RecurringRuleDto`, `OccurrenceDto`, `RecurringRuleWriteRequest` with exactly the 21 `RecurringRuleWrite` members incl. `version`, `RuleVersionOnlyRequest`, `OccurrenceWriteRequest` with the nine `OccurrenceWrite` members; enums as strings, unknown members ignored) and queue payloads `fr/data/dto/RulePayload.kt`, `OccurrencePayload.kt` per data-model §5 (explicit nulls)
- [ ] T020 `fr/data/RecurringApi.kt` (put/delete rule with JSON body, put occurrence, materialise POST without body) on `ApiClient` to pass T009 (depends on T019)
- [ ] T021 [P] Room entities and DAOs `fr/data/local/RecurringRuleEntity.kt`, `RecurringRuleDao.kt`, `OccurrenceEntity.kt`, `OccurrenceDao.kt` per data-model §1–2 (`interval` 1–365, `anchor_day` 1–31 nullable, `occurrence_count` nullable, phone-only `follows_rule_id`, `local_notice`; `toString()` = id and version)
- [ ] T022 `cm/core/database/AppDatabase.kt` version 7 with both entities and DAOs; `TransactionEntity.pendingConfirmation` (`pending_confirmation INTEGER NOT NULL DEFAULT 0`); `MIGRATION_6_7` in `cm/core/database/Migrations.kt` per data-model §4 (incl. `UPDATE sync_scope SET feed_cursor = NULL, feed_read_at = NULL WHERE scope LIKE 'wallet:%'`); `buildAppDatabase` adds it; build to export `shared/schemas/com.fares.saveme.core.database.AppDatabase/7.json`; `DatabaseCleaner` deletes both tables, to pass T014, T016 (depends on T021)
- [ ] T023 [P] Engine additions in `cm/core/sync/SyncHandler.kt` (`acceptsClash`, default false), `cm/core/sync/BatchItem.kt` (`RecordKey.untilSettled = false`) and `cm/core/sync/SyncEngine.kt` (`settle` and batch item settle; `isHeld`) to pass T007, T008; every existing engine test unchanged (hotspot)
- [ ] T024 `fr/data/RecurringRepository.kt` and `fr/data/OccurrenceRepository.kt`: Room flows, rule placeholder insert/remove, server-copy store with version guard, tombstone rules (R-03), sweep, `follows_rule_id` lookups, `local_notice` set/clear; engine resolved lazily (depends on T020, T022)
- [ ] T025 [P] Schedule domain `fr/domain/schedule/RuleDates.kt`, `Allocation.kt`, `RuleToday.kt`, `OccurrenceState.kt`, `TodayTicker.kt` (pure; `Clock` injected) to pass T004–T006
- [ ] T026 Transactions side (research R-07) in `ft/domain/OccurrenceTransactions.kt` (+ implementation bound in `transactionsModule`), `ft/data/TransactionRepository.kt` (`placeConfirmation`, `updateConfirmation`, `dropConfirmation`, replace-with-carry in `storeFeedCopy`/`storeServerCopy`, `sweepConfirmations`), `ft/data/local/TransactionDao.kt`, `ft/domain/TransactionPostings.kt` (count pending-confirmation rows) to pass T015 (depends on T022)
- [ ] T027 `fr/data/RecurringRuleSyncHandler.kt` and `fr/data/OccurrenceSyncHandler.kt` per research R-04/R-05 to pass T010, T011; bind both as `SyncHandler` in `recurringModule`; the occurrence handler gets `OccurrenceTransactions.dropConfirmation` and the rule handler a launched discard callback as lambdas (P-10.6) (depends on T023, T024, T026)
- [ ] T028 `fr/data/RecurringRuleFeedApplier.kt` and `fr/data/OccurrenceFeedApplier.kt` (wallet scope, row types `recurring_rule` and `occurrence`) to pass T012; bind as `FeedApplier` in `recurringModule` (depends on T024, T026)
- [ ] T029 [P] Domain models `fr/domain/model/` per data-model §6 (`Rule`, `RuleView`, `RuleDraft`, `RulePrefill`, `FrozenMembers`, `Occurrence`, `OccurrenceView`, `OccurrenceLists`, `ConfirmDraft`, `SchedulePreview`, `RuleField`, result types, `RecurringRecord`) with mapping from entities + payload overlays
- [ ] T030 `fr/domain/RecurringService.kt` skeleton (`DefaultRecurringService`): `observeRules`, `observeRule`, `observeOccurrences`, `observeOccurrence` (overlays, `TodayTicker`, rule zone, roles from `WalletsService`), `preview`; inputs: verified address and profile zone from auth/account domain, `WalletsService`, `CategoriesService`, `TransactionsService.observeDestinations`, `OccurrenceTransactions`, engine (lazy), `SyncRunner`; other members added per story; register in `recurringModule` (depends on T025, T028, T029)
- [ ] T031 Hooks with defaults (research R-10): `ft/domain/RecurringLinks.kt` + `NoRecurringLinks` bound in `transactionsModule`; `cm/feature/auth/domain/DueSummary.kt` + `NoDueSummary` bound in the auth module; `cm/feature/categories/di/CategoriesModule.kt` resolves `PendingCategoryUses` as the union of `getAll<PendingCategoryUses>()` (transactions' binding re-declared as a named/multi binding); `fr/domain/RecurringCategoryUses.kt` bound in `recurringModule` to pass T018; `fr/domain/RecurringLinksImpl.kt`, `RecurringDueSummary.kt` (owner/editor wallets only) override the defaults in `recurringModule` (hotspots)
- [ ] T032 `fr/domain/RecurringWalletCleanup.kt` bound as `WalletDeletionParticipant`, with `rt/domain/RecurringWalletCleanupTest.kt`: the wallet's rules, occurrences and their queue rows go; pending confirmations of that wallet dropped; discards launched outside the engine lock (FR-033) (depends on T024)
- [ ] T033 Extend `ht/core/di/AppGraphTest.kt`: graph starts with `recurringModule`; handlers include `recurring_rule` and `occurrence`; ten `FeedApplier`s; `RecurringLinks` resolves to `RecurringLinksImpl`, `DueSummary` to `RecurringDueSummary`; `PendingCategoryUses` union includes both sources; `WalletDeletionParticipant`s include `RecurringWalletCleanup` (depends on T027, T028, T031, T032)
- [ ] T034 [P] `fr/ui/RecurringErrorCopy.kt` (refusals by code + pointer, field problems, `auto_confirm_refusal` reasons, status marks) and `fr/ui/CadenceText.kt` (plural resources) to pass T017
- [ ] T035 [P] Design system `cm/designsystem/component/Recurring.kt`: `OccurrenceRow`, `RuleRow`, `DueCard`, `PreviewDateList` (states as icon + text, 48 dp, `MoneyText`); catalogue `cm/designsystem/catalog/RecurringCatalog.kt` + entry in `CatalogSections.kt`; screenshots in `ht/designsystem/ComponentScreenshotTest.kt` (EN, AR RTL, 2×) for every state; check the "Save Me Mobile Design System" canvas first (P-07.3, research R-13)
- [ ] T036 Navigation keys in `cm/navigation/Routes.kt`: `RecurringKey(walletId: String?)`, `RuleDetailKey(ruleId)`, `RuleEditorKey(walletId: String?, ruleId: String?, fromDate: Boolean = false, prefill: RulePrefill? = null)`, `OccurrenceKey(occurrenceId, confirm: Boolean = false)` with `RuleEditorKey.toString()` redacting the prefill (P-09.2) and a test in `ct/navigation/RecurringKeysTest.kt`; `AppNavHost` entries added as each screen lands

**Checkpoint**: storage, engine additions, API, handlers, appliers, schedule, pending confirmation, hooks and design system ready; `./gradlew :shared:testAndroidHostTest` green for T004–T018 and T033.

---

## Phase 3: User Story 1 - Set up a recurring rule and see what is coming (Priority: P1) 🎯 MVP

**Goal**: create a custom rule offline from the wallet or the transaction editor, preview dates, see upcoming occurrences once synced.

**Independent Test**: offline, create a monthly rule on day 31: preview shows month-ends and "Dates appear after sync"; online: the rule syncs and Upcoming lists its next occurrence.

### Tests for User Story 1

- [ ] T037 [P] [US1] Service tests in `rt/domain/RecurringServiceCreateTest.kt`: verified gate (nothing queued); viewer and archived wallet → `NotAllowed`; local validation of FR-005 (interval 1–365, day 1–31, count 1–1000, end ≥ start, dates 1900–2999, texts within limits, amount digits by currency); exactly one of end date/count; placeholder v0 + `enqueue("recurring_rule", id, SyncScope(Wallet, walletId), 0, …)` with a UUIDv7, the wallet's currency, `auto_confirm = false` by default (FR-011); preview equals `RuleDates` (FR-001–FR-007)
- [ ] T038 [P] [US1] Editor reducer and store tests (custom, create) in `rt/ui/editor/RuleEditorReducerTest.kt` and `RuleEditorViewModelTest.kt`: kinds income/expense/transfer; day defaults to the start's day; frequency switch hides/shows the day; end mode Never/On a date/After N; preview recomputed per change; category sheet from C5 choices; destinations from `observeDestinations`; field errors; `canSave`; `RequireVerification`; prefill from `RulePrefill` (FR-003, FR-004, FR-006, FR-008, FR-009)
- [ ] T039 [P] [US1] List reducer and store tests in `rt/ui/list/RecurringReducerTest.kt` and `RecurringViewModelTest.kt`: sections Due/Upcoming/Rules (active, paused, ended) for one wallet and all wallets; "Add recurring" only for writers of an active wallet; unsynced rule shows "Dates appear after sync"; pull to refresh → forced read of the shown wallet scopes; offline banner (FR-017, FR-019, FR-029)
- [ ] T040 [P] [US1] Entry tests: `TransactionEditorViewModelTest` — "Make recurring" only in create mode for writers → `Effect.MakeRecurring(RulePrefill)` with the draft's wallet, kind, amount, category/destination, texts, date; nothing recorded (FR-003); `WalletDetailViewModelTest` — `RecurringClicked` effect

### Implementation for User Story 1

- [ ] T041 [US1] `RecurringService.create` and `preview` in `fr/domain/RecurringService.kt` to pass T037 (depends on T030)
- [ ] T042 [US1] Rule editor (custom) in `fr/ui/editor/` (`RuleEditorContract`, `RuleEditorViewModel`, `RuleEditorRoute`, `RuleEditorScreen` + preview) per contracts/ui-and-e2e.md; tags; register; wire `RuleEditorKey` in `AppNavHost` (depends on T034, T035, T036, T041)
- [ ] T043 [US1] Recurring list in `fr/ui/list/` (`RecurringContract`, `RecurringViewModel`, `RecurringRoute`, `RecurringScreen`) with `OccurrenceRow`/`RuleRow`, sections, FAB, pull to refresh through `SyncRunner`; wire `RecurringKey` (depends on T041)
- [ ] T044 [US1] Entry points: wallet detail row "Recurring" (`walletDetail.recurring`) in `cm/feature/wallets/ui/detail/*` with an `AppNavHost` callback; "Make recurring" (`txEditor.makeRecurring`) in `ft/ui/editor/*` → `RuleEditorKey(prefill)` replacing the editor; re-record wallet detail and editor goldens on purpose, to pass T040 (depends on T042)
- [ ] T045 [P] [US1] Screenshots in `ht/feature/recurring/ui/RecurringScreenshotTest.kt` (EN, AR RTL, 2×): editor empty, filled with preview, field errors, transfer; list empty, with sections, all-wallets mode, offline
- [ ] T046 [US1] E2E plumbing: `createRule`, `deleteRule`, `materialise`, `listOccurrences`, `confirmOccurrence` actions in `.maestro/common/api.js`; `.maestro/common/open-recurring.yaml`
- [ ] T047 [US1] Write flows `.maestro/010-recurring/US1-checkpoint-monthly-upcoming.yaml`, `US1-make-recurring-from-editor.yaml`, `US1-offline-preview.yaml` (each starting `# deferred: E2E on owner's device`)
- [ ] T048 [US1] Run `/speckit-e2e 010-recurring` for US1 — **deferred: E2E on owner's device**

**Checkpoint**: MVP — a rule created offline, previewed with the server's dates, and its upcoming occurrences listed after sync (brief checkpoint 1).

---

## Phase 4: User Story 2 - Confirm, skip or adjust what is due (Priority: P1)

**Goal**: due/missed shown on the transactions screen, Home and the recurring screen; confirm (offline) with a pending transaction in the balance; skip/undo; edit one; clashes and refusals.

**Independent Test**: offline, confirm a due occurrence with another amount: the transaction is listed waiting and the balance moves; online: one linked transaction on the server, no balance jump.

### Tests for User Story 2

- [ ] T049 [P] [US2] Service tests in `rt/domain/RecurringServiceOccurrenceTest.kt`: confirm queues one `occurrence` upsert (status confirmed, base version, a UUIDv7 `transaction_id`) and calls `placeConfirmation` with the form's values (both wallets for a transfer); confirming again while waiting keeps the id and calls `updateConfirmation`; `cancelConfirmation` discards and drops; skip/unskip payloads with `transaction_id` null; edit one; confirm refused for a confirmed occurrence, an unknown status or a rule without received occurrences; verified gate; viewer → `NotAllowed` (FR-021–FR-023, FR-026)
- [ ] T050 [P] [US2] Clash and refusal tests in `rt/domain/OccurrenceClashTest.kt` (engine over fakes): 409 with confirmed `current` → accepted, pending transaction dropped, notice set, never re-sent under a new id (SC-004); 409 scheduled → clash, "Keep mine" re-sends the same `transaction_id` on the newer version; "Use the other device's" drops the pending transaction; tombstone → only "Accept the deletion"; refusal at `/status` (destination gone, currency mismatch) and `FORBIDDEN` → "Not saved" with Try again / Discard (discard drops) (FR-024, FR-025)
- [ ] T051 [P] [US2] Occurrence reducer and store tests in `rt/ui/occurrence/OccurrenceReducerTest.kt` and `OccurrenceViewModelTest.kt`: states Due/Missed/Upcoming/Skipped/Confirmed/Payout; confirm form prefilled (kind, currency, destination fixed; categories from C5 with current kept); save → effect; skip/undo; edit one; waiting confirmation → change/cancel; confirmed read-only with "Open transaction"; refusal reason and auto-confirm refusal shown; notice dismissed on open; viewer read-only (FR-020–FR-027)
- [ ] T052 [P] [US2] Transactions list tests in `tt/ui/list/TransactionsDueSectionTest.kt` and `TransactionsViewModelTest`: `State.due` from `RecurringLinks.observeDue` (at most 3, missed first, then oldest, "See all (n)"); due items never in totals, search or filters; tapping → `Effect.OpenOccurrence`; a pending-confirmation row shows its occurrence status and opens the occurrence (FR-019, FR-020, FR-022)
- [ ] T053 [P] [US2] Home tests in `HomeViewModelTest`: `DueSummary` count > 0 shows the card, 0 hides it, tap → `Effect.OpenDue`; count only owner/editor wallets (in `rt/domain/RecurringDueSummaryTest.kt`) (FR-019)

### Implementation for User Story 2

- [ ] T054 [US2] `RecurringService.confirm`, `cancelConfirmation`, `skip`, `unskip`, `edit`, `dismissNotice`, clash/refusal passthroughs in `fr/domain/RecurringService.kt` to pass T049, T050 (depends on T030)
- [ ] T055 [US2] Occurrence screen in `fr/ui/occurrence/` (`OccurrenceContract`, `OccurrenceViewModel`, `OccurrenceRoute`, `OccurrenceScreen`) with the in-place confirm/edit form; tags; wire `OccurrenceKey` (+ "Open transaction" → `TransactionEditorKey`) to pass T051 (depends on T054)
- [ ] T056 [US2] `RecurringLinksImpl.observeDue` and `observeConfirmationStatus`; transactions list "Due" section (`transactions.due.<index>`, `transactions.recurring`) and pending-confirmation row routing in `ft/ui/list/*` and `ft/domain/TransactionsService.kt`; re-record list goldens on purpose, to pass T052 (depends on T031, T054)
- [ ] T057 [US2] `RecurringDueSummary` and Home "Due" card (`home.due`) in `cm/feature/auth/ui/home/*` → `RecurringKey(null)`; re-record Home goldens on purpose, to pass T053 (depends on T031)
- [ ] T058 [P] [US2] Screenshots: occurrence screen (due, missed, payout, confirm form, waiting, clash, refusal, auto-confirm refusal, already confirmed, confirmed), transactions list with due section, Home with due card (EN, AR RTL, 2×)
- [ ] T059 [US2] Write flows `US2-confirm-offline.yaml`, `US2-checkpoint-skip.yaml`, `US2-auto-confirm-race.yaml`
- [ ] T060 [US2] Run `/speckit-e2e 010-recurring` for US2 — **deferred: E2E on owner's device**

**Checkpoint**: due items are visible and confirmed exactly once, offline or not (brief checkpoint 2).

---

## Phase 5: User Story 3 - Change a rule or one occurrence (Priority: P1)

**Goal**: rule detail; whole-series edit with frozen members; "Change from a date" split; pause/resume/end/delete; rule clashes.

**Independent Test**: with round 1 confirmed, change from round 3's date to 5,000: the old rule ends the day before, a new rule starts; rounds 1–2 unchanged.

### Tests for User Story 3

- [ ] T061 [P] [US3] Service tests in `rt/domain/RecurringServiceChangeTest.kt`: save sends the whole rule with the editor's base version; preset/kind/currency/destination never changed; frozen members (frequency, interval, day, start, basis, payout round; + amount and count for gam'eya/total) refused once any round is confirmed or skipped; end cutting off a resolved round refused; ended rule refuses all but delete; a rule with an unknown frequency, preset or status (or an occurrence with an unknown status) is read-only and has no preview (FR-034, research R-15); pause/resume/end payloads; delete queues `{version}` or removes a never-sent rule locally (FR-012, FR-013, FR-015, FR-016)
- [ ] T062 [P] [US3] Split tests in `rt/domain/RecurringSplitTest.kt` (engine over fakes): `changeFrom` queues the old rule's end `D − 1` then a new rule with `follows_rule_id`; D bounds (after last resolved, ≥ old start); the new rule is held while the old change waits, clashes or is refused, also in the same request; sent after acceptance; discard / use theirs / accept deletion of the old change discards the new rule (launched); never-sent rule → plain edit (FR-014, research R-09)
- [ ] T063 [P] [US3] Rule detail reducer and store tests in `rt/ui/rule/RuleDetailReducerTest.kt` and `RuleDetailViewModelTest.kt`: summary, occurrences, preview for unsynced, "Upcoming rounds update after sync" while an edit waits (FR-039), actions by role/status, confirmations, clash/refusal choices, "Waiting for the change to <rule>" for a held split (FR-015, FR-039)
- [ ] T064 [P] [US3] Editor tests for edit and from-date modes in `RuleEditorReducerTest`/`RuleEditorViewModelTest`: frozen fields disabled with a reason and "Change from a date" offered; from-date mode shows the date first and its note (FR-013, FR-014)

### Implementation for User Story 3

- [ ] T065 [US3] `RecurringService.save`, `changeFrom`, `setStatus`, `delete` in `fr/domain/RecurringService.kt` to pass T061, T062 (depends on T030)
- [ ] T066 [US3] Rule detail in `fr/ui/rule/` (`RuleDetailContract`, `RuleDetailViewModel`, `RuleDetailRoute`, `RuleDetailScreen`); wire `RuleDetailKey`; editor edit and from-date modes in `fr/ui/editor/*`, to pass T063, T064 (depends on T065)
- [ ] T067 [P] [US3] Screenshots: rule detail (active, paused, ended, waiting, clash, refusal, held split, unsynced preview), editor edit with frozen fields, from-date mode (EN, AR RTL, 2×)
- [ ] T068 [US3] Write flows `US3-checkpoint-change-from-date.yaml`, `US3-edit-one.yaml`, `US3-pause-end-delete.yaml`
- [ ] T069 [US3] Run `/speckit-e2e 010-recurring` for US3 — **deferred: E2E on owner's device**

**Checkpoint**: series edits change later rounds only (brief checkpoint 3).

---

## Phase 6: User Story 4 - Gam'eya and installments in one step (Priority: P2)

**Goal**: preset shapes, previews with exact amounts and payout.

**Independent Test**: gam'eya 10 × 1,000, payout round 4 → payout 10,000; installment 10,000 / 12 → 833.34 × 4, 833.33 × 8.

### Tests for User Story 4

- [ ] T070 [P] [US4] Service tests in `rt/domain/RecurringPresetsTest.kt`: gam'eya = monthly, interval 1, expense, `occurrence_count` 2–120, `end_date` null, `payout_round` 1..count and an income `payout_category_id` required; installment = monthly, interval 1, expense or transfer, count 2–120, `end_date` null, basis per round or total (total ≥ count minor units); custom/installment send null payout members; preset never changes on edit (FR-010)
- [ ] T071 [P] [US4] Editor tests for presets in `RuleEditorReducerTest`/`RuleEditorViewModelTest`: preset switch fixes and hides fields; payout category from income choices; preview lists every round and the payout (sum), or the split with the remainder to the earliest (FR-006, FR-010)

### Implementation for User Story 4

- [ ] T072 [US4] Preset rules in `fr/domain/RecurringService.kt` and the editor's preset UI in `fr/ui/editor/*` to pass T070, T071 (depends on T041, T042)
- [ ] T073 [P] [US4] Screenshots: gam'eya and installment (per round, total) editors with previews; payout `OccurrenceRow` (EN, AR RTL, 2×)
- [ ] T074 [US4] Write flows `US4-gameya.yaml`, `US4-installment-total.yaml`
- [ ] T075 [US4] Run `/speckit-e2e 010-recurring` for US4 — **deferred: E2E on owner's device**

---

## Phase 7: User Story 5 - Don't record the same payment twice (Priority: P2)

**Goal**: the transaction editor offers "Confirm … instead" for a matching received occurrence.

**Independent Test**: with "Rent 4,500" due today, a hand entry of an expense with the rent category offers the confirm; accepting records one linked transaction.

### Tests for User Story 5

- [ ] T076 [P] [US5] Match tests in `rt/domain/RecurringMatchesTest.kt`: same wallet, scheduled after overlays, not pending confirm, same kind, same category (income/expense) or destination (transfer), |date − draft| ≤ 7 days, rule not ended, person can write; ordered by distance, at most 3; none for a rule without received occurrences (FR-031, US5-S4)
- [ ] T077 [P] [US5] Editor tests in `TransactionEditorViewModelTest`: offer shown in create mode only; accept → `confirmFromDraft` with the typed amount, date and texts, then the editor is replaced by `OccurrenceKey(id)` showing the waiting confirmation, no transaction created; ignore → normal create (FR-031)

### Implementation for User Story 5

- [ ] T078 [US5] `RecurringLinksImpl.observeMatches` and `confirmFromDraft` in `fr/domain/RecurringLinksImpl.kt` to pass T076 (depends on T054)
- [ ] T079 [US5] Offer in `ft/ui/editor/*` (`txEditor.match.<index>`) to pass T077; screenshots of the editor with the offer (EN, AR RTL, 2×) (depends on T078)
- [ ] T080 [US5] Write flow `US5-match-offer.yaml`; run it — **deferred: E2E on owner's device**

---

## Phase 8: User Story 6 - Catch up and follow shared wallets (Priority: P3)

**Goal**: "Check for new dates"; shared-wallet roles; produced transactions link back.

**Independent Test**: online, "Check for new dates" on a rule behind today → new occurrences after the follow-up read; offline → "needs a connection".

### Tests for User Story 6

- [ ] T081 [P] [US6] Service tests in `rt/domain/RecurringCatchUpTest.kt`: online → one materialise call then `SyncRunner.refresh(wallet, force = true)`; 404/409 → read; offline → `NeedsConnection`, nothing queued; 403 by code; 5xx → `Failed(requestId)`; ended rule or viewer → not offered; never called automatically (FR-028)
- [ ] T082 [P] [US6] Role and shared tests in `rt/domain/RecurringRolesTest.kt`: viewers read-only everywhere; another member's rule editable by an editor; a transfer rule's confirm offered only when the person writes both wallets (server `FORBIDDEN` authoritative) (spec §6)
- [ ] T083 [P] [US6] `TransactionEditorViewModelTest`: a produced transaction shows "From a recurring rule" and "Open the rule" when the rule is on the phone (FR-038)

### Implementation for User Story 6

- [ ] T084 [US6] `RecurringService.catchUp` and the rule detail action (`rule.catchUp`) to pass T081 (depends on T065, T066)
- [ ] T085 [US6] Role checks in `fr/domain/RecurringService.kt` and "Open the rule" (`txEditor.openRule`) in `ft/ui/editor/*` via `RecurringLinks` to pass T082, T083
- [ ] T086 [US6] Write flow `US6-catch-up.yaml`; run it — **deferred: E2E on owner's device**

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T087 [P] Arabic copy review of the recurring block (قاعدة متكررة, جمعية, قسط, دفعة, القبض, مستحقة, فائتة; Western digits; amounts LTR in RTL rows); list words for owner review in `summary.md`
- [ ] T088 [P] Layering test `ht/feature/recurring/ui/RecurringLayeringTest.kt` modelled on `TransactionsLayeringTest`: `feature/recurring/ui` imports no `data`; `feature/recurring/data` imports nothing from its domain/ui and no other feature; other features only through `…domain`; `feature/transactions`, `wallets`, `categories`, `auth` import nothing from `feature/recurring`; `core.sync` imports no feature (P-10)
- [ ] T089 [P] Accessibility test `ht/feature/recurring/ui/RecurringAccessibilityTest.kt`: occurrence states and rule status as text; icon buttons described; 48 dp; no clipped text at 2× on all four screens (P-08)
- [ ] T090 [P] Performance test `rt/domain/RecurringPerformanceTest.kt`: 50 rules / 2,000 occurrences derived into lists under 1 s on the host; preview of 120 rounds under 5 ms (SC-008)
- [ ] T091 [P] Log-capture test `rt/data/RecurringLoggingTest.kt`: a rule send, a confirm, an accepted clash, a refusal and a catch-up log no amount, text, payment date or payload (FR-036)
- [ ] T092 Trace every spec §4 invariant to its passing test (list them in `summary.md`) (P-14.2)
- [ ] T093 [P] `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS compiles)
- [ ] T094 `./gradlew build allTests` and `:shared:verifyRoborazziAndroidHostTest` green; `7.json` exported
- [ ] T095 Host QA without a device (quickstart "Host QA"): map every acceptance scenario, FR and edge case to a passing host test in `specs/010-recurring/qa-report.md`; replay every call of contracts/recurring-api-client.md with `curl` against the live local backend with the app's headers and bodies (verified QA account registered through the API, two EGP wallets, one USD wallet, an editor and a viewer member, an unverified account): rule create for every frequency incl. day 31 and 29 Feb, gam'eya, installment total, save, frozen refusals, ended, split, delete; occurrence confirm/replay/skip/un-skip/edit, confirm racing `materialise` auto-confirm, `/status` refusals, viewer 403, unknown occurrence 404; materialise 200/404/409; batch items; wallet-feed pages with both row types; compare server dates with `RuleDates` (SC-002) — **QA step**
- [ ] T096 Turn the captured answers into fixtures `rt/CapturedRecurringResponses.kt` and drive them through the real `RecurringApi`/`HttpSyncApi`, engine, handlers, appliers and service in `rt/RecurringCapturedResponsesTest.kt` (incl. the race and the balance sequence) (depends on T095) — **QA step**
- [ ] T097 Static review of the Maestro flows (selectors vs test tags, dialog buttons by text, `network-off`/`network-on` parts, `# deferred` header) and fix what it finds
- [ ] T098 Rerun `speckit-contract-check`; confirm `recurring.yaml` unchanged since `c0e9844`; record drift as FRs or follow-ups in spec §5
- [ ] T099 Update README "On-device database" (Room v7; recurring rules and occurrences) and the C7 row of `specs/briefs/README.md`
- [ ] T100 Run quickstart.md manual checks 1–10 on the dev build — **deferred: E2E on owner's device**
- [ ] T101 Run the full `/speckit-e2e 010-recurring` and write `specs/010-recurring/e2e-report.md` incl. the brief's three checkpoints (SC-007) — **deferred: E2E on owner's device**
- [ ] T102 Write specs/010-recurring/summary.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 (blocks all stories) → US1 (MVP) → US2 → US3; US4 after US1 (editor); US5 after US2 (confirm path); US6 after US3 (rule detail) → Polish.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T004–T018 all [P]; T019, T021, T023, T025, T029, T034, T035 in parallel.
- US4 (presets) can run beside US2/US3 once US1's editor exists (different files except the editor, coordinate).
- Within each story the test tasks are [P]; screenshot tasks run beside the next story's tests.

## Parallel Example: User Story 2

```text
T049 service  |  T050 clash/refusal  |  T051 occurrence store  |  T052 transactions due  |  T053 Home
then: T054 service  →  T055 occurrence screen  →  T056 due section  →  T057 Home card  →  T058 screenshots  →  T059 flows  →  T060 (deferred)
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: a rule created offline is queued in the wallet scope and sent; its server occurrences are applied from the feed and listed as upcoming; host tests green.

### Incremental Delivery

1. US1 → US2 (confirm exactly once, right balance) → US3 (series edits).
2. US4 presets, US5 duplicate guard, US6 catch-up.
3. Polish: host QA with live contract checks and captured fixtures, contract recheck, summary; device E2E and quickstart deferred to the owner.
