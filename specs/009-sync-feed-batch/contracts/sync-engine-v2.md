# Contract: Sync engine, wave 2 (Kotlin API inside `:shared`)

Extends spec 004 [contracts/sync-engine.md](../../004-sync-engine/contracts/sync-engine.md). Every
member listed there keeps its behaviour unless changed here. Features depend on `core.sync` only
(P-10).

## SyncHandler (additions, all with defaults so existing handlers compile)

| Member | Behaviour |
|---|---|
| `suspend fun batchItem(change: PendingChange): BatchItem?` (default `null`) | The change as exactly its per-record write: `type`, `op`, `id`, `walletId` (transactions only), `data` = the body `send()` would send. `null` = per-record only (archive, archive + edit, restore, unsupported) |
| `suspend fun prerequisites(change: PendingChange): List<RecordKey>` (default empty) | Records that must exist on the server first (transactions: source wallet, destination wallet, category) |
| `suspend fun applySentAnswer(json: JsonElement)` (default `applyServerCopy(json)`) | The server's answer to this phone's own write (per-record or item); transactions add the balance carry here (replaces the `ownAnswers` set) |

Per handler:

| Handler | Upsert item | Delete item | Per-record only |
|---|---|---|---|
| `wallet` | `{type: wallet, op: upsert, id, data: WalletWrite}` when `!draft.archive` | `{op: delete, data: {version}}` | any change with `archive = true` |
| `category` | `{type: category, op: upsert, id, data: CategoryWrite}` (stored kind) when `!payload.archive` | `{op: delete, data: {version}}` | any change with `archive = true` |
| `transaction` | `{type: transaction, op: upsert, id, wallet_id: path wallet, data: TransactionWrite}` (stored kind, destination, and currency for an accepted record) | `{op: delete, wallet_id, data: {version}}` | `restore = true` (PATCH then PUT) |
| `profile` | `{type: profile, op: upsert, id: profile id, data: ProfileWrite}` | — | — |

## SyncEngine (changed)

| Member | Behaviour |
|---|---|
| `suspend fun drain(): DrainReport` | Under `SyncLock`: load account, reset stale `Sending`, walk due rows oldest first (R-05): hold rows with an unaccepted prerequisite not earlier in the current request (R-07); collect batchable items up to 50; a per-record row flushes the batch then goes alone; settle every answer with `settle()` (R-06); request-level fallback to per-record (FR-021). Returns the scopes written to and whether anything was applied. Reschedules for the earliest due row as today |
| everything else | unchanged (`enqueue`, `enqueueDelete`, `keepMine`, `useTheirs`, `discard`, `syncNow`, `observe*`, `status`) |

`settle()` is unchanged; the only new caller-side rules are: own answers go through
`applySentAnswer`, and a dependent whose prerequisite item in the same request was not applied is
returned to `Waiting` without calling `settle()`.

## FeedApplier (new, one per scope kind × row type, registered in Koin)

| Member | Behaviour |
|---|---|
| `val scopeKind: ScopeKind` | `User` or `Wallet` |
| `val rowType: String` | the feed `type` it consumes |
| `suspend fun apply(scope: SyncScope, records: List<JsonElement>)` | Decode (unknown members ignored); per record apply the version guard and deletion rule of data-model §1; never touch `pending_change` |
| `suspend fun sweep(scope: SyncScope, seenIds: Set<String>)` | After a complete cursorless read: remove this type's synced rows of the scope not in `seenIds`, except rows with a queued change, placeholders and carried transactions |

Registered: `account` → user `profile`, user `device_session`; `categories` → user `category`,
wallet `category_reference`; `wallets` → user `wallet_member` (own memberships), wallet `wallet`,
wallet `wallet_member`; `transactions` → wallet `transaction`.

**Adoption rule for later kinds** (C7 `recurring_rule`/`occurrence`, C8 `budget`, C9 `invitation`):
the feature registering a new applier MUST, in the same release's Room migration, set
`feed_cursor = NULL, feed_read_at = NULL` on every `sync_scope` row of the scope kind that carries
the kind, so its first run re-reads those scopes from the start.

## WalletScopes (new, implemented by `feature.wallets`)

| Member | Behaviour |
|---|---|
| `suspend fun followed(): List<String>` | wallets with `version > 0` ∪ wallet ids of live own memberships |
| `suspend fun isAccepted(walletId: String): Boolean` | the wallet row exists with `version > 0`, or a live own membership exists |
| `suspend fun unconfirmed(seenWalletIds: Set<String>): List<String>` | after a complete cursorless user read: held wallets (`version > 0`) whose own membership was not seen; the puller reads their feeds in the same run (FR-011) |
| `suspend fun accessLost(walletId: String)` | `WalletRepository.remove(walletId)` (participants included) unless it is a placeholder or the wallet record itself has a queued change (then only stop following it; settling that change removes it, research R-03); clear that scope's `sync_scope` row |

## FeedPuller (new)

| Member | Behaviour |
|---|---|
| `suspend fun read(scope: SyncScope): ScopeRead` | Pages of 200 from the stored `feed_cursor` (none = from the start), rows to appliers in order, unknown types skipped and counted, `feed_cursor` stored after each page, `feed_read_at` = read start on the last page, sweep when the read began without a cursor; 410/400/404 per data-model §4 |

## SyncRunner (new)

| Member | Behaviour |
|---|---|
| `suspend fun refresh(target: ReadTarget, force: Boolean): ReadOutcome` | `ReadTarget` = `Everything` / `User` / `Wallet(id)`. Merged into the next run; floor 60 s per scope unless `force`; returns `Done` / `Offline` / `Failed` for the reads that target needed (services map it to `RefreshOutcome`) |
| `suspend fun runBackground()` | Worker entry: `drain()`, then read only the written scopes |
| `fun requestUpload()` | Schedule a run with no reads beyond the written scopes (feature saves call this instead of `engine.drain()`) |

Run order: `drain()` → the user scope (when an `Everything` or `User` target is in the run, or the
user scope was written to) → every followed wallet for `Everything`, else the targeted ∪ written wallet
scopes, plus wallets newly gained from the user scope read, one at a time.

## SyncScheduler

Unchanged API. `SyncDrainWorker` calls `SyncRunner.runBackground()` instead of `engine.drain()`.
