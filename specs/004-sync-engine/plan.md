# Implementation Plan: Sync Engine (Offline Outbox)

**Branch**: `004-sync-engine` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-sync-engine/spec.md`

## Summary

The plan generalises spec 002's `ProfileSyncer` into a shared engine in `core.sync`:
- **Queue**: a persistent `pending_change` table with one row per record. New edits collapse into the row, and a revision counter keeps edits made during a send.
- **Sending**: a drain loop sends each record through its feature's `SyncHandler` and classifies the answer by code: success, clash, refusal, or retry with backoff.
- **Background**: a WorkManager job that waits for a connection, so changes leave the phone even with the app closed (owner-approved dependency).
- **Status**: flows that the account screen and sign-out sheet read. Sign-out warns before discarding.

The profile moves onto the engine behind its existing API. A Room v1→v2 migration moves any pending profile edit into the queue.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack (Compose Multiplatform 1.12.1, MVI base, Navigation 3, Ktor 3.6.0, kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite, Kermit) **plus** `androidx.work:work-runtime-ktx` 2.12.0 in `androidMain` only (owner-approved 2026-09-30, research R-03), and `androidx.work:work-testing` for the host test.

**Storage**: Room `saveme.db` v2: new `pending_change`, `profile` without pending columns, `sync_scope.last_pushed_at` (data-model §1, research R-09).

**Testing**: kotlin-test, coroutines-test (virtual time for backoff), Turbine, fakes for DAO/scheduler/handlers in `commonTest`; Room DAO + `MigrationTestHelper` + `WorkManagerTestInitHelper` in `androidHostTest` (Robolectric); Roborazzi for the account and sign-out sheet; Maestro spec 002 suite for the unchanged profile.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles with a no-op scheduler.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**:
- A queued change leaves within about 1 minute of a connection returning (SC-003): offline failures re-enqueue with no delay behind a connectivity constraint.
- `enqueue` is a single local write (under 50 ms).

**Constraints**:
- P-04 (every rule).
- Never send under another account (FR-018).
- No retry of refusals.
- Profile behaviour unchanged (FR-019).
- The migration must keep a waiting edit (FR-005).

**Scale/Scope**: 1 new table + 2 changed, 1 migration, the engine, 1 handler (profile), Android worker, 2 UI touch points, ~10 string keys × 2 languages.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails. One new dependency (WorkManager) needed the owner's approval under P-14's quality gate, as Room did in spec 002; it was approved on 2026-09-30.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Backend terms scope, version, tombstone in code; UI copy "waiting to sync", "changed on another device", "not saved" (contracts/ui.md), never "outbox" or "version" |
| P-02 API contract fidelity | PASS | Classification by `code` and retry class only (contracts/sync-engine.md, R-06); `retry_after_seconds` honoured (R-03); a replay is safe by contract; nothing is auto-retried that the catalogue marks `Never`/`AfterChange` except via the person's edit |
| P-03 Money | N-A | No amounts handled yet; payloads are opaque JSON to the engine |
| P-04 Offline-first | PASS | Rule 1: screens read the database, incl. the queue (R-10). Rule 2: `enqueue` is a local write (contracts). Rule 3: ids stay feature-generated UUIDv7. Rule 4: every send carries `base_version`, and a clash waits for the person (R-05). Rule 5: deletes are queued deletions (R-05, FR-004) |
| P-05 Time | PASS | `next_attempt_at`, `updated_at`, `last_pushed_at` stored as UTC instants; "Last synced" shown in the profile timezone via the existing `formatDateTime` |
| P-06 Localisation & RTL | PASS | New copy in `values/` and `values-ar/` with plurals (contracts/ui.md); screenshots in AR RTL |
| P-07 Design system only | PASS | Status lines and warning use existing `AppListItem`, `InlineAlert`, `SecondaryButton` |
| P-08 Accessibility | PASS | Counts announced as text, not colour; 2× font screenshots |
| P-09 Security & privacy | PASS | Rows tagged with `account_id`, drained only for the signed-in user, wiped by `DatabaseCleaner`, scheduler cancelled at sign-out (R-07); payloads are never logged (engine logs type, id, state only) |
| P-10 Shared-first architecture | PASS | Engine in `core.sync` (commonMain) imports no feature; features register `SyncHandler`s (R-01). WorkManager only in `androidMain` behind `SyncScheduler` (rule 2). `ProfileSyncHandler` lives in `feature.account.data`/`domain` per rule 6 |
| P-11 Naming | PASS | `SyncEngine`, `SyncHandler`, `SyncScheduler`, `PendingChange`, `SyncStatus` |
| P-12 Entitlements & billing | N-A | No gated capability |
| P-13 Observability | PASS | Kermit `sync` tag logs record type, id, state transitions and request ids; no payloads |
| P-14 Quality gates | PASS | Tests per invariant (spec §4) at the layers in research R-11; migration test; new dependency approved |
| P-15 Scope guard | PASS | No MVP-guarded capability; wave 2 (batch, change feed) explicitly deferred |
| P-16 MVI presentation | PASS | Account and sign-out changes go through the existing MVI contracts (new State fields, Intents `SyncNowClicked`), reducer + VM tests |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/004-sync-engine/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── sync-engine.md
│   └── ui.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                     # + work = "2.12.0", work-runtime-ktx, work-testing
shared/
├── build.gradle.kts                          # androidMain: work-runtime-ktx; androidHostTest: work-testing, room-testing
├── schemas/…AppDatabase/2.json               # exported v2
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/database/                    # AppDatabase v2 (+ PendingChangeEntity, Migration1To2), DatabaseCleaner (+ pending_change)
    │   ├── core/sync/                        # SyncEngine, SyncHandler, SyncScheduler, PendingChange, SyncStatus, Backoff, PendingChangeDao
    │   ├── feature/account/data/             # ProfileEntity (−4 columns), ProfileRepository, ProfileSyncHandler
    │   ├── feature/account/domain/           # ProfileSyncer on the engine (same public API); AccountSyncCoordinator also drains on foreground
    │   ├── feature/account/ui/account/       # sync status + Sync now
    │   └── feature/auth/ui/signout/          # SignOutConfirmSheet: pending warning + Sync now; Home/Account pass counts
    ├── androidMain/kotlin/com/fares/saveme/core/sync/   # WorkManagerSyncScheduler, SyncDrainWorker
    ├── iosMain/kotlin/com/fares/saveme/core/sync/       # NoopSyncScheduler
    ├── commonTest/…/core/sync/, feature/account/        # engine + profile tests
    └── androidHostTest/…/core/database/, core/sync/     # DAO, migration, worker tests; screenshots
```

