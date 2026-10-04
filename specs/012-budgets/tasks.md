---
description: "Task list for 012-budgets"
---

# Tasks: Budgets (client)

**Input**: Design documents from `specs/012-budgets/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (budgets-api-client.md, budgets-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `9eedc12` (specs 004–011: engine, entitlements, wallets, categories, transactions, feeds + batch + heads, recurring; Room v8)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per component and screen; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/012-budgets/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Host QA (Phase 9) replays every call against the live local backend with `curl` and turns the real answers into fixtures, as specs 007–011 did.

**Organization**: Phase 2 builds storage, the wire client, the handler and applier, the pure domain (periods, math, totals, selection, validation), `OwnSpending` in transactions, the hooks and the design-system change. US1 (set a budget) is the MVP; US2 shows progress; US3 makes it move offline and match the server; US4 limit/archive/delete; US5 start-day periods; US6 sync completeness.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US6 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); budgets feature `cm/feature/budgets/…` (`fb/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); budgets tests `ct/feature/budgets/…` (`bt/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Transactions feature `cm/feature/transactions/…` (`ft/…`), tests `ct/feature/transactions/…` (`tt/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: budgets ui → budgets domain → budgets data; `fb/data` imports no domain, ui or other feature; budgets domain reaches transactions, categories, wallets, account and auth only through their `domain`; no other feature imports `feature.budgets`), naming (P-11).
- Glossary (P-01): Budget, Budget Period, budget start day; no identifier or copy uses "envelope" or "plan" for a budget; nothing user-facing says "version", "tombstone", "scope" or "cursor".
- Money (P-03): `Long` minor units only; integer arithmetic (ceil division for the projection); fraction digits from the currency; U+2212 for a negative remaining.
- Dates (P-05): `LocalDate` for period dates and transaction dates; "today" from the profile's zone only for derivation, never written.
- Never log amounts, limits, spending, projections or payloads; ids, outcomes, codes, request ids, counts only (P-09.2).
- **MVI screen recipe (P-16)** as specs 001–010: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas, `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin**: repositories and services resolve `SyncEngine` lazily (spec 006 lesson); queue discards from `onDiscarded` run in a launched job (spec 008 deadlock lesson); `budgetsModule` is listed after `authModule`, `categoriesModule`, `transactionsModule` and `recurringModule` so its rebinding wins; `AppGraphTest` stays green.
- **Merge hotspots**: `AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt`, `TransactionDao` (in `TransactionEntity.kt`), `RecurringLinks.kt`, `RecurringLinksImpl.kt`, `Categories.kt` (design system), `CatalogSections.kt`, Home contract/VM/screen, `AuthModule.kt`, `AppModules.kt`, `Routes.kt`, `AppNavHost.kt`: additive only. New strings in one append-only block `<!-- Budgets (spec 012) -->` at the end of each `strings.xml`.
- Every interactive element gets the `testTag` of contracts/ui-and-e2e.md; dialog buttons are tapped by text in flows.

---

## Phase 1: Setup

**Purpose**: confirm prerequisites, create the package, strings.

- [X] T001 Confirm on `012-budgets` (from `main` `9eedc12`): `AppDatabase` version 8 with `MIGRATION_7_8` and `shared/schemas/…/8.json`; `sync_scope.feed_position`; `FeedApplier`, `SyncRunner`, `BatchItem`/`RecordKey`, `SyncHandler.batchItem`/`prerequisites`/`onDiscarded` in `cm/core/sync/`; `ProfileSyncer.view` with `budgetStartDay`/`budgetStartDayFrom`; `CategoriesService.observeChoices`/`observeDisplays`; `PendingCategoryUses` with two sources in `ht/core/di/AppGraphTest.kt`; `RecurringLinks` in `ft/domain/`; `DueSummary` hook pattern in `cm/feature/auth/domain/`; `BudgetProgress` in `cm/designsystem/component/`; `.maestro/common/api.js`. If any is missing, stop and report
- [X] T002 Create `fb/di/BudgetsModule.kt` with an empty `val budgetsModule = module { }` and append it at the end of `appFeatureModules` in `cm/AppModules.kt` (hotspot: one list element)
- [X] T003 [P] Add the string block `<!-- Budgets (spec 012) -->` with every key from contracts/ui-and-e2e.md "Copy" plus list/editor/Home labels, refusal copy, confirmations and empty state (about 70) to the end of `values/strings.xml` and `values-ar/strings.xml` (glossary words of spec §2; Western digits; no exclamation marks; research R-14)

---

## Phase 2: Foundational (blocking)

**Purpose**: Room v9, wire client, handler, applier, pure domain, `OwnSpending`, hooks, design system, navigation keys.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T004 [P] Migration test `ht/core/database/Migration8To9Test.kt`: from a v8 database with a `user:<id>` scope (cursor, read stamp, position set) and two `wallet:<id>` scopes, after `MIGRATION_8_9` the tables `budget` and `budget_progress` exist with data-model §1–§2 columns and index `index_budget_account_id_archived_at`; the user scope's `feed_cursor`, `feed_read_at`, `feed_position` are NULL; wallet scopes keep all three; every other row kept (FR-020)
- [X] T005 [P] DAO test `ht/feature/budgets/data/BudgetDaoTest.kt`: upsert/observe by account, active vs archived queries, delete with its `budget_progress` row in one transaction, progress `replaceWalk` (deletes rows of non-archived budgets not returned, keeps archived budgets' rows, research R-09)
- [X] T006 [P] Contract tests `bt/data/BudgetApiContractTest.kt` (MockEngine, budgets.yaml examples): A1 create with category, create all-expenses with explicit `"category_id": null`, save with `version`; exactly the `BudgetWrite` members; A2 `DELETE` with `{"version": n}` body; A3 archive path and body; A4 `GET /budget-progress?limit=200` with no `date`/`filter`/`sort`, cursor follow, decoding `currentPeriod` and `pastPeriodOver` examples (null `days_elapsed`, null `projected_end`), unknown member ignored; A5 single progress; every A1 code mapped by `code` + pointer (contracts/budgets-api-client.md)
- [X] T007 [P] Handler tests `bt/data/BudgetSyncHandlerTest.kt`: batch upsert item has type `budget`, `walletId == null`, `BudgetWrite` body; for `version > 0` category/currency/period type come from the stored row, not the payload; delete item carries `{version}`; `archive = true` → `batchItem` null; `send` = PUT then archive (archive-only with unchanged limit skips the PUT); `prerequisites` = `RecordKey("category", id)` for a payload category, none for all expenses; `canRestore == false`; `onDiscarded` removes a placeholder only; `refusalFields` maps `/category_id`, `/amount_minor`, `/currency`, `/period_type` (research R-04)
- [X] T008 [P] Applier tests `bt/data/BudgetFeedApplierTest.kt`: `ScopeKind.User`, row type `budget`; older version never replaces newer; tombstone removes the row and its progress unless a change waits; unknown members and an unknown `period_type` are kept; `sweep` removes synced budgets not seen, never placeholders or rows with a waiting change (FR-019)
- [X] T009 [P] Period tests `bt/domain/BudgetPeriodsTest.kt` (table): start days 1, 15, 25, 28 across Jan–Dec incl. Feb 28/29 and 31-day months; `appliesFrom` null, past, today, future (pending → stored server period containing today, else `Unknown`); profile absent → day 1; days in period and days elapsed (backend spec 015 US5 scenarios 1–2: 2026-10-02 with day 25 → 2026-09-25..2026-10-24; day 1 → 25 changed on 2026-10-10 → 2026-10-01..2026-10-24 from the stored period) (FR-009, FR-010, SC-004)
- [X] T010 [P] Math tests `bt/domain/BudgetMathTest.kt`: remaining negative when over; over limit exactly when spent > limit; percentage half up via `budgetPercent` (3,420 of 4,000 = 86; 0; 112); projection = ceil(spent × days ÷ elapsed) (350,000 × 30 ÷ 8 = 1,312,500), null for null/0 elapsed, null above 2^53 − 1, null when the product would overflow; EGP, KWD, JPY digits (FR-013, R-07)
- [X] T011 [P] Totals tests `bt/domain/SpendingTotalsTest.kt`: from a `SpendingSnapshot`, per budget: category vs all expenses, currency match, other currencies sorted and never summed, `includesWaiting` when any waiting touch matches (period, currency, category or any for all expenses), synced-only totals from `syncedExpenses` (FR-012, R-06)
- [X] T012 [P] Selection tests `bt/domain/ProgressSelectionTest.kt` (spec FR-014 (a)–(d)): server shown iff stored period == current, no waiting touch, synced-only now == stored local figures, stored limit == effective limit; otherwise phone; waiting accepted before the next read → phone figure stays (no jump back); server covers rows the phone lacks; limit edit → phone (FR-014, R-06)
- [X] T013 [P] Validation tests `bt/domain/BudgetValidationTest.kt`: limit 1 … 2^53 − 1; zero, negative, too large, too many fraction digits for the currency refused with a field error; duplicate (category or ALL, currency) among active budgets refused as `AlreadyBudgeted` (FR-003, FR-004)
- [X] T014 [P] `OwnSpending` tests `tt/domain/TransactionOwnSpendingTest.kt` (fake engine + fake rows): own expenses only (created_by == account); income, transfer, deleted, carry-only rows excluded; wallet not on the phone excluded; queued create/edit/delete overlaid (edit moving the date or category in and out of range); refused and clashing changes overlaid; pending confirmation counted for `ruleAuthorOf` (partner author → excluded; null → the person); `syncedExpenses` ignores the queue and pending confirmations; `waitingTouches` carries both sides of each queued change (research R-05)
- [X] T015 [P] Room test `ht/feature/transactions/data/OwnSpendingQueryTest.kt`: the new `TransactionDao` query returns rows dated in range plus rows whose id is in the waiting set plus pending confirmations, for one account only

### Implementation

- [X] T016 [P] `fb/data/local/BudgetEntity.kt` + `BudgetDao` (columns of data-model §1 verbatim: `category_id` nullable, `period_type` TEXT, `amount_minor` INTEGER, `currency`, `archived_at` nullable, `version`, `change_seq`, `created_at`, `updated_at`, `refreshed_at`, `account_id`; index `(account_id, archived_at)`; `toString` id + version only)
- [X] T017 [P] `fb/data/local/BudgetProgressEntity.kt` + `BudgetProgressDao` (data-model §2 verbatim: `budget_id` PK, `period_start`, `period_end`, `days_in_period`, `limit_minor`, `spent_minor`, `currency`, `other_currencies` comma-separated, `local_spent_minor`, `local_other_currencies`, `read_at`, `account_id`; `replaceWalk(accountId, rows, archivedIds)`; redacted `toString`)
- [X] T018 `MIGRATION_8_9` in `cm/core/database/Migrations.kt` (data-model §4 SQL; KDoc citing FR-020), `AppDatabase` v9 with both entities and DAOs, `buildAppDatabase` adds the migration, `DatabaseCleaner` deletes `budget_progress` then `budget`; build exports `shared/schemas/…/9.json` (depends on T016, T017)
- [X] T019 [P] DTOs `fb/data/dto/BudgetDtos.kt`: `BudgetDto` (all `Budget` members, unknown ignored), `BudgetWriteRequest` (`category_id` with explicit null encoding, `period_type`, `amount_minor`, `currency`, `version`), `BudgetVersionRequest`, `BudgetProgressDto` + `BudgetPeriodDto` + `MoneyDto` reuse, `BudgetProgressPageDto`; no percentage member; redacted `toString`
- [X] T020 `fb/data/BudgetApi.kt`: `put`, `delete` (JSON body), `archive`, `progressPage(cursor)`, `progressOne(id)` on the shared client (depends on T019)
- [X] T021 [P] `fb/data/BudgetPayload.kt` (data-model §5: `category_id`, `currency`, `amount_minor`, `archive`; serializable; redacted)
- [X] T022 `fb/data/BudgetRepository.kt` (observe active/archived/one, placeholder create, `storeServerCopy`/`storeFeedCopy` with the version guard, tombstone rule, `sweepOwn`, `removePlaceholder`, lazy `hasQueuedChange`) and `fb/data/BudgetProgressRepository.kt` (store page rows with local figures, `replaceWalk`, observe) (depends on T016–T018, T021)
- [X] T023 `fb/data/BudgetSyncHandler.kt` per research R-04 (depends on T020–T022)
- [X] T024 `fb/data/BudgetFeedApplier.kt` per research R-09 (depends on T022)
- [X] T025 [P] Pure domain in `fb/domain/`: `BudgetPeriods.kt`, `BudgetMath.kt`, `SpendingTotals.kt`, `ProgressSelection.kt`, `BudgetValidation.kt`, `DayTicker.kt` (minute ticks, research R-08), models in `fb/domain/model/` (data-model §6) (makes T009–T013 pass)
- [X] T026 `OwnSpending`, `OwnExpense`, `SpendingKey`, `SpendingSnapshot` in `ft/domain/OwnSpending.kt`; `TransactionDao.observeForSpending(accountId, from, to, waitingIds)` in `ft/data/local/TransactionEntity.kt`; `TransactionOwnSpending` in `ft/domain/` (one snapshot of rows + `engine.observeAll("transaction")`, wallets from `WalletsService.observeList()`, rules of research R-05); bind in `ft/di/TransactionsModule.kt` (makes T014–T015 pass)
- [X] T027 `RecurringLinks.ruleAuthorOf(ruleId): String? = null` in `ft/domain/RecurringLinks.kt` (default-off) and its implementation from `recurring_rule.created_by` in `cm/feature/recurring/domain/RecurringLinksImpl.kt` with a test in `ct/feature/recurring/domain/RecurringLinksRuleAuthorTest.kt`
- [X] T028 [P] `BudgetSummary`, `BudgetGlimpse` (redacted `toString`), `NoBudgetSummary` in `cm/feature/auth/domain/BudgetSummary.kt`; bind `NoBudgetSummary` in `cm/feature/auth/di/AuthModule.kt`
- [X] T029 [P] Design system: `CategoryChoiceItem.enabled: Boolean = true` in `cm/designsystem/component/Categories.kt` (disabled row: caption shown, not selectable, `disabled()` semantics, ink2 text); catalogue entries in `cm/designsystem/catalog/CatalogSections.kt` (a disabled "Already budgeted" row; a budgets section with under/near/over/waiting `BudgetProgress` rows plus caption lines); goldens EN LTR, AR RTL, 2.0 in `ht/designsystem/ComponentScreenshotTest.kt` (research R-13)
- [X] T030 Koin `fb/di/BudgetsModule.kt`: `BudgetApi`, repositories (lazy engine), `BudgetSyncHandler` bind `SyncHandler`, `BudgetFeedApplier` bind `FeedApplier`, placeholders for service/coordinator/hooks filled by later tasks; `ht/core/di/AppGraphTest.kt` gains `theBudgetsModuleStartsWithItsHandlerAndApplier` (handler type `budget`, user applier `budget`, `OwnSpending` resolves) (depends on T023, T024, T026)
- [X] T031 [P] Navigation keys `BudgetsKey`, `BudgetEditorKey(budgetId: String? = null)` in `cm/navigation/Routes.kt`

**Checkpoint**: storage, sync, wire and pure domain ready; all Phase 2 tests green.

---

## Phase 3: User Story 1 - Set a monthly budget for a category or for all expenses (Priority: P1) 🎯 MVP

**Goal**: create a budget offline or online; it is listed at once and syncs.

**Independent Test**: offline, create Groceries 6,000 EGP; listed with "Waiting to sync" and the current period's Groceries spending; online, it syncs unchanged.

### Tests for User Story 1

- [X] T032 [P] [US1] Service tests `bt/domain/BudgetsServiceCreateTest.kt`: create queues one `budget` change in `user:<account>` with base version 0 and a UUIDv7 id and writes a placeholder; unverified → `NotVerified`, nothing queued; invalid limit → `Invalid`; duplicate → `AlreadyBudgeted`; offline does not block (FR-001, FR-002, FR-004)
- [X] T033 [P] [US1] Choices tests `bt/domain/BudgetChoicesTest.kt`: "All expenses" first, then C5 own active and defaults (expense only); currencies = wallets' currencies on the phone ∪ profile default, default selected = profile default; "Already budgeted" keys for the chosen currency from active budgets; currency switch clears a chosen category already budgeted in it (FR-003, FR-004)
- [X] T034 [P] [US1] Editor reducer + ViewModel tests `bt/ui/editor/BudgetEditorReducerTest.kt`, `BudgetEditorViewModelTest.kt` (create mode): pick category/currency/limit, field errors, save → effect close, verification prompt effect, disabled save while invalid
- [X] T035 [P] [US1] List reducer + ViewModel tests `bt/ui/list/BudgetsReducerTest.kt`, `BudgetsViewModelTest.kt`: empty state with "Set a budget", a waiting budget row with its status, Add → navigate to the editor

### Implementation for User Story 1

- [X] T036 [US1] `fb/domain/BudgetsService.kt` (`DefaultBudgetsService`): `create`, `observeChoices`, `observeBudget`, base `observeOverview` (list + status overlay, figures filled in US2), verification gate from `SessionManager`, lazy engine (contracts/budgets-domain.md) (makes T032–T033 pass)
- [X] T037 [US1] Editor screen `fb/ui/editor/` (`BudgetEditorContract.kt`, `BudgetEditorViewModel.kt`, `BudgetEditorRoute.kt`, `BudgetEditorScreen.kt`): create mode with category sheet (`CategoryChoiceSheet` with disabled rows), currency `SelectionSheet`, `AmountField`, period line "Monthly, starting on day <n>", tags of contracts/ui-and-e2e.md (makes T034 pass)
- [X] T038 [US1] Budgets screen `fb/ui/list/` (Contract, ViewModel, Route, Screen): active list, empty state, Add action, status lines, tags (makes T035 pass)
- [X] T039 [US1] Wire service and both ViewModels in `fb/di/BudgetsModule.kt`; `AppNavHost` entries for `BudgetsKey` and `BudgetEditorKey` in `cm/navigation/AppNavHost.kt`
- [X] T040 [US1] Home "Budgets" row: `HomeIntent.BudgetsClicked`, `HomeEffect.NavigateBudgets` in `cm/feature/auth/ui/home/HomeContract.kt`, `HomeViewModel.kt`, `HomeScreen.kt` (tag `home.budgets`), nav wiring; Home reducer/VM tests updated in `ct/feature/auth/ui/home/`
- [X] T041 [P] [US1] Screenshots `ht/feature/budgets/ui/BudgetsScreenshotTest.kt`: empty budgets screen, editor create (with a disabled "Already budgeted" row) — EN LTR, AR RTL, 2.0
- [X] T042 [P] [US1] Maestro flows `.maestro/012-budgets/US1-create-groceries.yaml`, `US1-duplicate-blocked.yaml` (`# deferred: E2E on owner's device`); `api.js` gains `createBudget`, `deleteBudget`, `archiveBudget`, `listBudgetProgress`, `setStartDay`
- [ ] T043 [US1] Run the US1 flows — **deferred: E2E on owner's device**

