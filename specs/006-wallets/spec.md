# Feature Specification: Wallets & Membership (client)

**Feature Branch**: `006-wallets`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "specs/briefs/C04-wallets.md" — client brief C4, wave 1 (per-record writes through the spec 004 sync engine). Pairs with backend B4 → spec 010.

**Backend contract**: `saveme-backend@a42f5d0`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/wallets.yaml` (frozen in backend spec 010, commit `310aa24`, unchanged since), `docs/api-conventions.md` (Syncable resources, Writes, Version conflicts, Deleting and restoring), `docs/openapi/error-catalog.yaml`, `docs/glossary.md` (Wallet, Wallet Member).
- Backend behaviour: `saveme-backend/specs/010-wallets-membership/summary.md` (type immutable; version in the JSON body, DELETE with a body; deletion final; archive one-way; owner-only edit, archive, delete and transfer; `EMAIL_NOT_VERIFIED` on writes; `FEATURE_NOT_ENTITLED` on create; currency locked once anything posted).
- Builds on spec 004 (sync engine: queue, clashes, refusals, status) and spec 005 / C3 (entitlements gate `Entitlements.can`/`quota`/`allowsAnother`, `FEATURE_NOT_ENTITLED` copy and refresh), which lands on `main` before this feature is implemented.

## Clarifications

### Session 2026-10-02

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Should archiving wait in the sync queue like an edit when offline, or need a connection? → A: Queued like an edit (P-04.2 covers every change to a synced record); it travels with the wallet's waiting change and is sent after any field edit.
- Q: Do archived wallets count in the per-currency totals? → A: No; archived wallets keep their own balance on their card in the "Archived" section but leave the totals.
- Q: What icons and colours can a person choose? → A: A fixed set from the design system: the six type icons as icon keys, and a palette of eight design-system colours as `#RRGGBB` keys; "none" (the default) shows the type icon and the default tile colour.
- Q: How is a liability's opening amount entered, and can it start in credit? → A: The person enters the amount owed as a non-negative number; the app stores it negated. A liability starting in credit is not offered at creation (rare; the balance can still go positive through later transactions).
- Q: What happens when an unverified person tries to create or edit a wallet? → A: The existing verify prompt is shown and nothing is queued; viewing stays available. A server refusal for that reason (stale account data) is still handled as "Not saved" with "Try again".

## User Scenarios & Testing *(mandatory)*

A wallet is the container every later money feature lives in: categories, transactions, budgets
and sharing all start from one. This feature lets a person keep their wallets on the phone, with
or without a connection, and see what each holds.

### User Story 1 - Create a wallet, even offline (Priority: P1)

A person creates a wallet by choosing its type, a name, a currency (their default currency is
preselected), an opening balance, and optionally an icon and a colour. It appears at once in their
wallet list, whether or not the phone is online, and reaches the server on its own.

**Why this priority**: Without a wallet nothing else in the app can be recorded; creation must work on patchy networks (P-04.2).

**Independent Test**: With the phone offline, create a "Cash" wallet with an opening balance of 250.00 EGP; it shows in the list marked "Waiting to sync"; go online; the mark clears and the wallet is on the server.

**Acceptance Scenarios**:

1. **Given** a signed-in, verified person on the wallet list, **When** they create a wallet with type, name, currency and opening balance, **Then** it appears in the list at once with that name, type icon and a balance equal to the opening balance.
2. **Given** the create form, **When** it opens, **Then** the currency is preselected to the person's default currency, and every currency the app can format can be chosen instead.
3. **Given** no connection, **When** the person creates a wallet, **Then** it is saved on the phone, shown in the list marked "Waiting to sync", and sent once a connection returns without any action from them.
4. **Given** the type "Savings", **When** it is shown anywhere, **Then** it reads "Savings" in English and "حصالة" in Arabic.
5. **Given** a liability type (credit card or loan), **When** the person enters the opening amount, **Then** they enter the amount owed, and the wallet shows it as an amount owed, never as a positive balance.
6. **Given** a blank or over-long name (more than 100 characters), **When** they try to save, **Then** the form says what is wrong next to the name and nothing is saved.
7. **Given** the person's plan does not allow another wallet, **When** they try to create one, **Then** the app says their plan doesn't include more wallets instead of showing the form; and if the server refuses a create for that reason anyway, the wallet is marked "Not saved" with the same explanation and can be discarded.
8. **Given** a person whose email address is not verified, **When** they try to create a wallet, **Then** the app asks them to verify their address first (the existing verify prompt) and nothing is queued.

