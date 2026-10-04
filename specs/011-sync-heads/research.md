# Research: Sync Heads Adoption (client)

Phase 0 of [plan.md](plan.md). Owner away with delegated authority: each decision is the
conservative option, recorded as an assumed default. Code references are to the state at `main`
`8f7b308` (specs 009 and 010 merged).

## R-01 Where the integer position lives: a column, not derived

- **Decision**: a new nullable `feed_position INTEGER` column on `sync_scope` (Room 7 → 8,
  `MIGRATION_7_8` = one `ALTER TABLE … ADD COLUMN`, exported `8.json`).
- **Rationale**: the only other source is the opaque `sync_cursor`. The contract forbids parsing it
  ("never parsed, compared or constructed by a client"; ADR-0021 rule 2 keeps it opaque), and its
  encoding (`{v,f,s,p,h}` + signature) is a server detail that already changed once in spec 017
  (the `h` member). The wallet record's own `change_seq` is not a substitute: ADR-0021's rejected
  alternative "compare the head with the held wallet record's `change_seq`" — a phone holding its
  own write's answer is ahead of its feed position and would skip partners' changes in between.
  Transactions' `change_seq` columns are per record, not per scope.
- **Alternatives**: decode the cursor (contract violation, P-02.1); a separate table keyed by scope
  (two writes per page instead of one, no gain); keep positions in memory (lost on process death,
  so every cold start would read every wallet).

## R-02 Writing the position: same statement as the cursor

- **Decision**: `SyncScopeDao.storeFeedPage(scope, cursor, position: Long?)` sets `feed_cursor` and
  `feed_position` in one upsert; `clearFeed` nulls both (and `feed_read_at`, as today). A page whose
  `meta.sync_position` is absent stores `NULL`. `FeedMetaDto` gains `syncPosition: Long? = null`.
- **Rationale**: FR-001/FR-002. If the cursor moved without the position, a stale position could
  equal the head while the cursor is ahead — harmless — but the reverse (position ahead of cursor)
  could skip unread rows; one statement makes either impossible. Every existing caller of
  `clearFeed` (410, invalid cursor, `accessLost`, `WalletSyncHandler.reconsiderAfterDiscard`) gets the
  rule for free. Migrations that clear positions with raw SQL (`MIGRATION_6_7`) are historical and
  run before `feed_position` exists; a later feature's one-time re-read must null both (noted in
  data-model §1).
- **Alternatives**: a second `storeFeedPosition` call (two writes, a crash window).

## R-03 Which refreshes consult the heads

- **Decision**: only runs that include a `ReadTarget.Everything` request (sign-in, foreground,
  wallet list refresh, `requestWalletRead()` with no ids), forced or not. `ReadTarget.Wallet` and
  `ReadTarget.User` never read the heads. Within an Everything run, the heads decide only for wallets
  that came from `walletScopes.followed()`; wallets the upload wrote to (`report.written`), wallets
  returned by `walletScopes.unconfirmed(…)`, wallets also requested by a `ReadTarget.Wallet` in the
  same merged run, and wallets with no stored place are always read.
- **Rationale**: FR-004, FR-007. A single-wallet read costs one request either way; the balance
  carry's settling read (`BalanceReconciler.settle`) and "Use the other device's" for named wallets
  must read the wallet whatever the heads say. Written-to wallets certainly moved. Unconfirmed
  wallets are being checked for a `404`, which the heads cannot give.
- **Implementation shape**: the runner's `walletForce: Map<String, Boolean>` becomes
  `Map<String, WalletWant(force, direct)>`; `direct = true` for written, unconfirmed and explicit
  `Wallet` targets. After floors and the "not accepted" exclusion, the wallets with `direct = false`
  and a stored position are the heads candidates; if there is at least one, the heads are read once.

## R-04 The decision table as a pure function

- **Decision**: `HeadsDecision.decide(place: FeedPlace?, head: WalletHead?): WalletReadPlan` in
  `core.sync`, returning `Read` (from the stored place, or from the start without one),
  `ReadFromStart` (forget the place first) or `Skip`. Order (FR-005): not listed → `Read`; no place or
  no position → `Read`; `purgedChangeSeq > position` → `ReadFromStart`; `changeSeq == position` →
  `Skip`; otherwise → `Read`.
- **Rationale**: one table-driven unit test per row (spec §4) without a runner; the runner only
  executes plans.
- **Edge**: `purgedChangeSeq == position` is not expired (the contract says "greater than"); the
  head decides. `changeSeq < position` (impossible per ADR-0021 rule 3) → `Read`, and the server's
  `400 INVALID_CURSOR` path (spec 009 FR-009) handles a truly bad place.

