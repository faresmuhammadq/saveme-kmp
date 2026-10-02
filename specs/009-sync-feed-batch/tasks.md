---
description: "Task list for 009-sync-feed-batch"
---

# Tasks: Sync Feeds and Batch Upload (client)

**Input**: Design documents from `specs/009-sync-feed-batch/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (sync-engine-v2.md, sync-api-client.md), quickstart.md; constitution v1.3.0; `main` `ec5e5a1` (spec 004 engine, 005 entitlements, 006 wallets, 007 categories, 008 transactions; Room v5); backend contract `saveme-backend@6b8e290`

**Tests**: Included. P-14 requires contract tests per endpoint and handled code and a test per invariant (spec §4). Write each story's tests first and confirm they fail. No screen changes, so no screenshot or MVI tests are added; existing goldens must stay identical (SC-008).

**Testing policy (owner decision)**: no emulator, Maestro or device runs. Flows are written under `.maestro/009-sync-feed-batch/` and every task that runs them is marked **deferred: E2E on owner's device**. A host-only QA (Phase 8) checks every call against the live local backend with `curl` and turns the real answers into fixtures, as specs 007 and 008 did.

**Organization**: Phase 2 builds the wire client, storage, core types, the puller and the runner. US1 (batch upload) and US2 (feeds) are the MVP pair; US3 adds wallets gained and lost; US4 recovery (410, 400, first run); US5 fallback and resume.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); sync core `cm/core/sync/…` (`cs/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); sync core tests `ct/core/sync/…` (`ts/…`)
- Host (JVM) tests: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)

## Standing rules for every task

- Layering (P-10): `core.sync` imports no feature; features implement `SyncHandler`, `FeedApplier`, `WalletScopes`; feature `data` imports no domain service, ui or other feature (`*LayeringTest` stay green).
- Contract (P-02): only declared members on the wire; unknown members and unknown row types ignored; branch on `code` only.
- Never log payloads, amounts, notes or cursor values; log type, id, outcome, code, counts and request id (P-09.2, FR-030).
- **Koin (spec 006 lesson)**: anything a handler or applier depends on resolves `SyncEngine`/`SyncRunner` lazily; `ht/core/di/AppGraphTest.kt` must stay green.
- **Parity rule**: `settle()` in `cs/SyncEngine.kt` is not changed in meaning; every item answer goes through it.
- **Merge hotspots**: `AppDatabase.kt`, `Migrations.kt`, `SyncEngine.kt`, `CoreModule.kt`, the four feature modules and services are edited additively where possible; retired functions are deleted together with the tests that asserted them (rewritten to the feed path).

---

## Phase 1: Setup

**Purpose**: confirm prerequisites; codes and storage.

- [X] T001 Confirm on `009-sync-feed-batch` (from `main` `ec5e5a1`): `AppDatabase` version 5 with `MIGRATION_4_5` and `shared/schemas/…/5.json`; `SyncEngine.settle()` as in spec 004 contracts; `SyncHandler.canRestore`; `WalletRepository.remove` calling `WalletDeletionParticipant`s; `TransactionsWalletCleanup`, `CategoryReferencesCleanup`; `BalanceReconciler`; `ht/core/di/AppGraphTest.kt`; the backend dev container answers `GET /api/v1/sync/user/changes` with `meta.sync_cursor`. If any is missing, stop and report
- [X] T002 [P] Add `INVALID_CURSOR(RetryClass.AfterChange)` and `SYNC_CURSOR_EXPIRED(RetryClass.AfterChange)` to `cm/core/network/ApiErrorCode.kt` (FR-032) with a test in `ct/core/network/ApiErrorCodeTest.kt` (create if absent) that both decode from their wire code and are never `Automatic`; run `.specify/scripts/bash/check-backend-contract.sh` and confirm "Consistent"
- [X] T003 Add `@ColumnInfo(name = "feed_cursor") val feedCursor: String? = null` and `@ColumnInfo(name = "feed_read_at") val feedReadAt: String? = null` to `SyncScopeEntity` in `cm/core/database/SyncScopeEntity.kt`, `MIGRATION_5_6` (`ALTER TABLE sync_scope ADD COLUMN feed_cursor TEXT`, `ALTER TABLE sync_scope ADD COLUMN feed_read_at TEXT`, nothing else) in `cm/core/database/Migrations.kt`, `version = 6` and `.addMigrations(…, MIGRATION_5_6)` in `cm/core/database/AppDatabase.kt`; build to export `shared/schemas/com.fares.saveme.core.database.AppDatabase/6.json`
- [X] T004 Write `ht/core/database/Migration5To6Test.kt` (a v5 file with rows in every table, incl. `sync_scope` with `last_pushed_at` and a `pending_change` row, migrates to v6 with all data intact and `feed_cursor`/`feed_read_at` null — FR-029) and register `MIGRATION_5_6` in `MigrationTest.kt`, `Migration2To3Test.kt`, `Migration3To4Test.kt`, `Migration4To5Test.kt` so their chains reach v6

