# Implementation Plan: Sync Heads Adoption (client)

**Branch**: `011-sync-heads` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-sync-heads/spec.md`

## Summary

Four engine follow-ups to backend spec 017, no screen change:

- **Positions**: `sync_scope` gains `feed_position` (Room 7 → 8), written with `feed_cursor` from the
  same page (`meta.sync_position`) and cleared with it (R-01, R-02).
- **Heads**: an Everything run reads `GET /sync/wallets/heads` once after the user scope; a pure
  `HeadsDecision` turns each heads-eligible wallet's place + head into `Read`, `ReadFromStart` or
  `Skip`; skipped wallets are stamped as read at the heads' start. Written-to, unconfirmed,
  placeless and explicitly targeted wallets bypass the heads; heads offline stops reads; any other
  heads failure reads every wallet (R-03 – R-07).
- **Names**: the existing "same or newer" member guard already applies re-stamped rows; tests and a
  live fixture lock it (R-08).
- **Restore pair**: `BatchOp.Restore`; `SyncHandler.batchItems` (default wraps `batchItem`);
  transactions emit restore (n) + upsert (n + 1) as one engine entry never split across requests,
  settled as one answer; per-record fallback unchanged (R-09, R-10).
- **Purge-horizon fix**: no behaviour change; the once-per-run safeguard stays, its comment updated,
  and live QA proves full reads past a horizon (R-11).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, Ktor 3.6.0,
kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite,
WorkManager 2.12.0, Kermit). **No new dependency.**

**Storage**: Room `saveme.db` v8: `sync_scope.feed_position INTEGER NULL` (data-model §1). No other
schema change.

**Testing**: kotlin-test, coroutines-test, Ktor `MockEngine` contract tests from the `sync.yaml`
v1.10 examples, fakes (`FakeSyncApi` + heads, `FakeWalletScopes`, `BatchHarness`) in `commonTest`;
Room migration/DAO/sign-out tests in `androidHostTest` (Robolectric); captured live-backend answers
(`CapturedSyncResponses.kt`). No emulator, Maestro or device run (owner policy).

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: quiet Everything refresh = 2 requests for ≤ 200 wallets (SC-001); k moved
wallets = 2 + k (SC-002); the heads decision is O(followed wallets) in memory.

**Constraints**: P-02 (opaque cursor, codes), P-04, P-09.2 (no position values in logs), spec 009
behaviour parity (FR-023); one run at a time under `SyncLock`.

**Scale/Scope**: 1 migration; ~3 new `core.sync` types (`HeadsDecision`, `HeadsRead`, heads DTOs);
changes in `SyncApi`, `FeedPuller`, `DefaultSyncRunner`, `SyncEngine`, `SyncHandler`, `BatchItem`,
`TransactionSyncHandler`, `SyncScopeDao`; no new strings, no UI.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails. No new dependency; no UI; no MVP-guarded
capability (sync is core MVP, brief C14).

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code names follow the backend glossary (Scope Head → `WalletHeadDto.changeSeq`, Sync Position → `feed_position`, Purge Horizon → `purgedChangeSeq`); no user copy (spec §2) |
| P-02 API contract fidelity | PASS | Shapes from `sync.yaml` v1.10 (contracts/sync-heads-client.md); cursor never parsed (R-01); heads sends only `cursor`/`limit`; unknown members ignored; failures branch on `code` (heads table); restore item only for `transaction` as the schema allows; no new automatic retry |
| P-03 Money | PASS | No amount logic; the restore pair's upsert reuses the existing `TransactionWriteRequest` (Long minor units) |
| P-04 Offline-first | PASS | Rule 1: heads write only stamps; feeds still write Room only. Rule 4: the pair carries base n and n + 1; a conflict reaches the person through `settle()` (FR-015); a skip never hides a server change (head == position, ADR-0021 rule 3) |
| P-05 Time | PASS | `feed_read_at` stamps stay fixed-width UTC instants (`syncStamp`) |
| P-06 Localisation & RTL | N-A | No new user-facing text or screen (names arrive as data) |
| P-07 Design system only | N-A | No UI change |
| P-08 Accessibility | N-A | No UI change |
| P-09 Security & privacy | PASS | Positions wiped with `sync_scope` at sign-out (FR-021); heads read stops on account change; logs carry counts and request ids, never positions, heads, cursors or names (FR-011, R-13); heads never grant access (FR-008) |
| P-10 Shared-first architecture | PASS | All in `commonMain`; `core.sync` still imports no feature (`HeadsDecision` and heads in `core.sync`; the pair is produced by `feature.transactions.data.TransactionSyncHandler` through the `SyncHandler` interface) |
| P-11 Naming | PASS | `HeadsDecision`, `HeadsRead`, `WalletReadPlan`, `WalletHeadDto`, `FeedPlace`, `BatchOp.Restore` |
| P-12 Entitlements & billing | N-A | Heads need authentication only; no gated capability |
| P-13 Observability | PASS | Heads failures logged with `request_id`; one summary line per Everything run (R-13); correlation ids via `ApiClient` |
| P-14 Quality gates | PASS | Each spec §4 invariant has a named test (R-14); contract tests for the new endpoint and each handled code, `sync_position` and the pair body; migration test; no new dependency |
| P-15 Scope guard | PASS | No guarded capability |
| P-16 MVI presentation | N-A | No store or screen changes; services keep signatures and outcomes |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/011-sync-heads/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── sync-heads-client.md
├── checklists/
│   ├── requirements.md
│   └── sync.md          # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/
├── schemas/com.fares.saveme.core.database.AppDatabase/8.json        # exported v8
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/database/AppDatabase.kt                              # version 8, + MIGRATION_7_8
    │   ├── core/database/Migrations.kt                               # MIGRATION_7_8
    │   ├── core/database/SyncScopeEntity.kt                          # feed_position; storeFeedPage(…, position); clearFeed nulls it; feedPlace()
    │   ├── core/sync/SyncApi.kt                                      # FeedMetaDto.syncPosition; WalletHeadDto, CollectionMetaDto, HeadsPageDto; walletHeads()
    │   ├── core/sync/HeadsDecision.kt                                # new: WalletReadPlan + decide()
    │   ├── core/sync/FeedPuller.kt                                   # store position with cursor; heads(); markUnchanged(); forget(); safeguard comment
    │   ├── core/sync/SyncRunner.kt                                   # WalletWant(force, direct); heads step; plans; summary log
    │   ├── core/sync/BatchItem.kt                                    # BatchOp.Restore
    │   ├── core/sync/SyncHandler.kt                                  # batchItems() default
    │   ├── core/sync/SyncEngine.kt                                   # Entry.items; packing; flattened match; per-entry reduction
    │   └── feature/transactions/data/TransactionSyncHandler.kt       # batchItems(): restore + upsert(n + 1)
    ├── commonTest/kotlin/com/fares/saveme/
    │   ├── core/sync/                                                # HeadsDecisionTest, FeedPullerHeadsTest, FeedPullerPositionTest, SyncRunnerHeadsTest,
    │   │                                                             #   SyncEngineRestorePairTest, SyncApiContractTest (+heads), FeedFakes (+heads),
    │   │                                                             #   CapturedSyncResponses (+v1.10 answers), SyncCapturedResponsesTest (+cases), SyncLoggingFeedBatchTest (+heads)
    │   ├── feature/transactions/data/TransactionSyncHandlerTest.kt  # pair mapping
    │   └── feature/wallets/data/WalletMemberRestampTest.kt           # equal-version names
    └── androidHostTest/kotlin/com/fares/saveme/core/database/        # Migration7To8Test; chain tests + MIGRATION_7_8; SyncScopeDaoTest; SignOutWipeTest
README.md                                                              # "On-device database": v8; sync: heads
```

