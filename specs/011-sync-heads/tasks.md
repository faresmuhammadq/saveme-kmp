---
description: "Task list for 011-sync-heads"
---

# Tasks: Sync Heads Adoption (client)

**Input**: Design documents from `specs/011-sync-heads/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/sync-heads-client.md, quickstart.md; constitution v1.3.0; `main` `8f7b308` (specs 009 engine with feeds + batch, 010 recurring; Room v7); backend contract `saveme-backend@192e3ab` (`sync.yaml` v1.10)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code and a test per invariant (spec §4). Write each story's tests first and confirm they fail. No screen changes, so no screenshot or MVI tests are added; existing goldens must stay identical (SC-008).

**Testing policy (owner decision)**: no emulator, Maestro or device runs. Any flow is written under `.maestro/011-sync-heads/` and every task that runs it is marked **deferred: E2E on owner's device**. A host-only QA (Phase 7) checks every new call against the live local backend with `curl` and turns the real answers into fixtures, as specs 007–010 did.

**Organization**: Phase 2 adds storage of positions and the heads wire client. US1 (heads) is the MVP; US2 (names), US3 (restore pair) and US4 (full reads) are independent of each other after Phase 2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); sync core `cm/core/sync/…` (`cs/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); sync core tests `ct/core/sync/…` (`ts/…`)
- Host (JVM) tests: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)

## Standing rules for every task

- Layering (P-10): `core.sync` imports no feature; the restore pair is produced by `TransactionSyncHandler` through `SyncHandler`; `*LayeringTest`s stay green.
- Contract (P-02): the cursor is never parsed, compared or built; the position is never sent; heads requests carry only `cursor` and `limit`; branch on `code` only.
- Never log payloads, amounts, notes, cursor, position, head or horizon values; log counts, outcomes, codes and request ids (P-09.2, FR-011).
- **Parity rule**: `settle()` in `cs/SyncEngine.kt` is not changed in meaning; the restore pair's reduced answer goes through it.
- **Koin**: no new binding is expected; if one is added, `ht/core/di/AppGraphTest.kt` must stay green.

---

## Phase 1: Setup

**Purpose**: confirm prerequisites; storage.

- [X] T001 Confirm on `011-sync-heads` (from `main` `8f7b308`): `AppDatabase` version 7 with `MIGRATION_6_7` and `shared/schemas/…/7.json`; `SyncScopeDao.storeFeedPage/clearFeed/markFeedRead`; `FeedPuller.failure`'s from-the-start 410 branch; `DefaultSyncRunner.run`; `SyncEngine.flush/settleItems/answerMatches`; `TransactionSyncHandler.batchItem` returning null for a restore; `WalletRepository.storeMember` guard `stored.version > dto.version`; the backend dev container answers `GET /api/v1/sync/wallets/heads` and feed pages carry `meta.sync_position`. If any is missing, stop and report
- [X] T002 Add `@ColumnInfo(name = "feed_position") val feedPosition: Long? = null` to `SyncScopeEntity`; change `storeFeedPage(scope, cursor, position: Long?)` to write `feed_cursor` and `feed_position` in one upsert; make `clearFeed` also null `feed_position`; add `data class FeedPlace(cursor: String?, position: Long?)` and `suspend fun feedPlace(scope): FeedPlace?` in `cm/core/database/SyncScopeEntity.kt` (data-model §1, FR-001, FR-002)
- [X] T003 Add `MIGRATION_7_8` (`ALTER TABLE sync_scope ADD COLUMN feed_position INTEGER`, nothing else) to `cm/core/database/Migrations.kt`; `version = 8` and `.addMigrations(…, MIGRATION_7_8)` in `cm/core/database/AppDatabase.kt`; build to export `shared/schemas/com.fares.saveme.core.database.AppDatabase/8.json` (FR-020)
- [X] T004 Write `ht/core/database/Migration7To8Test.kt` (a v7 file with rows in every table incl. `sync_scope` with `feed_cursor`, `feed_read_at`, `last_pushed_at` and a `pending_change` restore row migrates to v8 with all data and places intact and `feed_position` null — FR-020, SC-007) and register `MIGRATION_7_8` in `MigrationTest.kt`, `Migration2To3Test.kt`, `Migration3To4Test.kt`, `Migration4To5Test.kt`, `Migration5To6Test.kt`, `Migration6To7Test.kt` so their chains reach v8
- [X] T005 [P] Extend `ht/core/database/SyncScopeDaoTest.kt`: `storeFeedPage` writes cursor and position together and keeps `refreshedAt`/`last_pushed_at`/`feed_read_at`; a null position overwrites an earlier one; `clearFeed` nulls cursor, position and read stamp; `feedPlace` of an unknown scope is null; and confirm `ht/…/SignOutWipeTest.kt` asserts no `sync_scope` row (so no position) survives sign-out (FR-001, FR-002, FR-021)

