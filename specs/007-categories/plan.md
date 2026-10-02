# Implementation Plan: Categories (client)

**Branch**: `007-categories` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-categories/spec.md`

## Summary

A new `feature.categories` context keeps the person's own categories, the platform's default set
and each wallet's category references in Room (schema v4) and renders two MVI screens from them:
the categories screen (Expense / Income views: own, defaults in the app language, archived) and
the category editor (create, edit, status with clash and refusal choices, archive, delete). Every
create, edit, archive and delete goes through the spec 004 sync engine via a
`CategorySyncHandler` (record type `category`, scope `SyncScope(User, userId)`,
`canRestore = false`): PUT for create/save, then POST archive when requested, DELETE with a JSON
body for deletion (offered only while unused; `CATEGORY_IN_USE` refusals offer "Archive instead").
Lists refresh on sign-in, foreground and screen open (wave 1). For C6/C7/C8 the domain exposes the
category choice list and an offline resolver (own → default → wallet reference → placeholder), and
`designsystem` gains category icons, a data-colour tile rule, `CategoryRow` and
`CategoryChoiceSheet`.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only: Compose Multiplatform 1.12.1 + Material 3, MVI base (`MviViewModel`), Navigation 3, Ktor 3.6.0, kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite, WorkManager (spec 004, Android), Kermit. No new dependency (research R-21).

**Storage**: Room `saveme.db` v4: new `category`, `platform_category`, `category_reference`; `MIGRATION_3_4` in `core/database/Migrations.kt`; exported `shared/schemas/…AppDatabase/4.json`. Queue rows in the existing `pending_change` (record type `category`, scope `user:<id>`).

**Testing**: kotlin-test, coroutines-test, Turbine; Ktor `MockEngine` contract tests; fakes for the APIs, the engine's DAO/scheduler, `PendingCategoryUses` and the language flow; Room DAO + migration 3→4 tests in `androidHostTest` (Robolectric, as spec 006); `AppGraphTest` (real Koin graph); Roborazzi screenshots (EN, AR RTL, 2× font); Maestro flows written, **runs deferred to the owner's device**.

**Target Platform**: Android minSdk 24 / targetSdk 37 (live API); iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: 200 own + 26 default categories rendered from Room in < 1 s (SC-005); language switch re-renders defaults in < 1 s with no I/O (SC-004); a save is one local transaction + one queue write (< 50 ms); a refresh is ⌈own/200⌉ + 1 GETs; references at most one read per wallet per minute.

**Constraints**:
- P-04 (every rule): local-first writes, base version on every send, clashes to the person, deletion as a queued tombstone; defaults and references are read-only.
- Deletion final (`canRestore = false`), archive one-way, `kind` immutable.
- Glossary banned synonyms ("tag, label, bucket", "record, entry") in code and copy (P-01).
- Data-driven colours rendered only through `designsystem` (P-07.2), icons ≥ 3:1 on their tile (P-08).
- Merge hotspots shared with C6 (below).

**Scale/Scope**: 3 tables + 1 migration, 2 API clients (6 calls), 1 sync handler, 1 domain service + resolver + coordinator, 2 screens, design-system additions (~20 icon paths, `CategoryIcons`, `CategoryColors`, `CategoryTile`, `CategoryRow`, `CategoryChoiceSheet`), ~60 string keys × 2 languages, 1 client error code, 29 acceptance scenarios (22 with planned flows; US4's five join C6's checkpoint).

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: all principles PASS or N-A on the spec alone. P-01 (banned
synonyms around "category"), P-04 (offline-first own categories) and P-02 (the missing
`CATEGORY_IN_USE` code) are central. No FAIL; proceed.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | `Category`, `CategoryKind` (income/expense), `CategoryReference`, "Default" for platform categories; no class or key named `Label`/`Tag`/`Bucket`/`Record`/`Entry` (`CategoryDisplay`, `AppIcons.Cycle` for the `repeat` key, R-13); copy keys in contracts/ui-and-e2e.md say "Category"/"فئة"; no "tombstone"/"version"/"reference" in UI |
| P-02 API contract fidelity | PASS | DTOs mirror categories.yaml (data-model §2) with explicit `null` for `icon`/`color`; unknown members ignored; outcomes branch on `code` (engine + `RefusalReason`, R-07); `CATEGORY_IN_USE` added to `ApiErrorCode` with retry class Never (R-17); headers from `HttpClientFactory`; retries owned by the engine |
| P-03 Money | N-A | No amounts are held or shown by this feature |
| P-04 Offline-first | PASS | Rule 1: screens, choice list and resolver read Room (R-03, R-10, R-11). Rule 2: create/edit/archive/delete are `engine.enqueue*` local writes (R-04, R-06). Rule 3: `Uuid.generateV7()`. Rule 4: base version on PUT/archive/DELETE; clashes shown with choices (R-07). Rule 5: deletion is a queued deletion; rows removed only on the server's tombstone or for never-sent creates (R-06). Defaults and references are not syncable records the phone writes (read-only reference data and projection) |
| P-05 Time | PASS | `archived_at`, `created_at`, refresh stamps as UTC `Instant`; "Updated …" through `formatDateTime` in the profile timezone |
| P-06 Localisation & RTL | PASS | ~60 keys in `values/` + `values-ar/`; default names switch by `AppLanguage` from server-sent `names.{ar,en}` (R-12), own names untouched; refusal copy by code, never `detail`; RTL screenshots |
| P-07 Design system only | PASS | Screens use `CategoryRow`, `CategoryTile`, `SegmentedControl`, `ChoiceGrid`, `InlineAlert`, `AppFab`, fields and dialogs; new visuals (icons, `CategoryColors`, tile, row, choice sheet) are added to `designsystem` first with catalogue entries and screenshots; feature code passes colour strings, never `Color` (R-13) |
| P-08 Accessibility | PASS | Sync marks, "Default" and "Archived" are text; icon/colour choices have content descriptions and 48 dp targets; selection by check mark; tile tint ≥ 3:1 (unit test over all 34 colours); 2× font screenshots of both screens |
| P-09 Security & privacy | PASS | No category names or payloads logged (the engine logs id and outcome); partner data limited to the reference's display fields; three tables cleared by `DatabaseCleaner`; queue rows tagged by account (spec 004) |
| P-10 Shared-first architecture | PASS | All in `commonMain`; `feature.categories.ui` imports only its own domain; domain imports `core`, `auth.domain`, `wallets.domain`; data imports neither domain nor ui (the queue payload `CategoryPayload` and field names live in data, research R-04 — stricter than spec 006); C6 reaches categories through `feature.categories.domain` + `designsystem` (R-16); the verify sheet through an `AppNavHost` callback; layering test extended |
| P-11 Naming | PASS | `feature.categories`, `CategoriesService`, `CategorySyncHandler`, `CategoriesKey`; no codename |
| P-12 Entitlements & billing | N-A | No plan feature gates categories (backend: no limits); no billing surface |
| P-13 Observability | PASS | Unexpected refusals show the server `request_id` ("Not saved. Reference: …", using `Refusal.requestId` from spec 006) |
| P-14 Quality gates | PASS | Contract tests per call and handled code; a test per spec §4 invariant; screenshot tests for new components and screens; migration test; `AppGraphTest`; no new dependency |
| P-15 Scope guard | PASS | No nesting (flat, `parent_id` not on the wire), no hide-a-default, no wave 2 feed/batch; nothing from the MVP exclusion list |
| P-16 MVI presentation | PASS | Two screens each with `Contract.kt` (State/Intent/Effect/Change + pure `reduce`), a store on `MviViewModel` taking domain lambdas, `…Route`, stateless `…Screen` + preview; reducer and store tests (data-model §5). `CategoryChoiceSheet` is a stateless component whose store is C6's |

No FAIL. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/007-categories/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── categories-api-client.md
│   ├── categories-domain.md
│   └── ui-and-e2e.md
├── checklists/          # requirements.md (specify), categories.md (checklist)
├── tasks.md             # /speckit-tasks
└── summary.md           # last task
```

