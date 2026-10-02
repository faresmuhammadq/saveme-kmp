# Quickstart: Transactions (client)

## Prerequisites

- Branch `008-transactions`; `main` at `77710b5` or later (specs 004–007 merged; Room v4).
- For the live checks: the saveme-backend dev container (`dev project open` in `../saveme-backend`, port 43871), seeded (`dev -- php artisan db:seed`, default categories), at `saveme-backend@f6d66eb` or a later commit with `transactions.yaml` unchanged.
- `devApiHost` set (README "Development backend").

## Automated checks (host only)

```bash
./gradlew :shared:testAndroidHostTest                 # unit, contract, store, DAO, migration 4→5, AppGraphTest
./gradlew :shared:verifyRoborazziAndroidHostTest      # screenshots: EN, AR RTL, 2× font
./gradlew :androidApp:assembleDevDebug
./gradlew :shared:compileKotlinIosSimulatorArm64       # iOS compiles
```

Expected: all green; `shared/schemas/com.fares.saveme.core.database.AppDatabase/5.json` exists and
is committed with the change.

## Host QA against the live backend (no device; research R-25)

Replay each call of contracts/transactions-api-client.md with `curl` and the app's headers
(`X-Client-Platform: android`, `X-Client-Version`, `Accept-Language`, `X-Correlation-Id`) for a
verified QA account with two EGP wallets and one USD wallet, a viewer membership seeded by hand,
and an unverified account. Record each status, code and pointer in `qa-report.md`; save the real
bodies into `CapturedTransactionResponses.kt` and run `TransactionCapturedResponsesTest`. Check the
wallet's `balance_minor` and `currency_locked` after create, edit, delete and restore.

## Manual checks (dev build, owner's device) — deferred

1. Record an expense from a wallet's transactions in three taps (amount, category, Save); it is listed under Today.
2. Airplane mode: record three transactions; each shows "Waiting to sync"; the wallet's balance includes them on the list, the detail and the wallet list. Back online: all sync; the balance does not jump.
3. Home → Add transaction: the last wallet is preselected; switching wallet works.
4. Transfer 500 from Cash to Bank offline: listed in both, both balances change; online: the API lists one transaction under both wallets.
5. Edit on the API session while the phone edits offline: "Changed on another device" with both values; Keep mine / Use the other device's.
6. Delete on the API session, edit on the phone: "Restore with my change" brings it back with the phone's values.
7. Search "plumb", filter This month + Expense; clear all.
8. Arabic: copy, RTL rows, amounts left-to-right, Western digits in the date picker.
9. 200% font scale: editor and list usable.
10. Wallet editor: currency read-only once a transaction is recorded on the phone.
11. Categories: a category used by an unsent transaction offers no Delete.
12. Upgrade from the 007 build: existing wallets and categories survive migration 4 → 5.
