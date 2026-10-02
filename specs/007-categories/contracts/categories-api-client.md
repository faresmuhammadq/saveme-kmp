# Contract: Categories API client (`feature.categories.data`)

`CategoryApi` and `CategoryReferenceApi` on the shared `ApiClient` (headers, correlation id,
problem decoding and `ApiEvent`s come from `HttpClientFactory`, P-02.5). Source of truth:
`saveme-backend/docs/openapi/paths/categories.yaml` (v1.4, `e358537`). Every call returns
`ApiResult<T>`; outcomes branch on `ProblemDetails.code` only (P-02.4).

| Call | Request | Success | Failures the feature handles |
|---|---|---|---|
| `list(cursor, limit = 200)` | `GET /categories?cursor=&limit=` | `Page<CategoryDto>` | offline, timeout, 5xx (keep local); 401 (session ended, core) |
| `put(id, CategoryWriteRequest)` | `PUT /categories/{id}` body `{kind, name, icon, color, version}` (nulls explicit) | 200 / 201 `CategoryDto` | 409 `VERSION_CONFLICT` with `current` (live or tombstone); 422 `VALIDATION_FAILED` (`errors[]`: `OUT_OF_RANGE` `/name`, `REQUIRED`, `INVALID_ENUM_VALUE` `/kind`, `IMMUTABLE_FIELD` `/kind`, `UNKNOWN_FIELD`); 422 `INVALID_ID`; 403 `EMAIL_NOT_VERIFIED` (core publishes `ApiEvent.EmailNotVerified`); 404 `NOT_FOUND`; 429/503 with `Retry-After` (engine backoff) |
| `archive(id, version)` | `POST /categories/{id}/archive` body `{version}` | 200 `CategoryDto` (`archived_at` set; unchanged if already archived) | 409 `VERSION_CONFLICT` (+`current`); 403; 404 |
| `delete(id, version)` | `DELETE /categories/{id}` **with JSON body** `{version}` and `Content-Type: application/json` | 200 `CategoryDto` tombstone (also for an already deleted one) | 409 `CATEGORY_IN_USE` (no `current`); 409 `VERSION_CONFLICT` (+`current`); 403; 404 |
| `listDefaults()` | `GET /platform-categories` (no query parameters) | `Page<PlatformCategoryDto>` (one page, `next_cursor` null) | offline, 5xx (keep cache) |
| `listReferences(walletId, cursor, limit = 200)` | `GET /wallets/{id}/category-references` | `Page<CategoryReferenceDto>` | 404 (remove that wallet's references); offline, 5xx (keep) |

Rules:

1. No call is retried by the API client; the sync engine owns retries for the three writes (catalogue class), and refreshes are re-triggered by their triggers.
2. `GET /categories/{id}` and `PATCH /categories/{id}` are not implemented (research R-05).
3. A list is merged only when every page succeeded (research R-08, R-09).
4. Nothing logs names or payloads; the engine logs record id, outcome and request id (P-09.2).
5. `CATEGORY_IN_USE` is a member of `ApiErrorCode` with retry class `Never` (FR-026); `VERSION_CONFLICT` without `current` stays retryable (spec 004 contract) and is distinct from it.

Contract tests (`CategoryApiContractTest`, Ktor `MockEngine`, fixtures from the YAML examples and
backend spec 011 QA evidence): method, path and exact body per call; explicit nulls; DELETE body
and content type; decoding with an unknown member; each failure row above, including a
`CATEGORY_IN_USE` problem decoding to the new code and a 409 tombstone `current`.