---

## Phase 2: Foundational (blocking)

**Purpose**: wire types, the heads call, positions stored by every read.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T006 [P] Extend `ts/SyncApiContractTest.kt` (MockEngine): `walletHeads(null)` → `GET sync/wallets/heads?limit=200` with the standard headers and no `cursor`, `filter` or `sort`; `walletHeads("c2")` sends `cursor=c2`; decodes the OpenAPI `twoWallets` example into `HeadsPageDto` (and `data: []`); an unknown member ignored; `400 INVALID_CURSOR`, `400 INVALID_QUERY_PARAMETER`, `400 FILTER_FIELD_NOT_SUPPORTED`, `400 SORT_FIELD_NOT_SUPPORTED`, `401`, `406`, `426`, `429` with `Retry-After`, `500` decode to `Failure` with their code; an undecodable 200 is `UNREADABLE_ANSWER`; feed pages decode `meta.sync_position` and a page without it decodes to `syncPosition = null`; no feed or heads request ever carries a position parameter (FR-003) (contracts/sync-heads-client.md, P-14.3); add the `twoWallets` example to `ts/OpenApiSyncExamples.kt`
- [X] T007 [P] `ts/FeedPullerPositionTest.kt` with `FakeSyncApi`: an incremental read stores each page's position with its cursor; a from-the-start read stores cursor + position only after its last page; a page without `sync_position` stores a null position over an earlier value; a `410` restart and an `INVALID_CURSOR` restart forget the position with the cursor; `WalletScopes.accessLost` path leaves no position (FR-001, FR-002)

### Implementation

- [X] T008 Add to `cs/SyncApi.kt`: `FeedMetaDto.syncPosition: Long? = null` (`sync_position`, kept out of `toString`); `WalletHeadDto`, `CollectionMetaDto`, `HeadsPageDto` (counts-only `toString`); `SyncApi.walletHeads(cursor: String?, limit: Int = PAGE_SIZE): ApiResult<HeadsPageDto>` and its `HttpSyncApi` implementation through `guarded { client.execute(…) }` (data-model §2)
- [X] T009 Add heads support to `ts/FeedFakes.kt` `FakeSyncApi` (`headsCalls`, scripted `heads: suspend (cursor) -> ApiResult<HeadsPageDto>`, default empty `Listed` page) and a `headsPage(vararg heads, next = null)` helper; keep every existing test compiling unchanged
- [X] T010 In `cs/FeedPuller.kt` pass `page.meta.syncPosition` to every `scopes.storeFeedPage(…)` call (intermediate pages of an incremental read, and the final page) so T007 passes; existing `FeedPullerPagingTest`, `FeedPullerRecoveryTest`, `FeedPullerWalletsTest`, `SyncResumeTest`, `FirstRunAfterUpdateTest` stay green

**Checkpoint**: positions are stored; the heads can be called.

---

## Phase 3: User Story 1 - A refresh with nothing new is cheap (Priority: P1) 🎯 MVP

