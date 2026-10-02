# Implementation Plan: Transactions (client)

**Branch**: `008-transactions` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-transactions/spec.md`

## Summary

A new `feature.transactions` context keeps every transaction of the wallets on the phone in Room
(schema v5, table `transactions`) and renders two MVI screens from it: a wallet's transactions
(search, filters, day groups with net totals, balance, add) and the transaction editor (create,
edit and detail with clash and refusal choices, delete). Every create, edit and deletion goes
through the spec 004 sync engine via a `TransactionSyncHandler` (record type `transaction`, scope
`SyncScope(Wallet, sourceWalletId)`, `canRestore = true`): PUT for create/save, DELETE with a JSON
body, and PATCH `{deleted_at: null, version}` followed by a PUT for "Restore with my change". Lists
are read whole per wallet when the screen opens (wave 1). The feature fills the hooks C4 and C5
left: `TransactionPostings` (balances include unsent changes, plus a carry until the wallet is
re-read so the balance never jumps), the currency lock in the wallet editor,
`TransactionCategoryUses` and a `WalletDeletionParticipant`; and it places C5's category choice
and display on real screens (proving C5 US4). `designsystem` gains a date picker, a date field, an
amount field and an extended `TransactionRow`.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only: Compose Multiplatform 1.12.1 + Material 3 (incl. `DatePicker`), MVI base (`MviViewModel`), Navigation 3, Ktor 3.6.0, kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite, WorkManager (spec 004, Android), Kermit. No new dependency (research R-28).

**Storage**: Room `saveme.db` v5: new `transactions` table; `MIGRATION_4_5` in `core/database/Migrations.kt`; exported `shared/schemas/…AppDatabase/5.json`. Queue rows in the existing `pending_change` (record type `transaction`, scope `wallet:<source id>`); per-wallet read stamps in `sync_scope` (`transactions:<walletId>`); last-used wallet in `KeyValueStore` (`transactions.lastWallet`).

**Testing**: kotlin-test, coroutines-test, Turbine; Ktor `MockEngine` contract tests; fakes for the API, the engine's DAO/scheduler, `WalletsService` and `CategoriesService`; Room DAO + migration 4→5 tests in `androidHostTest` (Robolectric); `AppGraphTest` (real Koin graph); Roborazzi screenshots (EN, AR RTL, 2× font); host QA with curl against the live local backend, captured responses as fixtures (research R-25); Maestro flows written, **runs deferred to the owner's device**.

**Target Platform**: Android minSdk 24 / targetSdk 37 (live API); iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: a wallet's transactions screen with 2,000 rows from Room in < 1 s, search/filter change < 300 ms (SC-005); a save is one local transaction + one queue write (< 50 ms) and the list shows it on the next emission; a refresh is ⌈n/200⌉ GETs per wallet, at most once a minute unless pulled; one wallet re-read per burst of synced changes (1 s debounce).

**Constraints**:
- P-04 (every rule): local-first writes, base version on every send, clashes to the person, deletion as a queued tombstone, restore only from a clash.
- P-03: positive `Long` minor units, currency from the wallet, fraction digits from the currency, U+2212, expenses in neutral ink.
- `kind` and a transfer's wallets immutable; one record for a transfer, listed in both wallets (ADR-0015).
- Glossary banned synonyms "entry, record, movement, operation" (Transaction), "line, leg" (Posting) in code and copy (P-01); class names say `Transaction…`, never `Entry…`/`Record…` (the spec 004 term "record type" is the engine's existing API and is not renamed).
- Merge hotspots shared with C4/C5 code and later C7/C8 (below).

**Scale/Scope**: 1 table + 1 migration, 1 API client (4 calls), 1 sync handler, 1 engine query, 1 domain service + balance projection + reconciler + coordinator + 3 hook implementations, 2 screens, edits to 3 existing screens (Home, wallet detail, wallet editor), design-system additions (date picker + field, amount field, `TransactionRow` options), ~85 string keys × 2 languages, 40 acceptance scenarios (31 covered by planned flows, 9 manual or host-only).

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: all principles PASS or N-A on the spec alone. P-03 (money in
every row), P-04 (offline-first ledger, restore), P-05 (calendar dates) and P-01 (banned synonyms
for Transaction) are central. No FAIL; proceed.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | `Transaction`, `TransactionKind` (income/expense/transfer), "counter wallet" only in wire DTO names; no class, table or key named `Entry`/`Record`/`Movement`/`Operation`/`Line`/`Leg`/`Posting`; table `transactions`; copy keys in contracts/ui-and-e2e.md say "Transaction"/"معاملة"; no "version"/"tombstone"/"posting" in UI |
| P-02 API contract fidelity | PASS | DTOs mirror transactions.yaml (data-model §2) with explicit `null`s; read-only members (`created_by`, `recurring_rule_id`, `occurrence_id`, `wallet_id`) never sent; unknown members ignored; outcomes branch on `code` and pointer (research R-07), `CURRENCY_MISMATCH` as a field code (R-20); headers from `HttpClientFactory`; retries owned by the engine |
| P-03 Money | PASS | `amountMinor: Long` everywhere; parsing via the float-free `AmountInput` (R-15); digits from `MoneyFormatter.digitsFor`; effects and day totals are `Long` sums in one currency per wallet; `MoneyText`/`TransactionRow` render U+2212 and neutral ink for expenses; no cross-currency arithmetic (transfers same currency, R-11) |
| P-04 Offline-first | PASS | Rule 1: screens, balances and search read Room (R-08, R-12, R-13). Rule 2: create/edit/delete are `engine.enqueue*` local writes. Rule 3: `Uuid.generateV7()`. Rule 4: base version on PUT/PATCH/DELETE; clashes shown with choices incl. restore (R-04, R-07). Rule 5: deletion is a queued deletion; rows removed only on the server's tombstone (after the carry) or for never-sent creates (R-06, R-09) |
| P-05 Time | PASS | `date` as `LocalDate` (`YYYY-MM-DD`), never zoned; "today" and month presets from the profile time zone at display/entry time (R-16); instants (`created_at`, carry, refresh stamps) in UTC |
| P-06 Localisation & RTL | PASS | ~85 keys in `values/` + `values-ar/`; refusal copy by code, never `detail`; Western digits incl. the date picker (R-16, AR screenshot); amounts LTR inside RTL rows; RTL screenshots |
| P-07 Design system only | PASS | Screens use `TransactionRow`, `DateHeader`, `CategoryRow`/`CategoryTile`, `CategoryChoiceSheet`, `SegmentedControl`, `AppFilterChip`, `AppSelectField`, fields, sheets, dialogs; new visuals (`AppDatePickerDialog`, `DateField`, `AmountField`, `TransactionRow` options) added to `designsystem` first with catalogue entries and screenshots |
| P-08 Accessibility | PASS | Sync marks and kinds as text; direction as sign and words in content descriptions; icon-only buttons described; 48 dp targets; 2× font screenshots of both screens and the filter sheet |
| P-09 Security & privacy | PASS | No amounts, notes, details, people or payloads logged (R-23, log-capture test); table cleared by `DatabaseCleaner`, last wallet by a `UserDataCleaner`; queue rows tagged by account (spec 004) |
| P-10 Shared-first architecture | PASS | All in `commonMain` (the date picker's locale override is common Compose); `feature.transactions.ui` imports its domain + `categories.domain` + `wallets.domain`; data imports no domain/ui/other feature (payload and field names in data, R-01); wallets ui reaches transactions only through `AppNavHost` callbacks (R-14); hooks bound in DI; layering test extended |
| P-11 Naming | PASS | `feature.transactions`, `TransactionsService`, `TransactionSyncHandler`, `TransactionsKey`; no codename |
| P-12 Entitlements & billing | N-A | No plan feature gates transactions (backend: no limit); no billing surface |
| P-13 Observability | PASS | Unexpected refusals show the server `request_id` ("Not saved. Reference: …", `Refusal.requestId`) |
| P-14 Quality gates | PASS | Contract tests per call and handled code; a test per spec §4 invariant; screenshot tests for new components and screens; migration test; `AppGraphTest`; captured live responses; no new dependency |
| P-15 Scope guard | PASS | No multi-currency transfers or FX (same-currency only), no splits, no wave 2 feed/batch, no bank sync; nothing from the MVP exclusion list |
| P-16 MVI presentation | PASS | Two screens each with `Contract.kt` (State/Intent/Effect/Change + pure `reduce`), a store on `MviViewModel` taking domain lambdas, `…Route`, stateless `…Screen` + preview; reducer and store tests (data-model §5); Home, wallet detail and wallet editor changes go through their existing stores |

No FAIL. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/008-transactions/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── transactions-api-client.md
│   ├── transactions-domain.md
│   └── ui-and-e2e.md
├── checklists/          # requirements.md (specify), transactions.md (checklist)
├── tasks.md             # /speckit-tasks
├── qa-report.md         # host QA (implementation)
└── summary.md           # last task
```

