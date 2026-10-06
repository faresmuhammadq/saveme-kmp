# QA report: Export & Deletion Completion (client), spec 016

Run 2026-10-06 on branch `016-export-deletion` (implementation `9446457`, merged with `main` incl. the C13 reports merge
`e0eef36`). Backend: `saveme-backend` committed `776e5b2` (spec 022; summary `e8c308a`), local container, queue worker running.
Owner policy: no emulator, Maestro or device run; device items stay deferred (T027, T038, T044, T052, T060, T068, T076).

Legend: PASS = proven by a passing host test or a code path named here; GAP = was open, closed by the test or fix named;
DEFERRED = needs the owner's device (flow written, not run).

## 1. Summary

| Item | Count | PASS | GAP closed | DEFERRED |
|---|---|---|---|---|
| Acceptance scenarios (US1 7, US2 5, US3 6, US4 9, US5 5, US6 4) | 36 | 36 | 0 | 0 (E2E flows are additional) |
| Functional requirements FR-001..FR-039 | 39 | 39 (FR-038 after the gap below) | 1 (FR-038) | 0 |
| Success criteria SC-001..SC-007 | 7 | 7 (SC-001 is a count of taps, proven by navigation; no stopwatch) | 0 | 0 |
| Edge cases | 20 | 20 | 0 | 0 |
| Live contract cases (section 3) | 96 answers recorded, 64 kept as fixtures | all replayed by 30 host tests | n/a | n/a |
| Other gaps | 1 (T063 had no dedicated test) | closed by `FormerMemberLiveReplayTest` | | |

Gaps found and closed:

1. **FR-038 had no host test** for "announce status changes to screen readers", the stable tags, 48 dp targets and 200% font on the new
   screens (screenshots show layout, not semantics). Closed by `ExportAccessibilityTest` (5 tests): status in words and a polite live
   region beside the indicator, tags present, every clickable at least 48 dp, nothing lost at 2.0.
2. **T063** (ownership transfer arrives as an ordinary feed: person becomes owner, departed member is a tombstone) was marked done
   but no test pinned it. Closed by `FormerMemberLiveReplayTest`, which replays the real post-purge feeds of three partners through the
   real feed puller and appliers.

Live QA found **no backend bug that affects the client** and no client defect. Worth knowing (section 3.7): successors absent in a repeated
request keep the stored choices while a present list replaces all of them (the client always sends one per offered wallet); a
hand-forced `purging` account with a live session is cancelled silently (unreachable in reality); the signed file answers
`Cache-Control: no-store, public`.

## 2. Traceability

Paths: `ct` = `shared/src/commonTest/kotlin/com/fares/saveme/`, `ht` = `shared/src/androidHostTest/kotlin/com/fares/saveme/`,
`at` = `ct feature/account/`. "Live" = the replay tests over captured answers (section 3).

### 2.1 Acceptance scenarios

