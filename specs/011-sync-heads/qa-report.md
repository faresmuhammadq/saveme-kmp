# QA report: Sync Heads Adoption (spec 011)

**Status**: host QA done; live-backend contract QA (T030) done on 2026-10-05 against `saveme-backend@64ca1ea` (`sync.yaml` v1.10; spec 017 heads, `sync_position`, member re-stamp and purge-horizon fix committed; queue worker under supervisor). 150 HTTP calls, 3 throwaway accounts. Real answers are in `CapturedHeadsResponses.kt` and drive the real `HttpSyncApi`, `FeedPuller`, `DefaultSyncRunner` and `SyncEngine` in `SyncHeadsCapturedTest` (13) and `SyncRestorePairCapturedTest` (8).

## Contract recheck (T033)

`.specify/scripts/bash/check-backend-contract.sh` (baseline 192e3ab): Consistent. The only change in the watched files is the uncommitted backend spec 019 (reports) paragraph in `api-conventions.md`; the error catalogue is unchanged; every client code exists in the catalogue with the same retry class. (The backend has since committed spec 019 as `64ca1ea`; the live QA below ran against that commit.)

## Live contract QA (T030)

Accounts registered through `POST /auth/register` (202, created by the queue), verified with an `email_verified_at` update in `dev tinker`, signed in through `POST /auth/login`. A: Plus (`OperatorSubscriptions::grant` under a UUID operator id), owner of W1-W5. B: editor of W1 and W3. C: viewer of W1. Partners made through `PUT /wallets/{id}/invitations/{iid}` (link) and `POST /invitations/accept`. Every request carried the app's headers. Only data was written (no backend code change, no reset, refresh or seed).

Purge safety: before aging, no tombstone older than 60 days existed anywhere in the dev DB (all eleven tombstone tables checked); only W5's own transactions were aged (3, then 2, by `deleted_at = now() - 91 days` in `dev tinker`), and `dev sync:purge-tombstones` reported exactly "Purged 3 tombstones." and "Purged 2 tombstones."

### Heads, positions, quiet refresh, partner write, rename

