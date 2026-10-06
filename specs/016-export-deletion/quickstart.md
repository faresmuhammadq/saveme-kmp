# Quickstart: Export & Deletion Completion (client)

Validation guide. Owner policy: **no emulator, Maestro or device run** by agents; device steps are
listed for the owner and marked deferred.

## 1. Host gates (agent)

```bash
./gradlew :shared:testAndroidHostTest              # unit, contract, store, reducer, scan, layering, AppGraphTest
./gradlew :shared:verifyRoborazziAndroidHostTest   # screenshots (record first with recordRoborazziAndroidHostTest)
./gradlew :androidApp:assembleDevDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Expected: green; new goldens only for the screens/steps listed in
[contracts/ui-and-e2e.md](contracts/ui-and-e2e.md); changed goldens only for the Delete account
explanation, Account (new row), wallet detail (new action) and Home (if the notice variant is
captured).

## 2. Live contract QA (agent, after backend spec 022 is implemented and committed)

Prerequisites: `../saveme-backend` at the spec 022 commit, `dev project open`, migrations run, a
queue worker running (`dev -- php artisan queue:work`), base URL `http://127.0.0.1:43871/api/v1`.

1. Rerun `.specify/scripts/bash/check-backend-contract.sh`; record the new commit and fix drift.
2. Throwaway accounts (register + verify through Mailpit as spec 013/015 QA did).
3. Exports: `POST /exports {scope: account}` → 202 queued; repeat at once → 409 `EXPORT_IN_PROGRESS` with `current`; poll `GET /exports/{id}` until `ready` (note time); `GET /exports` page; `curl -I` the `download_url` → 200 `application/zip` attachment, no bearer; read again → a different signature; wallet scope as a viewer → 202; a wallet the account left → 404; 5 accepted then the 6th → 429 with `Retry-After`; unverified account → 202; pending-deletion account → 202.
4. Deletion: owner with wallet A (one viewer) and B (editor + viewer); `GET /account/deletion-request` → `active`, A `only_member`, B `oldest_editor` + `choice_expected`; `POST` with `successors` naming B's viewer → 200 with `chosen`; repeat with another choice → date unchanged; bad member id → 422 at `/successors/0/member_user_id`; cancel → choices dropped.
5. Purge (backend quickstart: travel past the date, run the purge command): the partner's wallet feed shows the new owner and a member tombstone; transactions keep `created_by`; the departed session → 401.
6. Capture answers into `CapturedExportResponses.kt` / `CapturedDeletionPlanResponses.kt` (URLs and signatures replaced by fakes of the same shape) and run the replay tests.

## 3. Device checks (deferred: E2E on owner's device)

Run `.specify/scripts/bash/run-e2e.sh 016-export-deletion` (flows in `.maestro/016-export-deletion/`),
then spot checks:

- "Open download" opens the browser, which saves `export-YYYY-MM-DD.zip`; the app's data directory holds no ZIP.
- Leaving the export screen stops the polling (backend request log shows no further `GET /exports/{id}`).
- Arabic RTL at 200 % font on every new screen and step.
- TalkBack announces status changes on the export screen.
- After the owner's purge, the partner's phone shows them as owner and "Added by a former member".
