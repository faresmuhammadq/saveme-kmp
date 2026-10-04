# Data Model: Sync Heads Adoption (client)

Phase 1 of [plan.md](plan.md). Only what changes; everything else is as in
`specs/009-sync-feed-batch/data-model.md`.

## 1. `sync_scope` (Room, `core.database`) — v7 → v8

| Column | Type | Change | Meaning |
|---|---|---|---|
| `scope` | TEXT PK | — | `user:<id>` / `wallet:<id>` (plus legacy keys) |
| `refreshedAt` | TEXT | — | legacy "Updated …" stamp |
| `last_pushed_at` | TEXT? | — | spec 004 "Last synced" |
| `feed_cursor` | TEXT? | — | opaque place after the last applied page; never parsed or logged |
| `feed_read_at` | TEXT? | **meaning widened** | start of the last read that reached its end, **or** of the heads read that found the wallet unchanged (R-06) |
| `feed_position` | INTEGER? | **new** | `meta.sync_position` of the same page as `feed_cursor`; null = unknown (pre-v8 place, older server, or forgotten). Never parsed from the cursor, never sent, never logged |

Rules:

- `feed_cursor` and `feed_position` are written by one statement (`storeFeedPage(scope, cursor,
  position)`) and cleared by one (`clearFeed`), FR-001/FR-002.
- A position exists only with a cursor: `feed_position IS NOT NULL ⇒ feed_cursor IS NOT NULL`.
- Any future migration that forces a re-read of some scopes MUST null `feed_cursor`, `feed_position`
  and `feed_read_at` together.
- Sign-out: `SyncScopeDao.deleteAll()` (unchanged) removes it with the row.

`MIGRATION_7_8`: `ALTER TABLE sync_scope ADD COLUMN feed_position INTEGER` (nullable, no default).
Existing rows keep their cursor and stamps (R-12). Exported as `shared/schemas/…/8.json`.

New DAO projection: `FeedPlace(cursor: String?, position: Long?)` from
`SELECT feed_cursor AS cursor, feed_position AS position FROM sync_scope WHERE scope = :scope`.

## 2. Wire types (`core.sync`, not stored)

| Type | Fields | Source |
|---|---|---|
| `FeedMetaDto` | + `syncPosition: Long? = null` (`sync_position`) | `ChangeFeedMeta` (optional, always sent from v1.10) |
| `WalletHeadDto` | `walletId: String` (`wallet_id`), `changeSeq: Long` (`change_seq`), `purgedChangeSeq: Long` (`purged_change_seq`) | `WalletHead` |
| `CollectionMetaDto` | `nextCursor: String?`, `hasMore: Boolean`, `limit: Int` | `CollectionMeta` |
| `HeadsPageDto` | `data: List<WalletHeadDto>`, `meta: CollectionMetaDto` | `listWalletHeads` 200 |

Unknown members are ignored (P-02.3); `toString()` of the DTOs prints counts only (positions are not
secrets but stay out of logs, FR-011).

## 3. Engine values (`core.sync`, in memory)

| Type | Shape | Notes |
|---|---|---|
| `HeadsRead` | `Listed(heads: Map<String, WalletHeadDto>, startedAt: Instant)` \| `Offline` \| `Failed` | one per Everything run that needs it (R-05) |
| `WalletReadPlan` | `Read` \| `ReadFromStart` \| `Skip` | output of `HeadsDecision.decide(place, head)` (R-04) |
| `WalletWant` (runner-private) | `force: Boolean`, `direct: Boolean` | `direct` = always read, heads not consulted (R-03) |
| `BatchOp` | + `Restore("restore")` | transactions only (sync.yaml `BatchItem.op`) |
| `Entry` (engine-private) | `items: List<BatchItem>` replaces `item: BatchItem?` (null/empty = per record) | a restore pair is one entry with two items (R-09) |

## 4. Restore pair (one queued change, two upload items)

`pending_change` row with `restore = 1`, `is_delete = 0`, `base_version = n` (unchanged, spec 004):

1. `{type: "transaction", op: "restore", id, wallet_id: pathWallet, data: {deleted_at: null, version: n}}`
2. `{type: "transaction", op: "upsert", id, wallet_id: pathWallet, data: TransactionWrite(…, version: n + 1)}`

Settlement (FR-015): answer = first item not `applied`, else the last item's answer → `settle()`.

## 5. `wallet_member` (unchanged schema)

Guard stays `stored.version > incoming.version → ignore`; equal versions replace the row
(`display_name` included), FR-012.
