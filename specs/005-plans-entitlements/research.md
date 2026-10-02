# Research: Plans & Entitlements (client)

Phase 0 of the plan. Contract baseline `saveme-backend@055afa1` (billing.yaml unchanged since
spec 009 landed in `56b44fb`).

## R-01 A new `feature.billing` package

- **Decision**: Plans, subscription, the gate and the Plan screen live in a new
  `com.fares.saveme.feature.billing` package (`data`, `domain`, `ui`, `di`), named after the
  backend context "Plans & Billing" (P-01, P-10). C10 (Google Play) and C11 (redeem code) extend it.
- **Rationale**: One bounded context per feature package (constitution, Architecture). New files
  keep this branch away from the files the in-flight sync-engine branch (004) edits.
- **Alternatives**: Put it in `feature.account`. Rejected: account is a hotspot (AccountModule,
  AccountSyncCoordinator) and billing is a separate backend context.

## R-02 Entitlements stay in the account snapshot (auth); billing reads them through auth's domain

- **Decision**: The entitlements answer remains part of `AccountSnapshotDto` (key-value store,
  spec 001), because sign-in delivers it there and relaunch must not need a call (FR-005).
  `feature.auth` gains a new `EntitlementsApi` (GET `me/entitlements`) in `auth/data` and
  `SessionManager.refreshEntitlements()` in `auth/domain`, which writes the new answer into the
  snapshot only if the same person is still signed in (FR-008). `feature.billing.domain` reads
  `SessionManager.state` and calls `refreshEntitlements()` — cross-feature access through the other
  feature's domain only (P-10.4).
- **Rationale**: A second cache in billing would be a second source of truth that can disagree
  with the one sign-in writes. Auth already owns the snapshot and its clearing at sign-out.
- **Alternatives**: (a) Billing owns its own entitlements store and DTO. Rejected: duplicate DTO
  (billing may not import `auth.data`), two caches, two clear paths. (b) Room table. Rejected: not
  syncable (P-04.6), and `AppDatabase` is a sync-engine hotspot.

## R-03 Feature values: `null` = unlimited, missing/wrong type = off/0

- **Decision**: `FeatureValue` (auth domain model) becomes `Flag(enabled: Boolean)` and
  `Quota(limit: Long?)` with `null` = unlimited. The mapping reads `JsonNull` → `Quota(null)`,
  a JSON boolean → `Flag`, a JSON integer → `Quota(n)`, anything else is dropped (so it reads as
  missing). `AccountSnapshot` gains `planId`. The stored JSON is unchanged (it already keeps
  `JsonNull`), so no store migration.
- **Rationale**: billing.yaml `Features`: "quotas are integers, or null for unlimited"; backend
  spec 009: an omitted capability is off / 0. The current mapping (`longOrNull ?: 0`) turns
  `wallets.max: null` into 0 — a latent block on wallet creation once C4 gates on it.
- **Alternatives**: Treat unknown as allowed. Rejected: the server would refuse anyway; showing a
  control that fails is worse than hiding one the server would allow (stale hints are refreshed,
  FR-007).

## R-04 The gate API

- **Decision**: `feature.billing.domain.Entitlements` (single instance):
  - `val state: StateFlow<EntitlementSet?>` (null when signed out);
  - `fun can(flag: FlagFeature): Boolean`;
  - `fun quota(quota: QuotaFeature): QuotaLimit` (`Unlimited` | `Limited(n)`);
  - `fun allowsAnother(quota: QuotaFeature, currentCount: Int): Boolean`;
  - `suspend fun refreshAfterBillingAction()` (the call C10/C11 make, never skipped).

  `FlagFeature` = `SharingEnabled`, `ExportEnabled`, `ReportsAdvanced`; `QuotaFeature` =
  `MembersPerWallet`, `Wallets`; each carries its wire key. The plan reference is not reachable
  through the gate.
- **Rationale**: Typed keys make "gate on the plan name" impossible to write by accident (P-12.1)
  and keep wire keys in one place. `can`/`quota` mirror the brief's `can(key)`/`quota(key)`.
- **Alternatives**: String keys. Rejected: typos compile.

## R-05 Refresh orchestration

- **Decision**: `EntitlementsRefresher` (billing domain) runs one refresh at a time: a `Mutex`
  plus a shared in-flight `Deferred`; a trigger during a refresh awaits the same result (FR-006).
  A refresh calls `SessionManager.refreshEntitlements()` and `SubscriptionRepository.fetch()`
  concurrently, records the success time from an injected `Clock`, and drops the result when the
  signed-in user id changed between start and finish (FR-008). Triggers:
  - sign-in: `BillingSyncCoordinator` watches `SessionManager.state` for a new `SignedIn` user and
    fetches the subscription (entitlements came with the sign-in answer);
  - foreground: `BillingSyncCoordinator.onForeground()` — skipped if the last success is < 60 s old;
  - Plan screen open / refresh and `refreshAfterBillingAction()` — never skipped;
  - `ApiEvent.FeatureNotEntitled` — never skipped (R-06).
- **Rationale**: Mirrors the existing `AccountSyncCoordinator` pattern without touching it
  (hotspot). The 60 s floor stops bursts on app switching (clarification 2026-10-02).
- **Alternatives**: Extend `AccountSyncCoordinator`/`AccountRefresher`. Rejected: hotspot files
  in the sync-engine branch. Periodic polling. Rejected: battery and no requirement.

## R-06 `FEATURE_NOT_ENTITLED` everywhere

