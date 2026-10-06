---
description: "Task list for 014-reports"
---

# Tasks: Reports (client)

**Input**: Design documents from `specs/014-reports/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (api-client.md, domain-apis.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `bee8e65` (specs 004–013: engine, entitlements, wallets, categories, transactions, feeds + batch + heads, recurring, budgets, sharing; Room v10)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per component and screen; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/014-reports/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Host QA (Phase 10) replays the calls against the live local backend with `curl` and turns the real answers into fixtures, as specs 007–013 did.

**Organization**: Phase 2 builds the wire client, the pure domain (calculator, shares, periods, ranges, covered wallets), the three hooks (`ReportLedger`, `ScheduledMoney`, `observeReportLabels`), the in-memory holders and the chart components. US1 (cross-wallet summary and categories) is the MVP; US2 makes it offline-exact with parity proof; US3 wallet reports; US4 trend; US5 periods and ranges; US6 currencies; US7 scheduled money.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US7 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); reports feature `cm/feature/reports/…` (`fr/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); reports tests `ct/feature/reports/…` (`rt/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Transactions `cm/feature/transactions/…` (`ft/…`), recurring `cm/feature/recurring/…` (`fc/…`), categories `cm/feature/categories/…` (`fk/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: reports ui → reports domain → reports data; `fr/data` imports only `core`; reports domain reaches transactions, recurring, categories, wallets, budgets, account and auth only through their `domain`; no other feature imports `feature.reports`), naming (P-11).
- Glossary (P-01): Report, Report Range, Budget Period, Transfer, Wallet; no copy says "account" for a wallet or "advanced"; nothing user-facing says "version", "scope", "cursor" or "key".
- Money (P-03): `Long` minor units only, checked addition, integer share arithmetic; digits from the currency; U+2212 for negatives; liability balances as amounts owed in a wallet report.
- Dates (P-05): `LocalDate` for ranges, periods and transaction dates; "today" from the profile's zone only for derivation, never written.
- Never log amounts, figures, shares, category or wallet names, or payloads (P-09.2); ids, kinds, outcomes, codes, request ids, counts and member names (probe) only.
- **No schema change**: no entity, column or migration; `AppDatabase` stays v10; `10.json` unchanged.
- **MVI screen recipe (P-16)** as specs 001–013: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas, `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin**: hooks resolve `SyncEngine` lazily (spec 006 lesson); `reportsModule` is listed after `transactionsModule`, `recurringModule`, `categoriesModule`, `walletsModule`, `budgetsModule` and `sharingModule` so its rebindings win; `AppGraphTest` stays green.
- **Merge hotspots**: `TransactionDao` (in `TransactionEntity.kt`), `OccurrenceEntity.kt`, `CategoriesService.kt`, `CatalogSections.kt`, Home contract/VM/screen, wallet-detail contract/VM/screen, `AppModules.kt`, `Routes.kt`, `AppNavHost.kt`: additive only. New strings in one append-only block `<!-- Reports (spec 014) -->` at the end of each `strings.xml`.
- Every interactive element gets the `testTag` of contracts/ui-and-e2e.md.

---

## Phase 1: Setup

**Purpose**: confirm prerequisites, create the package, strings, navigation keys.

- [X] T001 Confirm on `014-reports` (from `main` `bee8e65`): `AppDatabase` version 10 and `shared/schemas/…/10.json`; `SyncRunner.refresh(ReadTarget, force)` with `Everything`/`User`/`Wallet`; `SyncEngine.observeAll`; `TransactionPostings` and `TransactionOwnSpending` patterns in `ft/domain/`; `BalanceEffect` in `ft/data/`; `OccurrenceDao` in `fc/data/local/OccurrenceEntity.kt`; `CategoriesService.observeDisplays` and `CategoryResolver`; `WalletsService.observeList()` with `WalletView.shown` and `WalletList.updatedAt`; `BudgetPeriods.calendar` and `DayTicker` in `cm/feature/budgets/domain/`; `ProfileView` (time zone, default currency, start day, "applies from"); `UserDataCleaner`; `AppConfig.environment`; `formatDayMonth`; `.maestro/common/api.js` with the `+partner` seed. If any is missing, stop and report
- [X] T002 Create `fr/di/ReportsModule.kt` with an empty `val reportsModule = module { }` and append it at the end of `appFeatureModules` in `cm/AppModules.kt` (hotspot: one list element)
- [X] T003 [P] Add the string block `<!-- Reports (spec 014) -->` with every key of contracts/ui-and-e2e.md "Copy" (about 45) to the end of `values/strings.xml` and `values-ar/strings.xml` (glossary words of spec §2; Western digits; no exclamation marks; research R-14)
- [X] T004 [P] Navigation keys `ReportsKey(walletId: String? = null)` and `ReportCategoriesKey(walletId: String?, from: String, to: String, currency: String, includeScheduled: Boolean, kind: String)` in `cm/navigation/Routes.kt` (redacted `toString` without dates)

---

## Phase 2: Foundational (blocking)

**Purpose**: wire client, pure domain, hooks, in-memory holders, chart components.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T005 [P] Contract tests `rt/data/ReportsApiContractTest.kt` (MockEngine, reports.yaml examples): the six paths with their query members (`date` | `from`+`to`, `months`, `include_planned` `true`/`false`, `currency`), shared headers; decoding `currentPeriod`, `creditCard`, `withPlanned`, `threeMonths`, `egp` (summary and by-category), `twoPeriodsPlanned`; `planned: null` vs object; `meta: {}` vs `excluded_wallets`; unknown member ignored; no share member in any DTO; 400 `INVALID_QUERY_PARAMETER`, 401, 404 `NOT_FOUND`, 422 `INVALID_ID`, 426, 500 mapped by `code`; the lookup `trend(months, date)` sends neither `currency` nor `include_planned` (contracts/api-client.md)
- [X] T006 [P] Calculator tests `rt/domain/ReportCalculatorTest.kt` (table-driven): wallet scope — income, expense, transfer out/in, unknown kind ignored, deleted ignored, date outside range ignored; cross-wallet — covered↔covered transfer nowhere, covered→uncovered out, uncovered→covered in (incl. deleted counter wallet); balances from openings + `before` + range effect; balance identity holds over 500 random ledgers; liability negative balances unclamped; checked addition → `TooLarge` (FR-003–FR-005, ADR-0022)
- [X] T007 [P] Breakdown tests `rt/domain/CategoryBreakdownTest.kt`: platform grouped by `system_key`, others by id; two platform categories never merged by name; zero items dropped (kept when planned is non-zero and the switch is on); sort value desc then label asc (byte order as the backend's `strcmp`: e.g. `"0192…"` UUID labels before lowercase system keys, `"Zeta" < "groceries"`); totals = Σ items; transfers in neither group (FR-006)
- [X] T008 [P] Share tests `rt/domain/SharesTest.kt`: half up (350,000 / 550,000 = 64; 120,000 / 550,000 = 22; 80,000 / 550,000 = 15), exactly .5 rounds up, non-zero under 0.5% → `UnderOne`, zero total → `None`, value = total → 100, no overflow at 2^53 − 1 (FR-007)
- [X] T009 [P] Period tests `rt/domain/ReportPeriodsTest.kt` (table): start days 1, 15, 25, 28 across 28–31-day months via `BudgetPeriods.calendar`; `appliesFrom` null → derivable everywhere; past F → derivable for dates ≥ F only, the period that would start before F `Unknown`; pending F (after today) → today's period `Unknown` unless learned; learned periods used when they contain the date; no profile → day 1; previous/next walk across derived and learned periods; forward limit = period containing today + 35; trend of n periods ending with the period containing `to`, with `Unknown` slots (FR-008, FR-010, FR-011, SC-004)
- [X] T010 [P] Range tests `rt/domain/RangeRulesTest.kt`: `from ≤ to`; 366 days inclusive accepted (2026-01-01..2027-01-01), 367 refused; 1900-01-01 and 2999-12-31 bounds; problem kinds `Order`, `TooLong`, `OutOfBounds` (FR-009)
- [X] T011 [P] Covered-wallet tests `rt/domain/CoveredWalletsTest.kt`: cross-wallet covers live wallets on the phone in the currency (archived included, any role, phone-only waiting create included, waiting delete excluded as the list hides it); excluded sorted by currency then name; no wallet in the currency → empty covered, all excluded; currencies list = wallets' currencies ∪ profile default, sorted; default currency = profile's, else EGP (FR-004, FR-013, FR-014)
- [X] T012 [P] `ReportLedger` tests `ct/feature/transactions/domain/TransactionReportLedgerTest.kt` (fake engine + fake rows): lines touching the wallets in range (source or destination); queued create/edit/delete overlaid (edit moving the date in or out of range and before `from`); refused and clashing changes overlaid; phone-only confirmation is a line; `before` per wallet = synced effect before `from` corrected by every waiting change whose stored or shown date is before `from`; `synced*` ignore the queue and phone-only rows; `waitingTouches` lists both sides (research R-03)
- [X] T013 [P] Room test `ht/feature/transactions/data/ReportLedgerQueryTest.kt`: `observeForReport` returns rows touching the wallet set dated in range, plus waiting ids, plus pending confirmations, one account only; `observeBalancesBefore` sums the signed effect per wallet for source and destination legs, ignores deleted rows and unknown kinds, excludes rows dated on or after `before`
- [X] T014 [P] `ScheduledMoney` tests `ct/feature/recurring/domain/ScheduledMoneyTest.kt` + Room `ht/feature/recurring/data/ScheduledQueryTest.kt`: status `scheduled` only; waiting confirm or skip removes it; waiting edit's values apply; wallet or counter wallet in the set; dated in range (research R-05)
- [X] T015 [P] Labels tests `ct/feature/categories/domain/ReportLabelsTest.kt`: `keyOf` = `system_key` for a platform id, id otherwise; `display` resolves own → platform in Arabic and English → references of every covered wallet → unavailable; a miss asks for references of each covered wallet under the existing floor (research R-08)
- [X] T016 [P] Holder tests `rt/domain/ReportChoicesStoreTest.kt`, `rt/domain/LearnedPeriodsTest.kt`: defaults (current period, 6, default currency, off); per account and scope; cleared by the cleaner and on another account; learned periods dropped when the profile's start day or "applies from" changes (FR-012, data-model §4)

### Implementation

- [X] T017 [P] DTOs `fr/data/dto/ReportDtos.kt`: `ReportSummaryDto`, `PlannedSummaryDto`, `ReportByCategoryDto`, `CategoryGroupDto`, `CategoryItemDto`, `ReportTrendDto`, `TrendBudgetPeriodDto`, `ChartPointDto`, `WalletReportMetaDto`, `CrossWalletReportMetaDto`, `ExcludedWalletDto`, `ReportMoneyDto` (`amount_minor`, `currency`; reports' own copy, since `MoneyDto` lives in budgets' `data`, which reports may not import); no share member; redacted `toString`
- [X] T018 `fr/data/ReportsApi.kt`: `trend(months, date, walletId?, includePlanned?, currency?)`, `summary(...)`, `byCategory(...)` for wallet and cross-wallet paths on the shared client, enforcing the query rules of contracts/api-client.md (depends on T017; makes T005 pass)
- [X] T019 [P] Pure domain in `fr/domain/`: `ReportCalculator.kt`, `Shares.kt`, `ReportPeriods.kt`, `RangeRules.kt`, `CoveredWallets.kt`, models in `fr/domain/model/` (data-model §2, redacted `toString`) (makes T006–T011 pass)
- [X] T020 `ReportLedger`, `LedgerLine`, `LedgerSnapshot`, `LedgerTouch`, `NoReportLedger` in `ft/domain/ReportLedger.kt`; `TransactionDao.observeForReport` and `observeBalancesBefore` in `ft/data/local/TransactionEntity.kt`; `TransactionReportLedger` in `ft/domain/` (one snapshot of rows + `engine.observeAll("transaction")`, rules of research R-03); bind in `ft/di/TransactionsModule.kt` (makes T012–T013 pass)
- [X] T021 `ScheduledMoney` (with `observeWaiting`), `ScheduledLine`, `NoScheduledMoney` in `fc/domain/ScheduledMoney.kt`; `OccurrenceDao.observeScheduledFor` in `fc/data/local/OccurrenceEntity.kt`; implementation with the occurrence queue overlay; bind in `fc/di/RecurringModule.kt` (makes T014 pass)
- [X] T022 `CategoriesService.observeReportLabels(walletIds)` with a default body, `ReportLabels` in `fk/domain/model/`, implementation in `DefaultCategoriesService` over the existing resolver with the union of the covered wallets' references (makes T015 pass)
- [X] T023 [P] `fr/domain/ReportChoicesStore.kt`, `fr/domain/LearnedPeriods.kt`, `fr/domain/ReportsCleaner.kt` (a `UserDataCleaner`) (makes T016 pass)
- [X] T024 [P] Design system `cm/designsystem/component/Charts.kt`: `ShareBarRow`, `TrendBarChart` (Canvas; grouped income/expense bars, outlined scheduled segment, zero baseline, oldest at the start edge, one semantics node with a summary), `TrendTable`, `ReportFigureRow`, `SummaryCard`, `ExcludedWalletsNotice` (research R-10); catalogue section `cm/designsystem/catalog/ReportsCatalog.kt` registered in `CatalogSections.kt`; goldens EN LTR, AR RTL, 2.0 in `ht/designsystem/ComponentScreenshotTest.kt` (zero, single, twelve periods, negative net, scheduled, long Arabic labels)
- [X] T025 Koin `fr/di/ReportsModule.kt`: `ReportsApi`, holders, cleaner bound as `UserDataCleaner`, `NoParityProbe` placeholder; `ht/core/di/AppGraphTest.kt` gains `theReportsModuleResolvesItsHooks` (`ReportLedger` is `TransactionReportLedger`, `ScheduledMoney` resolves, no new `SyncHandler` or `FeedApplier`) (depends on T018, T020–T023)
- [X] T026 [P] Layering test `ht/feature/reports/ReportsLayeringTest.kt` modelled on `ht/feature/budgets/ui/BudgetsLayeringTest.kt`: `fr/ui` imports no `data`; `fr/data` imports only `core`; other features only through `…domain`; no feature other than reports imports `feature/reports`; nothing in `feature/reports` references Room, a notification API or `reports.advanced` (P-10, FR-024)

**Checkpoint**: wire client, calculator, hooks and charts ready; all Phase 2 tests green; `10.json` unchanged.

---

## Phase 3: User Story 1 - See where my money went this period (Priority: P1) 🎯 MVP

**Goal**: Home → Reports shows the current period's cross-wallet summary and top expense categories with shares.

**Independent Test**: three EGP wallets, salary 10,000, groceries 3,500, transport 1,200, own 800 → income 10,000, expenses 5,500, net 4,500; 64% / 22% / 15%.

### Tests for User Story 1

- [X] T027 [P] [US1] Service tests `rt/domain/ReportsServiceSummaryTest.kt`: `observe(AllWallets)` combines wallets, ledger, labels, profile and choices into `Ready(Report)` for the current period; `NoWallets` with none; figures and top items match the independent test; labels resolved; recomputes on a new row (FR-001–FR-007, FR-015, FR-023)
- [X] T028 [P] [US1] Reports reducer + ViewModel tests `rt/ui/reports/ReportsReducerTest.kt`, `ReportsViewModelTest.kt`: loading → ready, summary lines, top 5 + `moreCategories`, "No spending in this period", empty wallets → `OpenWallets` effect, See all → `OpenCategories` with the scope's args
- [X] T029 [P] [US1] Categories reducer + ViewModel tests `rt/ui/categories/ReportCategoriesReducerTest.kt`, `ReportCategoriesViewModelTest.kt`: Expenses default, Income switch, rows with share and label, total

### Implementation for User Story 1

- [X] T030 [US1] `fr/domain/ReportsService.kt` (`DefaultReportsService`): `observe`, `observeChoices`, `observeLabels`, `observeCurrencies` (cross-wallet, current period, actual figures) over the hooks, `DayTicker` and `ProfileView` (contracts/domain-apis.md) (makes T027 pass)
- [X] T031 [US1] Reports screen `fr/ui/reports/` (`ReportsContract.kt`, `ReportsViewModel.kt`, `ReportsRoute.kt`, `ReportsScreen.kt`): scope header, range label, `SummaryCard`, top categories with `ShareBarRow`, See all, empty states, tags of contracts/ui-and-e2e.md; section order of FR-022 (makes T028 pass)
- [X] T032 [US1] Spending by category screen `fr/ui/categories/` (Contract, ViewModel, Route, Screen) with `SegmentedControl` Expenses/Income and `ShareBarRow` rows (makes T029 pass)
- [X] T033 [US1] Wire service and both ViewModels in `fr/di/ReportsModule.kt`; `AppNavHost` entries for `ReportsKey` and `ReportCategoriesKey` in `cm/navigation/AppNavHost.kt`
- [X] T034 [US1] Home "Reports" row: `HomeIntent.ReportsClicked`, `HomeEffect.NavigateReports` in `cm/feature/auth/ui/home/HomeContract.kt`, `HomeViewModel.kt`, `HomeScreen.kt` (tag `home.reports`, after "Budgets"), nav wiring; Home reducer/VM tests updated in `ct/feature/auth/ui/home/`; Home goldens re-recorded on purpose (FR-021, SC-001)
- [X] T035 [P] [US1] Screenshots `ht/feature/reports/ui/ReportsScreenshotTest.kt`: Reports ready (EGP, three categories), no spending, no wallets; Spending by category (expenses, income) — EN LTR, AR RTL, 2.0 (SC-006)
- [X] T036 [P] [US1] Maestro flows `.maestro/014-reports/US1-summary-and-shares.yaml`, `US1-see-all.yaml`, `common/open-reports.yaml`, `common/seed-reports.yaml` (`# deferred: E2E on owner's device`); `api.js` gains `createTransfer` (if absent), `seedReportsData`
- [ ] T037 [US1] Run the US1 flows — **deferred: E2E on owner's device**

