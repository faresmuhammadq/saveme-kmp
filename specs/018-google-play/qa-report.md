# QA report: Google Play Billing (client) — spec 018

Date: 2026-10-07. Branch `018-google-play`. Backend `saveme-backend@1a0e1cd` (spec 024 committed as `2062c0c`; `google-play.yaml` v1.15).
Owner policy: no emulator, Maestro or device run; no Google Play call of any kind; no new dependency.

Result: **0 open gaps.** Five gaps were found in the traceability pass and the live run; all are closed (section 4).

## 1. Traceability (host tests, all green)

Test classes live under `shared/src/commonTest/kotlin/com/fares/saveme/feature/billing/` (`bt/`) unless stated; `ht/` is `shared/src/androidHostTest/…`.
`Verifier` = `bt/domain/play/PlayPurchaseVerifierTest`, `Restore` = `bt/domain/play/PlayRestoreServiceTest`, `Contract` = `bt/data/GooglePlayContractTest`,
`Replay` = `bt/play/GooglePlayLiveReplayTest` (live answers), `PlanPlay` = `bt/ui/plan/PlanPlayReducerTest` + `PlanPlayViewModelTest`, `Coord` = `bt/domain/BillingSyncCoordinatorTest`.

### Acceptance scenarios

| Id | Evidence | Result |
|---|---|---|
| US1.1 purchased unacknowledged sent once, applied, never acknowledged | Verifier `aPurchasedUnacknowledgedPurchaseBecomesThePlanWithOneKeyedSend`; Replay `theLiveActiveAnswerBecomesPlusAndTheTestPurchaseIsNoted`; Coord `aColdStartRunsAVerifyPass…`, `foregroundRunsAPassAtMostOncePerMinute`; PlanPlay `openingRunsAPurchasePass…` | PASS |
| US1.2 same key across retry and process death | Verifier `theSameKeyIsUsedAcrossARetryAndAcrossAProcessDeath`, `aServiceFailureIsRetried…`, `offlineAndRateLimitedAreRetriedAfterTheirWait`, `anInProgressAnswerIsRetriedWithTheSameKey`; Replay `theServersOwnWaitIsHonoured…` | PASS |
| US1.3 pending sent once, nothing granted, then purchased with a new key | Verifier `aPendingPurchaseIsSentOnce…`; Replay `theLivePendingAnswerGrantsNothingAndThePurchasedOneLaterGrantsPlus`; live keys differ (`theAppsKeysAreExactlyTheKeysTheServerAcceptedAndReplayed`) | PASS |
| US1.4 `202` for a purchased purchase rechecked after an hour with an hour key | Verifier `aPurchasedPurchaseTheServerAnswers202IsRecheckedNoSoonerThanAnHour…`; Replay `theLiveRecheckAnswerForAHourKeyIsAnOrdinaryGrant` | PASS |
| US1.5 `PURCHASE_NOT_VERIFIED` not resent in the run | Verifier `anUnverifiablePurchaseIsNotResentInTheRun`; Replay `theLiveNotVerifiedAnswer…` | PASS |
| US1.6 `acknowledged:false` changes nothing | Verifier `anUnacknowledgedAnswerChangesNothingOnThePhone` | PASS |
| US1.7 only acknowledged purchases: no request | Verifier `onlyUnacknowledgedPurchasesAreSent` | PASS |
| US2.1 sign-in restore, one request, silent unless elsewhere | Restore `aSignInWithPurchasesSendsOneRestoreWithAllOfThemAndStaysSilent`; Coord `aRealSignInAfterBeingSignedOutRunsARestore…`; Replay `aSignInRestoreWithPurchasesIsSilentUnlessSomethingBelongsElsewhere` | PASS |
| US2.2 no purchases: no request | Restore `aSignInWithNoPurchasesSendsNothing` | PASS |
| US2.3 tap: progress, all purchases or `[]`, combined message | Restore `aTapSends…`, `aTapWithNothingOwned…`, `theSummaryAssemblesOneCombinedMessage`; PlanPlay `restoreRunsThenShowsTheCombinedResult`; Replay `theLiveMixedRestore…`, `anEmptyRestore…`; goldens `restore_*` | PASS |
| US2.4 refusal says why, Try again reuses the key | Restore `aRefusalStopsTheRemainingChunks…`, `eachRefusalHasItsOwnResult`; PlanPlay `eachRefusalMapsAndTryAgainReusesTheAttempt`; Replay `theLiveRestoreRateLimitShowsItsWaitThroughThePlanStoreAndTryAgainReusesTheKey` | PASS |
| US2.5 Google Play unavailable: message, nothing sent | Restore `googlePlayUnavailableSendsNothing`; PlanPlay `googlePlayUnavailableShowsItsOwnRefusalWithoutRetry`; `bt/play/UnavailablePlayBillingClientTest` | PASS |
| US2.6 offline: message, nothing sent | Restore `eachRefusalHasItsOwnResult` (Offline); PlanPlay `eachRefusalMapsAndTryAgainReusesTheAttempt` | PASS |
| US3.1 `PURCHASE_ALREADY_LINKED`: note once, not resent | Verifier `anotherAccountsPurchaseIsExplainedOnceAndNotResentByTriggers`; Replay `bothLiveAlreadyLinkedAnswersExplainOnceAndAreNotResent` | PASS |
| US3.2 `linked_elsewhere` in a restore, other items still apply | Restore `linkedElsewhereSetsTheAnotherAccountNote…`; Replay `theLiveMixedRestore…` | PASS |
| US3.3 account switch clears A's notes | `bt/domain/play/PlayBillingNotesTest` `notesAreKeptPerPerson…`; Verifier `signOutCancelsTheBackoffTimersAndClearsEveryNote` | PASS |
| US4.1 grace warning with Fix payment | PlanPlay `aGracePeriodShowsTheWarningWithTheGraceEnd`, `fixPaymentForAHold…`; Replay `graceShowsTheWarningWithTheGraceEnd`; golden `play_grace` | PASS |
| US4.2 Manage only for Google Play's subscription | PlanPlay `manageShowsOnlyForAGooglePlaySubscription…`; Replay `aManualSubscriptionThatGoverns…` | PASS |
| US4.3 hold / paused notes from a verify answer | PlanPlay `holdAndPausedShowOnlyAfterAVerifyAnswerInThisRun`; Verifier `holdPausedAndTestComeFromTheAnswerForThisRun`; Replay `anOnHold…`, `aPaused…` | PASS |
| US4.4 pending note | PlanPlay `pendingAndAnotherAccountFollowTheNotes`; Replay pending test | PASS |
| US4.5 "Test purchase" label | PlanPlay `theTestLabelShowsOnlyForTheGoverning…`; Replay `theLiveActiveAnswer…` | PASS |
| US4.6 refunded / revoked reads as the free plan, no blame | Replay `anExpiredOrRevokedAnswerShowsTheFreePlanAndNoNoteBlamingAnyone`; live state read `subscriptionRevoked` | PASS |
| US5.1 Google Play's price, minor units, app formatter | `bt/domain/play/PlayPricesTest` (all); PlanPlay `cardPricesComeOnlyFromGooglePlay` | PASS |
| US5.2 no answer: no price, never the catalogue's | PlanPlay `cardPricesComeOnlyFromGooglePlay`; `bt/domain/CataloguePlanTest`; PlayPricesTest `noPriceShows…` | PASS |
| US5.3 language change reformats | PlayPricesTest `googlePlaysPriceIsWrittenByTheAppsFormatter…InEnglishAndArabicWithWesternDigits` (added); goldens `arabicRtl` | PASS (gap G3 closed) |
| US6.1 switch off: no Subscribe anywhere | PlanPlay `theBillingActionsAreExactlyRestoreAndEnterCodeAndNoSubscribeExists`; `ht/BillingLayeringTest` `noReachableCodePathLaunchesAPurchase…`; `ht/GooglePlayStringsTest` `theSubscribeKeysAreReferencedByNoSource` | PASS |
| US6.2–US6.7 Subscribe flow | Gated (G2, tasks T051–T054, owner amends P-12.3); not built | GATED, out of scope |

