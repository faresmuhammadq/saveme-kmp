# Data Model: Budgets (client)

**Feature**: [spec.md](spec.md) · **Research**: [research.md](research.md) · **Date**: 2026-10-05

Room `saveme.db` goes from v8 to v9. Money is `Long` minor units plus an ISO 4217 code (P-03);
dates are `LocalDate` stored as ISO `YYYY-MM-DD` text; instants are ISO-8601 UTC text (P-05).

## 1. Table `budget` (new)

The server's last copy of each of the person's own budgets (`Budget` in `budgets.yaml` v1.8), or a
placeholder (`version = 0`) for one created on this phone and not yet accepted.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT PK | no | client UUIDv7 |
| `account_id` | TEXT | no | the signed-in person (`user_id` on the wire); every query filters by it |
| `category_id` | TEXT | yes | null = all expenses; immutable |
| `period_type` | TEXT | no | `monthly`; another value is kept and shown read-only (research R-15) |
| `amount_minor` | INTEGER | no | the limit, 1 … 2^53 − 1 |
| `currency` | TEXT | no | ISO 4217; immutable |
| `archived_at` | TEXT | yes | instant; null while active |
| `version` | INTEGER | no | 0 = placeholder |
| `change_seq` | INTEGER | no | 0 for a placeholder |
| `created_at` | TEXT | no | server's, or the phone's instant for a placeholder (orders the list) |
| `updated_at` | TEXT | no | |
| `refreshed_at` | TEXT | no | when the phone stored this copy |

Index: `(account_id, archived_at)`. A tombstone (`deleted_at` set) is never stored: it removes the
row unless a change waits (then the engine's deletion clash decides). `toString` prints id and version only.

## 2. Table `budget_progress` (new)

The server's last progress figures for a budget, and the phone's synced-only figures at that moment
(research R-06). Phone-side cache, never synced.

| Column | Type | Null | Notes |
|---|---|---|---|
| `budget_id` | TEXT PK | no | removed with its budget |
| `account_id` | TEXT | no | |
| `period_start` | TEXT | no | `LocalDate` |
| `period_end` | TEXT | no | inclusive |
| `days_in_period` | INTEGER | no | |
| `limit_minor` | INTEGER | no | `limit.amount_minor` |
| `spent_minor` | INTEGER | no | `spent.amount_minor` |
| `currency` | TEXT | no | |
| `other_currencies` | TEXT | no | sorted codes, comma-separated (`""` when none) |
| `local_spent_minor` | INTEGER | no | the phone's synced-only spent for the same period when stored |
| `local_other_currencies` | TEXT | no | the phone's synced-only other currencies when stored |
| `read_at` | TEXT | no | instant of the read |

Not stored: `remaining`, `over_limit`, `projected_end`, `days_elapsed`, `is_current` — all
re-derived on the phone (R-07), so a stored row never goes stale against "today".

## 3. Table `sync_scope` (unchanged schema)

Migration 8 → 9 sets `feed_cursor`, `feed_read_at` and `feed_position` to NULL for the `user:%`
rows only (R-02).

## 4. Migration 8 → 9

```text
CREATE TABLE budget (… §1 …, PRIMARY KEY(id))
CREATE INDEX index_budget_account_id_archived_at ON budget (account_id, archived_at)
CREATE TABLE budget_progress (… §2 …, PRIMARY KEY(budget_id))
UPDATE sync_scope SET feed_cursor = NULL, feed_read_at = NULL, feed_position = NULL WHERE scope LIKE 'user:%'
```

Every existing row in every other table is kept. `9.json` is exported; `Migration8To9Test` checks
the tables, the user-scope reset and that wallet scopes keep cursor and position.

## 5. Queue payload (`budgets.data`, spec 004 `pending_change.payload_json`)

`BudgetPayload { category_id: String?, currency: String, amount_minor: Long, archive: Boolean }`,
record type `budget`, scope `user:<account id>`. A create carries every member; a later edit carries
the stored category and currency unchanged and the new limit. `archive = true` queues the archive
(after a whole save when the limit also changed). A deletion is the engine's delete change with the
base version. Never printed (P-09.2).

## 6. Domain models (`budgets.domain.model`)

| Model | Members |
|---|---|
| `Budget` | id, categoryId?, periodType (`Monthly` / `Unknown(wire)`), limit `Money`, currency, archived, version, createdAt |
| `BudgetDraft` | categoryId?, currency, limitMinor |
| `BudgetPeriod` | start, end, daysInPeriod, daysElapsed? |
| `PeriodState` | `Known(period)` · `Unknown` (pending start day change, no stored server period) |
| `BudgetFigures` | spentMinor, limitMinor, remainingMinor, overLimit, percent, projectedMinor?, otherCurrencies, source (`Server` / `Phone`), includesWaiting |
| `BudgetView` | budget, display name (null = all expenses), figures? (null when the period is unknown or the type is unknown), sync state (`Synced` / `Waiting` / `Clash(theirs?, deletedElsewhere)` / `Refused(reason, fields)`) |
| `BudgetsOverview` | period state, pending start day (`day`, `from`)?, active list, archived list, refreshedAt |
| `BudgetChoices` | category choices (C5) plus "All expenses", currencies, "already budgeted" keys for the chosen currency, start day |

## 7. State transitions

```text
budget:  (none) --create--> Waiting(v0) --accepted--> Active(v1)
         Active --limit edit--> Waiting --accepted--> Active(v+1)
         Active --archive--> Waiting --accepted--> Archived (one-way; limit still editable)
         Active|Archived --delete--> Waiting --accepted--> removed (tombstone on the server)
         Waiting --409--> Clash --keep mine--> Waiting(newer base) | --use theirs--> server copy
         Waiting --409 tombstone--> Deleted elsewhere --accept--> removed
         Waiting --refused--> Refused --try again--> Waiting | --discard--> server copy / removed (placeholder)

figures: period known? --no--> "Progress appears after sync"
         period known --> phone figures --(progress read, nothing changed since)--> server figures
         server figures --(any matching waiting change, synced change, limit edit, new period)--> phone figures
```
