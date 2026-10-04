# Feature Specification: Sync Heads Adoption (client)

**Feature Branch**: `011-sync-heads`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "Adopt the backend's spec 017 sync follow-ups: (1) store meta.sync_position beside each wallet cursor and use GET /sync/wallets/heads (one request) to skip wallets whose head change_seq equals the stored position, read from the stored cursor when the head is greater, read from no cursor when purged_change_seq exceeds the stored position — so a quiet refresh costs 2 requests (user feed + heads) instead of 1 + N; a wallet missing from the heads is NOT a removal signal (rely on the user feed's tombstoned membership or a wallet feed 404); (2) apply wallet_member rows whose version equals the stored one (a re-stamped row keeps its version but carries the new display_name) so partners see renames; (3) use restore-then-upsert (n, n+1) in one batch for 'Restore with my change' instead of the per-record PATCH+PUT two-step where the item types allow it (transactions; wallets/categories have no restore); (4) benefit from the backend's purge-horizon paging fix (a read from no cursor now pages to the end) — remove any client workaround that fails a scope after a mid-read 410 only if it is now unnecessary, keep the 'once per run' safeguard otherwise." — client brief C14, **wave 3** (sync follow-ups). Pairs with backend B14a → spec 017 (built, committed `c0406b0`, `sync.yaml` v1.10).

