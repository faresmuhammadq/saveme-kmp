# Data Model: Google Play Billing (client) — spec 018

**No Room change (stays v10), no new key-value record, no syncable entity** (research R-14). Every
type below is in memory; the answers update C3's existing account snapshot and `billing_subscription`
record. Types that hold a secret print redacted (FR-050).

## 1. Port types (`feature.billing.data.play`, plain Kotlin)

| Type | Fields | Notes |
|---|---|---|
| `PlayConnection` | `Ready` \| `Unavailable(reason: PlayUnavailable)` | `PlayUnavailable` = `NotSupported` (iOS), `NotWired` (Android before G1), `BillingUnavailable`, `Disconnected`, `Error` |
| `PlayResult<T>` | `Ok(value)` \| `Failed(code: PlayError)` | `PlayError` = `Network`, `ServiceUnavailable`, `BillingUnavailable`, `ItemUnavailable`, `ItemAlreadyOwned`, `DeveloperError`, `FeatureNotSupported`, `Unknown` |
| `PlayPurchase` | `token: PurchaseToken`, `productId: String`, `state: PlayPurchaseState` (`Pending`, `Purchased`), `acknowledged: Boolean`, `obfuscatedAccountId: String?`, `purchaseTime: Instant`, `kind: PlayProductKind` (`Subscription`, `OneTime`) | Never stored. `PurchaseToken` is a value class whose `toString()` is `PurchaseToken(***)` |
| `PlayProduct` | `productId`, `kind`, `priceMicros: Long`, `currency: String`, `basePlanId: String?`, `offerToken: String?` (US6) | Base plan's recurring price (R-10) |
| `PlayPurchaseUpdate` | `Purchased(purchases: List<PlayPurchase>)` \| `Cancelled` \| `Failed(code: PlayError)` | Emitted by Google Play's listener |
| `PlayLaunchRequest` (US6) | `productId`, `offerToken`, `obfuscatedAccountId: AccountReference` | `AccountReference` value class, redacted |
| `PlayLaunchResult` (US6) | `Launched` \| `Failed(code: PlayError)` | The outcome of the purchase itself arrives as a `PlayPurchaseUpdate` |

## 2. Wire DTOs (`feature.billing.data.dto`, from `google-play.yaml` v1.15)

| DTO | Fields (wire names) | Rules |
|---|---|---|
| `PlayPurchaseRequestDto` | `purchase_token`, `product_id` | Only these members (P-02.2); `toString` redacts the token |
| `PlayRestoreRequestDto` | `purchases: List<PlayPurchaseRequestDto>` | 0–25 items, distinct tokens |
| `PlayPurchaseStateDto` | `product_id`, `state`, `acknowledged`, `auto_renew`, `current_period_end: String?`, `test` | Unknown members ignored (P-02.3); unknown `state` kept as a string → `PlayServerState.Unknown` |
| `PlayPurchaseResultDto` | `purchase: PlayPurchaseStateDto`, `entitlements: EntitlementSummaryDto`, `subscription: MySubscriptionDto` | Same DTOs as spec 005/015 for the last two |
| `PlayRestoreItemResultDto` | `index: Int`, `product_id`, `outcome: String` | Unknown `outcome` → `RestoreOutcome.Unknown` |
| `PlayRestoreResultDto` | `results`, `entitlements`, `subscription` | |
| `PlayAccountReferenceDto` (US6) | `obfuscated_account_id` | 64 lowercase hex; a value that does not match is treated as a failed read |

`BillingApi` gains (plain strings, P-10.6): `verifyPlayPurchase(token, productId, key)`,
`restorePlayPurchases(items: List<Pair<String, String>>, key)`, `playAccountReference()`.
`200` and `202` share one body shape; the client tells them apart by `purchase.state == "pending"`
(the body, not the status — P-02.4), so `ApiResult` needs no change.

## 3. Domain types (`feature.billing.domain.play`)

| Type | Fields / states | Notes |
|---|---|---|
| `PlayServerState` | `Pending`, `Active`, `Grace`, `Canceled`, `OnHold`, `Paused`, `Expired`, `Revoked`, `Unknown` | From `purchase.state` |
| `VerifyOutcome` | `Granted(state, test, planChanged)`, `Pending`, `AnotherAccount`, `NotVerified`, `RetryLater(afterSeconds?)`, `SignedOut`, `Failed(requestId?)` | One per purchase send |
| `RestoreOutcome` | `Linked`, `AlreadyYours`, `LinkedElsewhere`, `NotVerified`, `Pending`, `Unavailable`, `Unknown` | Per item |
| `RestoreSummary` | `sent: Int`, counts per `RestoreOutcome` | Drives FR-023's message |
| `RestoreResult` | `Done(summary)`, `GooglePlayUnavailable`, `Offline`, `RateLimited(retryAfterSeconds?)`, `ServiceUnavailable`, `InProgress`, `SignedOut`, `Failed(requestId?)` | Tapped restore |
| `PlayNotes` | `userId`, `pendingProducts: Set<String>`, `anotherAccount: Boolean`, `heldProducts: Set<String>`, `pausedProducts: Set<String>`, `testProducts: Set<String>`, `refused: Set<String>` (token digests), `activated: PlanActivated?` (one-shot, FR-017) | `PlayBillingNotes` holds it; reset on user change / sign-out |
| `PlayKeys` | `verify(userId, token, state, recheckHour?)`, `attempt()` | R-3 |
| `SubscribeEligibility` (US6) | `Offer(productId)`, `Hidden(reason)`; reasons `SwitchOff`, `SignedOut`, `PlayUnavailable`, `UnknownProduct`, `PaidGoverns`, `OwnedOrPending`, `Busy` | FR-041 |
| `SubscribeFlowState` (US6) | `Idle`, `Connecting`, `Launching`, `Verifying`, `Done(planName, test)`, `Failed(message)` | FR-043 |

`Subscription` (spec 005) gains `provider: String?` (`google_play` / `instapay` / `manual` / null)
from `MySubscriptionDto.provider`, used only to decide Google Play links (FR-030); spec 005's "the
channel is not shown" stands — the provider is never printed.

`CataloguePlan` (spec 005) gains `playProductId: String?`; `price` is no longer filled from the
catalogue (FR-037).

## 4. Plan screen state additions (`feature.billing.ui.plan`)

| Field | Type | Source |
|---|---|---|
| `playSupported` | `Boolean` | port `platformSupported` (hides Restore/Manage/Fix on iOS) |
| `restore` | `RestoreUi`: `Idle`, `Running`, `Result(message: RestoreMessage)`, `Refused(kind, retryAfterSeconds?, requestId?)` | FR-021–FR-025 |
| `playPrices` | `Map<String, Money>` | R-10 |
| `notes` | `PlayNotes?` (current user) | R-9 |
| `manageProductId` | `String?` | governing plan → catalogue `playProductId` |
| `subscribe` (US6) | `SubscribeFlowState` + per-card eligibility | gated |

Derived: `showManage` (provider `google_play` and `playSupported`), `paymentProblem`
(grace / hold / paused / none), `pendingNote`, `anotherAccountNote`, `testLabel`, card prices from
`playPrices[card.playProductId]`.

New intents: `RestorePurchases`, `RetryRestore`, `DismissRestoreResult`, `OpenManage`, `OpenFixPayment`,
`Subscribe(productId)` (US6). New effects: `OpenUrl(url)`, `ShowActivated(planName)`.
