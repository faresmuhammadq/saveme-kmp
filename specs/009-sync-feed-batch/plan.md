# Implementation Plan: Sync Feeds and Batch Upload (client)

**Branch**: `009-sync-feed-batch` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/009-sync-feed-batch/spec.md`

## Summary

The spec 004 engine keeps its queue, its answer classification and every person-facing behaviour;
what changes is how changes travel:

- **Upload**: `drain()` packs due changes, oldest first, into `POST /sync/batch` requests of up to
  50 items. Each handler maps a change to exactly its per-record write (`batchItem`); archives and
  restores stay per-record at their place in the order. Each item's answer goes through the
  unchanged `settle()`. Changes whose wallet or category was created on the phone and not yet
  accepted are held (R-07). Request-level shape/endpoint refusals fall back to per-record sends.
- **Reads**: a `FeedPuller` reads `GET /sync/user/changes` and `GET /sync/wallets/{id}/changes`
  from one stored position per scope (new columns on `sync_scope`, Room 5 → 6) and hands rows to
  per-type `FeedApplier`s registered by the features, with a version guard, a sweep after
  cursorless reads, `410`/`400`/`404` handling and gained/lost wallets from the person's own
  memberships.
- **Runs**: a `SyncRunner` serializes upload-then-read runs under one lock, merges triggers and
  keeps the services' `refresh(...)` signatures, floors and outcomes, so stores and screens do not
  change. The balance carry now settles per wallet on that wallet's feed read.
- **Retired**: every whole-list and single-record read of a syncable record (wallets, members,
  transactions, own categories, references, sessions).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, MVI base, Ktor 3.6.0
with `HttpTimeout`, kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5
+ bundled SQLite, WorkManager 2.12.0 in `androidMain`, Kermit). **No new dependency.**

**Storage**: Room `saveme.db` v6: `sync_scope` + `feed_cursor`, `feed_read_at` (data-model §1).
No other schema change.

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor `MockEngine` (contract tests from the
OpenAPI examples), fakes for appliers/handlers/scheduler in `commonTest`; Room DAO, migration and
sign-out wipe tests in `androidHostTest` (Robolectric); captured live-backend answers as fixtures
(`CapturedSyncResponses.kt`). No emulator, Maestro or device run (owner policy); Maestro flows
written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles (no-op scheduler, runner works
in the foreground).

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**:
- 20 offline changes → ≤ 2 batch requests (SC-001).
- A refresh with nothing new: 1 + N empty feed pages, no Room writes beyond positions (SC-003).
- Applying a 200-row page in under 200 ms on the host (rows batched per type).

**Constraints**: P-04 (every rule); classification parity with spec 004 (FR-019); no screen change
(SC-008); batch answer allowance 60 s; one run at a time; never another account's data.

**Scale/Scope**: 1 migration, ~6 new `core.sync` types, 4 handler extensions, 8 appliers across 4
features, 1 interface implementation (`WalletScopes`), ~8 retired read paths, services rewired; no
new strings.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails. No new dependency; no UI; no MVP-guarded
capability (sync is core MVP, brief C14 wave 2).

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code uses backend terms (change row, sync batch, scope, cursor) internally; no new user copy; screens keep "Waiting to sync", "Updated …" (spec §2) |
| P-02 API contract fidelity | PASS | Shapes from `sync.yaml` (contracts/sync-api-client.md); items carry only declared members, `wallet_id` only on transactions; unknown members and unknown row types ignored (R-02, R-12); every branch on `code` (`INVALID_CURSOR`, `SYNC_CURSOR_EXPIRED`, `NOT_FOUND`, item `problem.code`), the two cursor codes added to `ApiErrorCode` as after-change (FR-032); retries follow the catalogue via `settle()` incl. `Retry-After` (R-06); headers via the shared client |
| P-03 Money | PASS | Amounts stay `Long` minor units inside the unchanged DTOs; the engine never reads amounts; the carry arithmetic is unchanged and per wallet (R-09) |
| P-04 Offline-first | PASS | Rule 1: feeds write Room only, screens read Room (FR-001). Rule 2: saves still enqueue locally. Rule 3: ids unchanged. Rule 4: items carry the base version; clashes go to the person through `settle()` (R-06); feeds never overwrite queued changes (R-02). Rule 5: deletes are queued deletions sent as `delete` items |
| P-05 Time | PASS | `feed_read_at` and carry stamps are fixed-width UTC instants; no calendar logic added |
| P-06 Localisation & RTL | N-A | No new user-facing text or screen |
| P-07 Design system only | N-A | No UI change |
| P-08 Accessibility | N-A | No UI change |
| P-09 Security & privacy | PASS | Positions bound to the account and wiped with `sync_scope` at sign-out (FR-028); lost wallets purged (R-03); logs carry types, ids, outcomes, codes, counts, request ids — never payloads, amounts, notes or cursor values (FR-030); no raw storage of skipped kinds (R-12) |
| P-10 Shared-first architecture | PASS | All in `commonMain`; `core.sync` imports no feature (`FeedApplier`, `WalletScopes`, `SyncHandler` are implemented by features, R-01); WorkManager stays behind `SyncScheduler`; feature `data` appliers call their own repositories; `wallets.domain` implements `WalletScopes` over its repository |
| P-11 Naming | PASS | `SyncApi`, `FeedPuller`, `FeedApplier`, `SyncRunner`, `WalletScopes`, `BatchItem` |
| P-12 Entitlements & billing | N-A | No gated capability; `FEATURE_NOT_ENTITLED` items refused as today |
| P-13 Observability | PASS | One log line per batch (count, request id) and per item (type, id, outcome, code); one per scope read (rows, skipped types, outcome) |
| P-14 Quality gates | PASS | Tests per invariant (spec §4) at the layers in R-15; contract tests for both feeds and the batch incl. each handled code (contracts/sync-api-client.md); migration test; no new dependency |
| P-15 Scope guard | PASS | No guarded capability |
| P-16 MVI presentation | N-A | No store or screen changes; services keep their signatures and outcomes |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/009-sync-feed-batch/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── sync-engine-v2.md
│   └── sync-api-client.md
├── checklists/
│   ├── requirements.md
│   └── sync.md          # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/
├── schemas/…AppDatabase/6.json                       # exported v6
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/network/ApiErrorCode.kt              # + INVALID_CURSOR, SYNC_CURSOR_EXPIRED
    │   ├── core/database/                            # AppDatabase v6, MIGRATION_5_6, SyncScopeEntity/Dao (+ cursor, read stamp)
    │   ├── core/sync/                                # SyncApi + DTOs, BatchItem, RecordKey, FeedApplier, WalletScopes,
    │   │                                             #   FeedPuller, SyncRunner, SyncLock; SyncEngine.drain (batch), SyncHandler (+3 members)
    │   ├── core/di/CoreModule.kt                     # wiring: SyncApi, puller, runner, lock; getAll<FeedApplier>()
    │   ├── feature/account/data/                     # ProfileSyncHandler (batchItem), ProfileFeedApplier, DeviceSessionFeedApplier; AccountApi − listSessions
    │   ├── feature/account/domain/                   # AccountRefresher (GET /me kept, version guard), SessionRefresher → runner, AccountSyncCoordinator → runner
    │   ├── feature/wallets/data/                     # WalletSyncHandler (batchItem), WalletFeedApplier, WalletMemberFeedApplier (wallet), OwnMembershipFeedApplier (user);
    │   │                                             #   WalletRepository − refreshAll/refreshOne/refreshMembers; WalletApi − list/get/members
    │   ├── feature/wallets/domain/                   # DefaultWalletScopes; WalletsService.refresh/refreshMembers → runner; WalletsSyncCoordinator → runner
    │   ├── feature/categories/data/                  # CategorySyncHandler (batchItem), CategoryFeedApplier, CategoryReferenceFeedApplier;
    │   │                                             #   CategoryRepository − refreshOwn; CategoryReferenceApi/refresh removed
    │   ├── feature/categories/domain/                # CategoriesService.refresh (defaults read kept + runner), refreshReferences → runner
    │   ├── feature/transactions/data/                # TransactionSyncHandler (batchItem, prerequisites, applySentAnswer), TransactionFeedApplier;
    │   │                                             #   TransactionRepository − refreshWallet, + clearCarryFor(walletId, upTo); TransactionApi − list
    │   └── feature/transactions/domain/              # TransactionsService.refresh → runner; BalanceReconciler settles per wallet read
    ├── androidMain/kotlin/com/fares/saveme/core/sync/SyncDrainWorker.kt   # → SyncRunner.runBackground()
    ├── commonTest/…/core/sync/                       # SyncEngineBatch*, SyncEngineDependency*, FeedPuller*, SyncRunner*, SyncApiContractTest, CapturedSyncResponses
    ├── commonTest/…/feature/*/data/                  # applier + batchItem tests per feature
    └── androidHostTest/…/core/database/              # Migration5To6Test, MigrationTest chain, SignOutWipeTest, DAO guard/sweep/carry tests
.maestro/009-sync-feed-batch/                         # written, deferred: E2E on owner's device
```

