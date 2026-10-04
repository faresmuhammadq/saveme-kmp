# Contract: Budgets API as the client uses it

**Source of truth**: `saveme-backend/docs/openapi/paths/budgets.yaml` v1.8 and `paths/sync.yaml`
(v1.8 additions), recorded at `saveme-backend@64ca1ea`. Where this file and the YAML differ, the
YAML wins. Every request goes through the shared client (headers P-02.5, correlation id P-13.1,
401/426 handling, retry classes P-02.6).

## A1 `PUT /budgets/{id}` — create or save (`BudgetApi.put`; batch item `budget` upsert)

Request `BudgetWriteRequest` (exactly these members; `category_id` serialised as explicit `null`):

```json
{ "category_id": "0192f7a1-…-c5", "period_type": "monthly", "amount_minor": 600000, "currency": "EGP", "version": 0 }
```

| Answer | Client meaning |
|---|---|
| 201 / 200 `data: Budget` | applied; store the copy (`applySentAnswer`) |
| 409 `VERSION_CONFLICT` + `current` (live) | clash: "Keep mine" / "Use the other device's" |
| 409 `VERSION_CONFLICT` + `current.deleted_at` set | deleted elsewhere: "Accept the deletion" |
| 422 `VALIDATION_FAILED` `INVALID_ENUM_VALUE` at `/category_id` | refused: category can't be budgeted |
| 422 `OUT_OF_RANGE` / `AMOUNT_NOT_INTEGER` at `/amount_minor` | refused: limit |
| 422 `INVALID_CURRENCY` at `/currency` | refused: currency |
| 422 `IMMUTABLE_FIELD` (any pointer) | refused: "can't be changed after the budget is created" (should not happen: stored values are sent) |
| 422 `REQUIRED` / `UNKNOWN_FIELD` / `INVALID_TYPE` | refused generically (client defect; logged with request id) |
| 422 `INVALID_ID` | refused generically |
| 403 `EMAIL_NOT_VERIFIED` | refused: verification prompt |
| 404 `NOT_FOUND` | refused: "no longer available" (version > 0 with no record, or an id held by something else) |
| retryable (429, 5xx, offline, timeout) | engine backoff |

## A2 `DELETE /budgets/{id}` with a JSON body (`BudgetApi.delete`; batch item `budget` delete)

Request `{"version": n}`. 200 tombstone (also for an already deleted budget) → remove the row;
409 → clash as A1; 403; 404 → refused.

## A3 `POST /budgets/{id}/archive` (`BudgetApi.archive`; per record only)

Request `{"version": n}`. 200 archived copy (also already archived); 409 (stale or tombstone) as
A1; 403; 404. Never a batch item: a batch carrying op `archive` is refused whole (422) by the server.

## A4 `GET /budget-progress` (`BudgetApi.progressPage`)

Query: `limit=200`, `cursor` on later pages; **no `date`, `filter` or `sort`**. Response
`data: BudgetProgress[]`, `meta {next_cursor, has_more, limit}`.

`BudgetProgressDto`: `budget_id`, `period {start, end, days_in_period, days_elapsed?}`,
`is_current`, `limit`, `spent`, `remaining`, `projected_end?` (Money or null), `over_limit`,
`other_currencies`. Only `budget_id`, `period.*`, `limit`, `spent` and `other_currencies` are stored;
the rest is re-derived (data-model §2). There is no percentage member.

| Answer | Client meaning |
|---|---|
| 200 | store each row with the phone's synced-only figures; follow `next_cursor` to the end |
| 400 `INVALID_CURSOR` | restart the walk from the first page, once per refresh |
| 400 `INVALID_QUERY_PARAMETER` | failed (client defect) |
| 401 / 426 | shared handling |
| offline / timeout / 5xx | keep stored rows; outcome Offline / Failed |

## A5 `GET /budgets/{id}/progress` (`BudgetApi.progressOne`)

No query. 200 → store as A4; 404 → ignore (the user feed decides whether the budget exists);
offline/timeout/5xx → keep.

## A6 User feed rows (`GET /sync/user/changes`, spec 009)

Row `{type: "budget", record: Budget}` → `BudgetFeedApplier`. `Budget`: `id`, `user_id`, `version`,
`change_seq`, `created_at`, `updated_at`, `deleted_at`, `category_id`, `period_type`,
`amount_minor`, `currency`, `archived_at`; unknown members ignored.

## A7 Batch items (`POST /sync/batch`, spec 009)

`{type: "budget", op: "upsert", id, data: BudgetWrite}` and `{type: "budget", op: "delete", id, data: {version}}`;
**no `wallet_id` member** (a `wallet_id` on a budget item rejects the whole batch with 422).

## Not used

`GET /budgets`, `GET /budgets/{id}` (feed instead), `PATCH /budgets/{id}` (whole save instead), the
`date` parameter (current period only).

## Contract tests (MockEngine, examples from budgets.yaml)

Request shape for A1 (create with category, create all-expenses with explicit null, save with
stored immutable members), A2, A3 (path and body), A4 (no `date`, `limit=200`, cursor follow,
`INVALID_CURSOR` restart), A5; decoding of both progress examples (`currentPeriod`,
`pastPeriodOver`) and of an unknown member; every code in the A1 table mapped by code and pointer;
batch item JSON without `wallet_id`.
