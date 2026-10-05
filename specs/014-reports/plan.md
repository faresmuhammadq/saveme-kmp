# Implementation Plan: Reports (client)

**Branch**: `014-reports` | **Date**: 2026-10-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-reports/spec.md`

## Summary

A new bounded context `feature.reports` (thin data, domain, ui, di) shows per-wallet and
cross-wallet reports — summary, spending by category with shares, and a trend over Budget Periods —
**computed on the phone** from the transactions, occurrences and opening balances it already holds,
with waiting changes overlaid (research R-01). Two new domain hooks give reports what it needs
without reading other features' tables: `ReportLedger` in transactions (range lines plus per-wallet
balances before a date, R-03) and `ScheduledMoney` in recurring (R-05); categories gains
`observeReportLabels` (grouping key and localised names across covered wallets, R-08). Budget
Periods reuse budgets' `BudgetPeriods`; periods before the profile's "applies from" date are learned
from `GET /reports/trend` and never guessed (R-07). The other report endpoints are read only by a
development-build parity probe (R-12); release never shows or fetches server figures. Two MVI screens
(Reports, Spending by category), a Home "Reports" row, a wallet-detail "Report" action, and six new
design-system chart components drawn with Compose Canvas (R-10). **No Room change (stays v10), no
sync-engine change, no new dependency.**

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, MVI base, Ktor 3.6.0,
kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite,
Navigation 3, Kermit). **No new dependency.**

**Storage**: Room `saveme.db` **v10 unchanged** — two new DAO queries on `transactions` and one on
`occurrence` (no schema change; data-model §1). In-memory: session choices and learned periods.

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor `MockEngine` (contract tests from the
reports.yaml examples), fakes in `commonTest`; `ReportLedger`/`ScheduledMoney` queries over an
in-memory Room database, captured-answer parity, performance, graph and layering tests and Roborazzi
in `androidHostTest`; captured live-backend answers (`CapturedReportResponses.kt`). No emulator,
Maestro or device run (owner policy); Maestro flows written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: SC-005 — 20,000 transactions in the range across 20 wallets with a 12-period
trend computed within 1 s (one range query, one grouped balance query, one pass in Kotlin); a change
reflected within 1 s (flows, no polling; SC-003).

**Constraints**: ADR-0022 rules exactly (covered wallets, transfers, balances, grouping, sort, no
share from the server); P-03 integer arithmetic; periods never guessed; no figure stored; no amounts
in logs; no server figure shown; release never calls the figure endpoints.

**Scale/Scope**: 1 API client (6 operations), 1 service + 7 pure domain parts, 2 in-memory holders,
1 dev probe, 2 MVI screens, 2 navigation-only changes (Home, wallet detail), 3 hook additions
(`ReportLedger`, `ScheduledMoney`, `observeReportLabels`), 2 DAO query sets, 6 design-system
components, ~45 strings EN/AR.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails. Reports are MVP (brief C13; no export, no FX, no
AI, no savings goals); no new dependency; the backend gates nothing (`reports.advanced` reserved).

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code: `Report…`, `ReportRange`, `BudgetPeriodSpan`, `covered`/`excluded` wallets, `ScheduledMoney` (glossary "scheduled" occurrence); UI "Report" / "تقرير", "Budget period", "Transfer"; string test rejects "account" and "advanced" in report strings (R-14) |
| P-02 API contract fidelity | PASS | Shapes from reports.yaml v1.11 (contracts/api-client.md); query rules enforced before sending; unknown members ignored; failures by `code`; the lookup sends only `months` and `date`; shared headers and retry classes via `ApiClient` |
| P-03 Money | PASS | All figures `Long` minor units with checked addition; shares integer half up (R-04); digits from the currency; U+2212 for negative net and cross-wallet balances; liability balances as amounts owed; expenses in neutral ink |
| P-04 Offline-first | PASS | Reports are not syncable (rules 1–5 bind syncable entities), yet every figure comes from Room with waiting changes overlaid (R-01, R-03); nothing blocks offline; no write is made |
| P-05 Time | PASS | Ranges and periods `LocalDate`; "today" from the profile's zone only for derivation (`DayTicker`); read stamps `Instant` |
| P-06 Localisation & RTL | PASS | ~45 strings EN/AR; Western digits; labels localised on the phone from machine keys; charts mirror (oldest period and bar fill at the start edge); screenshots EN LTR, AR RTL, 2.0 |
| P-07 Design system only | PASS | Six chart components added to `designsystem` first with catalogue and goldens (R-10); screens use them and existing tokens only |
| P-08 Accessibility | PASS | Written value beside every bar; legend words; trend table as text alternative; chart summary descriptions; 48 dp controls; 200% font goldens |
| P-09 Security & privacy | PASS | No amounts, names or payloads in logs (R-15; log-capture test); probe logs member names only and is dev-only; DTOs and figures redact `toString`; sign-out clears in-memory choices and periods |
| P-10 Shared-first architecture | PASS | All in `commonMain`; reports reads transactions, recurring, categories, wallets, budgets and account only through their `domain`; `reports.data` imports only `core`; Home and wallet detail only emit navigation effects; `ReportsLayeringTest` |
| P-11 Naming | PASS | `feature.reports`, `ReportsService`, `ReportLedger`; no codename |
| P-12 Entitlements & billing | N-A | No capability gates reports; `reports.advanced` is not read (FR-024) |
| P-13 Observability | PASS | Refresh/lookup/probe outcomes with ids, codes, request ids, counts; unexpected lookup errors keep the request id in the log |
| P-14 Quality gates | PASS | Spec §4 invariants each with a failing-when-broken test; contract tests for all six operations incl. each handled code; screenshots for every new component and both screens; no new dependency |
| P-15 Scope guard | PASS | No FX (one currency per report, others excluded and named), no export (C15), no AI, no savings goals |
| P-16 MVI presentation | PASS | Two screens with Contract/ViewModel/Route/Screen; stores call `ReportsService` only; reducer and store tests; Home and wallet detail gain one intent and one effect each with tests |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/014-reports/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── api-client.md
│   ├── domain-apis.md
│   └── ui-and-e2e.md
├── checklists/
│   ├── requirements.md
│   └── design.md        # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/src/
├── commonMain/kotlin/com/fares/saveme/
│   ├── feature/reports/
│   │   ├── data/                                   # ReportsApi, dto/ (summary, by-category, trend, metas)
│   │   ├── domain/                                 # ReportsService, ReportCalculator, Shares, ReportPeriods, RangeRules,
│   │   │                                           #   CoveredWallets, ReportChoicesStore, LearnedPeriods, ReportParityProbe,
│   │   │                                           #   ReportsCleaner, model/
│   │   ├── ui/                                     # reports/, categories/ (Contract, ViewModel, Route, Screen), ReportRowsUi, ReportCopy
│   │   └── di/ReportsModule.kt
│   ├── feature/transactions/domain/                # ReportLedger (+ TransactionReportLedger, NoReportLedger)
│   ├── feature/transactions/data/local/            # TransactionDao: observeForReport, observeBalancesBefore
│   ├── feature/recurring/domain/ + data/local/     # ScheduledMoney (+ impl), OccurrenceDao.observeScheduledFor
│   ├── feature/categories/domain/                  # observeReportLabels, ReportLabels
│   ├── feature/auth/ui/home/                       # "Reports" row (intent + effect)
│   ├── feature/wallets/ui/detail/                  # "Report" action (intent + effect)
│   ├── designsystem/component/Charts.kt            # ShareBarRow, TrendBarChart, TrendTable, ReportFigureRow, SummaryCard, ExcludedWalletsNotice
│   ├── designsystem/catalog/ReportsCatalog.kt      # catalogue section
│   ├── core/di/                                    # reportsModule registered
│   └── navigation/                                 # ReportsKey, ReportCategoriesKey; AppNavHost wiring
├── commonMain/composeResources/values*/strings.xml # + Reports block (EN, AR)
├── commonTest/…/feature/reports/                   # calculator, shares, periods, ranges, covered, choices, service, reducers, stores, contract, probe
├── commonTest/…/feature/transactions/ + recurring/ # TransactionReportLedgerTest, ScheduledMoneyTest (fake engine)
└── androidHostTest/…                               # ledger/scheduled Room tests, captured parity, performance, AppGraphTest, ReportsLayeringTest,
                                                    #   SignOutWipeTest, screenshots (components, screens, Home, wallet detail)
.maestro/014-reports/                               # written, deferred: E2E on owner's device
```