**Checkpoint**: a budget can be created offline, listed and sent (batch upsert), and arrives on another phone through the feed.

---

## Phase 4: User Story 2 - See where I stand this period (Priority: P1)

**Goal**: figures on the budgets screen and on Home, neutral and complete.

**Independent Test**: 6,000 EGP Groceries, 3,500 EGP spent on day 8 of 30 → "3,500 EGP of 6,000", "2,500 EGP left", 58%, "On pace for 13,125 EGP".

### Tests for User Story 2

- [X] T044 [P] [US2] Overview tests `bt/domain/BudgetsOverviewTest.kt`: phone figures per budget from `OwnSpending` + `SpendingTotals` + `BudgetMath`; active oldest first, archived separate; period line data (dates, days left = end − today, last day flag); "On pace" omitted when spent is 0; other currencies; unknown period → no figures; unknown period type → read-only, no figures (FR-012–FR-016, R-15)
- [X] T045 [P] [US2] Home summary tests `bt/domain/BudgetsHomeSummaryTest.kt`: up to 3 active budgets, highest percentage first then oldest; unknown-period budgets after the others; archived never; null name for all expenses; display names from C5 in the app language (FR-017)
- [X] T046 [P] [US2] Home reducer/VM tests in `ct/feature/auth/ui/home/`: budgets card state, `BudgetClicked(id)` → `OpenBudget(id)`, "See all" → `NavigateBudgets`, card hidden when empty
- [X] T047 [P] [US2] Copy test `bt/ui/BudgetCopyTest.kt`: no "envelope"/"plan" in budget strings (EN), no "!" in any budget string (EN/AR), every key present in both files (FR-015, FR-025)

