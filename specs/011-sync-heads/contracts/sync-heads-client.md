# Client contract: Sync heads, positions and the restore pair (v1.10)

Authoritative: `saveme-backend/docs/openapi/paths/sync.yaml` v1.10 (recorded at
`saveme-backend@192e3ab`). This file states how the client uses it; shapes are not restated beyond
what the client binds to (freeze list).

## `GET /api/v1/sync/wallets/heads` (`listWalletHeads`)

Request: `limit=200`, `cursor=<next_cursor>` from the second page on; no `filter[…]`, no `sort`.
Shared headers via `ApiClient` (P-02.5). Credentialed.

| Answer | Client handling |
|---|---|
| `200 {data: [{wallet_id, change_seq, purged_change_seq}], meta: {next_cursor, has_more, limit}}` | collect; continue while `has_more`; `Listed` after the last page |
| `200` with `data: []` | `Listed(empty)`: every followed wallet is "not listed" → read as today |
| `200` that does not decode | `Failed` → read every wallet (FR-009) |
| `400 INVALID_CURSOR` / `INVALID_QUERY_PARAMETER` / `FILTER_FIELD_NOT_SUPPORTED` / `SORT_FIELD_NOT_SUPPORTED` | `Failed` → read every wallet; logged with `request_id` (a client defect) |
| `401` | session ended (app-wide, P-09.5); `Failed`, and the run's later reads find no signed-in account and apply nothing |
| `406`, `426` | app-wide handling as today; `Failed` for the run |
| `429`, `5xx` | `Failed` → read every wallet |
| no connection / timeout | `Offline` → stop the run's remaining reads, outcome offline |

Freeze list (fields the client binds to): `wallet_id`, `change_seq`, `purged_change_seq`,
`meta.next_cursor`, `meta.has_more`.

Decision per followed, heads-eligible wallet with stored place `c` and position `p`
(`HeadsDecision`, R-04):

| Condition (first match wins) | Plan |
|---|---|
| wallet not in the heads | `Read` (from `c`, or from the start) |
| `c == null` or `p == null` | `Read` |
| `purged_change_seq > p` | `ReadFromStart` (forget `c` and `p`, then read with the sweep) |
| `change_seq == p` | `Skip` (stamp `feed_read_at` = heads start) |
| otherwise (`change_seq > p`, or `< p`) | `Read` from `c` |

Never: compare `p` with another wallet's head, the user feed, a record's `change_seq`, or a cursor;
send `p`; parse `c`.

## `ChangeFeedMeta.sync_position` (both feeds)

Decoded as `Long?`; stored with `sync_cursor` of the same page (`storeFeedPage(scope, cursor,
position)`); absent → stored as null. Freeze list: `sync_position`.

## `POST /api/v1/sync/batch` — restore pair

| # | Item | Notes |
|---|---|---|
| 1 | `{type: transaction, op: restore, id, wallet_id, data: {deleted_at: null, version: n}}` | `wallet_id` = the per-record path wallet (source unless gone from the phone, then destination) |
| 2 | `{type: transaction, op: upsert, id, wallet_id, data: {kind, date, amount_minor, currency, category_id, counter_wallet_id, description, memo, counterparty, cleared, version: n + 1}}` | kind, currency and counter wallet are the stored ones (spec 008 FR-012) |

The two items are consecutive in one request. Answers reduced to one (first not `applied`, else the
last):

| Item 1 | Item 2 | Settled as |
|---|---|---|
| `applied` | `applied` | accepted with item 2's `record` (own answer, carry) |
| `applied` | `conflict` (409 + `current`) | clash on item 2's `current` |
| `applied` | `rejected` | item 2's problem (retry or "not saved", per catalogue) |
| `conflict` / `rejected` | anything | item 1's problem; item 2 ignored |

Recorded against the live backend (qa-report.md): a restore against a record that is already live is `applied` and returns
it unchanged whatever version it names, so a replay of a pair at n + 2 answers both items `applied` with the same record
(accepted once); after a later edit item 2 is a `409` carrying the live copy, and after a re-delete both items are `409`
carrying the tombstone. A viewer's pair is `403 FORBIDDEN` on both items.

Restore is valid only for `type: transaction` (sync.yaml `BatchItem.op`); no other handler emits it.

## Behaviour notes bound by tests

- Wallet feed `wallet_member` row at the stored `version` with a new `display_name` → replaces the
  copy (FR-012).
- A read from no cursor pages to the end across a purge horizon; a `410` on that read's own next
  page (a purge during the read) ends the scope `Failed` for the run (FR-018, FR-019).

## Contract tests (P-14.3)

`SyncApiContractTest` additions on `MockEngine`: heads request path, `limit=200`, cursor on page 2,
no filter/sort; decode of the OpenAPI `twoWallets` example; `data: []`; each code in the table above
mapped to `Listed`/`Failed`/`Offline`; feed page with and without `sync_position`; a batch body with
a restore pair (item order, members, `wallet_id`, versions n and n + 1).
