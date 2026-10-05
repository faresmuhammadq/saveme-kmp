# Contract: Redeem API client

Source of truth: `saveme-backend/docs/openapi/paths/instapay.yaml` (v1.12) at `saveme-backend@b63e917`.
All paths under `/api/v1`. Standard client headers (P-02.5); never retried automatically (FR-009).

| Client method | Request | Session | Success | Result |
|---|---|---|---|---|
| `BillingApi.redeem(code: String, idempotencyKey: String)` (plain strings: `data` never imports `domain`, P-10.6; the service passes `attempt.value`) | `POST billing/redeem`, body `{"code":"XXXX-XXXX-XXXX-XXXX"}`, header `Idempotency-Key: <attempt>` (via `core.network.idempotencyKey`) | bearer | 200 `{data: RedeemResult, meta: {}}` → `RedeemResultDto` | `ApiResult<RedeemResultDto>` |

Never called: `POST billing/instapay/claims` (web only, P-12.4). A test asserts no client code
path builds that URL.

## Codes → `RedeemOutcome` (research R-08)

| Status / code | Outcome |
|---|---|
| 200 (first, repeat by this account, or same-key replay) | `Redeemed` (after the same-person check) or `SignedOut` |
| 403 `REDEEM_CODE_EMAIL_MISMATCH` | `OtherAddress` |
| 403 `EMAIL_NOT_VERIFIED` | `NotVerified` (the `ApiClient` also publishes `ApiEvent.EmailNotVerified`) |
| 404 `NOT_FOUND` | `NotValid` |
| 409 `REDEEM_CODE_USED` | `Used` |
| 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` | `InProgress` |
| 409 `IDEMPOTENCY_KEY_REUSED` | `KeyReused(requestId)` |
| 410 `REDEEM_CODE_EXPIRED` | `Expired` |
| 422 `VALIDATION_FAILED` | `InvalidFormat` |
| 429 `RATE_LIMITED` | `RateLimited(retryAfterSeconds)` (`Problem.retry_after_seconds`, else `Retry-After`) |
| 401 | `Failed(null)`; `ApiEvent.SessionEnded` signs out (existing) |
| 426 | `Failed(null)`; `ApiEvent.UpdateRequired` (existing) |
| other 4xx/5xx | `Failed(requestId)` |
| no response / timeout | `Offline` |

## New client error codes (`core.network.ApiErrorCode`)

| Code | Retry class | Copy key | EN | AR |
|---|---|---|---|---|
| `REDEEM_CODE_EMAIL_MISMATCH` | AfterChange | `error_redeem_code_email_mismatch` | "This code was sent to a different email address. Sign in with that address to use it." | "أُرسل هذا الرمز إلى بريد إلكتروني آخر. سجّل الدخول بذلك البريد لاستخدامه." |
| `REDEEM_CODE_USED` | Never | `error_redeem_code_used` | "This code has already been used." | "تم استخدام هذا الرمز من قبل." |
| `REDEEM_CODE_EXPIRED` | Never | `error_redeem_code_expired` | "This code has expired. Contact support for help." | "انتهت صلاحية هذا الرمز. تواصل مع الدعم للمساعدة." |
| `IDEMPOTENCY_REQUEST_IN_PROGRESS` | Automatic | (screen copy `redeem_in_progress`) | — | — |
| `IDEMPOTENCY_KEY_REUSED` | Never | (general failure) | — | — |

The `ApiErrorCodeTest` catalogue cross-check and `check-backend-contract.sh` must stay green
(every client code exists in the catalogue with the same retry class).

## Contract tests (MockEngine, `RedeemContractTest`)

Fixtures from the `instapay.yaml` examples (`redeemedGoverns`, `playStillGoverns`) plus problem
bodies per code. For `redeem`: method POST, URL suffix `billing/redeem`; `Authorization` present;
`Idempotency-Key` present and equal to the given key; body is exactly `{"code":"…"}` in the
grouped form; `Accept-Language` sent; success decodes both examples (null `current_period_end`
for a lifetime variant, unknown member ignored); each code in the table maps to its outcome;
`Retry-After` header and `retry_after_seconds` both read; offline → `Offline`.
Live replay (`RedeemLiveReplayTest`): the captured answers of quickstart §2 through the real
`ApiClient` → `RedeemService`.