---

### User Story 2 - See my wallets and what they hold (Priority: P1)

A person sees every wallet they belong to, each with its balance and their role, a total for each
currency, and can open a wallet to see its details. Changes made on another device appear after the
app refreshes.

**Why this priority**: The list is the app's home for money; balances must be trustworthy and honest about what hasn't synced.

**Independent Test**: Seed two EGP wallets and one USD wallet; the list shows three cards, an EGP total and a separate USD total; open one to see its detail.

**Acceptance Scenarios**:

1. **Given** the person belongs to wallets, **When** they open the wallet list, **Then** each active wallet shows its name, type icon, balance in its own currency, and the person's role when it is not "Owner".
2. **Given** wallets in more than one currency, **When** the list is shown, **Then** it shows one total per currency (liabilities subtracting from it) and never a total that adds different currencies.
3. **Given** a wallet with a change that hasn't reached the server, **When** it is shown in the list or its detail, **Then** it is marked "Waiting to sync", and its balance is the last balance from the server adjusted by the changes still on this phone.
4. **Given** a wallet was created or edited on another device, **When** the app comes back to the foreground or the person pulls to refresh the list, **Then** the change is shown on this phone.
5. **Given** a wallet was deleted on another device, or the person lost access to it, **When** the list refreshes and this phone has no unsent change to it, **Then** it disappears from the phone.
6. **Given** no wallets, **When** the list is shown, **Then** an empty state invites the person to create their first wallet.
7. **Given** the person opens a wallet, **When** the detail shows, **Then** it shows the balance, type, currency, whether it is archived, the members with their roles, and the actions the person's role allows.
8. **Given** no connection, **When** the person opens the list or a wallet, **Then** the last known data is shown with when it was last updated; nothing is blank because of the connection.

---

### User Story 3 - Edit a wallet, and decide when it clashes (Priority: P1)

The owner renames a wallet, changes its icon, colour or opening balance, or its currency while
nothing has been recorded in it. Edits are saved on the phone first. If someone changed the same
wallet on another device first, the person sees both versions and chooses.

**Why this priority**: Wallets are long-lived; fixing a name or opening balance is routine, and a silently lost edit to money data destroys trust (P-04.4).

**Independent Test**: Rename a wallet offline; it shows the new name marked "Waiting to sync"; rename it differently from another session; go online: the phone shows both names with "Keep mine" and "Use the other device's".

**Acceptance Scenarios**:

1. **Given** the owner on a wallet's detail, **When** they edit the name, icon, colour or opening balance and save, **Then** the change shows at once (offline too) and is sent on its own.
2. **Given** the wallet's type, **When** the owner edits the wallet, **Then** the type is shown but cannot be changed.
3. **Given** a wallet whose currency is locked because something was recorded in it, **When** the owner edits it, **Then** the currency is shown read-only with a note saying it can't change once transactions exist.
4. **Given** the server refuses a currency change because the wallet's currency became locked meanwhile, **When** the answer arrives, **Then** the change is marked "Not saved" with that reason next to the currency, and the person can edit it or discard it.
5. **Given** a waiting edit to a wallet that was changed on another device, **When** it is sent, **Then** the detail shows "Changed on another device" with this phone's and the other device's values side by side and "Keep mine" / "Use the other device's"; nothing is applied until the person chooses.
6. **Given** that choice, **When** the person keeps theirs, **Then** their edit is sent again and wins; **When** they take the other device's, **Then** their edit is dropped and the other device's values show.
7. **Given** a waiting edit to a wallet deleted on another device, **When** it is sent, **Then** the detail says the wallet was deleted on another device and offers only "Accept the deletion"; accepting removes the wallet from the phone.
8. **Given** a member who is not the owner, **When** they view the wallet, **Then** no edit, archive, delete or transfer action is offered.

---

### User Story 4 - Archive or delete a wallet (Priority: P2)

The owner archives a wallet they no longer use but want to keep, or deletes one for good. Both work
offline. Archiving cannot be undone in this release; deletion is final.

**Why this priority**: Cleaning up is needed but less frequent than creating and editing.

