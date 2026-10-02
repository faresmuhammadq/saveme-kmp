# Contract: Screens, test tags, copy and E2E

## Navigation

Home → row "Categories" (`home.categories`, under "Wallets") → `CategoriesKey` → own category row →
`CategoryEditorKey(id)`. FAB "New category" → `CategoryEditorKey(null, <selected kind>)`. Back
returns one step. `AppNavHost` passes `onRequireVerification` to both Routes; it shows the
existing `EmailNotVerifiedSheet`.

## Screens (design-system components only, P-07)

| Screen | Content |
|---|---|
| Categories | `AppTopAppBar` ("Categories", back); `SegmentedControl` Expense / Income; `SyncBanner` when offline with waiting changes; refresh-failed `InlineAlert`; section "Your categories" with `CategoryRow`s (tappable) or the note "Add your own categories to sort your money your way."; section "Default categories" with `CategoryRow`s (caption "Default", not tappable) or "Default categories couldn't be loaded."; section "Archived" with archived own rows (caption "Archived", tappable); `AppFab` "New category"; pull to refresh; "Updated %1$s" offline; `Skeleton` rows before the first refresh |
| Category editor (create / edit, also the detail) | status `InlineAlert` (waiting / changed on another device with side-by-side lines / deleted elsewhere / not saved + reason, with its choices); kind `SegmentedControl` (create) or read-only row (edit); name `AppTextField`; icon `ChoiceGrid` (24 icons + "None"); colour `ChoiceGrid` (8 accents + "None"); `PrimaryButton` Save; edit mode: `AppListItem` "Archive" (active only), `DestructiveButton` "Delete category" or the in-use note; confirm `AppAlertDialog`s |

## Design-system additions (catalogue + screenshots EN, AR RTL, 2× font)

`CategoryIcons` (24 keys, research R-13), `CategoryColors.of(hex)` (swatch, container, tint ≥ 3:1),
`CategoryTile`, `CategoryRow` (status text + icon; captions), `CategoryChoiceSheet` (two titled
groups, a "current" item first, radio semantics, check mark for the selection).

## Test tags

| Element | Tag |
|---|---|
| Home row | `home.categories` |
| Screen root / FAB / kind switch | `categories.list` / `categories.create` / `categories.kind.expense`, `categories.kind.income` |
| Own, default and archived rows | `categories.own.<index>`, `categories.default.<index>`, `categories.archived.<index>` (0-based, display order) |
| Row status mark | `categories.own.<index>.status` ("Waiting to sync", "Changed on another device", "Not saved", or "Not deleted" for an in-use refusal) |
| Notes | `categories.ownEmpty`, `categories.defaultsMissing`, `categories.refreshFailed` |
| Editor fields | `categoryEditor.kind.expense`, `categoryEditor.kind.income`, `categoryEditor.name`, `categoryEditor.icon.<key>`, `categoryEditor.color.<index>`, `categoryEditor.save` |
| Editor status and choices | `categoryEditor.status`, `categoryEditor.keepMine`, `categoryEditor.useTheirs`, `categoryEditor.acceptDeletion`, `categoryEditor.deleteAnyway`, `categoryEditor.keepCategory`, `categoryEditor.archiveInstead`, `categoryEditor.tryAgain`, `categoryEditor.discard` |
| Editor actions | `categoryEditor.archive`, `categoryEditor.delete`, `categoryEditor.inUseNote` |
| Choice sheet (component) | `categoryChoice.current`, `categoryChoice.own.<index>`, `categoryChoice.default.<index>` |

