# Research: Sync Engine (Offline Outbox)

Phase 0 of the plan. The profile sync built in spec 002 (`ProfileSyncer`: a pending edit, base
version, conflict and rejection held on the profile row, with in-process retry delays) is the
prototype; these decisions generalise it.

## R-01 Where the engine lives

- **Decision**: A new `core.sync` package. Features plug in through a `SyncHandler` per record type,
  registered in Koin and collected by the engine (`getAll<SyncHandler>()`), the same pattern as
  `UserDataCleaner`.
- **Rationale**: Every future syncable feature (C4 wallets, C5 categories, C6 transactions) shares it,
  and `core` must not import a feature (P-10.7), so features call into core, never the reverse.
- **Alternatives**: Keep one syncer per feature (as `ProfileSyncer` is today). Rejected: C14's point
  is one queue, one retry policy and one sign-out rule; three copies of `ProfileSyncer` would drift.

## R-02 One queue row per record

- **Decision**:
  - Table `pending_change`, primary key `(record_type, record_id)`: one row per record with unsent content.
  - A new edit to a waiting record rewrites the row's content and keeps the oldest base version (FR-003). Order within a record is then trivially kept (FR-007).
  - A monotonically increasing `revision` column detects edits that land while a send is in flight. On success the row is deleted only if its revision is unchanged; otherwise it stays waiting with its base moved to the version the server returned.
- **Rationale**: The server stores whole records and a write carries the whole record, so only the
  latest content per record matters. One row per record makes "changes to the same record in order"
  structural rather than a scheduling rule.
- **Alternatives**: An append-only log of every edit. Rejected: every entry after the first would
  be sent against a base version the first send already bumped, conflicting with itself.

## R-03 Sending, retries and background work

- **Decision**:
  - `SyncEngine.drain()` sends every row in state `Waiting` whose `next_attempt_at` has passed. One send per record at a time, different records in `created_order` order.
  - Scheduling goes through a common `SyncScheduler` interface:
    - **Android**: WorkManager (`androidx.work:work-runtime-ktx` 2.12.0, owner-approved 2026-09-30), unique work `sync-drain` with a `NetworkType.CONNECTED` constraint. `enqueue` runs whenever a change is queued, a retry is due, the app comes to the foreground, or the person taps "Sync now".
    - **iOS**: a no-op; iOS has no live API (spec 001).
  - Offline and timeout failures re-enqueue with no delay, so the constraint releases them the moment a connection returns.
  - `429` and `5xx` failures use backoff: 2 s, 5 s, 15 s, 30 s, 60 s, then 2 min, capped at 5 min, or the server's `retry_after_seconds` when longer. Attempts never stop while the row waits (FR-009).
- **Rationale**:
  - WorkManager survives process death and reboot (FR-005, FR-006), and its connectivity constraint replaces a hand-written network listener.
  - The no-delay offline path meets SC-003's "within 1 minute once a connection returns".
- **Alternatives**:
  - A foreground coroutine only (as `ProfileSyncer` does). Rejected by the owner: changes would wait until the app reopened.
  - An `AlarmManager` / `JobScheduler` wrapper. Rejected: WorkManager is the maintained abstraction over both.

## R-04 Handler contract

- **Decision**: `SyncHandler` has five members (contracts/sync-engine.md):
  - `recordType`
  - `send(change)`, which returns the raw `ApiResult` of the record's per-record `PUT`
  - `applyServerCopy(json)`, which writes the server's answer into the feature's own table
  - `onDiscarded(recordId)`, for side effects such as restoring the app language after a profile edit is dropped
  - `refusalFields(problem)`, which maps validation pointers to the feature's field names for its screens

  The engine owns the classification (success, clash, refused, retry) by `code`, never the handler (P-02.4).
- **Rationale**: Adding a record type (FR-020) is then: its table, its handler, its screens. The
  error-handling matrix lives once (spec §5).
- **Alternatives**: Handlers classify their own answers. Rejected: the matrix would be copied per
  feature, the drift C14 exists to prevent.

## R-05 Clashes and deletions