**Checkpoint**: the brief's first checkpoint holds on host (summary and shares from seeded rows).

---

## Phase 4: User Story 2 - The report works offline and agrees with the server (Priority: P1)

**Goal**: offline changes counted at once with a note; freshness captions; parity proven.

**Independent Test**: offline 200 EGP Groceries → counted, "Includes changes waiting to sync", "You're offline · Updated …"; after sync the note goes, figures unchanged and equal to the server.

### Tests for User Story 2

- [X] T038 [P] [US2] Overlay tests `rt/domain/ReportsServiceWaitingTest.kt`: a waiting create/edit/delete in range changes the figures at once; `includesWaiting` when a transaction touch hits a covered wallet in or before the range, when a covered wallet itself has a waiting change (`WalletView.syncState` waiting), and, with the switch on, when an occurrence change waits in range; accepting the change (row stored, queue cleared) leaves every figure equal (no jump) (FR-001, FR-002, SC-003)
- [X] T039 [P] [US2] Freshness tests `rt/domain/ReportsServiceRefreshTest.kt`: open → `SyncRunner.refresh(Everything, false)` (wallet scope → `Wallet(id)` then `User`); pull → forced; `Offline`/`Failed` map to captions with the oldest covered read stamp; `Done` clears them (FR-020)
- [X] T040 [P] [US2] Probe tests `rt/domain/ReportParityProbeTest.kt`: dev binding compares synced-only figures with mapped server answers member by member (summary, items and their order, trend points, planned; excluded wallets as a set of `(wallet_id, currency)`, since the screen sorts them by currency and name); logs `ok` or the differing member names; skipped with any waiting change in the covered wallets, on 404/422 and offline; release binding never calls the API (FR-028)
- [X] T041 [P] [US2] Log-capture test `rt/domain/ReportsLoggingTest.kt`: refresh, lookup and probe (ok and mismatch) log no amount, share, category or wallet name (FR-025)