### Source Code (repository root)

```text
shared/
├── schemas/com.fares.saveme.core.database.AppDatabase/5.json     # exported
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/database/{AppDatabase.kt, Migrations.kt, DatabaseCleaner.kt}   # v5, MIGRATION_4_5, clear table
    │   ├── core/sync/{SyncEngine.kt, PendingChangeDao.kt}                     # + observeAll(type) / observeByType
    │   ├── designsystem/
    │   │   ├── component/{Dates.kt (NEW: AppDatePickerDialog, DateField), Money.kt (+AmountField), Lists.kt (TransactionRow options)}
    │   │   └── catalog/CatalogSections.kt         # entries
    │   ├── feature/transactions/                  # NEW
    │   │   ├── data/{TransactionApi, TransactionRepository, TransactionSyncHandler, BalanceEffect}.kt
    │   │   ├── data/dto/{TransactionDtos, TransactionPayload}.kt
    │   │   ├── data/local/{TransactionEntity, TransactionDao}.kt
    │   │   ├── domain/{TransactionsService, TransactionPostings, BalanceReconciler, TransactionCategoryUses,
    │   │   │          TransactionsWalletCleanup, TransactionsSyncCoordinator, DayGrouping,
    │   │   │          TransactionFilter, DestinationChoices, TransactionMapping}.kt
    │   │   ├── domain/model/{Transaction, TransactionKind, TransactionDraft, TransactionView, TransactionList,
    │   │   │                 TransactionQuery, TransactionField, RefusalReason, Results}.kt
    │   │   ├── ui/TransactionErrorCopy.kt
    │   │   ├── ui/list/{TransactionsContract, TransactionsViewModel, TransactionsRoute, TransactionsScreen, FilterSheet}.kt
    │   │   ├── ui/editor/{TransactionEditorContract, TransactionEditorViewModel, TransactionEditorRoute, TransactionEditorScreen}.kt
    │   │   └── di/TransactionsModule.kt
    │   ├── feature/wallets/di/WalletsModule.kt     # drop ZeroPendingPostings binding (hotspot)
    │   ├── feature/wallets/ui/detail/*             # Transactions row + Add transaction (effects → nav)
    │   ├── feature/wallets/ui/editor/WalletEditorViewModel.kt   # currency locked by hasPendingPostings
    │   ├── feature/categories/di/CategoriesModule.kt            # drop NoPendingCategoryUses binding
    │   ├── feature/auth/ui/home/{HomeContract, HomeScreen, HomeRoute, HomeViewModel}.kt   # Add transaction (hotspot)
    │   ├── feature/auth/ui/AuthErrorCopy.kt        # CURRENCY_MISMATCH field code line
    │   ├── navigation/{Routes.kt, AppNavHost.kt}   # 2 keys + entries + callbacks
    │   └── {AppModules.kt, App.kt}                 # transactionsModule; coordinator foreground hook
    ├── commonMain/composeResources/values{,-ar}/strings.xml   # append-only block "Transactions (spec 008)"
    ├── commonTest/kotlin/com/fares/saveme/feature/transactions/…   # contract, handler, repository, domain, reducers, stores, captured
    └── androidHostTest/kotlin/com/fares/saveme/
        ├── core/database/Migration4To5Test.kt
        ├── core/di/AppGraphTest.kt                       # extended
        ├── feature/transactions/data/TransactionDaoTest.kt
        ├── feature/transactions/ui/{TransactionsScreenshotTest, TransactionsAccessibilityTest, TransactionsLayeringTest}.kt
        └── designsystem/ComponentScreenshotTest.kt
.maestro/008-transactions/                         # flows (runs deferred)
.maestro/common/{api.js, open-transactions.yaml, add-expense.yaml}
```

