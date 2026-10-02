# Research: Transactions (client)

Phase 0 of the plan. Every unknown in the Technical Context is resolved here; each decision is an
assumed default (owner away with delegated authority) unless the spec's clarifications fixed it.
Paths are relative to `shared/src/commonMain/kotlin/com/fares/saveme/`.

## R-01 Package and boundaries

**Decision**: one new bounded context `feature.transactions` with `data`, `domain`, `ui`, `di`.
`ui` imports its own `domain`, `feature.categories.domain` (choices and displays) and
`feature.wallets.domain` (wallet views, `AmountInput`); `domain` imports `core`, `auth.domain`,
`wallets.domain`, `categories.domain`; `data` imports no `domain`, no `ui` and no other feature:
its queue payload (`TransactionPayload`) and plain field names live in `data`, as spec 007 R-04
did (stricter than spec 006).
**Rationale**: P-10.3, P-10.4, P-10.6; the categories layering test is the model.
**Alternatives**: putting transactions inside `feature.wallets` (one context "Wallets & Ledger" on
the backend) — rejected: the wallets package is already large and C7/C8/C13 consume transactions
separately; the backend glossary names the context, not the client package.

## R-02 Room schema v5 (one table, one migration)

**Decision**: table `transactions` (plural: `TRANSACTION` is an SQLite keyword) with every wire
field, `account_id`, `refreshed_at` and the balance carry (R-09); indexes
`(wallet_id, date, created_at)` and `(counter_wallet_id, date, created_at)`. `MIGRATION_4_5`
creates it; schema `5.json` exported; `DatabaseCleaner` clears it. Per-wallet read stamps use the
existing `sync_scope` table with key `transactions:<walletId>`.
**Rationale**: transaction-level rows (brief: "prefer transaction-level rows"); postings are never
on the wire (glossary) and the client needs none — a transaction's effect on a wallet is derived
from kind, amount and direction (R-09).
**Alternatives**: a postings table mirroring the server — rejected (P-04 backend rule 6: postings
are never exposed; it would duplicate derivation the server owns).

## R-03 Local row = server copy; pending content overlays it (as specs 006–007)

**Decision**: a row is what the server last said. A create inserts a placeholder row with
`version = 0` (so lists, joins and the balance have something to show); unsent content lives in
the spec 004 queue payload and overlays the row in every view. A row with `deleted_at` set is a
confirmed deletion kept only until its balance carry is confirmed (R-09); it is never listed.
**Rationale**: one rule across wallets, categories and transactions; the engine's clash and
refusal states already hang off the queue row.

## R-04 `TransactionSyncHandler` (record type `transaction`, `canRestore = true`)

**Decision**:
- **Path wallet**: the record's `wallet_id` (source), unless that wallet is not on the phone (deleted, or the person is not a member), then `counter_wallet_id` (ADR-0015 rule 5: readable and writable under either path; a deleted source is only deletable through the live one).
- **Delete** (`isDelete`): `DELETE …/transactions/{tid}` with body `{version: base}`.
- **Restore** (`restore`, set by the engine's `keepMine` on a tombstone clash): `PATCH {deleted_at: null, version: base}`; if the payload differs from the restored record, a `PUT` of the payload with the returned version follows; the last answer is returned. A failure of the PUT after a successful PATCH is returned as that failure (the record is live again on the server; the engine shows the refusal or clash on the person's change).
- **Create / save**: `PUT` with every `TransactionWrite` member (explicit `null`s) and `version = base` (0 = create). `kind`, `counter_wallet_id` and `currency` for an existing record are always the stored ones, never the payload's (FR-012); `currency` for a create is the wallet's at save time (FR-006).
- **`applyServerCopy`**: stores the server record. When the copy is the answer to this handler's own successful send (the handler remembers the id between `send` and the engine's immediate `applyServerCopy` call), it adds the change's balance effect to the carry (R-09) and a tombstone marks the row deleted (hidden) until the carry is confirmed; any other copy ("Use the other device's", "Accept the deletion") adds no carry, and a tombstone then removes the row at once.
- **Effect rule in `data`**: the effect of a stored state on a wallet (`BalanceEffect.of(kind, amountMinor, walletId, counterWalletId, deleted, forWallet)`, primitives only) lives in `feature.transactions.data`, because the handler needs it and `data` must not import `domain` (P-10.6); the domain's `TransactionPostings` reuses it (domain may import its own data).
- **`onDiscarded`**: removes a placeholder (`version = 0`); otherwise nothing (the server copy shows again).
- **`refusalFields`**: `/amount_minor`→`amount`, `/date`, `/category_id`→`category`, `/counter_wallet_id`→`destination`, `/currency`, `/kind`, `/description`→`note`, `/memo`→`details`, `/counterparty`→`person`, `/cleared`.
**Rationale**: the engine owns classification; the handler only talks to its endpoint and table (spec 004 contract). Restore-then-PUT keeps "Restore with my change" one queue row, as the engine's `restore` flag intends.
**Alternatives**: a PATCH-only restore dropping the person's edit — rejected (the choice says "with my change").