**Independent Test**: Archive a wallet: it moves to an "Archived" section and leaves the totals. Delete another after confirming: it disappears from the phone and, once synced, from the server.

**Acceptance Scenarios**:

1. **Given** the owner on an active wallet's detail, **When** they choose Archive and confirm, **Then** the wallet moves to the list's "Archived" section, keeps its balance and members, no longer counts in the totals, and the change is sent on its own.
2. **Given** the archive confirmation, **When** it is shown, **Then** it says archiving can't be undone and the wallet stays readable and editable.
3. **Given** the owner chooses Delete, **When** the confirmation is shown, **Then** it names the wallet and says deletion is final and removes everything recorded in it; nothing happens until they confirm.
4. **Given** they confirm, **When** the deletion is saved, **Then** the wallet and everything recorded in it disappear from every screen at once (offline too), and the deletion is sent on its own.
5. **Given** a wallet created on this phone and never sent, **When** it is deleted, **Then** nothing is sent to the server for it.
6. **Given** a waiting deletion of a wallet that was changed on another device, **When** it is sent, **Then** the wallet reappears marked "Changed on another device" with "Delete anyway" and "Keep the wallet"; nothing is deleted on the server until the person chooses.

---

### User Story 5 - See who is in a wallet (Priority: P2)

Every member can see the wallet's members and their roles, and their own role. The owner can hand
the wallet to another member when one exists.

**Why this priority**: Only owners exist until sharing arrives (C9), but the role already decides what each person may do, and the member list is where sharing will grow.

**Independent Test**: Open a wallet: the members section shows the person as "You" with the role "Owner"; no transfer action is shown because nobody else is a member.

**Acceptance Scenarios**:

1. **Given** a wallet, **When** its detail shows the members, **Then** each member shows their display name (or a neutral placeholder when they have none), their role, and "You" for the person; no email address or other detail of another person is shown.
2. **Given** the person is the owner and the wallet has no other member, **When** the detail shows, **Then** no "Transfer ownership" action is offered.
3. **Given** the owner of a wallet that has another active member, **When** they choose "Transfer ownership", pick that member and confirm while online, **Then** the member becomes the owner, the person becomes an editor, and the edit actions disappear for them.
4. **Given** no connection, **When** the owner tries to transfer ownership, **Then** the app says it needs a connection and nothing changes.
5. **Given** the chosen member is no longer a member, or the wallet changed meanwhile, **When** the transfer is refused, **Then** the app says so, refreshes the wallet and its members, and nothing changes.

### Edge Cases

- A wallet created offline, edited, then deleted before it ever synced: nothing reaches the server (spec 004 FR-004).
- An edit to a wallet with a waiting archive: both are sent together; the archive is never lost behind the edit.
- An archived wallet is still editable by its owner, and its edits sync like any other.
- The phone's last list refresh missed a wallet deleted elsewhere but this phone has a waiting edit to it: the edit is sent, the server answers with the deletion, and the person accepts it (US3-S7); the wallet is not removed silently before that.
- The server refuses a waiting change because the person lost access (not found) or is no longer the owner (forbidden): the change is marked "Not saved" with that reason, the person can discard it, and the wallet is refreshed or removed at the next list refresh.
- The server refuses because the address is not verified or the plan doesn't allow it, although the phone thought otherwise (stale account data): the change is "Not saved" with that reason and offers "Try again" (resend unchanged, after the person verifies or their plan changes) and "Discard".
- A wallet whose icon or colour was set to a value this app version doesn't know: it shows the type's icon and the default colour, and saving an edit keeps the unknown value unchanged.
- Very large amounts: any balance or opening balance the server accepts (up to ±9,007,199,254,740,991 minor units) is shown without loss.
- A currency with 0 or 3 fraction digits (JPY, KWD): amounts are entered and shown with the currency's digits.
- Duplicate wallet names are allowed.
- Signing out removes every wallet and member from the phone, with the waiting-changes warning of spec 004.
- The wallet open on screen disappears (deleted elsewhere or access lost, found by a refresh): the detail closes back to the list with a short message; an open editor keeps the person's typing and its save becomes a waiting change that the server answers (clash or refusal).
- The server reports a wallet type this app version doesn't know: the wallet is shown with a generic icon and "Other" type label, read-only for its type, and saved back with the type unchanged.
- A wallet's membership changes elsewhere (later, C9): the next refresh of the wallet updates the member list and the person's role, and the actions offered follow the new role.