**Structure Decision**: a feature package above transactions, recurring, categories, wallets and
budgets (R-02); every dependency on a lower feature goes through hooks it owns; `core` changes are
limited to DI registration (no database change).

## Implementation phases (input to /speckit-tasks)

1. **Setup**: package skeleton, Koin module registration, navigation keys, strings block.
2. **Foundation**: DTOs + `ReportsApi` + contract tests; pure domain (`ReportCalculator`, `Shares`, `ReportPeriods`, `RangeRules`, `CoveredWallets`); holders; `ReportLedger` (+ DAO queries); `ScheduledMoney` (+ DAO query); `observeReportLabels`; design-system charts + catalogue + goldens; `AppGraphTest`, layering test.
3. **US1 See**: service (cross-wallet, current period, summary + top categories), Reports screen, Spending by category screen, Home row.
4. **US2 Offline/parity**: waiting overlay note, refresh + freshness, dev parity probe, captured live answers and parity test.
5. **US3 Wallet**: wallet scope, liability rendering, wallet-detail action, purge close.
6. **US4 Trend**: trend computation, chart + table, length choice.
7. **US5 Periods**: previous/next/custom, forward limit, period lookup + learned periods, ticker.
8. **US6 Currencies**: currency choice, excluded notice, "See <code> report".
9. **US7 Scheduled**: `ScheduledMoney` folding, switch, "known up to".
10. **Polish**: log-capture test, performance test, sign-out, live contract QA + fixtures, Maestro flows (deferred), contract recheck, README, `summary.md` (parent).

