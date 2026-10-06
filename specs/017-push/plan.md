# Implementation Plan: Push Notifications (client)

**Branch**: `017-push` | **Date**: 2026-10-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-push/spec.md`

## Summary

A new `feature.notifications` context registers this phone's push token for the current device
session (`PUT /me/devices/{session id}`, `{push_token}` only) after sign-in, on a new token, when
notifications become allowed and at most once a day on foreground, through one idempotent
WorkManager "reconcile" job; removes it (`DELETE`) when notifications are off on the phone and,
bounded to 2 s, before the session is wiped at sign-out, then discards the token and clears the
shade. Six Android channels (ids = categories, private on the lock screen) are created in the app
language; foreground messages are posted through `NotificationManager` with the received text and
the contract's collapse key as tag, de-duplicated by `notification_id`, and trigger a background
read. Taps (background FCM extras on the launcher activity, or the app's own `PendingIntent`) go
through a `PushTapBus` into a pure parser and routing table: the target screen opens at once when
its record is on the phone, otherwise after a bounded read on an "Opening…" screen; a tap never
acts. The Notifications screen (Account → Notifications) reads and saves the preferences online
with one Save, version and `409 current` handling, quarter-hour times through a new design-system
`TimeOfDayField`. The permission is asked only from that screen or from a once-per-installation
explanation sheet after the first recurring rule, budget or join. Firebase is isolated behind
`PushTokenProvider` + `PushReceiver`: development builds use a fake provider and a "Simulate a
notification" developer tool; staging and production bind no provider (no push UI) until the
**gated Phase 9** adds `firebase-messaging`, the Google Services plugin and `google-services.json`
per flavor in `:androidApp` only, after owner approval. No Room change.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, MVI base, Ktor 3.6.0,
kotlinx.serialization, coroutines, Koin 4.2.2, Navigation 3, Kermit, WorkManager 2.12.0,
DataStore). Android `NotificationManager`/`NotificationChannel` (framework) and `NotificationCompat`
from `androidx.core` (already on the classpath transitively; `libs.androidx.core.ktx` is already in
the catalog if a direct declaration is needed — no new artifact). **No new dependency before Phase 9.**
Phase 9 (gated): `com.google.firebase:firebase-messaging` (+ `firebase-bom`) and plugin
`com.google.gms.google-services`, `:androidApp` only, **requires owner approval** (P-14.5).

**Storage**: none new in Room (v10 unchanged). Key-value records: `push_registration`,
`push_shown_ids`, `push_prompt_offered`, `push_permission_asked`, `dev_fake_push_token`
(data-model §3). The token stays with the provider.

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor `MockEngine` (contract tests from
`notifications.yaml` examples), fakes in `commonTest`; Robolectric (channels, posting, permission
state on API 32/33, `PushTapExtras`, WorkManager scheduler/worker), `AppGraphTest`, layering,
log capture and Roborazzi in `androidHostTest`; captured live answers
(`CapturedNotificationsResponses.kt`) after backend spec 023 is committed. No emulator, Maestro or
device run (owner policy); Maestro flows written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37 (runtime permission on 33+, channels on 26+);
iOS compiles with no-op actuals.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: a tap on a record already on the phone opens its screen in under 1 s
(SC-003); a tap needing a read waits at most 10 s; foreground reconcile is a no-op in under 1 ms
when nothing changed; sign-out grows by at most 2 s in the worst case (SC-008).

**Constraints**: P-09.2 (token, payload values, ids and texts never logged or stored; only a
fingerprint), P-02.2/02.4/02.6 (exact bodies, code branching, no automatic retry of the preferences
save), P-10.6/10.7 (data never imports domain; core and the sync core never import the feature),
FR-044 (launcher extras untrusted), P-14.5 (Firebase gated).

**Scale/Scope**: 4 API calls + 6 DTOs; 1 parser, 1 router, 6 domain services, 5 ports; 3 MVI
screens + 1 global sheet + 1 dev MVI screen; 1 design-system component; 1 core interface
(`SignOutParticipant`) and a `SignOutUseCase` change; Android adapters (display/channels,
permission, scheduler + worker, settings intent, tap extras); `MainActivity` intake; ~60 strings
EN/AR; 2 manifest lines (`POST_NOTIFICATIONS`, nothing else before Phase 9); ~25 Maestro flows
(deferred).

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails on the spec alone. The one risk is P-14.5 (new
dependency): the spec isolates Firebase and gates it on owner approval (FR-045, FR-046), so the
plan can pass without it. Push for recurring occurrences is in scope (the scope guard excludes
"bill reminders beyond recurring occurrences" only). No iOS-specific work.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Context `notifications` (backend context); `PushRegistration…`, `NotificationPreferences`, `PushMessage`, `DeviceSession` ids; UI never says "token", "FCM", "Firebase" (string-scan test, contracts/ui-and-e2e.md); category names fixed EN/AR |
| P-02 API contract fidelity | PASS | contracts/push-api-client.md: register body exactly `{push_token}`, preferences write without `updated_at`, unknown answer members ignored, outcomes by `code`, standard headers; registration retries only `RATE_LIMITED` (honouring `Retry-After`) and transient failures; preferences save never retried; contract tests per call and code |
| P-03 Money | N-A | No amount is received, stored or shown; notification text never composed from records (FR-028) |
| P-04 Offline-first | PASS | Rule 6: registration and preferences are account data (online-only, nothing syncable written); tap reads use the existing sync runner and screens render from Room; rule 4 spirit: `409` shows `current`, never overwrites |
| P-05 Time | PASS | `registered_at` and the 24 h window as `Instant` with an injected clock; quiet hours are wall-clock quarter hours shown with the profile time zone named, never converted on the phone |
| P-06 Localisation & RTL | PASS | ~60 strings EN/AR incl. channel names; Western digits, times in LTR runs; received text shown as is (server-localised); no server `detail`; screenshots EN/AR/2.0 |
| P-07 Design system only | PASS | `TimeOfDayField` added to `designsystem` first with catalogue entry and screenshots (R-13); screens use `AppScreen`, `SettingsSwitchRow`, `InlineAlert`, cards, `AppAlertDialog`, `BottomSheet` |
| P-08 Accessibility | PASS | 48 dp targets; switches announced with title and state; messages as live regions; 200 % screenshots; nothing by colour alone |
| P-09 Security & privacy | PASS | Rule 2: token never stored or logged (fingerprint only, R-04), payload/text redacted `toString`, log-capture test; lock screen private + public version (R-08, R-09); notifications and records cleared at sign-out and session end (R-07); launcher extras validated, navigation only (R-10); rule 5: 401 → existing session end |
| P-10 Shared-first architecture | PASS | All logic in `commonMain`; Android adapters behind interfaces / expect-actual (rule 2); `ui → domain → data`; `data` imports no domain (rule 6); cross-feature only through domain (`SessionManager`, `AccountRefresher`, wallets/recurring/budgets lookups, R-11); `core` gains only `SignOutParticipant` and imports no feature (rule 7); Firebase only in `:androidApp` (layering test) |
| P-11 Naming | PASS | No codename in identifiers; `notifications`, `PushTokenProvider`, `TimeOfDayField` |
| P-12 Entitlements & billing | PASS | No gating on plan (registration and preferences are open to all); `subscription_*` taps only refresh entitlements and open Plan; no billing action added; "InstaPay" never in strings |
| P-13 Observability | PASS | Fresh correlation id per call (existing); unexpected preferences failures show the request id; logs carry outcome/type only (R-16) |
| P-14 Quality gates | PASS | Every spec §4 invariant has a test (tasks); contract tests for all four calls and handled codes; screenshot tests for `TimeOfDayField` and every new screen; **rule 5**: no new dependency in Phases 1–8; Phase 9 adds `firebase-messaging`, `firebase-bom` and the `google-services` plugin **only after the owner's recorded approval** (Complexity Tracking row 1) |
| P-15 Scope guard | PASS | Push for recurring occurrences, budgets, sharing, billing, exports and deletion — all backed by existing features; no iOS work (no-op actuals); no new capability from the scope list |
| P-16 MVI presentation | PASS | `NotificationSettings`, `NotificationOpen`, `InvitationNotice`, `SimulatePush` each with Contract/ViewModel/Route/Screen; reducer and store tests; the global sheet's state lives in the nav host like `EmailNotVerifiedSheet` (no business state) |

No FAIL → plan not blocked. Complexity Tracking records the gated dependency.

## Project Structure

### Documentation (this feature)

```text
specs/017-push/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0 (R-01…R-17)
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── push-api-client.md
│   ├── push-domain.md
│   └── ui-and-e2e.md
├── checklists/
│   ├── requirements.md
│   └── push.md          # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/src/commonMain/kotlin/com/fares/saveme/
├── core/session/SignOutParticipant.kt                       # new fun interface
├── feature/auth/domain/usecase/SignOutUseCase.kt            # + participants before server sign-out
├── designsystem/component/Pickers.kt                        # + TimeOfDayField
├── designsystem/format/TimeOfDay.kt                         # formatTimeOfDay(minutes)
├── designsystem/catalog/…                                   # + TimeOfDayField entry
├── feature/notifications/
│   ├── data/{NotificationsApi,PushRegistrationStore,ShownNotificationsStore,PromptFlags}.kt
│   ├── data/dto/NotificationsDtos.kt
│   ├── domain/{PushTokenProvider,NotificationDisplay,NotificationPermissionState,PushRegistrationScheduler}.kt
│   ├── domain/{PushMessage,PushPayloadParser,PushRoute,PushRouter,ChannelLabels}.kt
│   ├── domain/{PushRegistrationService,PushCoordinator,PushSignOutParticipant,PushDataCleaner}.kt
│   ├── domain/{PushReceiver,NotificationOpenService,RecordPresence,NotificationPreferencesService,NotificationPromptGate}.kt
│   ├── domain/{UnavailablePushTokenProvider,Fingerprint}.kt
│   ├── ui/settings/{NotificationSettingsContract,…ViewModel,…Route,…Screen}.kt
│   ├── ui/open/{NotificationOpenContract,…ViewModel,…Route,…Screen}.kt
│   ├── ui/invitation/{InvitationNoticeContract,…ViewModel,…Route,…Screen}.kt
│   ├── ui/prompt/NotificationPromptSheet.kt
│   ├── ui/{NotificationsErrorCopy,ChannelLabelsResolver,PermissionRequester}.kt   # expect rememberNotificationPermissionRequester, openAppNotificationSettings
│   └── di/NotificationsModule.kt
├── devtools/push/{DevFakePushTokenProvider,SimulatePushContract,…ViewModel,…Route,…Screen}.kt
├── devtools/{DevToolsContract,DevToolsScreen,DevToolsViewModel}.kt   # + Simulate item
├── feature/account/ui/account/{AccountContract,AccountScreen,AccountViewModel}.kt   # + Notifications, DevTools rows
├── feature/recurring/ui/editor/{RuleEditorContract,RuleEditorViewModel}.kt   # + RuleEditorEffect.Created (additive)
├── navigation/{Routes,AppNavHost,PushTapBus}.kt             # keys, tap intake, Opening replace, sheet, moments
├── App.kt                                                   # + pushCoordinator.onForeground(), channels effect
└── AppModules.kt                                            # + notificationsModule
shared/src/commonMain/composeResources/values{,-ar}/strings.xml   # block <!-- Push (spec 017) -->
shared/src/androidMain/kotlin/com/fares/saveme/
├── feature/notifications/{AndroidNotificationDisplay,AndroidNotificationPermissionState,WorkManagerPushRegistrationScheduler,PushRegistrationWorker,PushTapExtras}.kt
├── feature/notifications/ui/{PermissionRequester.android,AppNotificationSettings.android}.kt
└── core/di/PlatformModule.android.kt                        # + display, permission, scheduler; provider parameter
shared/src/iosMain/kotlin/com/fares/saveme/feature/notifications/…  # no-op actuals and bindings
androidApp/src/main/AndroidManifest.xml                      # + POST_NOTIFICATIONS
androidApp/src/main/res/drawable/ic_stat_notification.xml    # monochrome small icon
androidApp/src/main/kotlin/com/fares/saveme/{MainActivity,MainApplication}.kt   # tap intake; provider per flavor
# Phase 9 (gated):
androidApp/build.gradle.kts, build.gradle.kts, gradle/libs.versions.toml     # firebase-messaging, bom, google-services plugin
androidApp/src/{dev,staging,prod}/google-services.json                       # supplied by the owner
androidApp/src/main/kotlin/com/fares/saveme/push/{FcmPushTokenProvider,AppFirebaseMessagingService}.kt
shared/src/commonTest/kotlin/com/fares/saveme/feature/notifications/…        # tables, services, stores, contract, live replay
shared/src/androidHostTest/kotlin/com/fares/saveme/feature/notifications/…   # Robolectric, screenshots, layering, log capture
.maestro/017-push/*.yaml                                                    # deferred
```

**Structure Decision**: a new feature context (R-01) with Android adapters in `:shared/androidMain`
and the Firebase adapter in `:androidApp` only. Merge hotspots, append-only: `Routes.kt`,
`AppNavHost.kt`, `App.kt`, `AppModules.kt`, `AccountContract/Screen/ViewModel`, `DevTools*`,
`SignOutUseCase.kt`, `PlatformModule.android.kt`, `MainActivity.kt`, `MainApplication.kt`,
both `strings.xml`, `AppGraphTest.kt`. The parallel branches 016 (export/deletion: Account,
Routes, AppNavHost, strings) and 018 (Google Play billing: build files, Plan) touch some of the
same files; 018 may also edit `gradle/libs.versions.toml` and `androidApp/build.gradle.kts`, which
Phase 9 edits — rebase Phase 9 last.

## Phases (for tasks)

1. Setup — contract re-check, string block, manifest permission, icon.
2. Foundational — core `SignOutParticipant`, ports, parser/router, data API and stores, Koin module, Android adapters, iOS no-ops, design-system `TimeOfDayField`, layering/graph tests.
3. US1 — registration lifecycle, permission flow, explanation sheet, sign-out.
4. US2 — tap intake, "Opening…" screen, invitation notice, routing.
5. US3 — Notifications screen.
6. US4 — channels, foreground display, de-dup, clear on sign-out.
7. US5 — fake provider, developer tool, Account → Developer tools.
8. Polish — log capture, string scan, E2E flows (deferred), live QA + captured fixtures, gates, summary.
9. **FCM wiring — blocked on owner approval** (dependency, plugin, `google-services.json` per flavor, adapter, service, manifest) and the owner-device checks.

## Complexity Tracking

| Violation | Why needed | Simpler alternative rejected because |
|---|---|---|
| P-14.5 new dependency (`firebase-messaging`, `firebase-bom`, `com.google.gms.google-services` plugin) — **Phase 9 only, not taken until the owner approves** | Android receives FCM push only through Google Play services' messaging client; the backend already sends through FCM HTTP v1 | A self-built receiver is not possible (FCM delivery is a Play services component); a third-party push SDK is also a new dependency and a new data processor; polling is not push and drains the battery |
