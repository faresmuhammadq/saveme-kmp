# QA report: Recurring transactions (client, spec 010)

**Date**: 2026-10-04 · **Branch**: `010-recurring` · **Mode**: no device (owner policy; device tasks T048, T060, T069, T075, T080, T086, T100, T101 stay deferred) · **Backend**: local dev stack at `saveme-backend@192e3ab` (`recurring.yaml` v1.7, unchanged since `c0e9844`; no backend change needed).

Evidence is a passing host test (`./gradlew :shared:testAndroidHostTest`), a live backend answer (section 5) or a code path. Test paths: `ct/` = `shared/src/commonTest/kotlin/com/fares/saveme/`, `ht/` = `shared/src/androidHostTest/kotlin/com/fares/saveme/`, `rt/` = `ct/feature/recurring/`. "Captured" = `rt/CapturedRecurringResponses.kt` (157 live answers with the requests QA sent) driven through the real `RecurringApi`, engine, `HttpSyncApi`, handlers, appliers and service by `rt/RecurringCapturedResponsesTest` (24 tests). `rt/domain/RecurringQaGapsTest` (4 tests) closes the gaps found below.

## 1. Acceptance scenarios

| Scenario | Evidence | Result |
|---|---|---|
| US1-S1 rule listed at once, "Waiting to sync" | `RecurringServiceCreateTest.aValidRuleIsAPlaceholderPlusOneQueuedWriteInTheWalletsCurrency`, `RecurringTest.rulesAreGroupedByStatusAndWaitingChangesAreCounted`; captured create for 10 shapes | PASS |
| US1-S2 next five dates, server schedule | `RuleDatesTest`, `RuleEditorTest.thePreviewIsRecomputedAfterEachChange`, `RecurringServiceCreateTest.thePreviewIsTheSchedulesOwnDates`; captured `thePhonesDatesEqualTheServersForEveryCapturedRule` (50+ live rounds) | PASS |
| US1-S3 upcoming listed, never in a balance | `RecurringDueTest`, `RecurringFeedAppliersTest`, `TransactionsDueSectionTest.dueItemsAreNeverInTheTotals…`; captured feed test | PASS |
| US1-S4 unsynced rule: preview, no confirm/skip | `RuleDetailTest.thePreviewStandsInUntilTheServerSendsDates`, `RecurringMatchesTest.aRuleWithoutReceivedOccurrencesHasNothingToMatch` | PASS |
| US1-S5 Make recurring creates only the rule | `TransactionEditorRecurringTest.makeRecurringHandsOverTheFormAndRecordsNothing`, `RuleEditorTest.aPrefillFromTheTransactionEditorFillsTheForm` | PASS |
| US1-S6 viewer/archived/unverified | `RecurringServiceCreateTest.aViewerAnArchivedWalletAndAnUnknownWalletAreRefused`, `.anUnverifiedAddressQueuesNothing`, `RecurringTest.addOpensTheEditor…OnlyForWriters…`, `TransactionEditorRecurringTest.makeRecurringIsOnlyOffered…` | PASS |
| US1-S7 other member's rule appears | `RecurringRolesTest`, `RecurringFeedAppliersTest`; captured feed (rules of editor B) | PASS |
| US2-S1 Due / Missed | `OccurrenceStateTest`, `RecurringDueTest`, `TransactionsDueSectionTest`, `HomeViewModelTest.theCardShowsWhenSomethingIsDue…`; captured states in the rule's own today | PASS |
| US2-S2 confirm shows transaction, balance, waiting | `RecurringServiceOccurrenceTest.confirmingQueuesOneWriteOnTheBaseVersionAndShowsTheTransactionAtOnce`, `PendingConfirmationTest.theBalanceCountsItOnceAndBothWalletsOfATransfer` | PASS |
| US2-S3 one transaction, same id, no jump | `PendingConfirmationTest.confirmThenAcceptedThen…`, `OccurrenceClashTest.theBalanceCountsTheConfirmation…`; captured `aConfirmationIsSentAsQaSentIt…` (-383,634 → -409,634 with no intermediate value) | PASS |
| US2-S4 skip / undo | `RecurringServiceOccurrenceTest.skipAndUndoSkipSendNoTransactionId`; captured `skipUndoAndEditOneAreSentAsQaSentThem` | PASS |
| US2-S5 confirmed is read-only, links to its transaction; deleting it does not un-confirm | `OccurrenceTest.aConfirmedOccurrenceIsReadOnlyWithAnOpenTransaction`; **gap**: `RecurringQaGapsTest.aConfirmedOccurrenceWhoseTransactionWasDeletedStillShowsAsConfirmed` | PASS (gap closed) |
| US2-S6 refused auto-confirm shown due with the reason | `RecurringCopyTest.everyAutoConfirmRefusalMapsToARecurringKey`; captured feed: `not_permitted`, `counter_wallet_unavailable`, `currency_mismatch` (live, after `wallets:materialise-occurrences`) | PASS |
| US2-S7 server-confirmed shows in list "From a recurring rule" | `TransactionsServiceListTest.addedByNames…TheRecurringLinkShows`; captured (3 auto-confirmed rounds with transactions) | PASS |
| US3-S1 template edit | `RecurringServiceChangeTest.savingSendsTheWholeRule…`; captured template change (server recomputes only unresolved, unedited rounds) | PASS |
| US3-S2 change from a date | `RecurringSplitTest` (8); captured `aSplitEndsTheOldRuleThenSendsTheNewOneAsQaSentThem` | PASS |
| US3-S3 frozen members | `RecurringServiceChangeTest.frozenScheduleMembers…`, `.aGameyaOrATotalAlsoFreezes…`, `RuleEditorTest.frozenMembersLockTheirFieldsInEditModeOnly`; captured: 8 live `IMMUTABLE_FIELD` answers incl. `/amount_minor` for gam'eya and total | PASS |
| US3-S4 edit one occurrence | `RecurringServiceOccurrenceTest.editingOneOccurrence…`; captured edit | PASS |
| US3-S5 pause / end | `RecurringServiceChangeTest.pausingResumingAndEndingQueue…`; captured pause/resume/end and ended-rule refusals | PASS |
| US3-S6 delete | `RecurringServiceChangeTest.deletingASentRule…`, `RuleDetailTest.endingAndDeletingAskFirst`; captured delete with body | PASS |
| US3-S7 clash choices | `OccurrenceClashTest`, `RuleDetailTest.clashAndRefusalChoicesPassTheRuleId`, `RecurringRuleSyncHandlerTest`; captured stale save/delete | PASS |
| US4-S1..S4 presets | `RecurringPresetsTest`, `AllocationTest`, `RuleEditorTest.aGameyaIsCreatedWithItsRoundsAndPayout`; captured gam'eya (payout 1,000,000 on round 4) and installment (4 × 83,334 then 83,333) | PASS |
| US5-S1..S4 match offer | `RecurringMatchesTest` (9), `TransactionEditorRecurringTest` | PASS |
| US6-S1..S2 catch up | `RecurringCatchUpTest` (7); captured materialise 200/404/409 | PASS |
| US6-S3 shared wallet | `RecurringRolesTest`; captured viewer feed read, editor skip of another member's occurrence | PASS |
| US6-S4 wallet loss | `RecurringWalletCleanupTest`, `SignOutWipeTest` | PASS |

