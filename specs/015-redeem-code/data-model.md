# Data Model: Redeem Code (client)

No Room change (database stays at its current version), no new `KeyValueStore` key, nothing
syncable (P-04.6). Everything new is in memory; the answer updates two existing C3 records.

## 1. Wire DTOs (`feature.billing.data.dto`, from `instapay.yaml` v1.12)

| DTO | Members | Notes |
|---|---|---|
| `RedeemRequestDto` | `code: String` | Exactly the declared member (P-02.2); value is `RedeemCode.grouped(raw)`; `toString()` redacted |
| `RedeemResultDto` | `subscription: RedeemedSubscriptionDto`, `governing: MySubscriptionDto` (existing), `entitlements: RedeemEntitlementsDto` | Unknown members ignored (P-02.3) |
| `RedeemedSubscriptionDto` | `id`, `plan: PlanRefDto`, `provider: String`, `status: String`, `current_period_start: String`, `current_period_end: String?` | `status` ∈ active, grace, expired, revoked (repeat redemption reports it as it stands) |
| `RedeemEntitlementsDto` | `plan: PlanRefDto`, `features: Map<String, JsonPrimitive>` | Mirrors `EntitlementSummary`; billing's own copy so billing never imports auth `data` (P-10.4) |

Headers sent: the standard four (P-02.5) + `Authorization` + `Idempotency-Key` (UUIDv7 text).
Header read: none required (`Idempotency-Replayed` is not used; a replay is a normal 200).

## 2. Domain (`feature.billing.domain`)

| Type | Shape | Rules |
|---|---|---|
| `RedeemCode` (object) | `ALPHABET`, `normalise(String)`, `fromPaste(String)`, `grouped(String)`, `isComplete(String)` | R-02; pure; max 16 |
| `AttemptKey` (value class) | `value: String` (UUIDv7) | `toString()` = `AttemptKey(***)` |
| `RedeemOutcome` (sealed) | `Redeemed(RedeemConfirmation)`, `Offline`, `NotValid`, `Used`, `Expired`, `OtherAddress`, `NotVerified`, `RateLimited(retryAfterSeconds: Long?)`, `InvalidFormat`, `InProgress`, `KeyReused(requestId: String?)`, `Failed(requestId: String?)`, `SignedOut` | R-08 |
| `RedeemConfirmation` | `governingPlanName`, `governingEnd: Instant?` (the grace end when the governing subscription is in grace), `governingIsRedeemed: Boolean`, `playRenews: Boolean`, `redeemedPlanName`, `redeemedEnd: Instant?`, `redeemedStartsAt: Instant?` (only when after the redemption moment) | R-07; contains no code |

## 3. Screen state (`feature.billing.ui.redeem`, memory only)

`RedeemState` (immutable `data class`, `toString()` redacts `code` and `attempt`):

| Field | Type | Meaning |
|---|---|---|
| `code` | `String` | Raw normalised characters, 0–16 |
| `attempt` | `AttemptKey?` | Current attempt identifier (R-05) |
| `verified` | `Boolean` | Phone's copy of the verified flag (FR-003) |
| `signedInEmail` | `String?` | For the mismatch copy only (own address) |
| `phase` | `Editing \| Sending \| Done(RedeemConfirmation)` | `Done` shows the confirmation |
| `message` | `RedeemMessage?` | The refusal shown (cleared on `CodeChanged` or a new send) |
| `waitUntil` | `Instant?` | Rate-limit end; `Redeem` disabled while set |
| `timeZoneId` | `String` | Profile time zone for dates (P-05) |

Derived: `canRedeem = phase == Editing && RedeemCode.isComplete(code) && waitUntil == null`.

`RedeemMessage` (sealed): `Offline`, `NotValid`, `Used`, `Expired`, `OtherAddress(signedInEmail)`,
`NotVerified`, `RateLimited(minutes: Int?)`, `InvalidFormat`, `InProgress`, `Failed(requestId)`.

## 4. State transitions

```text
Editing --Redeem [not verified]--> Editing + Effect.RequireVerification (nothing sent)
Editing --Redeem [canRedeem]--> Sending (attempt = existing ?: new)
Sending --Redeemed--> Done(confirmation)
Sending --SignedOut--> Editing (code cleared) ; nav host leaves the screen
Sending --Offline/InProgress--> Editing + message (attempt kept; TryAgain = Redeem)
Sending --RateLimited(s)--> Editing + message, waitUntil = now + s (attempt kept)
Sending --KeyReused--> Editing + Failed message, attempt = null
Sending --other refusal--> Editing + message (attempt kept)
Editing --CodeChanged--> Editing, code = normalised, attempt = null, message = null
Editing --WaitOver--> waitUntil = null
Done --Done--> Effect.NavigateBack
any --session signed out--> code = "", attempt = null
```

## 5. Records updated on success (existing, spec 005)

| Record | Written by | With |
|---|---|---|
| Account snapshot entitlements (auth) | `SessionManager.applyEntitlements(forUserId, …)` (new, R-06) | `entitlements.plan` + `features` |
| Stored governing subscription (`billing_subscription`) | `SubscriptionRepository.save` (existing) | `governing` |

Then `Entitlements.refreshAfterBillingAction()` re-reads both (existing hook).
