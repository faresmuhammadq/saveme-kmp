# Implementation Plan: Plans & Entitlements (client)

**Branch**: `005-plans-entitlements` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-plans-entitlements/spec.md`

## Summary

Give every later feature one entitlements gate (`can(flag)`, `quota(quota)`) backed by the
entitlements already cached in the account snapshot, fix the cache's reading of `null` quotas
(unlimited, not 0), keep it fresh (sign-in, foreground, Plan screen, billing action,
`FEATURE_NOT_ENTITLED`), and add a Plan screen reached from Account that shows the current plan,
the subscription's status and dates, and the catalogue with Google-Play-only prices. No purchase
action. A new `feature.billing` package holds the work so the in-flight sync-engine branch's files
stay mostly untouched.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only: Compose Multiplatform 1.12.1 + Material 3, MVI base (`MviViewModel`), Navigation 3, Ktor 3.6.0, kotlinx.serialization, coroutines, Koin 4.2.2, Kermit. No new dependencies (R-13).

**Storage**: existing `KeyValueStore`: the account snapshot (entitlements, unchanged JSON shape) plus two billing records (`billing_subscription`, `billing_catalogue`). No Room change (R-07).

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor MockEngine contract tests, Roborazzi screenshots, a JVM source-scan test (`PlanNameNotGatedTest`), Maestro E2E via `/speckit-e2e`.

**Target Platform**: Android minSdk 24 / targetSdk 37 (live API); iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: Plan screen renders cached data in < 100 ms after navigation; a refresh is at most 2 parallel GETs; foreground refresh at most once per 60 s.

**Constraints**:
- Gate on features, never the plan name or id (P-12.1).
- No purchase, checkout, InstaPay action or non-Google-Play price (P-12.2–P-12.4).
- Refresh answers after sign-out are discarded; sign-out clears billing records (P-09).
- Minimal edits to sync-engine hotspots (see Merge hotspots).
- EN + AR with RTL, 200% font.

**Scale/Scope**: 1 new screen, 1 new design-system component, 3 endpoints (1 public), 1 new error code + 1 core event, ~30 string keys × 2 languages, 11 E2E scenarios (7 more proven by unit/contract tests, spec SC-007).

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: all principles PASS or N-A on the spec alone. P-12 is the core
of the feature (rules 1 and 3 drive FR-002, FR-010, FR-014). P-04 is N-A (nothing syncable). No
FAIL; proceed.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Plan, Subscription, Entitlement, billing period (spec §2); copy keys in contracts/ui-and-e2e.md use "plan", grace shown as "Payment due"; Arabic محفظة for wallet |
| P-02 API contract fidelity | PASS | DTOs mirror billing.yaml, unknown members ignored (data-model §1); `FEATURE_NOT_ENTITLED` branches on code via `ApiEvent` (R-06); reads use the existing retry policy only (contracts/billing-api-client.md) |
| P-03 Money | PASS | `PriceDto.amountMinor: Long` + currency → shared `Money`, formatted by `formatMoney` with currency fraction digits (R-08, data-model §2) |
| P-04 Offline-first | N-A | No syncable entity; account/billing data is exempt (rule 6). Offline still works from cached records (R-07) |
| P-05 Time | PASS | Billing-period instants held as `Instant`; displayed as dates in the profile timezone, device fallback (R-11) |
| P-06 Localisation & RTL | PASS | All copy in `values/` + `values-ar/`; server plan names requested via `Accept-Language`; server `detail` never shown (FEATURE_NOT_ENTITLED copy by code); screenshots AR RTL |
| P-07 Design system only | PASS | Plan screen from existing components; the one new visual (`PlanCard`) is added to `designsystem` first with catalogue entry and screenshots (R-09) |
| P-08 Accessibility | PASS | "Current plan" is text, not colour only; refresh icon has a content description and 48 dp target; 2× font screenshots |
| P-09 Security & privacy | PASS | No credential or amount logged; refresh-after-sign-out discarded and billing records cleared by `BillingDataCleaner` (data-model §3) |
| P-10 Shared-first architecture | PASS | All in `commonMain`; `feature.billing` uses only `auth.domain` (`SessionManager`) and `account.domain` (`ProfileSyncer` for timezone); the `/me/entitlements` call lives in `auth.data` because auth owns the snapshot (R-02); core publishes `ApiEvent.FeatureNotEntitled` without importing a feature (P-10.7) |
| P-11 Naming | PASS | `feature.billing`, `Entitlements`, `PlanCard`, `PlanKey`; no codename |
| P-12 Entitlements & billing | PASS | Typed gate without plan access (contracts/entitlements-gate.md) + `PlanNameNotGatedTest`; no purchase/checkout UI; prices filtered to `google_play` in the mapper (R-08) |
| P-13 Observability | PASS | Correlation id per request unchanged; unexpected refresh errors show `request_id` through the existing generic error copy |
| P-14 Quality gates | PASS | Contract tests per endpoint and handled code; invariant tests per spec §4; screenshot tests for `PlanCard` and the Plan screen; no new dependency |
| P-15 Scope guard | PASS | No out-of-scope capability; web checkout explicitly excluded |
| P-16 MVI presentation | PASS | Plan screen: `PlanContract.kt` (State/Intent/Effect/Change + pure `reduce`), `PlanViewModel` on `MviViewModel`, `PlanRoute`, stateless `PlanScreen`; reducer and store tests (data-model §4) |

No FAIL. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/005-plans-entitlements/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── billing-api-client.md
│   ├── entitlements-gate.md
│   └── ui-and-e2e.md
├── checklists/          # requirements.md (specify), entitlements.md (checklist)
├── tasks.md             # /speckit-tasks
└── summary.md           # last task
```