**Backend contract**: `saveme-backend@192e3ab`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/sync.yaml` v1.10 (additive: `GET /sync/wallets/heads` `listWalletHeads` with `WalletHead {wallet_id, change_seq, purged_change_seq}` and `CollectionMeta`; optional-in-schema, always-sent `ChangeFeedMeta.sync_position`; the wallet feed re-sending a renamed member's `wallet_member` row at the same `version`; the written `restore` (n) + `upsert` (n + 1) batch pair), `specs/017-sync-follow-ups/contracts/sync-follow-ups-api.md`, `specs/017-sync-follow-ups/summary.md` ("Contract notes for the client"), `docs/adr/0021-scope-heads-and-member-name-restamps.md`, `docs/api-conventions.md` ("Which wallets moved (v1.10)" table; `meta.sync_position` bullet), `docs/openapi/error-catalog.yaml` (no new code).
- Builds on spec 009 (C14 wave 2: `FeedPuller`, `FeedApplier`s, `WalletScopes`, `SyncRunner` floors and outcomes, positions on `sync_scope`, batch drain with held dependents, per-record "Restore with my change", the from-the-start 410 safeguard) and spec 010 (Room v7, engine additions `acceptsClash` and `RecordKey.untilSettled`). Their `summary.md` and "Implementation notes" record the paths this spec changes.

## Clarifications

### Session 2026-10-04

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Do forced refreshes of everything (sign-in, pull to refresh on the wallet list, "Use the other device's" without a named wallet) also let the heads skip unchanged wallets, or do they read every wallet? → A: The heads decide for them too. "Forced" only lifts the once-a-minute floor; a wallet whose head equals its stored position has nothing to return, so reading it would download nothing. Wallets the upload wrote to, unconfirmed and placeless wallets are always read (FR-004, FR-007).
- Q: When the heads show a wallet's purge horizon above its stored position, does the phone first try its stored place (and get refused as expired) or go straight to a read from the start? → A: Straight to a read from the start, forgetting the place first, exactly the spec 009 expiry path (copy kept, sweep after the last page, waiting changes kept) minus the refused request. A stored place that expires between the heads and the read is still handled by the refusal path (FR-005 rule 3).
- Q: In a restore pair, when the restore is accepted but the save is refused, does the phone store the restored record? → A: No. The change is settled exactly as the per-record path settles that save's answer today (clash, retry or "not saved"); the restored record arrives with the wallet's next read. Parity with today's two-step keeps one behaviour for both paths (FR-015).
- Q: Does the database update clear stored places so every scope gets its integer position at once? → A: No. Places are kept and positions start empty; each wallet is read once more from its own place (a normal small read) and stores its position then. Clearing would re-download every wallet from the start (FR-020, SC-007).
- Q: Now that the backend pages a from-the-start read past its purge horizon, should the client drop its "fail the scope once per run after a mid-read expiry" safeguard or restart once? → A: Keep it unchanged. The server still answers "expired" when it purges again during a long read; failing that scope for one refresh (copy kept, next refresh starts again) is the conservative choice and avoids a restart loop against an older or misbehaving server (FR-019).

## User Scenarios & Testing *(mandatory)*

Since spec 009 the phone reads only what changed, but it still asks every wallet it follows on each
refresh: a person in 10 wallets pays 11 requests to learn that nothing happened. The backend now
answers "which wallets moved" in one request, re-sends a member's row on their wallets when they
rename themselves, promises that a restore followed by an edit can travel in one upload, and no
longer expires its own paging position during a long first read. This feature takes all four,
without changing anything a person decides or sees except that partners' new names now show.

### User Story 1 - A refresh with nothing new is cheap, whatever the number of wallets (Priority: P1)

A person who belongs to many wallets opens the app or pulls to refresh the wallet list. When nothing
changed anywhere, the phone learns that with two small requests instead of one per wallet; when
some wallets changed, it reads only those.

**Why this priority**: The brief's reason for this wave and backend spec 009's open risk 3: refresh cost grows with the number of wallets, on every foreground, on poor mobile networks.

**Independent Test**: With 10 wallets fully read once, foreground the app after a minute with nothing changed: exactly two requests are made (the person's own changes and the wallet heads). Change one wallet from another session and pull to refresh: three requests, and the change appears.

**Acceptance Scenarios**:

1. **Given** every followed wallet was read to its end and nothing changed since, **When** the app refreshes everything, **Then** the phone makes one request for the person's own changes and one for the wallet heads, reads no wallet, and the screens show "Updated just now".
2. **Given** one followed wallet changed on another device, **When** the app refreshes everything, **Then** only that wallet is read, from where the phone left off, and the change appears.
3. **Given** a wallet's history was purged past the phone's place in it (the phone was away long enough), **When** the app refreshes everything, **Then** that wallet is read again from the start without first being refused, records deleted elsewhere disappear, and waiting changes are kept.
4. **Given** a wallet the phone follows is missing from the heads, **When** the app refreshes everything, **Then** the phone reads that wallet's changes as before: it is removed only if that read answers "not found" or the person's own membership ended.
5. **Given** the heads cannot be read (server error or unexpected answer), **When** the app refreshes everything, **Then** every followed wallet is read as before and the refresh still succeeds.
6. **Given** no connection, **When** the app refreshes, **Then** it shows the phone's copy with its "Updated …" time, as today.
7. **Given** the app was just updated to this version, **When** it first refreshes, **Then** nothing is downloaded again from the start because of the update; each wallet is read once from where it left off, and later quiet refreshes cost two requests.

---

### User Story 2 - Partners see a member's new name (Priority: P2)

When a member of a shared wallet changes their display name, the other members see the new name in
the wallet's members list after their next refresh.

**Why this priority**: Spec 009 risk 6: partners keep a stale name forever, which confuses "who recorded this" and ownership transfer; the backend now re-sends the row, the phone must accept it.

**Independent Test**: Two accounts share a wallet; the second renames itself; on the first phone, refresh the wallet: the members list shows the new name.

**Acceptance Scenarios**:

1. **Given** a partner renamed themselves, **When** the phone next reads that wallet, **Then** the members list and the ownership-transfer choice show the new name.
2. **Given** a member row arrives with an older version than the phone holds, **When** it is applied, **Then** it is ignored, as today.

---

### User Story 3 - "Restore with my change" leaves in one request (Priority: P3)

A person whose transaction was deleted on another device chooses "Restore with my change". The
restore and their edit travel together in the next upload, alongside other waiting changes, instead
of two requests of their own that interrupt the upload.

**Why this priority**: Rare, but it removes the last per-record two-step from the batch path and a window where the restore applied but the edit did not reach the server because the connection dropped between the two requests.

**Independent Test**: Make a transaction clash with a deletion, choose "Restore with my change" with 5 other edits waiting: one upload request carries all of them, and the transaction comes back with the person's content.

**Acceptance Scenarios**:

1. **Given** a "Restore with my change" waits with other changes, **When** the app syncs, **Then** they reach the server in one upload request and the transaction is live again with the person's content.
2. **Given** the transaction was changed again on another device after the deletion, **When** the upload is answered, **Then** the person sees the same clash choice as today, and nothing is applied to a version they did not see.
3. **Given** the restore succeeds but the edit is refused for its content, **When** the upload is answered, **Then** the change shows "not saved" with the reason, exactly as the per-record path shows it today.
4. **Given** the upload request as a whole is refused for a reason that is not about the changes, **When** the phone falls back to one request per change, **Then** the restore and the edit are sent as today (two requests) with the same outcomes.
5. **Given** wallets, categories, recurring rules and occurrences, **When** they are deleted elsewhere, **Then** they still offer only "Accept the deletion" (their deletion is final), unchanged.

---

### User Story 4 - A new phone or a long-idle phone reads a large history to its end (Priority: P3)

A person signs in on a new phone, or returns after more than 90 days, to a wallet with a long
history. The phone reads the whole wallet from the start in one refresh, instead of stopping halfway
because the server expired its own paging position.

**Why this priority**: Spec 009's backend bug (fixed by backend spec 017) made such scopes unreadable once purges run; the client's safeguard kept them failing once per refresh. The fix lands server-side; the phone must read to the end and still not loop if the rare case remains.

**Independent Test**: Against the live backend with a purged horizon and more rows below it than one page, read the wallet from the start with a small page: every page is applied and the read completes.

**Acceptance Scenarios**:

1. **Given** a wallet whose purge horizon is above some of its rows, **When** the phone reads it from the start, **Then** it pages to the end and the wallet is complete on the phone.
2. **Given** the server purges again while that read is in progress and refuses the read's own next page as expired, **When** this happens, **Then** that wallet's read ends as failed for this refresh, the phone keeps its copy, and the next refresh starts it again (no loop within one refresh).

### Edge Cases

- **Stored position missing** (a scope read before this version, or a page from an older server without a position): the wallet is read from its stored place as today; the position is stored by that read.
- **Head behind the stored position** (should never happen): the wallet is read from its stored place; the server decides (an invalid position restarts it from the start once, spec 009 FR-009).
- **Head greater and purge horizon greater than the stored position**: the purge rule wins; the wallet is read from the start.
- **Purge horizon equal to the stored position**: not expired; the head decides.
- **A wallet listed in the heads that the phone does not follow** (the person just joined it): ignored by the heads; the person's own membership on their own changes makes the phone read it from the start (spec 009 FR-010).
- **A wallet the phone follows with no stored place at all** (new to the phone, or reset by an expiry or invalid position): read from the start whatever the heads say.
- **A wallet the phone created and the server has not accepted**: not read (no feed yet), as today.
- **A wallet kept only because a change to the wallet record waits after access was lost**: not in the heads; read as today, answering "not found" until the person settles it (spec 009).
- **A wallet the upload just wrote to**: always read; the heads are not consulted for it (its head certainly moved).
- **A wallet whose own membership was missing from a complete read of the person's changes**: always read; its feed decides (spec 009 FR-011).
- **A single wallet's screen opened or pulled to refresh, and the balance carry's settling read**: the wallet is read directly; heads would cost the same one request.
- **A change committed after the heads were read**: a wallet skipped in this refresh shows it on the next refresh (the heads are as of their request, ADR-0021 rule 3); a wallet read in this refresh may already include it.
- **The heads span several pages** (a person in more than 200 wallets): every page is read before any wallet is skipped; a failure on a later page means no wallet is skipped in that refresh.
- **The connection drops while reading the heads**: the refresh stops its reads and reports offline, like any read.
- **The account changes or signs out while the heads are read**: nothing is applied or stored; the run ends.
- **A rename while the backend's queue is down**: the name reaches partners only when the backend's queue runs (backend risk); nothing for the phone to do.
- **A member row re-sent with the same version and the same name** (any re-stamp): applied; nothing visible changes.
- **The person's own membership on their own changes, same version**: applied as today (the newer or equal version wins).
- **A "Restore with my change" that is the 50th change of an upload**: the restore and its edit are never split across two requests; the pair goes in the next request.
- **A "Restore with my change" whose answer is lost** (timeout) and is sent again: it ends accepted or as a clash the person resolves; never a duplicate transaction and never an edit applied to a version the person did not see.
- **A "Restore with my change" for a transaction that another device has already restored**: the restore is refused or answered unchanged; the edit meets the newer version and the person sees today's clash choice.
- **A transaction whose destination or category was created on this phone and still waits**: the restore pair is held like any dependent change (spec 009 FR-022).
- **An archive, archive with an edit, or ownership transfer**: still per record at its place in the order (unchanged).
- **The person edits the transaction again while its restore pair is in flight**: as today, the pair's accepted answer becomes the base and the new edit is sent next as an ordinary save.
- **The background upload job**: unchanged; it reads only the wallets it wrote to, directly, without the heads.
- **Migration with stored places**: kept; positions start empty and fill on each scope's next read.

## Requirements *(mandatory)*

### Functional Requirements

**Scope positions**

- **FR-001**: For every scope, the app MUST store the integer position the server states with each feed page beside the opaque position it already stores, taken from the same page and written in the same step, so the two always describe the same place. A page that states no integer position MUST leave the scope with no integer position (never a value from an earlier page).
- **FR-002**: Whenever a scope's stored place is forgotten (expired, invalid, wallet access lost, a later feature's one-time re-read, sign-out), its integer position MUST be forgotten with it.
- **FR-003**: The integer position MUST be compared only with the same wallet's head from the heads read, never with another scope's, with a record's own change counter, or with the opaque position; it MUST never be sent to the server and MUST never be parsed out of the opaque position.

**Reading only the wallets that moved**

- **FR-004**: A refresh that reads every followed wallet (sign-in, foreground, the wallet list and other "everything" refreshes) MUST, after the person's own changes and before any wallet's changes, read the wallet heads once (every page), but only when at least one followed wallet is still to be read after the once-a-minute floor and the exclusions of FR-007. This holds for forced refreshes too: forcing lifts only the floor. The heads are read whatever the outcome of the person's own changes, except when that read found no connection (the refresh then stops its reads, spec 009).
- **FR-005**: For each followed wallet in such a refresh, with stored integer position *p*, the app MUST decide, in this order:
  1. the wallet is not in the heads → read its changes as today (from its stored place, or from the start without one);
  2. no stored place, or no integer position → read as today (from its stored place, or from the start);
  3. the purge horizon is greater than *p* → forget the wallet's stored place and read it from the start under spec 009 FR-007 (keep the copy, remove what the server no longer returns, keep waiting changes), without first asking from the expired place;
  4. the head equals *p* → do not read the wallet;
  5. otherwise (head greater than *p*, or the anomaly of a head below *p*) → read from the stored place as today.
- **FR-006**: A wallet not read under FR-005 rule 4 MUST count as a completed read that started when the heads request started: its "Updated …" time and its once-a-minute floor move as if it had been read, and its outcome is "done".
- **FR-007**: The heads MUST NOT decide for: a wallet the upload of the same run wrote to; a wallet whose own membership was missing from a complete read of the person's changes; a wallet with no stored place; a wallet created on the phone and not yet accepted (not read at all, as today). A refresh of a single wallet (its screen, pull to refresh there, the balance carry's settling read, "Use the other device's" for named wallets) MUST read that wallet directly without the heads.
- **FR-008**: The heads MUST NOT add or remove a followed wallet: a listed wallet the phone does not follow is ignored, and a followed wallet that is not listed is read as today, where only a "not found" answer or the person's ended membership removes it (spec 009 FR-011).
- **FR-009**: If the heads read finds no connection or no answer in time, the refresh MUST stop its remaining reads and report offline (spec 009 FR-026 rule for reads). If it is refused for any other reason (server error, busy, malformed request, unreadable answer, or a failure on a later page), the app MUST read every followed wallet as before (no wallet skipped), and log the refusal with its request id. A session that ended or an update that is required is handled app-wide as today.
- **FR-010**: The heads MUST be read at the largest page size the contract allows (200), following each page's continuation until there is no more, with no filter and no sort.
- **FR-011**: Each refresh's log MUST record the number of wallets in the heads, skipped, read from their stored place and read from the start because of the purge horizon; never the positions' values, heads' values or wallet names (P-09.2).

**Partners' names**

- **FR-012**: A member row from a wallet's changes whose version equals the one the phone holds MUST replace the phone's copy (its name included); an older version MUST still be ignored. The person's own membership rows from their own changes keep the same rule.
- **FR-013**: Every screen that shows a member's name (the wallet's members list, the ownership-transfer choice and confirmation) MUST show the replaced name from the phone's copy without any other action.

**"Restore with my change" in the upload**

- **FR-014**: A waiting "Restore with my change" of a transaction MUST be sent through the batch upload as two consecutive items in the same request: the restore at the change's base version *n*, then the save of the person's content at *n* + 1, addressed to the same wallet the per-record path would use. The two MUST never be split across requests; when fewer than two places remain in a request, the request MUST be sent first and the pair starts the next one. Its held-dependent rules (spec 009 FR-022) apply to the pair as one change.
- **FR-015**: The pair's answers MUST settle the change as one: both accepted → accepted with the save's record (this phone's own answer, with its balance carry); the restore accepted and the save not → settled exactly as the per-record path settles that save's answer today (clash, retry, or "not saved" with its reason); the restore not accepted → settled with the restore's answer and the save's answer ignored. An answer whose items do not match the request falls back to per-record sends (spec 009 FR-021). Each item is logged with its outcome and code as every batch item is (spec 009 FR-030). A change edited again while its pair is in flight settles as today: the accepted answer becomes the base and the newer edit is sent next.
- **FR-016**: When the batch upload is not used (the per-record fallback of spec 009 FR-021), "Restore with my change" MUST keep today's per-record restore then save. Records that have no restore in the batch contract (wallets, categories, recurring rules, occurrences) MUST keep offering only "Accept the deletion" for a deletion elsewhere.
- **FR-017**: A resent pair after a lost answer MUST end accepted or as a clash the person resolves, never as a duplicate or an edit applied to a version the person did not see; contract QA MUST record the server's real answers to a resent pair.

**Reading from the start**

- **FR-018**: A read of a scope from the start MUST page to its end at any page size against a server with the purge-horizon fix.
- **FR-019**: If such a read is still refused as expired on its own next page (the server purged again during the read), the scope's read MUST end as failed for that refresh, keep the phone's copy and its waiting changes, and start from the beginning on the next refresh; it MUST NOT restart within the same refresh (the spec 009 once-per-run safeguard stays).

**Storage, contract and safety**

- **FR-020**: The on-device database version MUST go up by one (7 → 8) with a migration that keeps all data and every stored place, starts every scope with no integer position, and the schema MUST be exported.
- **FR-021**: Sign-out MUST forget every integer position with the other sync state (spec 009 FR-028); every read and the heads read MUST be for the signed-in account only.
- **FR-022**: Every client mismatch reported by the contract check MUST be fixed; none exists at `saveme-backend@192e3ab`.
- **FR-023**: Every behaviour a person sees today (clash choices, refusals, "waiting to sync", "Updated …" meaning, refresh outcomes, sign-out) MUST be unchanged except partners' names (FR-012, FR-013).

### Key Entities *(include if feature involves data)*

- **Scope position** (extended): per scope, the opaque place, the integer position stated with the same page, and when its last read completed.
- **Wallet head**: per wallet the person belongs to, the wallet's head and its purge horizon, as of the heads request. Never stored.
- **Restore pair**: one waiting "Restore with my change" expressed as two upload items (restore, then save at the next version) settled as one change.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Makes a quiet refresh cost two requests whatever the number of wallets, lets partners see a
member's new name, sends "Restore with my change" inside the batch upload, and lets a first or
recovery read of a large wallet reach its end — the four client follow-ups of backend spec 017,
with no change to what a person decides.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Scope Head**, **Sync Position** (new in backend spec 017),
**Purge Horizon**, **Change Row**, **Sync Batch**, **scope** (user, wallet), **tombstone**,
**Wallet Member**, **Transaction**. All internal: no new user-facing copy; the UI never says
"head", "position", "cursor", "feed", "batch" or "scope".

### 3. Data model

No new syncable table. `sync_scope` gains one nullable integer column for the integer position,
written with the opaque place; the database version goes 7 → 8 with a migration that keeps all rows
and places, and the schema is exported. `wallet_member` is unchanged (its version guard becomes
"same or newer", which it already is for the wallet feed; FR-012 locks it with tests). The queue
(`pending_change`) is unchanged: a restore pair is still one queued change.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| The integer position is stored with the opaque place of the same page, and is empty when the page states none | FR-001, ADR-0021 rule 2 | puller paging tests (incremental, from the start, page without position) |
| Forgetting a place forgets its integer position | FR-002 | `SyncScopeDao` tests for clear, access lost, sign-out wipe |
| A wallet is skipped only when its head equals its stored position and its purge horizon is not above it | FR-005 | runner heads decision tests, one per rule |
| A purge horizon above the position reads the wallet from the start with the sweep, no request from the expired place | FR-005 rule 3, spec 009 FR-007 | runner + puller test asserting no cursor sent and the sweep ran |
| Heads never remove or add a wallet | FR-008, ADR-0021 | runner test with a followed wallet not listed (read, 404 removes) and a listed unfollowed one (ignored) |
| Written-to, unconfirmed, placeless wallets and single-wallet refreshes bypass the heads | FR-007 | runner tests |
| Heads offline → reads stop, outcome offline; any other heads failure → every wallet read | FR-009 | runner tests per answer |
| A skipped wallet counts as read when the heads request started ("Updated …", floor) | FR-006 | runner + `feed_read_at` test |
| Positions are never logged | FR-011, P-09.2 | log-capture test over a heads refresh |
| An equal-version member row replaces the name; an older one is ignored | FR-012 | `WalletRepository`/applier tests; captured re-stamp page |
| A restore pair is two consecutive items in one request, never split | FR-014 | engine batch packing test at the 49/50 boundary |
| The pair settles as one change per FR-015 | FR-015 | engine pair settlement tests (both applied, restore applied + save refused/clash, restore clash) |
| The per-record fallback keeps restore then save | FR-016 | engine fallback test with a restore |
| A from-the-start read refused as expired on its own page fails once, no restart in the run | FR-019 | existing `FeedPullerRecoveryTest` case kept |
| Migration keeps places, adds an empty position | FR-020 | `Migration7To8Test`, migration chain |

### 5. API

All under `/api/v1`, defined in `saveme-backend/docs/openapi/paths/sync.yaml` (v1.10).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Wallet heads (new) | `GET /sync/wallets/heads?limit=200[&cursor=]` (`listWalletHeads`) | — | 200 `{data:[{wallet_id, change_seq, purged_change_seq}], meta:{next_cursor, has_more, limit}}` (FR-004–FR-010); `400` `INVALID_CURSOR`, `INVALID_QUERY_PARAMETER`, `FILTER_FIELD_NOT_SUPPORTED`, `SORT_FIELD_NOT_SUPPORTED` → fall back (FR-009); `401` session ended; `406`, `426`, `5xx`, unreadable → fall back or app-wide handling (FR-009); offline/timeout → stop reads |
| Person's scope read | `GET /sync/user/changes` | unchanged | + `meta.sync_position` stored (FR-001) |
| Wallet read | `GET /sync/wallets/{id}/changes` | unchanged | + `meta.sync_position` stored (FR-001); `wallet_member` rows at an equal version applied (FR-012); from-the-start reads page past the purge horizon (FR-018, FR-019) |
| Upload | `POST /sync/batch` | + `{type: transaction, op: restore, id, wallet_id, data: {deleted_at: null, version: n}}` followed by `{type: transaction, op: upsert, …, data: {…, version: n + 1}}` (FR-014) | per item as spec 009; pair settlement FR-015 |
| Kept, per record | `PATCH /wallets/{id}/transactions/{tid}` then `PUT` | unchanged | used only by the per-record fallback (FR-016) |

#### Contract changes since last spec

Baseline `saveme-backend@2a3b414` (spec 010) → `192e3ab`, plus uncommitted backend edits (spec 019,
reports):

- **`GET /sync/wallets/heads`** (v1.10): FR-004–FR-011.
- **`meta.sync_position`** on both feeds (v1.10): FR-001–FR-003.
- **A renamed member's `wallet_member` row re-sent at the same `version`** (v1.10): FR-012, FR-013.
- **Restore (n) + upsert (n + 1) batch pair documented** (v1.10): FR-014–FR-017.
- **Purge-horizon paging fix** (backend spec 017 FR-022, behaviour only): FR-018, FR-019.
- **Reports section in api-conventions** (uncommitted, backend spec 019): follow-up: not in scope of this spec (C13).
- Error catalogue: no change since the baseline; the client's codes match the catalogue (no client mismatch).

### 6. Authorization and entitlements

The heads need a signed-in caller only (no verified address, no plan capability) and list only
wallets the caller actively belongs to; the phone never infers access from them (FR-008). Wallet
reads and batch items keep their authorization (spec 009 §6). No plan feature gates sync.

### 7. Events

Not applicable: the client publishes no domain events. It consumes `ApiEvent`s (session ended,
update required, email not verified) as today; the heads read raises the same ones through the
shared client.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Quiet refresh | Runner tests over fakes (decision rules, exclusions, fallbacks, offline, outcomes, stamps); puller tests for position storage; contract tests for the heads (MockEngine, OpenAPI examples, each handled code); live contract QA with request counts; captured heads and feed pages | tests; QA log; captured fixtures |
| US2 Names | Repository/applier tests; captured re-stamped page from the live backend driven through the real client and appliers | tests; captured fixtures |
| US3 Restore pair | Engine packing and settlement tests; handler item-mapping tests; live QA of the pair, its conflict and its replay; captured answers | tests; captured fixtures |
| US4 Full read | Puller tests (kept safeguard); live QA of a from-the-start read past a purged horizon at small page sizes | tests; QA log |

Owner policy: no emulator, Maestro or device runs in this feature's QA. Proof is host tests plus a
contract QA against the live local backend (curl, the app's headers and bodies) whose real answers
become test fixtures, as specs 007–010 did. Any Maestro flow written is marked "deferred: E2E on
owner's device".

### 9. Open questions

- **Replay of a restore pair after a lost answer**: the contract states replays of single writes are idempotent but does not spell out a resent restore at *n* against a record already at *n* + 2; FR-017 requires the outcome to be safe and contract QA records the real answers. Non-blocking.
- **A user-scope head** (ADR-0021 follow-up) would make a quiet refresh one request; not offered. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After every scope has been read once by this version, a refresh of everything with nothing new makes exactly 2 requests for a person in 1 to 200 wallets (3 at 201–400, one per extra 200 wallets of heads).
- **SC-002**: A refresh of everything after k wallets changed makes 2 + k requests (plus extra pages of a long wallet), and every change appears.
- **SC-003**: A wallet whose history was purged past the phone's place is re-read from the start in the same refresh with zero refused requests, no waiting change lost and other wallets untouched.
- **SC-004**: A partner's new name shows on the members list within one refresh of the wallet after the backend has re-sent it.
- **SC-005**: A "Restore with my change" with up to 48 other waiting changes reaches the server in one upload request, and in 100% of cases ends accepted, as a clash, or "not saved" with a reason.
- **SC-006**: A from-the-start read of a wallet with more rows below its purge horizon than one page completes in one refresh.
- **SC-007**: Updating to this version causes no from-the-start re-read of any scope.
- **SC-008**: Every behaviour a person sees in specs 004–010 is unchanged except partners' names: their existing host tests pass unmodified except those asserting the per-record restore inside a batch run or the reads a heads refresh now skips.

## Assumptions

- Backend B14a is built and committed (`c0406b0`, `sync.yaml` v1.10); the recorded contract is `saveme-backend@192e3ab`; its uncommitted working tree adds only reports (spec 019).
- The integer position is stored for every scope (person's and wallets') for one code path; only wallet positions are compared, because the backend offers no user-scope head.
- The heads are consulted only by refreshes that read every followed wallet; single-wallet refreshes cost one request either way.
- A skipped wallet's "Updated …" time is the heads request's start, since the head is read as committed then (ADR-0021 rule 3).
- The heads' purge horizon is used to avoid the expired request (one fewer round trip); a stored place that turns out expired anyway (a purge between the heads and the read) is handled by spec 009 FR-008 as today.
- `storeMember`'s guard already applies equal versions on the wallet feed; this feature locks that with tests and live fixtures rather than changing behaviour.
- The from-the-start 410 safeguard (spec 009 implementation note, research R-20 of its QA) stays: the backend still answers 410 when it purges again during a read, and one failed refresh is cheaper than a possible restart loop.
- Device runs are deferred to the owner's phone (owner policy); QA is host tests plus live-backend contract QA.