**Structure Decision**: Everything stays in `core.sync` except the restore pair mapping, which the
transactions feature owns through `SyncHandler.batchItems`; no new cross-feature contract.

## Implementation phases (input to /speckit-tasks)

1. **Setup**: `SyncScopeEntity.feed_position`, DAO changes, `MIGRATION_7_8`, AppDatabase v8, exported
   schema, migration tests.
2. **Foundation**: wire DTOs + `walletHeads()` + contract tests; `FeedPuller` stores the position
   with the cursor (FR-001, FR-002); `FakeSyncApi` heads support.
3. **US1 Heads**: `HeadsDecision`; `FeedPuller.heads/markUnchanged/forget`; runner `WalletWant`, heads
   step, plan execution, fallbacks, stamps, summary log; runner tests.
4. **US2 Names**: re-stamp tests (repository, applier, captured page).
5. **US3 Restore pair**: `BatchOp.Restore`, `SyncHandler.batchItems`, engine entries/packing/
   reduction; `TransactionSyncHandler.batchItems`; engine and handler tests; update the existing
   order test that asserted a per-record restore inside a batch run.
6. **US4 Full reads**: safeguard comment; captured from-the-start pages past a horizon.
7. **Polish**: log-capture test; live contract QA + captured fixtures (`qa-report.md`); contract
   recheck; README; Maestro flows (deferred); gates; `summary.md` (parent).

## External dependencies (not in this repo)

Backend B14a is built and committed (`c0406b0`); contract recorded at `saveme-backend@192e3ab`. The
backend working tree has uncommitted spec 019 (reports) edits, unrelated to sync. Contract gaps
(non-blocking): the replay answer of a restore pair against a record already at n + 2 is not written
(R-10, QA records it); no user-scope head (ADR-0021 follow-up).

