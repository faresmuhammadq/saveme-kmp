# Implementation Plan: Account Security & Profile

**Branch**: `002-account-security` (spec directory; work currently on `main`) | **Date**: 2026-09-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-account-security/spec.md`

## Summary

Add an Account area to the signed-in app: devices signed in (list, end one, end all others), change password, profile view/edit (display name, language, timezone, default currency, budget start day) and account deletion with a 30-day grace notice and cancel. Profile and Device Session are syncable, so this feature introduces the app's on-device database (Room KMP + bundled SQLite): screens observe the database, profile edits are written locally first and pushed by a syncer with the last server `version`, and `VERSION_CONFLICT` is resolved by the person ("Keep mine" / "Use the other device's"). The profile language also drives the app's display language through a runtime locale override. Credential actions (ending sessions, change password, deletion) stay online-only. A latent bug is fixed: a credentialed 401 currently signs the person out even when the code is `INVALID_CREDENTIALS` (wrong current password).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack (Compose Multiplatform 1.12.1 + Material 3, lifecycle-viewmodel MVI base, Navigation 3, Ktor 3.6.0, kotlinx.serialization, coroutines, kotlinx-datetime 0.8.0, Koin 4.2.2, DataStore, Kermit) **plus** Room KMP 2.8.5 (runtime + compiler via KSP 2.3.12 + Room Gradle plugin) and `androidx.sqlite:sqlite-bundled` 2.7.1 (R-01)

**Storage**: Room database `saveme.db` (tables `profile`, `device_session`, `sync_scope`) for syncable user data; existing Keystore DataStore for the credential; KeyValueStore gains `app_language`

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor MockEngine contract tests, Room DAO tests on an in-memory database (bundled driver, host), Roborazzi screenshot tests (new components + new screens)

**Target Platform**: Android minSdk 24 / targetSdk 37 (live API); iOS compiles (Room iOS actual built, no live sign-in per spec 001)

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: Account screens render from the database in < 100 ms after navigation; profile save shows "saved" instantly (local write); pull of `/me` + sessions completes in one round of requests (sessions: all pages, ≤ 20)

**Constraints**: P-04 offline-first for Profile/Device Session; no automatic retry or offline queue for credential actions (FR-024); 401 handling by code (R-04); error copy by code; en + ar with RTL switching at runtime; no user data survives sign-out

**Scale/Scope**: 5 new screens + Home changes, 8 endpoints, 3 tables, 4 new components + 6 icons, ~60 new string keys × 2 languages

## Constitution Check

*Constitution v1.1.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: P-04 was **FAIL (non-negotiable)** against the spec as first written (online-only profile save of a syncable entity). Resolved before design by the owner's decision (spec Clarifications, 2026-09-28): add an on-device database. **Post-design (after Phase 1)** — table below; evidence cites Phase 1 artifacts.

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Glossary terms Profile, Device Session, Budget Period; UI copy "device"/"devices signed in", never "token" (contracts/ui-and-components.md Copy, FR-026) |
| P-02 API contract fidelity | PASS | Request DTOs mirror identity.yaml exactly incl. explicit `display_name: null` (contracts/account-api-client.md); unknown members ignored; `VERSION_CONFLICT` + `IMMUTABLE_FIELD` mapped by code (R-10); 401 branches on code not status (R-04); no auto-retry of credential writes (FR-024) |
| P-03 Money | PASS | Only the default currency *code* is handled; currency list shared with `MoneyFormatter` digit table (R-09); no amounts |
| P-04 Offline-first | PASS | Room DB is the UI source of truth for Profile and Device Session (data-model §1, R-01); profile edits written locally first and pushed with last `version` (R-02); conflict resolved by user choice, never silent overwrite (R-03); UUIDv7/tombstone rules N-A: client creates/deletes no syncable records; ending a session is a credential revocation (rule 6) |
| P-05 Time | PASS | Instants stored UTC; times presented in the profile timezone via `formatDateTime` (R-09) |
| P-06 Localisation & RTL | PASS | All copy in `values/` + `values-ar/`; runtime language switch re-resolves resources and layout direction (R-06); Western digits in date formatting (R-09) |
| P-07 Design system only | PASS | New screens use only `designsystem` components; 4 new components added to the library first (R-08, contracts/ui-and-components.md) |
| P-08 Accessibility | PASS | New components guarantee 48 dp targets, merged row semantics, content descriptions (contracts/ui-and-components.md); font scale 2 screenshots |
| P-09 Security & privacy | PASS | Sign-out wipes DB + language via `UserDataCleaner`s (R-07); credential untouched by this feature; password fields masked; passwords never stored or logged |
| P-10 Shared-first architecture | PASS | All logic in `commonMain` (`core.database`, `core.locale`, `feature.account.{data,domain,ui}`); platform code only for DB builder and locale actuals; repositories do I/O only, `ProfileSyncer`/use cases orchestrate (rule 6); `SessionManager` calls cleaners through a `core` interface, never the account feature (rule 7) |
| P-11 Naming | PASS | `AppAlertDialog`, `AppListItem`, `AppSelectField`, `SelectionSheet`, `AppLanguage`, `AccountApi` follow existing prefixes |
| P-12 Entitlements & billing | N-A | No gated capability; entitlement summary untouched |
| P-13 Observability | PASS | Correlation id per request unchanged; generic errors show `request_id` (Outcome.Error carries it) |
| P-14 Quality gates | PASS | Contract tests per endpoint incl. every handled code (contracts/account-api-client.md); invariant tests per spec §4; screenshot tests for new components/screens; new dependencies approved by the owner 2026-09-28 (R-01) |
| P-15 Scope guard | PASS | No MVP-guarded capability; Google sign-in, 2FA, export excluded |
| P-16 MVI presentation | PASS | Each new screen has Contract (State/Intent/Effect/Change + pure `reduce`), ViewModel on `MviViewModel`, Route, stateless Screen; reducer + VM tests (data-model §4) |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-account-security/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── account-api-client.md
│   └── ui-and-components.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/
├── build.gradle.kts                    # + ksp, room plugin, room/sqlite deps, schema dir
├── schemas/                            # exported Room schema (v1)
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── App.kt                      # wraps AppNavHost in AppLocaleProvider (R-06)
    │   ├── core/
    │   │   ├── database/               # AppDatabase, entities, DAOs, DatabaseFactory (expect), DatabaseCleaner
    │   │   ├── locale/                 # AppLanguage, AppLanguageStore, LocalAppLocale (expect), AppLocaleProvider
    │   │   ├── session/UserDataCleaner.kt
    │   │   └── network/                # + Page/CollectionMeta, callPage, ProblemDetails.current, VERSION_CONFLICT, 401-by-code
    │   ├── designsystem/
    │   │   ├── component/              # + AppAlertDialog, AppListItem, AppSelectField, SelectionSheet
    │   │   ├── icon/AppIcons.kt        # + Person, Devices, Lock, Delete, Logout, Language
    │   │   ├── format/DateTimeFormat.kt
    │   │   └── money/Currencies.kt
    │   ├── feature/auth/               # + signOut (001 US5 if open), SessionManager.updateAccount/cleaners, Home changes
    │   ├── feature/account/
    │   │   ├── data/                   # AccountApi, dto/, ProfileRepository, DeviceSessionRepository
    │   │   ├── domain/                 # model/, ProfileSyncer, AccountRefresher, use cases, ProfileValidation
    │   │   ├── ui/                     # account/, profile/, sessions/, changepassword/, deleteaccount/, AccountErrorCopy
    │   │   └── di/AccountModule.kt
    │   └── navigation/                 # + AccountKey, ProfileKey, DeviceSessionsKey, ChangePasswordKey, DeleteAccountKey
    ├── androidMain/…/core/database/, core/locale/   # DB builder (context path), locale actual
    ├── iosMain/…/core/database/, core/locale/       # DB builder (Documents), locale actual
    ├── commonTest/…/feature/account/, core/database/, core/locale/
    └── androidHostTest/…/                            # screenshots for new components + account screens
```

