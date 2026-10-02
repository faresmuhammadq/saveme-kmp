---
description: "Task list for 008-transactions"
---

# Tasks: Transactions (client)

**Input**: Design documents from `specs/008-transactions/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (transactions-api-client.md, transactions-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `77710b5` (spec 004 engine, spec 005 entitlements, spec 006 wallets, spec 007 categories; Room v4)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per screen and component; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator or Maestro runs in this feature. Flows are written under `.maestro/008-transactions/`; every task that runs them is marked **deferred: E2E on owner's device**. A host-only QA (Phase 8) checks every call against the live local backend with `curl` and turns the real answers into test fixtures, as spec 007 did.

**Organization**: Phase 2 builds the storage, API, sync handler, repository, hooks and design-system pieces every story uses. US1 (record) is the MVP; US2 adds the list and the balance; US3 edit, delete, clashes and restore; US4 transfers; US5 search and filters.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`
- Transactions feature: `cm/feature/transactions/…` (`ft/…`), tests `ct/feature/transactions/…` (`tt/…`)

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: transactions UI → transactions domain → transactions data; other features only through their `domain`; `ft/data` imports no domain, ui or other feature), naming (P-11).
- Glossary (P-01): no identifier or copy uses "entry", "record", "movement", "operation" for a transaction or "line", "leg", "posting" for anything the client holds (the engine's existing `recordType` API is not renamed); nothing user-facing says "version", "tombstone", "posting", "scope" or "counter wallet" ("Reference: …" before a request id stays, as specs 006–007).
- Money (P-03): `Long` minor units only; never `Float`/`Double`; fraction digits from the currency; U+2212 for money out; expenses never in the error colour.
- Never log amounts, notes, details, people or payloads; log transaction id, outcome and request id only (P-09.2).
- **MVI screen recipe (P-16)**, as in specs 001–007: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure top-level `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas (never repositories), `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin (spec 006 lesson)**: anything the transaction handler depends on resolves `SyncEngine` lazily; `AppGraphTest` must stay green.
- **Merge hotspots** (plan.md): edits to `AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt`, `SyncEngine.kt`, `PendingChangeDao.kt`, `WalletsModule.kt`, `CategoriesModule.kt`, wallet detail/editor, `AppModules.kt`, `App.kt`, `Routes.kt`, `AppNavHost.kt`, Home, `Lists.kt`, `Money.kt` and the catalogue are additive only (the two hook bindings move). New strings go in one append-only block at the end of each `strings.xml` under `<!-- Transactions (spec 008) -->`.
- Every interactive element gets the `testTag` from contracts/ui-and-e2e.md; dialog buttons are tapped by text in flows.

---

## Phase 1: Setup

**Purpose**: confirm the prerequisites and create the package.

- [X] T001 Confirm on `008-transactions` (from `main` `77710b5`): `AppDatabase` is version 4 with `MIGRATION_3_4` and `shared/schemas/…/4.json`; `SyncHandler.canRestore` (default true) and the engine's `keepMine` sets `restore` on a tombstone clash; `Refusal.requestId` exists; `PendingPostings`/`ZeroPendingPostings` and `WalletDeletionParticipant` in `cm/feature/wallets/domain/`; `PendingCategoryUses`/`NoPendingCategoryUses`, `observeChoices`, `observeDisplays`, `refreshReferences` in `cm/feature/categories/domain/`; `CategoryChoiceSheet`/`CategoryChoiceList` in `cm/designsystem/component/Categories.kt`; `AmountInput` in `cm/feature/wallets/domain/`; `ht/core/di/AppGraphTest.kt`; `run-e2e.sh` `network-off`/`network-on`; `.maestro/common/api.js` slot B with `createWallet` and `createExpense`. If any is missing, stop and report
- [X] T002 Create `ft/di/TransactionsModule.kt` with an empty `val transactionsModule = module { }` and add it to the feature module list in `cm/AppModules.kt` (hotspot: one list element)
- [X] T003 [P] Add the string block `<!-- Transactions (spec 008) -->` with every key from contracts/ui-and-e2e.md "Copy" to the end of `values/strings.xml` and `values-ar/strings.xml` (Arabic per research R-27, Western digits)

---

## Phase 2: Foundational (blocking)

**Purpose**: domain types and pure rules, wire client, Room v5, engine query, sync handler, repository, hooks, design-system pieces, navigation.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T004 [P] Model and pure-rule tests in `tt/data/BalanceEffectTest.kt` and `tt/domain/model/TransactionModelTest.kt`: `TransactionKind` wire round-trip, unknown kept as `kindWire`; effect on a wallet for income, expense, transfer out (source), transfer in (destination), deleted/absent and unknown kind (research R-09, R-22); `TransactionDraft` ↔ `TransactionPayload` round-trip with explicit nulls; `TransactionField` from plain names (data-model §2–3)
- [X] T005 [P] Contract tests in `tt/data/TransactionApiContractTest.kt` (Ktor `MockEngine`, fixtures from transactions.yaml examples and backend spec 012 QA evidence) covering every row of contracts/transactions-api-client.md: list (`limit=200`, cursor echoed, no filter/sort/search, transfer-in rows decode); put (exactly 11 members, explicit nulls, no `wallet_id`/`created_by`/`recurring_rule_id`/`occurrence_id`/`deleted_at`); restore (`{"deleted_at": null, "version": n}` with the null present); delete (**body on DELETE**, JSON content type); 200/201 decoding with an unknown member and non-null recurring links; 409 `VERSION_CONFLICT` with live and tombstone `current`; 422 field codes at their pointers (`REQUIRED`, `OUT_OF_RANGE@/amount_minor`, `INVALID_ENUM_VALUE@/category_id` and `@/counter_wallet_id`, `CURRENCY_MISMATCH@/currency` and `@/counter_wallet_id`, `IMMUTABLE_FIELD@/kind` and `@/counter_wallet_id`, `UNKNOWN_FIELD`), 422 `INVALID_ID`; 403 `EMAIL_NOT_VERIFIED` (publishes `ApiEvent.EmailNotVerified`); 403 `FORBIDDEN`; 404; 503 with `retry_after_seconds`; offline; 401 → `SessionEnded` (P-14.3, FR-033)
- [X] T006 [P] Engine test in `ct/core/sync/SyncEngineObserveAllTest.kt`: `observeAll("transaction")` emits every row of that type in any state, updates on enqueue/discard/settle, and never another type's rows (research R-21)
- [X] T007 [P] Handler tests in `tt/data/TransactionSyncHandlerTest.kt` with a fake `TransactionApi` and repository (research R-04): create → PUT version 0 under the source; edit → PUT with base and the **stored** kind, counter wallet and currency; restore → PATCH then PUT with the PATCH's version when the payload differs, PATCH only when equal, PUT failure after PATCH returned as that failure; delete → DELETE with base; path = destination when the source is not on the phone; `applyServerCopy` after an own send adds the carry for each touched wallet (a tombstone keeps the row hidden) and after a `useTheirs` adds none (a tombstone removes the row at once); `onDiscarded` removes a `version = 0` placeholder only; `refusalFields` maps all ten pointers; `canRestore == true`; `recordType == "transaction"`; nothing logged contains the amount or note
- [X] T008 [P] DAO test in `ht/feature/transactions/data/TransactionDaoTest.kt` (Robolectric, in-memory Room): a wallet's list includes rows where it is `wallet_id` or `counter_wallet_id`, excludes `deleted_at` rows, orders `date DESC, created_at DESC, id DESC`; date-range, kind and category filters; carry sums per wallet; delete by "touches none of these wallets"; `deleteAll`
- [X] T009 [P] Migration test in `ht/core/database/Migration4To5Test.kt`: a v4 file from the exported v4 definitions (as `Migration3To4Test`) with rows in profile, wallet, category and `pending_change` migrates to v5; all rows survive; `transactions` and its three indexes exist and Room validates; register `MIGRATION_4_5` in the existing migration tests
- [X] T010 [P] Repository tests in `tt/data/TransactionRepositoryTest.kt` (fake API, DAO fake): placeholder row for a create (version 0, `created_by` = the person); `refreshWallet` upserts page by page and merges removals only when every page succeeded; an unlisted row touching the wallet with `version > 0` and no queue row is removed, one with a queue row and any placeholder are kept (FR-018); offline/failed keep rows; 404 stops without removing; `sync_scope` `transactions:<id>` stamped on success; the carry is added, cleared up to a time, and hidden tombstones with no carry purged
- [X] T011 [P] Extend `ht/feature/account/SignOutWipeTest.kt`: sign-out empties `transactions` and removes `transactions.lastWallet` (FR-032)
- [X] T012 [P] Copy tests: `tt/ui/TransactionCopyTest.kt` (every `RefusalKind` and field problem maps to a transaction key, never a generic message; `FORBIDDEN` on a transfer → `tx_reason_no_right_other`, on income/expense → `tx_reason_no_right`, P-06.5, FR-014) and `ht/feature/transactions/ui/TransactionStringsTest.kt` modelled on `WalletStringsTest` (every key in both languages; no "entry", "record", "movement", "operation", "posting", "version", "tombstone", "token" in English; no "قيد", "سجل", "نسخة" in Arabic; Western digits only)
- [X] T013 [P] Hook tests in `tt/domain/TransactionPostingsTest.kt` and `tt/domain/TransactionCategoryUsesTest.kt`: `deltaFor(W)` over waiting create, edit (amount change, date-only change = 0), deletion, clash and refused rows, for both sides of a transfer, plus the carry; `hasPending(W)` true for any queue row touching W; `usedLocally()` = category ids of non-deletion transaction payloads (FR-017, FR-029, FR-030)

### Implementation

- [X] T014 [P] Domain models in `ft/domain/model/` (`Transaction`, `TransactionKind`, `TransactionDraft`, `TransactionField`, `TransactionFieldProblem`, `RefusalKind`, `RefusalReason`, `TransactionSyncState`, `TransactionView`, `Direction`, `Counterpart`, `AddedBy`, `DayGroup`, `TransactionList`, `TransactionQuery`, `WalletChoice`, result types) per data-model §3; pure `ft/data/BalanceEffect.kt` (primitives only, so the handler in `data` can use it, P-10.6) to pass T004
- [X] T015 [P] DTOs in `ft/data/dto/TransactionDtos.kt` (`TransactionDto`, `TransactionWriteRequest`, `TransactionRestoreRequest`, `VersionOnlyRequest`) and queue payload `ft/data/dto/TransactionPayload.kt` with `TransactionFields` per data-model §2 (explicit nulls); nothing in `ft/data` imports `ft/domain` (P-10.6)
- [X] T016 `ft/data/TransactionApi.kt` (list, put, restore, delete with `contentType(Json)` + `setBody`) on `ApiClient` (`callPage` for the list) per contracts/transactions-api-client.md to pass T005 (depends on T015)
- [X] T017 [P] `PendingChangeDao.observeByType(type)` in `cm/core/sync/PendingChangeDao.kt` and `SyncEngine.observeAll(recordType): Flow<List<PendingChange>>` in `cm/core/sync/SyncEngine.kt` to pass T006 (hotspot: additive)
- [X] T018 [P] Room entity and DAO in `ft/data/local/TransactionEntity.kt` and `TransactionDao.kt` per data-model §1 (`toString()` = id and version)
- [X] T019 `cm/core/database/AppDatabase.kt` version 5 with `TransactionEntity` and `transactionDao()`; `MIGRATION_4_5` in `cm/core/database/Migrations.kt`; `buildAppDatabase` adds it; build once to export `shared/schemas/com.fares.saveme.core.database.AppDatabase/5.json`; `DatabaseCleaner` deletes the table (depends on T018)
- [X] T020 `ft/data/TransactionRepository.kt`: Room flows per wallet, placeholder insert/remove, server-copy store (tombstone → `deleted_at`), carry add/clear/purge, `refreshWallet` (all pages `limit=200`, progressive upsert, all-or-nothing removals), `walletsOnPhone` input for the path choice; `hasQueuedChange` resolves the engine lazily (research R-03, R-08, R-09, R-26) to pass T010 (depends on T016, T019)
- [X] T021 `ft/data/TransactionSyncHandler.kt` per research R-04 to pass T007; register `single { … } bind SyncHandler::class` in `transactionsModule` (depends on T020)
- [X] T022 `ft/domain/TransactionsService.kt` skeleton (`DefaultTransactionsService`): `observeTransaction`, `refresh(walletId, force)` with the 60 s per-wallet floor (forced: `engine.syncNow()`), the profile time zone and the signed-in person from `SessionManager`/`ProfileSyncer`, `WalletsService` and `CategoriesService` inputs; other members added per story; register in `transactionsModule` (depends on T020)
- [X] T023 `ft/domain/TransactionPostings.kt` and `ft/domain/TransactionCategoryUses.kt` to pass T013; bind them in `transactionsModule` as `PendingPostings` and `PendingCategoryUses`, and remove `single<PendingPostings> { ZeroPendingPostings }` from `cm/feature/wallets/di/WalletsModule.kt` and `single<PendingCategoryUses> { NoPendingCategoryUses }` from `cm/feature/categories/di/CategoriesModule.kt` (keep the zero objects for tests) (research R-09, R-10, R-26) (depends on T017, T020)
- [X] T024 `ft/domain/TransactionsWalletCleanup.kt` (research R-19) bound as `WalletDeletionParticipant` in `transactionsModule`, with `tt/domain/TransactionsWalletCleanupTest.kt`: rows touching no remaining wallet removed and their queue rows discarded; a transfer into a wallet still on the phone kept (FR-031) (depends on T020)
- [X] T025 `ft/domain/TransactionsSyncCoordinator.kt` (reconciler kick on foreground and on connectivity; the open list refreshes itself through its `Foregrounded` intent, research R-08) with `tt/domain/TransactionsSyncCoordinatorTest.kt`; `single(createdAtStart = true)` in `transactionsModule`; `onForeground()` call in `cm/App.kt` (hotspot: one call) (depends on T022)
- [X] T026 Extend `ht/core/di/AppGraphTest.kt`: the real graph starts with `transactionsModule`; the engine's handlers include `transaction`; `PendingPostings` resolves to `TransactionPostings` and `PendingCategoryUses` to `TransactionCategoryUses` (one binding each); `getAll<WalletDeletionParticipant>()` includes `TransactionsWalletCleanup` (depends on T021, T023, T024, T025)
- [X] T027 [P] `ft/ui/TransactionErrorCopy.kt` (refusal reasons by kind and pointer, field problems, status marks) to pass T012; add the `CURRENCY_MISMATCH` field-code line to `cm/feature/auth/ui/AuthErrorCopy.kt` (research R-20)
- [X] T028 [P] Design system: `cm/designsystem/component/Dates.kt` (`AppDatePickerDialog` with min/max and a Western-digit locale override, `DateField`), `AmountField` in `cm/designsystem/component/Money.kt`, `TransactionRow` optional `leading`, `status` text in `cm/designsystem/component/Lists.kt` (defaults keep today's look); catalogue entries in `cm/designsystem/catalog/CatalogSections.kt`; screenshots in `ht/designsystem/ComponentScreenshotTest.kt` (EN, AR RTL with Western digits in the picker, 2×); existing baselines unchanged. Check the "Save Me Mobile Design System" canvas first (P-07.3; research R-16)
- [X] T029 Navigation: `TransactionsKey(walletId)` and `TransactionEditorKey(transactionId: String? = null, walletId: String? = null)` in `cm/navigation/Routes.kt`; entries in `cm/navigation/AppNavHost.kt` passing `onRequireVerification`; Routes wired as each screen lands (research R-14)

**Checkpoint**: storage, API, handler, repository, hooks and navigation ready; `./gradlew :shared:testAndroidHostTest` green for T004–T013 and T026.

---

## Phase 3: User Story 1 - Record an expense or income in a few taps, even offline (Priority: P1) 🎯 MVP

**Goal**: record income and expenses from a wallet or Home, locally first, with the category choice from C5.

**Independent Test**: offline, from a wallet's transactions add 45.50 "Groceries": listed under Today with "Waiting to sync", balance −45.50; online: mark clears, server lists it, balance unchanged.

### Tests for User Story 1

- [X] T030 [P] [US1] Service tests in `tt/domain/TransactionsServiceCreateTest.kt`: verified gate (nothing queued); role gate (viewer → `NotAllowed`); archived wallet → `NotAllowed`; amount ≤ 0 / too many fraction digits (EGP 2, KWD 3, JPY 0) / above max refused via `AmountInput`; category required for income/expense, none on a transfer; text limits in code points; date range; placeholder v0 + `engine.enqueue("transaction", id, SyncScope(Wallet, walletId), 0, …)` with a UUIDv7 and the wallet's currency; last wallet stored; no entitlement dependency (FR-002–FR-009)
- [X] T031 [P] [US1] `defaultWalletId` and `observeWritableWallets` tests in `tt/domain/WritableWalletsTest.kt`: last wallet when still writable, else first active Owner/Editor wallet, else null; viewers, archived and hidden wallets excluded (FR-008)
- [X] T032 [P] [US1] Editor reducer and store tests (create mode) in `tt/ui/editor/TransactionEditorReducerTest.kt` and `TransactionEditorViewModelTest.kt`: opens with the key's wallet (or the default), Expense, today in the profile zone, amount focused; kind switch clears category/destination; wallet switch keeps the amount and re-checks digits; category sheet fed by `observeChoices(kind, current)` (own + defaults groups only); date picker result; more details; field errors; `canSave`; `RequireVerification`; no wallet → `noWallet` with `OpenWalletEditor`; `Saved` → `Close` (FR-002, FR-007, FR-008)
- [X] T033 [P] [US1] Wallet-side tests: `WalletEditorViewModelTest` — currency read-only when `hasPendingPostings` (FR-029, US1-S10); `WalletDetailViewModelTest` — `TransactionsClicked` / `AddTransactionClicked` effects, add offered only to Owner/Editor of an active wallet; Home `HomeViewModelTest` — `AddTransactionClicked` effect

### Implementation for User Story 1

- [X] T034 [US1] `TransactionsService.create`, `observeWritableWallets`, `defaultWalletId` (last wallet in `KeyValueStore` `transactions.lastWallet` + a `UserDataCleaner` bound in `transactionsModule`) in `ft/domain/TransactionsService.kt` (depends on T022)
- [X] T035 [US1] Transaction editor in `ft/ui/editor/` (`TransactionEditorContract`, `TransactionEditorViewModel`, `TransactionEditorRoute`, `TransactionEditorScreen` + preview), create mode: wallet select, kind `SegmentedControl` (Expense/Income; Transfer added in US4), `AmountField`, category field opening `CategoryChoiceSheet`, `DateField` + `AppDatePickerDialog`, note, "More details" (details, person, cleared), Save, no-wallet `EmptyState`; test tags; register the ViewModel; wire `TransactionEditorKey` in `AppNavHost` (depends on T028, T029, T034)
- [X] T036 [US1] Entry points: Home `AddTransactionClicked` → `TransactionEditorKey()` with button `home.addTransaction` in `cm/feature/auth/ui/home/*`; wallet detail `TransactionsClicked` (row `walletDetail.transactions`, replacing the "No transactions yet" placeholder) and `AddTransactionClicked` (`walletDetail.addTransaction`) with `AppNavHost` callbacks in `cm/feature/wallets/ui/detail/*`; wallet editor currency lock by `hasPendingPostings` in `cm/feature/wallets/ui/editor/WalletEditorViewModel.kt` to pass T033; re-record Home and wallet detail goldens on purpose (depends on T029)
- [X] T037 [P] [US1] Editor screenshots in `ht/feature/transactions/ui/TransactionsScreenshotTest.kt` (EN, AR RTL, 2×): create expense empty, filled with category and more details, field errors, no wallet, category sheet list (`CategoryChoiceList`)
- [X] T038 [US1] E2E plumbing: `listTransactions`, `createTransaction`, `saveTransaction`, `deleteTransaction` (`DELETE` with a JSON body), `restoreTransaction`, `getWallet` actions in `.maestro/common/api.js`; `.maestro/common/open-transactions.yaml` and `.maestro/common/add-expense.yaml` (research R-24)
- [X] T039 [US1] Write flows `.maestro/008-transactions/US1-S1`, `S2` (online; offline in three parts), `S3`, `S4`, `S5`, `S6`, `S7`, `S8` (`unverified-user`), `S10` per contracts/ui-and-e2e.md; US1-S9 recorded as manual (viewer needs C9)
- [ ] T040 [US1] Run `/speckit-e2e 008-transactions` for US1 — **deferred: E2E on owner's device**

**Checkpoint**: the MVP — income and expenses recorded offline, queued, and C5's category choice used for real (C5 US4-S1).

---

## Phase 4: User Story 2 - See a wallet's transactions by day, with the right balance (Priority: P1)

**Goal**: the wallet's transactions screen, day groups and totals, partner categories resolved, and a balance that includes unsent changes without jumping after sync.

**Independent Test**: seed three transactions on two dates; open: two headers with correct totals; record one offline: "Waiting to sync" and the balance includes it; online: balance unchanged after sync.

### Tests for User Story 2

- [X] T041 [P] [US2] List-building tests in `tt/domain/DayGroupingTest.kt` and `tt/domain/TransactionsServiceListTest.kt`: order by date, created, id; "Today"/"Yesterday" by the profile zone; day net totals with transfers by direction; pending overlay (waiting edit shows new values; waiting deletion hidden; placeholder listed); wallet hidden by its own waiting deletion → empty list; rows resolve categories through `observeDisplays(walletId)` with "Category unavailable" for misses; added-by (you / member name / another member) and "From a recurring rule"; `canAdd` by role and archived state (FR-015, FR-016, FR-020, FR-031)
- [X] T042 [P] [US2] Balance carry and reconciler tests in `tt/domain/BalanceReconcilerTest.kt`: queue row sent → carry added in the same step the queue row disappears, so `deltaFor` is unchanged across the send; reconciler debounces, calls `WalletsService.refresh(force = true)`, clears carries confirmed at or before the read's start only on `Done`, keeps them on `Offline`/`Failed` and retries on foreground; a carry confirmed after the start survives; tombstone rows purged when their carry clears (FR-017, SC-004)
- [X] T043 [P] [US2] Refresh tests in `tt/domain/TransactionsServiceRefreshTest.kt`: opening forces `refreshReferences(walletId, force = true)` (C5) and the wallet read with the floor; pull forces; a read that changed rows asks `WalletsService.refresh(force = true)`; offline/failed outcomes (FR-018, FR-019)
- [X] T044 [P] [US2] List reducer and store tests in `tt/ui/list/TransactionsReducerTest.kt` and `TransactionsViewModelTest.kt`: `Opened` → refresh; `Foregrounded` → refresh without force (the Route forwards lifecycle resume); pull; offline "Updated"; balance line from `observeWallet(walletId).displayedBalanceMinor`; refresh failed; loading before the first read; empty (writer vs viewer copy); `RowClicked` → `OpenEditor(id, walletId)`; `AddClicked` → editor or `RequireVerification`; balance line

### Implementation for User Story 2

- [X] T045 [US2] `ft/domain/DayGrouping.kt`; `TransactionsService.observeList(walletId, query)` (no filters yet), `refresh` wiring of references and wallet re-read in `ft/domain/TransactionsService.kt` (depends on T022, T023)
- [X] T046 [US2] `ft/domain/BalanceReconciler.kt` (research R-09) started by `transactionsModule`, with the foreground/connectivity retry through `TransactionsSyncCoordinator` (depends on T020, T023, T025)
- [X] T047 [US2] Transactions screen in `ft/ui/list/` (`TransactionsContract`, `TransactionsViewModel`, `TransactionsRoute`, `TransactionsScreen` + preview): top bar, balance line, `SyncBanner`, refresh-failed alert, `LazyColumn` with sticky `DateHeader`s and `TransactionRow`s (category tile, status text), empty note, FAB, pull to refresh, "Updated …", skeleton; test tags; register; wire `TransactionsKey` in `AppNavHost` (depends on T028, T029, T045)
- [X] T048 [P] [US2] List screenshots in `ht/feature/transactions/ui/TransactionsScreenshotTest.kt` (EN, AR RTL, 2×): loaded with two days, income/expense/transfer in and out, each row status, a partner category and "Category unavailable", empty (writer, viewer), offline with "Updated", loading
- [X] T049 [US2] Write flows `.maestro/008-transactions/US2-S1`, `S3` (two parts), `S5`, `S6` (two parts), `S7`; US2-S4, S8, S9 recorded as manual
- [ ] T050 [US2] Run `/speckit-e2e 008-transactions` for US2 — **deferred: E2E on owner's device**

**Checkpoint**: the ledger loop — record, see, balance right — works offline and online.

---

## Phase 5: User Story 3 - Edit and delete a transaction, safely (Priority: P1)

**Goal**: edit and delete locally; clashes (including deleted elsewhere → restore) and refusals shown on the transaction.

**Independent Test**: edit an amount offline; slot B edits the note; online: "Changed on another device" with both values; "Keep mine": the server has the phone's values.

### Tests for User Story 3

- [X] T051 [P] [US3] Service tests in `tt/domain/TransactionsServiceEditDeleteTest.kt`: save uses the editor's base version and the stored kind/wallets; edit of a placeholder rewrites the create; delete confirmed → `enqueueDelete` with the row's version; a never-sent create removed with nothing queued; viewer → `NotAllowed`; `keepMine` / `useTheirs` / `tryAgain` / `discard` call the engine; `useTheirs` asks the reconciler for one wallet re-read; discard of a placeholder removes it (FR-005, FR-021, FR-022)
- [X] T052 [P] [US3] Sync-state tests in `tt/domain/TransactionSyncStateTest.kt`: queue row → `TransactionSyncState` (`Sending` → Waiting; Clash with live `current` and tombstone `current`, mine an edit or a deletion; Refused reasons from code and pointer for every `RefusalKind`, `requestId` kept; field map) (FR-023–FR-025)
- [X] T053 [P] [US3] Editor tests (edit and detail mode) in `TransactionEditorReducerTest.kt` / `TransactionEditorViewModelTest.kt`: kind and transfer wallets read-only; current category kept selected even when archived/retired/partner's (C5 US4-S2); base version from the first emission; clash lines list only differing fields; choices per clash kind ("Keep mine"/"Use the other device's"; "Restore with my change"/"Accept the deletion"; "Delete anyway"/"Keep the transaction"); refusal choices (Try again only for not verified; `CurrencyChanged` edit opens with the new currency and an empty amount, FR-026); delete confirmation text with amount and date; viewer read-only; gone → message + back, except after the person's own accept/discard (spec 007 lesson)
- [X] T054 [P] [US3] Engine + handler integration test in `tt/data/TransactionRestoreFlowTest.kt` over `EngineHarness`: waiting edit → 409 tombstone → `keepMine` → handler sends PATCH (base = tombstone version) then PUT (restored version) → row live with the phone's values, queue empty, carry recorded (FR-024)

### Implementation for User Story 3

- [X] T055 [US3] `TransactionsService.save`, `delete`, `keepMine`, `useTheirs`, `tryAgain`, `discard`, sync-state mapping in `ft/domain/TransactionMapping.kt` (research R-06, R-07) (depends on T022)
- [X] T056 [US3] Editor edit/detail mode in `ft/ui/editor/*`: read-only kind/wallets with notes, status `InlineAlert` with side-by-side lines and choices, Delete with confirmation dialog, added-by and recurring captions, viewer read-only (depends on T035, T055)
- [X] T057 [P] [US3] Editor screenshots (EN, AR RTL, 2×): edit, waiting, clash (live), deleted elsewhere, delete clash, refused (category, currency changed, no right, other with reference), viewer read-only, delete dialog
- [X] T058 [US3] Write flows `.maestro/008-transactions/US3-S1`, `S2` (slot B archives the category; C5 checkpoint SC-007), `S4-S5`, `S5` never-sent (three parts), `S6` keep mine and use theirs (two parts each), `S7` restore (two parts), `S8` (two parts), `S9`; US3-S10 recorded as manual
- [ ] T059 [US3] Run `/speckit-e2e 008-transactions` for US3 — **deferred: E2E on owner's device**

**Checkpoint**: nothing is lost: every edit, deletion, clash and refusal is visible and resolvable.

---

## Phase 6: User Story 4 - Move money between two of my wallets (Priority: P2)

**Goal**: transfers between same-currency writable wallets, listed in both, moving both balances.

**Independent Test**: offline transfer 500 Cash → Bank: listed in both with both balances changed; online: the server lists one transaction under both wallets.

### Tests for User Story 4

- [X] T060 [P] [US4] Destination tests in `tt/domain/DestinationChoicesTest.kt`: active, visible, same currency, Owner/Editor, not the source; archived, other-currency, viewer and hidden wallets excluded; empty → `destinationsEmpty` (FR-010)
- [X] T061 [P] [US4] Transfer service tests in `tt/domain/TransactionsServiceTransferTest.kt`: create sends `counter_wallet_id`, no category, the source's currency, scope = source; listed in both wallets with direction and "To/From <name>" or "another wallet"; both `deltaFor`s move; edit needs Owner/Editor in both; delete offered per FR-013; `FORBIDDEN` on a transfer → `NoRightInOtherWallet`; `CURRENCY_MISMATCH@/counter_wallet_id` → `CurrenciesDiffer`; `INVALID_ENUM_VALUE@/counter_wallet_id` → `DestinationUnavailable` (FR-010–FR-014)
- [X] T062 [P] [US4] Editor transfer tests: Transfer kind shows the destination choice instead of the category; empty note and Save unavailable; edit mode shows both wallets read-only

### Implementation for User Story 4

- [X] T063 [US4] `ft/domain/DestinationChoices.kt`, `TransactionsService.observeDestinations`, transfer rules in create/save/delete and the view's direction/counterpart (depends on T034, T055)
- [X] T064 [US4] Editor Transfer kind: segment, destination `AppSelectField`, empty note, read-only wallets in edit (depends on T056, T063)
- [X] T065 [P] [US4] Transfer screenshots (EN, AR RTL, 2×): create transfer, no destination available, transfer rows in both wallets, refused "other wallet"
- [X] T066 [US4] Write flows `.maestro/008-transactions/US4-S1`, `S2` (three parts), `S3`; US4-S4, S5, S6 recorded as manual
- [ ] T067 [US4] Run `/speckit-e2e 008-transactions` for US4 — **deferred: E2E on owner's device**

**Checkpoint**: transfers move both balances, one record on both lists.

---

## Phase 7: User Story 5 - Find a transaction (Priority: P2)

**Goal**: local search and filters with chips, matching the server's rules.

**Independent Test**: search "plumb"; add This month + Expense; clear all; offline too.

### Tests for User Story 5

- [X] T068 [P] [US5] Filter tests in `tt/domain/TransactionFilterTest.kt`: search trims, 1–100, case-insensitive, literal `%` and `_`, matches note/details/person of the shown (pending) values only; date presets in the profile zone; custom inclusive range; categories and kinds OR within, AND across; blank search = all (FR-027, FR-028)
- [X] T069 [P] [US5] List reducer/store tests for search and filters: typing updates the query; chips per active filter; clear one / all; no-matches note; filters reset on reopen; category filter choices = used + choosable

### Implementation for User Story 5

- [X] T070 [US5] `ft/domain/TransactionFilter.kt` and the query path in `observeList` (Room range/kind/category, Kotlin search) (depends on T045)
- [X] T071 [US5] Search field, filter chip, `ft/ui/list/FilterSheet.kt` (presets, custom dates with `AppDatePickerDialog`, categories, kinds), active chips, "Clear all", no-matches note in the transactions screen (depends on T047, T070)
- [X] T072 [P] [US5] Screenshots (EN, AR RTL, 2×): search results, filter sheet, active chips, no matches
- [X] T073 [US5] Write flows `.maestro/008-transactions/US5-S1`, `S2-S3`, `S4` (two parts); US5-S5 covered by T069
- [ ] T074 [US5] Run `/speckit-e2e 008-transactions` for US5 — **deferred: E2E on owner's device**

**Checkpoint**: all five stories done on the host.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T075 [P] Arabic copy review of the transactions block (معاملة, Western digits, amounts LTR in RTL rows, transfer "إلى/من", no banned words, no "نسخة"); list the words for owner review in `summary.md`
- [X] T076 [P] Layering test `ht/feature/transactions/ui/TransactionsLayeringTest.kt` modelled on `CategoriesLayeringTest`: `feature/transactions/ui` imports no `data`; `feature/transactions/data` imports nothing from its `domain`/`ui` and no other feature; other features reached only through `…domain`; `feature/wallets` and `feature/categories` import nothing from `feature/transactions`; `designsystem` imports no feature (P-10)
- [X] T077 [P] Accessibility test `ht/feature/transactions/ui/TransactionsAccessibilityTest.kt`: status marks and kinds as text; direction in content descriptions; icon-only buttons described; 48 dp targets; no clipped text at 2× on both screens and the filter sheet (FR-035, P-08)
- [X] T078 [P] Performance test `tt/domain/TransactionsListPerformanceTest.kt`: building the list for 2,000 rows (with 50 pending) under 1 s and a search/filter change under 300 ms on the host (SC-005)
- [X] T079 [P] Log-capture test `tt/data/TransactionLoggingTest.kt`: a send, a clash and a refusal log no amount, note, details, person or payload (FR-036, P-09.2)
- [X] T080 Trace every spec §4 invariant to its passing test (list them in `summary.md`) (P-14.2)
- [X] T081 [P] `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS compiles)
- [X] T082 `./gradlew build allTests` and `:shared:verifyRoborazziAndroidHostTest` green; schema `5.json` committed with the change
- [X] T083 Host QA without a device (research R-25): map every acceptance scenario, FR and edge case to a passing host test in `specs/008-transactions/qa-report.md`; replay every call of contracts/transactions-api-client.md with `curl` against the live local backend (verified QA account, two EGP wallets, one USD wallet, a hand-seeded viewer membership, an unverified account) with the app's headers and bodies; record status, code and pointer; check `balance_minor` and `currency_locked` after create, edit, delete, restore and a transfer; note any drift as a backend contract gap — **QA step** (a separate host QA agent replays the contract against the live backend)
- [X] T084 Turn the captured responses into fixtures `tt/CapturedTransactionResponses.kt` and drive them through the handler, engine and service in `tt/TransactionCapturedResponsesTest.kt` (create 201, replay 200, stale 409 live, 409 tombstone → restore PATCH + PUT, DELETE tombstone, 422 codes, 403 unverified/forbidden, 404, list paging with a transfer in both wallets) (depends on T083) — **QA step** (a separate host QA agent replays the contract against the live backend)
- [X] T085 Static review of the Maestro flows (selectors vs test tags, `checked` vs `selected`, dialog buttons by text, `network-off`/`network-on` parts) and fix what it finds
- [X] T086 Rerun `speckit-contract-check`; confirm `transactions.yaml` is unchanged since `c0e9844`; record drift as FRs or follow-ups in spec §5
- [ ] T087 Run quickstart.md manual checks 1–12 on the dev build; record deviations in plan.md "Implementation notes" — **deferred: E2E on owner's device**
- [ ] T088 Run the full `/speckit-e2e 008-transactions` and write `specs/008-transactions/e2e-report.md`, including the brief's integration checkpoint (three offline transactions sync; transfer moves both balances; API edit → conflict choice) and C5's (SC-007); no `FAIL (app)` or `FAIL (env)` attributable to the contract — **deferred: E2E on owner's device**
- [X] T089 Update the C6 row in `specs/briefs/README.md` ("Backend state" built, "Client state" built) and the C5 row's note that its choice and display are now used by C6
- [X] T090 Write `specs/008-transactions/summary.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (prerequisite gate); T002–T003 after.
- **Foundational (Phase 2)**: after Setup; blocks all stories. Inside: T015 → T016; T018 → T019; T016 + T019 → T020 → T021, T022, T024; T017 + T020 → T023; T022 → T025; T021 + T023 + T024 + T025 → T026.
- **US1 (Phase 3)**: after Phase 2.
- **US2 (Phase 4)**: after Phase 2; its list opens the US1 editor, so after US1 when staffed by one person.
- **US3 (Phase 5)**: after US1's editor (T035) and service (T034).
- **US4 (Phase 6)**: after US1 (create) and US3 (edit/delete rules).
- **US5 (Phase 7)**: after US2's list (T045, T047).
- **Polish (Phase 8)**: after all stories; T083 → T084; T090 is last.

### User Story Dependencies

- US1: none beyond Foundation (MVP).
- US2: shows what US1 records; the carry needs the handler (T021).
- US3: extends US1's editor.
- US4: extends US1's create and US3's edit/delete.
- US5: extends US2's list.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T004–T013 all [P]; T014, T015, T017, T018, T027, T028 in parallel.
- US5 filter rules (T068, T070) can be built beside US3/US4 by a second person (different files).
- Within each story the test tasks are [P]; screenshot tasks run beside the next story's tests.

## Parallel Example: User Story 2

```text
T041 DayGroupingTest + ListTest  |  T042 BalanceReconcilerTest  |  T043 RefreshTest  |  T044 list reducer/store tests
then: T045 observeList  →  T046 reconciler  →  T047 Transactions screen  →  T048 screenshots  →  T049 flows  →  T050 (deferred)
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: an expense recorded offline is queued in the wallet scope and sent; the wallet's displayed balance includes it; host tests green.

### Incremental Delivery

1. US1 → US2 (the core loop with the right balance).
2. US3 (corrections, clashes, restore).
3. US4 (transfers), US5 (find).
4. Polish: host QA with live contract checks and captured fixtures, contract recheck, summary; device E2E and quickstart deferred to the owner.
