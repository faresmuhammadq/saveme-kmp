# QA report: Sharing & Invitations (spec 013)

**Status**: host QA done; live-backend contract QA (T117) and captured-answer comparison (T118) done on 2026-10-05 against `saveme-backend@26e7c44` (`sharing.yaml` v1.9; the working tree also holds backend spec 021 work in progress, which touched none of these endpoints). About 290 HTTP calls, 7 throwaway accounts, one dev database (no reset, refresh or seed; backend code untouched). Real answers are in `CapturedSharingResponses.kt` (101 constants, tokens replaced by fake values of the same 43-character shape) and drive the real wire clients (`SharingCapturedWireTest`, 22 tests) and the real engine, `InvitationSyncHandler`, feed puller and appliers, and `DefaultSharingService` (`SharingCapturedResponsesTest`, 18 tests).

## Counts

| Group | Total | PASS | GAP closed in this QA | Open GAP | Deferred (device) |
|---|---|---|---|---|---|
| Acceptance scenarios (US1 8, US2 10, US3 5, US4 7, US5 5, US6 6, US7 6) | 47 | 47 | 0 | 0 | 0 (E2E flows written, T049 T062 T071 T081 T089 T097 T107) |
| Edge cases | 15 | 15 | 0 | 0 | 0 |
| Functional requirements FR-001..FR-036 | 36 | 36 | 1 (FR-005 clipboard flag had no test) | 0 | 0 |
| Success criteria SC-001..SC-007 | 7 | 6 | 0 | 0 | 1 (SC-001 timing, owner's device) |
| Section 4 invariants | 14 | 14 | 0 | 0 | 0 |

Gaps found by the mapping and closed: **G1** the Android clipboard copy marked sensitive and the share chooser had no test: `AndroidLinkSharerTest` added (2 tests). **G2** the live answers were never run through the real client: `SharingCapturedWireTest` and `SharingCapturedResponsesTest` added. **G3** (live finding, see below) the quickstart expected `422 IMMUTABLE_FIELD` for a replay with another role at version 0; the server answers `409 VERSION_CONFLICT` with the live copy (the client takes it: `PUT_REPLAY_OTHER_ROLE_VERSION_ZERO_409`, `aCreateRetriedAfterTheInvitationWasClosed...`). **G4** the spec says the signed-out message appears "over the entry screen"; the app shows it on the invitation screen (which opens over the entry screen) with Sign in / Create account buttons. Same intent, wording of the spec amended in the summary; no code change.

## Live contract QA (T117)

Accounts registered through `POST /auth/register` (202; the 10 per hour counter was not hit), verified in `dev tinker`-equivalent artisan tinker (`docker exec saveme-backend-main php artisan tinker`; `dev status` hangs on this machine), signed in through `POST /auth/login`. OWN: Plus (`OperatorSubscriptions::grant`, UUID operator id), owner of "Home". ED: Free, invited by email, editor. UNV: not verified, joined by link (viewer), removed, rejoined, left. VW: Free, declines and mismatched-address checks. FREE: Free owner. RL and RT: rate-limit accounts. Plus caps people at 5 (`sharing.max_members_per_wallet`); Free has `sharing.enabled=false`, max 1. Every request carried the app's headers (`X-Client-Platform`, `X-Client-Version`, `Accept-Language`, `X-Correlation-Id`); bodies equal what the clients build (asserted in the wire tests against the live request). The API was reached on `http://127.0.0.1:43871/api/v1` (the `saveme-backend.test` name stopped resolving during the run; same server). Mailpit held the invitation mail ("OWN QA invited you to join a shared wallet ... as an editor": link `http://saveme-backend.test/invite?token=<43 chars>`, "works once, expires in 7 days"; a verify mail and an invitation mail use different tokens).

### Invitation writes (`PUT/DELETE /wallets/{id}/invitations/{iid}`)

| Call | Live answer | Client handling |
|---|---|---|
| Create email, editor, v0 | 201, `status pending`, `channel email`, address echoed, `expires_at` +7 days, `meta {}` | stored, no link |
| Replay identical | 200, same copy, `meta {}` | accepted |
| Create link, viewer, `invitee_email: null` | 201, `channel link`, `meta.share_url` (`.../invite?token=` + 43 chars) **only here** | link to memory holder |
| Replay link | 200, `meta {}` (no link) | no link offered |
| Same id, other role, version 0 | **409** `VERSION_CONFLICT` + live `current` (quickstart assumed 422) | takes the copy |
| Same id at version 1, other role / other address | 422 `IMMUTABLE_FIELD` at `/role` / `/invitee_email` | refusal fields |
| Same id at version 1, same body | 200 | accepted |
| Lost-answer retry (same body, v0) after the invitation was withdrawn | 409 + `current` tombstone (`revoked`, address erased) | row removed, change dropped |
| Free owner (email and link) | 403 `FEATURE_NOT_ENTITLED`, `feature: sharing.enabled` | "Sharing isn't included" |
| Plus owner at the cap (owner + member + pending, incl. link) | 403, `feature: sharing.max_members_per_wallet` | "reached the number of people" |
| Expired invitations are not counted | cap full with 2 live pending; after aging them, create 201 | |
| Editor / viewer caller | 403 `FORBIDDEN` | "Only the owner can invite" |
| Unverified owner (and unverified member caller) | 403 `EMAIL_NOT_VERIFIED` (checked before FORBIDDEN) | verify prompt |
| Non-member | 404 | |
| Role `owner` / bad address / 262-char address / missing address / unknown member / malformed id | 422 `INVALID_ENUM_VALUE /role`; `INVALID_TYPE /invitee_email`; `OUT_OF_RANGE /invitee_email`; `REQUIRED /invitee_email`; `UNKNOWN_FIELD`; 422 `INVALID_ID` | field errors |
| Invite to the owner's own address / to an existing member's address | **201 both** (the server does not refuse) | refused by the client rule (FR-003), nothing sent |
| New invitation to the same address (case-insensitive) | 201; the earlier one becomes `revoked` (tombstone) | supersede confirmation, then new |
| 21st create in 24 h (replays do not count) | 429 `RATE_LIMITED`, `retry_after_seconds 86394` + `Retry-After` | stays "Waiting to sync", retried after the wait |
| Withdraw `{version:1}` | 200 tombstone, `status revoked`, `deleted_at` set, address `null`, v2 | row removed |
| Withdraw again (v1 against closed v2) | 200 same tombstone | accepted |
| Withdraw stale (v5) on a live one | 409 + live `current` | clash accepted |
| Withdraw by editor | 403 `FORBIDDEN` | |
| Withdraw on a Free owner | 200 (never plan-gated) | |
| Withdraw of an expired-but-still-`pending` / swept `expired` invitation | 200 revoked / 200 same `expired` tombstone | |
| `GET .../invitations` (list), `GET .../{iid}` | 200; a closed one is a tombstone (not used by the client) | decode checked |

### Preview, accept, decline (`POST /invitations/*`, `{token}` in the body)

| Call | Live answer | Client handling |
|---|---|---|
| Preview of the mailed token | 200 `{invitation_id, wallet_id, wallet_name "Home", role, invited_by_display_name, expires_at, already_member false}` | Ready |
| Preview of a pending link by the wallet owner | 200, `already_member true` | "You're already in this wallet" |
| Preview by an unverified person | 200 | allowed (FR-015) |
| Unknown token / address mismatch / already used / declined / expired / wallet deleted, preview and accept and decline | **404 `NOT_FOUND`, byte-identical apart from `request_id`** (asserted) | one uniform message |
| Malformed / missing token | 422 `INVALID_TYPE` / `REQUIRED` at `/token` | "isn't a valid invitation" |
| Accept (email, link, unverified person) | 200 `WalletMember {id, wallet_id, member_user_id, display_name, role, version 1}` | member stored, wallet read from the start |
| Accept again by the same person | 200, same member row | wallet shown, no error |
| Accept while the owner was lapsed to Free | 403 `FEATURE_NOT_ENTITLED`, `feature sharing.enabled` (preview still 200) | "owner's plan doesn't allow..."; invitation not used up |
| Removed member accepts a new link | 200, **a new member row id** | see feeds |
| Decline / decline again | 204 / 204 | Declined |
| 31st token call in an hour (preview, accept, decline share the counter) | 429, `retry_after_seconds 3597` + `Retry-After` | "Try again in 60 minutes" (ceil), not retried |

### Members, leave, activity

| Call | Live answer | Client handling |
|---|---|---|
| `PATCH` role editor to viewer `{role, version:1}`; viewer to editor on Plus (v2) | 200 member v2 / v3 | stored |
| `PATCH` stale version | 409 + `current` member v2 | current stored, wallet re-read, "Look again" |
| `PATCH` owner row / role `owner` | 422 `IMMUTABLE_FIELD /role` | "Ownership moves only by transfer" |
| `PATCH` role `admin` | 422 `INVALID_ENUM_VALUE /role` | |
| `PATCH` by editor / non-member / non-member target / removed member | 403 `FORBIDDEN` / 404 / 404 / 404 | OnlyOwner / Gone |
| `PATCH` raise on a lapsed (Free) owner; lowering on Free | 403 `FEATURE_NOT_ENTITLED sharing.enabled`; 200 | NotEntitled by feature; lowering never refused |
| `DELETE` member `{version}` | 200 tombstone v2; again 200 same; stale 409 + current; by editor 403; owner row 403 | removed |
| `POST leave {version:4}` | 200 own tombstone v5; stale 409 + current; again 404; owner 403 `FORBIDDEN`; removed member 404 | Left / done / Changed / OwnerCannotLeave |
| `GET activity?limit=50` | 200, newest first, 18 entries: `created`, `removed`, `role_changed`, `invited`, `invitation_revoked`, `joined`; ids only, no amounts; the former member's entry has `actor_user_id` and `actor_display_name: null` | named from phone, else "A former member" |
| limit 2 then `cursor` | 200 with `next_cursor`; page 2 continues without repeats | paging |
| bad cursor | 400 `INVALID_CURSOR` | restart once |
| limit 0 | 400 `INVALID_QUERY_PARAMETER`; limit 51 is accepted (capped to 50) | client always sends 50 |
| non-member / after leaving | 404 | Gone (wallet re-read) |

### Feeds, demotion, expiry, access loss

- **`invitation` rows on the wallet feed**: live `pending` rows and tombstones (`deleted_at` set, address `null`) for `accepted`, `declined`, `revoked`, `expired`; a deleted wallet's feed carries the invitation tombstone (`revoked`) with the member and wallet tombstones. Replayed through the real puller and `InvitationFeedApplier`: only live pending rows are stored (`theOwnersLiveWalletFeedStores...`).
- **User feed**: own `wallet_member` live row (editor v3); after demotion the same row, viewer v4; after removal or leaving a tombstone (`deleted_at`) of the own row; the wallet feed of that person answers **404** (removal, leaving). Replayed: wallet purged with its invitations (`anOwnMembershipTombstone...`), `ScopeRead.Done(lost = true)` for the 404.
- **Plan demotion**: lapsing OWN's Plus (subscription set `expired` and `SubscriptionLapsed` fired in tinker, queued `DemoteOnLapse` ran): ED became **viewer v4** by an ordinary `wallet_member` row on the wallet feed and on ED's user feed; the activity gained `role_limited_by_plan` (null actor, role `viewer`). The demoted viewer's write answers 403 `FORBIDDEN` (the copy `refusal_now_viewer`, TransactionCopyTest). A Free owner is refused a raise (403 sharing.enabled), may still lower and withdraw. Plus granted again for the rest of the run.
- **Expiry**: aging `expires_at` in tinker: preview and accept answer 404 at once; `GET` still shows `status pending` until `sharing:expire-invitations` ("Closed 2 expired invitation(s)"), then a tombstone `expired` (version 2). The phone derives "Expired" from `expires_at` (MembersTest).
- **Rejoin (observation)**: a removed member who accepts a new link gets a NEW member row id. Their user feed then holds the old row's tombstone followed by the new live row. The client processes the tombstone first (purge), then the live row (followed again, read from the start): the end state is correct (wallet followed, one own row, position forgotten); it costs one re-download. Pinned by `aMemberRemovedAndTakenBack...`. Not changed (purging first is also the conservative choice for what a person may keep after losing access).
- **Backend findings (not defects of the client; no backend edit)**: (1) the server accepts an invitation to the owner's own address and to an existing member's address (201); the app refuses the first by rule and the second cannot be told apart on the phone for an unlisted member name. (2) A create replay with another role at version 0 is 409, not 422. (3) The dev default `SHARING_INVITATION_URL` is `http://saveme-backend.test/invite`: the mail and `share_url` links do not open the dev app (documented, quickstart). (4) `activity` limit 51 is accepted, not refused.

### Cleanup

Wallets created by the QA were deleted through the API (Home, Doomed, Third, Free's, the rate-limit and UNV's wallet). Throwaway accounts (`qa13+<run>-<name>@example.com`, 7) and their sessions, two device-session rows each, the invitation, member and activity rows of deleted wallets and the OWN subscription history remain in the dev database.

## Host QA: requirement to test

Test paths are under `shared/src/commonTest/kotlin/com/fares/saveme/` (CT) or `shared/src/androidHostTest/kotlin/com/fares/saveme/` (AH). `S` = `feature/sharing`.

## Acceptance scenarios

| Item | Requirement (short) | Evidence | Result |
|---|---|---|---|
| US1-AS1 | Invite by email/role, pending with address, role, expiry, sent | CT/S/domain/SharingServiceInviteTest `anInvitationQueuesOneChangeInTheWalletScopeWithVersionZeroAndAPlaceholder`, `anOnlineInvitationIsSentAndReportedAsDelivered`; CT/S/ui/members/MembersTest `theOwnerViewShowsRowsInvitationsAndAnEnabledInvite`; live: SharingCapturedResponsesTest `anEmailInvitationQueuedOnThePhoneIsSentOnce...` | PASS |
| US1-AS2 | Offline: "Waiting to sync", sent later | SharingServiceInviteTest `offlineDoesNotBlockAndTheInvitationShowsAsWaiting`; InvitationSyncHandlerTest `offlineAndFailuresPassThroughForTheEngine` | PASS |
| US1-AS3 | No sharing in plan: explanation + Plan screen, nothing sent | InviteGateTest `theOrderIsVerifiedThenSharingThenCount`; SharingServiceInviteTest `theGateAnswersInOrder...`; MembersTest `inviteOpensTheInviteScreenWhenAllowedAndExplainsWhenNot`, `seeYourPlanOnlyOpensThePlanScreen` | PASS |
| US1-AS4 | Wallet at plan's number of people (owner + members + unexpired pending) | InviteGateTest `theOwnerTwoMembersAnUnexpiredPendingAndAnUnsentPlaceholderAllowOneMoreOfFive`, `aLimitOfOneWithOnlyTheOwnerIsFull`; SharingServiceInviteTest `aFullWalletFreesTheEarlierInvitationsPlaceWhenSuperseding`, `theCheckFollowsEntitlementsAndWaitingWithdrawalsAreNotCounted`; live cap answers | PASS |
| US1-AS5 | Unverified owner: verify prompt, nothing queued | SharingServiceInviteTest `theGateAnswersInOrder...`; InviteTest `anUnverifiedOwnerGetsThePrompt`; live `PUT_UNVERIFIED_OWNER_403` | PASS |
| US1-AS6 | Same address pending: confirm, then send | SharingServiceInviteTest `aSecondInvitationToTheSameAddressNeedsConfirmationAndThenSupersedes`; InviteTest `supersedeAsksThenSendsTheConfirmedInvitation`; InviteValidationTest `aDuplicateAddressIsFoundIgnoringCaseAndSpaces`; live re-invite (old one revoked) | PASS |
| US1-AS7 | Server refusal: "Not sent" by code/feature, Discard / Try again | SharingServiceInviteTest `aRefusalByFeatureMapsToTheRightReason`, `discardRemovesThePlaceholderAndTheChangeAndTryAgainRequeues`; SharingCapturedResponsesTest `everyLiveCreateRefusalBecomesItsOwnReasonOnTheInvitation`; MembersTest `aLostLinkIsReplacedThroughTheInviteScreenAndRefusedOnesCanBeDiscardedOrRetried` | PASS |
| US1-AS8 | Editor/viewer see no owner controls | MembersTest `anEditorOrViewerSeesNoOwnerControls`, `ownerControlsNeverApplyToTheOwnersOwnRowOrToYourself`; live editor `GET invitations` 200 | PASS |
| US2-AS1 | Invitation screen: wallet, inviter ("Someone"), role, end date, Join/Decline/Not now | InvitationTest `aSignedInPersonSeesWhatTheLinkOffers`; SharingServiceJoinTest `aPreviewReadsTheWalletTheInviterAndTheRole`; live preview | PASS |
| US2-AS2 | Join: member, wallet opens "Getting the wallet…", in list | SharingServiceJoinTest `joiningStoresTheMemberRowRequestsAForcedReadAndClearsTheHeldInvitation`; InvitationTest `joiningOpensTheWalletAndNeverRepeatsOnItsOwn`; WalletDetailSharingTest `whileJoiningAMissingWalletIsNeverGone...`, `aJoinReadThatDidNotCompleteOffersTryAgain...`; live `anUnverifiedPersonJoinsAndTheLiveMemberRow...` | PASS |
| US2-AS3 | Decline with confirmation, "Invitation declined" | InvitationTest `decliningConfirmsFirstThenClosesWithTheMessage`; SharingServiceJoinTest `decliningClearsTheHeldInvitation`; HomeInvitationTest `aDeclinedInvitationIsSaidOnceOnHome` | PASS |
| US2-AS4 | Signed out: message, secret in memory, screen opens after sign-in | InvitationLinkRoutingTest `signedOutTheSecretIsHeldInMemoryAndTheScreenStillOpens`; InvitationTest `signedOutMakesNoCallAndAMalformedLinkNeverCallsTheServer`; SharingHoldersTest `aHeldInvitationIsReplacedByANewLinkAndClearedAtSignOut`; code AppNavHost.kt:141 (held invitation opened on the sign-in transition), InvitationScreen.kt:114 (message + Sign in / Create account); E2E deferred T071 (G4) | PASS (navigation by code path) |
| US2-AS5 | Already a member: "Open wallet", nothing sent | InvitationTest `openWalletOnlyWorksForAnAlreadyMember`; live `PREVIEW_ALREADY_MEMBER_200` | PASS |
| US2-AS6 | One uniform "can't be opened" for every cause | SharingServiceJoinTest `everyNotFoundIsOneUnavailableAnswer`; InvitationTest `everyPreviewAnswerBecomesAMode`; live `everyNotFoundCauseIsTheSameAnswer...` (9 causes byte-identical) | PASS |
| US2-AS7 | Owner's plan full: explanation, invitation not used up | SharingServiceJoinTest `theOwnersPlanBeingFullIsOneAnswerForBothFeatures`; InvitationTest `theOtherJoinAnswersBecomeModes`; live `ACCEPT_OWNER_PLAN_FREE_403` (preview still 200) | PASS |
| US2-AS8 | Unverified person may open, join, decline | live accept/preview by unverified (`anUnverifiedPersonJoins...`); SharingServiceJoinTest (no verify gate in service) | PASS |
| US2-AS9 | Offline: "needs a connection", Try again | SharingServiceJoinTest `offlineAndOtherFailuresAreNamedAndCarryTheRequestId`; InvitationTest `aJoinThatFailsKeepsTheScreenAndTryAgainRepeatsTheJoinOnly` | PASS |
| US2-AS10 | Other host/path/malformed token: nothing sent, "isn't a valid invitation" | InvitationLinkParserTest `anotherSchemeHostOrPathIsNull`, `aMatchingPathWithAMalformedSecretIsMalformed`; InvitationLinkRoutingTest `aMalformedSecretOpensTheInvalidModeWithoutHoldingAnything`, `linksThatAreNotOursAreIgnored`; InvitationTest (malformed link never calls) | PASS |
| US3-AS1 | Link invitation pending; Share/Copy on server answer; link never on screen | SharingServiceLinkTest `theServersLinkMakesTheShareTextAvailable...`; InviteTest `theLinkChannelWaitsThenBecomesReadyWhenTheLinkArrives`, `shareAndCopyEmitRedactedEffectsOnceTheLinkIsReady`; SharingAccessibilityTest `theLinkStatesAreWords`; live `aLinkInvitationHandsTheLiveLinkToTheMemoryHolder...` | PASS |
| US3-AS2 | Offline link: waits; Share/Copy once the answer arrives in session | InviteTest `offlineTheLinkWaitsForTheConnectionAndCanBeSharedFromMembers`; SharingServiceLinkTest | PASS |
| US3-AS3 | Lost link: "can't be shown again", Replace link | SharingServiceLinkTest `replacingWithdrawsTheOldLinkAndQueuesANewOneWithTheSameRole`; InviteTest `replacingALostLinkStartsInTheLinkChannel...`; MembersTest `aLostLinkIsReplaced...`; live: replay returns no link (`PUT_LINK_REPLAY_200`) | PASS |
| US3-AS4 | Share text: app name, sentence, link; no amount or wallet name | ShareTextTest (3); SharingStringsTest `theShareTextNamesNoWalletAndNoAmount`; captured test asserts "Home" absent | PASS |
| US3-AS5 | Copy marked sensitive | AH/S/AndroidLinkSharerTest `theCopyIsMarkedSensitive...` (new, G1) | PASS (gap closed) |
| US4-AS1 | Role change online only; offline "needs a connection" | SharingServiceMembersTest `aRoleChangeSendsTheStoredMemberVersionAndStoresTheAnswer`, `everyAnswerBecomesAMemberActionResult` (Offline); MembersTest `theMemberSheetChangesTheRoleAndWordsEveryAnswer`; live `theOwnersRoleChangesAndRemovalsFollowTheLiveAnswers` | PASS |
| US4-AS2 | Raise plan-checked, lowering never | SharingServiceMembersTest `aRaiseChecksThePlanAndALoweringNeverDoes`; live Free owner: raise 403, lower 200 | PASS |
| US4-AS3 | Remove with confirmation (privacy limit stated) | MembersTest `removingAsksFirstThenCallsOnce`; SharingStringsTest (copy keys); SharingServiceMembersTest `removingStoresATombstoneAndDropsTheRow`; live DELETE member | PASS |
| US4-AS4 | Withdraw: hidden at once, offline too, queued with version | SharingServiceMembersTest `withdrawingASentInvitationQueuesTheDeleteWithItsVersionAndHidesIt`, `withdrawingANeverSentInvitationRemovesItWithNothingSent`; MembersTest `withdrawingAsksFirst`; live withdraw (`aWithdrawalIsQueued...`) | PASS |
| US4-AS5 | Transfer ownership offered once another member exists | WalletDetailSharingTest `transferIsOfferedOnceASecondMemberExistsAndNothingWaits...`; demotion arrives as ordinary rows (live feed test `aDemotionArrivesAsAnOrdinaryOwnMemberRow...`) | PASS |
| US4-AS6 | Stale member: re-read, "Look again", nothing overwritten | SharingServiceMembersTest `everyAnswerBecomesAMemberActionResult` (409 stores current, wallet refresh); live stale PATCH/DELETE (`PATCH_ROLE_STALE_409`) | PASS |
| US4-AS7 | Owner's own row: no change/remove | SharingServiceMembersTest `theOwnRowAndTheOwnerRowAreRefusedLocallyAndNothingIsSent`; MembersTest `ownerControlsNeverApply...` | PASS |
| US5-AS1 | Leave: wallet and data leave the phone, "You left <wallet>" | SharingServiceLeaveTest `leavingSendsTheOwnMembershipVersionAndClearsTheWalletFromThePhone`; AH/S/AccessLostPurgeTest `aLeaveThatTheServerAnsweredWith200PurgesTheWallet`; live `leavingFollowsTheLiveAnswersAndRemovesTheWallet`; WalletDetailSharingTest `aWalletThatVanishesWithoutALeave...` | PASS |
| US5-AS2 | Waiting changes uploaded first; count in the confirmation | SharingServiceLeaveTest `theCheckUploadsFirstThenCountsWhatStillWaitsInTheWalletScope`; MembersTest `leavingChecksThenAsksThenLeaves...` | PASS |
| US5-AS3 | Owner: no Leave, note to transfer | SharingServiceLeaveTest `theOwnerAndOfflineAreTheirOwnChecks`; MembersTest `leavingOfflineOrAsOwnerNeverAsks...` | PASS |
| US5-AS4 | Removed / wallet deleted: purge on next sync, "no longer have access" | AccessLostPurgeTest (4 signals); live `anOwnMembershipTombstoneOnTheUserFeedRemoves...`, `aWalletFeedThatAnswers404MeansTheAccessIsLost`; WalletDetailSharingTest `aWalletThatVanishesWithoutALeaveSaysAccessLost...`; MembersTest `aWalletThatVanishesClosesTheScreenQuietly` | PASS |
| US5-AS5 | Repeat leave (404) = done, purge | SharingServiceLeaveTest `anAlreadyEndedMembershipIsDone`; AccessLostPurgeTest `aLeaveThatTheServerAnsweredWith404PurgesTheWalletToo`; live `LEAVE_REPEAT_404` | PASS |
| US6-AS1 | Activity newest first, actor, action, subject from the phone, time, paging | ActivityMappingTest (7), ActivityTest (reducer 3 + store 5), SharingServiceActivityTest `aPageIsMappedNamedAndSkipsWhatItDoesNotKnow`; live `theLiveActivityIsNamedFrom...`, `aPageFollowsTheCursor...` | PASS |
| US6-AS2 | Record not on phone: names the kind | ActivityMappingTest `subjectsAreNamedFromThePhonesCopyOrLeftToTheKindsWords` | PASS |
| US6-AS3 | Server actor "Automatically"; plan demotion wording | ActivityMappingTest `theActorIsTheServersNameThenThePhonesMemberNameThenAFormerMemberAndNull...`; live `aBadCursorRestartsFromTheFirstPageOnceAndAPlanDemotion...` (null actor, `role_limited_by_plan`) | PASS |
| US6-AS4 | Former member | ActivityMappingTest (same); live: former member entry decodes with no name (Actor.FormerMember) | PASS |
| US6-AS5 | Offline: "needs a connection", nothing stale | SharingServiceActivityTest `offlineAndFailureAreNamed`; ActivityTest `errorsOfferTryAgainWhichRepeatsTheRightRead` | PASS |
| US6-AS6 | Unknown action skipped | ActivityMappingTest `anUnknownActionOrSubjectTypeIsSkipped`; ActivityApiContractTest `anUnknownActionStillDecodesAndTheDomainSkipsIt`; live: all 15 backend actions known (ActivityAction.php vs `ActivityAction`) | PASS |
| US7-AS1 | Role in words for non-owned wallets | existing C4 list/detail role caption (T104, WalletDetail tests); WalletDetailSharingTest `membersAndActivityRowsOpenTheirScreens` | PASS |
| US7-AS2 | Viewer note (plain / plan), no write controls | WalletDetailSharingTest `theViewerNoteAppliesToViewersOnly...`, `aViewerCannotAddATransaction`; SharingServiceActivityTest `theNewestRoleChangeAboutThePersonDecidesThePlanNote`; live demoted viewer write 403 → `refusal_now_viewer` (TransactionCopyTest, RecurringCopyTest); screenshots wallet detail viewer notes | PASS |
| US7-AS3 | Owner note: plan allows N people | SharingServiceMembersTest `thePlanNoteIsThePlansMaxWhenPeopleExceedItElseNull`, `aMemberSeesNoPlanNote`; MembersTest `sheetConfirmBusyPlanNoteAndInvitationsAreCarried` | PASS |
| US7-AS4 | Role change on the server reaches every screen | WalletMembershipsTest `thePersonsOwnLiveRowSetsTheWalletsRoleWhenTheWalletExists`; live `aDemotionArrivesAsAnOrdinaryOwnMemberRow...` | PASS |
| US7-AS5 | Updated phone reads every wallet once (invitations appear) | AH/core/database/Migration9To10Test (3); InvitationsAfterUpgradeTest | PASS |
| US7-AS6 | Sign-out clears invitations and held secret | AH/feature/account/SignOutWipeTest (extended); SharingHoldersTest | PASS |

## Edge cases

| Item | Evidence | Result |
|---|---|---|
| EC-1 Closed/expired invitation leaves the list on the tombstone; "Expired" before the sweep; not counted; withdrawable | InvitationFeedApplierTest `aTombstoneOfEachClosedStatusRemovesTheRowUnlessAChangeWaits`; InvitationDaoTest `theLivePendingCountCountsUnexpiredAndNullExpiryAtAnInstant`; MembersTest (Expired mark); live aging, sweep, count and withdraw of an expired one | PASS |
| EC-2 Own address refused on the phone | InviteValidationTest `theOwnAddressIsRefusedIgnoringCase`; SharingServiceInviteTest `theFormatAndTheOwnAddressAreRefusedBeforeAnythingIsQueued`; live: the server accepts it (201), so the client rule is the only guard | PASS |
| EC-3 Link opened by another account: uniform message | live mismatched-address 404 identical to every other cause | PASS |
| EC-4 Same link twice / Join tapped twice: existing membership | live accept repeat 200 same row; SharingServiceJoinTest | PASS |
| EC-5 New link replaces the open invitation screen | AppNavHost.kt:150-153 (code path); SharingHoldersTest `aHeldInvitationIsReplacedByANewLink...` | PASS |
| EC-6 429: create waits; token calls "Try again in n minutes", not retried | live `aRateLimitedCreateStaysWaiting...`, `thePreviewAndTheJoinOutcomes...` (TooMany(60)); SharingServiceJoinTest `noTokenCallIsRetriedAutomatically` | PASS |
| EC-7 Wallet deleted while the screen is open: "can't be opened" | live accept/preview of a deleted wallet's invitation 404 (`ACCEPT_DELETED_WALLET_404`) | PASS |
| EC-8 Role change while the editor is open: existing refusal path | TransactionCopyTest `NoRightHere` → viewer copy; live demoted write 403 `FORBIDDEN` | PASS |
| EC-9 Queued change refused with FORBIDDEN after demotion: "You can now only view" + Discard | TransactionCopyTest, RecurringCopyTest (the key); live 403 | PASS |
| EC-10 Removed while changes wait: wallet stays until settled | AccessLostPurgeTest `aWalletWithAWaitingChangeToItsOwnRecordStaysUntilThatSettles`; spec 009 rule | PASS |
| EC-11 Only the owner: no transfer/leave, invite to share | WalletDetailSharingTest `transferIsOfferedOnceASecondMember...AndHiddenWithOnlyTheOwner`; MembersTest | PASS |
| EC-12 Activity member names: server name, phone, "A former member" | ActivityMappingTest; live null-name entry | PASS |
| EC-13 Queued invitation when the wallet is deleted/access lost: discarded | AccessLostPurgeTest (waiting invitation change dropped) | PASS |
| EC-14 Two phones create link invitations offline | per-record ids (UUIDv7); live: each create is its own 201 with its own one-time link (links 1-4) | PASS |
| EC-15 Share sheet dismissed: stays pending, Share remains | InviteTest `shareAndCopyEmitRedactedEffectsOnceTheLinkIsReady`; ShareLinks holder keeps the link (SharingHoldersTest `shareLinksOfferGetForgetAndClear`) | PASS |

## Functional requirements

| FR | Evidence | Result |
|---|---|---|
| FR-001 | SharingServiceInviteTest `anArchivedWalletAndANonOwnerAreNotOffered`, `aWalletMadeOnThisPhone...` InvitationSyncHandlerTest `aWalletMadeOnThisPhoneAndNotYetAcceptedHoldsTheCreateBack` | PASS |
| FR-002 | InviteGateTest (7), SharingServiceInviteTest `theGateAnswersInOrder...` | PASS |
| FR-003 | InviteValidationTest (6) | PASS |
| FR-004 | SharingServiceInviteTest (placeholder, v0); InvitationSyncHandlerTest `theRecordTypeIsInvitationAndItIsNeverABatchItem`; `theOutcomeMessageDoesNotDependOnWhetherTheAddressHasAnAccount`; live: one request per invitation | PASS |
| FR-005 | SharingServiceLinkTest, SharingHoldersTest, InvitationDaoTest `theEntityHasNoSecretLikeColumn...`, AndroidLinkSharerTest (clipboard flag, closes G1), SharingLoggingTest | PASS |
| FR-006 | ShareTextTest; SharingStringsTest | PASS |
| FR-007 | SharingServiceMembersTest (withdraw, never sent); SharingServiceLinkTest (link forgotten) | PASS |
| FR-008 | SharingServiceInviteTest `aRefusalByFeatureMapsToTheRightReason`, `theRefusalRecordKeepsTheFeatureAndRequestId`; RefusalFeatureTest; InvitationSyncHandlerTest `aClashIsAcceptedAndTheTombstoneRemovesTheRow`; live refusals; rate limit stays waiting | PASS |
| FR-009 | InvitationLinkParserTest (5), InvitationLinkRoutingTest (6); devtools/DevToolsModule.kt (Paste link accepts invitation links; PasteLinkViewModel takes the predicate; code path) | PASS |
| FR-010 | SharingLoggingTest `everyTypeThatCarriesASecretPrintsItRedacted`; InvitationApiContractTest `theTokenTravelsInTheBodyAndNeverInTheUrl`; InvitationTest `theStateHoldsNoSecret`; live `aPreviewSendsOnlyTheTokenInTheBody...` | PASS |
| FR-011 | InvitationLinkRoutingTest; SharingHoldersTest; AppNavHost.kt:141 | PASS |
| FR-012 | InvitationTest; SharingServiceJoinTest; live preview | PASS |
| FR-013 | SharingServiceJoinTest; InvitationTest; HomeInvitationTest `theCardShowsWhileAnInvitationIsHeld...`, `theCardOpensTheInvitation`; WalletDetailSharingTest | PASS |
| FR-014 | SharingServiceJoinTest (all codes), InvitationApiContractTest `everyHandledCodeIsReadByCodeAndTheFeatureMember`; live codes | PASS |
| FR-015 | live unverified preview/accept; no verify gate in `join`/`preview`/`decline`/`leave` | PASS |
| FR-016 | MembersTest (rows, marks), SharingPerformanceTest; MinuteTicker ticks the "Expired" state (SharingFixtures `ticks`, InvitationView.expired) | PASS |
| FR-017 | SharingServiceMembersTest `aRaiseChecksThePlan...`; live | PASS |
| FR-018 | SharingServiceMembersTest; MembersTest `removingAsksFirstThenCallsOnce` | PASS |
| FR-019 | SharingServiceLeaveTest (4); live | PASS |
| FR-020 | SharingServiceMembersTest `everyAnswerBecomesAMemberActionResult`, `aSecondActionForTheSameWalletWhileOneRunsIsBusy`; MemberApiContractTest `everyHandledAnswerIsReadByCode`; live | PASS |
| FR-021 | SharingServiceMembersTest `theOwnRowAndTheOwnerRowAreRefusedLocally...`; WalletDetailSharingTest (transfer) | PASS |
| FR-022 | WalletMembershipsTest (3); SharingServiceMembersTest; live demotion feed test | PASS |
| FR-023 | AccessLostPurgeTest; live purge tests | PASS |
| FR-024 | ActivityTest; WalletDetailSharingTest `membersAndActivityRowsOpenTheirScreens`; ActivityApiContractTest | PASS |
| FR-025 | ActivityMappingTest; SharingPerformanceTest; live `noAmount` assertion | PASS |
| FR-026 | SharingServiceActivityTest `aMissingWalletForcesAReadAndIsGone`; ActivityTest `aRestartedWalkReplacesTheListAndGoneClosesTheScreen`; live | PASS |
| FR-027 | WalletDetailSharingTest; SharingServiceActivityTest; copy tests | PASS |
| FR-028 | SharingServiceMembersTest `thePlanNoteIsThePlansMax...` | PASS |
| FR-029 | InvitationFeedApplierTest (6); live wallet feed through the puller | PASS |
| FR-030 | Migration9To10Test; MigrationTest; InvitationsAfterUpgradeTest; `10.json` exported | PASS |
| FR-031 | SignOutWipeTest; SharingHoldersTest | PASS |
| FR-032 | Contract tests of A1-A9; live: every request body equals the live request (wire tests), `Problem.feature` read (ProblemFeatureTest + live) | PASS |
| FR-033 | MembersTest/Store tests render from fakes of the DAO; SharingLayeringTest `uiImportsNoDataPackage...` | PASS |
| FR-034 | SharingLoggingTest (3); SharingServiceLinkTest `nothingTheServiceLogsContainsTheLink` | PASS |
| FR-035 | SharingStringsTest (8) | PASS |
| FR-036 | MembersTest `noEffectOrIntentOfferingAPurchaseExists`; SharingLayeringTest | PASS |

## Success criteria

| SC | Evidence | Result |
|---|---|---|
| SC-001 owner invites in under 30 s | tap count in the flow (InviteTest); timing needs a phone | Deferred (device, T049) |
| SC-002 two taps to join | InvitationTest (Join then wallet opens); live accept | PASS |
| SC-003 100 % of the wallet's data gone | AccessLostPurgeTest asserts every table, positions and waiting changes | PASS |
| SC-004 no secret, link or address in logs/stores | SharingLoggingTest; InvitationDaoTest; Migration9To10Test `noColumnCanHoldAnInvitationSecretOrALink`; fixtures hold only fakes (`noFixtureHoldsARealSecret...`) | PASS |
| SC-005 every refusal code has its own message in EN/AR | SharingStringsTest `theContractsKeysExistInBothLanguages`, `bothLanguagesHaveTheSameKeys`; live codes all mapped | PASS |
| SC-006 a viewer is never offered a write control and sees why | WalletDetailSharingTest `aViewerCannotAddATransaction`, viewer note tests | PASS |
| SC-007 same on every phone after a sync | feed appliers (live feed replays: invitations, members, own role) | PASS |

## Section 4 invariants (the "test that fails when broken" column)

| Invariant | Evidence | Result |
|---|---|---|
| Render only from the phone's copy | MembersTest, SharingServiceInviteTest `observe*` | PASS |
| Create/withdraw offline, per record, v0 / stored version | SharingServiceInviteTest, SharingServiceMembersTest, InvitationSyncHandlerTest; live bodies | PASS |
| Secret/link never stored, logged, shown, in a URL | SharingLoggingTest, InvitationApiContractTest, InvitationDaoTest, Migration9To10Test, live | PASS |
| Share text has no amount/wallet name | ShareTextTest, captured link test | PASS |
| Gate order verified, included, count | InviteGateTest, SharingServiceInviteTest | PASS |
| Raise plan-checked, lowering never | SharingServiceMembersTest; live Free owner | PASS |
| Loss of access purges everything | AccessLostPurgeTest; live feeds | PASS |
| Own member row sets wallet role | WalletMembershipsTest; live demotion feed | PASS |
| Branch on code/feature; no auto retry | SharingServiceJoinTest, MemberApiContractTest, InvitationApiContractTest; live | PASS |
| Uniform answer for every token 404 | SharingServiceJoinTest; live byte-identical answers | PASS |
| Only the flavor's `/invite` link, 43 chars | InvitationLinkParserTest, InvitationLinkRoutingTest | PASS |
| Migration forgets wallet positions only | Migration9To10Test, MigrationTest, InvitationsAfterUpgradeTest | PASS |
| Sign-out clears table and secrets | SignOutWipeTest | PASS |
| Activity: no amount, unknown skipped | ActivityMappingTest, SharingServiceActivityTest; live | PASS |
| No purchase action | MembersTest, SharingLayeringTest | PASS |

## Quality gates (T116)

`./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64 --rerun-tasks`: BUILD SUCCESSFUL, 85 tasks executed; 2853 host tests, 0 failed, 0 skipped. `./gradlew build allTests --continue` covers every module task (unit tests, Roborazzi, lint, assemble of the three flavors, iOS compiles): all pass except four tasks that fail on code this spec did not touch (iOS common-metadata compile of `Dates.kt`, iOS test compile of `TransactionsListPerformanceTest.kt`, and `lintDevDebug` `AppLinkUrlError`/`exported` at the existing spec-003 manifest lines). T116 therefore stays open (plan implementation notes).
