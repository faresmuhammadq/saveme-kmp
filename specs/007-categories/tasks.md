---
description: "Task list for 007-categories"
---

# Tasks: Categories (client)

**Input**: Design documents from `specs/007-categories/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (categories-api-client.md, categories-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `f64e0f6` (spec 004 engine, spec 005 entitlements, spec 006 wallets; Room v3)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per screen and component; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator or Maestro runs in this feature. Flows are written under `.maestro/007-categories/`; every task that runs them is marked **deferred: E2E on owner's device**.

**Organization**: Phase 2 builds the storage, APIs, sync handler, repositories and design-system pieces every story uses. US1 (see categories) is the MVP; US2 adds create and edit; US4 adds the choice and display building blocks C6 needs; US3 adds archive and delete.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`
- Categories feature: `cm/feature/categories/…` (`fc/…`), tests `ct/feature/categories/…` (`tc/…`)

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: categories UI → categories domain → categories data; other features only through their `domain`), naming (P-11).
- Glossary (P-01): no identifier or copy uses "tag", "label", "bucket", "record", "entry" for a category or transaction; the entity is "Category" / "فئة"; nothing user-facing says "version", "tombstone", "category reference" or "system key" ("Reference: …" before a request id is the spec 006 wording and stays).
- Never log category names or payloads; log record id, outcome and request id only (P-09.2).
- Feature code never builds a `Color` from a category's hex; it passes the string to `designsystem` (P-07.2).
- **MVI screen recipe (P-16)**, as in specs 001–006: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure top-level `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas (never repositories), `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin (spec 006 lesson)**: anything the category handler depends on resolves `SyncEngine` lazily; `AppGraphTest` must stay green.
- **Merge hotspots** (plan.md): edits to `AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt`, `ApiErrorCode.kt`, `AppModules.kt`, `App.kt`, `Routes.kt`, `AppNavHost.kt`, Home, `AppIcons.kt` and the catalogue are additive only. New strings go in one append-only block at the end of each `strings.xml` under `<!-- Categories (spec 007) -->`.
- Every interactive element gets the `testTag` from contracts/ui-and-e2e.md; dialog buttons are tapped by text in flows.

---

## Phase 1: Setup

**Purpose**: confirm the prerequisites and create the package.

- [X] T001 Confirm on `007-categories` (from `main` `f64e0f6`): `AppDatabase` is version 3 with `MIGRATION_2_3` and `shared/schemas/…/3.json`; `SyncHandler.canRestore` and `Refusal.requestId` exist; `WalletRepository` resolves `WalletDeletionParticipant`s at call time and also on a refresh drop; `ScopeKind.User` exists; `ht/core/di/AppGraphTest.kt` exists; `run-e2e.sh` supports `network-off` / `network-on` and `.maestro/common/api.js` has slot B and `createWallet`. If any is missing, stop and report
- [X] T002 Create `fc/di/CategoriesModule.kt` with an empty `val categoriesModule = module { }` and add it to the feature module list in `cm/AppModules.kt` (hotspot: one list element)
- [X] T003 [P] Add the string block `<!-- Categories (spec 007) -->` with every key from contracts/ui-and-e2e.md "Copy" to the end of `values/strings.xml` and `values-ar/strings.xml` (Arabic: فئة / الفئات, Western digits, research R-20)
- [X] T004 [P] Add `CATEGORY_IN_USE(RetryClass.Never)` to `cm/core/network/ApiErrorCode.kt`; test in `ct/core/network/ProblemDetailsTest.kt` that a 409 `CATEGORY_IN_USE` problem decodes to it, and in the engine tests (`ct/core/sync/`) that the engine classifies it as `Refused` with the code kept (FR-026, research R-17)

---

## Phase 2: Foundational (blocking)