## Requirements *(mandatory)*

### Functional Requirements

**Wallet list and detail**

- **FR-001**: The app MUST keep the person's wallets and their members on the phone and render every wallet screen from that local copy (P-04.1).
- **FR-002**: The wallet list MUST show every live wallet the person belongs to, active ones first in creation order, then an "Archived" section; each with name, type icon (or its chosen icon), colour, balance in its own currency, a "Waiting to sync" / "Changed on another device" / "Not saved" mark when one applies, and the person's role when it is not Owner.
- **FR-003**: The list MUST show one total per currency over active wallets, liabilities counted with their (negative) sign, and MUST NOT add amounts in different currencies.
- **FR-004**: A wallet's displayed balance MUST be the last balance the server reported plus the effect of this phone's unsent changes (a changed opening balance now; unsent transactions once C6 exists). Before the wallet's first sync, it is the opening balance.
- **FR-005**: Liability wallets (credit card, loan) MUST be shown as an amount owed; expenses or negative balances MUST NOT use the error colour (P-03.5).
- **FR-006**: The wallet detail MUST show balance, type, currency, archived state, the members with roles, sync state, and only the actions the person's role allows; it is the entry point C6 builds transactions on.
- **FR-007**: The app MUST refresh the wallet list from the server on sign-in, on returning to the foreground (at most once a minute), when the list opens, and when the person pulls to refresh; and refresh a wallet's members when its detail opens. Offline, it MUST show the last known data with when it was last updated. A refresh that fails for another reason MUST keep the local data and say the list couldn't be updated, without blocking any action. Before the first refresh after sign-in completes, the list MUST show a loading state, not the empty state.
- **FR-008**: After a complete list refresh, a wallet that is no longer listed and has no unsent change MUST be removed from the phone together with its members (deleted elsewhere or access lost). A wallet with an unsent change MUST be kept until its change is resolved.

**Create and edit (local-first)**

- **FR-009**: Creating a wallet MUST need a type (cash, checking, savings, credit card, loan, investment), a name of 1–100 characters after trimming, a currency, and an opening balance; icon and colour are optional, chosen from the design system's fixed set (six type icons, eight colours) or left as "none". The currency MUST default to the profile's default currency.
- **FR-010**: Creates, edits, archives and deletions MUST be saved on the phone first, succeed without a connection, and be sent through the shared sync queue (spec 004) with the version the person last saw; a new wallet MUST get a client-generated UUIDv7 (P-04.2–P-04.4). An edit MUST be based on the version shown when the editor was opened, so a refresh that lands while the person is editing produces a clash rather than a silent overwrite.
- **FR-011**: The type MUST NOT be editable after creation.
- **FR-012**: The currency MUST be read-only once the server reports it locked, and an `IMMUTABLE_FIELD` refusal at `/currency` MUST be shown next to the currency.
- **FR-013**: Amounts MUST be entered and stored as integer minor units with the currency's fraction digits (P-03). For liability types the person enters the amount owed (zero or more) and the app stores it negated; for other types a negative opening balance MUST be possible.
- **FR-014**: Name length and blankness MUST be checked before saving; server field refusals (`OUT_OF_RANGE`, `REQUIRED`, `INVALID_ENUM_VALUE`, `IMMUTABLE_FIELD`) MUST be shown next to the matching field with app copy chosen by code, never the server's text (P-06.5).
- **FR-015**: An icon or colour value the app doesn't know MUST fall back to the type icon and the default colour for display, and MUST be preserved unchanged when the wallet is saved.

**Clashes and refusals**

- **FR-016**: A clash on a wallet MUST be shown on its detail with both versions of every differing field and the choices "Keep mine" / "Use the other device's" (edit) or "Delete anyway" / "Keep the wallet" (deletion); nothing is applied until the person chooses (P-04.4).
- **FR-017**: When the server's version is a deletion, the app MUST offer only "Accept the deletion"; an edit MUST NOT bring a deleted wallet back (deletion is final).
- **FR-018**: A refused change MUST be shown as "Not saved" with a reason chosen by error code: not verified, plan doesn't allow it, not the owner, no longer available, or a field problem; the person can edit (field problems), "Try again" (not verified, plan), or discard. Discarding a never-synced create removes the wallet from the phone.