### Implementation for User Story 2

- [X] T048 [US2] Figures in `DefaultBudgetsService.observeOverview`/`observeBudget` (combine budgets, profile view, `DayTicker`, `OwnSpending.observe(period)`, stored progress, C5 displays) (makes T044 pass)
- [X] T049 [US2] `fb/domain/BudgetsHomeSummary.kt` implementing `BudgetSummary`; rebind in `fb/di/BudgetsModule.kt` (makes T045 pass)
- [X] T050 [US2] `fb/ui/BudgetRowContent.kt`: `BudgetProgress` + caption lines ("On pace for", "Also spent in … (not counted)", "Includes changes waiting to sync", "Progress appears after sync" with the limit) with tokens only; list rows and period line in the budgets screen; archived section
- [X] T051 [US2] Home "Budgets" card in `HomeScreen.kt` (tags `home.budgetsCard`, `home.budgetsCard.all`), `HomeViewModel` takes `budgets: Flow<List<BudgetGlimpse>>` wired in `cm/feature/account/di/AccountModule.kt` (where Home is built) (makes T046 pass)
- [X] T052 [P] [US2] Screenshots: budgets screen with under/near/over/other-currency/waiting/unknown-period rows and archived section; Home card — EN LTR, AR RTL, 2.0 (Home goldens re-recorded on purpose)
- [X] T053 [P] [US2] Maestro flows `.maestro/012-budgets/US2-home-card.yaml`, `US2-over-and-pace.yaml` (deferred header)
- [ ] T054 [US2] Run the US2 flows — **deferred: E2E on owner's device**

