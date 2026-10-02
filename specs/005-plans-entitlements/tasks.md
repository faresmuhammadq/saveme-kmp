---
description: "Task list for 005-plans-entitlements"
---

# Tasks: Plans & Entitlements (client)

**Input**: Design documents from `specs/005-plans-entitlements/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (billing-api-client.md, entitlements-gate.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0

**Tests**: Included. P-14 requires contract tests per endpoint (every handled code), a test per invariant (spec §4) and screenshot tests per screen and component; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Organization**: Phase 2 fixes the cached feature values and adds the code, event, API clients and DI every story uses. US1 (gate) is the MVP; US2 and US3 build the Plan screen on top of it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US3 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10), naming (P-11), no logging of subscription details or prices (FR-021).
- **MVI screen recipe (P-16)**, as in specs 001–003: `PlanContract.kt` (immutable State, sealed Intent/Effect/Change, pure top-level `reduce`), `PlanViewModel.kt` on `MviViewModel` taking domain lambdas (never repositories), `PlanRoute.kt`, stateless `PlanScreen.kt` + `@Preview`; tests `PlanReducerTest.kt` + `PlanViewModelTest.kt` with `runMviTest`.
- **Merge hotspots** (plan.md): do not edit `AccountModule.kt`, `AccountSyncCoordinator.kt`, `AppDatabase.kt` or `HomeScreen.kt`. New strings go in one append-only block at the end of each `strings.xml` under `<!-- Plans & entitlements (spec 005) -->`.
- Every interactive element gets the `testTag` listed in contracts/ui-and-e2e.md (FR-019).
- No purchase, checkout or InstaPay element anywhere (FR-010, FR-014).

---

## Phase 1: Setup

**Purpose**: the new package and its DI entry.

- [X] T001 Create `cm/feature/billing/di/BillingModule.kt` with an empty `val billingModule = module { }` and add it to `appFeatureModules` in `cm/AppModules.kt` (hotspot: one list element)
- [X] T002 [P] Add the string block `<!-- Plans & entitlements (spec 005) -->` with every key from contracts/ui-and-e2e.md "Copy" to the end of `values/strings.xml` and `values-ar/strings.xml` (Arabic: Western digits, محفظة for wallet, no "token", no InstaPay)

---

## Phase 2: Foundational (blocking)

**Purpose**: correct feature values, the not-entitled code and event, API clients, the billing stores.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T003 [P] Feature-value mapping tests in `ct/feature/auth/domain/FeatureValueMappingTest.kt`: `JsonNull` → `Quota(null)`; `true`/`false` → `Flag`; `5` → `Quota(5)`; a string value is dropped; `planId` is mapped from `entitlements.plan.id`; a stored snapshot written before this change (with `"wallets.max": null`) decodes to `Quota(null)` (FR-003, R-03)
- [X] T004 [P] Contract tests in `ct/feature/billing/data/BillingContractTest.kt` per contracts/billing-api-client.md, with hand-written fixtures mirroring the launch seed (Free: `period_type: "lifetime"`, `is_default: true`, `prices: []`, `sharing.enabled: false`, `sharing.max_members_per_wallet: 1`, `wallets.max: null`; Plus · Monthly / Plus · Yearly with InstaPay prices only), one fixture with a `google_play` price and one with an unknown member and unknown capability key:
  - `listPlans`: GET `…/api/v1/plans`, no `Authorization`, `Accept-Language` sent; 200 decodes in order; unknown member ignored; offline → `Offline`
  - `mySubscription`: GET `me/subscription`, `Authorization` present; paid and all-null free bodies decode; 401 publishes `ApiEvent.SessionEnded`
  - `EntitlementsApi.fetch`: GET `me/entitlements`, `Authorization` present; `null` quota survives decoding; 401 publishes `SessionEnded`
  - any call answering 403 `FEATURE_NOT_ENTITLED` publishes `ApiEvent.FeatureNotEntitled` and returns `Failure(code=FEATURE_NOT_ENTITLED)`
- [X] T005 [P] Error-copy tests: extend `ct/feature/auth/ui/AuthErrorCopyTest.kt` and add `ct/feature/billing/ui/BillingErrorCopyTest.kt` asserting `FEATURE_NOT_ENTITLED` maps to `error_feature_not_entitled`, never a generic message (FR-007, FR-018)
- [X] T006 [P] `BillingDataCleanerTest` in `ct/feature/billing/data/BillingDataCleanerTest.kt`: after `clear()` both `billing_subscription` and `billing_catalogue` keys are gone and other keys are untouched (FR-008)

### Implementation

- [X] T007 Change `FeatureValue.Quota(val limit: Long)` to `Quota(val limit: Long?)` (null = unlimited) and add `val planId: String = ""` as the **last** parameter of `AccountSnapshot` in `cm/feature/auth/domain/model/Account.kt` (a default keeps existing positional calls in `ct/` such as `HomeViewModelTest`, `AccountViewModelTest`, `ResendStateTest` compiling unchanged); rewrite the mapping in `AccountSnapshotDto.toDomain()` in `cm/feature/auth/domain/SessionManager.kt` per data-model §2 (hotspot) (FR-003)
- [X] T008 [P] Add `FEATURE_NOT_ENTITLED(RetryClass.Never)` to `cm/core/network/ApiErrorCode.kt`; add `data object FeatureNotEntitled : ApiEvent` to `cm/core/network/ApiEvents.kt`; publish it in `cm/core/network/HttpClientFactory.kt` next to the `EMAIL_NOT_VERIFIED` branch (hotspots, additive) (R-06)
- [X] T009 [P] Map `FEATURE_NOT_ENTITLED` → `error_feature_not_entitled` in `cm/feature/auth/ui/AuthErrorCopy.kt` and `cm/feature/account/ui/AccountErrorCopy.kt`; add `cm/feature/billing/ui/BillingErrorCopy.kt` exposing the same mapping for later features (FR-007)
- [X] T010 [P] Add `cm/feature/auth/data/EntitlementsApi.kt` (`suspend fun fetch(): ApiResult<EntitlementSummaryDto>`, GET `me/entitlements`) and register it in `cm/feature/auth/di/AuthModule.kt` (R-02)
- [X] T011 [P] Add billing DTOs in `cm/feature/billing/data/dto/BillingDtos.kt` per data-model §1 (`PlanDto`, `PriceDto` with `amountMinor: Long`, `MySubscriptionDto` with every member but `plan` nullable, billing's own `PlanRefDto`, stored `CatalogueRecord(language, plans)`) and `cm/feature/billing/data/BillingApi.kt` (`listPlans()` public, `mySubscription()` bearer; no retry beyond the existing client policy, FR-020)
- [X] T012 [P] Add `cm/feature/billing/data/SubscriptionRepository.kt` (fetch + read/write key `billing_subscription`), `cm/feature/billing/data/CatalogueRepository.kt` (fetch with current language + read/write key `billing_catalogue`) and `cm/feature/billing/data/BillingDataCleaner.kt` (`UserDataCleaner` clearing both); register all three in `BillingModule.kt`, the cleaner with `bind UserDataCleaner::class` (R-07)
- [X] T013 [P] Add `@Serializable data object PlanKey : NavKey` to `cm/navigation/Routes.kt` (hotspot, additive)

**Checkpoint**: T003–T006 pass; nothing user-visible changed except "unlimited" now reads correctly.

---

## Phase 3: User Story 1 — The app knows what the person may do (Priority: P1) 🎯 MVP

**Goal**: one gate, correct and current, for every later feature.

**Independent Test**: Free person: `can(SharingEnabled)` false, `quota(Wallets)` Unlimited; grant Plus, refresh, `can(SharingEnabled)` true and `quota(MembersPerWallet)` = Limited(5).

### Tests for User Story 1 (write first)

- [X] T014 [P] [US1] Gate tests in `ct/feature/billing/domain/EntitlementSetTest.kt`: `can` true/false/missing/wrong-type; `quota` Unlimited (null) / Limited(n) / missing → Limited(0); `allowsAnother` at, below and above the limit and for Unlimited; signed-out state answers off / 0; a downgrade (Plus → Free set) changes only sharing answers (spec §4 "Losing paid access")
- [X] T015 [P] [US1] `SessionManager.refreshEntitlements()` tests in `ct/feature/auth/domain/RefreshEntitlementsTest.kt`: success replaces features and plan in state and snapshot; signed out before the answer arrives → nothing written; another user signed in before the answer → nothing written; offline → state unchanged, no sign-out (FR-008, FR-009)
- [X] T016 [P] [US1] Refresher tests in `ct/feature/billing/domain/EntitlementsRefresherTest.kt` with a fake clock: concurrent triggers produce one call each to entitlements and subscription; foreground within 60 s of the last success is skipped, after 60 s runs; Plan-screen, billing-action and not-entitled triggers are never skipped; subscription is not written when the user changed mid-flight (FR-006, FR-008)
- [X] T017 [P] [US1] Coordinator tests in `ct/feature/billing/domain/BillingSyncCoordinatorTest.kt`: a new `SignedIn` user triggers a subscription read; `onForeground()` while signed out does nothing; `ApiEvent.FeatureNotEntitled` triggers a never-skipped refresh (FR-006, FR-007)
- [X] T018 [P] [US1] `PlanNameNotGatedTest` in `ht/feature/billing/PlanNameNotGatedTest.kt`: scans `shared/src/commonMain/kotlin/com/fares/saveme/feature/` and fails when any file outside `feature/billing/ui/` and `feature/auth/` references `.planId` or `account.plan` (P-12.1, SC-003)

### Implementation for User Story 1

- [X] T019 [US1] Add `suspend fun refreshEntitlements(): RefreshOutcome` to `cm/feature/auth/domain/SessionManager.kt` using `EntitlementsApi` (injected via `AuthModule.kt`), writing the snapshot and state only when the same user id is still signed in (hotspot, additive; R-02, FR-008)
- [X] T020 [P] [US1] Add `cm/feature/billing/domain/Features.kt` (`FlagFeature`, `QuotaFeature` with wire keys `sharing.enabled`, `export.enabled`, `reports.advanced`, `sharing.max_members_per_wallet`, `wallets.max`; `QuotaLimit`; `EntitlementSet`) per data-model §2
- [X] T021 [US1] Add `cm/feature/billing/domain/EntitlementsRefresher.kt` (Mutex + shared in-flight `Deferred`, injected `Clock`, 60 s foreground floor, `RefreshReason` = SignIn/Foreground/PlanScreen/BillingAction/NotEntitled; `SignIn` reads only the subscription because the sign-in answer already carried the entitlements; same-user check before writing the subscription) (R-05)
- [X] T022 [US1] Add `cm/feature/billing/domain/Entitlements.kt` (interface per contracts/entitlements-gate.md + implementation mapping `SessionManager.state` to `StateFlow<EntitlementSet?>`, `refreshAfterBillingAction()` delegating to the refresher) and register it as a single in `BillingModule.kt`
- [X] T023 [US1] Add `cm/feature/billing/domain/BillingSyncCoordinator.kt` (sign-in watcher, `onForeground()`, `ApiEvent.FeatureNotEntitled` collector) registered `createdAtStart = true` in `BillingModule.kt`; in `cm/App.kt` inject it and call `onForeground()` inside the existing `LifecycleStartEffect` (hotspot, two lines)

**Checkpoint**: T014–T018 pass; later features can `get<Entitlements>()`.

---

## Phase 4: User Story 2 — See my plan (Priority: P1)

**Goal**: Account → Plan shows the current plan, what it allows, status and dates; works offline.

**Independent Test**: Free person sees "Free", "Sharing: not included", no dates; after a Plus grant and a refresh, "Plus · Monthly", "Active · Ends on {date}".

### Tests for User Story 2 (write first)

- [X] T024 [P] [US2] Capability-line tests in `ct/feature/billing/ui/plan/CapabilityLinesTest.kt`: sharing off hides the members quota; sharing on with 5 / unlimited members; wallets unlimited / N; export and reports on/off; unknown keys produce no line (FR-013, clarification 2026-10-02)
- [X] T025 [P] [US2] `PlanReducerTest` in `ct/feature/billing/ui/plan/PlanReducerTest.kt` for the current section: free (no status line), active renews / ends, lifetime "No end date", grace with grace end; subscription whose plan id differs from the entitlements' → status hidden; loading placeholder before the first subscription read; stale flag on failed refresh; generic error carries the request id (FR-011, FR-012, FR-015)
- [X] T026 [P] [US2] `PlanViewModelTest` in `ct/feature/billing/ui/plan/PlanViewModelTest.kt`: `Opened` renders cached entitlements and subscription immediately, then triggers a never-skipped refresh; `Refresh` sets `refreshing` then clears it; offline → `stale`; `Back` → `NavigateBack`; dates use the profile timezone with device fallback
- [X] T027 [P] [US2] Account screen tests: extend the Account reducer/ViewModel tests in `ct/feature/account/ui/account/` for `AccountDestination.Plan` → `Navigate(Plan)`

### Implementation for User Story 2

- [X] T028 [P] [US2] Add `PlanCard` to `cm/designsystem/component/Plans.kt` (name, optional period line, optional price via `MoneyText`, capability lines, "Current plan" text label with outline emphasis, merged semantics so screen readers announce one item, `modifier` for test tags), a catalogue entry in `cm/designsystem/catalog/CatalogSections.kt`, and screenshots in `ht/designsystem/ComponentScreenshotTest.kt` (EN, AR RTL, 2× font); check it against the "Save Me Mobile Design System" canvas first — if the canvas has no plan card, build it from existing tokens only and list it in `summary.md` as needing the owner's design review (P-07.3, FR-019)
- [X] T029 [P] [US2] Add domain models `cm/feature/billing/domain/model/Subscription.kt` (`SubscriptionStatus` Active/Grace, `Subscription` per data-model §2, bad instants → null) and `cm/feature/billing/domain/PlanOverview.kt` exposing `subscription: Flow<Subscription?>` from `SubscriptionRepository` for the Plan screen; register it in `BillingModule.kt` (the `Entitlements` gate stays capability-only, contracts/entitlements-gate.md)
- [X] T030 [US2] Add `cm/feature/billing/ui/plan/CapabilityLines.kt` (pure mapping `EntitlementSet` → list of string resources + args) and `cm/feature/billing/ui/plan/PlanContract.kt` (current section of `PlanState`, `PlanIntent` Opened/Refresh/Back, `PlanEffect.NavigateBack`, `PlanChange`, `reduce`) per data-model §4–5
- [X] T031 [US2] Add `cm/feature/billing/ui/plan/PlanViewModel.kt` (lambdas: entitlements flow, subscription flow, refresh(PlanScreen), timezone flow from `ProfileSyncer` like `profileTimeZone` in AccountModule — re-implemented locally in `BillingModule.kt`, not by editing AccountModule) and register it in `BillingModule.kt`
- [X] T032 [US2] Add `cm/feature/billing/ui/plan/PlanScreen.kt` (`AppScreen`, back bar, refresh `AppIconButton` with `AppIcons.Sync` + `cd_refresh` tagged `plan.refresh`, current section tagged `plan.current`/`plan.current.name`/`plan.current.status`, stale `InlineAlert` tagged `plan.stale`, `Skeleton` while loading, no purchase action; a reserved, empty billing-actions slot) and `PlanRoute.kt` + `@Preview`
- [X] T033 [US2] Add the "Plan" row: `AccountDestination.Plan` in `cm/feature/account/ui/account/AccountContract.kt`, an `AppListItem` tagged `account.plan` in `AccountScreen.kt`, routing in `AccountRoute.kt`; wire `PlanKey` → `PlanRoute` and Account → Plan in `cm/navigation/AppNavHost.kt` (hotspot, additive)
- [X] T034 [P] [US2] Plan screen screenshots (current section: free, active, grace, stale) in `ht/feature/billing/PlanScreenScreenshotTest.kt` (EN, AR RTL, 2× font)

**Checkpoint**: US2 scenarios 1–6 pass manually on the dev backend (quickstart §3 rows 1–3).

---

## Phase 5: User Story 3 — Browse the plans (Priority: P2)

**Goal**: the catalogue in order, localised, current plan marked, Google Play price only.

**Independent Test**: Plan screen lists Free, Plus · Monthly, Plus · Yearly in EN and AR; Free marked current; no price shown with the launch seed.

### Tests for User Story 3 (write first)

- [X] T035 [P] [US3] Catalogue mapping tests in `ct/feature/billing/domain/CataloguePlanTest.kt`: price = the `google_play` entry only, InstaPay-only plans → no price (SC-004); `period_type` monthly/yearly/lifetime/custom(`period_days`)/unknown → `BillingPeriod`; the default plan shows no period (FR-013, FR-014)
- [X] T036 [P] [US3] Extend `PlanReducerTest` and `PlanViewModelTest` for the catalogue: order preserved; `isCurrent` by id; retired current plan → no card marked; never-loaded + offline → `Unavailable` with retry; cached catalogue shown while reloading; language change triggers a re-request (FR-013, FR-015, FR-017)

### Implementation for User Story 3

- [X] T037 [US3] Add `cm/feature/billing/domain/model/CataloguePlan.kt` (`BillingPeriod`, `CataloguePlan`, mapper keeping only the `google_play` price as `Money(amountMinor, currency)`) and add to `PlanOverview` a `catalogue` flow + `loadCatalogue()` over `CatalogueRepository` that re-requests when the stored language differs from the app language (R-07, R-08)
- [X] T038 [US3] Extend `PlanContract.kt` and `PlanViewModel.kt` with the catalogue section (`catalogue`, `catalogueStatus`, `CatalogueLoaded`/`CatalogueUnavailable` changes)
- [X] T039 [US3] Render the catalogue in `PlanScreen.kt` with `PlanCard` per plan tagged `plan.card.<index>`, `plan.card.<index>.current`, `plan.card.<index>.price`; `EmptyState` with retry tagged `plan.catalogue.retry` when unavailable
- [X] T040 [P] [US3] Plan screen catalogue screenshots in `ht/feature/billing/PlanScreenScreenshotTest.kt` (with and without a Google Play price, Arabic names, 2× font)

**Checkpoint**: US3 scenarios 1–6 pass manually.

---

## Phase 6: Polish & Cross-Cutting

- [X] T041 Extend `.specify/scripts/bash/run-e2e.sh` per research R-12: seed suffix `+plus` (grant Plus · Monthly for 30 days via `OperatorSubscriptions::grant($operatorId, $userId, $planId, 30, 'e2e')`; corrected at T042: fixed-UUID operator id, plan looked up by code `plus_monthly`), `grant_plus <email>` helper, and two-part flows (`*.part1.yaml` then `# e2e-between: grant-plus` then `*.part2.yaml` without resetting the app; part 2 taps `plan.refresh` or reopens Plan, never relies on a foreground return that the 60 s floor may skip) (hotspot, additive)
- [X] T042 Run `/speckit-e2e 005-plans-entitlements` to generate and run flows in `.maestro/005-plans-entitlements/` for the 11 device-reachable scenarios listed in spec SC-007, including the checkpoint (US2-S5 two-part: Free → grant → Plan refresh shows Plus, no sign-in); write `specs/005-plans-entitlements/e2e-report.md` (SC-007)
  - Status: the 13 flow files (11 scenarios; US1-S3 and US2-S5 are two-part) are written in `.maestro/005-plans-entitlements/` and `run-e2e.sh` is extended; the device run and `e2e-report.md` are left for the separate E2E step.