**Goal**: an Everything refresh reads the heads once and reads only the wallets that moved.

**Independent Test**: `SyncRunnerHeadsTest` "ten quiet wallets cost two requests"; live QA rows of quickstart §2.

### Tests (write first, confirm they fail)

- [X] T011 [P] [US1] `ts/HeadsDecisionTest.kt`: one case per row of contracts/sync-heads-client.md — not listed → `Read`; no place → `Read`; place without position → `Read`; `purged > p` → `ReadFromStart` (also when `head > p`); `purged == p` and `head == p` → `Skip`; `head == p` → `Skip`; `head > p` → `Read`; `head < p` → `Read` (FR-005, edge cases)
- [X] T012 [P] [US1] `ts/FeedPullerHeadsTest.kt`: `heads(run)` pages with `limit=200` following `next_cursor` until `has_more` is false and returns `Listed` with `startedAt`; offline or timeout on any page → `Offline`; a `Failure` on page 1 or 2 → `Failed` (logged with request id); account changed between pages → `Failed` with nothing returned; `markUnchanged(scope, startedAt)` writes only `feed_read_at`; `forget(scope)` clears cursor, position and stamp (FR-006, FR-009, FR-010, FR-021)
- [X] T013 [P] [US1] `ts/SyncRunnerHeadsTest.kt` with `FakeSyncApi`, real `FeedPuller` over an in-memory `SyncScopeDao` fake and `FakeWalletScopes`: (a) ten followed wallets with positions equal to their heads → exactly 1 user read + 1 heads read, no wallet read, outcome `Done`, every wallet's `feed_read_at` = heads start (SC-001, FR-006); (b) one head greater → only that wallet read, from its stored cursor (SC-002); (c) `purged > p` → that wallet read with no cursor, its sweep run, no request with the expired cursor (FR-005 rule 3); (d) a followed wallet not listed → read from its cursor, a `404` removes it through `accessLost`, nothing else removed (FR-008); (e) a listed wallet not followed → never read (FR-008); (f) heads offline → no wallet read, outcome `Offline`; (g) heads `500`/undecodable → every wallet read, outcome from those reads (FR-009); (h) a wallet the upload wrote to, an unconfirmed wallet, a placeless wallet and a wallet also requested by `ReadTarget.Wallet` in the same merged run are read without consulting the heads, and a run with only such wallets makes no heads request (FR-007, FR-004); (i) `ReadTarget.Wallet` and `ReadTarget.User` refreshes never call the heads; (j) a forced Everything refresh still skips equal wallets (Clarification 1); (k) a wallet within the 60 s floor is neither read nor counted as a heads candidate, and a skipped wallet is floored on the next unforced run; (l) a user read that failed (not offline) still reads the heads; (m) a wallet not accepted is `Done` without a read (spec 009 rule) (FR-004–FR-009)
- [X] T014 [P] [US1] Extend `ts/SyncLoggingFeedBatchTest.kt`: a heads refresh logs counts (wallets, skipped, read, from the start, direct) and request ids on failure, and no cursor, position, head or horizon value appears in any captured line (FR-011, P-09.2)

### Implementation

