# Contract: Export & deletion API as the client binds to it

Source of truth: `saveme-backend/docs/openapi/paths/exports.yaml` and the
`/account/deletion-request` entry of `paths/identity.yaml`, `openapi.yaml` v1.13 (backend spec
022, uncommitted on `b63e917`), and `specs/022-export-data-deletion/contracts/export-deletion-api.md`
"What C15 binds to". All under `/api/v1`, bearer, standard headers (P-02.5), no `Idempotency-Key`.

## ExportApi (`feature.account.data`)

| Method | Call | Body | Returns |
|---|---|---|---|
| `requestExport(body: ExportRequestDto)` | `POST exports` | `{"scope":"account"}` or `{"scope":"wallet","wallet_id":"…"}` | `ApiResult<ExportDto>` (202) |
| `listExports(cursor: String?, limit: Int = 20)` | `GET exports?limit=20[&cursor=…]` | — | `ApiResult<Page<ExportDto>>` |
| `getExport(id: String)` | `GET exports/{id}` | — | `ApiResult<ExportDto>` |

## AccountApi additions

| Method | Call | Body | Returns |
|---|---|---|---|
| `deletionPlan()` | `GET account/deletion-request` | — | `ApiResult<DeletionPlanDto>` |
| `requestDeletion(body: DeletionRequestDto)` (changed signature) | `POST account/deletion-request` | `{"password":"…"}` or `{"password":"…","successors":[{"wallet_id":"…","member_user_id":"…"}]}` | `ApiResult<DeletionScheduleDto>` |
| `cancelDeletion()` | `DELETE account/deletion-request` | — | unchanged |

## Codes handled → outcome

| Call | Answer | Outcome |
|---|---|---|
| request | 202 | `Created` |
| request | 409 `EXPORT_IN_PROGRESS` (`current` = Export) | `InProgress(current?)` |
| request | 429 `RATE_LIMITED` (`retry_after_seconds` / `Retry-After`) | `RateLimited(seconds?)` |
| request | 404 `NOT_FOUND` | `WalletGone` |
| request | 422 `VALIDATION_FAILED`, 400/406/415/500/other | `Failed(code, requestId)` |
| list | 200 | `Page` |
| list | 400 `INVALID_CURSOR` | `CursorInvalid` → first page once |
| get / link | 200 | `Read` / `Open` (ready + url) / `NotReady` |
| get / link | 404 `NOT_FOUND` | `NotFound` |
| plan | 200 | `Loaded` |
| deletion request | 200 | `Scheduled(date, owners)` |
| deletion request | 422 `VALIDATION_FAILED` with any `errors[].pointer` starting `/successors` | `SuccessorsChanged` |
| deletion request | 401 `INVALID_CREDENTIALS`, 423 `ACCOUNT_LOCKED`, 422 at `/password` | spec 002 outcomes |
| any | 401 `UNAUTHENTICATED`, 426 | app-wide session end / update required |
| any | no answer / timeout | `Offline` |

Field codes possible under `/successors`: `INVALID_ENUM_VALUE`, `INVALID_ID`, `OUT_OF_RANGE`
(above 100). The client branches on the pointer prefix, not on the field code.

## Contract-test fixtures (MockEngine)

From `exports.yaml`: `queued` (202), `readyAndExpired` (list), `running`, `ready`, `failed` (get).
From `identity.yaml`: `activeWithChoice`, `pendingChosen` (plan); `passwordOnly`, `withChoice`
(request bodies, asserted byte-for-byte as JSON objects). Problem bodies: 409 with `current` =
`running` example; 429 with `retry_after_seconds: 3600` and `Retry-After: 3600`; 404; 422 with
`errors: [{pointer: "/successors/0/member_user_id", code: "INVALID_ID"}]`.

## Not in the API

The signed file route (`download_url`, `GET /exports/{id}/file?expires&signature`, outside
`/api/v1`) is opened by the system browser only; the app never requests it and sends it no
headers.