**26 scenarios: 26 PASS** (1 after closing a gap).

## 2. Functional requirements

| FR | Evidence | Result |
|---|---|---|
| 001 phone's copy only; UUIDv7 | `RecurringLayeringTest`, `RecurringDaoTest`, `RecurringServiceCreateTest` | PASS |
| 002, 003 | `RecurringServiceCreateTest`, `TransactionEditorRecurringTest` | PASS |
| 004, 005 | `RuleEditorTest` (29), `RecurringServiceCreateTest.theServersRulesAreCheckedBeforeAnythingIsQueued` | PASS |
| 006 | `RuleDatesTest`, captured SC-002 | PASS |
| 007 | `RecurringRuleSyncHandlerTest.aNeverSentRule…WalletsPresentCurrency` | PASS |
| 008, 009, 010 | `RecurringServiceCreateTest.aTransferNeedsASameCurrencyWritableDestination`, `RecurringPresetsTest` | PASS |
| 011 auto-confirm off by default | `RuleEditorTest.creatingStartsOnToday…`; captured rules sent with `auto_confirm:false` | PASS |
| 012, 013 | `RecurringServiceChangeTest`, `RuleEditorTest`; captured `end cuts off round two` (OUT_OF_RANGE `/end_date`) | PASS |
| 014 split order and cascade | `RecurringSplitTest`, captured split | PASS |
| 015, 016 | `RuleDetailTest`, `RecurringServiceChangeTest` | PASS |
| 017, 018 | `OccurrenceStateTest`, `RuleTodayTest`, `RecurringDueTest` | PASS |
| 019, 020 | `TransactionsDueSectionTest`, `HomeViewModelTest`, `RecurringDueTest.theHomeCountCoversOnlyWalletsThePersonCanWrite` | PASS |
| 021, 022 | `RecurringServiceOccurrenceTest`, `PendingConfirmationTest`, captured confirm | PASS |
| 023 | `RecurringServiceOccurrenceTest`, captured skip/undo/edit | PASS |
| 024 | `OccurrenceClashTest`, captured race (409 with confirmed `current`; pending transaction dropped, never re-sent) | PASS |
| 025, 035 | `RecurringCopyTest`, captured refusals (`/status` `INVALID_ENUM_VALUE` and `CURRENCY_MISMATCH` on confirm, `FORBIDDEN`, 404) | PASS |
| 026 | gap test above | PASS (gap closed) |
| 027 manual save clears the reason | server clears it (captured: skip and confirm of refused rounds answer `auto_confirm_refusal:null`); **gap**: the screen kept showing the reason while a skip/edit waited, fixed in `OccurrenceScreen` (hidden while any save of the occurrence waits) | PASS (fixed) |
| 028 | `RecurringCatchUpTest`, captured | PASS |
| 029 | `RecurringFeedAppliersTest`, `RecurringTest.openingAsksOnceWithoutForceAndPullForces`, captured batch (`aRuleAndTwoOccurrenceChangesTravelInOneBatch…`: one request, byte-equal items, all settle) | PASS |
| 030 | `Migration6To7Test.walletScopesLosePositionsOnce…` | PASS |
| 031 | `RecurringMatchesTest`, `TransactionEditorRecurringTest` | PASS |
| 032 | `RecurringCategoryUsesTest` | PASS |
| 033 | `RecurringWalletCleanupTest`, `SignOutWipeTest` | PASS |
| 034 | `RecurringApiContractTest`, captured (every create/save/confirm request equals QA's member for member) | PASS |
| 036 | `RecurringLoggingTest` | PASS |
| 037 | `RecurringStringsTest` (7) | PASS |
| 038 | `TransactionEditorRecurringTest.aProducedTransactionOffersTheRuleOnlyWhenItIsOnThePhone` | PASS |
| 039 | **gap**: `RecurringQaGapsTest.whileARuleEditWaitsItsOccurrencesKeepTheirValues…`; copy `recurring_update_after_sync` in `RuleDetailScreen`/`RuleEditorScreen` | PASS (gap closed) |

**39 FRs: 39 PASS.**

## 3. Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Month-end anchors | `RuleDatesTest`; captured day 31 (30 Sep, 31 Oct), day 29, every 2 months on 30, yearly 29 Feb (next 28 Feb 2027, ordinal 4) | PASS |
| Today across zones | `RuleTodayTest`; captured rules carry `timezone` `Africa/Cairo` | PASS |
| Rule created offline | `RuleDetailTest`, `RecurringMatchesTest` | PASS |
| Confirm racing auto-confirm / another member | `OccurrenceClashTest`; captured race (live 409) | PASS |
| Confirm racing a rule edit | `OccurrenceClashTest.aClashWithAnOccurrenceTheOtherDeviceMerelyChanged…KeepMineResends…` | PASS |
| Confirm refused | `OccurrenceClashTest.aRefusedConfirmationIsNotSaved…`; captured `theRealRefusedConfirmationsAreNotSavedWithTheirReason…` | PASS |
| Rule deleted elsewhere | `OccurrenceClashTest.aDeletedOccurrenceOffersOnlyTheDeletionToBeAccepted`, `RecurringFeedAppliersTest.aRuleTombstoneRemoves…` | PASS |
| Ended rule | `RuleDetailTest.anEndedRuleOnlyCanBeDeleted`; captured ended-rule refusals | PASS |
| Wallet currency changed while a rule waits | `RecurringRuleSyncHandlerTest`; captured `CURRENCY_MISMATCH` on `/currency` | PASS |
| Category deleted or archived | `RecurringCategoryUsesTest` | PASS |
| Transfer rule, T-02 | `RecurringServiceCreateTest`, `RecurringServiceOccurrenceTest.aTransferIsNotOffered…`; captured: editor with viewer rights in the destination refused `FORBIDDEN` on rule and on confirm | PASS |
| Split refused | `RecurringSplitTest.discardingTheOldChangeDiscardsTheNewRuleWithIt` | PASS |
| Occurrence outside pattern | `RecurringDaoTest` (date order); captured edited round (date 7 Oct, scheduled 5 Oct) | PASS |
| 50 rules, 2,000 occurrences | `RecurringPerformanceTest`, `RecurringDaoTest.aListOfTwoThousandOccurrences…` | PASS |
| Process killed after confirming offline | **gap**: `RecurringQaGapsTest.aConfirmationMadeOfflineIsSentByAnEngineStartedAfterTheProcessWasKilled` | PASS (gap closed) |
| Sign-out | `SignOutWipeTest` | PASS |
| Unknown values | `RecurringApiContractTest.responsesIgnoreUnknownMembers…`, `RuleDetailTest.aRuleWithUnknownValuesIsReadOnly…`, `RecurringCopyTest` | PASS |

**17 edge cases: 17 PASS** (1 after closing a gap).

Success criteria: SC-001..SC-008 are covered by the rows above (SC-002 by the captured date comparison, SC-004 by the race/replay/restart tests, SC-005 by the captured balance sequence, SC-008 by `RecurringPerformanceTest`); SC-007's three checkpoints are E2E flows deferred to the owner's phone.

## 4. Spec section 4 invariants and their tests (plan.md fork G)

| Invariant | Tests |
|---|---|
| Screens render from the phone's copy only | `RecurringLayeringTest`, `RecurringDaoTest`, `RecurringFeedAppliersTest` |
| Offline writes queue with base version | `RecurringServiceCreateTest`, `RecurringServiceChangeTest`, `RecurringServiceOccurrenceTest` |
| Phone never creates/deletes an occurrence | `OccurrenceSyncHandlerTest` (`batchItemsAreAlwaysUpsertsWithTheWallet`, `recordTypeAndNoRestore`); live batch `occurrence` + `delete` is rejected by the server (422 `INVALID_ENUM_VALUE` `/items/0/op`) |
| One confirmation = one occurrence write; clash drops the local transaction | `OccurrenceSyncHandlerTest`, `OccurrenceClashTest`, `PendingConfirmationTest`, captured race |
| Occurrence never counts in a balance; pending confirmation counts once | `PendingConfirmationTest`, captured balance sequence |
| Preview dates equal the server's schedule | `RuleDatesTest`, captured `thePhonesDatesEqualTheServersForEveryCapturedRule` |
| Split sums exactly | `AllocationTest`, `RecurringSplitTest`; live installment 83,334 × 4 then 83,333 |
| Due/missed derived in the rule's zone | `RuleTodayTest`, `RecurringDueTest`, `OccurrenceStateTest` |
| Frozen members | `RecurringServiceChangeTest`, `RuleEditorTest`, captured |
| No overlapping rules after a split | `RecurringSplitTest`, `RecurringRuleSyncHandlerTest` |
| Writes carry declared members with explicit nulls | `RecurringApiContractTest`, `RecurringRuleSyncHandlerTest`, captured request equality |
| Failures branch on code and pointer | `RecurringApiContractTest`, `OccurrenceSyncHandlerTest`, `RecurringCopyTest`, captured refusals |
| Wallet removal and sign-out clear rules/occurrences | `RecurringWalletCleanupTest`, `SignOutWipeTest` |
| Migration 6 → 7 | `Migration6To7Test` |
| No amounts/texts in logs | `RecurringLoggingTest` |
| Match offer only for received, unconfirmed, matching occurrences | `RecurringMatchesTest` |

## 5. Contract QA against the live backend (T095, T096)

Accounts registered through `POST /auth/register` (A with a Plus subscription from `OperatorSubscriptions::grant` under a UUID operator id, B, C, and an unverified U), verified by an `email_verified_at` update in `dev tinker`, signed in through `POST /auth/login`. Wallets: two EGP, one USD (plus two more USD for refusals). B joined `CASH` as editor, C as viewer, B joined `BANK` as viewer, through invitation links. Every request carried the app's headers. Only data was written (no backend code change, no reset or seed); the rate limit on registration was cleared once with `cache:clear`. Times are 2026-10-04.

| Call (as the app sends it) | Live answer | Matches the client |
|---|---|---|
| `PUT …/recurring-rules/{id}` create: monthly day 31, weekly every 2, daily every 3 × 5, yearly 29 Feb, monthly 29, every 2 months on 30, gam'eya, installment total, transfer, income | 201 each; occurrences materialised from `max(start, today−90)` to `today+35` and at least the next | Yes; requests byte-equal to the app's (captured test) |
| Identical replay / changed body at version 0 | 200 unchanged / 409 `VERSION_CONFLICT` with the live copy | Yes (clash) |
| 14 invalid creates | 422 with pointers: `CURRENCY_MISMATCH` `/currency`, `/counter_wallet_id`; `INVALID_ENUM_VALUE` `/anchor_day`, `/occurrence_count`, `/amount_basis`, `/frequency`, `/status`; `OUT_OF_RANGE` `/interval`, `/end_date`, `/amount_minor`, `/start_date`, `/occurrence_count` (payout round beyond count); `REQUIRED` `/category_id`; `UNKNOWN_FIELD`; 422 `INVALID_ID` | Yes (each lands on its field) |
| Viewer / unverified / unknown wallet | 403 `FORBIDDEN` / 403 `EMAIL_NOT_VERIFIED` / 404 | Yes |
| Editor transfer rule into a wallet where he is a viewer (T-02) | 403 `FORBIDDEN` | Yes |
| Save after a round was resolved: interval, anchor day, start, frequency, preset/kind/currency | 422 `IMMUTABLE_FIELD` at that member | Yes |
| Gam'eya amount / count / payout round; installment total amount / count | 422 `IMMUTABLE_FIELD` at `/amount_minor`, `/occurrence_count`, `/payout_round` | Yes |
| End before a resolved round | 422 `OUT_OF_RANGE` `/end_date` | Yes |
| Template change, installment texts, unresolved weekly schedule change | 200; unresolved, unedited rounds recomputed; edited round kept | Yes |
| Pause / resume / end | 200 each; rounds dated after today tombstoned on end | Yes |
| Ended rule: any change, resume | 422 `IMMUTABLE_FIELD` | Yes |
| Split: end old on D−1, create new from D | 200 then 201 | Yes (requests byte-equal) |
| `DELETE` rule with `{version}`; replay; stale; missing body | 200 tombstone; 200; 409; 422 `REQUIRED` `/version` | Yes |
| `POST …/materialise` | 200 (also for an ended rule); 409 tombstone; 404 unknown; 403 viewer; 403 `EMAIL_NOT_VERIFIED` | Yes (forced read after 200/404/409) |
| Occurrence confirm with client UUIDv7 `transaction_id` and another amount | 200 v2; transaction with the same id, `recurring_rule_id`, `occurrence_id`, balance −340,000 (opening 100,000 − 440,000) | Yes |
| Identical replay of the confirm | 200 unchanged | Yes |
| Confirm twice with another id (race) | 409 `VERSION_CONFLICT`, `current` confirmed with the first id | Yes (accepted silently, pending transaction dropped) |
| Change a confirmed occurrence | 422 `IMMUTABLE_FIELD` (`/amount_minor`, `/transaction_id`) | Yes |
| Skip, undo, edit one (edited flag, scheduled date kept), confirm a skipped one | 200 each | Yes |
| Occurrence refusals | 422 `REQUIRED` and `INVALID_ENUM_VALUE` at `/transaction_id`; `INVALID_ENUM_VALUE` `/category_id`; `OUT_OF_RANGE` `/amount_minor`, `/date`; `INVALID_ENUM_VALUE` `/status`; `UNKNOWN_FIELD` | Yes (all pointers mapped) |
| Stale version; unknown id; viewer; unverified; non-member | 409; 404; 403; 403 `EMAIL_NOT_VERIFIED`; 404 | Yes |
| Transfer confirm | 200; source −50,000, destination +50,000; editor without T-02 in the destination: 403 `FORBIDDEN` | Yes |
| Gam'eya payout, round, installment round | 200; payout +1,000,000, balances as computed | Yes |
| Auto-confirm: rule dated in the past, `wallets:materialise-occurrences` (13 rules) | nothing before `auto_confirm_from` is aged (no back-posting); after aging in `tinker`: 3 confirmed with transactions by the rule's author | Yes |
| Phone confirm racing the server's auto-confirm | 409 with the confirmed copy and its transaction id | Yes |
| Auto-confirm refusals (author demoted to viewer; destination deleted; destination currency changed) | `auto_confirm_refusal` `not_permitted`, `counter_wallet_unavailable`, `currency_mismatch`, rounds stay `scheduled` | Yes |
| Manual save of a refused round | 200 with the reason cleared | Yes |
| Manual confirm of a transfer whose destination is gone / currency changed | 422 `INVALID_ENUM_VALUE` / `CURRENCY_MISMATCH` at `/status` | Yes |
| `GET /sync/wallets/{id}/changes`, pages of 30 and 200 | `recurring_rule` and `occurrence` rows beside `transaction`, `wallet_member`, `invitation`, `wallet`; position advances | Yes |
| `POST /sync/batch`: rule create, occurrence skip, occurrence confirm; replay | 200, all `applied` (201/200/200); replay `applied` 200/200/200 | Yes |
| Batch refusals | item-level: `rejected` 404 (unknown occurrence), `rejected` 422 `INVALID_ENUM_VALUE` `/frequency`, `conflict` 409 for occurrence and rule delete; request-level 422 for `occurrence`+`delete` (`/items/0/op`) and a rule without `wallet_id` | Yes |

**No backend bug and no client mismatch found.** Quirks of the server worth knowing, none of them a defect: a body identical to the stored one is answered 200 even with a stale `version`; `materialise` answers 200 for an ended rule (the app never offers it); the server accepts a rule overlapping another one (the client prevents overlap by ordering a split).

SC-002: the server's occurrence dates equal `RuleDates` for every captured rule (monthly month-end, yearly 29 February → 28 February, weekly every 1 and 2, daily every 3 with a count, every 2 months, presets).

Clean-up: all 23 rules and 4 wallets deleted through the API, QA sessions signed out. The five QA accounts (`qa010-*@example.test`) and A's manual Plus subscription remain.

## 6. Golden screenshots and the real clock

`ComponentScreenshotTest [transactions]` failed on every day but 2026-10-02 (confirmed: it fails identically on `main`, whose `Dates.kt`, `TransactionsCatalog.kt` and goldens are unchanged by this branch). Cause: Material's date picker marks today from the system clock and offers no seam. Fix: `LocalPickerToday` in `designsystem/component/Dates.kt` (default `null` = system clock); when a day is pinned the picker suppresses its today marker; the catalogue pins 2 Oct 2026. Re-recorded once: the three goldens came out byte-identical, so nothing changed. Other leaks: none reachable from screenshot tests. `Clock.System` is used only by `WalletDetailViewModel` (default parameter, tests inject), `TransactionsModule`'s "today" supplier, and `TodayTicker`/`RuleToday` (injected clocks); screens are rendered from fixed states; the only picker in screenshots is the catalogue's. The change of the end/delete confirmation wording (section 7) re-recorded three `rule_end_dialog` goldens.

## 7. Maestro static review (`.maestro/010-recurring`)

| Risk | Finding | Fix |
|---|---|---|
| Dialog buttons by text | The rule screen's button "End rule" and the dialog's confirm "End rule" (likewise "Delete rule") are the same text; a text tap is ambiguous while the dialog is open (dialog windows expose no tags) | Dialog confirms reworded "Yes, end it" / "Yes, delete it" (Arabic "نعم، أنهِها" / "نعم، احذفها"); `US3-pause-end-delete.yaml` taps those |
| `checked` vs `selected` | No flow asserts either; presets and basis are tapped by tag (`ruleEditor.preset.gameya`, `.installment`, `ruleEditor.basis.total`, tags verified in `RuleEditorScreen`) | none needed |
| Date picker day computation | No flow opens the picker; the day field is typed (`eraseText: 3`, `31`), and "Change from a date" keeps the default date (the next round, after the last confirmed one) | none needed |
| Tags and strings | Every `id:` used exists in the sources; texts ("Skipped", "Upcoming", "Paused", "Ended", "Preview", "Dates appear after sync", "Already confirmed", "Dates are up to date.", "Checking for new dates needs a connection.") match `strings.xml` | none needed |
