# Quickstart: Budgets (client)

## Prerequisites

- JDK and Android SDK as in the README; no device needed for the automated checks.
- For live QA: `../saveme-backend` dev container running (`dev project open`), API reachable on the port in `devApiHost`; throwaway users registered through `POST /auth/register` and verified as in specs 009–011.

## Automated checks (host only)

```bash
./gradlew :shared:testAndroidHostTest --tests '*Budget*'          # calculators, periods, selection, service, handler, applier, stores
./gradlew :shared:testAndroidHostTest --tests '*Migration8To9Test' --tests '*AppGraphTest' --tests '*LayeringTest' --tests '*SignOutWipeTest'
./gradlew :shared:verifyRoborazziAndroidHostTest                  # choice row, budget catalogue, screens (EN, AR RTL, 2.0)
./gradlew :shared:testAndroidHostTest --rerun-tasks                # everything
./gradlew :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64
```

Expected: all green; `shared/schemas/…/AppDatabase/9.json` present; no unexpected golden change
(Home goldens change on purpose for the Budgets row and card).

## Host QA against the live backend (no device; research R-16)

1. Register owner A and partner B; A creates an EGP wallet and a USD wallet, invites B to the EGP wallet as editor (sharing API).
2. As A: create Groceries 6,000 EGP and an all-expenses budget through the app's request shapes (curl, A1); record A's Groceries expenses, an income, a transfer, a USD Groceries expense, one deleted expense; as B: a Groceries expense in the shared wallet.
3. `GET /budget-progress` and `GET /budgets/{id}/progress`; capture every answer into `CapturedBudgetResponses.kt` (request ids kept).
4. Replay through the real API client, engine, handler, applier, `TransactionOwnSpending`, `SpendingTotals` and `ProgressSelection`: the phone's figures equal the server's (spent, remaining, over limit, projection, other currencies) — SC-003.
5. Start day: set 25, read periods for today; change to another day and capture `budget_period_start_day_from` and the server period; check `BudgetPeriods` against them — SC-004.
6. Batch: upsert and delete items without `wallet_id`; archive per record; a stale save (409), a tombstone (409 with `deleted_at`), an income category (422 `INVALID_ENUM_VALUE /category_id`), limit 0 (422 `OUT_OF_RANGE`), unverified (403).
7. User feed: `budget` rows from the start after clearing the position; tombstone row after delete.

## Manual checks (dev build, owner's device) — deferred

Run `.maestro/012-budgets/` (contracts/ui-and-e2e.md) and the brief's checkpoint: Groceries budget,
record an expense offline, progress moves at once and matches the server after sync.
