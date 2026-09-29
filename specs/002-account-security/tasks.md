---
description: "Task list for 002-account-security"
---

# Tasks: Account Security & Profile

**Input**: Design documents from `specs/002-account-security/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (account-api-client.md, ui-and-components.md), quickstart.md; constitution v1.1.0

**Tests**: Included. Constitution P-14 requires contract tests per endpoint (every handled code), a test per invariant (spec §4), screenshot tests per component, and P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Organization**: Phase 2 builds the database, locale override, 401 fix, components and the Account hub every story uses. US1–US4 are then independent of each other.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (abbreviated `cm/…` below)
- Android actuals: `shared/src/androidMain/kotlin/com/fares/saveme/…` (`am/…`)
- iOS actuals: `shared/src/iosMain/kotlin/com/fares/saveme/…` (`im/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`)
- Screenshot tests: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Everything in spec 001's "Standing rules" (P-06 strings in both files, P-07 design system only, P-09 never log passwords/credentials, P-10 layering, P-11 naming) applies.
- **MVI screen recipe (P-16)**, as in spec 001 tasks: `<Screen>Contract.kt` (immutable State, sealed Intent/Effect/Change, pure top-level `reduce`), `<Screen>ViewModel.kt` on `MviViewModel` taking use-case lambdas/flows (never repositories), `<Screen>Route.kt` (`koinViewModel()`, `collectAsStateWithLifecycle`, effects in `LaunchedEffect`), stateless `<Screen>Screen.kt` + `@Preview`; tests `<Screen>ReducerTest.kt` + `<Screen>ViewModelTest.kt` with `runMviTest`.
- Screens read syncable data (Profile, Device Session) **only** from the database via repository `Flow`s (P-04.1). Never render a network response.
- Credential actions (end session, change password, request/cancel deletion) are never retried automatically and never queued offline (FR-024).
- UI copy says "device"/"session", never "token" (FR-026).

---

## Phase 1: Setup

**Purpose**: Add Room KMP and the database skeleton.

- [X] T001 Add to `gradle/libs.versions.toml`: versions `room = "2.8.5"`, `sqlite = "2.7.1"`, `ksp = "2.3.12"`; libraries `androidx-room-runtime` (`androidx.room:room-runtime`), `androidx-room-compiler` (`androidx.room:room-compiler`), `androidx-sqlite-bundled` (`androidx.sqlite:sqlite-bundled`); plugins `ksp` (`com.google.devtools.ksp`) and `androidx-room` (`androidx.room`); declare both plugins `apply false` in root `build.gradle.kts`
- [X] T002 In `shared/build.gradle.kts`: apply `ksp` + `androidx-room` plugins; `commonMain` deps `androidx-room-runtime`, `androidx-sqlite-bundled`; `room { schemaDirectory("$projectDir/schemas") }`; KSP for every target: `add("kspAndroid", libs.androidx.room.compiler)`, `kspIosArm64`, `kspIosSimulatorArm64` (and `kspIosX64` if that target exists); confirm `./gradlew :shared:compileKotlinMetadata :androidApp:assembleDevDebug` still passes
- [X] T003 Create `cm/core/database/AppDatabase.kt`: `@Database(entities = [ProfileEntity::class, DeviceSessionEntity::class, SyncScopeEntity::class], version = 1, exportSchema = true)` `@ConstructedBy(AppDatabaseConstructor::class) abstract class AppDatabase : RoomDatabase` with `profileDao()`, `deviceSessionDao()`, `syncScopeDao()`; `expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>`; `fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase` = `builder.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()` (entities/DAOs created in T004–T006)
- [X] T004 [P] Create `cm/core/database/SyncScopeEntity.kt` (`@Entity(tableName = "sync_scope")`: `scope: String` PK — values `"profile"`, `"device_sessions"`; `refreshedAt: String` UTC instant ISO) and `SyncScopeDao.kt` (`upsert`, `observe(scope): Flow<SyncScopeEntity?>`)
- [X] T005 [P] Create `cm/feature/account/data/local/ProfileEntity.kt` per data-model §1 `profile` table (every column listed, snake_case `@ColumnInfo` names; `pendingJson`, `pendingBaseVersion`, `conflictJson`, `rejectionJson` nullable) and `ProfileDao.kt`: `observe(): Flow<ProfileEntity?>`, `get(): ProfileEntity?`, `@Upsert upsert`, `@Query` `updateServerCopy(...)` that writes only server-copy columns + `refreshed_at` and never touches `pending_json`/`pending_base_version`/`conflict_json`/`rejection_json`, `setPending(json, baseVersion)`, `setConflict(json)`, `setRejection(json)`, `clearPending()` (clears pending, conflict, rejection)
- [X] T006 [P] Create `cm/feature/account/data/local/DeviceSessionEntity.kt` per data-model §1 `device_session` table and `DeviceSessionDao.kt`: `observeAll(): Flow<List<DeviceSessionEntity>>` ordered `signed_in_at DESC`, `@Transaction replaceAll(items)` (delete all + insert), `delete(id)`
- [X] T007 [P] Create `am/core/database/DatabaseBuilder.android.kt` (`fun databaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase>` at `context.getDatabasePath("saveme.db")`) and `im/core/database/DatabaseBuilder.ios.kt` (`NSDocumentDirectory` + `/saveme.db`); register `single<AppDatabase> { buildAppDatabase(databaseBuilder(...)) }` in `am/core/di/PlatformModule.android.kt` and `im/core/di/PlatformModule.ios.kt`; add `saveme.db*` to the Android backup/data-extraction exclusions in `androidApp/src/main/res/xml/` (P-09)
- [X] T008 Build the Android dev app and run `./gradlew allTests`; commit the generated `shared/schemas/…/1.json`

**Checkpoint**: app builds with an (unused) database on Android and iOS.

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Core network fixes, database cleaners, language override, new components, sign-out, Account hub.

### Tests (write first, must fail)

- [X] T009 [P] Test 401 handling in `ct/core/network/SessionEndedEventTest.kt` (MockEngine via `AuthTestEnv`): credentialed 401 with `code: UNAUTHENTICATED` publishes `ApiEvent.SessionEnded`; credentialed 401 with `code: INVALID_CREDENTIALS` does **not**; credentialed 401 with no body publishes it; public (no Authorization) 401 does not (research R-04, FR-027)
- [X] T010 [P] Test paging + conflict decoding in `ct/core/network/PageAndConflictTest.kt`: `callPage<T>` decodes `data` list + `meta {next_cursor, has_more, limit}`; `ProblemDetails.current` holds the raw JSON of a 409 body; `ApiErrorCode.from("VERSION_CONFLICT") == VERSION_CONFLICT` with `RetryClass.AfterChange`
- [X] T011 [P] Room DAO tests in `ht/core/database/AccountDaoTest.kt` (Robolectric + `AndroidSQLiteDriver` in-memory; Room needs a platform context, so SQL tests live in androidHostTest while domain tests use fake DAOs in commonTest): `updateServerCopy` leaves `pending_json` intact (invariant "refresh never overwrites a pending edit"); `replaceAll` replaces rows atomically; `clearAllTables` empties everything
- [X] T012 [P] Test `AppLanguageStore` in `ct/core/locale/AppLanguageStoreTest.kt`: absent → `effective` = device language (`supportedLanguage`); set `ar` → `effective` = ar and persisted under key `app_language`; `clear()` → device language again; HTTP `Accept-Language` header follows `effective` (extend `ct/core/network/HttpClientFactoryTest.kt`)
- [X] T013 [P] Test sign-out wipe in `ct/feature/auth/domain/SignOutCleanupTest.kt`: `SessionManager.signOutLocally(any reason)` calls every registered `UserDataCleaner` once, and a failing cleaner does not stop the others or the sign-out
- [X] T014 [P] If spec 001 T105 is unchecked: write it now in `ct/feature/auth/domain/SignOutUseCaseTest.kt` + Home cases in `ct/feature/auth/ui/home/HomeViewModelTest.kt` exactly as spec 001 T105 describes
- [X] T015 [P] Screenshot cases for the new components (T021–T024) via their catalogue sections in `ht/designsystem/ComponentScreenshotTest.kt` (en, ar RTL, font scale 2); record with `./gradlew recordRoborazziAndroidHostTest`
- [X] T016 [P] Account hub MVI tests `ct/feature/account/ui/account/AccountReducerTest.kt` + `AccountViewModelTest.kt`: state mirrors `AuthState.SignedIn` account (email, verified, pending-deletion date); each row intent emits its `Navigate` effect; sign-out intents mirror Home's (confirm visible → use case called once → dismissed hides)

### Implementation

- [X] T017 In `cm/core/network/HttpClientFactory.kt` `publishEvents`: publish `SessionEnded` only when the request carried Authorization **and** (`code == UNAUTHENTICATED` or (status 401 and `code == UNKNOWN`)) (makes T009 pass)
- [X] T018 [P] `cm/core/network/ApiErrorCode.kt`: add `VERSION_CONFLICT(RetryClass.AfterChange)`; `ProblemDetails.kt`: add `val current: JsonElement? = null`; update `cm/feature/auth/ui/AuthErrorCopy.kt` exhaustive `when` (VERSION_CONFLICT → `error_profile_conflict`) and `FieldProblem.Server` copy for `IMMUTABLE_FIELD` → `field_error_immutable`; strings in both files
- [X] T019 [P] `cm/core/network/Page.kt`: `@Serializable data class CollectionMeta(@SerialName("next_cursor") val nextCursor: String? = null, @SerialName("has_more") val hasMore: Boolean = false, val limit: Int = 0)`, `data class Page<T>(val items: List<T>, val nextCursor: String?, val hasMore: Boolean)`; in `ApiClient` add `suspend inline fun <reified T> callPage(noinline block: HttpRequestBuilder.() -> Unit): ApiResult<Page<T>>` decoding `data` + `meta` (makes T010 pass)
- [X] T020 [P] `cm/core/session/UserDataCleaner.kt` (`fun interface UserDataCleaner { suspend fun clear() }`); `cm/core/database/DatabaseCleaner.kt` (`UserDataCleaner` calling `clearAllTables()` on `Dispatchers.IO`); `SessionManager` takes `cleaners: List<UserDataCleaner>` and runs each (try/catch, log with Kermit, no user data in the log) inside `signOutLocally`; register with Koin `getAll<UserDataCleaner>()` in `cm/feature/auth/di/AuthModule.kt` and bind `DatabaseCleaner` in `cm/core/di/CoreModule.kt` (makes T013 pass)
- [X] T021 [P] `cm/core/locale/AppLanguage.kt` (`enum class AppLanguage(val tag: String) { Ar("ar"), En("en") }`), `AppLanguageStore.kt` (`KeyValueStore` key `"app_language"`; `language: StateFlow<AppLanguage?>`, `fun effective(): AppLanguage` = override ?: device via `ClientInfo.languageTag()`, `suspend fun set(AppLanguage)`, `suspend fun clear()`, loads persisted value at start); `AppLanguageCleaner : UserDataCleaner`; the `ClientHeaders` plugin reads `appLanguageStore.effective().tag` for `Accept-Language` (pass a `languageProvider: () -> String` into `createHttpClient`, wired in `CoreModule`) (makes T012 pass)
- [X] T022 [P] Runtime locale in Compose: `cm/core/locale/LocalAppLocale.kt` (`expect object LocalAppLocale { val current: String @Composable get; @Composable infix fun provides(value: String?): ProvidedValue<*> }`) with `am/core/locale/LocalAppLocale.android.kt` (sets `Locale.setDefault`, provides `LocalConfiguration` copy with `setLocale`, per the Compose Multiplatform resources "change locale at runtime" pattern) and `im/core/locale/LocalAppLocale.ios.kt` (`NSUserDefaults.standardUserDefaults.setObject(listOf(tag), "AppleLanguages")`); `cm/core/locale/AppLocaleProvider.kt`: `@Composable fun AppLocaleProvider(language: AppLanguage, content)` providing `LocalAppLocale` and `LocalLayoutDirection` (Rtl for ar) inside `key(language)`; wrap `AppNavHost()` in `cm/App.kt` with it, collecting `AppLanguageStore.language` via `koinInject()`
- [X] T023 [P] New icons in `cm/designsystem/icon/AppIcons.kt`: `Person`, `Devices`, `Lock`, `Delete`, `Logout`, `Language` (Material Symbols outlined paths, same size/stroke conventions as existing icons; directional ones none)
- [X] T024 [P] `cm/designsystem/component/AppAlertDialog.kt` per contracts/ui-and-components.md (`title, text, confirmLabel, onConfirm, dismissLabel, onDismiss, destructive = false, confirmLoading = false`; destructive confirm uses the `DestructiveButton` colour role) + catalogue section in `cm/designsystem/catalog/CatalogSections.kt`
- [X] T025 [P] `cm/designsystem/component/AppListItem.kt` (`headline, modifier, supporting: String? = null, leadingIcon: ImageVector? = null, trailing: ListTrailing = ListTrailing.None, onClick: (() -> Unit)? = null`; `sealed interface ListTrailing { None; Chevron; Text(value); Custom(content) }`; min height 56 dp, whole row one ≥ 48 dp touch target with merged semantics, chevron `AppIcons.ChevronForward` auto-mirrored in RTL) + catalogue section
- [X] T026 [P] `cm/designsystem/component/AppSelectField.kt` (read-only `AppTextField` look, `label, value, onClick, modifier, supportingText, errorText, enabled`, `Role.Button` semantics) and `SelectionSheet.kt` (`<T> SelectionSheet(title, options: List<T>, label: (T) -> String, selected: T?, onSelect, onDismiss, searchable = true)` on `AppBottomSheet`, search `AppTextField` filters case-insensitively, selected row shows `AppIcons.Check`) + catalogue sections (makes T015 pass once recorded)
- [X] T027 [P] `cm/designsystem/format/DateTimeFormat.kt`: `formatDateTime(instant: Instant, timeZoneId: String, language: String): String` (e.g. en "12 Sep 2026, 14:05", ar "12 سبتمبر 2026، 14:05", Western digits, falls back to UTC for an unknown zone) and `formatDate(date: LocalDate, language: String)`; `cm/designsystem/money/Currencies.kt`: `val knownCurrencies: List<String>` (ISO 4217 codes present in `MoneyFormatter`'s digit table, plus common ones: EGP, SAR, AED, KWD, QAR, BHD, OMR, JOD, USD, EUR, GBP) — unit tests in `ct/designsystem/format/DateTimeFormatTest.kt`
- [X] T028 If spec 001 T106–T107 are unchecked: implement them now as spec 001 describes (`IdentityApi.signOut`, `AuthRepository.signOut`, `SignOutUseCase`, Home sign-out with `AppBottomSheet` confirm) and tick them in `specs/001-design-system-auth/tasks.md` (makes T014 pass)
- [X] T029 `SessionManager` additions in `cm/feature/auth/domain/SessionManager.kt`: `suspend fun updateAccount(account: AccountDto)` and `suspend fun updateDeletion(state: AccountState, scheduledFor: Instant?)` — both rewrite the stored snapshot (`AccountSnapshotStore`) and emit a new `SignedIn` state with the same entitlements
- [X] T030 Navigation keys in `cm/navigation/Routes.kt`: `@Serializable data object AccountKey`, `ProfileKey`, `DeviceSessionsKey`, `ChangePasswordKey`, `DeleteAccountKey` (all `NavKey`)
- [X] T031 Account hub MVI screen in `cm/feature/account/ui/account/` (recipe): rows via `AppListItem` with leading icons — Profile (`Person`), Devices signed in (`Devices`), Change password (`Lock`), Delete account (`Delete`, error tone text), Sign out (`Logout`, opens the spec 001 confirm sheet, calls `SignOutUseCase`); header shows email + verified/unverified; strings `account_*` per contracts/ui-and-components.md; `cm/feature/account/di/AccountModule.kt` with `viewModel { AccountViewModel(...) }`; add `accountModule` to `appFeatureModules` in `cm/AppModules.kt` (makes T016 pass)
- [X] T032 Home entry + routes: `HomeScreen` top bar action `AppIconButton(AppIcons.Person, stringResource(cd_account))` → `HomeIntent.AccountClicked` → `HomeEffect.NavigateAccount`; in `cm/navigation/AppNavHost.kt` add `entry<AccountKey>` and push it; Account effects push `ProfileKey`/`DeviceSessionsKey`/`ChangePasswordKey`/`DeleteAccountKey` (their entries added in each story)

**Checkpoint**: 401 fix green; database + language override + components in place; Home → Account hub works; sign-out wipes local data.

---

## Phase 3: User Story 1 - See and end the devices signed in (Priority: P1) 🎯 MVP

**Goal**: List active device sessions from the database; end one or all others after confirmation.

**Independent Test**: quickstart scenarios 1–2.

### Tests (write first, must fail)

- [X] T033 [P] [US1] Contract tests in `ct/feature/account/data/SessionsContractTest.kt` (MockEngine via an `AccountTestEnv` built like `AuthTestEnv`, in `ct/feature/account/AccountFixtures.kt` with `sessionJson(id, current, …)` and `pageBody(items, nextCursor, hasMore)`): `listSessions` GET `auth/sessions` with query `filter[state]=active`, `limit=50`, and `cursor` only when given, Authorization present, unknown members ignored; `endSession` DELETE `auth/sessions/{id}` 204; `endOtherSessions` DELETE `auth/sessions` 204; 404 NOT_FOUND on `endSession` maps to done; 401 UNAUTHENTICATED publishes `SessionEnded`
- [X] T034 [P] [US1] Domain tests in `ct/feature/account/domain/SessionRefresherTest.kt`: refresh loops cursors until `has_more == false` (stop at 20 pages), then `replaceAll` once and updates `sync_scope("device_sessions")`; Offline leaves stored rows untouched; `EndSessionUseCase` NOT_FOUND → Done and triggers a refresh; no automatic retry on Offline
- [X] T035 [P] [US1] MVI tests `ct/feature/account/ui/sessions/DeviceSessionsReducerTest.kt` + `DeviceSessionsViewModelTest.kt`: current session has no end action (invariant); EndClicked → confirm One(id, name); ConfirmEnd → use case once, `ending` true then false, list refreshed; DismissConfirm → nothing ended; "end others" hidden when only the current session exists (FR-006); offline end → banner `error_offline`, list unchanged

### Implementation

- [X] T036 [P] [US1] DTOs in `cm/feature/account/data/dto/SessionDtos.kt`: `DeviceSessionDto(id, @SerialName("user_id") userId, version: Long, @SerialName("device_name") deviceName, platform, @SerialName("app_version") appVersion, state, @SerialName("end_reason") endReason: String? = null, @SerialName("signed_in_at") signedInAt, @SerialName("last_used_at") lastUsedAt: String? = null, @SerialName("ended_at") endedAt: String? = null, current: Boolean)`
- [X] T037 [US1] `cm/feature/account/data/AccountApi.kt` (`class AccountApi(client: ApiClient)`): `listSessions(cursor: String?)` (`parameter("filter[state]", "active")`, `parameter("limit", 50)`, cursor if non-null) via `callPage<DeviceSessionDto>`, `endSession(id)` and `endOtherSessions()` via `call<Unit>` (makes T033 pass)
- [X] T038 [US1] `cm/feature/account/data/DeviceSessionRepository.kt` (I/O only): `observe(): Flow<DeviceSessionList>` joining `DeviceSessionDao.observeAll()` + `SyncScopeDao.observe("device_sessions")`, mapping to domain `DeviceSession` (`Instant.parse`); `fetchPage(cursor)`, `store(items)`, `end(id)`, `endOthers()`
- [X] T039 [US1] Domain in `cm/feature/account/domain/`: `model/DeviceSession.kt` (`DeviceSession`, `DeviceSessionList`, `EndSessionOutcome { Done, Offline, Error(code, requestId) }`, `RefreshOutcome`); `SessionRefresher` (single-flight `Mutex`, all pages, max 20); `usecase/EndSessionUseCase.kt`, `usecase/EndOtherSessionsUseCase.kt` (NOT_FOUND → Done; Done → refresh) (makes T034 pass)
- [X] T040 [US1] MVI screen `cm/feature/account/ui/sessions/` (recipe): `AppScreen` + `AppTopAppBar(sessions_title, back)`; `AppListItem` per session — headline device name, supporting "Signed in {formatDateTime(signedInAt, profile tz, lang)} · Last used {…}" or `sessions_not_used_yet`, trailing `Text("This device")` for current else `Custom(AppTextButton(sessions_end))`; `SecondaryButton(sessions_end_others)` when another session exists else `EmptyState`-style note `sessions_none_other`; offline note `sessions_updated_at` from `refreshedAt`; `AppAlertDialog` (destructive) for both confirmations; refresh on open; profile timezone read from the profile flow (fallback: device timezone) (makes T035 pass)
- [X] T041 [US1] Wire DI in `AccountModule.kt` (`AccountApi`, `DeviceSessionRepository`, `SessionRefresher`, use cases, `DeviceSessionsViewModel`) and `entry<DeviceSessionsKey>` in `AppNavHost.kt`; strings `sessions_*` en + ar
- [X] T042 [P] [US1] Screenshot cases for `DeviceSessionsScreen` (list with current + 2 others; confirm dialog) in `ht/feature/account/AccountScreensScreenshotTest.kt` (en, ar RTL, font scale 2)

**Checkpoint**: quickstart 1–2 pass; US1 usable on its own.

---

## Phase 4: User Story 2 - Change the password while signed in (Priority: P1)

**Goal**: Change password with the current one; this device stays signed in; others are signed out.

**Independent Test**: quickstart scenario 3.

### Tests (write first, must fail)

- [X] T043 [P] [US2] Contract tests in `ct/feature/account/data/ChangePasswordContractTest.kt`: POST `auth/change-password`, Authorization present, body key set exactly `{current_password, new_password}`; 204 → Success; 401 INVALID_CREDENTIALS → WrongCurrentPassword **and no `SessionEnded` event**; 423 ACCOUNT_LOCKED with `Retry-After: 900` → Locked(900); 422 with pointers `/current_password`, `/new_password` → Validation; 429 → RateLimited
- [X] T044 [P] [US2] MVI tests `ct/feature/account/ui/changepassword/ChangePasswordReducerTest.kt` + `ViewModelTest.kt`: client validation blocks submit — new password "8 to 128 characters, not equal to the address" (`ClientValidation.validateNewPassword(new, account email)`), current non-empty; submit once while submitting; WrongCurrentPassword → error on current field only, still signed in (invariant "change password never signs this device out"); Locked → submit disabled with minutes; Success → `Done(otherDevicesSignedOut = true)` effect and a sessions refresh requested

### Implementation

- [X] T045 [P] [US2] `ChangePasswordRequest(@SerialName("current_password") currentPassword, @SerialName("new_password") newPassword)` in `cm/feature/account/data/dto/AccountDtos.kt`; `AccountApi.changePassword(current, new)` via `call<Unit>` (makes T043 pass)
- [X] T046 [US2] `cm/feature/account/domain/model/Outcomes.kt`: `ChangePasswordOutcome { Success, WrongCurrentPassword, Locked(seconds), Validation(fields: Map<PasswordField, String>), RateLimited(seconds), Offline, Error(code, requestId) }`, `enum PasswordField { Current, New }` (pointer `/current_password`, `/password` → Current; `/new_password` → New); `usecase/ChangePasswordUseCase.kt` (no retry; Success → `SessionRefresher.refresh()` best-effort)
- [X] T047 [US2] MVI screen `cm/feature/account/ui/changepassword/` (recipe): two `PasswordField`s (current, new with supporting text of the rules), `PrimaryButton(change_password_submit, loading)`, `InlineAlert(Error)` for Locked/RateLimited/Error with `minutesToWait`; on `Done` pop to Account and show snackbar `change_password_done_other_devices`; strings `change_password_*` (makes T044 pass)
- [X] T048 [US2] DI + `entry<ChangePasswordKey>` in `AppNavHost.kt`; screenshot cases for `ChangePasswordScreen` (empty, field error, locked) in `ht/feature/account/AccountScreensScreenshotTest.kt`

**Checkpoint**: quickstart 3 passes.

---

## Phase 5: User Story 3 - View and edit the profile (Priority: P2)

**Goal**: Profile stored on the device, edited offline-first, synced with conflict choice; language drives the app.

**Independent Test**: quickstart scenarios 4–7.

### Tests (write first, must fail)

- [X] T049 [P] [US3] Contract tests in `ct/feature/account/data/ProfileContractTest.kt`: `getMe` GET `me` decodes `MeDto{account, profile}` ignoring unknown members; `saveProfile` PUT `me/profile` body key set exactly `{display_name, locale, timezone, default_currency, budget_period_start_day, version}` with `display_name: null` present when cleared; 200 → ProfileDto; 409 VERSION_CONFLICT → `current` decodes as `ProfileDto`; 422 with `errors[{pointer:"/default_currency", code:"IMMUTABLE_FIELD"}]` → field error on DefaultCurrency
- [X] T050 [P] [US3] Syncer tests in `ct/feature/account/domain/ProfileSyncerTest.kt` (real in-memory DB + MockEngine): local save writes `pending_json` + base version and emits Pending immediately, offline OK (FR-011a); push sends base version; 200 → server copy replaced, pending cleared; 409 → conflict stored, pending kept, no further push until resolved; KeepMine → base version := conflict version, conflict cleared, push again; UseTheirs → server copy := conflict, pending + conflict cleared; 422 → rejection stored, pending kept; Offline → pending kept, retry scheduled (5 s, 15 s, 60 s, then 5 min; use a test scheduler); a pull (`refresh`) during Pending updates server copy only (invariant); pull and push are single-flight
- [X] T051 [P] [US3] Language tests (in `ct/feature/account/domain/ProfileSyncerTest.kt` and `ct/core/locale/AppLanguageStoreTest.kt`): pull with `locale: "ar"` sets `AppLanguageStore` to Ar; local save with a new locale sets it at once; UseTheirs with a different locale switches to that locale; sign-out clears it (FR-016)
- [X] T052 [P] [US3] MVI tests `ct/feature/account/ui/profile/ProfileReducerTest.kt` + `ViewModelTest.kt`: form initialised from `ProfileView.effective`; Save disabled until dirty; budget day only 1–28; display name > 100 chars after trim → error, empty → null; currency field read-only when `currencyLocked`; Pending shows `profile_waiting_to_sync`; Conflict shows both versions and KeepMine/UseTheirs intents call the syncer once; rejection maps to field errors; after save with a new start day, `budgetStartDayFrom` shown when present

### Implementation

- [X] T053 [P] [US3] DTOs in `cm/feature/account/data/dto/AccountDtos.kt`: `ProfileDto(id, @SerialName("user_id") userId, version: Long, @SerialName("display_name") displayName: String? = null, locale, timezone, @SerialName("default_currency") defaultCurrency, @SerialName("default_currency_locked") defaultCurrencyLocked, @SerialName("budget_period_start_day") budgetPeriodStartDay: Int, @SerialName("budget_period_start_day_from") budgetPeriodStartDayFrom: String? = null)`, `MeDto(account: AccountDto, profile: ProfileDto)`, `ProfileWriteRequest(...)` whose `display_name` is always encoded (use `@EncodeDefault` or a custom serializer since `AppJson` has `explicitNulls = false`); `AccountApi.getMe()`, `AccountApi.saveProfile(request)` (makes T049 pass)
- [X] T054 [US3] Domain models `cm/feature/account/domain/model/Profile.kt` per data-model §2 (`Profile`, `ProfileDraft`, `ProfileView` with `effective`, `ProfileField` + pointer map, `SyncOutcome`); `cm/feature/account/domain/ProfileValidation.kt` — display name "≤ 100 after trim; empty ⇒ null", budget day "1–28", currency "`^[A-Z]{3}$`, from the known list", timezone "non-empty IANA id"
- [X] T055 [US3] `cm/feature/account/data/ProfileRepository.kt` (I/O only): `observe(): Flow<ProfileView?>` from `ProfileDao.observe()` (decode `pending_json`/`conflict_json`/`rejection_json` with `AppJson`), `storeServerCopy(ProfileDto, refreshedAt)` (insert if absent else `updateServerCopy`), `savePending(draft, baseVersion)`, `storeConflict(ProfileDto)`, `storeRejection(fields)`, `clearPending()`, `fetchMe()`, `push(draft, version)`
- [X] T056 [US3] `cm/feature/account/domain/ProfileSyncer.kt`: `saveLocally(draft)` (writes pending, applies locale to `AppLanguageStore`, triggers push), `push()`, `keepMine()`, `useTheirs()`, backoff retry on Offline via an injected `CoroutineScope` + delay schedule (5 s, 15 s, 60 s, then 300 s), single-flight `Mutex`; `cm/feature/account/domain/AccountRefresher.kt`: `refresh()` = `fetchMe()` → `storeServerCopy` + `SessionManager.updateAccount(account)` + apply `profile.locale` to `AppLanguageStore` unless a pending edit exists, then push if pending (makes T050, T051 pass)
- [X] T057 [US3] Refresh triggers: `cm/feature/account/domain/AccountSyncCoordinator.kt` (single, own `SupervisorJob` scope) — collects `SessionManager.state` and calls `AccountRefresher.refresh()` on each transition into `SignedIn`; exposes `onForeground()`; in `cm/App.kt` observe `LocalLifecycleOwner.current.lifecycle` `ON_START` → `onForeground()` (refresh profile, account state and push pending); start the coordinator from `AccountModule` (`createdAtStart = true`)
- [X] T058 [US3] MVI screen `cm/feature/account/ui/profile/` (recipe): header email + verified/unverified `RolePill`-style label; `AppTextField` display name; `SegmentedControl` language (العربية / English); `AppSelectField` timezone → `SelectionSheet(TimeZone.availableZoneIds.sorted())`; `AppSelectField` currency → `SelectionSheet(knownCurrencies)`, disabled with supporting text `profile_currency_locked` when locked; `AppSelectField` budget day → `SelectionSheet((1..28).toList())`, supporting text "Applies from {formatDate(from)}" when present; `PrimaryButton(profile_save, enabled = dirty)`; `InlineAlert(Info, profile_waiting_to_sync)` while pending; conflict: `InlineAlert(Warning, profile_conflict_title)` + a card listing differing fields "Mine / Other device" + `PrimaryButton(profile_keep_mine)` + `SecondaryButton(profile_use_theirs)`; triggers `refresh()` on open (makes T052 pass)
- [X] T059 [US3] DI (`ProfileRepository`, `ProfileSyncer`, `AccountRefresher`, `AccountSyncCoordinator`, `ProfileViewModel`) + `entry<ProfileKey>`; strings `profile_*`, `field_error_immutable`; screenshot cases for `ProfileScreen` (synced, pending, conflict, currency locked) in `ht/feature/account/AccountScreensScreenshotTest.kt`

**Checkpoint**: quickstart 4–7 pass.

---

## Phase 6: User Story 4 - Request and cancel account deletion (Priority: P2)

**Goal**: Password-confirmed deletion request with explanation, persistent Home notice, cancel.

**Independent Test**: quickstart scenario 8.

### Tests (write first, must fail)

- [X] T060 [P] [US4] Contract tests in `ct/feature/account/data/DeletionContractTest.kt`: POST `account/deletion-request` body exactly `{password}`, 200 decodes `DeletionScheduleDto{state:"pending_deletion", deletion_scheduled_for}`; 401 INVALID_CREDENTIALS → WrongPassword **and no `SessionEnded`**; 423 → Locked; 422 `/password` → Validation; DELETE `account/deletion-request` 204 → Done
- [X] T061 [P] [US4] Domain + MVI tests: `ct/feature/account/domain/DeletionUseCasesTest.kt` (Scheduled → `SessionManager.updateDeletion(PendingDeletion, date)`; cancel Done → `updateDeletion(Active, null)`; no retry on Offline); `ct/feature/account/ui/deleteaccount/DeleteAccountReducerTest.kt` + `ViewModelTest.kt` (Explain → Continue → Confirm; empty password blocks; WrongPassword error; Scheduled(date) shown); `ct/feature/auth/ui/home/HomeViewModelTest.kt` additions (notice visible iff account state PendingDeletion — invariant; CancelDeletion calls use case once, shows `home_deletion_cancelled_done`)

### Implementation

- [X] T062 [P] [US4] `PasswordConfirmation(password)` and `DeletionScheduleDto(state, @SerialName("deletion_scheduled_for") deletionScheduledFor)` in `AccountDtos.kt`; `AccountApi.requestDeletion(password)` via `call<DeletionScheduleDto>`, `AccountApi.cancelDeletion()` via `call<Unit>` (makes T060 pass)
- [X] T063 [US4] `DeletionOutcome { Scheduled(date: Instant), WrongPassword, Locked(seconds), Validation(fields), Offline, Error(code, requestId) }`, `CancelDeletionOutcome { Done, Offline, Error }` in `Outcomes.kt`; `usecase/RequestDeletionUseCase.kt`, `usecase/CancelDeletionUseCase.kt` calling `SessionManager.updateDeletion` on success
- [X] T064 [US4] MVI screen `cm/feature/account/ui/deleteaccount/` (recipe): step Explain (body `delete_explain_body`: 30-day grace, data restorable until the date, signing in again or cancelling stops it; `DestructiveButton(delete_continue)`), step Confirm (`PasswordField`, `DestructiveButton(delete_confirm, loading)`, errors), step Scheduled (`delete_scheduled_body` with `formatDate`, `PrimaryButton(action_done)` → pop to Home) (makes T061 MVI part pass)
- [X] T065 [US4] Home notice: `HomeState` gains `deletionPendingOn: Instant?`, `cancellingDeletion`; `HomeIntent.CancelDeletion`; `HomeScreen` shows `InlineAlert(Warning, home_deletion_pending(date), actionLabel = home_cancel_deletion)` at the top while pending; `HomeViewModel` takes the cancel use case lambda; snackbar on done (makes T061 Home part pass)
- [X] T066 [US4] DI + `entry<DeleteAccountKey>`; strings `delete_*`, `home_deletion_pending`, `home_cancel_deletion`, `home_deletion_cancelled_done`; screenshot cases for `DeleteAccountScreen` (explain, confirm with error, scheduled) and Home with the pending notice

**Checkpoint**: quickstart 8 passes.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T067 [P] Arabic copy review of every new string in `values-ar/strings.xml` (P-06: no forced uppercase, Western digits, "جهاز"/"جلسة" wording, never "رمز"/token)
- [X] T068 [P] Sign-out wipe end-to-end test in `ht/feature/account/SignOutWipeTest.kt` (real Room database): signed-in with profile + sessions + language ar → sign out → tables empty, `app_language` absent, `Accept-Language` = device language (quickstart 9)
- [X] T069 [P] Logging review: grep new code for Kermit calls; no passwords, credentials, profile values or emails logged (P-09)
- [X] T070 Update `specs/002-account-security/plan.md` if anything deviated; update `README.md` (Room/KSP, schema export, dev database location)
- [ ] T071 Full run: `./gradlew allTests verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug`, then quickstart manual scenarios 1–9 on phone + second device — automated part done 2026-09-28 (host tests 240/240, screenshots verified, dev APK, iOS compile; iOS test run needs macOS); manual scenarios pending on device

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → stories.** Phase 2 blocks all stories (database, 401 fix, components, Account hub).
- **US1, US2, US3, US4 are independent** after Phase 2. US3's `AccountSyncCoordinator` also refreshes account state used by US4's notice, but US4 works without it (deletion endpoints update the snapshot directly).
- US1's session list uses the profile timezone when US3 exists, device timezone otherwise.
- Within a story: tests → DTOs/API → repository → domain → screen → DI/nav → screenshots.

## Parallel Examples

- Phase 1: T004, T005, T006, T007 together after T003.
- Phase 2 tests: T009–T016 all [P]. Implementation: T018, T019, T020, T021, T022, T023, T024, T025, T026, T027 in parallel; then T028–T032.
- US1: T033, T034, T035 together; T036 alongside; then T037 → T038 → T039 → T040 → T041; T042 last.
- Stories in parallel: one developer each on US1, US2, US3, US4 once Phase 2 is done.

## Implementation Strategy

- **MVP**: Phase 1 + Phase 2 + US1 (devices signed in) — the security-critical capability.
- **Increment 2**: US2 (change password) — completes "lost phone" response.
- **Increment 3**: US3 (profile, offline-first, language).
- **Increment 4**: US4 (deletion).
- Polish last; each checkpoint is shippable.
