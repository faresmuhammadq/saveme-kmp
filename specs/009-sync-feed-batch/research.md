# Research: Sync Feeds and Batch Upload (client)

Phase 0 of [plan.md](plan.md). Each item: Decision, Rationale, Alternatives. Owner away; every
decision is the conservative option and counts as an assumed default.

## R-01 Where the feed and batch code lives

**Decision**: `core.sync` gains the wire layer (`SyncApi`: two feed reads and the batch upload,
with their DTOs), a `FeedPuller`, a `SyncRunner` (one run at a time: upload, then reads) and a
`FeedApplier` interface. Features register one `FeedApplier` per (scope kind, row type) in Koin, as
they register `SyncHandler`s. `core` imports no feature (P-10.7): the puller learns the wallets to
follow and how to drop a lost one through a `WalletScopes` interface in `core.sync`, implemented by
`feature.wallets`.

**Rationale**: The engine already lives in `core.sync` and owns `pending_change` and `sync_scope`;
feeds are its other half. Registration mirrors spec 004 R-01, so each feature keeps its table logic.

**Alternatives**: Each feature reading its own rows from the feeds (would read each feed several
times and could not keep one position per scope); a feature-level "sync" package (would import
every feature).

## R-02 Applying rows: one applier per (scope, type), in feed order

**Decision**: The puller walks a page's rows in order and hands consecutive rows of one type to that
type's applier (`apply(scope, records)`); rows of an unregistered type are counted and skipped
(FR-006). Each applier writes through its repository's `storeServerCopy` with a **version guard**:
apply when the incoming `version` ≥ the stored one (equal is applied: a wallet record is re-sent
with the same version when only its balance moved, sync.yaml), skip when lower (FR-004). The
position is written to `sync_scope` after the page's rows are applied (FR-003); a crash in between
re-reads the page and the guard makes it harmless.

**Rationale**: Commit order matters only within a scope (memberships before the wallets they open);
batching consecutive rows keeps Room writes few. Writing the position last needs no cross-feature
transaction.

**Alternatives**: One Room transaction around a page and its position (needs every applier to run
inside a shared writer transaction across feature DAOs; not worth it given the guard); applying by
type in a fixed order (breaks commit order).

## R-03 Which wallets are read, and gained/lost

**Decision**: A run reads the user feed first. Its `wallet_member` rows are the person's own
memberships (ADR-0017): a live row is upserted into `wallet_member` (the same record the wallet
feed carries), an ended one calls `WalletRepository.remove(walletId)` (the spec 006 path: members,
then `WalletDeletionParticipant`s — C5 references, C6 transactions and their queued changes) and
clears that wallet scope's position. Followed wallets = wallets on the phone with `version > 0`
∪ wallet ids of live own memberships. A wallet feed `404` for a followed wallet with `version > 0`
is the same as an ended membership (FR-011). After a complete cursorless read of the user scope,
a wallet held on the phone whose own membership was not returned is **not** removed on that alone:
it stays followed for this run and its wallet feed decides (only a `404` removes it), so a gap in
the user feed can never wipe a wallet the person still has. Placeholders (`version = 0`) are never read or
removed by a read.

Exception: while `pending_change` holds a row for the wallet record itself, `accessLost` only stops
following the wallet (its scope position is cleared) and leaves its rows; the person's settling of
that row removes it (`useTheirs` applies the tombstone → `remove`; `WalletSyncHandler.onDiscarded`
removes a wallet that has no live own membership). This keeps spec 006's "Accept the deletion"
choice for a wallet deleted elsewhere while an edit waits (SC-008).

**Rationale**: Uses the record the backend designed for this (ADR-0017) and the removal path
specs 006–008 already test. A wallet's queued changes can never be accepted after access is lost
(spec 004 edge case "Access to the record is lost").

**Alternatives**: Keeping the lost wallet until its queued changes are refused (leaves data on a
phone without the right to it, backend P-08); a separate "own membership" table (duplicate of
`wallet_member`).

## R-04 Cursorless reads, 410 and 400 INVALID_CURSOR

