# Contract: the Google Play port (`PlayBillingClient`)

The only place the app touches Google Play. Plain Kotlin types (data-model §1); no Play Billing
Library type crosses it. Lives in `feature.billing.data.play` (P-10.6: data imports no domain).

```kotlin
interface PlayBillingClient {
    val platformSupported: Boolean
    suspend fun connect(): PlayConnection
    suspend fun productDetails(productIds: Set<String>): PlayResult<List<PlayProduct>>
    suspend fun purchases(): PlayResult<List<PlayPurchase>>
    val purchaseUpdates: Flow<PlayPurchaseUpdate>
    suspend fun launchPurchase(request: PlayLaunchRequest): PlayLaunchResult   // US6 only
}
```

## Rules every implementation follows

1. **No acknowledge, no consume.** The port has no such operation; the Google adapter MUST NOT call `acknowledgePurchase` or `consumeAsync` (source-scan test). The server acknowledges (backend spec 024).
2. `connect()` is idempotent and cheap when already connected; every other call connects first and answers `Failed(BillingUnavailable)` / `Unavailable` rather than throwing.
3. `purchases()` returns subscriptions and one-time products Google Play reports as owned now, pending and purchased, in any order; `UNSPECIFIED_STATE` purchases are dropped.
4. `purchaseUpdates` is hot, buffered (≥ 16), never completes; `USER_CANCELED` → `Cancelled`; `OK` with purchases → `Purchased(list)`; other codes → `Failed(code)`.
5. `launchPurchase` needs a resumed Activity, found by the adapter (an `ActivityLifecycleCallbacks` holder registered in `:androidApp`); none → `Failed(Unknown)`. It always sets the obfuscated **account** id from the request and never an obfuscated profile id.
6. Tokens are `PurchaseToken` values (redacted `toString`); nothing in an implementation logs a token, an order id, a reference or a price.
7. Not called while signed out (callers check first); the port itself keeps no per-account state.

## Implementations

| Class | Source set | `platformSupported` | Behaviour |
|---|---|---|---|
| `UnavailablePlayBillingClient` | commonMain | `false` on iOS, `true` on Android pre-G1 | `connect()` → `Unavailable(NotSupported / NotWired)`; lists empty; no updates |
| `FakePlayBillingClient` | commonTest | configurable | scripted purchases, products, connection, launch results; records every call; can emit updates; asserts no acknowledge exists |
| `ScriptedPlayBillingClient` | devtools (dev flavour) | `true` | in-memory purchases edited from the Dev tools screen; tokens match the backend's `BILLING_PLAY_FAKE_SCRIPT` |
| `GooglePlayBillingClient` | androidMain, **G1 only** | `true` | Play Billing Library wrapper (research R-11) |

## Response-code mapping (G1, pure function `playErrorOf(responseCode)`)

| Play `BillingResponseCode` | Port |
|---|---|
| `OK` | success |
| `USER_CANCELED` | `Cancelled` (updates) |
| `ITEM_ALREADY_OWNED` | `ItemAlreadyOwned` |
| `BILLING_UNAVAILABLE` | `BillingUnavailable` |
| `ITEM_UNAVAILABLE` | `ItemUnavailable` |
| `SERVICE_DISCONNECTED`, `SERVICE_UNAVAILABLE` | `ServiceUnavailable` |
| `NETWORK_ERROR` | `Network` |
| `DEVELOPER_ERROR` | `DeveloperError` |
| `FEATURE_NOT_SUPPORTED` | `FeatureNotSupported` |
| `ERROR`, anything else | `Unknown` |
