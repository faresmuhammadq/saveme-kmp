# Summary: Reports (spec 014, C13)

**Date**: 2026-10-06 · **Status**: implemented, host QA and live-backend parity QA green, device E2E deferred to the owner, committed on branch `014-reports` (3853f33) · **Backend contract**: `saveme-backend@b63e917` (`reports.yaml` v1.11, unchanged since d7ed413).

## What was built

- **Reports computed entirely on the phone** from its own transactions, occurrences and opening balances, with waiting changes overlaid (works offline; "Includes changes waiting to sync"). Server figures are never shown.
- **Two screens**: Reports (per wallet, or across all wallets in one currency) and Spending by category. Summary (income, expenses, net, transfers in and out, balance start and end), spending by category with half-up integer shares and "<1%", and a 3/6/12 Budget Period trend with a Canvas bar chart (a written value under every bar, an outlined scheduled segment, mirrored in RTL) plus a data table as the text alternative.
- **Ranges**: previous/next Budget Period (next stops at the period containing today + 35 days) and custom ranges up to 366 days. Budget Periods are derived with `BudgetPeriods`; periods the phone cannot derive (before the profile's start-day change date) are learned from one `GET /reports/trend` (`months` and `date` only), never guessed. Offline the section says "Shown when you're online".
- **Cross-wallet**: one currency per report (the profile's default, else EGP; a choice of the wallets' currencies); a notice names wallets in other currencies with a "See <code> report" link. "Include scheduled" is off by default; scheduled money is shown beside actual figures, never added to them or to balances.
- **Entry points**: a Home "Reports" row and a "Report" action on wallet detail. A freshness caption ("Updated …" offline or after a failed refresh) and pull to refresh.
- **Hooks**: `ReportLedger`, `ScheduledMoney`, `observeReportLabels`, `TransactionsService.observeFeedReadAt`. A development-only parity probe (`DevReportParityProbe`) logs only figure names, never amounts.
- **Design system**: six new chart components drawn with Compose Canvas, no new dependency.
- **No schema change** (Room stays v10); nothing stored — choices and learned periods are in memory and cleared at sign-out.
- E2E assets: Maestro flows in `.maestro/014-reports/` (each marked deferred), `common/open-reports.yaml`, `common/seed-reports.yaml`, `api.js` `createTransfer`, `seedReportsData`, `getReportSummary`.

## Results

- **Host QA** (`qa-report.md`): 38/38 acceptance scenarios, 20/20 edge cases, 29/29 FRs, 12/12 invariants and 6/7 SCs PASS (SC-001's timing is device-only). 3,179 host tests in 405 suites, 0 failures; Roborazzi verify, `assembleDevDebug`, the iOS simulator compile and `cleanAllTests build allTests --continue` all green. 57 existing goldens changed on purpose (Home gained the Reports row, wallet detail the Report action); new goldens for the catalogue, chart states, 22 screen states and a 12-period page, each in EN LTR, AR RTL and EN 2×.
- **Live parity** (about 750 backend calls, 4 throwaway accounts, 7 wallets for the main one — Cash, Bank, a credit card with a negative opening balance, a USD wallet, an archived wallet, a wallet shared with a partner, a wallet where the person is a viewer, a deleted wallet whose transfer was kept — 44 transactions from 31 Jul to 3 Nov incl. deleted, future-dated and tied category sums, own, platform and partner categories, 8 recurring rules with one occurrence confirmed and one skipped, and a pending start day 25): captured answers driven through the app's real Koin graph (in-memory Room, mock HTTP engine, real sign-in, real `SyncRunner`/`FeedPuller` with all appliers, `TransactionReportLedger`, `ScheduledMoney`, labels, `ReportPeriods`, `ReportCalculator`) and compared with `ReportMapping`. **511 recorded answers compared (229 with `include_planned=true`); no unexplained difference.** Summaries (177) equal except the 6 gap-2 cases below; by-category (177): totals, every item and its order equal including ties (own category id before platform `transport` system key; byte order); trends (157) for 1, 3, 6 and 12 periods equal; the pending start-day user's 45 reports equal, with periods 1–31 Aug, 1–30 Sep, 1–24 Oct learned from `GET /reports/trend`.
- **Refusals recorded**: 400 `INVALID_QUERY_PARAMETER` for 367 days, `months` 13 or 0, lowercase `currency`, `date` with `from`/`to`, `from` after `to`, `from` alone, a bad date, a bad `include_planned`, `from` 1899-12-31 and 2999-12-31 – 3000-01-01; 404 for another person's, an unknown and a deleted wallet; 422 `INVALID_ID`; 401 without a credential. Accepted: 366 days, 1900-01-01, 2999-12-31. A wallet report ignores `currency` (200).
- **Fixed during QA**: with "Include scheduled" on and a currency with no wallet (live: EUR) the server answers zero `planned` figures but the phone left them absent (`ReportsService.data()`, test `ReportsServiceCurrencyTest…`). Added accessibility tests for the liability "Owed" text and the true minus sign (U+2212). `ReportsPerformanceTest` (SC-005) hardened: two warm-ups, then the fastest of seven runs, all runs in the failure message, bound unchanged (largest set measured 0.30–0.35 s against 1 s).
- Fixtures: `CapturedReportsResponses.kt` and `CapturedReportsA/B/D/Refusals.kt` with `ReportsCapturedParityTest` (8 tests). No credential in them.

## Backend gap found (confirmed live)

**A scheduled transfer into a covered wallet from a wallet the person is not in never reaches the phone.** The partner's rule and occurrences live in the partner's wallet; the covered wallet's feed carries transactions in other wallets that name it as destination (the actual transfer arrives and counts) but not another wallet's rule or occurrences. The server counts the amount in the planned `transfers_in` of the wallet's report and the cross-wallet report; the phone counts 0. Only planned `transfers_in` (6 summaries, with the switch on and the range containing the occurrence date) is affected; actual figures, balances, categories and trends are unaffected; the partner's own phone is exact. `ReportsCapturedParityTest` pins this and fails on any other difference or if the gap disappears. A fix needs a backend feed change (carry such occurrences on the destination wallet's feed).

## Deferred to the owner's device

T037, T047, T054, T061, T069, T075, T081, T094, T095 (flow runs, quickstart, full E2E report), SC-001 timing.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. The phone computes all figures; release builds call only `GET /reports/trend`, only for Budget Period dates it cannot derive.
2. Two screens; entry points are a Home row and a wallet-detail action; no Home card.
3. Default range is the current Budget Period; "Next" stops at the period containing today + 35 days; custom ranges up to 366 days; trend 3, 6 or 12 periods (default 6); choices kept in memory only (defaults after process death).
4. "Include scheduled" is off by default.
5. Cross-wallet uses the profile's default currency (EGP when unknown) with a choice of the wallets' currencies; a wallet report uses the wallet's currency.
6. Opening a report triggers a sync read (60-second floor; pull to refresh forces it).
7. Charts are Compose Canvas components: share bars, grouped trend bars with written values, a data table as the text alternative; no donut chart.
8. Shares round half up with whole numbers; tiny non-zero shares show "<1%"; ties sort by the machine key in byte order, as the backend does.
9. `reports.advanced` is not read; nothing is gated by plan.

### Implementation

10. `ReportScope.AllWallets` is an object; the currency is a session choice.
11. `setRange` and `setTrendLength` are `suspend` (they run the period lookup); Previous/Next from a custom range step out to the adjoining Budget Period.
12. The lookup is one `GET /reports/trend` with `months` = the number of unknown oldest periods and `date` = the day before the oldest nameable one; one read of at most 12 periods always suffices; not repeated until the next open or pull.
13. For SC-005 the ledger reads ten columns and only the range's rows, with per-day sums for the other trend days (70,000 rows about 0.65 s versus 1.6–3 s before).
14. `INVALID_QUERY_PARAMETER` and `INVALID_ID` are matched by raw problem code (core unchanged).
15. At 200 % text amounts drop below the label rather than squeezing it, and the chart scrolls from the start edge.
16. `BudgetsLayeringTest` lets `feature/reports` import `budgets.domain.BudgetPeriods` and `DayTicker` only.
17. The money DTO lives in reports (the existing one is in budgets' data layer).

### Owner review

18. The Arabic wording and the design-canvas review of the six chart components (they are not on the design canvas yet).

## Remaining risks

- Planned `transfers_in` under-counts for scheduled transfers into a covered wallet from a wallet the person is not in (backend gap above).
- Device-only checks are deferred (SC-001 timing, all Maestro flows, quickstart).
- A start-day change already in force cannot be produced live on a fresh account; the past-change case is proven by `ReportPeriodsTest` only.
- With a pending start-day change at app start and no network, the default report shows "Shown when you're online" until the server is reached once (by design).
- Parity depends on the phone holding every transaction of every covered wallet; QA proved it for the recorded dataset.
- Dev environment note: another process (the backend purge job) purged 34 accounts in the shared dev database during QA, mine included; the captured answers were taken before.
