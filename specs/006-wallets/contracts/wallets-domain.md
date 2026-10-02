# Contract: Wallets domain API (for C5, C6, C9 and this feature's screens)

Package `com.fares.saveme.feature.wallets.domain`. Other features use only this package (P-10.4).

```kotlin
interface WalletsService {
    fun observeList(): Flow<WalletList>                     // visible wallets (active, archived), totals, updatedAt
    fun observeWallet(id: String): Flow<WalletView?>        // null when gone or hidden by a waiting deletion
    fun observeMembers(id: String): Flow<List<Member>>
    suspend fun canCreateAnother(): Boolean                 // C3 Entitlements, research R-14
    suspend fun create(draft: WalletDraft): CreateResult    // Created(id) | NotVerified | NotEntitled | Invalid(errors)
    suspend fun save(id: String, draft: WalletDraft, baseVersion: Long): SaveResult
        // Saved | NotVerified | NotOwner | Invalid(errors); baseVersion = the version shown when editing began
    suspend fun archive(id: String): SaveResult
    suspend fun delete(id: String): SaveResult
    suspend fun keepMine(id: String)
    suspend fun useTheirs(id: String)                       // also "Accept the deletion" / "Keep the wallet"
    suspend fun tryAgain(id: String)
    suspend fun discard(id: String)
    suspend fun refresh(force: Boolean = false): RefreshOutcome    // Done | Offline | Failed; 60 s floor unless force
    suspend fun refreshMembers(id: String): RefreshOutcome
    suspend fun transferOwnership(id: String, toUserId: String): TransferResult
        // Done | Offline | NoLongerMember | Changed | NotOwner | Failed(requestId)
}

/** C6 rebinds this so balances include unsent transactions (spec FR-004). C4 binds a zero implementation. */
interface PendingPostings {
    fun deltaFor(walletId: String): Flow<Long>
    fun hasPending(walletId: String): Flow<Boolean>
}

/** C6 (and later C7/C9) register one to drop their local rows when a wallet's deletion is confirmed. */
fun interface WalletDeletionParticipant { suspend fun onWalletDeleted(walletId: String) }
```

Rules for consumers:

1. A wallet is usable for new records only when `observeWallet(id)` emits a non-null view whose
   `server.myRole` is Owner or Editor (C6 decides the editor rules with the backend matrix).
2. A wallet hidden by a waiting deletion is `null` here; C6 hides its transactions the same way.
3. Amounts are `Long` minor units in `WalletView.shown.currency`; never convert across currencies.
4. `WalletView.server.currencyLocked` is the server's answer; C6 adds its own local lock (a
   transaction recorded on the phone) through `PendingPostings.hasPending` until it syncs.