**Checkpoint**: every active budget shows neutral, complete figures on the screen and Home.

---

## Phase 5: User Story 3 - Progress moves offline at once and matches the server after sync (Priority: P1)

**Goal**: instant offline movement; server figures after sync; no jump.

**Independent Test**: offline 200 EGP Groceries expense → spent +200 with "Includes changes waiting to sync"; online sync → note gone, figures equal `GET /budget-progress`.

### Tests for User Story 3

- [X] T055 [P] [US3] Refresh tests `bt/domain/BudgetsRefreshTest.kt`: `refresh` runs `runner.refresh(Everything, force)` then walks every progress page (no `date`, `limit=200`), storing server figures with the synced-only figures of one `OwnSpending` snapshot taken after the reads; `INVALID_CURSOR` restarts once; offline/timeout/5xx keep stored rows and report Offline/Failed; 60 s floor, forced by pull; `refreshOne` only for `version > 0`, 404 ignored (FR-018, R-09)
- [X] T056 [P] [US3] Sequence test `bt/domain/BudgetsOfflineToServerTest.kt` (fake API + fake engine + real pure parts): read → server shown; offline expense → phone shown with waiting note; send accepted → phone shown, no jump; next read → server shown and equal (US3-S1–S3, SC-002)
- [X] T057 [P] [US3] Coordinator test `bt/domain/BudgetsSyncCoordinatorTest.kt`: sign-in and foreground trigger one `refresh(force = false)`; nothing after sign-out

