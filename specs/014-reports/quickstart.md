# Quickstart: Reports (client)

## Prerequisites

- Worktree on branch `014-reports`; Room v10 (unchanged by this feature).
- Local backend at `saveme-backend@b63e917` or later with spec 019 migrated (`dev migrate`), reached as in specs 012–013 (`127.0.0.1:43871` if the `.test` name flakes).
- Two verified accounts (owner and the spec 013 `+partner` seed) for shared-wallet checks.

## Automated checks (host only)

```bash
./gradlew :shared:testAndroidHostTest --tests '*Report*'           # calculator, periods, ledger, labels, service, stores, contract, captured parity
./gradlew :shared:testAndroidHostTest --tests '*AppGraphTest' --tests '*LayeringTest' --tests '*SignOutWipeTest' --tests '*ReportsPerformanceTest'
./gradlew :shared:verifyRoborazziAndroidHostTest                  # chart components, Reports catalogue, screens (EN, AR RTL, 2.0)
./gradlew :shared:testAndroidHostTest --rerun-tasks                # everything
./gradlew :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64
```

Expected: all green; `shared/schemas/…/AppDatabase/10.json` unchanged and no `11.json`; Home and
wallet-detail goldens change on purpose (the "Reports" row and the "Report" action).

## Host QA against the live backend (no device; research R-13)

1. Seed with curl: three EGP wallets (cash, bank, credit card with an opening debt), one USD wallet, one archived EGP wallet; salary, groceries, transport, an own category, a partner's expense in a shared wallet, a transfer cash → bank, a transfer by the partner from the partner's own wallet into the shared wallet (a wallet the owner is not in), a deleted transaction, a future-dated one; a monthly rent rule (scheduled occurrence) and a partner's monthly transfer rule into the shared wallet (gap 2); a second account whose start day is changed to 25 (a pending change: its "applies from" date is ahead). A start day change already in force cannot be produced live on a fresh account (spec 012 summary), so the past-change case is proven by the period table test only.
2. Capture `/sync/user/changes`, every wallet feed, and the six reports (cross-wallet EGP and USD, each wallet; `include_planned` true and false; an explicit range; trend `months=12`) into `CapturedReportResponses.kt` (no token or credential committed).
3. `ReportsCapturedParityTest` replays the feeds into an in-memory Room database through the real appliers and asserts the phone's figures equal the captured reports member by member (SC-002), and the derived periods equal the captured `periods` (SC-004).
4. Check gap 2 (spec §9): whether a scheduled transfer into a covered wallet from a wallet the owner is not in reaches the phone; record the answer in the QA report.

## Manual checks (dev build, owner's device) — deferred

1. Home → Reports: current period, default currency, summary and top categories (US1).
2. Airplane mode, add a 200 EGP Groceries expense, reopen Reports: counted, "Includes changes waiting to sync", "You're offline · Updated …" (US2).
3. Back online, pull to refresh: note gone, figures unchanged; Logcat shows `report.parity ok` (dev probe).
4. Wallet detail → Report on a shared wallet; partner's phone shows the same (US3).
5. Trend 3/6/12; Arabic mirrored; 200% font (US4, SC-006).
6. Previous/Next, custom range 366 vs 367 days (US5); USD notice (US6); Include scheduled (US7).
