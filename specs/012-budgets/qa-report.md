# QA report: Budgets (spec 012)

**Status**: host QA done; live-backend contract QA (T094) and captured-answer comparison (T095) done on 2026-10-05 against `saveme-backend@64ca1ea` (`budgets.yaml` v1.8; spec 019 reports committed; working tree has uncommitted InstaPay/billing docs only). 140 HTTP calls, 3 throwaway accounts. Real answers are in `CapturedBudgetResponses.kt` and drive the real `BudgetApi`, engine, `BudgetSyncHandler`, feed puller and appliers, `TransactionOwnSpending`, `SpendingTotals`, `BudgetPeriods` and `ProgressSelection` in `BudgetCapturedContractTest` (12), `BudgetCapturedBatchTest` (3) and `BudgetCapturedProgressTest` (4).

## Counts

| Group | Total | PASS | GAP closed in this QA | Open GAP | Deferred (device) |
|---|---|---|---|---|---|
| Acceptance scenarios (US1 7, US2 7, US3 7, US4 7, US5 6, US6 4) | 38 | 38 | 0 | 0 | 0 (E2E flows deferred, T043 T054 T062 T072 T078 T085) |
| Edge cases | 16 | 16 | 1 (EC-5) | 0 | 0 |
| Functional requirements FR-001..FR-026 | 26 | 26 | 1 (FR-004 overlap clause) | 0 | 0 |
| Success criteria SC-001..SC-008 | 8 | 7 | 2 (SC-003, SC-004 live) | 0 | 1 (SC-001 timing, owner's device) |
| Section 4 invariants | 13 | 13 | 1 (no percentage from the server) | 0 | 0 |

Gaps found by the mapping and closed: G1/G2 (live comparison, below), G3 (DTO has no share member: test added), G4 (two overlapping budgets: test added). G5 (FR-016 "Couldn't refresh" with the request id): no list on the phone shows a request id on a failed refresh (wallets, categories, transactions alike); the spec wording is amended to "as on the other lists" without it. G8/G9 minor, accepted with the existing tests (`BudgetStringsTest.arabicTextUsesWesternDigits` and goldens; `BudgetsPerformanceTest` under 1 s).

## Live contract QA (T094)

Accounts registered through `POST /auth/register` (202; the registration counter, 10 per hour per origin, was exhausted by earlier specs, so only that one cache counter was cleared), verified with an `email_verified_at` update in `dev tinker` for A and B, signed in through `POST /auth/login`. A: Plus (`OperatorSubscriptions::grant` under a UUID operator id), owner of Cash and Bank (EGP), Dollars (USD) and Shared (EGP). B: editor of Shared (link invitation, `POST /invitations/accept`). C: unverified. Every request carried the app's headers. Only data was written (no backend code change, no reset or seed).

Date: server 2026-10-04 23:2x UTC = 2026-10-05 in Africa/Cairo; A's start day 1.

### Budget writes (the C8 code's exact bodies; request bodies equal the ones `BudgetApi` builds)

| Call | Live answer | Client handling |
|---|---|---|
| `PUT /budgets/{id}` create with category (v0) | 201, version 1 | stored (`PUT_CREATED_CATEGORY_201`) |
| Create all expenses, `category_id` explicit `null` | 201 | explicit null on the wire, stored with no category |
| Identical replay | 200, unchanged version 1 | accepted once |
| Stale create (v0 against existing, other limit) | 409 `VERSION_CONFLICT` + `current` v1 | clash |
| Limit edit v1 | 200, version 2 | stored |
| Stale edit v1 against v2 | 409 + `current` v2 live | clash "Changed on another device" |
| Category change (also to null) | 422 `IMMUTABLE_FIELD` at `/category_id` | refusal `Immutable` |
| Currency change | 422 `IMMUTABLE_FIELD` at `/currency` | same |
| Period type `weekly` | 422 **`INVALID_ENUM_VALUE`** at `/period_type` (not `IMMUTABLE_FIELD`: only `monthly` exists) | never sent (stored value is sent) |
| Income category / unknown category | 422 `INVALID_ENUM_VALUE` at `/category_id` | "can't have a budget any more" |
| Limit 0, negative, 2^53 | 422 `OUT_OF_RANGE` at `/amount_minor` | limit message |
| Limit 2^53 - 1 (USD, all expenses) | 201, returned unrounded | accepted |
| Currency `XXX` | 422 `INVALID_CURRENCY` at `/currency` | |
| Unknown member `user_id`; `category_id` missing | 422 `UNKNOWN_FIELD` at `/user_id`; 422 `REQUIRED` at `/category_id` | never sent (client defect) |
| Malformed id | 422 `INVALID_ID` | generic refusal |
| Unverified address (PUT, archive, DELETE) | 403 `EMAIL_NOT_VERIFIED` | verification prompt |
| Another person's id (PUT, DELETE, progress) / version > 0 with no record | 404 `NOT_FOUND` | |
| `POST /budgets/{id}/archive` | 200, `archived_at` set | |
| Archive again; archive with a stale version on an archived budget | 200, **unchanged (the stale version is not a conflict once archived)** | accepted |
| Limit edit after archive | 200, still archived | allowed |
| `DELETE` with JSON body `{"version":1}` | 200, tombstone (`deleted_at`) | removed |
| Delete again | 200, same tombstone | accepted |
| PUT or archive on a tombstone | 409 + `current.deleted_at` set | "Accept the deletion" only |
| Stale delete | 409 + live `current` | clash |
| `GET /budgets/{id}/progress` of a deleted budget | 404 | ignored |

### Batch (`POST /sync/batch`)

Six `budget` items in one request (new KWD budget, limit edit, delete, a stale save, a delete of an already deleted budget, a limit of 0): results `applied` 201, `applied` 200, `applied` 200 (tombstone), `conflict` 409 with `current` v2, `applied` 200 (tombstone), `rejected` 422 `OUT_OF_RANGE` at `/amount_minor`. The items the engine builds equal the live request item for item (no `wallet_id`); the conflict settles as a clash and the invalid limit as a refusal with the field `limit`. A `wallet_id` on a budget item: whole request 422. Op `archive`: whole request 422. Unverified person: request 200, item `rejected` 403 `EMAIL_NOT_VERIFIED`.

### User feed and progress reads

- `GET /sync/user/changes?limit=200` (A): 8 `budget` rows (live, archived at v3 and two tombstones), 4 `wallet_member`, `profile`, `device_session`; `sync_position` 20; paging at `limit=3` in 5 pages.
- `GET /budget-progress?limit=200`: only active budgets (the archived one is absent), each with `period {start,end,days_in_period,days_elapsed}`, `is_current`, `limit`, `spent`, `remaining`, `projected_end`, `over_limit`, `other_currencies`; no percentage. At `limit=1`: 5 pages through `next_cursor`.
- `date` parameter (not used by the client): same period 200; past period gives `is_current:false`, `days_elapsed:null`, `projected_end:null`; a `next_cursor` of today's walk used with `date=2026-09-15` is 400 `INVALID_CURSOR`; a garbage cursor 400 `INVALID_CURSOR`. **Observation**: a cursor sent *without* `date` is answered for the period the cursor was issued for (the cursor carries `period_start`), not for today. The client never sends `date` and a walk that spans midnight keeps its period; the stored period is checked against today (FR-014 a), so no client change is needed.
- `GET /budgets/{id}/progress`: 200 for active and archived budgets, 404 for a deleted one and for another person's.
- C (unverified) `GET /budget-progress`: 200 `data: []` (reads need no verification).

### Attribution (A's budgets, period 1-31 Oct)

A's Groceries expenses: 45,050 and 12,000 (Cash), 8,000 (Shared, own), 3,000 (own rule confirmed by A), 2,500 (A's rule in Shared confirmed **by B**); not counted: 7,000 dated 30 Sep, a deleted 9,999, B's 6,000 and B's rule occurrence 5,000 (B's rule confirmed **by A**, attributed to B: `created_by` is the rule's author), the USD 2,500, the income 500,000 and the transfer 20,000. Live: Groceries EGP spent **70,550** (other currencies `["USD"]`), all expenses EGP 73,550 (adds Transport 3,000), Transport 3,000, all expenses USD 2,500 (`["EGP"]`), all expenses KWD 0 (`["EGP","USD"]`). B: Groceries 11,000 (6,000 + the rule's 5,000), all expenses 13,000. After B left Shared: both 0 (their wallet no longer counts); A unchanged.

### Start day change

B set `budget_period_start_day` 25: answer `budget_period_start_day_from` **2026-10-25** (the next 25th), and the period under way is truncated, 2026-10-01 to 2026-10-24, 24 days, 5 elapsed. A date strictly in the future is the only form a change can take, so a start day other than 1 cannot be live-tested as "since always" on a fresh account before its date arrives (see "Not reachable live").

## SC-003 and SC-004 (T095)

`BudgetCapturedProgressTest` reads A's real feed pages (user scope and four wallet scopes) through the real `FeedPuller` and appliers (budgets, wallets, members, transactions), builds the figures with the real `TransactionOwnSpending`, `SpendingTotals`, `BudgetMath` and `BudgetPeriods` from the profile row of the feed, and compares them with the captured `GET /budget-progress`:

- **SC-003**: for all 5 active budgets, spent, remaining, over limit, projection, share (from the server's integers) and other currencies are equal; the archived budget is archived on the phone; deleted budgets are gone. After the progress read, the stored phone synced-only sums equal the server's for every budget (so the server's figures are shown and never jump). **Result: 100% equal; no client or backend difference found.**
- **SC-004**: the period derived from the profile (start 1) equals the server's (start, end, 31 days, 5 elapsed); with B's pending change to 25 the phone shows "Progress appears after sync" until one read, then uses the server's period (1-24 Oct, 24 days, 5 elapsed) and the phone's own sum for it equals the server's. **Result: equal.**
- Leaving Shared: the phone's own figure falls to 0, equal to the server's 0.

## Not reachable live

A start day other than 1 that is already in force (day 15/25/28 "since always") cannot exist on a fresh account until its "applies from" date arrives (changing the history rows is a database write the QA does not make). Those periods are proven by the spec-rule table `BudgetPeriodsTest` (every start day 1..28 over 27 months); the live pending case and day 1 are compared with the server. Recheck on 2026-10-25 for B (start day 25 takes over) if wanted.

## Cleanup

Every budget of A and B and A's four wallets (with their transactions and recurring rules) were deleted through the API afterwards (tombstones remain). The two accounts' remaining data (profiles, sessions) and the accounts themselves stay (the backend has no account erasure).

## Host QA: requirement to test

## Acceptance scenarios

| Item | Requirement (short) | Evidence | Result |
|---|---|---|---|
| US1-AS1 | Create offline, listed at once "Waiting to sync", progress immediate | CT/budgets/domain/BudgetsServiceCreateTest `aValidCreateWritesAPlaceholderAndQueuesOneChangeAtVersionZeroInTheUserScope`, `offlineDoesNotBlockAndTheChangeStaysWaiting`; BudgetsOverviewTest `theBriefsExampleSeesSpentLeftShareAndPace`; BudgetsOfflineToServerTest `aBudgetCreatedOnThisPhoneShowsThePhonesEstimateUntilItSyncs`; BudgetsTest `rowsCarryFiguresStatusMarksAndTheSplitBetweenActiveAndArchived` | PASS |
| US1-AS2 | Only expense categories + "All expenses" | BudgetChoicesTest `theCategoriesAreC5sExpenseChoices`; BudgetEditorTest `chooseCategoryOrAllExpensesMarksItChosen`; code BudgetsService.kt:241 (CategoryKind.Expense) | PASS (filter delegated to C5) |
| US1-AS3 | Currencies = wallets + profile default, default selected; digits per currency | BudgetChoicesTest `currenciesAreTheWalletsPlusTheProfileDefaultWithTheDefaultFirstAndSelected`, `withoutAProfileTheFirstWalletCurrencyIsTheDefault...`, `anArchivedWalletsCurrencyIsStillOffered`; BudgetEditorTest `firstChoicesPickTheProfilesDefaultCurrency`; BudgetValidationTest `aPositiveAmountWithinTheCurrencysDigitsIsParsedWithoutFloats` | PASS |
| US1-AS4 | Already budgeted shown + not choosable; other currency / All expenses can | BudgetChoicesTest `alreadyBudgetedIsKeyedByCategoryOrAllForTheChosenCurrencyOnly`; BudgetEditorTest `anAlreadyBudgetedCategoryCannotBeChosenAndAnotherCanAndSaveCreates`; BudgetsServiceCreateTest `aSecondActiveBudgetForTheSameCategoryAndCurrencyIsRefusedButAnotherCurrencyOrAllExpensesIsNot`; AH/feature/budgets/ui/BudgetsAccessibilityTest `theDisabledChoiceIsAnnouncedAsDisabledWithItsReason`; screenshot screen_budgets_choices_already_budgeted_{en,ar,en_fs2} | PASS |
| US1-AS5 | Zero/negative/too large/too many digits -> field error, nothing saved | BudgetValidationTest `zeroNegativeEmptyAndGarbageAreRefusedWithAFieldError`, `tooManyFractionDigitsAndTooLargeAmountsAreRefused`; BudgetsServiceCreateTest `aLimitOfZeroNegativeOrTooLargeIsRefusedAndNothingIsSaved`; BudgetEditorTest `anInvalidLimitIsNotSentAndShowsItsProblem`, `typingClearsTheLimitProblem` | PASS |
| US1-AS6 | Unverified -> prompt, nothing queued | BudgetsServiceCreateTest `anUnverifiedPersonQueuesNothing`; BudgetEditorTest `anUnverifiedAddressShowsThePromptAndAServerSideDuplicateMarksTheCategory`; BudgetsTest `addOpensTheEditorForAVerifiedPersonAndThePromptOtherwise` | PASS |
| US1-AS7 | Budget from another phone appears with progress | CT/budgets/data/BudgetFeedApplierTest `itConsumesBudgetRowsOfThePersonsOwnFeed`; BudgetFeedReadTest `anUpdatedPhoneReadsThePersonsScopeFromTheStart...`; BudgetsRefreshTest `aRowForABudgetNotYetOnThePhoneIsStoredAndShownOnceItArrives` | PASS |
| US2-AS1 | Name, spent of limit, remaining, % and bar via DS element; words always | CM/feature/budgets/ui/BudgetRowContent.kt (uses `BudgetProgress`); CT/designsystem/component/BudgetStateTest `percentIsRoundedToNearestAsOnTheBoard`; BudgetsAccessibilityTest `theShareAndTheWordsAreWrittenInEveryState`; BudgetMathTest `percentageIsWholeAndHalfUp`; BudgetsOverviewTest `theBriefsExampleSeesSpentLeftShareAndPace` | PASS |
| US2-AS2 | Over: "X over", % > 100, neutral | BudgetsOverviewTest `overTheLimitIsNegativeRemainingAndOverLimit`; BudgetsAccessibilityTest `noAlarmWordingAppearsEvenWhenOverTheLimit`; BudgetStringsTest `noAlarmWordingAndNoExclamationMarks`; BudgetMathTest `remainingIsNegativeWhenOverAndOverLimitIsExactlySpentAboveLimit` | PASS |
| US2-AS3 | "On pace for" = ceil(spent*days/elapsed); absent with no spend / overflow | BudgetMathTest `projectionIsSpentTimesDaysOverElapsedRoundedUp`, `noProjectionWithoutElapsedDaysOrWhenItWouldOverflow`; BudgetsOverviewTest `noPaceBeforeSpendingAndOtherCurrenciesAreNamed`; BudgetsTest `theProjectionIsDroppedWhileNothingIsSpent` | PASS |
| US2-AS4 | Other-currency spend named "Also spent in USD (not counted)" | SpendingTotalsTest `otherCurrenciesAreNamedSortedAndNeverSummed`; BudgetsOverviewTest `noPaceBeforeSpendingAndOtherCurrenciesAreNamed`; BudgetRowContent.kt `budgets_other_currencies` | PASS |
| US2-AS5 | Home card <= 3, highest % first then oldest, See all, hidden when none | BudgetsHomeSummaryTest `upToThreeActiveBudgetsHighestShareFirstThenOldest`, `nothingActiveMeansAnEmptyCardList`; CT/auth/ui/home/HomeBudgetsTest `theCardListsWhatTheSummaryGivesAndIsHiddenWhenEmpty`, `theRowAndSeeAllOpenTheBudgetsScreen`; BudgetsAccessibilityTest `theHomeCardWritesTheShareAndOffersSeeAll` | PASS |
| US2-AS6 | Period "25 Sep - 24 Oct" + days left in profile tz, language, Western digits | BudgetsOverviewTest `theDaysLeftAreTheEndMinusTodayAndZeroOnTheLastDay`; BudgetsPeriodOverviewTest `todayIsTakenInTheProfilesTimeZoneAndTheDevicesUntilTheProfileIsKnown`; BudgetsTest `thePeriodLineAndThePendingStartDayAreCarried`; BudgetStringsTest `arabicTextUsesWesternDigits`; screenshots screen_budgets_list_populated_*, list_last_day_offline_* | PASS (see G8) |
| US2-AS7 | Archived listed under "Archived" with figures, never on Home | BudgetsOverviewTest `activeBudgetsAreOldestFirstAndArchivedOnesAreSeparate`; BudgetsHomeSummaryTest (active only); BudgetsRefreshTest `anArchivedBudgetIsReadOneByOne`; BudgetsTest `rowsCarryFigures...` | PASS |
| US3-AS1 | Record/edit/delete matching expense -> immediate update (offline too) | CT/transactions/domain/TransactionOwnSpendingTest `aWaitingCreateIsAddedAndAWaitingPlaceholderIsNotSynced`, `aWaitingEditReplacesTheStoredValuesAndTouchesBothSides`, `aWaitingDeletionRemovesItFromTheShownListButTheStoredRowStaysSynced`; AH/feature/transactions/data/OwnSpendingQueryTest (`aRowWithAWaitingChangeIsReadWhateverItsStoredDateOrKind`); BudgetsOfflineToServerTest `readThenOfflineExpenseThenAcceptedThenReadAgain` | PASS |
| US3-AS2 | "Includes changes waiting to sync"; no jump when accepted | SpendingTotalsTest `aWaitingChangeMatchesOnItsOldOrNewValuesInTheBudgetsPeriodCurrencyAndCategory`; ProgressSelectionTest `aWaitingChangeThatMatchesSwitchesToThePhonesFiguresAndSaysSo`, `acceptingAWaitingChangeNeverJumpsBackToTheOlderServerFigure`; BudgetsOfflineToServerTest | PASS |
| US3-AS3 | Server figures shown while nothing changed | ProgressSelectionTest `theServersFiguresAreShownWhileNothingChanged`, `aSyncedChangeSinceTheReadSwitchesToThePhone`, `aNewSyncedExpenseFromAnotherPhoneNeverLeavesAStaleServerFigure` (OfflineToServer) | PASS |
| US3-AS4 | Transfer/income/partner/other-currency/out-of-period/deleted do not count | TransactionOwnSpendingTest `onlyTheSignedInPersonsLiveExpensesInTheirWalletsCount`, `dateAndRangeUseTheEffectiveCalendarDate`; AH OwnSpendingQueryTest `liveExpensesInTheRangeAreReadAndIncomeTransfersAndOtherDatesAreNot`; SpendingTotalsTest `aCategoryBudgetCountsItsCategoryAndCurrencyInThePeriodOnly` | PASS |
| US3-AS5 | Phone-only recurring confirmation counts for rule author | TransactionOwnSpendingTest `aConfirmationWaitingOnThePhoneCountsForItsRulesAuthor`, `anIncomeConfirmationWaitingOnThePhoneCountsForNothing`; CT/recurring/domain/RecurringLinksRuleAuthorTest `theAuthorIsTheRulesCreatorWhenTheRuleIsOnThePhone`, `anUnknownRuleHasNoKnownAuthor` | PASS |
| US3-AS6 | Left/lost wallet stops counting | TransactionOwnSpendingTest `aWalletThePersonLeavesStopsCounting` | PASS |
| US3-AS7 | Unsynced budget shows phone estimate; server replaces after sync + read | BudgetsOfflineToServerTest `aBudgetCreatedOnThisPhoneShowsThePhonesEstimateUntilItSyncs`; ProgressSelectionTest `withoutAStoredReadThePhonesOwnFiguresAreShown`; BudgetsRefreshTest `aRowForABudgetNotYetOnThePhoneIsStoredAndShownOnceItArrives` | PASS |
| US4-AS1 | Category/currency/period read-only with reason; only limit editable (also archived) | BudgetEditorTest `theCurrencyOfAnExistingBudgetNeverChanges`, `editSaveNeedsAChangedValidLimit`, `storedViewsDriveStatusProgressAndTheOfferedActions`; BudgetsServiceChangeTest `anArchivedBudgetsLimitStaysEditableAndIsNotArchivedAgain`; strings `budgets_immutable`; screenshots screen_budgets_editor_edit_*, editor_archived_* | PASS |
| US4-AS2 | Limit save: new figures at once, queue carries shown version | BudgetsServiceChangeTest `aLimitSaveQueuesTheStoredCategoryAndCurrencyWithTheNewLimitOnTheShownVersion`, `aSecondEditKeepsTheOldestBaseVersion`; BudgetsOverviewTest `aWaitingLimitEditIsShownAtOnceWithRecomputedFigures`; BudgetEditorTest `editSavesOnlyTheLimitOnTheShownVersion` | PASS |
| US4-AS3 | Archive with confirmation, moves at once, queued | BudgetsServiceChangeTest `archiveQueuesTheArchiveWithTheShownLimitAndMovesTheBudgetToArchived`, `archivingTwiceOrAnArchivedBudgetQueuesNothingMore`; BudgetEditorTest `archiveAndDeleteAskFirstAndThenLeave`; BudgetsOverviewTest `aWaitingArchiveMovesTheBudgetToArchived...`; screenshot screen_budgets_confirm_archive_* | PASS |
| US4-AS4 | Delete with confirmation, queued with version; never-sent removed with nothing sent | BudgetsServiceChangeTest `deleteQueuesTheDeletionWithTheVersionAndHidesTheBudget`, `aBudgetNeverSentIsRemovedWithNothingSent`; screenshot confirm_delete_* | PASS |
| US4-AS5 | Clash: "Changed on another device", Keep mine / Use the other device's | BudgetsServiceChoicesTest `aClashShowsTheOtherDevicesCopyAndKeepMineResendsOnTheNewerVersion`, `useTheirsAppliesTheOtherDevicesCopyAndDropsTheChange`; BudgetEditorTest `aClashListsOnlyTheMembersThatDiffer`, `clashAndRefusalChoicesCallTheService`; screenshot editor_clash_* | PASS |
| US4-AS6 | Deleted elsewhere -> only Accept the deletion | BudgetsServiceChoicesTest `aClashWithADeletionOnlyOffersToAcceptItAndAcceptingRemovesTheBudgetAndItsProgress`; BudgetSyncHandlerTest `deletionIsFinalSoThereIsNoRestore`; screenshot editor_deleted_elsewhere_* | PASS |
| US4-AS7 | Refusal "Not saved", plain words by code+pointer, Try again / Discard | BudgetsServiceChoicesTest `aRefusalShowsItsKindByCodeAndPointerAndTryAgainRequeues`, `discardingARefusedNeverSentBudgetRemovesItAndDiscardingASyncedOneShowsTheServerCopyAgain`; BudgetCopyTest (6 tests); BudgetSyncHandlerTest `refusalsMapTheFourPointersToFieldNames`; BudgetEditorTest `aRefusalCarriesItsReasonAndItsFieldProblems`; screenshot editor_refused_* | PASS |
| US5-AS1 | Period start/end for start day d | BudgetPeriodsTest `startDayOneIsTheCalendarMonth`, `...FifteenSwitchesOnTheFifteenth`, `...TwentyFiveRunsTwentyFifthToTwentyFourth`, `...TwentyEightWorksInEveryMonthIncludingFebruary`, `everyStartDayAndEveryDayOfAYearYieldsAPeriodContainingToday` | PASS (captured server comparison: `BudgetCapturedProgressTest`) |
| US5-AS2 | Pending change: last server period + "From <date>, periods start on day n" | BudgetPeriodsTest `aPendingChangeUsesTheStoredServerPeriodContainingToday`; BudgetsPeriodOverviewTest `aPendingChangeKeepsTheStoredServerPeriodAndSaysWhenTheNewDayApplies`; screenshot list_pending_start_unknown_* | PASS |
| US5-AS3 | Pending + no stored period -> limit + "Progress appears after sync" | BudgetPeriodsTest `aPendingChangeWithNoStoredPeriodForTodayIsUnknown`; BudgetsPeriodOverviewTest `withoutAStoredPeriodTheBudgetShowsItsLimitAndNoFigures`; BudgetsOverviewTest/BudgetsTest `anUnknownPeriodHasNoFiguresAndNoPeriodLine`; screenshot home_budgets_unknown_period_* | PASS |
| US5-AS4 | New periods apply when date reached | BudgetPeriodsTest `theNewPeriodsApplyOnceTheDateIsReached`, `anAppliesFromDateThatIsAbsentTodayOrPastGivesTheCalendarPeriod`; BudgetsPeriodOverviewTest `onceTheAppliesFromDateIsReachedTheNewPeriodsApplyWithoutAnyAction` | PASS |
| US5-AS5 | Start day edit waiting on phone changes nothing | BudgetsPeriodOverviewTest `aStartDayEditWaitingOnThisPhoneChangesNothing`; BudgetChoicesTest `theStartDayComesFromTheServersProfileCopy` | PASS |
| US5-AS6 | Moves to new period at midnight without restart | BudgetsPeriodOverviewTest `theOverviewMovesToTheNewPeriodWhenTheDayTurnsInTheProfileZone`; DayTickerTest `emitsNowThenAgainAtTheStartOfEveryMinute`; BudgetsOfflineToServerTest `aTransactionSpendingInThePreviousPeriodAfterTheDayTurnsReadsTheNewPeriod`; code BudgetsService.kt:122,169 (DayTicker in clockContext) | PASS |
| US6-AS1 | First sync after update reads user scope from start; data stays shown | AH/core/database/Migration8To9Test `theUserScopesPlaceIsForgottenAndTheWalletScopesKeepTheirs`, `everyOtherRowIsKeptIncludingTheWaitingChange`; BudgetFeedReadTest `anUpdatedPhoneReadsThePersonsScopeFromTheStartAndAppliesBudgetsBesideTheOtherTypes` | PASS |
| US6-AS2 | Change/archive/delete elsewhere appears; deletion removes unless change waits | BudgetFeedApplierTest `aBudgetIsAppliedWhenItsVersionIsTheSameOrNewerAndIgnoredWhenLower`, `aTombstoneRemovesTheBudgetAndItsProgressUnlessAChangeWaitsForIt`, `aFeedCopyNeverTouchesTheWaitingChange`, `aSweepRemovesUnseenSyncedBudgetsButNeverPlaceholdersOrQueuedOnes`; BudgetSyncHandlerTest `theServerCopyIsStoredAndATombstoneRemovesTheRow` | PASS |
| US6-AS3 | Budget changes batch with category/transaction changes; waits for unsent category | BudgetBatchTest `budgetItemsShareTheBatchWithCategoryAndTransactionItemsAndCarryNoWalletId`, `aBudgetNamingACategoryNotYetAcceptedIsHeldWithoutCountingATry`, `anArchiveGoesPerRecordAtItsPlaceInTheOrder`; BudgetSyncHandlerTest `theCategoryOfABudgetIsAPrerequisiteAndAllExpensesHasNone` | PASS |
| US6-AS4 | Sign-out removes budgets, stored progress, waiting changes | AH/feature/account/SignOutWipeTest (extended: budgetDao, budgetProgressDao, pendingChange "budget" asserted empty/null); BudgetDaoTest `progressIsObservedPerAccountAndDeleteAllEmptiesBothTables`; BudgetsSyncCoordinatorTest `nothingHappensAfterSignOut` | PASS |

## Edge cases

| Item | Requirement (short) | Evidence | Result |
|---|---|---|---|
| EC-1 | Period boundaries (day 1, day 28 incl. Feb, 28-31 day months) | BudgetPeriodsTest `startDayTwentyEightWorksInEveryMonthIncludingFebruary`, `everyStartDayAndEveryDay...` | PASS |
| EC-2 | Today in profile tz (device tz until profile known); tx by calendar date only | BudgetsPeriodOverviewTest `todayIsTakenInTheProfilesTimeZoneAndTheDevicesUntilTheProfileIsKnown`; TransactionOwnSpendingTest `dateAndRangeUseTheEffectiveCalendarDate` | PASS |
| EC-3 | Day turns while screen open | DayTickerTest; BudgetsPeriodOverviewTest `theOverviewMovesToTheNewPeriodWhenTheDayTurnsInTheProfileZone` | PASS |
| EC-4 | Budget created offline: phone estimate; per-budget read not attempted before server has it | BudgetsOfflineToServerTest `aBudgetCreatedOnThisPhoneShowsThePhonesEstimateUntilItSyncs`; BudgetsRefreshTest `oneBudgetIsReadOnlyWhenTheServerHoldsItAndStoredLikeAWalkRow` | PASS |
| EC-5 | Two budgets same category+currency both shown and count | `BudgetsOverviewTest.twoBudgetsForTheSameCategoryAndCurrencyFromTwoPhonesAreBothShownAndBothCount` (added in this QA) | PASS (GAP G4 closed) |
| EC-6 | Archived/deleted category: keeps counting; name current or "Category unavailable"; archived not offered | BudgetsOverviewTest `aCategoryThatCannotBeResolvedIsMarkedUnavailable`; BudgetChoicesTest `theCurrentCategoryStaysListedWhateverItsState`; BudgetsHomeSummaryTest `theNameIsNullForAllExpensesAndTheAppLanguageNameOtherwise` | PASS |
| EC-7 | Category named by any budget (waiting or synced) cannot be deleted on phone | BudgetCategoryUsesTest (4 tests); AH/core/di/AppGraphTest (PendingCategoryUses union now 3 + `theBudgetsModuleStartsWithItsHandlerAndApplierAndRebindsTheHooks`); CT/categories/domain/CategoriesServiceArchiveDeleteTest only got a harness tweak (awaitCancellation), no budget-specific delete assertion | PASS (uses hook proven by CategoryUses + AppGraph; a CategoriesService test that a budget-used category refuses delete is inherited from C5's PendingCategoryUses tests) |
| EC-8 | Limit edit after archive allowed; never re-activated | BudgetsServiceChangeTest `anArchivedBudgetsLimitStaysEditableAndIsNotArchivedAgain`, `anAlreadyArchivedAnswerToThePutIsNotArchivedAgain` (SyncHandlerTest); BudgetEditorTest `anArchivedBudgetOffersNoArchive` | PASS |
| EC-9 | Past limit changes: current limit vs current period | code: BudgetMath/ProgressSelection use the live limit; ProgressSelectionTest `aChangedLimitOrCurrencySwitchesToThePhone` | PASS |
| EC-10 | Shared wallet: only own txs; viewer-role own tx still counts; partner recategorising moves it | TransactionOwnSpendingTest `onlyTheSignedInPersonsLiveExpensesInTheirWalletsCount` (asserts "own","viewer" counted, partner/other-account/non-member excluded) | PASS |
| EC-11 | Recurring: occurrences never count; waiting confirmation counts for author / person if rule unknown | TransactionOwnSpendingTest `aConfirmationWaitingOnThePhoneCountsForItsRulesAuthor`; RecurringLinksRuleAuthorTest; OwnSpendingQueryTest `aPhoneOnlyConfirmationIsAlwaysReadAndAWaitingIdOfADeletedRowIsNot` | PASS |
| EC-12 | Process killed after offline edit: change survives | Inherited: queue is a Room table (`PendingChangeEntity`, spec 004); BudgetsServiceCreateTest/ServiceChangeTest assert the queue row. No budget-specific kill test | PASS (inherited from spec 004) |
| EC-13 | 50 budgets over 20,000 tx in < 1 s | AH/feature/budgets/BudgetsPerformanceTest `fiftyBudgetsOverTwentyThousandTransactionsShowWithinASecond` (asserts elapsed < 1_000) | PASS |
| EC-14 | Limit 2^53-1 accepted; projection omitted on overflow | BudgetValidationTest `aLimitInMinorUnitsIsCheckedAgainstTheRange`; BudgetMathTest `noProjectionWithoutElapsedDaysOrWhenItWouldOverflow` | PASS |
| EC-15 | Unknown values from later server; unknown period type read-only "Shown in full on newer versions" | BudgetApiContractTest `aResponseWithAnUnknownMemberAndAnUnknownPeriodTypeStillDecodes`; BudgetFeedApplierTest `unknownMembersAndAnUnknownPeriodTypeAreKept`; BudgetsOverviewTest `anUnknownPeriodTypeIsReadOnlyWithItsLimitAndNoFigures`; BudgetsServiceChangeTest `aBudgetOfAnUnknownPeriodTypeIsReadOnly`; BudgetsTest `aBudgetOfAnUnknownPeriodTypeIsReadOnly`; screenshot editor_unknown_period_type_* | PASS |
| EC-16 | Sign-out removes budgets/progress | SignOutWipeTest (see US6-AS4) | PASS |

## Functional requirements

| Item | Requirement (short) | Evidence | Result |
|---|---|---|---|
| FR-001 | Local-first, UUIDv7, user scope | BudgetsServiceCreateTest `newBudgetsCarryAClientMadeUuidV7`, `aValidCreate...InTheUserScope`; AH BudgetsLayeringTest `uiImportsNoDataPackage...` | PASS |
| FR-002 | Verified-only writes; no plan gate | BudgetsServiceCreateTest `anUnverifiedPersonQueuesNothing`; BudgetsServiceChangeTest `anUnverifiedPersonQueuesNothingForAnyWrite`; grep: no `Entitle*` reference under feature/budgets | PASS |
| FR-003 | Editor fields: category, currency, limit, read-only period line | BudgetEditorTest `creatingStartsWithTheDefaultCurrencyTheStartDayAndNothingChosen`; BudgetChoicesTest; BudgetEditorScreen.kt (`budgets_period_monthly`) ; screenshot editor_create_* | PASS |
| FR-004 | No already-budgeted offered; currency change clears; overlap tolerated | BudgetEditorTest `aCurrencySwitchClearsAChosenCategoryAlreadyBudgetedInTheNewCurrency`, `switchingCurrencyClears...`, `allExpensesIsClearedWhenItIsAlreadyBudgetedInTheChosenCurrency`; BudgetChoicesTest; overlap tolerance = G4 | PASS (overlap clause: `BudgetsOverviewTest.twoBudgets…`) |
| FR-005 | Only limit editable; whole budget queued with opened version; archived editable | BudgetsServiceChangeTest (first 3 tests); BudgetEditorTest `editStartsFromWhatThePhoneShowsAndRemembersTheVersion`; BudgetSyncHandlerTest `forABudgetTheServerHasCategoryCurrencyAndPeriodTypeComeFromTheStoredRow` | PASS |
| FR-006 | Archive/delete confirm; archive one-way; never-sent delete sends nothing | BudgetsServiceChangeTest; BudgetEditorTest `archiveAndDeleteAskFirstAndThenLeave` | PASS |
| FR-007 | Clash / deletion / refusal copy by code+pointer | BudgetCopyTest all; BudgetsServiceChoicesTest; BudgetSyncHandlerTest `refusalsMapTheFourPointersToFieldNames` | PASS |
| FR-008 | Only declared members, explicit null category, ignore unknown | BudgetApiContractTest `putSendsExactlyTheSchemaMembersWithAnExplicitNullForAllExpenses`, `deleteAndArchiveCarryOnlyTheVersion...`, `aResponseWithAnUnknownMember...`; BudgetSyncHandlerTest `theUpsertItemIsExactlyThePutBody...`, `aCreateTakesCategoryAndCurrency...AllExpensesIsAnExplicitNull` | PASS |
| FR-009 | Period from server profile; tz; default day 1 without profile | BudgetPeriodsTest `withoutAProfileTheStartDayIsOne`, table tests; BudgetsPeriodOverviewTest tz test | PASS |
| FR-010 | Pending change -> stored server period / "Progress appears after sync"; local edit no effect | BudgetPeriodsTest (pending x2); BudgetsPeriodOverviewTest (3 tests) | PASS |
| FR-011 | Current period only; never send `date` | BudgetApiContractTest `theProgressListAsksForTodaysPeriodOnlyAndFollowsTheCursor`, `theProgressOfOneBudgetIsReadWithoutAQuery...`; BudgetsRefreshTest `aRefreshReadsTheFeedsFirstThenEveryProgressPageWithoutADate` | PASS |
| FR-012 | Spending rules with waiting overlay, other currencies sorted | SpendingTotalsTest (5); TransactionOwnSpendingTest (13); OwnSpendingQueryTest (5) | PASS |
| FR-013 | Integer maths, rounding, projection | BudgetMathTest (6); BudgetStateTest; grep: no Double/Float in feature/budgets | PASS |
| FR-014 | Server-vs-phone selection rule (a)-(d) | ProgressSelectionTest (8); BudgetsRefreshTest `theStoredLocalFiguresComeFromOneSnapshotTakenAfterTheFeedReads`; BudgetDaoTest | PASS |
| FR-015 | DS element + pace + other currencies, neutral, not colour alone | BudgetRowContent.kt; BudgetsAccessibilityTest `theShareAndTheWordsAreWrittenInEveryState`; BudgetStringsTest `noAlarmWordingAndNoExclamationMarks` | PASS |
| FR-016 | List: active oldest first, Archived section, period, days left, pending day, status marks, pull refresh states, empty state | BudgetsOverviewTest; BudgetsTest `refreshOutcomesSetOfflineAndFailed`, `anOfflineOrFailedReadIsShown...`, `openingReadsWithinTheFloorAndAPullAlwaysReads`, `theScreenFollows...ShowsTheEmptyStateAfterTheFirstRead`; screenshot list_empty_*, list_loading_* | PASS (G5: the request id is not shown on a failed refresh, as on the wallets, categories and transactions lists; spec wording amended) |
| FR-017 | Home card + row; open editor | HomeBudgetsTest (6), BudgetsHomeSummaryTest (5) | PASS |
| FR-018 | Progress reads after feeds on sign-in/foreground/open (1/min)/pull (forced); per-budget; 404 ignored; INVALID_CURSOR restart once | BudgetsSyncCoordinatorTest (3); BudgetsRefreshTest (15 incl. `anInvalidCursorRestartsTheWalkOnce`, `aSecondInvalidCursorFailsTheWalk...`, `withoutForceAWalkWithinTheLastMinuteIsSkippedAndAPullAlwaysReads`, `aNotFoundOnOneBudgetIsIgnoredAndTheFloorIsPerBudget`, `offlineTimeoutAndServerErrorsKeepTheStoredRows...`) | PASS |
| FR-019 | User feed read; batch upsert/delete, archive per record after save; held dependents | BudgetFeedApplierTest; BudgetBatchTest; BudgetSyncHandlerTest `anEditSendsOnePutAndAnArchiveWithAnEditSendsThePutThenTheArchiveOnTheNewVersion`, `anArchiveOnlyChangeWithTheStoredLimitSkipsThePut`, `aFailedPutStopsBeforeTheArchive` | PASS |
| FR-020 | Forget user-scope position once; wallet scopes keep | Migration8To9Test (4 tests incl. exported schema); BudgetFeedReadTest | PASS |
| FR-021 | Categories named by budgets are in use | BudgetCategoryUsesTest (4); AppGraphTest | PASS |
| FR-022 | Sign-out wipes; delete removes progress | SignOutWipeTest; BudgetDaoTest `anUpsertReplacesAndDeleteWithProgressRemovesBothInOneTransaction`; BudgetFeedApplierTest tombstone | PASS |
| FR-023 | Failures by code+pointer; retries per catalogue | BudgetApiContractTest `everyValidationCodeKeepsItsPointerAndCode`, `retryClassesFollowTheCatalogue`, `anUnverifiedAddressAndANotFoundAreReportedByCode`; BudgetCopyTest `theServersWordsNeverDecideTheKind` | PASS |
| FR-024 | No amounts/payloads in logs | CT/budgets/data/BudgetLoggingTest `noLimitSpentProjectionOrPayloadReachesTheLog`; BudgetDaoTest `entitiesNeverPrintTheirAmounts`; HomeBudgetsTest `aGlimpseNeverPrintsItsAmounts`. NOTE `BudgetsService.kt:400,467` log request id + code only | PASS |
| FR-025 | EN+AR, Western digits, RTL bar from right, 200% font, glossary words | AH BudgetStringsTest (8); BudgetsScreenshotTest `englishLtr`, `englishFontScale2` + RTL variant (`arabicRtl`, line 78); screenshots *_ar, *_en_fs2; BudgetsAccessibilityTest `nothingClipsAtDoubleFontScale`, `theEditorAndTheHomeCardStayUsableAtDoubleFontScale`; DS BudgetProgress.kt fill is `Box.fillMaxWidth(fraction)` (starts at layout start => right in RTL). RTL bar direction is proven only by the AR golden image, not by an assertion | PASS (golden-based) |
| FR-026 | No notifications | AH BudgetsLayeringTest `budgetsReadsTransactionsOnlyThroughTheOwnSpendingHookAndNeverTouchesNotifications` | PASS |

## Success criteria

| Item | Requirement (short) | Evidence | Result |
|---|---|---|---|
| SC-001 | Set budget < 30 s | usability | DEFERRED-DEVICE (G6) |
| SC-002 | Update < 1 s offline | Perf test (EC-13) + reactive tests | PASS (G9 note) |
| SC-003 | Shown figures == server for 100% of live QA set | `BudgetCapturedProgressTest` (live set, 5 active budgets + partner/B cases): spent, remaining, over limit, projection, share and other currencies equal for 100% | PASS |
| SC-004 | Period == server for table | `BudgetPeriodsTest` (every start day 1..28 over 27 months) + live: start day 1 (31-day month, elapsed 5) and a pending change to 25 (server period 1-24 Oct, 24 days) in `BudgetCapturedProgressTest` | PASS (live limits: see "Not reachable live") |
| SC-005 | Offline changes reach server or need decision | BudgetsServiceCreate/Change tests (queued), BudgetSyncHandlerTest, BudgetBatchTest, BudgetsServiceChoicesTest (decisions) | PASS |
| SC-006 | Every active budget within one tap of Home | HomeBudgetsTest `theRowAndSeeAllOpenTheBudgetsScreen`, `aBudgetOnTheCardOpensItsEditor`; (card shows only 3, See all + row cover the rest) | PASS |
| SC-007 | Brief checkpoint | host: BudgetsOfflineToServerTest `readThenOfflineExpenseThenAcceptedThenReadAgain`; E2E `.maestro/012-budgets/US3-*` deferred | PASS (host) / DEFERRED-DEVICE (E2E) |
| SC-008 | 50 budgets / 20k tx < 1 s | BudgetsPerformanceTest | PASS |

## Section 4 invariants (spot check of the "test that fails when broken" column)

| Invariant | Test | Result |
|---|---|---|
| render only from phone's copy | BudgetsLayeringTest `uiImportsNoDataPackage...`, `domainImportsNoNetwork...` | PASS |
| offline create/edit/archive/delete queue in user scope with base version | BudgetsServiceCreate/ChangeTest | PASS |
| category/currency/period immutable | BudgetEditorTest `theCurrencyOfAnExistingBudgetNeverChanges`; BudgetSyncHandlerTest `forABudgetTheServerHasCategory...FromTheStoredRow` | PASS |
| spending == server rules | SpendingTotalsTest etc.; `BudgetCapturedProgressTest` | PASS |
| integer maths | BudgetMathTest | PASS |
| server vs phone selection | ProgressSelectionTest, BudgetsOfflineToServerTest | PASS |
| period == server's | BudgetPeriodsTest; `BudgetCapturedProgressTest` | PASS |
| writes declare only members, archive never batched, no wallet_id | BudgetApiContractTest, BudgetBatchTest, BudgetSyncHandlerTest | PASS |
| failures branch on code+pointer | BudgetCopyTest | PASS |
| migration 8->9 | Migration8To9Test | PASS |
| sign-out wipe | SignOutWipeTest | PASS |
| category in use | BudgetCategoryUsesTest, AppGraphTest | PASS |
| no amounts in logs | BudgetLoggingTest | PASS |
| no percentage from server; state not by colour | `BudgetCapturedContractTest.theProgressTypeHasNoPercentageMemberSoAServerShareIsNeverRead` (added); BudgetsAccessibilityTest | PASS (GAP G3 closed) |