### Implementation for User Story 3

- [X] T058 [US3] `refresh`/`refreshOne` in `DefaultBudgetsService` with floors and the walk; `ProgressSelection` wired into the figures (makes T055–T056 pass)
- [X] T059 [US3] `fb/domain/BudgetsSyncCoordinator.kt` (created at start, sign-in and foreground) registered in `fb/di/BudgetsModule.kt` (makes T057 pass)
- [X] T060 [US3] Budgets screen: refresh on open (floor) and pull (forced); offline "Updated …" and failure snackbar with request id; editor open calls `refreshOne`
- [X] T061 [P] [US3] Maestro flow `.maestro/012-budgets/US3-offline-moves-then-matches.yaml` (parts a/b, `network-off`/`network-on`, deferred header)
- [ ] T062 [US3] Run the US3 flow — **deferred: E2E on owner's device**

**Checkpoint**: the brief's integration checkpoint passes on host (SC-007).

---

## Phase 6: User Story 4 - Change the limit, archive or delete a budget (Priority: P2)

**Goal**: limit edits, one-way archive, final delete, with clash and refusal choices.

**Independent Test**: offline raise Groceries to 7,000 (moves at once), archive it (moves to "Archived"), delete another (gone); sync → all reach the server.

### Tests for User Story 4