**Archive and delete**

- **FR-019**: Only the owner MUST be offered Archive; it MUST be confirmed, is one-way in this release, and an archived wallet stays readable and editable by its owner and leaves the totals.
- **FR-020**: Only the owner MUST be offered Delete; it MUST be confirmed naming the wallet and stating that deletion is final; on confirmation the wallet, its members and (from C6) everything recorded in it MUST disappear from every screen at once and the deletion be queued as the local tombstone (P-04.5); the data is removed from the phone when the server confirms the deletion, or at once for a wallet that never reached the server.

**Members and roles**

- **FR-021**: The detail MUST list the wallet's active members with display name (or a placeholder), role and a "You" marker, and MUST NOT show another person's email or other attributes.
- **FR-022**: The person's role on each wallet MUST gate every edit action: only an owner sees edit, archive, delete and transfer.
- **FR-023**: "Transfer ownership" MUST be offered only to the owner, only when another active member exists, and only when no change to the wallet is waiting to sync; it needs a connection (membership is written only by the server), names an active member, MUST be confirmed, and on success the person becomes an editor.

**Gates**

- **FR-024**: Creating a wallet MUST first ask the entitlements gate (C3) whether the person's plan allows another wallet, counting the live wallets they own (archived included); when not, the app MUST explain instead of opening the form. The server's `FEATURE_NOT_ENTITLED` stays the authority and is handled by FR-018 and C3's refresh.
- **FR-025**: Creating or editing a wallet while the account's address is not verified MUST show the existing verify prompt instead of queuing a change the server will refuse.

**Sign-out and identity**

- **FR-026**: Sign-out MUST remove every wallet and member from the phone with the rest of the account's data (P-09, spec 004 FR-018).
- **FR-027**: All wallet copy MUST exist in English and Arabic, use "Wallet" / "محفظة" only, show "Savings" as "حصالة" in Arabic, and mirror in RTL (P-01, P-06).
- **FR-028**: Every sync mark ("Waiting to sync", "Changed on another device", "Not saved"), role and archived state MUST be conveyed by text, not colour alone; icon and colour choices MUST have spoken descriptions and 48 dp targets; all wallet screens MUST stay usable at 200% font scale (P-08).

### Key Entities *(include if feature involves data)*

- **Wallet**: id (UUIDv7, client-made), type (fixed enum, immutable), name, currency (ISO 4217, lockable), opening balance and balance (integer minor units), icon key and colour (optional, stored as given), archived-at, currency-locked flag, the person's role, and the sync members version, change sequence, created/updated/deleted instants. Is itself the wallet sync scope.
- **Wallet Member**: one person's membership of one wallet: member user id, display name (optional), role (owner, editor, viewer), plus sync members. Read-only on the phone.
- **Waiting change (spec 004)**: a wallet's unsent create, edit, archive or deletion, with the version it was based on.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a person keep their wallets — the containers of all their money — on the phone, create and
change them with or without a connection, see balances and per-currency totals they can trust, and
see who is in each wallet and what their own role allows. Categories (C5), transactions (C6) and
sharing (C9) are built on it.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Wallet** (محفظة; never "account", "purse" or "fund"),
**Wallet Member**, **role** (owner, editor, viewer), **type** (cash, checking, savings shown as
"حصالة", credit card, loan, investment), **liability** (credit card, loan: shown as an amount owed),
**opening balance**, **balance**, **archived**, **currency locked**, **ownership transfer**,
**tombstone** (internal only). UI says "Waiting to sync", "Changed on another device", "Not saved",
"Archived", "Delete"; never "tombstone", "version" or "scope".

### 3. Data model

