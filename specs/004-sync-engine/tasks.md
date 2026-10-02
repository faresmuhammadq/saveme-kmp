---
description: "Task list for 004-sync-engine"
---

# Tasks: Sync Engine (Offline Outbox)

**Input**: Design documents from `specs/004-sync-engine/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (sync-engine.md, ui.md), quickstart.md; constitution v1.3.0

**Tests**: Included. P-14 requires a test per invariant (spec §4), and P-16 requires reducer + ViewModel tests for changed screens. Write each story's tests first and confirm they fail.

**Organization**:
- Phase 2 builds the queue, engine skeleton, scheduler and wiring.
- US1 (never lost) is the MVP: the engine sends, retries and survives process death, tested with a fake handler.
- US2 adds visible status.
- US3 adds clashes and moves the profile onto the engine (FR-019).
- US4 adds refusals; US5 adds the sign-out warning.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`)
- Android code: `shared/src/androidMain/kotlin/com/fares/saveme/…` (`am/…`)
- iOS code: `shared/src/iosMain/kotlin/com/fares/saveme/…` (`im/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`)
- Host tests (Robolectric, Room, WorkManager, screenshots): `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Spec 001/002 standing rules apply:
  - P-06: strings in both files
  - P-07: design-system components only
  - P-10: layering; `core.sync` never imports a feature
  - P-11: naming
- The engine never logs payloads; only record type, id, state and request id (P-09.2, P-13).
- Classification is by problem `code` and retry class only (P-02.4), exactly as the table in contracts/sync-engine.md.
- Profile behaviour must stay as spec 002 defines it (FR-019). Its existing tests are the guard; don't weaken them.

---

## Phase 1: Setup

- [X] T001 Add WorkManager to `gradle/libs.versions.toml`: `work = "2.12.0"`, libraries `androidx-work-runtime-ktx` and `androidx-work-testing`. In `shared/build.gradle.kts` add `androidx-work-runtime-ktx` to `androidMain` dependencies, and `androidx-work-testing` plus `androidx.room:room-testing` (same `room` version) to the Android host-test dependencies
- [X] T002 Add `PendingChangeEntity` in `cm/core/sync/PendingChangeEntity.kt` with exactly the columns of data-model §1:
  - `record_type` and `record_id` as the composite primary key
  - `account_id`, `scope_kind` ("user"|"wallet"), `scope_id`
  - `base_version` ("0 = never sent (create)"), `payload_json`, `is_delete`, `restore`, `revision`, `created_order`
  - `state` ("Waiting · Sending · Clash · Refused")
  - `attempts`, `next_attempt_at`, `clash_json`, `refusal_json`, `updated_at`
  - UTC instants stored as ISO strings
- [X] T003 Schema v2 in `cm/core/database/AppDatabase.kt`:
  - bump `version = 2`
  - add `PendingChangeEntity` and `abstract fun pendingChangeDao()`
  - remove `pending_json`, `pending_base_version`, `conflict_json`, `rejection_json` from `cm/feature/account/data/local/ProfileEntity.kt` along with their DAO queries (`setPending`, `setConflict`, `setRejection`, `clearPending`, `rebasePending`); callers are fixed in T030
  - add `@ColumnInfo(name = "last_pushed_at") val lastPushedAt: String? = null` to `cm/core/database/SyncScopeEntity.kt`
- [X] T004 `Migration(1, 2)` in `cm/core/database/Migrations.kt`, registered in `buildAppDatabase` (research R-09):
  1. `CREATE TABLE pending_change (…)` matching T002
  2. `INSERT INTO pending_change SELECT` from `profile` rows with `pending_json IS NOT NULL`: `record_type='profile'`, `record_id=id`, `account_id=user_id`, `scope_kind='user'`, `scope_id=user_id`, `base_version=pending_base_version`, `payload_json=pending_json`, `state` = `Clash` if `conflict_json` else `Refused` if `rejection_json` else `Waiting`, `clash_json=conflict_json`, `refusal_json` = `{"code":"VALIDATION_FAILED","fields":<rejection_json>}`
  3. rebuild `profile` without the four columns (create `profile_new`, copy, drop, rename)
  4. `ALTER TABLE sync_scope ADD COLUMN last_pushed_at TEXT`

  Export the v2 schema to `shared/schemas/com.fares.saveme.core.database.AppDatabase/2.json`

---

## Phase 2: Foundational (blocking)

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T005 [P] DAO tests in `ht/core/database/PendingChangeDaoTest.kt` (Robolectric, in-memory, as `AccountDaoTest`):
  - upsert and get by `(type, id)`
  - `due(accountId, now)` returns only `Waiting` rows of that account with `next_attempt_at` null or ≤ now, ordered by `created_order`
  - `countsByState`
  - `resetSending()` moves `Sending` → `Waiting`
  - `deleteAll()`
- [X] T006 [P] Migration test in `ht/core/database/MigrationTest.kt` with `MigrationTestHelper` and schema `1.json`:
  - a v1 profile with `pending_json` + `pending_base_version = 3` becomes a `pending_change` row (`Waiting`, base 3, same payload)
  - with `conflict_json` set it becomes `Clash` with `clash_json`
  - the `profile` row keeps its server columns
  - `sync_scope` gains `last_pushed_at`

### Implementation

- [X] T007 `PendingChangeDao` in `cm/core/sync/PendingChangeDao.kt` with the queries T005 needs:
  - `upsert`, `get(type, id)`, `observe(type, id): Flow`, `due(accountId, now)`
  - `observeCounts(): Flow` (waiting; needsDecision = Clash + Refused)
  - `resetSending()`, `delete(type, id)`, `deleteAll()`
- [X] T008 [P] Domain types in `cm/core/sync/`:
  - `PendingChange` (immutable mirror of the row) and `ChangeState`, in `PendingChange.kt`
  - `SyncStatus(waiting, needsDecision, lastSyncedAt)` and `Refusal(code, fields)`, in `SyncStatus.kt`
  - mappers entity ↔ domain
- [X] T009 [P] `SyncHandler` interface in `cm/core/sync/SyncHandler.kt`, exactly the members of contracts/sync-engine.md: `recordType`, `send`, `applyServerCopy`, `onDiscarded`, `refusalFields`
- [X] T010 [P] `SyncScheduler` interface (`enqueue(delay: Duration = Duration.ZERO)`, `cancel()`) in `cm/core/sync/SyncScheduler.kt`, plus `NoopSyncScheduler` in `im/core/sync/NoopSyncScheduler.kt`
- [X] T011 [P] `Backoff` in `cm/core/sync/Backoff.kt`: delays 2 s, 5 s, 15 s, 30 s, 60 s, 2 min, then 5 min forever; `delayFor(attempts, retryAfterSeconds)` returns the larger of the step and the server's wait (research R-03)
- [X] T012 `SyncEngine` skeleton in `cm/core/sync/SyncEngine.kt`:
  - constructor: `(dao, syncScopeDao, handlers: List<SyncHandler>, scheduler, currentAccountId: () -> String?, clock, backoff)`
  - `enqueue` / `enqueueDelete` per contracts/sync-engine.md: collapse, keep the oldest base, bump `revision`, reset `Refused` → `Waiting`, delete-before-first-send removes the row (FR-003, FR-004); then `scheduler.enqueue()`
  - `observe(type, id)`
  - a `Mutex` guarding per-record transitions
- [X] T013 Koin wiring in `cm/core/di/CoreModule.kt` (or a new `cm/core/sync/SyncModule.kt` added to `AppModules.kt`):
  - `single { SyncEngine(...) }` with `handlers = getAll<SyncHandler>()` and `currentAccountId` from `SessionManager` via an injected lambda registered in `authModule`, so `core` doesn't import auth (P-10.7)
  - add the `pendingChangeDao` provider
  - add `pending_change` to `cm/core/database/DatabaseCleaner.kt`
- [X] T014 Android scheduler in `am/core/sync/WorkManagerSyncScheduler.kt`:
  - unique work `sync-drain`
  - `ExistingWorkPolicy.REPLACE` only when the new delay is shorter than the pending one, else `KEEP`
  - `Constraints(requiredNetworkType = CONNECTED)`
  - `cancel()` cancels the unique work

  Worker in `am/core/sync/SyncDrainWorker.kt`: a `CoroutineWorker` that gets `SyncEngine` from Koin (`KoinComponent`) and calls `drain()`, returning `success()`, or `retry()` only on an unexpected exception. Register the scheduler in `cm/core/di/PlatformModule` android actual (`am/core/di/PlatformModule.android.kt`) and `NoopSyncScheduler` in the iOS actual

**Checkpoint**: DAO and migration tests pass; the app builds; the engine can enqueue.

---

## Phase 3: User Story 1 - Changes made offline are never lost (Priority: P1) 🎯 MVP

**Goal**: queued changes are sent automatically, in order, with retries, surviving process death.

**Independent Test**: engine tests with a fake handler: offline → queued → "online" → sent, with collapse, ordering and backoff; worker test shows a scheduled drain runs.

### Tests for User Story 1

- [X] T015 [P] [US1] Engine send tests in `ct/core/sync/SyncEngineSendTest.kt` with a fake DAO, fake scheduler, fake handler and virtual time:
  - enqueue while offline → row `Waiting`, `scheduler.enqueue()` called
  - drain success → row deleted, `applyServerCopy` called with the answer, `last_pushed_at` set
  - three edits before a send → one send with the latest payload and the first base (FR-003)
  - an edit landing during a send (revision changed) → row stays `Waiting` with base = returned version, and is sent next (spec Edge Cases)
  - two records → both sent, same-record order kept (FR-007)
- [X] T016 [P] [US1] Retry tests in `ct/core/sync/SyncEngineRetryTest.kt`:
  - `Offline` / `Timeout` → `Waiting`, `attempts + 1`, scheduler enqueued with **zero** delay
  - `RATE_LIMITED` with `retry_after_seconds = 90` → `next_attempt_at = now + 90 s`
  - `INTERNAL_ERROR` → backoff steps 2 s, 5 s, 15 s …, capped at 5 min, never giving up (FR-009)
  - `drain()` skips rows not yet due
- [X] T017 [P] [US1] Account-tagging and restart tests in `ct/core/sync/SyncEngineAccountTest.kt`:
  - rows of another `account_id` are never sent (FR-018)
  - `Sending` rows at drain start are reset to `Waiting` and resent (process death)
  - `DatabaseCleaner.clear()` empties the queue
- [X] T018 [P] [US1] Worker test in `ht/core/sync/SyncDrainWorkerTest.kt` with `WorkManagerTestInitHelper`: scheduling enqueues `sync-drain` with a network constraint; running it calls `SyncEngine.drain()` (Koin test module with a fake engine)

### Implementation for User Story 1

- [X] T019 [US1] `SyncEngine.drain()` in `cm/core/sync/SyncEngine.kt`:
  1. `resetSending()`
  2. for each `due(currentAccountId, now)` row: mark `Sending`; call the handler's `send`; classify per contracts/sync-engine.md (success → delete or rebase by revision, `applyServerCopy`, `syncScopeDao` `last_pushed_at`; retryable → backoff, `scheduler.enqueue(delay)`)
  3. offline or timeout → back to `Waiting` and `scheduler.enqueue()`, stopping the loop (no connection for the rest either)

  Clash and refusal branches are completed in US3/US4; until then they stay `Waiting` with a TODO-free `error(...)` guard covered by those stories' tests.
- [X] T020 [US1] Triggers: in `cm/feature/account/domain/AccountSyncCoordinator.kt` call `scheduler.enqueue()` on sign-in and on `onForeground()` (the engine drains pending rows after an app restart), and `scheduler.cancel()` on sign-out through a `UserDataCleaner` registered in `cm/core/sync/SyncModule.kt`

**Checkpoint**: the MVP. The engine is proven with fake handlers; no feature uses it yet.

---

## Phase 4: User Story 2 - See what hasn't synced yet (Priority: P1)

**Goal**: counts, last sync time, and "Sync now" in the account area; per-record state available to screens.

**Independent Test**: with rows in the fake DAO, `status` emits the right counts; the Account screen shows them and "Sync now" triggers an immediate drain.

### Tests for User Story 2

- [X] T021 [P] [US2] `ct/core/sync/SyncEngineStatusTest.kt`:
  - `status` emits `waiting` (Waiting + Sending), `needsDecision` (Clash + Refused), `lastSyncedAt` = newest `last_pushed_at`
  - `syncNow()` clears `next_attempt_at` on waiting rows and calls `scheduler.enqueue()`
- [X] T022 [P] [US2] Account MVI tests in `ct/feature/account/ui/account/AccountViewModelTest.kt`: state mirrors `SyncStatus`; `SyncNowClicked` calls the injected `syncNow` once; "Sync now" appears only when `waiting > 0`

### Implementation for User Story 2

- [X] T023 [US2] `SyncEngine.status` and `syncNow()` in `cm/core/sync/SyncEngine.kt` (research R-10)
- [X] T024 [US2] Account screen:
  - `cm/feature/account/ui/account/AccountContract.kt`: state `sync: SyncStatus`, intent `SyncNowClicked`
  - `AccountViewModel.kt`: takes `status: Flow<SyncStatus>` and `syncNow: suspend () -> Unit` lambdas
  - `AccountScreen.kt`: an `AppListItem` "N changes waiting to sync" / "M need your decision" / "Last synced %1$s" (tag `account.syncStatus`) and a `SecondaryButton` "Sync now" (tag `account.syncNow`) when `waiting > 0`, per contracts/ui.md

  Wire the lambdas in `cm/feature/account/di/AccountModule.kt`
- [X] T025 [P] [US2] Strings in both files: plurals `sync_waiting_count`, `sync_needs_decision_count`; `sync_last_synced`, `sync_never`, `action_sync_now` (Arabic plurals: zero/one/two/few/many/other)
- [X] T026 [P] [US2] Screenshot cases in `ht/feature/account/AccountScreensScreenshotTest.kt`: `account_sync_waiting` (2 waiting, 1 needs decision) and `account_synced` (0 waiting, last synced time)

---

## Phase 5: User Story 3 - Decide when a change clashes with another device (Priority: P1)

**Goal**: 409 answers become person-resolved clashes; the profile runs on the engine with no visible change.

**Independent Test**: engine clash tests; spec 002's `ProfileSyncerTest` and profile UI tests pass unchanged against the engine-backed `ProfileSyncer`.

### Tests for User Story 3

- [X] T027 [P] [US3] `ct/core/sync/SyncEngineClashTest.kt`:
  - `VERSION_CONFLICT` with `current` → `Clash`, `clash_json` stored, other rows still sent (FR-011)
  - `keepMine` → base = `current.version`, `Waiting`, sent
  - `useTheirs` → `applyServerCopy(current)`, row deleted, `onDiscarded`
  - `current.deleted_at` set → `keepMine` sets `restore = true` and the handler receives it; an ordinary resend never restores (FR-013)
  - `VERSION_CONFLICT` without `current` → retried
- [X] T028 [P] [US3] `ProfileSyncHandler` tests in `ct/feature/account/data/ProfileSyncHandlerTest.kt` (MockEngine via `AccountFixtures`): `send` issues `PUT /me/profile` with `version = baseVersion` and the draft fields exactly as spec 002's contract; `applyServerCopy` writes the server columns only; `onDiscarded` restores the app language from the server copy

### Implementation for User Story 3

- [X] T029 [US3] Clash branch, `keepMine`, `useTheirs`, `discard` in `cm/core/sync/SyncEngine.kt` (research R-05)
- [X] T030 [US3] Move the profile onto the engine:
  - `cm/feature/account/data/ProfileSyncHandler.kt` implements `SyncHandler` (`recordType = "profile"`, scope `user`), registered in `AccountModule` as `single<SyncHandler>`
  - `cm/feature/account/domain/ProfileSyncer.kt` keeps its public API (`view`, `saveLocally`, `push`, `keepMine`, `useTheirs`, `discard`) and delegates to `SyncEngine`:
    - `view` = `combine(profile row, engine.observe("profile", id))` building the same `ProfileView` (data-model §4)
    - its in-process retry job is removed (the engine owns retries)
  - `cm/feature/account/data/ProfileRepository.kt` loses the pending/conflict/rejection methods
- [X] T031 [US3] Keep spec 002's profile tests green: adapt only construction in `ct/feature/account/domain/ProfileSyncerTest.kt` and `ct/feature/account/AccountFixtures.kt` (engine + fake scheduler) — assertions unchanged; if one must change, record why in this task
- [X] T032 [P] [US3] Strings for future tombstone screens: `sync_deleted_elsewhere`, `action_restore_with_mine`, `action_accept_deletion` (contracts/ui.md; no screen uses them yet)

---

## Phase 6: User Story 4 - A refused change is never silently dropped (Priority: P2)

**Goal**: permanent refusals are kept with a reason until the person edits or discards.

**Independent Test**: engine refusal tests; the profile's "rejected currency" scenario behaves as in spec 002.

### Tests for User Story 4

- [X] T033 [P] [US4] `ct/core/sync/SyncEngineRefusalTest.kt`:
  - `VALIDATION_FAILED`, `NOT_FOUND`, `FORBIDDEN`, `METHOD_NOT_ALLOWED`, `INVALID_ID` → `Refused` with `refusal_json` {code, fields from `refusalFields`}, never retried by `drain()`
  - a new `enqueue` for that record → `Waiting` and sent
  - `discard` → row deleted, `onDiscarded`
  - `UNAUTHENTICATED` → row untouched

### Implementation for User Story 4

- [X] T034 [US4] Refusal branch in `SyncEngine.drain()` and `Refusal` decoding in `cm/core/sync/SyncEngine.kt`. In `ProfileSyncHandler.refusalFields` map pointers with the existing `ProfileField.fromPointer` so `ProfileView.rejection` is unchanged (FR-019)

---

## Phase 7: User Story 5 - Signing out doesn't lose changes by surprise (Priority: P2)

**Goal**: the sign-out sheet warns with the count and offers "Sync now".

**Independent Test**: with 1 waiting change the sheet shows the warning; with 0 it looks as in spec 002.

### Tests for User Story 5

- [X] T035 [P] [US5] Home and Account sign-out tests in `ct/feature/auth/ui/home/HomeViewModelTest.kt` and `ct/feature/account/ui/account/AccountViewModelTest.kt`: state carries `pendingChanges` from `SyncStatus.waiting + needsDecision`; `SyncNowClicked` from the sheet calls `syncNow` once; confirming still signs out (queue wiped by the cleaner, T017)

### Implementation for User Story 5

- [X] T036 [US5] `cm/feature/auth/ui/signout/SignOutConfirmSheet.kt` takes `pendingChanges: Int` and `onSyncNow: (() -> Unit)?`. When `pendingChanges > 0` it shows an `InlineAlert(Warning, sign_out_pending_warning)` (tag `signOut.pendingWarning`) and a `SecondaryButton` "Sync now" (tag `signOut.syncNow`) above Sign out. Pass the values from `HomeScreen`/`HomeViewModel` (status flow + syncNow lambda wired in `AccountModule`, where Home is registered) and from `AccountScreen`
- [X] T037 [P] [US5] String `sign_out_pending_warning` (plural) in both files; screenshot case `sign_out_pending` in `ht/feature/account/AccountScreensScreenshotTest.kt`

---

## Phase 8: Polish & Cross-Cutting

- [X] T038 [P] Logging review: `cm/core/sync/` logs only type, id, state and request id (spec §4, P-09.2); add a test that a payload containing a marker string never appears in captured Kermit output (`ct/core/sync/SyncEngineLoggingTest.kt`)
- [X] T039 [P] Arabic copy review of every new string (native plurals, Western digits, no "version"/"outbox")
- [X] T040 Full run: `./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug`
- [X] T041 Rerun spec 002's E2E suite (`.specify/scripts/bash/run-e2e.sh 002-account-security`) on the emulator; expect the 24 passes of its report (FR-019, SC-006)
- [X] T042 Quickstart §3 manual checks 1–5 on the emulator (dev build); record results in `specs/004-sync-engine/quickstart.md` under a "Results" heading

---

## Dependencies & Execution Order

- **Setup (T001–T004)** → **Foundational (T005–T014)** → stories.
- **US1** (T015–T020) needs Foundation. **US2** needs US1's `drain` only for "Sync now" (T023 builds on T019).
- **US3** needs US1 (its clash branch completes T019), and **moves the profile**; nothing in the app uses the engine until T030.
- **US4** needs US3's profile handler for T034's mapping.
- **US5** needs US2's `status`.
- **Polish** last; T041 needs T030 and T034.

### Parallel opportunities

- T005 ∥ T006. T008 ∥ T009 ∥ T010 ∥ T011.
- Each story's tests run together.
- Strings (T025, T032, T037) run beside their screens.
- US2 (UI) and US3 (clash + profile) can proceed in parallel after US1; both touch `AccountModule.kt`, so merge those edits one at a time.

## Parallel Example: User Story 1

```text
T015 send tests  ∥  T016 retry tests  ∥  T017 account/restart tests  ∥  T018 worker test
then T019 drain → T020 triggers
```

## Implementation Strategy

1. **MVP**: Phases 1–3. The engine sends, retries and survives restarts, proven with fakes; the app is unchanged.
2. **US2 + US3**: people see sync status, and the profile runs on the engine with clashes; spec 002 must stay green.
3. **US4 + US5**: refusals and the sign-out warning complete the spec.
4. **Polish**: E2E regression (spec 002) and manual offline checks.
