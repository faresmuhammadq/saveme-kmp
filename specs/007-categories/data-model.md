# Data Model: Categories (client)

Wire shapes are `categories.yaml` (backend spec 011, `e358537`, v1.4). Instants are UTC strings in
Room and `Instant` in domain (P-05). No money is held by this feature.

## 1. Tables (Room, schema v4, `feature.categories.data.local`)

### `category` (new) — the person's own categories

| Column | Type | Rules |
|---|---|---|
| `id` | TEXT PK | UUIDv7; client-made for new categories |
| `account_id` | TEXT | signed-in user id that holds this copy (spec 004 FR-018) |
| `kind` | TEXT | `income` · `expense`; an unknown value is kept, listed nowhere, still resolvable, never sent changed |
| `name` | TEXT | 1–50 chars after trimming, stored as the server returns it |
| `icon` | TEXT NULL | stored as given |
| `color` | TEXT NULL | stored as given |
| `archived_at` | TEXT NULL | UTC instant |
| `in_use` | INTEGER (bool) | server-reported; set to true locally on a `CATEGORY_IN_USE` refusal (research R-07) |
| `version` | INTEGER | `0` = placeholder, never synced |
| `change_seq` | INTEGER | stored, not interpreted (wave 2) |
| `created_at` | TEXT | UTC instant (placeholder: local time of creation) |
| `updated_at` | TEXT | UTC instant |
| `refreshed_at` | TEXT | when this copy was last written from the server (or created) |

Index: `(account_id, kind, archived_at)`.

### `platform_category` (new) — the cached default set

| Column | Type | Rules |
|---|---|---|
| `id` | TEXT PK | platform id, used wherever a category id is expected |
| `system_key` | TEXT | stable, never shown |
| `kind` | TEXT | `income` · `expense` (unknown kept, never offered) |
| `name_ar` | TEXT | |
| `name_en` | TEXT | |
| `icon` | TEXT | key |
| `color` | TEXT | `#RRGGBB` |
| `position` | INTEGER | platform order |
| `archived_at` | TEXT NULL | retired when set |
| `updated_at` | TEXT | |

Index: `(kind, position)`. Replaced as a whole on each complete read.

### `category_reference` (new) — partners' categories per wallet, read-only

| Column | Type | Rules |
|---|---|---|
| `id` | TEXT PK | reference id |
| `wallet_id` | TEXT | indexed |
| `category_id` | TEXT | the id a transaction carries |
| `owner_user_id` | TEXT | the member who owns the category |
| `kind` | TEXT | |
| `name` | TEXT | shown as is, never translated |
| `icon` | TEXT NULL | |
| `color` | TEXT NULL | |
| `version` | INTEGER | |
| `change_seq` | INTEGER | stored, not interpreted |
| `updated_at` | TEXT | |

Unique index `(wallet_id, category_id)` (the server keeps one live reference per pair). A wallet's
rows are replaced as a set on a complete read and deleted on `404`, on the wallet's confirmed
deletion and when a wallet refresh drops the wallet (`WalletDeletionParticipant`).

### `sync_scope` (existing)

Keys `categories` and `platform_categories` hold the last complete refresh of each (`refreshed_at`,
shown as "Updated …" offline, the older of the two). Key `user:<id>` gets `last_pushed_at` from
the engine on each successful send (spec 004; shared with the profile).

### `pending_change` (existing, spec 004)

Record type `category`, record id = category id, scope `user:<userId>`, payload `CategoryPayload`
(§2). No schema change.

### Migration `MIGRATION_3_4`

`CREATE TABLE category (…)`, `CREATE TABLE platform_category (…)`, `CREATE TABLE
category_reference (…)` and their indexes. Nothing else is touched. `buildAppDatabase` adds it
after `MIGRATION_2_3`. `DatabaseCleaner` adds `categoryDao().deleteAll()`,
`platformCategoryDao().deleteAll()` and `categoryReferenceDao().deleteAll()`.

## 2. Wire DTOs (`feature.categories.data.dto`)

Decoding ignores unknown members (P-02.3); request bodies carry only schema members (P-02.2).