- [X] T015 [US1] Create `cs/HeadsDecision.kt`: `enum class WalletReadPlan { Read, ReadFromStart, Skip }` and `object HeadsDecision { fun decide(place: FeedPlace?, head: WalletHeadDto?): WalletReadPlan }` per research R-04 (T011 passes)
- [X] T016 [US1] In `cs/FeedPuller.kt` add `sealed interface HeadsRead { Listed(heads: Map<String, WalletHeadDto>, startedAt: Instant); Offline; Failed }`, `suspend fun heads(run: ReadRun): HeadsRead` (under `SyncLock`, all pages, account check per page, log lines per R-13), `suspend fun place(scope): FeedPlace?`, `suspend fun markUnchanged(scope, startedAt)` and `suspend fun forget(scope)` (T012 passes)
- [X] T017 [US1] In `cs/SyncRunner.kt` (`DefaultSyncRunner.run`): replace `walletForce` with `WalletWant(force, direct)` (`direct` for `report.written`, `unconfirmed`, explicit `ReadTarget.Wallet`); after the user read, when the run wants Everything and is not stopped, compute heads candidates (accepted, not `direct`, outside the floor unless forced, with a stored place and position); if any, call `puller.heads(readRun)` once: `Offline` → stop the run's reads (remaining wallets `Offline`); `Failed` → read every wallet as today; `Listed` → per candidate `HeadsDecision.decide(place, heads[id])`: `Skip` → `markUnchanged` + `lastRead` + `Done`; `ReadFromStart` → `forget` then `readScope(force = true)`; `Read` → `readScope`. Non-candidates are read as today. One summary log line per R-13 (T013, T014 pass); existing `SyncRunnerTest` stays green
- [X] T018 [US1] Update the KDoc of `ReadTarget.Everything`, `SyncRunner` and `FeedPuller` to describe the heads step, and the `SyncScopeEntity` KDoc for `feed_position` and the widened `feed_read_at` (data-model §1)

**Checkpoint**: US1 complete; a quiet refresh costs two requests in host tests.

---

## Phase 4: User Story 2 - Partners see a member's new name (Priority: P2)

**Goal**: equal-version member rows replace the name (already true; locked by tests).

**Independent Test**: `WalletMemberRestampTest`; captured re-stamped page in `SyncCapturedResponsesTest` (T031).

- [X] T019 [P] [US2] `ct/feature/wallets/data/WalletMemberRestampTest.kt` (repository over the in-memory DAOs used by `WalletRepositoryTest`): a wallet-feed `wallet_member` row with the stored version and a new `display_name` replaces it through `WalletMemberFeedApplier`; an older version is ignored; the person's own membership on the user feed (`OwnMembershipFeedApplier`) applies an equal version; `observe(walletId)` emits the new name (what `WalletDetailScreen`'s members list and the transfer choice read) (FR-012, FR-013)
- [X] T020 [US2] Add a one-line KDoc to `WalletRepository.storeMember` stating that an equal version is applied on purpose (backend ADR-0021 re-stamps keep the version) so the guard is not "tightened" later; no behaviour change (research R-08)

**Checkpoint**: US2 complete.

---

## Phase 5: User Story 3 - "Restore with my change" leaves in one request (Priority: P3)

**Goal**: a transaction restore travels as restore (n) + upsert (n + 1) inside the batch, settled as one change.

**Independent Test**: `SyncEngineRestorePairTest` "a restore with five edits leaves in one request".

### Tests (write first, confirm they fail)

- [X] T021 [P] [US3] `ts/SyncEngineRestorePairTest.kt` over `BatchHarness` with a handler whose `batchItems` returns two items for a restore change: (a) a restore pair with 5 other changes → one batch request, the pair consecutive in queue order (SC-005); (b) 48 items queued before the pair → one request of 50 ending with the pair; 49 before → a request of 49, and the pair starts the next request (never split, FR-014); (c) both applied → accepted with item 2's record via `applySentAnswer` (FR-015); (d) item 1 applied, item 2 `conflict` with `current` → clash on that `current`; (e) item 1 applied, item 2 `rejected` 422 → "not saved" with item 2's refusal; item 2 rejected 500 → waiting with backoff; (f) item 1 `conflict` → clash on item 1's `current`, item 2 ignored; (g) a result count or id mismatch → per-record fallback calls `send()` for the restore (FR-016); (h) a request-level failure (offline, 503) applies to the pair once, as one change; (i) a pair whose prerequisite wallet create waits is held without counting a try, and a pair whose prerequisite earlier in the request was not accepted returns to waiting (spec 009 FR-022); (j) an edit made while the pair is in flight is rebased and sent next (FR-015 last sentence)
- [X] T022 [P] [US3] Extend `ct/feature/transactions/data/TransactionSyncHandlerTest.kt`: for a restore change `batchItems` returns `[restore {deleted_at: null, version: n}, upsert {…, version: n + 1}]` with the same `wallet_id` the per-record path uses (source, or destination when the source is gone), the stored kind, currency and counter wallet in the upsert, and only the restore item when the content cannot be decoded; `batchItem` still returns null for a restore; non-restore changes keep a single item; `send()` of a restore still does PATCH then PUT (FR-014, FR-016)
- [X] T023 [P] [US3] Extend `ts/SyncApiContractTest.kt`: a batch body carrying a restore pair serialises `op: "restore"` and `op: "upsert"` in order with `wallet_id` on both and versions n and n + 1 (contracts/sync-heads-client.md)

