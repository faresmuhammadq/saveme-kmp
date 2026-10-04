# Implementation Plan: Budgets (client)

**Branch**: `012-budgets` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-budgets/spec.md`

## Summary

A new bounded context `feature.budgets` (data, domain, ui, di) lets a person set monthly budgets
(a category or all expenses, one currency, a limit) offline-first, synced through the existing
engine as `budget` records in the user scope (batch upsert/delete, archive per record) and read
from the user feed. Progress for the current Budget Period is computed on the phone from the
transactions it already holds, through a new transactions domain API `OwnSpending` that applies
the server's attribution rules (own expenses, member wallets, currency, category, period) with
waiting changes overlaid (research R-05). The server's `GET /budget-progress` figures are stored
and shown while nothing that feeds a budget has changed on the phone since they were read;
otherwise the phone's own figures are shown (R-06). Periods come from the server's profile (start
day, "applies from"), with the stored server period used while a start day change is pending
(R-08). A budgets screen and a budget editor (MVI), a Home "Budgets" row and card (through a
`BudgetSummary` hook), the existing `BudgetProgress` component, and one additive design-system
change (a disabled choice row). Room 8 → 9 adds `budget` and `budget_progress` and forgets the
user scope's feed position once. No engine change, no new dependency.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, MVI base, Ktor 3.6.0,
kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite,
Navigation 3, Kermit). **No new dependency.**

**Storage**: Room `saveme.db` v9: `budget`, `budget_progress`; the user scope's feed cursor,
read stamp and position cleared once (data-model §4).

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor `MockEngine` (contract tests from the
budgets.yaml examples), fakes in `commonTest`; Room DAO, migration 8 → 9, `OwnSpending` over an
in-memory database, sign-out wipe, graph and layering tests and Roborazzi in `androidHostTest`;
captured live-backend answers (`CapturedBudgetResponses.kt`). No emulator, Maestro or device run
(owner policy); Maestro flows written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: 50 budgets over 20,000 current-period transactions shown within 1 s from
Room (SC-008; one range query, one pass grouping by currency and category); figures update within
1 s of a transaction write (SC-002; flows, no polling); a 200-row progress page stored < 200 ms on host.

**Constraints**: P-04 (all rules), ADR-0019 attribution and rounding, server parity of figures
and periods (SC-003, SC-004), no figure jump when a waiting change is accepted (R-06), 60 s read
floors, never another account's data, no notification (FR-026).

**Scale/Scope**: 1 migration, 2 entities + DAOs, 1 API, 1 sync handler, 1 feed applier, 1 service
+ 6 pure domain parts, 2 MVI screens, Home changes, 1 design-system change, 3 hook additions
(`OwnSpending`, `RecurringLinks.ruleAuthorOf`, `BudgetSummary`) and 1 hook source
(`PendingCategoryUses`), ~70 strings EN/AR.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails. Budgets are MVP (brief C8; rollover and
per-period rows are out of MVP and not added; no savings goals); no new dependency; no
entitlement exists for budgets on the backend.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code: `Budget`, `BudgetPeriod`, `BudgetProgress…`, `startDay`; UI "Budget" / "ميزانية", "Budget period" / "فترة الميزانية" (contracts/ui-and-e2e.md Copy); string test rejects "envelope" and "plan" in budget strings (R-14) |
| P-02 API contract fidelity | PASS | Shapes from budgets.yaml v1.8 (contracts/budgets-api-client.md); writes carry exactly `BudgetWrite` / `BudgetVersionOnly`, `category_id` explicit null, stored immutable members (R-04); batch items without `wallet_id`; unknown members ignored, unknown `period_type` read-only (R-15); refusals by code + pointer; no `date` sent; shared headers and retry classes |
| P-03 Money | PASS | `amount_minor`, spent, limit, projection as `Long`; `BudgetMath` integer only with ceil division and overflow guard (R-07); digits from the currency; percentage from integers (`budgetPercent`); U+2212 for negatives in remaining |
| P-04 Offline-first | PASS | Rule 1: screens from Room (`BudgetsService` flows; progress cache in Room, R-09). Rule 2: create/limit/archive/delete queue locally (R-03, R-04). Rule 3: UUIDv7 ids on the phone. Rule 4: base version on every write; clashes to the person. Rule 5: delete is a queued tombstone; a never-sent budget is dropped locally with nothing sent |
| P-05 Time | PASS | Period dates `LocalDate`; instants UTC; profile time zone only to derive "today" (R-08); nothing zone-dependent is written |
| P-06 Localisation & RTL | PASS | ~70 strings EN/AR; Western digits in dates and amounts; `BudgetProgress` already fills from the right in RTL; screenshots EN LTR, AR RTL, 2.0 |
| P-07 Design system only | PASS | Rows use `BudgetProgress`; one additive change `CategoryChoiceItem.enabled` with catalogue and goldens first (R-13); captions with type/colour tokens |
| P-08 Accessibility | PASS | Percentage and words always written (state not by colour, P-08.3); disabled choice announced; 48 dp actions with content descriptions; 200% font screenshots |
| P-09 Security & privacy | PASS | No amounts/limits/payloads in logs (R-17, log-capture test); entities/DTOs/glimpses redact `toString`; sign-out clears both tables (FR-022) |
| P-10 Shared-first architecture | PASS | All in `commonMain`; `budgets.data` imports no domain/ui/other feature; budgets reads transactions only through `OwnSpending` (transactions domain), Home through `BudgetSummary` (auth domain), categories through the `PendingCategoryUses` union (R-01, R-05, R-10, R-11); `core.sync` unchanged; `BudgetsLayeringTest` |
| P-11 Naming | PASS | `feature.budgets`, `BudgetsService`, `BudgetSyncHandler`, `OwnSpending`; no codename |
| P-12 Entitlements & billing | N-A | No plan feature gates budgets (backend spec 015 default 18) |
| P-13 Observability | PASS | Logs per send/answer/progress walk with ids, codes, request ids, counts; unexpected errors show the request id |
| P-14 Quality gates | PASS | Spec §4 invariants each with a failing-when-broken test; contract tests for A1–A7 incl. each handled code; migration 8→9 test; screenshots for the changed component and the budget catalogue; no new dependency |
| P-15 Scope guard | PASS | No rollover, no savings goals, no FX (other currencies named only), no notifications (C16) |
| P-16 MVI presentation | PASS | Two screens with Contract/store/Route/Screen (R-12); stores call `BudgetsService` only; reducer and store tests; Home keeps one store and gains intents/state/effects with tests |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/012-budgets/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── budgets-api-client.md
│   ├── budgets-domain.md
│   └── ui-and-e2e.md
├── checklists/
│   ├── requirements.md
│   └── budgets.md       # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/
├── schemas/…AppDatabase/9.json                         # exported v9
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/database/                              # AppDatabase v9 (+2 entities, 2 DAOs), MIGRATION_8_9, DatabaseCleaner
    │   ├── feature/budgets/
    │   │   ├── data/                                   # BudgetApi, dto/ (BudgetDto, BudgetWriteRequest, BudgetVersionRequest, BudgetProgressDto),
    │   │   │                                           #   local/ (BudgetEntity/Dao, BudgetProgressEntity/Dao), BudgetPayload,
    │   │   │                                           #   BudgetRepository, BudgetProgressRepository, BudgetSyncHandler, BudgetFeedApplier
    │   │   ├── domain/                                 # BudgetsService, BudgetPeriods, BudgetMath, SpendingTotals, ProgressSelection,
    │   │   │                                           #   BudgetValidation, DayTicker, model/, BudgetCategoryUses, BudgetsHomeSummary, BudgetsSyncCoordinator
    │   │   ├── ui/                                     # list/, editor/ (Contract, ViewModel, Route, Screen each), BudgetErrorCopy, BudgetRowContent
    │   │   └── di/BudgetsModule.kt
    │   ├── feature/transactions/domain/                # OwnSpending (+ TransactionOwnSpending), RecurringLinks.ruleAuthorOf (default null)
    │   ├── feature/transactions/data/local/            # TransactionDao: range + waiting-ids query for OwnSpending
    │   ├── feature/recurring/domain/                   # RecurringLinksImpl.ruleAuthorOf
    │   ├── feature/auth/domain + ui/home/              # BudgetSummary (+ default), Home row and card
    │   ├── designsystem/component/Categories.kt        # CategoryChoiceItem.enabled; catalog budget section
    │   ├── core/di/                                    # budgetsModule registered
    │   └── navigation/                                 # BudgetsKey, BudgetEditorKey; AppNavHost wiring
    ├── commonMain/composeResources/values*/strings.xml # + Budgets block (EN, AR)
    ├── commonTest/…/feature/budgets/                   # periods, math, totals, selection, validation, reducers, stores, service, handler, applier, contract, captured
    ├── commonTest/…/feature/transactions/              # TransactionOwnSpendingTest (fake engine)
    └── androidHostTest/…                               # Migration8To9Test, BudgetDaoTest, OwnSpending Room test + perf, SignOutWipeTest, AppGraphTest,
                                                        #   BudgetsLayeringTest, screenshots
.maestro/012-budgets/                                   # written, deferred: E2E on owner's device
```

