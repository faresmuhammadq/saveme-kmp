# Data model: Plans & Entitlements (client)

Nothing here is syncable (P-04.6); no Room table, no schema version change.

## 1. Wire DTOs (`feature.billing.data.dto`, billing.yaml at `saveme-backend@055afa1`)

Response decoding ignores unknown members (P-02.3). No request bodies.

| DTO | Fields | Notes |
|---|---|---|
| `PlanDto` | `id: String`, `name: String`, `periodType: String` (`period_type`), `periodDays: Int?` (`period_days`), `isDefault: Boolean` (`is_default`), `features: Map<String, JsonElement>`, `prices: List<PriceDto>` | `periodType` kept as a string; unknown values map to "no period line" |
| `PriceDto` | `channel: String`, `productId: String?` (`product_id`), `amountMinor: Long` (`amount_minor`), `currency: String` | `amountMinor` is `Long` (P-03) |
| `MySubscriptionDto` | `plan: PlanRefDto`, `provider: String?`, `status: String?`, `currentPeriodStart: String?`, `currentPeriodEnd: String?`, `graceUntil: String?`, `autoRenew: Boolean?` | All but `plan` null on the free plan |
| `PlanRefDto` | `id`, `name` | Billing's own copy (it may not import `auth.data`, P-10.4) |
| `CatalogueRecord` (stored) | `language: String`, `plans: List<PlanDto>` | Key `billing_catalogue` |

Existing in `feature.auth.data.dto` (unchanged shape): `PlanRefDto`, `EntitlementSummaryDto
(plan, features: Map<String, JsonPrimitive>)`, `AccountSnapshotDto(account, entitlements)`.
New in auth data: `EntitlementsApi.fetch(): ApiResult<EntitlementSummaryDto>` (GET `me/entitlements`).

## 2. Domain models

### auth domain (changed)

```text
sealed interface FeatureValue
  Flag(enabled: Boolean)
  Quota(limit: Long?)            // null = unlimited (was Long, null read as 0)

AccountSnapshot(... , plan: String, features: Map<String, FeatureValue>, planId: String = "")   // planId last, defaulted
```

Mapping from JSON (`toFeatureValue`): `JsonNull` → `Quota(null)`; boolean → `Flag`; integer
(`longOrNull` on a non-string primitive) → `Quota(n)`; string or other → dropped.

### billing domain (new)

```text
enum FlagFeature(wireKey)   SharingEnabled("sharing.enabled"), ExportEnabled("export.enabled"),
                            ReportsAdvanced("reports.advanced")
enum QuotaFeature(wireKey)  MembersPerWallet("sharing.max_members_per_wallet"), Wallets("wallets.max")

sealed interface QuotaLimit { Unlimited; Limited(max: Long) }

EntitlementSet(features: Map<String, FeatureValue>)
  can(flag)    = (features[flag.wireKey] as? Flag)?.enabled ?: false
  quota(q)     = when (val v = features[q.wireKey]) { is Quota -> v.limit?.let(::Limited) ?: Unlimited; else -> Limited(0) }
  allowsAnother(q, count) = quota(q) is Unlimited || count < max

enum SubscriptionStatus { Active, Grace }           // unknown → treated as no status shown
Subscription(planId, planName, status: SubscriptionStatus?, periodEnd: Instant?, graceUntil: Instant?,
             autoRenew: Boolean?, isPaid: Boolean)  // isPaid = status != null
BillingPeriod { Monthly, Yearly, Lifetime, EveryDays(n), Unknown }
CataloguePlan(id, name, period: BillingPeriod, isDefault, features: EntitlementSet, price: Money?)
  price = prices.firstOrNull { channel == "google_play" }?.let { Money(amountMinor, currency) }
```

Instants parse with `Instant.parse`; a bad value becomes null (shown as no date), never a crash.

## 3. Stores and lifetimes

| Record | Owner | Store key | Written | Cleared |
|---|---|---|---|---|
| Entitlements (inside account snapshot) | auth | `account_snapshot` (existing) | sign-in; `SessionManager.refreshEntitlements()` | sign-out (existing `clearLocal`) |
| Subscription | billing | `billing_subscription` | each successful refresh | `BillingDataCleaner` at sign-out |
| Catalogue | billing | `billing_catalogue` | each successful `/plans` read | `BillingDataCleaner` at sign-out |

Refresh write rule (FR-008): `SessionManager.refreshEntitlements()` captures the signed-in user id
before the call and writes only if the state is still `SignedIn` with that id afterwards; the
billing refresher applies the same check before writing the subscription.

## 4. Screen state (MVI, `feature.billing.ui.plan`)

```text
PlanState(
  current: CurrentPlanUi?,          // name, capability lines, status, periodEnd, graceUntil, autoRenew, noEndDate
  catalogue: List<PlanCardUi>,      // name, period, price: Money?, capability lines, isCurrent
  catalogueStatus: Loading | Loaded | Unavailable,
  refreshing: Boolean,
  stale: Boolean,                   // last refresh failed offline / error: show "may be out of date"
  timeZoneId: String,
)
PlanIntent  = Opened | Refresh | Back
PlanEffect  = NavigateBack
PlanChange  = Entitlements(set, planName, planId) | Subscription(sub?) | Catalogue(list) |
              CatalogueUnavailable | RefreshStarted | RefreshFinished(ok: Boolean) | TimeZone(id)
```

`CapabilityLine` is a pure mapping (unit-tested): sharing off → "Sharing: not included" (members
quota hidden); sharing on → "Share wallets with up to N members" / "unlimited members"; wallets →
"Unlimited wallets" / "Up to N wallets"; export and advanced reports → "included" / "not
included". Unknown keys produce no line. `isCurrent` = `plan.id == current planId`.

## 5. State transitions

Subscription status shown: `Active` → "Active · Renews on {date}" or "Active · Ends on {date}"
(by `autoRenew`), lifetime → "No end date"; `Grace` → "Payment due · Access until {graceUntil}";
free (`status == null`) → no status line.
