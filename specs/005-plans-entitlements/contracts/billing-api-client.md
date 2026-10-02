# Contract: Billing API client

Source of truth: `saveme-backend/docs/openapi/paths/billing.yaml` at `saveme-backend@055afa1`.
All paths under `/api/v1`. Reads only; every call sends the standard client headers (P-02.5),
`Accept-Language` being the app's current language. Automatic retries only per the catalogue's
retry class (P-02.6) — the existing client behaviour, nothing new.

| Client method | Request | Session | Success | Handled codes → client result |
|---|---|---|---|---|
| `BillingApi.listPlans()` | GET `plans` | public (no `Authorization`) | 200 `{data: Plan[], meta}` → `List<PlanDto>` | offline → `Offline`; shared refusals generic |
| `BillingApi.mySubscription()` | GET `me/subscription` | bearer | 200 `{data: MySubscription}` → `MySubscriptionDto` | UNAUTHENTICATED → existing session-ended event; offline → `Offline` |
| `EntitlementsApi.fetch()` (auth data) | GET `me/entitlements` | bearer | 200 `{data: EntitlementSummary}` → `EntitlementSummaryDto` | UNAUTHENTICATED → session-ended event; offline → `Offline` |
| any (core) | — | — | — | 403 FEATURE_NOT_ENTITLED → `ApiEvent.FeatureNotEntitled` published; the call's own result carries the code |

## Contract tests (MockEngine)

Fixtures are hand-written from the billing.yaml schemas (billing.yaml has no examples; spec open
question 1) and mirror the launch seed: Free (default, `period_type: lifetime`, no prices,
`sharing.enabled: false`, `sharing.max_members_per_wallet: 1`, `wallets.max: null`), Plus · Monthly
and Plus · Yearly (InstaPay prices only), plus one fixture with a `google_play` price and one with an
unknown member and an unknown capability key.

For each method: URL suffix and method; `Authorization` present only on the `me/*` calls;
`Accept-Language` sent; success decodes (including `null` quota and all-null free subscription);
an unknown member is ignored; UNAUTHENTICATED publishes the session-ended event; a
FEATURE_NOT_ENTITLED problem on any call publishes `ApiEvent.FeatureNotEntitled`.

## New client error code

`FEATURE_NOT_ENTITLED` → retry class `Never`; copy key `error_feature_not_entitled`:
EN "Your plan doesn't include this." · AR "خطتك لا تشمل هذه الميزة."
