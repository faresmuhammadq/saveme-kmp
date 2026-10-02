# Data Model: Transactions (client)

Paths are relative to `shared/src/commonMain/kotlin/com/fares/saveme/`. Wire shapes are defined
in `saveme-backend/docs/openapi/paths/transactions.yaml`; this file names the client's mirror.

## 1. Tables (Room, schema v5, `feature.transactions.data.local`)

### `transactions` (new) — every transaction of the wallets on the phone

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUIDv7; client-made for this phone's creates |
| `account_id` | TEXT | the signed-in person (rows never cross accounts) |
| `wallet_id` | TEXT | the scope; a transfer's source |
| `counter_wallet_id` | TEXT? | a transfer's destination; null otherwise |
| `kind` | TEXT | `income` / `expense` / `transfer`, or an unknown value kept as received |
| `date` | TEXT | `YYYY-MM-DD` |
| `amount_minor` | INTEGER | positive |
| `currency` | TEXT | ISO 4217 |
| `category_id` | TEXT? | null for a transfer |
| `description` | TEXT? | note, ≤ 100 |
| `memo` | TEXT? | details, ≤ 1000 |
| `counterparty` | TEXT? | person, ≤ 100 |
| `cleared` | INTEGER | boolean |
| `created_by` | TEXT | user id; the signed-in person for a placeholder |
| `recurring_rule_id` | TEXT? | read-only link |
| `occurrence_id` | TEXT? | read-only link |
| `version` | INTEGER | 0 for a placeholder never sent |
| `change_seq` | INTEGER | stored, not interpreted (wave 1) |
| `created_at` / `updated_at` | TEXT | UTC instants from the server; the phone's clock for a placeholder |
| `deleted_at` | TEXT? | set only on a confirmed deletion kept for its carry (never listed) |
| `refreshed_at` | TEXT | when this phone last stored the server copy |
| `carry_source_minor` | INTEGER | balance effect on `wallet_id` confirmed by the server but not yet in a wallet read (default 0) |
| `carry_counter_minor` | INTEGER | the same for `counter_wallet_id` (default 0) |
| `carry_confirmed_at` | TEXT? | when the last carried change was confirmed (UTC) |

Indexes: `(wallet_id, date, created_at)`, `(counter_wallet_id, date, created_at)`, `(account_id)`.
`toString()` prints id and version only (P-09.2).

### `sync_scope` (existing)

Row `transactions:<walletId>`: when that wallet's list was last read completely ("Updated …").

### `pending_change` (existing, spec 004)

Record type `transaction`, scope `wallet:<wallet_id>` (the source), payload `TransactionPayload`
(§2). New DAO query `observeByType(type)` behind `SyncEngine.observeAll(type)` (research R-21).

### Migration `MIGRATION_4_5`

`CREATE TABLE transactions (…)` plus the three indexes; nothing else changes. Registered in
`buildAppDatabase`; `5.json` exported; tests: `Migration4To5Test` (data in every v4 table kept,
new table empty), the existing migration tests register `MIGRATION_4_5`, `SignOutWipeTest` covers
the table.

## 2. Wire DTOs and queue payload (`feature.transactions.data.dto`)

- `TransactionDto` — every `Transaction` member (`id`, `wallet_id`, `kind`, `date`, `amount_minor`, `currency`, `category_id`, `counter_wallet_id`, `description`, `memo`, `counterparty`, `cleared`, `created_by`, `recurring_rule_id`, `occurrence_id`, `version`, `change_seq`, `created_at`, `updated_at`, `deleted_at`); unknown members ignored (`AppJson`).
- `TransactionWriteRequest` — exactly `kind, date, amount_minor, currency, category_id, counter_wallet_id, description, memo, counterparty, cleared, version`, nulls encoded explicitly.
- `TransactionRestoreRequest` — exactly `{deleted_at: null, version}` (the null encoded).
- `VersionOnlyRequest` — `{version}` (DELETE body).
- `TransactionPayload` (queue, `data`) — `kind, date, amountMinor, currency, categoryId, counterWalletId, description, memo, counterparty, cleared`; the domain maps its draft to it.
- `TransactionFields` — the plain field names `refusalFields` returns (research R-04).
- `BalanceEffect` (`feature.transactions.data`, not a DTO) — the pure effect rule on primitives, used by the handler for the carry and by the domain for `PendingPostings` and day totals.