**Structure Decision**:
- The engine sits in `core`, next to the database it owns a table in, because every feature uses it.
- Handlers sit in each feature.
- Only the scheduler has platform code.
- The profile keeps its screen-facing API, so `feature.account.ui` barely changes.

## Implementation phases (input to /speckit-tasks)

1. **Setup**: WorkManager and test dependencies; `AppDatabase` v2 entity and `Migration(1, 2)`; exported schema.
2. **Foundation**:
   - `PendingChange` model, DAO and entity
   - `SyncHandler` and `SyncScheduler` interfaces, backoff
   - `SyncEngine`: enqueue, collapse, revision, drain, classification, clash and refusal handling, account tagging, stale `Sending` reset
   - `DatabaseCleaner` and scheduler cancellation at sign-out
   - Koin wiring; Android WorkManager scheduler and worker; iOS no-op
3. **US1 Never lost**: engine drain and backoff tests; worker test; foreground trigger in `AccountSyncCoordinator`.
4. **US3 Clashes**:
   - `keepMine`, `useTheirs` and tombstone restore in the engine
   - `ProfileSyncHandler`, then `ProfileSyncer` moved onto the engine
   - migration of existing pending edits
   - spec 002 profile tests kept green
5. **US4 Refusals**: refusal classification and profile field mapping; edit and discard paths.
6. **US2 Status**: `status` flow; account screen status lines and Sync now; strings; screenshots.
7. **US5 Sign-out**: sheet warning with count and Sync now (Home + Account); tests; screenshots.
8. **Polish**: spec 002 E2E rerun (FR-019); quickstart manual checks; Arabic copy review.

## External dependencies (not in this repo)

None for wave 0. Wave 2 waits on backend B14 (batch upload, change feed).

## Implementation notes (deviations from the design above)

- **`ProfileSyncerTest.offlineKeepsTheEditAndRetriesWithBackoff` was rewritten** as
  `offlineKeepsTheEditAndSendsItOnceOnline` (T031). It asserted `ProfileSyncer`'s own retry timer,
  which the engine replaces. The person-visible behaviour (the edit stays waiting and is sent once
  online) is asserted unchanged; backoff moved to `SyncEngineRetryTest`.
- **`AccountDaoTest.refreshNeverOverwritesAPendingEdit`** now checks the same invariant across
  tables (a server-copy update leaves the `pending_change` row alone); its `clearPending` case was
  dropped with the columns.
- **WorkManager policy**: `APPEND_OR_REPLACE` while a drain is running or nothing is pending, and
  `REPLACE` only for a sooner run. A plain `REPLACE` would cancel a drain mid-send whenever the engine
  re-schedules from inside it.
- **Migration test** builds a v1 database file from the exported v1 table definitions and lets Room
  validate the migrated schema, instead of `MigrationTestHelper`, which needs the schema as an
  asset on the KMP Android target.
- **`ProfileSyncer.saveLocally`** still starts a drain in its own scope, besides scheduling one, so
  a profile save with the app open is sent at once rather than whenever WorkManager runs.
- **The account card is always shown** ("Everything is synced" when nothing waits), so the three
  existing `screen_account_*` baselines were re-recorded on purpose.
- **`CurrentAccount`** (a `core.sync` interface implemented in `authModule`) gives the engine the
  signed-in user without `core` importing auth (P-10.7).

- **Found by quickstart check 2** (each with an engine test):
  - `CurrentAccount.load()`: `drain()` first restores the saved session when none is known, because
    a background drain runs in a fresh process where no screen has restored it.
  - `drain()` reschedules for the earliest waiting row (`PendingChangeDao.nextDueAt`) when it ends
    with rows not yet due, so an early run can't leave a change waiting until the app is opened.
  - Stored instants are fixed-width (`…:50.000Z`): `Instant.toString()` drops a zero fraction,
    which broke the string comparison in `due`.
  - Zero-delay runs are expedited WorkManager work on API 31+: JobScheduler batched a closed app's
    ready ordinary jobs for many minutes. Offline failures after the first back off (2 s … capped
    at 30 s), so a phone that is connected but can't reach the server doesn't loop.

## Complexity Tracking

None.