**Structure Decision**: a feature package above categories and transactions (R-01); every
dependency from lower features goes through hooks they own (R-05, R-10, R-11); `core` changes are
limited to the database (two tables, migration, cleaner) and DI registration.

## Implementation phases (input to /speckit-tasks)

1. **Setup**: entities, DAOs, `MIGRATION_8_9` (incl. user-scope reset), AppDatabase v9, exported schema, cleaner; migration test chain.
2. **Foundation**: DTOs, `BudgetApi` + contract tests; payload; repositories; `BudgetSyncHandler`; `BudgetFeedApplier`; pure domain (`BudgetPeriods`, `BudgetMath`, `SpendingTotals`, `ProgressSelection`, `BudgetValidation`, `DayTicker`); `OwnSpending` in transactions (+ DAO query, `RecurringLinks.ruleAuthorOf` and its C7 implementation); `BudgetSummary` hook; Koin module; `AppGraphTest`; design-system change + catalogue + goldens; strings.
3. **US1 Set**: service create + choices + duplicates; editor (create); budgets list (basic); Home row; navigation.
4. **US2 See**: overview with figures; list rows; Home card; period line; screenshots.
5. **US3 Offline → server**: progress walk + store; selection rule; waiting note; coordinator; captured live answers.
6. **US4 Change**: limit save, archive, delete; clash and refusal choices; error copy.
7. **US5 Periods**: pending start-day path; ticker; period lines.
8. **US6 Every phone**: applier sweep; batch; category uses; sign-out; layering.
9. **Polish**: log-capture test; performance test; live contract QA + fixtures; Maestro flows (deferred); contract recheck; README (Room v9); `summary.md` (parent).