### Functional requirements

| Id | Evidence | Result |
|---|---|---|
| FR-001 billing actions exactly Restore + Enter code (+ links) | PlanPlay `theBillingActionsAreExactly…`; `ht/PlanAccessibilityTest` | PASS |
| FR-002 never acknowledge/consume/grant | `ht/BillingLayeringTest` `noSourceAcknowledgesOrConsumes…`; Verifier `nothingIsGrantedWhenTheServerIsDown`; fake port records no such call | PASS |
| FR-003 never queued in the outbox | `ht/BillingLayeringTest` `noBillingSourceTouchesTheSyncEngineOrItsOutbox` (added) | PASS (gap G1 closed) |
| FR-004 narrow port, unavailable degrade | `ht/BillingLayeringTest` `theGooglePlayPortImportsNoDomain…`; `UnavailablePlayBillingClientTest` (added); `ht/core/di/AppGraphTest` `theReleaseGraphBindsTheUnavailablePort…`; Verifier `nothingIsSentWhenGooglePlayIsUnavailable…` | PASS (gap G2 closed) |
| FR-010 triggers and unacknowledged only | Coord (cold start, foreground floor, update, signed out); Verifier `onlyUnacknowledgedPurchasesAreSent`; PlanPlay `refreshRunsAnotherPass` | PASS |
| FR-011 one pass at a time, answers for another person discarded | Verifier `aTriggerDuringAPassWaitsForItAndRunsOnceMore…`, `anAnswerForAPersonWhoSignedOutMeanwhileIsDiscarded`; `bt/domain/BillingAnswerApplierTest` | PASS |
| FR-012 stable derived key, never logged/stored | `bt/domain/play/PlayKeysTest`; Replay `theAppsKeysAreExactlyTheKeysTheServerAcceptedAndReplayed` (live); `bt/GooglePlayLoggingTest` | PASS |
| FR-013 pending sent once per run | Verifier `aPendingPurchaseIsSentOnce…` | PASS |
| FR-014 apply answer, refresh, keep state/test in memory | Verifier first test; `BillingAnswerApplierTest`; Replay state tests | PASS |
| FR-015 `202` recheck ≥ 1 h | Verifier `…RecheckedNoSoonerThanAnHour…` | PASS |
| FR-016 refusals by code | Verifier tests per code; Contract per code; Replay (live bodies); Retry-After honoured in full (gap G4 closed) | PASS |
| FR-017 silent background, "You're on <plan>." once | Verifier `aChangedPlanAnnouncesItselfOnlyWhilePlanScreenShows`, `theSecondSend…`; PlanPlay `theActivatedNoteBecomesAOneShotEffect` | PASS |
| FR-020 sign-in restore replaces the verify pass | Coord `aRealSignInAfterBeingSignedOut…`, `aColdStartRunsAVerifyPassNotARestore` | PASS |
| FR-021 tap sends everything, progress, disabled | Restore `aTapSends…`; PlanPlay `restoreRunsThen…`; goldens | PASS |
| FR-022 25 per request, distinct, most recent first | Restore `twentySixPurchases…`, `aRepeatedTokenIsSentOnce`; Contract `restoreSendsZeroOneAndTwentyFiveItems`; Replay (25 live) | PASS |
| FR-023 combined message, kept until dismissed, announced | Restore `theSummaryAssemblesOneCombinedMessage`; PlanPlay; `ht/PlanAccessibilityTest` (polite live region, added) | PASS (gap G5 closed) |
| FR-024 refusals with Try again; generic error with request id | Restore `eachRefusalHasItsOwnResult`; PlanPlay; Replay `everyLiveRestoreRefusalMapsToItsOwnResult…` | PASS |
| FR-025 unavailable: say so, send nothing; sign-in restore does nothing | Restore `googlePlayUnavailableSendsNothing` | PASS |
| FR-026 no email or plan gate | Restore `restoreNeedsNoVerifiedEmailAndNoPlan` | PASS |
| FR-030 Manage for Google Play only | PlanPlay `manageShows…`, `manageAndFixPaymentOpen…`; `bt/domain/play/PlayStoreLinksTest` | PASS |
| FR-031 grace / hold / paused notes | PlanPlay; Replay | PASS |
| FR-032 another-account note, no Sign out | PlanPlay `pendingAndAnotherAccountFollowTheNotes`; golden; `ht/GooglePlayStringsTest` | PASS |
| FR-033 pending note | PlanPlay; Verifier | PASS |
| FR-034 test label | PlanPlay `theTestLabel…` | PASS |
| FR-035 notes are state, cleared at sign-out | `PlayBillingNotesTest`; Verifier `signOutCancels…` | PASS |
| FR-036 Google Play price, exact minor units, no float | `PlayPricesTest` | PASS |
| FR-037 catalogue amount never shown | `CataloguePlanTest`; PlanPlay `cardPricesComeOnlyFromGooglePlay` | PASS |
| FR-038 requested on open / catalogue change, once a minute | PlanPlay `openingRuns…`, `aChangedProductSet…`; `PlayPricesTest` `theLoaderReturns…CachesForAMinute` | PASS |
| FR-040 switch off, no reachable path | `BillingLayeringTest` `noReachableCodePath…` | PASS |
| FR-041–FR-049 Subscribe (US6) | Gated G2, not built | GATED |
| FR-050 no token/key/reference/price in logs or storage | `bt/GooglePlayLoggingTest`; `PlayKeysTest` redaction; `ht/BillingLayeringTest` | PASS |
| FR-051 sign-out clears every note | `PlayBillingNotesTest`; Verifier | PASS |
| FR-052 new codes with copy; key codes fall to the generic error | `bt/core/network/ApiErrorCodeTest`; `bt/ui/BillingErrorCopyTest`; Replay `theLiveKeyRefusals…` (400 key required/invalid handled as unexpected) | PASS |
| FR-053 EN/AR, RTL, 200 %, 48 dp, tags, not by colour alone | goldens `ht/PlanScreenScreenshotTest` (EN, AR, 200 %); `ht/PlanAccessibilityTest` (48 dp, tags, live regions, 200 %); `ht/GooglePlayStringsTest` | PASS |
| FR-054 plain subscription-centre link; open failure message | `PlayStoreLinksTest`; PlanPlay effects | PASS |
| FR-055 iOS: no Restore/Manage/Fix payment | `AppGraphTest` (iOS flag false binds the unavailable port); PlanPlay (`playSupported=false` shows none); `UnavailablePlayBillingClientTest` | PASS |
| FR-056 dev-only scripted Google Play | `bt/devtools/playbilling/PlayBillingDevTest`; `AppGraphTest` (release binds no scripted client) | PASS |