### Implementation for User Story 2

- [X] T042 [US2] `ReportsService.refresh` and freshness in `fr/domain/ReportsService.kt` (makes T039 pass); waiting note from `LedgerSnapshot.waitingTouches` and scheduled overlay (makes T038 pass)
- [X] T043 [US2] `fr/domain/ReportParityProbe.kt` (`DevReportParityProbe`, `NoParityProbe`) with DTO → domain mapping in `fr/domain/ReportMapping.kt`; bound by `AppConfig.environment == Dev` in `fr/di/ReportsModule.kt`; invoked after a successful refresh (makes T040–T041 pass)
- [X] T044 [US2] Reports screen: freshness line, waiting note, pull to refresh (`reports.freshness`, `reports.waiting`); reducer/VM tests extended
- [X] T045 [P] [US2] Screenshots: offline with "Updated", failed refresh, waiting note — EN, AR, 2.0
- [X] T046 [P] [US2] Maestro flows `US2-offline-waiting.yaml`, `US2-after-sync-equal.yaml` (deferred header)
- [ ] T047 [US2] Run the US2 flows — **deferred: E2E on owner's device**

**Checkpoint**: offline exactness and the parity probe green on host.

---

## Phase 5: User Story 3 - One wallet's report (Priority: P1)

**Goal**: wallet detail → Report; every member's transactions; liability balances owed.