### Source Code (repository root)

```text
shared/
├── schemas/com.fares.saveme.core.database.AppDatabase/4.json     # exported
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/database/{AppDatabase.kt, Migrations.kt, DatabaseCleaner.kt}   # v4, MIGRATION_3_4, clear 3 tables
    │   ├── core/network/ApiErrorCode.kt          # + CATEGORY_IN_USE (Never)
    │   ├── designsystem/
    │   │   ├── icon/AppIcons.kt                  # ~20 new stroke icons
    │   │   ├── icon/CategoryIcons.kt             # NEW 24 keys → icons, fallback
    │   │   ├── tokens/CategoryColors.kt          # NEW hex → swatch/container/tint (≥ 3:1)
    │   │   ├── component/Categories.kt           # NEW CategoryTile, CategoryRow, CategoryChoiceSheet
    │   │   └── catalog/CatalogSections.kt        # entries
    │   ├── feature/categories/                   # NEW
    │   │   ├── data/{CategoryApi, CategoryReferenceApi, CategoryRepository, CategoryReferenceRepository, CategorySyncHandler}.kt
    │   │   ├── data/dto/{CategoryDtos, CategoryPayload}.kt
    │   │   ├── data/local/{CategoryEntity, PlatformCategoryEntity, CategoryReferenceEntity, CategoryDao, PlatformCategoryDao, CategoryReferenceDao}.kt
    │   │   ├── domain/{CategoriesService, CategoriesSyncCoordinator, CategoryResolver, CategoryChoicesBuilder, PendingCategoryUses, CategoryReferencesCleanup}.kt
    │   │   ├── domain/model/{Category, DefaultCategory, CategoryReference, CategoryDraft, CategoryView, CategoryKind, CategoryField, CategoryDisplay, Results}.kt
    │   │   ├── ui/CategoryErrorCopy.kt
    │   │   ├── ui/list/{CategoriesContract, CategoriesViewModel, CategoriesRoute, CategoriesScreen}.kt
    │   │   ├── ui/editor/{CategoryEditorContract, CategoryEditorViewModel, CategoryEditorRoute, CategoryEditorScreen}.kt
    │   │   └── di/CategoriesModule.kt
    │   ├── feature/auth/ui/home/{HomeContract, HomeScreen, HomeRoute, HomeViewModel}.kt   # + Categories row (hotspot)
    │   ├── navigation/{Routes.kt, AppNavHost.kt}  # 2 keys + entries + verify callback
    │   └── {AppModules.kt, App.kt}                # categoriesModule; coordinator foreground hook
    ├── commonMain/composeResources/values{,-ar}/strings.xml   # append-only block "Categories (spec 007)"
    ├── commonTest/kotlin/com/fares/saveme/feature/categories/…   # contract, handler, repositories, domain, reducers, stores
    └── androidHostTest/kotlin/com/fares/saveme/
        ├── core/database/Migration3To4Test.kt
        ├── core/di/AppGraphTest.kt                       # extended
        ├── feature/categories/data/CategoryDaoTest.kt
        ├── feature/categories/ui/CategoriesScreenshotTest.kt
        └── designsystem/ComponentScreenshotTest.kt
.maestro/007-categories/                           # flows (runs deferred)
.maestro/common/{api.js, open-categories.yaml}     # category actions for slot B
```

