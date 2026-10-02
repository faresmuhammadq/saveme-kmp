# Contract: Sync API client (wire use)

Source of truth: `saveme-backend/docs/openapi/paths/sync.yaml` (v1.6+, `saveme-backend@6b8e290`)
and `specs/013-sync-protocol/contracts/sync-api.md`. This page lists only what the client sends and
binds to. Every request carries `X-Client-Platform`, `X-Client-Version`, `Accept-Language`,
`X-Correlation-Id` and the bearer credential (P-02.5).

## `GET /api/v1/sync/user/changes` and `GET /api/v1/sync/wallets/{id}/changes`

| Sent | Value |
|---|---|
| `cursor` | the stored `feed_cursor`, omitted when null (never `cursor=`) |
| `limit` | `200` |
| anything else | nothing (no `filter[…]`, `sort`, `since`) |

| Answer | Client action |
|---|---|
| `200` | apply `data` in order; store `meta.sync_cursor`; continue while `meta.has_more` (sending `meta.next_cursor`, equal to `sync_cursor` then) |
| `400 INVALID_CURSOR` | clear the position, read from the start (once per run), FR-009 |
| `400 INVALID_QUERY_PARAMETER` / `FILTER_FIELD_NOT_SUPPORTED` / `SORT_FIELD_NOT_SUPPORTED` | client defect: scope `Failed`, logged with request id |
| `404 NOT_FOUND` (wallet feed) | access lost for an accepted wallet (FR-011) |
| `410 SYNC_CURSOR_EXPIRED` | clear the position, cursorless read with sweep (FR-008) |
| `422 INVALID_ID` (wallet feed) | client defect: scope `Failed`, logged |
| `401` | session ended (existing `ApiEvent`), run stops |
| `426` | update required (existing `ApiEvent`), run stops |
| `5xx`, `429`, offline, timeout | scope `Offline` (no answer) or `Failed`; position stays at the last applied page |

Row kinds consumed: user — `profile`, `device_session`, `category`, `wallet_member`; wallet —
`wallet`, `wallet_member`, `category_reference`, `transaction`. Any other `type` is skipped.
`record` decodes with the feature's existing DTO (`ProfileDto`, `DeviceSessionDto`, `CategoryDto`,
`WalletMemberDto`, `WalletDto`, `CategoryReferenceDto`, `TransactionDto`); unknown members ignored.

## `POST /api/v1/sync/batch`

Request `{"items": [ … ]}`, 1–50 items, `Content-Type: application/json`, no `Idempotency-Key`,
request timeout 60 s.

| Item | `type` | `op` | `id` | `wallet_id` | `data` |
|---|---|---|---|---|---|
| wallet create/save | `wallet` | `upsert` | wallet id | absent | `WalletWrite` (`type, name, currency, opening_balance_minor, icon, color, version`) |
| wallet delete | `wallet` | `delete` | wallet id | absent | `{version}` |
| category create/save | `category` | `upsert` | category id | absent | `CategoryWrite` (`kind` (stored), `name, icon, color, version`) |
| category delete | `category` | `delete` | category id | absent | `{version}` |
| transaction create/save | `transaction` | `upsert` | transaction id | source (destination when the source is gone) | `TransactionWrite` with explicit nulls |
| transaction delete | `transaction` | `delete` | transaction id | as above | `{version}` |
| profile save | `profile` | `upsert` | the person's profile id | absent | `ProfileWrite` |

Not sent as items: `restore` (sent per record as `PATCH` then `PUT`), archives, ownership transfer,
`recurring_rule`, `occurrence`, `budget`.

| Answer | Client action |
|---|---|
| `200` with `data[i]` per item, same order, same `id` and `type` | `applied` → `Success(record)`; `conflict` → `Failure(VERSION_CONFLICT, problem with current)`; `rejected` → `Failure(problem.code)`; each through `settle()` (R-06); item problems raise `EMAIL_NOT_VERIFIED` / `FEATURE_NOT_ENTITLED` events |
| `200` whose `data` doesn't match the items (count, order, id, type) or doesn't decode | per-record fallback for those rows (FR-021) |
| `401` | every item unchanged, session ended |
| `426`, `429`, `5xx` | every item gets that answer through `settle()` (Retry-After honoured) |
| `422 VALIDATION_FAILED`, `400 MALFORMED_JSON`, `415`, `406`, `404`, `405` | per-record fallback this run (FR-021); logged with request id |
| offline / timeout | every item `Offline` / `Timeout`; run stops |

## Contract tests (mock HTTP engine, P-14.3)

- Feed: request shape (path, `limit=200`, `cursor` only when stored, headers); decoding the OpenAPI
  examples `userFeedPage` and `walletFeedPage`; `has_more` paging; empty page keeps the position;
  unknown `type` (`budget`, `invitation`) skipped; `400 INVALID_CURSOR`, `410`, `404`, `422`.
- Batch: request body for every item row above equals the per-record request body for the same
  change (byte-equal JSON of `data`); no `wallet_id` on non-transaction items; profile id; the
  OpenAPI examples `mixedResults` and `validationRefusal` decode and settle as their single-write
  equivalents; every request-level answer in the table.