**Independent Test**: shared wallet, own 1,000 + partner 500 expenses → 1,500; same on the partner's phone.

### Tests for User Story 3

- [X] T048 [P] [US3] Service tests `rt/domain/ReportsServiceWalletTest.kt`: `observe(Wallet(id))` counts every member's rows; transfers by direction; wallet currency; no currencies list or excluded; liability flag; archived wallet reported; `WalletGone` when `observeWallet(id)` emits null (FR-003, FR-017)
- [X] T049 [P] [US3] Wallet detail reducer/VM tests in `ct/feature/wallets/ui/detail/`: `ReportClicked` → `OpenReport(walletId)` for owner, editor and viewer

### Implementation for User Story 3

- [X] T050 [US3] Wallet scope in `ReportsService` (makes T048 pass); Reports screen: wallet title, no currency choice, "Owed …" rendering via `SummaryCard`, `Close` effect on `WalletGone`
- [X] T051 [US3] Wallet detail "Report" action: `WalletDetailIntent.ReportClicked`, `WalletDetailEffect.OpenReport` in `cm/feature/wallets/ui/detail/` (tag `wallet.report`), nav wiring in `AppNavHost` (makes T049 pass); wallet-detail goldens re-recorded on purpose (FR-021)
- [X] T052 [P] [US3] Screenshots: wallet report (cash), credit-card report with owed balances — EN, AR, 2.0
- [X] T053 [P] [US3] Maestro flows `US3-wallet-report.yaml` (with `+partner`), `US3-liability.yaml` (deferred header)
- [ ] T054 [US3] Run the US3 flows — **deferred: E2E on owner's device**

