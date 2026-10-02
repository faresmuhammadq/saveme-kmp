# Feature Specification: Categories (client)

**Feature Branch**: `007-categories`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "specs/briefs/C05-categories.md" — client brief C5, wave 1 (per-record writes through the spec 004 sync engine plus list refreshes). Pairs with backend B5 → spec 011.

**Backend contract**: `saveme-backend@8aee567`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/categories.yaml` (frozen in backend spec 011 at `info.version` v1.4, commit `e358537`, unchanged since), `docs/api-conventions.md` (Syncable resources, Writes, Version conflicts, Deleting and restoring, Read-only resources), `docs/openapi/error-catalog.yaml` (`CATEGORY_IN_USE`), `docs/glossary.md` (Category, Category Reference, banned synonyms "tag, label, bucket").
- Backend behaviour: `saveme-backend/specs/011-categories/summary.md` and `docs/adr/0014-platform-owned-categories.md` (user-scoped categories; a platform-owned default set referenced directly, never copied, never archived or hidden by a person; `kind` immutable; archive one-way; delete final and only while unused, else `409 CATEGORY_IN_USE`; Category Reference read-only, wallet-scoped; every write behind the verification gate; no plan limits).
- `saveme-backend/docs/openapi/paths/transactions.yaml` (`category_id` resolution order: own, then platform, then the wallet's category references; a new or changed category must be the recorder's own or a platform one of the same kind).
- Builds on spec 004 (sync engine: queue, clashes, refusals, status; `canRestore`), spec 005 (C3, entitlements: not used here, categories have no plan limit) and spec 006 (C4, wallets: `WalletDeletionParticipant`, the visible-wallet list, the screen, handler and refresh patterns this feature repeats).

## Clarifications

### Session 2026-10-02

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: When the server refuses a waiting deletion because the category is in use, what does the app offer? → A: The category reappears marked "Not deleted: this category is in use" with two choices: "Archive instead" (replaces the waiting deletion with an archive) and "Keep the category" (drops the deletion). Nothing is archived without the person's choice.
- Q: In a shared wallet, may the person pick a partner's category for a new or re-categorised transaction? → A: No. The choice offers only the person's own active categories and the active defaults (the backend refuses any other category unless unchanged); a partner's category is shown read-only and stays selected on a transaction that already carries it.
- Q: What icons and colours can a person choose for their own category? → A: A fixed set from the design system: the category icons (every icon key the default set uses, so defaults and own categories share one look) and the eight accent colours wallets already use; "none" (the default) shows the neutral icon and colour. Any valid `#RRGGBB` colour from the server is still shown as given.
- Q: In the category choice, which group comes first? → A: The person's own categories first (by name), then the defaults (platform order), as two labelled groups — the same order as the categories view.
- Q: How often does the phone fetch a wallet's category references in this wave? → A: On demand only: when a screen showing that wallet's transactions asks (C6), and when a display meets an id it cannot resolve, at most once a minute per wallet; never by polling every wallet.

## User Scenarios & Testing *(mandatory)*

A category says what money was spent on or came from. Every transaction (C6), budget (C8) and
report (C13) leans on it. This feature lets a person see the app's default categories in their
language, keep their own categories on the phone with or without a connection, and gives the
transaction screens one way to offer a category and to show any transaction's category.

### User Story 1 - See my categories and the defaults (Priority: P1)

A person opens their categories and sees, for expenses and for income separately, their own
categories and the app's default categories, each with its icon and colour. Default categories are
named in the app's language; the person's own categories keep the names they typed.

**Why this priority**: Nothing can be categorised until the person can see what is available; the defaults make the app usable from the first transaction without any setup.

**Independent Test**: Sign in as a new person; open Categories: the Expense view lists the 19 default expense categories in the platform's order and the Income view the 7 default income categories, with no own categories; switch the app to Arabic: the defaults are named in Arabic at once, offline too.

**Acceptance Scenarios**:

1. **Given** a signed-in person, **When** they open Categories, **Then** they see an Expense view (shown first) and an Income view, each listing the person's active categories of that kind, then the default categories of that kind, each with its icon, colour and name.
2. **Given** the app language is Arabic (or English), **When** the default categories are shown, **Then** each shows its Arabic (or English) name; switching the language changes those names at once without a connection, while the person's own categories keep the exact names they typed.
3. **Given** a default category the platform has retired, **When** the categories are shown, **Then** it is not listed among the defaults.
4. **Given** the person has archived categories, **When** a view is shown, **Then** they appear in an "Archived" section after the active ones, marked as archived in words.
5. **Given** a category with a change that hasn't reached the server, **When** it is shown, **Then** it is marked "Waiting to sync" (or "Changed on another device" / "Not saved" when that applies).
6. **Given** categories were created, edited, archived or deleted on another device, **When** the app comes back to the foreground or the person pulls to refresh, **Then** the change is shown on this phone; a category deleted elsewhere with no unsent change on this phone disappears.
7. **Given** no connection, **When** the person opens Categories, **Then** the last known categories and defaults are shown with when they were last updated; nothing is blank because of the connection.
8. **Given** the person has no categories of their own, **When** a view is shown, **Then** the defaults are listed and a short note invites them to add their own.

---

### User Story 2 - Create and edit my own categories, even offline (Priority: P1)

A person adds a category of their own by choosing whether it is for expenses or income, typing a
name, and optionally choosing an icon and a colour. They can later rename it or change its icon or
colour. Changes are saved on the phone first and reach the server on their own; if the same
category was changed on another device first, the person sees both versions and chooses.

**Why this priority**: The defaults never fit everyone; the person's own words ("Gam'eya payment", "Mama's medicine") are what makes the ledger theirs, and a lost edit destroys trust (P-04.4).

**Independent Test**: With the phone offline, create an expense category "Coffee" with the "restaurant" icon and the teal colour; it shows in the Expense view marked "Waiting to sync"; go online: the mark clears and `GET /categories` on the server lists it.

**Acceptance Scenarios**:

1. **Given** a signed-in, verified person on a categories view, **When** they create a category with a name and save, **Then** it appears at once among their categories of that view's kind, online or offline, and is sent on its own.
2. **Given** the create form, **When** it opens from the Expense (or Income) view, **Then** the kind is preselected to that view's kind and can be switched before saving.
3. **Given** an existing category, **When** the person edits it, **Then** they can change its name, icon and colour, and the kind is shown but cannot be changed.
4. **Given** a blank name or one longer than 50 characters after trimming, **When** they try to save, **Then** the form says what is wrong next to the name and nothing is saved.
5. **Given** a name that another of the person's categories already has, **When** they save, **Then** it is saved; duplicate names are allowed.
6. **Given** a waiting edit to a category that was changed on another device, **When** it is sent, **Then** the category shows "Changed on another device" with this phone's and the other device's values side by side and "Keep mine" / "Use the other device's"; nothing is applied until the person chooses.
7. **Given** a waiting edit to a category deleted on another device, **When** it is sent, **Then** the category says it was deleted on another device and offers only "Accept the deletion".
8. **Given** a person whose email address is not verified, **When** they try to create or edit a category, **Then** the app asks them to verify their address first (the existing verify prompt) and nothing is queued.
9. **Given** a default category, **When** the person views it, **Then** no edit, archive or delete action is offered.

---

### User Story 3 - Archive or delete a category (Priority: P2)

A person archives a category they no longer want to pick, keeping it on everything already filed
under it, or deletes one they never used. Both work offline. Archiving cannot be undone in this
release; deletion is final. A category already used cannot be deleted, and the app says so.

**Why this priority**: Cleaning up matters but happens far less often than picking and creating.

**Independent Test**: Archive "Coffee": it moves to the Archived section and is no longer offered when picking a category. Create "Typo", delete it after confirming: it disappears from the phone and, once synced, from the server.

**Acceptance Scenarios**:

1. **Given** an active own category, **When** the person chooses Archive and confirms, **Then** it moves to the "Archived" section, is no longer offered when picking a category, still shows on anything already filed under it, and the change is sent on its own.
2. **Given** the archive confirmation, **When** it is shown, **Then** it says archiving can't be undone, that the category stays on existing transactions, and that it stays editable.
3. **Given** an own category the server reports as used, **When** the person views it, **Then** Delete is not offered, and a note says categories in use can be archived instead.
4. **Given** an own category not known to be used, **When** the person chooses Delete, **Then** the confirmation names the category and says deletion is final; nothing happens until they confirm.
5. **Given** they confirm, **When** the deletion is saved, **Then** the category disappears from every screen at once (offline too), and the deletion is sent on its own; a category created on this phone and never sent is removed with nothing sent.
6. **Given** a waiting deletion that the server refuses because the category is in use (used on another device meanwhile), **When** the answer arrives, **Then** the category reappears marked "Not deleted: this category is in use" with "Archive instead" (queues an archive in place of the deletion) and "Keep the category" (drops the deletion); nothing changes until the person chooses.
7. **Given** a waiting deletion of a category that was changed on another device, **When** it is sent, **Then** the category reappears marked "Changed on another device" with "Delete anyway" and "Keep the category"; nothing is deleted on the server until the person chooses.

