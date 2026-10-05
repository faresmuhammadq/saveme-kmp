# Data Model: Reports (client)

**Feature**: [spec.md](spec.md) · **Research**: [research.md](research.md)

## 1. Storage: no schema change

Room stays at **version 10**; no entity, column, index, DAO table or migration is added, and
`schemas/…/10.json` is unchanged. Reports read existing tables through other features' domain APIs:

| Table (owner) | Read for | Through |
|---|---|---|
| `wallet` (C4) | covered/excluded wallets, currency, effective opening balance, liability, archived, name | `WalletsService.observeList()` (existing; `WalletView.shown`) |
| `transactions` (C6) | range lines, balances before `from` | `ReportLedger` (new, transactions domain) |
| `occurrence` (C7) | scheduled money | `ScheduledMoney` (new, recurring domain) |
| `category`, `platform_category`, `category_reference` (C5) | grouping key, localised name, icon, colour | `CategoriesService.observeReportLabels` (new) |
| `profile` (spec 002) | time zone, default currency, start day, "applies from" | `ProfileView` (account domain, existing) |
| `sync_scope` (core) | "Updated" time | `WalletsService.observeList().updatedAt` (cross-wallet) / `TransactionsService` list `updatedAt` (one wallet) |
| `pending_change` (core) | waiting overlay and touches | inside `ReportLedger` / `ScheduledMoney` |

New DAO **queries** (no schema change) in `TransactionDao`:

- `observeForReport(accountId, walletIds, from, to, waitingIds)`: live rows with `wallet_id IN walletIds OR counter_wallet_id IN walletIds` dated in `from..to`, plus rows whose id is in `waitingIds`, plus phone-only confirmations (`pending_confirmation = 1`).
- `observeBalancesBefore(accountId, walletIds, before)`: per wallet, `SUM` of the signed effect of live synced rows dated `< before` (income +, expense −, transfer − on source / + on destination; unknown kinds 0), grouped by wallet id (two `UNION ALL` legs: source side and destination side).

New DAO query in `OccurrenceDao`: `observeScheduledFor(accountId, walletIds, from, to)` — status
`scheduled`, wallet or counter wallet in the set, date in range.

Indexes: the existing `transactions(wallet_id, date, created_at)` and `(counter_wallet_id, date, created_at)` indexes are checked in
the performance test (SC-005); if the aggregate misses its 1 s budget, an index is added under a
Room v11 decision recorded in plan Complexity Tracking (not expected).

## 2. Domain models (`reports.domain.model`)

| Model | Members | Notes |
|---|---|---|
| `ReportScope` | `Wallet(walletId)` · `AllWallets(currency)` | |
| `ReportRange` | `from: LocalDate`, `to: LocalDate`, `kind: Period|Custom`, `source: Derived|Server` | `to − from + 1 ≤ 366` |
| `BudgetPeriodSpan` | `start`, `end` | label = `start` |
| `PeriodLookup` | `Known(span)` · `Unknown` | Unknown → "Shown when you're online" |
| `Covered` | `walletIds: Set`, `excluded: List<ExcludedWallet(walletId, name, currency)>`, `currency` | sorted by currency then name for display |
| `SummaryFigures` | `income`, `expenses`, `net`, `transfersIn`, `transfersOut`, `balanceStart`, `balanceEnd`: `Long` (minor units); `currency` | identity asserted; `toString` redacted |
| `PlannedSummary` | `income`, `expenses`, `transfersIn`, `transfersOut` | null when the switch is off; zeros when on and nothing scheduled |
| `CategoryItem` | `label` (system key or id), `categoryId`, `systemKey?`, `valueMinor`, `plannedMinor?` | |
| `CategoryGroup` | `totalMinor`, `plannedMinor?`, `items` | sorted value desc, label asc |
| `CategoryBreakdown` | `expenses: CategoryGroup`, `income: CategoryGroup` | |
| `TrendPoint` | `period: BudgetPeriodSpan`, `incomeMinor`, `expensesMinor`, `netMinor`, `plannedIncomeMinor?`, `plannedExpensesMinor?` | `Unknown` periods carry no figures |
| `Report` | `scope`, `range`, `currency`, `summary`, `planned?`, `breakdown`, `trend: List<TrendPoint>|Unknown`, `includesWaiting: Boolean`, `updatedAt: Instant?`, `tooLarge: Boolean` | one emission per change |
| `ReportChoices` | `range`, `trendLength: 3|6|12`, `currency?`, `includeScheduled` | memory only, per account + scope |
| `Share` | `Percent(n)` · `UnderOne` · `None` | integer half up |

## 3. Domain hooks' value types

- `LedgerLine(id, kind, date, amountMinor, currency, walletId, counterWalletId?, categoryId?, waiting)` and `LedgerSnapshot(lines, before: Map<walletId, Long>, syncedLines, syncedBefore, waitingTouches: List<LedgerTouch(walletId, date)>)` — transactions domain.
- `ScheduledLine(occurrenceId, kind, date, amountMinor, currency, walletId, counterWalletId?, categoryId?)` — recurring domain.
- `ReportLabels(keyOf: (String) -> String, display: (String) -> CategoryDisplay)` — categories domain.

All `toString` redacted (no amounts, names) as in specs 008–012.

## 4. In-memory state

- `ReportChoicesStore` (reports domain, singleton): `Map<(accountId, scopeKey), ReportChoices>`; cleared on sign-out (`UserDataCleaner`) and when another account signs in.
- `LearnedPeriods` (reports domain, singleton): `Map<accountId, SortedSet<BudgetPeriodSpan>>` from `GET /reports/trend` answers; cleared the same way; discarded when the profile's start day or "applies from" date changes.

## 5. State transitions

- Choices: default (current Budget Period, 6, default currency, off) → changed by intents → kept until sign-out / process end.
- Period lookup: `Unknown` → (online, lookup ok) `Known(source = Server)`; `Unknown` → (offline / failure) stays `Unknown`; profile start-day change → learned periods dropped → re-derive.
- Freshness: `Fresh` (read ok) · `Offline(updatedAt)` · `Failed(updatedAt)`.