**Checkpoint**: wallet reports on host.

---

## Phase 6: User Story 4 - Compare with earlier periods (Priority: P2)

**Goal**: trend of 3/6/12 Budget Periods with chart and table.

**Independent Test**: Aug 0/0, Sep 10,000/12,000 (−2,000), Oct 10,000/3,500 (6,500), oldest first.

### Tests for User Story 4

- [X] T055 [P] [US4] Trend tests `rt/domain/ReportsServiceTrendTest.kt`: n periods ending with the period containing `to`; zero-filled; transfers excluded; net = income − expenses; length change re-emits; `Unknown` periods carry no figures (FR-010)
- [X] T056 [P] [US4] Reducer/VM tests: `TrendLengthPicked`, chart points and table rows; semantics summary string with the period count
- [X] T057 [P] [US4] Accessibility test `ht/feature/reports/ui/ReportsAccessibilityTest.kt`: the chart is one node with the summary description; the table rows are readable; every bar has a written value; legend words present; 48 dp controls; no clipped text at 2× (FR-018, P-08)

### Implementation for User Story 4

- [X] T058 [US4] Trend in `ReportsService` and the Reports screen (`SegmentedControl` 3/6/12, `TrendBarChart`, `TrendTable`, tags `reports.trend.*`) (makes T055–T057 pass)
- [X] T059 [P] [US4] Screenshots: trend 3, 6 and 12 periods, a negative net, AR mirrored — EN, AR, 2.0
- [X] T060 [P] [US4] Maestro flow `US4-trend.yaml` (deferred header)
- [ ] T061 [US4] Run the US4 flow — **deferred: E2E on owner's device**