## Complexity Tracking

None.

## Implementation notes

Recorded during implementation (owner away, delegated authority). Each is the choice consistent with spec, plan and research.

1. **`CollectionMeta` reused.** `HeadsPageDto.meta` is the existing `core.network.CollectionMeta` (`next_cursor`, `has_more`, `limit`); no `CollectionMetaDto` was added (same wire shape).
2. **`storeFeedPage` call sites in tests.** The DAO method now takes a position, so the 60-odd existing test calls `storeFeedPage(scope, cursor)` became `storeFeedPage(scope, cursor, null)` mechanically (no assertion changed); `FakeSyncScopeDao` mirrors the new SQL (`feedPlace`, position stored and cleared with the cursor).
3. **Rewritten test (deliberate).** `SyncEngineBatchOrderTest.anArchiveAndARestoreInTheMiddleOfTenEditsKeepTheirPlace` is now `anArchiveInTheMiddleOfTenEditsKeepsItsPlaceAndARestoreRidesTheBatchAsAPairAtItsPlace`: the restore rides the batch as two consecutive items at its place (log `batch 8: e5, e6, e7, restore, restore, e8, e9, e10`). The old per-record restore scenario is kept as `aRestoreAHandlerCannotPairStillGoesPerRecordAtItsPlace`. No other engine test changed; `BatchHandler` gained `pairs` / `batchItems` / `BatchHarness.saveRestore` only.
4. **Heads candidates** are decided before the wallet loop: accepted, not direct, outside the floor (or forced), with a stored cursor and position. Heads are read only if there is at least one. A skipped wallet gets `feed_read_at` = the heads read's start, `lastRead` = now, outcome `Done`; a `ReadFromStart` wallet is read with `force = true` after `forget`. When the heads answer `Offline` the run stops and the remaining wallets report `Offline`.
5. **Summary log** (R-13): `wallets: N followed, S unchanged, R read, F from the start (purge horizon), D direct`, once per run that includes Everything. `R` counts only reads that were not floored.
6. **Restore pair** reuses `writeRequest(change, stored, n + 1)`; when the stored row is gone (a feed tombstone removed it) the payload's own kind, currency and counter wallet are sent, exactly as the per-record save does. The per-record `restoreThenSave` (which used the restored server copy's fields) is unchanged.
7. **Pair key:** the engine entry's key for held dependents is its first item's key; both items share it.
8. **Maestro:** `US3-restore-with-my-change` is three parts (`.part1/.part2/.part3`) because the Given needs the network toggled between parts, as the spec 008 restore flow does. `US2-partner-rename` uses the person's own member row (the app has no sharing screens yet; the row travels on the same feeds as a partner's) and the existing `saveProfile` action. Both start with `# deferred: E2E on owner's device`; every `id:` selector was checked against a `testTag` in the source.
9. **T030 live-backend contract QA done** (2026-10-05, `saveme-backend@64ca1ea`): see `qa-report.md`. The recorded answers (`CapturedHeadsResponses.kt`) complement the hand-built `SyncHeadsWireTest` and drive the real `HttpSyncApi`, `FeedPuller`, `DefaultSyncRunner` and `SyncEngine` in `SyncHeadsCapturedTest` (13 tests) and `SyncRestorePairCapturedTest` (8). Findings: heads ordered by `wallet_id`, `limit` clamped to 200 (default 50); `sync_position` equals the head after a full read and an intermediate page states where its `next_cursor` resumes; a re-stamp keeps the version and moves the head within a second of the rename; a restore against a live record is a no-op `applied`, so a replayed pair at n + 2 answers both items `applied` with the unchanged record (accepted once), after a later edit item 2 conflicts (clash), and after a re-delete both conflict (clash on the tombstone); reads from no cursor page across the horizon at limits 1, 2, 5 and 200, a stored cursor below the horizon gets 410, and a purge landing mid-read gives one 410. **No client defect was found; no engine or handler change was needed** for R-10.
10. **Contract check (T033):** `check-backend-contract.sh` against baseline `192e3ab` reports only the uncommitted spec 019 (reports) edit to `api-conventions.md`; the error catalogue is unchanged and the client codes are consistent.
11. **Gates (T036):** `:shared:testAndroidHostTest --rerun-tasks` 2092 tests (2071 + 21 recorded-answer tests), 0 failed, 0 skipped; `verifyRoborazziAndroidHostTest` green with no golden changed; `:androidApp:assembleDevDebug` and `:shared:compileKotlinIosSimulatorArm64` pass; `8.json` exported. `AppGraphTest` unchanged and green (no new Koin binding).