### Success criteria

| Id | Evidence | Result |
|---|---|---|
| SC-001 plan within one pass | Verifier first test; Replay active test | PASS (host proxy) |
| SC-002 reinstall then restore | Coord sign-in restore; Restore; Replay mixed restore | PASS (host proxy) |
| SC-003 0 acknowledgements/local grants | Layering scan; fake port; Verifier | PASS |
| SC-004 retries reuse the key | Verifier key tests; live keys equal the server's | PASS |
| SC-005 every refusal/outcome has its own copy, no server text | `GooglePlayStringsTest` `everyRefusalAndRestoreOutcomeHasItsOwnKey`; `BillingErrorCopyTest` | PASS |
| SC-006 no secrets in logs/storage | `GooglePlayLoggingTest` | PASS |
| SC-007 no Subscribe/price from other channels | PlanPlay, `GooglePlayStringsTest`, Layering | PASS |
| SC-008 AR/EN/200 % review | goldens + `PlanAccessibilityTest` | PASS |

### Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Killed after paying, same key | Verifier `theSameKeyIsUsedAcrossARetryAndAcrossAProcessDeath` | PASS |
| Pending never completes: note stops | Verifier `recomputePending` cases (pending cleared when Google Play stops listing it) | PASS |
| Pending and sign-out | Verifier sign-out test; Notes test | PASS |
| Signed out: nothing sent | Verifier `nothingIsSentWhen…NobodyIsSignedIn`; Coord `signedOutNothingRunsFor…` | PASS |
| Account deletion pending: purchases sent as for any account | No account-state check in the pipeline (code path); Restore `restoreNeedsNoVerifiedEmailAndNoPlan` | PASS (by construction) |
| Two triggers at once | Verifier `aTriggerDuringAPassWaitsForIt…` | PASS |
| More than 25 purchases | Restore `twentySixPurchases…` | PASS |
| Unknown product (`VALIDATION_FAILED` at `/product_id`) | Verifier `anUnknownProductReloadsTheCatalogueOnce…`; Replay (live body) | PASS |
| Unknown state / outcome | Contract `anUnknownStateOrMemberDecodes…`, `anUnknownRestoreOutcome…`; Restore `anUnknownOutcomeDecodesAsUnknown` | PASS |
| Rate limit on verify / restore | Verifier; Restore; Replay (live 429, wait honoured) | PASS |
| Price currency differs / does not fit digits | `PlayPricesTest` (KWD, JPY, inexact → null) | PASS |
| InstaPay or manual governs with a Google Play purchase also owned | Replay `aManualSubscriptionThatGovernsKeepsTheYearlyPlan…` (live precedence) | PASS |
| Upgrade/downgrade not offered | `PlanPlayReducerTest` (no action exists); Layering | PASS |
| iOS compiles, Google Play unavailable | `:shared:compileKotlinIosSimulatorArm64` in the gate; `AppGraphTest` | PASS |
| Purchase made with another account's reference | Replay `bothLiveAlreadyLinkedAnswers…` (by first link and by reference) | PASS |