**Checkpoint**: trend green on host.

---

## Phase 7: User Story 5 - Choose the period or a custom range (Priority: P2)

**Goal**: previous/next, custom range, start days, server-learned periods, midnight.

**Independent Test**: start day 25 on 2 Oct → 25 Sep – 24 Oct; previous → 25 Aug – 24 Sep; 366 days accepted, 367 refused.

### Tests for User Story 5

- [X] T062 [P] [US5] Lookup tests `rt/domain/PeriodLookupTest.kt`: an `Unknown` period online starts one `ReportsApi.trend(months, date)` (months = unknown count, max 12, several reads beyond 12 ending the day before the earliest learned start); answers stored in `LearnedPeriods` for the account; offline/failure/400 leave `Unknown`; 400 logged as a client defect; a lookup answer for a signed-out account is dropped (FR-011, FR-019, FR-027)
- [X] T063 [P] [US5] Range tests `rt/domain/ReportsServiceRangeTest.kt`: previous/next across derived and learned periods; next disabled at the forward limit; custom range validation; current-period reset; midnight via a test `DayTicker` updates today, the period, the limit and "known up to" (FR-008, FR-009, US5-S5)
- [X] T064 [P] [US5] Reducer/VM tests: range intents, custom picker open/dismiss/problem, "Shown when you're online" sections

