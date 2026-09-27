# Phase 0 Research: Design System Components & Authentication Flows

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-09-26

Every Technical Context unknown is resolved below. Library versions are not pinned here: each is added to `gradle/libs.versions.toml` at its latest stable release compatible with Kotlin 2.4.20 and Compose Multiplatform 1.12.1 when the task that introduces it runs.

---

## R-01 Module layout

- **Decision**: Keep one KMP module, `:shared`, organised by package: `designsystem` (tokens, theme, components, catalogue), `core` (config, network, storage, platform), `feature.auth` (data, domain, UI). `:androidApp` stays a thin host.
- **Rationale**: The project is a fresh template with one screen. Separate Gradle modules add build configuration (KMP targets, Android library blocks, resources) for no present consumer. Package boundaries plus an internal-visibility discipline give the same separation; the `designsystem` package can be lifted into its own module later with no code changes because it depends on nothing else in the app.
- **Alternatives considered**: `:designsystem` + `:core` + `:feature-auth` modules — cleaner boundaries and faster incremental builds at scale, rejected for now as premature. Revisit when a second feature module arrives.

## R-02 HTTP client and serialization

- **Decision**: Ktor Client (OkHttp engine on Android, Darwin on iOS) with `ContentNegotiation` + kotlinx.serialization JSON, `ignoreUnknownKeys = true`, `explicitNulls = false` on decode; request bodies encode only declared fields.
- **Rationale**: The multiplatform standard; engines per platform; MockEngine gives contract tests in `commonTest`. `ignoreUnknownKeys` satisfies the API rule "a client ignores response members it does not recognise". Unknown request members are refused by the server (422 UNKNOWN_FIELD), so request DTOs mirror the OpenAPI schemas exactly.
- **Alternatives considered**: Ktorfit (annotation layer over Ktor — adds KSP for seven endpoints), Retrofit (Android-only).

## R-03 Problem-details and error mapping

- **Decision**: Every non-2xx body is decoded as `ProblemDetails` (`type`, `title`, `status`, `detail`, `code`, `request_id`, optional `errors[]` with `pointer` + `code`, `retry_after_seconds`, `minimum_version`). `code` maps to an `ApiErrorCode` enum with an `Unknown(raw)` fallback; the UI maps `ApiErrorCode` → localised string resource. `detail` is never shown. The error catalogue's `retryable` value is encoded as a property on the enum.
- **Rationale**: API conventions: "branch on `code`, never on the message"; FR-014/FR-016/FR-017.
- **Alternatives considered**: Branching on HTTP status — rejected by the conventions (409 carries two different retry classes).

## R-04 Retry policy

- **Decision**: No automatic retry on any auth POST (sign-in, register, reset, forgot, verify, resend, sign-out). Transport failures (no connection, timeout) surface as an `Offline`/`Timeout` result for the UI to offer "try again". `Retry-After` / `retry_after_seconds` is parsed and shown as a wait time for `RATE_LIMITED` and `ACCOUNT_LOCKED`. The retry-with-backoff helper for `automatic` codes is built in `core.network` but only enabled for idempotent reads (none in this feature).
- **Rationale**: FR-017 forbids auto-retrying credential submissions; register/forgot/resend are rate-budgeted server-side, so silent retries would burn the person's hourly budget.

## R-05 Request headers

- **Decision**: A Ktor `defaultRequest` plugin adds on every call: `Accept: application/json`, `X-Client-Platform: android`, `X-Client-Version: <semver>`, `Accept-Language: ar|en` (from the current app locale, default `en`), `X-Correlation-Id: <UUIDv7>` per request. `Authorization: Bearer <credential>` is added only when a stored session exists and the operation is not public.
- **Rationale**: headers.yaml marks platform and version required on every request (absent → 400 CLIENT_VERSION_REQUIRED). Correlation ID lets support join client logs with server logs.
- **Consequence**: `versionName` in `androidApp/build.gradle.kts` must be semver — `1.0.0`, the dev backend's configured minimum for `android` (found during implementation; `0.1.0` got 426) — because the template's `1.0` fails the `^\d+\.\d+\.\d+$` pattern and every request would be 400 CLIENT_VERSION_INVALID. iOS sends `android` nowhere — iOS live calls are out of scope (spec clarification); the iOS `ClientInfo` actual reports its own platform and the auth repository short-circuits with an "unsupported platform" result so nothing is sent.

## R-06 Secure credential storage

- **Decision**: `expect interface`-style `SecureStore` in common with platform actuals:
  - **Android**: AES-256-GCM key generated in the Android Keystore (non-exportable), ciphertext stored in a private Jetpack DataStore (Preferences) file excluded from backup (`android:allowBackup` rules / `dataExtractionRules` exclude it).
  - **iOS**: stub actual that stores nothing (iOS never signs in; constitution P-15 forbids iOS-specific work in MVP). When iOS enters scope: Keychain generic password, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
