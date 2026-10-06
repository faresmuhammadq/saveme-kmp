# Research: Reports (client)

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-10-06

Each entry records a decision, why, and what else was weighed. Owner away with delegated authority:
each open choice takes the conservative option and is listed under "Assumed defaults" in the plan.

## R-01 Source of the figures: the phone computes, the server is not shown

**Decision**: Every figure on screen is computed on the phone from its copy of the covered wallets,
their transactions and occurrences, with waiting changes overlaid (spec Q1, FR-001). The six report
endpoints are not used for display. In release builds the only report read is the Budget Period
lookup (R-07); in the development build a parity probe also reads all three reports of the shown
scope and logs mismatching member names (R-12).

**Rationale**:

| | Server reports + cached last answer | Phone computation (chosen) |
|---|---|---|
| Offline | Last answer only, missing every offline change; needs a fallback computed on the phone anyway (brief) | Always current, offline changes included at once |
| Agreement with other screens | Can disagree with the budgets screen and the wallet balance (both phone-computed) until the next read | Same rules and same rows as budgets (spec 012) and balances (spec 008) |
| Ranges | One round trip per range, period, currency and switch change | Any range instantly |
| Storage | A cache table (Room v11), invalidation on every write, membership loss, sign-out | None (Room stays v10) |
| Server knowledge the phone lacks | Start day history; data not read yet | Start day history → lookup (R-07); data not read yet → read on open (R-11) |
| Code paths | Two (server + fallback) that must agree, plus a visible jump between them | One |
| Parity risk | None for the shown answer; fallback still needs parity | Proven by captured answers (R-13) and the dev probe (R-12) |

The backend designed for this: ADR-0022 notes that the cross-wallet set "is exactly the wallets the
phone holds, so the phone can reproduce the figures offline", and contract item 8 makes the offline
fallback the same rules over local transactions. Since the fallback must exist in any case, using it
always removes the cache, the second path and the jump. P-04.1 does not forbid either choice (a
report is not syncable), but the phone-computed one keeps the offline-first promise.