| Scenario | Evidence | Result |
|---|---|---|
| US1-1 list newest first with kind, status, date; New export | `ExportsViewModelTest.openLoadsTheFirstPage...`, `rowsAndNewNavigate`, `walletRowsAreNamedFromTheWalletsFlow`; `ExportCopyTest`; Live `theLiveListIsNewestFirst...`; screenshot `list` | PASS |
| US1-2 New export, Everything, confirm: one request, opens detail "Waiting to start" | `ExportRequestViewModelTest.defaultsToEverything...`, `oneRequestPerTapAndNoneWhileInFlight`; `ExportServiceTest.requestSendsOnePost...`; Live `theLiveAcceptedRequestsAreQueuedExports...` | PASS |
| US1-3 reads every 5 s while queued/running in foreground, words + indicator | `ExportWatchTest.readsAtOnceThenEveryFiveSeconds...`; `ExportDetailViewModelTest.startedWatchesAndStoppedCancels...`; Live `theLiveSequenceQueuedRunningReady...` (10 s virtual for 3 reads); `ExportAccessibilityTest.aWaitingExport...` | PASS |
| US1-4 ready: size, prepared time, until, "Open download"; polling stops | `ExportDetailViewModelTest.readyEndsThePolling`; `ExportCopyTest.sizesAre...`; screenshots `detail_ready`, `detail_ready_megabytes`; `ExportAccessibilityTest.aReadyExport...` | PASS |
| US1-5 open: reads again, only if ready with link; link not shown/copied/stored/logged | `ExportDetailViewModelTest.openDownloadReadsAFreshLinkForEachTap...`; `ExportServiceTest.everyLinkRequestMakesAFreshRead`; `ExportLoggingTest` (3 tests); `ExportLayeringTest.exportCodeUsesNoFile...`; Live `theDetailStoreWatchesTheLiveSequenceAndHandsOnly...` | PASS |
| US1-6 expired meanwhile: "Expired", browser not opened | `ExportDetailViewModelTest.expiredOnTapUpdatesTheScreen...`; Live `theDetailStoreShowsAnExpiredExportWithoutOpeningTheBrowser` (real expired answer without link) | PASS |
| US1-7 explanation: ZIP, JSON + CSV, others anonymous, 24 h, email, no XLSX/encryption | `ExportDeletionStringsTest.theExplanationNamesZipJsonCsvAndTheTwentyFourHours`, `noSpec016CopyClaimsAFormat...`; live ZIP content (3.5): other members absent, `transactions.csv` + JSON | PASS |
| US2-1 "Export this wallet" for every role, archived, only a server-held wallet | `WalletDetailTest.everyRoleAndArchivedWalletsAreExportable...`, `aWalletOnlyOnThisPhoneIsNotExportable`; Live: a viewer's wallet export 202 | PASS |
| US2-2 preset wallet, switchable | `ExportRequestViewModelTest.defaultsToEverythingAndToTheWalletWhenPreset`, `aWalletRequestNeedsAWalletAndSendsIt`; screenshot `request_wallet` | PASS |
| US2-3 wallet no longer held: "You no longer have this wallet" | `ExportServiceRefusalTest.notFoundOnARequestIsTheWalletGone`; Live `theLiveRefusalsMapToTheirOwnOutcomes` (unknown wallet and non-member both 404) | PASS |
| US2-4 `wallet_unavailable` copy | `ExportCopyTest.failuresReadByCode...`; `ExportMappingTest.everyFailureCodeMapsAnd...`; screenshot `detail_failed_wallet` | PASS |
| US2-5 export of a wallet the phone lacks: "A wallet you no longer have" | `ExportsViewModelTest.walletRowsAreNamedFromTheWalletsFlow`; screenshot `list` row `gone` | PASS |
| US3-1 in progress: message + "See it" opens `current` | `ExportServiceRefusalTest.conflictOpensTheCurrentExport`, `...WithoutOrWithAnUnreadableCurrentHasNone`; `ExportRequestViewModelTest.inProgressOffersSeeIt...`; Live `theLiveConflictCarriesTheExportAlreadyBeingPrepared`, `theRequestStoreOpensTheLiveCurrentExport...` | PASS |
| US3-2 rate limit: hours/minutes/later, request unavailable until the wait passes | `ExportRequestViewModelTest.rateLimitBlocksUntilTheWaitPasses...`, `rateLimitWithoutAWaitDoesNotBlock`; `ExportCopyTest.waitsAreRoundedUp...`; Live `theLiveSixthRequestIsRateLimited...` (86386 s -> 24 hours; store blocks, sends nothing, ends by itself) | PASS |
| US3-3 failed: "We couldn't prepare this file", Try again = same scope | `ExportDetailViewModelTest.failedAndExpiredOfferANewRequestOfTheSameScope`; `ExportCopyTest.failuresReadByCode...` | PASS |
| US3-4 offline everywhere: needs a connection, Try again, nothing queued/retried | `ExportServiceTest.offlineAndTimeoutAreOutcomesNeverRetried`, `linkOutcomesForOfflineAndGone`; `ExportDetailViewModelTest.offlineWatchOffersCheckAgain`; `ExportsViewModelTest.offlineAndErrorsShowABanner...` | PASS |
| US3-5 10-minute cap: "Still preparing", Check again | `ExportWatchTest.stillActiveAfterTenMinutesSaysStillPreparing`, `aHundredAndTwentyReads...`; `ExportDetailViewModelTest.stillPreparingStopsAndCheckAgainRestartsAFreshWindow` | PASS |
| US3-6 unsent changes noted | `ExportRequestViewModelTest.unsentChangesAreSaidFromTheSyncStatus`; screenshot `request_unsent` | PASS |
| US4-1 explanation: 30 days, shared wallets pass on, own deleted, entries stay, Play notice, export first | `ExportDeletionStringsTest.thePlayNoticeIsShownOnTheExplanationTheReviewAndThePendingView`, `everySpec016KeyExistsInBothLanguages`; screenshot `explain` | PASS |
| US4-2 plan read before the password; offline stays with Try again | `DeleteAccountViewModelTest.explainsBeforeAskingForThePassword...`, `aPlanFailureReturnsToTheExplanation...`; Live plan reads (`theLiveActivePlan...`) | PASS |
| US4-3 no successors: New owners skipped, review says none | `DeleteAccountViewModelTest.withNoOfferedChoiceNoSuccessorsAreSent`; Live `anAccountWithNoSharedWalletHasAnEmptyPlan...` | PASS |
| US4-4 `choice_expected: false` shown without a choice | `DeletionPlanServiceTest.joinsNamesAndOrders...`; Live `theLiveActivePlanNamesEachRule...` (Alpha `only_member`) | PASS |
| US4-5 `choice_expected: true`: candidates, suggested first and preselected | same + `SuccessorChoicesTest`; Live (Beta: editor first; Delta: viewer first) | PASS |
| US4-6 review lists owners, 30 days, no date of its own, Play notice | `DeleteAccountViewModelTest.picksDefaultToSuggestions...`; screenshot `review`; code: the review step reads no date | PASS |
| US4-7 request carries the password and one choice per offered wallet; scheduled shows the server's date and owners | `RequestDeletionUseCaseTest.sendsSuccessorsWhenGivenAnd...`, `omitsSuccessorsWhenNull`; Live `theDeleteStoreWalksTheLivePlan...` (2 choices for 3 wallets, Alpha not sent), `theLiveRequestBodyHasOnlyThePassword...` | PASS |
| US4-8 refused successor: message, plan re-read, back to New owners, nothing scheduled | `DeleteAccountViewModelTest.successorsChangedRereadsThePlan...`; Live `aLiveRefusedSuccessorSendsThePersonBack...` (real 422 `/successors/0/member_user_id`) | PASS |
| US4-9 wrong password, lock, others: spec 002 handling | `RequestDeletionUseCaseTest.aPasswordPointerKeepsSpec002Handling`, `wrongPasswordStill...`; `DeletionContractTest.refusalsMapToOutcomes`; Live `theLiveRefusalsMapToSpec002AndSuccessorOutcomes` (missing password, wrong password) | PASS |
| US5-1 pending view: date, owners with reason, Change, Export, Play notice, Cancel | `DeleteAccountViewModelTest.openingWhilePendingReadsThePlanAndShowsThePendingView`, `changeOwnersIsOnlyOfferedWhenAWalletExpectsAChoice`; Live `theLivePendingPlansShowTheStoredChoice...` | PASS |
| US5-2 change owners with the password; date unchanged | `DeleteAccountViewModelTest.changingOwnersGoesThroughReviewAndPassword...`, `scheduledWhileChangingReturnsToPending...`; Live `theLiveScheduledAnswersCarryTheServersDate...` (date identical across 5 live requests) | PASS |
| US5-3 cancel: spec 002 cancel, "choices dropped" | `DeleteAccountViewModelTest.cancelDropsTheChoicesAndSaysSoOnlyWhenAnyWereStored`; Live `theLiveCancelledPlanIsActiveAgainWithEveryChoiceDropped`, `theLiveCancelAnswers204...` | PASS |
| US5-4 date passed: "being deleted", Cancel kept | `DeleteAccountViewModelTest.afterTheDateThePendingViewIsBeingDeleted`; `HomeViewModelTest.theNoticeBecomesBeingDeletedOnceTheDateHasPassedAndKeepsCancel`; screenshots `screen_delete_home_being_deleted_*` | PASS |
| US5-5 purge: next request 401, existing sign-out and wipe, no special message | `DeletionPurgeSignOutTest.aPendingDeletionAccountWhoseSessionWasEndedIsSignedOutAndWiped`; Live: after the real purge every call answered `401 UNAUTHENTICATED` (3.6) | PASS |
| US6-1 transaction "Added by a former member" | `TransactionAddedByTest` (5), `TransactionsServiceListTest`; Live `FormerMemberLiveReplayTest.theLiveSoleViewer...`, `...ChosenViewerOwnsBeta...` (real `created_by` of the departed person) | PASS |
| US6-2 rule author "former member" | `RuleAuthorTest.anAuthorMissingFromTheHeldMembersIsAFormerMember` | PASS |
| US6-3 ownership passed: person shown as owner via the feeds | Live `FormerMemberLiveReplayTest` (wallet `my_role` owner, `ownRole` owner, departed absent from members, partners' members endpoint) | PASS (T063 gap closed) |
| US6-4 member without a name stays "another member" | `TransactionAddedByTest.aPresentMemberWithoutAUsableNameIsAnotherMember`, `RuleAuthorTest.aHeldMemberIsNamedOrAnotherMemberWithoutAName` | PASS |

### 2.2 Functional requirements

| FR | Evidence | Result |
|---|---|---|
| FR-001 Account offers Export my data | `AccountViewModelTest.rowsNavigateAndOpeningRefreshes`; `AccountScreensScreenshotTest` | PASS |
| FR-002 wallet detail action, every role, archived, server-held only | `WalletDetailTest` (3 tests) | PASS |
| FR-003 pending view and explanation offer Export | `DeleteAccountViewModelTest.scheduledShowsTheDateThenFinishesAndExportsOpens`; `ExportDeletionStringsTest` | PASS |
| FR-004 no gating | `ExportLayeringTest.exportCodeReadsNoEntitlementOrFeature`; `ExportServiceTest` (constructor takes only the API); Live: unverified account 202, account pending deletion 202, free plan 202 | PASS |
| FR-005 list: 20/page, newest first, INVALID_CURSOR restarts once, empty text | `ExportsViewModelTest.aRefusedPositionRestartsOnce`, `emptyIsShownOnlyAfterTheFirstAnswer`; Live `theListStoreShowsTheLivePages...`, `aListedPageSendsTwenty...` | PASS |
| FR-006 two scopes, wallet choice, explanation | `ExportRequestViewModelTest` (scope, wallet tests); screenshots `request_*` | PASS |
| FR-007 one request per tap | `oneRequestPerTapAndNoneWhileInFlight`; Live `aRequestIsOneCallWithOnlyTheScope...` | PASS |
| FR-008 online-only, nothing stored | `ExportServiceTest.offlineAndTimeout...`; `ExportsViewModelTest.signOutClearsEverything...`; no Room change (Room stays v10) | PASS |
| FR-009 unsent changes | `unsentChangesAreSaidFromTheSyncStatus` | PASS |
| FR-010 read at open, every 5 s, words + indicator | `ExportWatchTest`, `ExportAccessibilityTest` | PASS |
| FR-011 stop rules (final, background, sign-out, offline, 10 min/120 reads) | `ExportWatchTest` (7), `ExportDetailViewModelTest.startedWatchesAndStopped...`, `signOutCancelsAndClears` | PASS |
| FR-012 detail content per status | `ExportCopyTest`; screenshots `detail_*`; Live reads of queued, running, ready, expired | PASS |
| FR-013 open reads again | `ExportServiceTest.everyLinkRequestMakesAFreshRead`; Live `theLiveReadyExportOpensWithAFreshLinkAndAnExpiredOneNeverDoes` | PASS |
| FR-014 link never stored/shown/logged, redacted | `ExportContractTest.theDownloadUrlNeverPrints`, `ExportMappingTest.theDomainExportCarriesNoLink`, `ExportLoggingTest`, `ExportsViewModelTest.signOut...StateNeverHoldsAUrl`, Live (effect and state print no signature) | PASS |
| FR-015 app never writes the file | `ExportLayeringTest.exportCodeUsesNoFileDownloadShareOrClipboardApi`; live: the app never requests the signed route (3.5) | PASS |
| FR-016 no browser | `ExportDetailViewModelTest.theBrowserFailureIsReportedWithoutTheLink` | PASS |
| FR-017 `EXPORT_IN_PROGRESS` -> current | `ExportServiceRefusalTest`, Live | PASS |
| FR-018 explanation content, no XLSX/encryption/names | `ExportDeletionStringsTest` | PASS |
| FR-019 in-progress copy | `ExportCopyTest.theNewCodeHasItsOwnCopyChosenByCode` | PASS |
| FR-020 rate limit | `ExportServiceRefusalTest.rateLimitCarriesTheWaitFromEitherPlaceOrNone`; Live | PASS |
| FR-021 NOT_FOUND on request / read | `notFoundOnARequestIsTheWalletGone`; `ExportDetailViewModelTest.notFoundOnTheWatchStopsAndHidesTheExport`; Live 404s | PASS |
| FR-022 failure codes | `ExportCopyTest.failuresReadByCode...`; `ExportMappingTest` | PASS |
| FR-023 UNAUTHENTICATED/update-required app-wide; others general failure with request id | `ExportServiceRefusalTest.unauthenticatedAndUpdateRequired...`, `otherRefusalsAreFailuresWithTheirCodeAndRequestId`; Live `theLiveRefusalsMapToTheirOwnOutcomes` (every 422 carries a request id) | PASS |
| FR-024 plan before the password | `DeleteAccountViewModelTest.explainsBeforeAskingForThePassword...` | PASS |
| FR-025 New owners step content and order | `DeletionPlanServiceTest` (8), Live | PASS |
| FR-026 review step, no date of its own | screenshot `review`; code path (no date in the Review branch of `DeleteAccountScreen`) | PASS |
| FR-027 choices sent per offered wallet; scheduled shows server date/owners | `SuccessorChoicesTest` (5), `RequestDeletionUseCaseTest`, Live | PASS |
| FR-028 `/successors` pointer re-reads | Live `aLiveRefusedSuccessor...`; `RequestDeletionUseCaseTest.aSuccessorsPointerMeansTheWalletsChanged...` | PASS |
| FR-029 never queued/retried | `DeletionPlanServiceTest.offlineAndRefusalsAreOutcomesAndEachCallIsOneGet`, `RequestDeletionUseCaseTest...NothingIsRetried`, `DeletionContractTest.cancelOfflineIsReportedNotRetried` | PASS |
| FR-030 explanation text | `ExportDeletionStringsTest` | PASS |
| FR-031 pending view | `DeleteAccountViewModelTest` (pending group, 9 tests) | PASS |
| FR-032 being deleted; purging invisible | `HomeViewModelTest`, `DeletionPurgeSignOutTest` | PASS |
| FR-033 transactions | `TransactionAddedByTest`, Live | PASS |
| FR-034 rules | `RuleAuthorTest` | PASS |
| FR-035 activity unchanged | `ActivityMappingTest` (spec 013) | PASS |
| FR-036 error code + copy + `current` decoding | `ApiErrorCodeTest`, `ExportCopyTest`, `ExportContractTest.conflictCarriesTheActiveExportAsCurrent` | PASS |
| FR-037 body shape, schedule decoding, password redaction | `DeletionPlanContractTest` (5), `RequestDeletionUseCaseTest`, Live `theLiveRequestBodyHasOnlyThePassword...` | PASS |
| FR-038 design system, RTL, 200%, 48 dp, tags, never colour alone, announcements | screenshots EN/AR/2.0 for every screen; **GAP closed**: `ExportAccessibilityTest` (5); `ExportLayeringTest` (design-system only) | PASS |
| FR-039 MVI per screen | reducer + store tests per screen; `AppGraphTest.theExportAndDeletionServicesAndStoresResolve...` | PASS |

### 2.3 Success criteria

| SC | Evidence | Result |
|---|---|---|
| SC-001 3 taps from Account | `AccountViewModelTest.rowsNavigate...`, `ExportsViewModelTest.rowsAndNewNavigate`, `ExportRequestViewModelTest.defaultsToEverything...` (Submit enabled at once) | PASS |
| SC-002 one tap, every open uses a link read for that tap | `ExportDetailViewModelTest.openDownloadReadsAFreshLinkForEachTap...`; Live (4 requests: 3 watch reads + 1 tap read) | PASS |
| SC-003 no file, no link in storage/logs/crash reports | `ExportLoggingTest`, `ExportLayeringTest`, redaction tests | PASS |
| SC-004 polling stops everywhere | `ExportWatchTest`, `ExportDetailViewModelTest` | PASS |
| SC-005 every refusal has its own copy in both languages | `ExportCopyTest`, `ExportDeletionStringsTest.everySpec016KeyExistsInBothLanguages`; Live: every real refusal of section 3 maps to a named outcome | PASS |
| SC-006 choice before the password, preselected; no deletion without the plan | `DeleteAccountViewModelTest`, Live store test | PASS |
| SC-007 after a purge every transaction of the departed reads "former member" | Live `FormerMemberLiveReplayTest` over the real post-purge feeds | PASS |

### 2.4 Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Two taps | `oneRequestPerTapAndNoneWhileInFlight`; the second after a lost answer meets 409 with `current` (Live) | PASS |
| Ready export, new request | Live: a ready export did not block the next request (the account made 4 exports one after the other); `ExportDetailViewModelTest.failedAndExpiredOffer...` | PASS |
| Export id gone | `notFoundOnTheWatchStopsAndHidesTheExport`; Live `getUnknown`, `getForeign` | PASS |
| Ready without `download_url` | `ExportDetailViewModelTest.readyWithoutALinkKeepsReadyAndSaysSo...`, `ExportServiceTest.notReadyOrNoLinkOpensNothing` | PASS |
| Link expiry never reused | `everyLinkRequestMakesAFreshRead`; Live (same second: same link; a later read: a new signature) | PASS |
| No browser | `theBrowserFailureIsReportedWithoutTheLink` | PASS |
| App backgrounded | `startedWatchesAndStoppedCancelsWithNoReadsAfter`, `stillPreparingStops...` | PASS |
| Signed out while open | `signOutCancelsAndClears` | PASS |
| Deletion pending: exports keep working, date never moves | Live: export request while pending 202; the date identical across repeats | PASS |
| Plan lists a wallet the phone lacks | `DeletionPlanServiceTest.aWalletTheArchivedListHoldsIsNamedToo`, `noChoiceWhenThePhoneHoldsNoOtherMember` | PASS |
| Suggested successor not among the phone's members | `aSuggestionTheCandidatesLackBecomesASyntheticUnknownOne` | PASS |
| A candidate left meanwhile | Live `aLiveRefusedSuccessor...` | PASS |
| More than 100 choices | `SuccessorChoicesTest.atMostAHundredInPlanOrder` | PASS |
| Plan read after members changed | `DeletionPlanServiceTest.departedMembersAreNotCandidates`; pending view always reads a fresh plan (`openingWhilePending...`) | PASS |
| Deletion requested on another device | `DeleteAccountViewModelTest.startingWhileAnotherDeviceAlreadyAskedShowsThePendingView` | PASS |
| Sign-in during the grace period cancels | spec 002/007 behaviour (unchanged); not re-run live (see 3.8) | PASS (inherited) |
| Not an owner of any shared wallet | `withNoOfferedChoiceNoSuccessorsAreSent`; Live plan with no successors | PASS |
| A member's own deletion pending is invisible | no field, no code (spec Assumptions); nothing to test | PASS |
| Unknown reason/state | `DeletionPlanServiceTest.unknownReasonAndStateKeepTheirText`, `DeleteAccountViewModelTest.anUnknownPlanStateIsHandledAsPending...` | PASS |
| Unknown export status/failure | `ExportMappingTest`, `ExportCopyTest.failuresReadByCode...`, `ExportContractTest.everyExampleDecodesAnd...` | PASS |

## 3. Live contract QA (T074)

Environment notes: the local `*.test` DNS is flaky, so every call went to `http://127.0.0.1:80` with `Host: saveme-backend.test`; the
`download_url` the server returns names `http://saveme-backend.test/...` and was fetched the same way. Throwaway accounts
`qa016{own,par,edi,vie,oth,unv}...@example.com`. Verification by `dev tinker` (issue the link, `POST /auth/verify-email`), Plus granted
to the owner through the operator use case so that sharing works; Mailpit was not needed. The registration budget (10/hour per origin,
shared with the other agents) was hit; I waited out the window and did not clear it. Registering three users directly through the use
case in tinker was refused by the permission system, so I did not. `identity:purge-accounts` was **not** run: after checking that no
other account was due, each of my own accounts was aged and purged through `PurgeAccount` in tinker. Backend code and DB structure were
not touched.

Backend tree note: other agents' uncommitted spec 023 work was in the tree. Its `notifications` purge step (not in `776e5b2`) failed for
about 7 minutes (`push_registrations` not yet migrated), leaving my first account `purging` at step `subscriptions`; the purge resumed
by itself from that step once the table existed (a real-world proof that the purge resumes). Not a spec 022 defect.

### 3.1 Contract check

`check-backend-contract.sh` clean (T001/T075): every client `ApiErrorCode` matches the catalogue with the same retry class. The catalogue
has codes the client has no entry for (`INVALID_ID`, `INVALID_QUERY_PARAMETER`); they read as the general failure with the request id,
which is what the spec asks for unnamed refusals (Live `theLiveReadsFollow...`, `aListedPageSendsTwenty...`).

### 3.2 Exports

| # | Call | Real answer | Fixture | Replayed by |
|---|---|---|---|---|
| 1 | `POST /exports {scope: account}` | 202 `queued`, `wallet_id` null | `requestAccountQueued` | `theLiveAcceptedRequests...` |
| 2 | same at once | 409 `EXPORT_IN_PROGRESS`, `current` = the first export (`queued`) | `conflictAccountAgain` | `theLiveConflict...` |
| 3 | wallet scope while one is active | 409 with the account export as `current` (any scope) | `conflictWalletWhileActive` | same |
| 4 | wallet scope (owner / viewer) | 202 | `requestWalletQueued`, `requestWalletAsViewer` | `theLiveAccepted...` |
| 5 | 6th within 24 h | 429 `RATE_LIMITED`, `Retry-After: 86386` = `retry_after_seconds: 86386` | `rateLimitedSixth` | `theLiveSixth...`, store test (24 hours) |
| 6 | unknown wallet / non-member wallet | 404 `NOT_FOUND` both; while rate-limited the 404 still wins | `walletNotFoundUnknown`, `walletNotFoundNonMember` | `theLiveRefusals...` |
| 7 | bad scope, wallet scope without id, empty body, bad wallet id | 422 at `/scope` `INVALID_ENUM_VALUE`, `/wallet_id` `REQUIRED`, `/scope` `REQUIRED`, `/wallet_id` `INVALID_ID` | four fixtures | `theLiveRefusals...` |
| 8 | no credential | 401 `UNAUTHENTICATED` | `unauthenticated` | same |
| 9 | unverified account; account pending deletion; free plan | 202 (not gated) | `requestWhilePending` | `theLiveAccepted...` |
| 10 | `GET /exports/{id}` through the real worker | `queued` -> `running` -> `ready` in about 3 s; a tight poll caught all three | `getWalletQueued/Running/Ready` | `theLiveSequence...`, detail store test |
| 11 | ready, three reads | two reads in the same second return the identical signed link; a read a second later has a new signature | `getAccountReady` | `theLiveReadyExportOpens...` |
| 12 | after `expires_at` | `expired`, no `download_url`, no expiry of link | `getWalletExpired` | `theLiveReads...`, expired store test |
| 13 | unknown id / another person's id / malformed id | 404 / 404 / 422 `INVALID_ID` | `getUnknown`, `getForeign`, `getBadId` | `theLiveReads...` |
| 14 | `GET /exports?limit=20` | newest first, `meta {next_cursor, has_more, limit}` | `listTwo`, `listReadyAndExpired`, `listFiveReady` | `theLiveList...` |
| 15 | `limit=1` then `cursor=` | page 1 `has_more: true` with opaque cursor; page 2 ends it | `listPageOne`, `listPageTwo` | `theLiveList...`, list store test |
| 16 | `cursor=garbage` | 400 `INVALID_CURSOR` | `listInvalidCursor` | `theLiveList...`, list store restarts once |
| 17 | `limit=0` / `limit=500` | 400 `INVALID_QUERY_PARAMETER` / 200 clamped to 200 (the app always sends 20) | `listLimitZero`, `listLimitHuge` | `aListedPageSends...` |

The list never exceeded 20 in a real run (the largest account had 5 exports: the 5 per day bound), so "20 per page" is verified
by the request (`limit=20`) and by the paging shape with `limit=1`.

### 3.3 Signed file route (browser-like request; the app never fetches it)

`GET <download_url>` with the `Host` header and a Chrome-like user agent, no bearer: `200`, `Content-Type: application/zip`,
`Content-Disposition: attachment; filename=export-2026-10-06.zip`, 3066 bytes, `Cache-Control: no-store, public` (an odd pair; harmless).
A tampered signature: 404 HTML page. After the owner's purge the old link: 404. The client never requests this route
(`ExportLayeringTest`, `ManifestExportsLinkTest`).

### 3.4 Archive content

Account archive: `manifest.json`, `account.json`, `budgets.json`, `categories.json`, `devices.json`, `invitations.json`, `memberships.json`,
`occurrences.json`, `profile.json`, `recurring_rules.json`, `subscriptions.json`, `transactions.json`, `wallets.json`, `transactions.csv`
(UTF-8 with BOM; columns `date, wallet, wallet_id, kind, amount_minor, currency, category, ..., author, ...`). Wallet archive (as a
viewer): manifest, categories, occurrences, recurring rules, transactions (JSON and CSV), wallets. In the owner's archive with a partner,
an editor and a viewer in the wallets, **no other member's display name, address or id appears anywhere** (checked for all three), the
owner's own transactions read `me`, the partner's read `other_member`; in the viewer's archive the owner's name, address and id are absent.
This matches FR-018's wording ("other member", no names).

### 3.5 Deletion plan and request

| # | Call | Real answer | Fixture | Replayed by |
|---|---|---|---|---|
| 1 | plan with Alpha (one viewer), Beta (editor + viewer), Delta (two viewers), Solo | Alpha `only_member` / `choice_expected false`; Beta `oldest_editor` / true; Delta `oldest_viewer` / true; the solo wallet is not listed | `planActiveThreeWallets` | `theLiveActivePlan...` |
| 2 | request with a choice on the one-other-member wallet | 200, date = request + 30 days, `successors` with `reason` | `requestFirstScheduled` | `theLiveScheduledAnswers...` |
| 3 | repeat with the password only | 200, same date, stored choices kept | `requestRepeatPasswordOnly` | same |
| 4 | repeat with choices | 200, same date, Beta and Delta `chosen` | `requestWithChoices` | same, store test |
| 5 | repeat with a list naming only Beta | 200, same date; Delta falls back to `oldest_viewer` (the list replaces all choices) | `requestReplaceChoice` | same |
| 6 | plan while pending | state `pending_deletion`, requested/scheduled instants, reasons | `planPendingChosen`, `planPendingReplaced`, `planPendingNoChoices` | `theLivePendingPlans...` |
| 7 | missing password | 422 `/password` `REQUIRED` | `missingPassword` | `theLiveRefusalsMapToSpec002...` |
| 8 | wrong password with an invalid successor | 422 at `/successors/0/member_user_id`: validated before the password | `successorsBeforePassword` | same -> `SuccessorsChanged` |
| 9 | wrong password, valid body | 401 `INVALID_CREDENTIALS` | `wrongPassword` | same |
| 10 | successor not a member / wallet not owned / duplicate wallet | 422 pointers `/successors/0/member_user_id`, `/successors/0/wallet_id` (+member), `/successors/1/wallet_id` | three fixtures | same |
| 11 | `DELETE` | 204; plan afterwards `active` with suggested reasons (choices dropped); `DELETE` when nothing is pending also 204 | `cancelled`, `planAfterCancel`, `cancelWhenActive` | `theLiveCancel...`, `theLiveCancelledPlan...` |
| 12 | no credential | 401 on the plan | `planUnauthenticated` | `anAccountWithNoSharedWallet...` |

Store replay: `theDeleteStoreWalksTheLivePlan...` drives the real plan service, use case and store: Continue, New owners (Beta picked), Review,
password, Scheduled; the request carried two choices (Beta, Delta) and none for Alpha; the scheduled owners are the answer's.

### 3.6 Purge and the end state

* **`purging`**: sign-in of a `purging` account answers `401 INVALID_CREDENTIALS` with the same body as an unknown address (compared in
  `aSignInDuringPurgingAnswersExactlyAsForAnUnknownAddress`). I could not observe the real `purging` window (the purge is fast and ends
  sessions in its first step). With the state **forced by hand** while a session was still live: `DELETE` answered 204 and changed nothing
  (state stayed `purging`), and `GET /account/deletion-request` answered `state: "active"` with non-null requested/scheduled instants. Neither
  is reachable in real operation (every session ends in the same unit of work); noted for the backend, no client impact (the client treats
  `active` as active).
* **After the purge** (own account, real `PurgeAccount`): every call with the old token answered `401 UNAUTHENTICATED` (plan, wallets, export
  request, cancel), sign-in `401 INVALID_CREDENTIALS`, the account row is a husk (`state purged`), the account's export rows are gone and its old signed
  link is 404.
* **Ownership**: Alpha (sole viewer) -> the partner became owner (`only_member`); Beta -> the chosen viewer became owner and, because that
  person is on the free plan, the editor was demoted to viewer by the plan rule (nobody removed); Delta (choice replaced earlier) -> the older viewer;
  the solo wallet was deleted with the account.
* **Partners' feeds** (`GET /sync/wallets/{id}/changes`): the departed member's row is a tombstone (`deleted_at` set, `display_name` null, role
  stamp `viewer`/`editor` from the hand-over), the new owner's member row says `owner`, the wallet record's `my_role` for the partner is `owner`,
  transactions keep `created_by` (the departed id), balances unchanged; the partners' `GET /wallets/{id}/members` no longer lists the departed person.
  Replayed by `FormerMemberLiveReplayTest` (4 tests).

### 3.7 Behaviour worth knowing

1. A repeated request **without** `successors` keeps stored choices; **with** a list it replaces all of them (wallets not named revert to the
   suggestion). The client always sends one choice per offered wallet, so what the person last confirmed is what is stored.
2. A choice naming a wallet with exactly one other member is accepted (200) and ignored (`only_member`); the client never sends one.
3. Validation order confirmed: 404 (wallet) before 409 before 429 on `POST /exports`; `successors` before the password on deletion.
4. `choice_expected` stays true after a choice was sent; the client shows a made choice from `reason: chosen`.

### 3.8 Not exercised live

Sign-in during the grace period cancelling the deletion (spec 007/002, unchanged by this spec); the real `purging` window (above); a
`failed` export (no way to make the generator fail without touching the backend; the failure copy is proven by the contract examples and
`ExportCopyTest`); more than 20 exports (limited to 5 per account per day).

### 3.9 Clean-up

All six throwaway accounts were purged by the feature itself (`state purged`); the shared wallets were deleted through the API first;
no other account was touched. No bearer credential, password or real signature is committed: the fixtures carry fake signatures of the
right shape and the throwaway `example.com` addresses.

## 4. Fixes made by this QA

* `ExportAccessibilityTest` (FR-038) and `FormerMemberLiveReplayTest` (T063) added (gaps above).
* Three captured fixture files and three replay tests (`ExportLiveReplayTest` 14, `DeletionPlanLiveReplayTest` 12, `FormerMemberLiveReplayTest` 4).
* Merge with `main`: strings, AppNavHost, `AppGraphTest` and the README table kept both sides; the wallet-detail and Home goldens were
  re-recorded because both features changed those screens (wallet detail now shows "Report" and "Export this wallet" together).
* No product code changed: the live run found no client defect.
