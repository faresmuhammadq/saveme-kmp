# Feature Specification: Transactions (client)

**Feature Branch**: `008-transactions`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "specs/briefs/C06-transactions.md" — client brief C6, wave 1 (per-record writes through the spec 004 sync engine plus list refreshes). Pairs with backend B6 → spec 012.

**Backend contract**: `saveme-backend@f6d66eb`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/transactions.yaml` (frozen in backend spec 012 at v1.5, commit `2dbb8d7`; v1.7 header from spec 014 `c0e9844` adds the read-only `recurring_rule_id` and `occurrence_id`), `docs/api-conventions.md` (Syncable resources, Writes, Version conflicts, Deleting and restoring, Collections), `docs/openapi/error-catalog.yaml` (field code `CURRENCY_MISMATCH`), `docs/glossary.md` (Transaction, transfer, counter wallet, counterparty, Posting; banned synonyms "entry, record, movement, operation").
- Backend behaviour: `saveme-backend/specs/012-transactions-ledger/summary.md` and `docs/adr/0015-transfers-on-two-wallet-feeds.md` (a transfer is one record listed in both wallets; `kind` and the destination immutable; restore is `PATCH {deleted_at: null, version}`; owner or editor needed in both wallets of a transfer; `CURRENCY_MISMATCH` at `/currency` or `/counter_wallet_id`; the wallet's currency locks after its first transaction; archived wallets and archived categories accepted; balances recomputed by the server; no plan limit; every write behind the verification gate).
- Wallet permission matrix rows T-01 (read, any member), T-02 (create, edit, delete, restore any author's transaction: owner or editor), T-03 (a transfer needs T-02 in both wallets): `saveme-backend/specs/010-wallets-membership/spec.md` §6.
- Builds on spec 004 (sync engine: queue, clashes, refusals, status; `canRestore`), spec 006 (C4, wallets: `PendingPostings`, `WalletDeletionParticipant`, displayed balance = server balance + unsent effect, roles, currency lock) and spec 007 (C5, categories: `observeChoices`, `observeDisplays`, `refreshReferences`, `PendingCategoryUses`, `CategoryChoiceSheet`, `CategoryRow`; this feature is where C5's US4 is proved end to end).

## Clarifications

### Session 2026-10-02

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Where can a person start recording a transaction, and how many taps does it take? → A: From a wallet's transactions screen ("Add transaction", that wallet preselected) and from an "Add transaction" button on Home (the wallet last used on this phone, else the first active wallet the person can write to). The editor opens with the amount focused and Expense selected; a typical expense is amount, category, Save.
- Q: After deleting a transaction, can the person undo it? → A: No undo in this release. Deletion is confirmed first (naming the amount and date); a transaction deleted on another device can still be brought back from a clash with "Restore with my change".
- Q: Do search and filters ask the server or work on the phone? → A: On the phone, over the wallet's transactions the phone holds, so they work offline; they match the server's rules (search is a literal, case-insensitive match in the note, details or person; filters by date range, category and kind; several values of one filter combine with "or", different filters with "and").
- Q: How much of a wallet's history does the phone fetch? → A: All of it: opening a wallet's transactions reads every page of its list (at most once a minute unless the person pulls to refresh), showing rows as they arrive; transactions missing from a complete read with nothing waiting on this phone are removed.
- Q: Which wallets can a transfer go to? → A: Active wallets on the phone in the same currency where the person is an owner or editor, other than the source. Archived wallets and wallets in another currency are not offered; when none qualifies, the transfer option says so.

## User Scenarios & Testing *(mandatory)*

A transaction is what the person actually records: money spent, money received, or money moved
between two of their wallets. This feature is the app's core loop: record in a few taps with or
without a connection, see each wallet's transactions by day with a balance that already includes
what hasn't synced, find a transaction again, and correct or delete it safely.

### User Story 1 - Record an expense or income in a few taps, even offline (Priority: P1)

A person records what they spent or received: they type an amount, pick a category, and save.
The date is today unless they change it; a short note and, optionally, the person involved, longer
details and a "cleared" mark can be added. The transaction is saved on the phone at once and sent
when a connection exists.

**Why this priority**: Entry is where retention is won (brief); everything else in the ledger reads what is recorded here.

**Independent Test**: With the phone offline, open a wallet's transactions, add an expense of 45.50 with the default category "Groceries": it appears at once under Today marked "Waiting to sync" and the wallet's balance drops by 45.50; go online: the mark clears, the server's list has it, and the balance stays the same.

**Acceptance Scenarios**:

1. **Given** a signed-in, verified owner or editor of a wallet, **When** they open "Add transaction" from that wallet's transactions screen, **Then** the editor opens with that wallet, Expense selected, today's date, and the amount field focused.
2. **Given** the editor, **When** they enter an amount, choose a category and save, **Then** the transaction appears at once in the wallet's list under its date, online or offline, and is sent on its own.
3. **Given** an expense (or income) being recorded, **When** the person opens the category choice, **Then** it offers their active categories of that kind and the active default categories of that kind, in two labelled groups, and nothing else (C5 US4-S1).
4. **Given** the editor, **When** the amount is empty, zero, or not a valid amount in the wallet's currency (more fraction digits than the currency has, or above the largest amount), or no category is chosen for an income or expense, **Then** the form says what is wrong next to that field and nothing is saved.
5. **Given** the editor, **When** the person picks a past or future date, **Then** the transaction is saved with that calendar date and listed under it.
6. **Given** the editor, **When** the person adds a note (up to 100 characters), details (up to 1000), a person's name (up to 100) or marks it cleared, **Then** these are saved with it; longer text is refused next to the field.
7. **Given** Home, **When** the person taps "Add transaction", **Then** the editor opens on the wallet they last recorded in on this phone (else their first active wallet they can write to) and lets them switch wallet before saving; when they can write to no wallet, the editor says to create a wallet first and offers to open the wallet editor.
8. **Given** a person whose address is not verified, **When** they try to record, **Then** the app asks them to verify first (the existing verify prompt) and nothing is queued.
9. **Given** a viewer of a wallet, **When** they view its transactions, **Then** no "Add transaction" action is offered for that wallet.
10. **Given** a wallet with nothing recorded yet, **When** a first transaction is saved on this phone, **Then** the wallet's currency can no longer be changed in the wallet editor (the note "can't change once transactions exist" shows), even before the transaction syncs.

---

### User Story 2 - See a wallet's transactions by day, with the right balance (Priority: P1)

A person opens a wallet and sees its transactions newest first, grouped under date headers with
each day's net total. Each row shows the category (or the other wallet for a transfer), the note,
and the signed amount. The wallet's balance already includes transactions that haven't reached the
server, and the list keeps working offline.

**Why this priority**: The list is how a person checks they recorded something and what their money did; a balance that jumps or disagrees with the list destroys trust.

**Independent Test**: Seed three transactions on two dates through the API; open the wallet's transactions: two date headers with the right day totals and three rows; record one offline: it appears with "Waiting to sync" and the balance includes it; go online: after it syncs the balance stays the same.

**Acceptance Scenarios**:

1. **Given** a wallet with transactions, **When** the person opens its transactions, **Then** they are listed newest date first (ties: most recently created first), under headers "Today", "Yesterday" or the date, each header showing the day's net total in the wallet's currency.
2. **Given** a row, **When** it is shown, **Then** it shows the category's icon, colour and name (a default in the app's language, a partner's as named), the note when there is one, and the amount with its direction: an expense or an outgoing transfer as money out, an income or an incoming transfer as money in; expenses never in the error colour.
3. **Given** transactions recorded, edited or deleted on this phone and not yet synced, **When** the wallet and its list are shown, **Then** the rows show "Waiting to sync" (or "Changed on another device" / "Not saved") and the wallet's balance on every screen equals the server's last balance plus the effect of those changes.
4. **Given** a change that has just synced, **When** the server's answer arrives, **Then** the balance shown does not jump back and forth: it keeps the change's effect until the wallet's new balance has been read.
5. **Given** transactions changed on another device or by another member, **When** the person opens the wallet's transactions, pulls to refresh, or returns to the app with the list open, **Then** the changes are shown; a transaction deleted elsewhere with no unsent change on this phone disappears.
6. **Given** no connection, **When** the person opens a wallet's transactions, **Then** the last known transactions are shown with when they were last updated; nothing is blank because of the connection.
7. **Given** a wallet with no transactions, **When** its list is shown, **Then** a short note invites the person to add the first one (with the add action when they can write).
8. **Given** a shared wallet, **When** a transaction a partner filed under the partner's own category is shown, **Then** that category shows with the partner's name, icon and colour; an id the phone can't resolve yet shows "Category unavailable" and the phone fetches that wallet's partner categories (C5 US4-S4, S5).
9. **Given** a transaction created by another member, **When** its details are shown, **Then** they say who added it (by member name, or "Another member" when unknown); a transaction made from a recurring rule says so.

---

### User Story 3 - Edit and delete a transaction, safely (Priority: P1)

A person corrects a transaction (amount, category, date, note, details, person, cleared) or deletes
it. Both work offline. If the same transaction was changed or deleted on another device first, the
person sees both versions and chooses; nothing is overwritten silently.

**Why this priority**: Typos in amounts are common; a ledger that can't be corrected, or that loses a correction to another device, is useless.

**Independent Test**: Record an expense; edit its amount offline; on the API session, edit the same transaction's note; go online: the transaction shows "Changed on another device" with both amounts and notes side by side; choose "Keep mine": the server has this phone's values at the next version.

**Acceptance Scenarios**:

1. **Given** an owner or editor viewing a transaction, **When** they edit it, **Then** they can change amount, category, date, note, details, person and cleared; the kind (and, for a transfer, the two wallets) is shown but cannot be changed.
2. **Given** an existing transaction filed under a category since archived (or a default since retired, or a partner's category), **When** its category choice opens, **Then** that category is still listed and selected (marked archived when it is), and other archived categories are not offered (C5 US4-S2).
3. **Given** the person saves an edit, **When** it is saved, **Then** the list and the balance show the new values at once, online or offline, and the edit is sent on its own based on the version shown when the editor opened.
4. **Given** the person chooses Delete, **When** the confirmation is shown, **Then** it names the amount and date and says the transaction will be removed from the wallet's balance; nothing happens until they confirm.
5. **Given** they confirm, **When** the deletion is saved, **Then** the transaction disappears from the list and the balance at once (offline too) and the deletion is sent on its own; a transaction recorded on this phone and never sent is removed with nothing sent.
6. **Given** a waiting edit to a transaction changed on another device, **When** it is sent, **Then** the transaction shows "Changed on another device" with this phone's and the other device's values of every differing field side by side and "Keep mine" / "Use the other device's"; nothing is applied until the person chooses.
7. **Given** a waiting edit to a transaction deleted on another device, **When** it is sent, **Then** the transaction says it was deleted on another device and offers "Restore with my change" (it comes back with this phone's values) and "Accept the deletion".
8. **Given** a waiting deletion of a transaction changed on another device, **When** it is sent, **Then** the transaction reappears marked "Changed on another device" with "Delete anyway" and "Keep the transaction"; nothing is deleted until the person chooses.
9. **Given** a refused change, **When** the answer arrives, **Then** the transaction is marked "Not saved" with a reason in the app's words (not verified; no longer available; no right to change this wallet's transactions; the category can't be used; the wallet's currency changed; a field problem; or an unexpected error with the server's reference), and the person can edit it, "Try again" (not verified only) or discard it.
10. **Given** a viewer, **When** they open a transaction, **Then** it is shown read-only with no edit or delete action.

---

### User Story 4 - Move money between two of my wallets (Priority: P2)

A person records a transfer: money out of one wallet into another wallet of the same currency
they can write to. It is one transaction listed in both wallets, and both balances move.

**Why this priority**: Moving cash to a bank wallet or paying a credit card is routine, but recording income and expenses comes first.

**Independent Test**: Two EGP wallets "Cash" and "Bank"; record a transfer of 500 from Cash to Bank offline: it is listed in both wallets (out of Cash, into Bank) and both balances change at once; go online: the server lists the one transaction under both wallets and both balances match.

**Acceptance Scenarios**:

1. **Given** the editor in an active wallet, **When** the person chooses Transfer, **Then** the category field is replaced by a destination choice listing the active wallets on the phone in the same currency where the person is an owner or editor, other than the source; when none qualifies, the choice says no wallet can receive this transfer and Save stays unavailable.
2. **Given** a saved transfer, **When** the wallets are shown, **Then** it is listed in both wallets' transactions ("To <destination>" in the source as money out, "From <source>" in the destination as money in), it has no category, and both wallets' balances include it at once.
3. **Given** a transfer, **When** it is edited, **Then** amount, date, note, details, person and cleared can change; the source and destination are shown read-only (moving a transfer means deleting it and recording a new one).
4. **Given** a transfer the server refuses because the person has no right to write in the other wallet (their role changed meanwhile), **When** the answer arrives, **Then** it is marked "Not saved: you can't add or change transactions in the other wallet", not as a general permission problem.
5. **Given** a transfer refused because the two wallets now have different currencies (or the destination is no longer available), **When** the answer arrives, **Then** it is marked "Not saved" with that reason and the person can discard it.
6. **Given** a transfer whose other wallet is not on this phone (a partner's private wallet, or a deleted wallet), **When** it is shown, **Then** it reads "From another wallet" / "To another wallet", and it can be deleted but not edited when the person can't write to both wallets.

---

### User Story 5 - Find a transaction (Priority: P2)

A person searches a wallet's transactions by words in the note, details or person, and filters by
date range, category and kind.

**Why this priority**: Finding "what did I pay the plumber" matters once a wallet has history; it is not needed to record.

**Independent Test**: In a wallet with 30 transactions, search "plumb": only transactions whose note, details or person contains it (any letter case) are shown, still grouped by day; add the filter "This month" and the kind Expense: the list narrows; clear the filters: all 30 return. Works offline.

**Acceptance Scenarios**:

1. **Given** a wallet's transactions, **When** the person types a search, **Then** only transactions whose note, details or person contains the typed text (case-insensitive, characters matched literally, after trimming; 1–100 characters) are shown, grouped by day.
2. **Given** the filters, **When** the person picks a date range (this month, last month, or a custom from/to), one or more categories, and/or one or more kinds, **Then** only transactions matching every chosen filter are shown; several values of one filter match any of them.
3. **Given** active filters or search, **When** the list is shown, **Then** the active filters are visible as chips that can be cleared one by one or all at once, and a "No transactions match" note shows when nothing matches.
4. **Given** no connection, **When** the person searches or filters, **Then** it works on the transactions the phone holds.
5. **Given** a search or filter is active, **When** the person leaves the wallet's transactions and comes back, **Then** the list starts unfiltered.

### Edge Cases

- A transaction recorded offline, edited, then deleted before it ever synced: nothing reaches the server (spec 004 FR-004), and the balance returns to where it was.
- Several transactions recorded offline in a row are sent oldest first; a refusal of one doesn't stop the others.
- A category archived (or deleted) on another device while a transaction using it waits to sync: archived categories are accepted by the server; a deleted one is refused ("the category can't be used") and the person edits the category or discards.
- The person's role in a wallet drops to viewer while a change waits: the server refuses (no right); the person can discard. A viewer is never offered add, edit or delete.
- A wallet is deleted (or the person loses access) while its transactions are on the phone: they are removed from the phone together with any change waiting for them; a transfer to a wallet still on the phone stays listed there.
- The wallet's currency was changed on another device before this phone's first transaction synced: refused with "the wallet's currency changed"; editing shows the wallet's current currency and asks for the amount again (an amount is never reinterpreted in another currency).
- A balance is allowed to go negative; nothing warns or refuses.
- An amount at the largest value the server accepts (9,007,199,254,740,991 minor units) is accepted; one above it is refused before saving.
- A transaction dated before 1900-01-01 or after 2999-12-31 cannot be chosen.
- The person's default category choice for a kind is empty (no own categories and the defaults not loaded): the choice says no categories are available yet, and Save stays unavailable for income and expense.
- A transaction whose kind this app version doesn't know is listed with a neutral icon, its amount and no direction sign, and cannot be edited; it can be deleted.
- A transaction made from a recurring rule (it carries the rule's links) can be edited and deleted like any other; the links are kept as the server sends them and never sent back.
- The transaction open in the editor disappears (deleted elsewhere, found by a refresh): the editor keeps the person's typing and its save becomes a waiting change that the server answers (clash or refusal); a transaction with no waiting change closes back to the list with a short message.
- Two members edit the same transaction from its two wallets (a transfer): one version, one clash, like any other edit.
- "Restore with my change" whose second step (saving this phone's values) is refused or clashes: the transaction is live again on the server with the other device's last values, and this phone's change shows "Not saved" (or the clash) with the usual choices; discarding it leaves the restored transaction.
- A transfer whose source wallet was deleted stays listed in its destination; it can be deleted there, not edited or restored.
- Very long histories: any number of transactions the server returns is shown; a wallet with thousands of rows keeps scrolling smoothly and search answers within the success criteria.
- The time zone changes (travel): "today" follows the profile's time zone; stored dates never shift.
- Signing out removes every transaction from the phone, with the waiting-changes warning of spec 004.

## Requirements *(mandatory)*

### Functional Requirements

**Recording (local-first)**

- **FR-001**: The app MUST keep each visible wallet's transactions on the phone and render every transaction screen, the wallet balances and the search results from that local copy (P-04.1).
- **FR-002**: Recording MUST need a wallet (preselected from where the editor was opened), a kind (expense, income or transfer; Expense preselected), an amount, a date (today in the profile's time zone, preselected), and a category for an income or expense or a destination wallet for a transfer; the note (≤ 100), details (≤ 1000), person (≤ 100) and cleared mark are optional. Text limits count Unicode code points after trimming; blank text is saved as empty (null). Switching the kind clears what no longer applies: the category when switching between expense, income and transfer (a category of the other kind is never kept), the destination when leaving transfer.
- **FR-003**: The amount MUST be entered in the wallet's currency, accept Western or Arabic-Indic digits and `.` or `٫` as the decimal mark, allow no more fraction digits than the currency has, be greater than zero and at most 9,007,199,254,740,991 minor units, and be stored and sent as a positive whole number of minor units; the direction comes from the kind (P-03).
- **FR-004**: The date MUST be a calendar date from 1900-01-01 to 2999-12-31, stored and sent as `YYYY-MM-DD` with no time zone; past and future dates are allowed (P-05). The date picker MUST show Western digits in both languages (P-06.2).
- **FR-005**: Creates, edits and deletions MUST be saved on the phone first, succeed without a connection, and be sent through the shared sync queue (spec 004) in the transaction's wallet scope (its source wallet) with the version the person last saw; a new transaction MUST get a client-generated UUIDv7 (P-04.2–P-04.4). An edit MUST be based on the version shown when the editor opened, so a refresh that lands while the person is editing produces a clash rather than a silent overwrite.
- **FR-006**: The transaction's currency MUST always be its wallet's currency at the time of saving and is not chosen by the person.
- **FR-007**: For an income or expense, the category MUST be chosen through C5's category choice for that kind with the transaction's current category kept (`observeChoices(kind, current)`); a partner's category, an archived own category or a retired default MUST NOT be offered for a new or re-categorised transaction.
- **FR-008**: The "Add transaction" action MUST be offered only in an active (not archived) wallet where the person is an owner or editor; Home's "Add transaction" MUST preselect the wallet last recorded in on this phone if it still qualifies, else the first qualifying wallet, and allow switching to any qualifying wallet before the first save; switching keeps the typed amount and re-checks it against the new wallet's currency, and clears a transfer's destination.
- **FR-009**: Recording, editing or deleting while the account's address is not verified MUST show the existing verify prompt instead of queuing a change the server will refuse. Transactions MUST NOT be gated by the person's plan (the backend sets no limit).

**Transfers**

- **FR-010**: A transfer MUST be one transaction with the source wallet as its wallet and the destination as its counter wallet, no category, and the source's currency; the destination choice MUST list only active wallets on the phone with the same currency where the person is an owner or editor, other than the source.
- **FR-011**: A transfer MUST be listed in both wallets' transactions (money out of the source, money into the destination) and MUST change both wallets' displayed balances while it waits to sync.
- **FR-012**: A transfer's wallets and any transaction's kind MUST NOT be editable after creation and MUST NOT be sent changed; an `IMMUTABLE_FIELD` refusal at `/kind` or `/counter_wallet_id` MUST be shown next to that field.
- **FR-013**: A transfer MUST be editable only when the person is an owner or editor of both of its wallets on this phone. Delete MUST be offered when the person is an owner or editor of the wallet it is viewed from and the other wallet is either writable on the phone or not on the phone (it may have been deleted, which the server allows); a server refusal then shows FR-014's reason. Otherwise the transfer is read-only. A transfer whose other wallet is not on the phone MUST read "From another wallet" / "To another wallet".
- **FR-014**: A `FORBIDDEN` refusal of a transfer write MUST be shown as having no right to write in the other wallet; for income and expense it MUST be shown as having no right to change this wallet's transactions.

**List and balance**

- **FR-015**: A wallet's transactions MUST be listed newest date first, ties by creation time newest first, then by id, grouped under date headers ("Today", "Yesterday", else the date in the app language with Western digits) with the day's net total in the wallet's currency (money in minus money out, transfers by their direction in this wallet).
- **FR-016**: Each row MUST show the category through C5's display (`observeDisplays(walletId)`) — or, for a transfer, "To / From <other wallet name>" — the note when present, the signed amount (expenses in neutral ink with U+2212, income with "+"), and a "Waiting to sync" / "Changed on another device" / "Not saved" mark when one applies, in words.
- **FR-017**: A wallet's displayed balance MUST equal the server's last reported balance plus the effect of every unsent create, edit and deletion of its transactions on this phone (C4 `PendingPostings`), including the transfers where it is the destination. After a change syncs, its effect MUST keep counting until a read of the wallet's balance that started after the server confirmed the change has completed, so the balance never shows a value that leaves the change out.
- **FR-018**: The app MUST read every page of a wallet's transactions when its transactions screen opens (at most once a minute per wallet), when the person pulls to refresh (always), and on returning to the foreground with that screen open (at most once a minute); rows MUST show as pages arrive. After a complete read, a transaction of that wallet that is no longer listed, has reached the server at least once and has no unsent change MUST be removed from the phone. Offline, the screen MUST show the last known data with when it was last updated; a refresh that fails for another reason MUST keep the local data and say the transactions couldn't be updated. Before the first read completes, the screen MUST show a loading state, not the empty note.
- **FR-019**: When a wallet's transactions screen opens, the app MUST ask C5 to refresh that wallet's partner categories (`refreshReferences(walletId, force = true)`), and resolve every category id offline through C5's display (unresolved ids show "Category unavailable").
- **FR-020**: Transaction details MUST show who added it when it was not the person (member name from the wallet's members, else "Another member"), and "From a recurring rule" when it carries a recurring rule link.

**Edit, delete, clashes and refusals**

- **FR-021**: Edit and delete MUST be offered only to an owner or editor of the wallet (both wallets for editing a transfer, FR-013); a viewer sees the transaction read-only.
- **FR-022**: Delete MUST be confirmed (naming the amount and date); on confirmation the transaction MUST disappear from every screen and balance at once and the deletion be queued as the local tombstone (P-04.5); the data is removed from the phone when the server confirms the deletion, or at once for a transaction that never reached the server. No undo is offered.
- **FR-023**: A clash MUST be shown on the transaction with both versions of every differing field (amount, date, category, note, details, person, cleared) and the choices "Keep mine" / "Use the other device's" (edit) or "Delete anyway" / "Keep the transaction" (deletion); nothing is applied until the person chooses (P-04.4).
- **FR-024**: When the server's version is a deletion, the app MUST offer "Restore with my change" (the transaction is restored on the server and then saved with this phone's values) and "Accept the deletion".
- **FR-025**: A refused change MUST be shown as "Not saved" with a reason chosen by error code and pointer: not verified (`EMAIL_NOT_VERIFIED`); no longer available (`NOT_FOUND`); no right (`FORBIDDEN`, FR-014); category can't be used (`INVALID_ENUM_VALUE` at `/category_id`); destination not available (`INVALID_ENUM_VALUE` at `/counter_wallet_id`); the wallet's currency changed (`CURRENCY_MISMATCH` at `/currency`); wallets use different currencies (`CURRENCY_MISMATCH` at `/counter_wallet_id`); a field problem (`REQUIRED`, `OUT_OF_RANGE`, `IMMUTABLE_FIELD`, other field codes, shown next to the field); or an unexpected error with the server's reference. Never the server's text (P-06.5). The person can edit (field, category, currency problems), "Try again" (not verified), or discard; discarding a never-synced create removes it from the phone.
- **FR-026**: Editing after a "wallet's currency changed" refusal MUST show the wallet's current currency and require the amount to be entered again.

**Find**

- **FR-027**: Search MUST match a literal, case-insensitive substring (1–100 characters after trimming; the field accepts at most 100 characters, and a blank search shows everything) of the note, details or person of the wallet's transactions on the phone, offline included; it updates as the person types.
- **FR-028**: Filters MUST cover date range (this month, last month, custom from–to inclusive), category (one or more, from the categories the wallet's transactions use plus the person's choosable ones) and kind (one or more of expense, income, transfer); values of one filter combine with "or", filters and search with "and"; active filters show as clearable chips; filters reset when the screen is left.

**Hooks for other features**

- **FR-029**: The app MUST report to C4 the per-wallet effect of unsent transactions and whether any is unsent (`PendingPostings`), so the wallet list, detail and totals include them and keep the "waiting" mark; and the wallet editor MUST treat a wallet with an unsent transaction as currency-locked.
- **FR-030**: The app MUST report to C5 the categories used by unsent transaction changes (`PendingCategoryUses`), so C5 does not offer Delete for them.
- **FR-031**: When a wallet's deletion is confirmed or a refresh drops it (C4 `WalletDeletionParticipant`), the app MUST remove from the phone every transaction that no longer touches a wallet on the phone, together with any change waiting for them. While a wallet is hidden by its own deletion waiting to sync, its transactions MUST be hidden too (and kept, in case the deletion is refused or clashes).

**Sign-out, contract and identity**

- **FR-032**: Sign-out MUST remove every transaction from the phone with the rest of the account's data (P-09, spec 004 FR-018).
- **FR-033**: Request bodies MUST carry only `TransactionWrite`, `TransactionRestore` or `VersionOnly` members; `recurring_rule_id`, `occurrence_id`, `created_by`, `wallet_id` and other read-only members MUST never be sent; unknown response members MUST be ignored and the read-only links kept as received (P-02.2, P-02.3).
- **FR-034**: All transaction copy MUST exist in English and Arabic, call the entity "Transaction" (Arabic "معاملة"), never "entry", "record", "movement" or "operation" (backend glossary banned synonyms) nor "version", "tombstone" or "posting", and mirror in RTL with amounts kept left-to-right (P-01, P-06).
- **FR-035**: Every sync mark, kind and direction MUST be conveyed by text or sign as well as colour; icon-only controls described; touch targets ≥ 48 dp; all transaction screens usable at 200% font scale (P-08).
- **FR-036**: Amounts, notes, details, person names and payloads MUST NOT be logged or sent to crash reporting (P-09.2); logs carry record id, outcome and request id only.

### Key Entities *(include if feature involves data)*

- **Transaction**: id (UUIDv7, client-made), wallet (the source for a transfer), kind (income, expense, transfer; immutable), date (calendar date), amount in minor units (positive) with currency, category (income/expense only), counter wallet (transfer only; immutable), note (`description`), details (`memo`), person (`counterparty`), cleared, added by (`created_by`), recurring rule and occurrence links (read-only), and the sync members version, change sequence, created/updated/deleted instants. Rides its wallet's scope.
- **Waiting change (spec 004)**: a transaction's unsent create, edit or deletion with the version it was based on, or a restore chosen from a clash.
- **Balance carry**: per synced change, the effect on each touched wallet still to be confirmed by the next read of that wallet's balance (FR-017).

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a person record income, expenses and transfers in a few taps, with or without a connection;
see each wallet's transactions by day with a balance that includes what hasn't synced; find,
correct and delete transactions without ever losing a change to another device. It also completes
the hooks C4 and C5 left for it: wallet balances and the currency lock include unsent transactions,
categories used by unsent transactions can't be deleted, and the category choice and display are
used on real screens.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Transaction** (kind `income` / `expense` / `transfer`;
never "entry", "record", "movement", "operation"), **transfer**, **counter wallet**, **Wallet**,
**Wallet Member** (roles owner / editor / viewer), **Category**, **Category Reference**,
**counterparty**, **cleared**, **Posting** (internal only, never in UI or code of this client),
**Recurring Rule**, **Occurrence**, **tombstone** (internal only). UI says "Transaction"/"معاملة",
"Expense"/"مصروف", "Income"/"دخل", "Transfer"/"تحويل", "Note" (description), "Details" (memo),
"Person" (counterparty), "Cleared", "Waiting to sync", "Changed on another device", "Not saved";
never "tombstone", "version", "posting", "scope" or "counter wallet". "Reference: …" appears only
before the server's request id, as in specs 006–007.

### 3. Data model

Adds one phone-side table, **transactions**, holding every `Transaction` field (including
`version`, `change_seq`, `created_at`, `updated_at`, `recurring_rule_id`, `occurrence_id`) plus
the phone's own `account_id`, `refreshed_at` and the balance carry (per touched wallet, with the
time the server confirmed it), cleared at sign-out. Per-wallet "last read" stamps use the existing
`sync_scope` table. Unsent changes use the spec 004 queue with record type "transaction" and the
transaction's wallet scope. The on-device database version goes up by one (4 → 5) with a migration
that keeps all existing data, and the schema is exported.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Transaction screens, balances and search render only from the phone's copy | P-04.1, FR-001 | store/service tests over fakes; no API in UI/store |
| Create/edit/delete succeed offline and are queued in the source wallet's scope with the base version | P-04.2, P-04.4, FR-005 | service test with the network off; queue contents asserted |
| New transactions carry a client UUIDv7 | P-04.3 | id generation test |
| Deletion is a queued deletion; a never-sent create leaves nothing to send | P-04.5, FR-022 | service + engine tests |
| Amounts are positive `Long` minor units with the wallet's currency; fraction digits from the currency | P-03, FR-003, FR-006 | amount parsing tests (EGP, KWD, JPY); handler request test |
| Kind and a transfer's wallets never sent changed; never editable after create | FR-012, ADR-0015 rule 3a | handler request test; editor reducer test |
| A tombstone clash offers restore; "Restore with my change" sends the restore then the edit | FR-024, `canRestore = true` | handler test (PATCH then PUT); engine keep-mine test |
| A transfer is one row listed in both wallets and moves both balances | FR-010, FR-011, ADR-0015 | DAO list test; `PendingPostings` test |
| Displayed balance = server balance + unsent effect + unconfirmed carry; no flicker after sync | FR-017, spec 006 FR-004 | balance projection tests over the send/refresh sequence |
| Read-only members never sent; unknown members ignored | P-02.2, P-02.3, FR-033 | contract tests |
| Failures branch on `code` and pointer only; 403 on a transfer reads as "other wallet" | P-02.4, FR-014, FR-025 | refusal-mapping and copy tests |
| Category choice offers only own active + active defaults, current kept | FR-007, C5 FR-018 | editor store test over C5's choices |
| Transactions with unsent changes survive a refresh that no longer lists them | FR-018 | refresh/merge repository test |
| Search and filters match the server's rules | FR-027, FR-028, transactions.yaml | filter/search tests (literal `%` and `_`, case, ranges) |
| Unsent transactions lock the wallet currency and block category deletion | FR-029, FR-030 | hook tests; wallet editor store test |
| Wallet removal removes its transactions and their waiting changes | FR-031 | participant test |
| No amount, note or payload in logs | P-09.2, FR-036 | log-capture test over a send and a refusal |
| Sign-out empties the transactions table | FR-032, P-09 | `DatabaseCleaner` / sign-out wipe test |
| Migration keeps existing data | spec 004 FR-005 | migration test 4 → 5 |

### 5. API

All under `/api/v1`, frozen in `transactions.yaml` (backend spec 012, `2dbb8d7`, v1.5; v1.7 adds read-only links).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| List (refresh) | `GET /wallets/{id}/transactions` (all pages, `cursor`, `limit=200`, default sort `-date`) | — | 200; offline/timeout/5xx → keep local; 404 → the wallet is gone for this person (C4 refresh decides); 401 → session ended |
| Create / save (sync engine) | `PUT /wallets/{id}/transactions/{tid}` (path = source wallet; the destination's when the source is not on the phone) | `TransactionWrite`: `kind, date, amount_minor, currency, category_id, counter_wallet_id, description, memo, counterparty, cleared, version` with explicit `null`s | 200/201; `409 VERSION_CONFLICT` (+`current`, live or tombstone); `422 VALIDATION_FAILED` (`REQUIRED`, `OUT_OF_RANGE`, `INVALID_ENUM_VALUE` at `/category_id` or `/counter_wallet_id`, `CURRENCY_MISMATCH` at `/currency` or `/counter_wallet_id`, `IMMUTABLE_FIELD` at `/kind` or `/counter_wallet_id`, `UNKNOWN_FIELD`), `422 INVALID_ID`; `403 EMAIL_NOT_VERIFIED`; `403 FORBIDDEN`; `404 NOT_FOUND`; retryable class per catalogue |
| Restore (sync engine, after "Restore with my change") | `PATCH /wallets/{id}/transactions/{tid}` | `TransactionRestore`: `{deleted_at: null, version}`; then a `PUT` of the person's values with the returned version | 200; 409 (+`current`); 422 (`INVALID_ENUM_VALUE` at `/counter_wallet_id` when the other wallet was deleted); 403; 404 |
| Delete (sync engine) | `DELETE /wallets/{id}/transactions/{tid}` **with a JSON body** | `VersionOnly` | 200 (tombstone; also for an already deleted one); `409 VERSION_CONFLICT` (+`current`); 403; 404 |

`GET /wallets/{id}/transactions/{tid}` is not used (a refusal or clash carries what the phone
needs); `PATCH` is used only to restore — every edit is a whole-record `PUT` (the wallets and
categories decision). The list's server filters and `search` are not used: search and filters run
on the phone (clarification 3) with the same rules. Transaction writes are not idempotency-keyed
(idempotent by id and version). `change_seq` is stored but not interpreted in this wave.
Transactions ride the wallet feeds (`GET /sync/wallets/{id}/changes`, transfers on both) and the
batch upload carries `transaction` items; switching to them is the wave 2 sync spec (C14 batch),
not this one. Wallet balances come from C4's wallet reads (`balance_minor`), which this feature
asks C4 to repeat after its changes sync (FR-017).

#### Contract changes since last spec

Baseline `saveme-backend@8aee567` (spec 007) → `f6d66eb` (the backend working tree has
uncommitted budget edits outside `transactions.yaml`; `transactions.yaml` itself is unchanged since
`c0e9844`):

- **Batch upload item types** now listed (`wallet`, `category`, `transaction`, `profile`, `recurring_rule`, `budget`; `occurrence` upsert only). Follow-up: not in scope of this spec (C14 wave 2).
- **Occurrence**: a server-created record that accepts a `PUT` but never a create or `DELETE`. Follow-up: not in scope of this spec (C7).
- **`recurring_rule_id` / `occurrence_id`** (transactions.yaml v1.7): read-only, null unless made from a recurring rule. Handled by FR-020 and FR-033.
- **Field code `CURRENCY_MISMATCH`** (catalogue v1.5, a field-level code, so not an `ApiErrorCode` entry): handled by FR-025 and FR-026.
- Error catalogue: no change since the baseline; the client's codes match the catalogue (no client mismatch).

### 6. Authorization and entitlements

Following the backend wallet permission matrix (spec 010 §6, not copied here): any active member
reads a wallet's transactions (T-01); owners and editors create, edit, delete and restore any
member's transaction (T-02); a transfer needs that in both wallets (T-03). The app offers actions
by the person's role as the phone last read it (C4 `WalletView.server.myRole`) and treats the
server's `FORBIDDEN` as the authority (FR-014). A non-member gets 404, which the app treats as "no
longer available". Every write needs a verified address (FR-009). No plan feature gates
transactions.

### 7. Events

Not applicable: the client publishes no domain events. It consumes the existing `ApiEvent`s
(session ended, email not verified), the sync engine's status and queue, C4's wallet views and
deletion hook, and C5's choices, displays and reference refresh.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Record | Unit (editor reducer/store, amount parsing, service create, handler PUT) + contract tests + E2E | tests; `.maestro/008-transactions/US1-*` |
| US2 List & balance | Unit (list building, day totals, balance projection and carry, refresh merge) + screenshots + E2E | tests; `US2-*` |
| US3 Edit & delete | Unit (editor edit mode, clash and refusal mapping, handler restore/delete) + contract tests + E2E (API second session for clashes) | tests; `US3-*` |
| US4 Transfer | Unit (destination choices, both-wallet listing, both balances, 403 copy) + E2E | tests; `US4-*` |
| US5 Find | Unit (search and filter rules, chips) + E2E | tests; `US5-*` |

Offline scenarios are driven end to end by the runner's `network-off` / `network-on` steps (spec
006); otherwise they are proved by unit and persistence tests. Device runs are deferred to the
owner's phone; a host-only QA checks every call against the live local backend and turns the real
answers into test fixtures (as spec 007 did).

### 9. Open questions

- **Wallet feeds and batch upload (backend B14)**: replacing whole-list reads with the wallet feed (transfers on both feeds) arrives with C14 wave 2. Non-blocking.
- **Undo and a "recently deleted" view**: not in this release (clarification 2); restore is reachable from a clash only. Decided by: owner, later. Non-blocking.
- **Arabic words "ملاحظة" (Note), "تفاصيل" (Details), "الشخص" (Person), "تمت المطابقة" (Cleared)**: assumed defaults for owner review. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can record a typical expense (amount, category, save) in under 10 seconds from a wallet's transactions screen, online or offline, and it is listed the moment they save.
- **SC-002**: 100% of transactions recorded, edited or deleted offline reach the server, or are shown as needing the person's decision, once a connection returns.
- **SC-003**: Zero transaction changes are silently overwritten or dropped: every clash and refusal is shown on the transaction.
- **SC-004**: The balance shown for a wallet always equals the server's balance plus the effect of this phone's unsent changes, and never shows an intermediate value while a change syncs.
- **SC-005**: A wallet's transactions screen with 2,000 transactions opens within 1 second from the phone's copy, with or without a connection, and search or a filter change answers within 300 ms.
- **SC-006**: A transfer recorded offline is listed in both wallets and changes both balances at once, and once synced the server lists one transaction under both wallets with both balances equal to the phone's.
- **SC-007**: The brief's integration checkpoint passes end to end: three transactions recorded offline all sync when online; a transfer moves both balances; an edit on the API session produces a conflict choice on the phone. Together with C5's checkpoint: a transaction with a default and with a custom category; the custom one archived; the transaction still shows it.

## Assumptions

- Wave 1: per-record writes through the spec 004 engine and list refreshes; wallet feeds and batch upload arrive with C14 wave 2 (backend B14 is built but its client switch is out of scope).
- The wallet's transactions are their own screen, opened from a "Transactions" row on the wallet's detail (which replaces C4's "No transactions yet" placeholder); the wallet detail also offers "Add transaction" to an owner or editor of an active wallet (a third entry point beside clarification 1's two, same rules as FR-008); the editor is also the transaction's detail.
- New transactions are not offered in archived wallets (the backend would accept them); existing ones there stay editable.
- Backdated transactions re-cut budget progress in C8; nothing here computes budgets.
- The backend's default categories are seeded on the dev stack (`php artisan db:seed`).
- Shared-wallet scenarios need a second member, seeded through the API or by hand until C9 invitations exist.
- Transactions have no plan limit (backend spec 012 assumed default 13).