## External dependencies (not in this repo)

Backend B8 built (`bb7e61b`); contract recorded at `saveme-backend@64ca1ea` (budgets.yaml v1.8
unchanged; the backend working tree has uncommitted InstaPay docs outside budgets/sync). Backend
contract gaps (spec §9): start day history is server-internal; archived budgets absent from the
progress list; no server progress for an unsynced budget; archive not a batch op; "applies from"
known only after the server answers.

## Complexity Tracking

Empty: no constitution FAIL.

## Assumed defaults (owner away, delegated authority)

Each is the conservative option.

### Design (spec clarifications and research)

1. The phone always computes current-period progress from its own transactions with waiting changes; server figures are shown while nothing feeding the budget changed since the read (synced-only comparison, no waiting touch, same limit, same period) (spec Q1, R-06).
2. Home "Budgets" card with up to 3 active budgets, highest percentage first; a Home "Budgets" row; no per-wallet summary (spec Q2).
3. No second active budget for the same category (or all expenses) and currency from this phone; overlaps from other phones are shown and counted (spec Q3).
4. Current period only; no history (C13) (spec Q4).
5. Periods follow the server's profile; during a pending start day change the stored server period is used, else "Progress appears after sync"; a local unsynced start day change has no effect (spec Q5, R-08).
6. Only the user scope's feed position is cleared by the migration (R-02).
7. Limit edits are whole `PUT`s (no `PATCH`); archive per record after the save; category/currency/period type sent from the stored row (R-04).
8. A confirmation waiting on the phone counts for its rule's author, else the confirming person (R-05).
9. Waiting refused and clashing transaction changes overlay the figures as the transaction list shows them (R-05).
10. Board colours of `BudgetProgress` kept (positive, pending from 80%, over above 100%); words and percentage always written; no alarm copy (spec Assumptions, FR-015).
11. Currency choices: wallets' currencies on the phone plus the profile default; default selected = profile default (FR-003).
12. Progress reads: after feed reads on sign-in, foreground, budgets screen open (60 s floor), pull (forced), and per budget on editor open (60 s floor); `INVALID_CURSOR` restarts once (R-09).
13. "On pace for" hidden when spent is 0 (R-07).
14. Archived budgets listed under "Archived" with current-period figures, never on Home (FR-016, FR-017).
15. A budget with an unknown period type is read-only with no figures (R-15).
16. Own `DayTicker` in budgets (no budgets → recurring dependency) (R-08).
17. Disabled choice rows as an additive `CategoryChoiceItem.enabled` (R-13).