**Structure Decision**: one new feature package with the standard split; the engine, database,
navigation, Home, the wallets and categories modules are touched only additively (two DI bindings
move to the transactions module). Repositories own Room + API; `TransactionsService` (domain)
orchestrates gates, the engine, refreshes, the balance carry, choices and filters; stores call
domain lambdas only.

## Merge hotspots

| File | Change here | Overlap | Merge note |
|---|---|---|---|
| `core/database/AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt` | v5, entity/DAO, `MIGRATION_4_5`, clear | C7/C8 bump to v6 | Additive; starts from v4 (`77710b5`) |
| `core/sync/SyncEngine.kt`, `PendingChangeDao.kt` | `observeAll(type)` | C14 wave 2 | One method + one query |
| `feature/wallets/di/WalletsModule.kt`, `feature/categories/di/CategoriesModule.kt` | remove the zero/no-op hook bindings | — | Bindings move to `transactionsModule`; `AppGraphTest` proves one binding each |
| `feature/wallets/ui/detail/*`, `ui/editor/WalletEditorViewModel.kt` | Transactions row, Add transaction; currency lock | C9 (members) | Small; wallet goldens change on purpose |
| `strings.xml` (both) | append-only block `<!-- Transactions (spec 008) -->` | C7, C8 | Keep blocks |
| `AppModules.kt`, `App.kt` | `transactionsModule`; coordinator `onForeground()` | C7, C8 | One element / one call each |
| `navigation/Routes.kt`, `AppNavHost.kt` | 2 keys + entries + callbacks | C7, C8 | Additive |
| `feature/auth/ui/home/*` | `AddTransactionClicked`, effect, one button | C8 may add rows | Home tests and goldens updated on purpose |
| `designsystem/component/Lists.kt`, `Money.kt`, catalogue, `ComponentScreenshotTest.kt` | `TransactionRow` options, `AmountField`, date picker | C7, C13 | Defaults keep existing baselines unchanged |
| `.maestro/common/api.js` | transaction actions | C7 | Additive |