**Structure Decision**: Stay in the single `:shared` module (spec 001 R-01) with a new `feature.account` package and new `core.database` / `core.locale` packages. The database lives in `core` because later features (wallets, transactions) share it; each feature owns its entities and DAOs.

## Implementation phases (input to /speckit-tasks)

1. **Setup** — Room/KSP/sqlite deps + plugins, schema dir; `AppDatabase` + builders; Koin wiring.
2. **Foundation** — 401-by-code fix (R-04) + test; `VERSION_CONFLICT`, `ProblemDetails.current`, `callPage`; `UserDataCleaner` + SessionManager hooks + `updateAccount`; `AppLanguageStore` + locale actuals + `Accept-Language` from it; new components + icons + catalogue + screenshots; date/currency formatting; 001 US5 sign-out if still open; Account hub + Home entry + navigation keys.
3. **US1 Devices signed in** — DTOs, API, DAO, repository/refresher, use cases, screen.
4. **US2 Change password** — API, use case, screen.
5. **US3 Profile** — `/me` + `/me/profile` API, profile DAO/repository, `ProfileSyncer` (push, conflict, rejection, backoff), refresh triggers (sign-in, foreground, screen open), language application, screen with conflict UI and pickers.
6. **US4 Account deletion** — API, use cases, DeleteAccount flow, Home pending-deletion notice + cancel, account state refresh on foreground.
7. **Polish** — Arabic copy review, sign-out wipe test end-to-end, quickstart run.

## External dependencies (not in this repo)

- Backend spec 008 (`/me`, `/me/profile`) committed to `saveme-backend`; currently only in its working tree.
- Future: user-scope change feed replaces the interim full re-reads (R-02).

## Implementation notes (deviations from the design above)

- **DAO tests** run in `androidHostTest` (Robolectric + framework SQLite driver): Room needs a platform context, so SQL is tested there, while repository/syncer/use-case tests in `commonTest` use in-memory fake DAOs that mirror the SQL.
- **Home view model** is registered in `accountModule` (not `authModule`), because it now takes the cancel-deletion and sign-out use cases.
- **Change password success** is confirmed on the same screen (message + Done) instead of a snackbar on the Account hub, avoiding a cross-screen message channel.
- **`AppNavHost`** now resets the signed-in stack only on the *transition* into signed-in; before, any refreshed account (same person) would have thrown the person back to Home.
- **`ProfileSyncer.saveLocally`** returns the push `Job` (tests join it; screens ignore it); a retry clears itself before pushing so a failed retry can schedule the next one.
- **Spec 001 US5** (sign-out, T105–T107) was implemented here as the prerequisite in R-07.

## Complexity Tracking

None.