### Owner review

18. Arabic words in contracts/ui-and-e2e.md Copy.

## Implementation notes

Built in one pass on `012-budgets` (host tests only; no emulator, Maestro run or device). No sync-engine change, no new dependency.

### Deviations and assumed defaults (owner away, delegated authority)

1. **`BudgetGlimpse.spentMinor` is nullable** (contract: `Long`). A budget whose period is unknown must still appear on Home with its limit and "Progress appears after sync" (FR-017); `null` carries that.
2. **Currency fallback**: when the phone knows neither the profile's default currency nor any wallet, the editor offers `EGP` (`DefaultBudgetsService.FALLBACK_CURRENCY`). The product's first market; the editor is unusable otherwise.
3. **`BudgetsService` additions** to the contract's list: `observeStartDay()` (the editor's "Monthly, starting on day n" line) and `refreshOne(id, force = false)`; `observeChoices(currency, currentCategoryId)` returns `BudgetChoices.forCurrency` so the editor can tell choices computed for the old currency from the new ones (a currency switch clears a chosen category only against the new currency's set, FR-004). `RefreshOutcome` from `auth.domain` is reused.
4. **Tags**: the empty state's button is `budgets.empty.add` (the top bar's is `budgets.add`) so a flow never meets two views with one tag. Multi-step flows use `.part1/.part2/.part3` files as in spec 010 (`US1-create-groceries`, `US3-offline-moves-then-matches`, `US4-clash`).
5. **Design system** (additive, both with goldens): `CategoryChoiceItem.enabled` (+ `CategoryRow.enabled`, announced as disabled, ink2 text), and `formatDayMonth` ("25 Sep") for the period line, whose year is obvious. Catalogue: a disabled "Already budgeted" row in `CategoriesSection`; the `budgets` section gains the caption lines a budget row carries ("On pace for", "Also spent in … (not counted)", "Includes changes waiting to sync"). `BudgetProgress` is unchanged.
6. **Layout on Home**: the "Budgets" card sits above "Add transaction" (shown only with an active budget), the "Budgets" row after "Categories".
7. **Updated stamp**: "Updated …" (offline only) is the latest progress read time, not the feed read stamp: it answers "how old are these figures".
8. **Delete that meets a newer copy** reads "Delete anyway" / "Keep the budget" (two extra strings) instead of "Keep mine" / "Use the other device's"; deleted elsewhere keeps only "Accept the deletion".
9. **The phone's synced-only figure excludes placeholders** (`version = 0` rows) and phone-only confirmations: counting a placeholder would make the server figure reappear, without it, the moment its create is accepted (the "jump back" FR-014 forbids). `OwnSpending.syncedExpenses` therefore holds only server copies.
10. **Waiting touches of another currency do not flip the figure** (FR-014 b matches period, currency and category only); the shown "other currencies" list follows the chosen source.
11. **`BudgetMath.percent` is the design system's `budgetPercent`** so the written share and the bar agree; it multiplies `spent × 100` in `Long` (safe for any real total, which is far below 9 × 10^16).
12. **Progress floors** (60 s walk, 60 s per budget) are forgotten when another account signs in. A failed walk does not start its floor; a failed per-budget read does (so a reopened editor offline asks once a minute).
13. **Sign-in refresh** is `refresh(force = false)` (T057); the user-scope re-read after the migration rides the runner's normal path (the cleared position makes it read from the start).
14. **Arabic**: the spec's table verbatim; added for owner review: "استخدم ما على الجهاز الآخر" (use the other device's; the glossary-neutral wording avoids "نسخة"), "احذفها على أي حال" (delete anyway), "احتفظ بالميزانية" (keep the budget), "حُذفت على جهاز آخر", "لم تُحفظ", "باقٍ n يوم" plurals, "يمكنك تغييره من الملف الشخصي". Western digits throughout.
15. **Maestro `US6-update-reads-budgets`** signs a fresh phone in to an account that already has budgets (the same read-from-the-start path); the update-from-v8 case itself is proven on host by `Migration8To9Test` and `BudgetFeedReadTest`.

### Existing tests changed (on purpose)