| DTO | Members |
|---|---|
| `CategoryDto` | `id, user_id, kind, name, icon: String?, color: String?, archived_at: String?, in_use: Boolean, version: Long, change_seq: Long, created_at, updated_at, deleted_at: String?` |
| `CategoryWriteRequest` | `kind, name, icon (explicit null), color (explicit null), version` — PUT |
| `CategoryVersionOnlyRequest` | `version` — archive, DELETE body |
| `CategoryPayload` (queue payload, not a wire DTO) | `kind, name, icon, color, archive` — `pending_change.payload_json`; mapped from the domain's `CategoryDraft` |
| `PlatformCategoryDto` | `id, system_key, kind, names: {ar, en}, icon, color, position: Int, archived_at: String?, updated_at` |
| `CategoryReferenceDto` | `id, wallet_id, category_id, owner_user_id, kind, name, icon: String?, color: String?, version: Long, change_seq: Long, created_at, updated_at, deleted_at: String?` |

`icon` and `color` are required-nullable in `CategoryWrite`: the encoder MUST write `null`, not
omit them (contract test). `CategoryWrite` never carries `user_id`, `system_key`, `archived_at`,
`in_use`, `parent_id` or any sync member (they are refused as `UNKNOWN_FIELD`).

## 3. Domain (`feature.categories.domain`)

```text
enum CategoryKind(wire)      Expense, Income; fromWire(unknown) = null (never offered, never re-encoded differently)
enum CategoryField(pointer)  Name(/name), Kind(/kind), Icon(/icon), Color(/color)
enum CategorySource          Own, Default, Partner, Unavailable

Category(id, kindWire: String, name, icon: String?, color: String?, archivedAt: Instant?, inUse: Boolean,
         version: Long, createdAt: Instant)
DefaultCategory(id, systemKey, kindWire, nameAr, nameEn, icon, color, position: Int, retiredAt: Instant?)
CategoryReference(walletId, categoryId, ownerUserId, kindWire, name, icon: String?, color: String?)

CategoryDraft(kind: String, name: String, icon: String?, color: String?, archive: Boolean = false)
  Category.toDraft(); draft.sameFieldsAs(category)        // ignores archive
  draft.toPayload() / CategoryPayload.toDraft()           // the queue payload lives in data (P-10.6)

CategoryView(
  server: Category,                // the row
  pending: CategoryDraft?,         // queue payload, null when nothing waits
  pendingDelete: Boolean,
  syncState: None | Waiting (engine Waiting or Sending) | Clash(theirs: Category?, deletedElsewhere: Boolean)
           | Refused(reason: RefusalReason, fields: Map<CategoryField, String>),
) { shown = pending ?: server.toDraft(); hidden = pendingDelete && syncState == Waiting;
    archived = shown.archive || server.archivedAt != null }
  fun CategoryView.canDelete(usedLocally: Set<String>) = !server.inUse && server.id !in usedLocally && !pendingDelete
      // R-06; an archived category may still be deleted while unused

enum RefusalReason  NotVerified, NoLongerAvailable, InUse, Invalid, Other(requestId)

CategoryDisplay(id, name: String, iconKey: String?, colorHex: String?, kindWire: String?,
                source: CategorySource, archived: Boolean)   // name already in the app language for a default
CategoryDisplays(resolve: (String) -> CategoryDisplay)
CategoryChoices(current: CategoryDisplay?, own: List<CategoryDisplay>, defaults: List<CategoryDisplay>)
CategoriesOverview(kind: CategoryKind, own: List<CategoryView>, defaults: List<CategoryDisplay>,
                   archived: List<CategoryView>, updatedAt: Instant?, loaded: Boolean, defaultsMissing: Boolean)

interface PendingCategoryUses { fun usedLocally(): Flow<Set<String>> }   // C5 binds NoPendingCategoryUses; C6 rebinds
```

Services (domain, called by stores and by C6/C7/C8):

| API | Does |
|---|---|
| `CategoriesService.observeOverview(kind): Flow<CategoriesOverview>` | FR-002 groups and ordering, hidden ones dropped, defaults by language |
| `observeCategory(id): Flow<CategoryView?>` | editor/detail; null when gone or hidden by a waiting deletion |
| `observeChoices(kind, currentCategoryId?): Flow<CategoryChoices>` | FR-018 (R-11) |
| `observeDisplays(walletId?): Flow<CategoryDisplays>` | FR-019 (R-10); a miss in a wallet triggers a references refresh |
| `create(draft): CreateResult` | verified gate → validate → new UUIDv7 → placeholder row → `engine.enqueue(base 0)` |
| `save(id, draft, baseVersion): SaveResult` | verified gate → validate → kind forced to the stored kind → `engine.enqueue(base = baseVersion)` |
| `archive(id)` | `save(id, shown.copy(archive = true), row.version)` |
| `delete(id)` | R-06 (refuses with `InUse` when the rule says no) |
| `archiveInstead(id)` / `keepCategory(id)` | R-07 (InUse refusal) |
| `keepMine / useTheirs / discard / tryAgain(id)` | engine calls (R-07) |
| `refresh(force)` | R-08: own list + defaults, 60 s floor unless forced; `RefreshOutcome` Done / Offline / Failed |
| `refreshReferences(walletId, force)` | R-09 |