- [X] T063 [P] [US4] Service tests `bt/domain/BudgetsServiceChangeTest.kt`: `saveLimit` queues the stored category/currency with the new limit and base version; archive queues `archive = true` (with a waiting limit edit → one change, PUT then archive); archived budget limit editable; delete queues the deletion; never-sent budget delete removes the placeholder and discards the change, nothing sent; unverified → nothing queued (FR-005, FR-006)
- [X] T064 [P] [US4] Choices tests `bt/domain/BudgetsServiceChoicesTest.kt`: clash → keep mine re-queues on the newer version; use theirs applies the server copy; tombstone clash → accept the deletion removes the budget and its progress; refused → try again / discard (FR-007)
- [X] T065 [P] [US4] Error copy tests `bt/ui/BudgetErrorCopyTest.kt`: `INVALID_ENUM_VALUE /category_id`, `OUT_OF_RANGE` and `AMOUNT_NOT_INTEGER /amount_minor`, `INVALID_CURRENCY`, `IMMUTABLE_FIELD`, `EMAIL_NOT_VERIFIED`, `NOT_FOUND`, unknown code → generic with request id; by code + pointer only (FR-007, FR-023)
- [X] T066 [P] [US4] Editor reducer/VM tests (edit mode) in `bt/ui/editor/`: read-only category/currency/period with reason; save enabled only on a changed valid limit; archive and delete confirmations; status choices per state

### Implementation for User Story 4

- [X] T067 [US4] `saveLimit`, `archive`, `delete`, `keepMine`, `useTheirs`, `tryAgain`, `discard` in `DefaultBudgetsService` (makes T063–T064 pass)
- [X] T068 [US4] `fb/ui/BudgetErrorCopy.kt` (makes T065 pass)
- [X] T069 [US4] Editor edit/detail mode: progress at the top (`BudgetRowContent`), limit field, "Archive budget"/"Delete budget" with dialogs ("Yes, archive it"/"Yes, delete it"), status block with choices, tags (makes T066 pass)
- [X] T070 [P] [US4] Screenshots: editor edit mode (active, archived, clash, deleted elsewhere, refused) — EN LTR, AR RTL, 2.0
- [X] T071 [P] [US4] Maestro flows `.maestro/012-budgets/US4-limit-archive-delete.yaml`, `US4-clash.yaml` (deferred header)
- [ ] T072 [US4] Run the US4 flows — **deferred: E2E on owner's device**