## 3. Domain (`feature.transactions.domain`)

```kotlin
enum class TransactionKind(val wire: String) { Income, Expense, Transfer, Unknown }

data class Transaction(            // the server copy
    val id: String, val walletId: String, val counterWalletId: String?, val kind: TransactionKind,
    val kindWire: String, val date: LocalDate, val amountMinor: Long, val currency: String,
    val categoryId: String?, val description: String?, val memo: String?, val counterparty: String?,
    val cleared: Boolean, val createdBy: String, val recurringRuleId: String?, val occurrenceId: String?,
    val version: Long, val createdAt: Instant, val updatedAt: Instant,
)

data class TransactionDraft(       // what the person entered
    val walletId: String, val kind: TransactionKind, val date: LocalDate, val amountMinor: Long,
    val currency: String, val categoryId: String?, val counterWalletId: String?,
    val description: String?, val memo: String?, val counterparty: String?, val cleared: Boolean,
)

enum class TransactionField { Wallet, Kind, Amount, Date, Category, Destination, Currency, Note, Details, Person, Cleared }

sealed interface TransactionFieldProblem { /* AmountEmpty, AmountNotPositive, AmountDigits(n), AmountOutOfRange,
    DateOutOfRange, CategoryRequired, DestinationRequired, TooLong(max), Server(code) */ }

enum class RefusalKind { NotVerified, NoLongerAvailable, NoRightHere, NoRightInOtherWallet, CategoryUnusable,
    DestinationUnavailable, CurrencyChanged, CurrenciesDiffer, Invalid, Other }
data class RefusalReason(val kind: RefusalKind, val requestId: String? = null)

sealed interface TransactionSyncState {
    data object None; data object Waiting
    data class Clash(val theirs: Transaction?, val deletedElsewhere: Boolean, val mineIsDeletion: Boolean)
    data class Refused(val reason: RefusalReason, val fields: Map<TransactionField, String>)
}

data class TransactionView(        // one transaction as a screen sees it, from one wallet's side
    val server: Transaction?,      // null for a never-sent create
    val shown: TransactionDraft,   // pending content, else the server copy
    val pendingDelete: Boolean, val syncState: TransactionSyncState,
    val direction: Direction,      // In, Out, None — relative to the viewed wallet
    val counterpart: Counterpart?, // the other wallet of a transfer: Named(name) | Unknown
    val addedBy: AddedBy,          // You | Member(name) | AnotherMember
    val fromRecurringRule: Boolean,
    val canEdit: Boolean, val canDelete: Boolean,
)

data class DayGroup(val date: LocalDate, val netMinor: Long, val items: List<TransactionView>)

data class TransactionList(
    val walletId: String, val currency: String, val days: List<DayGroup>,
    val updatedAt: Instant?, val loaded: Boolean, val canAdd: Boolean,
)

data class TransactionQuery(       // search + filters (FR-027, FR-028)
    val search: String = "", val from: LocalDate? = null, val to: LocalDate? = null,
    val categoryIds: Set<String> = emptySet(), val kinds: Set<TransactionKind> = emptySet(),
)
```

Results: `CreateResult` = `Created(id) | NotVerified | NoWallet | NotAllowed | Invalid(problems)`;
`SaveResult` = `Saved | NotVerified | NoLongerAvailable | NotAllowed | Invalid(problems)`.

