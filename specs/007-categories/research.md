# Research: Categories (client)

Each decision lists what was chosen, why, and what was rejected. The owner was away with delegated
authority; decisions marked **(assumed default)** took the most conservative option. Spec 006
(wallets) is the pattern: where a decision repeats one of its research items, it says so and only
the differences are written down.

## R-01 Package and boundaries

- **Decision**: New bounded context `feature.categories` (`data`, `domain`, `ui`, `di`), named after the backend's Category entity (Wallets & Ledger module, `Categories` sub-namespace). It depends on `core` (`sync`, `network`, `database`, `presentation`, `locale`), `designsystem`, and other features' `domain` only: `feature.auth.domain` (`SessionManager`: user id, `emailVerified`) and `feature.wallets.domain` (`WalletDeletionParticipant`). Nothing in `feature.wallets` imports categories, so the dependency is one-way.
- **Rationale**: P-10.4; C6 (transactions), C7 and C8 will read categories through `feature.categories.domain` the same way. A separate context keeps the wallets package (and its tests) untouched.
- **Alternatives**: putting categories inside `feature.wallets` because the backend module is "Wallets & Ledger" (rejected: the client splits by entity, as spec 006 did, and wallets is already large); `feature.ledger` (rejected: C6 owns the ledger).

## R-02 Room schema v4 (assumed default: three tables, one migration)

- **Decision**: `AppDatabase` version 3 → 4 with `MIGRATION_3_4` in `core/database/Migrations.kt` creating `category`, `platform_category` and `category_reference` with their indexes (data-model §1); schema exported to `shared/schemas/com.fares.saveme.core.database.AppDatabase/4.json`. Entities and DAOs live in `feature.categories.data.local` and are registered in `AppDatabase` like the wallet tables. `DatabaseCleaner` deletes all three.
- **Rationale**: P-04.1 needs the local copy of own categories; the default set and the references are read-only but must render offline (FR-001, FR-019). Spec 006 left the database at v3 on `main` (`f64e0f6`). A pure create-table migration keeps every existing row (spec 004 FR-005).
- **Alternatives**: the default set as JSON in `KeyValueStore` (rejected: resolution and choice lists join it with own categories; one query source is simpler and testable with the DAO tests); not clearing the default set at sign-out because it is not personal (rejected: the README promises "sign-out clears every table", and refetching 26 rows at sign-in is cheap).

## R-03 Local row = server copy; pending content overlays it (as spec 006 R-03)

- **Decision**: The `category` row holds only what the server last said. A category created on the phone gets a **placeholder row** with `version = 0`, `in_use = false`. Screens read a `CategoryView` = row + the spec 004 `pending_change` row for `("category", id)` (payload overlay, clash, refusal). Everything that shows a category — the list, the editor, the choice list and the display resolver — uses the overlay, so a rename made offline shows everywhere at once.
- **Rationale**: identical to wallets; keeps `applyServerCopy` from touching queued content and makes "use theirs" and refresh merges safe.

## R-04 `CategorySyncHandler` (record type `category`, `canRestore = false`)

- **Decision**:
  - Scope: `SyncScope(ScopeKind.User, <signed-in user id>)` — categories are user-scoped (glossary), unlike wallets, which are their own scope.
  - Payload `CategoryPayload` (JSON, `@Serializable`, declared in `feature.categories.data`): `kind, name, icon, color, archive: Boolean`. The domain's `CategoryDraft` maps to it before `engine.enqueue`, so `data` imports nothing from `domain` (P-10.6, stricter than spec 006, whose data layer imports the wallet domain models).
  - `send(change)`:
    - `isDelete` → `DELETE /categories/{id}` with body `{"version": base}`.
    - else → `PUT /categories/{id}` (`CategoryWrite` with `version = base`, 0 = create), **skipped** when `base > 0` and the draft's fields equal the stored row's (archive-only change); then, when `archive` is true and the category isn't archived yet, `POST /categories/{id}/archive` with the version just returned (or `base` when the PUT was skipped). The last response is returned.
    - `kind` is always the stored row's kind for an edit (never sent changed, FR-008).
  - `applyServerCopy(json)`: `deleted_at != null` → delete the row; else upsert it.
  - `onDiscarded(id)`: if the row's `version == 0` (never synced), delete the placeholder.
  - `refusalFields`: pointers `/name`, `/kind`, `/icon`, `/color` → the field names `name`, `kind`, `icon`, `color` (plain strings); the domain maps them to `CategoryField`.
  - Bound in Koin with `bind SyncHandler::class` in `categoriesModule`.