**Decision**: A scope read without a position collects the ids seen per type and, only after the
last page (`has_more = false`), calls `sweep(scope, seen, readStartedAt)` on every applier of that
scope kind: each removes its synced rows of that scope not seen, skipping rows with a queued change
(`pending_change`), placeholders, and transaction rows still carrying a balance carry. `410
SYNC_CURSOR_EXPIRED` clears the position and does a cursorless read of that scope in the same run
(FR-008). `400 INVALID_CURSOR` does the same once per run; a second one ends that scope as
`Failed` (FR-009). A cursorless read interrupted before its last page sweeps nothing and leaves the
position empty, so the next run starts again from the beginning.

**Rationale**: The end state equals the contract's "discard the scope's local data, keep queued
writes, re-read" without an empty screen (clarification 3). Keeping rows with queued changes is
what the contract's "keep queued writes" needs for their screens.

**Alternatives**: Wiping the scope before re-reading (blank screens for the length of a full read of
a large wallet); trusting a partial read (would delete rows on later pages).

## R-05 Batch building and the order of sends

**Decision**: `drain()` takes the due rows of the signed-in account in `created_order` and walks
them:
1. Ask the row's handler for `batchItem(change)`. `null` (archive, archive + edit, restore, or a
   handler without batch support) means **per-record**: flush the batch built so far, send this row
   with `send()` as today, then continue (FR-018).
2. Check `prerequisites(change)` (R-07); a held row is skipped for this run without a try.
3. Otherwise add the item; flush at 50 items (FR-017).
Rows are marked `Sending` before their request and each answer goes through the existing
`settle()` with a synthesized `ApiResult` (R-06), so collapse, the revision guard, clash, refusal,
backoff and `last_pushed_at` are untouched.

**Rationale**: Splitting around per-record rows keeps the global creation order (FR-007 of spec
004) without teaching the batch about archive/restore, which the contract excludes (ADR-0016).