Adds two phone-side tables mirroring the server's syncable shape: **wallet** (all `Wallet` fields
including `version`, `change_seq`, `created_at`, `updated_at`, `deleted_at`, `archived_at`,
`currency_locked`, `my_role`, `balance_minor`) and **wallet member** (`member_user_id`,
`display_name`, `role` plus sync members), both cleared at sign-out. Unsent changes use the
existing spec 004 queue with record type "wallet" and the wallet's own scope. The on-device
database version goes up by one with a migration that keeps all existing data.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Wallet screens render only from the phone's copy | P-04.1, FR-001 | store tests over a fake repository; no API in UI/store |
| Create/edit/archive/delete succeed offline and are queued with the base version | P-04.2, P-04.4, FR-010 | repository test with the network off; queue contents asserted |
| New wallets carry a client UUIDv7 | P-04.3 | id generation test |
| Deletion is a queued deletion, never a local-only removal; a never-sent create leaves nothing to send | P-04.5, FR-020, US4-S5 | repository + engine tests |
| A deleted-elsewhere clash never offers restore | FR-017, backend: deletion final | handler `canRestore = false` test; detail store test |
| Money is `Long` minor units with currency digits; liabilities stored negative | P-03, FR-013 | amount parsing tests (EGP, JPY, KWD, liability) |
| No total adds two currencies | P-03, FR-003 | totals test |
| Type never sent changed; currency read-only when locked | FR-011, FR-012 | editor reducer tests; handler request test |
| Unknown icon/colour preserved on save | FR-015 | handler request test |
| Only owners see owner actions | FR-022 | detail reducer tests per role |
| Wallets with unsent changes survive a list refresh that no longer lists them | FR-008 | refresh/merge repository test |
| Every request body carries only schema members; decoding ignores unknown members | P-02.2, P-02.3 | contract tests |
| Failures branch on `code` only | P-02.4 | contract + error-copy tests |
| Sign-out empties wallet tables | FR-026, P-09 | `DatabaseCleaner` test |
| Migration keeps existing data | spec 004 FR-005 | migration test 2 → 3 |

### 5. API

All under `/api/v1`, frozen in `wallets.yaml` (backend spec 010, `310aa24`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| List (refresh) | `GET /wallets` (all pages, `cursor`/`limit`) | — | 200; offline/timeout/5xx → keep local; 401 → session ended |
| One wallet (refresh after transfer refusal) | `GET /wallets/{id}` | — | 200 (live or tombstone); 404 → remove locally if no waiting change |
| Create / save (sync engine) | `PUT /wallets/{id}` | `WalletWrite`: `type, name, currency, opening_balance_minor, icon, color, version` | 200/201; `409 VERSION_CONFLICT` (+`current`, live or tombstone); `422 VALIDATION_FAILED` (`OUT_OF_RANGE`, `REQUIRED`, `INVALID_ENUM_VALUE`, `IMMUTABLE_FIELD` at `/type` or `/currency`, `UNKNOWN_FIELD`); `403 FORBIDDEN`, `EMAIL_NOT_VERIFIED`, `FEATURE_NOT_ENTITLED`; `404 NOT_FOUND`; retryable class per catalogue |
| Archive (sync engine, after the save) | `POST /wallets/{id}/archive` | `VersionOnly` | 200; 409 (+`current`); 403; 404 |
| Delete (sync engine) | `DELETE /wallets/{id}` **with a JSON body** | `VersionOnly` | 200 (tombstone); 409 (+`current`); 403; 404 |
| Members | `GET /wallets/{id}/members` (all pages) | — | 200; 404 → wallet gone |
| Transfer (online only) | `POST /wallets/{id}/transfer-ownership` | `OwnershipTransfer`: `user_id, version` | 200; 409; 422 `INVALID_ENUM_VALUE` at `/user_id`; 403; 404 |

`PATCH /wallets/{id}` is not used: every save is a whole-record `PUT`, which the sync engine already
replays safely. Wallet writes are not idempotency-keyed (idempotent by id and version). The
wallet's `change_seq` is stored but not interpreted in this wave (it also moves on membership
changes). The change feed and batch upload (backend B14) arrive with the wave 2 sync spec.

#### Contract changes since last spec

Baseline `saveme-backend@56b44fb` (spec 004) → `a42f5d0`:

- **`CATEGORY_IN_USE`** (409, never) added — categories. Follow-up: not in scope of this spec (C5).
- **`CURRENCY_MISMATCH`** (422, field, after-change) added — transactions across currencies. Follow-up: not in scope of this spec (C6).
- **Batch upload** (`POST /sync/batch`), **`meta.sync_cursor`** always present on the change feeds, and the precise **tombstone purge horizon** for `410 SYNC_CURSOR_EXPIRED`. Follow-up: not in scope of this spec (C14 wave 2; B14 is designed, not built, and its docs are still uncommitted in the backend).
- **Validation response** now lists its field-level codes, among them `IMMUTABLE_FIELD` and `CURRENCY_MISMATCH`. Handled by FR-012 and FR-014 (field codes are read from `errors[].code`).
- **Client mismatches**: none reported by the script. `FEATURE_NOT_ENTITLED`, which wallet create returns, is added to the client's codes by spec 005 (C3, FR-018 there), a prerequisite of this feature.