### Implementation for User Story 5

- [X] T065 [US5] Period lookup in `fr/domain/ReportsService.kt` + `LearnedPeriods` wiring; profile change drops learned periods (makes T062 pass)
- [X] T066 [US5] Range bar on the Reports screen (Previous/Next with content descriptions, dates label via `formatDayMonth` + year when not this year, "Custom range" with two `AppDatePickerDialog` picks, "Current period"), `reports.trend.unknown` and summary/category "Shown when you're online" states (makes T063–T064 pass)
- [X] T067 [P] [US5] Screenshots: custom range problem, unknown period offline — EN, AR, 2.0
- [X] T068 [P] [US5] Maestro flow `US5-previous-next-custom.yaml` (deferred header)
- [ ] T069 [US5] Run the US5 flow — **deferred: E2E on owner's device**

**Checkpoint**: periods equal the server's in the table; nothing guessed.

---

## Phase 8: User Story 6 - Several currencies (Priority: P2)

**Goal**: one currency per report, choice, excluded notice, switch.

**Independent Test**: three EGP + one USD wallet → EGP report says "Not included: Savings (USD)"; "See USD report" switches.

### Tests for User Story 6

- [X] T070 [P] [US6] Service tests `rt/domain/ReportsServiceCurrencyTest.kt`: default currency; `setCurrency` re-emits with covered/excluded swapped; a currency with no wallet → zeros and all excluded; no sum across currencies (FR-013, FR-014)
- [X] T071 [P] [US6] Reducer/VM tests: `CurrencyPicked`, `SeeCurrencyClicked`, notice content and order

### Implementation for User Story 6

- [X] T072 [US6] Currency choice (`SelectionSheet`) and `ExcludedWalletsNotice` with "See <code> report" on the Reports screen (tags `reports.currency`, `reports.excluded*`) (makes T070–T071 pass)
- [X] T073 [P] [US6] Screenshots: EGP with a USD exclusion, USD report naming EGP wallets — EN, AR, 2.0
- [X] T074 [P] [US6] Maestro flow `US6-currencies.yaml` (deferred header)
- [ ] T075 [US6] Run the US6 flow — **deferred: E2E on owner's device**

**Checkpoint**: currencies green on host.

---

## Phase 9: User Story 7 - Scheduled money (Priority: P3)

**Goal**: "Include scheduled" adds separate planned figures.

**Independent Test**: rent 5,000 scheduled on 28 Oct, nothing recorded → expenses 0, "Scheduled: 5,000 EGP", Rent planned 5,000, outlined October bar; balance unchanged.

### Tests for User Story 7

- [X] T076 [P] [US7] Planned tests `rt/domain/PlannedFiguresTest.kt`: summary planned income/expenses/transfers with the covered-transfer rule; other currency left out; per-category planned (income/expense only); per-period planned; zeros (not absent) when on and nothing scheduled; never in actual figures, shares or balances; waiting confirm/skip removes; "known up to" = today + 35 (FR-016)
- [X] T077 [P] [US7] Reducer/VM tests: `IncludeScheduledToggled`, planned lines and captions, switch survives navigation to the categories screen and back (FR-012)

### Implementation for User Story 7

- [X] T078 [US7] Fold `ScheduledMoney` into `ReportCalculator` and `ReportsService` (makes T076 pass); switch, "Scheduled: …" lines, outlined trend segments and "Scheduled money is known up to …" on both screens (makes T077 pass)
- [X] T079 [P] [US7] Screenshots: scheduled on (summary, category rows, trend) — EN, AR, 2.0
- [X] T080 [P] [US7] Maestro flow `US7-scheduled.yaml` (deferred header)
- [ ] T081 [US7] Run the US7 flow — **deferred: E2E on owner's device**

