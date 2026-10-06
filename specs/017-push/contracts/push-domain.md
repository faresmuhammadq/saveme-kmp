# Contract: Push domain, the Firebase seam and the payload

What the rest of the app and the platform adapters may rely on. Signatures are indicative; names
are fixed (tasks and tests use them).

## 1. The seam (FR-045, FR-046)

```kotlin
// feature/notifications/domain/PushTokenProvider.kt
interface PushTokenProvider {
    val isAvailable: Boolean
    suspend fun currentToken(): String?
    suspend fun discard()
}

// feature/notifications/domain/PushReceiver.kt — the only entry point a platform push service calls
class PushReceiver(...) {
    fun onNewToken()
    suspend fun onMessage(data: Map<String, String>, title: String?, body: String?)
}

// feature/notifications/domain/NotificationDisplay.kt
interface NotificationDisplay {
    suspend fun ensureChannels(labels: ChannelLabels)
    fun post(notification: PostedNotification): Boolean   // false when the phone does not allow it
    fun cancelAll()
}

// feature/notifications/domain/NotificationPermissionState.kt
interface NotificationPermissionState {
    fun notificationsAllowed(): Boolean
    fun canAskAtRuntime(): Boolean
}

// feature/notifications/domain/PushRegistrationScheduler.kt
interface PushRegistrationScheduler {
    fun request(trigger: ReconcileTrigger)
    fun cancel()
}
```

| Binding | Dev (`isDebugTools`) | Staging / prod (until gated phase) | After gated phase | iOS | Host tests |
|---|---|---|---|---|---|
| `PushTokenProvider` | `DevFakePushTokenProvider` | `UnavailablePushTokenProvider` | `FcmPushTokenProvider` (`:androidApp`) | `UnavailablePushTokenProvider` | `FakePushTokenProvider` |
| `NotificationDisplay` | `AndroidNotificationDisplay` | same | same | `NoopNotificationDisplay` | `FakeNotificationDisplay` / Robolectric |
| `NotificationPermissionState` | `AndroidNotificationPermissionState` | same | same | always not allowed | fake |
| `PushRegistrationScheduler` | `WorkManagerPushRegistrationScheduler` | same (job no-ops without a provider) | same | `NoopPushRegistrationScheduler` | `InlinePushRegistrationScheduler` |

The provider binding moves from `androidPlatformModule` to a parameter of it
(`pushTokenProvider: (Scope) -> PushTokenProvider`), so `:androidApp` chooses it per flavor and the
gated phase changes only `:androidApp`.

**Firebase may be imported only in** `androidApp/src/main/kotlin/com/fares/saveme/push/` (gated
phase). A layering test fails on any `com.google.firebase` import elsewhere.

## 2. Payload (`PushPayloadParser`, FR-030)

Input: the data map (strings). Required: `schema == "1"`, `type`, `notification_id`. Ids must
match the UUID text form (8-4-4-4-12 hex, either case).

| `type` | Case | Needed ids | Collapse key |
|---|---|---|---|
| `occurrence_due` | `OccurrenceDue` | `wallet_id`, `occurrence_id` | `occurrence:{occurrence_id}` |
| `occurrence_auto_confirm_refused` | `OccurrenceAutoConfirmRefused` | `wallet_id`, `occurrence_id` | `occurrence:{occurrence_id}` |
| `occurrences_due_summary` | `OccurrencesDueSummary` | — | `occurrences_due` |
| `budget_exceeded` | `BudgetExceeded` | `budget_id` | `budget:{budget_id}` |
| `invitation_received` | `InvitationReceived` | (`invitation_id` optional for routing) | `invitation:{invitation_id}` |
| `member_role_changed` | `MemberRoleChanged` | `wallet_id` (`role` optional, unused) | `wallet:{wallet_id}` |
| `member_removed` | `MemberRemoved` | (`wallet_id` optional for routing) | `wallet:{wallet_id}` |
| `wallet_ownership_received` | `WalletOwnershipReceived` | `wallet_id` | `wallet:{wallet_id}` |
| `subscription_ending` | `SubscriptionEnding` | — | `subscription` |
| `subscription_lapsed` | `SubscriptionLapsed` | — | `subscription` |
| `export_ready` | `ExportReady` | `export_id` | `export:{export_id}` |
| `deletion_reminder` | `DeletionReminder` | — | `deletion` |
| anything else, or `schema != "1"` | `Unknown` | — | `other:{notification_id}` |
| known type, needed id missing/invalid | `Malformed(type)` | — | `other:{notification_id}` |

