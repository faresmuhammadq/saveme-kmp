# Contract: Sharing domain API and hooks

Package `com.fares.saveme.feature.sharing.domain` unless noted. Stores call only these (P-16.6).

## SharingService

```kotlin
interface SharingService {
    // Owner side
    fun observeInvitations(walletId: String): Flow<List<InvitationView>>     // pending, oldest first; hidden ones dropped
    fun observeMembers(walletId: String): Flow<List<MemberView>>             // owner first, then joining order
    fun observeInviteCheck(walletId: String): Flow<InviteCheck>              // R-06, re-emits on entitlements/rows
    fun observePlanNote(walletId: String): Flow<Int?>                        // plan's max people when the wallet exceeds it (FR-028), else null
    suspend fun invite(walletId: String, draft: InvitationDraft): InviteResult
        // Queued | NotVerified | SharingNotIncluded | Full(max) | Invalid(field, problem) | OwnAddress | SupersedeNeeded
    suspend fun inviteConfirmed(walletId: String, draft: InvitationDraft): InviteResult  // after the supersede confirmation
    suspend fun withdraw(invitationId: String): WithdrawResult               // Withdrawn | NotOwner | Gone
    suspend fun replaceLink(invitationId: String): InviteResult              // withdraw + new link invitation, same role
    fun shareTextFor(invitationId: String): String?                          // null when no link held; never logged
    suspend fun discard(invitationId: String)                                // a refused create
    suspend fun tryAgain(invitationId: String)
    suspend fun changeRole(walletId: String, userId: String, role: WalletRole): MemberActionResult
    suspend fun remove(walletId: String, userId: String): MemberActionResult
        // Done | Offline | Changed | OnlyOwner | NotEntitled(feature) | NotVerified | Gone | OwnerRole | Busy | Failed(requestId)
    suspend fun leaveCheck(walletId: String): LeaveCheck                     // uploads first; Ready(waitingCount) | Owner | Offline
    suspend fun leave(walletId: String): LeaveResult                         // Left(walletName) | Offline | OwnerCannotLeave | Changed | Failed

    // Invitee side
    suspend fun preview(token: String): PreviewResult
    suspend fun join(token: String): JoinResult                              // Joined(walletId) | OwnerPlanFull | Unavailable | InvalidLink | TooMany(minutes) | Offline | Failed
    suspend fun decline(token: String): DeclineResult

    // Activity
    suspend fun activityPage(walletId: String, cursor: String?): ActivityPage // Loaded(items, nextCursor) | Offline | Gone | Failed(requestId)
}
```

Rules: owner checks read `WalletMemberships.ownRole`; member actions are refused while another
for the same wallet runs (`MemberActionResult.Busy`); `invite` validates (R-07) then gates (R-06)
then inserts the placeholder and enqueues; nothing here logs a token, link or address.

## Memory holders (sharing domain; each a `UserDataCleaner`)

- `HeldInvitation { val held: StateFlow<InvitationLink.Valid?>; fun hold(link); fun clear() }`
- `ShareLinks { fun offer(invitationId: String, url: String); fun get(invitationId: String): String?; fun forget(invitationId: String) }`
- `PlanDemotionNotes { fun record(walletId: String, limitedByPlan: Boolean); fun observe(walletId: String): Flow<Boolean> }`

## Pure parts

- `InvitationLinkParser(scheme, linkHost, linkPort).parse(raw): InvitationLink?` → `Valid(token)` | `Malformed` | null (R-08).
- `InviteGate.check(verified, sharingEnabled, quota, peopleCount): InviteCheck` (R-06).
- `InviteValidation.validate(email, ownEmail): EmailProblem?` (R-07).
- `ActivityMapping.map(entry, names): ActivityItem?` (null = skipped) (R-18).
- `ShareText.build(appName, url, template)` (FR-006).

## New in wallets domain (`feature.wallets.domain`)

```kotlin
interface WalletMemberships {
    fun observe(walletId: String): Flow<List<MemberRecord>>   // with version; owner first
    suspend fun ownRole(walletId: String): WalletRole?
    suspend fun ownMembershipVersion(walletId: String): Long?
    suspend fun store(record: MemberRecord)                  // version guard; tombstone deletes; own row sets wallet.my_role
    suspend fun leftWallet(walletId: String)                 // accessLost
}
data class MemberRecord(val id: String, val walletId: String, val userId: String, val displayName: String?,
                        val role: String, val version: Long, val createdAt: String, val deleted: Boolean)

interface SharingGlance {                                    // default: flowOf(0) / flowOf(false)
    fun observePendingCount(walletId: String): Flow<Int>
    fun observePlanLimited(walletId: String): Flow<Boolean>
}

// WalletsService additions
fun observeJoining(id: String): Flow<Boolean>                // own member row present, wallet row absent
```

## New in auth domain (`feature.auth.domain`)

```kotlin
interface HeldInvitationGlimpse { fun observeHeld(): Flow<Boolean> }   // default flowOf(false)
```

## New in core

- `core.sync.Refusal.feature: String?`; `SyncEngine.pendingIn(scope: SyncScope): List<PendingChange>`.
- `core.network.ProblemDetails.feature`, `ApiClient.callWithMeta`.

## Wallet deletion participant

`InvitationsWalletCleanup : WalletDeletionParticipant` — deletes the wallet's invitation rows,
forgets their held links, discards their queued changes (launched job).

## Koin (SharingModule)

`InvitationSyncHandler` bind `SyncHandler` (engine resolved lazily, AppGraphTest), `InvitationFeedApplier`
bind `FeedApplier`, `InvitationsWalletCleanup` bind `WalletDeletionParticipant`, `DefaultSharingGlance`
bind `SharingGlance`, `HeldInvitationGlimpse` impl, holders bind `UserDataCleaner`, `LinkSharer`
from platform modules, four ViewModels.