### Implementation

- [X] T024 [US3] `cs/BatchItem.kt`: add `BatchOp.Restore("restore")` with KDoc (transactions only); `cs/SyncHandler.kt`: add `suspend fun batchItems(change: PendingChange): List<BatchItem>? = batchItem(change)?.let(::listOf)` with KDoc (items of one change are consecutive in one request and settled as one answer)
- [X] T025 [US3] `cs/SyncEngine.kt`: `Entry.items: List<BatchItem>` (empty = per record); `drain()` uses `handler.batchItems`; flush first when the request's item count plus the entry's would exceed `MAX_BATCH`; `run.keys` gets the entry's first item key; `answerMatches` checks the flattened items; `settleItems` reduces each entry's slice to one answer (first not `applied`, else the last) before `settleOne`; logs count items; per-record `sendEach`/`sendOne` unchanged (research R-09; T021 passes)
- [X] T026 [US3] `feature/transactions/data/TransactionSyncHandler.kt`: implement `batchItems` per T022, reusing `pathWallet`, `writeRequest(change, stored, change.baseVersion + 1)` and `TransactionRestoreRequest(change.baseVersion).toJson()`; update the class KDoc (T022 passes)
- [X] T027 [US3] Update `ts/SyncEngineBatchOrderTest.kt` `anArchiveAndARestoreInTheMiddleOfTenEditsKeepTheirPlace`: the restore now rides the batch as a pair in its place while the archive stays per record; record the rename/rewrite in the plan's implementation notes (SC-008 allows edits to tests asserting the per-record restore inside a batch run)

**Checkpoint**: US3 complete.

---

## Phase 6: User Story 4 - A first or long-idle read reaches its end (Priority: P3)

**Goal**: full reads past a purge horizon complete; the once-per-run safeguard stays.

**Independent Test**: captured from-the-start pages in `FeedCapturedPagesTest` (T032); `FeedPullerRecoveryTest` mid-read case unchanged.

- [X] T028 [US4] In `cs/FeedPuller.kt` rewrite the comment and log text of the `SYNC_CURSOR_EXPIRED && hadPosition && fromStart` branch: since backend spec 017 a read from no cursor pages past the horizon; a 410 on its own page now means a purge during the read (or a pre-v1.10 server); the scope fails for this run and starts again next run (research R-11, FR-019). No behaviour change; `FeedPullerRecoveryTest` stays unmodified and green
- [X] T029 [P] [US4] Add to `ts/FeedPullerRecoveryTest.kt` a case where a from-the-start read of 3 pages at limit 1 completes past a horizon (no 410), stores cursor + position after the last page and runs the sweep (FR-018)

**Checkpoint**: US4 complete.

---

## Phase 7: Polish, QA & Cross-Cutting