---

### User Story 4 - Choose a category for a transaction, and see any transaction's category (Priority: P1)

When a transaction is recorded (C6), the person picks one of their active categories or an active
default of the transaction's kind. Wherever a transaction is shown, its category is shown with its
icon, colour and name — the person's own, a default (in the app's language), or, in a shared
wallet, a partner's category exactly as the partner named it.

**Why this priority**: This is the brief's core purpose; C6 cannot record an income or expense without it. The screens that host the picker belong to C6, so this story delivers the picker and the category display as parts C6 places on its screens.

**Independent Test**: Proved without C6 by the picker's and the display's tests over the phone's data (own, default, archived, retired, a partner's reference, an unknown id); end to end with C6's checkpoint ("record a transaction with a default category and a custom one; archive the custom one; the transaction still shows it").

**Acceptance Scenarios**:

1. **Given** an expense (or income) being recorded, **When** the person opens the category choice, **Then** it offers the person's active categories of that kind and the active defaults of that kind, in two labelled groups, and nothing else.
2. **Given** an existing transaction filed under a category that has since been archived (or a default since retired), **When** its category choice opens, **Then** that category is still shown and selected, marked archived, while other archived categories are not offered.
3. **Given** a transaction filed under the person's own category, a default, or an archived one, **When** it is shown, **Then** its category's icon, colour and current name are shown (a default in the app's language).
4. **Given** a shared wallet with a transaction a partner filed under the partner's own category, **When** it is shown, **Then** the partner's category is shown with the partner's name, icon and colour, read-only; the person cannot pick a partner's category for a new transaction (the choice offers only their own and the defaults), but a transaction that already carries one keeps it selected.
5. **Given** a category id the phone cannot resolve yet (a partner's category not yet fetched), **When** it is shown, **Then** a neutral "Category unavailable" placeholder is shown, and the phone fetches that wallet's category references so the next showing resolves it, without a blank row or a crash.

### Edge Cases

- A category created offline, edited, then deleted before it ever synced: nothing reaches the server (spec 004 FR-004).
- An edit to a category with a waiting archive: both are sent together; the archive is never lost behind the edit (the wallets rule, spec 006).
- An archived category is still editable (rename, icon, colour), and its edits sync like any other; it stays out of the category choice.
- The server refuses a waiting change because the category is no longer available (not found): it is marked "Not saved" with that reason; the person can discard it, and the next refresh removes the category.
- The server refuses because the address is not verified although the phone thought otherwise (stale account data): "Not saved" with that reason, "Try again" and "Discard".
- A category whose icon this app version doesn't know: it shows a neutral fallback icon, and saving an edit keeps the unknown value unchanged. Any colour in the server's `#RRGGBB` form is shown as given; a value outside that form shows the neutral colour and is kept unchanged on save.
- A default category's icon this app version doesn't know: the neutral fallback icon.
- The server reports a category kind this app version doesn't know: the category is not offered in the category choice, is listed in neither view, and still resolves for display.
- The platform set is empty on the server (not yet seeded) or could not be read before: the defaults group is empty with a note that defaults couldn't be loaded; the person's own categories still work.
- A default category retired after it was used: existing transactions keep showing it (in the app's language); it leaves the category choice.
- The category open in the editor disappears (deleted elsewhere, found by a refresh): the editor keeps the person's typing and its save becomes a waiting change the server answers (clash or refusal); a category with no waiting change closes back to the list with a short message.
- A wallet is deleted or the person loses access to it: that wallet's category references are removed from the phone.
- Two devices offline both create a category named "Coffee": both are kept (different ids; duplicates allowed).
- Very many categories: any number the server returns is shown (the server pages at up to 200 per request).
- The category choice has nothing to offer for a kind (no own categories and the defaults not loaded): it says no categories are available yet instead of an empty list.
- Signing out removes every category, the cached defaults and every category reference from the phone, with the waiting-changes warning of spec 004.

## Requirements *(mandatory)*

### Functional Requirements

**Categories view**

- **FR-001**: The app MUST keep the person's own categories, the platform's default set and, per wallet, the category references on the phone, and render every category screen, choice and display from that local copy (P-04.1).
- **FR-002**: The categories screen MUST show one view per kind, Expense first and Income second; each view lists, in order: the person's active categories of that kind, then the active default categories of that kind in the platform's order, then an "Archived" section with the person's archived categories of that kind. Own categories, active and archived, are ordered by name (case-insensitive), ties by creation time. Each row shows icon, colour, name, and when one applies a "Waiting to sync" / "Changed on another device" / "Not saved" mark; defaults are marked "Default".
- **FR-003**: Default categories MUST be named in the app's current language (Arabic or English) from the names the platform sends for both languages, switching at once and offline when the language changes; the person's own categories and partners' categories MUST be shown exactly as named, never translated.
- **FR-004**: The app MUST refresh the person's categories and the default set from the server on sign-in, on returning to the foreground (at most once a minute), when the categories screen opens, and when the person pulls to refresh. Offline, it MUST show the last known data with when it was last updated. A refresh that fails for another reason MUST keep the local data and say the categories couldn't be updated, without blocking any action. Before the first refresh after sign-in completes, the screen MUST show a loading state, not the empty note.
- **FR-005**: After a complete read of the person's categories, an own category that is no longer listed, has reached the server at least once, and has no unsent change MUST be removed from the phone. A complete read of the default set MUST replace the cached set (retired entries included, since the server lists them).

**Create and edit (local-first)**

- **FR-006**: Creating a category MUST need a kind (expense or income, preselected from the view it was started from), and a name of 1–50 characters after trimming (counted in Unicode code points, as the server counts them, never in bytes or UTF-16 units); icon and colour are optional, chosen from the design system's category icons (every key the default set uses) and its eight accent colours, or left as "none" (neutral icon and colour).
- **FR-007**: Creates, edits, archives and deletions MUST be saved on the phone first, succeed without a connection, and be sent through the shared sync queue (spec 004) in the person's user scope with the version the person last saw; a new category MUST get a client-generated UUIDv7 (P-04.2–P-04.4). An edit MUST be based on the version shown when the editor was opened, so a refresh that lands while the person is editing produces a clash rather than a silent overwrite.
- **FR-008**: The kind MUST NOT be editable after creation and MUST NOT be sent changed; an `IMMUTABLE_FIELD` refusal at `/kind` MUST be shown next to the kind.
- **FR-009**: Name blankness and length MUST be checked before saving; server field refusals (`OUT_OF_RANGE`, `REQUIRED`, `INVALID_ENUM_VALUE`, `IMMUTABLE_FIELD`) MUST be shown next to the matching field with app copy chosen by code, never the server's text (P-06.5). Duplicate names MUST be accepted without warning.
- **FR-010**: An icon value the app doesn't know MUST be shown with a neutral fallback icon; a colour not in `#RRGGBB` form MUST be shown with the neutral colour; both MUST be preserved unchanged when the category is saved.
- **FR-011**: Default categories MUST be read-only on the phone: no edit, archive or delete action is offered for them, and no write is ever sent for them.

**Clashes and refusals**

- **FR-012**: A clash on a category MUST be shown on that category with both versions of every differing field (name, icon, colour, archived) and the choices "Keep mine" / "Use the other device's" (edit) or "Delete anyway" / "Keep the category" (deletion); nothing is applied until the person chooses (P-04.4).
- **FR-013**: When the server's version is a deletion, the app MUST offer only "Accept the deletion"; an edit MUST NOT bring a deleted category back (deletion is final).
- **FR-014**: A refused change MUST be shown as "Not saved" with a reason chosen by error code: not verified, no longer available, in use (deletion only), a field problem, or an unexpected error with the server's reference; the person can edit (field problems), "Try again" (not verified), or discard. Discarding a never-synced create removes the category from the phone.

**Archive and delete**

- **FR-015**: Archive MUST be confirmed, is one-way in this release, and is queued like an edit; an archived category stays visible in the Archived section, stays editable, keeps showing on everything filed under it, and is never offered in the category choice for a new or re-categorised transaction, except as the transaction's current category (FR-018).
- **FR-016**: Delete MUST be offered only for an own category (active or archived) the server has not reported as in use and that no unsent change on this phone uses (later features report their unsent uses); it MUST be confirmed naming the category and stating deletion is final; on confirmation the category MUST disappear from every screen at once and the deletion be queued as the local tombstone (P-04.5); the data is removed from the phone when the server confirms the deletion, or at once for a category that never reached the server.
- **FR-017**: A deletion refused with `CATEGORY_IN_USE` MUST bring the category back marked "Not deleted: this category is in use" and offer "Archive instead" (the waiting deletion becomes a waiting archive, based on the same version) and "Keep the category" (the deletion is dropped); from then on Delete is not offered for it until the server reports it unused.

**Category choice and display (consumed by C6, C7, C8)**

- **FR-018**: The app MUST provide, for a given kind and an optional current category, the list of choosable categories in two labelled groups: the person's active own categories of that kind (by name), then the active defaults of that kind (platform order); never a partner's category, archived own categories, retired defaults, categories of the other kind or of an unknown kind; except the current category, which MUST stay listed and selected whatever its state, marked archived when it is.
- **FR-019**: The app MUST resolve any category id for display, in this order: the person's own categories (archived included), the default set (retired included), then the category references of the transaction's wallet; when none matches, it MUST show a neutral "Category unavailable" placeholder. Resolution MUST work offline from the phone's copy.
- **FR-020**: The app MUST keep each visible wallet's category references on the phone, read-only, and refresh them from the server when a consumer asks for that wallet and when a display in that wallet finds an id it cannot resolve (at most once a minute per wallet), never by polling every wallet; a complete read replaces the wallet's references; a not-found answer, the wallet's confirmed deletion, or the wallet leaving the phone MUST remove them.

**Gates**

- **FR-021**: Creating, editing, archiving or deleting a category while the account's address is not verified MUST show the existing verify prompt instead of queuing a change the server will refuse.
- **FR-022**: Categories MUST NOT be gated by the person's plan (the backend sets no category limit); no entitlement check is made.

**Sign-out, contract and identity**

- **FR-023**: Sign-out MUST remove every own category, the cached default set and every category reference from the phone with the rest of the account's data (P-09, spec 004 FR-018).
- **FR-024**: All category copy MUST exist in English and Arabic, call the entity "Category" (Arabic "فئة"), never "tag", "label" or "bucket" (backend glossary banned synonyms), never "record", "entry", "version" or "tombstone", and mirror in RTL (P-01, P-06).
- **FR-025**: Every sync mark, "Default" and "Archived" state MUST be conveyed by text, not colour alone; icon and colour choices MUST have spoken descriptions and 48 dp targets; category icons drawn on a colour MUST stay distinguishable (at least 3:1 against their tile); all category screens MUST stay usable at 200% font scale (P-08).
- **FR-026**: The client's error-code list MUST include `CATEGORY_IN_USE` (409, never retried automatically), so the refusal is classified by code (P-02.4; contract-check client mismatch).

### Key Entities *(include if feature involves data)*

- **Category (own)**: id (UUIDv7, client-made), kind (expense or income, immutable), name (1–50, as typed, trimmed), icon key and colour (optional, stored as given), archived-at, in-use flag (server-reported), and the sync members version, change sequence, created/updated/deleted instants. Rides the person's user scope.
- **Default category (platform)**: id, `system_key` (stable, language-independent), kind, names in Arabic and English, icon key, colour, platform position, retired-at, updated-at. Reference data: cached, never written.
- **Category Reference**: per wallet, read-only: category id, owner's member id, kind, name, icon, colour, plus sync members. Produced only by the server.
- **Waiting change (spec 004)**: an own category's unsent create, edit, archive or deletion, with the version it was based on.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a person see the app's default categories in their language, keep their own income and
expense categories on the phone, create and change them with or without a connection, and retire
them safely; and gives the transaction screens (C6), recurring rules (C7) and budgets (C8) one way
to offer a category and to show any transaction's category, including a partner's in a shared
wallet.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Category** (kind `income` / `expense`; never "tag",
"label" or "bucket"), **platform category** (shown as a "Default" category; `system_key`;
**retired**), **archived**, **in use**, **Category Reference**, **Wallet**, **Wallet Member**,
**Transaction**, **tombstone** (internal only). UI says "Category"/"فئة", "Default", "Archived",
"Waiting to sync", "Changed on another device", "Not saved"; never "tombstone", "version",
"scope", "system key", "category reference" or "record" (the word "Reference" appears only before the server's request id in "Not saved. Reference: …", as in spec 006).

### 3. Data model

Adds three phone-side tables: **category** (all `Category` fields including `version`,
`change_seq`, `created_at`, `updated_at`, `deleted_at`, `archived_at`, `in_use`), **platform
category** (all `PlatformCategory` fields, both names), and **category reference** (all
`CategoryReference` fields with its `wallet_id`), all cleared at sign-out. Unsent changes use the
existing spec 004 queue with record type "category" and the person's user scope. The on-device
database version goes up by one (3 → 4) with a migration that keeps all existing data.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Category screens, choice and display render only from the phone's copy | P-04.1, FR-001 | store/service tests over fakes; no API in UI/store |
| Create/edit/archive/delete succeed offline and are queued in the user scope with the base version | P-04.2, P-04.4, FR-007 | service test with the network off; queue contents asserted |
| New categories carry a client UUIDv7 | P-04.3 | id generation test |
| Deletion is a queued deletion; a never-sent create leaves nothing to send | P-04.5, FR-016 | service + engine tests |
| A deleted-elsewhere clash never offers restore | FR-013, backend: deletion final | handler `canRestore = false` test; editor store test |
| Kind never sent changed; never editable after create | FR-008 | handler request test; editor reducer test |
| Default categories are never written | FR-011, ADR-0014 rule 4 | handler/service test: no queue row for a platform id; screen tests: no actions |
| Archived own and retired defaults never offered, except the current category | FR-015, FR-018 | choice-list tests |
| Resolution order own → default → wallet reference → placeholder | FR-019, transactions.yaml | resolver tests |
| Default names follow the app language; own names never translated | FR-003 | display tests in both languages |
| Unknown icon/colour preserved on save | FR-010 | handler request test |
| Own categories with unsent changes survive a refresh that no longer lists them | FR-005 | refresh/merge repository test |
| Every request body carries only schema members; decoding ignores unknown members | P-02.2, P-02.3 | contract tests |
| Failures branch on `code` only (`CATEGORY_IN_USE` included) | P-02.4, FR-026 | contract + error-copy tests |
| Sign-out empties the three category tables | FR-023, P-09 | `DatabaseCleaner` test |
| Migration keeps existing data | spec 004 FR-005 | migration test 3 → 4 |

### 5. API

All under `/api/v1`, frozen in `categories.yaml` (backend spec 011, `e358537`, v1.4).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Own list (refresh) | `GET /categories` (all pages, `cursor`/`limit`) | — | 200; offline/timeout/5xx → keep local; 401 → session ended |
| Create / save (sync engine) | `PUT /categories/{id}` | `CategoryWrite`: `kind, name, icon, color, version` | 200/201; `409 VERSION_CONFLICT` (+`current`, live or tombstone); `422 VALIDATION_FAILED` (`OUT_OF_RANGE` at `/name`, `REQUIRED`, `INVALID_ENUM_VALUE`, `IMMUTABLE_FIELD` at `/kind`, `UNKNOWN_FIELD`), `422 INVALID_ID`; `403 EMAIL_NOT_VERIFIED`; `404 NOT_FOUND`; retryable class per catalogue |
| Archive (sync engine, after the save) | `POST /categories/{id}/archive` | `CategoryVersionOnly` | 200; 409 (+`current`); 403; 404 |
| Delete (sync engine) | `DELETE /categories/{id}` **with a JSON body** | `CategoryVersionOnly` | 200 (tombstone); `409 CATEGORY_IN_USE`; `409 VERSION_CONFLICT` (+`current`); 403; 404 |
| Default set | `GET /platform-categories` (one page) | — | 200; offline/5xx → keep cache |
| Wallet references | `GET /wallets/{id}/category-references` (all pages) | — | 200; 404 → remove that wallet's references |

`GET /categories/{id}` and `PATCH /categories/{id}` are not used: every save is a whole-record
`PUT` (the wallets decision), and a refusal or clash carries what the phone needs. Category writes
are not idempotency-keyed (idempotent by id and version). `change_seq` is stored but not
interpreted in this wave. Categories ride the user feed (`GET /sync/user/changes`), and the batch
upload carries `category` items; switching to them is the wave 2 sync spec (C14 batch), not this one.

#### Contract changes since last spec

Baseline `saveme-backend@a42f5d0` (spec 006) → `8aee567` (backend working tree has uncommitted
edits outside `categories.yaml`; `categories.yaml` itself is unchanged since `e358537`):

- **`meta.sync_cursor`** always present on the two change feeds, and the precise **tombstone purge horizon** behind `410 SYNC_CURSOR_EXPIRED`. Follow-up: not in scope of this spec (C14 wave 2).
- **Batch upload** (`POST /sync/batch`, item types include `category` `upsert`/`delete`; no idempotency key). Follow-up: not in scope of this spec (C14 wave 2).
- **Occurrence**: a server-created record that accepts a `PUT` but never a create or `DELETE`. Follow-up: not in scope of this spec (C7).
- **Client mismatch**: `CATEGORY_IN_USE` (409, never; added to the catalogue before the baseline and noted as a C5 follow-up by spec 006) is missing from the client's codes. Handled by FR-026.

### 6. Authorization and entitlements

Categories are user-scoped: only the owner reads or writes one; anyone else gets 404, which the
app treats as "no longer available". Default categories are read by everyone and written by
nobody (FR-011). In a shared wallet, a partner's category is shown only through its read-only
Category Reference, which any active member may read (backend wallets permission matrix row T-01);
nobody can change it from this app. Every write needs a verified address (FR-021). No plan
feature gates categories (FR-022).

### 7. Events

Not applicable: the client publishes no domain events. It consumes the existing `ApiEvent`s
(session ended, email not verified), the sync engine's status, and C4's wallet deletion hook
(`WalletDeletionParticipant`) to drop a deleted wallet's category references.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 See categories | Unit (list building, ordering, language names, merge on refresh) + screenshots + E2E | tests; `.maestro/007-categories/US1-*` |
| US2 Create & edit | Unit (editor reducer/store, handler, refusal mapping, clash view) + contract tests + E2E (API second session for clash) | tests; `US2-*` |
| US3 Archive & delete | Unit (queue, in-use rule, tombstone, in-use refusal) + E2E | tests; `US3-*` (S6 needs a transaction using the category, seeded through the API) |
| US4 Choose & display | Unit (choice list, resolver, reference refresh) + component screenshots | tests; end to end with C6's checkpoint |

Offline scenarios are driven end to end by the runner's `network-off` / `network-on` steps (spec
006); otherwise they are proved by unit and persistence tests.

### 9. Open questions

- **Category icon set and the Arabic word for "Category"**: the backend fixes 24 icon keys through the default set and leaves the rest to the client; the glossary has no Arabic term for Category. Decided by: plan (icon set, owner design review) and assumed default "فئة" (owner review). Non-blocking.
- **References on the wallet feed (backend B14)**: learning about a partner's new category without asking per wallet arrives with C14 wave 2. Non-blocking.
- **Hiding defaults a person dislikes**: not offered by the backend in MVP (ADR-0014). Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can create a category in under 20 seconds, online or offline, and it is listed the moment they save.
- **SC-002**: 100% of categories created, edited, archived or deleted offline reach the server, or are shown as needing the person's decision, once a connection returns.
- **SC-003**: Zero category edits are silently overwritten or dropped: every clash and refusal is shown on the category.
- **SC-004**: Switching the app language renames 100% of the default categories shown, with no connection, in under 1 second.
- **SC-005**: The categories view for 200 own categories plus the 26 defaults is shown within 1 second of opening, from the phone's copy, with or without a connection.
- **SC-006**: Every transaction category id the phone holds a source for is shown with a name; ids it cannot resolve show the placeholder, never a blank row or a crash.
- **SC-007**: The brief's integration checkpoint passes end to end once C6 exists: a transaction recorded with a default category and with a custom one; the custom one archived; the transaction still shows it.

## Assumptions

- Wave 1: per-record writes through the spec 004 engine and list refreshes; the user change feed, the wallet feed's references and batch upload arrive with C14 wave 2 (backend B14 is built but its client switch is out of scope).
- Until C6 no transactions exist on the phone: "in use" comes only from the server's `in_use`, and C6 reports its unsent uses through a hook this feature exposes. The category choice and display are delivered as building blocks; C6 places them on its screens.
- The backend's default set is seeded on the dev stack (`php artisan db:seed`); without it the defaults group is empty (Edge Cases).
- Categories have no plan limit (backend spec 011 assumed default 16).
- The categories screen is reached from a "Categories" row on Home, beside "Wallets"; Home stays the signed-in start screen.