## 2. Live QA against the local backend (T049–T050)

**Contract check (T049).** `check-backend-contract.sh`: backend `1a0e1cd`; catalogue changes ADDED `PURCHASE_ALREADY_LINKED` (409, never) and `PURCHASE_NOT_VERIFIED` (422, never); "Consistent: every client code exists in the catalogue with the same retry class"; no `NOT IN CATALOGUE` line. `google-play.yaml` was committed unchanged at v1.15. The commit is recorded in spec.md and plan.md.

**Environment.** The dev backend's Play driver is `disabled` (no `BILLING_PLAY_*` in `.env`), and a dev-database edit that maps `plus_monthly` to the catalogue (needed for any grant) was not permitted. The dev database and the dev `.env` were therefore not touched (`.env` sha256 identical before and after; no seed, refresh or plan edit on the dev database). The QA ran against a second, throwaway PHP server inside the dev container (`php -S` on port 8099, scripted `fake` gateway, throwaway account-reference and lookup keys, `APP_ENV=local`) on the **test** database `saveme_backend_main_test` (already migrated; the billing seeder mapped `plus_monthly` / `plus_yearly` there). Seven throwaway users were registered through `POST /auth/register`, verified with tinker. All requests carried `X-Client-Platform: android` and `X-Client-Version`. Google was never called.

