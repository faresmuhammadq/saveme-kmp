# Contract: Reports API as the client uses it

**Source of truth**: `saveme-backend/docs/openapi/paths/reports.yaml` v1.11 (`d7ed413`, unchanged at
`b63e917`). Where this file and the YAML differ, the YAML wins. Client: `feature.reports.data.ReportsApi`
on the shared `ApiClient` (headers P-02.5, problem mapping by `code`, retries per catalogue).

All six operations are `GET`, read-only, not syncable, no `Idempotency-Key`, no verification gate,
no entitlement. Shared refusals: 400 `INVALID_QUERY_PARAMETER`, 401 (session ends, P-09.5), 406,
426 (update required, existing handling), 500 (retryable per catalogue). Wallet reports add 404
`NOT_FOUND` and 422 `INVALID_ID`.

## A1 `GET /reports/trend` — Budget Period lookup (release and dev; `ReportsApi.trend`)

Request: `months` (1–12), `date` (`YYYY-MM-DD`); **no** `currency`, **no** `include_planned`.
Used: `data.periods[] {label, from, to}` only (FR-019). Ignored: every money member, `meta`.

| Answer | Client |
|---|---|
| 200 | periods → `LearnedPeriods` for the signed-in account |
| 400 `INVALID_QUERY_PARAMETER` | logged as a client defect (`report.lookup invalid`), section stays "Shown when you're online" |
| 401 | the core's session end (P-09.5) |
| 426 | the core's update-required path |
| 406 | handled by the core like every read (no client branch); section stays "Shown when you're online" |
| offline / timeout / 5xx | stays "Shown when you're online"; retried on the next open or pull |

## A2 Parity probe reads (development build only)

`ReportsApi.walletSummary | walletByCategory | walletTrend | summary | byCategory | trend`.
Requests mirror the screen: wallet `GET /wallets/{id}/reports/{summary,by-category}` with `from`/`to`
(always explicit, so the server never re-derives the range) and `include_planned=true|false`;
`.../trend` with `date` = range end, `months`, `include_planned`; cross-wallet the same under
`/reports/*` plus `currency`. Responses decode to `ReportSummaryDto`, `ReportByCategoryDto`,
`ReportTrendDto`, `WalletReportMetaDto` (`{}`), `CrossWalletReportMetaDto {excluded_wallets[] {wallet_id, currency}}`;
unknown members ignored; no share member exists (a test asserts the DTOs have none).

| Answer | Client |
|---|---|
| 200 | mapped and compared with the synced-only computation (research R-12); never shown |
| 404 / 422 `INVALID_ID` | wallet gone or not yet on the server: probe skipped, logged |
| 400 | logged as a client defect |
| others | probe skipped |

## A3 Feeds (specs 009, 011; unchanged)

`GET /sync/user/changes`, wallet heads and `GET /sync/wallets/{id}/changes` through `SyncRunner.refresh`.

## Query rules the client enforces before sending

- Dates `YYYY-MM-DD` within 1900-01-01..2999-12-31; never `date` together with `from`/`to`.
- `from ≤ to`, span ≤ 366 days inclusive.
- `months` an integer 1–12 (the server refuses, never clamps; the client never sends anything else).
- `include_planned` exactly `true` or `false`.
- `currency` `^[A-Z]{3}$` from a wallet or the profile.

## Contract tests (MockEngine, examples from reports.yaml)

`ReportsApiContractTest`: for each of the six operations — path, query names and values, headers,
decoding of every example (`currentPeriod`, `creditCard`, `withPlanned`, `threeMonths`, `egp`,
`twoPeriodsPlanned`), `planned: null` vs object, `meta: {}` vs `excluded_wallets`, unknown member
ignored; each handled code (400, 401, 404, 422, 426, 500) mapped by `code`. The lookup sends neither
`currency` nor `include_planned`.