- **Rationale**: the wallets handler shape (spec 006 R-04) carries an edit and an archive in one queue row so the archive is never lost behind the edit. Replays are safe: an identical PUT is answered 200 before the version check (categories.yaml precedence), archiving an archived category returns it unchanged, and deleting a deleted one returns the tombstone. `canRestore = false` because deletion is final (categories.yaml DELETE, ADR-0014).
- **Alternatives**: separate record types for archive/delete (rejected as in spec 006); one queue row per category kind (meaningless).

## R-05 PUT only; no PATCH, no GET of one category (assumed default)

- **Decision**: Every save is a whole-record `PUT`. `PATCH /categories/{id}` and `GET /categories/{id}` are not called.
- **Rationale**: spec 006 R-05 for PUT. A single-category read is not needed: a clash carries `current`, a refusal carries the code, and the list refresh brings everything else; fewer calls means fewer contract tests and failure paths.
- **Alternatives**: `GET /categories/{id}` after an in-use refusal to refresh `in_use` (rejected: the refusal itself proves the category is in use, R-07).

## R-06 Deletion is a queued local tombstone; Delete offered only while unused

- **Decision**:
  - Delete is offered when `row.in_use == false` **and** `PendingCategoryUses` (a `feature.categories.domain` interface; C5 binds a version reporting nothing, C6 rebinds it with its unsent transactions) does not list the category, and the row is not a default.
  - Confirming calls `engine.enqueueDelete("category", id, SyncScope(User, userId), version)`. Never-synced (`version == 0`): the engine drops the queue row (spec 004 FR-004) and the repository deletes the placeholder at once.
  - Otherwise every screen and the choice list hide a category whose queue row is a delete in `Waiting`/`Sending`; the display resolver still resolves it (a transaction can never point at a category the server will delete, and C6 hides nothing on its account). The server's tombstone (or "Accept the deletion") removes the row through `applyServerCopy`.
  - A delete in `Clash` or `Refused` makes the category visible again with its mark.
- **Rationale**: P-04.5; FR-016; mirrors spec 006 R-06. The server's 409 `CATEGORY_IN_USE` stays the authority for uses the phone cannot see (another device).
- **Alternatives**: offering Delete always and relying on the server (rejected: a predictable refusal is poor UX); computing "in use" locally (rejected: the phone never holds every transaction, recurring rule and budget).

## R-07 Clash and refusal choices