- **Decision**:
  - On `409 VERSION_CONFLICT` the row moves to `Clash` and stores `current`; other rows keep draining.
  - "Keep mine" rebases the row on `current.version` and moves it back to `Waiting`.
  - "Use the other device's" calls `applyServerCopy(current)`, deletes the row, then `onDiscarded`.
  - When `current.deleted_at` is set, "Keep mine" is offered as "Restore with my change": the row is marked `restore = true`, and the handler sends `deleted_at: null` with the content (backend: Deleting and restoring). "Use the other device's" becomes "Accept the deletion".
  - A delete enqueued for a row with base version 0 (never sent) removes the row instead of queuing a delete (FR-004).
- **Rationale**: Backend ADR-0008 rules 8, 9 and 14: the server never merges and an edit never
  silently undeletes.

## R-06 Refusals

- **Decision**:
  - Any failure whose code isn't a clash, a retryable condition, or `UNAUTHENTICATED` moves the row to `Refused`. That covers `VALIDATION_FAILED`, `IMMUTABLE_FIELD`, `INVALID_ID`, `NOT_FOUND`, `FORBIDDEN` and `METHOD_NOT_ALLOWED`.
  - The problem's `code` and `errors[]` are stored as JSON. Nothing is retried until the person edits (the row returns to `Waiting`) or discards.
  - `UNAUTHENTICATED` leaves the row alone; the existing session-ended flow signs out, and the cleaner discards the queue.
- **Rationale**: FR-014. The retry class decides which codes retry (`Automatic` → backoff), matching the catalogue (P-02.6).

## R-07 Account tagging and sign-out

- **Decision**:
  - Every row stores the `account_id` (user id) that made it.
  - `drain()` sends only rows whose `account_id` equals the signed-in user.
  - `DatabaseCleaner` deletes the whole table at sign-out, and `SyncScheduler.cancel()` stops pending work.
  - The person-initiated sign-out sheet reads the waiting and needs-decision counts, warns, and offers "Sync now" (FR-017).
- **Rationale**: FR-018 twice over. Wiping alone would do on one phone, but a background worker racing a sign-out/sign-in could otherwise send the previous person's row.

## R-08 Moving the profile onto the engine

- **Decision**:
  - `ProfileSyncer` keeps its public API (`view`, `saveLocally`, `keepMine`, `useTheirs`, `discard`), so the profile screen and its tests don't change. Underneath it delegates to the engine.
  - `ProfileEntity` loses `pending_json`, `pending_base_version`, `conflict_json` and `rejection_json`. The view joins the profile row with the engine's row for `(profile, <id>)`.
  - A `ProfileSyncHandler` sends `PUT /me/profile` and writes the server copy.
- **Rationale**: FR-019. The screen, view model and E2E flows keep working because the view model
  sees the same `ProfileView`.

## R-09 Database migration

- **Decision**: Room schema v1 → v2 with a hand-written `Migration(1, 2)`:
  1. Create `pending_change`.
  2. Copy any existing profile edit into it (`record_type = 'profile'`, state from the conflict and rejection columns).
  3. Rebuild `profile` without the four pending columns (SQLite's create–copy–drop–rename).
  4. Add `last_pushed_at` to `sync_scope`.

  Tested with Room's `MigrationTestHelper` in `androidHostTest` against the exported v1 schema.
- **Rationale**: FR-005 says a waiting edit survives an app update, so a destructive fallback is not
  acceptable even before release.
- **Alternatives**: An `AutoMigration` with `@DeleteColumn`. Rejected: it can't carry the data copy in
  step 2.

## R-10 Status for screens

- **Decision**: The engine exposes:
  - `observe(recordType, recordId): Flow<PendingChange?>`, for a record's "waiting / changed elsewhere / not saved" state
  - `status: Flow<SyncStatus>`, with `waiting`, `needsDecision` and `lastSyncedAt`

  `lastSyncedAt` is the newest `sync_scope.last_pushed_at`. The account screen shows the counts, the time, and "Sync now" (FR-010, FR-016).
- **Rationale**: Screens read state from the database (P-04.1); the engine's table is part of that
  database.

## R-11 Testing

- **Decision**:
  - The engine is tested in `commonTest` with a fake DAO, a fake scheduler, fake handlers, and virtual time for backoff.
  - The queue SQL and the v1→v2 migration are tested in `androidHostTest` (Robolectric, framework driver), as spec 002's DAO tests are.
  - The WorkManager worker gets a Robolectric test with `WorkManagerTestInitHelper`.
  - Offline end-to-end runs remain manual (spec §8).
- **Rationale**: Mirrors spec 002's split (its plan's implementation notes); P-14.
