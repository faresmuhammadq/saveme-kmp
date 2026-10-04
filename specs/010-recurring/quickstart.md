# Quickstart: Recurring Transactions (client)

## Prerequisites

- Branch `010-recurring` from `main` at `34e878d` (specs 004–009 merged; Room v6).
- For the live checks: the saveme-backend dev container (`dev project open` in `../saveme-backend`, port 43871), seeded (`dev -- php artisan db:seed`), at `saveme-backend@2a3b414` or a later commit with `recurring.yaml` v1.7 unchanged; the scheduler need not run (`materialise` is called by hand).
- `devApiHost` set (README "Development backend").

## Automated checks (host only)

```bash
./gradlew :shared:testAndroidHostTest                 # unit, contract, store, DAO, migration 6→7, AppGraphTest, layering
./gradlew :shared:verifyRoborazziAndroidHostTest      # screenshots: EN, AR RTL, 2× font
./gradlew :androidApp:assembleDevDebug
./gradlew :shared:compileKotlinIosSimulatorArm64       # iOS compiles
```

Expected: all green; `shared/schemas/com.fares.saveme.core.database.AppDatabase/7.json` exists.

## Host QA against the live backend (no device; research R-16)

For a verified QA account (registered through the API, spec 009 lesson) with an EGP wallet, a
second EGP wallet, a USD wallet, a second member (editor, then viewer) and an unverified account,
replay every call of [contracts/recurring-api-client.md](contracts/recurring-api-client.md) with
`curl` and the app's headers. Cover: rule create (custom monthly day 31, weekly, daily, yearly 29
Feb, gam'eya, installment total), replay, save, frozen-member refusals after a confirm, `ended`
then change, split steps, delete (and replay), occurrence confirm / identical replay / skip /
un-skip / edit / confirmed-then-change, confirm racing `materialise` auto-confirm (409 with the
confirmed copy), refusals (`/status` currency and destination, `/transaction_id` reuse, viewer
403, 404 unknown occurrence), `materialise` (200, 404, 409 tombstone), batch items of both types,
and wallet-feed pages carrying both row types. Compare server occurrence dates with
`RuleDates` for every captured rule (SC-002). Record outcomes in `qa-report.md`; save real bodies
in `CapturedRecurringResponses.kt` and run `RecurringCapturedResponsesTest`.

## Manual checks (dev build, owner's device) — deferred

1. Wallet → Recurring → Add: monthly rent on day 31 from next month; preview shows month ends; save offline → "Waiting to sync", "Dates appear after sync". Online → Upcoming lists the next occurrence.
2. Transaction editor → Make recurring → only a rule is created.
3. With an occurrence due today: Home shows "Due"; the wallet's transactions show it on top; confirm offline with another amount → transaction listed, balance moved; online → API shows one transaction linked to the rule.
4. Skip next month → leaves Upcoming; undo.
5. Confirm round 1, then Change from a date (round 3) with a new amount → rounds 1–2 unchanged.
6. Gam'eya 10 × 1,000, payout round 4 → payout 10,000 on round 4's date. Installment 10,000 / 12 → 833.34 × 4, 833.33 × 8.
7. Record the rent by hand while it is due → "Confirm Rent instead" → one linked transaction.
8. Check for new dates online; offline message.
9. Arabic RTL, Western digits, 200% font scale on all four screens.
10. Upgrade from the 009 build: data kept; existing rules (created via API) appear after the first sync.
