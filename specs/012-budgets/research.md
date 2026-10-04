# Research: Budgets (client)

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-10-05

Every unknown in the plan's Technical Context is resolved here. Owner away with delegated
authority: each decision is the conservative option and is listed again in the plan's assumed
defaults.

## R-01 Package and boundaries

**Decision**: a new bounded context `feature.budgets` (`data`, `domain`, `ui`, `di`). It depends on
`core`, `designsystem` and the `domain` packages of categories (choices, displays,
`PendingCategoryUses`), transactions (a new `OwnSpending` API, R-05), wallets (wallet list:
currencies and the person's memberships), account (`ProfileSyncer.view`: start day, "applies
from", time zone) and auth (`SessionManager`, `BudgetSummary` hook, R-11). No lower feature imports
budgets: Home reads `BudgetSummary` (owned by `auth.domain`, default `NoBudgetSummary`, rebound by
budgets), categories read the union of `PendingCategoryUses` (budgets adds one source), transactions
reads rule authors through its own `RecurringLinks` (C7 adds one defaulted member).

**Rationale**: the same shape as specs 007 and 010 (P-10.4: cross-feature access only through
`domain`; P-10.6: `data` imports no `domain`). Budgets sit above transactions and categories.

**Alternatives**: putting budgets inside `feature.transactions` (rejected: a separate bounded
context in the backend, `Budgeting`; it would grow C6's service further).

## R-02 Room v9: two tables, one migration that forgets the user scope's position

**Decision**: `MIGRATION_8_9` creates `budget` and `budget_progress` (data-model §1–§2) and runs
`UPDATE sync_scope SET feed_cursor = NULL, feed_read_at = NULL, feed_position = NULL WHERE scope LIKE 'user:%'`.
Wallet scopes keep their cursors and positions. `AppDatabase` v9, schema `9.json` exported,
`DatabaseCleaner` deletes both tables.

**Rationale**: spec 009 default 9 ("C7/C8/C9 clear the affected positions in their own
migrations"); budgets ride only the user feed (backend spec 015), so only that scope is re-read.
The user feed is small (profile, sessions, categories, memberships, budgets); a from-the-start read
uses the spec 009 path (copy kept, sweep after the last page, rows with waiting changes kept), and
a missing membership is still checked against the wallet's feed before any removal (spec 009 FR-019).
The integer position must be cleared with the cursor (spec 011: written and cleared together).

**Alternatives**: clearing every scope (rejected: re-reads every wallet for nothing); a one-off
"read budgets" call through `GET /budgets` (rejected: a second read path the feed already covers,
spec 009 decision).

## R-03 Local row = server copy; the waiting change overlays it (specs 006–010)

**Decision**: `budget` holds the server's last copy; a budget created on the phone has a
placeholder row with `version = 0` (as categories). The queued `BudgetPayload` (`category_id`,
`currency`, `amount_minor`, `archive`) overlays it for display. A tombstone from the feed removes
the row unless a change to it waits (spec 009 deletion rule). Delete of a never-sent budget removes
the placeholder and discards its queued change (spec 007 pattern), nothing sent.

**Rationale**: P-04.1/P-04.4 with the established overlay pattern; screens never read the network.

## R-04 `BudgetSyncHandler` (record type `budget`, user scope, `canRestore = false`)

**Decision**:
- `batchItem`: delete → `BatchItem("budget", Delete, id, walletId = null, {version})`; a payload with `archive = true` → `null` (per record, archive is not a batch op); otherwise `Upsert` with `BudgetWrite` (`category_id` explicit null allowed, `period_type: "monthly"`, `amount_minor`, `currency`, `version`). For a budget the server already has (`version > 0`), category, currency and period type come from the **stored** row, never the payload (immutable members; avoids `IMMUTABLE_FIELD`).
- `send`: delete → `DELETE /budgets/{id}` with `{version}`; otherwise `PUT` (skipped when the change is archive-only and the stored limit equals the payload's), then `POST /budgets/{id}/archive` with the PUT's version when `archive` and not already archived. Same structure as `CategorySyncHandler`.
- `prerequisites`: the payload's `category_id` → `RecordKey("category", id)` (a category created on the phone and not yet accepted holds the budget, spec 009 FR-022). The wire type string is a local constant, as in `TransactionSyncHandler` (P-10.4).
- `onDiscarded`: removes a placeholder (`version = 0`); `refusalFields`: `/category_id` → category, `/amount_minor` → limit, `/currency` → currency, `/period_type` → period.
- Clash: the engine's standard choices; a tombstone in `current` = deleted elsewhere → "Accept the deletion" only (`canRestore = false`). `acceptsClash` stays false.

**Rationale**: the backend contract (PUT whole, DELETE with body, archive per record, batch
`upsert`/`delete` with no `wallet_id`); `PATCH` is unnecessary because a whole save carries the
limit and rides the batch. No engine change.

**Alternatives**: `PATCH` for limit edits (rejected: not a batch op; two write paths).

## R-05 Spending on the phone: a transactions domain API, `OwnSpending`

**Decision**: `feature.transactions.domain` gains

```kotlin
interface OwnSpending {
    /** The person's own expenses dated in [from]..[to] as the phone shows them (waiting changes applied), plus every waiting touch. */
    fun observe(from: LocalDate, to: LocalDate): Flow<SpendingSnapshot>
}
data class OwnExpense(val id: String, val date: LocalDate, val amountMinor: Long, val currency: String, val categoryId: String?, val waiting: Boolean)
data class SpendingSnapshot(val expenses: List<OwnExpense>, val syncedExpenses: List<OwnExpense>, val waitingTouches: List<SpendingKey>)
data class SpendingKey(val date: LocalDate, val currency: String, val categoryId: String?)
```

implemented by `TransactionOwnSpending` (bound in the transactions module) from one Room snapshot
— rows dated in the range **or** with a queued change, plus phone-only pending confirmations —
combined with `engine.observeAll("transaction")` (the `TransactionPostings` snapshot pattern, so
the queue and rows never disagree mid-update). Rules (ADR-0019, FR-012):

1. effective values = the queued payload over the stored row (a queued delete removes it); refused and clashing changes overlay too, as the transaction list shows them;
2. kind `expense` only; live only (`deleted_at` null; rows kept only for a balance carry excluded);
3. author = `created_by`; for a phone-only pending confirmation, `RecurringLinks.ruleAuthorOf(ruleId)` (new defaulted member, C7 binds it; null → the confirming person);
4. author == the signed-in person; wallet ∈ wallets on the phone where the person is a member (`WalletsService.observeList()`, any role, archived included);
5. `expenses` = effective; `syncedExpenses` = stored rows only (no overlay, no pending confirmations); `waitingTouches` = for every queued change and pending confirmation, the (date, currency, category) of its stored side and of its effective side when either is an own expense.

**Rationale**: P-10.4 (budgets never reads transaction tables); one source of truth for overlay
rules, already proven by the list and balance projection. The three lists are what the selection
rule (R-06) needs. Range-restricted reads keep 20,000 rows cheap; no new index (the existing
`account_id` index plus a date predicate measures well under the 1 s budget on host, checked by a
performance test).

**Alternatives**: a SQL `SUM` per budget (rejected: overlays and author rules live in Kotlin; would
duplicate them in SQL); computing in `feature.budgets.data` over `TransactionDao` (rejected: P-10.4).

## R-06 Which figure is shown: the phone's, unless nothing changed since the server's

**Decision** (`ProgressSelection`, pure):

- At each progress read the phone stores, per budget, the server's figures and period **and the phone's synced-only figures** (`syncedExpenses` summed with the same rules) at that moment.
- Shown = server figures **iff** (a) the stored period equals the current period, (b) no waiting touch matches the budget (period, currency, category or any for all expenses), (c) the phone's synced-only figures now equal those stored with the server's, and (d) the stored limit equals the budget's effective limit. Otherwise shown = the phone's figures (with waiting changes).
- "Includes changes waiting to sync" shows when (b) fails.
- Remaining, over limit, percentage and projection are always re-derived on the phone from the shown spent and the effective limit (R-07), so a limit edit is reflected at once.

**Rationale**: the server's figure is the authority when it can still be true, and covers rows the
phone has not received; any local change makes the phone's own recomputation the fresher figure.
Comparing **synced-only** figures prevents the "jump back" when a waiting expense is accepted
before the next progress read (the synced-only sum grows, so the phone's figure stays shown until
the next read re-anchors). This is the C8 brief's "replace it with the server figure after pull"
without the balance-carry machinery of spec 008.

**Alternatives**: always show the phone's figure (rejected: hides spending the phone has not
received, e.g. a first sync in progress); always show the server's plus a waiting delta (rejected:
needs a carry like spec 008's to avoid flicker; more state, same result).

## R-07 Arithmetic (P-03, ADR-0019 rule 4)

**Decision** `BudgetMath` (pure, `Long` only): `remaining = limit − spent`; `overLimit = spent > limit`;
percentage via the existing `budgetPercent` (half up) — the component already uses it; projection
for the current period = `ceilDiv(spent × daysInPeriod, daysElapsed)`, null when `daysElapsed` is
null or 0, when `spent > Long.MAX_VALUE / daysInPeriod` (no overflow; common Kotlin has no
`multiplyExact`), or when the result exceeds 2^53 − 1. Sums of spent use plain `Long` addition
(any realistic total is far below 2^63; the calculator caps at `Long.MAX_VALUE` defensively). The UI omits "On pace" when spent is 0.

**Rationale**: matches `Money::allocate` rounding up (backend summary default 11) and the captured
example (350,000 × 30 ÷ 8 = 1,312,500).

## R-08 Budget Period on the phone

**Decision** `BudgetPeriods.current(today, startDay, appliesFrom, stored)` (pure):
1. `appliesFrom == null || appliesFrom ≤ today` → start = the latest date ≤ today whose day of month is `startDay` (this month if `today.day ≥ startDay`, else last month — `startDay ≤ 28` exists in every month); end = start + 1 month − 1 day; `daysInPeriod = end − start + 1`; `daysElapsed = today − start + 1`.
2. `appliesFrom > today` (pending change) → the stored server period (any budget's `budget_progress` row) whose `start ≤ today ≤ end`; none → `Unknown` (FR-010: "Progress appears after sync").
3. "Today" = `Clock.now()` in the profile's time zone (`ProfileSyncer.view.server.timezoneId`), else `ClientInfo.timezoneId()`; a `DayTicker` (minute ticks, as `TodayTicker` in spec 010, own copy in `budgets.domain` to avoid a budgets → recurring dependency) re-derives at midnight.
4. Only the server's profile copy is read; a pending profile edit is ignored (FR-010).

**Rationale**: reproduces backend spec 015 US5 scenarios; the start day history is server-only, so
a period begun before `appliesFrom` can only come from the server (contract gap 1).

## R-09 Feed applier and progress reads

**Decision**: `BudgetFeedApplier` (`ScopeKind.User`, row type `budget`): version guard (older never
replaces newer), tombstone removes unless a change waits, `sweep` removes synced own budgets not seen
(never placeholders or rows with waiting changes). A removed budget's `budget_progress` row goes
with it (same DAO transaction).

`BudgetsService.refresh(force)`: `runner.refresh(ReadTarget.Everything, force)` (cheap since spec 011
heads), then walk `GET /budget-progress?limit=200` (no `date`) to the end, storing each page's rows
with the synced-only figures computed from one `OwnSpending` snapshot taken **after** the feed reads.
`INVALID_CURSOR` restarts the walk once. A completed walk replaces the stored rows of every non-archived budget (rows of ids it did not return are deleted, archived budgets' rows kept); a row whose budget is not on the phone yet is stored and shown once the budget arrives (removed with the next completed walk if it never does). Offline/timeout/5xx: keep stored rows, report Offline/Failed.
A 60 s floor (forced by pull). `refreshOne(id)` reads `GET /budgets/{id}/progress` for a budget with
`version > 0`; 404 ignored. Triggers: `BudgetsSyncCoordinator` (sign-in, foreground), budgets screen
open, pull to refresh, editor open (one budget). Progress of a period other than the phone's current
one is stored but never shown (R-06 a).

**Rationale**: P-04.1 (render from Room); the walk's cursor is bound to its period (backend default
17), so a restart covers a walk crossing midnight.

## R-10 Category in use and duplicates

**Decision**: `BudgetCategoryUses : PendingCategoryUses` = the category ids of every budget row on
the phone plus every waiting budget payload (FR-021). The union in categories gains a third source
(`AppGraphTest` assertion updated). The editor's "Already budgeted" set = active (non-archived)
budgets on the phone with the chosen currency, keyed by category id or `ALL` (FR-004).

**Rationale**: the server keeps a category in use while any budget names it, even deleted; the phone
errs on the same side. C5 handles a `CATEGORY_IN_USE` refusal with "Archive instead".

## R-11 Home: `BudgetSummary` hook

**Decision**: `feature.auth.domain.BudgetSummary { fun observeTop(limit: Int): Flow<List<BudgetGlimpse>> }`
with `BudgetGlimpse(id, categoryName: String?, spentMinor, limitMinor, currency, includesWaiting)`
(null name = "All expenses"; resolved by budgets with C5 displays in the app language) and
`NoBudgetSummary`. `HomeViewModel` gains `budgets: Flow<List<BudgetGlimpse>>`, intents
`BudgetsClicked`, `BudgetClicked(id)`, effects `NavigateBudgets`, `OpenBudget(id)`. Order: highest
percentage first, then oldest (`created_at`, id).

**Rationale**: the spec 010 `DueSummary` precedent (P-10.4).

## R-12 Screens (P-16) and navigation

**Decision**: two screens, each with Contract, ViewModel, Route, Screen:
- `ui/list` — `BudgetsViewModel`: active and archived lists, period line, pending start-day line, "Updated …", pull to refresh, empty state; intents `Refresh`, `Add`, `Open(id)`.
- `ui/editor` — `BudgetEditorViewModel`: create (category, currency, limit) and edit/detail (progress at top, limit editable, category/currency/period read-only, archive, delete, clash/refusal choices). Saving closes the editor; archive and delete ask first.

Keys `BudgetsKey`, `BudgetEditorKey(budgetId: String? = null)`; `AppNavHost` wiring from Home (row,
card, "See all") and from the budgets screen.

## R-13 Design system (P-07)

**Decision**: reuse `BudgetProgress` (unchanged signature) for every progress row. One additive
change: `CategoryChoiceItem.enabled: Boolean = true` — a disabled row shows its caption ("Already
budgeted"), is not selectable and announces as disabled; catalogue entry plus goldens (EN LTR, AR
RTL, 2.0). "On pace for …", "Also spent in …", "Includes changes waiting to sync" and the period
lines are `Text` with type and colour tokens under the component; status marks reuse the existing
sync status line used by category and transaction rows. A budget catalogue section shows the four
states (under, near, over, waiting) for review.

**Rationale**: no new visual vocabulary; the board's `BudgetProgress` already encodes the neutral
states with written percentages (P-08.3).

## R-14 Copy and words (P-01, P-06)

**Decision**: about 70 strings EN/AR: glossary "Budget" / "ميزانية", "Budgets" / "الميزانيات",
"Budget period" / "فترة الميزانية", "All expenses" / "كل المصروفات", "Limit" / "الحد", "On pace for
%s" / "بهذا المعدل ستصل إلى %s", "Also spent in %s (not counted)" / "صُرف أيضًا بعملة %s (غير محسوب)",
"Already budgeted" / "له ميزانية بالفعل", "Includes changes waiting to sync" / "يشمل تغييرات في
انتظار المزامنة", "Progress appears after sync" / "يظهر التقدم بعد المزامنة", "From %1$s, periods
start on day %2$d" / "من %1$s تبدأ الفترات في اليوم %2$d", archive/delete confirmations. A string
test rejects "envelope" and "plan" in budget strings. Dates via the existing date formatter with
Western digits. Owner review.

## R-15 Unknown values (P-02.3)

**Decision**: DTOs ignore unknown members; `period_type` other than `monthly` → the budget is shown
with its limit, read-only, with "Shown in full on newer versions of the app" and no figures: the
phone cannot derive its period, so neither its own figures nor stored server figures (R-06 a) apply.
A progress row for such a budget is stored but never shown.

## R-16 Testing (owner policy: no device)

**Decision**: host tests only — calculators and periods (table-driven), `OwnSpending` over a real
in-memory Room + fake engine, handler/applier, `MockEngine` contract tests from the `budgets.yaml`
examples, reducers and stores (P-16.9), Roborazzi for the changed choice row and the budget
catalogue section and screens, migration 8 → 9, sign-out wipe, `AppGraphTest`, layering test, log
capture. Live contract QA against the local backend (curl with the app's headers; throwaway users
registered through the API; an owner and a partner in a shared wallet), every answer captured into
`CapturedBudgetResponses.kt` and replayed through the real API client, engine, handler, applier,
`OwnSpending` and `ProgressSelection`, comparing the phone's figures with the server's (SC-003,
SC-004). Maestro flows in `.maestro/012-budgets/` written and marked "deferred: E2E on owner's
device".

## R-17 Logging (P-09.2, P-13)

**Decision**: log lines carry record type, id, outcome, code, request id and counts
("budget progress: 4 rows, 1 page"); never amounts, limits, spending, projections or payloads.
`BudgetEntity`, `BudgetProgressEntity`, payloads, DTOs and `OwnExpense` redact `toString`.
