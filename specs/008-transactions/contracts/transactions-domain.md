# Contract: Transactions domain API (for C7, C8, C13 and this feature's screens)

Package `com.fares.saveme.feature.transactions.domain`. Other features use only this package
(P-10.4). Visual pieces (`TransactionRow`, `DateHeader`, `CategoryRow`, `CategoryChoiceSheet`,
`AppDatePickerDialog`) are in `designsystem` and take plain values.

```kotlin
interface TransactionsService {
    /** One wallet's live transactions from its side (transfers in included), grouped by day, newest first, filtered by [query]. */
    fun observeList(walletId: String, query: TransactionQuery = TransactionQuery()): Flow<TransactionList>

    /** Null when gone or hidden by a deletion that waits to sync; [fromWalletId] chooses the side for direction. */
    fun observeTransaction(id: String, fromWalletId: String): Flow<TransactionView?>

    /** Wallets the person can record in: active, visible, role Owner or Editor (FR-008). */
    fun observeWritableWallets(): Flow<List<WalletChoice>>

    /** Home's preselection: the last wallet recorded in when still writable, else the first writable. */
    suspend fun defaultWalletId(): String?

    /** FR-010: active, same currency, Owner/Editor, not [sourceWalletId]. */
    fun observeDestinations(sourceWalletId: String): Flow<List<WalletChoice>>

    suspend fun create(draft: TransactionDraft): CreateResult   // Created(id) | NotVerified | NoWallet | NotAllowed | Invalid
    /** [baseVersion] is the version shown when the editor opened; kind and wallets are always the stored ones. */
    suspend fun save(id: String, draft: TransactionDraft, baseVersion: Long): SaveResult
    suspend fun delete(id: String): SaveResult
    suspend fun keepMine(id: String)          // also "Restore with my change" and "Delete anyway"
    suspend fun useTheirs(id: String)         // also "Accept the deletion" and "Keep the transaction"
    suspend fun tryAgain(id: String)
    suspend fun discard(id: String)

    /** Reads every page of the wallet's list; without [force] a read within the last 60 s is skipped. */
    suspend fun refresh(walletId: String, force: Boolean = false): RefreshOutcome
}

/** Bound in place of C4's ZeroPendingPostings (research R-09). */
class TransactionPostings : PendingPostings          // deltaFor(walletId), hasPending(walletId)

/** Bound in place of C5's NoPendingCategoryUses (research R-10). */
class TransactionCategoryUses : PendingCategoryUses  // usedLocally()

/** Registered as a WalletDeletionParticipant (research R-19). */
class TransactionsWalletCleanup : WalletDeletionParticipant
```

Rules for consumers (C7 recurring, C8 budgets, C13 reports):

1. Amounts are positive `Long` minor units; direction comes from `kind` and, for a transfer, the side (`TransactionView.direction`). Never add amounts across currencies.
2. Transfers are excluded from every spending and income total (glossary).
3. A transaction hidden by a waiting deletion is absent from `observeList` and `observeTransaction`; its effect is already out of the wallet's displayed balance.
4. `date` is a calendar `LocalDate`; never convert it through a time zone (P-05).
5. `recurring_rule_id` / `occurrence_id` are read-only; C7 confirms occurrences through its own endpoint, never by writing a transaction with those links (ADR-0018).