## R-05 Whole-record PUT; PATCH only to restore; no GET of one transaction

**Decision**: every edit is a full `PUT` of the shown values (the wallets/categories decision);
`PATCH` is sent only by a restore. `GET /wallets/{id}/transactions/{tid}` is not called: a clash
carries `current`, a refusal its code.
**Rationale**: one write shape per record keeps the handler and its contract tests small; partial
updates gain nothing when the phone always holds the whole record.

## R-06 Deletion: queued local tombstone, hidden at once (clarification 2)

**Decision**: confirmed deletion → `engine.enqueueDelete(type, id, scope, row.version)`; a
never-sent create (`version = 0`) is removed at once with nothing sent (engine FR-004). A waiting
deletion hides the transaction from every list and removes its effect from the balance. No undo;
the clash path offers "Restore with my change".
**Rationale**: P-04.5; an undo after the deletion was sent would need a restore outside a clash,
which the engine does not queue today.

## R-07 Clash and refusal choices

**Decision**: the transaction editor doubles as the detail and shows the status:
- live clash → differing fields side by side, "Keep mine" (`engine.keepMine`) / "Use the other device's" (`engine.useTheirs`);
- tombstone clash on an edit → "Restore with my change" (`keepMine`, engine sets `restore`) / "Accept the deletion" (`useTheirs`);
- clash on a deletion → "Delete anyway" (`keepMine`) / "Keep the transaction" (`useTheirs`);
- refused → "Not saved" + reason; "Try again" (`engine` re-enqueue unchanged) for `NotVerified`; "Discard" (`engine.discard`); editing re-enqueues.

`RefusalKind` (domain): `NotVerified` (`EMAIL_NOT_VERIFIED`), `NoLongerAvailable` (`NOT_FOUND`),
`NoRightHere` (`FORBIDDEN`, income/expense), `NoRightInOtherWallet` (`FORBIDDEN`, transfer),
`CategoryUnusable` (`INVALID_ENUM_VALUE` at `category`), `DestinationUnavailable`
(`INVALID_ENUM_VALUE` or `REQUIRED` at `destination`), `CurrencyChanged` (`CURRENCY_MISMATCH` at
`currency`), `CurrenciesDiffer` (`CURRENCY_MISMATCH` at `destination`), `Invalid` (other
`VALIDATION_FAILED`), `Other` (with request id). Chosen by code and pointer only (P-02.4).
**Rationale**: the brief's "a 403 on a transfer write must show as no rights in the other wallet";
the backend summary's precedence list.

## R-08 Refresh from the server (wave 1, clarification 4)

**Decision**: `TransactionRepository.refreshWallet(walletId)` reads every page of
`GET /wallets/{id}/transactions?limit=200` (default sort), upserting each page as it arrives
(rows show progressively), then — only when every page succeeded — removes rows touching that
wallet (`wallet_id = W OR counter_wallet_id = W`) that were not listed, have `version > 0` and no
queue row, and stamps `sync_scope` `transactions:<W>`. Triggers: the wallet's transactions screen
opening (60 s floor per wallet), pull to refresh (forced), foreground while that screen is open
(floor). A 404 stops the read and leaves the wallet's fate to C4's refresh. When a complete read
changed any row, the domain asks C4 to re-read balances (`WalletsService.refresh(force = true)`)
so the balance and the list describe the same server state.
The foreground trigger belongs to the open screen: the transactions Route forwards its lifecycle's resume as the `Foregrounded` intent and the store calls `refresh(walletId)` (floor applies); the coordinator only kicks the balance reconciler on foreground.
**Rationale**: P-04.1 needs the whole history locally for offline search; the wallet feed (C14
wave 2) replaces this. 10,000 transactions = 50 requests, the backend's p95 for a page is 60 ms.
**Alternatives**: fetching only the first page and paging on scroll — rejected: offline search and
the day totals would silently cover part of the history, and deletion elsewhere could not be
detected.