`category` is taken from the data when it is one of the six; otherwise from the type's row in the
backend table (`occurrence_*` → `recurring_due`, `budget_exceeded` → `budget_exceeded`,
`invitation_received`/`member_*`/`wallet_ownership_received` → `sharing`, `subscription_*` →
`billing`, `export_ready` → `export_ready`, `deletion_reminder` → `deletion_reminder`).

## 3. Routing (`PushRouter`, FR-031)

| Case | `PushRead` | `PushTarget` |
|---|---|---|
| `OccurrenceDue`, `OccurrenceAutoConfirmRefused` | `Wallet(walletId)` | `Occurrence(occurrenceId)` |
| `OccurrencesDueSummary` | `Everything` | `RecurringAll` |
| `BudgetExceeded` | `User` | `Budget(budgetId)` |
| `InvitationReceived` | `None` | `InvitationNotice` |
| `MemberRoleChanged`, `WalletOwnershipReceived` | `Wallet(walletId)` | `Wallet(walletId)` |
| `MemberRemoved` | `Everything` | `Wallets` |
| `SubscriptionEnding`, `SubscriptionLapsed` | `Entitlements` | `Plan` |
| `ExportReady` | `None` | `Export(exportId)` |
| `DeletionReminder` | `Account` | `AccountDeletion` |
| `Malformed(occurrence_*)` | `None` | `RecurringAll` |
| `Malformed(budget_exceeded)` | `None` | `Budgets` |
| `Malformed(member_role_changed | wallet_ownership_received)` | `None` | `Wallets` |
| `Malformed(export_ready)` | `None` | `Exports` |
| `Unknown` | `None` | `Home` |

`PushRead` → sync: `User` → `ReadTarget.User`, `Wallet(id)` → `ReadTarget.Wallet(id)`,
`Everything` → `ReadTarget.Everything` (all `force = true`); `Entitlements` →
`SessionManager.refreshEntitlements()`; `Account` → `AccountRefresher.refresh()`.

## 4. Services

| Service | Responsibility | Collaborators |
|---|---|---|
| `PushRegistrationService.reconcile(trigger)` | research R-04 | `NotificationsApi`, `PushRegistrationStore`, `SecureStore` (session), `SessionManager.state` (user), provider, permission state, clock |
| `PushCoordinator` | triggers (R-06) | `SessionManager`, scheduler |
| `PushSignOutParticipant : SignOutParticipant` | bounded `DELETE` (R-07) | API, store, `SecureStore` |
| `PushDataCleaner : UserDataCleaner` | cancel all, clear records, cancel jobs, discard token (R-07) | display, stores, scheduler, provider, app scope |
| `PushReceiver` | foreground messages (R-09), new token | parser, router, display, `ShownNotificationsStore`, `SyncRunner`, `SessionManager`, labels provider |
| `NotificationOpenService.open(route)` | tap reads (R-10, R-11) | `SyncRunner`, `SessionManager`, `AccountRefresher`, `RecordPresence` |
| `NotificationPreferencesService` | load/save (R-12) | `NotificationsApi`, profile time zone (account domain) |
| `NotificationPromptGate` | explanation sheet once (R-14) | provider, key-value flag |

Rules:

1. Nothing in `domain` imports Ktor, Compose, Room or Android (P-10.3); nothing in `data` imports `domain` or `ui` (P-10.6); `core` (including `core.sync`) imports nothing of `notifications` (P-10.7).
2. `NotificationOpenService` never calls anything that writes (no confirm, skip, join, decline, save); its fakes record calls and the test asserts the allowed set only.
3. Every holder of a token, payload or text prints redacted (`toString` = type/outcome only).
4. Koin: `SignOutUseCase` resolves participants lazily (`{ getAll<SignOutParticipant>() }`); the notifications services resolve `SyncRunner` lazily (`{ get<SyncRunner>() }`) like the other features' coordinators (spec 006 lesson: a resolution cycle overflows the stack at start).