- **Rationale**: FR-012. `EncryptedSharedPreferences` (security-crypto) is deprecated; Keystore + DataStore is the current Android guidance. Excluding from backup stops a credential being restored onto another device, which would break the one-credential-per-device rule.
- **Alternatives considered**: Tink-based AEAD over DataStore — equivalent security, extra dependency; third-party KMP secure-settings libraries — viable, but a 60-line actual per platform is simpler to audit for a credential.

## R-07 Device identity and session identity

- **Decision**: `device_id`: random UUID generated on first launch, stored in the same private DataStore (not secret, but backup-excluded so a restored install gets a new one). `session_id`: `kotlin.uuid.Uuid.generateV7()` generated when the person taps "Sign in"; the same value is reused for any retry of that attempt and replaced only after a successful sign-in or an explicit new attempt from a clean form. Device name from `Build.MANUFACTURER` + `Build.MODEL`, truncated to 100.
- **Rationale**: DeviceDescription schema; idempotency of sign-in per `session_id` (identity.yaml); `device_id` pattern `^[A-Za-z0-9._:-]{1,64}$` accepts a UUID string.

## R-08 Session lifecycle and 401 handling

- **Decision**: A single `SessionManager` in `feature.auth.domain` (application scope) exposes `StateFlow<AuthState>` (`Unknown` → `SignedOut` | `SignedIn(account)`). The HTTP layer in `core.network` never imports the feature (constitution P-10.7): it publishes `ApiEvent`s on an application-scoped `ApiEvents` stream — `SessionEnded` (401 on a credentialed request), `UpdateRequired(minimumVersion)` (426), `EmailNotVerified` (403). `SessionManager` subscribes to `SessionEnded` and clears the store; `AppNavHost` subscribes to `UpdateRequired` and `EmailNotVerified`. Orchestration lives in domain use cases (`SignInUseCase`, `VerifyEmailUseCase`, `ResetPasswordUseCase`, `SignOutUseCase`) that call `AuthRepository` (data) and then `SessionManager`; the repository never calls back into domain (P-10.6). Password reset success also clears the local session (FR-031, US4-4).
- **Rationale**: FR-011, FR-023; revocation is effective on the next request, so the client learns of it only through a 401.

## R-09 Navigation

- **Decision**: Navigation 3 (JetBrains multiplatform artifacts `org.jetbrains.androidx.navigation3`) with a serializable `NavKey` per screen and two back stacks selected by `AuthState` (auth graph vs signed-in graph) — the "conditional navigation" pattern. Deep-link intents are parsed into a `PendingLink` and pushed onto whichever stack is active.
- **Rationale**: Back stack is plain state owned by the app, which makes auth-state switching and deep-link injection straightforward and unit-testable. The project's installed `navigation-3` skill covers the logged-in/logged-out pattern.
- **Alternatives considered**: Navigation Compose (multiplatform) with type-safe routes — mature and acceptable fallback if the Nav3 multiplatform artifact is not stable against CMP 1.12 at implementation time; the screen and ViewModel code is identical either way.

## R-10 Presentation architecture (MVI) and dependency injection

- **Decision**: MVI per constitution P-16. A shared base `MviViewModel<State, Intent, Effect>` in `core.presentation` (extends multiplatform `androidx.lifecycle.ViewModel`) exposes `val state: StateFlow<State>`, `val effects: Flow<Effect>` (buffered `Channel` → `receiveAsFlow()`), `abstract fun onIntent(intent: Intent)`, and protected `reduce(change)` / `emit(effect)`. Each screen has `ui/<screen>/<Screen>Contract.kt` holding `data class <Screen>State`, `sealed interface <Screen>Intent`, `sealed interface <Screen>Effect`, `sealed interface <Screen>Change` and a pure `fun reduce(state, change): <Screen>State`; `<Screen>ViewModel` handles intents by calling domain use cases and feeding their outcomes to `reduce`; `<Screen>Route` obtains the store (`koinViewModel()`), collects state with `collectAsStateWithLifecycle()`, handles effects in a `LaunchedEffect`; `<Screen>Screen(state, onIntent)` is stateless and previewable. Test helper `MviTestHarness` (Turbine) sends intents and asserts states/effects. DI with Koin (multiplatform core + compose-viewmodel).
- **Rationale**: One immutable state and one entry point make every screen reproducible from a list of intents and fully testable in `commonTest`; the pure reducer is testable without coroutines. `lifecycle-viewmodel-compose` is already on the classpath; Koin is the lightest DI with multiplatform ViewModel support.
- **Alternatives considered**: MVVM with multiple exposed flows and public methods (the earlier draft) — rejected by P-16; third-party MVI frameworks (Orbit, MVIKotlin) — capable, but add a dependency for what a ~60-line base class provides; manual DI / kotlin-inject for DI.