**Counts.** 89 requests recorded; 54 of them on `billing/google/*`; statuses: 63 × 200, 2 × 202, 8 × 422, 4 × 409, 4 × 400, 3 × 401, 3 × 503, 2 × 429. 59 captured answers in `CapturedPlayResponses.kt`; 27 replay tests (`GooglePlayLiveReplayTest`) all green.

| Case | Real behaviour | App result |
|---|---|---|
| `GET account-reference` (twice, two accounts, no bearer) | 200 `data.obfuscated_account_id` = 64 lowercase hex, identical on repeat, different per account; 401 without bearer | PASS (decodes, well-formed) |
| verify, scripted `active` unacknowledged | 200, `purchase{product_id,state:active,acknowledged:true,auto_renew:true,current_period_end,test:true}`, `entitlements`, `subscription{provider:google_play,status:active}`; server acknowledged | PASS: Plus applied, test note set, refresh launched |
| same key again | 200 + `Idempotency-Replayed: true`, same body | PASS |
| same token, new key | 200 (already linked to the caller) | PASS |
| key reused with another body | 409 `IDEMPOTENCY_KEY_REUSED` | PASS (refused for the run) |
| missing / too short key | 400 `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_KEY_INVALID` | PASS (generic, next trigger; cannot occur from the app) |
| pending purchase | 202, same body shape, `state:pending`, plan stays Free; replay of the key → 202 + `Idempotency-Replayed` | PASS: nothing granted, pending note |
| purchased later, new key | 200 Plus; `/me/subscription` active | PASS |
| key replay after Google's state changed | still the first answer (replayed) | PASS: why the state is part of the key |
| other account, token already linked | 409 `PURCHASE_ALREADY_LINKED`; the account stays Free | PASS: note once, not resent |
| other account, token carrying the first account's reference | 409 `PURCHASE_ALREADY_LINKED` | PASS |
| unscripted token | 422 `PURCHASE_NOT_VERIFIED` | PASS |
| unknown product id | 422 `VALIDATION_FAILED`, `errors[0].pointer=/product_id`, `INVALID_ENUM_VALUE` | PASS: one catalogue reload, not resent |
| empty token / extra member | 422 `VALIDATION_FAILED` at `/purchase_token` / `/price` (`UNKNOWN_FIELD`) | PASS |
| no bearer | 401 `UNAUTHENTICATED` | PASS: stops the pass |
| 21st-hour verify budget | 429 `RATE_LIMITED`, `Retry-After: 3563`, `retry_after_seconds` (the 18th attempt after earlier ones; the limit is 20 an hour) | PASS after fix G4 |
| gateway unavailable (driver `disabled`) | 503 `SERVICE_UNAVAILABLE`, `Retry-After: 3600` | PASS: same key, waits as told |
| states via re-verify of one token: grace, active, canceled, on_hold, paused, expired, revoked | 200 each; `/me/subscription`: grace → `status:grace` + `grace_until`; canceled → `active`, `auto_renew:false`; on_hold, paused, expired, revoked → Free plan, `provider:null` | PASS: grace warning; canceled "ends"; hold/paused notes this run only; expired/revoked plain free plan |
| manual subscription granted with `OperatorSubscriptions::grant` (UUID operator) then a Google Play purchase | 200; the answer's `entitlements` and `/me/subscription` stay the manual Plus · Yearly (`provider:manual`) | PASS: no Manage link, no Google Play notes |
| restore, empty list | 200 `results:[]` + current entitlements/subscription | PASS ("Nothing to restore") |
| restore, mixed 4 items | 200 outcomes `linked`, `linked_elsewhere`, `not_verified`, `pending` in request order; Plus applied | PASS: message Restored + another account + pending |
| restore replay / again | replay: same body + `Idempotency-Replayed`; again: `already_yours` | PASS |
| restore, 25 unknown items | 200, 25 × `not_verified` | PASS |
| restore, 26 items / duplicate tokens / unknown product | 422 `VALIDATION_FAILED` (`/purchases` `OUT_OF_RANGE`; `/purchases/1/purchase_token` `INVALID_TYPE`; `/purchases/0/product_id`) | PASS (the app never sends them: de-duplicated, chunked at 25) |
| restore key missing / reused / no bearer | 400 / 409 / 401 | PASS |
| restore budget | 429 `RATE_LIMITED`, `Retry-After: 3586`, after 6 more empty restores | PASS |
| restore with the gateway unavailable | 503, `Retry-After: 3600` (with items) and 60 (empty) | PASS |
| old client version | 400 `CLIENT_VERSION_INVALID` (not 426) for an unparsable version | not a Google Play case; handled app-wide |