## R-05 Reading the heads: in the puller, under the sync lock

- **Decision**: `FeedPuller.heads(run: ReadRun): HeadsRead` pages `SyncApi.walletHeads(cursor,
  limit = 200)` until `has_more` is false, under `SyncLock`, checking `stillSignedIn(run)` after each
  page. `HeadsRead` = `Listed(heads: Map<walletId, WalletHead>, startedAt)` | `Offline` | `Failed`.
  `Offline`/`Timeout` on any page → `Offline`; any `Failure` (incl. unreadable 200, `400`, `5xx`,
  `429`) or a page that does not decode → `Failed` (logged with request id and code). A
  `401`/`426` still raises the app-wide events through `ApiClient`.
- **Rationale**: the puller already owns the API, the lock, the account check and the logging rules;
  the runner stays a scheduler. All-or-nothing paging (FR-009): a partial list could leave a moved
  wallet unlisted, which would merely be read (rule 1) — safe — but "unlisted" is not "unchanged",
  so the conservative rule is to skip nothing unless every page arrived.
- **Alternatives**: a separate `HeadsReader` class (one more Koin binding for 30 lines); reading
  heads in parallel with the user feed (breaks the "user scope first" order and the offline stop
  rule).

## R-06 What a skipped wallet records

- **Decision**: `FeedPuller.markUnchanged(scope, startedAt)` writes `feed_read_at = startedAt` (the
  heads request's start), leaving cursor and position as they are; the runner records `lastRead`
  for the floor and reports `Done`.
- **Rationale**: FR-006. The wallet list's "Updated …" is the oldest `feed_read_at` across scopes
  (`WalletDao.observeOldestFeedRead`); without this a quiet refresh would leave it stuck at the last
  real read. A head equal to the stored position means the phone's copy *is* the server's state as
  of the heads read (ADR-0021 rule 3), so stamping it is truthful. The balance carry is unaffected:
  it settles only through `ReadTarget.Wallet` reads (R-03), and a carried change's wallet was written
  to, so its head moved.

## R-07 Purge horizon above the position: proactive restart

- **Decision**: `ReadFromStart` → `scopes.clearFeed(key)` then the ordinary `puller.read`, which,
  finding no place, runs the spec 009 from-the-start read with its sweep.
- **Rationale**: Clarification 2; contract table row 3. Saves the `410` round trip, and reuses the
  only from-the-start path (copy kept, sweep after the last page, waiting changes and carried rows
  never swept). A purge landing between the heads and the read still yields a `410`, handled by the
  unchanged restart path.

## R-08 Equal-version member rows

- **Decision**: no behaviour change. `WalletRepository.storeMember` already skips only when
  `stored.version > dto.version`, so an equal version (the re-stamp) replaces the row including
  `display_name`. Tests lock it (repository and applier), and a live re-stamped page becomes a
  captured fixture driven through the real client and appliers.
- **Rationale**: FR-012, FR-013. The members list (`WalletDetailScreen`) and the ownership transfer
  choice read `wallet_member` through Room flows, so the new name shows with no other action.
- **Check**: no other table copies member names (grep: only `wallets` UI reads `displayName`).

## R-09 The restore pair in the batch

- **Decision**:
  - `BatchOp` gains `Restore("restore")`.
  - `SyncHandler` gains `suspend fun batchItems(change): List<BatchItem>?` with the default
    `batchItem(change)?.let(::listOf)`, so every other handler is untouched.
  - `TransactionSyncHandler.batchItems` for `change.restore && !change.isDelete`: `[restore item
    (data = TransactionRestoreRequest(baseVersion) = {"deleted_at": null, "version": n}), upsert item
    (data = the same TransactionWriteRequest the per-record PUT would send, built from the stored kind,
    currency and counter wallet, with version n + 1)]`, both addressed to `pathWallet(…)`. If the
    change has no decodable content, only the restore item (the per-record path also stops after the
    PATCH then). `batchItem` keeps returning `null` for a restore so the per-record fallback is
    unchanged.
  - Engine: `Entry.items: List<BatchItem>`; packing flushes first when `entries' item count +
    entry.items.size > 50`; `answerMatches` checks the flattened list; `settleItems` hands each entry
    its slice and reduces it to one answer: the first item not `applied`, else the last item's answer.
    The entry's key (for held dependents) is its first item's key.
- **Rationale**: FR-014, FR-015, Clarification 3. The reduction gives exactly: both applied → the
  upsert's record via `applySentAnswer` (own answer, carry); restore applied + upsert refused or
  clash → the upsert's answer (as per-record `restoreThenSave` returns the PUT's answer); restore
  not applied → the restore's answer. Per-record `send` (fallback) is untouched.