**Alternatives considered**: (a) server-first with a cached last answer and an "Updated" caption
(the brief's sketch) — rejected for the stale-after-offline-edit problem and the second code path;
(b) the budgets pattern (phone figure, server figure shown while nothing changed) — rejected: once
the phone reads the covered feeds on open (R-11) the server figure adds nothing but start-day
history, which R-07 covers for dates only, and it would need the server answers stored per scope,
range and switch; (c) online-only — violates the offline-first product promise.

## R-02 Package and boundaries

**Decision**: A new bounded context `feature.reports` with `domain` and `ui` and a thin `data`
(the API client and DTOs only) plus `di`. It reads other features only through their `domain` APIs
(P-10.4): transactions (`ReportLedger`, new, R-03), recurring (`ScheduledMoney`, new, R-05),
wallets (`WalletsService.observeList`, existing), categories (`CategoriesService.observeReportLabels`,
new, R-08), account (`ProfileView` for time zone, default currency, start day and its date; existing,
as budgets use it). Home (auth) gains a navigation-only row; wallet detail (wallets) gains a
"Report" action that emits a navigation effect — neither imports reports.

**Rationale**: the budgets precedent (spec 012 R-01, R-05): hooks owned by the lower feature keep
the graph one-way and testable by `ReportsLayeringTest`.

**Alternatives considered**: reading the transaction DAO from reports (breaks P-10.4); putting the
calculator in transactions (reports' rules — covered wallets, currency, planned — are not
transactions' concern).

## R-03 `ReportLedger` in transactions: effective rows plus balances before a date

**Decision**: A new transactions domain API:

```kotlin
interface ReportLedger {
    /** Effective live income/expense/transfer lines touching [walletIds] dated in [from]..[to], and per wallet the
     *  effect of every effective live line dated before [from], with waiting changes overlaid. */
    fun observe(walletIds: Set<String>, from: LocalDate, to: LocalDate): Flow<LedgerSnapshot>
}
data class LedgerLine(val id: String, val kind: String, val date: LocalDate, val amountMinor: Long, val currency: String,
                      val walletId: String, val counterWalletId: String?, val categoryId: String?, val waiting: Boolean)
data class LedgerSnapshot(val lines: List<LedgerLine>, val before: Map<String, Long>, val syncedLines: List<LedgerLine>,
                          val syncedBefore: Map<String, Long>, val waitingTouches: List<LedgerTouch>)
```

`TransactionReportLedger` reads, in one Room snapshot with the queue (the `TransactionPostings`
pattern): (1) rows touching the wallets dated in the range plus every row a waiting change names
plus phone-only confirmations; (2) a grouped `SUM` of `BalanceEffect` per wallet over synced live
rows dated before `from` (SQL `CASE` on kind and side, the same rule as `BalanceEffect.of`); then in
Kotlin removes the stored effect and adds the effective effect of every waiting change whose stored
or shown date is before `from`. Unknown kinds are ignored (spec 008 R-22). `synced*` excludes the
overlay and phone-only rows (for the dev parity probe, R-12). `waitingTouches` lists the wallet and
date of both sides of every waiting change (FR-002).

**Rationale**: one range query plus one aggregate keeps SC-005 (20,000 rows, 1 s) without loading
the whole history; the overlay rules are those of `TransactionOwnSpending` and `TransactionPostings`,
so a waiting change shows the same here as in the list and the balance.

**Alternatives considered**: computing balances from `balance_minor` + pending delta (the wallet
screen's figure) — only valid for "today", not for `from` in the past, and the server never uses
`balance_minor` (ADR-0022 rule 4); loading all rows — too slow at scale.

## R-04 The calculator (pure, `reports.domain`)

**Decision**: `ReportCalculator` (pure functions, no I/O, no clock):

- `summary(scope, range, ledger, openings, planned?)`: per line, `income`/`expense` add to income/
  expenses when the line's wallet is covered; a `transfer` adds to transfers out when its source is
  covered and its destination is not, to transfers in when the destination is covered and the source
  is not, nowhere when both are covered (a wallet report covers one wallet, so its transfers always
  count in their direction). Balances = Σ covered openings + Σ covered `before` (start) and + the
  covered effect of the range's lines (end). Identity asserted in tests.
- `byCategory(...)`: group income/expense lines of covered wallets by `labelOf(categoryId)` =
  platform `system_key` or the id (R-08); drop zero items (FR-006); sort by value desc, then label
  asc (byte order: the backend sorts with PHP `strcmp` in `CategoryBreakdown`; confirmed against
  captured answers, R-13); totals = Σ items.
- `trend(periods, lines)`: for each period, income, expenses, net; zero-filled.
- Sums use `Math.addExact`-style checked addition (`Long`); overflow → a `TooLarge` marker shown as
  "Too large to show" (spec edge case).
- `shares(items, total)`: `(value × 200 + total) / (2 × total)` — half up, integer; `0` for a
  non-zero value → "<1%".

**Rationale**: P-03 integer arithmetic; mirrors `SummaryFigures`, `CategoryBreakdown`, `TrendSeries`
of the backend's Reporting Domain (spec 019).

## R-05 Scheduled money: `ScheduledMoney` in recurring

**Decision**: A recurring domain API `ScheduledMoney.observe(walletIds, from, to): Flow<List<ScheduledLine>>`
returning occurrences on the phone with server status `scheduled`, no waiting confirm or skip (a
waiting edit's shown values apply), dated in the range, with kind, amount, currency, wallet, counter
wallet, category. Reports keeps only lines in the report's currency (spec 019 Q: an occurrence in
another currency is left out) and folds them with the same covered-transfer rule (backend
`PlannedFigures`). Planned summary, per-category planned sums (income/expense only) and per-period
planned income/expenses. With the switch on and nothing scheduled, planned figures are zeros, not
absent (backend contract note).

**Rationale**: the phone holds the occurrences of every rule in its wallets (C7) and they are
generated by the server with the same horizon (today + 35 days), so the phone sees the same set
after a read. **Gap**: a rule in a wallet the person does not belong to that transfers into a covered
wallet — its occurrences ride the source wallet's feed; live QA (T0xx) checks whether they reach the
phone; if not, the planned transfers in of such a wallet are under-counted and the gap is recorded
(spec §9).

## R-06 Ranges and the forward limit

**Decision**: `ReportRange(from, to, kind: BudgetPeriod|Custom, source: Derived|Server)`. Previous/
next walk Budget Periods (R-07). The forward limit is the period containing `today + 35 days`
(FR-008). Custom: `from ≤ to`, `to − from + 1 ≤ 366`, within 1900-01-01..2999-12-31 (the server's
`ReportRange` rule). The trend ends with the period containing `range.to`.

## R-07 Budget Periods: derive what the phone can, ask the server for the rest

**Decision**: `ReportPeriods.containing(date, profile, learned)`:

1. Profile known with start day `d`, `appliesFrom = F`: if `F == null` or `date ≥ F`, the period is
   `BudgetPeriods.calendar(date, d)` — the pure object in budgets' domain (spec 012), reused
   through another feature's domain as P-10.4 allows, so the two features can never disagree — and
   the derived start must be ≥ F (a period that would start before F is the transitional one, not
   derivable).
2. Otherwise the learned server periods (memory, per account) are searched for one containing `date`.
3. Otherwise → `Unknown` ("Shown when you're online"). When online, a lookup is started:
   `GET /reports/trend?months=m&date=x` with `x` the needed date and `m` the number of periods still
   unknown (max 12; more → several reads, each ending the day before the earliest learned start).
   Only `data.periods` is kept.

No profile on the phone yet → start day 1, no F (server default without a profile, spec 012 FR-009).

**Rationale**: the start day history is server-internal (spec 012 §9 gap 1); the profile's
`budget_period_start_day_from` is the date the current day applies from (identity.yaml), so periods
from F onward are regular; anything before F needs the server. Showing guessed dates would break
SC-004. Most people never change the day (F null), so the lookup is rare.

**Alternatives considered**: storing learned periods in Room (schema change for a rare case; the
in-memory copy is re-learned on the first online open); using the budgets' stored progress periods
(only current periods of people with budgets); guessing with the current day (wrong figures).

## R-08 Labels: grouping keys and localised names

**Decision**: A categories domain addition `CategoriesService.observeReportLabels(walletIds: Set<String>): Flow<ReportLabels>`
giving (a) `keyOf(categoryId)`: the platform category's `system_key` when the id is a platform
category on the phone, else the id; (b) `display(categoryId)`: a `CategoryDisplay` resolved from
own categories, platform defaults in the app language and the category references of **every**
covered wallet (the existing resolver with the union of references; a miss asks for the references
of each covered wallet under the existing per-wallet floor). The machine key is never shown.

**Rationale**: the server groups platform categories by `system_key` and others by id (FR-006);
the phone must use the same key for the same sort order. `observeDisplays(walletId)` takes one
wallet; a cross-wallet report needs the union.

## R-09 Screens (P-16) and navigation

**Decision**: Two screens, each Contract/ViewModel/Route/Screen:

- `ReportsKey(walletId: String? = null)` → `ui/reports`: scope header (wallet name, or "All wallets"
  + currency choice), range bar (Previous, dates, Next, "Custom range"), excluded notice, freshness
  and waiting notes, summary card, top 5 expense categories + "See all", trend (3/6/12 segmented
  control, chart, table), "Include scheduled" switch. Pull to refresh.
- `ReportCategoriesKey(walletId: String?, from, to, currency, includeScheduled, kind)` →
  `ui/categories`: Expenses/Income segmented control and the full list.

Session choices live in a `ReportChoices` holder (domain, in memory, per account and scope) so the
detail screen and returning to Reports keep them (FR-012). Home: `ReportsClicked` → `NavigateReports`.
Wallet detail: `ReportClicked` → `OpenReport(walletId)`. The wallet report closes on the wallet's
purge (observe `WalletsService.observeWallet(id)` null, as the wallet detail does).

## R-10 Design system: chart components (P-07.3)

**Decision**: Add to `designsystem/component/Charts.kt`, each with catalogue entries and screenshot
tests in EN LTR, AR RTL and font scale 2.0 (P-14.4), drawn with Compose `Canvas`/`Box` — no new
dependency:

- `ShareBarRow(label, icon, color, amount, shareText, scheduledText?)`: a row with a horizontal bar
  filled to the share from the start edge (mirrors in RTL), the written share and amount; the bar is
  decorative (`clearAndSetSemantics` on the row with one description).
- `TrendBarChart(points: List<TrendPointUi>, …)`: grouped bars per period (income in the positive
  money colour, expenses in neutral ink per P-03.5, scheduled as an outlined segment), a zero
  baseline, period labels under the bars, oldest at the start edge; heights scaled to the largest
  value; one semantics node with a summary content description.
- `TrendTable(rows)`: period, income, expenses, net — the text alternative, always shown.
- `ReportFigureRow(label, amount, emphasis)` and `SummaryCard` layout for the seven summary lines
  (reusing `MoneyText`).
- `ExcludedWalletsNotice(...)`: an informational banner with actions (reusing `Feedback` styles).

Colours come from existing tokens (`MoneyColors` positive/neutral); legend words ("Income",
"Expenses", "Scheduled") always written, so state is never by colour alone (P-08.3).

**Rationale**: spec 001 has no chart component (catalogue checked) and the design canvas "Save Me
Mobile Design System" has no chart either; the components are built from its tokens (money colours,
card, type, spacing) and are listed for the owner's design review (plan, Owner review). Bars with
written values are the most accessible chart form and mirror simply.

**Alternatives considered**: a donut for categories (harder to label, poor at 200% text, no RTL
benefit); a chart library (new dependency, P-14.5).

## R-11 Freshness: read on open, "Updated" from read stamps

**Decision**: On screen open the store asks `ReportsService.refresh(scope, force = false)` which calls
`SyncRunner.refresh(ReadTarget.Everything, force)` for cross-wallet or `ReadTarget.Wallet(id)` then
`ReadTarget.User` (categories and profile) for a wallet report; the runner's existing 60 s floors apply; pull to refresh passes
`force = true`. "Updated <time>" = the oldest completed feed read among the covered scopes: cross-wallet uses
`WalletList.updatedAt` (already the oldest of the person's scope and every wallet,
`WalletRepository.observeListRefreshedAt`); a wallet report uses that wallet's
`TransactionList.updatedAt` from `TransactionsService` (its feed read stamp), shown only offline or after
a failed refresh (FR-020), as budgets and the wallet list do.

## R-12 Parity probe (development build only)

**Decision**: `ReportParityProbe` bound only when `AppConfig.environment == Environment.Dev` (the
development flavor, which already alone carries cleartext and the Paste link tool). After a successful refresh with no waiting touch on the scope,
it reads the three reports for the shown scope/range/currency/switch, maps them to the domain shapes
and compares with the calculator's result over the **synced-only** ledger; it logs
`report.parity ok|mismatch kind=summary scope=wallet:<id> members=income,balance_end` — names only.
Release: the probe binding is a no-op (`NoParityProbe`), so release never calls the figure endpoints
(FR-028). Covered by a flavor/binding test and a log-capture test.

**Rationale**: continuous evidence on the owner's device runs (deferred E2E) without showing server
figures or leaking amounts.

## R-13 Testing (owner policy: no device)

**Decision**: Host tests only. Pure calculator, shares, periods, ranges, labels, choices and
reducers in `commonTest`; `ReportLedger` SQL over an in-memory Room database in `androidHostTest`
(with the performance test); contract tests on `MockEngine` from `reports.yaml` examples (all six,
because the dev probe consumes them; P-14.3) including 400/401/404/422/426; screenshots for the chart
components and both screens (EN, AR, 2.0). **Live QA**: curl against the local backend with the
spec 013 `+partner` seed builds the SC-002 data set; the feeds (`/sync/...`) and the six reports
(cross-wallet in EGP and USD, each wallet, with and without `include_planned`, explicit range and
trend months 12) are captured into `CapturedReportResponses.kt`; `ReportsCapturedParityTest` loads
the captured feeds through the real appliers into an in-memory Room database, runs the real ledger,
scheduled-money, labels and calculator, and asserts equality with the captured reports member by
member (including item order and `excluded_wallets`). Maestro flows under `.maestro/014-reports/`
written and marked deferred.

## R-14 Copy and words (P-01, P-06)

**Decision**: An append-only block `<!-- Reports (spec 014) -->` in both `strings.xml`. Glossary
words: Report/تقرير, Budget period/فترة الميزانية, Transfer/تحويل, Wallet/محفظة. A string test
rejects "account", "advanced", "percentage of plan", "!" and any key missing in one language. Period
dates use `formatDayMonth` (spec 012) and add the year when the range is not in the current year.

## R-15 Logging (P-09.2, P-13)

**Decision**: Log refresh outcomes, period lookups (dates are not financial but are still not
logged; only "learned n periods"), probe outcomes (member names), and request ids. Never amounts,
shares, category names or wallet names. DTOs and domain figures redact `toString` (as budgets).

## R-16 Unknown values (P-02.3)

**Decision**: Report DTOs ignore unknown members (`Json { ignoreUnknownKeys = true }`, existing);
an unknown transaction kind or wallet type contributes nothing (as `BalanceEffect`); a probe answer
with an unknown member is compared on known members only.
