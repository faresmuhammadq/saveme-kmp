# QA report: Categories (client, spec 007)

**Date**: 2026-10-02 · **Branch**: `007-categories` · **Mode**: no device (owner policy: no emulator or
Maestro runs; E2E deferred to the owner's phone) · **Backend**: local dev stack, `saveme-backend@f6d66eb`
(seeded; includes the "Home supplies" rename of the `household` default).

Evidence is a passing host test (`./gradlew :shared:testAndroidHostTest`), a Roborazzi screenshot
(`verifyRoborazziAndroidHostTest`), or a code path. Test classes are under
`shared/src/commonTest/kotlin/com/fares/saveme/feature/categories/` unless a path is given
(`ht/` = `shared/src/androidHostTest/kotlin/com/fares/saveme/`).

## 1. Acceptance scenarios

| Scenario | Evidence | Result |
|---|---|---|
| US1-S1 Expense (first) and Income views: own, then defaults, icon/colour/name | `CategoriesOverviewTest.eachKindListsOnlyItsOwnAndSplitsOutArchived`, `.defaultsFollowThePlatformOrderAndLeaveOutRetiredAndOtherKinds`; `CategoriesState.kind = Expense` (`CategoriesReducerTest`); `CategoryCapturedResponsesTest.theRealDefaultSetDecodes…` (19 expense / 7 income from the real server); screenshot `screen_categories_list_loaded_*` | PASS |
| US1-S2 Defaults in app language, switch offline at once; own names untouched | `CategoriesOverviewTest.defaultNamesFollowTheLanguageAndOwnNamesAreNeverTranslated`; `CategoriesOverviewPerformanceTest.aLanguageChangeReEmitsWithoutReadingTheDatabaseAgain`; captured test (Arabic "بقالة" from real data) | PASS |
| US1-S3 Retired default not listed | `CategoriesOverviewTest.defaultsFollowThePlatformOrderAndLeaveOutRetiredAndOtherKinds` (flow manual: not seedable) | PASS |
| US1-S4 Archived section after active, marked in words | `CategoriesOverviewTest.eachKind…SplitsOutArchived`, `.aPendingArchiveMovesTheCategoryToTheArchivedGroup`; `CategoriesReducerTest.rowsCarryCaptionsAndStatusMarks`; `ht/…/CategoriesAccessibilityTest.syncMarksDefaultAndArchivedAreText` | PASS |
| US1-S5 Waiting / Changed / Not saved marks | `CategoriesReducerTest.rowsCarryCaptionsAndStatusMarks`; `CategorySyncStateTest`; screenshot `screen_categories_list_loaded_*` (all four marks) | PASS |
| US1-S6 Other-device changes on foreground / pull; deleted elsewhere disappears | `CategoriesSyncCoordinatorTest.foregroundRefreshesWithoutForceOnlyWhenSignedIn`; `CategoriesViewModelTest.theKindSwitchReadsTheOtherViewAndPullRefreshes`; `CategoryRepositoryTest.anUnlistedSyncedCategoryIsRemovedButQueuedAndPlaceholderRowsStay` | PASS |
| US1-S7 Offline shows last known with "Updated" | `CategoriesServiceRefreshTest.offlineAndFailedKeepTheLocalDataAndDoNotStartTheFloor`; `CategoriesOverviewTest.theUpdatedTimeIsTheOlderStamp…`; `CategoriesViewModelTest.offlineAndFailedRefreshesKeepTheRows`; screenshot `screen_categories_list_offline_*` | PASS |
| US1-S8 No own categories: note + defaults | `CategoriesScreen` (`state.own.isEmpty()` → `categories.ownEmpty`); screenshot `screen_categories_list_no_own_*`; `CategoriesReducerTest.anEmptyStoredListIsNotLoadedUntilARefreshFinishes` (no note before the first refresh) | PASS |
| US2-S1 Create appears at once, online or offline, sent on its own | `CategoriesServiceCreateSaveTest.aValidCreateSavesAPlaceholderAndQueuesVersionZeroInTheUserScope`; captured test `aCreateAnsweredWith201StoresTheServerCopyAndClearsTheQueue` | PASS |
| US2-S2 Kind preselected from the view, switchable | `CategoryEditorViewModelTest.createModePreselectsTheKindAndSavesTheDraft`; `CategoryEditorReducerTest.theKindIsChosenWhileCreatingAndReadOnlyWhileEditing`; `CategoriesViewModelTest.createOpensTheEditorWithTheSelectedKindWhenVerified` | PASS |
| US2-S3 Kind shown, not editable | `CategoryEditorReducerTest.theKindIsChosenWhileCreatingAndReadOnlyWhileEditing`; `CategoriesServiceCreateSaveTest.saveUsesTheEditorsBaseVersionAndTheStoredKind`; `CategorySyncHandlerTest.editSendsPutWithTheBaseAndTheStoredKind` | PASS |
| US2-S4 Blank / over 50 (code points) refused next to the name | `CategoriesServiceCreateSaveTest.blankAndTooLongNamesAreRefusedAndFiftyCodePointsWithAnEmojiAreAccepted`; `CategoryEditorReducerTest.typingChecksTheNameAndClearsItsOwnError`, `.theNameIsCountedInCodePoints`; `CategoryEditorViewModelTest.aBlankNameCannotBeSaved` | PASS |
| US2-S5 Duplicate names accepted | `CategoriesServiceCreateSaveTest.duplicateNamesAreAccepted` | PASS |
| US2-S6 Clash: both values side by side, Keep mine / Use theirs, nothing applied before | `CategorySyncStateTest.aLiveClashCarriesTheOtherDevicesCopy`; `CategoryEditorReducerTest.aClashListsOnlyTheDifferingFields…`; `CategoryEditorViewModelTest.clashAndRefusalChoicesCallTheirDomainActions`; captured test `aStaleSaveBecomesAClashWithTheOtherDevicesValues` (real 409, then keep mine rebases on v2, use theirs stores the server copy) | PASS (gap closed) |
| US2-S7 Deleted elsewhere: only "Accept the deletion" | `CategorySyncStateTest.aTombstoneClashIsDeletedElsewhere`; `CategoryEditorReducerTest.aClashListsOnlyTheDifferingFieldsAndATombstoneListsNone`; captured test `aSaveAfterADeletionElsewhereOffersOnlyAcceptingIt` (real 409 with tombstone `current`); `CategoryEditorViewModelTest.aRemovalThePersonChoseClosesWithoutTheGoneMessage` | PASS (fix, see §4) |
| US2-S8 Unverified: verify prompt, nothing queued | `CategoriesServiceCreateSaveTest.anUnverifiedPersonQueuesNothing`; `CategoriesViewModelTest.anUnverifiedPersonGetsTheVerifyPrompt`; `CategoryEditorViewModelTest.serverFieldErrorsAndTheVerifyPromptComeBackFromCreate` | PASS |
| US2-S9 Defaults have no edit/archive/delete | `CategoriesViewModelTest.onlyOwnRowsOpenTheEditorAndDefaultsDoNot`; `CategoriesServiceCreateSaveTest.aDefaultsIdIsNeverQueued`; `CategoriesServiceArchiveDeleteTest.aDefaultCanNeverBeDeleted` | PASS |
| US3-S1 Archive: moves to Archived, out of the choice, sent on its own | `CategoriesServiceArchiveDeleteTest.archiveQueuesTheShownDraftWithArchiveTrueOnTheRowsVersion`; `CategoryChoicesTest.neverArchivedRetired…`; `CategorySyncHandlerTest.anArchiveOnlyChangeSendsNoPutAndArchivesWithTheBase` | PASS |
| US3-S2 Archive confirmation copy | `CategoryEditorViewModelTest.archiveIsOfferedForActiveOnlyAndConfirmedBeforeItRuns`; `ht/…/CategoryStringsTest`; screenshot `screen_categories_dialog_archive_*` | PASS |
| US3-S3 In use: no Delete, note shown | `CategoriesServiceArchiveDeleteTest.deleteIsOfferedOnlyWhenNothingUsesTheCategory`; `CategoryEditorViewModelTest.deleteIsOfferedPerTheRuleElseTheInUseNoteShows`; captured `theRealListDecodesInUseArchivedAndPaging` (real `in_use: true` after a transaction); screenshot `screen_categories_editor_in_use_note_*` | PASS |
| US3-S4 Delete confirmation names it, says final | `CategoryEditorViewModelTest.confirmingArchiveAndDeleteRunThemAndGoBack`; `ConfirmCard` (`category_delete_title` with the name); screenshot `screen_categories_dialog_delete_*` | PASS |
| US3-S5 Delete disappears at once; never-sent sends nothing | `CategoriesServiceArchiveDeleteTest.deletingANeverSentCategoryRemovesItAndQueuesNothing`, `.aWaitingDeletionHidesTheCategoryAndAClashOrRefusalShowsItAgain`; captured `aConfirmedDeletionRemovesTheCategoryAndAStaleOneClashes` (real tombstone 200) | PASS |
| US3-S6 In-use refusal: "Not deleted", Archive instead / Keep | `CategoriesServiceArchiveDeleteTest.archiveInsteadTurnsTheRefusedDeletionIntoAWaitingArchiveOnTheSameBase`, `.keepTheCategoryDropsTheDeletionAndMarksItInUse`; `CategoryEditorViewModelTest.anInUseRefusalOffersArchiveInsteadAndKeep`; captured `anInUseDeletionComesBackMarkedNotDeleted` (real 409 `CATEGORY_IN_USE`) | PASS |
| US3-S7 Delete clash: Delete anyway / Keep | `CategoriesServiceArchiveDeleteTest.aDeleteClashOffersDeleteAnywayOrKeepAndATombstoneOnlyAcceptance`; screenshot `screen_categories_editor_delete_clash_*`; captured `aConfirmedDeletion…AStaleOneClashes` (real 409 on a stale DELETE) | PASS |
| US4-S1 Choice: own active + active defaults of the kind, two groups | `CategoryChoicesTest.ownActiveByNameThenActiveDefaultsByPosition`, `.neverArchivedRetiredOtherKindUnknownKindOrHiddenOnes` | PASS |
| US4-S2 Current archived/retired stays listed and selected | `CategoryChoicesTest.theCurrentCategoryStaysListedWhateverItsState`, `.aCurrentCategoryThatIsAlreadyChoosableIsNotListedTwice` | PASS |
| US4-S3 Display own / default (language) / archived | `CategoryResolverTest.resolvesInTheDocumentedOrder`, `.archivedOwnAndRetiredDefaultsStillResolve`, `.defaultNamesFollowTheLanguageAndOthersAreNeverTranslated` | PASS |
| US4-S4 Partner category shown read-only, not choosable except current | `CategoryResolverTest.resolvesInTheDocumentedOrder`; `CategoryChoicesTest.neverArchived…` (no partner) and `.theCurrentCategoryStaysListedWhateverItsState` (partner current) | PASS |
| US4-S5 Unknown id: placeholder + fetch the wallet's references | `CategoryResolverTest.theServiceResolvesFromThePhonesCopyAndAMissAsksForThatWalletsReferencesOnce`; `CategoryReferencesRefreshTest`; screenshot of the "Category unavailable" row (`ht/designsystem/ComponentScreenshotTest`) | PASS |

**29 scenarios: 29 PASS** (US1-S3 has no E2E flow by design; device E2E for all flows deferred).

## 2. Edge cases

| Edge case | Evidence | Result |
|---|---|---|
| Create, edit, delete offline before any sync: nothing sent | `CategoriesServiceArchiveDeleteTest.deletingANeverSentCategoryRemovesItAndQueuesNothing` (one queue row per category; the edit folds into it) | PASS |
| Edit with a waiting archive keeps the archive | `CategoriesServiceCreateSaveTest.anEditKeepsAWaitingArchive`; `CategorySyncHandlerTest.editAndArchiveSendPutThenArchiveWithThePutsVersion` | PASS |
| Archived category stays editable, edits sync, stays out of the choice | `CategoriesServiceCreateSaveTest.anArchivedCategoryStaysEditableArchivedAndOutOfTheChoice` (**added**) | PASS (gap closed) |
| Not found refusal: "Not saved", discard, next refresh removes | `CategorySyncStateTest.everyRefusalCodeHasItsReasonAndKeepsTheRequestId`; `CategoryRepositoryTest.anUnlistedSyncedCategoryIsRemoved…` | PASS |
| Stale "verified": Not saved + Try again / Discard | `CategoryEditorViewModelTest.notVerifiedRefusalOffersTryAgain…`; captured `anUnverifiedAddressAndAnInvalidIdAreRefusalsWithTheirReasons` (real 403) | PASS |
| Unknown icon / colour: neutral display, preserved on save | `CategoriesServiceCreateSaveTest.anUnknownIconAndColourArePreserved`; `CategorySyncHandlerTest.anUnknownIconAndColourAreSentBackUnchanged`; `CategoryIconsTest`, `CategoryColorsTest`; captured `anUnknownIconKeyTheServerAcceptedIsKeptOnSave` (real `zzz_unknown` / `#ABCDEF`) | PASS |
| Unknown kind listed nowhere, still resolves | `CategoriesOverviewTest.anUnknownKindIsListedNowhere`; `CategoryResolverTest.anUnknownKindStillResolves`; `CategoryModelTest` | PASS |
| Empty / unread default set | `CategoriesOverviewTest.defaultsMissingWhenTheCachedSetIsEmpty`; screenshot `screen_categories_list_defaults_missing_*` | PASS |
| Retired default still shown on old transactions | `CategoryResolverTest.archivedOwnAndRetiredDefaultsStillResolve` | PASS |
| Category disappears under the editor | `CategoryEditorViewModelTest.goneWhileEditingClosesWithAMessageOnceAndBack`, `.aMissingCategoryClosesBackWithAMessage`; a row with a waiting change is never dropped by a refresh (`CategoryRepositoryTest`) | PASS |
| Wallet deleted / access lost: references removed | `CategoryReferencesRefreshTest.aWalletDeletionRemovesItsReferences`, `.aNotFoundRemovesTheWalletsReferences`; captured `aWalletsReferencesAreStoredAndANotFoundRemovesThem` (real 404) | PASS |
| Duplicate offline creates | UUIDv7 ids (`newCategoriesCarryAClientMadeUuidV7`) + duplicates allowed | PASS |
| Very many (paging, 200 + 26) | `CategoryRepositoryTest.refreshOwnMergesOnlyWhenEveryPageSucceeds`; `CategoriesOverviewPerformanceTest`; captured `theRealListDecodes…AndPaging` (real `next_cursor`) | PASS |
| Empty choice says so | `CategoryChoicesTest.nothingToOfferIsEmpty` | PASS |
| Sign-out empties the three tables | `ht/feature/account/SignOutWipeTest` | PASS |

## 3. Functional requirements

| FR | Evidence | Result |
|---|---|---|
| FR-001 local copy | layering test (UI imports no data), overview/choice/resolver tests over DAOs | PASS |
| FR-002 view order and marks | `CategoriesOverviewTest`, `CategoriesReducerTest` | PASS |
| FR-003 default names by language | overview, resolver, choices language tests | PASS |
| FR-004 refresh triggers, offline, failed, loading | coordinator, refresh, list VM tests | PASS |
| FR-005 merge rules | `CategoryRepositoryTest` | PASS |
| FR-006 kind + name 1–50 code points, icon/colour sets | create/save, editor reducer, `CategoryIconsTest`, `CategoryColorsTest` | PASS |
| FR-007 local-first, user scope, base version, UUIDv7 | `CategoriesServiceCreateSaveTest`, `editModeStartsFromWhatIsShownAndSavesOnTheFirstSeenVersion` | PASS |
| FR-008 kind immutable, `IMMUTABLE_FIELD` at `/kind` next to kind | handler + copy tests; captured `realFieldRefusalsLandOnTheirFields` (real 422) | PASS |
| FR-009 field refusals by code | `CategoryCopyTest`; captured `realFieldRefusalsLandOnTheirFields` (`OUT_OF_RANGE`, `INVALID_TYPE` at `/icon` and `/color`, `UNKNOWN_FIELD`) | PASS |
| FR-010 unknown icon/colour preserved | see edge cases | PASS |
| FR-011 defaults read-only | `aDefaultsIdIsNeverQueued`, `aDefaultCanNeverBeDeleted`, list VM | PASS |
| FR-012 / FR-013 clashes; deletion final | sync-state, editor, archive/delete, captured tests; `canRestore == false` | PASS |
| FR-014 refusals by code | `CategorySyncStateTest`, `CategoryCopyTest`, captured | PASS |
| FR-015 archive | archive/delete service tests, handler | PASS |
| FR-016 delete rule, tombstone | archive/delete service tests | PASS |
| FR-017 in-use refusal choices | archive/delete service + editor tests, captured | PASS |
| FR-018 choice list | `CategoryChoicesTest` | PASS |
| FR-019 resolution order | `CategoryResolverTest` | PASS |
| FR-020 references refresh | `CategoryReferencesRefreshTest`, `CategoryReferenceRepositoryTest` | PASS |
| FR-021 verification gate | create/save + list VM tests | PASS |
| FR-022 no plan gate | `categoriesAreNotGatedByThePlan` | PASS |
| FR-023 sign-out | `SignOutWipeTest` | PASS |
| FR-024 copy, glossary, RTL | `CategoryStringsTest`, AR screenshots | PASS |
| FR-025 accessibility | `CategoriesAccessibilityTest`, `CategoryColorsTest` (≥ 3:1), 2× screenshots | PASS |
| FR-026 `CATEGORY_IN_USE` code | `ProblemDetailsTest`, `SyncEngineRefusalTest`, contract test; real 409 decodes to it | PASS |

**26 FRs: 26 PASS.**

## 4. Gaps found and closed

1. **Edit-clash choices at the service level with real data** (US2-S6): only covered by editor fakes and the delete-clash test. Closed by `CategoryCapturedResponsesTest.aStaleSaveBecomesAClashWithTheOtherDevicesValues` (keep mine rebases on the server's version 2; use theirs stores "Coffee beans" and empties the queue).
2. **Archived category stays editable** (edge case): no test. Closed by `CategoriesServiceCreateSaveTest.anArchivedCategoryStaysEditableArchivedAndOutOfTheChoice`.
3. **Bug: "This category is no longer available." after the person's own choice.** "Accept the deletion", "Delete anyway" and "Discard" (of a never-sent create) remove the category from the phone, and the editor reported that as "gone elsewhere" with a message. Fixed in `CategoryEditorViewModel` (`resolvedByPerson`): the editor closes quietly; test `CategoryEditorViewModelTest.aRemovalThePersonChoseClosesWithoutTheGoneMessage`.
4. **Flaky `ProfileSyncerTest.aLocalSaveIsStoredAtOnceAndPushedWithTheServerVersion`** (spec 002 test, failed 2 of 8 isolated reruns): `saveLocally` starts the send at once, the MockEngine answers on Ktor's own thread, and the PUT could clear the waiting edit before the test read it. The mock now holds the PUT on a `CompletableDeferred` until the local copy is checked; 15 of 15 reruns pass.
5. **Contract decoding only against hand-written fixtures.** Closed by `CategoryCapturedResponsesTest` (12 tests) over 19 responses captured from the live backend (`data/CapturedCategoryResponses.kt`).

## 5. Contract QA against the live backend

Throwaway accounts `qa007-cat@example.test` (verified) and `qa007-unv@example.test` (unverified),
made with `UserFactory` through `dev tinker`; requests with curl, the app's headers
(`X-Client-Platform`, `X-Client-Version`, `Accept-Language`, bearer credential) and the exact bodies
`CategoryApi` / `CategoryReferenceApi` build.

| Call (as the app sends it) | Answer | Matches the client |
|---|---|---|
| `GET /platform-categories` | 200, 26 items (19 expense, 7 income, none retired), one page (`next_cursor` null, `limit` 26); `household` → en "Home supplies", ar "مستلزمات البيت"; 24 distinct icon keys | Yes: decodes; every icon key resolves in `CategoryIcons`, every colour is valid `#RRGGBB` and passes the tint rule |
| `GET /categories?limit=200` (and `limit=1`) | 200; extra member `user_id` ignored; `next_cursor` + `has_more` paging | Yes |
| `PUT /categories/{id}` version 0 | 201; identical replay 200 unchanged | Yes |
| `PUT` stale version | 409 `VERSION_CONFLICT` with live `current` | Yes → Clash with the other device's values |
| `PUT` changed kind | 422 `IMMUTABLE_FIELD` at `/kind` | Yes → field error next to the kind |
| `PUT` 51-character name | 422 `OUT_OF_RANGE` at `/name` | Yes |
| `PUT` kind `transfer` | 422 `INVALID_ENUM_VALUE` at `/kind` | Yes |
| `PUT` with `in_use` member | 422 `UNKNOWN_FIELD` at `/in_use` | Yes (client never sends it; contract test asserts the exact five members) |
| `PUT` id `not-a-uuid` | 422 `INVALID_ID` | Yes → "Not saved. Reference: …" |
| `PUT` icon `zzz-unknown`, colour `#abc` | 422 `INVALID_TYPE` at `/icon` and `/color` (server patterns `^[a-z0-9_]{1,40}$`, `^#[0-9A-F]{6}$`) | Yes → generic field message; the client only offers known keys and uppercase accents |
| `PUT` icon `zzz_unknown`, colour `#ABCDEF` | 201 (any key matching the pattern is stored) | Yes: shown with the fallback icon and sent back unchanged |
| `PUT` unverified account | 403 `EMAIL_NOT_VERIFIED` | Yes |
| `POST /categories/{id}/archive` `{version}` | 200 with `archived_at`; stale version on an archived category → 200 unchanged | Yes |
| `PUT /wallets/{w}/transactions/{t}` with the category, then `GET /categories` | 201; category `in_use: true` | Yes |
| `DELETE /categories/{id}` body `{version}` while used | 409 `CATEGORY_IN_USE`, no `current` | Yes → "Not deleted: this category is in use" |
| `DELETE` unused, stale version | 409 `VERSION_CONFLICT` with live `current` | Yes → delete clash |
| `DELETE` unused | 200 tombstone (`deleted_at`); repeat → 200 same tombstone | Yes → row removed |
| `PUT` / `POST archive` after deletion | 409 with tombstone `current` | Yes → "Deleted on another device", only "Accept the deletion" |
| `DELETE` without a body | 422 `REQUIRED` at `/version` | Confirms the body is required (client always sends it) |
| `PUT` an id never created, version 3 | 404 `NOT_FOUND` | Yes → "no longer available" |
| `GET /wallets/{w}/category-references?limit=200` | 200; extra `created_at` ignored | Yes |
| same, unknown wallet | 404 `NOT_FOUND` | Yes → the wallet's references removed |

No drift from `categories.yaml`. The client has no fixture, golden or copy that hard-codes the
old "Household" name, so the rename needs no client change.

Observed backend behaviour worth knowing: a category stays `in_use` after the only transaction that
used it is deleted (the tombstone still counts), so it can then only be archived.

**Cleanup**: the QA transaction, wallet and the archived category were deleted through the API and
both sessions signed out. The "Used" category could not be deleted (`CATEGORY_IN_USE`, above) and
the two throwaway accounts remain: the backend has no account erasure yet, and the database was
not touched directly.

## 6. Maestro flows (static review, not run)

| Risk (plan.md) | Finding | Action |
|---|---|---|
| `scrollUntilVisible` on grids | Icon and colour grids are `FlowRow`s (not lazy) inside the editor's scrolling screen, so every cell exists and scrolling reaches it | none |
| Selected state | `US2-S2` asserted `selected: true` on a `SegmentedControl` option; options are `selectable(role = RadioButton)`, which Compose reports as checkable/checked (only `Role.Tab` maps to selected) | **fixed**: `checked: true` |
| Editor closing after save | Save, confirmed archive and confirmed delete close the editor; clash and refusal choices keep it open. `US3-S6 part3` waited for the list after "Archive instead" | **fixed**: wait for the choice to go, then `back` |
| | `US2-S7` pressed `back` after "Accept the deletion", but the editor closes itself when the category leaves the phone, so `back` would have left the categories screen | **fixed**: wait for `categories.list` instead |
| "Archive" / "Delete category" dialog vs row | The dialog's confirm button has the same text as the editor's row/button behind it, and tags inside dialog windows are not exposed. Accessibility lists windows top-most first, so a text match should find the dialog's button first (the spec 006 practice, also unverified on a device). Left as is | noted as a risk for the first device run |
| Copy in flows | Every asserted string matches `values/strings.xml` (curly apostrophes included); accent index 5 is `#1F7F8C`; first defaults are "Groceries" / "Salary" on the live server | none |