- **Why not skip the upsert when the content equals the tombstone** (as `restoreThenSave` does with
  `restored.matches(payload)`): the batch must be built before the answer exists. Sending an equal
  upsert is safe — the server either answers it unchanged or bumps the version; either way the
  answer is applied as the own copy. Contract QA records which (T-QA).
- **Alternatives**: a combined batch op (rejected by the backend, ADR-0021 rule 6); two queued
  changes (would let the queue collapse/reorder them and break "one change per record", spec 004).

## R-10 Replay of a lost restore pair

- **Decision**: no special handling; the engine's ordinary settle applies to whatever the replay
  answers. Contract QA sends a pair, then the identical pair again, and records: the restore's answer
  against a record at n + 2 (expected `applied` unchanged or `409` with `current`), and the upsert's
  answer at n + 1 (expected `applied` with nothing bumped, per the batch idempotency rule). Both
  outcomes are settled safely by R-09's reduction: `409` with a `current` equal to the person's
  content becomes an ordinary clash the person resolves; an `applied` replay is accepted.
- **Rationale**: FR-017; the per-record PATCH has the same replay exposure today. Spec §9 records it
  as a non-blocking open question with QA as the proof.

**Recorded (T030)**: the replay answered both items `applied` with the unchanged record (not `409`); the engine's ordinary
settle accepts it once. See qa-report.md.

## R-11 The from-the-start 410 safeguard

- **Decision**: keep `FeedPuller.failure`'s branch `SYNC_CURSOR_EXPIRED && hadPosition && fromStart
  → Failed` unchanged; update its comment (backend spec 017 fixed the systematic case; the branch now
  covers a purge during the read, ADR-0021 / spec 017 QA "mid-read-then-repurged cursors still 410").
  The existing `FeedPullerRecoveryTest` case stays as the proof.
- **Rationale**: Clarification 5, FR-019. The branch is still reachable; removing it would turn that
  rare case into an immediate restart (a second full read in the same run), and against a pre-fix
  server into the wasteful pattern spec 009 QA found. The "once per run" `restarts` bound and the
  `invalidCursors` set are unchanged.
- **Proof of the fix (FR-018)**: contract QA reads a wallet with a purged horizon from no cursor at
  limits 1, 2 and 5 to the end; the pages become a captured fixture replayed through the real
  `HttpSyncApi` and puller (from-the-start read completes, sweep runs).

## R-12 Migration and first run

- **Decision**: `MIGRATION_7_8` adds the column only; cursors and read stamps are kept.
- **Rationale**: Clarification 4, SC-007. The first Everything run after the update finds every
  wallet with a place but no position → rule 2 → reads from its place (an empty page when quiet) and
  stores the position; from the next run quiet refreshes cost two requests.

## R-13 Logging

- **Decision**: one line per heads read (`heads: N wallets in P pages`), one line per Everything run
  (`wallets: N followed, S unchanged, R read, F from the start (purge horizon), D direct`), and the
  existing per-scope lines. No position, head, horizon, cursor or wallet name values (FR-011,
  P-09.2); wallet ids are already logged by the engine and are not logged here.

## R-14 Test layers

- Pure: `HeadsDecisionTest` (every row and edge of R-04).
- `commonTest` with fakes: `FeedPullerHeadsTest` (paging, offline, failure mid-paging, account
  change), `FeedPullerPositionTest` (position stored with the cursor per page, absent → null,
  cleared on 410/invalid), `SyncRunnerHeadsTest` (two requests when quiet, k + 2 when k moved,
  exclusions, offline/failed fallbacks, stamps and floors, outcomes), `SyncEngineRestorePairTest`
  (packing at 49/50, every settlement branch, held dependents, fallback), `TransactionSyncHandlerTest`
  (pair mapping, path wallet, missing content), `WalletMemberRestampTest` (equal version replaces the
  name, older ignored, own-membership path), `SyncApiContractTest` (heads request shape, OpenAPI
  example, each handled code; `sync_position` decoded and optional).
- `androidHostTest`: `Migration7To8Test`, the migration chain tests extended with `MIGRATION_7_8`,
  `SyncScopeDaoTest` (store/clear pairs), `SignOutWipeTest`.
- Captured live answers: heads pages, feed pages with `sync_position`, a re-stamped member page, a
  purged from-the-start read at small limits, restore pair (applied, conflict, replay) —
  `CapturedSyncResponses.kt` additions driven by `SyncCapturedResponsesTest`.
