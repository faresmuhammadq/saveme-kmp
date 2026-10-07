# Research: Google Play Billing (client) — spec 018, C10

All decisions below were taken with the owner away (delegated authority): each is the conservative
option and is an **assumed default** unless it restates a frozen backend rule.

## R-1 — P-12.3 and the brief: what billing UI is allowed

**Decision**: Ship exactly two billing actions — "Restore purchases" and "Enter code" — plus
"Manage in Google Play" / "Fix payment" links to Google Play's own subscription centre when the
governing subscription is Google Play's. Build the in-app Subscribe flow (US6) completely, behind a
build-time switch `AppConfig.inAppSubscribe` that is `false` in every flavour and only the owner's
P-12.3 amendment may turn on.

**Rationale**:
- P-12.3 says, verbatim: "The in-app billing surface MUST expose only "restore purchases" and
  "enter code"." Spec 005 already reads it literally (FR-010: "MUST NOT contain any purchase or
  payment action"; SC-004: "0 purchase … elements anywhere in the app"). A Subscribe button is a
  third billing action.
- The backend's mirror (backend P-10.2) says "in-app **API** surface", and spec 024 / ADR-0026 (to
  be written) read verify as "the single-item form of restore" and the account-reference read as "a
  read that starts nothing". That reading is about which **endpoints** the app may call; it lets
  this feature call verify and restore. It does not add a button to P-12.3's UI list, and the
  client constitution is the one that governs the app's UI.
- P-12.2 ("In-app purchases MUST use Google Play Billing") presupposes purchases can happen in the
  app — the contradiction inside P-12 is real and only the owner can resolve it (constitution
  Governance: "Contradictions … are defects to resolve before code"). The plan therefore does not
  block on it: everything except the button is built.
- Subscription-centre links start no payment and are what Google Play's subscriptions policy
  expects an app to offer for managing or cancelling a subscription; the same reasoning ADR-0026
  applies to the account-reference read.

**Alternatives rejected**:
- *Show Subscribe now, citing ADR-0026*: overrides the client constitution's literal text by a
  backend reading of a different rule; non-negotiable principle → blocked plan.
- *"Get Plus on Google Play" link to the Play Store listing*: the store page does not sell an app's
  subscriptions; it would mislead.
- *Leave US6 out entirely*: the owner would have to re-plan the headline of the brief later; the
  cost of building it behind a switch is small and it is fully testable with the fake.

**Recommended amendment** (owner): client P-12.3 → "The in-app billing surface MUST expose only
Google Play's own purchase sheet, "restore purchases" and "enter code"; links to Google Play's
subscription centre are allowed." (v1.4.0 MINOR), with the matching backend P-10.2 wording and
ADR-0026 recording both. Then set `inAppSubscribe = true` (tasks phase G2).

## R-2 — The Google Play port

**Decision**: one interface in `feature.billing.data.play`, `PlayBillingClient`, with plain Kotlin
types only (no Play Billing Library type crosses it):

```kotlin
interface PlayBillingClient {
    /** Unavailable on iOS and until the Play Billing dependency is approved and wired (G1). */
    val platformSupported: Boolean
    suspend fun connect(): PlayConnection                       // Ready | Unavailable(reason)
    suspend fun productDetails(productIds: Set<String>): PlayResult<List<PlayProduct>>
    suspend fun purchases(): PlayResult<List<PlayPurchase>>      // SUBS + INAPP, as owned now
    val purchaseUpdates: Flow<PlayPurchaseUpdate>                // Purchased(list) | Cancelled | Failed(code)
    suspend fun launchPurchase(request: PlayLaunchRequest): PlayLaunchResult   // US6 only
}
```

No `acknowledge` or `consume` exists on the port (FR-002) — the invariant is structural, and a
source scan proves the G1 adapter never calls them.

**Rationale**: P-10.2 (platform code behind an interface), P-10.6 (the port is a data source: it
lives in `data`, imports no `domain`; domain services consume it). It is the narrowest surface that
covers verify, restore, prices and US6. `launchPurchase` takes no Activity: the Android adapter
finds the resumed Activity through an `Application.ActivityLifecycleCallbacks` holder registered
by `:androidApp` (G1), so stores never hold platform objects (P-16.8).

**Implementations**:

| Implementation | Where | When bound |
|---|---|---|
| `UnavailablePlayBillingClient(platformSupported)` | `commonMain` | iOS (`false`); Android until G1 (`true`) |
| `FakePlayBillingClient` | `commonTest` | every host test |
| `ScriptedPlayBillingClient` | `devtools` (dev flavour only) | dev flavour, toggled from the Dev tools screen (R-12) |
| `GooglePlayBillingClient` | `androidMain` | G1, after the dependency is approved |

