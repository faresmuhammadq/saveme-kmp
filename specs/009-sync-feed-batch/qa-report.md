# QA report: Sync feeds and batch upload (client, spec 009)

**Date**: 2026-10-02 · **Branch**: `009-sync-feed-batch` · **Mode**: no device (owner policy: no emulator, Maestro
or device runs; E2E deferred to the owner's phone, T071) · **Backend**: local dev stack at `saveme-backend@e4dc4c6`
(spec 016 sharing now committed; `sync.yaml` v1.9 = the spec's baseline `6b8e290` plus the v1.9 additions spec §5
already lists; the contract check reports the client codes consistent).

Evidence is a passing host test (`./gradlew :shared:testAndroidHostTest`, 1,480 tests, 0 failed), a live backend
answer (§5) or a code path. Test classes are in `shared/src/commonTest/kotlin/com/fares/saveme/core/sync/` unless a
feature path is given (`ht/` = `shared/src/androidHostTest/kotlin/com/fares/saveme/`). "Captured" means the live
answers recorded in §5 (`CapturedSyncResponses.kt`, 55 answers and 3 request bodies) driven through the real wire
client (`HttpSyncApi` on the app's `ApiClient`), engine, puller, runner, handlers and appliers by
`SyncCapturedResponsesTest` (7 tests) and `FeedCapturedPagesTest` (12 tests, its OpenAPI inputs replaced, T069).

## 1. Acceptance scenarios

| Scenario | Evidence | Result |
|---|---|---|
| US1-S1 20 changes in ≤ 2 uploads, "waiting" clears when accepted | `SyncEngineBatchTest.dueChangesArePackedOldestFirstAtMostFiftyPerRequest` (51 → 2), `.anAppliedItemClearsTheRowStoresTheAnswerAndStampsTheLastPush`; captured `aBacklogLeavesAsTheItemsTheLiveServerAcceptedAndEveryAnswerSettles` (4 changes, 1 request, queue empty) | PASS |
| US1-S2 New wallet before its transactions, none refused for it | `SyncEngineDependencyTest` (8); captured backlog test (wallet item before the expense in the same request; live 201 for both) | PASS |
| US1-S3 New category first, transaction accepted with it | `SyncEngineDependencyTest.aDependencyOnAnUnacceptedCategoryInAnotherScopeHolds…`; captured backlog test (category and expense in one request, live 201) | PASS |
| US1-S4 A clash shows today's choice, others settle on their own | `SyncEngineBatchTest.aConflictBecomesAClashWithTheCurrentRecord…`; captured `aRefusedNewWalletHoldsItsTransfer…` (live 409 with `problem.current` v1 → `Clash` with the current copy, five other items settled independently) | PASS |
| US1-S5 Refused for good: "not saved", same reason, no retry | `SyncEngineBatchTest.aValidationRefusalIsRefusedWithTheMappedFieldsAndNotRetried`; captured (live 422 `OUT_OF_RANGE@/amount_minor` → `Refused` with the amount field; live 404 → `Refused NOT_FOUND`) | PASS |
| US1-S6 Dependent of a failed/waiting prerequisite keeps waiting | `SyncEngineDependencyTest.aTransactionWhoseWalletCreateClashedWasRefusedOrIsBackingOff…`, `.aDependentWhosePrerequisiteInTheSameRequestWasNotAccepted…`; captured (live: refused wallet 2 → the transfer into it answered 422 at `/counter_wallet_id`; the app ignores that answer, `Waiting`, 0 attempts, then sends it with the fixed wallet: 201) | PASS |
| US1-S7 Cut-off upload: nothing lost or duplicated | `SyncEngineBatchRequestTest.aTimedOutBatchIsResentNextRunWithTheSameItems`, `.noConnectionOrNoAnswerInTime…`; captured `aLostAnswerIsSentAgainUnchangedAndTheReplayIsAcceptedOnce` (identical items; live replay answers 200, versions unchanged; stored once; balance counted once, **gap closed §4.1**) | PASS (gap closed) |
| US1-S8 Archive / restore / ownership transfer keep own requests in place | `SyncEngineBatchOrderTest.anArchiveAndARestoreInTheMiddleOfTenEditsKeepTheirPlace`; handler `batchItem = null` tests (`WalletSyncHandlerBatchTest.anArchiveGoesPerRecord`, `CategorySyncHandlerBatchTest`, `TransactionSyncHandlerBatchTest.aRestoreWithMyChange…`); transfer-ownership is not a queued change (C4 endpoint unchanged) | PASS |
| US2-S1 Only changes since the last read | `FeedPullerPagingTest`; captured `theFirstReadsStore…` (second read sent the stored position, got only the uploads' results), `aReadFromTheStartFollowsEveryPage…` (empty page, same position); live §5 | PASS |
| US2-S2 Newer replaces, older never does | `AccountFeedAppliersTest`, `CategoryFeedAppliersTest`, `WalletFeedAppliersTest`, `TransactionFeedApplierTest` version-guard tests; captured `aTransferFromBothWalletFeeds…` (re-read changes nothing) | PASS |
| US2-S3 Deletion elsewhere removes, unless a change waits | applier tombstone tests (`…UnlessAChangeWaits` ×4); captured `aMemberWhoLeftOrWasRemoved…` (live tombstones) | PASS |
| US2-S4 Transfer once, in both wallets, both balances right | `TransactionFeedApplierTest.aTransferFromEitherWalletsFeedIsStoredOnce…`; captured `aTransferFromBothWalletFeedsIsStoredOnceAndTheBalancesAreTheServers` (live: identical record on both feeds; 81,800 / 5,000) | PASS |
| US2-S5 Own accepted change: no jump back, no double count | `BalanceReconcilerFeedTest` (9), `TransactionPostingsTest`; `WalletCopyCarryTest` (3, new); captured backlog and replay tests (87,500 throughout) | PASS (gap closed §4.1) |
| US2-S6 Interrupted read resumes, nothing skipped or doubled | `SyncResumeTest.aReadKilledAfterPageTwoOfFour…`, `FeedPullerPagingTest.thePositionIsStoredOnlyAfterAPagesRowsAreApplied`, `.noConnectionHalfwayLeaves…` | PASS |
| US2-S7 Unknown kinds skipped, rest applies | `FeedPullerPagingTest.unknownKindsAreSkippedAndThePositionStillAdvances`; captured `kindsTheAppDoesNotConsumeAreSkipped…` (live `recurring_rule`, `occurrence` ×2, `invitation` ×2 on the wallet feed and `budget` on the person's) | PASS |
| US2-S8 Offline: phone's copy and "Updated …" | `WalletsServiceListTest`, `CategoriesServiceRefreshTest`, `TransactionsServiceRefreshTest.offlineAndFailedOutcomesAreReported…`, `SessionRefresherTest.endingOfflineIsReportedNotQueued` | PASS |
| US3-S1 Gained wallet appears with members, transactions, references | `FeedPullerWalletsTest.anEverythingRunReadsThePersonsScopeThenEveryFollowedWallet…`, `SyncRunnerTest.everythingReads…IncludingOnesGained…`; captured `aWalletGainedByInvitationIsReadFromTheStartInTheSameRun…` (live invite + accept: B's run reads B's scope then wallet 1 from the start: 3 transactions, 2 members, 1 reference) | PASS |
| US3-S2 Lost wallet leaves with its data and queued changes; waiting wallet edit keeps it | `OwnMembershipFeedApplierTest` (6), `TransactionsWalletCleanupFeedTest`, `DefaultWalletScopesTest.accessLostKeepsAWalletWhoseRecordHasAWaitingChange`; captured (live leave → tombstone → wallet, members, transactions gone; live delete → own membership tombstone → wallet 2 gone, the transfer touching wallet 1 kept; live 404 → removed) | PASS |
| US3-S3 Role change follows | `OwnMembershipFeedApplierTest.aLiveOwnMembershipIsStoredWithItsRole…`; captured (live PATCH to viewer → own membership v2 and wallet `my_role: viewer` applied) | PASS |
| US3-S4 Unaccepted wallet never treated as lost | `OwnMembershipFeedApplierTest.aWalletMadeOnThisPhoneAndNotYetAccepted…`, `FeedPullerWalletsTest.aPlaceholderWalletIsNeverRead…`, `SyncRunnerTest.aTargetedWalletIsReadAloneAndAPlaceholderWalletIsNeverRead` | PASS |
| US4-S1 Expired position: that part re-read, gone records removed, others untouched | `FeedPullerRecoveryTest.anExpiredWalletIsReadAgain…`; captured `anExpiredWalletPositionIsReadAgainFromTheStart…` (live 410 after aging + `sync:purge-tombstones`; purged member swept; user and wallet 2 positions unchanged) | PASS |
| US4-S2 Waiting changes kept and still sent | `FeedPullerRecoveryTest` (queued rows kept), `ht/core/sync/FeedSweepRoomTest` (sweep keeps queued, placeholder, carried rows) | PASS |
| US4-S3 Invalid position: start again | `FeedPullerRecoveryTest.anInvalidPositionIsClearedAndReadAgainOnce…`; captured `anInvalidPositionIsForgotten…` (live 400 for garbage, another account's, a user cursor on a wallet feed, another wallet's) | PASS |
| US4-S4 First sync after update reads every part once, screens keep data | `FirstRunAfterUpdateTest`, `ht/…/Migration5To6Test` (2) | PASS |
| US5-S1 Batch refused as a whole → per record, same order and outcomes | `SyncEngineFallbackTest` (4); captured `theLiveServersRequestLevelShapeRefusalsSendTheSameChangesPerRecord…` (live 422 for 0 / 51 items and a missing `items`, 400 `MALFORMED_JSON`, 415, 405 `METHOD_NOT_ALLOWED`) | PASS |
| US5-S2 Killed mid-upload or mid-read resumes | `SyncResumeTest` (2), `SyncEngineAccountTest.aSendInterruptedByProcessDeathIsSentAgain` | PASS |

**26 scenarios: 26 PASS** (two after closing gap §4.1).

## 2. Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Same record twice in a batch | queue holds one row per record (`SyncEngineSendTest.severalEditsCollapse…`); captured request lists | PASS |
| Edit while its upload is in flight | `SyncEngineBatchTest.aChangeEditedDuringTheRequestIsRebased…` | PASS |
| Answer lost, sent again | captured replay test (live 200 replay) | PASS (gap closed §4.1) |
| Needed record refused: dependents wait, discard cleans | captured `aRefusedNewWalletHoldsItsTransfer…`; `SyncEngineDependencyTest`; spec 008 wallet cleanup | PASS |
| Category waiting in the person's scope goes first | `SyncEngineDependencyTest.aDependencyOnAnUnacceptedCategoryInAnotherScope…` | PASS |
| Transfer destination created on the phone goes first | `SyncEngineDependencyTest` (destination prerequisite); captured (wallet 2 then the transfer in one request, live 201) | PASS |
| Transfer on both feeds applied once | captured transfer test (re-read changes nothing) | PASS |
| Wallet re-sent, same version, new balance | `WalletFeedAppliersTest.theWalletRecordIsAppliedWhen…AnEqualVersion…`; live: wallet 1 stayed v1 while its balance moved | PASS |
| Deleted elsewhere while a change waits | applier tombstone tests; `WalletFeedAppliersTest.aTombstoneLeavesAWalletWhoseRecordHasAWaitingChange` | PASS |
| Own deletion read back stays hidden with its carry | `TransactionFeedApplierTest.aTombstoneForARowWithAnUnsettledCarry…`; captured (accepted delete → 100,000 shown) | PASS |
| Half the pages applied, app dies | `SyncResumeTest`, `FeedPullerPagingTest` | PASS |
| Access lost while a change waits | `OwnMembershipFeedApplierTest.aWalletWhoseOwnRecordHasAWaitingEditStays…`, `TransactionsWalletCleanupFeedTest` | PASS |
| Waiting transaction edited to use a later category | `SyncEngineDependencyTest.aTransactionEditedToUseACategoryCreatedLaterIsHeldOneRun` | PASS |
| Own membership missing from a full read (no tombstone) | `FeedPullerWalletsTest.aWalletOnlyMissingFromACompleteRead…` (kept on 200, removed on 404); `SyncRunnerTest.heldWalletsWhoseMembershipWasNotSeen…` | PASS |
| New wallet never reached the server: not read | `SyncRunnerTest.aTargetedWalletIsReadAloneAndAPlaceholderWalletIsNeverRead` | PASS |
| Wallet feed 404 | `FeedPullerWalletsTest.aWalletFeedNotFound…`; captured `aWalletFeedAnsweringNotFoundRemovesTheWallet` (live 404 after leaving / removal / for a stranger) | PASS |
| Signed out mid-sync | `SyncEngineSignOutTest` (3), `FeedPullerPagingTest.nothingIsAppliedOrStoredAfterTheAccountChangesMidRead`, `ht/…/SignOutWipeTest` | PASS |
| Different account signs in | positions wiped (`ht/…/SyncScopeDaoTest.signOutRemovesEveryPosition`); a stray position → live 400 (captured `INVALID_CURSOR_OTHER_ACCOUNT`) → read from the start | PASS |
| Known kind, unknown fields | captured bodies carry `recurring_rule_id`, `occurrence_id`, `default_currency_locked`, `budget_period_start_day_from`; all decode | PASS |
| Kind learned later | adoption rule (contracts/sync-engine-v2.md); skipping proven by captured unknown kinds | PASS |
| Platform default categories | `CategoriesServiceRefreshTest.theOwnCategoriesAreReadThroughTheRunnerAndTheDefaultsFromTheirOwnList` | PASS |
| Plan, entitlements, account, verification | unchanged reads (`AccountRefresher`, entitlements); no feed carries them | PASS |

**22 edge cases: 22 PASS.**

## 3. Functional requirements and success criteria

| FR | Evidence | Result |
|---|---|---|
| FR-001 feeds keep every syncable copy current | appliers ×8 (`AppGraphTest.theFeedGraphHasOneApplierPerConsumedPair…`); captured feed tests | PASS |
| FR-002 person's scope first, then followed wallets | `SyncRunnerTest`, `FeedPullerWalletsTest`, `DefaultWalletScopesTest`; captured gained-wallet run | PASS |
| FR-003 one position per scope, stored after applying, empty page stores | `FeedPullerPagingTest` (12), `ht/…/SyncScopeDaoTest` (5); captured empty-page test | PASS |
| FR-004 version guard, queue untouched | applier tests; `…FeedCopyNeverTouchesTheWaitingChange` | PASS |
| FR-005 deletions | applier tombstone tests | PASS |
| FR-006 unknown kinds skipped, position advances | paging test; captured (5 live kinds) | PASS |
| FR-007 cursorless read sweeps after the last page only | `FeedPullerPagingTest.aReadFromTheStartStoresItsPositionOnlyAtTheEnd…`, `ht/…/FeedSweepRoomTest` (5) | PASS |
| FR-008 410 → that scope from the start | `FeedPullerRecoveryTest`; captured 410 test; **live gap in the server, §5.1** | PASS (client) |
| FR-009 400 → once per run | `FeedPullerRecoveryTest.anInvalidPosition…`; captured (4 live variants) | PASS |
| FR-010 gained wallet read in the same run; role follows | captured gained-wallet run | PASS |
| FR-011 lost wallet removal rules | `OwnMembershipFeedApplierTest`, `DefaultWalletScopesTest`, `FeedPullerWalletsTest`; captured leave / delete / 404 | PASS |
| FR-012 transfer stored once; no cross-feed comparison | captured transfer test (live: the transfer keeps the source's `change_seq` 10 on wallet 2's feed although wallet 2's counter is 5) | PASS |
| FR-013 balance, lock, role from the wallet record; carry never double counts | `BalanceReconcilerFeedTest`, `WalletCopyCarryTest` (new), `ht/…/TransactionDaoTest.aWalletCopyAtAPositionSettles…` (new); captured | PASS (gap closed §4.1) |
| FR-014 triggers and floors | `SyncRunnerTest.anUnforcedReadOfAScopeWithinAMinute…`, coordinator tests | PASS |
| FR-015 "Updated …" and outcomes | `SyncRunnerTest.theOutcomeIsDone…`, service list/refresh tests | PASS |
| FR-016 no retired read on a sync path | grep: no `refreshAll/refreshOne/refreshOwn/listSessions/refreshWallet/CategoryReferenceApi` caller remains | PASS |
| FR-017 batch by default, ≤ 50, items = per-record writes | `SyncEngineBatchTest`, handler batch tests ×4; captured (app items byte-equal to the bodies the live server accepted, 2 requests) | PASS |
| FR-018 non-batchable in place | `SyncEngineBatchOrderTest` (4) | PASS |
| FR-019 per-item settle parity | `SyncEngineParityTest`; captured `theLiveServersItemAnswersLeaveTheSameRows…` (live applied 200/201, conflict, rejected 422/404/409/403) | PASS |
| FR-020 request-level answers | `SyncEngineBatchRequestTest` (6); captured 401 (unchanged) and 426 (refused as the single write) | PASS |
| FR-021 shape/endpoint refusal fallback | `SyncEngineFallbackTest`; captured (6 live refusals) | PASS |
| FR-022 prerequisites | `SyncEngineDependencyTest` (8); captured held transfer | PASS |
| FR-023 60 s, safe resend | `SyncApiContractTest.theBatchGetsSixtySeconds…`; captured replay | PASS |
| FR-024 declared members only, `wallet_id` on transactions only, profile id | `SyncApiContractTest.theBatchBodyHasOnlyItems…`, handler tests; live: `wallet_id` on a wallet item → 422 `UNKNOWN_FIELD@/items/0/wallet_id` (the app never sends it) | PASS |
| FR-025 per-record endpoints kept | handler `send` tests unchanged | PASS |
| FR-026 one run, upload first, offline skips reads | `SyncRunnerTest` | PASS |
| FR-027 process death | `SyncResumeTest`, `ht/core/sync/SyncDrainWorkerTest` | PASS |
| FR-028 signed-in account only, wipe | `SyncEngineSignOutTest`, `ht/…/SignOutWipeTest`, `ht/…/SyncScopeDaoTest` | PASS |
| FR-029 Room 5 → 6, no positions, schema exported | `ht/…/Migration5To6Test`, migration chains; `shared/schemas/…/6.json` | PASS |
| FR-030 logs | `SyncLoggingFeedBatchTest` (4), `SyncEngineLoggingTest` | PASS |
| FR-031 no client mismatch | `check-backend-contract.sh`: consistent at `e4dc4c6` | PASS |
| FR-032 cursor codes, after-change | `ApiErrorCodeTest` (2) | PASS |
| FR-033 item refusals raise the same signals | `SyncEngineBatchEventsTest` (2); captured `anItemRefusedForAnUnverifiedAddressPromptsVerification` (live 403 item) | PASS |

**33 FRs: 33 PASS.**

| SC | Evidence | Result |
|---|---|---|
| SC-001 20 changes ≤ 2 uploads, 100% settle | `SyncEngineBatchTest` packing; captured backlog (4 → 1 request) and mixed answers | PASS |
| SC-002 nothing refused for a phone-made prerequisite | `SyncEngineDependencyTest`; captured held transfer (its live refusal ignored) | PASS |
| SC-003 nothing new → no records; one change → that change | captured empty pages; `FeedPullerPagingTest` | PASS |
| SC-004 large wallet not re-downloaded | position per scope (`FeedPullerPagingTest`); **caveat §5.1** for a first read past a purge horizon | PASS (client) |
| SC-005 balance = server + unsent at every moment | `WalletCopyCarryTest`, `BalanceReconcilerFeedTest.theBalanceNeverJumps…`; captured | PASS (gap closed §4.1) |
| SC-006 gained/lost within one refresh | captured gained / role / leave run | PASS |
| SC-007 expired scope re-read alone | captured 410 test | PASS |
| SC-008 behaviour of specs 004–008 unchanged | full host suite green; `verifyRoborazziAndroidHostTest` green, no golden changed | PASS |

**8 SCs: 8 PASS.**

## 4. Gaps found and closed

1. **Double-counted balance when a wallet copy already includes an accepted change** (FR-013, SC-005, US1-S7,
   US2-S5). Found replaying the live answers: after a lost upload, the resend's answer carries wallet 1 at
   `change_seq` 7 with balance 87,500 (the expense already in it) and then the expense; the expense's own answer
   added its carry again, so the phone showed 75,000 until the next read of wallet 1. The same happens without a
   replay whenever a wallet save follows an expense in one upload (or per record, since spec 008). Fix: a server copy
   of a wallet at scope position *p* counts every change of its scope up to *p*, so
   (a) `TransactionRepository.storeServerCopy(own = true)` adds no source carry when the stored wallet copy is at or
   past the change's `change_seq`; (b) storing a wallet copy (own answer or feed) clears the source carry of rows at
   or below its position (`WalletRepository.onWalletCopy` → new `WalletCopyParticipant`, implemented by
   `TransactionsWalletCleanup`; `TransactionDao.clearSourceCarryCoveredBy`). The destination part of a transfer still
   waits for the destination's read (a transfer's position is its source scope's). The reconciler's per-read settle
   is unchanged. Tests: `WalletCopyCarryTest` (3), `ht/…/TransactionDaoTest.aWalletCopyAtAPositionSettles…`,
   captured replay test. The spec 008 captured fixture now places the seeded wallets at the live position before the
   first transaction (`walletJson(changeSeq = …)`), which the old fixed `change_seq: 7` contradicted.
2. **A read from the start that meets 410 on its own next page was restarted once more** (wasted a full re-read per
   run with the same result, §5.1). `FeedPuller` now ends such a read `Failed` at once (no second pass; position
   stays empty, nothing swept). Tests: `FeedPullerRecoveryTest.aReadFromTheStartWhoseOwnNextPageExpires…`, captured
   `aReadFromTheStartWhoseNextPageTheServerExpiresFailsOnceAndKeepsTheCopy`.
3. **Contract proof only against OpenAPI examples** → `CapturedSyncResponses.kt` (T068), `CapturedSyncServer` (the
   real `HttpSyncApi` answered by route and position), `SyncCapturedResponsesTest` (7) and `FeedCapturedPagesTest`
   rewritten on live pages (12) (T069).

## 5. Contract QA against the live backend (T067)

Accounts registered through `POST /auth/register` (`qa009x-a@example.test` A with a Plus subscription from
`SubscriptionFactory`, `qa009x-b` B, `qa009x-c` C, `qa009x-unv` unverified), verified with a `users.email_verified_at`
update in `dev tinker`, signed in through `POST /auth/login` with the app's device description. Every request
carried the app's headers (`Accept`, `X-Client-Platform: android`, `X-Client-Version: 1.0.0`, `Accept-Language`,
`X-Correlation-Id`, bearer) and the bodies the handlers build. (Accounts made with `UserFactory` alone have no
profile row, so their user feed answers 404; registering through the API avoids that. Not a client concern.)

| Call (as the app sends it) | Answer | Matches the client |
|---|---|---|
| `GET /sync/user/changes?limit=200`, no cursor | 200: `profile`, `device_session`; `sync_cursor` set, `next_cursor` null | Yes |
| Same from the stored `sync_cursor` after the uploads | 200: own `wallet_member` ×2 (owner), `category`, `profile` v2 | Yes |
| From a position with nothing new | 200, `data: []`, the same `sync_cursor` | Yes (position kept) |
| `limit=2` from the start | 3 pages; `next_cursor` = `sync_cursor` while `has_more`; last page `next_cursor: null` | Yes |
| `POST /sync/batch`: wallet create, category create, expense in that wallet with that category, profile save | 200: applied 201, 201, 201, 200 (profile v2) | Yes; the app's items are byte-equal (captured test) |
| Same batch again | 200: four `applied`, status 200, versions unchanged | Yes (replay) |
| Batch: wallet type `bank`, a transfer into it, amount 0, an unknown wallet, wallet 1 at a stale version, delete of the expense | `rejected` 422 `INVALID_ENUM_VALUE@/type`; `rejected` 422 `INVALID_ENUM_VALUE@/counter_wallet_id`; `rejected` 422 `OUT_OF_RANGE@/amount_minor`; `rejected` 404; `conflict` 409 with `problem.current` (v1); `applied` (tombstone v2) | Yes: pointers rooted at the item's `data`; the transfer's answer is ignored because its prerequisite was not applied |
| Batch: wallet 2 (`checking`), the transfer, the expense of 7.00 | three `applied` 201 | Yes (byte-equal) |
| Category create + delete; delete of a category in use; wallet delete without `version` | applied 201, applied 200 (tombstone), `rejected` 409 `CATEGORY_IN_USE`, `rejected` 422 `REQUIRED@/version` | Yes |
| Unverified account's wallet create | `rejected` 403 `EMAIL_NOT_VERIFIED` (request 200) | Yes → verification prompt |
| A `restore` item | `applied` v3 | n/a: listed by v1.6; the app keeps "Restore with my change" per record (PATCH then PUT, FR-018) |
| `wallet_id` on a wallet item; an `invitation` item | 422 `UNKNOWN_FIELD@/items/0/wallet_id`; 422 `INVALID_ENUM_VALUE@/items/0/type` | Yes (never sent) |
| 0 items; 51 items; `{"things":[]}` | 422 `OUT_OF_RANGE@/items` ×2; 422 `UNKNOWN_FIELD@/things` + `REQUIRED@/items` | Yes → per-record fallback |
| Cut-off body; `text/plain`; `GET /sync/batch` | 400 `MALFORMED_JSON`; 415 `UNSUPPORTED_MEDIA_TYPE`; 405 `METHOD_NOT_ALLOWED` (not in the app's catalogue → status rule) | Yes → per-record fallback |
| Bad bearer; `X-Client-Version: 0.9.0` | 401 `UNAUTHENTICATED`; 426 `CLIENT_VERSION_UNSUPPORTED` | Yes |
| `GET /sync/wallets/{1}/changes` from the start | `wallet_member`, `category_reference`, transfer, 2 expenses, `wallet` (balance 81,800) | Yes |
| `GET /sync/wallets/{2}/changes` from the start | `wallet_member`, the identical transfer record, `wallet` (5,000) | Yes (stored once) |
| Wallet 1 after a recurring rule (+ materialise), a budget, two invitations, B accepting | `recurring_rule`, `occurrence` ×2, B's `wallet_member`, `invitation` ×2 (one accepted tombstone), `wallet`; the person's feed: `budget` | Yes: unknown kinds skipped, position advances |
| `cursor=garbage`; A's cursor sent by C; a user cursor on a wallet feed; wallet 1's cursor on wallet 2 | 400 `INVALID_CURSOR` ×4 | Yes → read from the start once |
| `cursor=` (empty) | 200 from the start | n/a (the app never sends `cursor=`) |
| C reads wallet 1 (not a member); `/sync/wallets/not-a-uuid/changes` | 404 `NOT_FOUND`; 422 `INVALID_ID` | Yes (lost / client defect) |
| `limit=0`; `limit=201`; `filter[kind]=expense` | 400 `INVALID_QUERY_PARAMETER`; 200 with `limit` 200; 400 `FILTER_FIELD_NOT_SUPPORTED` | Yes (the app sends `limit=200` only) |
| Invite by link (`PUT /wallets/{1}/invitations/{id}`, A on Plus) → B `POST /invitations/accept` | 201 with `meta.share_url`; 200, B's membership v1 | — |
| B's feeds after accepting | person's: own `wallet_member` (editor); wallet 1 from the start: everything, `wallet.my_role: editor` | Yes → gained in the same run |
| A makes B a viewer (`PATCH /wallets/{1}/members/{B}`) | B's person feed: own membership v2 `viewer`; wallet 1: member v2 + `wallet.my_role: viewer` | Yes |
| B leaves (`POST /wallets/{1}/leave`) | B's person feed: own membership tombstone; wallet 1 feed: 404 (with or without a cursor) | Yes → removed |
| C invited, accepts, removed by A (`DELETE /wallets/{1}/members/{C}`) | C's person feed: tombstone; C's wallet 1: 404; A's wallet 1: B's and C's member tombstones, C's invitation tombstone | Yes |
| A deletes wallet 2 (batch `delete`) | applied (v2 tombstone); A's person feed: own membership tombstone; wallet 2's own feed still readable by A with member + wallet tombstones | Yes (the person-feed tombstone removes it first) |
| B's membership tombstone aged 91 days in `dev tinker`, then `dev sync:purge-tombstones` ("Purged 1 tombstones") | wallet 1 from an old position: 410 `SYNC_CURSOR_EXPIRED`; B's person feed from an old position: 410; A's person feed from an old position: 200 (untouched); wallet 1 from the start: 200 without B's row; from the latest position: 200 empty | Yes |
| **Wallet 1 from the start with `limit=6` after that purge** | page 1: 200, `has_more`, `next_cursor` at position 16; page 2 with that `next_cursor`: **410 `SYNC_CURSOR_EXPIRED`**; same on B's person feed with `limit=1` (page 2: 410) | **Backend bug, §5.1** |

### 5.1 Backend bug: a read from the start cannot page past a purge horizon

`ChangeFeedService::positionOf` expires every cursor whose position is below the scope's `purged_change_seq`,
including the `next_cursor` the server itself issued one page earlier in a read that began without a cursor. A full
resync — the very remedy the 410 prescribes (FR-007 / ADR-0008 rule 13 in backend spec 013, api-conventions "Purge
horizon") — therefore fails on page 2 whenever the scope has more live rows below its horizon than one page holds.
Reproduced live (above): after one membership tombstone at wallet 1's position 29 was purged, a from-the-start read
with `limit=6` got page 1 (positions ≤ 16) and then 410 for its own `next_cursor`. With the app's `limit=200` it bites
any wallet (or person's scope) with more than 200 rows below its horizon once 90 days of deletions have been purged:
a new phone, a reinstall, a 410 recovery or the first run after this update can then never complete that scope —
it keeps page 1 applied and the rest missing, every run. Suggested fix (backend): mint the cursor of a read that
began at 0 with the horizon it started under (e.g. a `h` member), and expire it only if the scope's horizon has
moved past `max(position, h)`; or skip the horizon check for a cursor minted inside a from-the-start read.
Client side: such a read ends `Failed` after one pass (gap §4.2), shows the existing copy and is retried on the next
run; nothing the client can send avoids the 410.

**Cleanup**: the pending invitation revoked, the budget and the recurring rule deleted, wallet 1 deleted through the
batch (wallet 2 was deleted during QA), category 1 archived (still referenced by tombstones), all four sessions signed
out. Left in the dev database (no account erasure endpoint; not reset or seeded): the four `qa009x-*` accounts, A's
Plus subscription, the tombstones, and three earlier factory accounts `qa009-a/b/c@example.test` (one with a Plus
subscription) made before the missing-profile finding. The only direct data edits were the `email_verified_at`
updates, the subscription rows and the one aged `wallet_members.deleted_at`.

## 6. Commands

`./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64 --rerun-tasks`
— BUILD SUCCESSFUL in 11 min (1,480 host tests, 0 failures; Roborazzi verify green with no golden changed; dev debug
APK and iOS simulator compile green). The QA ran against a clean `e4dc4c6` working tree; uncommitted backend sync
work by another agent appeared afterwards and was not exercised.
