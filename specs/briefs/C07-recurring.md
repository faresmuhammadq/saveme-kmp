# C7 · Recurring transactions (client)

**Pairs with**: backend [B7](../../../saveme-backend/specs/briefs/B07-recurring-transactions.md) · **Call with**: `/speckit-specify specs/briefs/C07-recurring.md`

## Description

Create a recurring rule from the transaction entry screen (frequency, interval, anchor day, start,
end or count), with presets for **gam'eya** (جمعية: monthly contribution, payout month) and
**installment** (قسط: monthly, fixed count). Show upcoming occurrences, skip one, edit one, or
edit the series from a date forward. Catch up missed occurrences with the materialise endpoint.

## Consumes

The B7 rule endpoints and `POST /recurring/{id}/materialise`; occurrences arrive as ordinary
transactions with `recurring_rule_id` (and `planned` status if B7 creates them ahead).

## Can build before the backend ships

Rule schema and editor, presets, a local "next due" preview computed with the same month-end rule
the backend picks, and the upcoming list.

## Needs from the backend

Whether occurrences are created ahead as `planned`, the month-end anchoring rule, the
occurrence key, and the series-edit semantics (from date forward).

## Considerations

- "Today" is the user's timezone, not the phone's clock zone if they differ (P-05).
- `planned` occurrences never count in balances; show them visually distinct.
- Don't materialise on the phone; ask the server, then pull.

## Integration checkpoint

E2E: monthly rule created, next occurrence visible as upcoming; skipping it removes it; series
edit changes later ones only.