## R-09 Balance projection and the carry (FR-017, `PendingPostings`)

**Decision**: the effect (computed by `data`'s `BalanceEffect`, R-04) of a transaction state `s` on wallet `W` is `+amount` for an income in W
or a transfer into W, `−amount` for an expense in W or a transfer out of W, 0 otherwise and for a
deleted or absent state. `TransactionPostings : PendingPostings` (domain, bound in place of
`ZeroPendingPostings`):
- `deltaFor(W)` = Σ over queue rows of type `transaction` touching W (Waiting, Sending, Clash, Refused) of `effect_W(shown) − effect_W(row)` (a deletion's shown state is "deleted"; a create's row is the placeholder, effect 0), **plus** Σ of the carry for W.
- `hasPending(W)` = any such queue row exists.
- **Carry**: when the handler applies the answer to its own send, it adds `effect_W(new) − effect_W(old row)` for each touched wallet to that row's carry (`carry_source_minor`, `carry_counter_minor`) and stamps `carry_confirmed_at` (UTC, now). `BalanceReconciler` (domain) watches for rows with a carry, waits 1 s (debounce), notes `start = now`, calls `WalletsService.refresh(force = true)`; on `Done` it clears every carry confirmed at or before `start` and purges hidden tombstone rows with no carry left. Offline/failed: it retries on the next foreground, connectivity or send. "Use the other device's" / "Accept the deletion" also ask for one wallet re-read (no carry: the other device's change may or may not be in the wallet's last read, and only a read settles it).
**Rationale**: without the carry, the queue row disappears the moment the send succeeds while the wallet row still holds the old `balance_minor` until the next wallet read, so the balance would jump back and then forward (the brief: "Local balance = server balance + pending local changes; reconcile on pull"). Comparing against the read's start time avoids clearing a carry with a read the server answered before committing the change.
**Alternatives**: re-reading the wallet inside the handler after each send — rejected: `data` would call another feature's domain (P-10.6) and every send would cost two requests; accepting the jump — rejected (SC-004).

## R-10 Categories on transaction screens (proves C5 US4)

**Decision**: the editor's category field opens `CategoryChoiceSheet` fed by
`CategoriesService.observeChoices(kind, currentCategoryId)`; rows and the detail show
`observeDisplays(walletId).resolve(id)` through `CategoryRow`/`CategoryTile`; the transactions
screen calls `refreshReferences(walletId, force = true)` when it opens. `TransactionCategoryUses :
PendingCategoryUses` (domain, bound in place of `NoPendingCategoryUses`) emits the category ids in
the payloads of non-deletion queue rows of type `transaction`.
**Rationale**: C5's consumer rules 1–5 (contracts/categories-domain.md).

## R-11 Transfers (clarification 5)

**Decision**: destination choices come from `WalletsService.observeList()`: active, visible,
same currency as the source, role Owner or Editor, not the source. A row in wallet W reads "To
<counter name>" when W is the source and "From <source name>" when W is the destination; a wallet
not on the phone reads "another wallet". Editing a transfer needs Owner/Editor in both wallets on
the phone; deleting needs it in the path wallet (R-04), and the server decides the rest. Day
totals and the balance use the direction in W.
**Rationale**: ADR-0015; T-03; the backend's `INVALID_ENUM_VALUE`/`CURRENCY_MISMATCH` at
`/counter_wallet_id` are avoided up front rather than met as refusals.

## R-12 Search and filters on the phone (clarification 3)

**Decision**: Room selects the wallet's live rows (plus placeholders) by date range, kinds and
categories (indexed `IN` and range); the search runs in Kotlin as
`contains(trimmed, ignoreCase = true)` over `description`, `memo`, `counterparty` of the shown
(pending-overlaid) values — a literal match, so `%` and `_` match themselves (transactions.yaml).
Date presets use the profile time zone's current month. Category filter choices: the categories
the wallet's transactions use plus the person's choosable ones (resolved through C5's display).
**Rationale**: works offline; 2,000 rows filter in well under 300 ms on the host (performance test).
**Alternatives**: SQLite `LIKE` — rejected: case-insensitive only for ASCII and needs escaping;
the server's search parameter — rejected (P-04.1, offline).

## R-13 List rendering

**Decision**: one Room query per wallet ordered `date DESC, created_at DESC, id DESC`, combined
with the queue's rows into display items; a `LazyColumn` with `DateHeader` (sticky) and
`TransactionRow`. "Today"/"Yesterday" use the profile time zone; other dates use `formatDate`
(Western digits). Day total = Σ of the day's effects in this wallet.
**Rationale**: the existing design-system rows; lazy composition for long histories (spec 007
lesson).

## R-14 Screens and navigation (clarification 1)

**Decision**: two MVI screens. `TransactionsKey(walletId)` — the wallet's transactions (search
field, filter chips and sheet, day-grouped list, "Add transaction" FAB, pull to refresh, offline
"Updated …"). `TransactionEditorKey(transactionId: String? = null, walletId: String? = null)` —
create (wallet preselected, or Home's default) / edit / detail with status and choices. Entry
points: a "Transactions" row on the wallet detail (C4's `WalletDetailIntent.TransactionsClicked` →
effect → `AppNavHost` pushes `TransactionsKey`; replaces the "No transactions yet" placeholder),
an "Add transaction" action on the wallet detail for Owner/Editor of an active wallet,
and an "Add transaction" button on Home (`home.addTransaction`). The last wallet recorded in is
kept in `KeyValueStore` under `transactions.lastWallet`, cleared at sign-out by a
`UserDataCleaner`. When the person can write to no wallet, the editor shows "Create a wallet
first" with a button that opens the wallet editor.
**Rationale**: P-10.4 (wallets ui reaches transactions only through navigation); entry in at most
two taps from Home or a wallet.

## R-15 Amount input

**Decision**: reuse `feature.wallets.domain.AmountInput.parse(text, currency, liability = false)`;
a valid result ≤ 0 or a leading minus is refused as "Enter an amount greater than zero"; its
`AmountDigits`/`AmountOutOfRange` problems map to the transaction's own field problems. Editing
shows `AmountInput.format(minor, currency, false)`.
**Rationale**: one exact, float-free parser (P-03.2) already tested for EGP/KWD/JPY digits and
Arabic-Indic input.

## R-16 Dates

**Decision**: `kotlinx.datetime.LocalDate`; today = `Clock.System.todayIn(profile time zone ?: device
zone)`. A new design-system `AppDatePickerDialog` wraps Material 3's `DatePickerDialog`, limited to
1900-01-01…2999-12-31, with a field showing `formatDate`. The picker is rendered inside a
Western-digit locale override (the app's `LocalAppLocale` with the `-u-nu-latn` numbering
extension) so Arabic shows Western digits (P-06.2); its screenshot in Arabic RTL proves it.
**Rationale**: P-05.2–P-05.3, P-07.3.
**Risk**: Material 3's picker formats through the platform; if the override does not reach it on
some Android versions, the fallback is a simple design-system day/month/year field.

## R-17 Gates and roles

**Decision**: verified address checked before any write (existing verify prompt via an
`AppNavHost` callback, as specs 006–007); role from the wallet view (`server.myRole`): Owner and
Editor write, Viewer reads; no entitlement check (backend: no limit). The server's `FORBIDDEN` is
the authority (R-07).

## R-18 Currency

**Decision**: the editor shows the wallet's currency; the payload stores it at save time. A
`CurrencyChanged` refusal keeps the transaction marked; "Edit" opens the editor with the wallet's
current currency and an empty amount (FR-026). The wallet editor (C4 ui) locks the currency when
`WalletView.hasPendingPostings` is true (C4 contract rule 4).

## R-19 Wallet removal (`WalletDeletionParticipant`)

**Decision**: `TransactionsWalletCleanup` removes every row whose `wallet_id` and
`counter_wallet_id` are both absent from the phone's wallets after the removal (so a transfer into
a wallet still on the phone stays), and discards their queue rows (`engine.discard`). A wallet
hidden by its own waiting deletion hides its transactions (C4 rule 2) without removing them.
**Rationale**: nothing can be saved into a deleted wallet (deletion final); keeping unreachable
rows would show money nobody can see in a list.

## R-20 Client error codes

**Decision**: no new `ApiErrorCode` (`CURRENCY_MISMATCH` is a field-level code carried in
`errors[].code`); the transaction copy maps it by pointer (R-07), and `AuthErrorCopy`'s generic
field-code mapping gains a `CURRENCY_MISMATCH` line so the exhaustive copy stays complete.
`INVALID_QUERY_PARAMETER`/`INVALID_CURSOR` can only follow a client bug in the list read (no
filters are sent); they fail the refresh as "couldn't be updated".

## R-21 Sync engine addition

**Decision**: `SyncEngine.observeAll(recordType): Flow<List<PendingChange>>` backed by a new
`PendingChangeDao.observeByType(type)` query — additive, used by `PendingPostings`,
`PendingCategoryUses` and the list overlay.
**Rationale**: the hooks need every unsent transaction, not one by id.

## R-22 Unknown kind and recurring links

**Decision**: a kind other than income/expense/transfer is stored as received, listed with the
neutral icon and unsigned amount, contributes 0 to day totals and pending effects, offers Delete
but not Edit. `recurring_rule_id` / `occurrence_id` are stored and shown as "From a recurring rule"
on the detail; never sent (FR-033).

## R-23 Logging

**Decision**: the handler, repository and services log record id, outcome and request id only;
the `PendingChange.toString()` already redacts payloads; `TransactionEntity.toString()` is
overridden to id and version (as `WalletEntity`).

## R-24 E2E strategy (owner policy: no device runs)

**Decision**: flows under `.maestro/008-transactions/` (one per automatable scenario, parts for
offline and second-session steps), `api.js` transaction actions (`listTransactions`,
`createTransaction`, `saveTransaction`, `deleteTransaction`, `restoreTransaction`) beside spec
007's `createExpense`, and common flows `open-transactions.yaml` and `add-expense.yaml`. Every
task that runs them is marked **deferred: E2E on owner's device**.

## R-25 Host-only QA with live-backend contract checks (as spec 007)

**Decision**: after implementation, a QA pass without a device: every acceptance scenario, FR and
edge case mapped to a passing host test in `qa-report.md`; every call the app sends replayed with
`curl` against the local dev backend (`saveme-backend` container) with the app's headers and
bodies — create (201), identical replay (200), stale save (409 + live `current`), save after a
deletion elsewhere (409 + tombstone), restore PATCH, DELETE with a body (200 tombstone; repeated),
missing DELETE body (`REQUIRED`), 422 codes (`OUT_OF_RANGE`, `REQUIRED`, `INVALID_ENUM_VALUE` at
`/category_id` and `/counter_wallet_id`, `CURRENCY_MISMATCH` at `/currency` and
`/counter_wallet_id`, `IMMUTABLE_FIELD` at `/kind` and `/counter_wallet_id`, `UNKNOWN_FIELD` for
`recurring_rule_id`), 403 `EMAIL_NOT_VERIFIED`, 403 `FORBIDDEN` (viewer; transfer into a wallet
where the caller is a viewer), 404, the list (paging, transfer in both wallets) and the wallet's
`balance_minor` and `currency_locked` after writes. Real responses become fixtures in
`CapturedTransactionResponses.kt` driving `TransactionCapturedResponsesTest` through the handler,
engine and service.
**Rationale**: the owner's testing policy; spec 007 found no drift this way and caught two app
bugs.

## R-26 Koin wiring (spec 006 lesson)

**Decision**: `TransactionRepository` and the hooks resolve `SyncEngine` lazily; `AppGraphTest`
gains assertions that the real graph resolves `TransactionsService`, the `transaction` handler,
`PendingPostings` as `TransactionPostings`, `PendingCategoryUses` as `TransactionCategoryUses`
and the wallet cleanup participant. C4's and C5's `single<…> { Zero… }` bindings are replaced in
their modules by the transactions module's (Koin override is not relied upon).

## R-27 Arabic copy

**Decision**: "معاملة / المعاملات" (Transaction), "مصروف", "دخل", "تحويل" (existing catalogue
words), "ملاحظة" (Note), "تفاصيل" (Details), "الشخص" (Person), "تمت المطابقة" (Cleared), "إلى
<wallet>" / "من <wallet>" for transfers, clash copy reusing spec 007's wording without "نسخة".
Owner review listed in the summary.

## R-28 No new dependencies

**Decision**: the existing stack covers everything (Material 3 date picker, Room, Ktor, Koin,
kotlinx-datetime). P-14.5 satisfied.