Services and hooks (contracts/transactions-domain.md): `TransactionsService`,
`TransactionPostings : PendingPostings`, `TransactionCategoryUses : PendingCategoryUses`,
`TransactionsWalletCleanup : WalletDeletionParticipant`, `BalanceReconciler`,
`TransactionsSyncCoordinator`, `DestinationChoices`, `TransactionFilter` (pure), `DayGrouping`
(pure). The effect rule (a state's effect on a wallet, research R-04/R-09) is `BalanceEffect` in
`feature.transactions.data`, shared by the handler and the domain.

## 4. State transitions of a transaction on the phone

```text
(new) --save--> Placeholder(v0) + Waiting(create) --sent--> Synced(v1) [+carry until wallet read]
Synced --edit--> Synced + Waiting(edit, base v) --sent--> Synced(v+1) [+carry]
Synced --delete--> hidden + Waiting(delete, base v) --sent--> Deleted(row kept with carry) --wallet read--> removed
Placeholder --delete--> removed (nothing sent)
Waiting --409 live--> Clash --keep mine--> Waiting(base = theirs) | --use theirs--> Synced(theirs)
Waiting(edit) --409 tombstone--> Clash(deleted elsewhere) --restore with my change--> Waiting(restore, base = tombstone v)
                                                         --accept the deletion--> Deleted → removed
Waiting(delete) --409 live--> Clash --delete anyway--> Waiting(delete, base = theirs) | --keep--> Synced(theirs)
Waiting --refused--> Refused --edit--> Waiting | --try again (not verified)--> Waiting | --discard--> Synced (or removed if placeholder)
Synced (not listed by a complete read, no queue row) --> removed
Any (wallet removed from the phone, R-19) --> removed, queue row discarded
```

## 5. Screen state (MVI, `feature.transactions.ui`)

### `transactions/` — a wallet's transactions

`TransactionsState(walletId, walletName, currency, balanceMinor, isLiability, days: List<DayUi>, loading, refreshing,
offline, updatedAt, refreshFailed, canAdd, query: TransactionQuery, searchText, filterSheetOpen,
categoryFilterChoices, chips: List<FilterChip>, noMatches)`; `DayUi(label, netMinor, rows: List<RowUi>)`;
`RowUi(id, title, subtitle, amountMinor, moneyKind, status, categoryIcon, categoryColor)`.
Intents: `Opened`, `Foregrounded`, `PullToRefresh`, `SearchChanged(text)`, `FiltersOpened`,
`FilterDateRange(preset|from,to)`, `FilterCategoryToggled(id)`, `FilterKindToggled(kind)`,
`ChipCleared(chip)`, `ClearAll`, `RowClicked(id)`, `AddClicked`, `Back`. Effects:
`OpenEditor(transactionId?, walletId)`, `RequireVerification`, `Back`, `ShowMessage`.

### `editor/` — create, edit and detail

`TransactionEditorState(mode: Create|Edit|ReadOnly, transactionId, walletId, walletChoices,
kind, kindEditable, amountText, currency, date, categoryId, categoryDisplay, categoryChoices,
categorySheetOpen, destinationId, destinationChoices, destinationsEmpty, note, details, person,
cleared, moreOpen, errors: Map<TransactionField, TransactionFieldProblem>, status (sync state
for display), clashLines: List<ClashLine>, addedBy, fromRecurringRule, canEdit, canDelete,
confirmDelete, saving, noWallet)`.
Intents: `Opened`, `WalletChosen`, `KindChosen`, `AmountChanged`, `DateClicked`, `DateChosen`,
`CategoryClicked`, `CategoryChosen`, `SheetDismissed`, `DestinationChosen`, `NoteChanged`,
`DetailsChanged`, `PersonChanged`, `ClearedToggled`, `MoreToggled`, `SaveClicked`,
`DeleteClicked`, `DeleteConfirmed`, `DeleteCancelled`, `KeepMine`, `UseTheirs`,
`RestoreWithMine`, `AcceptDeletion`, `DeleteAnyway`, `KeepTransaction`, `TryAgain`, `Discard`,
`CreateWalletClicked`, `Back`. Effects: `Close`, `CloseWithMessage(gone)`,
`RequireVerification`, `OpenWalletEditor`.

Reducers are pure top-level `reduce(state, change)`; stores take domain lambdas only (P-16).