**Structure Decision**: The wire layer, puller and runner sit in `core.sync` next to the engine
(R-01); each feature owns the appliers for its tables and the batch mapping in its handler; the
only cross-feature contract added is `WalletScopes`, implemented by wallets and consumed by core.

## Implementation phases (input to /speckit-tasks)

1. **Setup**: `ApiErrorCode` additions; `SyncScopeEntity` columns, `MIGRATION_5_6`, AppDatabase v6, exported schema; migration test chain.
2. **Foundation**: `SyncApi` + DTOs + contract tests; `BatchItem`, `RecordKey`, `FeedApplier`, `WalletScopes`, `SyncLock`; `SyncHandler` additions with defaults; `FeedPuller`; `SyncRunner`; Koin wiring; worker → runner.
3. **US1 Backlog (batch)**: engine batch drain (packing, split around per-record rows, settle per item, request-level answers, fallback, dependencies, own-answer path); handler `batchItem`/`prerequisites` for wallet, category, transaction, profile; parity tests with captured answers; item events.
4. **US2 Feeds**: appliers for profile, device session, category, category reference, wallet, wallet member, transaction with version guard, deletion rule and sweep; services rewired to the runner (floors, outcomes, "Updated …" fallback); carry per wallet in `BalanceReconciler`; retired reads removed and their tests moved.
5. **US3 Gained/lost**: own-membership applier, `DefaultWalletScopes`, wallet 404, participants; role change.
6. **US4 Recovery**: 410 / 400 handling and sweep tests; first run after migration.
7. **US5 Fallback & resume**: fallback paths; process-death resume (stale `Sending`, position after page).
8. **Polish**: log-capture test; contract QA against the live backend and captured fixtures; Maestro flows (deferred); contract recheck; README sync section; `summary.md` (parent).

