# QA report: Push Notifications (client), spec 017

Branch `017-push`, QA 2026-10-06 after merging `main` (specs 001-016 and the C13 reports / C15 export and deletion merge `9f7742f`).
Host tests only (owner policy: no emulator, Maestro or device run; no Firebase artifact anywhere). Backend: `saveme-backend@e8c308a`
plus the **uncommitted** spec 023 working tree (`docs/openapi/paths/notifications.yaml` v1.14; no spec 023 commit existed when QA ended).

## 1. Summary

| | Count |
|---|---|
| User-story acceptance scenarios (US1 10, US2 13, US3 10, US4 6, US5 4) | 43: 43 PASS (one of them device-observable only in part, see 3) |
| Functional requirements FR-001 to FR-046 | 46: 46 PASS (FR-037 is "flows written", not run; FR-046's Firebase wiring is the gated Phase 9) |
| Success criteria SC-001 to SC-009 | 9: 9 PASS on the host (SC-001/003 timing parts are bounded by virtual-time tests, not measured on a phone) |
| Edge cases | 33: 33 PASS |
| GAPs found | 4, all closed (section 6) |
| Live contract QA (T080 to T082) | about 100 real calls (incl. the rate-limit loops), 7 recorded pushes, 17 replay tests, 0 contract drift, 0 backend bugs |

## 2. Merge notes

`git merge main` conflicted in `AccountContract.kt` (`AccountDestination` now `Profile, Devices, ChangePassword, DeleteAccount, Plan,
Exports, Notifications, DevTools`), `AccountScreen.kt` (imports and rows of both sides, order Profile, Plan, Devices, Notifications
(provider only), Export my data, Change password, Developer tools (dev only), Delete account) and `AppNavHost.kt` (destination mapping
has `NotificationSettingsKey`, `DevToolsKey` and `ExportsKey`). `strings.xml` en/ar (1160 names each, no duplicate), `api.js`
(`node --check` clean), `Routes.kt`, `AppGraphTest.kt` and `AccountModule.kt` auto-merged. The only goldens that changed legitimately
(both features altered the Account screen) are `screen_push_account_rows_en|ar|en_fs2.png`: the new "Export my data" row between
Notifications and Change password; re-recorded after inspecting the compare image.

The export and deletion taps no longer fall back to the Account screen (spec 016 is merged): `PushTarget.Export(id)` opens
`ExportDetailKey(id)`, a malformed export payload opens `ExportsKey`, `deletion_reminder` opens `DeleteAccountKey`, which for a pending
account is the pending-deletion view with the date, the owners, "Export my data" and "Cancel deletion".

## 3. Acceptance scenarios

| Scenario | Evidence | Result |
|---|---|---|
| US1-1 register after sign-in, background | `PushCoordinatorTest.theTransitionToSignedInRequestsOneReconcilePerSignIn`; `PushRegistrationServiceTest.theFirstRegistrationSendsTheStoredSessionAndKeepsOnlyAFingerprint`; `AndroidAdaptersTest.theSchedulerEnqueuesOneUniqueJobThatNeedsANetwork`; Live `theRealRegisteredAnswer...`, `registeringAfterSignIn...` | PASS |
| US1-2 new push address registers | `PushRegistrationServiceTest.aNewAddressRegistersAtOnceEvenOnForeground`; `PushCoordinatorTest.aNewAddressAndAPermissionChangeAlwaysRequest`; `PushReceiverTest.aNewAddressIsHandedToTheCoordinator`; Live rotation `registeringAfterSignIn...` | PASS |
| US1-3 24 hours rule | `theSameAddressWithinADayIsNotSentAgainOnForeground`, `aDayOldRegistrationIsRefreshedOnForeground`, `otherTriggersRegisterAgainEvenWithinADay` | PASS |
| US1-4 offline wait; server wait honoured | `offlineAndServerErrorsRetryLaterAndWriteNothing`, `rateLimitedAsksToRetryAfter...`; `AndroidAdaptersTest.aRateLimitedRunSucceedsAndQueuesANewRunAfterTheWait`, `theWorkerPassesItsTriggerAndMapsEachOutcome`; Live `theRealTooManyRegistrationsWaitTheServersHeader...` (3564 s) | PASS |
| US1-5 explanation sheet at the three moments, once | `NotificationPromptGateTest` (4), `PushTapRoutingTest.theMomentsAreACreatedBudgetARuleCreateAndAJoinedWalletNeverAnEdit`, `RuleEditorTest.savingCreatesTheRuleAndCloses` / `anEditSaveAndACancelEmitOnlyClose`; screenshot `NotificationsScreenshotTest` (prompt) | PASS (the system dialog itself is device-only: T036 deferred) |
| US1-6 allowed later registers | `NotificationSettingsTest.aGrantedDialogAnswerUpdatesTheCardAndTellsTheJob`, `aChangeOfThePhonesStatus...`; `PushCoordinatorTest.aNewAddressAndAPermissionChangeAlwaysRequest`; `PushRegistrationServiceTest.permissionRevokedRemovesTheRegistrationOnceAndForgetsIt` (registers again) | PASS |
| US1-7 blocked later removes | `permissionRevokedRemovesTheRegistrationOnceAndForgetsIt`, `anUnregisterThatFindsNothingStillClears`; Live `theRealNoContentAnswers...`, `theRealNotFoundAndRateLimitOnDeleteAreHandledByTheJob` | PASS |
| US1-8 sign-out: bounded DELETE, then sign-out, discard, clear | `SignOutParticipantsTest` (4), `PushSignOutTest.theParticipantDeletesOnlyWhenThisSessionIsRegistered`, `aServerThatHangsIsCutByTheSignOutsTwoSecondBound`, `theCleanerCancelsTheShade...`, `theCleanerNeverWaitsForTheProviderToDiscard`; Live logout + queue run removed the row | PASS |
| US1-9 remote session end: clean up, no DELETE | `PushSignOutTest.aRemoteSessionEndRunsTheCleanerAndNeverTheDelete`; Live session ended from another session: row gone server-side | PASS |
| US1-10 NOT_FOUND: re-read once, else stop, never sign out | `notFoundWithAChangedStoredSessionRetriesOnceForIt`, `notFoundWithAnUnchangedSessionStopsUntilANewSessionOrAddress`, `aNewSessionClearsTheStopMarker`; Live `theRealNotFoundForAnotherSessionStops...` (both real 404 bodies, still signed in) | PASS |
| US2-1 occurrence screen, not confirm | `PushRouterTest.everyValidRowOpensItsScreenAfterItsRead`; `PushTapRoutingTest.everyTargetMapsToItsKey` (`confirm = false`); Live `everyRecordedPushRoutes...` (`occurrence_due`) | PASS |
| US2-2 summary opens recurring (all) | `PushRouterTest`, `PushTapRoutingTest.everyTargetMapsToItsKey` (`RecurringKey(null)`), `PushPayloadParserTest.everyKnownTypeParses...` (not producible on the dev server, section 5) | PASS |
| US2-3 budget | same + Live `everyRecordedPushRoutes...` (`budget_exceeded`) | PASS |
| US2-4 invitation notice, no call | `InvitationNoticeTest` (2), `NotificationOpenServiceTest.noReadMeansNoCallAtAll`; Live `invitation_received` recorded and routed | PASS |
| US2-5 role change / ownership: wallet after wallet read | `NotificationOpenServiceTest.eachReadRunsExactlyItsOwnForcedRead`; Live `member_role_changed` (role `viewer`) routes to `WalletDetailKey` with `PushRead.Wallet` | PASS |
| US2-6 member removed: read everything, list | `PushRouterTest`; Live `member_removed` routes `Wallets` with `PushRead.Everything` | PASS |
| US2-7 plan refresh and Plan | `PushRouterTest`, `everyTargetMapsToItsKey` | PASS |
| US2-8 export opens export by id | `PushTapRoutingTest.everyTargetMapsToItsKey` (`ExportDetailKey(id)`, malformed `ExportsKey`), `everyPushTheLiveBackendRecordedOpensItsScreen`; Live `export_ready` | PASS (was a GAP: Account fallback, closed) |
| US2-9 deletion reminder: refresh and deletion screen | `PushRouterTest` (`PushRead.Account`), `everyTargetMapsToItsKey` (`DeleteAccountKey`); Live `deletion_reminder` (data block carries no id) | PASS |
| US2-10 "Opening…" up to 10 s, Cancel; present record opens at once | `NotificationOpenServiceTest.aRecordAlreadyOnThePhoneOpensAtOnce...`, `anAbsentRecordWaitsForItsRead`, `offlineOrFailedStillOpensTheTarget`, `aReadThatNeverEndsOpensTheTargetAfterTenSeconds`; `NotificationOpenTest` (4) | PASS |
| US2-11 same screen from any state; Back returns | `PushTapRoutingTest.aTargetGoesOnTopSoAnEditedScreen...`, `homeTrimsBackToTheExistingHome...`, `theBusReplaysOneTapUntilItIsConsumed`; `AndroidAdaptersTest.theIntakeSubmitsOneTapAndStripsItsKeys...` | PASS |
| US2-12 unknown or malformed opens Home / list, no error | `PushPayloadParserTest.unknownTypeOtherSchema...`, `aKnownTypeWithAMissingOrNonUuid...`; `PushRouterTest.malformedKnownTypesFallBackToTheirListScreens`, `unknownOpensHomeWithNoRead` | PASS |
| US2-13 signed out: sign-in, tap forgotten | `PushTapRoutingTest.aTapWithNobodySignedInIsForgottenAnd...` (new, closes a GAP); `AppNavHost` collects after the auth transition | PASS |
| US3-1 defaults | `NotificationPreferencesServiceTest.loadMapsTheDefaultsToTheDomain`; `NotificationSettingsTest.openingReadsOnceAndShowsTheTimeZone`; Live `theRealDefaultsAreAllOn...` | PASS |
| US3-2 Save only when different | `anEditMakesItDirtyAndSaveable`, `saveDoesNothingWhenNotDirtyInvalidOrAlreadySaving` | PASS |
| US3-3 whole object with the read version | `saveSendsOneRequestWithEveryMemberAndTheLastReadVersion`, `twoEditsThenOneSaveSendsEverythingWithTheReadVersion`; Live `theWholeObjectTheAppSendsIsByteForByte...` | PASS |
| US3-4 stale version: stored values and message | `savedReplacesBothCopiesAndConflictReplacesTheEdits`, `aConflictReplacesTheEditsWithTheStoredValues`; Live `theRealStaleVersionAnswerCarriesCurrent...` | PASS |
| US3-5 offline open / save | `offlineAndFailureShowTheNotLoadedMessageAndTryAgainReloads`, `aFailedSaveKeepsTheEditsAndShowsTheMessage`, `validationOfflineAndOtherRefusalsAreNeverRetried` | PASS |
| US3-6 start equal to end | `startEqualToEndBlocksSave`; Live `theRealValidationRefusalsAre...` (the server refuses it too, also with quiet hours off) | PASS |
| US3-7 rate limit wait | `rateLimitMinutesRoundUp`, `rateLimitedKeepsTheWaitWhenThereIsOne`; Live `theRealRateLimitCarriesTheWaitTheScreenShows` (3579 s) | PASS |
| US3-8 discard dialog | `backWithEditsAsksFirstAndBackWithoutLeavesAtOnce` | PASS |
| US3-9 phone card: Turn on / Open settings | `turnOnAsksWhenAskableAndOpensSettingsWhenBlocked`; screenshots `states` | PASS |
| US3-10 system settings for this phone | `NotificationSettingsTest` (effect `OpenPhoneSettings`); `AndroidAdaptersTest` | PASS |
| US4-1 six channels in the app language, private | `AndroidNotificationDisplayTest.sixChannelsWithTheCategoryIds...`, `aSecondCallRenamesWithoutDeleting`; `ChannelLabelsTest` (4) | PASS |
| US4-2 foreground message shown as received, background read | `PushReceiverTest.aKnownTypeIsPostedWithTheReceivedText...`, `theReadIsTheRoutesScopeForced`; Live `everyRecordedPushIsShownOnItsChannel...` | PASS |
| US4-3 same subject replaces | `PushRouterTest.collapseKeysFollowTheContractTable`, `AndroidNotificationDisplayTest.theSameTagReplacesAndADifferentOneAdds`; Live: the app's tag equals the server's stored collapse key for all 7 recorded types | PASS |
| US4-4 same message twice shown once | `PushReceiverTest.theSameNotificationIdTwiceIsShownOnce`; `PushRecordsStoreTest.shownIdsKeepTheNewestHundredAndDeduplicate`; Live (second delivery of a recorded push) | PASS |
| US4-5 lock screen shows no text | `AndroidNotificationDisplayTest.postSetsChannelTagAutoCancelPublicVersion...`, channels private | PASS (actual lock screen device-only: T066 deferred) |
| US4-6 cleared at sign-out / session end | `PushSignOutTest.theCleanerCancelsTheShade...`, `aRemoteSessionEndRuns...`; `AndroidNotificationDisplayTest.cancelAllClearsEverythingTheAppPosted` | PASS |
| US5-1 13 rows, ids from the phone | `SimulatePushTest.thirteenRowsAndTheOnesWithInventedIdsAreLabelled`, `idsComeFromThePhone...` | PASS |
| US5-2 Post goes through the real path and routes | `SimulatePushTest.postHandsTheContractData...`, `everyBuiltPayloadParsesToItsOwnType...` | PASS |
| US5-3 dev build registers a fake address | `DevFakePushTokenProviderTest.aStableTokenInTheBackendsFormat`; Live: `dev-fake-push-<uuid>` accepted (200) | PASS |
| US5-4 staging/prod: no tool, no fake | `AppGraphTest` (provider is `UnavailablePushTokenProvider` when `isDebugTools = false`; dev binds the fake); `AccountViewModelTest.theNotificationsAndDeveloperRowsShowOnlyWhenTheBuildHasThem` | PASS |

## 4. Functional requirements and success criteria

| Item | Evidence | Result |
|---|---|---|
| FR-001, FR-009 | `PushRegistrationServiceTest.theFirst...`, `registrationNeedsNeitherAVerifiedEmailNorAPlan`; Live (registered with a verified, Free account) | PASS |
| FR-002 | the 24 h / new address / permission tests above | PASS |
| FR-003 | `AndroidAdaptersTest` (unique work, network constraint, outcome mapping); `PushRegistrationServiceTest` (stored session at run time, nobody signed in) | PASS |
| FR-004, FR-008, SC-005 | `PushRecordsStoreTest.noStoredValueEqualsOrContainsThePushAddress`; `PushLogCaptureTest`; `NotificationsApiTest.theRequestDtoNeverPrintsTheAddress`; replay test asserts no stored value holds the address | PASS |
| FR-005 | NOT_FOUND tests above | PASS |
| FR-006 | `permissionRevokedRemovesTheRegistrationOnceAndForgetsIt` | PASS |
| FR-007, SC-008 | `SignOutParticipantsTest`, `PushSignOutTest` | PASS |
| FR-010, SC-002 | `NotificationPromptGateTest`; code: the permission is requested only from the sheet's "Turn on" and the screen's "Turn on" | PASS |
| FR-011 | `NotificationPromptGateTest`, `PushTapRoutingTest.theMoments...` | PASS |
| FR-012 | `NotificationSettingsTest.turnOnAsksWhenAskableAndOpensSettingsWhenBlocked`; `AndroidAdaptersTest.onAndroid12AndEarlier...` | PASS |
| FR-013 | `AccountViewModelTest.theNotificationsAndDeveloperRowsShowOnlyWhenTheBuildHasThem`; merged Account screen golden | PASS |
| FR-014 | `NotificationSettingsTest.loadingThenReady`, `offlineAndFailure...`; nothing cached | PASS |
| FR-015 | screenshots `states`; `PushStringsTest` | PASS |
| FR-016 | `TimeOfDayFormatTest` (3), `NotificationPreferencesServiceTest.quarterHoursParseAndPrintAndRefuseTheRest`, `startEqualToEndBlocksSave`; Live replay (validity ignores the quiet-hours switch, as the server does) | PASS |
| FR-017 | `saveDoesNothingWhenNotDirtyInvalidOrAlreadySaving`, `validationOfflineAndOtherRefusalsAreNeverRetried` | PASS |
| FR-018 | `aConflictReplacesTheEdits...`, `rateLimitMinutesRoundUp`, `otherRefusalsMapToTheirMessages`; Live replays | PASS |
| FR-019, FR-020, FR-021 | `backWithEdits...`, `turnOnAsks...`, phone-settings effect | PASS |
| FR-022 | `AndroidNotificationDisplayTest` channel tests, `ChannelLabelsTest.theChannelsEffectRunsAtStartAndAgain...`, `aBuildWithoutAProviderCreatesNoChannels` | PASS |
| FR-023 to FR-028 | `PushReceiverTest` (10), `AndroidNotificationDisplayTest`, Live `everyRecordedPushIsShown...` | PASS |
| FR-029, FR-033 | `PushTapRoutingTest` (bus, signed out, update screen, replace), `NotificationOpenServiceTest.noReadMeansNoCallAtAll`, `AndroidAdaptersTest` intake | PASS |
| FR-030, FR-044 | `PushPayloadParserTest` (8), `AndroidAdaptersTest.tapExtrasKeepOnlyContractKeysWithStringValues`, `aLauncherIntentWithoutATypeAndAnIdIsNotATap` | PASS |
| FR-031, SC-003 | `PushRouterTest`, `PushTapRoutingTest`, Live recorded pushes | PASS |
| FR-032 | `NotificationOpenServiceTest` (6), `NotificationOpenTest` (4) | PASS |
| FR-034 | `InvitationNoticeTest`; strings | PASS |
| FR-035, FR-036 | `DevFakePushTokenProviderTest`, `SimulatePushTest`, `AppGraphTest`, `SimulatePushScreenshotTest` | PASS |
| FR-037 | 25 flows under `.maestro/017-push/`, each `# deferred: E2E on owner's device`; none run | PASS (written; runs deferred) |
| FR-038 | `PushLogCaptureTest`, `PushRouterTest.aRouteAndATargetPrintTheTypeOnly`, `PushReceiverTest.theRedactedTypesNeverPrintTheirContent` | PASS |
| FR-039 | `NotificationsApiTest.registerSendsExactlyThePushToken...`; Live: the real server refuses any other member with `UNKNOWN_FIELD`, so the exact body matters | PASS |
| FR-040 | screenshots EN/AR/2.0 for every screen and sheet; code: live regions, headings, test tags; TalkBack announcement is device-only | PASS (host-verifiable part) |
| FR-041 | `PushStringsTest` (4) | PASS |
| FR-042 | `:shared:compileKotlinIosSimulatorArm64` in the gate; iOS no-ops | PASS |
| FR-043 | `PushSignOutTest`, `PushRecordsStoreTest.anotherPersonStartsAnEmptyListAndClearForgetsAll` | PASS |
| FR-045, FR-046, SC-009 | `NotificationsLayeringTest` (9 incl. no Firebase import, no Firebase dependency declared), devDebug runtime classpath has no Firebase artifact (section 8) | PASS |
| SC-001 | registration path tests with the unique network-constrained job | PASS (bounded, not timed on a phone) |
| SC-004 | `twoEditsThenOneSaveSendsEverythingWithTheReadVersion` | PASS |
| SC-006 | `PushSignOutTest`, `PushReceiverTest.signedOutIsDropped` | PASS |
| SC-007 | `AndroidNotificationDisplayTest.theSameTagReplaces...`, `PushReceiverTest.theSameNotificationIdTwiceIsShownOnce` | PASS |

## 5. Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| No provider in this build | `ChannelLabelsTest.aBuildWithoutAProviderCreatesNoChannels`, `NotificationPromptGateTest`, `PushRegistrationServiceTest.noProviderDoesNothing`, `AppGraphTest` | PASS |
| Two people share a phone | `PushSignOutTest` cleaner, `PushRecordsStoreTest.anotherPersonStartsAnEmptyList...`, `aNewSessionClearsTheStopMarker` | PASS |
| Sign-out offline | `PushSignOutTest.aServerThatHangsIsCutByTheSignOutsTwoSecondBound`, `anyAnswerIsIgnored`, `theCleanerNeverWaitsForTheProviderToDiscard` | PASS |
| Tap after sign-out | `PushTapRoutingTest.aTapWithNobodySignedIn...` | PASS |
| Language change keeps shown text; new ones follow the profile | channels renamed only (`ChannelLabelsTest`); text is the received text (`PushReceiverTest`) | PASS |
| Session id answered 404 | US1-10 tests | PASS |
| Registration rate limit | `rateLimitedAsksToRetryAfter...`; Live 429 + `Retry-After` | PASS |
| Permission revoked while signed in | US1-7 | PASS |
| Channel blocked by the person | no code path changes anything (nothing to test) | PASS |
| Unknown type / `schema` other than "1" | `PushPayloadParserTest.unknownTypeOtherSchema...`, `PushReceiverTest.anUnknownTypeWithTextIsPostedUnderAnOtherTag...` | PASS |
| Identifiers missing or not UUIDs | `aKnownTypeWithAMissingOrNonUuidNeededIdIsMalformedNotUnknown`, `malformedKnownTypesFallBackToTheirListScreens` | PASS |
| Occurrence already confirmed/skipped, wallet gone, budget deleted, export expired, deletion cancelled | the target screens' existing states (specs 006, 010, 012, 016, 002); the tap only navigates (`noReadMeansNoCallAtAll`) | PASS |
| Tap while another "Opening…" runs | `PushTapRoutingTest.aNewerTapReplacesAnOpeningScreenStillShowing`, `NotificationOpenTest.startingTwiceOpensOnce` | PASS |
| Tap while update required | `PushTapRoutingTest.aTapWithNobodySignedIn...AndOneUnderTheUpdateScreenIsDropped` | PASS |
| Tap while on a screen with unsaved edits | `aTargetGoesOnTopSoAnEditedScreenStaysUnderneath` | PASS |
| Many notifications at once | same-subject replacement (SC-007); the app adds no summary (`PushReceiverTest`) | PASS |
| Preferences changed on two phones | US3-4; Live real 409 | PASS |
| Quiet hours across midnight or not | `quarterHoursParse...`; Live replay `isValid` for 22:00-08:00 and 13:00-15:00 | PASS |
| Profile time zone differs | `openingReadsOnceAndShowsTheTimeZone`, `theTimeZoneNoteIsAbsentWhileTheProfileIsNotOnThePhone` | PASS |
| Account pending deletion | nothing gates registration or the screen (`registrationNeedsNeitherAVerifiedEmailNorAPlan`); Live: the reminder user was pending deletion and registered | PASS |
| Process killed while pending | `AndroidAdaptersTest.theSchedulerEnqueuesOneUniqueJobThatNeedsANetwork` (WorkManager unique work) | PASS |
| Notification while nobody signed in | `PushReceiverTest.signedOutIsDropped`; tap case above | PASS |
| Large font / RTL | screenshots EN/AR/font 2.0 for the screens, sheet, field, tool | PASS |
| Spoofed tap | `AndroidAdaptersTest.tapExtras...`, parser validation, tap never acts | PASS |

## 6. GAPs found and closed

1. **Tap intake had no test** for the signed-out, update-required and "newer tap replaces Opening…" rules (tasks T040 named them, the file did
   not hold them). The logic lived inline in `AppNavHost`; it is now `MutableList<NavKey>.openPushTap(route, signedIn, nextNonce)` in
   `PushTapRouting.kt` (behaviour unchanged) with three tests in `PushTapRoutingTest`.
2. **Export taps opened the Account screen** "until spec 016 merges". Now `ExportDetailKey(id)` / `ExportsKey`; tests updated.
3. **T082 replay**: no test ran the real answers through the wire clients and services. `NotificationsLiveReplayTest` (17 tests) and
   `CapturedPushResponses.kt` added.
4. **Account screen goldens** stale after merging C15's "Export my data" row: three goldens re-recorded.

## 7. Live contract QA (T080, T081, T082)

**T080.** Backend spec 023 was **not committed** at the end of QA (`git -C saveme-backend log -1` is still `e8c308a`; `app/Modules/Notifications/`,
`docs/openapi/paths/notifications.yaml`, `config/notifications.php`, ADR-0025 untracked). `check-backend-contract.sh` reports no error-catalogue
change and one watched-file change, `docs/api-conventions.md` (the two sections this spec already cites: "A versioned `/me` resource that is not
syncable", "The device id on `/me/devices/{id}`"). The path file still says v1.14 (the root `openapi.yaml` info now says v1.15). No contract drift.
Baseline stays `e8c308a` plus the uncommitted tree; re-check once spec 023 lands.

**Environment.** `*.test` DNS was not used: `http://127.0.0.1:80/api/v1` with `Host: saveme-backend.test`, headers
`X-Client-Platform: android`, `X-Client-Version: 1.0.0`. Throwaway accounts `qa017a|b|c<epoch>@example.com`, verified from Mailpit (no tinker
needed for that; 3 of the 10 registrations per hour used, none cleared). The `log` push gateway only (the worker reported `push_logged`); no fcm
configuration, no FCM request. Direct changes to the dev database, all on my own throwaway rows: Plus granted to account A with the operator use
case `OperatorSubscriptions::grant` (needed to invite someone), and `users.deletion_scheduled_for` of account C moved to now + 2 days so that
`identity:send-deletion-reminders` would pick it (the real grace is 30 days). Nothing else was written, reset, seeded or edited. The dev server has no
queue worker, so deliveries were pushed through with `dev queue:work --queue=notifications,default --stop-when-empty`.

**Calls and real answers** (verbatim bodies in `CapturedPushResponses.kt`):

| Call | Real answer |
|---|---|
| `PUT /me/devices/{current session}` `{"push_token":"dev-fake-push-..."}` | 200 `{"data":{"session_id","registered_at","refreshed_at"},"meta":{}}`; the token is never returned; `Ratelimit-Limit: 20` |
| same again | 200, `refreshed_at` moves, `registered_at` stays |
| another token (rotation) | 200, same shape, `registered_at` unchanged |
| another account's session id, a random uuid | 404 `NOT_FOUND` (`{"type":".../not-found","code":"NOT_FOUND","request_id"}`) |
| body with `platform` | 422 `VALIDATION_FAILED`, `errors:[{"pointer":"/platform","code":"UNKNOWN_FIELD"}]` |
| `push_token: ""` | 422, pointer `/push_token`, code `OUT_OF_RANGE` |
| no bearer | 401 `UNAUTHENTICATED` |
| 21st registration write of one session within the hour | 429 `RATE_LIMITED`, `Retry-After: 3564`, `retry_after_seconds: 3564`, `Ratelimit-Remaining: 0`; the limit is per session and shared with `DELETE` (a `DELETE` right after got 429, `Retry-After: 3558`); a second session of the same account had its own 20 |
| the same token registered by account B | 200; account A's row for that token is gone (read-only table check), B's session holds it |
| the same token registered by a second session of account A | 200, it moved to the new session |
| `DELETE /me/devices/{session}` | 204 no body; again 204; for a session whose token had moved 204; another account's session 404; no bearer 401 |
| `GET /me/notification-preferences` (first read) | 200 version 1, all six categories true, quiet hours `{enabled:true,start:"22:00",end:"08:00"}`, `updated_at: null` |
| `PUT` whole object `version:1` | 200 version 2 with the stored values and `updated_at` |
| `PUT` with `version:1` again | 409 `VERSION_CONFLICT` with `current` (version 2, the saved values) |
| `start == end` | 422, pointer `/quiet_hours/end`, code `OUT_OF_RANGE`; refused **also with `enabled:false`** |
| `22:10` | 422, pointer `/quiet_hours/start`; unknown member `/foo` `UNKNOWN_FIELD`; no `version` `/version` `REQUIRED` |
| 31st save in the hour | 429, `Retry-After: 3579`, `retry_after_seconds`; every `PUT` attempt counts (409 and 422 included), `GET` is not limited |
| `POST /auth/logout`; session ended from another session | 204; the registration row disappeared once the `notifications` queue ran (the listener `DropRegistrationOfEndedSession` is queued, so the row outlives the sign-out for a moment) |

**Recorded pushes** (`log` gateway, then the `push_notifications` row, `state: sent`): `invitation_received` (invitation to a verified registered
address), `member_role_changed` (`role: viewer`), `member_removed`, `export_ready` (`POST /exports`), `occurrence_due` (`notifications:occurrence-reminders`),
`budget_exceeded`, `deletion_reminder` (`identity:send-deletion-reminders`, data block has no identifier). The server honours preferences: the first
`budget_exceeded` for the user who had switched budgets off was stored `skipped` with `category_off`; a second user got it `sent`. Not producible on
the dev server in the time and without edits: `occurrences_due_summary`, `occurrence_auto_confirm_refused`, `wallet_ownership_received`,
`subscription_ending`, `subscription_lapsed`; they stay covered by `PushPayloadParserTest` from the contract's examples.

**Replay (`NotificationsLiveReplayTest`, 17 tests, all green).** The real wire clients (`ApiClient`, `NotificationsApi`), `PushRegistrationService`,
`NotificationPreferencesService` and the receiving path (`PushPayloadParser`, `PushRouter`, `PushReceiver`) are driven by those answers:
the registered shape decodes and refreshes only move the time; both real 404 bodies stop registration without signing out; the real 422s stop;
the real 429 header gives `RetryAfter(3564)` and `RateLimited(3579)`; the 204s read as success and a real 404 on `DELETE` still clears the record;
the real defaults, save, stale-version answer (`Conflict` with `current`), the four 422 bodies (`Invalid`) and the post-save read; the byte-exact
body the app builds equals the one the live server accepted. For each of the 7 recorded pushes the app parses the data, routes it as the table says,
posts it on the data's channel under a tag equal to the server's stored collapse key with only contract keys (an extra `google.message_id` dropped),
starts the right forced read, and shows a repeat once. `PushTapRoutingTest.everyPushTheLiveBackendRecordedOpensItsScreen` maps each to its key.

**Findings.** No backend bug and no client-visible contract drift. Observations worth knowing: the 200 body has no `registered`/`rotated`
discriminator (the yaml names are example names; the client does not need one); start equal to end is refused even when quiet hours are off
(the client already blocks Save in both cases); the preferences `PUT` rate limit counts refused attempts; removal on sign-out is queue-delayed on
the server.

**Clean-up.** Every QA session was signed out and the queue run, so none of my registrations remain. Account C is pending deletion (scheduled in two days;
the daily purge will remove it). Accounts A (with a 30-day Plus grant) and B stay as throwaway rows; one `export` row, one wallet and one budget exist for them.
The temporary credentials were only kept in the session's scratch directory, which was deleted.

## 8. Gates

- `./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64 --rerun-tasks --max-workers=3`: BUILD SUCCESSFUL, all four tasks executed; 3,879 host tests, 0 failed, 0 skipped; Roborazzi verify clean.
- `./gradlew cleanAllTests build allTests --continue --max-workers=3`: BUILD SUCCESSFUL; the test tasks were cleaned first and re-ran (3,879 host tests, 0 failed).
- `./gradlew :androidApp:dependencies --configuration devDebugRuntimeClasspath | grep -i firebase`: empty (SC-009).
- A first, non-rerun run after the merge failed 4 tests: the 3 stale Account goldens (fixed, section 2) and `ReportsPerformanceTest`
  (`UncaughtExceptionsBeforeTest`, a timing/leak flake of spec 014 under load; it passed in both later full runs).

## 9. Not done on purpose

T036, T050, T059, T066, T072 (device E2E, owner's phone) and Phase 9 T085 to T091 (Firebase, gated on the owner's approval) stay unchecked;
T084 (summary) is the parent's.