## Dependencies on C4 (spec 006), C5 (spec 007) and C14 (spec 004) code

- `core/sync/SyncEngine` (`enqueue`, `enqueueDelete`, `keepMine` with `restore`, `useTheirs`, `discard`, `syncNow`, `get`, `observe`), `SyncHandler.canRestore`, `Refusal.requestId`.
- `feature/wallets/domain`: `WalletsService` (`observeList`, `observeWallet`, `observeMembers`, `refresh`), `WalletView` (`server.myRole`, `server.currencyLocked`, `hasPendingPostings`, `hidden`), `PendingPostings`, `WalletDeletionParticipant` (resolved at call time, also on refresh drops), `AmountInput`.
- `feature/categories/domain`: `CategoriesService.observeChoices`, `observeDisplays`, `refreshReferences`, `PendingCategoryUses`; `designsystem` `CategoryChoiceSheet`/`CategoryChoiceList`, `CategoryRow`, `CategoryTile`.
- The spec 006 E2E plumbing (`network-off` / `network-on`, `api.js` slot B, `createWallet`) and spec 007's `createExpense`.

All are on `main` `77710b5`; T001 confirms.

## Implementation phases (input to /speckit-tasks)

1. **Setup**: confirm prerequisites; package, Koin module, string block.
2. **Foundation**: domain models and pure helpers (effect, grouping, filter); DTOs, API, contract tests; engine `observeAll`; Room entity/DAO, v5 migration, schema, cleaner; `TransactionSyncHandler`; repository (overlay, refresh merge, carry); `TransactionsService` skeleton + coordinator; hook implementations and DI moves; design-system additions; navigation keys; `AppGraphTest`.
3. **US1 Record** (P1): editor create, amount/date/category/more, gates, Home and wallet-detail entry, currency lock, E2E flows.
4. **US2 List & balance** (P1): list building, day totals, balance projection + reconciler, refresh triggers, list screen, E2E flows.
5. **US3 Edit & delete** (P1): edit mode, delete, clash and refusal UI incl. restore, E2E flows.
6. **US4 Transfer** (P2): destination choices, both-wallet listing and balances, transfer refusals, E2E flows.
7. **US5 Find** (P2): search, filters, chips, E2E flows.
8. **Polish**: Arabic review, accessibility, layering, performance, host QA with live contract checks, contract recheck, quickstart (deferred), summary.