**Structure Decision**: one new feature package with the standard split; the engine, database,
error codes, navigation and Home are touched only additively. Repositories own Room + API;
`CategoriesService` (domain) orchestrates the gate, the engine, refreshes, the resolver and the
choice list; stores call domain lambdas only.

## Merge hotspots

| File | Change here | Overlap | Merge note |
|---|---|---|---|
| `core/database/AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt` | v4, 3 entities/DAOs, `MIGRATION_3_4`, clear | C6 bumps to v5 | Additive; C6 must start from v4 |
| `core/network/ApiErrorCode.kt` | `CATEGORY_IN_USE` | C6 adds `CURRENCY_MISMATCH` | One enum entry each |
| `strings.xml` (both) | append-only block `<!-- Categories (spec 007) -->` | C6 | Keep both blocks |
| `AppModules.kt`, `App.kt` | `categoriesModule`; `CategoriesSyncCoordinator` `onForeground()` | C6 | One list element / one call each |
| `navigation/Routes.kt`, `AppNavHost.kt` | 2 keys + entries | C6 | Additive |
| `feature/auth/ui/home/*` | `CategoriesClicked`, `NavigateCategories`, one row | C6 may add a row | Minimal; Home tests and screenshots updated on purpose |
| `designsystem/icon/AppIcons.kt`, `catalog/CatalogSections.kt`, `ComponentScreenshotTest.kt` | new icons and entries | C6 | Additive; existing baselines must not change |
| `.maestro/common/api.js` | category and expense actions | C6 adds transaction actions | Additive; C6 reuses `createExpense` |

## Dependencies on C4 (spec 006) and C14 (spec 004) code

- `core/sync/SyncEngine` (`enqueue`, `enqueueDelete`, `keepMine`, `useTheirs`, `discard`, `syncNow`, `get`, `observe`), `SyncHandler.canRestore`, `Refusal.requestId` (spec 006).
- `feature/wallets/domain/WalletDeletionParticipant` (resolved at call time by `WalletRepository`, also on refresh drops).
- `ScopeKind.User` and the signed-in user id from `SessionManager`.
- The spec 006 E2E plumbing: `network-off` / `network-on`, `# e2e-between:`, `api.js` slot B, `createWallet`.

