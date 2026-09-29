# C8 · Budgets (client)

**Pairs with**: backend [B8](../../../saveme-backend/specs/briefs/B08-budgets.md) · **Call with**: `/speckit-specify specs/briefs/C08-budgets.md`

## Description

Set a spending limit per category (or all expenses) per period, starting on the profile's budget
start day. Show progress (spent, remaining, percentage, projected end, over limit) on a budgets
screen and on the wallet/home summary with the existing `BudgetProgress` component.

## Consumes

The B8 budget endpoints and progress read model.

## Can build before the backend ships

Budget schema and editor, and a local progress estimate from local transactions so progress
updates instantly offline; replace it with the server figure after pull.

## Needs from the backend

Answers to the six banked Budgeting questions (shared-wallet attribution, currency, income
budgets, overlap, archived category, where progress is served), the `Period` boundaries, and the
progress fields.

## Considerations

- Neutral presentation: numbers and remaining amount, no red shaming (the backend brief's
  research note); colour choice is the client's and should follow the design board.
- Changing the start day applies forward only; the UI must say which date it applies from (as
  the profile already does).

## Integration checkpoint

E2E: budget for Groceries, record an expense, progress moves offline immediately and matches the
server after sync.
