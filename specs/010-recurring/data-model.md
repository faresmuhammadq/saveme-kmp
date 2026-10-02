# Data Model: Recurring Transactions (client)

Room `saveme.db` goes from **v6 to v7** (`MIGRATION_6_7`, exported `shared/schemas/…/7.json`).
Wire shapes are `recurring.yaml` v1.7 (`RecurringRule`, `Occurrence`); this file lists only what
the phone stores and derives.

## 1. Table `recurring_rule` (new)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | UUIDv7, client-generated |
| `account_id` | TEXT NOT NULL | the signed-in person (never another account's data) |
| `wallet_id` | TEXT NOT NULL | the source wallet (transfer rules too) |
| `preset` | TEXT NOT NULL | `custom` \| `gameya` \| `installment` \| unknown kept verbatim |
| `status` | TEXT NOT NULL | `active` \| `paused` \| `ended` \| unknown |
| `kind` | TEXT NOT NULL | `income` \| `expense` \| `transfer` |
| `amount_minor` | INTEGER NOT NULL | Long minor units (P-03) |
| `amount_basis` | TEXT NOT NULL | `per_occurrence` \| `total` |
| `currency` | TEXT NOT NULL | ISO 4217 |
| `category_id` | TEXT NULL | |
| `counter_wallet_id` | TEXT NULL | transfer destination |
| `description`, `memo`, `counterparty` | TEXT NULL | never logged |
| `auto_confirm` | INTEGER NOT NULL | boolean |
| `frequency` | TEXT NOT NULL | `daily` \| `weekly` \| `monthly` \| `yearly` \| unknown |
| `interval` | INTEGER NOT NULL | 1–365 |
| `anchor_day` | INTEGER NULL | 1–31, monthly/yearly |
| `start_date` | TEXT NOT NULL | `YYYY-MM-DD` (LocalDate, P-05.2) |
| `end_date` | TEXT NULL | |
| `occurrence_count` | INTEGER NULL | |
| `payout_round` | INTEGER NULL | gam'eya |
| `payout_category_id` | TEXT NULL | gam'eya |
| `timezone` | TEXT NULL | server read-only; null on a placeholder |
| `created_by` | TEXT NULL | server read-only; null on a placeholder |
| `version` | INTEGER NOT NULL | 0 = placeholder (never accepted) |
| `change_seq` | INTEGER NOT NULL | |
| `created_at`, `updated_at` | TEXT NOT NULL | UTC instants |
| `refreshed_at` | TEXT NOT NULL | when this copy was stored |
| `follows_rule_id` | TEXT NULL | **phone-only**: the rule this one continues after "Change from a date" while it is unsent (research R-09); cleared when accepted |

Indexes: `(account_id, wallet_id, status)`, `(follows_rule_id)`.

## 2. Table `occurrence` (new)

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | server-minted UUIDv7 |
| `account_id`, `wallet_id` | TEXT NOT NULL | |
| `recurring_rule_id` | TEXT NOT NULL | |
| `ordinal` | INTEGER NOT NULL | round number |
| `is_payout` | INTEGER NOT NULL | boolean |
| `scheduled_date` | TEXT NOT NULL | fixed; part of the occurrence key |
| `date` | TEXT NOT NULL | effective date |
| `status` | TEXT NOT NULL | `scheduled` \| `confirmed` \| `skipped` \| unknown |
| `kind`, `amount_minor`, `currency`, `category_id`, `counter_wallet_id`, `description`, `memo`, `counterparty` | as for rules | |
| `edited` | INTEGER NOT NULL | boolean |
| `transaction_id` | TEXT NULL | set when confirmed |
| `auto_confirm_refusal` | TEXT NULL | `not_permitted` \| `counter_wallet_unavailable` \| `currency_mismatch` \| unknown |
| `version`, `change_seq`, `created_at`, `updated_at`, `refreshed_at` | | |
| `local_notice` | TEXT NULL | **phone-only**: `already_confirmed` after an accepted clash (R-06); cleared when opened |

Indexes: `(account_id, wallet_id, date)`, `(recurring_rule_id, date)`, `(transaction_id)`.

## 3. Table `transactions` (changed)

| Column | Change |
|---|---|
| `pending_confirmation` | **new** INTEGER NOT NULL DEFAULT 0. 1 = phone-only row showing a confirmation not yet answered (research R-07): `version = 0`, `recurring_rule_id` and `occurrence_id` set, never queued as a transaction change. Replaced in place by the server copy (same id). |

## 4. Migration 6 → 7

```text
CREATE TABLE recurring_rule (...);   CREATE INDEX ...
CREATE TABLE occurrence (...);       CREATE INDEX ...
ALTER TABLE transactions ADD COLUMN pending_confirmation INTEGER NOT NULL DEFAULT 0
UPDATE sync_scope SET feed_cursor = NULL, feed_read_at = NULL WHERE scope LIKE 'wallet:%'
```

Every existing row in every other table is kept; the user scope's position is kept.
`DatabaseCleaner` deletes from `occurrence` and `recurring_rule` at sign-out.

## 5. Queue payloads (`recurring.data`, spec 004 `pending_change.payload_json`)

- `RulePayload` — every `RecurringRuleWrite` member except `version` (`preset … payout_category_id`), plus the phone-only `followsRuleId`. Record type `recurring_rule`, scope `wallet:<wallet_id>`. Delete = `isDelete` with no payload use.
- `OccurrencePayload` — `status, date, amount_minor, category_id, description, memo, counterparty, transaction_id`. Record type `occurrence`, scope `wallet:<wallet_id>`. Never `isDelete`.

Collapse (spec 004): a later edit replaces the payload, keeps the base version; a confirm's
`transaction_id` is kept by every later edit of the same waiting change (FR-024).

## 6. Domain models (`recurring.domain.model`)

- `RuleView(rule: Rule, pending: RulePending?, state: SyncMark, nextDate: LocalDate?, today: LocalDate, readOnly: Boolean, frozen: FrozenMembers, myRole)`.
- `Rule` — the wire fields as domain types (`LocalDate`, `Money`, enums with `Unknown(raw)`).
- `FrozenMembers(schedule: Boolean, amountAndCount: Boolean, lastResolvedDate: LocalDate?)` — from the held occurrences (R-08, FR-013).
- `OccurrenceView(occurrence, pending: OccurrencePending?, state: OccurrenceState, ruleDescription, syncMark, notice, refusal)`.
- `OccurrenceState` = `Upcoming | Due | Missed | Confirmed | Skipped` (+ `isPayout`, `autoConfirmRefusal`).
- `RuleDraft` (editor), `ConfirmDraft` (confirm form), `RulePrefill` (from the transaction editor).
- `SchedulePreview(dates: List<PreviewRound(date, amountMinor, isPayout)>)`.

## 7. State transitions

**Rule** (`status`): create `active` | `paused`; `active ⇄ paused`; `active|paused → ended`;
`ended` → only delete. Delete (any) → tombstone → removed from the phone (with its occurrences).
A placeholder (version 0) deleted before sending → removed, nothing sent.

**Occurrence** (`status`, server-created only): `scheduled → confirmed | skipped`;
`skipped → scheduled | confirmed`; `confirmed` final. Derived on the phone (never stored or sent):
`due` = scheduled ∧ `date ≤ today(rule zone)`; `missed` = due ∧ ∃ later non-payout due sibling;
`upcoming` = scheduled ∧ `date > today`.

**Pending confirmation** (transactions row, `pending_confirmation = 1`):
confirm queued → row inserted (counts in balance) → occurrence accepted (row stays) → feed brings
the transaction (row replaced, effect becomes carry) → wallet copy covers it (carry cleared).
Clash with confirmed / discard / use theirs / accept deletion → row dropped.