**Alternatives**: Sending all per-record rows after all batches (breaks order: an archive could
precede the wallet's create); predicting the version a restore `PATCH` returns so restore + edit fit
one batch as two items (the contract does not promise `version + 1`).

## R-06 Item answers → the spec 004 classification

**Decision**: `applied` → `ApiResult.Success(record)`; `conflict` and `rejected` →
`ApiResult.Failure(problem, ApiErrorCode.from(problem.code), problem.retryAfterSeconds)`. `settle()`
then behaves exactly as for a single write (`VERSION_CONFLICT` + `current` → Clash; Automatic →
backoff; `CATEGORY_IN_USE`, 403, 404, 422 → Refused with `refusalFields`, whose pointers are
rooted at `data` exactly like the single write's). The batch client publishes the same `ApiEvent`s
for item problems as `ApiClient` does for a response (`EMAIL_NOT_VERIFIED`,
`FEATURE_NOT_ENTITLED`) (FR-033). Request-level answers (FR-020): `Offline`/`Timeout` → every item
settles as `Offline`/`Timeout` (the run stops, as today); `UNAUTHENTICATED` → unchanged, sign-out;
`426`, `429`, `5xx` → every item gets that `Failure` (backoff with `Retry-After`, or Refused for
`CLIENT_VERSION_UNSUPPORTED`, exactly as the single write would). Fallback (FR-021): a request-level
`VALIDATION_FAILED`, `MALFORMED_JSON`, `UNSUPPORTED_MEDIA_TYPE`, `NOT_ACCEPTABLE`, `NOT_FOUND`,
`405`, an undecodable `200`, or a result list whose length, ids or types don't match → the same rows
are sent with `send()` one by one in this run; the batch is used again next run.

**Rationale**: "Per-item outcomes must map one-to-one onto settle()": settle stays the single point
of truth, and the parity test runs the same captured answers through both paths.

**Alternatives**: A second classification for batches (two places to keep in step); treating a
malformed batch answer as retryable (would loop on a server bug instead of using the safe
per-record path).

## R-07 Dependencies between waiting changes

**Decision**: `SyncHandler.prerequisites(change): List<RecordKey>` (default none). Transactions
declare their source wallet (the scope), a transfer's destination, and `category_id`. A
prerequisite blocks only while `pending_change` holds a row for it with `base_version = 0` (a
create not yet accepted). A blocked row is sent only if that prerequisite is already an item
earlier in the same request; otherwise it stays `Waiting` with no attempt counted and no
`next_attempt_at` (held). After a request, a dependent whose prerequisite item in the same request
was not `applied` has its answer ignored and returns to `Waiting` without a try (FR-022). Once the
prerequisite is accepted, the next run sends it (the engine schedules a run after any applied item).

**Rationale**: Matches the clarification ("never sent early only to be refused") and covers
cross-scope order (categories are user-scoped, transactions wallet-scoped). Discarding a refused
new wallet already discards its transactions (spec 008 `TransactionsWalletCleanup`); a discarded
new category leaves the transaction's `category_id` pointing at nothing, which the transaction's
own send then answers (C6 behaviour, unchanged).

**Alternatives**: Relying on `created_order` only (wave 0 behaviour: a refused wallet create makes
its transactions "not saved: not found"); a dependency graph table (more state than the problem
needs).

## R-08 Request timeout

**Decision**: The batch call sets a per-request timeout of 60 s through Ktor's `timeout {}` on the
already installed `HttpTimeout` plugin; the 30 s default stays for every other call. Tests keep
`requestTimeoutMillis = null`.

**Rationale**: The contract asks for ≥ 30 s; backend QA measured p95 7.6 s (12.5 s with Xdebug) for
50 items, so 30 s would leave little room on a slow uplink. No new dependency.

**Alternatives**: Smaller batches (more round trips, the thing batching removes); raising the global
timeout (changes every other call's failure time).

## R-09 Balance carry with feeds

**Decision**: The spec 008 carry stays as the bridge between an accepted send and the wallet's next
balance read. What settles it changes: `BalanceReconciler` asks the runner to read the touched
wallets (instead of `GET /wallets`) and, when a wallet scope's read that started after the carry's
confirmation completes, clears that wallet's part of each carry (`carry_source_minor` when the
wallet is the source, `carry_counter_minor` when it is the destination; `carry_confirmed_at` goes
when both are zero; settled hidden deletions are purged). A feed tombstone for a transaction that
still carries is stored as a hidden deletion (as an own answer is), not removed (FR-005).

**Rationale**: Inside one wallet feed the wallet record is re-sent at the end of the changes it
summarizes, so between pages the balance can lag; the carry prevents the jump. Settling per wallet
is needed because wallets are now read one scope at a time.

**Alternatives**: Dropping the carry and refreshing the wallet right after each upload (a window
remains between applying the answer and the read; spec 008 measured a visible double count);
clearing carries globally after a full run (a failed read of one wallet would settle another's).

## R-10 Run triggers, floors and outcomes

**Decision**: `SyncRunner.request(targets, force)` merges requests into the next run under one
`SyncLock` shared with the engine's drain and the puller (FR-026). A run: `drain()` → collect the
scopes written to → read the user scope (if targeted or any wallet gained/lost is possible) → read
the targeted wallets ∪ written wallet scopes ∪ newly gained wallets. Targets: `Everything` (sign-in,
foreground, the wallet list, "Use the other device's"), `User` (categories, account and sessions
screens: only the person's scope) and `Wallet(id)` (a wallet's transactions, detail or members).
The runner returns its own `core.sync` `ReadOutcome { Done, Offline, Failed }`; services map it to
`RefreshOutcome` (which lives in `feature.auth.domain.model`, so `core` cannot return it, P-10.7). The floors of specs 006–008 move into the runner per
target, unchanged: callers pass the same `force` they pass today (sign-in, pull to refresh and
"Use the other device's" force; screen opens and foreground returns don't), and an unforced
target reads a scope at most once a minute (FR-014). The caller awaits the
outcome of the run that served its request: `Done` when every read it targeted completed,
`Offline` when one was unreachable, `Failed` otherwise (the spec 007 rule). The background worker
runs `drain()` and then reads only the scopes it wrote to.

**Rationale**: Services keep their `refresh(...)` signatures and outcomes, so stores and screens do
not change (SC-008). One lock removes the equal-version race between a batch answer and a feed row
(R-02).

**Alternatives**: Periodic background reads (clarification 4: no); letting each service read its
own feed (one position per scope would be shared by several readers).

## R-11 Positions in `sync_scope`; "Updated …" stamps

**Decision**: `sync_scope` gains `feed_cursor TEXT NULL` and `feed_read_at TEXT NULL` on the rows
keyed `user:<userId>` and `wallet:<walletId>` (the keys `markPushed` already uses). Room 5 → 6:
two `ALTER TABLE … ADD COLUMN`, nothing else; existing rows keep their values and every position
starts empty (first run = full read per scope, FR-029). Services' "Updated …" flows read
`feed_read_at` of the scope(s) behind the screen (wallet list and categories: the user scope and,
for the list, the oldest of the followed wallets; a wallet's transactions: that wallet scope; the
sessions and profile: the user scope), falling back to the legacy stamp (`wallets`,
`transactions:<id>`, the categories' own key, `device_sessions`, `profile`) while `feed_read_at`
is null, so an upgraded phone keeps its "Updated …" until its first full read completes.

**Rationale**: No new table; sign-out already empties `sync_scope` (positions go with the account,
FR-028). The fallback keeps screens identical across the upgrade.

**Alternatives**: A new `feed_position` table (one more table for two columns, plus cleaner wiring);
copying legacy stamps in the migration (they describe whole-list reads, not feed positions).

## R-12 Kinds skipped now, adopted later

**Decision**: `recurring_rule`, `occurrence`, `budget`, `invitation` and any unknown type are
skipped and counted in the log line; the position advances. The adoption rule for C7/C8/C9 is
written in `contracts/sync-engine-v2.md`: a feature that registers an applier for a new type
clears `feed_cursor` (and `feed_read_at`) of the scope kind that carries it in its Room migration,
so its first run re-reads that scope from the start. Nothing raw is stored.

**Rationale**: Clarification 2; storing raw rows would keep unencrypted financial records with no
reader and no tests.

**Alternatives**: Storing raw JSON rows per type for later (storage and privacy cost, a second
apply path later).

## R-13 Device sessions and profile from the user feed

**Decision**: `device_session` rows: active ones upserted, any other state or a tombstone removes
the row (the screen lists active sessions only, as the replaced `filter[state]=active` read did);
the sweep removes unseen ones. `profile` rows go through `ProfileRepository.storeServerCopy` with
the version guard, and the app language follows the profile only when no profile edit waits (the
`AccountRefresher` rule). `GET /me` stays for the account, verification state and entitlements
(not syncable); the profile it carries is applied with the same guard.

**Rationale**: Behaviour of the account and sessions screens is unchanged (SC-008).

**Alternatives**: Dropping `GET /me` (the account and entitlements have no feed).

## R-14 Retired code

**Decision**: Removed from sync paths and deleted with their tests rewritten to the feed path:
`WalletApi.list/get/members`, `WalletRepository.refreshAll/refreshOne/refreshMembers`,
`CategoryApi.list` and `CategoryRepository.refreshOwn`, `CategoryReferenceApi` and
`CategoryReferenceRepository.refresh`, `TransactionApi.list` and
`TransactionRepository.refreshWallet`, `AccountApi.listSessions` and the `SessionRefresher` list
loop. Kept: every per-record write, archive, restore, transfer-ownership, `GET /me`, entitlements,
`GET /platform-categories`. The handler's `ownAnswers` set in `TransactionSyncHandler` is replaced
by the engine calling `applySentAnswer(json)` for its own answers (default `applyServerCopy`).

**Rationale**: FR-016: no whole-list read left on a sync path; dead code would drift from the
contract. `applySentAnswer` makes "this is my own answer" explicit for both paths.

**Alternatives**: Keeping the old reads as a fallback (two sources of truth for the same rows).

## R-15 Testing without devices

**Decision**: Host tests in `commonTest` (engine batching, ordering, dependencies, parity,
request-level answers, puller paging/resume/410/400/404, unknown types, runner merging and floors,
appliers per kind with fakes) and `androidHostTest` (Room: migration 5 → 6, version guard and
sweep queries, carry per wallet, sign-out wipe). A contract QA against the live local backend
(curl with the app's headers and bodies) captures real feed pages, a mixed batch answer, `410`
(made by purging aged tombstones on the dev stack per backend QA), `400 INVALID_CURSOR` and a wallet
feed `404`; these become fixtures (`CapturedSyncResponses.kt`) driving the engine, puller and
appliers. Maestro flows for the brief's checkpoint are written and marked "deferred: E2E on
owner's device"; none is run.

**Rationale**: Owner policy (no emulator/Maestro/device runs) and the specs 007/008 precedent.

**Alternatives**: Waiting for a device run (blocked by policy).
