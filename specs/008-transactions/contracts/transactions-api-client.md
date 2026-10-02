# Contract: Transactions API client (`feature.transactions.data`)

`TransactionApi(client: ApiClient)`; all calls credentialed, headers from `HttpClientFactory`
(P-02.5), results as `ApiResult`. Shapes: `saveme-backend/docs/openapi/paths/transactions.yaml`
(v1.5/v1.7). Nothing here logs bodies.

| Function | Request | Success | Failures the callers handle |
|---|---|---|---|
| `list(walletId, cursor)` | `GET wallets/{walletId}/transactions?limit=200[&cursor=…]` — no `filter`, `sort` or `search` | `Page<TransactionDto>` (transfers into the wallet included) | offline, timeout, 5xx (keep local); 404 (stop; C4 decides); 400 `INVALID_CURSOR` / `INVALID_QUERY_PARAMETER` (refresh failed); 401 |
| `put(walletId, id, request)` | `PUT wallets/{walletId}/transactions/{id}`, JSON `TransactionWriteRequest` (11 members, explicit nulls) | 201 created / 200 saved or identical replay → `TransactionDto` | 409 `VERSION_CONFLICT` (`current` live or tombstone); 422 `VALIDATION_FAILED` with `errors[]` (`REQUIRED`, `OUT_OF_RANGE`, `INVALID_ENUM_VALUE`, `CURRENCY_MISMATCH`, `IMMUTABLE_FIELD`, `UNKNOWN_FIELD`, `INVALID_TYPE`, `DATE_NOT_CALENDAR_DATE`, `INVALID_CURRENCY`); 422 `INVALID_ID`; 403 `EMAIL_NOT_VERIFIED` (also publishes `ApiEvent.EmailNotVerified`); 403 `FORBIDDEN`; 404; 5xx/429 with `Retry-After` |
| `restore(walletId, id, version)` | `PATCH wallets/{walletId}/transactions/{id}`, JSON `{"deleted_at": null, "version": n}` | 200 → `TransactionDto` (live) | 409 (+`current`); 422 `INVALID_ENUM_VALUE` at `/counter_wallet_id`; 403; 404 |
| `delete(walletId, id, version)` | `DELETE wallets/{walletId}/transactions/{id}` **with** JSON `{"version": n}` and `Content-Type: application/json` | 200 → tombstone `TransactionDto` (`deleted_at` set); also for an already deleted one | 409 (+`current`); 422 `REQUIRED` (missing body — a client bug); 403; 404 |

`GET wallets/{id}/transactions/{tid}` is not wrapped (research R-05).

## Contract tests (`TransactionApiContractTest`, Ktor `MockEngine`, P-14.3)

Fixtures from transactions.yaml examples, backend spec 012 QA evidence and, after the host QA,
captured real responses (`CapturedTransactionResponses.kt`). Assert per row: method, path,
query (`limit=200`, `cursor` echoed unchanged, no filter/sort/search), exact body members (no
`wallet_id`, `created_by`, `recurring_rule_id`, `occurrence_id`, `deleted_at` on PUT; explicit
`null`s; `deleted_at: null` present on restore; body present on DELETE), decoding with an unknown
member and with non-null recurring links, and each handled failure: 409 with live and tombstone
`current`, every 422 field code at its pointer, 403 `EMAIL_NOT_VERIFIED` (event published), 403
`FORBIDDEN`, 404, 503 with `retry_after_seconds`, offline, 401 → `SessionEnded`.

## Handler mapping (`TransactionSyncHandler`, research R-04)

| Queue row | Calls (path wallet = source, else destination when the source is not on the phone) |
|---|---|
| create (`base 0`) | `put(…, version 0)` with the payload; currency = payload's (the wallet's at save) |
| edit | `put(…, version base)`; `kind`, `counter_wallet_id`, `currency` from the stored row |
| `restore` | `restore(…, base)`; then, when the payload differs from the restored copy, `put(…, restored.version)` |
| `isDelete` | `delete(…, base)` |

Pointer map for `refusalFields`: `/amount_minor` amount · `/date` date · `/category_id` category
· `/counter_wallet_id` destination · `/currency` currency · `/kind` kind · `/description` note ·
`/memo` details · `/counterparty` person · `/cleared` cleared.