- [X] T030 (run 2026-10-05 against `saveme-backend@64ca1ea`, see qa-report.md) Live contract QA against the local backend (quickstart §2), recorded in `specs/011-sync-heads/qa-report.md` with request ids: heads (paging at limit 1, empty, each 400 code), positions equal to heads after full reads, a partner write moving one head, a rename re-stamp (queue worker running) with unchanged `version`, restore pair (applied, conflict, item 2 rejected, **replay of the same pair** — record the real answers, R-10/FR-017), a purged wallet read from no cursor at limits 1, 2 and 5 to the end, and the heads `purged_change_seq` above an old stored position. Accounts registered through the API; only QA rows aged and purged
- [X] T031 (done: recorded answers in `CapturedHeadsResponses.kt`, driven by `SyncHeadsCapturedTest` and `SyncRestorePairCapturedTest`; `SyncHeadsWireTest` kept for the edge cases the QA could not stage) Add the real answers from T030 to `ts/CapturedSyncResponses.kt` and drive them through the real `HttpSyncApi`, `FeedPuller`, `DefaultSyncRunner`, engine and appliers in `ts/SyncCapturedResponsesTest.kt`: quiet refresh = 2 requests; one moved wallet read; re-stamped member name applied; restore pair settled (applied, conflict, rejected, replay as recorded)
- [X] T032 [P] (done: the recorded from-the-start pages past the horizon at limits 1, 2, 5, the old-cursor 410 and the mid-read 410 are in `CapturedHeadsResponses.kt` and `SyncHeadsCapturedTest`) Replace or extend `ts/FeedCapturedPagesTest.kt` with the captured from-the-start pages past a purge horizon (T030) proving a complete read and sweep (FR-018, SC-006)
- [X] T033 [P] Rerun `.specify/scripts/bash/check-backend-contract.sh`; confirm "Consistent" and no change beyond spec §5; note the result in `qa-report.md` (FR-022)
- [X] T034 [P] Update `README.md` "On-device database" (v8: integer position stored beside each place) and the sync paragraph (a refresh asks which wallets moved, then reads only those)
- [X] T035 [P] (written; deferred: E2E on owner's device, not run) Write `.maestro/011-sync-heads/US2-partner-rename.yaml` (two accounts, rename, refresh, members list shows the new name) and `US3-restore-with-my-change.yaml`, each starting with `# deferred: E2E on owner's device`; static review that every `id:` selector exists as a `testTag`. **Deferred: E2E on owner's device** — do not run
- [X] T036 Gates: `./gradlew :shared:testAndroidHostTest --rerun-tasks`, `:shared:verifyRoborazziAndroidHostTest` (no golden changed), `:androidApp:assembleDevDebug`, `:shared:compileKotlinIosSimulatorArm64`; `8.json` exported; record counts and any test edited in the plan's Implementation notes
- [X] T037 Host QA of spec 011 (host-only, as specs 009 and 010 did): every acceptance scenario, edge case, FR and SC mapped to a passing test or a QA row in `qa-report.md`
- [X] T038 (parent writes it) Write specs/011-sync-heads/summary.md (parent writes it)

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → stories. T002 → T003 → T004; T005 after T002.
- Phase 2: T006, T007 (tests) before T008–T010; T009 before T007 can run; T010 after T002 and T008.
- US1: T011–T014 before T015–T017; T015 → T017; T016 → T017; T018 last.
- US2 (T019–T020) depends only on Phase 2 (it does not need US1).
- US3: T021–T023 before T024–T026; T024 → T025 → T026 → T027. Independent of US1/US2.
- US4: T028–T029 after Phase 2.
- Polish: T030 needs US1–US4 implemented; T031, T032 after T030; T036 after all code; T037 after T036; T038 last.

## Parallel opportunities

- T005 with T003/T004; T006, T007 together.
- After Phase 2: US1, US2, US3 and US4 in parallel (different files: `SyncRunner`/`HeadsDecision`, wallets tests, `SyncEngine`/`TransactionSyncHandler`, `FeedPuller` comment — T016 and T028 both touch `FeedPuller.kt`, so do them in sequence).
- Within stories, all `[P]` test tasks together.

## Implementation strategy

MVP = Phases 1–3 (positions + heads). Then US3 (restore pair), US2 (names tests), US4 (safeguard
comment and proof), then Polish with live QA.