## External dependencies (not in this repo)

Backend B13 built (`d7ed413`); contract recorded at `saveme-backend@b63e917` (reports.yaml v1.11
unchanged; backend working tree clean). Backend contract gaps (spec §9): start day history is
server-internal (lookup through the trend endpoint); a scheduled transfer into a covered wallet from
a wallet the person is not in may not reach the phone (QA check); no data-time stamp on reports
(not needed: the phone computes).

## Complexity Tracking

Empty: no constitution FAIL.

## Assumed defaults (owner away, delegated authority)

Each is the conservative option.

### Design (spec clarifications and research)

1. The phone computes every figure; server report figures are never shown; release reads only the trend endpoint for Budget Period dates (spec Q1, R-01, R-07).
2. Parity is proven by captured live answers replayed through the real appliers and calculator, and in the dev build by a parity probe that logs member names only (R-12, R-13).
3. Two screens (Reports per scope, Spending by category); entry from a Home "Reports" row and a wallet-detail "Report" action; no Home card (spec Q2).
4. Ranges: Budget Period (previous/next, forward to the period containing today + 35 days) or custom ≤ 366 days; trend 3/6/12 (default 6) ending with the range's last period; choices kept in memory only (spec Q3, R-06).
5. "Include scheduled" off by default; scheduled figures beside, never added (spec Q4, R-05).
6. Cross-wallet in the profile default currency (EGP when unknown) with a choice of the wallets' currencies; excluded wallets named with "See <code> report"; wallet reports in the wallet's currency (spec Q5).
7. Periods before the profile's "applies from" date are never guessed: learned from the server online, "Shown when you're online" offline; learned dates kept in memory only (R-07).
8. No Room change; no stored figure; nothing to wipe at sign-out except in-memory holders (spec §3).
9. Open triggers a feed read of the covered scopes (60 s floors); "Updated <time>" only offline or after a failed refresh (R-11).
10. Charts: horizontal share bars and grouped trend bars with written values and a table; no donut; no library (R-10).
11. Shares half up with "<1%" for tiny non-zero items; never normalised to 100 (R-04).
12. Category sort ties by machine key in byte order (the backend's `strcmp`), confirmed against captured answers (R-04).
13. A wallet report closes when the wallet is purged (R-09).
14. `reports.advanced` is not read; the Plan screen's line is unchanged (FR-024).

### Owner review

15. Arabic words in contracts/ui-and-e2e.md Copy.
16. The six chart components are not on the design canvas yet (P-07.3 asks to check against it): built from its tokens and to be added to the canvas after review (research R-10).