## External dependencies (not in this repo)

Backend B14 is built (`4f26ef5`); contract recorded at `saveme-backend@6b8e290`. The backend
working tree has uncommitted spec 016 (sharing, v1.9) edits that only add `invitation` rows and
membership causes, both handled generically. Backend contract gaps found (non-blocking, see spec
§9): no "which wallets changed" summary (1 + N reads per refresh); restore + edit cannot be one
batch pair because the restored version is not promised.

## Complexity Tracking

None.

## Implementation notes

Recorded while implementing (2026-10-02, owner away; each is an assumed default). Numbers refer to research items.

- **A read that begins without a position stores it only after its last page (R-04 over FR-003).** The sweep needs the ids of every page, so an interrupted cursorless read starts again from the beginning rather than resuming; a read that began with a position stores it after each page as specified. `feed_read_at` is written only when the last page (and the sweep) completed. `FeedPullerPagingTest` proves both, `SyncResumeTest` the resume.
- **`SyncApi` is an interface with `HttpSyncApi` behind it** (contract tests and the puller/engine tests use a scripted fake). The batch's 60 s allowance is a constructor default (`batchTimeoutMillis`); contract tests pass `null` for everything except the one that checks the capability on a real-time dispatcher (the 30 s default's timer would run on virtual time). A 200 whose body cannot be read is returned as a `Failure` whose `problem.code` is `UNREADABLE_ANSWER` and `status` 200, which the engine treats as a shape refusal.
- **`SyncRunner` is an interface with `DefaultSyncRunner`** so features' tests use `FakeSyncRunner`. Besides `refresh`, `requestUpload`, `runBackground` it has `suspend upload()` (an upload run awaited by `ProfileSyncer.push` and the worker) and `cancel()` (sign-out, through `SyncSignOutCleaner`). Floors: an unforced read of a scope within 60 s of its last *completed* read is skipped (a failed read starts none); scopes an upload wrote to are always read (forced); a read answering "no connection" stops the rest of the run's reads. A wallet that is not accepted is "Done" without a read.
- **Floors moved to the runner; the services kept three things.** `WalletsService`, `TransactionsService` and `CategoriesService` pass the caller's `force` through (they still call `engine.syncNow()` first when forced, as before). `CategoriesService` keeps its 60 s floor for the platform defaults list (no feed carries it) and its per-wallet "attempt counts even when it fails" floor for `refreshReferences` (a screen full of misses asks once a minute).
- **"Updated …" stamps.** Categories, sessions and transactions read the scope's `feed_read_at` and fall back to the legacy stamp while null. The wallet list shows the oldest of the person's scope and every server-accepted wallet's read (a `WalletDao.observeOldestFeedRead` join over `sync_scope`), falling back to the legacy stamp until every one of them has completed once.
- **Wallet access lost.** `WalletRepository.accessLost` clears the scope's position, then removes the wallet through `remove()` (participants included) unless a change to the wallet record waits; a placeholder is never touched. A kept wallet is still "followed" (its server version is > 0), so each run answers one `404` for it until the person settles the change. **Deviation from R-03/T052:** `WalletSyncHandler.onDiscarded` does not remove a wallet that has no live own membership (a wallet read before any member rows exist would be removed wrongly); it clears the wallet's position instead (`reconsiderAfterDiscard`), so the next read starts from the beginning and the tombstone or the `404` decides.
- **Device sessions keep no version** (the table has none), so feed rows are applied in order (active kept, any other state or a deletion removes). The profile and `GET /me` share one guarded store (`ProfileRepository.storeFeedCopy`); the app language follows only when no profile change waits.
- **The carry settles per wallet read.** Three DAO updates (`clearSourceCarry`, `clearCounterCarry`, `settleClearedCarry`, in one `@Transaction clearCarryFor`) replace the global `clearCarryUpTo`; `BalanceReconciler.settle` reads each carried wallet one at a time with the start time taken before the request. The wallet record and the carry clear are two writes (the same one-dispatcher-hop window spec 008 had between the wallet list upsert and the clear).
- **Batch drain.** `DrainReport(written, appliedAny, offline)`; a request-level refusal that is a shape/endpoint problem is `VALIDATION_FAILED`, `MALFORMED_JSON`, `UNSUPPORTED_MEDIA_TYPE`, `NOT_ACCEPTABLE`, `NOT_FOUND`, an unknown-coded 404/405/406/415, or the unreadable 200. A run that finds the account changed after an answer applies nothing (FR-028). A run is scheduled again after an applied item only when rows stayed held. Dependencies use literal record types `"wallet"` and `"category"` (the transactions `data` layer imports no other feature).
- **Handlers.** `TransactionSyncHandler.ownAnswers` is gone: `applySentAnswer` stores the copy with its carry, `applyServerCopy` never does (`aSendAloneNoLongerMarksTheNextCopyAsItsOwn`).
- **Koin.** `FeedPuller` and the runner get appliers and `WalletScopes` through lambdas (`getAll`/`getOrNull` on use). `AppGraphTest` asserts eight appliers (one per consumed pair), one `WalletScopes`, the runner, the puller and the sign-out cleaner.
- **Retired** (grep finds no caller): `WalletApi.list/get/members`, `WalletRepository.refreshAll/refreshOne/refreshMembers`, `CategoryApi.list`, `CategoryRepository.refreshOwn`, `CategoryReferenceApi` (file deleted) and `CategoryReferenceRepository.refresh`, `TransactionApi.list`, `TransactionRepository.refreshWallet` and `WalletRead`, `AccountApi.listSessions`, `DeviceSessionRepository.fetchPage/store`, the global `clearCarryUpTo` / `clearCarryConfirmedUpTo`.
- **Tests edited or rewritten because reads moved to feeds** (everything else passed unmodified, no golden changed): `WalletApiContractTest`, `CategoryApiContractTest`, `TransactionApiContractTest`, `SessionsContractTest` (list cases dropped; their feed counterparts are in `SyncApiContractTest`), `WalletRepositoryTest`, `CategoryRepositoryTest`, `CategoryReferenceRepositoryTest`, `TransactionRepositoryTest` (refresh cases replaced by the applier tests), `CategoryCapturedResponsesTest` and `TransactionCapturedResponsesTest` (real list answers now go through the appliers; the wallet read the reconciler asked for is the runner's `onRefresh`), `WalletsServiceListTest`, `WalletsSyncCoordinatorTest`, `CategoriesServiceRefreshTest`, `CategoryReferencesRefreshTest`, `CategoryResolverTest`, `TransactionsServiceRefreshTest`, `TransactionsServiceEditDeleteTest.useTheirs…`, `TransactionsSyncCoordinatorTest`, `TransferOwnershipTest`, `SessionRefresherTest`, `ChangePasswordContractTest`, `TransactionSyncHandlerTest` (own-answer path), `TransactionDaoTest` (per-wallet carry), `SyncDrainWorkerTest` (runner), the four `Migration*Test` chains, `SignOutWipeTest`, `AppGraphTest`, the three `*LayeringTest`s. `BalanceReconcilerTest` was replaced by `BalanceReconcilerFeedTest`.
- **Test fixtures.** `FakeSyncRunner`, `FakeSyncApi`, `RecordingApplier`, `FakeWalletScopes` (`core/sync`), `BatchHarness`/`BatchHandler` (engine over fakes with batch items), `OpenApiSyncExamples` (the sync.yaml examples). `runTest`'s `advanceUntilIdle` does not wait for `backgroundScope` work, so runner tests give the runner a scope on the test dispatcher.
- **Contract recheck (T072).** `check-backend-contract.sh` reports the client codes consistent with the catalogue. Against `saveme-backend@6b8e290` the working tree differs from the baseline only by the v1.9 `feature` member on `FEATURE_NOT_ENTITLED` problems, `invitation` rows and membership causes (all in spec §5); nothing new. Two cursor codes were added to `ApiErrorCode` (and to the exhaustive `when` in `AuthErrorCopy`, generic reference copy).
- **Maestro (not run, owner policy).** `.maestro/009-sync-feed-batch/`: `US1-checkpoint-backlog` (20 offline changes: 2 edits, 9 expenses, a wallet and 8 expenses in it), `US1-checkpoint-conflict`, `US1-checkpoint-kill-mid-sync` (kill and relaunch with the connection back), `US3-wallet-from-second-session`. Each starts with `# deferred: E2E on owner's device`. Static review: every `id:` selector exists as a `testTag` (`transactions.list`, `txEditor.amount/save/keepMine`, `walletDetail.transactions`, plus the shared subflows').
- **QA (T067–T069, qa-report.md).** Live contract QA at `saveme-backend@e4dc4c6` (sharing committed; `sync.yaml` v1.9, nothing beyond spec §5). Real answers in `CapturedSyncResponses.kt` (55 answers, 3 request bodies), replayed through the real `HttpSyncApi` by `CapturedSyncServer` (routes by scope and position) in `SyncCapturedResponsesTest` and in `FeedCapturedPagesTest` (its OpenAPI inputs replaced). Two client fixes: (1) **a wallet copy settles the carries it already counts** — `TransactionRepository.storeServerCopy(own)` adds no source carry when the stored wallet copy's `change_seq` is at or past the change's, and storing a wallet copy (own answer or feed) clears the source carry of rows at or below its position (`WalletRepository.onWalletCopy` → `WalletCopyParticipant`, implemented by `TransactionsWalletCleanup`; `TransactionDao.clearSourceCarryCoveredBy`); a transfer's destination part still waits for the destination's read. Found when the live replay of a lost upload answered the wallet with the expense already in its balance (shown 75,000 instead of 87,500 until the next read). (2) **a read from the start that gets 410 for its own next page ends `Failed`** without a second full pass (backend bug, qa-report.md §5.1: the server expires its own `next_cursor` below a purge horizon). The spec 008 captured test's wallets now sit at the live position before its first transaction (`walletJson(changeSeq = …)`).
- **Left to the QA step:** T067 (live curl QA, `qa-report.md`), T068 (`CapturedSyncResponses.kt`), T069 (`SyncCapturedResponsesTest`, and replacing the OpenAPI inputs of `FeedCapturedPagesTest` with captured pages). T071 is deferred to the owner's device. T075 (`summary.md`) is the parent's.
- **Gates after QA.** The same four tasks with `--rerun-tasks`: 1,480 host tests, 0 failed; no golden changed.
- **Gates (T074).** `:shared:testAndroidHostTest` 1,459 tests (baseline 1,293), 0 failed; `:shared:verifyRoborazziAndroidHostTest` green with no golden changed; `:androidApp:assembleDevDebug` and `:shared:compileKotlinIosSimulatorArm64` green. `shared/schemas/…/6.json` is exported.