## R-3 — Idempotency keys without storage

**Decision**: `PlayKeys.verify(userId, token, state, recheckHour?)` =
`"gp1:" + hex(SHA-256("gp1|" + userId + "|" + state + "|" + (recheckHour ?: "-") + "|" + token))`
(68 characters, inside the convention's 16–255 `[A-Za-z0-9._:-]`). `state` is `pending` or
`purchased` (Google Play's view). `recheckHour` is set only for a recheck after a `202` on a
purchase Google Play reports as purchased: the UTC hour (`epochSeconds / 3600`). Restore keys are
random (`Uuid.random()`, as spec 015's `AttemptKey`), one per attempt, reused on its automatic
retries and on "Try again".

**Rationale**:
- Same purchase + same state → same key across retries, foreground passes and process death,
  without storing anything (Google Play keeps the purchase; the key is recomputed). That is what
  "replay verify with a stable Idempotency-Key per purchase token" needs.
- The server stores only `2xx` answers for 24 hours: a stored `202` would be replayed to every
  later send with the same key, so a purchase that moves pending → purchased must change key
  (`state` is in the derivation), and a recheck of a purchased-but-202 purchase must change key
  (the hour bucket) — at most one new key per hour, inside the 20-an-hour budget.
- After a process death that follows a `202` for a purchased purchase, the first send uses the
  plain `purchased` key again; the server replays the stored `202` (a replay spends no budget),
  and the hourly recheck key takes over from then on.
- The key is one-way: the token cannot be recovered from it, and `userId` keeps two accounts on
  one phone from sharing keys (the server scopes keys per user anyway).
- SHA-256 needs a tiny `expect fun sha256(bytes: ByteArray): ByteArray` in `core.platform`
  (Android `MessageDigest`, iOS CommonCrypto `CC_SHA256`) — no new dependency.

**Alternatives rejected**: a Room table or key-value record of attempts (adds a migration or a
record that holds or indexes secrets, and must be cleaned; Google Play already is the record);
random key per pass (breaks replay after a killed process: a second verify of a purchase whose
first answer was lost would still be fine server-side, but SC-004 and the backend's guidance ask
for the same key).

## R-4 — Triggers and the single pass

**Decision**: `PlayPurchaseVerifier` owns a `Mutex` and a "dirty" flag. A pass = `connect` →
`purchases()` → for each purchase not acknowledged and not refused in this run → send. Triggers:

| Trigger | Source |
|---|---|
| App start while signed in, and sign-in | `BillingSyncCoordinator.start()` (state → `SignedIn`) |
| Foreground (floor 60 s since the last pass) | `BillingSyncCoordinator.onForeground()` (already called by `App`) |
| Plan screen open and refresh | `PlanViewModel` intent → domain call |
| Purchase update from Google Play | `PlayBillingClient.purchaseUpdates`, collected by the coordinator |
| In-run retry | the verifier's own backoff (R-5) |

A trigger during a pass sets "dirty"; the pass runs once more when it ends. Answers are applied
only if the same `userId` is still signed in (the existing `SessionManager.applyEntitlements`
guard). There is **no connectivity trigger**: the app has no connectivity signal outside the sync
engine's WorkManager constraint, and in-run backoff plus foreground covers the gap.

**Sign-in restore vs cold start**: restore runs on a transition **from `SignedOut` to `SignedIn`**
(or to a different `userId`), not on `Unknown → SignedIn` (a cold start restoring the stored
session), so a person opening the app many times an hour does not spend the 10-an-hour restore
budget. Cold starts run the verify pass only.

## R-5 — Retries

**Decision**: per purchase per run: `SERVICE_UNAVAILABLE`, `RATE_LIMITED`,
`IDEMPOTENCY_REQUEST_IN_PROGRESS`, offline and timeout → retry with the same key after
`Retry-After` (seconds; capped at 15 min) or 30 s doubling (30 s, 60 s, 2 min, 4 min, 8 min, 15 min),
at most 6 automatic sends; then wait for the next trigger. `PURCHASE_ALREADY_LINKED`,
`PURCHASE_NOT_VERIFIED`, `VALIDATION_FAILED`, `IDEMPOTENCY_KEY_REUSED` → the purchase joins the
run's refused set (memory; cleared at sign-out). `UNAUTHENTICATED`/`426` → the existing app-wide
handling; the pass stops. Anything else → next trigger only. All timers run in the coordinator's
scope and are cancelled at sign-out. Matches P-02.6 (retry classes) and spec 024's client table.

## R-6 — Applying an answer

**Decision**: extract spec 015's private apply step (`RedeemService.applied`) into a shared
`BillingAnswerApplier.apply(userId, entitlements, subscription): Boolean` in `billing.domain`
(same-person guard → `SessionManager.applyEntitlements` → `SubscriptionRepository.save` → launch
`Entitlements.refreshAfterBillingAction()`), used by redeem, verify and restore. `RedeemService`
keeps its behaviour (its tests stay green unchanged).

**Rationale**: three callers of one rule; the "apply only for the same person" invariant then has
one test.

## R-7 — Restore

**Decision**: `PlayRestoreService.restore(trigger)`:
1. `connect()`; unavailable → `Unavailable` (tap) / nothing (sign-in).
2. `purchases()`; sign-in trigger with none → nothing.
3. Order most recent first; chunk by 25 (distinct tokens); one request per chunk, each with its own
   random key, sequentially; a refusal of one chunk stops the rest (tapped restore reports it;
   "Try again" resends the failed chunk with its key, then the remaining chunks).
4. Apply each `200`'s entitlements/subscription (R-6); fold outcomes into notes (R-9) and a
   `RestoreSummary` (counts per outcome) from which the Plan screen assembles the message (FR-023).
5. A tapped restore with no purchases sends `{purchases: []}` (server re-reads linked purchases).

## R-8 — Google Play subscription-centre link

**Decision**: `PlayStoreLinks.subscriptions(applicationId, productId?)` =
`https://play.google.com/store/account/subscriptions?package=<applicationId>[&sku=<productId>]`,
opened through Compose's `LocalUriHandler` in `PlanRoute` (as `UpdateRequiredRoute` does); a
failure (`IllegalArgumentException` / no handler) → "Couldn't open Google Play." The application
id comes from a new `AppConfig.applicationId` (set by `:androidApp` from `BuildConfig.APPLICATION_ID`,
so the dev/staging suffixes are right). Product id: the catalogue plan whose id equals the governing
subscription's plan id → its `google_play` `product_id` (backend gap 3).

## R-9 — Billing notes (memory)

**Decision**: `PlayBillingNotes` (singleton, `StateFlow<PlayNotes>` keyed by `userId`):
`pendingProducts`, `anotherAccount`, `heldProducts`, `pausedProducts`, `testProducts` (the last three
from verify answers' `purchase.state` / `test`), `refused: Set<tokenDigest>` (data-model §3); a `lastActivated`
one-shot for FR-017. Reset when the signed-in `userId` changes or on sign-out (a
`UserDataCleaner`, like `BillingDataCleaner`). Nothing persisted (FR-051). `pendingProducts` is
recomputed from Google Play's list on every pass (purchases that disappear drop out).

## R-10 — Prices

**Decision**: `PlayPrices.toMoney(priceMicros: Long, currency: String): Money?` with
`digits = MoneyFormatter.digitsFor(currency)` (plain Kotlin; feature domains already import it —
`budgets/domain/BudgetMath.kt`, `wallets/domain/AmountInput.kt`). `minor = micros / 10^(6 − digits)`
only when the remainder is 0 and `digits ≤ 6`; otherwise `null` (no price). No `Double` anywhere
(P-03.2).

- `CataloguePlan` gains `playProductId: String?` (from the `google_play` entry's `product_id`);
  `CataloguePlan.price` stops being read from the catalogue's `amount_minor` (FR-037) and is filled
  by the Plan screen from a `Map<productId, Money>` built from `productDetails()`.
- The adapter returns one price per product: for a subscription, the recurring price of the base
  plan (the offer with no offer id; its last, infinitely recurring pricing phase); for a one-time
  product, its single price. Trials and introductory offers are not shown (FR-049).
- Product details are requested on Plan screen open and when the catalogue's product-id set
  changes, at most once a minute, memory only (FR-038).

**Alternative rejected**: showing Google Play's own `formattedPrice` string — it follows the Play
account's locale and may carry Arabic-Indic digits or another format than the app's (P-06.2).

## R-11 — The real adapter (G1, blocked on dependency approval)

**Decision** (applied only after approval): `com.android.billingclient:billing-ktx`, current
stable major at wiring time (use the `play-billing-library-version-upgrade` skill to pick it;
backend spec 024 assumes PBL ≥ 7), in `:shared` `androidMain` only, version in
`libs.versions.toml`. `GooglePlayBillingClient`:
- `BillingClient.newBuilder(context).setListener(…)
  .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())`
  (+ automatic service reconnection where the version offers it); `connect()` wraps
  `startConnection` in `suspendCancellableCoroutine`, single connection reused.
- `purchases()` = `queryPurchasesAsync(SUBS)` + `(INAPP)`; maps `purchaseToken`, `products.first()`,
  `purchaseState` (PURCHASED/PENDING; UNSPECIFIED dropped), `isAcknowledged`,
  `accountIdentifiers?.obfuscatedAccountId`, `purchaseTime`.
- `productDetails()` = `queryProductDetailsAsync` for SUBS then INAPP; base-plan price from
  `subscriptionOfferDetails` (offer with `offerId == null`), its last pricing phase.
- `launchPurchase()` = `launchBillingFlow(activity, BillingFlowParams…setObfuscatedAccountId(ref))`
  with `ProductDetailsParams(productDetails, offerToken)`; result codes mapped to `PlayLaunchResult`.
- Listener → `MutableSharedFlow(extraBufferCapacity = 16)`.
- Never `acknowledgePurchase`/`consumeAsync` (scan test). No R8 rule needed (the library ships
  consumer rules); the BILLING permission comes from the library manifest.
- Response-code mapping (`OK`, `USER_CANCELED`, `ITEM_ALREADY_OWNED`, `BILLING_UNAVAILABLE`,
  `ITEM_UNAVAILABLE`, `SERVICE_DISCONNECTED`, `SERVICE_UNAVAILABLE`, `NETWORK_ERROR`,
  `DEVELOPER_ERROR`, `ERROR`, `FEATURE_NOT_SUPPORTED`) is a pure function, unit-tested on the host;
  the adapter's wiring is checked by the owner with a license tester.

**What the owner must supply for G1 to work end to end**: dependency approval; the app uploaded to
a Play testing track (internal) signed by Google Play app signing with the same application id
(`com.fares.saveme`; dev/staging suffixes are separate Play apps or are tested with the prod id);
subscription products `plus_monthly` / `plus_yearly` each with one auto-renewing base plan and
prices; license tester accounts; backend spec 024's Google Cloud/RTDN setup.

## R-12 — Dev and E2E story

**Decision**: `devtools/playbilling/ScriptedPlayBillingClient` (dev flavour only; the Dev tools
screen gains "Google Play (scripted)": on/off, add a purchase with token, product, state pending
/purchased, acknowledged flag; emit an update; mark unavailable). The dev Koin graph binds a
`SwitchingPlayBillingClient(real, scripted)`; release builds bind the real/unavailable client only
(devtools excluded, P-09.3 gate). The local backend runs `BILLING_PLAY_DRIVER=fake` with
`BILLING_PLAY_FAKE_SCRIPT` scripting the same tokens (backend quickstart §3), so verify, restore,
`202`, `409` and `422` run end to end on the dev build. Maestro flows (written, **deferred: E2E on
owner's device**) drive the Dev tools screen to script a purchase, then assert the Plan screen; they
never open Google Play. Real purchases: the owner, with a license tester, after G1.

## R-13 — Testing

- Host (commonTest): key derivation, verifier (fake port + `MockEngine`), restore, notes, prices,
  applier, Plan reducer and store, US6 flow and eligibility; contract tests from every
  `google-play.yaml` example and each error code (P-14.3), written against v1.15 now.
- androidHostTest: Roborazzi Plan screen states (EN LTR, AR RTL, 200 %); layering (no Android type
  in common billing; port has no acknowledge; adapter never calls it); logging capture; string scan
  (no "token", "InstaPay", "acknowledge" in strings; Subscribe strings referenced only from the
  gated path); `AppGraphTest` resolves `PlayPurchaseVerifier` and `PlayBillingClient`.
- Live contract QA (after backend spec 024 is built and committed): curl against the local backend
  with the fake gateway scripted per token; capture answers to `CapturedGooglePlayResponses.kt`
  (tokens redacted) and replay through the real `ApiClient` → `BillingApi` → services.
- No emulator/Maestro/device runs (owner policy).

## R-14 — Room

**Decision**: no schema change; Room stays v10. Nothing in this feature is syncable or needs to
survive a process death that Google Play does not already keep (R-3, R-9). The existing
`billing_subscription` key-value record and account snapshot receive the answers.

## R-15 — iOS

**Decision**: `UnavailablePlayBillingClient(platformSupported = false)`; the Plan screen hides
Restore, Manage and Fix payment when `platformSupported` is false (FR-055). `sha256` iOS actual via
CommonCrypto (compiles; unused at runtime). No iOS billing in MVP (P-15).
