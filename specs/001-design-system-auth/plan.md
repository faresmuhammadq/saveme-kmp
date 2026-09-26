# Implementation Plan: Design System Components & Authentication Flows

**Branch**: `001-design-system-auth` (spec directory; work currently on `main`) | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-design-system-auth/spec.md`

## Summary

Build the Save Me mobile design system — tokens (colour, type, spacing, shape, elevation, money colours) and ~25 reusable Compose Multiplatform components matching the "Save Me Mobile Design System" canvas, with RTL/Arabic and 200% font-scale support, plus a dev-only catalogue. On top of it, build the auth flows (entry, sign in, register, check inbox, verify email, forgot/reset password, sign-out, verify prompt) against the backend Identity API: Ktor client with problem-details error mapping by `code`, credential in platform secure storage, session state driving conditional navigation, mail links routed into the app (verified HTTPS in staging/prod; IP links + paste screen in dev), and per-environment base URLs via Android product flavors. Android is the only live-API target; iOS compiles the same UI.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: Compose Multiplatform 1.12.1 + Material 3; lifecycle-viewmodel (multiplatform); Navigation 3 multiplatform (fallback: Navigation Compose, R-09); Ktor Client (OkHttp / Darwin) + kotlinx.serialization; kotlinx-coroutines; kotlinx-datetime; Koin; Jetpack DataStore; Compose resources (fonts, strings). Versions pinned in `gradle/libs.versions.toml` at implementation.

**Storage**: Android Keystore-encrypted DataStore for the credential; plain private DataStore for device id and account snapshot (backup-excluded); iOS stub actual until iOS enters scope (R-06, R-07, P-15)

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor MockEngine (contract), Compose multiplatform UI tests, Roborazzi screenshot tests on Android host tests (R-15)

**Target Platform**: Android minSdk 24 / targetSdk 37 (live API); iOS arm64 + simulator (UI only, no live auth)

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: cold relaunch to signed-in area < 2 s (SC-006); 60 fps component rendering; auth form submit → result shown as soon as the response arrives, with spinner after 150 ms

**Constraints**: credential never outside secure storage or logs; cleartext only in dev flavor; error copy by `code` only; every request carries platform/version/language/correlation headers; `versionName` must be semver; touch targets ≥ 48 dp; en + ar with full RTL

**Scale/Scope**: ~25 components, 9 auth screens + 2 dev screens, 7 endpoints, 2 languages, 3 environments

## Constitution Check

*Constitution v1.0.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)** and **post-design (after Phase 1)** — results identical; post-design evidence cites the Phase 1 artifacts.

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | UI copy says "session"/"device"; `token` only as internal wire member name (data-model §3); glossary terms User, Profile, Device Session |
| P-02 API contract fidelity | PASS | Request DTOs mirror identity.yaml exactly, decoder ignores unknown keys (R-02); `ApiErrorCode` branch on `code` (R-03, contracts/identity-api-client.md); required headers via `defaultRequest` + semver `versionName` 0.1.0 (R-05); no auto-retry of auth POSTs (R-04) |
| P-03 Money | PASS | `MoneyFormatter` over `Long` minor units + ISO code, digits by currency, U+2212 (R-14); `MoneyText` expense = neutral ink (contracts/design-system-components.md) |
| P-04 Offline-first | N-A | Auth and account data are not syncable (rule 6; glossary: User "Sync: no"); `AccountSnapshot` is a read cache, not a syncable entity |
| P-05 Time | PASS | `issuedAt`, `deletionScheduledFor` as UTC `Instant` (data-model §1); registration sends device IANA timezone, never converts |
| P-06 Localisation & RTL | PASS | `values/` + `values-ar/` string resources, Arabic type rules, RTL mirroring (R-13); server `detail` never shown (contracts/identity-api-client.md) |
| P-07 Design system only | PASS | Auth screens use only `designsystem` components (FR-010, SC-008); tokens in `designsystem.tokens` (data-model §4) |
| P-08 Accessibility | PASS | 48 dp targets, required `contentDescription` on icon buttons, percent written on budget bars (contracts/design-system-components.md Guarantees); fontScale 2.0 screenshot tests (R-15) |
| P-09 Security & privacy | PASS | Keystore-encrypted DataStore, backup-excluded (R-06); cleartext only in `dev` flavor network config (R-11); uniform copy for INVALID_CREDENTIALS and register/forgot confirmations; 401 clears session (R-08); tokens held only in screen state, never logged (contracts/deep-links.md) |
| P-10 Shared-first architecture | PASS | All logic in `commonMain` packages `core`, `feature.auth.{data,domain,ui}`; platform code only via `SecureStore`/`ClientInfo` expect/actual; `designsystem` depends on nothing app-level (Project Structure) |
| P-11 Naming | PASS | Symbols renamed to `AppTheme`, `AppTextField`, `AppFab`, …; Gradle property `devApiHost`; root package `com.fares.saveme` grandfathered |
| P-12 Entitlements & billing | PASS | `features` flags stored from sign-in, plan label display-only (data-model §1); no billing surface in this feature |
| P-13 Observability | PASS | UUIDv7 `X-Correlation-Id` per request (R-05); `request_id` shown on unexpected errors (FR-016) |
| P-14 Quality gates | FAIL (non-NN) | Rules 1–4 met (R-15: contract, screenshot, invariant tests). Rule 5: new dependencies await owner approval — see Complexity Tracking |
| P-15 Scope guard | PASS | Android only live target; iOS actuals limited to what compilation needs (stub `SecureStore`, no sign-in); light theme only |

No non-negotiable FAIL → plan not blocked.

## Project Structure

### Documentation (this feature)

```text
specs/001-design-system-auth/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── identity-api-client.md
│   ├── deep-links.md
│   └── design-system-components.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (not created here)
```

### Source Code (repository root)

```text
shared/src/
├── commonMain/
│   ├── composeResources/
│   │   ├── font/                       # Hanken Grotesk, IBM Plex Sans Arabic
│   │   ├── values/strings.xml          # en
│   │   └── values-ar/strings.xml       # ar
│   └── kotlin/com/fares/saveme/
│       ├── App.kt                      # AppTheme + root navigation (replaces template)
│       ├── core/
│       │   ├── config/AppConfig.kt     # baseUrl, environment, isDebugTools
│       │   ├── platform/ClientInfo.kt  # expect: platform, appVersion, deviceName, timezone
│       │   ├── network/                # HttpClientFactory, headers, ProblemDetails, ApiErrorCode, ApiResult
│       │   ├── storage/                # SecureStore (expect), KeyValueStore, DeviceIdentityStore
│       │   └── di/                     # Koin modules
│       ├── designsystem/
│       │   ├── tokens/                 # Colors, MoneyColors, Typography, Spacing, Shapes, Elevation, Sizes
│       │   ├── theme/AppTheme.kt
│       │   ├── money/MoneyFormatter.kt
│       │   ├── component/              # one file per component family (buttons, bars, fields, cards, lists, feedback, sheet, empty)
│       │   └── catalog/ComponentCatalogScreen.kt
│       ├── feature/auth/
│       │   ├── data/                   # IdentityApi (Ktor), DTOs, AuthRepository, SessionStore
│       │   ├── domain/                 # SessionManager, AuthState, ClientValidation, AuthLink + AuthLinkParser
│       │   └── ui/                     # entry, signin, register, checkinbox, verify, forgot, reset, home placeholder, updaterequired; ViewModels
│       ├── devtools/PasteLinkScreen.kt
│       └── navigation/                 # NavKeys, auth vs signed-in back stacks, deep-link intake
├── androidMain/kotlin/com/fares/saveme/  # SecureStore (Keystore+DataStore), ClientInfo, Ktor OkHttp engine
├── iosMain/kotlin/com/fares/saveme/      # stub SecureStore, ClientInfo, Darwin engine (compile-only, P-15)
├── commonTest/kotlin/com/fares/saveme/   # unit, contract (MockEngine), Compose UI tests
└── androidHostTest/kotlin/com/fares/saveme/  # Roborazzi screenshot tests