---

## Phase 2: Foundational (blocking)

**Purpose**: wire client, core types, DAO, puller, runner, wiring.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T005 [P] `ts/SyncApiContractTest.kt` on Ktor `MockEngine`: user and wallet feed requests (path, `limit=200`, `cursor` only when stored, standard headers), decoding the `userFeedPage` and `walletFeedPage` OpenAPI examples into `FeedPageDto` with `record` as raw JSON, an unknown meta member ignored; every feed refusal the feature handles decodes to its `ApiErrorCode` (`400 INVALID_CURSOR`, `400 INVALID_QUERY_PARAMETER`, `410 SYNC_CURSOR_EXPIRED`, `404 NOT_FOUND`, `422 INVALID_ID`, `401`, `426`, `429` with `Retry-After`, `500`); batch request-level `422 VALIDATION_FAILED` (`OUT_OF_RANGE` at `/items`), `400 MALFORMED_JSON`, `415`, `404` decode likewise (P-14.3); batch request body shape (`items`, `wallet_id` omitted when null, no `Idempotency-Key`), decoding `mixedResults` and `validationRefusal`; the batch call's request timeout is 60 s while others keep 30 s (FR-023, contracts/sync-api-client.md)
- [X] T006 [P] `ht/core/database/SyncScopeDaoTest.kt`: store and read a scope's cursor and read stamp without touching `refreshedAt`/`last_pushed_at`; `clearFeed(scope)` nulls both; rows created by `markPushed` keep a null cursor; sign-out `deleteAll` removes positions (FR-003, FR-028)
- [X] T007 [P] `ts/FeedPullerPagingTest.kt` with fake `SyncApi` and fake appliers: rows reach appliers in feed order with consecutive same-type rows grouped; unknown types (`budget`, `invitation`, `recurring_rule`) skipped and counted while the position still advances; position stored only after a page's rows are applied (an applier throwing mid-page leaves the previous position); `has_more` paging; empty last page stores `sync_cursor`; `feed_read_at` = the read's start time, only on completion; offline mid-read → `Offline` with the last applied position (FR-003, FR-006, FR-027)
- [X] T008 [P] `ts/SyncRunnerTest.kt`: requests during a run merge into one next run and every caller gets its run's outcome; upload runs before reads; a run whose upload found no connection skips reads and reports `Offline`; the per-target floor (60 s) applies unless `force`; outcome combination rule (all done → `Done`, any unreachable → `Offline`, else `Failed`) (FR-014, FR-015, FR-026)

### Implementation

