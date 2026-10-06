# Contract: Reports domain APIs and hooks

## `feature.reports.domain.ReportsService` (new)

```kotlin
interface ReportsService {
    /** The report for a scope under the session's choices; recomputed on any change of rows, queue, profile, labels or day. */
    fun observe(scope: ReportScope): Flow<ReportState>          // Loading | NoWallets | Ready(Report) | WalletGone
    fun observeChoices(scope: ReportScope): Flow<ReportChoices>
    fun observeCurrencies(): Flow<List<String>>                 // currencies of the person's wallets + profile default, sorted
    fun setRange(scope: ReportScope, range: RangeChoice)         // CurrentPeriod | Previous | Next | Custom(from, to)
    fun setTrendLength(scope: ReportScope, length: Int)          // 3, 6, 12
    fun setCurrency(currency: String)                            // cross-wallet only
    fun setIncludeScheduled(scope: ReportScope, on: Boolean)
    fun validateCustom(from: LocalDate, to: LocalDate): RangeProblem?   // null | Order | TooLong | OutOfBounds
    suspend fun refresh(scope: ReportScope, force: Boolean = false): RefreshOutcome   // existing auth.domain type
    fun observeLabels(scope: ReportScope): Flow<ReportLabels>
}
```

Pure parts (no I/O): `ReportCalculator` (summary, breakdown, trend, planned), `Shares`,
`ReportPeriods` (derive / learned / unknown, previous / next, forward limit), `RangeRules`,
`CoveredWallets` (covered and excluded from the wallet list and a currency). In-memory holders:
`ReportChoicesStore`, `LearnedPeriods`. `ReportParityProbe` (dev) / `NoParityProbe` (release).

## `feature.transactions.domain.ReportLedger` (new; research R-03)

```kotlin
interface ReportLedger { fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<LedgerSnapshot> }
object NoReportLedger : ReportLedger   // empty, until transactions binds TransactionReportLedger
```

Rules: effective values = waiting change over the stored row (refused and clashing too); a waiting
deletion removes the row; a waiting create without a placeholder adds its payload; a phone-only
confirmation is an actual line; deleted rows and unknown kinds contribute nothing; `before` is, per
wallet, the signed effect of effective live lines dated before `from`; `synced*` excludes the
overlay and phone-only rows; `waitingTouches` lists `(walletId, date)` of both sides of every waiting
change. One Room snapshot read together with the queue.

## `feature.recurring.domain.ScheduledMoney` (new; research R-05)

```kotlin
interface ScheduledMoney { fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<List<ScheduledLine>> }
object NoScheduledMoney : ScheduledMoney
```

Rules: server status `scheduled`; a waiting confirm or skip removes the occurrence; a waiting edit's
values apply; wallet or counter wallet in the set; dated in the range. `observeWaiting(walletIds, from, to): Flow<Boolean>` tells whether an occurrence change waits there (FR-002).

## `feature.categories.domain.CategoriesService.observeReportLabels` (added member; research R-08)

```kotlin
fun observeReportLabels(walletIds: Set<String>): Flow<ReportLabels>
```

`keyOf(id)` = platform `system_key` or the id; `display(id)` resolves own → platform (app language)
→ covered wallets' references → unavailable (a miss asks for the wallet's references under the
existing per-wallet floor). The interface gets a default body (ids as keys, `observeDisplays(null)`)
so existing fakes compile.

## `feature.budgets.domain.BudgetPeriods` (unchanged, reused)

`calendar(today, startDay)` is called by `ReportPeriods` for derivable periods.

## `feature.wallets.domain.WalletsService` (unchanged)

`observeList()` (active and archived `WalletView`s with `shown` values and `updatedAt`) and
`observeWallet(id)` (null after a purge → `WalletGone`).

## Home and wallet detail (navigation only)

- `auth.ui.home`: `HomeIntent.ReportsClicked` → `HomeEffect.NavigateReports`; a "Reports" row after "Budgets".
- `wallets.ui.detail`: `WalletDetailIntent.ReportClicked` → `WalletDetailEffect.OpenReport(walletId)`; a "Report" action for every role.

## `core` (unchanged)

`SyncRunner.refresh(ReadTarget, force)`, `SyncEngine.observeAll(recordType)`, `ApiClient`,
`UserDataCleaner` (reports registers one to clear choices and learned periods), `AppConfig.environment`.

## Layering (checked by `ReportsLayeringTest`)

- `feature.reports.data` imports only `core` (no domain, ui or other feature).
- `feature.reports.domain` imports `core`, its own `data` (the API, for the lookup and the probe) and
  other features' `domain` only: transactions, recurring, categories, wallets, budgets, account, auth.
- `feature.reports.ui` imports its `domain` and `designsystem` only; no DTO reaches `ui`.
- No other feature imports `feature.reports`; `navigation` imports the `feature.reports.ui` entry points.
