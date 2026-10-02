# QA report: Transactions (client, spec 008)

**Date**: 2026-10-02 · **Branch**: `008-transactions` · **Mode**: no device (owner policy: no emulator or
Maestro runs; E2E deferred to the owner's phone) · **Backend**: local dev stack, `saveme-backend@f6d66eb`
(`transactions.yaml` unchanged since `c0e9844`; seeded defaults).

Evidence is a passing host test (`./gradlew :shared:testAndroidHostTest`), a Roborazzi screenshot
(`verifyRoborazziAndroidHostTest`), a live backend answer (§5) or a code path. Test classes are under
`shared/src/commonTest/kotlin/com/fares/saveme/feature/transactions/` unless a path is given
(`ht/` = `shared/src/androidHostTest/kotlin/com/fares/saveme/`). "Captured" = `data/TransactionCapturedResponsesTest`
(15 tests over 51 responses recorded from the live backend in `data/CapturedTransactionResponses.kt`).

## 1. Acceptance scenarios

| Scenario | Evidence | Result |
|---|---|---|
| US1-S1 Editor opens on the wallet, Expense, today, amount focused | `TransactionEditorViewModelTest.createModeOpensWithTheKeysWalletExpenseTodayAndAnEmptyAmount`; `TransactionsTest.rowAndAddIntentsAreEffectsAndAddNeedsAVerifiedAddress`; screenshot `screen_transactions_editor_create_empty_*` | PASS |
| US1-S2 Save lists it at once, online or offline, sent on its own | `TransactionsServiceCreateTest.aValidCreateWritesAPlaceholderAndOneQueueRowInTheSourceWalletScope…`, `.theWalletStaysCreatableOfflineAndNothingNeedsAConnection`; captured `aCreateSendsWhatQaSentAndTheBalanceNeverJumps…` (request body equals the one the live server accepted with 201) | PASS |
| US1-S3 Category choice: own + defaults of the kind, two groups | `TransactionEditorViewModelTest.theCategorySheetIsFedByTheChoicesForTheKindAndTheKindSwitchChangesThem`; C5 `CategoryChoicesTest`; screenshot `screen_transactions_editor_category_sheet_*` | PASS |
| US1-S4 Amount / category errors next to the field, nothing saved | `TransactionEditorReducerTest.typingTheAmountChecksItAgainstTheWalletsCurrency`, `TransactionEditorViewModelTest.aBadAmountIsReportedAndNothingIsSent`; `TransactionModelTest.theAmountParserRefuses…PerCurrency`; `TransactionsServiceCreateTest.theAmountMustBePositive…`, `.anIncomeOrExpenseNeedsACategory…`; screenshot `screen_transactions_editor_create_errors_*` | PASS |
| US1-S5 Past/future date saved and listed under it | `TransactionEditorViewModelTest.theDatePickerResultIsKept`; `DayGroupingTest.newestDateFirst…`; captured list test (real rows on two dates) | PASS |
| US1-S6 Note / details / person / cleared saved; over-long refused | `TransactionsServiceCreateTest.textLimitsCountCodePointsAfterTrimmingAndBlankBecomesNone`; `TransactionEditorReducerTest.textLimitsAreCountedInCodePointsAfterTrimming`; live: 100 emoji note + 1,000-char details → 201, 101 → 422 `OUT_OF_RANGE@/description` (captured refusal test) | PASS |
| US1-S7 Home: last wallet, switchable; no wallet → create one | `WritableWalletsTest` (4 tests); `TransactionEditorViewModelTest.homesPreselectionIsTheDefaultWallet…`, `.withNoWritableWalletTheEditorSaysSoAndOffersToCreateOne`; `HomeViewModelTest.addTransactionClickedNavigatesToTheEditor`; screenshot `screen_transactions_editor_no_wallet_*` | PASS |
| US1-S8 Unverified: verify prompt, nothing queued | `TransactionsServiceCreateTest.anUnverifiedAddressQueuesNothing`; `TransactionEditorViewModelTest.unverifiedAndInvalidResultsAreRouted…`; `TransactionsTest.rowAndAddIntents…AddNeedsAVerifiedAddress` | PASS |
| US1-S9 Viewer: no add | `TransactionsServiceListTest.canAddFollowsTheRoleAndTheArchivedState`; `WalletDetailTest.addTransactionIsOfferedToOwnersAndEditorsOfAnActiveWalletOnly`; screenshot `screen_transactions_list_empty_viewer_*` | PASS |
| US1-S10 First transaction locks the currency before it syncs | `WalletEditorTest.theCurrencyIsReadOnlyWhileAnUnsentTransactionExists…`; captured `aCreateSendsWhatQaSent…` (locked while waiting, **while the carry waits after the sync** and after the read: gap closed, §4.2); live: `currency_locked` true after the first transaction | PASS (gap closed) |
| US2-S1 Newest first, Today/Yesterday/date headers with day net | `DayGroupingTest` (3); `TransactionsServiceListTest.rowsAreGroupedByDayNewestFirstWithNetTotals`; screenshot `screen_transactions_list_loaded_*` | PASS |
| US2-S2 Row: category display, note, signed amount, expense not in error colour | `TransactionsTest.theScreenShowsThePhonesCopyGroupedByDayWithResolvedCategories`; `ht/…/TransactionsAccessibilityTest.moneyDirectionIsAnnouncedInWordsAndNotOnlyBySign`; design-system `TransactionRow` (U+2212, ink2); screenshots | PASS |
| US2-S3 Marks; balance = server + unsent on every screen | `TransactionsTest.rowsCarryTheirStatusInWords`; `TransactionPostingsTest` (7); captured `threeOfflineTransactionsAreSentOldestFirst…` (93,850 shown before and after) | PASS |
| US2-S4 No jump after sync | `TransactionPostingsTest.theDeltaDoesNotMoveAcrossTheSendAndTheBalanceNeverJumps`; `BalanceReconcilerTest` (5); captured `aCreateSendsWhatQaSent…` (95,450 before the send, after it, after the wallet read) and `aDeleteSends…UntilTheWalletReadSettlesIt` | PASS |
| US2-S5 Other-device changes on open/pull/foreground; deleted elsewhere disappears | `TransactionsServiceRefreshTest` (6); `TransactionsTest.openingAndForegroundingRefreshWithoutForceAndPullForces`; `TransactionRepositoryTest.anUnlistedRowIsRemovedUnless…` | PASS |
| US2-S6 Offline: last known + updated time | `TransactionsServiceRefreshTest.offlineAndFailedOutcomes…`; `TransactionsTest.anOfflineRefreshShowsTheOfflineState…`; screenshot `screen_transactions_list_offline_*` | PASS |
| US2-S7 Empty note, add when writable | `TransactionsTest.theLoadingStateShowsUntilTheFirstRead…`; screenshots `screen_transactions_list_empty_*`, `_empty_viewer_*` | PASS |
| US2-S8 Partner category shown; unresolved → "Category unavailable" + reference fetch | `TransactionsServiceRefreshTest.openingReadsTheListAndAsksForThePartnerCategories…`; C5 `CategoryResolverTest`; screenshot `screen_transactions_list_loaded_*` (partner and unavailable rows); live: B's own category in A's shared wallet is published as a reference (`CATEGORY_REFERENCES_200`) | PASS |
| US2-S9 Added by member / "Another member"; recurring caption | `TransactionsServiceListTest.addedByNamesTheMember…`; captured `aPartnersTransferFromAWalletNotOnThePhone…` (real `created_by` of B → "Omar"); `TransactionEditorViewModelTest.addedByAndTheRecurringRuleCaptions…` | PASS |
| US3-S1 Edit fields; kind/wallets read-only | `TransactionEditorViewModelTest.editModeStartsFromWhatThePhoneShows…`; `TransactionsServiceEditDeleteTest.saveUsesTheEditorsBaseVersionAndTheStoredKind…`; screenshot `screen_transactions_editor_edit_*` | PASS |
| US3-S2 Current category kept even when archived/retired/partner's | `TransactionEditorViewModelTest.theCurrentCategoryStaysSelectedEvenWhenItIsArchivedOrAPartnersOrUnknown`; live: save keeping an archived own category → 200 (`PUT_ARCHIVED_CATEGORY_200`); captured `aPartnersCategoryAndTheReadOnlyLinks…` | PASS |
| US3-S3 Edit shows at once, sent on the version shown at open | `TransactionsServiceEditDeleteTest.saveUsesTheEditorsBaseVersion…`; captured `aStaleSave…` (real 409 for base 1 against version 2) | PASS |
| US3-S4 Delete confirmation names amount and date | `TransactionEditorViewModelTest.deleteAsksForConfirmationThenDeletesAndCloses`; screenshot `screen_transactions_dialog_delete_*` | PASS |
| US3-S5 Delete disappears at once; never-sent sends nothing | `TransactionsServiceEditDeleteTest.aConfirmedDeleteQueues…`, `.aNeverSentCreateIsRemovedAtOnceWithNothingToSend`; captured `aDeleteSendsItsVersionInABody…` | PASS |
| US3-S6 Edit clash side by side, Keep mine / Use theirs | `TransactionEditorReducerTest.aClashListsOnlyTheDifferingFields`; `TransactionEditorViewModelTest.aClashOffersKeepMineAndUseTheirs…`; captured `aStaleSaveBecomesAClash…` (real `current`: 60.00, "Market", own category, v2) | PASS |
| US3-S7 Deleted elsewhere: Restore with my change / Accept | `TransactionRestoreFlowTest`; captured `aSaveAfterADeletionElsewhereOffersRestore…` (PATCH `{"deleted_at":null,"version":3}` then PUT on v4, as the live server took them) | PASS |
| US3-S8 Delete clash: Delete anyway / Keep | `TransactionRestoreFlowTest.aDeleteClashOffersDeleteAnyway…`; captured `aStaleDeleteBecomesADeleteClashWithTheLiveCopy`; screenshot `screen_transactions_editor_delete_clash_*` | PASS |
| US3-S9 Refusal reasons by code and pointer | captured `everyRealRefusalReadsAsItsReasonAndLandsOnItsField` (17 real answers); `TransactionSyncStateTest`; `TransactionCopyTest`; screenshots `screen_transactions_editor_refused_*` | PASS |
| US3-S10 Viewer read-only | `TransactionEditorViewModelTest.aViewerSeesTheTransactionReadOnlyWithNoSaveOrDelete`; screenshot `screen_transactions_editor_viewer_*` | PASS |
| US4-S1 Destination choice; none qualifies → note, no Save | `DestinationChoicesTest` (2); `TransactionEditorViewModelTest.aTransferHasADestinationChoiceInsteadOfACategory`; screenshots `…create_transfer_*`, `…create_transfer_none_*` | PASS |
| US4-S2 Listed in both wallets, no category, both balances | `TransactionsServiceTransferTest.itIsListedInBothWalletsAndMovesBothDisplayedBalancesWhileItWaits`; captured `aTransferIsOneRowListedInBothWallets…` (real lists of both wallets); live: Cash −500.00 and Bank +500.00 | PASS |
| US4-S3 Transfer edit: wallets read-only | `TransactionEditorViewModelTest.aTransferShowsBothWalletsReadOnlyInEditMode`; `TransactionSyncHandlerTest.aTransferKeepsItsStoredDestination`; live: changing the destination → 422 `IMMUTABLE_FIELD@/counter_wallet_id` | PASS |
| US4-S4 FORBIDDEN on a transfer → "other wallet" | captured refusal test (real 403 for B: editor of Cash, viewer of Bank); `TransactionCopyTest.aForbiddenTransferReadsAsNoRightInTheOtherWallet…` | PASS |
| US4-S5 Currencies differ / destination gone → Not saved, discard | captured refusal test (`CURRENCY_MISMATCH@/counter_wallet_id`, `INVALID_ENUM_VALUE@/counter_wallet_id`, discard leaves nothing) and `aRestoreOfATransferWhoseDestinationWasDeleted…` | PASS |
| US4-S6 Other wallet not on the phone: "another wallet", delete only | `TransactionsServiceListTest.aTransferWhoseOtherWalletIsNotOnThePhoneHasAnUnknownCounterpart`; `TransactionsServiceTransferTest.aTransferFromAWalletThatWasDeleted…`; captured `aPartnersTransferFromAWalletNotOnThePhone…` (DELETE through the destination, real 200) | PASS |
| US5-S1 Search: literal, case-insensitive, 1–100 | `TransactionFilterTest` (6); `TransactionsTest.typingSearchesAsYouGo…`, `.searchTextIsLimitedToOneHundredCharacters`; live: `search=PHONE` matches "Phone edit", `%` and `_` match nothing (literal) | PASS |
| US5-S2 Date / category / kind filters, OR within, AND across | `TransactionFilterTest.valuesOfOneFilterAreOrAndFiltersAreAnd`, `.theDateRangeIsInclusive`, `.theMonthPresetsFollowTheGivenToday`; `TransactionsTest.categoryAndKindTogglesCombine…` | PASS |
| US5-S3 Chips, clear one / all, no matches | `TransactionsTest.chipsListTheActiveFilters…`, `.clearingAChipRemovesOnlyThatFilter`; screenshots `…list_chips_*`, `…list_no_matches_*` | PASS |
| US5-S4 Offline search | `TransactionsServiceListTest.searchRunsOverTheShownValuesIncludingAWaitingEdit` (Room only, no API) | PASS |
| US5-S5 Leaving resets filters | `TransactionsTest.aNewStoreStartsWithoutFiltersSoLeavingTheScreenResetsThem` | PASS |

**40 scenarios: 40 PASS** (one gap closed). Device runs (T040–T088) remain deferred.

## 2. Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Offline create → edit → delete: nothing sent, balance back | `TransactionsServiceEditDeleteTest.editingAPlaceholderRewritesTheCreateWithBaseZero`, `.aNeverSentCreateIsRemovedAtOnceWithNothingToSend`; spec 004 `SyncEngineSendTest` | PASS |
| Several offline sent oldest first; a refusal doesn't stop others | captured `threeOfflineTransactionsAreSentOldestFirstAndARefusalOfOneDoesNotStopTheOthers` (new); `SyncEngineSendTest.recordsAreSentInTheOrderTheyWereFirstChanged` | PASS (gap closed) |
| Category archived/deleted while waiting | live: archived category accepted (200/201); an unknown category → 422 `INVALID_ENUM_VALUE@/category_id` → `CategoryUnusable` (captured) | PASS |
| Role drops to viewer while waiting | captured (real 403 for a viewer → `NoRightHere`, discard) ; `TransactionsServiceListTest.editAndDeleteRightsFollowTheRole…` | PASS |
| Wallet deleted / access lost | `TransactionsWalletCleanupTest` (2) | PASS |
| Currency changed before the first sync | captured `aCurrencyChangedRefusalOfANeverSentCreateIsSavedAgainInTheWalletsPresentCurrency` (new: re-sent in USD, version 0); `TransactionEditorViewModelTest.editingAfterACurrencyChangedRefusal…` | PASS (gap closed) |
| Negative balance allowed | code path (no balance check in `validate`); live: Cash accepted down to −90,071,992,547,008.92 | PASS |
| Largest amount accepted; above refused | `TransactionsServiceCreateTest.theAmountMustBePositiveAndAtMostTheLargestTheServerAccepts`; live: 9,007,199,254,740,991 → 201, +1 → 422; captured list decodes it as `Long` | PASS |
| Dates outside 1900–2999 not choosable | `TransactionsServiceCreateTest.theDateStaysBetween1900And2999`; `AppDatePickerDialog` min/max; live: 1899-12-31 and 3000-01-01 → 422 `OUT_OF_RANGE@/date` | PASS |
| No categories available: note, no Save | `TransactionEditorReducerTest.saveIsUnavailableForATransferWithoutADestinationAndWhenNoCategoryCanBePicked` | PASS |
| Unknown kind: neutral, not editable, deletable | `TransactionsServiceEditDeleteTest.anUnknownKindCannotBeEditedButCanBeDeleted`; `DayGroupingTest.anUnknownKindContributesNothing`; screenshot `…editor_unknown_kind_*` | PASS |
| Recurring links kept, never sent | `TransactionApiContractTest.putSendsExactlyTheElevenWritableMembers…`, `.responsesIgnoreUnknownMembersAndKeepTheRecurringLinks`; captured (real `null` links; edit body equals QA's); live: sending `recurring_rule_id` → 422 `UNKNOWN_FIELD` | PASS |
| **Open transaction vanishes: keep typing, save becomes a waiting change** | **implemented in QA** (§4.1): `TransactionEditorViewModelTest.aTransactionRemovedWhileThePersonTypesKeepsTheTypingAndSavesOnTheCopyTheyOpened`, `.aTransactionRemovedWithNothingTypedClosesWithTheMessage…`; `TransactionsServiceEditDeleteTest.aSaveOfATransactionRemovedWhileEditing…`, `.aRemovedTransactionInAWalletThePersonCanNoLongerWriteIsNotPutBack`; captured `aTransactionRemovedByARefreshWhileThePersonTypes…` (real 409 tombstone → deleted-elsewhere clash → accept removes it) | PASS (gap closed) |
| Two members edit one transfer: one version, one clash | one record id, one queue row, scope = source (`TransactionSyncHandler`); the 409 path is the same as any edit | PASS |
| Restore whose second step is refused or clashes | `TransactionSyncHandlerTest.aFailureOfThePutAfterAPatchIsReturnedAsThatFailure`; captured restore tests. The phone keeps its pre-deletion copy until the next read brings the restored version (a later edit on the old base clashes and resolves) | PASS |
| Source wallet deleted: listed in the destination, delete only | `TransactionsServiceTransferTest.aTransferFromAWalletThatWasDeleted…`; live: still listed in Cash, edit → 422 `INVALID_ENUM_VALUE@/counter_wallet_id`, DELETE via Cash → 200 | PASS |
| Very long histories | `TransactionsListPerformanceTest` (2,000 rows); refresh follows every cursor (captured paging test, 3 real pages) | PASS |
| Time zone change | `ProfileToday`; `TransactionsTest.theMonthPresetsUseTheProfilesToday`; dates stored as `YYYY-MM-DD` | PASS |
| Sign-out removes every transaction | `ht/…/SignOutWipeTest` | PASS |

**19 edge cases: 19 PASS** (three gaps closed).

## 3. Functional requirements

| FR | Evidence | Result |
|---|---|---|
| FR-001 local copy renders everything | `TransactionsServiceListTest`, layering test (no API in UI) | PASS |
| FR-002 required/optional fields, kind switch clears | `TransactionsServiceCreateTest`, `TransactionEditorReducerTest.switchingTheKindClearsWhatNoLongerApplies` | PASS |
| FR-003 amount input, digits, max | `TransactionModelTest` (EGP/KWD/JPY, Arabic-Indic digits), create tests, live max | PASS |
| FR-004 date range, Western digits | create test, AR screenshot `transactions_ar.png` | PASS |
| FR-005 local first, queued in source scope with base version, UUIDv7 | create/edit tests, `theIdIsAUuidV7`, captured | PASS |
| FR-006 currency from the wallet | `aValidCreate…WithTheWalletsCurrency`; captured currency-changed test | PASS |
| FR-007 category through C5's choice | editor category tests | PASS |
| FR-008 add only for owner/editor of active wallet; Home preselection; wallet switch | `WritableWalletsTest`, editor wallet-switch test | PASS |
| FR-009 verification gate; no plan gate | create test (no entitlement dependency) | PASS |
| FR-010 one record, destination choices | `DestinationChoicesTest`, transfer create test, live 201 | PASS |
| FR-011 both lists and both balances | transfer service + postings tests; captured | PASS |
| FR-012 kind/wallets immutable, `IMMUTABLE_FIELD` at the field | handler tests; captured (real `/kind` and `/counter_wallet_id` → field `Kind` / `Destination`) | PASS |
| FR-013 edit/delete rights for transfers | `TransactionsServiceTransferTest` (3), captured partner transfer; live: A (destination owner, not a member of the source) deleting B's transfer while B Cash existed → 403, shown as "other wallet" | PASS |
| FR-014 403 wording by kind | copy + captured | PASS |
| FR-015 order, headers, day net | `DayGroupingTest` | PASS |
| FR-016 row contents and marks | list tests, accessibility test | PASS |
| FR-017 balance = server + unsent + carry | `TransactionPostingsTest`, `BalanceReconcilerTest`, captured | PASS |
| FR-018 all pages, floors, merge removals | `TransactionRepositoryTest`, refresh tests, captured paging | PASS |
| FR-019 partner categories refreshed, resolved offline | refresh test; C5 resolver | PASS |
| FR-020 added by, recurring caption | list + editor tests | PASS |
| FR-021 viewer read-only | editor/list tests | PASS |
| FR-022 delete confirmed, tombstone, never-sent removed | edit/delete tests; captured delete | PASS |
| FR-023 clash lines and choices | reducer/VM tests; captured | PASS |
| FR-024 restore with my change | restore flow + captured | PASS |
| FR-025 refusal reasons, choices, discard | captured refusal matrix; `TransactionSyncStateTest`; copy test | PASS |
| FR-026 re-enter amount after currency change | editor test; captured currency-changed test | PASS |
| FR-027 search rules | `TransactionFilterTest`; live server agrees (`%`, `_`, case) | PASS |
| FR-028 filters and chips | filter + list store tests | PASS |
| FR-029 balances and currency lock include unsent | postings tests; `WalletEditorTest`; captured (lock through the carry, §4.2) | PASS (fix) |
| FR-030 category uses | `TransactionCategoryUsesTest` | PASS |
| FR-031 wallet removal | `TransactionsWalletCleanupTest`; list test (hidden wallet) | PASS |
| FR-032 sign-out | `SignOutWipeTest` | PASS |
| FR-033 only write members; unknown ignored | contract test; captured bodies compared member for member | PASS |
| FR-034 copy, glossary, RTL | `ht/…/TransactionStringsTest`, AR screenshots | PASS |
| FR-035 accessibility | `ht/…/TransactionsAccessibilityTest`, 2× screenshots | PASS |
| FR-036 no payload in logs | `TransactionLoggingTest`, handler log test | PASS |

**36 FRs: 36 PASS.**

## 4. Gaps found and closed

1. **Edge case "the open transaction vanishes" was not implemented** (plan recorded it as a follow-up). Now: with typing on screen the editor stays, Delete is withdrawn, and Save passes the server copy it opened on; `TransactionsService.save(…, lastSeen)` puts that copy back and queues the edit on its version, so the server answers with the deletion clash (or a refusal). With nothing typed the editor closes with "no longer available", and a save that finds nothing to base on now says so too (it closed silently before). No spec amendment needed; "no waiting change" is read as "nothing typed" (plan.md implementation notes). 6 tests.
2. **Currency unlocked between a sync and the wallet re-read** (US1-S10, FR-029). `hasPending` covered only queued changes; after the send, the stored wallet still said `currency_locked: false` until the reconciler's read (≈1 s online, indefinitely offline), so the wallet editor briefly allowed a currency change the server would refuse. `TransactionPostings.hasPending` now also counts a carried change; captured `aCreateSendsWhatQaSent…` proves locked before, during and after.
3. **No end-to-end proof that a refusal of one offline transaction doesn't block the others**, and **no service-level proof of FR-026's re-send in the new currency**: two captured tests added.
4. **Contract decoding only against hand-written fixtures** → `TransactionCapturedResponsesTest` (15 tests, real bodies; request bodies compared with the ones the live server accepted).
5. **Flaky `TransactionsServiceRefreshTest.aPullAlwaysReadsAndAlsoSendsWhatWaits`** (failed once in the full `--rerun-tasks` run): a read that changed rows asks for a wallet read in the background, and the mock answered that `GET /wallets` with a transactions page, so the decode failed on Ktor's thread whenever the background read ran before the test ended. The three tests with a changed read now answer `/wallets` with a wallet page. Test-only.
6. **Maestro**: see §6 (one flow would fail every month but October 2026, one would never reach Home).

## 5. Contract QA against the live backend (T083)

Throwaway accounts `qa008-a@example.test` (A, verified), `qa008-b@example.test` (B, verified),
`qa008-unv@example.test` (unverified), made with `UserFactory` through `dev tinker`; signed in through
`POST /auth/login`. Requests sent with the app's headers (`Accept`, `X-Client-Platform: android`,
`X-Client-Version`, `Accept-Language`, `X-Correlation-Id`, bearer) and the exact bodies `TransactionApi`
builds. A owns Cash (EGP, 1,000.00), Bank (EGP) and Dollars (USD); B owns B Cash (EGP). B was made
editor of Cash and viewer of Bank by a `wallet_members` insert (factory, `ScopeSequence` under the wallet
lock), as backend QA did before invitations exist.

| Call (as the app sends it) | Answer | Matches the client |
|---|---|---|
| `PUT …/transactions/{id}` v0 (11 members, explicit nulls) | 201; `recurring_rule_id`/`occurrence_id` null; `created_by` = A | Yes |
| identical replay | 200, unchanged v1 | Yes (stored once) |
| same id, v0, different values | 409 `VERSION_CONFLICT` with live `current` | Yes → clash |
| save v1 → v2 | 200 | Yes |
| stale save (v1 after v2) | 409 with live `current` | Yes → side-by-side clash |
| `GET /wallets/{id}` after create / edit / delete / restore / transfer | `balance_minor` 95,450 → 94,000 → … ; `currency_locked` false before the first transaction, true after (also for Bank after only an incoming transfer, and still true after every transaction was deleted) | Yes (client keeps the lock while unsent or carried) |
| currency USD into an EGP wallet | 422 `CURRENCY_MISMATCH@/currency` | Yes → "the wallet's currency changed" |
| transfer EGP → USD | 422 `CURRENCY_MISMATCH@/counter_wallet_id` | Yes → "wallets use different currencies" |
| kind changed | 422 `IMMUTABLE_FIELD@/kind` | Yes (never sent by the client) |
| transfer destination changed (other currency or same) | 422 `IMMUTABLE_FIELD@/counter_wallet_id` | Yes |
| expense without category | 422 `REQUIRED@/category_id` | Yes (field problem) |
| amount 0 / max+1; max | 422 `OUT_OF_RANGE@/amount_minor`; 201 | Yes |
| unknown / income-kind / partner-private category | 422 `INVALID_ENUM_VALUE@/category_id` | Yes → "category can't be used" |
| unknown counter wallet; transfer to itself; expense with a counter wallet | 422 `INVALID_ENUM_VALUE@/counter_wallet_id` | Yes |
| transfer with a category | 422 `INVALID_ENUM_VALUE@/category_id` | Yes (client never sends it) |
| `recurring_rule_id` member; `memo` member missing | 422 `UNKNOWN_FIELD`; 422 `REQUIRED@/memo` | Yes: the client always sends exactly the 11 members |
| id `not-a-uuid`; a UUIDv4 | 422 `INVALID_ID` (both) | Yes (client ids are UUIDv7) |
| date 1899-12-31 / 3000-01-01 / 2026-02-30 | 422 `OUT_OF_RANGE@/date`; `DATE_NOT_CALENDAR_DATE@/date` | Yes (picker cannot produce them) |
| note 101, details 1,001; note of 100 emoji + details of 1,000 | 422 `OUT_OF_RANGE`; 201 | Yes (code points) |
| blank note `"   "` | 201, stored as `null` | Yes (client sends null) |
| currency `egp` | 422 `INVALID_CURRENCY@/currency` | n/a (client sends the wallet's) |
| unverified account | 403 `EMAIL_NOT_VERIFIED` | Yes |
| not a member (list or PUT); never-created id at v3 | 404 `NOT_FOUND` | Yes |
| bad bearer | 401 `UNAUTHENTICATED` | Yes → session ended |
| `DELETE` stale / without body / with `{version}` / again | 409 live `current`; 422 `REQUIRED@/version`; 200 tombstone; 200 same tombstone | Yes |
| `PUT` after deletion; `PATCH` restore with a stale version | 409 with tombstone `current` | Yes → "Deleted on another device" |
| `PATCH {deleted_at:null, version}` then `PUT` on the returned version | 200 v4, 200 v5; `PATCH` with another member → 422 `UNKNOWN_FIELD`; `PATCH` on a live one → 200 unchanged | Yes |
| restore of a transfer whose destination was deleted | 422 `INVALID_ENUM_VALUE@/counter_wallet_id` | Yes → "destination not available" |
| transfer Cash → Bank | 201; listed in both lists; Cash −500.00, Bank +500.00 | Yes |
| `PUT` of a transfer through the destination path | 200 | Yes (path rule when the source is not on the phone) |
| list `limit=2` | 3 pages via `next_cursor`/`has_more`; `limit=201` → 200 with `limit` 200 | Yes (client asks 200 and follows every cursor) |
| cursor with other filters; `filter[amount_minor]`; `search` blank or 101 chars | 400 `INVALID_CURSOR`; 400 `FILTER_FIELD_NOT_SUPPORTED`; 400 `INVALID_QUERY_PARAMETER` | n/a: the client sends no filter, sort or search |
| repeated filters `filter[date]=gte:…&filter[date]=lte:…&filter[kind]=in:…` | 200, ANDed | n/a (server rules match `TransactionFilter`) |
| B (viewer of Bank) expense in Bank | 403 `FORBIDDEN` | Yes → "no right here" |
| B transfer Cash (editor) → Bank (viewer); B Cash (owner) → Bank | 403 `FORBIDDEN` | Yes → "the other wallet" |
| B transfer B Cash → Cash (editor) | 201; listed in A's Cash with B's `created_by` | Yes → "From another wallet", added by the member |
| A (owner of Cash only) deletes that transfer | 403 while B Cash exists; 200 through Cash after B deleted B Cash | Yes (FR-013: offered, server decides) |
| edit of a transfer whose source wallet was deleted | 422 `INVALID_ENUM_VALUE@/counter_wallet_id` | Yes: the client offers no edit there |
| B files an expense in Cash under B's own category | 201; `GET /wallets/{Cash}/category-references` lists it (and A's own category) | Yes → partner category resolved |
| A saves B's expense keeping B's category | 200 | Yes |
| delete a category used by a transaction (A's and B's) | 409 `CATEGORY_IN_USE` | Yes (C5) |
| save / create with an archived own category | 200 / 201 | Yes |

No drift from `transactions.yaml` v1.7; **no backend bug found.** Behaviour worth knowing: a wallet's
`currency_locked` stays true after all its transactions are deleted, and an incoming transfer alone
locks it.

**Cleanup**: all QA transactions deleted and the three wallets of A deleted through the API (B Cash was
deleted during QA); both QA categories archived (still `CATEGORY_IN_USE` through tombstones); all three
sessions signed out. The accounts and the two `wallet_members` rows (now on deleted wallets) remain:
the backend has no account erasure yet and the database was not edited beyond the seeding insert.

## 6. Maestro flows (static review, not run)

| Risk | Finding | Action |
|---|---|---|
| Seed assumptions | `api.js` `createTransaction`/`createExpense` and `seed-transaction.yaml` defaulted every seeded transaction to **2026-10-02**: US5-S2-S3 ("This month" must keep "Expense now") would fail in any other month | **fixed**: default is today (host clock); explicit `DATE`s kept |
| Date picker day | US1-S5 tapped yesterday's day number on the month the picker opens on; on the 1st yesterday is in the previous month | **fixed**: on the 1st, tap Material 3's "Change to previous month" first |
| Navigation | US1-S7 pressed Back three times from the editor to reach Home, but the stack is Home → Wallets → wallet → Transactions → editor | **fixed**: four Backs, then wait for `home.addTransaction` |
| Dialog / sheet buttons by text | Category, destination and wallet sheets and dialog buttons are tapped by text (tags inside dialog windows are not exposed). "Delete transaction" is both the editor's button and the dialog's confirm; the dialog's window is top-most, the practice of specs 006–007, still unverified on a device | noted as a first-device-run risk |
| Selectors | every `id:` in the flows exists as a `testTag` (kind segments by `txEditor.kind.<wire>`, day headers by `transactions.day.<date>`) | none |
| Time zone | the host clock decides "today"/"yesterday"; the app uses the profile's zone (the device's until the profile is read). They agree when the profile zone is the device's | noted |

## 7. Commands

`./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64 --rerun-tasks` — BUILD SUCCESSFUL (1,293 host tests, 0 failures; Roborazzi verify, dev debug APK and iOS simulator compile green).