- **Decision**: The editor store (which is also the category's detail) reads the queue row:
  - edit vs live `current` → "Keep mine" (`engine.keepMine`) / "Use the other device's" (`engine.useTheirs`);
  - delete vs live `current` → "Delete anyway" (`keepMine`) / "Keep the category" (`useTheirs`);
  - anything vs tombstone `current` → only "Accept the deletion" (`useTheirs`; the engine refuses `keepMine` because `canRestore` is false);
  - side by side: only the fields that differ (name, icon, colour, archived).
  - Refusal reasons by `Refusal.code`: `EMAIL_NOT_VERIFIED` → NotVerified ("Try again" = `engine.enqueue` of the unchanged payload with the same base); `NOT_FOUND` → NoLongerAvailable; `CATEGORY_IN_USE` → InUse; `VALIDATION_FAILED` → Invalid (field messages via `refusalFields`); anything else (`FORBIDDEN`, `INVALID_ID`, `UNKNOWN`) → Other with the request id (P-13.2). "Discard" = `engine.discard`.
  - **InUse** (clarification 1): "Archive instead" = `engine.enqueue("category", id, scope, row.version, shown.copy(archive = true))`, which turns the refused delete row into a waiting archive (the engine keeps the row's base version and clears `isDelete`); "Keep the category" = `engine.discard(id)`. Both first mark the row `in_use = true` locally (the refusal proves it), so Delete is no longer offered until a refresh says otherwise.
- **Rationale**: FR-012–FR-014, FR-017; reuses the spec 004 copy keys `sync_deleted_elsewhere`, `action_accept_deletion` and the wallet choice wording.
- **Alternatives**: archiving automatically on `CATEGORY_IN_USE` (rejected: changes the person's data without their choice).

## R-08 Refresh from the server (wave 1)

- **Decision**:
  - `CategoryRepository.refreshOwn()` reads `GET /categories?limit=200` page by page. Only when **every** page succeeds does it merge: upsert each listed category as the server copy; delete rows with `version > 0` not listed **and** without a `pending_change` row (FR-005); stamp `sync_scope` key `categories`.
  - `refreshDefaults()` reads `GET /platform-categories` (one page) and replaces the `platform_category` table in one transaction on success; stamps key `platform_categories`.
  - Triggers: a new `CategoriesSyncCoordinator` (domain) on sign-in (forced) and foreground (60 s floor), as `WalletsSyncCoordinator`; the categories screen calls `refresh(force = true)` when it opens and on pull. Both reads run together; the screen's "Updated …" uses the older of the two stamps.
  - A forced refresh also calls `engine.syncNow()` (spec 006 implementation note).
- **Rationale**: B14's user feed exists on the backend but the client's feed switch is C14 wave 2 (spec Assumptions). 200 categories fit one page (backend sizing target, assumed default 17).
- **Alternatives**: refreshing the defaults daily only (rejected: 26 rows; a retired default should leave pickers promptly); comparing `updated_at` per row (rejected: the whole set is smaller than the bookkeeping).

## R-09 Category references: on demand, per wallet (clarification 5)

- **Decision**:
  - `CategoryReferenceRepository.refresh(walletId)` reads `GET /wallets/{id}/category-references?limit=200` page by page and replaces that wallet's rows on a complete read; `404` deletes them.
  - `CategoriesService.refreshReferences(walletId, force)` applies a 60 s per-wallet floor (in memory) unless forced. Callers: C6's wallet transaction screen (forced on open, not this spec), and the display resolver when an id in a wallet resolves to nothing (not forced).
  - `categoriesModule` registers a `WalletDeletionParticipant` that deletes a wallet's references. C4's repository calls participants both on a confirmed deletion and when a refresh drops a wallet (spec 006 implementation note), which covers "the wallet leaving the phone".
  - Tombstoned references are never listed by the endpoint, so a complete read is the whole live set.
- **Rationale**: FR-020; no polling of every wallet; wave 2 moves references onto the wallet feed.
- **Alternatives**: refreshing every visible wallet's references on each foreground (rejected: up to 50 requests a minute for data that is empty until a partner categorises something); storing references inside the wallets feature (rejected: references are a projection of Category, and C4 is done).

## R-10 Resolving a category for display

- **Decision**: A pure domain function `resolve(id, own: Map, defaults: Map, references: Map, language)` returning `CategoryDisplay` with `source` = Own | Default | Partner | Unavailable, in the order own (overlay applied, archived included, pending-deletion included) → default (retired included, name by `language`) → the wallet's references → `Unavailable` (neutral icon/colour, "Category unavailable"). `CategoriesService.observeDisplays(walletId: String?)` combines the three tables (plus the queue overlay and the app language) into one `CategoryDisplays` snapshot with `resolve(id)`, so a list of 50 transactions is one flow, not 50. A miss with a non-null `walletId` triggers `refreshReferences(walletId, force = false)` once (the floor de-duplicates).
- **Rationale**: FR-019, transactions.yaml resolution order; SC-006.
- **Alternatives**: one flow per id (rejected: N database observers on a ledger screen).

## R-11 Choice list (clarifications 2 and 4)

- **Decision**: `CategoriesService.observeChoices(kind, currentCategoryId)` → `CategoryChoices(own, defaults, current)`:
  - `own`: active own categories of `kind`, not hidden by a waiting deletion, sorted by name with `lowercase()` comparison, ties by `created_at`;
  - `defaults`: platform entries of `kind` with `archived_at == null`, by `position`;
  - `current`: the resolved display of `currentCategoryId` when it is not already in `own`/`defaults` (archived, retired or a partner's), shown first and selected, marked archived when it is.
  - Never a category of the other kind or an unknown kind.
- **Rationale**: FR-018; the backend refuses any other category on a new or changed transaction (transactions.yaml INVALID_ENUM_VALUE at `/category_id`), so offering one would only produce a refusal.
- **Locale-aware sort**: `commonMain` has no collator; lowercase comparison is deterministic and matches the server's documented byte order closely enough for short lists. Recorded as a known limit (Arabic letters with hamza forms may sort apart).

## R-12 Default names follow the app language

- **Decision**: The domain takes `Flow<AppLanguage>` built from `AppLanguageStore.language.map { it ?: store.device() }` (core). `CategoryDisplay.name` for a default is `names.ar` when the language is Arabic, otherwise `names.en`; own and partner names are passed through untouched. Screens recompose when the flow emits; nothing is fetched.
- **Rationale**: FR-003, SC-004; the backend sends both names whatever `Accept-Language` asks (categories.yaml).

## R-13 Icons and colours (clarification 3)

- **Decision**: In `designsystem`:
  - `CategoryIcons`: every icon key the default set uses — `cart`, `restaurant`, `bus`, `fuel`, `home`, `bolt`, `phone`, `health`, `school`, `shirt`, `sofa`, `care`, `ticket`, `repeat`, `plane`, `child`, `gift`, `heart`, `dots`, `wallet`, `store`, `laptop`, `chart`, `undo` (24 keys) — mapping to `AppIcons`. Existing vectors are reused (`home` → `Home`, `dots` → `More`, `chart` → `Investment`, `wallet` → `Cash`); the rest are new stroke paths in the `AppIcons` style. Vector constants are named for what they draw and never with a glossary-banned word (`repeat` → `AppIcons.Cycle`, nothing named `Tag` or `Label`). The person's icon choice offers the same 24 keys. Unknown key → `AppIcons.More` (the neutral fallback).
  - Colours: the person's choice offers the eight `WalletAccents` (keys are uppercase `#RRGGBB`, so they are valid category colours). Display accepts any `^#[0-9A-F]{6}$` value (defaults use 26 platform colours): `CategoryColors.of(hex)` parses it into a swatch, a light container (the colour at low alpha over the surface) and an icon tint darkened until it reaches 3:1 against the container (pure function, unit-tested over the 26 platform colours and the 8 accents). An invalid or null value → the neutral tile (`ink`-family container). Feature code passes strings only (P-07.2).
  - Components: `CategoryTile(iconKey, colorHex)`; `CategoryRow(name, iconKey, colorHex, caption, status, selected, onClick)` (72 dp row, status as text + icon, `Default`/`Archived` captions); `CategoryChoiceSheet(groups, selectedId, onSelect, onDismiss)` (stateless, on `AppBottomSheet`, radio semantics, group titles passed in). Catalogue entries and screenshots (EN, AR RTL, 2× font) for each.
- **Rationale**: P-07.2–3; the backend fixes 24 icon keys through its seed and stores everything else uninterpreted; one icon vocabulary for defaults and own categories keeps the lists consistent. The 3:1 rule satisfies P-08 / FR-025 for the yellow and light platform colours (`#F9A825`, `#F06292`).
- **Alternatives**: a free colour picker (rejected: raw colours, accessibility); drawing the icon in white on the solid colour (rejected: white on `#F9A825` is about 1.9:1); a separate icon set for own categories (rejected: two vocabularies for one entity).
- **Owner design review**: the new icon paths and the derived tint rule (listed in `summary.md`).

## R-14 Gates before writing

- **Decision**: Before opening the editor (create) and before every save, archive or delete, the store asks `isEmailVerified()` (from `SessionManager`); when false it emits `Effect.RequireVerification`, which the Route turns into the existing `EmailNotVerifiedSheet` through an `AppNavHost` callback (categories UI can't import auth UI, P-10.4). No entitlement check (FR-022).
- **Rationale**: FR-021; spec 006 R-14 without the plan gate.

## R-15 Screens and navigation

- **Decision**:
  - Keys `CategoriesKey` and `CategoryEditorKey(categoryId: String? = null, kind: String = "expense")`.
  - Home gets a "Categories" row (`home.categories`) under "Wallets" via `HomeIntent.CategoriesClicked` / `HomeEffect.NavigateCategories` (minimal, append-only edit of a hotspot).
  - **Categories screen**: `SegmentedControl` Expense / Income (`categories.kind.expense|income`), the three groups of FR-002, pull to refresh, FAB "New category" (opens the editor with the selected kind), "Updated …" caption offline, refresh-failed alert, loading skeleton before the first refresh.
  - **Category editor** (create and edit; in edit mode it is also the category's detail): status alert with clash/refusal choices, kind choice (create) or read-only kind (edit), name, icon `ChoiceGrid`, colour `ChoiceGrid`, Save; in edit mode Archive (active only) and Delete (FR-016 rule) or the in-use note; confirmation dialogs.
  - Default rows are not tappable; they carry a "Default" caption.
- **Rationale**: two screens cover every story (the category has nothing to show beyond its fields, unlike a wallet's balance and members); P-16 per screen.
- **Alternatives**: a separate detail screen like wallets (rejected: it would repeat the editor's fields); a tab on Home (rejected: Home is a placeholder that later features redesign).

## R-16 The category choice and display as building blocks for C6

- **Decision**: C5 ships the domain API (`observeChoices`, `observeDisplays`, `refreshReferences`, `PendingCategoryUses`) and the stateless design-system pieces (`CategoryChoiceSheet`, `CategoryRow`, `CategoryTile`). C6's transaction editor store calls the domain and renders the sheet; C6's ledger rows render `CategoryTile` + the resolved name. C5 adds no transaction screen and no devtools entry beyond the component catalogue.
- **Rationale**: P-10.4 forbids C6 importing `feature.categories.ui`; designsystem components are shared by design. US4 is proved by domain tests and component screenshots (spec §8).

## R-17 Client error code

- **Decision**: Add `CATEGORY_IN_USE(RetryClass.Never)` to `core/network/ApiErrorCode.kt` with a contract test that a `409 CATEGORY_IN_USE` problem decodes to it and the engine classifies it as `Refused`.
- **Rationale**: FR-026; the contract check's client-mismatch rule ("never out of scope"). `CURRENCY_MISMATCH` stays with C6.

## R-18 E2E strategy

- **Decision**: Flows in `.maestro/007-categories/` per the `speckit-e2e` skill, written in this feature, **run deferred to the owner's device** (owner decision: no emulator or Maestro runs now).
  - `api.js` gains `listCategories`, `createCategory`, `saveCategory`, `archiveCategory`, `deleteCategory` (`http.request` with `DELETE` and a JSON body), `listPlatformCategories`, and `createExpense` (a `PUT /transactions/{id}` using a category, to make it "in use" for US3-S3/S6; backend B6 is built).
  - Offline scenarios reuse spec 006's `network-off` / `network-on` between-actions.
  - Dialog buttons are tapped by their text (dialog windows don't expose test tags to Maestro, spec 006 lesson).
  - US4 has no flow: its screens are C6's.
- **Rationale**: as spec 006 R-17.

## R-19 Koin wiring (spec 006 lesson)

- **Decision**: `CategoryRepository` resolves `SyncEngine` lazily (`hasQueuedChange = { get<SyncEngine>().get(...) }`), because the engine is built from every `SyncHandler` and the category handler needs the repository. `core/di/AppGraphTest` (which starts the real graph) must stay green with `categoriesModule` added; a test asserts the category handler is among the engine's handlers.
- **Rationale**: the spec 006 launch crash (`StackOverflowError`) came from exactly this loop.

## R-20 Arabic copy (assumed default)

- **Decision**: "Category" → "فئة" (plural "الفئات"); "Default" → "افتراضية"; "Expense" → "مصروفات"; "Income" → "دخل"; "Archived" → "مؤرشفة". No "وسم"/"تصنيف"/"ملصق" (closest to the banned "tag, label"). Western digits. **Owner review** (the glossary has no Arabic term for Category).

## R-21 No new dependencies

- **Decision**: none. UUIDv7 from `kotlin.uuid.Uuid.generateV7()`; Ktor `DELETE` with a body as in `WalletApi`; colour parsing and contrast in plain Kotlin.
