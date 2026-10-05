# QA report: Redeem Code (client), spec 015

Run 2026-10-05/06 on branch `015-redeem-code` (implementation `47da039`). Backend: `saveme-backend` working tree on
`7155d6d` (spec 020; spec 022 export work uncommitted in the tree, not touched), local container, queue worker
running, Mailpit v1.30.0. Owner policy: no emulator, Maestro or device run; device items stay deferred.

Legend: PASS = proven by a passing host test or a code path named here; DEFERRED = needs the owner's device (flow
written, not run); GAP = was open, closed by the test or fix named.

## 1. Summary

| Item | Count | PASS | DEFERRED (device) | GAP closed |
|---|---|---|---|---|
| Acceptance scenarios (US1 6, US2 4, US3 9) | 19 | 19 | 0 (E2E flows T029/T035/T044 are additional) | 0 |
| Functional requirements FR-001..FR-031 | 31 | 31 (FR-002 focus/keyboard, FR-029 keyboard flags and system menu, FR-030 touch targets: code path + host test, final proof on device T051) | 0 | 1 (FR-011 "not persisted / no clipboard" had no test: `BillingLayeringTest.theRedeemCodePathSavesNothingStoresNothingAndWritesNoClipboard`) |
| Success criteria SC-001..SC-007 | 7 | 7 (SC-001 timing is a device measure; the 3-tap path is proven) | 0 | 0 |
| Edge cases | 20 | 20 | 0 | 0 |
| Live contract cases (section 3) | 20 cases, 18 answers captured | 18 replayed through the real client | n/a | 0 |
| Flaky test | 1 | n/a | n/a | fixed (section 5) |

Live QA found no backend bug and no client defect. Findings worth knowing (section 3.3): the server already folds
raw `O`/`I`/`L`; "used" outranks "wrong address"; refusals are not replayed under the same key.

## 2. Traceability

Test names are in `shared/src/commonTest/kotlin/com/fares/saveme/feature/billing/` (`bt`) unless a path is given;
`ht` = `shared/src/androidHostTest/...`. "Live" = `RedeemLiveReplayTest` over `CapturedRedeemResponses`.

### 2.1 Acceptance scenarios