- `MigrationTest`, `Migration2To3Test` … `Migration6To7Test`, `Migration7To8Test`: the migration list gains `MIGRATION_8_9`. The user scope's position and read stamp now read `null` after the chain (`Migration6To7Test`, `Migration7To8Test` assertions updated with a comment), because 8 → 9 forgets them once (FR-020).
- `WalletStringsTest`, `CategoryStringsTest`, `TransactionStringsTest`, `RecurringStringsTest`: each scanned from its own marker to the end of `strings.xml`, so a later block was held to its banned words and prefixes; they now stop at the next `<!-- … (spec nnn) -->` marker. The spec's own copy "Shown in full on newer versions of the app" says "versions", which those lists ban for their features.
- `AppGraphTest`: three `PendingCategoryUses` sources, eleven appliers (`user/budget`), and a new test for the budgets graph. `SignOutWipeTest` also fills and checks `budget`, `budget_progress` and a queued budget change.
- `FakeTransactionDao` implements the new `observeForSpending`.
- Goldens re-recorded on purpose (15 files): `categories_*` and `budgets_*` (catalogue), and every Home golden (`screen_home_due_*`, `screen_home_pending_deletion_*`, `screen_home_verify_prompt_*`: the new "Budgets" row).

### Spec §4 invariants → tests

| Invariant | Test |
|---|---|
| Rendered only from the phone's copy | `BudgetsOverviewTest`, `BudgetsViewModelTest` (stores take lambdas; layering: ui imports no data) |
| Offline create/limit/archive/delete queue in the user scope with the base version | `BudgetsServiceCreateTest`, `BudgetsServiceChangeTest` |
| Category, currency, period immutable | `BudgetEditorReducerTest.theCurrencyOfAnExistingBudgetNeverChanges`, `BudgetSyncHandlerTest.forABudgetTheServerHasCategoryCurrencyAndPeriodTypeComeFromTheStoredRow`, `BudgetsServiceChangeTest.aLimitSaveQueuesTheStoredCategory…` |
| Phone spending = the server's rules | `TransactionOwnSpendingTest` (14), `SpendingTotalsTest`, `OwnSpendingQueryTest`; captured-answer comparison is the QA step (T095) |
| Integer arithmetic and the projection bound | `BudgetMathTest`, `BudgetValidationTest` |
| Server figure only while nothing changed; no jump back | `ProgressSelectionTest`, `BudgetsOfflineToServerTest` |
| Period equals the server's | `BudgetPeriodsTest` (every start day 1..28 over 27 months), `BudgetsPeriodOverviewTest` |
| Only declared members, explicit null, archive per record, no `wallet_id` | `BudgetApiContractTest`, `BudgetSyncHandlerTest`, `BudgetBatchTest` |
| Failures by code and pointer | `BudgetCopyTest`, `BudgetsServiceChoicesTest.aRefusalShowsItsKindByCodeAndPointer…` |
| Migration 8 → 9 keeps data, forgets only the user scope's position | `Migration8To9Test` |
| Sign-out removes budgets and progress | `SignOutWipeTest` |
| A category named by a budget is in use | `BudgetCategoryUsesTest`, `AppGraphTest` |
| No amounts or payloads in logs | `BudgetLoggingTest` |
| No percentage read from the server; state never by colour alone | `BudgetApiContractTest` (no such member), `BudgetsAccessibilityTest`, screenshots |

Also: `BudgetsLayeringTest` (P-10, FR-026), `BudgetStringsTest` (no "envelope"/"plan", no "!", keys in both languages), `BudgetsPerformanceTest` (50 budgets over 20,000 transactions under 1 s; a 200-row page stored under 200 ms, best of five), `BudgetsScreenshotTest` (21 states × EN LTR, AR RTL, 2.0).

### Housekeeping

- A flaky pre-existing test, `CategoriesServiceArchiveDeleteTest.aWaitingDeletionHidesTheCategoryAndAClashOrRefusalShowsItAgain`, failed about one run in three. Cause: the service's background upload (drain) answered 503 on another thread and rewrote the queue row the test had just set by hand. Fixed in the test (the upload parks at the server), 300 in-process repetitions and 15 isolated Gradle reruns green. `WalletsServiceArchiveDeleteTest` / `WalletsServiceSaveTest` use the same 503 pattern but need the answer; not changed.
- Contract recheck (T097): `budgets.yaml`, the budget parts of `sync.yaml` and `identity.yaml` are byte-identical to `saveme-backend@64ca1ea`; the working tree's uncommitted changes touch redeem-code errors and billing docs only.