### 6. Authorization and entitlements

The server's permission matrix (backend spec 010 §6) is the authority; the app only hides what the
person's `my_role` would be refused:

| Action | Owner | Editor | Viewer |
|---|---|---|---|
| See wallet, balance, members | yes | yes | yes |
| Edit, archive, delete, transfer ownership | yes | no | no |

A non-member never sees the wallet (the server answers 404). Every write needs a verified address
(FR-025). `wallets.max` gates creation through C3's `Entitlements.allowsAnother(Wallets, n)` with
`n` = live wallets the person owns, archived included (FR-024); it is unlimited on every launch plan.
Actions inside a wallet someone else owns are never blocked by the person's own plan (C3 gate rule 2).

### 7. Events

Not applicable: the client publishes no domain events. It consumes the existing `ApiEvent`s
(session ended, email not verified, feature not entitled from C3) and the sync engine's status.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Create | Unit (editor reducer/store, amount parsing, gates) + repository/handler + E2E | tests; `.maestro/006-wallets/US1-*` |
| US2 List & detail | Unit (totals, displayed balance, merge on refresh) + screenshots + E2E (API-seeded, second session) | tests; `US2-*` |
| US3 Edit & clash | Unit (handler, refusal mapping, clash view) + E2E (API second session for clash) | tests; `US3-*` |
| US4 Archive & delete | Unit (queue, cascade, tombstone) + E2E | tests; `US4-*` |
| US5 Members | Unit (role gating, transfer store) + contract tests + E2E for S1/S2 | tests; `US5-*`; S3–S5 need a second member (C9) |

Offline scenarios are driven end to end only if the runner can cut the phone's route to the
backend between two parts of a flow (plan decides); otherwise they are proved by unit and
persistence tests, as in spec 004.

### 9. Open questions

- **Pending postings in the balance** (FR-004): the shape of "unsent transactions" belongs to C6. Decided by: C6 spec; this feature exposes the hook. Non-blocking.
- **Wallet colour palette and icon set**: not defined by the backend (stored, never interpreted). Decided by: plan, from the design system canvas; owner design review. Non-blocking.
- **Membership on the user feed (backend ADR-0017)**: learning about shared or lost wallets without a list refresh arrives with C14 wave 2. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can create a wallet in under 30 seconds, online or offline, and it is in their list the moment they save.
- **SC-002**: 100% of wallets created, edited, archived or deleted offline reach the server, or are shown as needing the person's decision, once a connection returns.
- **SC-003**: Zero wallet edits are silently overwritten or dropped: every clash and refusal is shown on the wallet.
- **SC-004**: Balances and totals are exact to the minor unit for every currency the app formats, and no screen ever shows a sum across two currencies.
- **SC-005**: The wallet list for 50 wallets is shown within 1 second of opening, from the phone's copy, with or without a connection.
- **SC-006**: Non-owners are never offered an action the server would refuse for their role.
- **SC-007**: The brief's integration checkpoint passes end to end: a wallet created on the phone syncs; an edit made in a second session shows on the phone after refresh; a stale edit shows the clash choice.

## Assumptions

- Spec 005 (C3) is on `main` before implementation: `Entitlements`, `QuotaFeature.Wallets`, `FEATURE_NOT_ENTITLED` in the client's codes with its copy and refresh, and unlimited (`null`) quotas read correctly.
- Wave 1: per-record writes through the spec 004 engine and list refreshes per screen; the change feed, batch upload and membership on the user feed come with C14 wave 2 (backend B14), which is out of scope.
- Until C9 every wallet has only its owner, so transfer ownership can only be proved by tests, not end to end.
- Until C6 no transactions exist on the phone: the displayed balance adds only unsent opening-balance changes, and the server's `currency_locked` is the only source of the currency lock.
- Archived wallets are excluded from per-currency totals but keep their balance on their own card.
- The wallet list is its own screen reached from Home; Home stays the signed-in start screen until a Home feature redesigns it.