- [X] T009 [P] Create `cs/SyncApi.kt` with `ChangeRowDto`, `FeedMetaDto`, `FeedPageDto`, `BatchItemDto`, `BatchRequestDto`, `BatchItemResultDto` (data-model §2) and `userChanges(cursor: String?, limit: Int = 200)`, `walletChanges(walletId, cursor, limit = 200)`, `batch(items)` returning `ApiResult`; the batch call sets `timeout { requestTimeoutMillis = 60_000 }` per request; item problems publish the same `ApiEvent`s as `ApiClient.publishEvents` (`EMAIL_NOT_VERIFIED`, `FEATURE_NOT_ENTITLED`) (FR-023, FR-033)
- [X] T010 [P] Create `cs/BatchItem.kt` (`BatchItem`, `BatchOp { Upsert, Delete }`, `RecordKey`), `cs/FeedApplier.kt` (`scopeKind`, `rowType`, `apply(scope, records)`, `sweep(scope, seenIds)`) and `cs/WalletScopes.kt` (`followed()`, `isAccepted(id)`, `unconfirmed(seenWalletIds)`, `accessLost(id)`) exactly as contracts/sync-engine-v2.md
- [X] T011 Add `batchItem(change): BatchItem? = null`, `prerequisites(change): List<RecordKey> = emptyList()` and `applySentAnswer(json) = applyServerCopy(json)` to `cs/SyncHandler.kt`; the engine's success path calls `applySentAnswer` (existing engine tests stay green unchanged)
- [X] T012 Add `feedCursor(scope)`, `storeFeedPage(scope, cursor)`, `markFeedRead(scope, startedAt)`, `clearFeed(scope)` and `observeFeedReadAt(scope)` to `SyncScopeDao` in `cm/core/database/SyncScopeEntity.kt` (insert-or-update keeping other columns; fixed-width UTC stamps) (T006)
- [X] T013 Create `cs/SyncLock.kt` (one `Mutex` shared by the engine's drain and the puller) and take it in `SyncEngine.drain()` in place of the private `draining` mutex
- [X] T014 Create `cs/FeedPuller.kt`: `read(scope): ScopeRead` per contracts/sync-engine-v2.md (paging from the stored cursor, ordered grouped dispatch to `getAll<FeedApplier>()` by `(scopeKind, rowType)`, skip-and-count unknown types, position after each page, `feed_read_at` on completion, collect seen ids and call every applier's `sweep` of that scope kind after a complete read that began without a cursor; one log line per read with rows, skipped types and outcome) (T007, FR-003, FR-006, FR-007)
- [X] T015 Create `cs/SyncRunner.kt` (`ReadTarget { Everything, User, Wallet(id) }`, `ReadOutcome { Done, Offline, Failed }`, `refresh(target, force): ReadOutcome`, `runBackground()`, `requestUpload()`; merge queue; run = `drain()` → user scope when `Everything`/`User` or the user scope was written → followed wallets for `Everything`, else targeted ∪ written wallet scopes; 60 s floor per scope unless `force`; outcome rule). `core` never imports `RefreshOutcome` (P-10.7): services map `ReadOutcome` (T008, FR-014, FR-015, FR-026)
- [X] T016 Wire in `cm/core/di/CoreModule.kt`: `SyncApi`, `SyncLock`, `FeedPuller(getAll<FeedApplier>(), …, walletScopes = lazy)`, `SyncRunner`; change `SyncDrainWorker` in `shared/src/androidMain/kotlin/com/fares/saveme/core/sync/SyncDrainWorker.kt` to call `SyncRunner.runBackground()`; update `ht/core/sync/SyncDrainWorkerTest.kt`; `AppGraphTest` resolves `SyncRunner` and `FeedPuller` (FR-027)

**Checkpoint**: feeds and batch can be called and read; no feature is switched yet.

---

## Phase 3: User Story 1 - A backlog of offline changes leaves quickly and in order (Priority: P1) 🎯 MVP

**Goal**: batch upload as the default drain path with parity, order, dependencies and request-level handling.

**Independent Test**: 20 waiting changes (new wallet, new category, transactions in both, two edits) leave in ≤ 2 batch requests on a mock engine and settle exactly as their single writes; none is refused for a missing prerequisite.

### Tests (write first, confirm they fail)

- [X] T017 [P] [US1] `ts/SyncEngineBatchTest.kt`: due rows packed oldest first, ≤ 50 per request (51 → two requests); rows marked `Sending` before the request; `applied` → row cleared and `applySentAnswer` called, `last_pushed_at` set; edited during the request → rebased on the returned version and waiting (revision guard); `conflict` → `Clash` with `current`; `rejected` 422 → `Refused` with mapped fields; `rejected` 500 → waiting with backoff; one item's clash leaves the others' outcomes untouched (FR-017, FR-019, US1-S1, S4, S5)
- [X] T018 [P] [US1] `ts/SyncEngineBatchOrderTest.kt`: a row whose handler returns `batchItem = null` flushes the batch before it, is sent per record, and batching resumes after it (creation order kept across both paths); an archive and a restore in the middle of 10 edits keep their place (FR-018, US1-S8)
- [X] T019 [P] [US1] `ts/SyncEngineDependencyTest.kt`: a transaction whose wallet create (`base_version = 0`) is earlier in the same request is sent after it; one whose wallet create is `Clash`/`Refused`/backing off/later in the queue is held (stays `Waiting`, `attempts` unchanged, no `next_attempt_at`); a transfer whose destination create waits is held; a transaction whose category create waits in the user scope is held; a dependent whose prerequisite item in the same request was not `applied` returns to `Waiting` with its answer ignored; once the prerequisite is accepted the engine schedules a run and the dependent is sent; a transaction edited to use a category created later is held one run (FR-022, US1-S2, S3, S6)
- [X] T020 [P] [US1] `ts/SyncEngineBatchRequestTest.kt`: request-level `Offline`/`Timeout` → every item settles as offline/timeout and the run stops; `401` → items unchanged; `429` with `Retry-After`, `500`, `503` → every item backs off honouring the wait; `426` → every item as the single write's `CLIENT_VERSION_UNSUPPORTED`; a timed-out batch is resent next run with the same items (FR-020, FR-023, US1-S7)
- [X] T021 [P] [US1] Handler item tests: `ct/feature/wallets/data/WalletSyncHandlerBatchTest.kt`, `ct/feature/categories/data/CategorySyncHandlerBatchTest.kt`, `ct/feature/transactions/data/TransactionSyncHandlerBatchTest.kt`, `ct/feature/account/data/ProfileSyncHandlerBatchTest.kt` — for every change shape the item's `data` is byte-equal to the body the per-record `send()` sends (stored kind, transfer destination, currency of an accepted transaction vs a create's, explicit nulls); `wallet_id` only on transactions (source, or destination when the source is gone); profile id; archive / archive + edit / restore → `null`; transactions' `prerequisites` list source, destination and category (FR-017, FR-024)
- [X] T022 [P] [US1] `ts/SyncEngineBatchEventsTest.kt`: an item refused `EMAIL_NOT_VERIFIED` or `FEATURE_NOT_ENTITLED` publishes the same `ApiEvent` as the single write (FR-033)
- [X] T023 [P] [US1] `ts/SyncEngineParityTest.kt`: the same answers (OpenAPI `mixedResults` and `validationRefusal` items vs their per-record equivalents) driven once through the batch path and once through per-record `send()` leave identical `pending_change` rows, copies and status (FR-019)

### Implementation

- [X] T024 [US1] Rewrite `SyncEngine.drain()` in `cs/SyncEngine.kt` per research R-05/R-06/R-07: walk due rows, hold rows with unaccepted prerequisites not earlier in the request, pack items (≤ 50), flush around per-record rows, mark `Sending`, call `SyncApi.batch`, map each result to `ApiResult` and call `settle()`; request-level answers mapped to every item; ignore-and-return dependents; return `DrainReport(written, appliedAny)`; schedule a run after any applied item when held rows remain; one log line per batch (count, request id) and per item (type, id, outcome, code) (T017–T020, T022)
- [X] T025 [P] [US1] `WalletSyncHandler.batchItem` in `cm/feature/wallets/data/WalletSyncHandler.kt` (`upsert` with `WalletWriteRequest` when `!draft.archive`, `delete` with `{version}`, `null` when `archive`) (T021)
- [X] T026 [P] [US1] `CategorySyncHandler.batchItem` in `cm/feature/categories/data/CategorySyncHandler.kt` (`upsert` with the stored kind when `!payload.archive`, `delete` with `{version}`, `null` when `archive`) (T021)
- [X] T027 [P] [US1] `TransactionSyncHandler` in `cm/feature/transactions/data/TransactionSyncHandler.kt`: `batchItem` (`upsert` reusing `writeRequest`, `delete`, `null` when `restore`; `walletId` = the existing path-wallet rule), `prerequisites` (source wallet, destination wallet, category), `applySentAnswer` → `repository.storeServerCopy(dto, own = true)` replacing the `ownAnswers` set (T021; `TransactionPostingsTest` stays green)
- [X] T028 [P] [US1] `ProfileSyncHandler.batchItem` in `cm/feature/account/data/ProfileSyncHandler.kt` (`upsert`, id = the profile row id, `ProfileWriteRequest`) (T021)
- [X] T029 [US1] Route feature saves through `SyncRunner.requestUpload()` instead of launching `engine.drain()` directly (`DefaultWalletsService`, `DefaultCategoriesService`, `DefaultTransactionsService`, `ProfileSyncer.saveLocally`), keeping "a save with the app open is sent at once" (spec 004 note); existing service tests updated only for the injected runner
- [X] T030 [US1] Run the US1 tests and the full spec 004 engine suite (`ts/SyncEngine*Test.kt`) green; record any test that asserted per-record-only sending and was rewritten in plan.md "Implementation notes"

**Checkpoint**: uploads go through the batch; everything a person sees is unchanged.

---

## Phase 4: User Story 2 - The phone stays current by downloading only what changed (Priority: P1)

**Goal**: appliers for every consumed kind; services read through the runner; carry settles per wallet read; retired reads removed.

**Independent Test**: on a mock engine, a first read fills every table; a second read with one changed transaction applies one row (plus the wallet record) and nothing else; balances never jump across an upload → read sequence.

### Tests (write first, confirm they fail)

- [X] T031 [P] [US2] `ct/feature/account/data/AccountFeedAppliersTest.kt`: `profile` row applied when `version ≥ stored`, ignored when lower; app language follows only with no profile edit waiting; `device_session` active rows upserted, non-`active` or deleted rows removed, sweep removes unseen (FR-004, FR-005, research R-13)
- [X] T032 [P] [US2] `ct/feature/categories/data/CategoryFeedAppliersTest.kt`: `category` version guard, tombstone removes unless a queued change exists, sweep removes unseen synced rows but never placeholders or queued; `category_reference` upsert/delete per wallet and sweep per wallet (FR-004, FR-005, FR-007)
- [X] T033 [P] [US2] `ct/feature/wallets/data/WalletFeedAppliersTest.kt`: `wallet` applied when `version ≥ stored` incl. equal version with a new balance, `currency_locked` and `my_role`; lower ignored; a `wallet` tombstone removes through `remove()` unless a queued change exists; wallet-scope `wallet_member` upsert/delete and sweep per wallet (FR-004, FR-005, FR-013)
- [X] T034 [P] [US2] `ct/feature/transactions/data/TransactionFeedApplierTest.kt`: version guard; a transfer from either wallet's feed stored once and listed under both; a tombstone removes the row unless a queued change exists or the row carries an unsettled carry (kept hidden with `deleted_at`); feed rows never add a carry; sweep per wallet touches only rows touching that wallet and keeps queued, placeholder and carried rows (FR-005, FR-007, FR-012)
- [X] T035 [P] [US2] `ct/feature/transactions/domain/BalanceReconcilerFeedTest.kt` (rewrite of the wallet-read cases in `BalanceReconcilerTest.kt`): after an accepted send the reconciler requests reads of the touched wallets; a wallet read that started after confirmation clears that wallet's part of the carry only (source part for the source, counter part for the destination); a failed read keeps it; the displayed balance never jumps back or double counts across upload → page 1 (tombstone) → page 2 (wallet record) (FR-013, SC-005)
- [X] T036 [P] [US2] `ht/feature/transactions/data/TransactionDaoTest.kt` (new cases): `clearCarryFor(walletId, upTo)` clears only that wallet's part, clears `carry_confirmed_at` when both parts are zero, and purges settled hidden deletions
- [X] T037 [P] [US2] Service tests rewritten to the runner: `ct/feature/wallets/domain/WalletsServiceListTest.kt`, `ct/feature/categories/domain/CategoriesServiceRefreshTest.kt`, `ct/feature/transactions/domain/TransactionsServiceListTest.kt`, `ct/feature/account/domain/SessionRefresherTest.kt` — each `refresh(...)` asks the runner for the right target (`Everything` for wallets, `User` for categories and sessions, `Wallet(id)` for a wallet's transactions and members) with the same `force` as today and maps `ReadOutcome` to `RefreshOutcome` as before; "Updated …" reads `feed_read_at` and falls back to the legacy stamp while null; platform defaults still read from `GET /platform-categories` (FR-014, FR-015, FR-016, research R-11)
- [X] T038 [P] [US2] `ts/FeedCapturedPagesTest.kt`: OpenAPI feed examples (later replaced by captured pages, T069) through the real puller and every applier on fakes leave the expected tables; a second identical page changes nothing (FR-004)

### Implementation

- [X] T039 [P] [US2] Version-guarded store functions: `storeFeedCopy` (or a `guard` parameter) in `ProfileRepository`, `CategoryRepository`, `WalletRepository` (+ member upsert/delete), `TransactionRepository` (+ `clearCarryFor(walletId, upTo)` and its DAO queries), `CategoryReferenceRepository` (upsert/delete one), device sessions; each skips a lower version and never touches `pending_change` (T031–T036)
- [X] T040 [P] [US2] `cm/feature/account/data/ProfileFeedApplier.kt` and `DeviceSessionFeedApplier.kt`; register in `cm/feature/account/di/AccountModule.kt` (T031)
- [X] T041 [P] [US2] `cm/feature/categories/data/CategoryFeedApplier.kt` (user) and `CategoryReferenceFeedApplier.kt` (wallet); register in `CategoriesModule.kt` (T032)
- [X] T042 [P] [US2] `cm/feature/wallets/data/WalletFeedApplier.kt` and `WalletMemberFeedApplier.kt` (wallet scope); register in `WalletsModule.kt` (T033)
- [X] T043 [P] [US2] `cm/feature/transactions/data/TransactionFeedApplier.kt`; register in `TransactionsModule.kt` (T034)
- [X] T044 [US2] Rewire services to `SyncRunner`: `WalletsService.refresh(force)` and `refreshMembers(id)`, `CategoriesService.refresh(force)` (runner + defaults read) and `refreshReferences(walletId, force)`, `TransactionsService.refresh(walletId, force)`, `AccountRefresher`/`SessionRefresher`, and the four sync coordinators (account, wallets, categories, transactions); "Updated …" flows read `observeFeedReadAt` with the legacy fallback (T037)
- [X] T045 [US2] `BalanceReconciler` in `cm/feature/transactions/domain/BalanceReconciler.kt`: request `Wallet(id)` reads for touched wallets, settle per wallet from each read's start (`clearCarryFor`), `requestWalletRead()` → runner read of the wallet(s) concerned (T035)
- [X] T046 [US2] Delete the retired reads and their tests' assertions (rewritten to the feed path): `WalletApi.list/get/members`, `WalletRepository.refreshAll/refreshOne/refreshMembers`, `CategoryApi.list`, `CategoryRepository.refreshOwn`, `CategoryReferenceApi` + `CategoryReferenceRepository.refresh`, `TransactionApi.list`, `TransactionRepository.refreshWallet`, `AccountApi.listSessions`; grep proves no caller remains (FR-016, research R-14)
- [X] T047 [US2] Run every host test (`./gradlew :shared:testAndroidHostTest`) and `verifyRoborazziAndroidHostTest`: no golden changes (SC-008); list in plan.md "Implementation notes" each pre-existing test edited and why

**Checkpoint**: the phone reads only changes; screens behave as before.

---

## Phase 5: User Story 3 - Wallets the person gains or loses follow them (Priority: P1)

**Goal**: own memberships on the user feed drive which wallets are read and which leave.

**Independent Test**: a user page with a new live own membership makes the same run read that wallet from the start; an ended one removes the wallet, its members, references, transactions touching no kept wallet and their queued changes; a placeholder is never removed.

### Tests (write first, confirm they fail)

- [X] T048 [P] [US3] `ct/feature/wallets/data/OwnMembershipFeedApplierTest.kt`: live row upserted into `wallet_member`; ended row → `remove(walletId)` with participants (references, transactions, queued changes discarded) and the wallet scope's position cleared; a placeholder wallet untouched; a wallet whose own record has a queued edit stays (not followed) and its later "Accept the deletion" or discard removes it with its data; a role change updates the member row (FR-010, FR-011)
- [X] T049 [P] [US3] `ct/feature/wallets/domain/DefaultWalletScopesTest.kt`: `followed()` = wallets with `version > 0` ∪ live own memberships, never placeholders; `unconfirmed(seen)` lists held wallets whose membership was not seen; `accessLost` removes and clears the position (FR-002, FR-011)
- [X] T050 [P] [US3] `ts/FeedPullerWalletsTest.kt`: a run for `Everything` reads the user scope, then every followed wallet incl. ones gained in this run from the start; a wallet feed `404` for an accepted wallet calls `accessLost`; a wallet only missing from a complete cursorless user read is read, kept on `200` and removed on `404`; a `wallet_member` tombstone on a wallet feed for another member only removes that member (FR-002, FR-010, FR-011, US3-S1–S4)
- [X] T051 [P] [US3] `ct/feature/transactions/domain/TransactionsWalletCleanupFeedTest.kt`: losing wallet B keeps a transfer A→B (and its queued change) because it touches kept wallet A; transactions touching only B and their queued changes go (Clarification 1)

### Implementation

- [X] T052 [US3] `cm/feature/wallets/data/OwnMembershipFeedApplier.kt` (user scope `wallet_member`) and `cm/feature/wallets/domain/DefaultWalletScopes.kt`; register both in `WalletsModule.kt`; `WalletSyncHandler.onDiscarded` removes a wallet with no live own membership; the puller uses `WalletScopes` for followed wallets, 404, and the post-sweep `unconfirmed` reads (T048–T050)
- [X] T053 [US3] Confirm `TransactionsWalletCleanup` and `CategoryReferencesCleanup` run on the membership path and clear queued rows from a launched job (spec 008 R-19 lock note) (T051)

**Checkpoint**: gained and lost wallets follow the person within one refresh (SC-006).

---

## Phase 6: User Story 4 - A phone offline for a very long time recovers on its own (Priority: P2)

**Goal**: 410 and 400 INVALID_CURSOR recovery per scope; first run after the update.

**Independent Test**: a mock `410` on one wallet re-reads that wallet from the start in the same run, sweeps what the server no longer has, keeps queued changes and their rows, and leaves other scopes untouched.

### Tests (write first, confirm they fail)

- [X] T054 [P] [US4] `ts/FeedPullerRecoveryTest.kt`: `410` on wallet A → A's position cleared, A read without cursor and swept, wallet B and the user scope untouched, queued changes in A kept with their rows; `410` on the user scope → user scope re-read, wallets not swept by it except through FR-011 confirmation; `400 INVALID_CURSOR` → cleared and re-read once, a second one → `Failed` with the copy unchanged; an interrupted cursorless read sweeps nothing and leaves the position empty (FR-007, FR-008, FR-009, US4-S1–S3)
- [X] T055 [P] [US4] `ts/FirstRunAfterUpdateTest.kt`: a v6 database migrated with data and no positions → the first `Everything` run reads every scope from the start, screens keep their rows while it runs, the sweep removes only unseen synced rows without queued changes, and "Updated …" shows the legacy stamp until the read completes (FR-007, FR-029, US4-S4)
- [X] T056 [P] [US4] `ht/core/sync/FeedSweepRoomTest.kt`: the sweep queries on real Room tables never remove a row with a `pending_change`, a placeholder or a carried transaction (FR-007)

### Implementation

- [X] T057 [US4] `FeedPuller`: `410` and `400 INVALID_CURSOR` handling per data-model §4 (once per run for 400; logged with request id); `INVALID_QUERY_PARAMETER`/`422 INVALID_ID` → `Failed`, logged (T054)
- [X] T058 [US4] Sweep implementations in each applier (T040–T043) use the guarded DAO deletes that exclude rows with a queued change, placeholders and carried rows (T055, T056)

**Checkpoint**: a long-offline phone recovers per scope (SC-007).

---

## Phase 7: User Story 5 - Sync keeps working when the batch upload can't be used (Priority: P3)

**Goal**: per-record fallback and process-death resume.

**Independent Test**: a mock batch endpoint answering `404` makes the same run send the changes per record with the same outcomes; a run killed mid-upload or mid-read resumes.

### Tests (write first, confirm they fail)

- [X] T059 [P] [US5] `ts/SyncEngineFallbackTest.kt`: request-level `422 VALIDATION_FAILED`, `400 MALFORMED_JSON`, `415`, `406`, `404`, `405`, an undecodable `200`, and a `200` whose results don't match the items (count, order, id, type) → the same rows sent per record in the same order in this run; the next run tries the batch again (FR-021, US5-S1)
- [X] T060 [P] [US5] `ts/SyncResumeTest.kt`: rows left `Sending` by a killed batch return to `Waiting` and are resent (spec 004 reset); a read killed after page 2 of 4 resumes from page 2's position and the guard leaves no older copy over a newer one (FR-027, US5-S2)

### Implementation

- [X] T061 [US5] Fallback branch in `SyncEngine.drain()` (`cs/SyncEngine.kt`), logged with the request id (T059)
- [X] T062 [US5] Confirm the resume paths need no code beyond T014/T024 (stale `Sending` reset, position after page); fix and note in plan.md if they do (T060)

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T063 [P] `ts/SyncLoggingFeedBatchTest.kt`: log capture over a batch (applied, conflict, rejected) and a feed read with transactions and a profile: no payload, amount, note, display name or cursor value appears; type, id, outcome, code, counts and request id do (FR-030, P-09.2)
- [X] T064 [P] Extend `ht/feature/account/SignOutWipeTest.kt`: positions and read stamps are gone after sign-out; a run in progress at sign-out sends and applies nothing afterwards (FR-028)
- [X] T065 [P] Extend `ht/core/di/AppGraphTest.kt`: one `FeedApplier` per consumed (scope, type) pair (8), one `WalletScopes`, the runner and puller resolve without a cycle
- [X] T066 [P] Extend the `*LayeringTest`s (`ht/feature/wallets/ui/WalletsLayeringTest.kt`, `CategoriesLayeringTest.kt`, `TransactionsLayeringTest.kt`) and add a check that `cm/core/sync/**` imports no `feature` package (P-10.7)
- [X] T067 Contract QA against the live local backend per quickstart.md §2 (curl with the app's headers and bodies): both feeds incl. paging, empty page, transfer on both feeds, wallet re-sent, own membership live and ended; batch mixed answers, replay, stale conflict, validation pointer, unknown wallet `404`, envelope `422`; `400 INVALID_CURSOR`; wallet feed `404`; `410` via aged tombstones and `sync:purge-tombstones` on the dev stack (backend QA method). Record results in `specs/009-sync-feed-batch/qa-report.md`; report any drift as a backend contract gap — **QA step**
- [X] T068 Save the real answers from T067 as `ts/CapturedSyncResponses.kt` — **QA step**
- [X] T069 `ts/SyncCapturedResponsesTest.kt`: captured pages and batch answers through the real engine, puller, handlers and appliers (on fakes for Room), incl. the parity check of T023 with real bodies; replace the OpenAPI-example inputs of T038 with captured pages — **QA step**
- [X] T070 [P] Write Maestro flows under `.maestro/009-sync-feed-batch/` for the brief's checkpoint (20 offline edits across entities sync in order after reconnecting; a forced conflict shows the choice; kill the app mid-sync and it resumes) and US3 (wallet created then deleted from an API session appears and leaves within one refresh); each flow starts with `# deferred: E2E on owner's device`; static review that every `id:` selector exists as a `testTag`
- [ ] T071 Run the flows of T070 and the 004–008 regression flows — **deferred: E2E on owner's device**
- [X] T072 Re-run `.specify/scripts/bash/check-backend-contract.sh`; if `sync.yaml` changed since `6b8e290`, map each change to an FR or a follow-up in spec §5
- [X] T073 [P] README "On-device database": note v6 and that sync reads changes since the last position per scope (one sentence; no new section)
- [X] T074 Full gates: `./gradlew :shared:testAndroidHostTest --rerun-tasks`, `:shared:verifyRoborazziAndroidHostTest`, `:androidApp:assembleDevDebug`, `:shared:compileKotlinIosSimulatorArm64`; record counts and the implementation notes in plan.md
- [X] T075 Write specs/009-sync-feed-batch/summary.md (parent writes it)

---

## Dependencies & Execution Order

- **Phase 1** → **Phase 2** → stories. T003 before T004, T006, T012; T009–T013 before T014–T016.
- **US1** (Phase 3) and **US2** (Phase 4) both depend only on Phase 2 and can run in parallel; T029 (saves → runner) and T044 (services → runner) touch the same services: do T029 first.
- **US3** depends on US2's wallet appliers (T042) and the puller (T014).
- **US4** depends on the appliers' sweeps (T040–T043) and the puller.
- **US5** depends on T024.
- **Phase 8**: T067–T069 after US1–US4; T074 last before T075.

## Parallel examples

- After T016: T017–T023 (US1 tests) ∥ T031–T038 (US2 tests).
- US1 handlers T025–T028 in parallel (four files).
- US2 appliers T040–T043 in parallel (four feature packages).

## Implementation strategy

1. MVP = Phases 1–4: the engine uploads in batches and reads feeds; nothing visible changes.
2. Then US3 (privacy: lost wallets leave), US4 (recovery), US5 (fallback).
3. Polish: logs, wipe, graph, layering, live contract QA with captured fixtures; device E2E deferred.