## External dependencies (not in this repo)

- Backend spec 012 on the dev stack (`2dbb8d7`, built, committed; `transactions.yaml` v1.7 since `c0e9844`) and seeded default categories. B14 (feeds, batch) is not used.
- Backend contract gaps: none found (see spec §5); recorded again in `summary.md` after the host QA.

## Complexity Tracking

None.

## Analysis record (`/speckit-analyze`, 2026-10-02)

No CRITICAL findings. HIGH and MEDIUM findings, all fixed in this session:

| ID | Category | Severity | Finding | Fix |
|---|---|---|---|---|
| A1 | Constitution (P-10.6) | HIGH | The handler (in `data`) computes the balance carry with the effect rule, which the design had put in `domain` (`TransactionEffect`), so `data` would import `domain` | The rule is `BalanceEffect` in `feature.transactions.data` on primitives; the domain reuses it (research R-04, R-09; data-model §2–3; T004, T014) |
| A2 | Inconsistency | MEDIUM | FR-018's foreground refresh "with that screen open" was given to the coordinator (T025), which cannot know the open screen, while data-model already had a `Foregrounded` intent | The Route forwards lifecycle resume as `Foregrounded`; the store refreshes; the coordinator only kicks the reconciler (research R-08, T025, T044) |
| A3 | Inconsistency | MEDIUM | The navigation contract and T036 add "Add transaction" on the wallet detail, which the spec did not mention | Spec Assumptions name it as a third entry point under FR-008's rules; research R-14 updated |
| A4 | Underspecification | MEDIUM | A tombstone applied without a carry ("Accept the deletion") would stay as a hidden row until a reconcile that never comes | Without a carry a tombstone removes the row at once; only an own deletion keeps it hidden until its carry clears (research R-04, T007) |
| A5 | Coverage | MEDIUM | After "Use the other device's" the list shows the other device's values while the wallet balance may predate them | `useTheirs` asks the reconciler for one wallet re-read (research R-09, T051) |

Found earlier by `/speckit-checklist` and fixed in the spec: kind and wallet switches in the
editor (FR-002, FR-008), a wallet hidden by its own waiting deletion (FR-031), restore whose second
step is refused (Edge Cases), search field length (FR-027), transfer delete rule (FR-013), where
"create a wallet first" shows (US1-S7), Western digits in the date picker (FR-004).

LOW, left as is: SC-001 (record in under 10 s) is a usability target checked by hand
(quickstart 1), not a test; the carry is lost if the process dies between a send and its answer
being stored, which can show the old balance until the next wallet read (rare, self-healing);
`TransactionsState` gained `balanceMinor`/`isLiability` (data-model §5) for the balance line.

## Implementation notes

Recorded while implementing (2026-10-02, owner away; each is an assumed default). Numbers refer to research items.