All are on `main` `f64e0f6`; T001 confirms.

## Implementation phases (input to /speckit-tasks)

1. **Setup**: confirm prerequisites; package, Koin module, string block, error code.
2. **Foundation**: domain models; DTOs, APIs, contract tests; Room entities/DAOs, v4 migration, schema, cleaner; `CategorySyncHandler`; repositories (overlay, refresh merges, references); `CategoriesService` skeleton + coordinator; design-system additions; navigation keys; Home row; `AppGraphTest`.
3. **US1 See categories** (P1): overview building, language names, list screen, refresh triggers, E2E flows.
4. **US2 Create & edit** (P1): editor create/edit, validation, gate, clash/refusal UI, E2E flows.
5. **US4 Choose & display** (P1): choice builder, resolver, references refresh, choice sheet component.
6. **US3 Archive & delete** (P2): archive, delete rule, in-use refusal choices, delete clash, E2E flows.
7. **Polish**: Arabic review, accessibility, layering, contract recheck, quickstart (deferred), summary.

## External dependencies (not in this repo)

- Backend spec 011 on the dev stack (`e358537`, built, committed) **and seeded**; spec 012 (transactions) for in-use scenarios. B14 (feed, batch) is not used.
- Backend contract gaps: see `summary.md` (none blocking; the "household" default and the missing Arabic glossary term are recorded there).

## Complexity Tracking

None.

## Analysis record (`/speckit-analyze`, 2026-10-02)

No CRITICAL findings. HIGH and MEDIUM findings, all fixed in this session:

| ID | Category | Severity | Finding | Fix |
|---|---|---|---|---|
| A1 | Constitution (P-10.6) | HIGH | The handler would decode the domain's `CategoryDraft` and map pointers to `CategoryField`, so `data` imported `domain` (spec 006 accepted this; the rule says MUST NOT) | Queue payload `CategoryPayload` and plain field names live in `data`; domain maps both (research R-04, data-model §2–3, T005, T007, T015, layering test T063) |
| A2 | Inconsistency | MEDIUM | US3-S6 marks an in-use refusal "Not deleted", but list rows had only waiting / changed / not saved | `NotDeleted` row status (data-model §5, ui-and-e2e test tags, T037) |
| A3 | Coverage | MEDIUM | FR-022 (no plan gate) had no task | T041 asserts the service has no entitlement dependency |
| A4 | Terminology | MEDIUM | Spec §2 banned "reference" in UI while the copy keeps spec 006's "Not saved. Reference: …" for request ids | §2 and the standing rules ban "category reference" only and name the exception |

Found earlier by `/speckit-checklist` and fixed in the spec: Archived ordering (FR-002), deleting
an archived unused category (FR-016), FR-015/FR-018 exception, name length unit (FR-006), empty
choice (Edge Cases).

LOW, left as is: SC-001 (create under 20 s) is a usability target checked by hand, not a test;
default names are server data rather than string resources (as wallet and category names are),
which P-06.1 does not cover; the lowercase name sort is not locale-aware (research R-11).

## Implementation notes

Recorded while implementing (2026-10-02, owner away; each is an assumed default).