- **Decision**: Add `FEATURE_NOT_ENTITLED(RetryClass.Never)` to `ApiErrorCode`; add
  `ApiEvent.FeatureNotEntitled` published by the HTTP client for that code (one line next to
  `EMAIL_NOT_VERIFIED`); `BillingSyncCoordinator` collects it and refreshes. Copy key
  `error_feature_not_entitled` ("Your plan doesn't include this.") is added to the shared error
  copy mappings (`AuthErrorCopy`/`AccountErrorCopy` fall-through) so no screen shows a generic
  failure for it; later features use the same key.
- **Rationale**: The refusal can come from any feature; only `core` sees every response, and
  `ApiEvents` is the sanctioned core → feature channel (P-10.7).
- **Alternatives**: Each feature calls the gate on refusal. Rejected: easy to forget; FR-007 says
  any request.

## R-07 Subscription and catalogue storage

- **Decision**: Billing owns two key-value records through the existing `KeyValueStore`:
  `billing_subscription` (the last `MySubscriptionDto`) and `billing_catalogue` (the last plan list
  plus the language it was requested in). One `BillingDataCleaner` (bound as `UserDataCleaner`,
  like `DatabaseCleaner`) clears both at sign-out. The catalogue is re-requested on Plan screen open
  and when the app language differs from the stored one (FR-017).
- **Rationale**: Neither is syncable; the Plan screen must work offline (FR-015); no Room table
  (avoids `AppDatabase`, a hotspot, and a schema version bump).
- **Alternatives**: Memory only. Rejected: Plan screen empty offline after relaunch.

## R-08 Prices: Google Play channel only

- **Decision**: The UI model maps `prices` to at most one `Money` — the entry whose `channel` is
  `google_play`; otherwise no price. Formatted with the existing `formatMoney` (currency fraction
  digits, Western digits, P-03/P-06). The InstaPay entry is dropped in the mapper, so no UI code
  can show it.
- **Rationale**: Clarification 2026-10-02 (P-12.3/P-12.4, Play payments policy). Today's seed has
  only InstaPay prices, so no price shows until B10 adds Play entries (spec open question 2).

## R-09 Plan screen composition (P-07)

- **Decision**: Built from existing components: `AppScreen`, `BackBar` pattern (top bar),
  `InlineAlert` (offline / stale / error with retry), `AppListItem` rows for capabilities,
  `Skeleton` while loading, `EmptyState` for "catalogue unavailable". One new design-system
  component, `PlanCard` (name, period line, optional price via `MoneyText`, capability lines,
  "Current plan" text label + outline emphasis), added to `designsystem/component/Plans.kt` with a
  catalogue entry and screenshot tests (EN, AR RTL, 2× font) per P-07.3/P-14.4. Refresh is a
  top-bar `AppIconButton` with the existing `AppIcons.Sync` icon and a content description.
- **Rationale**: No existing card fits a plan (WalletCard is money-specific); a plan card is
  reused by C10/C11. No pull-to-refresh component exists; adding one is more surface than a
  refresh action (FR-015 allows "an equivalent refresh action").

## R-10 Entry point: Account screen row

- **Decision**: Add a "Plan" row to the existing Account screen (`AccountDestination.Plan`), routed
  by `AppNavHost` to a new `PlanKey`. Home is not touched.
- **Rationale**: Home (`HomeScreen`) is a sync-engine hotspot; the account screen is where the
  person looks for account-level settings. SC-001 (2 taps: Account → Plan).

## R-11 Dates and timezone

- **Decision**: Billing-period end and grace end show as dates in the profile timezone, falling
  back to the device's (P-05), reusing `formatDate` and the `profileTimeZone` approach of
  `DeviceSessionsViewModel` (via `ProfileSyncer`, account's domain). Lifetime (`current_period_end`
  null with a non-null status) shows "No end date".
- **Rationale**: Same rule as spec 002's device sessions.

## R-12 E2E seeding for an operator grant

- **Decision**: `run-e2e.sh` gains:
  - a seed suffix `+plus` (e.g. `verified-user+plus`): after seeding the account, the runner
    grants Plus · Monthly for 30 days through
    `OperatorSubscriptions::grant($operatorId, $userId, $planId, 30, 'e2e')` in
    `artisan tinker`. Corrected at the E2E run (T042): the operator id is a fixed UUID
    (`00000000-0000-7000-8000-0000000000e2`) because the audit log's `actor_id` is a uuid column, and
    the plan id is looked up by its backend `code` (`plus_monthly`) — codes are not exposed to
    clients, but this lookup runs inside the backend;
  - two-part flows for "changes while the app is open": `<flow>.part1.yaml` and
    `<flow>.part2.yaml` with header `# e2e-between: grant-plus`. The runner runs part 1, applies
    the grant, then runs part 2 **without** resetting the app (part 2 taps the Plan screen's refresh, or
    reopens Plan, and asserts Plus; a foreground return alone may fall inside the 60 s floor).
- **Rationale**: No HTTP endpoint grants (B12 back office later); the runner already seeds
  accounts through backend use cases in tinker (spec 003 R-07). The two-part split proves the
  brief's checkpoint ("Plus after refresh without re-sign-in") with the app's state kept.
- **Alternatives**: Insert a subscription row with SQL. Rejected: skips the audit and event a real
  grant records. Maestro `runScript` HTTP call. Rejected: no endpoint to call.

## R-13 No new dependencies

Ktor, kotlinx.serialization, Koin, Compose MP, Navigation 3, Turbine, MockEngine, Roborazzi —
all present. No owner approval needed (P-14.5).