`CategoriesSyncCoordinator(authState, service, scope, scheduler)` — sign-in (forced) and
foreground (floor), as `WalletsSyncCoordinator`.

`CategoryReferencesCleanup : WalletDeletionParticipant` — deletes a wallet's references.

## 4. State transitions of an own category on the phone

```text
(create) ─► placeholder v0 + queue[Waiting] ──200/201──► server copy vN, queue cleared
                    │ delete before first send ─► placeholder and queue row removed (nothing sent)
server copy ─edit/archive─► queue[Waiting] (overlay shown) ──200──► server copy vN+1
            ─delete (unused)─► queue[Waiting, delete] (hidden) ──200 tombstone──► row removed
queue[Waiting] ──409 VERSION_CONFLICT live──► Clash ─keepMine─► Waiting (rebased) │ ─useTheirs─► server copy
queue[Waiting] ──409 VERSION_CONFLICT tombstone──► Clash(deletedElsewhere) ─acceptDeletion─► row removed
queue[Waiting, delete] ──409 CATEGORY_IN_USE──► Refused(InUse), row.in_use = true
        ─archiveInstead─► queue[Waiting, archive] │ ─keepCategory─► queue cleared, category shown
queue[Waiting] ──403/404/422──► Refused ─edit─► Waiting │ ─tryAgain─► Waiting │ ─discard─► server copy (v0: removed)
list refresh: not listed ∧ v>0 ∧ no queue row ─► row removed
sign-out ─► category, platform_category, category_reference and queue rows removed
```

Archive is one-way: there is no "unarchive" control.

## 5. Screen state (MVI, `feature.categories.ui`)

```text
CategoriesState(kind: CategoryKind, own: List<CategoryRowUi>, defaults: List<CategoryRowUi>,
                archived: List<CategoryRowUi>, refreshing: Boolean, updatedAt: Instant?, offline: Boolean,
                refreshFailed: Boolean, defaultsMissing: Boolean, loaded: Boolean)
CategoryRowUi(id, name, iconKey, colorHex, caption: Default|Archived|None, status: None|Waiting|Changed|NotSaved|NotDeleted, clickable)
                // NotDeleted = a deletion refused with CATEGORY_IN_USE ("Not deleted: this category is in use.")
CategoriesIntent  = Opened | KindChosen(kind) | Refresh | CreateClicked | CategoryClicked(id) | Back
CategoriesEffect  = OpenEditor(id: String?, kind) | RequireVerification | NavigateBack

CategoryEditorState(mode: Create | Edit(id), kind: CategoryKind?, kindEditable: Boolean, name: String,
                    iconKey: String?, colorHex: String?, baseVersion: Long, errors: Map<CategoryField, FieldError>,
                    sync: SyncUi (none | waiting | clash(rows, deletedElsewhere, isDelete) | refused(reason, requestId)),
                    archived: Boolean, canArchive: Boolean, canDelete: Boolean, inUseNote: Boolean,
                    confirm: None | Archive | Delete, canSave: Boolean, saving: Boolean, gone: Boolean)
CategoryEditorIntent = Opened(id?, kind) | KindChosen | NameChanged | IconChosen | ColorChosen | SaveClicked |
                       ArchiveClicked | DeleteClicked | ConfirmArchive | ConfirmDelete | DismissConfirm |
                       KeepMine | UseTheirs | AcceptDeletion | TryAgain | Discard | ArchiveInstead | KeepCategory | Back
CategoryEditorEffect = Saved | NavigateBack | ShowMessage(EditorMessage) | RequireVerification
```

Each screen has `…Change` and a pure top-level `reduce` (P-16.4). The edit form keeps
`baseVersion` from the first emission after `Opened` (FR-007).
