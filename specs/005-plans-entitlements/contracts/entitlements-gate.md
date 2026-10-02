# Contract: Entitlements gate (for every later feature)

Package `com.fares.saveme.feature.billing.domain`. Features depend on it through Koin
(`get<Entitlements>()`); it is the only sanctioned way to ask a capability question (P-12.1).

```kotlin
interface Entitlements {
    /** Null when signed out. Emits on sign-in, on every refresh that changes the answer, and on sign-out. */
    val state: StateFlow<EntitlementSet?>

    fun can(flag: FlagFeature): Boolean                  // false when signed out or missing
    fun quota(quota: QuotaFeature): QuotaLimit           // Limited(0) when signed out or missing
    fun allowsAnother(quota: QuotaFeature, currentCount: Int): Boolean

    /** C10/C11 call this after a billing action; never skipped, joins an in-flight refresh. */
    suspend fun refreshAfterBillingAction(): RefreshOutcome   // Done | Offline | Failed
}
```

Rules for callers:

1. Use it to show, hide or explain controls; the server stays the authority. A
   `FEATURE_NOT_ENTITLED` refusal is still possible and shows `error_feature_not_entitled`.
2. It answers for the **signed-in person's own plan**. Actions inside a wallet someone else owns
   run on the owner's plan (backend glossary) — do not block them with this gate (C4/C9 decide).
3. Never read `AccountSnapshot.plan` / `planId` to decide a capability; they are display-only and
   used by the Plan screen alone. Enforced by `PlanNameNotGatedTest` (androidHostTest source scan).
4. Losing paid access never hides or blocks reading data (FR-016); gate only actions.