- **Filters run in Kotlin over the shown values, not in Room (R-12).** A waiting edit can move a transaction into or out of a date range or category, so filtering the stored columns would hide or show the wrong rows. The DAO returns the wallet's live rows (`wallet_id` or `counter_wallet_id`, newest first); `TransactionFilter` applies search, date range, categories and kinds to the overlaid values. 2,000 rows build and filter well under the targets on the host (`TransactionsListPerformanceTest`). `TransactionDaoTest` therefore has no filter queries.
- **`TransactionList`, `DayUi` and `RowUi` differ slightly from data-model §3/§5.** `TransactionList` gained `totalCount` (to tell "no matches" from "nothing yet"), `categoryIds` (the category filter lists what the wallet uses) and `waitingCount` (offline banner); `DayUi` carries the date, not a label, and the screen turns it into "Today" / "Yesterday" / the formatted date from `state.today`; `RowUi` carries the domain's direction, counterpart and the resolved `CategoryDisplay`, and the screen builds the words. `TransactionView` gained `createdAt` (sort key), `walletCurrency` (the wallet's present currency, for FR-026) and `baseVersion` is derived.
- **One snapshot for the balance (R-09).** The first design observed the queue, the rows by id and the carry as three flows; in the instant between a send's answer being stored and the queue row going, `combine` could add the change twice (a visible −91.00 for a −45.50 expense). `TransactionPostings` now reads the queue and one `observeRelevant(ids)` query (the given rows plus every row with a carry) together, so the delta is computed from one table snapshot; `deltaFor` is unchanged across the whole send (`TransactionPostingsTest`, driven through the real engine). `observeByIds` and `observeCarry` were removed.
- **The balance reconciler watches only once signed in.** `TransactionsSyncCoordinator` starts `BalanceReconciler.start()` on the first signed-in state (like the wallets and categories coordinators do their work). Starting it at app start made `AppGraphTest` collect a Room flow through the bundled SQLite driver, whose JNI cannot load on the host, and the uncaught exception failed an unrelated screenshot test.
- **Wallet cleanup drops queue rows from a launched job (R-19).** The wallet handler's tombstone path calls the participants while the engine holds its row lock, and `engine.discard` takes the same lock, so `TransactionsWalletCleanup` removes the rows at once and launches the `discard` calls.
- **Handler: currency and counter wallet.** R-04 said existing records always send the stored currency; FR-026 needs a never-sent create to send the wallet's present currency after a "currency changed" refusal. The handler sends the stored currency only for a record the server already has (version > 0); a create sends the payload's (the wallet's at save time). The counter wallet and kind are the stored ones whenever a stored row exists, even when it holds none.
- **Reference refresh with the floor.** `refresh(walletId)` asks C5 for the wallet's partner categories (`force = true`) only when the wallet read itself is not skipped by the 60 s floor; a pull always does both.
- **"Use the other device's" asks for a full wallets read.** `WalletsService` has no single-wallet read, so the reconciler's `requestWalletRead()` calls `refresh(force = true)` (and so does a list read that changed rows).
- **Editor: the transaction vanishing (closed in host QA).** When a refresh removes the open transaction from the phone (deleted elsewhere) and the person has typed a change (the form differs from what the editor loaded), the editor keeps the typing and stops offering Delete; Save passes the server copy the editor opened on (`save(id, draft, base, lastSeen)`), the service puts that copy back as the row and queues the edit on its version, and the server answers it like any other stale change (409 with the tombstone → "Deleted on another device" with "Restore with my change" / "Accept the deletion", or a refusal such as 404). With nothing typed, the editor closes with "no longer available" (the person's own accept/discard/delete excepted, as in spec 007); a save that finds nothing to base a change on says the same. "A transaction with no waiting change" in the edge case is read as "nothing typed": a transaction with a queued change is never removed by a refresh. Proved by `TransactionEditorViewModelTest.aTransactionRemovedWhileThePersonTypes…`, `TransactionsServiceEditDeleteTest.aSaveOfATransactionRemovedWhileEditing…` and the captured `TransactionCapturedResponsesTest.aTransactionRemovedByARefreshWhileThePersonTypes…` (real 409 tombstone). The delete confirmation names the amount and date the transaction had when the editor opened.
- **Currency lock through the carry (host QA).** `TransactionPostings.hasPending` is also true while a synced change's carry waits for a wallet read: until then the phone's wallet copy predates the change and can still say `currency_locked: false` (live QA: the server locks the wallet at its first transaction, including an incoming transfer). The wallet's "waiting" mark therefore stays until its balance is read again (normally about a second).
- **Editor: kind, wallets and category display.** The category field shows the category resolved when the editor opens (`observeDisplays(walletId)`) or chosen from the sheet; the sheet shows `current` / own / defaults with C5's tags (`categoryChoice.*`; the sheet is a window, so flows tap by text). A transfer's destination is chosen from `observeDestinations`; read-only in edit mode. Unknown kinds show the unknown-kind note and offer no edit.
- **Koin.** The editor store takes one `TransactionEditorArgs` parameter (two strings in one `parametersOf` are not safe to read by type). "Today" is `ProfileToday` (profile zone, device zone until the profile is read). `TransactionPostings`, `TransactionCategoryUses`, `TransactionsWalletCleanup` resolve the engine on use; `AppGraphTest` asserts one binding each of `PendingPostings` and `PendingCategoryUses`, the `transaction` handler and the cleanup participant.
- **Date picker (R-16).** The Material 3 picker prints Arabic-Indic digits under an Arabic locale, and neither `LocalAppLocale` nor `LocalLocale` reaches it (probed). `AppDatePickerContent` therefore builds its state with `DatePickerState(locale = westernDigitsLocale(language), …)` (`expect/actual`: `Locale("ar-u-nu-latn")` on Android, `NSLocale("ar@numbers=latn")` on iOS) and a wrapping `DatePickerFormatter` for the headline and month title; the Arabic screenshot (`transactions_ar.png`) shows Western digits. `designsystem` still imports nothing from `core` or a feature. The iOS actual compiles; it was not run.
- **Design-system additions.** `AppTextField` gained `textStyle` and `minLines`, `AppSelectField` a `trailingIcon`, `AmountField` an `enabled`; `TransactionRow` gained `leading`, `status`, `directionLabel` and tags for amount and status (defaults keep every old baseline pixel-identical). New icons `Calendar` and `Close`. A catalogue section `TransactionsSection` shows the date field, amount field, picker and the richer rows. No new dependency.
- **Wallet detail and Home.** The wallet detail shows a "Transactions" card (rows "Transactions" and, for an owner or editor of an active wallet, "Add transaction") above the members, replacing the "No transactions yet" placeholder; Home gets a primary "Add transaction" button under its alerts. Their goldens (2 Home states, 10 wallet-detail states, × 3 variants) were re-recorded on purpose and inspected.
- **`CURRENCY_MISMATCH` copy (R-20).** No `AuthErrorCopy` change was needed: field-level codes already fall to the generic field message and the transaction copy chooses by pointer; `ApiErrorCode` is unchanged (`check-backend-contract.sh` reports it consistent with the catalogue).
- **Contract recheck (T086).** `transactions.yaml` is unchanged since `c0e9844`; the catalogue and conventions show only the budget item type in the batch note (not used here).
- **Maestro (not run, owner policy).** Flows are under `.maestro/008-transactions/` (50 files for 31 scenarios, parts for offline and second-session steps); `api.js` gained a `PATCH` verb and `listTransactions`, `createTransaction`, `saveTransaction`, `deleteTransaction`, `restoreTransaction` (`getWallet` already existed) plus `resolveCategoryId` (own first, then the default set); `common/open-transactions.yaml`, `add-expense.yaml` and `seed-transaction.yaml`. Static review (T085): every `id:` selector in the flows exists as a `testTag` in the code; the category and destination sheets and all dialog buttons are tapped by text. US1-S5 (yesterday) taps the day number computed from the host clock; on the 1st it first pages to the previous month (host QA fix). Host QA also fixed the seed date (`api.js` defaulted every seeded transaction to 2026-10-02, which broke "This month" in US5-S2 on any other month; it is now today) and US1-S7's way back to Home (four screens, not three).
- **Invariants to tests (T080).** Offline-first reads: `TransactionsServiceListTest` (screens from Room only; no API in stores); queued create/edit/delete with base version and source scope: `TransactionsServiceCreateTest`, `TransactionsServiceEditDeleteTest`; UUIDv7: `theIdIsAUuidV7`; deletion as queued tombstone and never-sent removal: `aConfirmedDeleteQueues…`, `aNeverSentCreateIsRemovedAtOnce…`; money in minor units per currency: `TransactionModelTest` (EGP, KWD, JPY), `TransactionSyncHandlerTest`; kind/wallets never changed: `TransactionSyncHandlerTest` (stored kind and counter wallet), `TransactionEditorReducerTest`; restore PATCH then PUT: `TransactionSyncHandlerTest`, `TransactionRestoreFlowTest`; transfer in both wallets and both balances: `TransactionDaoTest`, `TransactionsServiceTransferTest`, `TransactionPostingsTest`; balance = server + unsent + carry, no jump: `TransactionPostingsTest`, `BalanceReconcilerTest`, `TransactionRepositoryTest`; read-only members never sent: `TransactionApiContractTest`; failures by code and pointer: `TransactionSyncStateTest`, `TransactionCopyTest`; category choice and current kept: `TransactionEditorViewModelTest`; refresh keeps unsent rows: `TransactionRepositoryTest`; search and filters: `TransactionFilterTest`, `TransactionsViewModelTest`; currency lock and category uses: `WalletEditorViewModelTest`, `TransactionCategoryUsesTest`; wallet removal: `TransactionsWalletCleanupTest`; no payload in logs: `TransactionLoggingTest`; sign-out wipe: `SignOutWipeTest`; migration: `Migration4To5Test`, `MigrationTest`, `Migration2To3Test`, `Migration3To4Test`.