- **`observeUsedLocally()` added to `CategoriesService`.** The editor needs the set `PendingCategoryUses` reports to decide Delete and the in-use note, so the domain exposes it (additive to contracts/categories-domain.md).
- **Saving or archiving closes the editor.** `Saved` pops back to the list, and so does a confirmed archive or delete; a clash or refusal that follows is seen by the row's mark on the list and by opening the row (the editor is also the category's detail). The Maestro flows follow this.
- **`SegmentedControl` gained `optionModifier`** (default none) so Expense / Income carry the contract's test tags (`categories.kind.*`, `categoryEditor.kind.*`); no baseline changed.
- **`CategoryChoiceSheet` is split.** `CategoryChoiceList` is the stateless list (titled groups, radio semantics, check mark, empty message) and `CategoryChoiceSheet` wraps it in `AppBottomSheet`. A sheet is its own window and cannot be captured, so the catalogue and screenshots render the list (as spec 006 did for the transfer sheet).
- **Row status types are the category's own** (`CategoryRowStatus`); drawing reuses the wallet status line. "Not deleted: this category is in use." uses the "not saved" icon and colour with its own text.
- **Icons and tint rule (owner design review).** New stroke icons in `AppIcons`: `Cart`, `Restaurant`, `Bus`, `Fuel`, `Bolt`, `Phone`, `Health`, `School`, `Shirt`, `Sofa`, `Sparkle` (key `care`), `Ticket`, `Cycle` (key `repeat`), `Plane`, `Smile` (key `child`), `Gift`, `Heart`, `Store`, `Laptop`, `Undo`; `home`, `dots`, `wallet` and `chart` reuse `Home`, `More`, `Cash` and `Investment`. `CategoryColors.of(hex)`: the container is the colour at 14% over white; the icon tint is the colour darkened in 5% steps toward black until it reaches 3:1 against the container (all 26 platform colours and the 8 accents pass, tested). Only uppercase `#RRGGBB` counts as a colour; anything else shows the neutral slate tile.
- **Name length** is counted as UTF-16 units minus low surrogates (code points) because common Kotlin has no `codePointCount`; the editor reducer and the service use the same rule. Blank or over 50 never reaches the queue.
- **Arabic clash wording.** Spec 006's Arabic clash copy uses "نسخة", which T012 bans for categories, so the category block reads "احتفظ بتعديلي" / "استخدم تعديل الجهاز الآخر" and "اختر ما تريد الاحتفاظ به." **Owner review**, with "فئة / الفئات".
- **A refresh that is partly offline reports `Offline`** (own list or defaults unreachable); `Failed` only when neither read was unreachable and one failed. The 60 s floor starts only when both reads succeed.
- **The references floor counts attempts**, including failed ones, so a screen full of unresolved ids asks once a minute, not once per row; `force` ignores it. `CategoryDisplays.resolve` reports a miss through a callback the service wires to a background `refreshReferences(walletId, force = false)`.
- **Choices include partners' categories only as the current one**, resolved from every wallet's references (the choice call takes no wallet); an unresolvable current id is listed as "Category unavailable".
- **Overview `loaded`** is true once either list was read completely or any own row exists; the screen still shows the skeleton until its first refresh ends or data exists. `updatedAt` is the older of the two read stamps (the other when only one exists).
- **The categories list is a `LazyColumn`** (up to 226 rows), unlike the wallet list's column, so a long list composes only what is on screen.
- **Unknown kind on create** is refused as `INVALID_ENUM_VALUE` at `/kind`; an unknown kind on a synced category is kept and sent unchanged by the handler (it sends the stored kind).
- **`CATEGORY_IN_USE` in `AuthErrorCopy`.** The exhaustive code-to-copy `when` needed the new code; it joins the generic "with reference" branch (the categories screens choose their own copy by reason).
- **Koin.** `CategoryRepository` resolves the engine only when it checks the queue; `AppGraphTest` gained a test that the real graph resolves `CategoriesService`, the `category` handler and the `CategoryReferencesCleanup` participant.
- **Migration tests.** `MigrationTest` and `Migration2To3Test` register `MIGRATION_3_4` too (Room needs a path to v4); `Migration3To4Test` covers 3 → 4 and `SignOutWipeTest` covers the three new tables.
- **Contract recheck (T069).** `categories.yaml` is unchanged since `e358537`; the backend error catalog already lists `CATEGORY_IN_USE` (added here) and `CURRENCY_MISMATCH` (stays with C6).
- **E2E.** The flows and `api.js` actions (`listCategories`, `getCategory`, `createCategory`, `saveCategory`, `archiveCategory`, `deleteCategory`, `listPlatformCategories`, `createExpense`) are written under `.maestro/007-categories/` and `.maestro/common/` (also `open-categories`, `create-category`, `seed-category`); none was run (owner policy). US1-S3 (retired default) stays manual, covered by `CategoriesOverviewTest`.
- **QA without a device (2026-10-02, `qa-report.md`).** Contract checked against the live local backend (`saveme-backend@f6d66eb`); 19 real responses are fixtures in `CapturedCategoryResponses.kt` and drive `CategoryCapturedResponsesTest`. The editor now closes without the "no longer available" message when the category leaves the phone because of the person's own choice (Accept the deletion, Delete anyway, Discard). Flow fixes: `US2-S2` asserts `checked` (radio-role segments are not "selected" to accessibility), `US2-S7` no longer presses back after accepting the deletion, `US3-S6 part3` presses back after "Archive instead". `ProfileSyncerTest.aLocalSaveIsStoredAtOnce…` flaked (2 of 8 runs): the PUT on Ktor's thread could clear the edit before the local check; the mock now holds the PUT until the check.