| Scenario | Evidence | Result |
|---|---|---|
| US1-1 redeem, confirmation names plan and end, plan shown at once | `RedeemServiceTest.successAppliesEntitlementsThenSavesGoverningThenRefreshesAndConfirms`; Live `theFirstRedeemNamesTheRedeemedPlanAndAppliesItEverywhere`; screenshot state `done` | PASS |
| US1-2 paste with spaces, lower case, hyphens shows four groups; Redeem at 16 | `RedeemCodeTest` (`lowerCaseIsUpperCased`, `spacesHyphensAndLineBreaksAreDropped`, `groupedAndIsComplete`), `CodeFieldTest.theVisualHyphensAreDrawnBetweenGroupsOnly`, `RedeemViewModelTest.pastingAWholeEmailLineYieldsTheCode`; live: the server also accepts those forms (3.2) | PASS |
| US1-3 typing: groups, upper case, O->0, I/L->1, bad characters refused | `RedeemCodeTest.oBecomesZeroAndIAndLBecomeOne`, `uAndPunctuationAreDroppedNotConverted...`, `CodeFieldTest.theOffsetMappingKeepsTheCursorRightTypingAndDeleting`, `typedTextReachesTheCallerRaw` | PASS |
| US1-4 fewer than 16: no Redeem, nothing sent | `RedeemReducerTest.canRedeemOnlyAtSixteenCharactersNotSendingAndNoWait`, `RedeemViewModelTest.anIncompleteCodeSendsNothing`, `RedeemServiceTest.anIncompleteCodeIsNeverSent` | PASS |
| US1-5 same account again shows the same confirmation | Live `aSameKeyReplayAndARepeatByTheSameAccountShowTheSameConfirmation` (real replay and real repeat answers) | PASS |
| US1-6 Done returns to Plan | `RedeemViewModelTest.doneAndBackNavigateBack`, `PlanViewModelTest.enterCodeOpensTheRedeemScreen`; nav wiring `AppNavHost` (`NavigateBack` pops); flow `US1-redeem` deferred | PASS |
| US2-1 governing is the redeemed one: "You're now on", end or "No end date" | `RedeemConfirmationTest.theRedeemedSubscriptionGoverningHasNoPlayNote`, `bothEndsNullCountAsEqualForTheRedeemedPlan`; screenshot `done` | PASS |
| US2-2 another subscription governs: says redeemed, names governing, states redeemed end | `RedeemConfirmationTest.aManualLifetimeGoverningHasNoEndAndIsNotTheRedeemedOne`; Live `anOperatorGrantedLifetimePlanKeepsGoverningAndHasNoEnd`; screenshot `done_current_stays` | PASS |
| US2-3 renewing Google Play governs: charging note | `RedeemConfirmationTest.aRenewingPlaySubscriptionThatKeepsGoverningIsNamedWithItsPlayNote`, `playWithoutAutoRenewHasNoNote`; Live `aRenewingPlaySubscriptionKeepsGoverningAndGetsItsNote`; screenshot `done_play_renews` | PASS |
| US2-4 early renewal says when the redeemed period starts | `RedeemConfirmationTest.aRedeemedStartAfterNowIsReportedOtherwiseNull`; Live `anEarlyRenewalSaysWhenTheRedeemedPeriodStarts`; screenshot `done_starts_later` | PASS |
| US3-1 unknown or withdrawn code: same "not valid" | `RedeemServiceRefusalTest.eachCatalogueCodeHasItsOwnOutcome`; Live (`refusedNotFound` and `refusedRevokedCode` both `NotValid`) | PASS |
| US3-2 used | same + Live `refusedUsed` | PASS |
| US3-3 expired, contact support | same + Live `refusedExpired` | PASS |
| US3-4 other address: message, own address only, other never shown | Live `theMismatchAnswerNamesNoAddress`, `theLiveOtherAddressAnswerShowsOnlyTheSignedInAddressThroughTheStore`; `RedeemReducerTest.otherAddressCarriesOnlyTheSignedInAddress`; `ht RedeemStringsTest` | PASS |
| US3-5 unverified: note, Redeem opens verify prompt, nothing sent; stale flag corrected by the server | `RedeemViewModelTest.unverifiedAsksToVerifyAndSendsNothing`; `RedeemReducerTest.aNotVerifiedAnswerCorrectsTheFlag`; Live `refusedNotVerified`; `HttpClientFactoryTest.upgradeRequiredAndEmailNotVerifiedArePublished` | PASS |
| US3-6 rate limit: minutes from the server wait, Redeem unavailable until then | `RedeemViewModelTest.rateLimitedDisablesRedeemUntilTheWaitPassesWithMinutesRoundedUp`, `underAMinute...`; Live `theLiveRateLimitDisablesRedeemFromTheServersWaitThroughTheStore` (real 429, 3466 s -> 58 min, wait timer ends) | PASS |
| US3-7 offline: needs a connection, Try again, nothing queued or retried | `RedeemServiceTest.offlineIsAnOutcomeAndNothingIsRetried`, `RedeemViewModelTest.offlineKeepsTheAttemptAndTryAgainReusesIt` | PASS |
| US3-8 retry after a timeout never duplicates or says "used" | `RedeemViewModelTest.offlineKeepsTheAttempt...`, `inProgressKeepsTheAttemptToo`; live: same key twice returned the same subscription id with `Idempotency-Replayed: true` (3.1 #2) | PASS |
| US3-9 any other refusal: general failure with request id | `RedeemServiceRefusalTest.anythingElseIsAnUnexpectedFailure...`, `aReusedKeyCarriesTheRequestId`; Live `theLiveKeyRefusalsCarryTheirRequestIds` | PASS |

### 2.2 Functional requirements

| FR | Evidence | Result |
|---|---|---|
| FR-001 Enter code on Plan only, every plan | `PlanViewModelTest.enterCodeOpensTheRedeemScreen`; `PlanScreenScreenshotTest` (button in the reserved column, no plan condition in `PlanScreen`); no other screen references `RedeemCodeKey`/`OpenRedeem` | PASS |
| FR-002 intro, one focused field, Redeem; no price/payment/link/"InstaPay" | screenshots; `RedeemRoute` requests focus on open (`LaunchedEffect`); `ht RedeemStringsTest.noRedeemCopyMentionsThePaymentPathAPriceOrABannedWord`; focus/keyboard open on device (T051) | PASS |
| FR-003 unverified note and verify prompt instead of sending | `RedeemViewModelTest.unverified...`; screenshot `unverified` | PASS |
| FR-004 16 characters, grouped, upper-case, LTR | `RedeemCodeTest.isCutAtSixteenCharacters`, `CodeFieldTest`, `CodeFieldScreenshotTest` (EN, AR RTL, 2.0) | PASS |
| FR-005 normalisation | `RedeemCodeTest` table (case, O/I/L, separators, alphabet, Arabic-Indic digits dropped) | PASS |
| FR-006 pasted grouped code extracted | `RedeemCodeTest.fromPasteTakesTheGroupedCodeOutOfAWholeEmailLine`, `fromPasteSkipsALookAlike...`, `fromPasteFallsBackToNormaliseForPlainText` | PASS |
| FR-007 Redeem availability; done key does the same | `RedeemReducerTest.canRedeem...`; `RedeemViewModelTest.aSecondRedeemWhileSendingIsIgnored`; `onDone` wired to `Redeem` in `RedeemScreen`, guarded by the same state | PASS |
| FR-008 grouped form sent | `RedeemContractTest.postsTheGroupedCodeWith...`, `RedeemServiceTest.sendsTheGroupedCodeAndTheAttemptKeyOnce`; Live `theFirstRedeem...` (body `{"code":"XXXX-XXXX-XXXX-XXXX"}`, accepted by the real server) | PASS |
| FR-009 online-only, never queued/retried | `RedeemServiceRefusalTest` (one request per code), `RedeemServiceTest.offline...`; Live `everyLiveRefusal...` (exactly one request each) | PASS |
| FR-010 attempt identifier lifecycle | `RedeemViewModelTest.redeemCreatesOneAttemptKeyAndPassesIt`, `editingTheCodeClearsTheMessageAndTheNextRedeemUsesANewKey`, `keyReusedDropsTheKey...`; `RedeemReducerTest.refusalsKeepTheAttemptExceptKeyReused`, `codeChangedStoresItAndClearsAttemptAndMessage` | PASS |
| FR-011 never logged/stored/clipboard/persisted; redacted | `RedeemLoggingTest`, `RedeemReducerTest.stateAndIntentAndAttemptNeverPrintTheCodeOrKey`, `RedeemContractTest.theRequestDtoNeverPrintsTheCode`, new `BillingLayeringTest.theRedeemCodePathSavesNothingStoresNothingAndWritesNoClipboard` (no `rememberSaveable`, `SavedStateHandle`, `KeyValueStore`, clipboard in the redeem sources) | PASS (GAP closed) |
| FR-012 apply only for the same person; entitlements, governing, then refresh; leaving does not stop a success | `RedeemServiceTest` (order, refresh failure, sign-out, leaving), `SessionManagerApplyEntitlementsTest`; Live asserts the stored plan, features and governing provider after real answers | PASS |
| FR-013 names governing plan and end in the person's zone; grace end | `RedeemConfirmationTest.graceGoverningShowsTheGraceEnd`; zone from the profile (`RedeemViewModelTest.theTimeZoneIsTheProfiles...`); screenshots | PASS |
| FR-014 existing subscription stays + redeemed plan/end; Play note | `RedeemConfirmationTest`, Live (manual lifetime, Play) | PASS |
| FR-015 early renewal start | `RedeemConfirmationTest.aRedeemedStartAfterNow...`, Live early renewal | PASS |
| FR-016 Done/Back return; code not shown | `RedeemViewModelTest.doneAndBack...`; `RedeemReducerTest.succeededIsDoneAndForgetsTheCodeAndAttempt` | PASS |
| FR-017 NOT_FOUND | `RedeemServiceRefusalTest`, `RedeemViewModelTest.eachRefusalSetsItsMessage`, `BillingErrorCopyTest`, Live | PASS |
| FR-018 USED | same | PASS |
| FR-019 EXPIRED | same | PASS |
| FR-020 EMAIL_MISMATCH | same + own address only | PASS |
| FR-021 EMAIL_NOT_VERIFIED | same + app-wide event | PASS |
| FR-022 RATE_LIMITED, wait, no wait time | `RedeemServiceRefusalTest.rateLimitTakesTheBodyWaitThenTheHeaderThenNothing`, `RedeemViewModelTest` (minutes, under a minute, no wait stays available), Live | PASS |
| FR-023 VALIDATION_FAILED | `RedeemServiceRefusalTest` (any pointer), Live `refusedMalformed`, `refusedMissingCode` | PASS |
| FR-024 IN_PROGRESS | `RedeemServiceRefusalTest`, `RedeemViewModelTest.inProgressKeepsTheAttemptToo`. Not reachable live (3.3 #5): contract only | PASS (contract) |
| FR-025 KEY_REUSED | `RedeemViewModelTest.keyReusedDropsTheKeyAndShowsTheRequestId`; Live `theLiveKeyRefusalsCarryTheirRequestIds` | PASS |
| FR-026 UNAUTHENTICATED, 426, other with/without request id | `RedeemServiceRefusalTest.sessionAndUpdateRefusalsAreHandledAppWide`, `anythingElse...`; `HttpClientFactoryTest` (session ended, update required events); live 401 shape recorded (3.1 #19) | PASS |
| FR-027 message stays until edit/retry; announced | `RedeemReducerTest`/`RedeemViewModelTest` (cleared only by edit or retry); `InlineAlert` carries the live region (`designsystem/component/Feedback.kt`) | PASS |
| FR-028 error codes with retry classes and copy | `ApiErrorCodeTest.theRedeemAndIdempotencyCodesDecodeWithTheirRetryClasses`, `BillingErrorCopyTest`, `ht RedeemStringsTest.everyRedeemKeyExistsInBothLanguages`; `check-backend-contract.sh` clean (T050) | PASS |
| FR-029 secure window, paste-only, keyboard hints | `ht SecureWindowTest` (both cases), `CodeFieldTest.theMenuKeepsPasteAndDropsCopyAndCut`; `CodeField` sets `KeyboardType.Password`, no autocorrect (code path); system menu and keyboard behaviour: device spot check T051 | PASS (device spot check pending) |
| FR-030 design system only, RTL/LTR, 200 %, 48 dp, tags, announcements | screenshots EN/AR/2.0 (`RedeemScreenScreenshotTest`, `CodeFieldScreenshotTest`); all 12 tags of `contracts/ui-and-e2e.md` present in `RedeemScreen`/`PlanScreen` (scripted check); `CodeFieldTest.theLabelIsAnnouncedAndTheCodeIsReadCharacterByCharacter` | PASS |
| FR-031 sign-out drops code, key and late answer | `RedeemReducerTest.signOutClearsCodeAndAttempt`, `RedeemViewModelTest.theTimeZoneIsTheProfilesAndSignOutClearsTheCode`, `signedOutOutcomeClearsTheCode`, `RedeemServiceTest.signingOutBeforeTheAnswerAppliesNothing` | PASS |

### 2.3 Success criteria

| SC | Evidence | Result |
|---|---|---|
| SC-001 under 30 s, 3 taps | path Plan -> Enter code -> paste -> Redeem is 3 interactions (`PlanViewModelTest`, `RedeemViewModelTest.pastingAWholeEmailLineYieldsTheCode`); wall-clock timing is a device measure (T029) | PASS (timing deferred) |
| SC-002 plan at once, also when the re-read fails | `RedeemServiceTest.aFailedReReadLeavesTheAppliedAnswer`; Live applied-state assertions | PASS |
| SC-003 8 codes plus offline each own message, both languages | `BillingErrorCopyTest`, `RedeemViewModelTest.eachRefusalSetsItsMessage`, `ht RedeemStringsTest`, screenshots for each message | PASS |
| SC-004 0 occurrences of code/key | `RedeemLoggingTest`; `BillingLayeringTest` source scan; redacted `toString` tests | PASS |
| SC-005 0 prices/payment paths/"InstaPay" | `ht RedeemStringsTest`; `BillingLayeringTest.noClientSourceBuildsTheClaimUrl` | PASS |
| SC-006 existing subscription named, never the redeemed as "now on" | `RedeemConfirmationTest` plus Live manual-lifetime and Play cases | PASS |
| SC-007 retry never duplicates | VM attempt-key tests; live same-key replay (one subscription id, `Idempotency-Replayed: true`) | PASS |

### 2.4 Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Which account (never reveals the other address) | Live mismatch answers carry no `@` | PASS |
| Unverified and wrong address together: wrong address first | Live `refusedOtherAddressWhileUnverified` (unverified C sending A's code: 403 `REDEEM_CODE_EMAIL_MISMATCH`) | PASS |
| Stale verified flag | `RedeemReducerTest.aNotVerifiedAnswerCorrectsTheFlag`; the VM refreshes the account on open | PASS |
| Double tap / impatient retry | `RedeemViewModelTest.aSecondRedeemWhileSendingIsIgnored` | PASS |
| `IDEMPOTENCY_REQUEST_IN_PROGRESS` copy and Try again | `RedeemViewModelTest.inProgressKeepsTheAttemptToo` | PASS |
| Code changed after a failure starts a new attempt | `RedeemViewModelTest.editingTheCodeClears...` | PASS |
| Leaving during a request: success applied, refusals dropped | `RedeemServiceTest.leavingTheScreenAfterTheRequestStartedStillAppliesASuccess`; refusals dropped because the screen's `viewModelScope` is cancelled (code path) | PASS |
| Leaving/process end/sign-out drops the code, never stored | `BillingLayeringTest` source scan, reducer and VM sign-out tests | PASS |
| Screenshots and app switcher | `SecureWindowTest`; device spot check T051 | PASS |
| Clipboard | `CodeFieldTest` menu; source scan | PASS |
| Sign-out during a request discards the answer | `RedeemServiceTest.signingOutBeforeTheAnswerAppliesNothing` | PASS |
| Answer applied, refresh fails | `RedeemServiceTest.aFailedReReadLeavesTheAppliedAnswer` | PASS |
| Lifetime plan "No end date" | Live manual-lifetime case; screenshot `done_current_stays` | PASS |
| Unknown status/provider | `RedeemConfirmationTest.anUnknownProviderIsAnotherSubscriptionWithoutAPlayNote`; `RedeemContractTest.redeemedGovernsDecodesAndIgnoresUnknownMembers` | PASS |
| Pasted text with surrounding words | `RedeemCodeTest.fromPaste...` | PASS |
| Large font / RTL | screenshots EN, AR, 2.0 | PASS |
| Pending account deletion: no special handling | none needed (server decides) | PASS |
| Same-key replay and repeat by the account (backend gaps 2) | Live | PASS |
| `Idempotency-Key` required | `IdempotencyKeyTest`; live 400 `IDEMPOTENCY_KEY_REQUIRED` when missing (client always sends it) | PASS |
| Early renewal chaining | Live early renewal | PASS |
| Raw `O`/`I`/`L` (backend gap 5) | live: server folds them too (3.2) | PASS |

## 3. Live-backend contract QA (T049)

Throwaway accounts `qa015-{a,b,c,d,e}-1791239362@example.com` (A, B, D, E verified; C left unverified); plan prices from
`GET /plans` (Plus Monthly 14900 EGP, Plus Yearly 129000 EGP). Requests used `http://127.0.0.1:80` with
`Host: saveme-backend.test` (local DNS flaky) and the standard headers (`X-Client-Platform: android`,
`X-Client-Version`, `Accept-Language`, `Authorization: Bearer ...`, `Idempotency-Key`).

### 3.1 Results (what the server really answered)

| # | Case | Request | Answer |
|---|---|---|---|
| 1 | success, code typed lower-case without hyphens | A, key K1 | 200 `{subscription, governing, entitlements}`; `governing.provider` instapay, plan Plus Monthly, period 2026-10-05 -> 2026-11-05, `auto_renew` false, `grace_until` null |
| 2 | same key again | A, K1 | 200, `Idempotency-Replayed: true`, same body |
| 3 | new key, same account | A, K2 | 200, same `subscription.id` (no `Idempotency-Replayed`) |
| 4 | lower-case with hyphens / spaced with spaces / raw `O`,`I` / raw `l` | A | 200 each (same subscription) |
| 5 | second code (Plus Yearly) while one is active | A | 200: redeemed period starts 2026-11-05 (when the first ends, "periods chain"), ends 2027-11-05; `governing` = the yearly one (later end) |
| 6 | operator-granted lifetime (manual, no end) governs | D, after `OperatorSubscriptions::grant` | 200: `subscription` instapay Monthly (starts after D's first code ends), `governing` manual Plus Yearly, `current_period_end` null; `entitlements` Plus Yearly |
| 7 | Google Play renewing governs | E (own Play row, own data) | 200: `governing` provider `google_play`, `auto_renew` true, ends 2027-10-05; redeemed instapay Monthly |
| 8 | code sent to another address | B redeems A's unredeemed code | 403 `REDEEM_CODE_EMAIL_MISMATCH`; body has no address |
| 9 | wrong address and unverified together | C redeems A's unredeemed code | 403 `REDEEM_CODE_EMAIL_MISMATCH` (outranks unverified, as specified) |
| 10 | unverified own code | C | 403 `EMAIL_NOT_VERIFIED` |
| 11 | code another account already redeemed | B redeems A's redeemed code | 409 `REDEEM_CODE_USED` (outranks mismatch, see 3.3) |
| 12 | expired (`expires_at` set a day back on my own row) | B | 410 `REDEEM_CODE_EXPIRED` |
| 13 | revoked (`billing:claim:revoke-code`) | D | 404 `NOT_FOUND` |
| 14 | unknown `AAAA-BBBB-CCCC-DDDD` | A | 404 `NOT_FOUND` |
| 15 | malformed `7KQ2-MX4D` | A | 422 `VALIDATION_FAILED`, `errors[0] = {pointer:/code, code:INVALID_TYPE}` |
| 16 | `{}` | A | 422, `{pointer:/code, code:REQUIRED}` |
| 17 | 11th refused attempt in the hour | B (3 refusals earlier + 7 more 404 = 10; the next is the 11th) | 429 `RATE_LIMITED`, `Retry-After: 3466`, `retry_after_seconds: 3466` (header and body agree); stays 429 |
| 18 | no `Idempotency-Key` | A | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| 19 | same key, different body | A, K1 + other code | 409 `IDEMPOTENCY_KEY_REUSED` (and 401 `UNAUTHENTICATED` without a bearer) |
| 20 | `IDEMPOTENCY_REQUEST_IN_PROGRESS` | six parallel requests, one key | not reachable: all six 404 (see 3.3) |

The 429 appeared on the 11th counted attempt (B: mismatch, used, expired, seven 404, then 429). Successes and replays of A (16 requests) never produced a 429, so the budget counts
refusals, not successes (inferred; the contract says "attempts").

### 3.2 Normalisation the server performs (raw, no client folding)

- Lower case, no hyphens: accepted. Lower case with hyphens: accepted. Spaces between groups and surrounding spaces:
  accepted.
- Raw `O` in place of `0` (code `xxx0-xxxx-1xxx-xxxx` sent as `xxxO-xxxx-Ixxx-xxxx`): 200. Raw `I` in place of `1`: 200.
  Raw lower-case `l` in place of `1`: 200. So the backend gap 5 (O to 0 untested in backend QA) is settled: the
  server folds `O`, `I` and `L`; the app's own folding before sending is redundant but harmless.

### 3.3 Findings

1. No backend bug. All statuses, codes and `Problem` shapes equal `instapay.yaml` v1.12 and the error catalogue.
2. `REDEEM_CODE_USED` is reachable without a row edit: any other account sending a redeemed code gets 409 `USED`
   before the address check. (The "wrong account" answer for a code that is unredeemed is 403.) The app shows
   "already used" in both: correct for US3-2, and no address leaks.
3. A repeat redemption by the same account answers 200 with the first redemption's `subscription` (same id) and no
   marker, as the spec's backend gap 2 says.
4. Early renewal is real: InstaPay periods chain, so a second code redeemed while one is active starts when the
   first ends; `governing` then points to the later-ending one (here the redeemed yearly), so the confirmation says
   "now on" plus the start date (FR-015).
5. `IDEMPOTENCY_REQUEST_IN_PROGRESS` could not be provoked (the dev server answered six parallel same-key requests
   serially; refusals are not stored, so each ran fresh and answered 404, none carried `Idempotency-Replayed`).
   Covered by the contract test and store tests only. Consequence for the app: a retry after a refusal re-executes
   (and counts against the hour's budget); only successes replay.
6. Claim submission is rate-limited per origin (5 an hour) and per address (5 a day) over HTTP; once the origin budget
   was spent, claims were submitted through `ClaimService::submit` in `dev tinker` (the same call `run-e2e.sh` makes).

### 3.4 Captured fixtures and replay

`shared/src/commonTest/kotlin/com/fares/saveme/feature/billing/CapturedRedeemResponses.kt`: 18 real answers
(first success, same-key replay, repeat, early renewal, manual-lifetime governs, Play renews governs, other address
x2, not verified, not found, revoked, used, expired, malformed, missing code, rate limited, key required, key reused).
Redaction: no redeem code appears in any answer; the placeholder `XXXX-XXXX-XXXX-XXXX` is used anyway; no bearer
credential, email address or token is stored; only `Retry-After` and `Idempotency-Replayed` headers are kept; ids are
the throwaway data's UUIDs (plan ids are public via `GET /plans`).

`RedeemLiveReplayTest` (10 tests, green) sends them through the real `ApiClient` -> `BillingApi` -> `RedeemService`
-> session and subscription stores, and two (rate limit, other address) through the real `RedeemViewModel`.

### 3.5 CHK036 and CHK037 (settled)

- **CHK037** (`ClaimService::approve` with the fixed E2E operator id): confirmed. `ClaimService::approve("00000000-0000-7000-8000-0000000000e2", <claim id>)`
  succeeded in `dev tinker`; the service does not look the operator up. The CLI is stricter: it needs an active
  operator: `dev billing:claim:approve --operator=<active operator uuid> <claim id>` (claim id is the positional
  argument; used here with the one active operator, `01a10849-32b6-72b6-a7ed-a48a5aa61033`, read-only from
  `operator_accounts where state = 'active'`). Revoke: `dev billing:claim:revoke-code --operator=<id> --reason=... <claim id>`.
- **CHK036** (Mailpit): Mailpit v1.30.0, container port `127.0.0.1:8025` (also reachable through the proxy as
  `http://mailpit.test`, Host header). Search `GET /api/v1/search?query=to:<address> subject:"code is ready"`
  (newest first; `messages[0].ID`), then `GET /api/v1/message/<ID>`: the `Text` field holds the code as
  `XXXX-XXXX-XXXX-XXXX` (regex `[0-9A-Z]{4}(?:-[0-9A-Z]{4}){3}`). Subject "Your saveme-backend code is ready"; the mail
  arrived about 4 s after approval with the queue worker running. The runner's `read_mailed_code` was executed
  verbatim against `MAILPIT_API=http://127.0.0.1:8025/api/v1` and returned the code. Note: a registered address also
  has a welcome/verification mail, so the runner's search `to:<address>` is only safe because it keeps polling until a
  code is found in the newest message; for an address with an older approval mail it would return the older code, but
  `run-e2e.sh` uses a fresh address per run.

### 3.6 Cleanup

QA accounts A to E remain (as agreed). Revoked the unredeemed codes of C and B (the expired one). Ended the manual
subscription granted to D (`OperatorSubscriptions::revoke`) and deleted E's Play row (own data). The redeemed
subscriptions of A, D and E remain on the throwaway accounts. One unredeemed code (`01a10b63-...`) is not mine
(created earlier by another run) and was left alone. The dev DB was not reset, refreshed or seeded; no backend code
was edited.

## 4. Gaps found and closed

| Gap | Closure |
|---|---|
| "Not persisted, never on the clipboard" (spec §4 invariant 1, FR-011) had a log test and `toString` tests but nothing proving the screen state is not saved or copied | `BillingLayeringTest.theRedeemCodePathSavesNothingStoresNothingAndWritesNoClipboard` |
| No captured-answer replay (T049) | `CapturedRedeemResponses.kt`, `RedeemLiveReplayTest` |
| `BillingSyncCoordinatorTest.aFeatureNotEntitledRefusalTriggersANeverSkippedRefresh` could time out | test race fixed, section 5 |

No client defect was found by the live run, so no production code changed.

## 5. Flaky test: `BillingSyncCoordinatorTest.aFeatureNotEntitledRefusalTriggersANeverSkippedRefresh`

Cause (a test race, not a product defect): the mock engine records a request before its answer is handled. The test
published `FeatureNotEntitled` right after seeing the first `/me/subscription` request. If the sign-in refresh was still
in flight, `EntitlementsRefresher.refresh(NotEntitled)` joined it (by design, FR-006: a trigger during a refresh awaits
the same result), so no `/me/entitlements` read ever happened and the 10 s `waitUntil` timed out. Under load the
window between "request recorded" and "answer handled" widens.

Fix: the test now settles the sign-in refresh first with `refresher.refresh(RefreshReason.Foreground)` (it awaits the
in-flight one, or is a no-op inside the 60 s floor after it succeeded), then publishes the refusal. No production code,
sleep or timeout change. Repetition result: 16 isolated runs (`--rerun`, one Gradle run each, four busy CPU loops in the background): 16 passed, 0 failed. The original flake did not reproduce in 60 in-process repetitions under load either (the window is narrow), so the diagnosis rests on the code path above.
