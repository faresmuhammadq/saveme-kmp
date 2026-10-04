# Contract: Budgets domain API and hooks

Internal Kotlin contracts between packages (P-10). Signatures are indicative; names are binding for
tasks and tests.

## `feature.budgets.domain.BudgetsService`

```kotlin
interface BudgetsService {
    fun observeOverview(): Flow<BudgetsOverview>                    // FR-016, FR-009/010, R-06
    fun observeBudget(id: String): Flow<BudgetView?>                // editor/detail
    fun observeChoices(currency: String, currentCategoryId: String?): Flow<BudgetChoices> // FR-003, FR-004
    suspend fun create(draft: BudgetDraft): CreateResult           // Created(id) | NotVerified | Invalid(errors) | AlreadyBudgeted
    suspend fun saveLimit(id: String, limitMinor: Long, baseVersion: Long): SaveResult
    suspend fun archive(id: String): SaveResult
    suspend fun delete(id: String): SaveResult
    suspend fun keepMine(id: String); suspend fun useTheirs(id: String)
    suspend fun tryAgain(id: String); suspend fun discard(id: String)
    suspend fun refresh(force: Boolean = false): RefreshOutcome    // R-09: feeds, then progress walk; 60 s floor
    suspend fun refreshOne(id: String): RefreshOutcome             // R-09: per-budget progress; 60 s floor per id
}
```

Pure parts (unit-tested without coroutines): `BudgetPeriods` (R-08), `BudgetMath` (R-07),
`SpendingTotals` (sums a `SpendingSnapshot` per budget: spent, other currencies, includesWaiting —
FR-012), `ProgressSelection` (R-06), `BudgetValidation` (limit range and fraction digits, FR-003),
`DayTicker`.

## `feature.transactions.domain.OwnSpending` (new, research R-05)

```kotlin
interface OwnSpending { fun observe(from: LocalDate, to: LocalDate): Flow<SpendingSnapshot> }
data class OwnExpense(val id: String, val date: LocalDate, val amountMinor: Long, val currency: String, val categoryId: String?, val waiting: Boolean)
data class SpendingKey(val date: LocalDate, val currency: String, val categoryId: String?)
data class SpendingSnapshot(val expenses: List<OwnExpense>, val syncedExpenses: List<OwnExpense>, val waitingTouches: List<SpendingKey>)
```

Bound in `transactionsModule` as `TransactionOwnSpending`. Rules in research R-05; budgets never
reads transaction tables.

## `feature.transactions.domain.RecurringLinks` (changed, default-off)

`suspend fun ruleAuthorOf(ruleId: String): String? = null` — the rule's `created_by` when the rule
is on the phone. `RecurringLinksImpl` (C7) implements it from `recurring_rule.created_by`.

## `feature.auth.domain.BudgetSummary` (new hook, research R-11)

```kotlin
interface BudgetSummary { fun observeTop(limit: Int): Flow<List<BudgetGlimpse>> }
data class BudgetGlimpse(val id: String, val categoryName: String?, val spentMinor: Long, val limitMinor: Long, val currency: String, val includesWaiting: Boolean)
object NoBudgetSummary : BudgetSummary
```

`authModule` binds `NoBudgetSummary`; `budgetsModule` rebinds `BudgetsHomeSummary`. `toString`
of `BudgetGlimpse` redacts amounts.

## `feature.categories.domain.PendingCategoryUses` (unchanged interface)

`BudgetCategoryUses` is one more source of the union (transactions, recurring, budgets).

## `core.sync` (unchanged)

`BudgetSyncHandler` (`SyncHandler`, record type `budget`) and `BudgetFeedApplier` (`FeedApplier`,
`ScopeKind.User`, row type `budget`) are registered by `budgetsModule`; the engine is resolved
lazily wherever a budgets class needs it (`AppGraphTest` lesson, spec 006).

## Layering (checked by `BudgetsLayeringTest`)

- `feature.budgets.data` imports no `domain`/`ui` and no other feature.
- `feature.budgets.domain` imports only `core`, its own `data`, and `domain` of categories, transactions, wallets, account, auth.
- `feature.budgets.ui` imports its own `domain`, `designsystem`, `core.presentation`.
- No package outside `feature.budgets` and `navigation`/DI imports `feature.budgets`.