### Source Code (repository root)

```text
shared/src/commonMain/kotlin/com/fares/saveme/
├── feature/billing/                         # NEW package (R-01)
│   ├── data/BillingApi.kt                   # GET plans, me/subscription
│   ├── data/SubscriptionRepository.kt       # fetch + KV record billing_subscription
│   ├── data/CatalogueRepository.kt          # fetch + KV record billing_catalogue (with language)
│   ├── data/BillingDataCleaner.kt           # UserDataCleaner: clears both records
│   ├── data/dto/BillingDtos.kt
│   ├── domain/Features.kt                   # FlagFeature, QuotaFeature, QuotaLimit, EntitlementSet
│   ├── domain/Entitlements.kt               # interface + implementation over SessionManager.state
│   ├── domain/EntitlementsRefresher.kt      # single flight, 60 s floor, same-user check
│   ├── domain/BillingSyncCoordinator.kt     # sign-in / foreground / FeatureNotEntitled triggers
│   ├── domain/PlanOverview.kt               # subscription + catalogue flows for the Plan screen
│   ├── domain/model/                        # Subscription, CataloguePlan, BillingPeriod
│   ├── ui/plan/                             # PlanContract, PlanViewModel, PlanRoute, PlanScreen, CapabilityLines
│   ├── ui/BillingErrorCopy.kt               # FEATURE_NOT_ENTITLED copy
│   └── di/BillingModule.kt
├── feature/auth/data/EntitlementsApi.kt     # NEW: GET me/entitlements (R-02)
└── designsystem/component/Plans.kt          # NEW: PlanCard (R-09)
shared/src/commonTest/kotlin/com/fares/saveme/feature/billing/…   # NEW tests
shared/src/androidHostTest/kotlin/com/fares/saveme/feature/billing/
├── PlanScreenScreenshotTest.kt              # NEW
└── PlanNameNotGatedTest.kt                  # NEW source scan
.maestro/005-plans-entitlements/              # NEW flows (via /speckit-e2e)
```

**Structure Decision**: Single `:shared` module; new `feature.billing` package with the standard
`data`/`domain`/`ui`/`di` split. Auth keeps ownership of the cached entitlements (R-02); billing
reads them through `auth.domain`.

## Merge hotspots

Files this feature must edit that already exist on `main`. The sync-engine branch
(`004-sync-engine`, uncommitted in the main checkout) is known to touch `AccountModule`,
`AccountSyncCoordinator`, `AppDatabase`, `strings.xml` and `HomeScreen`; this plan **does not
edit** `AccountModule.kt`, `AccountSyncCoordinator.kt`, `AppDatabase.kt` or `HomeScreen.kt`.

