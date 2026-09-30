---
description: "Task list for 001-design-system-auth"
---

# Tasks: Design System Components & Authentication Flows

**Input**: Design documents from `specs/001-design-system-auth/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (identity-api-client.md, deep-links.md, design-system-components.md), quickstart.md; constitution v1.1.0

**Tests**: Included. The spec's invariants table (Project Section 4) and constitution P-14 / P-16.9 require contract tests per endpoint, invariant tests, screenshot tests per component, and reducer + store tests per screen. Write each story's tests first and confirm they fail.

**Organization**: Grouped by user story. US1 (design system) is the only story whose UI the others consume; US2–US5 build on US1 components and the Phase 2 foundation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…`
- Android actuals: `shared/src/androidMain/kotlin/com/fares/saveme/…`
- iOS actuals: `shared/src/iosMain/kotlin/com/fares/saveme/…`
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…`
- Screenshot tests: `shared/src/androidHostTest/kotlin/com/fares/saveme/…`
- Resources: `shared/src/commonMain/composeResources/…`
- Android host: `androidApp/…`

## Standing rules for every task (constitution v1.1.0)

- No codename in symbols (P-11): use `App*` prefixes, never the codename. Root package `com.fares.saveme` stays.
- No hardcoded user-facing text (P-06): every string goes to `values/strings.xml` **and** `values-ar/strings.xml`.
- Feature UI uses only `designsystem` components and tokens (P-07).
- Never log credentials, mailed secrets (`token`), passwords (P-09).
- Layering (P-10): `ui → domain → data`; `data` never imports `domain`/`ui`; `core` never imports a feature.
- **MVI screen recipe (P-16)** — every "MVI screen" task below produces, in `feature/auth/ui/<screen>/` (or the named folder):
  1. `<Screen>Contract.kt`: `data class <Screen>State` (only `val`s, immutable collections), `sealed interface <Screen>Intent`, `sealed interface <Screen>Effect`, `sealed interface <Screen>Change`, and a pure top-level `fun reduce(state: <Screen>State, change: <Screen>Change): <Screen>State` (no I/O, coroutines, clock or randomness).
  2. `<Screen>ViewModel.kt`: extends `MviViewModel<State, Intent, Effect>`; `onIntent` is the only public entry; calls **domain** use cases/services only (never `data`), feeds outcomes to `reduce`, sends navigation/snackbar as `Effect`s; holds no Compose types, `Context` or nav controller.
  3. `<Screen>Route.kt`: `@Composable fun <Screen>Route(navigate: (<Screen>Effect) -> Unit)` — gets the store via `koinViewModel()`, `collectAsStateWithLifecycle()`, collects `effects` in `LaunchedEffect(Unit)`.
  4. `<Screen>Screen.kt`: stateless `@Composable fun <Screen>Screen(state, onIntent)` built from `designsystem` only, plus a `@Preview` with a literal state.
  5. Tests: `<Screen>ReducerTest.kt` (pure `(state, change) → state` cases) and `<Screen>ViewModelTest.kt` (via `MviTestHarness`: intents in → state sequence + effects out).
- Visual source of truth: design canvas https://claude.ai/artifact/T312v5J2QNbUMiVwFQep3c, boards *Mobile foundations* and *Mobile components*; values restated in spec FR-001–FR-006.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencies, build variants, resources, app wiring

- [X] T001 Add libraries to `gradle/libs.versions.toml` at latest stable versions compatible with Kotlin 2.4.20 / CMP 1.12.1: ktor-client-core, ktor-client-okhttp, ktor-client-darwin, ktor-client-content-negotiation, ktor-serialization-kotlinx-json, ktor-client-mock, kotlinx-serialization-json, kotlinx-coroutines-core, kotlinx-coroutines-test, kotlinx-datetime, koin-core, koin-compose, koin-compose-viewmodel, koin-android, androidx-datastore-preferences (multiplatform `datastore-preferences-core` + `datastore-preferences`), navigation3 multiplatform (`org.jetbrains.androidx.navigation3:navigation3-ui`) and lifecycle-viewmodel-navigation3, turbine, roborazzi + roborazzi-compose + robolectric, compose ui-test; plus plugins `kotlinSerialization` and `roborazzi`. **Gate**: do not start until the owner has approved this list (plan.md Complexity Tracking, P-14.5)
- [X] T002 Wire dependencies into `shared/build.gradle.kts`: apply `kotlinSerialization` + `roborazzi` plugins; commonMain gets ktor core/content-negotiation/json, serialization, coroutines, datetime, koin core/compose/viewmodel, datastore-preferences-core, navigation3; androidMain gets ktor okhttp, datastore-preferences, koin-android; iosMain gets ktor darwin; commonTest gets kotlin-test, coroutines-test, turbine, ktor-client-mock, compose ui-test; androidHostTest gets roborazzi, roborazzi-compose, robolectric, junit
- [X] T003 Update `androidApp/build.gradle.kts`: `versionName = "1.0.0"` (must match `^\d+\.\d+\.\d+$`), `buildFeatures { buildConfig = true }`, flavorDimension `env` with flavors `dev`, `staging`, `prod`; each flavor sets `buildConfigField("String","API_BASE_URL", …)`, `buildConfigField("String","ENVIRONMENT", …)`, `buildConfigField("boolean","DEBUG_TOOLS", …)` (true only for dev), `buildConfigField("String","LINK_HOST", …)` and manifest placeholders `linkHost` (host only, no port) and `linkPort` (empty for staging/prod); `dev` reads `devApiHost` via `providers.gradleProperty("devApiHost").orElse("10.0.2.2:8080")`, splits it into host and port, and builds `http://<host>:<port>`; staging/prod use placeholders `https://staging.api.example` / `https://api.example` with a `TODO(base-url)` comment (research R-11)
- [X] T004 [P] Download Hanken Grotesk (weights 500, 600, 700, 800) and IBM Plex Sans Arabic (500, 600, 700) TTF files (SIL OFL, from github.com/google/fonts) into `shared/src/commonMain/composeResources/font/` named `hanken_grotesk_medium.ttf`, `hanken_grotesk_semibold.ttf`, `hanken_grotesk_bold.ttf`, `hanken_grotesk_extrabold.ttf`, `ibm_plex_sans_arabic_medium.ttf`, `ibm_plex_sans_arabic_semibold.ttf`, `ibm_plex_sans_arabic_bold.ttf`; add `OFL.txt` license files beside them
- [X] T005 [P] Create `shared/src/commonMain/composeResources/values/strings.xml` and `shared/src/commonMain/composeResources/values-ar/strings.xml` with an `app_name` entry (display name is the only place the codename may appear, P-11); later tasks append keys to both files
- [X] T006 [P] Create `androidApp/src/main/res/xml/data_extraction_rules.xml` and `androidApp/src/main/res/xml/backup_rules.xml` excluding the DataStore files `secure_session.preferences_pb` and `app_prefs.preferences_pb` from cloud backup and device transfer; reference both from `androidApp/src/main/AndroidManifest.xml` (`android:dataExtractionRules`, `android:fullBackupContent`) (research R-06, P-09.1)
- [X] T007 [P] Create `androidApp/src/dev/res/xml/network_security_config.xml` permitting cleartext (`<base-config cleartextTrafficPermitted="true"/>`) and `androidApp/src/dev/AndroidManifest.xml` setting `android:networkSecurityConfig="@xml/network_security_config"` on `<application>` (dev flavor only, P-09.3)
- [X] T008 Add a "Development backend" section to `README.md`: set `devApiHost=<LAN-IP>:<container-port>` in `local.properties` or `~/.gradle/gradle.properties`; link to `specs/001-design-system-auth/quickstart.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Config, platform info, networking + `ApiEvents`, error model, storage, MVI base, DI, navigation shell — required by every auth story. No UI tokens here (those are US1).

**⚠️ CRITICAL**: No auth story (US2–US5) can begin until this phase is complete.

### Tests for Foundation (write first, must fail)

- [X] T009 [P] Test problem decoding + code mapping in `shared/src/commonTest/kotlin/com/fares/saveme/core/network/ProblemDetailsTest.kt`: decode a full problem body (type, title, status, detail, code, request_id, errors[{pointer, code}], retry_after_seconds, minimum_version); unknown members ignored; every catalogue code used in contracts/identity-api-client.md maps to its `ApiErrorCode`; an unseen code maps to `ApiErrorCode.Unknown(raw)`; mapping never reads `detail` (P-02.4)
- [X] T010 [P] Test HTTP client in `shared/src/commonTest/kotlin/com/fares/saveme/core/network/HttpClientFactoryTest.kt` with Ktor `MockEngine`: every request carries `Accept: application/json`, `X-Client-Platform: android`, `X-Client-Version` matching `^\d+\.\d+\.\d+$`, `Accept-Language` `ar` or `en`, `X-Correlation-Id` that is a UUIDv7 and differs per request; `Authorization: Bearer …` present only when the credential provider returns one and the request is not marked public; non-2xx → `ApiResult.Failure`, IOException → `ApiResult.Offline`; `ApiEvents` emits `SessionEnded` on 401 for a credentialed request only (not for public sign-in 401), `UpdateRequired(minimumVersion)` on 426, `EmailNotVerified` on 403 EMAIL_NOT_VERIFIED
- [X] T011 [P] Test `SecureStore` contract in `shared/src/commonTest/kotlin/com/fares/saveme/core/storage/SessionStoreTest.kt` against an in-memory fake: write/read/clear round trip of `StoredSession(credential, sessionId, issuedAt)`; clear leaves nothing readable; `toString()` never contains the credential
- [X] T012 [P] Test MVI base in `shared/src/commonTest/kotlin/com/fares/saveme/core/presentation/MviViewModelTest.kt` with a tiny counter store: initial state emitted; `onIntent` → `reduce` → new state; effects delivered exactly once in order and buffered while no collector is active; state is not re-emitted when `reduce` returns an equal state

### Implementation for Foundation

- [X] T013 [P] Create `AppConfig(baseUrl: String, linkHost: String, environment: Environment, isDebugTools: Boolean)` and `enum Environment { Dev, Staging, Prod }` in `shared/src/commonMain/kotlin/com/fares/saveme/core/config/AppConfig.kt`
- [X] T014 [P] Declare platform info interface in `shared/src/commonMain/kotlin/com/fares/saveme/core/platform/ClientInfo.kt`: `interface ClientInfo { val platform: String; val appVersion: String; val deviceName: String; fun timezoneId(): String; fun languageTag(): String /* "ar" or "en", default "en" */; val liveApiSupported: Boolean }`
- [X] T015 [P] Android `ClientInfo` in `shared/src/androidMain/kotlin/com/fares/saveme/core/platform/AndroidClientInfo.kt`: platform `android`, appVersion passed from host (`BuildConfig.VERSION_NAME`), deviceName = `"${Build.MANUFACTURER} ${Build.MODEL}"` trimmed to 100 chars, timezone from `java.util.TimeZone.getDefault().id`, language from current app locale, `liveApiSupported = true`
- [X] T016 [P] iOS `ClientInfo` in `shared/src/iosMain/kotlin/com/fares/saveme/core/platform/IosClientInfo.kt`: platform `ios`, `liveApiSupported = false` (P-15; iOS sign-in out of scope), other values from `NSBundle`/`NSTimeZone`/`NSLocale`
- [X] T017 [P] Create `ProblemDetails` (`@Serializable`: type, title, status, detail, code, request_id, instance?, errors: List<ProblemError>? where `ProblemError(pointer: String?, code: String)`, retry_after_seconds: Long?, minimum_version: String?) in `shared/src/commonMain/kotlin/com/fares/saveme/core/network/ProblemDetails.kt`
- [X] T018 [P] Create `sealed interface ApiErrorCode` with `enum class RetryClass { Automatic, AfterChange, Never }` in `shared/src/commonMain/kotlin/com/fares/saveme/core/network/ApiErrorCode.kt`: members for VALIDATION_FAILED, MALFORMED_JSON, CLIENT_VERSION_REQUIRED, CLIENT_VERSION_INVALID, CLIENT_VERSION_UNSUPPORTED, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, RATE_LIMITED, INTERNAL_ERROR, SERVICE_UNAVAILABLE, INVALID_CREDENTIALS, ACCOUNT_LOCKED, EMAIL_NOT_VERIFIED, VERIFICATION_TOKEN_INVALID, RESET_TOKEN_INVALID, plus `Unknown(raw)`; retry class per `saveme-backend/docs/openapi/error-catalog.yaml` `retryable`; `fun from(code: String): ApiErrorCode`
- [X] T019 Create `sealed interface ApiResult<out T> { Success(value: T); Failure(problem: ProblemDetails, code: ApiErrorCode, retryAfterSeconds: Long?); Offline; Timeout }` and `Envelope<T>(data: T, meta: JsonObject)` in `shared/src/commonMain/kotlin/com/fares/saveme/core/network/ApiResult.kt` (depends T017, T018)
- [X] T020 [P] Create `sealed interface ApiEvent { SessionEnded; UpdateRequired(minimumVersion: String?); EmailNotVerified }` and `class ApiEvents { val events: SharedFlow<ApiEvent>; fun publish(e: ApiEvent) }` (replay 0, extraBufferCapacity 8) in `shared/src/commonMain/kotlin/com/fares/saveme/core/network/ApiEvents.kt` — the only channel from `core` to features (P-10.7)
- [X] T021 Create `HttpClientFactory` in `shared/src/commonMain/kotlin/com/fares/saveme/core/network/HttpClientFactory.kt`: takes `HttpClientEngine`, `AppConfig`, `ClientInfo`, `credentialProvider: suspend () -> String?`, `ApiEvents`; installs ContentNegotiation JSON (`ignoreUnknownKeys = true`, `explicitNulls = false`, `encodeDefaults = false`), `defaultRequest` with base URL `${baseUrl}/api/v1/` and headers from T010, per-request UUIDv7 correlation id (`kotlin.uuid.Uuid.generateV7()`), bearer header unless request attribute `PublicEndpoint` set; no automatic retry plugin (research R-04); provide `suspend inline fun <reified T> HttpClient.call(block): ApiResult<T>` that decodes `Envelope<T>` on 2xx, `Unit` on 204, `ProblemDetails` otherwise, maps IO/timeout exceptions, and publishes `ApiEvent`s per T010 (makes T009–T010 pass) (depends T013, T014, T019, T020)
- [X] T022 [P] Engine providers: `expect fun httpEngine(): HttpClientEngine` in `shared/src/commonMain/kotlin/com/fares/saveme/core/network/HttpEngine.kt`, `actual` = `OkHttp.create()` in `shared/src/androidMain/kotlin/com/fares/saveme/core/network/HttpEngine.android.kt`, `Darwin.create()` in `shared/src/iosMain/kotlin/com/fares/saveme/core/network/HttpEngine.ios.kt`
- [X] T023 [P] Create `StoredSession(credential: String, sessionId: String, issuedAt: Instant)` and `interface SecureStore { suspend fun read(): StoredSession?; suspend fun write(s: StoredSession); suspend fun clear() }` in `shared/src/commonMain/kotlin/com/fares/saveme/core/storage/SecureStore.kt`; credential pattern `^[0-9a-f-]{36}\|[A-Za-z0-9]{64}$`; `toString()` of `StoredSession` redacts the credential (makes T011 pass)
- [X] T024 [P] Android `SecureStore` in `shared/src/androidMain/kotlin/com/fares/saveme/core/storage/KeystoreSecureStore.kt`: AES-256-GCM key alias `session_key` generated in `AndroidKeyStore` (non-exportable), JSON-serialised session encrypted, IV + ciphertext Base64 stored in DataStore file `secure_session`; `clear()` removes the entry; decryption failure → clear and return null (research R-06)
- [X] T025 [P] iOS stub `SecureStore` in `shared/src/iosMain/kotlin/com/fares/saveme/core/storage/StubSecureStore.kt`: `read()` returns null, `write()` throws `UnsupportedOperationException("iOS sign-in out of scope (P-15)")`, `clear()` no-op
- [X] T026 [P] Create `KeyValueStore` interface (string get/set/remove, `Flow` observation) in `shared/src/commonMain/kotlin/com/fares/saveme/core/storage/KeyValueStore.kt` with DataStore-backed Android actual in `shared/src/androidMain/kotlin/com/fares/saveme/core/storage/DataStoreKeyValueStore.kt` (file `app_prefs`) and in-memory iOS actual in `shared/src/iosMain/kotlin/com/fares/saveme/core/storage/InMemoryKeyValueStore.kt`
- [X] T027 Create `DeviceIdentityStore` in `shared/src/commonMain/kotlin/com/fares/saveme/core/storage/DeviceIdentityStore.kt`: `suspend fun deviceId(): String` — generate random UUID string on first call, persist in `KeyValueStore` key `device_id`, must match `^[A-Za-z0-9._:-]{1,64}$`, never exposed to UI (depends T026)
- [X] T028 [P] Create MVI base in `shared/src/commonMain/kotlin/com/fares/saveme/core/presentation/MviViewModel.kt`: `abstract class MviViewModel<S : Any, I : Any, E : Any>(initialState: S) : ViewModel()` with `val state: StateFlow<S>` (backed by private `MutableStateFlow`), `val effects: Flow<E>` (private `Channel<E>(Channel.BUFFERED)` → `receiveAsFlow()`), `abstract fun onIntent(intent: I)`, `protected fun setState(transform: (S) -> S)` (subclasses pass their pure `reduce`), `protected fun sendEffect(effect: E)`, `protected val currentState: S`; no other public API (P-16) (makes T012 pass)
- [X] T029 [P] Create test harness in `shared/src/commonTest/kotlin/com/fares/saveme/core/presentation/MviTestHarness.kt`: `suspend fun <S, I, E> MviViewModel<S, I, E>.test(block: suspend MviScope<S, I, E>.() -> Unit)` using Turbine to expose `intent(i)`, `awaitState()`, `awaitEffect()`, `expectNoEffects()`; sets `Dispatchers.setMain(StandardTestDispatcher())` for `viewModelScope`
- [X] T030 Create Koin modules in `shared/src/commonMain/kotlin/com/fares/saveme/core/di/CoreModule.kt` (AppConfig, ClientInfo, ApiEvents single, HttpClient via factory, stores) and `fun initKoin(config: AppConfig, platformModule: Module, featureModules: List<Module>)` in `shared/src/commonMain/kotlin/com/fares/saveme/core/di/Koin.kt`; platform modules in `shared/src/androidMain/kotlin/com/fares/saveme/core/di/PlatformModule.android.kt` (needs `Context`) and `shared/src/iosMain/kotlin/com/fares/saveme/core/di/PlatformModule.ios.kt`; `core` references no feature module (P-10.7) (depends T020–T027)
- [X] T031 Host wiring: add `androidApp/src/main/kotlin/com/fares/saveme/MainApplication.kt` (class `MainApplication : Application`) calling `initKoin(AppConfig(BuildConfig.API_BASE_URL, BuildConfig.LINK_HOST, Environment.valueOf(BuildConfig.ENVIRONMENT), BuildConfig.DEBUG_TOOLS), androidPlatformModule(this, BuildConfig.VERSION_NAME), appFeatureModules)` where `appFeatureModules` is exported from shared `shared/src/commonMain/kotlin/com/fares/saveme/AppModules.kt`; register `android:name=".MainApplication"` in `androidApp/src/main/AndroidManifest.xml`; iOS: call `initKoin` from `shared/src/iosMain/kotlin/com/fares/saveme/MainViewController.kt` with a Dev config
- [X] T032 Create navigation shell in `shared/src/commonMain/kotlin/com/fares/saveme/navigation/`: `Routes.kt` (serializable `NavKey`s: Entry, SignIn(prefillEmail?, reason?), Register, CheckInbox(email, kind), VerifyEmail(token), ForgotPassword(prefillEmail?), ResetPassword(token), Home, UpdateRequired(minimumVersion), Catalog, DevTools, PasteLink), `AppNavHost.kt` holding two back stacks (auth, signed-in) selected by an auth-state flow (placeholder always-signed-out until US2) and mapping each Route's effects to back-stack operations, `DeepLinkBus.kt` (`fun submit(uri: String)`; `val links: SharedFlow<String>` of raw URIs — parsing happens once in `AppNavHost` in US3) (research R-09; fallback to Navigation Compose if Nav3 multiplatform unavailable)
- [X] T033 Replace template `shared/src/commonMain/kotlin/com/fares/saveme/App.kt` with `App()` = `MaterialTheme { AppNavHost() }` (switched to `AppTheme` in T063); delete `Greeting.kt`, `GreetingUtil.kt`, the `compose-multiplatform.xml` drawable and the template tests that reference them (`SharedCommonTest.kt`, `SharedLogicIOSTest.kt`, `SharedLogicAndroidHostTest.kt`), replacing with an empty smoke test if a source set would otherwise be empty

**Checkpoint**: `./gradlew :shared:allTests :androidApp:assembleDevDebug` green; T009–T012 pass.

---

## Phase 3: User Story 1 - Shared design foundation and component library (Priority: P1) 🎯 MVP

**Goal**: Tokens, theme and all components from the *Mobile components* board, with RTL and 200% font-scale support, plus a dev-only catalogue.

**Independent Test**: Launch dev build → Dev tools → Components; compare with canvas in en and ar and at font scale 2.0 (quickstart scenario 1); screenshot tests green.

### Tests for User Story 1 (write first, must fail)

- [X] T034 [P] [US1] Test `MoneyFormatter` in `shared/src/commonTest/kotlin/com/fares/saveme/designsystem/money/MoneyFormatterTest.kt`: `45000 EGP` → `EGP 450.00` (en); `-45000 EGP` uses U+2212 `−EGP 450.00`; `1234567 KWD` → `KWD 1,234.567`; `500 JPY` → `JPY 500`; unknown currency defaults to 2 digits; ar locale → `450.00 ج.م` with Western digits wrapped in LTR isolate (U+2066…U+2069); never uses Float/Double (P-03)
- [X] T035 [P] [US1] Test `BudgetProgress` state thresholds in `shared/src/commonTest/kotlin/com/fares/saveme/designsystem/component/BudgetStateTest.kt`: 79% → Positive, 80% and 100% → Pending, 101% → Over; percent label always present
- [X] T036 [P] [US1] Accessibility semantics test in `shared/src/commonTest/kotlin/com/fares/saveme/designsystem/component/TouchTargetTest.kt` using `runComposeUiTest`: every clickable component in the contract (buttons, icon button, FAB, chips, segmented control, switch row, nav bar items) reports bounds ≥ 48×48 dp; `AppIconButton` exposes its `contentDescription`; disabled/loading `PrimaryButton` click invokes nothing (P-08)
- [X] T037 [P] [US1] Roborazzi screenshot tests in `shared/src/androidHostTest/kotlin/com/fares/saveme/designsystem/ComponentScreenshotTest.kt`: render each catalogue section (T058) in three configs — en LTR fontScale 1.0, ar RTL fontScale 1.0, en fontScale 2.0 — one image per section per config under `shared/src/androidHostTest/screenshots/` (P-14.4)
- [X] T038 [P] [US1] Catalogue MVI tests `shared/src/commonTest/kotlin/com/fares/saveme/designsystem/catalog/ComponentCatalogReducerTest.kt` and `ComponentCatalogViewModelTest.kt`: `LanguageSelected(ar)` → state.language ar; `FontScaleSelected(2.0)` → state.fontScale 2.0; `Back` → `Effect.NavigateBack`

### Tokens & theme for User Story 1

- [X] T039 [P] [US1] Colour tokens in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/tokens/AppColors.kt`: primary #4C6FD6, primaryContainer #E9EEFC, onPrimaryContainer/pressed #3553B0, background #EDF0F5, surface #FFFFFF, surfaceContainer (sunken) #F4F6FA, surfaceContainerHigh (sunken-2) #E9EDF3, outline #8A93A2, outlineVariant #E3E7EE, onSurface (ink) #161A22, onSurfaceVariant (ink-2) #5B6472, caption #6F7886, error #A9503A, errorContainer #F7E9E3, onPrimary #FFFFFF; plus `fun appLightColorScheme(): ColorScheme` mapping these to Material 3 roles (FR-001)
- [X] T040 [P] [US1] Money colours in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/tokens/MoneyColors.kt`: `data class MoneyColors(positive=#2C7A5B, positiveContainer=#E6F3EC, pending=#8F5E12, pendingContainer=#FBF1DE, over=#A9503A, overContainer=#F7E9E3)` and `val LocalMoneyColors = staticCompositionLocalOf { MoneyColors() }` (FR-002)
- [X] T041 [P] [US1] Fonts in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/tokens/AppFonts.kt`: `@Composable fun latinFontFamily()` (Hanken Grotesk 500/600/700/800) and `arabicFontFamily()` (IBM Plex Sans Arabic 500/600/700; 800 requests fall back to 700) from Compose resources (depends T004)
- [X] T042 [US1] Typography in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/tokens/AppTypography.kt`: `data class AppTypography(numeralHero 40sp/800/-0.02em/1.05 lh, numeralL 24sp/800/-0.02em, headline 26sp/800/-0.02em, title 18sp/700, itemTitle 16sp/600, body 15sp/500/1.5 lh, label 14sp/600, caption 12sp/600, overline 11sp/700/0.14em uppercase)`, all with `fontFeatureSettings = "tnum"`; `fun appTypography(isArabic: Boolean)` returns Arabic variants: line height ×1.6, letterSpacing 0, no uppercase overline, weight 800 → 700; numeral styles always use the Latin family; also map to Material 3 `Typography` (FR-003, P-06.4) (depends T041)
- [X] T043 [P] [US1] Spacing, shapes, elevation, sizes in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/tokens/AppDimens.kt`: `Spacing(xxs 4, xs 8, s 12, m 16, l 20, xl 24, xxl 32, gutter 16, sectionGap 24)`, `AppShapes(control 10, tile 14, card 18, sheet 22 top-only, hero 22)`, `Elevation` shadow specs (flat none; raised `0 1 2 rgba(20,30,55,.05)` + `0 1 3 rgba(20,30,55,.06)`; overlay `0 12 32 rgba(20,30,55,.14)`), `Sizes(touchTarget 48, button 52, field 52, fab 56, topBar 64, row 64, rowTwoLine 72, navBar 80, chip 36, navIndicatorW 56, navIndicatorH 30)` with CompositionLocals (FR-004, FR-005)
- [X] T044 [US1] `AppTheme(content)` in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/theme/AppTheme.kt`: provides MaterialTheme(colorScheme, typography, shapes) + `LocalMoneyColors`, `LocalAppTypography`, `LocalSpacing`, `LocalAppShapes`, `LocalSizes`; picks Arabic typography when `LocalLayoutDirection == Rtl`; `object AppTheme { val colors; val money; val type; val spacing; val shapes; val sizes }` accessors; light only (FR-009) (depends T039–T043)
- [X] T045 [US1] `MoneyFormatter` in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/money/MoneyFormatter.kt`: input `amountMinor: Long`, `currency: String`, `languageTag`; fraction digits table (default 2; KWD, BHD, OMR, JOD, TND, IQD, LYD = 3; JPY, KRW = 0), `,` grouping, U+2212 minus, Western digits, en = `CODE 1,234.56`, ar = `1,234.56 ج.م` for EGP (other currencies keep code), LTR isolate in ar; integer arithmetic only (research R-14) (makes T034 pass)

### Components for User Story 1 (contract: contracts/design-system-components.md)

- [X] T046 [P] [US1] Buttons in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Buttons.kt`: `PrimaryButton`, `SecondaryButton` (tonal on primaryContainer), `AppTextButton`, `DestructiveButton` — params `text, onClick, modifier, enabled = true, loading = false`; 52dp height, full width by default, control radius 10, pressed colour #3553B0, disabled container sunken-2 with ink-2 text, loading shows 20dp progress immediately and ignores clicks, semantics disabled when loading
- [X] T047 [P] [US1] `AppIconButton(icon, contentDescription: String, onClick, enabled)` (48dp target, contentDescription required non-null) and `AppFab(icon, text: String?, expanded: Boolean, onClick)` (56dp, radius 18, extended shows text, animates width) in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/IconButtons.kt`; directional icons (back, chevrons) use `Modifier.autoMirror()` helper defined in the same file
- [X] T048 [P] [US1] `AppTopAppBar(title, navigationIcon?, actions)` (64dp, surface, title style `title`) and `AppNavigationBar(items: List<NavItem(label, icon, contentDescription)>, selectedIndex, onSelect)` (80dp, pill indicator 56×30 on primaryContainer, label style caption) in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Bars.kt`
- [X] T049 [P] [US1] `SegmentedControl<T>(options: List<T>, label: (T)->String, selected: T, onSelect)` (48dp, sunken track, surface selected pill, raised elevation), `AppFilterChip(label, selected, onClick)` (36dp visual, 48dp touch via `minimumInteractiveComponentSize`), `SettingsSwitchRow(title, supportingText?, checked, onCheckedChange)` (whole row toggles, `Modifier.toggleable`) in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Selection.kt`
- [X] T050 [P] [US1] `AppTextField(value, onValueChange, label, modifier, supportingText?, errorText?, enabled, keyboardOptions, keyboardActions, singleLine = true)` (52dp field, control radius 10, outline #8A93A2, focus primary, error text in error colour replaces supporting text, `semantics { error(...) }`) and `PasswordField(...)` (masked by default, trailing visibility toggle `AppIconButton` with localised contentDescription) in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Fields.kt`; add string keys `cd_show_password`, `cd_hide_password` to both strings.xml files
- [X] T051 [P] [US1] `AmountDisplay(amountMinor, currency, style = numeralHero)` (auto-shrinks font down to numeralL before ellipsis) and `MoneyText(amountMinor, currency, kind: MoneyKind { Income, Expense, Liability, Balance })` — Income positive colour with `+`, Expense neutral ink with `−` (never red, P-03.5), Liability shown as positive amount + " owed" label (string `money_owed`), Balance negative in error colour — in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Money.kt` (depends T045)
- [X] T052 [P] [US1] `BalanceHero(label, amountMinor, currency, caption?)` (radius 22, solid primary, white text, no gradient) and `WalletCard(name, type: WalletType { Cash, Checking, Savings, CreditCard, Loan, Investment }, amountMinor, currency)` (tile-radius icon tile on primaryContainer, liability types render "owed") in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Cards.kt`; icons as inline vector `ImageVector`s in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/icon/AppIcons.kt`
- [X] T053 [P] [US1] `TransactionRow(title, subtitle, amountMinor, currency, kind, pendingSync: Boolean, onClick)` (72dp, pending dot 8dp in pending colour with localised contentDescription "Not synced yet"), `DateHeader(label, dayTotalMinor, currency)`, `MemberRow(name, initial, role: MemberRole { Owner, Editor, Viewer }, isYou)` + `RolePill(role)` in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Lists.kt`; glossary role names via strings `role_owner`, `role_editor`, `role_viewer`
- [X] T054 [P] [US1] `BudgetProgress(name, spentMinor, limitMinor, currency)` in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/BudgetProgress.kt` with pure `fun budgetState(spent, limit): BudgetState { Positive (<80%), Pending (80–100%), Over (>100%) }` in the same file; bar fill follows layout direction; percentage and "X of Y · Z left" / "Z over" text always shown (makes T035 pass)
- [X] T055 [P] [US1] `SyncBanner(state: SyncBannerState { Offline(pendingCount), Synced })`, `InlineAlert(tone: AlertTone { Info, Warning, Error }, text, actionLabel?, onAction?)`, `AppSnackbarHost(hostState)` with optional action (e.g. Undo) in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/Feedback.kt`
- [X] T056 [P] [US1] `AppBottomSheet(onDismiss, title?, content)` wrapping `ModalBottomSheet` with top radius 22, drag handle, scrim `#161A22` at 38% alpha in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/BottomSheet.kt`; `EmptyState(title, body, actionLabel?, onAction?)` and `Skeleton(modifier, shape)` (static sunken blocks) in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/EmptyAndLoading.kt`
- [X] T057 [P] [US1] `AppScreen(topBar, snackbarHost, content)` scaffold applying background, 16dp gutter, safe-area/IME insets, and vertical scroll for forms in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/component/AppScreen.kt` (used by every screen)

### Catalogue & dev tools for User Story 1

- [X] T058 [US1] Catalogue sections in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/catalog/CatalogSections.kt`: one stateless `@Composable` per family (Buttons, AppBars, Selection, Fields, Money, Cards, Lists, Budget, Feedback, BottomSheet trigger, EmptyAndLoading) showing every state from the contract, using sample data from the canvas (e.g. "Groceries", EGP 450.00, budgets 51% / 86% / 128%); sample copy in strings.xml under `catalog_*` keys in both languages (depends T046–T057)
- [X] T059 [US1] MVI screen `ComponentCatalog` in `shared/src/commonMain/kotlin/com/fares/saveme/designsystem/catalog/` (`ComponentCatalogContract.kt`: State(language: en|ar, fontScale: 1.0|1.5|2.0), Intents LanguageSelected/FontScaleSelected/Back, Effect NavigateBack; `ComponentCatalogViewModel.kt`; `ComponentCatalogRoute.kt`; `ComponentCatalogScreen.kt` rendering sections under `CompositionLocalProvider(LocalLayoutDirection, LocalDensity(fontScale))`) (FR-008) (makes T038 pass)
- [X] T060 [US1] MVI screen `DevTools` in `shared/src/commonMain/kotlin/com/fares/saveme/devtools/` (`DevToolsContract.kt`: empty State, Intents ComponentsClicked/PasteLinkClicked, Effects NavigateCatalog/NavigatePasteLink; ViewModel, Route, Screen) with reducer/ViewModel tests in `shared/src/commonTest/kotlin/com/fares/saveme/devtools/DevToolsViewModelTest.kt`; "Paste link" entry is wired in US3
- [X] T061 [US1] Register `Catalog` and `DevTools` entries in `shared/src/commonMain/kotlin/com/fares/saveme/navigation/AppNavHost.kt` and their ViewModels in `shared/src/commonMain/kotlin/com/fares/saveme/devtools/DevToolsModule.kt` (Koin), added to `AppModules.kt`
- [X] T062 [US1] MVI screen `Entry` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/entry/` (State(isDebugTools), Intents SignInClicked/CreateAccountClicked/DevToolsClicked, Effects NavigateSignIn/NavigateRegister/NavigateDevTools; Screen: app name, one-line value statement, `PrimaryButton` "Sign in", `SecondaryButton` "Create account", `AppTextButton` "Dev tools" only when `isDebugTools`) with `EntryReducerTest.kt`/`EntryViewModelTest.kt` in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/entry/`; until US2/US3 land, SignIn/Register effects are no-ops in `AppNavHost`
- [X] T063 [US1] Switch `App.kt` to `AppTheme { AppNavHost() }`; record Roborazzi baselines (`./gradlew :shared:recordRoborazziAndroidHostTest`) and review against the canvas

**Checkpoint**: Catalogue matches canvas; T034–T038 pass; US1 independently demoable.

---

## Phase 4: User Story 2 - Sign in on this device (Priority: P1)

**Goal**: Sign in against `POST /auth/login`, persist session securely, auto-route on relaunch, handle wrong password, lock, deletion-cancelled, offline, session ended remotely, update required.

**Independent Test**: With a known backend account: sign in, force-stop, relaunch → signed-in placeholder home without auth screens (quickstart 6, 7, 8, 10, 12).

### Tests for User Story 2 (write first, must fail)

- [X] T064 [P] [US2] Contract test `signIn` in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/data/SignInContractTest.kt` (MockEngine): POST `auth/login`, public (no Authorization), body exactly `{email, password, device{session_id, device_id, name, platform:"android", app_version}}` — no other members; `session_id` matches `^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`; 200 fixture decodes `SignInResultDto` ignoring unknown members; 401 INVALID_CREDENTIALS, 423 ACCOUNT_LOCKED with `Retry-After: 900`, 422 VALIDATION_FAILED with pointers, 429 RATE_LIMITED, 426 CLIENT_VERSION_UNSUPPORTED with `minimum_version` map to the right `ApiErrorCode`; a 401 here does NOT publish `ApiEvent.SessionEnded`
- [X] T065 [P] [US2] Test `SessionManager` + `SignInUseCase` in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/domain/SessionManagerTest.kt` (Turbine, fake repository): starts `Unknown` → `SignedOut` with empty store, → `SignedIn` with stored session + snapshot; `ApiEvent.SessionEnded` clears store + snapshot and emits `SignedOut(SessionEnded)`; `SignInUseCase` success persists the credential only through `SecureStore` and returns `SignInOutcome.Success(deletionCancelled)`; failures map to `SignInOutcome.InvalidCredentials | Locked(retryAfterSeconds) | Validation(fieldErrors) | RateLimited(seconds) | Offline | PlatformUnsupported | Error(requestId)`; returns `PlatformUnsupported` without calling the repository when `!clientInfo.liveApiSupported`
- [X] T066 [P] [US2] SignIn MVI tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/signin/SignInReducerTest.kt` (pure: field edits, submitting flag, each outcome → banner, password cleared on InvalidCredentials, `Locked(minutes = ceil(retryAfter/60))`, email kept on Offline, deletion notice flag) and `SignInViewModelTest.kt` (via `MviTestHarness`: invalid email blocks submit and sets `emailError`; two `Submit` intents while submitting call the use case once; retry reuses the same attempt `session_id`, editing email creates a new one; unknown-address and wrong-password fixtures yield identical state (P-09.4); success → `Effect.NavigateHome`; `ForgotPasswordClicked` → `Effect.NavigateForgotPassword(email)`)
- [X] T067 [P] [US2] Test error copy mapping in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/AuthErrorCopyTest.kt`: every `ApiErrorCode` listed in contracts/identity-api-client.md maps to a string resource key; `Unknown` maps to generic copy with request id; the mapping function has no access to `ProblemDetails.detail` (FR-014, P-02.4)

### Implementation for User Story 2

- [X] T068 [P] [US2] DTOs in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/data/dto/IdentityDtos.kt` (`@Serializable`, snake_case `@SerialName`): `SignInRequest(email, password /* maxLength 128 */, device: DeviceDescriptionDto)`, `DeviceDescriptionDto(session_id, device_id, name /* 1–100 */, platform, app_version)`, `SignInResultDto(credential, session: DeviceSessionDto, account: AccountDto, entitlements: EntitlementSummaryDto, deletion_cancelled)`, `AccountDto(id, email, email_verified, state /* active|pending_deletion */, deletion_scheduled_for: String?)`, `EntitlementSummaryDto(plan, features: Map<String, JsonPrimitive>)`, `DeviceSessionDto(id, device_name, platform, app_version, state, signed_in_at, …)`; `AcceptedDto(status)`; no default-valued members that would encode extra fields
- [X] T069 [P] [US2] Domain models in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/model/Account.kt`: `AccountSnapshot(userId, email, emailVerified, state: AccountState { Active, PendingDeletion }, deletionScheduledFor: Instant?, plan: String, features: Map<String, FeatureValue>)`, `sealed FeatureValue { Flag(Boolean), Quota(Long) }`; plan is display-only (P-12.1); plus `SignInOutcome` (variants per T065) in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/model/Outcomes.kt`
- [X] T070 [P] [US2] `AuthState` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/AuthState.kt`: `Unknown`, `SignedOut(reason: SignOutReason { None, SessionEnded, PasswordReset, UserSignedOut })`, `SignedIn(account: AccountSnapshot)`
- [X] T071 [P] [US2] `ClientValidation` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/ClientValidation.kt`: `validateEmail` (well-formed, ≤ 254), `validateNewPassword(password, email)` (8–128 chars, not equal to email case-insensitive trimmed), `validateDisplayName` (blank or 1–100), returning `FieldError` enums (FR-019); unit test in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/domain/ClientValidationTest.kt`
- [X] T072 [US2] `AccountSnapshotStore` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/data/AccountSnapshotStore.kt`: persist/read/clear a serializable snapshot DTO as JSON in `KeyValueStore` key `account_snapshot`; `setEmailVerified(true)`; takes and returns data-layer DTOs only (no domain import, P-10.6) (depends T026, T068)
- [X] T073 [US2] `IdentityApi` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/data/IdentityApi.kt` with `suspend fun signIn(req): ApiResult<SignInResultDto>` (public) using `HttpClient.call`; and `AuthRepository` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/data/AuthRepository.kt` with `signIn(email, password, device: DeviceDescriptionDto): ApiResult<SignInResultDto>` plus session/snapshot persistence helpers (`saveSession`, `readSession`, `clearLocal`) delegating to `SecureStore` / `AccountSnapshotStore` — returns results only, never calls domain (P-10.6) (makes T064 pass) (depends T068, T072)
- [X] T074 [US2] `SessionManager` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/SessionManager.kt`: `StateFlow<AuthState>`, `suspend fun restore()`, `suspend fun establish(result)` (maps DTO → `AccountSnapshot`, saves via repository), `suspend fun signOutLocally(reason)`, `suspend fun markEmailVerified()`, `suspend fun credential(): String?` (bound to `HttpClientFactory.credentialProvider` in Koin); subscribes to `ApiEvents` and calls `signOutLocally(SessionEnded)` on `ApiEvent.SessionEnded` (depends T070, T073)
- [X] T075 [US2] `SignInUseCase` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/usecase/SignInUseCase.kt`: `suspend operator fun invoke(email, password, attemptSessionId): SignInOutcome` — short-circuits `PlatformUnsupported` when `!clientInfo.liveApiSupported` (P-15); builds `DeviceDescriptionDto` from `ClientInfo` + `DeviceIdentityStore`; calls `AuthRepository.signIn`; on success `SessionManager.establish(result)`; maps `ApiResult` failures to `SignInOutcome` (makes T065 pass) (depends T074)
- [X] T076 [US2] Error copy in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/AuthErrorCopy.kt`: `fun ApiErrorCode.toMessage(): StringResource` + `requestId` passthrough for generic errors; add en + ar strings: `error_invalid_credentials` ("Email or password is incorrect."), `error_account_locked` (plural minutes), `error_email_not_verified`, `error_verification_link_invalid`, `error_reset_link_invalid`, `error_validation`, `error_rate_limited` (minutes), `error_session_ended`, `error_update_required`, `error_service_unavailable`, `error_generic` ("Something went wrong. Reference: %1$s"), `error_offline`, `error_platform_unsupported` (makes T067 pass)
- [X] T077 [US2] MVI screen `SignIn` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/signin/`: State(email, password, passwordVisible, emailError?, submitting, banner?: InvalidCredentials | Locked(minutes) | RateLimited(minutes) | Offline | SessionEnded | PasswordReset | Generic(requestId), deletionCancelledNotice, attemptSessionId); Intents EmailChanged, PasswordChanged, TogglePasswordVisibility, Submit, ForgotPasswordClicked, CreateAccountClicked; Effects NavigateHome, NavigateForgotPassword(email), NavigateRegister; Screen uses `AppScreen`, `AppTextField` (email keyboard, next), `PasswordField` (done → Submit), `InlineAlert` for banner, `PrimaryButton` (loading), `AppTextButton`s; initial banner from route arg `reason` (makes T066 pass)
- [X] T078 [P] [US2] MVI screen `UpdateRequired` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/update/` (State(minimumVersion), Intent OpenStoreClicked, Effect OpenUrl(storeUrl); Screen blocking, no back, names minimum version) with reducer/ViewModel tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/update/` (FR-018)
- [X] T079 [US2] MVI screen `Home` (placeholder) in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/home/`: State(email, emailVerified, deletionCancelledNotice) derived from `SessionManager.state`; Screen shows email and `InlineAlert` notice when sign-in cancelled deletion; later US3/US5 add intents; tests `HomeReducerTest.kt` / `HomeViewModelTest.kt` in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/home/`
- [X] T080 [US2] Wire navigation in `shared/src/commonMain/kotlin/com/fares/saveme/navigation/AppNavHost.kt`: observe `SessionManager.state`; `Unknown` renders only the background (no flash of wrong screen, FR-011); `SignedOut(reason)` → auth stack at Entry (or SignIn with reason for `SessionEnded`/`PasswordReset`); `SignedIn` → signed-in stack at Home; call `restore()` on start; observe `ApiEvents` → `UpdateRequired(minimumVersion)` pushes the blocking screen on either stack (central handling for every call, FR-018); register SignIn, UpdateRequired, Home entries; map Entry's SignIn effect
- [X] T081 [US2] Koin feature module `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/di/AuthModule.kt` (IdentityApi, AccountSnapshotStore, AuthRepository, SessionManager single, use cases, ViewModels via `viewModelOf`) added to `AppModules.kt`

**Checkpoint**: quickstart scenarios 6, 7, 8, 10, 12 pass on a dev build; T064–T067 green.

---

## Phase 5: User Story 3 - Create an account and verify the email address (Priority: P2)

> **Superseded (2026-09-29)**: built in spec 003 (`specs/003-auth-completion/tasks.md`), which changes this design in four places: registration of an existing address answers 409 with sign-in/reset choices (constitution v1.2.0 P-09.4); a verification re-reads the account instead of `markEmailVerified()`; a reset never signs the phone out locally (the server's session end arrives as a 401); the resend cooldown is 30 s.

**Goal**: Register, "check your inbox", verification via tapped or pasted link, verify prompt with resend, EMAIL_NOT_VERIFIED handling.

**Independent Test**: Register fresh address → open mailed link on phone (tap in dev chooser or paste in Dev tools) → Verified; after sign-in the verify prompt is gone (quickstart 2–5).

### Tests for User Story 3 (write first, must fail)

- [X] T082 [P] [US3] Contract tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/data/RegisterVerifyContractTest.kt`: `register` POST `auth/register` public, body only `{email, password, display_name?, timezone?}` (display_name omitted when blank); 202 → Accepted; 422 pointers `/email`, `/password`, `/display_name`; 429 RATE_LIMITED. `verifyEmail` POST `auth/verify-email` public, body `{token}`; 200 `{email_verified:true}`; 422 VERIFICATION_TOKEN_INVALID. `resendVerification` POST `auth/resend-verification` with bearer, no body; 202; 429 with `retry_after_seconds`; 401 publishes `ApiEvent.SessionEnded`
- [X] T083 [P] [US3] Test `AuthLinkParser` in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/domain/AuthLinkParserTest.kt`: `https://{host}/verify-email?token=abc` → `VerifyEmail("abc")`; `/reset-password?token=…` → `ResetPassword`; dev `http://192.168.1.20:8080/verify-email?token=x` accepted only when configured link host matches; rejects other hosts, other paths, missing/empty token, token > 128 chars; tolerates surrounding whitespace in pasted text (contracts/deep-links.md)
- [X] T084 [P] [US3] MVI tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/register/`: `RegisterReducerTest.kt`, `RegisterViewModelTest.kt` (client validation — password < 8, password == email — blocks request; double Submit sends once; 202 → `Effect.NavigateCheckInbox(email)` for both new and existing address fixtures (identical); server 422 errors land on matching fields via pointer), and in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/verify/`: `VerifyEmailReducerTest.kt`, `VerifyEmailViewModelTest.kt` (`Started` intent → Verifying → Verified; verified while signed in calls `SessionManager.markEmailVerified()`; VERIFICATION_TOKEN_INVALID → `LinkInvalid` with `canResend` only when signed in); Home verify-prompt cases added to `HomeViewModelTest.kt` (resend success → 60 s cooldown state; 429 → wait message)

### Implementation for User Story 3

- [X] T085 [P] [US3] Add DTOs `RegisterRequest(email, password, display_name: String? = null, timezone: String? = null)` (nulls omitted via `explicitNulls = false`), `TokenRequest(token)`, `VerifyEmailDataDto(email_verified)` to `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/data/dto/IdentityDtos.kt`
- [X] T086 [US3] Add `register`, `verifyEmail` (public) and `resendVerification` (bearer) to `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/data/IdentityApi.kt` and pass-through methods to `AuthRepository.kt` returning `ApiResult` (makes T082 pass)
- [X] T087 [US3] Domain use cases in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/usecase/`: `RegisterUseCase` (sends `clientInfo.timezoneId()`, maps to `RegisterOutcome.Accepted | Validation(fieldErrors) | RateLimited | Offline | Error`), `VerifyEmailUseCase` (on success calls `SessionManager.markEmailVerified()` when signed in; `VerifyOutcome.Verified | LinkInvalid | Offline | Error`), `ResendVerificationUseCase` (`ResendOutcome.Sent | RateLimited(seconds) | Error`); outcomes added to `domain/model/Outcomes.kt`
- [X] T088 [P] [US3] `AuthLink` + `AuthLinkParser(linkHost, linkPort?, scheme)` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/AuthLinkParser.kt`; paths `/verify-email`, `/reset-password`, query `token` as constants with `TODO(link-paths): confirm with backend mail templates` (makes T083 pass)
- [X] T089 [US3] MVI screen `Register` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/register/`: State(email, password, passwordVisible, displayName, fieldErrors: Map<Field, FieldError|ApiErrorCode>, submitting, banner?); Intents EmailChanged, PasswordChanged, DisplayNameChanged, TogglePasswordVisibility, Submit, SignInClicked; Effects NavigateCheckInbox(email), NavigateSignIn(email?); Screen with `AppTextField`s, `PasswordField`, `PrimaryButton` "Create account", `AppTextButton` "Already have an account? Sign in" (FR-025, FR-026)
- [X] T090 [P] [US3] MVI screen `CheckInbox` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/checkinbox/`: State(email, kind: Verification|Reset); Intent GoToSignInClicked; Effect NavigateSignIn(email); Verification copy "We sent a link to %1$s. Open it on this phone to confirm your address."; Reset copy "If that address is registered, a link is on its way."; reducer/ViewModel tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/checkinbox/`
- [X] T091 [US3] MVI screen `VerifyEmail` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/verify/`: State(status: Verifying|Verified|LinkInvalid|Offline|Error(requestId), canResend); Intents Started (sent by Route on first composition), Retry, ResendClicked, ContinueClicked; Effects NavigateHome, NavigateSignIn; token held in the ViewModel (from route arg), never in logs
- [X] T092 [US3] Verify prompt on Home (`feature/auth/ui/home/HomeContract.kt`, `HomeViewModel.kt`, `HomeScreen.kt`): State gains resendCooldownSeconds, resendBanner?; Intent ResendClicked; when `!emailVerified` show `InlineAlert(Warning, "Verify your email to add transactions. Check your inbox.", action "Resend email")`; 60 s cooldown driven by a ticker intent `CooldownTick`; hide once verified (FR-028) (makes Home cases of T084 pass)
- [X] T093 [US3] `EMAIL_NOT_VERIFIED` handling in `shared/src/commonMain/kotlin/com/fares/saveme/navigation/AppNavHost.kt`: on `ApiEvent.EmailNotVerified` show an `AppBottomSheet` with the verify copy and "Resend email", which calls the Home store's `ResendClicked` via the signed-in stack (or navigates Home and sends it) (FR-029)
- [X] T094 [US3] Deep-link intake: in `androidApp/src/main/kotlin/com/fares/saveme/MainActivity.kt` pass `intent.dataString` (in `onCreate` and `onNewIntent`) to shared `DeepLinkBus.submit(uri)`; `AppNavHost` parses via `AuthLinkParser` and pushes `VerifyEmail(token)` / `ResetPassword(token)` onto the active stack (replacing the top of the auth stack) (contracts/deep-links.md)
- [X] T095 [US3] Android intent filters: `androidApp/src/staging/AndroidManifest.xml` and `androidApp/src/prod/AndroidManifest.xml` add `<intent-filter android:autoVerify="true">` VIEW/DEFAULT/BROWSABLE, scheme `https`, host `${linkHost}`, paths `/verify-email` and `/reset-password` on `MainActivity`; `androidApp/src/dev/AndroidManifest.xml` adds a non-verified filter with scheme `http`, `android:host="${linkHost}"`, `android:port="${linkPort}"`, same paths; set `android:launchMode="singleTask"` on `MainActivity` in `androidApp/src/main/AndroidManifest.xml`
- [X] T096 [US3] MVI screen `PasteLink` in `shared/src/commonMain/kotlin/com/fares/saveme/devtools/pastelink/`: State(text, parseError); Intents TextChanged, OpenClicked; Effect SubmitLink(uri) (Route forwards to `DeepLinkBus.submit`); invalid → error "Not a verification or reset link"; reducer/ViewModel tests in `shared/src/commonTest/kotlin/com/fares/saveme/devtools/pastelink/`; wire DevTools `PasteLinkClicked`; dev only (FR-027b)
- [X] T097 [US3] Register Register, CheckInbox, VerifyEmail, PasteLink entries in `navigation/AppNavHost.kt` (map Entry `NavigateRegister` and SignIn `NavigateRegister`) and ViewModels/use cases in `feature/auth/di/AuthModule.kt` / `devtools/DevToolsModule.kt`; add all new copy to both strings.xml files

**Checkpoint**: quickstart 2–5 pass; T082–T084 green.

---

## Phase 6: User Story 4 - Recover a forgotten password (Priority: P2)

> **Superseded (2026-09-29)**: built in spec 003 (`specs/003-auth-completion/tasks.md`), which changes this design in four places: registration of an existing address answers 409 with sign-in/reset choices (constitution v1.2.0 P-09.4); a verification re-reads the account instead of `markEmailVerified()`; a reset never signs the phone out locally (the server's session end arrives as a 401); the resend cooldown is 30 s.

**Goal**: Forgot → mailed link → new password → signed out locally → sign in.

**Independent Test**: quickstart scenario 9.

### Tests for User Story 4 (write first, must fail)

- [X] T098 [P] [US4] Contract tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/data/PasswordRecoveryContractTest.kt`: `forgotPassword` POST `auth/forgot-password` public body `{email}` → 202; 422 `/email`; 429. `resetPassword` POST `auth/reset-password` public body `{token, password}` → 204; 422 RESET_TOKEN_INVALID; 422 VALIDATION_FAILED pointer `/password` and `/token`; 429
- [X] T099 [P] [US4] MVI + use-case tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/ui/password/`: `ForgotPasswordReducerTest.kt`, `ForgotPasswordViewModelTest.kt` (identical `NavigateCheckInbox(email)` effect for any well-formed address; double Submit sends once), `ResetPasswordReducerTest.kt`, `ResetPasswordViewModelTest.kt` (8–128 rule enforced before sending; 204 → `ResetPasswordUseCase` calls `SessionManager.signOutLocally(PasswordReset)` then `Effect.NavigateSignIn(email?)`; RESET_TOKEN_INVALID or `/token` pointer → `LinkInvalid`; 429 → wait message)

### Implementation for User Story 4

- [X] T100 [P] [US4] Add DTOs `EmailRequest(email)`, `ResetPasswordRequest(token, password)` to `feature/auth/data/dto/IdentityDtos.kt`; add `forgotPassword`, `resetPassword` (public) to `IdentityApi.kt` and pass-throughs to `AuthRepository.kt` (makes T098 pass)
- [X] T101 [US4] Use cases `ForgotPasswordUseCase` and `ResetPasswordUseCase` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/usecase/`; reset success calls `SessionManager.signOutLocally(PasswordReset)`; outcomes in `domain/model/Outcomes.kt`
- [X] T102 [US4] MVI screen `ForgotPassword` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/password/forgot/`: State(email prefilled from route arg, emailError, submitting, banner); Intents EmailChanged, Submit; Effect NavigateCheckInbox(email) with kind Reset (FR-030)
- [X] T103 [US4] MVI screen `ResetPassword` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/ui/password/reset/`: State(password, passwordVisible, fieldError, submitting, status: Editing|Done|LinkInvalid); Intents PasswordChanged, TogglePasswordVisibility, Submit, RequestNewLinkClicked; Effects NavigateSignIn(email?), NavigateForgotPassword; token from route arg held in ViewModel only (FR-031, FR-032) (makes T099 pass)
- [X] T104 [US4] Register ForgotPassword/ResetPassword in `navigation/AppNavHost.kt` and `AuthModule.kt`; map SignIn `NavigateForgotPassword(email)` (also from the Locked banner action); strings in both files

**Checkpoint**: quickstart 9 passes; T098–T099 green.

---

## Phase 7: User Story 5 - Sign out (Priority: P3)

**Goal**: Sign out of this phone; always clears locally, even offline.

**Independent Test**: quickstart scenario 11; old credential refused after sign-out.

### Tests for User Story 5 (write first, must fail)

- [X] T105 [P] [US5] Tests in `shared/src/commonTest/kotlin/com/fares/saveme/feature/auth/domain/SignOutUseCaseTest.kt`: `signOut` POST `auth/logout` with bearer, no body, 204 (MockEngine); on 204, 401, Offline and 500 alike `SecureStore` and snapshot are cleared and state becomes `SignedOut(UserSignedOut)` (FR-024); plus Home sign-out cases in `HomeViewModelTest.kt` (`SignOutClicked` → confirm sheet visible; `SignOutConfirmed` → use case called once; `SignOutDismissed` → sheet hidden)

### Implementation for User Story 5

- [X] T106 [US5] Add `signOut()` to `IdentityApi.kt` and `AuthRepository.kt` (returns `ApiResult<Unit>`); `SignOutUseCase` in `shared/src/commonMain/kotlin/com/fares/saveme/feature/auth/domain/usecase/SignOutUseCase.kt`: best-effort server call with 5 s timeout, then always `SessionManager.signOutLocally(UserSignedOut)` (makes T105 pass)
- [X] T107 [US5] Sign-out on Home (`HomeContract.kt`, `HomeViewModel.kt`, `HomeScreen.kt`): State gains `confirmSignOutVisible`, `signingOut`; Intents SignOutClicked, SignOutConfirmed, SignOutDismissed; Screen: `DestructiveButton` "Sign out" and `AppBottomSheet` confirm ("Sign out of this phone? Your other devices stay signed in.") with `DestructiveButton` confirm and `AppTextButton` cancel; strings in both files

**Checkpoint**: quickstart 11 passes; T105 green.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T108 [P] Architecture guard test in `shared/src/commonTest/kotlin/com/fares/saveme/ArchitectureRulesTest.kt` (or a Gradle verification task in `shared/build.gradle.kts`) scanning `shared/src/commonMain`: `feature/*/domain` imports no `io.ktor`, `androidx.compose`, `androidx.datastore`; `feature/*/data` imports nothing from `domain` or `ui` (P-10.6); `core` imports no `feature` (P-10.7); `feature/*/ui` imports no `data` (P-16.6); `designsystem` imports nothing from `feature` or `core.network`; `*Screen.kt` files import no `ViewModel` and no Koin (P-16.7); every `*ViewModel.kt` under `ui/` extends `MviViewModel` and declares only `onIntent` as public fun (P-16.1/3); no identifier contains the codename (P-11)
- [ ] T109 [P] Hardcoded-value guard: extend T108 to fail on string literals passed to `Text(`/`label =` and on `Color(0x`, `.dp`, `.sp` literals inside `feature/` (allowlist `@Preview` functions and tests) (P-06.1, P-07.2, SC-008)
- [ ] T110 [P] Arabic copy review of `shared/src/commonMain/composeResources/values-ar/strings.xml`: glossary terms (محفظة etc.), no "token"/"PAT" wording, Western digits in placeholders (P-01, P-06)
- [ ] T111 [P] Logging review: grep `shared/src` for `println`, `Log.`, `Napier` or Ktor `Logging` plugin; ensure none logs request bodies, `Authorization` header, `token` or `password` (P-09.2)
- [ ] T112 Release manifest check: `./gradlew :androidApp:processProdReleaseMainManifest` then assert merged manifest has no `http` data scheme, no cleartext `networkSecurityConfig`, and no dev-only entry points; document the command in `specs/001-design-system-auth/quickstart.md` if it differs (Quality Gate 5)
- [ ] T113 Update `specs/001-design-system-auth/plan.md` Constitution Check P-14 to PASS once the dependency list is approved, and remove the Complexity Tracking row
- [ ] T114 Run `./gradlew build allTests verifyRoborazziAndroidHostTest` and the full `specs/001-design-system-auth/quickstart.md` manual run against the LAN dev backend, including relaunch timing for SC-006 (`adb shell am start -W -n com.fares.saveme/.MainActivity`, expect `TotalTime` < 2000 ms with a stored session); record results under a "Last run" note in `quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none; T001 gated on owner approval of dependencies.
- **Foundational (Phase 2)**: after Setup; blocks US2–US5. US1 needs T001–T005, T028–T029 (MVI base + harness), T032–T033.
- **US1 (Phase 3)**: after Setup + T028/T029/T032/T033; can run in parallel with the rest of Phase 2 (touches only `designsystem/`, `devtools/`, entry screen, fonts, strings).
- **US2 (Phase 4)**: after Phase 2 **and** US1 components (T046–T057).
- **US3 (Phase 5)**, **US4 (Phase 6)**: after US2 (need `SessionManager`, `IdentityApi`, `AuthRepository`, Home). Independent of each other except shared files `IdentityApi.kt`, `IdentityDtos.kt`, `AuthRepository.kt`, `Outcomes.kt`, `AppNavHost.kt`, `AuthModule.kt` — serialise edits to those. US4's reset-link intake reuses US3's T088/T094/T095; if US4 runs first, do those three first.
- **US5 (Phase 7)**: after US2 (Home store).
- **Polish (Phase 8)**: after desired stories.

### Within Each Story

Tests first (fail) → DTOs/models → API/repository (data) → use cases/SessionManager (domain) → Contract + reducer → ViewModel → Route + Screen → navigation/DI wiring.

### Parallel Opportunities

- Setup: T004–T007 together.
- Foundation: T009–T012 tests together; T013–T018, T020, T022–T026, T028–T029 together.
- US1: T034–T038 together; T039–T041, T043 together; all component files T046–T057 together once T044–T045 are done.
- US2: T064–T067 together; T068–T071 together; T078 alongside T077.
- US3: T082–T084 together; T085, T088, T090 together.
- US4: T098, T099 together.
- Polish: T108–T111 together.

---

## Parallel Example: User Story 1

```bash
# Tests first:
Task: "T034 MoneyFormatterTest"  Task: "T035 BudgetStateTest"
Task: "T036 TouchTargetTest"     Task: "T038 ComponentCatalog reducer/ViewModel tests"

# After theme (T044) and formatter (T045), all component families at once:
Task: "T046 Buttons.kt"   Task: "T047 IconButtons.kt"  Task: "T048 Bars.kt"
Task: "T049 Selection.kt" Task: "T050 Fields.kt"       Task: "T051 Money.kt"
Task: "T052 Cards.kt"     Task: "T053 Lists.kt"        Task: "T054 BudgetProgress.kt"
Task: "T055 Feedback.kt"  Task: "T056 BottomSheet + EmptyAndLoading" Task: "T057 AppScreen.kt"
```

## Parallel Example: User Story 2

```bash
Task: "T064 SignInContractTest"   Task: "T065 SessionManagerTest"
Task: "T066 SignIn reducer + ViewModel tests"  Task: "T067 AuthErrorCopyTest"
Task: "T068 IdentityDtos.kt"      Task: "T069 Account.kt + Outcomes.kt"
Task: "T070 AuthState.kt"         Task: "T071 ClientValidation.kt"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup (after dependency approval) → T028/T029/T032/T033 → Phase 3 US1 (design system + catalogue) → **validate against canvas** (demoable without backend).
2. Rest of Phase 2 → Phase 4 US2 sign-in → **validate on LAN backend**. This is the functional MVP.

### Incremental Delivery

3. US3 register + verify → quickstart 2–5.
4. US4 password recovery → quickstart 9.
5. US5 sign-out → quickstart 11.
6. Polish → full quickstart run.

### Open items that do not block starting

- Link paths / `token` query name (T088 `TODO(link-paths)`), staging/prod base URLs (T003 `TODO(base-url)`), `assetlinks.json` on backend hosts, owner approval of dependencies (T001, T113).

---

## Notes

- [P] = different files, no dependency on incomplete tasks.
- Commit after each task or logical group; stop at any checkpoint to validate the story.
- Files touched by many stories (`IdentityApi.kt`, `IdentityDtos.kt`, `AuthRepository.kt`, `Outcomes.kt`, `AppNavHost.kt`, `AuthModule.kt`, Home screen files, both `strings.xml`) are never marked [P] across stories.