## R-11 Environments, base URL and cleartext

- **Decision**: Android product flavors `dev`, `staging`, `prod` in `:androidApp`, each with `buildConfigField("String", "API_BASE_URL", …)` and `manifestPlaceholders["linkHost"]`. `dev` reads the host from a git-ignored Gradle property `devApiHost` (e.g. `192.168.1.20:8080`) in `local.properties`/`~/.gradle/gradle.properties`, so each developer's LAN IP never lands in git. The host passes an `AppConfig(baseUrl, linkHost, environment, isDebugTools)` into shared code at startup. `dev` gets a `network_security_config.xml` permitting cleartext for all hosts (LAN IPs cannot be enumerated in XML); `staging`/`prod` use the default (cleartext refused). The release build type of `dev` is not published.
- **Rationale**: FR-027a; clarification (dev = LAN IP + container port over HTTP).
- **Alternatives considered**: BuildKonfig plugin (config in shared module) — useful once iOS needs the same values; unnecessary while only Android talks to the API.

## R-12 Deep links (verification & reset)

- **Decision**:
  - `staging`/`prod`: `<intent-filter android:autoVerify="true">` for `https://${linkHost}` with the verify and reset paths; the backend host serves `/.well-known/assetlinks.json` (backend task, listed as a dependency).
  - `dev`: a non-verified filter for `http://${linkHost}` with the same paths (Android shows a chooser or opens the browser); plus a dev-only "Paste link" screen reachable from the dev tools entry.
  - One common `AuthLinkParser` turns a URI (tapped or pasted) into `AuthLink.VerifyEmail(token)` | `AuthLink.ResetPassword(token)` | `null`.
  - Paths assumed `/verify-email` and `/reset-password` with query parameter `token` (matching the API request member name). These are the **only** open values: confirm with the backend mail templates; changing them touches `AuthLinkParser` and the manifest placeholders only.
- **Rationale**: FR-027, FR-027b; clarification Q4.
- **Alternatives considered**: see spec clarification options A, C, D.

## R-13 Fonts, localisation and RTL

- **Decision**: Bundle Hanken Grotesk (500/600/700/800) and IBM Plex Sans Arabic (500/600/700) as Compose resources (both SIL OFL). A `FontFamily` is chosen by the current layout direction/locale: Latin family for `en`, Arabic family for `ar`; numerals always rendered with the Latin family and `fontFeatureSettings = "tnum"`. Strings live in `composeResources/values/strings.xml` and `values-ar/strings.xml`. Arabic text styles drop letter-spacing to 0, uppercase overlines to sentence case, heading weight 800→700, line height ×1.6.
- **Rationale**: FR-003, FR-007; design board "Arabic & RTL".
- **Alternatives considered**: Downloadable Google Fonts — Android-only and fails offline.

## R-14 Money formatting

- **Decision**: Common `MoneyFormatter` taking `amountMinor: Long` + ISO 4217 code; fraction digits from a small in-code table (default 2; `KWD`,`BHD`,`OMR`,`JOD`,`TND`,`IQD`,`LYD` = 3; `JPY`,`KRW` = 0), grouping with `,`, U+2212 for negatives, Western digits always, currency code placed per locale (`EGP 450.00` in `en`, `450.00 ج.م` in `ar`) and wrapped in an LTR isolate inside RTL text.
- **Rationale**: FR-006 money bullet; ADR-0003 (minor units); design "Money on screen". Needed by the catalogue even though auth itself shows no money.

## R-15 Testing stack

- **Decision**:
  - `commonTest`: kotlin-test, kotlinx-coroutines-test, Turbine for flows, Ktor `MockEngine` for API contract tests using JSON fixtures copied from the OpenAPI schemas.
  - Compose UI tests: multiplatform `runComposeUiTest` in `commonTest` for screen behaviour.
  - Screenshot tests: Roborazzi on Android host tests (`androidHostTest`, Robolectric) for every catalogue component × {en LTR, ar RTL, fontScale 2.0}.
  - Accessibility: Compose semantics assertions for 48 dp minimum touch size and content descriptions on icon buttons.
- **Rationale**: Spec invariants table and Project Section 8.

## R-16 Constitution

- **Finding**: constitution v1.0.0 ratified 2026-09-26 (after this research was first drafted). Plan's Constitution Check re-run against P-01…P-15; symbols renamed per P-11 (`AppTheme`, `AppTextField`, …; Gradle property `devApiHost`).
