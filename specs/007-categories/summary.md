# Summary: Categories (spec 007, C5)

**Date**: 2026-10-02 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner's phone, committed on branch `007-categories` (ca2576e) · **Backend contract**: `saveme-backend@8aee567` (`categories.yaml` unchanged since e358537); contract QA ran against `f6d66eb`.

## What was built

- **`feature.categories`** (data, domain, ui, di) with Room v4: tables `category`, `platform_category`, `category_reference`, `MIGRATION_3_4`, exported `4.json`; all cleared at sign-out.
- **`CategoryApi`** (list, put, archive, delete with a JSON body, platform list) and **`CategoryReferenceApi`**. PATCH and single-category GET are not used.
- **`CategorySyncHandler`** (record type `category`, user scope, `canRestore = false`): PUT, then archive; an archive-only change skips the PUT; the stored kind is always sent. `CategoryPayload` and field names live in `data` (P-10.6).
- **`DefaultCategoriesService`**: verified-address gate, no plan gate, name length in code points, create/save/archive/delete queued through the sync engine, clash and refusal choices, "Archive instead" / "Keep the category" after a `CATEGORY_IN_USE` refusal, refresh with a 60 s floor, per-wallet references refresh. Building blocks for C6–C8: `observeChoices`, `observeDisplays`, the category resolver (own → platform → wallet references) and the `PendingCategoryUses` hook.
- `CategoriesSyncCoordinator` (sign-in, foreground) and `CategoryReferencesCleanup` as a `WalletDeletionParticipant`.
- **Two MVI screens**: categories list (Expense/Income, own, defaults in the app language, archived, pull to refresh, offline "Updated …", status marks) and category editor (create, edit, status and choices, archive, delete, confirmations). Reached from a `home.categories` row.
- **Design system**: 20 new stroke icons, `CategoryIcons` (24 keys), `CategoryColors` (3:1 tint rule), `CategoryTile`, `CategoryRow`, `CategoryChoiceList` / `CategoryChoiceSheet`, catalogue section; `SegmentedControl` gained an optional `optionModifier`.
- `CATEGORY_IN_USE` in `ApiErrorCode` (never retried); about 93 English/Arabic strings.
- **E2E plumbing**: 32 Maestro flows in `.maestro/007-categories/`, `api.js` category actions, common flows `open-categories`, `create-category`, `seed-category`.

## Results

- **Host QA**: all 29 acceptance scenarios, 26 FRs and 15 edge cases mapped to passing tests (`qa-report.md`). 955 host tests green with `--rerun-tasks`; Roborazzi verify, `assembleDevDebug` and the iOS compile pass. 60 new goldens; 6 Home goldens changed for the new Categories row.
- **Contract QA against the live local backend**: every call the app sends was exercised with the app's headers and bodies (create, replay, stale 409, 422 codes, 403 unverified, 404, archive, DELETE with body incl. `CATEGORY_IN_USE`, missing body → `REQUIRED`, platform list of 26 with every icon and colour known, category references and 404 cleanup). No drift. 19 real responses are now fixtures in `CapturedCategoryResponses.kt`, driving `CategoryCapturedResponsesTest` (12 tests) through the engine and service.
- **Fixed during QA**: the editor showed "This category is no longer available." after the person's own "Accept the deletion" / "Delete anyway" / "Discard"; it now closes silently. A race in `ProfileSyncerTest` (2 of 8 reruns failed) fixed with a `CompletableDeferred`; 15/15 after.
- **Maestro static review**: `selected` → `checked` on the kind control; removed a stray `back`; added one after "Archive instead".

## Deferred to the owner's phone

T040, T048, T061 (flow runs), T070 (quickstart), T071 (full E2E report).

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. After a `CATEGORY_IN_USE` refusal the category returns with "Archive instead" and "Keep the category"; Delete stays hidden until the server reports it unused.
2. The picker offers only own active categories and active defaults (own by name first, then platform order). A partner's, archived or retired category appears only as a transaction's current category.
3. Icons are the 24 keys the default set uses; colours are the 8 wallet accents; any valid server hex still renders.
4. Category references are fetched on demand, at most once a minute per wallet; never by polling every wallet.
5. Two screens from a new Home row; defaults are read-only.
6. Room 3 → 4.
7. PUT only; archive queued with the edit; DELETE sends a JSON body.
8. Delete is offered only when the server says unused and no unsent use exists (C6 binds `PendingCategoryUses`).
9. No plan gate; the verification gate applies.
10. Name limit of 50 counts Unicode code points.
11. Wallet deletion removes that wallet's references.
12. Sync engine resolved lazily (the spec 006 `AppGraphTest` lesson).
13. US4 (picker and display) ships as domain API and design-system components; its end-to-end proof comes with C6.

### Implementation

14. `observeUsedLocally()` added to `CategoriesService`.
15. Save, confirmed archive and confirmed delete close the editor; a clash or refusal shows as the row's mark and opening the row shows the choices.
16. `CategoryChoiceSheet` split into a stateless list plus the sheet wrapper so the list can be screenshotted.
17. "Not deleted" reuses the "not saved" icon and colour with its own text.
18. Tint rule: darken the colour in 5 % steps until 3:1 on a container that is the colour at 14 % over white; only uppercase `#RRGGBB` counts, anything else shows the neutral tile.
19. Name length counts UTF-16 units minus low surrogates (common Kotlin has no `codePointCount`).
20. Refresh: `Offline` when either read is unreachable; `Failed` only when neither was unreachable and one failed; the floor starts only when both succeed. A failed references read still counts toward the per-wallet floor.
21. An unresolvable current category id shows "Category unavailable".
22. An unknown kind on create is refused as `INVALID_ENUM_VALUE`.
23. Dialog buttons are tapped by text in Maestro.

### Owner review

24. **Arabic "فئة" / "الفئات"** for Category (the glossary has no Arabic term).
25. **Arabic clash wording** avoiding "نسخة": "احتفظ بتعديلي", "استخدم تعديل الجهاز الآخر", "اختر ما تريد الاحتفاظ به."
26. **About 20 new icon paths** (`repeat` drawn as `AppIcons.Cycle`) and the **tint rule**.

## Remaining risks

- No flow has run on a device; selector and navigation fixes are unverified.
- Archive/delete confirm buttons share text with the row or button behind the dialog; tapping by text relies on the top-most window matching first.
- US1-S3 (retired default) is manual only.
- A category used once stays in use even after that transaction is deleted (deleted transactions still count), so it can then only be archived.
- The name sort is lowercase, not locale-aware.
- Migration 3 → 4 on a device and the 200-category performance check are proved only by host tests.
- Two QA accounts (`qa007-cat@example.test`, `qa007-unv@example.test`) and one undeletable category remain in the dev database.
