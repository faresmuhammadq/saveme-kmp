# Quickstart: validating the Sync Engine

Shapes are in [contracts/](contracts/) and [data-model.md](data-model.md).

## 1. Automated

```bash
./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug
```

Expect green:
- engine tests: enqueue and collapse, revision guard, ordering, backoff on virtual time, clash / keepMine / useTheirs / tombstone restore, refusal, account tagging, stale `Sending` reset
- `pending_change` DAO tests and the v1→v2 migration test (a v1 profile with a pending edit comes out as a queued change)
- the WorkManager worker test
- spec 002's profile tests unchanged
- the account and sign-out-sheet screenshots with the new status lines

## 2. E2E (profile unchanged)

```bash
.specify/scripts/bash/run-e2e.sh 002-account-security
```

Expect the same 24 passes as spec 002's report (FR-019).

## 3. Manual, on an emulator with the dev build

| # | Do | Expect |
|---|---|---|
| 1 | Network off (`adb shell svc wifi disable; svc data disable`) and `adb reverse --remove tcp:43871`, edit the profile, save | "Saved on this phone. Waiting to sync."; Account shows "1 change waiting to sync" |
| 2 | Home, then `adb shell am kill com.fares.saveme` (not force-stop: Android cancels a force-stopped app's jobs by design), restore the connection (`adb reverse tcp:43871 tcp:43871`, network on), don't open the app | Within about a minute the backend's profile shows the edit (check via the API) |
| 3 | Offline edit, then Sign out | The sheet warns "1 change hasn't synced"; "Sync now" appears when back online |
| 4 | Offline edit on the phone, edit from another device via the API, reconnect | The profile shows the conflict card; Keep mine / Use the other device's behave as in spec 002 |
| 5 | Install the previous build, make an offline profile edit, install this build over it | The edit is still waiting and syncs (migration v1→v2) |

## Results (2026-10-02, emulator Pixel_10_Pro_Fold, API 37, dev build)

| # | Result | Notes |
|---|---|---|
| 1 | PASS | Maestro: "Saved on this phone. Waiting to sync." and "1 change waiting to sync" |
| 2 | PASS (after 3 fixes) | Server profile went version 1 → 2 with the name set about 7 s after the connection returned, app closed. The first runs found three gaps, fixed with tests: a background drain saw nobody signed in because no screen had restored the session (`CurrentAccount.load()`); a drain that ran before a row's wait was over scheduled nothing more (`drain()` reschedules for `nextDueAt`; stored times are now fixed-width so they compare as strings); JobScheduler held a closed app's ordinary jobs for many minutes although ready (zero-delay runs are now expedited on API 31+) |
| 3 | PASS | Sheet: "1 change hasn’t synced. Signing out now discards it." |
| 4 | PASS (covered) | Spec 002 E2E US3-S4 (conflict card, Keep mine / Use the other device's) passed on this build's engine |
| 5 | PASS (covered) | Not done by hand: `MigrationTest` builds a real v1 database with a pending profile edit and opens it with Room + `MIGRATION_1_2` |

Known limit: when the phone has a network but the server can't be reached (captive portal, server
down), retries back off and run as ordinary jobs, which Android may batch; the change still leaves,
later than a minute.
