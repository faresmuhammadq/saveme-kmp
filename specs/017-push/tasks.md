---
description: "Task list for 017-push"
---

# Tasks: Push Notifications (client)

**Input**: Design documents from `specs/017-push/`

**Prerequisites**: plan.md, spec.md, research.md (R-01…R-17), data-model.md, contracts/ (push-api-client.md, push-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `e0eef36` (specs 001–015). Spec 016 (export screens) is on its own branch: the export mapping in T047 uses it once merged (fallback `AccountKey`).

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per screen/component; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/017-push/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Host QA replays the four calls against the live local backend with `curl` once backend spec 023 is committed, and turns the real answers into fixtures, as specs 007–015 did.

**Dependency gate (owner decision pending)**: Phases 1–8 add **no new dependency** and build and test with a fake push provider. **Phase 9 "FCM wiring" is blocked on the owner's approval** of `com.google.firebase:firebase-messaging` (+ `firebase-bom`) and the `com.google.gms.google-services` plugin, and on the owner supplying a Firebase project and `google-services.json` per flavor. No Phase 9 task may start before that approval is recorded in plan.md.

**Organization**: Phase 2 builds the seam, the payload tables, the API, the stores, the Android adapters and the design-system field. US1 (registration + permission) and US2 (tap routing) are the P1 MVP and the brief's checkpoint; US3 the preferences screen; US4 display and privacy; US5 the developer tool and fake provider.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); notifications feature `cm/feature/notifications/…` (`fn/…`)
- Android shared code: `shared/src/androidMain/kotlin/com/fares/saveme/…` (`am/…`); iOS: `shared/src/iosMain/kotlin/com/fares/saveme/…` (`im/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); notifications tests `ct/feature/notifications/…` (`nt/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: `fn/ui → fn/domain → fn/data`; `fn/data` never imports `domain`/`ui`; other features only through their `domain`; `core` and `core.sync` import nothing of `notifications`; **no `com.google.firebase` import outside `androidApp/src/main/kotlin/com/fares/saveme/push/`**), naming (P-11).
- **Privacy (P-09.2)**: the push token lives only in the provider and in the request being sent; the app stores only its fingerprint. `PushMessage`, `PostedNotification`, `PushRegistrationRecord`, tap maps and `NotificationOpenKey` print the type/outcome only. Logs (`push` tag): outcome or type name only — never a token, id, title or text.
- A tap only navigates; nothing reached from a tap writes (FR-029).
- The preferences save is never queued or retried automatically (P-02.6).
- **MVI screen recipe (P-16)** as specs 001–015: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas, `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin**: anything that needs `SyncRunner`, `SignOutParticipant`s or feature services resolves them lazily (`{ get<…>() }`), spec 006 lesson; `AppGraphTest` proves the graph starts.
- **Merge hotspots** (parallel branches 016, 018): `Routes.kt`, `AppNavHost.kt`, `App.kt`, `AppModules.kt`, `Account*`, `DevTools*`, `SignOutUseCase.kt`, `PlatformModule.android.kt`, `MainActivity.kt`, `MainApplication.kt`, `AppGraphTest.kt`, `run-e2e.sh`: additive only. New strings in one append-only block `<!-- Push (spec 017) -->` at the end of each `strings.xml`.
- Every interactive element and message gets the `testTag` of contracts/ui-and-e2e.md.

---

## Phase 1: Setup

- [X] T001 Rerun `.specify/scripts/bash/check-backend-contract.sh` after backend spec 023 is committed; diff `docs/openapi/paths/notifications.yaml` against v1.14 as read here (`e8c308a` + working tree) and update spec §5 "Contract changes since last spec", contracts/push-api-client.md and contracts/push-domain.md §2–3 for any drift before coding the API (T014) or running live QA (T081)
- [X] T002 [P] Add the `<!-- Push (spec 017) -->` string block to both `strings.xml` files with every key of contracts/ui-and-e2e.md §2–§7 (EN and AR, Western digits, `%1$s`/`%1$d` placeholders), including channel names/descriptions and the public-version text
- [X] T003 [P] Declare `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` in `androidApp/src/main/AndroidManifest.xml` and add the monochrome small icon `androidApp/src/main/res/drawable/ic_stat_notification.xml` (vector, white on transparent)

---

## Phase 2: Foundational (blocking all stories)

### Tests first

- [X] T004 [P] Payload table test in `nt/domain/PushPayloadParserTest.kt`: each of the 12 types from the backend examples and a built sample per type parses to its case with typed ids; unknown type, `schema` "2", missing `type`/`notification_id` → `Unknown`; known type with a missing or non-UUID needed id → `Malformed(type)`; extra keys ignored; `category` from data or the type's default; `toString` prints the type only (contracts/push-domain.md §2)
- [X] T005 [P] Collapse-key and routing table tests in `nt/domain/PushRouterTest.kt`: every row of contracts/push-domain.md §2 (collapse) and §3 (read + target), including the `Malformed` fallbacks and `Unknown` → `Home`
- [X] T006 [P] Contract tests in `nt/data/NotificationsApiTest.kt` (MockEngine from `notifications.yaml` examples): register path/method/body exactly `{"push_token": …}` and standard headers; 200 `registered`/`rotated` decode with an unknown member ignored; 404, 422, 429 (`Retry-After` and `retry_after_seconds`), 500; unregister `DELETE` 204 and 404; GET `defaults`; PUT body from `quieterNights` (every key, no `updated_at`), 200 `saved`, 409 with `current` decoded by the same DTO, 422 with pointer `/quiet_hours/start`, 429 (contracts/push-api-client.md)
- [X] T007 [P] Store tests in `nt/data/PushRecordsStoreTest.kt` over `InMemoryKeyValueStore`: registration record round trip and clear; shown ids keep the newest 100 and reset for another user; prompt and permission flags per installation; no stored value equals the token (FR-004)
- [X] T008 [P] Fingerprint test in `nt/domain/FingerprintTest.kt`: FNV-1a 64 known vectors, hex output, different tokens differ, never contains the input
- [X] T009 [P] `TimeOfDayField` tests: `ht/designsystem/TimeOfDayFieldScreenshotTest.kt` (closed with value, open sheet scrolled to value, disabled, error × EN LTR, AR RTL, font 2.0) and `ct/designsystem/format/TimeOfDayFormatTest.kt` (`00:00`, `09:15`, `23:45`, Western digits under an Arabic locale)
- [X] T010 [P] `SignOutUseCase` test in `ct/feature/auth/domain/SignOutUseCaseTest.kt`: participants run before the server sign-out and before the local wipe; a participant that hangs is cut at 2 s (virtual time) and a throwing one does not skip the others; the session is still readable inside a participant; existing behaviour unchanged with no participants

### Implementation

- [X] T011 [P] Add `fun interface SignOutParticipant { suspend fun beforeSignOut() }` in `cm/core/session/SignOutParticipant.kt` (P-10.7) and extend `cm/feature/auth/domain/usecase/SignOutUseCase.kt` with `participants: () -> List<SignOutParticipant> = { emptyList() }` and `participantTimeout: Duration? = 2.seconds` (each participant guarded; total bound); wire `{ getAll<SignOutParticipant>() }` in the auth Koin module
- [X] T012 [P] Domain ports and values in `fn/domain/`: `PushTokenProvider`, `UnavailablePushTokenProvider`, `NotificationDisplay`, `PostedNotification`, `ChannelLabels`, `NotificationPermissionState`, `PushRegistrationScheduler`, `ReconcileTrigger`, `ReconcileOutcome`, `NotificationCategory`, `QuarterHour`, `QuietHours`, `NotificationPreferences` (data-model §2; redacted `toString`s)
- [X] T013 [P] `fn/domain/PushMessage.kt`, `PushPayloadParser.kt`, `PushRoute.kt` (`PushRead`, `PushTarget`, `PushRoute`), `PushRouter.kt`, `Fingerprint.kt` per contracts/push-domain.md §2–3 and research R-03/R-04 (makes T004, T005, T008 pass)
- [X] T014 [P] `fn/data/dto/NotificationsDtos.kt` and `fn/data/NotificationsApi.kt` (four calls, plain values, P-10.6) per contracts/push-api-client.md (makes T006 pass)
- [X] T015 [P] `fn/data/PushRegistrationStore.kt`, `ShownNotificationsStore.kt`, `PromptFlags.kt` over `KeyValueStore` per data-model §3 (makes T007 pass)
- [X] T016 `TimeOfDayField` in `cm/designsystem/component/Pickers.kt` (built on `AppSelectField` + `SelectionSheet`, 15-minute options, LTR value) and `formatTimeOfDay(minutes)` in `cm/designsystem/format/TimeOfDay.kt`; catalogue entry in the pickers section (research R-13; makes T009 pass)
- [X] T017 [P] Android adapters in `am/feature/notifications/`: `AndroidNotificationDisplay` (channels per R-08 with `VISIBILITY_PRIVATE`; `post` per R-09 with the host's small icon, tag, public version, `PendingIntent.getActivity` on the launch intent with the tap extras, `FLAG_IMMUTABLE`; skip when not allowed; `cancelAll`), `AndroidNotificationPermissionState` (R-14), `WorkManagerPushRegistrationScheduler` + `PushRegistrationWorker` (R-05, unique work `push-registration`, network constraint, backoff, `RetryAfter` → delayed re-enqueue), `PushTapExtras.from(bundle)` and `PushTapExtras.strip(intent)` (contract keys only, string values only)
- [X] T018 [P] iOS no-ops in `im/feature/notifications/`: `NoopNotificationDisplay`, permission state (never allowed), `NoopPushRegistrationScheduler`; iOS platform module binds them and `UnavailablePushTokenProvider` (FR-042)
- [X] T019 `fn/di/NotificationsModule.kt` (services, stores, API, coordinator start, `SignOutParticipant` and `UserDataCleaner` bindings, lazy `SyncRunner`) and `cm/AppModules.kt` (+ `notificationsModule`); `am/core/di/PlatformModule.android.kt` gains `notificationIcon: Int` and `pushTokenProvider: Scope.() -> PushTokenProvider` parameters and binds the Android adapters; `androidApp/.../MainApplication.kt` passes `R.drawable.ic_stat_notification` and the provider choice (dev: `DevFakePushTokenProvider` once T070 exists, otherwise `UnavailablePushTokenProvider`)
- [X] T020 [P] Robolectric tests in `ht/feature/notifications/AndroidNotificationDisplayTest.kt` (six channels: ids, names, default importance, private lock-screen visibility; second call renames without deleting; post sets channel, tag, auto-cancel, public version, PendingIntent extras exactly the contract keys; nothing posted on API 33 without permission; `cancelAll`) and `AndroidAdaptersTest.kt` (permission state on API 32 and 33; `PushTapExtras` drops non-contract and non-string extras; scheduler enqueues unique work with the network constraint; worker maps each `ReconcileOutcome`)
- [X] T021 [P] Layering test `ht/feature/notifications/NotificationsLayeringTest.kt`: `fn/data` imports no `fn/domain`/`fn/ui`; `fn/domain` imports no Ktor/Compose/Room/Android; `core` (incl. `core/sync`) imports nothing of `feature.notifications`; no other feature imports `feature.notifications.data`/`ui`; no `com.google.firebase` import anywhere in `shared/` or outside `androidApp/.../push/`
- [X] T022 `AppGraphTest` additions in `ht/core/di/AppGraphTest.kt`: the graph starts with `notificationsModule`; `PushRegistrationService`, `PushReceiver`, `PushCoordinator` resolve; one `PushSignOutParticipant` and one `PushDataCleaner` are registered; `SignOutUseCase` resolves without a cycle; with `isDebugTools = false` the provider is `UnavailablePushTokenProvider`

**Checkpoint**: the seam, tables, API, stores, adapters and graph exist; nothing is visible yet.

---

## Phase 3: User Story 1 - My phone can receive notifications (Priority: P1) 🎯 MVP

**Goal**: register after sign-in when allowed, keep it current, remove it when notifications are off or at sign-out; ask for the permission only in context.

**Independent Test**: host, fake provider + MockEngine: sign-in → one `PUT` for the stored session; new token → `PUT`; two foregrounds within 24 h → no call; permission revoked → `DELETE`; sign-out → bounded `DELETE` before the wipe, then token discarded.

### Tests first

- [X] T023 [P] [US1] `nt/domain/PushRegistrationServiceTest.kt` (fake API, provider, permission, clock, `InMemoryKeyValueStore`, `InMemorySecureStore`): every row of research R-04 — nobody signed in, no provider, not allowed with/without record, null token, first register, same token < 24 h on `Foreground` (no call) vs `SignedIn`/`TokenChanged`/`PermissionChanged` (call), ≥ 24 h, new fingerprint, other session; 404 with a changed stored session (one retry) and unchanged (stop marker, later `Foreground` skipped, cleared by a new token or session); 429 → `RetryAfter`; offline/500 → `RetryLater`; 422 → `Stopped`; the token never stored; no email-verification or plan check anywhere on the path (FR-001–FR-006, FR-009)
- [X] T024 [P] [US1] `nt/domain/PushCoordinatorTest.kt`: sign-in transition → `request(SignedIn)` once per sign-in; `onForeground` signed in/out; `onTokenChanged`; `onPermissionChanged`
- [X] T025 [P] [US1] `nt/domain/PushSignOutTest.kt`: participant sends `DELETE` only when the record belongs to the stored session, ignores any answer, finishes within 2 s when the server hangs; cleaner cancels all displayed notifications, clears both stores, cancels the scheduler and discards the token without blocking (bounded on the app scope); remote session end (`ApiEvent.SessionEnded`) runs the cleaner and never the `DELETE` (FR-007, FR-043)
- [X] T026 [P] [US1] `nt/domain/NotificationPromptGateTest.kt`: offered only with a provider, a runtime-askable permission and not offered before; `markOffered` is permanent across sign-out; not counted when suppressed (FR-011)
- [X] T027 [P] [US1] `nt/ui/prompt/NotificationPromptSheetScreenshotTest.kt` in `ht/…` (EN, AR, 2.0) and a nav-level test `ht/navigation/PushPromptMomentsTest.kt`: `BudgetEditorEffect.Saved` from a create key, `InvitationEffect.OpenWallet`, and `RuleEditorEffect.Created` each offer the sheet once; an edit save does not; another sheet showing suppresses it
- [X] T028 [P] [US1] `ct/feature/recurring/ui/editor/RuleEditorViewModelTest.kt` addition: a successful create emits `Created` then `Close`; an edit save and a cancel emit only `Close`

### Implementation

- [X] T029 [US1] `fn/domain/PushRegistrationService.kt` (`reconcile(trigger)`, research R-04) and `fn/domain/PushCoordinator.kt` (research R-06); `start()` in `NotificationsModule` (makes T023, T024 pass)
- [X] T030 [US1] `fn/domain/PushSignOutParticipant.kt` and `fn/domain/PushDataCleaner.kt` (research R-07), bound in `NotificationsModule` (makes T025 pass)
- [X] T031 [US1] Add `pushCoordinator.onForeground()` to the `LifecycleStartEffect` in `cm/App.kt`
- [X] T032 [US1] `fn/domain/NotificationPromptGate.kt` (makes T026 pass); `expect fun rememberNotificationPermissionRequester()` + `PermissionStatus` in `fn/ui/PermissionRequester.kt` with the Android actual (`rememberLauncherForActivityResult(RequestPermission)`, asked-before flag, rationale) in `am/feature/notifications/ui/PermissionRequester.android.kt` and an iOS actual; `expect fun openAppNotificationSettings()` with actuals (research R-14); the permission result calls `PushCoordinator.onPermissionChanged()`
- [X] T033 [US1] `RuleEditorEffect.Created` (additive) in `cm/feature/recurring/ui/editor/RuleEditorContract.kt` and emitted by `RuleEditorViewModel` after a successful create, before `Close` (makes T028 pass)
- [X] T034 [US1] `fn/ui/prompt/NotificationPromptSheet.kt` (design-system `BottomSheet`, texts and tags of contracts/ui-and-e2e.md §5) and the global sheet state in `cm/navigation/AppNavHost.kt`: offered from the three moments when `NotificationPromptGate.shouldOffer` and no other sheet shows; "Turn on" → requester; "Not now" → close (makes T027 pass)
- [X] T035 [US1] Write `.maestro/017-push/US1-permission-after-budget.yaml`, `US1-not-now-is-final.yaml`, `US1-registered-after-signin.yaml` (+ `common/open-notifications.yaml`), each starting `# deferred: E2E on owner's device`
- [ ] T036 [US1] Run the US1 flows — **deferred: E2E on owner's device**

**Checkpoint**: registration lifecycle and permission timing complete on the host with the fake provider.

---

## Phase 4: User Story 2 - Tapping a notification opens the right place (Priority: P1)

**Goal**: one tap path for foreground and background notifications; validated data; reads before routing; never an action.

**Independent Test**: for each of the 12 types, the tap data routes to the table's screen; a missing record waits for its scope read on "Opening…"; signed out → sign-in, tap forgotten.

### Tests first

- [X] T037 [P] [US2] `nt/domain/NotificationOpenServiceTest.kt` (recording fakes for `SyncRunner`, `SessionManager.refreshEntitlements`, `AccountRefresher`, `RecordPresence`): each `PushRead` runs exactly its read with `force = true`; present record → `Ready` at once with the read continuing; absent → waits for the read; offline/failed → `Ready`; 10 s timeout → `Ready` (virtual time); no other collaborator call (FR-029, FR-032)
- [X] T038 [P] [US2] `nt/ui/open/NotificationOpenReducerTest.kt` + `NotificationOpenViewModelTest.kt`: `Started` → waiting → `Replace(target)`; `Cancel` → `Close` and no later `Replace`
- [X] T039 [P] [US2] `nt/ui/invitation/InvitationNoticeViewModelTest.kt` + reducer test: shows the signed-in address; `OK` → close; no call made
- [X] T040 [P] [US2] `ht/navigation/PushTapRoutingTest.kt`: `PushTapBus` replay is consumed once; signed out → ignored; signed in: `None` read → target key pushed, otherwise `NotificationOpenKey` pushed and a second tap replaces it; update-required on top → dropped; the `PushTarget` → `NavKey` mapping for every target (`OccurrenceKey(id, confirm = false)`, `RecurringKey(null)`, `BudgetEditorKey(id)`, `BudgetsKey`, `InvitationNoticeKey`, `WalletDetailKey(id)`, `WalletsKey`, `PlanKey`, export keys or `AccountKey`, `DeleteAccountKey`, `HomeKey`)
- [X] T041 [P] [US2] `ht/feature/notifications/MainActivityTapIntakeTest.kt` (Robolectric, `PushTapExtras`): a background-style launch intent with FCM extras (`google.message_id`, contract keys) submits one tap and strips the keys; a launcher intent without `type`/`notification_id` submits nothing; a restored activity does not resubmit
- [X] T042 [P] [US2] Screenshot tests `ht/feature/notifications/NotificationOpenScreenshotTest.kt` and `InvitationNoticeScreenshotTest.kt` (EN, AR, 2.0)

### Implementation

- [X] T043 [US2] `fn/domain/RecordPresence.kt` with the implementation delegating to `WalletsService`, `RecurringService` and `BudgetsService` lookups (research R-11; add a one-line `exists(id)` to a service only if no lookup exists, with its own test in that feature)
- [X] T044 [US2] `fn/domain/NotificationOpenService.kt` (research R-10; makes T037 pass)
- [X] T045 [US2] `fn/ui/open/{NotificationOpenContract,NotificationOpenViewModel,NotificationOpenRoute,NotificationOpenScreen}.kt` (makes T038 pass)
- [X] T046 [US2] `fn/ui/invitation/{InvitationNoticeContract,…ViewModel,…Route,…Screen}.kt` (own address from `SessionManager`; makes T039 pass)
- [X] T047 [US2] `cm/navigation/PushTapBus.kt` (as `DeepLinkBus`), keys `NotificationOpenKey`, `InvitationNoticeKey` in `Routes.kt` (redacted `toString`), and in `AppNavHost.kt`: tap collection after the auth transition, the `PushTarget` → key mapping, `NotificationOpenKey` replace-on-ready, entries for the two screens (makes T040 pass)
- [X] T048 [US2] `androidApp/src/main/kotlin/com/fares/saveme/MainActivity.kt`: forward `PushTapExtras.from(intent.extras)` to `PushTapBus` in `onCreate` (no saved state) and `onNewIntent`, then strip (makes T041 pass)
- [X] T049 [US2] Write `.maestro/017-push/US2-route-<type>.yaml` × 12, `US2-route-unknown.yaml`, `US2-signed-out-tap.yaml` (+ `common/simulate-push.yaml`), each `# deferred: E2E on owner's device`
- [ ] T050 [US2] Run the US2 flows — **deferred: E2E on owner's device**

**Checkpoint**: P1 complete — registration and routing; the brief's checkpoint ("a notification that opens the invitation") is the invitation notice.

---

## Phase 5: User Story 3 - Choose what I hear about, and when not (Priority: P2)

**Goal**: one screen, one save, version and conflict handling, online-only.

**Independent Test**: MockEngine: open → defaults; edit two things → one `PUT` with all members and `version: 1`; 409 → `current` shown with the conflict message.

### Tests first

- [X] T051 [P] [US3] `nt/domain/NotificationPreferencesServiceTest.kt`: load outcomes (200, offline, failure with request id); save outcomes (200, 409 with `current`, 409 with unreadable `current` → `Failed`, 429 with and without wait, 422, offline); one request per call, never retried; DTO ↔ domain mapping incl. `HH:MM` ↔ `QuarterHour`
- [X] T052 [P] [US3] `nt/ui/settings/NotificationSettingsReducerTest.kt` + `NotificationSettingsViewModelTest.kt`: loading → ready; dirty/valid/canSave; start == end blocks Save with the message; save success → read = edited, "Saved" effect; conflict → edited and read replaced, message; rate-limit minutes rounding ("a minute" under 60 s, "later" without wait); offline keeps edits; Back with edits → discard dialog; phone status Askable/Blocked → card and effects (`RequestPermission`, `OpenPhoneSettings`); time-zone note present only when the profile is known
- [X] T053 [P] [US3] Screenshot tests `ht/feature/notifications/NotificationSettingsScreenshotTest.kt` for every state of contracts/ui-and-e2e.md §9 (EN, AR, 2.0)
- [X] T054 [P] [US3] `ct/feature/account/ui/AccountViewModelTest.kt` addition: the Notifications row shows only with an available provider and navigates to `NotificationSettingsKey`

### Implementation

- [X] T055 [US3] `fn/domain/NotificationPreferencesService.kt` (research R-12; profile time zone via the account domain) (makes T051 pass)
- [X] T056 [US3] `fn/ui/settings/{NotificationSettingsContract,…ViewModel,…Route,…Screen}.kt` with `SettingsSwitchRow` × 6, quiet-hours switch, two `TimeOfDayField`s, notes, phone card (requester + `openAppNotificationSettings`), Save, discard dialog, `fn/ui/NotificationsErrorCopy.kt`; switches announced with title and state, messages as live regions, 48 dp targets (FR-040) (makes T052, T053 pass); the Route re-reads the permission status on resume and calls `PushCoordinator.onPermissionChanged()` when it changed
- [X] T057 [US3] `AccountDestination.Notifications` row in `cm/feature/account/ui/account/{AccountContract,AccountViewModel,AccountScreen}.kt` (shown when `PushTokenProvider.isAvailable`), `NotificationSettingsKey` in `Routes.kt` and its entry in `AppNavHost.kt` (makes T054 pass)
- [X] T058 [US3] Write `.maestro/017-push/US3-edit-and-save.yaml`, `US3-conflict.yaml`, `US3-offline.yaml` and `api.js` `notificationPreferences`/`saveNotificationPreferences`, each flow `# deferred: E2E on owner's device`
- [ ] T059 [US3] Run the US3 flows — **deferred: E2E on owner's device**

---

## Phase 6: User Story 4 - Notifications stay private and tidy (Priority: P2)

**Goal**: channels in the app language, foreground display identical to background, de-duplication, background read, clearing at sign-out.

**Independent Test**: fake display: two same-subject messages → one tag; same id twice → one post; signed out → dropped; sign-out → cancel-all.

### Tests first

- [X] T060 [P] [US4] `nt/domain/PushReceiverTest.kt` (fake display, fake sync runner, `InMemoryKeyValueStore`): signed out → dropped; blank title/body → dropped; known type → posted on its category channel with the contract collapse key as tag and the tap map = contract keys only, then one forced background read of the route's scope (none for `Entitlements`/`Account`/`None`); unknown type with text → posted with `other:{id}` tag; same `notification_id` twice → one post; channels ensured before posting; `onNewToken` → coordinator (FR-023–FR-027)
- [X] T061 [P] [US4] `ht/feature/notifications/ChannelLabelsTest.kt`: labels resolved in English and Arabic from resources; the `App()` channels effect re-runs on a language change (Robolectric + compose test)
- [X] T062 [P] [US4] Log-capture test `ht/feature/notifications/PushLogCaptureTest.kt`: register, rotate, receive each type, tap, save preferences, sign out — no token, id, title or text in any Kermit line (SC-005)

### Implementation

- [X] T063 [US4] `fn/domain/PushReceiver.kt` (research R-09; makes T060 pass)
- [X] T064 [US4] `fn/ui/ChannelLabelsResolver.kt` (strings → `ChannelLabels`) and a `LaunchedEffect(effectiveLanguage)` in `cm/App.kt` calling `NotificationDisplay.ensureChannels` (makes T061 pass)
- [X] T065 [US4] Write `.maestro/017-push/US4-collapse.yaml`, `US4-cleared-on-signout.yaml`, each `# deferred: E2E on owner's device`
- [ ] T066 [US4] Run the US4 flows and the lock-screen spot check (quickstart §3.5) — **deferred: E2E on owner's device**

---

## Phase 7: User Story 5 - Developers can exercise push without Firebase (Priority: P3)

**Goal**: fake provider in dev builds; "Simulate a notification" through the real receiving path; Developer tools reachable while signed in.

**Independent Test**: store tests for every type; graph test that staging/prod have neither the tool nor the fake.

### Tests first

- [X] T067 [P] [US5] `ct/devtools/push/DevFakePushTokenProviderTest.kt`: stable token per installation in the backend's format (printable ASCII, ≤ 4,096), `discard` forgets it, `rotate` creates a new one and calls `PushReceiver.onNewToken`
- [X] T068 [P] [US5] `ct/devtools/push/SimulatePushViewModelTest.kt` + reducer test: 13 rows; ids from the phone's first wallet/occurrence/budget when present, random UUIDs labelled "not on this phone" otherwise; `Post` hands exactly the contract data map and the dev generic text to `PushReceiver.onMessage`; `Rotate` calls the provider
- [X] T069 [P] [US5] `ht/core/di/AppGraphTest.kt` addition and `ht/devtools/SimulatePushScreenshotTest.kt`: with `isDebugTools = true` the provider is `DevFakePushTokenProvider` and the Account Developer tools row shows; with `false` neither (FR-036)

### Implementation

- [X] T070 [US5] `cm/devtools/push/DevFakePushTokenProvider.kt` (research R-15) and its binding for `isDebugTools` builds via `MainApplication`'s provider choice (T019) (makes T067 pass)
- [X] T071 [US5] `cm/devtools/push/{SimulatePushContract,…ViewModel,…Route,…Screen}.kt`, `SimulatePushKey` + entry, "Simulate a notification" item in `cm/devtools/{DevToolsContract,DevToolsViewModel,DevToolsScreen}.kt`, and `AccountDestination.DevTools` row (dev builds) opening `DevToolsKey` on the app stack (makes T068, T069 pass)
- [ ] T072 [US5] Write `.maestro/017-push/US5-tool-absent-in-prod.yaml` (documentation-only) — **deferred: E2E on owner's device**

---

## Phase 8: Polish & Cross-Cutting

- [X] T073 [P] String-scan test `ht/feature/notifications/PushStringsTest.kt`: no "token", "FCM", "Firebase", "InstaPay" in the spec 017 block (EN/AR); every key has both languages; digits Western
- [X] T074 [P] Invariant sweep: confirm each row of spec §4 has its named test (T004–T069); add any missing one
- [X] T075 [P] README "On-device database": one sentence that spec 017 adds no table (Room stays v10) and keeps four small push records in the key-value store; "Running tests": the fake provider note
- [X] T076 [P] `.specify/scripts/bash/run-e2e.sh`: no new seed needed beyond a signed-in account; document in contracts/ui-and-e2e.md §10 that the conflict flow saves through `api.js` from a second session
- [X] T077 Run `./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug` and the iOS simulator compile; record goldens added/changed (Account screen rows, catalogue pickers section)
- [X] T078 Confirm no Firebase class on any `:shared` or `:androidApp` classpath (`./gradlew :androidApp:dependencies --configuration devDebugRuntimeClasspath | grep -i firebase` is empty) (SC-009)
- [X] T079 Review quickstart §1–3 against the built code; fix drift
- [ ] T080 Wait for backend spec 023 to be committed; rerun T001 — QA step (left for the QA agent: needs backend spec 023 committed and the live local backend)
- [ ] T081 Live contract QA per contracts/push-api-client.md "Live QA" (curl, throwaway `qa017-*` accounts, local backend `127.0.0.1:43871`), capturing answers (tokens replaced by same-shape fakes) into `nt/data/CapturedNotificationsResponses.kt` — QA step (left for the QA agent: needs backend spec 023 committed and the live local backend)
- [ ] T082 `nt/data/NotificationsLiveReplayTest.kt`: replay the captured answers through the real `ApiClient`, `NotificationsApi`, `PushRegistrationService` and `NotificationPreferencesService` — QA step (left for the QA agent: needs backend spec 023 committed and the live local backend)
- [X] T083 Full gate `./gradlew build allTests --continue`; note pre-existing failures separately from this spec's
- [ ] T084 Write specs/017-push/summary.md (parent writes it)

---

## Phase 9: FCM wiring — **blocked on owner approval** (do not start until approval is recorded in plan.md)

**Requires from the owner**: approval of `com.google.firebase:firebase-messaging` and `com.google.firebase:firebase-bom` and the Gradle plugin `com.google.gms.google-services` (P-14.5); a Firebase project with an Android app for `com.fares.saveme`; `google-services.json` for each flavor; the backend's service-account key (backend spec 023 open question 1).

- [ ] T085 [GATED] Record the owner's approval (date, artifacts, versions) in plan.md "Complexity Tracking" and the constitution check P-14 row
- [ ] T086 [GATED] Add `firebase-bom`, `firebase-messaging` and the `google-services` plugin to `gradle/libs.versions.toml`; apply the plugin in `androidApp/build.gradle.kts` (root `build.gradle.kts` `apply false`); `implementation(platform(libs.firebase.bom))` + `implementation(libs.firebase.messaging)` in `:androidApp` only (rebase after branch 018, which may edit the same files)
- [ ] T087 [GATED] Place the owner's `google-services.json` in `androidApp/src/dev/`, `androidApp/src/staging/`, `androidApp/src/prod/` (create the staging/prod source sets); confirm each builds (`assembleDevDebug`, `assembleStagingRelease`, `assembleProdRelease`); add a `.gitignore` decision (commit or keep local) per the owner
- [ ] T088 [GATED] `androidApp/src/main/kotlin/com/fares/saveme/push/FcmPushTokenProvider.kt` (`token`, `deleteToken` as suspend; `isAvailable` = Google Play services present) and `AppFirebaseMessagingService.kt` (`onNewToken` → `PushReceiver.onNewToken()`; `onMessageReceived` → `PushReceiver.onMessage(data, notification?.title, notification?.body)`), research R-02
- [ ] T089 [GATED] Manifest: the service (`exported="false"`, `com.google.firebase.MESSAGING_EVENT`), meta-data `default_notification_icon` = `@drawable/ic_stat_notification`, `firebase_analytics_collection_deactivated` = true; `MainApplication` binds `FcmPushTokenProvider` for every flavor (dev keeps the simulate tool)
- [ ] T090 [GATED] Robolectric test `androidApp` or `ht`: the service forwards data/title/body unchanged to `PushReceiver` and a new token triggers `TokenChanged`; layering test still passes (Firebase only under `androidApp/.../push/`)
- [ ] T091 [GATED] Owner device check (quickstart §4): real `invitation_received` with the app closed → tap → invitation notice; a background tap of each type routes as US2; token rotation; sign-out stops delivery — **deferred: E2E on owner's device**

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 → US1 (Phase 3) and US2 (Phase 4) → US3 (Phase 5), US4 (Phase 6), US5 (Phase 7) → Phase 8. Phase 9 only after owner approval, last (rebases on 018's build-file edits).
- T001 must pass before T014 and again (T080) before T081.
- T019's dev provider choice switches to `DevFakePushTokenProvider` once T070 lands.
- US2's export mapping (T047) uses spec 016's `ExportDetailKey`/`ExportsKey` when 016 is merged; otherwise it maps both export targets to `AccountKey` and leaves a follow-up note.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T004–T010 all [P]; implementation T011–T015, T017, T018 in parallel; T016 (design system) alone; T019 after T012–T018; T020–T022 after T019.
- US1 tests T023–T028 [P]; US2 tests T037–T042 [P]; US3 T051–T054 [P]; US4 T060–T062 [P]; US5 T067–T069 [P].
- US3, US4 and US5 can proceed in parallel after US1/US2 (different packages), except shared edits to `AppNavHost.kt`, `Routes.kt` and the Account files — coordinate those.

## Parallel Example: Phase 2

```text
T004 parser | T005 router | T006 contract | T007 stores | T008 fingerprint | T009 TimeOfDayField | T010 sign-out
then: T011 | T012 | T013 | T014 | T015 | T017 | T018   and   T016 (design system)
then: T019 → T020 | T021 | T022
```

## Implementation Strategy

### MVP First (User Stories 1 and 2)

1. Phase 1 → Phase 2 → Phase 3 → Phase 4.
2. Stop and validate: on the host with the fake provider, sign-in registers, sign-out unregisters and clears, and every type routes; the dev build can simulate a message (after T070/T071 if pulled forward).

### Incremental Delivery

1. US1 + US2 (brief checkpoint via the invitation notice) → US3 (preferences) → US4 (display/privacy) → US5 (developer tool).
2. Polish: string scan, logs, README, gates, live QA with captured fixtures, summary; device E2E deferred to the owner.
3. Phase 9 when the owner approves the dependency and supplies the Firebase configuration; then the owner-device check of real delivery.