**Checkpoint**: every write path queues correctly and every server answer has a visible outcome.

---

## Phase 7: User Story 5 - Budget Periods follow my start day (Priority: P2)

**Goal**: periods equal the server's, including a pending start day change and midnight.

**Independent Test**: day 25 since always, on 2 Oct → 25 Sep – 24 Oct; change to 1 on 10 Oct (applies from 1 Nov) → still 25 Sep – 31 Oct (stored server period) and "From 1 Nov, periods start on day 1".

### Tests for User Story 5

- [X] T073 [P] [US5] Overview tests `bt/domain/BudgetsPeriodOverviewTest.kt`: pending change uses the stored server period and shows the pending line; none stored → `Unknown` and "Progress appears after sync"; a profile edit waiting on this phone changes nothing; `appliesFrom` reached → new periods; profile time zone vs device zone for "today" (FR-009, FR-010)
- [X] T074 [P] [US5] Ticker test `bt/domain/DayTickerTest.kt` + overview re-derivation at midnight in the profile zone (test clock) (US5-S6)

### Implementation for User Story 5

- [X] T075 [US5] Pending-change path and pending line in `DefaultBudgetsService.observeOverview`; profile zone from `ProfileSyncer.view` else `ClientInfo.timezoneId()` (makes T073–T074 pass)
- [X] T076 [US5] Budgets screen period lines (`budgets.period`, `budgets.pending`, "Last day of the period"); editor period line with the server start day
- [X] T077 [P] [US5] Maestro flow `.maestro/012-budgets/US5-start-day.yaml` (deferred header)
- [ ] T078 [US5] Run the US5 flow — **deferred: E2E on owner's device**

**Checkpoint**: SC-004 holds on host.

---

## Phase 8: User Story 6 - Budgets on every phone (Priority: P3)

**Goal**: complete sync lifecycle: first read after update, batch, category in use, sign-out.

**Independent Test**: a budget created through the API before the update appears after the first sync on an updated phone.

### Tests for User Story 6

- [X] T079 [P] [US6] Engine integration test `bt/data/BudgetBatchTest.kt` (real engine + fake `SyncApi`): budget items ride the same batch as category and transaction items without `wallet_id`; a budget naming an unaccepted category create is held (no try counted); archive goes per record at its place in the order (FR-019)
- [X] T080 [P] [US6] Category uses test `bt/domain/BudgetCategoryUsesTest.kt`: synced and waiting budgets' categories in use; all-expenses adds nothing; `ht/core/di/AppGraphTest.kt` asserts three `PendingCategoryUses` sources incl. `BudgetCategoryUses` and that `BudgetSummary` resolves to `BudgetsHomeSummary` (FR-021)
- [X] T081 [P] [US6] Sign-out test: `ht/feature/account/SignOutWipeTest.kt` extended — `budget`, `budget_progress` empty and budget queue rows gone after sign-out (FR-022)
- [X] T082 [P] [US6] Feed integration test `bt/data/BudgetFeedReadTest.kt` (real `FeedPuller` + fake `SyncApi`): after the 8 → 9 reset the user scope is read from the start, `budget` rows applied, other user-scope types still applied and swept as in spec 009 (US6-S1, FR-020)

### Implementation for User Story 6

- [X] T083 [US6] `fb/domain/BudgetCategoryUses.kt` bound as `PendingCategoryUses` in `fb/di/BudgetsModule.kt`; fix anything T079 and T082 reveal in the handler, applier or repository (makes T079–T082 pass)
- [X] T084 [P] [US6] Maestro flow `.maestro/012-budgets/US6-update-reads-budgets.yaml` (deferred header)
- [ ] T085 [US6] Run the US6 flow — **deferred: E2E on owner's device**

