# Data Model: Sync Feeds and Batch Upload (client)

## 1. Tables (Room, schema v6)

### `sync_scope` (changed)

| Column | Type | Rules |
|---|---|---|
| `scope` | TEXT, PK | unchanged; feed rows use `user:<userId>` and `wallet:<walletId>` (`SyncScope.key`) |
| `refreshedAt` | TEXT | unchanged (legacy whole-list stamps, read only as a fallback, R-11) |
| `last_pushed_at` | TEXT or NULL | unchanged (spec 004) |
| `feed_cursor` | TEXT or NULL | **new**. The server's `meta.sync_cursor` after the last applied page; opaque, never parsed, compared or logged (FR-003). NULL = read from the start |
| `feed_read_at` | TEXT (UTC instant, fixed width) or NULL | **new**. When the last read of this scope that reached `has_more = false` **started** (R-09: carries confirmed at or before it settle) |

Migration 5 → 6: `ALTER TABLE sync_scope ADD COLUMN feed_cursor TEXT` and `ADD COLUMN feed_read_at
TEXT`. No other table changes; no data moves. Export `shared/schemas/…/6.json`. Sign-out already
deletes every `sync_scope` row (`DatabaseCleaner`).

### Unchanged tables, new rules

| Table | Written by (feed) | Version guard | Deletion row | Sweep after a cursorless read |
|---|---|---|---|---|
| `profile` | user `profile` | apply if `version ≥ stored` | never deleted | never |
| `device_session` | user `device_session` | apply if `version ≥ stored` | removed; non-`active` state also removed | unseen rows removed |
| `category` | user `category` | apply if `version ≥ stored` | removed unless a queued change exists | unseen synced rows (`version > 0`) without a queued change |
| `wallet_member` | user `wallet_member` (own) and wallet `wallet_member` | apply if `version ≥ stored` | own: wallet removal (R-03); other: row removed | wallet scope: unseen rows of that wallet; user scope: a wallet whose own membership is unseen is re-checked by reading its feed (only a `404` removes it, FR-011) |
| `wallet` | wallet `wallet` | apply if `version ≥ stored` (equal applied: balance moved) | wallet removal unless a queued change on the wallet | wallet record unseen → wallet removal (as a 404) |
| `category_reference` | wallet `category_reference` | apply if `version ≥ stored` | removed | unseen rows of that wallet |
| `transactions` | wallet `transaction` (both wallets of a transfer) | apply if `version ≥ stored` | removed, unless a queued change (kept) or an unsettled carry (kept hidden, `deleted_at` set) | unseen synced rows touching the wallet, without a queued change or carry |
| `pending_change` | — (feeds never write it) | — | — | — |

Placeholders (`version = 0`, created on this phone and not yet accepted) are never removed by a
sweep, a deletion row or a wallet `404`. A feed row with the same id can only exist once this
phone's create was accepted; it then replaces the placeholder like any newer version.

## 2. Wire types (`core.sync`, kotlinx.serialization, unknown members ignored)

| Type | Fields |
|---|---|
| `ChangeRowDto` | `type: String`, `record: JsonElement` |
| `FeedMetaDto` | `next_cursor: String?`, `has_more: Boolean`, `limit: Int`, `sync_cursor: String` |
| `FeedPageDto` | `data: List<ChangeRowDto>`, `meta: FeedMetaDto` |
| `BatchItemDto` | `type: String`, `op: String` (`upsert` / `delete` / `restore`), `id: String`, `wallet_id: String?` (omitted when null, `explicitNulls = false` for this DTO only), `data: JsonElement` |
| `BatchRequestDto` | `items: List<BatchItemDto>` (1–50) |
| `BatchItemResultDto` | `type`, `id`, `outcome` (`applied` / `conflict` / `rejected`), `status: Int`, `record: JsonElement?`, `problem: ProblemDetails?` |

## 3. Domain types (`core.sync`)

| Type | Fields / members |
|---|---|
| `RecordKey` | `recordType: String`, `recordId: String` |
| `BatchItem` | `type`, `op: BatchOp`, `id`, `walletId: String?`, `data: JsonElement` (built by a handler) |
| `BatchOp` | `Upsert`, `Delete` (no `Restore` from this client: restore stays per-record, R-05) |
| `FeedApplier` | see contracts/sync-engine-v2.md |
| `WalletScopes` | `followed(): List<String>`, `isAccepted(walletId): Boolean`, `accessLost(walletId)` |
| `ReadTarget` | `Everything`, `User`, `Wallet(id)` |
| `ReadOutcome` | `Done`, `Offline`, `Failed` (services map it to `RefreshOutcome`) |
| `ScopeRead` | per scope: `Done(startedAt)`, `Offline`, `Failed` |
| `RunReport` | `written: Set<SyncScope>`, `reads: Map<SyncScope, ScopeRead>`, `skippedTypes: Map<String, Int>` |
| `SyncStatus` | unchanged (spec 004) |

## 4. States

### A waiting change (spec 004 §2, extended)

```text
Waiting ──(held: a prerequisite create not accepted and not earlier in this request)──► Waiting (no try counted)
Waiting ──batchItem != null──► Sending (as an item) ──item answer──► settle() as spec 004
Waiting ──batchItem == null──► Sending (per record) ──answer──► settle() as spec 004
Sending (item) ──request-level Offline/Timeout/401/426/429/5xx──► settle() with that answer for every item
Sending (item) ──request refused as a shape/endpoint problem──► per-record send in the same run
Sending (item, dependent) ──prerequisite item in the same request not applied──► Waiting (answer ignored, no try)
```

### A scope's read

```text
(no cursor) ──page…──► applying (seen ids collected) ──has_more=false──► sweep ─► cursor + feed_read_at stored ─► Done
(cursor)    ──page…──► applying ──has_more=false──► cursor + feed_read_at stored ─► Done
any page ──offline/timeout──► stop, cursor = last applied page ─► Offline
410 ──► cursor := NULL, read from the start (same run)
400 INVALID_CURSOR ──first in run──► cursor := NULL, read from the start; ──second──► Failed
wallet 404 (accepted wallet) ──► WalletScopes.accessLost(id) (wallet, data, queue rows, position removed)
```