**Checkpoint**: all seven stories green on host.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T082 [P] Arabic copy review of the reports block (تقرير، فترة الميزانية، تحويلات واردة/صادرة، تضمين المجدول; Western digits; amounts LTR in RTL rows); list words for owner review in `summary.md`
- [X] T083 [P] String test `rt/ui/ReportStringsTest.kt`: every key in both languages; no "!", no "account", no "advanced"; scans from the `<!-- Reports (spec 014) -->` marker to the next spec marker (FR-026, R-14)
- [X] T084 [P] Performance test `ht/feature/reports/ReportsPerformanceTest.kt`: in-memory Room with 20 wallets and 20,000 transactions in the range plus 50,000 before it → a cross-wallet report with a 12-period trend emitted under 1 s, best of five (SC-005)
- [X] T085 [P] Sign-out test: `ht/feature/account/SignOutWipeTest.kt` also asserts the reports cleaner empties choices and learned periods (FR-029)
- [X] T086 Trace every spec §4 invariant to its passing test (list in plan.md "Implementation notes"; parent copies into `summary.md`) (P-14.2)
- [X] T087 [P] `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS compiles; no JVM-only API such as `String.format` in common code)
- [X] T088 `./gradlew build allTests` and `:shared:verifyRoborazziAndroidHostTest` green; `10.json` unchanged and no `11.json`
- [X] T089 Host QA without a device (quickstart "Host QA"): map every acceptance scenario, FR and edge case to a passing host test in `specs/014-reports/qa-report.md`; seed the quickstart data set with `curl` against the live local backend (owner, `+partner`, a start-day-25 account) and replay all six reports (cross-wallet EGP and USD, each wallet; `include_planned` true/false; explicit range; `months` 1, 6, 12; refusals: 367 days, `months=13`, `currency=egp`, `date` with `from`, another person's wallet 404, malformed id 422); check gap 2 (scheduled transfer into the shared wallet from the partner's wallet) — done: see qa-report.md (live QA 2026-10-06, 511 answers, gap 2 settled)
- [X] T090 Turn the captured feeds and reports into `ht/feature/reports/CapturedReportResponses.kt` (host source set, beside the Room-backed test) and drive them through the real `HttpSyncApi`, appliers, `TransactionReportLedger`, `ScheduledMoney`, labels, `ReportPeriods` and `ReportCalculator` in `ht/feature/reports/ReportsCapturedParityTest.kt`, asserting equality with every captured report member (SC-002, SC-004) (depends on T089) — done: `CapturedReportsResponses.kt`, `CapturedReportsA/B/D/Refusals.kt` and `ReportsCapturedParityTest` in `ht/feature/reports/`
- [X] T091 Static review of the Maestro flows (selectors vs test tags, `network-off`/`network-on` parts, `# deferred` header) and fix what it finds
- [X] T092 Rerun `speckit-contract-check`; confirm `reports.yaml` unchanged since `b63e917`; record drift as FRs or follow-ups in spec §5
- [X] T093 Update README (reports: computed on the phone, no table; Room stays v10) and the C13 row of `specs/briefs/README.md`
- [ ] T094 Run quickstart.md manual checks on the dev build — **deferred: E2E on owner's device**
- [ ] T095 Run the full `/speckit-e2e 014-reports` and write `specs/014-reports/e2e-report.md` incl. the brief's checkpoint (SC-007) — **deferred: E2E on owner's device**
- [X] T096 Write specs/014-reports/summary.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 (blocks all stories) → US1 (MVP) → US2 → US3; US4, US5, US6 after US1 (screen and service exist); US7 after US4 (trend segments) → Polish.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T005–T016 all [P]; T017, T019, T023, T024, T026 in parallel; T020, T021, T022 touch different features and can run in parallel.
- US5 and US6 can run beside US4 once US1 exists (different domain files; coordinate edits to `ReportsService` and the Reports screen).
- Within each story the test tasks are [P]; screenshot and flow tasks run beside the next story's tests.

## Parallel Example: Phase 2

```text
T005 contract | T006 calculator | T007 breakdown | T008 shares | T009 periods | T010 ranges | T011 covered | T012 ledger | T014 scheduled | T015 labels | T016 holders
then: T017 DTOs → T018 API  |  T019 pure domain  |  T020 ledger  |  T021 scheduled  |  T022 labels  |  T023 holders  |  T024 charts
then: T025 Koin + AppGraphTest  →  T026 layering
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: Home → Reports shows the current period's cross-wallet summary and shares from the phone's rows; host tests green.

### Incremental Delivery

1. US1 → US2 (offline exactness and parity; the brief's second checkpoint) → US3 (wallet).
2. US4 trend, US5 periods, US6 currencies, US7 scheduled.
3. Polish: host QA with live contract checks and captured fixtures, contract recheck, summary; device E2E and quickstart deferred to the owner.