| File | Change here | Overlap risk with 004 | Merge note |
|---|---|---|---|
| `shared/src/commonMain/composeResources/values/strings.xml` | ~30 keys (`plan_*`, `account_plan`, `cd_refresh`, `error_feature_not_entitled`) | **Known** | Append-only block at the end under a `<!-- Plans & entitlements (spec 005) -->` comment; resolve by keeping both blocks |
| `shared/src/commonMain/composeResources/values-ar/strings.xml` | same keys in Arabic | **Known** | Same append-only block |
| `shared/src/commonMain/kotlin/com/fares/saveme/App.kt` | inject `BillingSyncCoordinator`; call `onForeground()` in the existing `LifecycleStartEffect` | Likely (sync engine may add a foreground trigger) | Two-line change; keep both calls |
| `shared/src/commonMain/kotlin/com/fares/saveme/AppModules.kt` | add `billingModule` to the list | Likely (sync module) | One list element; keep both |
| `core/network/ApiErrorCode.kt` | + `FEATURE_NOT_ENTITLED` | Possible (sync codes such as idempotency/cursor) | Enum entries; keep both |
| `core/network/ApiEvents.kt`, `core/network/HttpClientFactory.kt` | + `ApiEvent.FeatureNotEntitled`, one `when` branch | Possible | Additive |
| `feature/auth/domain/SessionManager.kt` | + `refreshEntitlements()`; `toDomain()` maps `null` → unlimited and adds `planId` | Possible (session/sync hooks) | Additive method + mapping fix in `toDomain()` |
| `feature/auth/domain/model/Account.kt` | `FeatureValue.Quota(limit: Long?)`, `AccountSnapshot.planId` | Low | — |
| `feature/auth/di/AuthModule.kt` | register `EntitlementsApi`, pass it to `SessionManager` | Low | — |
| `feature/account/ui/account/AccountContract.kt`, `AccountScreen.kt`, `AccountRoute.kt` | `AccountDestination.Plan` + one row | Low (004 not known to edit the account screen) | — |
| `feature/account/ui/AccountErrorCopy.kt`, `feature/auth/ui/AuthErrorCopy.kt` | map `FEATURE_NOT_ENTITLED` to its copy | Low | — |
| `navigation/Routes.kt`, `navigation/AppNavHost.kt` | `PlanKey` + entry; Account → Plan wiring | Possible (sync screens) | Additive |
| `designsystem/catalog/CatalogSections.kt`, `androidHostTest/.../ComponentScreenshotTest.kt` | `PlanCard` catalogue entry and screenshots | Low | — |
| `.specify/scripts/bash/run-e2e.sh` | `+plus` seed, `grant_plus`, two-part flows (R-12) | Possible (004 may add seeds) | Additive functions |

## Implementation phases (input to /speckit-tasks)

1. **Foundation**
   - `FeatureValue.Quota(Long?)`, `planId`, mapping fix + tests (FR-003)
   - `FEATURE_NOT_ENTITLED` code, `ApiEvent.FeatureNotEntitled`, copy (FR-007, FR-018)
   - billing DTOs, `BillingApi`, `EntitlementsApi` + contract tests
   - `PlanKey`, billing Koin module, `BillingDataCleaner`
2. **US1 Gate**: `EntitlementSet`/`Entitlements`, `SessionManager.refreshEntitlements()`,
   `EntitlementsRefresher`, `BillingSyncCoordinator` (sign-in, foreground, event), App.kt hook,
   `PlanNameNotGatedTest`.
3. **US2 My plan**: subscription repository; `PlanCard`; Plan screen current section (status,
   dates, stale/offline); Account row and navigation.
4. **US3 Catalogue**: catalogue repository (language-aware); catalogue section with
   Google-Play-only price, current mark, unavailable/retry.
5. **E2E**: `run-e2e.sh` `+plus` seed and two-part flows; `/speckit-e2e 005`.
6. **Polish**: Arabic copy review, screenshots, quickstart run, `summary.md`.

## External dependencies (not in this repo)

- Backend contract gaps (spec §9): per-key `Features` schema and billing.yaml examples; Google
  Play prices in the catalogue (B10). Neither blocks this plan.

## Complexity Tracking

None.