Dialog buttons are not tagged for Maestro (dialog windows don't expose tags, spec 006); flows tap
them by text.

## Copy (new keys, English / Arabic)

`home_categories` ("Categories" / "الفئات"), `categories_title` ("Categories" / "الفئات"),
`category_kind_expense` ("Expense" / "مصروفات"), `category_kind_income` ("Income" / "دخل"),
`categories_new` ("New category" / "فئة جديدة"), `categories_own` ("Your categories" / "فئاتك"),
`categories_own_empty` ("Add your own categories to sort your money your way."),
`categories_defaults` ("Default categories" / "الفئات الافتراضية"), `category_default` ("Default" /
"افتراضية"), `categories_defaults_missing` ("Default categories couldn't be loaded."),
`categories_archived` ("Archived" / "مؤرشفة"), `category_archived` ("Archived"),
`categories_updated_at` ("Updated %1$s"), `categories_refresh_failed` ("Couldn't update your
categories. Showing what's on this phone."), `category_unavailable` ("Category unavailable" / "فئة
غير متاحة"), `category_gone` ("This category is no longer available."),
`category_field_kind` ("Expense or income" / "مصروفات أم دخل"), `category_field_name` ("Name"), `category_field_icon` ("Icon"),
`category_field_color` ("Colour"), `category_none` ("None"), `category_kind_locked_note` ("This can't
change after the category is created."), `category_error_name_blank` ("Enter a name."),
`category_error_name_long` ("Use at most 50 characters."), `category_error_kind_immutable`,
`category_status_waiting` (reuse spec 004 "Waiting to sync"), `category_status_clash` ("Changed on
another device."), `category_status_not_saved` ("Not saved."), `category_status_in_use` ("Not
deleted: this category is in use."), `category_this_phone` ("This phone: %1$s"),
`category_other_device` ("Other device: %1$s"), `category_keep_mine` / `category_use_theirs` (reuse
the wallet wording), `action_keep_category` ("Keep the category"), `action_archive_instead`
("Archive instead"), `action_delete_anyway`, `action_try_again`, `action_discard` (reuse spec 006),
`category_refusal_not_verified` ("Verify your email address to save this category."),
`category_refusal_gone` ("This category is no longer available."), `category_refusal_other` ("Not
saved. Reference: %1$s"), `category_archive` ("Archive"), `category_archive_title` ("Archive
%1$s?"), `category_archive_body` ("Archived categories stay on your existing transactions and stay
editable, but you can't pick them for new ones. This can't be undone."), `category_delete`
("Delete category"), `category_delete_title` ("Delete %1$s?"), `category_delete_body` ("Deleting is
final."), `category_in_use_note` ("This category is used, so it can't be deleted. You can archive
it instead."), `category_choice_title` ("Choose a category"), `cd_category_icon_<key>` (24),
`cd_category_color_<index>` (8). Spec 004 `sync_deleted_elsewhere` and `action_accept_deletion`
are reused. Arabic: فئة / الفئات, Western digits; never "tag", "label", "bucket", "record",
"entry", "token", "version" or "tombstone" in either language (P-01).

## E2E (`.maestro/007-categories/`, `/speckit-e2e`) — run deferred to the owner's device

Seeds: `verified-user`, `unverified-user` (existing). Second session through `common/api.js`
slot `B` with new actions `listCategories`, `createCategory`, `saveCategory`, `archiveCategory`,
`deleteCategory` (`http.request`, `DELETE`, JSON body), `listPlatformCategories`, `createExpense`
(wallet via the existing `createWallet`, then `PUT /transactions/{id}` with the category). New
common flow `common/open-categories.yaml`. Runner actions `network-off` / `network-on` (spec 006).

| Flow | Scenario |
|---|---|
| `US1-S1-views-and-defaults.yaml` | Expense first with 19 defaults; Income with 7 |
| `US1-S2-defaults-in-arabic.yaml` | Arabic app language: "بقالة"; own name unchanged |
| `US1-S4-archived-section.yaml` | API-archived category under "Archived" |
| `US1-S6-other-device-change-after-pull.yaml` | slot B renames; pull; new name shown |
| `US1-S7-offline-shows-last-known.part1/part2.yaml` | network off; list shown with "Updated" |
| `US1-S8-no-own-note.yaml` | new account: note shown, defaults listed |
| `US2-S1-create-online.yaml` | create "Coffee"; listed |
| `US2-S1-create-offline.part1/2/3.yaml` | network off → create → "Waiting to sync" → network on → mark clears, slot B lists it |
| `US2-S2-kind-from-view.yaml` | FAB from Income preselects Income |
| `US2-S3-kind-read-only.yaml` | edit: kind not changeable |
| `US2-S4-name-errors.yaml` | blank and 51-character names refused |
| `US2-S5-duplicate-name.yaml` | second "Coffee" saved |
| `US2-S6-clash-keep-mine.yaml`, `US2-S6-clash-use-theirs.yaml` | slot B saves while the editor is open; phone saves |
| `US2-S7-deleted-elsewhere-accept.yaml` | slot B deletes; phone edits; only "Accept the deletion" |
| `US2-S8-unverified-asks-to-verify.yaml` | verify prompt, nothing queued |
| `US2-S9-default-read-only.yaml` | default rows have no actions |
| `US3-S1-archive.yaml` | archive; moves to Archived |
| `US3-S2-archive-confirmation.yaml` | dialog text |
| `US3-S3-in-use-no-delete.yaml` | slot B `createExpense` with it; pull; Delete absent, note shown |
| `US3-S4-delete-confirmation.yaml` | dialog names it |
| `US3-S5-delete.yaml`, `US3-S5-delete-never-sent.part1/2/3.yaml` | delete online; offline create+delete sends nothing |
| `US3-S6-in-use-refusal.part1/2.yaml` | network off → delete → slot B `createExpense` → network on → "Not deleted" with "Archive instead" |
| `US3-S7-delete-clash.yaml` | slot B renames; phone deletes; "Delete anyway" / "Keep the category" |

US4 has no flow here (its screens are C6's); it joins C6's checkpoint (SC-007).