| Call (as the app sends it) | Live answer | Matches the client |
|---|---|---|
| `GET /sync/wallets/heads?limit=200`, A in 3 wallets (req `01a108d4-03c8-71d9-8df4-e15c996a1c63`) | 200: 3 entries ordered by `wallet_id` (heads 3, 7, 9; `purged_change_seq` 0), `has_more` false | Yes |
| Same at `limit=1` following `next_cursor` | 3 pages, same entries; the last `next_cursor` null | Yes |
| `limit=500`; no `limit` | 200 with `meta.limit` 200 (clamped); 200 with `meta.limit` 50 (default) | n/a (the app sends 200) |
| B before any wallet | 200 `data: []` | Yes (`Listed(empty)`) |
| `?sort=wallet_id`; `?filter[x]=1`; `?cursor=garbage`; `?limit=0` | 400 `SORT_FIELD_NOT_SUPPORTED`; 400 `FILTER_FIELD_NOT_SUPPORTED`; 400 `INVALID_CURSOR`; 400 `INVALID_QUERY_PARAMETER` | Yes (all `Failed` -> read every wallet) |
| No bearer; `X-Client-Version: 0.9.0`; `Accept: text/xml` | 401 `UNAUTHENTICATED`; 426 `CLIENT_VERSION_UNSUPPORTED` (`minimum_version`); 406 `NOT_ACCEPTABLE` | Yes |
| Each wallet feed to the end: `meta.sync_position` | W1 9, W2 7, W3 3 = the heads exactly; user feed 6 | Yes (FR-001) |
| W1 from its latest `sync_cursor`, nothing new | 200 `data: []`, same `sync_cursor`, `sync_position` 9 | Yes: a quiet refresh is head == stored position, so it skips |
| W1 from the start at `limit=3` | page 1 `sync_position` 5 (the last row of the page, where `next_cursor` resumes), page 2 `sync_position` 9 | Yes |
| B and C accepted onto W1, B onto W3; A's heads | W1 19, W3 8 moved; W2 7 not; after A read W1 and W3 to the end each equalled its stored position again | Yes |
| B's batch with a transaction in a category B does not own | request 200, item `rejected` 422 `INVALID_ENUM_VALUE@/category_id`; head unchanged | Yes |
| B creates a category and a transaction in W1; A's heads, then W1 from the stored cursor | W1 19 -> 23, W2/W3 unchanged; feed: `transaction`, `category_reference`, `wallet` (balance 93,000), `sync_position` 23 = the head | Yes: only W1 would be read |
| B renames (batch `profile` upsert, v1 -> v2); A's heads polled every second | 1 s later (queue run): W3 8 -> 10, W1 23 -> 25, W2 unchanged | Yes |
| A reads W1 from position 23 | one `wallet_member` row for B: **same `version` 1**, new `change_seq` 24, `display_name` "QA 011 B Renamed", plus the wallet; `sync_position` 25 = the head | Yes: equal-version rows replace the copy (FR-012) |
| W2 archived by A; new wallet W4 | archived W2 still listed (head 8); W4 listed (3) | Yes |
| W4 deleted by A; B left W3 (`POST /wallets/{id}/leave` with the member's version) | W4 absent from A's heads; W3 absent from B's heads; B's `GET /sync/wallets/{W3}/changes` 404 `NOT_FOUND` | Yes: absence is no removal signal, the 404 is |

### Restore pair (`POST /sync/batch`)

Transaction deleted (tombstone v2), then `restore {deleted_at: null, version: 2}` + `upsert {…, version: 3}` as consecutive items.

| Case | Live answer | Settled as |
|---|---|---|
| Pair at n = 2 (req `01a108d5-5283-7147-8bef-dcf18d694a8a`) | item 1 `applied` 200 v3; item 2 `applied` 200 v4 with the person's content | accepted, record v4 |
| **Replay of the same pair after a lost answer** (record already at n + 2 = 4; req `01a108d5-5333-7107-a341-ef9d07113662`) | request 200; **both items `applied` 200, both return the unchanged v4 record** (nothing bumped, same `change_seq`) | accepted once: no clash, no duplicate, no second bump (`aPairReplayedAfterALostAnswer…`) |
| Replay after a later edit (record at v5) | item 1 `applied` (a no-op, returns the live v5); item 2 `conflict` 409 `VERSION_CONFLICT` with `current` v5 | clash on the live v5 copy |
| Replay after the record was deleted again (tombstone v6) | both items `conflict` 409 with the tombstone | clash on item 1's tombstone |
| Stale pair: another device restored and edited (live v4) | item 1 `applied` (no-op, returns v4); item 2 `conflict` 409 with `current` v4 | clash on v4 |
| Stale pair: another device restored, edited and deleted again (tombstone v5) | both `conflict` 409 with the tombstone | clash on the tombstone |
| Stale pair where the other device only restored (live v3, no edit) | item 1 `applied`; item 2 `applied` 200 v4 (A's content at base 3 is current) | accepted (not a clash: the base is current) |
| Viewer (C) sends the pair | both items `rejected` 403 `FORBIDDEN` | "not saved" with `FORBIDDEN` |
| Item 2 with `amount_minor` 0 | item 1 `applied` 200 v3 (record restored on the server); item 2 `rejected` 422 `VALIDATION_FAILED` `OUT_OF_RANGE@/amount_minor` | "not saved" with the field error; the restored record arrives with the wallet's next read |

R-10 outcome: **a restore against a live record is a no-op `applied`, whatever version it names; the version check lives on the save.** So a replayed pair is idempotent exactly when the record is still the pair's own result (n + 2 with the same content), and a clash otherwise. The engine's reduction (first non-`applied` item, else the last) settles every real answer correctly; no engine or handler change was needed. The items the engine builds equal the ones sent to the live server (JSON-equal, `SyncRestorePairCapturedTest`).

### Purge horizon (W5: 8 transactions; 3 then 2 tombstones aged 91 days and purged)

| Call | Live answer | Matches the client |
|---|---|---|
| Heads after the first purge | W5 head 27, **`purged_change_seq` 26**; the stored position was 21 | Yes: 26 > 21 -> `ReadFromStart` without asking the expired cursor |
| W5 from the cursor stored at position 21 (req `01a108d6-88e0-738f-8b11-d2c976fbd640`) | 410 `SYNC_CURSOR_EXPIRED` | Yes |
| W5 from no cursor at `limit=1`, `2`, `5`, `200` after the purge | 8, 4, 2 and 1 pages; every `next_cursor` accepted; last page `sync_position` 27; **no 410** (the horizon is 26 and live rows lie below it) | Yes (FR-018, SC-006): the backend bug of spec 009 §5.1 is fixed |
| Read from no cursor at `limit=2`: page 1, then a second purge (2 tombstones, horizon 30), then page 2 with the server's own `next_cursor` (req `01a108d6-b907-724d-a7a6-fb0f6139f241`) | page 2: 410 `SYNC_CURSOR_EXPIRED`; the restart from no cursor completes in 3 pages (head 31) | Yes: one 410 for that scope in that run, the next run completes (FR-019, safeguard kept) |

### Cleanup

W1, W2, W3, W4, W5 deleted through the API (their transactions are tombstoned with them). The two QA categories remain (`CATEGORY_IN_USE` by the tombstoned transactions); three sessions logged out. The QA accounts, their profiles and the tombstones younger than 90 days remain (the backend has no account erasure).

## Host QA (T037): requirement to test

| Requirement | Proof |
|---|---|
| FR-001, FR-002 position stored and cleared with the cursor | `FeedPullerPositionTest`, `SyncScopeDaoTest`, `SyncApiContractTest` (sync_position), **live**: `SyncHeadsCapturedTest.everyRealFeedPageStoresItsPosition…` |
| FR-003 no position ever sent | `SyncApiContractTest.noFeedOrHeadsRequestEverCarriesAPosition` |
| FR-004, FR-007 which refreshes consult the heads, bypasses | `SyncRunnerHeadsTest` (written, placeless, positionless, unconfirmed, named, only-bypass run, Wallet/User targets) |
| FR-005 decision table | `HeadsDecisionTest`; **live**: quiet, moved, behind-the-horizon rows in `SyncHeadsCapturedTest` |
| FR-006 stamp of a skipped wallet, SC-001, SC-002 | `SyncRunnerHeadsTest.tenQuietWalletsCostTwoRequests`, `onlyTheWalletWhoseHeadMoved...`, `SyncHeadsWireTest`; **live**: `aQuietRefreshOfThreeWalletsOnTheRealAnswers…`, `aPartnersWriteMovesOnlyThatWalletsHead…` |
| FR-008 not listed / not followed | `SyncRunnerHeadsTest`; **live**: `anArchivedWalletIsListedADeletedOrLeftOneIsNotAnd…` |
| FR-009 failures and offline | `FeedPullerHeadsTest`, `SyncRunnerHeadsTest`, `SyncApiContractTest.everyHeadsRefusalDecodesToItsCode`; **live**: `aPersonInNoWalletGetsAnEmptyListAndEveryRealRefusalIsItsCode` |
| FR-010 account change | `FeedPullerHeadsTest.aChangeOfAccountBetweenPagesDropsEverythingRead`; **live** paging: `aHeadsReadFollowsEveryPageOfTheRealLimitOneAnswers` |
| FR-011 logs | `SyncLoggingFeedBatchTest` (heads refresh, refused heads) |
| FR-012, FR-013 names | `WalletMemberRestampTest`, `SyncHeadsWireTest`; **live**: `aPartnersRenameArrivesAsTheSameVersionRow…` |
| FR-014 - FR-016 restore pair | `SyncEngineRestorePairTest`, `TransactionSyncHandlerTest`, `SyncApiContractTest`, `SyncHeadsWireTest`, `SyncEngineBatchOrderTest`; **live**: `SyncRestorePairCapturedTest` (applied, stale live, stale tombstone, viewer, item 2 rejected) |
| FR-017 replay of a lost pair | **live**: `SyncRestorePairCapturedTest.aPairReplayedAfterALostAnswer…` (both `applied`, unchanged v4, accepted once), `…AfterALaterEdit…` (clash), `…DeletedAgain…` (clash on the tombstone) |
| FR-018, FR-019 full reads and safeguard | `FeedPullerRecoveryTest`, `SyncHeadsWireTest`; **live**: `aReadFromNoCursorAcrossTheRealHorizonPagesToTheEnd…`, `aStoredCursorOlderThanTheHorizon…`, `aPurgeLandingMidRead…` |
| FR-020, SC-007 migration | `Migration7To8Test`, chain tests, `MigrationTest` |
| FR-021 sign-out | `SignOutWipeTest`, `SyncScopeDaoTest` |
| FR-022 contract | contract recheck above |
| SC-008 goldens unchanged | `verifyRoborazziAndroidHostTest` green, no golden touched |