**Checkpoint**: all six stories green on host.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T086 [P] Arabic copy review of the budgets block (ميزانية, فترة الميزانية, كل المصروفات, الحد, بهذا المعدل ستصل إلى; Western digits; amounts LTR in RTL rows); list words for owner review in `summary.md`
- [X] T087 [P] Layering test `ht/feature/budgets/ui/BudgetsLayeringTest.kt` modelled on `RecurringLayeringTest`: `feature/budgets/ui` imports no `data`; `feature/budgets/data` imports nothing from its domain/ui and no other feature; other features only through `…domain`; no feature other than budgets imports `feature/budgets`; `core.sync` imports no feature (P-10); nothing in `feature/budgets` references a notification API (FR-026)
- [X] T088 [P] Accessibility test `ht/feature/budgets/ui/BudgetsAccessibilityTest.kt`: percentage and words present in every state; disabled choice announced; icon buttons described; 48 dp; no clipped text at 2× on both screens and the Home card (P-08)
- [X] T089 [P] Performance test `ht/feature/budgets/BudgetsPerformanceTest.kt`: in-memory Room with 20,000 current-period transactions and 50 budgets → overview emitted under 1 s; a 200-row progress page stored under 200 ms (SC-008)
- [X] T090 [P] Log-capture test `bt/data/BudgetLoggingTest.kt`: a send, an archive, a refusal, a clash and a progress walk log no amount, limit, spent, projection or payload (FR-024)
- [X] T091 Trace every spec §4 invariant to its passing test (list in plan.md "Implementation notes"; parent copies into `summary.md`) (P-14.2)
- [X] T092 [P] `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS compiles)
- [X] T093 `./gradlew build allTests` and `:shared:verifyRoborazziAndroidHostTest` green; `9.json` exported
- [X] T094 Host QA without a device (quickstart "Host QA"): map every acceptance scenario, FR and edge case to a passing host test in `specs/012-budgets/qa-report.md`; replay every call of contracts/budgets-api-client.md with `curl` against the live local backend with the app's headers and bodies (owner A with EGP and USD wallets, partner B as editor in the shared wallet, an unverified account): create category/all-expenses budgets, replay, save, stale 409, tombstone 409, archive (and again), delete (and again), income category 422, limit 0 and 2^53 422, unverified 403, another person's id 404; batch upsert/delete items, a `wallet_id` on a budget item (422 whole batch), archive as batch op (422); progress list paging at limit 1 with `INVALID_CURSOR` for another period's date; per-budget progress for archived and deleted (404); user-feed `budget` rows incl. tombstone; start day changes with `budget_period_start_day_from`; A's expenses, income, transfer, USD expense, deleted expense, B's expense, a recurring rule's confirmed occurrence — **QA step** (not done in the implement pass: needs the live backend; the parent runs it)
- [X] T095 Turn the captured answers into fixtures `bt/CapturedBudgetResponses.kt` and drive them through the real `BudgetApi`/`HttpSyncApi`, engine, handler, applier, `TransactionOwnSpending`, `SpendingTotals`, `BudgetPeriods` and `ProgressSelection` in `bt/BudgetCapturedResponsesTest.kt`, asserting the phone's figures and periods equal the server's (SC-003, SC-004) (depends on T094) — **QA step** (not done in the implement pass)
- [X] T096 Static review of the Maestro flows (selectors vs test tags, dialog buttons by text, `network-off`/`network-on` parts, `# deferred` header) and fix what it finds
- [X] T097 Rerun `speckit-contract-check`; confirm `budgets.yaml` and the budget parts of `sync.yaml` unchanged since `64ca1ea`; record drift as FRs or follow-ups in spec §5
- [X] T098 Update README "On-device database" (Room v9; budgets and their stored progress; the user scope re-read once) and the C8 row of `specs/briefs/README.md`
- [ ] T099 Run quickstart.md manual checks on the dev build — **deferred: E2E on owner's device**
- [ ] T100 Run the full `/speckit-e2e 012-budgets` and write `specs/012-budgets/e2e-report.md` incl. the brief's checkpoint (SC-007) — **deferred: E2E on owner's device**
- [X] T101 Write specs/012-budgets/summary.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 (blocks all stories) → US1 (MVP) → US2 → US3; US4 after US1 (editor) and US2 (row content); US5 after US2 (overview); US6 after US1 → Polish.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T004–T015 all [P]; T016, T017, T019, T021, T025, T028, T029, T031 in parallel.
- US5 (periods) can run beside US3/US4 once US2's overview exists (different files except the service; coordinate).
- Within each story the test tasks are [P]; screenshot and flow tasks run beside the next story's tests.

## Parallel Example: User Story 2

```text
T044 overview  |  T045 Home summary  |  T046 Home store  |  T047 copy
then: T048 service figures  →  T049 summary  →  T050 row content  →  T051 Home card  →  T052 screenshots  →  T053 flows  →  T054 (deferred)
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: a budget created offline is queued in the user scope and sent as a batch upsert; a budget from another phone is applied from the user feed; host tests green.

### Incremental Delivery

1. US1 → US2 (figures) → US3 (offline then server; the brief's checkpoint).
2. US4 changes, US5 periods, US6 sync completeness.
3. Polish: host QA with live contract checks and captured fixtures, contract recheck, summary; device E2E and quickstart deferred to the owner.
