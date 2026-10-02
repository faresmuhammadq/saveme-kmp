# Quickstart: validating Plans & Entitlements

How to prove the feature works. Shapes are in [contracts/](contracts/) and
[data-model.md](data-model.md).

## Prerequisites

- saveme-backend dev container on `127.0.0.1:43871`, migrated and seeded (`BillingSeeder`: Free,
  Plus · Monthly, Plus · Yearly).
- An Android emulator or phone in `adb devices`, English system language, Maestro installed,
  Maestro Studio closed. `adb reverse tcp:43871 tcp:43871` (the E2E runner does this).

## 1. Automated checks

```bash
./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest
```

Expect green: feature-value mapping (`null` → unlimited), `EntitlementSet` gate, refresher
(single flight, 60 s foreground floor, sign-out mid-flight discards), coordinator triggers
(sign-in, foreground, `FeatureNotEntitled` event), contract tests for `/plans`,
`/me/subscription`, `/me/entitlements`, Plan reducer and ViewModel tests (Google-Play-only price,
current mark, stale/offline), `PlanNameNotGatedTest`, `PlanCard` and Plan screen screenshots in EN,
AR and 2× font.

## 2. E2E

```bash
.specify/scripts/bash/run-e2e.sh 005-plans-entitlements
```

Or `/speckit-e2e 005`, which writes `specs/005-plans-entitlements/e2e-report.md`. The checkpoint
flow (`US2-S5`, two parts) must pass: Free → grant → Plus after refresh, no sign-in.

## 3. Manual spot checks (dev build)

| # | Do | Expect |
|---|---|---|
| 1 | Sign in as a new person → Account → Plan | "Free"; "Sharing: not included", "Unlimited wallets"; no dates; catalogue lists Free, Plus · Monthly, Plus · Yearly, Free marked "Current plan"; no prices (seed has no Google Play price) |
| 2 | Grant Plus in tinker (see `run-e2e.sh grant_plus`), then open Plan (or tap its refresh) | "Plus · Monthly", "Active · Ends on {date in 30 days}", sharing with up to 5 people |
| 3 | Airplane mode, kill and relaunch, open Plan | Last plan shown with the stale note; refresh shows the note again, no sign-out |
| 4 | Switch the app to Arabic, open Plan | Arabic plan names (مجاني, بلس · شهري), mirrored layout, Western digits, no clipping at 200% font |
| 5 | Sign out, sign in as another person | No trace of the previous plan or subscription |

## 4. Where a failure points

- Contract test fails: billing.yaml drifted; rerun `speckit-contract-check`.
- Plus not shown after foreground: check the 60 s floor (wait or use the refresh button) and that
  the grant targeted the right user id.