androidApp/
├── build.gradle.kts                    # flavors dev/staging/prod, buildConfig, manifestPlaceholders, versionName 0.1.0
└── src/
    ├── main/AndroidManifest.xml        # https verified link filter, backup rules, MainActivity intent handling
    ├── main/res/xml/                   # data_extraction_rules, backup_rules
    └── dev/
        ├── AndroidManifest.xml         # http link filter, networkSecurityConfig
        └── res/xml/network_security_config.xml
```

**Structure Decision**: Single `:shared` KMP module with package boundaries (`designsystem`, `core`, `feature.auth`, `navigation`, `devtools`) and a thin `:androidApp` host carrying flavors, manifest, and link filters (research R-01, R-11, R-12). `designsystem` depends on nothing else in the app so it can be extracted to its own module later.

## Implementation phases (input to /speckit-tasks)

1. **Setup** — dependencies in version catalog; flavors + buildConfig + semver versionName; fonts and string resources; Koin bootstrap; `AppConfig` wiring from `MainActivity` / `MainViewController`.
2. **Foundation (US1)** — tokens, `AppTheme`, `MoneyFormatter`, components by family, catalogue, screenshot + a11y tests.
3. **Core network & storage** — `HttpClientFactory` with headers, problem decoding, `ApiErrorCode` + retry class, `SecureStore` actuals, device identity, contract-test harness.
4. **US2 Sign in** — IdentityApi.signIn, SessionManager/AuthState, root navigation switch, SignIn screen, 401 handling, UpdateRequired screen.
5. **US3 Register & verify** — Register, CheckInbox, VerifyEmail, verify prompt + resend on home placeholder, `AuthLinkParser`, manifest filters, dev Paste link.
6. **US4 Password recovery** — Forgot, Reset, local session clear.
7. **US5 Sign out** — confirm dialog/sheet, offline-safe local clear.
8. **Polish** — Arabic copy review, release-manifest check, quickstart run-through.

## External dependencies (not in this repo)

- Backend: dev container reachable over LAN HTTP; mail catcher; confirm link paths/`token` parameter (contracts/deep-links.md); `assetlinks.json` on staging/prod hosts; minimum-version config for `android`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| P-14.5 new dependencies pending owner approval: Ktor client (+OkHttp/Darwin engines, content negotiation), kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime, Koin, Jetpack DataStore, Navigation 3 multiplatform, Turbine, Roborazzi (+Robolectric) | HTTP, JSON, secure/persistent storage, navigation, DI and screenshot testing are required by FR-012, FR-011, FR-027, P-14.4 | Hand-rolled HTTP/JSON/navigation would be larger and less tested; resolves to PASS once the owner approves this list |
