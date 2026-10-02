# Contract: Recurring API client (`recurring.data.RecurringApi`, handlers)

Source of truth: `saveme-backend/docs/openapi/paths/recurring.yaml` v1.7 and `sync.yaml` (batch
items). Every request goes through the shared client (headers P-02.5, `X-Correlation-Id` P-13.1).

## Calls the app makes

| # | Call | When | Body | Answers handled |
|---|---|---|---|---|
| A1 | `PUT /wallets/{wallet_id}/recurring-rules/{rid}` | rule create/save (per record; or batch item `recurring_rule` / `upsert` with `wallet_id`) | `RecurringRuleWrite`: `preset, status, kind, amount_minor, amount_basis, currency, category_id, counter_wallet_id, description, memo, counterparty, auto_confirm, frequency, interval, anchor_day, start_date, end_date, occurrence_count, payout_round, payout_category_id, version` — all present, explicit `null`s | 201/200 → store copy; 409 + `current` → clash (tombstone → accept deletion only); 422 field codes → refusal with fields; 422 `INVALID_ID`; 403 `EMAIL_NOT_VERIFIED` (app-wide signal) / `FORBIDDEN`; 404 → refusal "no longer available"; 401/426/429/5xx per catalogue |
| A2 | `DELETE /wallets/{wallet_id}/recurring-rules/{rid}` | rule delete (or batch `recurring_rule` / `delete`) | `{ "version": n }` | 200 tombstone → remove rule + occurrences; 409 → clash; 403; 404 |
| A3 | `PUT /wallets/{wallet_id}/occurrences/{oid}` | confirm / skip / un-skip / edit (or batch `occurrence` / `upsert`) | `OccurrenceWrite`: `status, date, amount_minor, category_id, description, memo, counterparty, transaction_id, version` | 200 → store copy; 409 + confirmed `current` → accept silently (drop pending transaction, notice); 409 other → clash; 409 tombstone → accept deletion only; 422 (`IMMUTABLE_FIELD`, `REQUIRED`/`INVALID_ENUM_VALUE` at `/transaction_id`, `INVALID_ENUM_VALUE`/`CURRENCY_MISMATCH` at `/status`, `INVALID_ENUM_VALUE` at `/category_id`, `OUT_OF_RANGE`); 403; 404 |
| A4 | `POST /wallets/{wallet_id}/recurring-rules/{rid}/materialise` | "Check for new dates", online only | none | 200 / 404 / 409 → forced read of the wallet feed; 403 by code; offline/timeout → "needs a connection"; 5xx → "try later" + request id |
| A5 | `GET /sync/wallets/{id}/changes` | spec 009 reads | — | rows `recurring_rule`, `occurrence` now applied |

Not called: `GET …/recurring-rules`, `GET …/recurring-rules/{rid}`, `GET …/occurrences`,
`GET …/occurrences/{oid}` (the feed carries both).

## Request rules

- Members exactly as declared (P-02.2); `timezone`, `created_by`, `wallet_id`, `deleted_at`, `change_seq`, `created_at`, `updated_at` never sent (they are `UNKNOWN_FIELD`).
- `preset`, `kind`, `counter_wallet_id` sent unchanged from the stored rule on a save; `currency` = stored for a rule the server has, the wallet's present currency for a never-sent create.
- `anchor_day` null for daily/weekly; `payout_*` null unless gam'eya; exactly one of `end_date`/`occurrence_count` or neither.
- Occurrence `version` ≥ 1 always; `transaction_id` a lowercase UUIDv7 on a confirm, generated once per confirmation and reused on every resend; `null` otherwise.
- Ids lowercase hyphenated UUIDv7 (`INVALID_ID` otherwise).

## Contract tests (MockEngine, recurring.yaml examples; P-14.3)

1. A1 create rent / installment (`createRent`, `createInstallment` bodies byte-for-member equal), save with version, `endBeforeRaise` split step 1.
2. A1 each 422 code with pointer → `refusalFields` mapping; 403 FORBIDDEN; 404; 409 live and tombstone.
3. A2 body `{version}`; 200 tombstone; 409.
4. A3 `confirmWithActualAmount`, `skip`, un-skip, edit; 409 confirmed → accepted silently; 409 scheduled → clash; 422 `/status` codes; 404.
5. A4 200 then a forced feed read; offline; 403; 404 → read.
6. Batch items for both types carry `wallet_id`; `occurrence` never `delete`.
7. Decoding ignores unknown members; unknown enum strings decode.