- [X] T043 [P] Arabic copy review of the spec 005 string block against the glossary (P-01, P-06), a scan that no string mentions InstaPay, checkout or "token" (SC-004), and a scan that no `Logger` call under `cm/feature/billing/` or in `SessionManager.refreshEntitlements` carries subscription fields or prices (FR-021)
- [X] T044 Run quickstart.md §1 (`./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest`), §3 manual checks, and `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS must compile)
- [X] T045 Rerun `.specify/scripts/bash/check-backend-contract.sh` and record any drift since `055afa1` in spec §5 (integration checkpoint, briefs protocol rule 4)
- [X] T046 Write `specs/005-plans-entitlements/summary.md`

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: needs Phase 1; blocks every story. T007 before T019; T010 before T019; T011/T012 before T016.
- **US1 (Phase 3)**: needs Phase 2. MVP.
- **US2 (Phase 4)**: needs US1 (the Plan screen reads the gate and refresher).
- **US3 (Phase 5)**: needs US2's screen (T030–T032); its tests and mapper (T035, T037) can start after Phase 2.
- **Polish (Phase 6)**: T041 before T042; T046 last.

### Within each story

Tests first (they must fail), then domain, then UI, then wiring (`AppNavHost`, `BillingModule`).

### Parallel opportunities

- T001 ∥ T002.
- T003 ∥ T004 ∥ T005 ∥ T006; then T008 ∥ T009 ∥ T010 ∥ T011 ∥ T012 ∥ T013 (T007 alone: it touches `SessionManager.kt`).
- US1 tests T014–T018 together; T020 alongside T019.
- US2: T024–T027 together; T028 ∥ T029; T034 after T032.
- US3: T035 ∥ T036; T040 after T039.

## Parallel Example: User Story 1

```text
T014 EntitlementSetTest ∥ T015 RefreshEntitlementsTest ∥ T016 RefresherTest ∥ T017 CoordinatorTest ∥ T018 PlanNameNotGatedTest
then T019 SessionManager.refreshEntitlements ∥ T020 Features.kt
then T021 Refresher → T022 Entitlements → T023 Coordinator + App.kt
```

## Implementation Strategy

### MVP first (US1)

1. Phase 1 + Phase 2 (fixes "unlimited read as 0" on its own).
2. Phase 3: later features (C4 wallets) can gate on `Entitlements`.
3. Stop and check: US1 unit tests and a manual Plus grant + foreground.

### Incremental delivery

1. US1 → the gate.
2. US2 → the person sees their plan.
3. US3 → the catalogue.
4. Phase 6 → E2E (brief checkpoint), contract recheck, summary.
