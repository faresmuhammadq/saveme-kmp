# Feature Specification: Sync Feeds and Batch Upload (client)

**Feature Branch**: `009-sync-feed-batch`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "specs/briefs/C14-sync-engine.md" — client brief C14, **wave 2**: switch the spec 004 sync engine from per-record writes and whole-list reads to the backend's change feeds and batch upload. Pairs with backend B14 → spec 013 (built, committed `4f26ef5`).

**Backend contract**: `saveme-backend@6b8e290`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/sync.yaml` (frozen at v1.6 in backend spec 013; v1.7 adds `recurring_rule` and `occurrence` rows and batch items, v1.8 `budget`, and the working tree's uncommitted v1.9 adds `invitation` rows on the wallet feed), `specs/013-sync-protocol/contracts/sync-api.md` (the frozen answers to this brief's "Needs from the backend"), `specs/013-sync-protocol/summary.md`, `docs/adr/0016-batch-upload-beside-per-record-writes.md`, `docs/adr/0017-membership-on-the-members-user-feed.md`, `docs/adr/0015-transfers-on-two-wallet-feeds.md`, `docs/api-conventions.md` (Batch upload, Change feeds, Purge horizon), `docs/openapi/error-catalog.yaml` (`INVALID_CURSOR`, `SYNC_CURSOR_EXPIRED`; no new code in spec 013).
- Builds on spec 004 (the engine: queue, collapse, revision guard, the answer classification in `contracts/sync-engine.md`, clash and refusal choices, status, WorkManager drain), spec 006 (C4 wallets: whole-list wallet reads, members read, `WalletDeletionParticipant`, the 60 s refresh floor), spec 007 (C5 categories: own list read, per-wallet category references read on demand) and spec 008 (C6 transactions: whole-wallet transaction reads, the balance carry settled by a wallet re-read, `TransactionsWalletCleanup`, the currency lock until a re-read). Their "Implementation notes" and `summary.md` record the read paths this spec replaces.

## Clarifications

### Session 2026-10-02

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: When the person loses a wallet (ended own membership, or its feed answers "not found") while changes to it still wait, what happens to those changes? → A: They leave the phone with the wallet (the server can never accept them), through the existing spec 006/008 wallet-removal path — except a change to the wallet record itself, which keeps the wallet until the person settles it (so a deletion elsewhere still shows today's "Accept the deletion" choice); waiting transfers that still touch a wallet the person keeps stay and are answered by the server. A wallet created on this phone and not yet accepted is never treated as lost. (FR-011)
- Q: Should the phone store feed rows of kinds it does not consume yet (recurring rules, occurrences, budgets, invitations) for the features that will need them? → A: No. They are skipped and the position advances; the later feature that consumes a kind clears the positions of the scopes that carry it once, in its own migration, so it reads that kind from the start. Nothing unused is stored on the phone. (FR-006)
- Q: When a scope must be read from the start (expired or invalid position, first sync after this update), does the phone wipe that scope's copy first? → A: No. It keeps showing the existing copy, applies every page, and only after the last page removes the synced copies the server did not return (never ones with a waiting change or an unsettled balance carry). The end state equals "discard and re-read" from the contract without an empty screen in between. (FR-007, FR-008)
- Q: Should this feature add a periodic background read? → A: No. Reads run on sign-in, foreground (once a minute at most), screen open and pull to refresh, after "Use the other device's", and after an upload (only the scopes written to, also in the background drain job). Server-pushed refresh comes with C16. (FR-014, FR-027)
- Q: What happens to a waiting change that needs a record created on this phone that the server has not accepted yet (for example transactions in a new wallet that was refused)? → A: It is held, waiting, without counting a try, until that record's create is accepted (or is earlier in the same upload); if the person discards the record, the existing cleanup discards its dependents. It is never sent early only to be refused. (FR-022)

## User Scenarios & Testing *(mandatory)*

Until now the phone keeps itself current by re-reading whole lists (every wallet, every member,
every transaction of an open wallet, every category, every reference) and sends each waiting change
in its own request. A wallet with 10,000 transactions costs about 50 page reads each time it is
opened, and 20 offline edits cost 20 round trips on a poor mobile network. The backend now offers
two change feeds (the person's own scope, and one per wallet) that return only what changed since
the phone last looked, and a batch upload that carries up to 50 waiting changes in one request with
an answer per change. This feature moves the engine onto both, without changing anything a person
decides: clashes, refusals, "waiting to sync" and sign-out behave exactly as in specs 004–008.

### User Story 1 - A backlog of offline changes leaves quickly and in order (Priority: P1)

A person records twenty changes offline across wallets, categories and transactions (including a
new wallet and transactions in it, and a new category used by a transaction). When the connection
returns, all of them reach the server in a few requests, in the order they were made, and each
change ends exactly as it would have if sent alone: accepted, clashed, or refused with a reason.

**Why this priority**: It is the brief's integration checkpoint and the reason batch upload exists; a long backlog on a weak connection is where per-record sending is slowest and most fragile.

**Independent Test**: With the network off, create a wallet, a category, and 18 transactions (some in the new wallet using the new category), edit two existing ones; turn the network on: everything is on the server after at most a few requests, none is refused because something it needed was not created yet.

**Acceptance Scenarios**:

1. **Given** 20 waiting changes and a connection, **When** the app syncs, **Then** they reach the server in at most 2 upload requests (plus one separate request for each change the batch upload cannot carry), and each stops showing "waiting to sync" once accepted.
2. **Given** a new wallet and transactions in it are waiting, **When** they are sent, **Then** the wallet is created before its transactions, and no transaction is refused because its wallet did not exist yet.
3. **Given** a new category and a transaction that uses it are waiting, **When** they are sent, **Then** the category is created first and the transaction is accepted with it.
4. **Given** one waiting change clashes with another device's edit, **When** the upload is answered, **Then** that change shows the same choice it shows today ("Keep mine" / "Use the other device's", or the deletion choices), and every other change in the same upload is accepted or refused on its own.
5. **Given** a change refused for good (for example a value the server no longer accepts), **When** the upload is answered, **Then** it shows "not saved" with the same reason and choices as today, and is not retried automatically.
6. **Given** the needed wallet or category of a waiting change could not be created (it clashed, was refused, or is still waiting), **When** the app syncs, **Then** the change that depends on it keeps waiting and is not refused because of it.
7. **Given** an upload is cut off (no connection, the server busy, or no answer in time), **When** the app tries again, **Then** nothing is lost or duplicated and the same changes are sent again.
8. **Given** an archive, a "Restore with my change" or an ownership transfer, **When** it is sent, **Then** it works exactly as today (these are not carried by the batch upload and keep their own requests, in their place in the order).

---

### User Story 2 - The phone stays current by downloading only what changed (Priority: P1)

A person opens the app, a wallet, or pulls to refresh, and sees what changed on the server since the
phone last looked (on another device, or by another member), without the app downloading everything
again. Deletions made elsewhere disappear; transfers show in both wallets; balances stay right.

**Why this priority**: Whole-list re-reads grow with history; a large wallet's re-read is the remaining risk recorded by spec 008. Feeds make every refresh cost what changed, not what exists.

**Independent Test**: Sign in with a wallet of 2,000 transactions (one full read), then add one transaction from another session and pull to refresh: the new transaction and the new balance appear after one small download; nothing else is re-downloaded.

**Acceptance Scenarios**:

1. **Given** the phone has read everything once, **When** the app comes back to the foreground or a screen is refreshed, **Then** only records changed since the last read are downloaded and applied.
2. **Given** a record was edited on another device, **When** the phone reads, **Then** the phone shows the newer version; an older copy never replaces a newer one.
3. **Given** a record was deleted on another device, **When** the phone reads, **Then** it disappears from the phone (unless this phone still has a change waiting for it, which then meets the deletion as a clash, as today).
4. **Given** a transfer between two of the person's wallets was recorded elsewhere, **When** the phone reads, **Then** it appears once, listed in both wallets, and both balances match the server.
5. **Given** this phone's own change was just accepted, **When** the phone next reads, **Then** the wallet balance never jumps back to the old value or counts the change twice (the spec 008 "no flicker" rule holds).
6. **Given** a read stops halfway (connection lost, app killed), **When** the app reads again, **Then** it resumes from where it last finished applying, and nothing is skipped or applied twice in a visible way.
7. **Given** the server sends a kind of record this version of the app does not know (for example a recurring rule or a budget before those features exist on the phone), **When** the phone reads, **Then** it skips it and everything else still applies.
8. **Given** no connection, **When** a screen is shown, **Then** it shows the phone's copy and when it was last updated, exactly as today.

---

### User Story 3 - Wallets the person gains or loses follow them (Priority: P1)

When the person gains access to a wallet (for example they created it on another device, or later
are invited to one), it appears on the phone with its history. When they lose it (the wallet was
deleted, or later they leave or are removed), the wallet and everything of it leaves the phone.

**Why this priority**: Without it a wallet created on another device never appears until a whole-list read, and a lost wallet's data stays on a phone that no longer has the right to it (privacy, backend P-08).

**Independent Test**: Create a wallet from a second session; refresh on the phone: it appears with its transactions. Delete it from the second session; refresh: it and its transactions are gone from the phone.

**Acceptance Scenarios**:

1. **Given** the person became a member of a wallet the phone does not have, **When** the phone reads, **Then** the wallet appears with its members, transactions and category references.
2. **Given** the person's membership of a wallet ended (deleted wallet, or later leaving or removal), **When** the phone reads, **Then** the wallet, its members, its category references, its transactions (except transfers that still touch a wallet the person keeps) and their waiting changes are removed from the phone; if the person has a change waiting for the wallet itself, the wallet stays until they settle it (a deletion shows today's "Accept the deletion" choice).
3. **Given** the person's role in a wallet changed, **When** the phone reads, **Then** the actions offered for that wallet follow the new role.
4. **Given** a wallet created on this phone has not reached the server yet, **When** the phone reads, **Then** it stays on the phone (it is not treated as lost).

---

### User Story 4 - A phone offline for a very long time recovers on its own (Priority: P2)

Deletions are kept by the server for 90 days. A phone that has not synced for longer cannot learn
what it missed from the feed, so the server tells it to start that part again. The phone re-reads
just that part, and the person's waiting changes are kept and still sent.

**Why this priority**: Rare, but without it a long-idle phone would show records deleted months ago forever, or stop syncing.

**Independent Test**: Make the server refuse the phone's stored position for one wallet as expired; refresh: that wallet is re-read from the start, records deleted elsewhere disappear, other wallets are untouched, and a waiting change in that wallet is still sent.

**Acceptance Scenarios**:

1. **Given** the server says the phone's position for one wallet (or the person's own scope) has expired, **When** the phone reads, **Then** it re-reads that part from the start, removes what the server no longer has, and keeps every other part as it is.
2. **Given** that re-read, **When** the person has changes waiting in that part, **Then** those changes are kept and sent as usual, and their records stay visible on the phone.
3. **Given** the server says a stored position is not valid (for example it belongs to another account), **When** the phone reads, **Then** it starts that part again from the beginning instead of failing.
4. **Given** the app was updated to this version with data already on the phone, **When** it first syncs, **Then** it reads every part once from the start and the screens keep showing the existing data while it does.

---

### User Story 5 - Sync keeps working when the batch upload can't be used (Priority: P3)

If an upload request as a whole is refused for a reason that is not about the person's changes (for
example a server that does not offer the batch upload), the phone sends those changes one by one
instead, as it does today, and nothing is lost.

**Why this priority**: A safety net; the batch path is the default and should rarely fail as a whole.

**Independent Test**: Make the batch upload answer "not found"; make an edit: it reaches the server through its own request.

**Acceptance Scenarios**:

1. **Given** the batch upload is refused as a whole for a reason other than the connection, the session or a busy server, **When** the phone syncs, **Then** the same changes are sent one per request in the same order, with the same outcomes.
2. **Given** the app is killed in the middle of an upload or a read, **When** it runs again, **Then** it resumes: unanswered changes are sent again (safe to repeat) and the read continues from the last applied page.

### Edge Cases

- **Same record in a batch twice**: never; the queue holds one change per record (spec 004), so one upload carries a record at most once.
- **An edit made while its upload is in flight**: as today, the in-flight answer settles the old content and the new edit waits and goes next on top of the returned version.
- **Upload answered but the answer lost** (timeout): sent again; already applied changes come back accepted with nothing changed. A clash that a first attempt would not have had may appear (backend ADR-0016 cost); it is shown like any clash.
- **A needed record created on this phone is refused** (for example a new wallet over the plan limit): it shows "not saved" with its reason; the transactions that need it keep waiting; discarding the wallet discards them with it (spec 008 wallet cleanup).
- **A transaction's category was created on this phone and is waiting** in another scope (categories belong to the person, transactions to a wallet): the category still goes first.
- **A transfer whose destination wallet was created on this phone and is waiting**: the destination goes first.
- **A transfer arrives on both wallets' feeds**: applied once; the later read of the same version changes nothing.
- **A wallet record re-sent with the same version but a new balance** (anything in the wallet changed): applied, since the balance is the server's.
- **A record deleted elsewhere while this phone has a change waiting for it**: the record stays until the person decides (the waiting change meets the deletion as a clash, as today).
- **The person's own deletion, accepted, then read back from the feed**: the deleted row stays hidden with its balance carry until the wallet's balance read covers it (spec 008), so the balance does not jump.
- **A read applies half its pages and the app dies**: the next read starts after the last applied page; records arriving again are applied only if newer or equal.
- **The person loses access to a wallet while a change to it waits**: the wallet, its data and their waiting changes leave the phone (they can never be sent); waiting transfers that still touch a wallet they keep stay and are answered by the server. If the waiting change is to the wallet record itself, the wallet and its data stay, no longer read, until the person settles that change (FR-011).
- **A waiting transaction edited to use a category created later on this phone**: the transaction is older in the queue than the category, so it is held for one run while the category goes first, then sent.
- **An own membership missing from a full read of the person's scope** (no ended row): the wallet is not removed on that alone; its own feed decides (a `404` removes it).
- **A new wallet on the phone has never reached the server**: it has no feed yet and is not read; once accepted, it is read from then on.
- **Reading a wallet's feed answers "not found"**: the person no longer has the wallet; treated like a lost membership, unless the wallet was created on this phone and not yet accepted.
- **Signed out mid-sync**: the run stops; sign-out empties the queue, the copies and every stored position (spec 004); nothing is sent or applied under another account.
- **A different account signs in on the same phone**: no stored position from the previous account exists (wiped at sign-out); a stray one would be refused by the server and the part re-read from the start.
- **A kind of record the app knows, with fields it does not**: unknown fields are ignored (P-02.3).
- **A kind of record the app learns in a later version** (recurring rules in C7, budgets in C8, invitations in C9): that later feature re-reads the parts that carry it from the start once, so nothing skipped earlier is missed.
- **Platform default categories**: not on any feed; still read from their own list as today.
- **The plan and entitlements, the account and the email-verified flag**: not syncable; still read as today.

## Requirements *(mandatory)*

### Functional Requirements

**Reading changes (feeds)**

- **FR-001**: The app MUST keep the phone's copy of the profile, device sessions, own categories, own memberships, wallets, wallet members, category references and transactions current by reading the person's scope change feed and one change feed per followed wallet, in place of whole-list reads.
- **FR-002**: A read MUST take the person's scope first, then each followed wallet. The followed wallets are those the phone holds that the server has accepted, plus every wallet with a live own membership; a wallet with an ended own membership is not read.
- **FR-003**: Each scope MUST have one stored position. The app MUST send it with the next read of that scope, store the new position the server returns after all records of a page are applied, keep reading while the server says more remain, and store the final position even when a page is empty.
- **FR-004**: A record from a feed MUST replace the phone's copy only when its version is the same or newer; an older version MUST be ignored. Applying a record MUST NOT touch the person's waiting change for it (the queue overlays the copy, P-04).
- **FR-005**: A record that arrives deleted MUST remove the phone's copy, except while the person has a change waiting for it (it stays until the change is settled) and except the person's own accepted deletion of a transaction whose balance carry has not settled (it stays hidden, spec 008).
- **FR-006**: A row of a kind the app does not consume MUST be skipped without failing the read; the read's position still advances. A later feature that starts consuming a kind MUST clear the positions of the scopes that carry it once, so that kind is read from the start.
- **FR-007**: A read without a stored position (first sync, after an update to this version, after FR-008/FR-009) MUST, once every page of that scope has been applied, remove the phone's synced copies of that scope that the read did not return, except records with a waiting change and transaction rows whose balance carry has not settled. Until that read completes, the phone MUST keep showing its existing copy.
- **FR-008**: When the server answers that a scope's position has expired (`410 SYNC_CURSOR_EXPIRED`), the app MUST clear that scope's position and read it from the start under FR-007, keep every waiting change, and leave every other scope untouched.
- **FR-009**: When the server answers that a stored position is invalid (`400 INVALID_CURSOR`), the app MUST clear it and read that scope from the start, at most once per sync run; a second refusal in the same run MUST end that scope's read as failed (logged with the request id) and leave the phone's copy as it is.
- **FR-010**: A live own membership for a wallet the phone does not have MUST make the app read that wallet's feed from the start in the same sync run. A changed role MUST be reflected in what the app offers for that wallet once its record is read.
- **FR-011**: An ended own membership, or a wallet feed answering `404 NOT_FOUND` for a wallet the server had accepted, MUST remove that wallet from the phone with its members, category references, stored position, its transactions that touch no remaining wallet, and the waiting changes of everything removed (the spec 006/008 wallet-removal path). A wallet created on this phone and not yet accepted MUST NOT be removed this way. While the person has a change waiting for the wallet record itself, the wallet MUST stay (no longer read) until that change is settled, so a deletion elsewhere still reaches them as today's "Deleted on another device" choice; accepting the deletion or discarding the change then removes it the same way. A wallet the phone holds whose own membership is merely missing from a complete read of the person's scope (no ended row) MUST NOT be removed on that evidence alone: its wallet feed is read, and only a `404` removes it.
- **FR-012**: A transfer MUST be stored once whichever wallet's feed brings it, and listed under both wallets (ADR-0015). Positions and record order MUST NOT be compared across feeds.
- **FR-013**: The wallet's balance, currency lock and the person's role MUST come from the wallet record on that wallet's feed. The balance carry of an accepted change (spec 008) MUST settle once a read of that wallet's feed that started after the change was accepted has completed; the displayed balance MUST never jump back or count a change twice.
- **FR-014**: Reads MUST run when the person signs in, when the app returns to the foreground, when a screen that shows synced data is opened or pulled to refresh, after "Use the other device's", and after an upload that changed anything (the scopes it wrote to). Each trigger keeps the floor it has today (specs 006–008): sign-in, pull to refresh and "Use the other device's" are forced; every other trigger reads a scope at most once a minute, exactly as each screen and coordinator asks today. Records MUST be applied page by page, so screens fill progressively.
- **FR-015**: Screens' "Updated …" times and refresh outcomes (done, offline, failed) MUST keep their meaning: the time of the last completed read of the scope(s) behind the screen, and the outcome of the read the screen asked for. When a screen's read covers several scopes, it is done only when all completed, offline when any could not reach the server, and failed otherwise (the spec 007 rule).
- **FR-016**: Platform default categories, the account, the email-verified flag and entitlements, which no feed carries, MUST keep their current reads; no other whole-list or single-record read of a syncable record may remain on a sync path.

**Sending changes (batch upload)**

- **FR-017**: Waiting changes MUST be sent through the batch upload by default: due changes of the signed-in account, oldest first, at most 50 per request, each item being exactly the per-record write the change would have made (same body, same version).
- **FR-018**: A change the batch upload cannot carry (an archive, an archive with an edit, "Restore with my change", and any other write the batch contract does not list) MUST be sent through its own per-record requests at its place in the order: the batch before it is sent first, and batching resumes after it.
- **FR-019**: Each item's answer MUST be settled exactly as the same per-record answer is today (spec 004 classification): accepted → the record replaces the copy and the change is cleared (or rebased if edited meanwhile); clash with the current record → the person's choice; a retryable code → waiting with backoff; any other code → "not saved" with the reason and field mapping. One item's outcome MUST NOT affect another item's.
- **FR-020**: A request-level answer MUST apply to every item of that request as the same answer to a single write would: no connection or no answer in time → waiting (as offline/timeout); session ended → unchanged, sign-out flow; any other request-level refusal (busy, server error, rate limit, update required) → each item settles exactly as the same single write with that answer settles today, honouring any wait the server asks for.
- **FR-021**: When the upload request as a whole is refused for a reason that is not about the changes (the request shape refused, the endpoint not found or not allowed, an unreadable answer, or an answer whose items don't match the request), the same changes MUST be sent one per request in the same order in that run, and the batch upload MUST be tried again on the next run.
- **FR-022**: A change that needs another record to exist first (a transaction's wallet, a transfer's destination, a transaction's category) MUST NOT be sent before that record's create was accepted, unless that create is earlier in the same request. A dependent change whose prerequisite is not accepted (waiting later, clashed, refused or backing off) MUST keep waiting without counting a try; if its prerequisite earlier in the same request was not accepted, the dependent's answer MUST be ignored and it MUST return to waiting without counting a try.
- **FR-023**: An upload MUST be allowed at least 60 seconds for its answer. A timed-out upload MUST be safe to send again (each item is idempotent by id and version).
- **FR-024**: The upload MUST carry only the members the contract declares; a wallet id only on transaction items (the source wallet unless the person no longer has it, then the destination, as today); the profile item's id MUST be the person's profile id. No idempotency key is sent.
- **FR-025**: Ownership transfer, archive, and restore MUST keep their per-record endpoints unchanged (backend ADR-0016 rule 1); the per-record write path MUST remain available to the engine.

**Engine, storage and safety**

- **FR-026**: Only one sync run (upload then reads) MUST be active at a time; triggers during a run MUST be merged into the next run, not lost. Uploads MUST run before the reads of the same run. A run whose upload found no connection MUST skip its reads and report offline.
- **FR-027**: A run MUST survive process death: unanswered items are sent again (spec 004 stale-sending reset) and reads resume from the last stored position. Background runs (the existing drain job) MUST upload and then read the scopes they wrote to.
- **FR-028**: Every send and every read MUST be for the signed-in account only; positions, copies and the queue MUST be emptied at sign-out (spec 004 FR-018, P-09).
- **FR-029**: The on-device database version MUST go up by one with a migration that keeps all existing data and starts with no stored positions (so the first run is a full read per FR-007), and the schema MUST be exported.
- **FR-030**: Logs MUST record per upload the number of items and per item the record type, id, outcome and code with the request id, and per read the scope, the number of rows and kinds skipped; never record content, amounts, notes or positions' values beyond their presence (P-09.2, P-13).
- **FR-031**: Every client mismatch reported by the contract check MUST be fixed; none exists at `saveme-backend@6b8e290`.
- **FR-032**: The app MUST recognise the catalogue codes this feature branches on, `INVALID_CURSOR` (400) and `SYNC_CURSOR_EXPIRED` (410), both retryable only after a change, so it never retries the same position automatically (P-02.4, P-02.6).
- **FR-033**: An item refused inside an upload MUST raise the same app-wide signals the same single write raises today (an unverified address prompts verification; a plan limit raises the entitlement signal), so nothing a person saw before depends on which path carried the change.

### Key Entities *(include if feature involves data)*

- **Scope position**: per scope (the person's own scope, or one wallet), the opaque position the server returned after the last applied page, and when that scope's read last completed. Never parsed, compared or built by the phone.
- **Change row**: one record on a feed: its kind and the record exactly as its own read returns it; a deletion is a record with its deletion time set.
- **Upload item / item answer**: one waiting change expressed as its per-record write (kind, operation, id, wallet for transactions, body), and its answer (accepted with the record, clash with the current record, or refused with the problem).
- **Own membership**: the person's own membership of a wallet, from their scope's feed; live means follow that wallet, ended means remove it.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Moves the sync engine from whole-list reads and one-request-per-change sends to the backend's
change feeds and batch upload (backend B14), so refreshes cost what changed and an offline backlog
leaves in a few requests, while every decision the person makes (clashes, refusals, waiting,
sign-out) stays exactly as specs 004–008 define it. It also lets the phone discover wallets gained
and lost through the person's own memberships.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Change Row**, **Sync Batch**, **Purge Horizon**,
**scope** (user, wallet), **tombstone**, **Wallet Member**, **Transaction**, **transfer**,
**Category Reference**, **Device Session**. All internal: no new user-facing copy; the UI keeps
"Waiting to sync", "Changed on another device", "Not saved", "Updated …" and never says "feed",
"cursor", "batch", "scope", "version" or "tombstone".

### 3. Data model

No new syncable table. Each scope's position and "last completed read" time are stored per scope
in the existing `sync_scope` table (two new nullable columns); the on-device database version goes
up by one (5 → 6) with a migration that keeps all data, and the schema is exported. Own memberships
are stored in the existing `wallet_member` table. The queue (`pending_change`) is unchanged.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Screens render only from the phone's copy; feeds write to Room, never to a screen | P-04.1, FR-001 | service/store tests over fakes; no API in stores |
| A position is stored only after its page is applied; a resumed read applies nothing older over newer | FR-003, FR-004, FR-027 | puller tests with an interrupted page; version-guard tests per kind |
| A feed record never overwrites a waiting change; a record with a waiting change is never removed by a read or a sweep | P-04.4, FR-004, FR-005, FR-007 | applier and sweep tests |
| Unknown row kinds are skipped and the position advances | FR-006, contract | puller test with a `budget` / `invitation` row |
| A cursorless read removes only unseen synced copies without waiting changes, after the last page | FR-007, FR-008 | sweep tests per kind; 410 test |
| `410` resets one scope only; queued changes survive | FR-008 | puller test with two wallets |
| Ended membership / wallet feed 404 removes the wallet, its data and its queued changes; a never-accepted wallet is never removed | FR-011 | membership applier tests; cleanup participant tests |
| Batch items are exactly the per-record writes (same body and version) | FR-017, FR-024, ADR-0016 rule 2 | handler item-mapping tests vs the per-record request tests |
| Each item's answer settles exactly like the single write's (classification parity) | FR-019, spec 004 contract | engine parity tests: same fixtures through both paths |
| Non-batchable changes keep their place in the order | FR-018 | engine ordering test |
| A dependent change is never sent before its prerequisite create is accepted or earlier in the request | FR-022 | engine dependency tests (wallet, destination, category) |
| Request-level failures map to every item; shape/endpoint refusals fall back to per-record | FR-020, FR-021 | engine tests per request-level answer |
| Item refusals raise the same app signals as single writes | FR-033 | batch client test (`EMAIL_NOT_VERIFIED`, `FEATURE_NOT_ENTITLED` items) |
| Cursor codes are known and never auto-retried | FR-032, P-02.6 | `ApiErrorCode` test; contract check script |
| Balance never jumps or double counts across upload → feed read | FR-013, spec 008 SC-004 | postings/reconciler tests through the feed path |
| One run at a time; upload before reads | FR-026 | coordinator test |
| Only the signed-in account's changes and reads; sign-out wipes positions | FR-028, P-09 | engine + sign-out wipe tests |
| No payloads, amounts, notes or cursor values in logs | FR-030, P-09.2 | log-capture test over an upload and a read |
| Migration keeps data and starts with no positions | FR-029 | migration test 5 → 6 |

### 5. API

All under `/api/v1`, frozen in `sync.yaml` (backend spec 013, v1.6, later additive types).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Person's scope read | `GET /sync/user/changes?cursor=&limit=200` | — | 200 page `{data:[{type, record}], meta:{next_cursor, has_more, limit, sync_cursor}}`; `400 INVALID_CURSOR` (FR-009); `410 SYNC_CURSOR_EXPIRED` (FR-008); `401` session ended; offline/timeout/5xx → keep copy, outcome offline/failed |
| Wallet read | `GET /sync/wallets/{id}/changes?cursor=&limit=200` | — | as above, plus `404 NOT_FOUND` (FR-011) and `422 INVALID_ID` (logged, scope skipped) |
| Upload | `POST /sync/batch` | `{items:[{type, op, id, wallet_id?, data}]}`, 1–50 items; types `wallet` (upsert, delete), `category` (upsert, delete), `transaction` (upsert, delete; `wallet_id`), `profile` (upsert) | `200` with one `{type, id, outcome, status, record, problem}` per item (FR-019); request-level `401`, `426`, `429`, `5xx` (FR-020); `422 VALIDATION_FAILED` envelope, `400 MALFORMED_JSON`, `415`, `404`/`405` (FR-021) |
| Kept, per record | `PUT`/`DELETE` of wallets, categories, transactions; `PUT /me/profile`; `POST /wallets/{id}/archive`, `POST /categories/{id}/archive`, `PATCH /wallets/{id}/transactions/{tid}` (restore), `POST /wallets/{id}/transfer-ownership` | unchanged | unchanged (specs 002, 006–008); used for FR-018 and FR-021 |
| Kept, reads | `GET /me`, entitlements, `GET /platform-categories` (defaults) | unchanged | unchanged |
| Retired from sync paths | `GET /wallets`, `GET /wallets/{id}`, `GET /wallets/{id}/members`, `GET /wallets/{id}/transactions`, `GET /categories` (own), `GET /wallets/{id}/category-references`, `GET /auth/sessions` | — | — |

Feed row kinds consumed: user feed `profile`, `device_session`, `category`, `wallet_member`;
wallet feed `wallet`, `wallet_member`, `category_reference`, `transaction`. Skipped:
`recurring_rule`, `occurrence` (C7), `budget` (C8), `invitation` (C9), and any other.

#### Contract changes since last spec

Baseline `saveme-backend@f6d66eb` (spec 008) → `6b8e290`, plus uncommitted backend edits in
`sync.yaml`, `schemas.yaml` and `api-conventions.md` (spec 016, sharing, v1.9):

- **Batch item type `budget`** (v1.8): not sent by this client. Follow-up: not in scope of this spec (C8).
- **Invitations are not batch items; `invitation` rows on the wallet feed** (v1.9, uncommitted): skipped by FR-006. Follow-up: C9 consumes them.
- **Membership changes by invitation acceptance, role change, plan demotion, removal, leaving** (v1.9, uncommitted): handled generically by FR-010 (live row, new role) and FR-011 (ended).
- **`feature` member on `FEATURE_NOT_ENTITLED` problems** (v1.9, uncommitted): ignored as an unknown member (P-02.3); a refused wallet create still branches on the code. Follow-up: not in scope of this spec (C3/C9 may use it for copy).
- Error catalogue: no change since the baseline; the client's codes match the catalogue (no client mismatch).

### 6. Authorization and entitlements

Reading a wallet's feed needs active membership (spec 010 W-01); the phone follows only wallets it
holds or has a live own membership for, and treats `404` as access lost. Each batch item is
authorized like its single write (ADR-0016 rule 2); `FORBIDDEN`, `EMAIL_NOT_VERIFIED` and
`FEATURE_NOT_ENTITLED` items are refusals shown on the record as today. No plan feature gates sync.

### 7. Events

Not applicable: the client publishes no domain events. It consumes `ApiEvent`s (session ended,
update required, email not verified) as today.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Backlog | Engine unit tests (batching, order, dependencies, parity, request-level answers) over fake and captured answers; contract tests on a mock engine; live-backend contract QA (curl) | tests; captured fixtures |
| US2 Feeds | Puller and applier unit tests per kind; persistence tests (Room) for version guard and sweep; captured feed pages | tests; captured fixtures |
| US3 Gained/lost | Membership applier and wallet-removal tests; live QA with a second session | tests; QA log |
| US4 Expired | Puller tests for 410 and 400 per scope; migration test | tests |
| US5 Fallback | Engine fallback tests; process-death resume tests | tests |

Owner policy: no emulator, Maestro or device runs in this feature's QA. Proof is host tests plus a
contract QA against the live local backend (curl, the app's headers and bodies) whose real answers
become test fixtures, as specs 007 and 008 did. Any Maestro flow written is marked "deferred: E2E
on owner's device", including the brief's checkpoint (20 offline edits, a forced conflict, kill
mid-sync).

### 9. Open questions

- **One read per followed wallet on every foreground**: the backend offers no "which wallets changed" summary, so a person in N wallets costs 1 + N small reads per refresh. Acceptable at MVP scale; a backend summary endpoint is a possible later addition. Non-blocking.
- **Storing skipped row kinds for later**: decided against (FR-006); later features re-read instead. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 20 offline changes across wallets, categories and transactions reach the server in at most 2 upload requests (plus one request per archive or restore among them), and 100% end accepted, shown as a clash, or shown as "not saved" with a reason.
- **SC-002**: Zero changes are refused only because a wallet or category they needed was created on the phone but not yet on the server.
- **SC-003**: After the first full read, a refresh with nothing new downloads no records, and a refresh after one change elsewhere downloads that change (and its wallet's record) only, whatever the wallet's size.
- **SC-004**: Opening a wallet of 10,000 transactions that the phone already holds triggers no re-download of its history (spec 008 needed about 50 page reads).
- **SC-005**: The displayed balance equals the server's balance plus the effect of unsent changes at every moment of an upload → read sequence (no jump back, no double count).
- **SC-006**: A wallet gained or lost on another device appears on or leaves the phone within one refresh.
- **SC-007**: A scope whose position expired is fully re-read on its own, with no waiting change lost and other scopes untouched.
- **SC-008**: Every behaviour a person sees in specs 004–008 (clash choices, refusals, "waiting to sync", sign-out warning, "Updated …") is unchanged: their existing host tests pass unmodified except where they assert the retired reads.

## Assumptions

- Backend B14 is built and committed (`4f26ef5`, `sync.yaml` v1.6); later item and row kinds are additive (v1.7–v1.9).
- The batch upload carries `wallet`, `category`, `transaction` and `profile` items; recurring rules, occurrences and budgets join with C7 and C8.
- The profile is still also read through `GET /me` (it carries the account and verification state that no feed carries); the newer version wins.
- Category references are applied from wallet feeds; the spec 007 per-wallet on-demand reference read and its 60 s floor are replaced by the wallet's read.
- No new periodic background read in this feature: reads run on the triggers in FR-014; the background drain job reads only the scopes it wrote to. Push-triggered reads come with C16.
- Device runs are deferred to the owner's phone (owner policy); QA is host tests plus live-backend contract QA.
- Batch size is the contract maximum (50) with a 60 s answer allowance (the contract asks for at least 30 s; backend QA measured a 50-item p95 of 7.6 s, about twice that with Xdebug on the dev stack).
- Feed pages are read at the contract maximum of 200 rows, the page size the whole-list reads of specs 006–008 already used.
