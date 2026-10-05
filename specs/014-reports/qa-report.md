# QA report: Reports (spec 014)

**Status**: host QA done; live-backend contract QA (T089) and captured-answer parity (T090) done on 2026-10-06 against `saveme-backend@b63e917` (`reports.yaml` v1.11, spec 019 committed `d7ed413`; the backend working tree holds only uncommitted work of other specs, no report code). About 750 HTTP calls, 4 throwaway accounts (A owner on Plus, B partner on Plus, D with a pending start day change on Plus, E for bounds checks). No emulator, Maestro or device run (owner policy): the nine device tasks (T037 T047 T054 T061 T069 T075 T081 T094 T095) stay deferred.

## Counts

| Group | Total | PASS | GAP closed in this QA | Open GAP | Deferred (device) |
|---|---|---|---|---|---|
| Acceptance scenarios (US1 7, US2 6, US3 6, US4 5, US5 5, US6 4, US7 5) | 38 | 38 | 1 (US3-S4 text asserted) | 0 | 0 (E2E flows deferred: T037 T047 T054 T061 T069 T075 T081) |
| Edge cases | 20 | 20 | 1 (Liability text, same as US3-S4) | 0 | 0 |
| Functional requirements FR-001..FR-029 | 29 | 29 | 2 (FR-016 zeros for a currency with no wallet; FR-017 owed and U+2212 text) | 0 | 0 |
| Success criteria SC-001..SC-007 | 7 | 6 | 0 | 0 | 1 (SC-001 timing on a device; host timing 0.30-0.35 s for the largest set) |
| Section 4 invariants | 12 | 12 | 0 | 0 | 0 |

Gaps found by the mapping and the live comparison, closed here:

- **G1 (client bug, fixed)**: with "Include scheduled" on and a currency with no wallet in it (live: `currency=EUR`), the server answers `planned` zeros (summary, per group, per trend period); the phone left the scheduled figures absent (`null`), against FR-016 / US6-S3 "every figure is zero". Fix in `ReportsService.data()`: a report that covers no wallet answers an empty scheduled list when the switch is on. Test `ReportsServiceCurrencyTest.aCurrencyWithNoWalletShowsZeroScheduledFiguresWhenScheduledIsOnAsTheServerAnswers`; the live comparison of all five EUR cases with the switch on now passes.
- **G2 (test gap, closed)**: FR-017 / US3-S4 (a liability wallet's balances read "Owed ...") and the U+2212 rule for negative net and cross-wallet balances were proven only by goldens. Tests `ReportsAccessibilityTest.aLiabilityWalletsNegativeBalancesAreAmountsOwed` and `aCrossWalletNegativeBalanceIsANetAmountWithTheTrueMinusSignNeverOwed` assert the written text.
- **G3 (backend gap 2, recorded, not a client defect)**: see "Backend gap 2" below.
- **G4 (test robustness)**: the performance test (SC-005) is now the fastest of seven runs after two warm-ups (was five after one); see "Flaky-test findings".

## Live contract QA (T089)

Environment: the local backend reached over `http://127.0.0.1:80` with `Host: saveme-backend.test` (the `.test` DNS name was flaky); accounts registered through `POST /auth/register` (202), verified with an `email_verified_at` update in `dev tinker`, Plus granted by `OperatorSubscriptions::grant` under a UUID operator id, signed in through `POST /auth/login`. Every request carried the app's `X-Client-Platform` and `X-Client-Version`. Server "today" 2026-10-05 23:2x UTC = 2026-10-06 in Africa/Cairo.

### Data set built through the real API

| Part | What |
|---|---|
| Wallets of A | Cash (EGP), Bank (EGP checking), Card (`credit_card` EGP, opening -4,200.00), Savings (`savings` USD), Old (EGP, archived after its two transactions), Household (EGP, owned by A, B editor), Gone (EGP, deleted after a transfer into Cash) |
| Wallets of B | B Own (A not a member), B Family (A is viewer) |
| Categories | 12 platform keys used; A's own Gym (expense), Groceries (own, same name as the platform one), Side gig (income); B's own B Snacks inside Household (reaches A as a category reference) |
| Transactions | 44 across 1 Aug - 3 Nov 2026 (and 31 Jul as a boundary): income, expense, transfers Bank to Cash and Cash to Bank (both covered), Bank to Card, B Own to Household and Household to B Own (the other wallet is not A's), B Family to B Own, Gone to Cash; 2 deleted (A's 99.99 expense, B's 770.00 expense); future-dated 20 Oct expense and 3 Nov income; ties in the category sums (own Gym = platform transport = 400.00, fuel = health = 250.00) |
| Recurring | 8 rules materialised with `POST .../materialise` (10 occurrences up to 10 Nov): rent in Cash (28th, 28 Aug confirmed, 28 Sep skipped), Cash to Bank, Bank to Cash, a USD rule, income, B's transfer rule B Own to Household, B's expense rule and B's transfer rule Household to B Own |
| Profile | D: `budget_period_start_day` 25, `applies from` 2026-10-25 (pending), so D's current period is 1-24 Oct and every period before 25 Oct comes from the server |
| Not possible | A transfer from a wallet A belongs to into one A does not belong to: `422` (so "uncovered" cases arise only from the partner's own transfers and the deleted wallet). A start day change already in force cannot be produced on a fresh account (spec 012 summary): the past-change case is proven by `ReportPeriodsTest` only |

### Reports recorded and compared (511 comparisons, 0 unexplained differences)

Requests: `from`/`to` ranges (October, September, August, 15 Aug - 6 Oct, 1-6 Oct, one day, November with only future scheduled money, 366 days 2026-01-01 - 2027-01-01, a week into the future), each with `include_planned` true and false; no parameters (the server's default period); trends `months` 1, 3, 6, 12 with `date` 2026-10-06 and with the planned switch both ways, and the default trend; cross-wallet in the default currency, `EGP` explicit, `USD`, and `EUR` (no wallet); each wallet of A (7), of B (3) and D's wallet.

| Family | Compared | Result |
|---|---|---|
| Summary: income, expenses, net, transfers in, transfers out, balance start, balance end, range, currency, `meta.excluded_wallets` | 177 (A 128, B 36, D 13) | equal, except 6 summaries that differ only by `planned.transfers_in` (gap 2) |
| By category: group totals, planned totals, every item's label, category id, system key, value, planned, and the order incl. tie-breaks | 177 (A 128, B 36, D 13) | equal |
| Trend: periods (dates), income, expenses, net, planned income, planned expenses for 1, 3, 6, 12 periods | 157 (A 102, B 36, D 19) | equal |
| Excluded wallets (cross-wallet answers) | every cross-wallet case | equal as `(wallet_id, currency)` sets |
| Planned figures (229 of the 511 have `include_planned=true`) | 229 | equal, except the gap-2 summaries; the EUR zeros fixed (G1) |
| Per account | A 358, B 108, D 45 | all green in `ReportsCapturedParityTest` |

Cross-wallet and wallet scopes are counted together above (A: 37 cross-wallet and 91 wallet summaries; B 9 and 27; D 10 and 3). Notable members proven equal: the identity `balance_end = balance_start + income - expenses + transfers_in - transfers_out`; the cross-wallet rule that a transfer between two covered wallets appears nowhere (Bank to Cash and Cash to Bank are in neither direction) while the partner's transfers into and out of Household count in their direction in Household's report and in the owner's cross-wallet report; the transfer from the deleted wallet Gone into Cash counts as a transfer in (200.00), Gone's own 70.00 expense and balance are absent, as on the server; the archived wallet is covered; deleted transactions never count; future-dated transactions count in the range containing their date and in `balance_end` of the current period; the credit card's negative opening and balances; a joined-today wallet counts for past ranges (Household and Family in the partner's and the owner's September and August reports); the 31 Jul expense is in no August range but in August's `balance_start`; ties: own Gym (id) before platform `transport` (system key), `fuel` before `health`, the own "Groceries" kept apart from the platform one (never merged by name).

**Periods (SC-004)**: account D (pending start day 25 from 25 Oct): the phone derives nothing for the current period, reads `GET /reports/trend` with only `months` and `date` (the recorded answers: 1 Aug - 31 Aug, 1 - 30 Sep, 1 - 24 Oct) and every figure of D's 45 reports, including trends of 1, 3, 6 and 12 periods ending in the truncated 1-24 Oct period, equals the server's. For A and B (start day 1) the derived periods equal the server's `periods` in every trend. The server accepted `date` 1900-01-15 and 2999-12-31 and answers periods before 1900 for the trend (the client still refuses dates outside 1900-2999).

**Dev probe (FR-028)**: `DevReportParityProbe` replays through the app's real graph against the recorded answers (`theDevelopmentProbeFindsTheOwnersSyncedFiguresEqualToTheRecordedAnswers`): `report.parity ok` for summary, by-category and trend of the owner's cross-wallet report, no `mismatch`, and no number in any log line.

### Refusals and bounds (live)

| Request | Answer | Client handling |
|---|---|---|
| range of 367 days (2026-01-01 - 2027-01-02) | 400 `INVALID_QUERY_PARAMETER` | the client refuses first (`RangeRules`, `ReportsApi`) |
| `months=13`, `months=0` | 400 `INVALID_QUERY_PARAMETER` | refused first |
| `currency=egp` | 400 `INVALID_QUERY_PARAMETER` | refused first |
| `date` with `from`/`to`; `from` after `to`; `from` alone; `date=2026-13-45`; `include_planned=maybe`; `from` 1899-12-31 | 400 `INVALID_QUERY_PARAMETER` | never sent (client defect if it were: logged, "Shown when you're online") |
| another person's wallet; an unknown wallet id; a deleted wallet | 404 `NOT_FOUND` | probe skipped (`reason=not_found`); replayed through the real client in `theRecordedRefusalsAreHandledByCodeAndDoNotHideAnythingOnThePhone` |
| malformed id (`not-a-uuid`) | 422 `INVALID_ID` | probe skipped; replayed as above |
| no credential | 401 `UNAUTHENTICATED` | session ends (P-09.5, existing handling) |
| 366 days inclusive; 1900-01-01 - 1900-01-02; 2999-12-30 - 2999-12-31 | 200 | accepted |
| 2999-12-31 - 3000-01-01 | 400 | refused first (bound 2999-12-31) |
| `currency=EGP` on a wallet report | 200, **ignored** | the client never sends it for a wallet |

### Backend gap 2 (settled): a scheduled transfer into a covered wallet from a wallet the person is not in

The partner's rule "B Own to Household" (22nd monthly, 300.00) and its occurrence live in B Own. **They do not reach the owner's phone**: the Household feed carries the partner's transactions, rules and occurrences made in Household, and *transactions* in another wallet that name Household as the destination (the actual transfer of 600.00 on 5 Oct reaches the phone and is counted), but not another wallet's rule or occurrences. The server counts the 300.00 in the planned transfers in of Household's report and of the owner's cross-wallet report; the phone counts 0. Consequence, exactly as the spec predicted: planned (scheduled) transfers in under-count by that amount for the owner; actual figures, balances, categories and trends are unaffected (the trend carries no transfers). The 6 affected summaries are listed in `ReportsCapturedParityTest` by rule (planned switch on, range contains 22 Oct, report counts Household); the test fails on any other difference and on the gap disappearing. The partner's own phone is exact (both wallets are his). No backend bug: the feed behaves as spec 009 defines it; recorded as a known limitation (Remaining risks). Nothing to fix on the phone without a backend change (for example: carry the destination wallet's incoming scheduled transfers in its feed).

### Backend observations (not bugs)

- `GET /wallets/{id}/reports/*` ignores `currency` (200); the client never sends it.
- The wallet-feed `transaction` rows include transfers whose source is another wallet (as the spec's Assumptions say); the user feed's `wallet_member` tombstone for the deleted wallet removes it; a transaction of a deleted wallet that names a covered wallet as destination remains live on the server and counts as a transfer in (the phone matches).
- Trend `periods` for dates before 1900 exist on the server although `date` itself is bounded at 1900.
- During this QA another process purged 34 accounts of the shared dev database (including the QA accounts: state `purged`), so "delete wallets/rules via API" clean-up was not possible and not needed; the captured answers were taken before.

## Captured fixtures (T090)

`shared/src/androidHostTest/kotlin/com/fares/saveme/feature/reports/CapturedReportsResponses.kt` (feeds as the app reads them: user feed and wallet feeds of A (the Cash wallet in two real pages of 25), B, D; wallet heads; platform categories) and `CapturedReportsA/B/D/Refusals.kt` (the 511 report answers, 7 period lookups and 4 refusals, path and body verbatim). No bearer credential, token or e-mail address is in any file (the sign-in used by the test is built with a placeholder credential); user and wallet ids are opaque UUIDs.

`ReportsCapturedParityTest` (host, Robolectric) starts the app's real Koin graph on an in-memory Room database with a mock HTTP engine that answers the recorded bodies, signs the person in, and drives `CategoriesService.refresh` and `SyncRunner.refresh(Everything)` (heads, `FeedPuller`, the wallet, member, transaction, category, category-reference, recurring and profile appliers), then builds `DefaultReportsService` over the real `WalletsService`, `TransactionReportLedger` (the Room queries), `ScheduledMoney`, labels, `ReportPeriods`, `ReportCalculator`, with "now" fixed at 2026-10-06 03:30 Cairo, and compares every recorded answer with `ReportMapping` (the dev probe's member-by-member comparison). Eight tests: owner (358), partner (108), start-day account (45), gap 2 pinned, shared-wallet and left-out-wallet figures, category names (platform key, own, partner's through the reference), refusals by code, dev probe.

## Acceptance scenarios

| Id | Evidence | Result |
|---|---|---|
| US1-S1 Home Reports row opens current period, default currency, dates | `HomeBudgetsTest` (ReportsClicked), `ReportsServiceSummaryTest.theCurrentPeriodsSummaryAndSharesMatchTheBriefsExample`, `ReportsReducerTest.aReadyReportFillsTheSummaryRangeAndCategoryRowsWithSharesFromTheTotal`, `ReportsServiceCurrencyTest.theReportStartsInTheProfilesDefaultCurrency...`, screenshots | PASS |
| US1-S2 seven figures and the identity | `ReportCalculatorTest.aWalletReportCountsIncomeExpensesAndTransfersByDirection`, `theBalanceIdentityHoldsOverRandomLedgers`, live summaries (identity inside every compared answer) | PASS |
| US1-S3 covered transfers appear nowhere; uncovered counts | `ReportCalculatorTest.aTransferBetweenTwoCoveredWalletsAppearsNowhere`, `aTransferToOrFromAnUncoveredWalletCountsInItsDirectionEvenWhenThatWalletWasDeleted`, live (Bank/Cash, Household/B Own, Gone) | PASS |
| US1-S4 top 5, order, ties, shares on the phone | `CategoryBreakdownTest.sortsBySumDescendingThenKeyAscendingInByteOrder`, `ReportsReducerTest.onlyTheTopFiveShowAndMoreCategoriesSaysSo`, `SharesTest`, `ReportsApiContractTest.noReportDtoHasAShareMember`, live by-category (items and order incl. ties) | PASS |
| US1-S5 labels: platform by language, own, partner's by reference, unavailable | `ReportLabelsTest.namesResolveOwnThenPlatformInTheAppLanguageThenTheReferencesOfEveryCoveredWallet`, `ReportsReducerTest.anUnnameableCategoryHasAnEmptyNameForTheScreenToSayUnavailable`, live `theCategoriesOfTheLiveSetResolveToNamesOnThePhone` ("Gym", own "Groceries", "B Snacks") | PASS |
| US1-S6 empty range: zeros, "No spending", balances show | `ReportsReducerTest.aReportWithNoSpendingHasNoRows`, `ReportCalculatorTest` (zero ranges), live one-day and future ranges | PASS |
| US1-S7 See all and Income choice | `ReportCategoriesTest.expensesAreTheDefault...`, `theIncomeSwitchShowsTheIncomeGroup`, `ReportsViewModelTest.seeAllOpensTheCategoriesWithTheScopesArguments` | PASS |
| US2-S1 offline: computed at once with "You're offline - Updated" | `ReportsServiceRefreshTest.offlineAndFailedReadsMapToTheirOutcomes`, `theUpdatedTimeIsTheOldestCoveredReadStamp`, `ReportsReducerTest.waitingTooLargeAndFreshnessReachTheState`, offline golden | PASS |
| US2-S2 waiting change counted, noted, unchanged on accept | `ReportsServiceWaitingTest.aWaitingChangeIsCountedAtOnceAndNoted`, `acceptingTheChangeLeavesEveryFigureEqualAndClearsTheNote`, `TransactionReportLedgerTest` (create, edit, delete, refused, clashing, confirmation) | PASS |
| US2-S3 open reads covered scopes, once a minute, pull forces | `ReportsServiceRefreshTest.aCrossWalletOpenReadsEverythingOnceAndAPullForcesIt`, `ReportsViewModelTest.openingReadsOnceWithinTheFloorAndAPullForces`; live: the real runner read heads and 8 feeds | PASS |
| US2-S4 after sync every figure equals the server's | `ReportsCapturedParityTest` (511 answers) | PASS |
| US2-S5 partner's expense appears after the read | live (partner's Household rows in the owner's report), `ReportsServiceSummaryTest.aNewRowRecomputesTheReport` | PASS |
| US2-S6 failed refresh: "Couldn't refresh - Updated", figures stay | `ReportsServiceRefreshTest.offlineAndFailedReadsMapToTheirOutcomes`, `ReportsViewModelTest.anOfflineOrFailedReadIsReportedAndDoneClearsIt`, failed-refresh golden | PASS |
| US3-S1 wallet detail Report, wallet currency, no choice or notice | `WalletDetailTest` (ReportClicked, OpenReport), `ReportsReducerTest.aWalletReportCarriesTheWalletIdAndTitleAndNoOtherCurrencies`, wallet golden | PASS |
| US3-S2 every member's transactions count | `ReportsServiceWalletTest.aWalletReportCountsEveryMembersTransactionsInTheWalletsCurrency`, live (Household 1,650.00 = 1,000.00 + 150.00 + 500.00 of three authors) | PASS |
| US3-S3 transfers by direction | `ReportsServiceWalletTest.transfersCountByDirection`, live Household (600.00 in, 250.00 out) | PASS |
| US3-S4 liability balances as amounts owed | `ReportsServiceWalletTest.aLiabilityWalletIsFlaggedAndItsBalancesStayNegative`, `ReportsAccessibilityTest.aLiabilityWalletsNegativeBalancesAreAmountsOwed` (new), credit-card golden, live Card report equal | PASS (G2 closed) |
| US3-S5 archived wallet; viewer sees the same | `ReportsServiceWalletTest.anArchivedWalletIsReportedLikeAnyOther`, live Old (archived) and Family (viewer) equal | PASS |
| US3-S6 purged wallet closes the screen | `ReportsServiceWalletTest.aPurgedWalletIsGone`, `ReportsViewModelTest.aPurgedWalletClosesTheScreen`, `AccessLostPurgeTest` | PASS |
| US4-S1 six periods ending with the range's last day, oldest first, zeros | `ReportsServiceTrendTest.threePeriodsAreZeroFilledOldestFirstWithoutTransfers`, `theTrendEndsWithThePeriodContainingARangesLastDay`, live trends | PASS |
| US4-S2 3, 6, 12 | `ReportsServiceTrendTest.changingTheLengthReEmits...`, `aLengthOtherThanThreeSixOrTwelveIsIgnored`, live m1/m3/m6/m12 | PASS |
| US4-S3 text summary and table | `ReportsAccessibilityTest.theChartIsOneNodeAnnouncedWithItsSummary`, `theTableListsEveryPeriodAndEachRowIsReadableAsOneNode`, `everyBarHasItsValueWrittenAndTheLegendNamesIncomeAndExpenses` | PASS |
| US4-S4 Arabic mirrored, Western digits, amounts LTR | Arabic RTL goldens (`ReportsScreenshotTest`, `ReportChartsScreenshotTest`), `ReportStringsTest.noExclamationMarksAndWesternDigitsOnly`, `MoneyFormatterTest` | PASS |
| US4-S5 transfers not in the trend | `ReportsServiceTrendTest.threePeriodsAreZeroFilledOldestFirstWithoutTransfers`, `ReportCalculatorTest.theTrendBucketsEachPeriodAndLeavesTransfersOut`, live trends | PASS |
| US5-S1 previous and next, stop at today + 35 | `ReportsServiceRangeTest.nextStopsAtThePeriodContainingTodayPlusThirtyFiveDays`, `ReportPeriodsTest.previousAndNextWalkAcrossDerivedAndLearnedPeriods`, `ReportsViewModelTest.rangeIntentsGoToTheDomainAndNextOnlyWhenAllowed` | PASS |
| US5-S2 custom range 366 accepted, 367 refused with the sentence | `RangeRulesTest`, `ReportsServiceRangeTest.aCustomRangeIsValidatedAndSetAndResetByCurrentPeriod`, `ReportsViewModelTest.theCustomPickerAcceptsAValidRangeAndKeepsAProblemOpen`, live 366 accepted, 367 refused by the server too | PASS |
| US5-S3 start day d derived as budgets and the server | `ReportPeriodsTest.startDaysAreDerivedAsBudgetsDeriveThemAcrossShortAndLongMonths`, `ReportsServiceRangeTest.startDayTwentyFiveOnTheSecondOfOctoberGivesTheBriefsPeriodAndPrevious`, live periods of A and B | PASS |
| US5-S4 past or pending change: server dates; offline "Shown when you're online" | `PeriodLookupTest` (all), `ReportsReducerTest.anUnknownPeriodHasNoRangeNoSummaryAndAnUnknownTrend`, live D (pending change): lookup, then all 45 reports equal | PASS |
| US5-S5 midnight in the profile zone | `ReportsServiceRangeTest.midnightInTheProfilesZoneMovesTodayThePeriodAndTheLimit`, `ReportsServiceSummaryTest.theDayTurningMovesTheCurrentPeriod` | PASS |
| US6-S1 default currency and choice of wallets' currencies | `ReportsServiceCurrencyTest` (default, no profile, USD profile), `CoveredWalletsTest.theCurrenciesAreTheWalletsPlusTheProfileDefaultSorted` | PASS |
| US6-S2 excluded notice and "See <code> report" | `ReportsReducerTest.excludedWalletsAndTheirCurrenciesAreListedSortedAndOnceEach`, `ReportsViewModelTest.theCurrencyChoiceExistsOnlyForTheCrossWalletReport`, live `meta.excluded_wallets` equal in every cross-wallet case, USD and EGP reports | PASS |
| US6-S3 currency with no wallet: zeros, every wallet named | `ReportsServiceCurrencyTest.aCurrencyWithNoWalletIsZerosAndEveryWalletIsNamed`, `...ShowsZeroScheduledFiguresWhenScheduledIsOn...` (new), live EUR | PASS (G1 closed) |
| US6-S4 never added or converted | `ReportsServiceCurrencyTest.settingTheCurrencySwapsCoveredAndExcludedAndNeverSumsAcrossCurrencies`, live (USD wallet absent from every EGP figure) | PASS |
| US7-S1 scheduled beside actual: not confirmed or skipped, missed and due included | `PlannedFiguresTest.withTheSwitchOnPlannedSumsAreSeparate...`, `ScheduledMoneyTest.onlyScheduledOccurrences...`, `ScheduledQueryTest`, live (229 planned comparisons incl. the missed 28 Sep occurrence in the owner's data) | PASS |
| US7-S2 never added to actual, shares or balances | `PlannedFiguresTest.actualMoneyIsNeverTakenFromTheScheduledLines`, `CategoryBreakdownTest.scheduledSumsAreOtherCurrencyFreeAndNeverAddedToTheValue`, live | PASS |
| US7-S3 waiting confirm or skip no longer scheduled | `PlannedFiguresTest.aWaitingConfirmOrSkipRemovesTheScheduledMoneyThroughTheHook`, `ScheduledMoneyTest.aWaitingConfirmOrSkipRemovesTheOccurrence` | PASS |
| US7-S4 switch off: nothing scheduled anywhere | `PlannedFiguresTest.theSwitchIsOffByDefaultAndNothingScheduledShowsAnywhere`, live (planned null in every `include_planned=false` answer, phone null) | PASS |
| US7-S5 "known up to" | `ReportsServiceRangeTest.theKnownUntilDateAppearsOnlyWhenTheSwitchIsOnAndTheRangeEndsAfterIt`, `ReportsAccessibilityTest.theScheduledPartIsWrittenAndNamedNotJustOutlined` | PASS |

## Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Opening balance in every range's balances; waiting change counts | `ReportsServiceSummaryTest.theBalancesStartFromTheOpeningsAndTheBeforeEffect`, `ReportsServiceWaitingTest.aWaitingChangeToACoveredWalletItselfIsNoted`, live balances | PASS |
| Transfer between covered wallets nowhere; covered to uncovered counts | `ReportCalculatorTest`, live | PASS |
| Deleted transaction never counts; waiting deletion removes at once | `TransactionReportLedgerTest.aWaitingDeletionRemovesTheLineAndTheBalanceEffect`, live deleted rows (t18, h7) absent from the server and the phone | PASS |
| Future-dated transaction | live (20 Oct expense, 3 Nov income in `balance_end` and ranges) | PASS |
| Liability wallet (wallet report owed; cross-wallet negative with U+2212) | G2 tests, `ReportCalculatorTest.aLiabilityWalletKeepsItsNegativeBalances` | PASS |
| Archived wallet / archived category | `ReportsServiceWalletTest.anArchivedWalletIsReportedLikeAnyOther`, `CoveredWalletsTest`, live Old | PASS |
| Left or purged wallet drops out of every range | `CoveredWalletsTest`, `AccessLostPurgeTest`, live (Gone absent) | PASS |
| Wallet joined today counts for past ranges | live (Household for B and Family for A, September and August ranges equal) | PASS |
| Wallet created on the phone, not yet synced | `CoveredWalletsTest` (phone-only create covered), `ReportsServiceWaitingTest` | PASS |
| Category kinds: one kind only; unresolvable counts under its id | `CategoryBreakdownTest`, `ReportsReducerTest.anUnnameableCategory...` | PASS |
| Shares: zero total none, half up, "<1%" | `SharesTest` | PASS |
| Very large amounts: checked addition, "Too large to show" | `ReportCalculatorTest.anOverflowIsTooLargeNotAWrongNumber`, `ReportsServiceSummaryTest.anOverflowShowsTooLargeInsteadOfAWrongNumber`, `ReportCategoriesTest.anOverflowShowsTooLarge` | PASS |
| Three- and zero-decimal currencies | `MoneyFormatterTest` (KWD 3 digits, JPY 0), reports use `formatMoney` | PASS |
| Range at the 366-day limit | `RangeRulesTest`, live | PASS |
| Transitional period after a start day change | `ReportPeriodsTest.theTransitionalPeriodIsNotDerivableEvenAfterTheChangeDate`, live D (1-24 Oct from the server) | PASS |
| Day turns while open | `ReportsServiceRangeTest.midnightInTheProfilesZone...` | PASS |
| Process killed while offline: a report is recomputed, nothing stored | `ReportsServiceRangeTest.theChoicesLastWhileTheAppRunsAndNothingIsStored`, schema unchanged | PASS |
| Large data (20,000 rows, 20 wallets, 12 periods) | `ReportsPerformanceTest` (0.30-0.35 s) | PASS |
| Unknown kind from a later server ignored | `ReportCalculatorTest.aKindThisAppDoesNotKnowAndALineOutsideTheRangeCountNothing` | PASS |
| Sign-out drops choices and learned periods | `SignOutWipeTest`, `ReportHoldersTest.theCleanerEmptiesChoicesAndLearnedPeriods` | PASS |

## Functional requirements

| FR | Evidence | Result |
|---|---|---|
| FR-001 phone computes, overlay, no server figure shown | calculator and service tests, `TransactionReportLedgerTest`, `ReportsLayeringTest.theLookupAndTheProbeAreTheOnlyCallersOfTheReportEndpoints` | PASS |
| FR-002 waiting note | `ReportsServiceWaitingTest` (all five) | PASS |
| FR-003 wallet report | `ReportsServiceWalletTest`, live wallet reports | PASS |
| FR-004 cross-wallet = sum of wallets minus covered transfers | `ReportCalculatorTest`, `CoveredWalletsTest`, live cross-wallet vs wallet sums | PASS |
| FR-005 balances and identity | `ReportCalculatorTest.theBalanceIdentityHoldsOverRandomLedgers`, live | PASS |
| FR-006 by category | `CategoryBreakdownTest`, live (items, order, totals) | PASS |
| FR-007 shares | `SharesTest`, contract test (no share member) | PASS |
| FR-008 default range, previous and next | `ReportsServiceRangeTest`, `ReportPeriodsTest` | PASS |
| FR-009 custom range | `RangeRulesTest`, `ReportsViewModelTest` | PASS |
| FR-010 trend | `ReportsServiceTrendTest`, live | PASS |
| FR-011 periods derived or from the server, never guessed | `ReportPeriodsTest`, `PeriodLookupTest`, live D | PASS |
| FR-012 choices in memory | `ReportHoldersTest`, `ReportsServiceRangeTest` | PASS |
| FR-013 one currency | `ReportsServiceCurrencyTest`, live | PASS |
| FR-014 excluded wallets named | `CoveredWalletsTest`, `ReportsReducerTest`, live | PASS |
| FR-015 labels and period text | `ReportLabelsTest`, `rangeText` goldens, live names | PASS |
| FR-016 scheduled beside actual | `PlannedFiguresTest`, live; G1 closed | PASS |
| FR-017 digits, U+2212, owed, neutral ink | `MoneyFormatterTest`, G2 tests, `ReportsAccessibilityTest.noAlarmWordingAndAnExpenseIsNeverShownInTheErrorColour` | PASS |
| FR-018 charts: mirrored, written values, text alternative, 200% | `ReportsAccessibilityTest`, `ReportChartsScreenshotTest`, `ReportsScreenshotTest` (EN, AR RTL, 2.0) | PASS |
| FR-019 lookup | `PeriodLookupTest`, live D | PASS |
| FR-020 freshness | `ReportsServiceRefreshTest`, `ReportsViewModelTest` | PASS |
| FR-021 Home row, wallet Report action | `HomeBudgetsTest`, `WalletDetailTest` | PASS |
| FR-022 section order, categories screen | `ReportsReducerTest`, `ReportCategoriesTest`, screenshots | PASS |
| FR-023 no wallet | `ReportsServiceSummaryTest.withNoWalletTheStateIsNoWallets`, `ReportsViewModelTest.noWalletsOffersTheWalletsScreenAndABackCloses` | PASS |
| FR-024 not gated, `reports.advanced` unread | `ReportsLayeringTest.nothingInReportsTouchesNotificationsOrTheReservedAdvancedFeature`, `PlanNameNotGatedTest` | PASS |
| FR-025 logs | `ReportsLoggingTest`, live probe log check | PASS |
| FR-026 EN and AR strings | `ReportStringsTest` | PASS |
| FR-027 failures by code | `ReportsApiContractTest.everyRefusalIsMappedByItsCode`, `PeriodLookupTest.offlineAFailureOrABadRequest...`, live refusals | PASS |
| FR-028 dev-only probe | `ReportParityProbeTest`, live probe test | PASS |
| FR-029 nothing stored, no table | `AppGraphTest`, `SignOutWipeTest`; `shared/schemas/.../10.json` unchanged, no `11.json` | PASS |

## Success criteria

| SC | Evidence | Result |
|---|---|---|
| SC-001 one tap, summary within 1 s | one tap: Home row test; compute: `ReportsPerformanceTest` 0.30-0.35 s for the largest set on the host | PASS on host; device timing deferred (T094) |
| SC-002 100% of figures equal after sync for the live set | `ReportsCapturedParityTest`: 511 answers, every member; the only difference is gap 2 (planned transfers in, 6 summaries), the set covers cross-wallet and wallet scopes, shared wallet with a partner, covered to covered, to an uncovered wallet and from a deleted wallet, liability, archived, deleted and future-dated, scheduled, a second currency, start day 1 and a pending 25 | PASS (gap 2 recorded) |
| SC-003 offline change within 1 s, noted, unchanged on accept | `ReportsServiceWaitingTest` | PASS |
| SC-004 phone's periods equal the server's | `ReportPeriodsTest` table (1, 15, 25, 28; past and pending change) and live A, B, D periods | PASS |
| SC-005 20,000 rows, 12 periods within 1 s | `ReportsPerformanceTest` | PASS |
| SC-006 screenshots EN, AR, 2.0 and text alternatives | `ReportsScreenshotTest`, `ReportChartsScreenshotTest`, `ReportsAccessibilityTest` (`verifyRoborazziAndroidHostTest` green) | PASS |
| SC-007 integration checkpoint | `ReportsServiceSummaryTest.theCurrentPeriodsSummaryAndSharesMatchTheBriefsExample`, offline caption tests | PASS |

## Invariants (spec section 4)

All twelve have their failing-when-broken test, unchanged from the implementation notes; the two added here are the captured-answer comparison (invariant 9, "phone figures equal the server's after sync", now over live answers) and the zeros-for-an-empty-currency rule inside invariant 8 (planned figures separate).

## Gradle runs

- `./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64 --rerun-tasks`: BUILD SUCCESSFUL in 5 m 38 s, 85 of 85 actionable tasks executed; `testAndroidHostTest` 3,179 tests in 405 suites, 0 failures, 0 errors, 0 skipped; `verifyRoborazziAndroidHostTest` executed green (no golden changed by this QA); the dev debug APK and the iOS simulator Kotlin compile executed.
- `./gradlew cleanAllTests build allTests --continue`: BUILD SUCCESSFUL in 6 m 57 s; `cleanAllTests` removed the old results and `testAndroidHostTest` executed again (3,179 tests, 0 failures); `lintDevDebug` and the release lint-vital analyses ran; the iOS simulator test task is skipped on this Linux host (compile only, as in specs 008-013); `androidApp` has no unit tests.

## Flaky-test findings

- `ReportsPerformanceTest` (SC-005): measured 300-350 ms for the fastest of five on a lightly loaded host with another Gradle build running (the bound is 1,000 ms; the earlier note of about 35% headroom was taken while the machine was busy). Hardened: two warm-up passes, then the fastest of seven runs, with all runs in the failure message. The bound stays the success criterion; a real regression slows every run, so taking the fastest run does not hide one, while a single run delayed by another process no longer fails the build.
- No other test showed timing sensitivity. The parity tests are deterministic (recorded answers, fixed "now").

## Deferred (owner's device)

T037 T047 T054 T061 T069 T075 T081 (flow runs), T094 (quickstart manual checks), T095 (`/speckit-e2e`), and T096 (summary, written by the parent).
