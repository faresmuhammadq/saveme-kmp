# Contract: Google Play API client (what this feature sends and how it reads answers)

Source of truth: `saveme-backend/docs/openapi/paths/google-play.yaml` **v1.15** (backend spec 024,
uncommitted at `e8c308a`). Re-run `speckit-contract-check` when spec 024 is committed; any drift is
fixed here first.

## Requests

| Method | Path | Headers beyond the standard four (P-02.5) | Body |
|---|---|---|---|
| POST | `billing/google/purchases` | `Idempotency-Key` (R-3), `Content-Type: application/json` | `{"purchase_token": "<1–2048>", "product_id": "<1–150>"}` |
| POST | `billing/google/restore` | `Idempotency-Key` (random per attempt) | `{"purchases": [ {purchase_token, product_id} ×0–25, tokens distinct ]}` |
| GET | `billing/google/account-reference` (US6 only) | — | — |

Never sent: `POST webhooks/google-play`, any price, amount or currency, any acknowledgement.

## Answers

| Answer | Client reading | Action |
|---|---|---|
| 200 purchases, `purchase.state` ≠ `pending` | `Granted` | apply `entitlements` + `subscription` (R-6); notes: hold/paused/test from `purchase` |
| 200/202 purchases, `purchase.state` = `pending` | `Pending` | apply `entitlements` + `subscription` (unchanged plan); pending note; recheck ≥ 1 h with a new key (R-3) |
| 200 with `Idempotency-Replayed: true` | same as the original | nothing extra |
| 409 `PURCHASE_ALREADY_LINKED` | `AnotherAccount` | note; refused for the run |
| 422 `PURCHASE_NOT_VERIFIED` | `NotVerified` | refused for the run |
| 422 `VALIDATION_FAILED` (`/product_id`, `/purchase_token`) | `NotVerified` | refused for the run; reload catalogue once |
| 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` | `RetryLater(Retry-After)` | same key |
| 409 `IDEMPOTENCY_KEY_REUSED` | client defect | refused for the run; log code only |
| 429 `RATE_LIMITED` | `RetryLater(Retry-After)` | same key |
| 503 `SERVICE_UNAVAILABLE` | `RetryLater(Retry-After)` | same key |
| Offline / timeout | `RetryLater(null)` | same key, backoff |
| 401 | session ended (app-wide) | stop the pass |
| 426 | update required (app-wide) | stop the pass |
| other / unreadable body | `Failed(request_id)` | next trigger only; tapped flows show the generic error with the request id |
| 200 restore | per item `outcome` | apply `entitlements` + `subscription`; fold outcomes (FR-023) |
| 200 account reference | `^[0-9a-f]{64}$` | memory only (US6) |

## Contract tests (MockEngine, P-14.3) — `GooglePlayContractTest`

1. Verify request: path, method, `Idempotency-Key` present and 68 chars `gp1:<hex>`, body has exactly the two members, token not in any header or log line.
2. Each `google-play.yaml` example decodes: `active`, `ackPending`, `pending` (202), `restored`, `reference`.
3. One test per handled code: `PURCHASE_ALREADY_LINKED` 409, `PURCHASE_NOT_VERIFIED` 422, `VALIDATION_FAILED` 422 (`/product_id`), `IDEMPOTENCY_REQUEST_IN_PROGRESS` 409 + Retry-After, `IDEMPOTENCY_KEY_REUSED` 409, `RATE_LIMITED` 429 + Retry-After, `SERVICE_UNAVAILABLE` 503 + Retry-After, 401, 426.
4. Restore request with 0, 1 and 25 items; 26 purchases → two requests (25 + 1) with different keys.
5. Unknown `state` / `outcome` values and unknown members decode without error.
6. Account reference: a 63-character or upper-case value is refused as unreadable.

## Error codes added to `ApiErrorCode`

| Code | Retry class | Copy key |
|---|---|---|
| `PURCHASE_ALREADY_LINKED` | `Never` | `billing_play_another_account` |
| `PURCHASE_NOT_VERIFIED` | `Never` | `billing_play_not_verified` |

`check-backend-contract.sh` will report both as `NOT IN CATALOGUE` until spec 024's first
implementation task adds them to `error-catalog.yaml` (backend gap 5); that line is expected and
must disappear at the integration checkpoint.