**Not reproducible live (covered by the contract examples and host tests):** `IDEMPOTENCY_REQUEST_IN_PROGRESS` (needs two concurrent requests with one key; the scripted gateway answers instantly), `acknowledged:false` (needs the fake's in-memory `failNextAcknowledgements`, which a script file cannot set), per-item `unavailable` (needs `failNext` on one item), and a genuine 426.

**Keys.** The `gp1:` keys the app derives (`PlayKeys.verify`) equal, byte for byte, the keys that were sent live and accepted: `theAppsKeysAreExactlyTheKeysTheServerAcceptedAndReplayed`. Same key, same state → replayed answer; pending → purchased changes the key (the server would otherwise replay the `202`); the hour-bucket recheck key is distinct.

**Cleanup.** The throwaway server was stopped; the script/keys directory in the backend's git-ignored `storage/app` was removed; `.env` is byte-identical to its original (sha256 `e1503f47…c53ac5`); `dev queue:restart` was run. QA users and their rows remain in the **test** database only (the backend test suite refreshes that database).

## 3. Defects and drift found

1. **Client defect (fixed, G4).** `PlayPurchaseVerifier.retryLater` clamped every wait, including the server's `Retry-After`, to 15 minutes. The live rate limit and gateway-unavailable answers say about an hour, so the app resent after 15 minutes against a limit that had not reset. Now the server's wait is honoured (up to 24 h, the idempotency window); only the app's own 30 s doubling stops at 15 minutes. Test: `theServersOwnWaitIsHonouredForTheLiveRateLimitAndTheLiveUnavailableAnswers`.
2. **Tooling defect (fixed).** `run-e2e.sh` (`play_account_reference`) and `.maestro/common/api.js` read `data.account_reference`; the answer's member is `data.obfuscated_account_id`. The deferred E2E seeds would have scripted an empty reference.
3. **Backend observations (no client impact, no backend change made):** a pending `202` still carries a `current_period_end`; with the fake, re-verifying one token moves `current_period_end` (the script computes "now + days" on every read). Neither is read by the app for pending purchases.
4. No backend bug found: every answer matched `google-play.yaml` v1.15 and `error-catalog.yaml`.

## 4. Gaps found by the traceability pass, and how each was closed

| Gap | Closed by |
|---|---|
| G1 FR-003 (never queued in the outbox) had no test | `BillingLayeringTest.noBillingSourceTouchesTheSyncEngineOrItsOutbox` |
| G2 FR-004 degrade (`UnavailablePlayBillingClient`) tested only through the graph | `UnavailablePlayBillingClientTest` |
| G3 US5.3 Arabic formatting of Google Play's price with Western digits | `PlayPricesTest.googlePlaysPriceIsWrittenByTheAppsFormatter…` |
| G4 Retry-After clamped to 15 min (live finding) | fix in `PlayPurchaseVerifier` + live-replay test |
| G5 FR-023/FR-053: restore message announced, 48 dp targets, tags (screenshots do not assert them) | `PlanAccessibilityTest` |

Deferred or gated, left as they are: T041 (device flows), T051–T054 (G2 Subscribe), T055–T061 (G1 Play Billing wiring), T062 (summary).
