# Contract: Redeem domain

Package `com.fares.saveme.feature.billing.domain`.

```kotlin
object RedeemCode {
    const val ALPHABET: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH: Int = 16
    fun normalise(input: String): String        // R-02: upper-case, O→0, I/L→1, alphabet only, ≤ 16
    fun fromPaste(text: String): String         // first grouped code in the text, else normalise(text)
    fun grouped(raw: String): String            // "XXXX-XXXX-XXXX-XXXX" (raw must be complete)
    fun isComplete(raw: String): Boolean
}

@JvmInline value class AttemptKey(val value: String) { override fun toString() = "AttemptKey(***)" }

class RedeemService(
    private val api: BillingApi,
    private val sessions: SessionManager,                 // auth domain
    private val subscriptions: SubscriptionRepository,    // billing data (own feature)
    private val entitlements: Entitlements,               // billing domain (C3 hook)
    private val scope: CoroutineScope,                    // runs the follow-up refresh
    private val clock: Clock = Clock.System,
) {
    /** One request; never retried. Applies the answer only for the person signed in when it started (FR-012). */
    suspend fun redeem(rawCode: String, attempt: AttemptKey): RedeemOutcome
}
```

Rules:

1. `redeem` requires `RedeemCode.isComplete(rawCode)`; it sends `grouped(rawCode)`.
2. Success path, in order: same-person check → `sessions.applyEntitlements(userId, plan.id, plan.name, features)` → `subscriptions.save(governing)` → `scope.launch { entitlements.refreshAfterBillingAction() }` → `Redeemed(RedeemConfirmation.from(result, clock.now()))`. If the person changed or signed out: nothing applied, `SignedOut`. Request and apply run inside `scope.async { … }.await()`, so cancelling the caller (the screen closed) never cancels applying a success (FR-012).
3. Nothing here logs the code, the attempt or the answer's dates/plan (P-09.2); info logs carry only the outcome name and request id.
4. The service never queues and never retries; offline is an outcome.

Auth domain addition (`SessionManager`):

```kotlin
/** Replaces the cached entitlements with an answer the caller already holds (C11), same guards as refreshEntitlements(). */
suspend fun applyEntitlements(forUserId: String, planId: String, planName: String, features: Map<String, JsonPrimitive>): Boolean
```

Core addition (`core.network`): `fun HttpRequestBuilder.idempotencyKey(key: String)`.
Core addition (`core.platform`): `@Composable expect fun SecureWindow()` (Android FLAG_SECURE while composed; iOS no-op).