**Purpose**: domain types, wire clients, Room v4, the sync handler, repositories, design-system pieces, navigation.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T005 [P] Model tests in `tc/domain/model/CategoryModelTest.kt`: `CategoryKind` wire round-trip and unknown → null (kept as `kindWire`, never re-encoded differently); `CategoryDraft` ↔ `CategoryPayload` round-trip with `icon`/`color` as explicit nulls; `sameFieldsAs` ignores `archive`; `CategoryField.fromName` for `name`, `kind`, `icon`, `color` (data-model §2, §3)
- [X] T006 [P] Contract tests in `tc/data/CategoryApiContractTest.kt` (Ktor `MockEngine`, fixtures from `categories.yaml` examples and backend spec 011 QA evidence) covering every row of contracts/categories-api-client.md: method/path/body for list (cursor, `limit=200`), put (exact members, explicit nulls, no server-owned members), archive, delete (**body on DELETE**, JSON content type), platform list, references list; 200/201 decoding with an unknown member; 409 `VERSION_CONFLICT` with live and tombstone `current`; 409 `CATEGORY_IN_USE` without `current`; 422 field codes (`OUT_OF_RANGE@/name`, `IMMUTABLE_FIELD@/kind`, `INVALID_ENUM_VALUE@/kind`, `UNKNOWN_FIELD`), 422 `INVALID_ID`; 403 `EMAIL_NOT_VERIFIED` (publishes `ApiEvent.EmailNotVerified`); 404; 503 with `retry_after_seconds`; offline; 401 → `SessionEnded` (P-14.3)
- [X] T007 [P] Handler tests in `tc/data/CategorySyncHandlerTest.kt` with a fake `CategoryApi` (research R-04): create → PUT version 0; edit → PUT with base and the **stored** kind; archive-only → no PUT, archive with base; edit+archive → PUT then archive with the PUT's version; PUT failure never archives; delete → DELETE with base; unknown icon/colour sent back unchanged; `applyServerCopy` upserts a live category and removes a tombstone; `onDiscarded` removes a `version = 0` placeholder only; `refusalFields` maps the four pointers to plain field names; `canRestore == false`; `recordType == "category"`
- [X] T008 [P] DAO test in `ht/feature/categories/data/CategoryDaoTest.kt` (Robolectric, in-memory Room): own categories by account and kind; platform set replaced as a whole in one transaction; references replaced per wallet, unique `(wallet_id, category_id)`, delete by wallet; `deleteAll` on each DAO
- [X] T009 [P] Migration test in `ht/core/database/Migration3To4Test.kt`: a v3 file built from the exported v3 definitions (as `Migration2To3Test`) with a profile row, a wallet row and a `pending_change` row migrates to v4; all rows survive; the three new tables and indexes exist and Room validates the schema
- [X] T010 [P] Repository tests in `tc/data/CategoryRepositoryTest.kt` and `tc/data/CategoryReferenceRepositoryTest.kt` (fake APIs, DAO fakes): placeholder row for a create (version 0, `in_use` false); `refreshOwn` merges only when every page succeeds; an unlisted category with `version > 0` and no queue row is removed, one with a queue row and any `version = 0` row are kept (FR-005); `refreshDefaults` replaces the set (retired kept) only on success; `markInUse`; references `refresh(walletId)` replaces the set on a complete read, keeps it on failure, removes it on 404; `removeFor(walletId)`; `sync_scope` stamps `categories` / `platform_categories`
- [X] T011 [P] Extend the sign-out wipe test (`ht/feature/account/SignOutWipeTest.kt`) so sign-out empties `category`, `platform_category` and `category_reference` (FR-023)
- [X] T012 [P] Copy tests: `tc/ui/CategoryCopyTest.kt` (every `RefusalReason` and field error code maps to a category key, never a generic message, P-06.5) and `ht/feature/categories/ui/CategoryStringsTest.kt` modelled on `WalletStringsTest` (every key of the block exists in both languages; no "tag", "label", "bucket", "record", "entry", "version", "tombstone", "token" in English; no "وسم", "ملصق", "تصنيف", "نسخة" in Arabic; Western digits only)
- [X] T013 [P] Design-system tests: `ct/designsystem/CategoryColorsTest.kt` (valid `#RRGGBB` parsed; lowercase, short, null and garbage → neutral; for the 26 platform colours of backend `PlatformCategorySeeder` and the 8 `WalletAccents`, the icon tint reaches ≥ 3:1 against the container) and `ct/designsystem/CategoryIconsTest.kt` (all 24 keys resolve; unknown and null → the fallback; the key list equals the seeder's icon keys)

### Implementation

- [X] T014 [P] Domain models in `fc/domain/model/` (`Category`, `DefaultCategory`, `CategoryReference`, `CategoryDraft`, `CategoryView`, `SyncState`, `RefusalReason`, `CategoryKind`, `CategoryField`, `CategorySource`, `CategoryDisplay`, `CategoryDisplays`, `CategoryChoices`, `CategoriesOverview`, result types) per data-model §3
- [X] T015 [P] DTOs in `fc/data/dto/CategoryDtos.kt` and the queue payload `fc/data/dto/CategoryPayload.kt` per data-model §2 (explicit nulls for `icon` and `color`); nothing in `fc/data` imports `fc/domain` (P-10.6)
- [X] T016 `fc/data/CategoryApi.kt` (list, put, archive, delete with `contentType(Json)` + `setBody`, listDefaults) and `fc/data/CategoryReferenceApi.kt` (listReferences) on `ApiClient` (`callPage` for lists) per contracts/categories-api-client.md (depends on T015)
- [X] T017 [P] Room entities and DAOs in `fc/data/local/` (`CategoryEntity`, `PlatformCategoryEntity`, `CategoryReferenceEntity`, `CategoryDao`, `PlatformCategoryDao`, `CategoryReferenceDao`) per data-model §1
- [X] T018 `cm/core/database/AppDatabase.kt` version 4 with the three entities and DAO accessors; `MIGRATION_3_4` in `cm/core/database/Migrations.kt`; `buildAppDatabase` adds it; build once to export `shared/schemas/com.fares.saveme.core.database.AppDatabase/4.json` (depends on T017)
- [X] T019 `cm/core/database/DatabaseCleaner.kt`: delete the three category tables (depends on T018)
- [X] T020 [P] `fc/domain/PendingCategoryUses.kt` (interface + `NoPendingCategoryUses`); bind it in `categoriesModule` (contracts/categories-domain.md)
- [X] T021 `fc/data/CategoryRepository.kt`: Room reads as flows joined with the engine's `observe("category", id)` into `CategoryView` inputs, placeholder insert, server-copy upsert/remove, `markInUse`, `refreshOwn` (paged `limit=200`, all-or-nothing merge), `refreshDefaults` (replace set); `hasQueuedChange` resolves the engine lazily (research R-03, R-08, R-19) (depends on T016, T018)
- [X] T022 `fc/data/CategoryReferenceRepository.kt` (`refresh(walletId)`, `removeFor(walletId)`, observe all) and `fc/domain/CategoryReferencesCleanup.kt` bound as `WalletDeletionParticipant` in `categoriesModule` (research R-09) (depends on T016, T018)
- [X] T023 `fc/data/CategorySyncHandler.kt` per research R-04 and register `single { … } bind SyncHandler::class` in `categoriesModule` (depends on T021)
- [X] T024 `fc/domain/CategoriesService.kt` skeleton (`DefaultCategoriesService`): `observeCategory`, `refresh(force)` with the 60 s floor and `engine.syncNow()` on force, the `Flow<AppLanguage>` from `AppLanguageStore` (research R-12), the user scope from `SessionManager`; other members added per story; register in `categoriesModule` (depends on T021, T022)
- [X] T025 `fc/domain/CategoriesSyncCoordinator.kt` (sign-in forced, foreground with floor, as `WalletsSyncCoordinator`) with `tc/domain/CategoriesSyncCoordinatorTest.kt`; `single(createdAtStart = true)` in `categoriesModule`; `onForeground()` call in `cm/App.kt` (hotspot: one call) (depends on T024)
- [X] T026 Extend `ht/core/di/AppGraphTest.kt`: the real graph starts with `categoriesModule`; the engine's handlers include `category`; `getAll<WalletDeletionParticipant>()` includes `CategoryReferencesCleanup` (research R-19) (depends on T023, T025)
- [X] T027 [P] `fc/ui/CategoryErrorCopy.kt` (refusal reasons, field errors, status marks) to pass T012
- [X] T028 [P] Design system (research R-13): new stroke icons in `cm/designsystem/icon/AppIcons.kt` (named for what they draw, `repeat` → `Cycle`, nothing named `Tag`/`Label`); `cm/designsystem/icon/CategoryIcons.kt` (24 keys, fallback `AppIcons.More`); `cm/designsystem/tokens/CategoryColors.kt` (`of(hex)` → swatch, container, tint ≥ 3:1; neutral fallback) to pass T013. Check the "Save Me Mobile Design System" canvas first; list the new icons and the tint rule in `summary.md` for the owner's design review (P-07.3)
- [X] T029 [P] Components in `cm/designsystem/component/Categories.kt`: `CategoryTile`, `CategoryRow` (status text + icon, "Default"/"Archived" captions, optional selection), `CategoryChoiceSheet` (titled groups, current item first, radio semantics, check mark, empty message slot); catalogue entries in `cm/designsystem/catalog/CatalogSections.kt`; screenshots in `ht/designsystem/ComponentScreenshotTest.kt` (EN, AR RTL, 2×); existing baselines unchanged (depends on T028)
- [X] T030 Navigation: `CategoriesKey`, `CategoryEditorKey(categoryId: String? = null, kind: String = "expense")` in `cm/navigation/Routes.kt`; entries in `cm/navigation/AppNavHost.kt` passing `onRequireVerification` (existing `EmailNotVerifiedSheet`) — Routes are wired as each screen lands (research R-15)
- [X] T031 Home row: `HomeIntent.CategoriesClicked`, `HomeEffect.NavigateCategories`, a "Categories" `AppListItem` tagged `home.categories` under "Wallets" in `cm/feature/auth/ui/home/{HomeContract,HomeViewModel,HomeScreen,HomeRoute}.kt`; extend the Home reducer/VM tests; re-record the Home screenshots on purpose (depends on T030)

**Checkpoint**: storage, APIs, handler, repositories and navigation ready; `./gradlew :shared:testAndroidHostTest` green for T004–T013 and T026.

---

## Phase 3: User Story 1 - See my categories and the defaults (Priority: P1) 🎯 MVP

**Goal**: the categories screen shows own, default (in the app language) and archived categories per kind, from the phone, refreshed on its triggers.

**Independent Test**: sign in as a new person; open Categories: 19 expense defaults in platform order, 7 income defaults, no own categories; switch to Arabic: defaults renamed at once offline.

### Tests for User Story 1

- [X] T032 [P] [US1] Overview tests in `tc/domain/CategoriesOverviewTest.kt`: per-kind grouping; own (active and archived) by name case-insensitive, ties by creation; defaults by `position`; retired defaults excluded; categories hidden by a waiting deletion excluded; unknown kind excluded; default names in Arabic and English, own names untouched; `defaultsMissing` when the cached set is empty; `updatedAt` is the older stamp; `loaded` false before the first refresh (FR-002, FR-003, FR-004)
- [X] T033 [P] [US1] Refresh tests in `tc/domain/CategoriesServiceRefreshTest.kt`: both reads run; 60 s floor unless forced; `Offline` and `Failed` keep local data; force calls `engine.syncNow()` (FR-004, FR-005)
- [X] T034 [P] [US1] List reducer and store tests in `tc/ui/list/CategoriesReducerTest.kt` and `CategoriesViewModelTest.kt`: `Opened` forces a refresh; `KindChosen`; `Refresh` (pull); offline and failed states; `CreateClicked` → `OpenEditor(null, kind)` when verified, `RequireVerification` otherwise; `CategoryClicked` opens own categories only (defaults are not clickable)

### Implementation for User Story 1

- [X] T035 [US1] `CategoriesService.observeOverview(kind)` in `fc/domain/CategoriesService.kt` (depends on T024)
- [X] T036 [US1] Categories screen in `fc/ui/list/` (`CategoriesContract`, `CategoriesViewModel`, `CategoriesRoute`, `CategoriesScreen` + preview): `SegmentedControl`, three sections, notes, `SyncBanner`, refresh-failed alert, skeleton, FAB, pull to refresh, "Updated …"; test tags; register the ViewModel in `categoriesModule`; wire `CategoriesKey` in `AppNavHost` (depends on T029, T030, T035)
- [X] T037 [P] [US1] Screenshots in `ht/feature/categories/ui/CategoriesScreenshotTest.kt` (EN, AR RTL, 2×): loaded with own + defaults + archived and each row status (waiting, changed, not saved, not deleted), no own (note), defaults missing, offline with "Updated", loading
- [X] T038 [US1] E2E plumbing: `listCategories`, `createCategory`, `saveCategory`, `archiveCategory`, `deleteCategory` (`http.request`, `DELETE`, JSON body), `listPlatformCategories`, `createExpense` actions in `.maestro/common/api.js`; `.maestro/common/open-categories.yaml` (research R-18)
- [X] T039 [US1] Write flows `.maestro/007-categories/US1-S1-…`, `S2` (Arabic), `S4`, `S6` (slot B rename + pull), `S7` (two parts, `network-off`), `S8` per contracts/ui-and-e2e.md; US1-S3 (retired default) recorded as manual (`not-seedable`: the platform set is seeded server-side) and covered by T032; US1-S5 covered by the US2 offline flow
- [ ] T040 [US1] Run `/speckit-e2e 007-categories` for US1 — **deferred: E2E on owner's device**

**Checkpoint**: the MVP — categories and defaults visible, in both languages, offline.

---

## Phase 4: User Story 2 - Create and edit my own categories, even offline (Priority: P1)

**Goal**: create and edit own categories locally with clashes and refusals shown on the category.

**Independent Test**: offline, create "Coffee" (restaurant, teal): listed with "Waiting to sync"; online: mark clears and the server lists it.

### Tests for User Story 2

- [X] T041 [P] [US2] Service tests in `tc/domain/CategoriesServiceCreateSaveTest.kt`: verified gate (nothing queued when not verified); name blank / 51 code points refused, 50 code points with an emoji accepted (counted in code points, FR-006); placeholder v0 + `engine.enqueue("category", id, SyncScope(User, userId), 0, …)` with a UUIDv7; save uses the editor's base version and the stored kind; duplicate names accepted; unknown icon/colour preserved; a platform id is never enqueued (FR-011); no entitlement check is made (the service has no `Entitlements` dependency, FR-022)
- [X] T042 [P] [US2] Sync-state tests in `tc/domain/CategorySyncStateTest.kt`: queue row → `CategoryView` (`Sending` → Waiting; Clash with live and tombstone `current`; Refused reasons from codes, incl. `CATEGORY_IN_USE` → InUse and `requestId`; field map)
- [X] T043 [P] [US2] Editor reducer and store tests in `tc/ui/editor/CategoryEditorReducerTest.kt` and `CategoryEditorViewModelTest.kt`: create mode (kind preselected from the key, switchable), edit mode (kind read-only, base version from the first emission), field errors, `canSave`, clash choices (edit vs live, anything vs deleted → only accept), refusal choices (not verified → Try again; gone; field; other with reference), `RequireVerification`, gone → message + back

### Implementation for User Story 2

- [X] T044 [US2] `CategoriesService.create`, `save`, `keepMine`, `useTheirs`, `tryAgain`, `discard` in `fc/domain/CategoriesService.kt` (research R-07, R-14) (depends on T024)
- [X] T045 [US2] Category editor in `fc/ui/editor/` (`CategoryEditorContract`, `CategoryEditorViewModel`, `CategoryEditorRoute`, `CategoryEditorScreen` + preview): kind, name, icon and colour `ChoiceGrid`s, Save, status `InlineAlert` with side-by-side lines and choices; test tags; register in `categoriesModule`; wire `CategoryEditorKey` in `AppNavHost` (depends on T029, T030, T044)
- [X] T046 [P] [US2] Editor screenshots in `ht/feature/categories/ui/CategoriesScreenshotTest.kt` (EN, AR RTL, 2×): create, edit, waiting, clash, deleted elsewhere, refused with field error, refused other with reference
- [X] T047 [US2] Write flows `.maestro/007-categories/US2-S1-create-online`, `US2-S1-create-offline` (three parts), `S2`, `S3`, `S4`, `S5`, `S6` (keep mine, use theirs), `S7`, `S8` (`unverified-user`), `S9` per contracts/ui-and-e2e.md
- [ ] T048 [US2] Run `/speckit-e2e 007-categories` for US2 — **deferred: E2E on owner's device**

**Checkpoint**: own categories can be created and edited offline; clashes and refusals are visible.

---

## Phase 5: User Story 4 - Choose a category for a transaction, and see any transaction's category (Priority: P1)

**Goal**: the domain choice list, the offline resolver and on-demand references, plus the choice sheet component C6 will place on its screens.

**Independent Test**: domain tests over own, default, archived, retired, partner and unknown ids; component screenshots of the choice sheet. End to end at C6's checkpoint (SC-007).

### Tests for User Story 4

- [X] T049 [P] [US4] Choice tests in `tc/domain/CategoryChoicesTest.kt`: own active of the kind by name, then active defaults by position; no archived own, retired default, other kind, unknown kind, partner category or category hidden by a waiting deletion; the current category (archived own, retired default, partner) listed first and selected, marked archived; empty result when nothing is available (FR-018, Edge Cases)
- [X] T050 [P] [US4] Resolver tests in `tc/domain/CategoryResolverTest.kt`: order own → default → the wallet's references → Unavailable; archived own and retired default still resolve; a waiting rename shows the new name; default name by language, own and partner names untouched; unknown kind still resolves; a miss with a wallet asks for that wallet's references once (FR-019)
- [X] T051 [P] [US4] References tests in `tc/domain/CategoryReferencesRefreshTest.kt`: per-wallet 60 s floor, `force` bypasses it, misses de-duplicated by the floor, 404 removes, `CategoryReferencesCleanup.onWalletDeleted` removes the wallet's rows (FR-020)

### Implementation for User Story 4

- [X] T052 [US4] `fc/domain/CategoryChoicesBuilder.kt` and `CategoriesService.observeChoices` (depends on T024)
- [X] T053 [US4] `fc/domain/CategoryResolver.kt`, `CategoriesService.observeDisplays(walletId)` (one combined snapshot, research R-10) and `refreshReferences(walletId, force)` (depends on T022, T024)
- [X] T054 [P] [US4] Catalogue states and screenshots for `CategoryChoiceSheet` (current archived item, partner current, empty "no categories yet") and `CategoryRow` with the "Category unavailable" placeholder in `ht/designsystem/ComponentScreenshotTest.kt` (depends on T029)

**Checkpoint**: C6 can offer and show categories through `feature.categories.domain` and `designsystem` only.

---

## Phase 6: User Story 3 - Archive or delete a category (Priority: P2)

**Goal**: archive (one-way) and delete (only while unused, final) offline, with the in-use refusal handled.

**Independent Test**: archive "Coffee": moves to Archived, no longer offered by the choice; create and delete "Typo": gone from the phone and, once synced, from the server.

### Tests for User Story 3

- [X] T055 [P] [US3] Service tests in `tc/domain/CategoriesServiceArchiveDeleteTest.kt`: archive queues the shown draft with `archive = true` on `row.version`; delete allowed only when `!in_use`, not in `PendingCategoryUses`, and not a default (archived allowed); `enqueueDelete` with the user scope; a never-sent category removed with nothing queued; hidden while the delete waits, visible again on clash/refusal; `archiveInstead` turns the refused delete row into a waiting archive on the same base and marks `in_use`; `keepCategory` discards the row and marks `in_use`; delete clash choices (FR-015–FR-017)
- [X] T056 [P] [US3] Editor reducer and store tests (extend T043 files): Archive offered only for active; Delete offered per the rule, else the in-use note; confirm dialogs; in-use refusal → "Archive instead" / "Keep the category"; delete clash → "Delete anyway" / "Keep the category"; deletion confirmed → back to the list

### Implementation for User Story 3

- [X] T057 [US3] `CategoriesService.archive`, `delete`, `archiveInstead`, `keepCategory` in `fc/domain/CategoriesService.kt` (research R-06, R-07) (depends on T044)
- [X] T058 [US3] Editor UI: Archive row, Delete button or in-use note, confirmation `AppAlertDialog`s (archive body says one-way and stays on existing transactions), in-use refusal and delete-clash choices in `fc/ui/editor/` (depends on T045, T057)
- [X] T059 [P] [US3] Screenshots (EN, AR RTL, 2×): archived category in edit, in-use note, in-use refusal, delete clash, both confirmations
- [X] T060 [US3] Write flows `.maestro/007-categories/US3-S1`…`S7` per contracts/ui-and-e2e.md (S3 and S6 seed an expense through slot B `createExpense`; S5 never-sent in three parts; S6 in two parts with `network-off`)
- [ ] T061 [US3] Run `/speckit-e2e 007-categories` for US3 — **deferred: E2E on owner's device**

**Checkpoint**: all four stories work independently.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T062 [P] Arabic copy review of the categories block (فئة / الفئات, Western digits, RTL of names inside sentences, no banned words); list the term for owner review in `summary.md`
- [X] T063 [P] Layering test `ht/feature/categories/ui/CategoriesLayeringTest.kt` modelled on `WalletsLayeringTest`: `feature/categories/ui` imports no `data` package; `feature/categories/data` imports nothing from `feature/categories/domain` or `ui` and no other feature; `feature/categories` imports other features only through `…domain`; `feature/wallets` imports nothing from `feature/categories`; `designsystem` imports no feature (P-10)
- [X] T064 [P] Accessibility test `ht/feature/categories/ui/CategoriesAccessibilityTest.kt`: sync marks, "Default" and "Archived" exposed as text; icon/colour choices and icon-only buttons described; touch targets ≥ 48 dp; no clipped text at 2× on both screens and the choice sheet (FR-025, P-08)
- [X] T065 [P] Performance test `tc/domain/CategoriesOverviewPerformanceTest.kt`: building the overview and the choice list for 200 own + 26 defaults stays under 50 ms on the host, and a language change re-emits without touching the DAOs (SC-004, SC-005)
- [X] T066 Trace every spec §4 invariant to its passing test (list them in `summary.md`) (P-14.2)
- [X] T067 [P] `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS compiles)
- [X] T068 `./gradlew build allTests` and `:shared:verifyRoborazziAndroidHostTest` green; schema `4.json` committed with the change
- [X] T069 Rerun `speckit-contract-check`; confirm `categories.yaml` is unchanged since `e358537`; record drift as FRs or follow-ups in spec §5
- [ ] T070 Run quickstart.md manual checks 1–11 on the dev build; record deviations in plan.md "Implementation notes" — **deferred: E2E on owner's device**
- [ ] T071 Run the full `/speckit-e2e 007-categories` and write `specs/007-categories/e2e-report.md`; no `FAIL (app)` or `FAIL (env)` attributable to the contract — **deferred: E2E on owner's device**
- [X] T072 Update the C5 row in `specs/briefs/README.md` ("Backend state" built, "Client state" built)
- [X] T073 Write `specs/007-categories/summary.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (prerequisite gate); T002–T004 after.
- **Foundational (Phase 2)**: after Setup; blocks all stories. Inside: T014/T015/T017 → T016, T018 → T019; T021 → T023; T021 + T022 → T024 → T025 → T026; T028 → T029; T030 → T031.
- **US1 (Phase 3)**: after Phase 2.
- **US2 (Phase 4)**: after Phase 2; the editor is opened from the list (T036), so after US1 when staffed by one person.
- **US4 (Phase 5)**: after Phase 2 (domain + components only); independent of US1–US2.
- **US3 (Phase 6)**: after US2's editor (T045) and service (T044).
- **Polish (Phase 7)**: after all stories; T073 is last.

### User Story Dependencies

- US1: none beyond Foundation (MVP).
- US2: uses US1's list as its entry.
- US4: none beyond Foundation; C6 consumes it.
- US3: extends US2's editor.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T005–T013 all [P]; T014, T015, T017, T020, T027, T028 in parallel.
- US4 (T049–T054) can run beside US1/US2 by a second person (different files).
- Within each story the test tasks are [P]; screenshot tasks run beside the next story's tests.

## Parallel Example: User Story 1

```text
T032 CategoriesOverviewTest  |  T033 CategoriesServiceRefreshTest  |  T034 list reducer/store tests
then: T035 observeOverview  →  T036 Categories screen  →  T037 screenshots  →  T038/T039 flows  →  T040 (deferred)
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: defaults in both languages, offline list, host tests green.

### Incremental Delivery

1. US1 → US2 (own categories, offline, clashes).
2. US4 (what C6 needs to start).
3. US3 (archive, delete, in-use refusal).
4. Polish: contract recheck, summary; device E2E and quickstart deferred to the owner.
