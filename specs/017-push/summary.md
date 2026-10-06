# Summary: Push Notifications, without Firebase (spec 017, C16)

**Date**: 2026-10-06 · **Status**: implemented on the host without Firebase, host QA and live-backend QA green, device E2E and the Firebase phase deferred to the owner, committed on branch `017-push` (97f2c13) · **Backend contract**: `saveme-backend@e8c308a` when the design was recorded; live QA ran against the working tree of backend spec 023 (B16), which has since been committed as `7f624bb` — `notifications.yaml` v1.14 unchanged, no drift (`check-backend-contract.sh`; re-run it once more after this merge).

## What was built

- **A new `feature.notifications` context** behind two seams, `PushTokenProvider` and `PushReceiver`. Dev builds bind a fake provider; staging and prod bind none and show no push UI until the Firebase phase lands. **No Firebase artifact is on the build**: no `firebase-messaging`, `firebase-bom` or google-services plugin (a layering test fails on any Firebase import or build-file mention; `devDebugRuntimeClasspath` is clean).
- **Registration**: one idempotent WorkManager job registers this phone with `PUT /me/devices/{session}` (body `{push_token}` only) after sign-in, on a new address, when notifications become allowed, and at most once a day on foreground. Only an FNV-1a fingerprint of the token is kept. It removes the registration with `DELETE` when notifications are switched off on the phone. Sign-out makes a best-effort `DELETE` capped at 2 s before the session is wiped (a new `SignOutParticipant` in core); a cleaner then clears the shade, the stored records and the job and discards the address. A session ended from elsewhere only clears; it never sends a `DELETE`.
- **Channels and display**: six private channels (one per category), created in the app language and renamed when the language changes. Foreground messages show exactly the received text, tagged with the contract's collapse key and de-duplicated by `notification_id`, then trigger a forced background read.
- **Tap routing**: taps arrive through `PushTapBus`, are parsed and routed by pure tables for all 12 message types, and only ever navigate. A record not yet on the phone opens through an "Opening…" screen (at most 10 s, with Cancel). An invitation push opens a "check your email" notice (the token is never in a push). Export taps open spec 016's screens: `export_ready` → `ExportDetailKey(id)` (a malformed export payload → `ExportsKey`), and `deletion_reminder` → `DeleteAccountKey`, the pending-deletion view.
- **Notifications screen** (Account → Notifications): reads and saves the preferences online with one Save; a 409 replaces the edits with the server's `current` and asks the person to check and save again. Six category switches, quiet hours with the new design-system `TimeOfDayField` (quarter-hour, 24-hour, Western digits).
- **Permission**: the Android 13+ permission is asked only in context: from the Notifications screen, or from a sheet offered once per installation after the first recurring rule, first budget or first shared-wallet join. "Not now" is final.
- **Dev tools**: Account → Developer tools → "Simulate a notification" (dev builds only).
- No Room change (stays v10); registration record, shown notification ids and prompt flags live in the key-value store. About 225 new tests; 25 Maestro flows in `.maestro/017-push/` plus `open-notifications`, `open-shade` and `simulate-push` subflows (written, deferred); `api.js` gained `notificationPreferences`, `saveNotificationPreferences`, `expectCurrentSession`, and a missing `break` after `redeemCode` (it fell through into `createTransfer`) was fixed.

## Results

- **Host QA** (`qa-report.md`): 43 acceptance scenarios, 46 FRs, 9 SCs and 33 edge cases PASS (behaviour only a device can show is noted; those runs stay deferred). Four gaps found and closed: the tap-intake rules had no test (extracted `openPushTap` from `AppNavHost` into `PushTapRouting.kt`, behaviour unchanged, plus 3 tests); export taps fell back to the Account screen; nothing replayed real answers through the wire clients (new `NotificationsLiveReplayTest`); the Account goldens were stale after the merge with main.
- **Merge with main** (C13 reports and C15 export & deletion): conflicts in `AccountContract.kt` (`AccountDestination` is now `Profile, Devices, ChangePassword, DeleteAccount, Plan, Exports, Notifications, DevTools`), `AccountScreen.kt` (row order Profile, Plan, Devices, Notifications (provider only), Export my data, Change password, Developer tools (dev only), Delete account) and `AppNavHost.kt`, resolved by keeping both sides; strings (1160 names each, no duplicates) and `api.js` merged cleanly. Only 3 goldens were re-recorded after inspection (`screen_push_account_rows_*`).
- **Gates**: `--rerun-tasks` four-task gate and `cleanAllTests build allTests --continue` green: 3,879 host tests, 0 failed, 0 skipped (the first run after the merge failed 4 tests: three stale Account goldens, now fixed, and one `ReportsPerformanceTest` timing flake that passed in both later runs).
- **Live contract QA** (about 100 real calls, 3 throwaway accounts, the backend's `log` gateway only, 7 recorded pushes, 0 backend bugs): registration `PUT` 200 `{session_id, registered_at, refreshed_at}` (the token is never returned; a second call only moves `refreshed_at`); another session's id or a random uuid → 404; a `platform` member → 422 `UNKNOWN_FIELD`; an empty token → 422 at `/push_token`; the token moves to a new session or account; `DELETE` 204 also when nothing is left (another session's id 404); the 21st registration write in an hour → 429 with `Retry-After` and `retry_after_seconds` (limit per session, shared with `DELETE`); preferences first `GET` version 1 with all categories on and quiet hours 22:00–08:00, a `PUT` at version 1 → 200 at version 2, stale version → 409 with `current`, equal start and end → 422 at `/quiet_hours/end` (even with quiet hours off), a non-quarter-hour → 422 at `/quiet_hours/start`, the 31st save → 429 (every `PUT` attempt counts, `GET` is not limited); logout and ending a session from another session both removed the registration, but only after the `notifications` queue ran. Recorded pushes: `invitation_received`, `member_role_changed`, `member_removed`, `export_ready`, `occurrence_due`, `budget_exceeded`, `deletion_reminder` (no id in its data); the server skipped a `budget_exceeded` with `category_off` for the user who had switched budgets off. `occurrences_due_summary`, `occurrence_auto_confirm_refused`, `wallet_ownership_received` and `subscription_*` could not be produced on dev (parser tests cover them from the contract examples). `NotificationsLiveReplayTest` (17 tests) drives the real clients, `PushRegistrationService`, the preferences service and the receiving path with the captured answers; the app's notification tag equals the server's stored collapse key for all 7 pushes and each routes to the table's screen. Fixtures: `CapturedPushResponses.kt` (`dev-fake-push-<uuid>` tokens, no bearer credentials).

## Deferred to the owner

- Device runs: T036, T050, T059, T066, T072 (flows, quickstart, spot checks).
- **Gated Phase 9 "FCM wiring", T085–T091**: needs the owner's approval of `com.google.firebase:firebase-messaging` + `firebase-bom` and the `com.google.gms.google-services` plugin (in `:androidApp` only), a Firebase project with an Android app for `com.fares.saveme`, a `google-services.json` for each flavor (`androidApp/src/{dev,staging,prod}/`), the service-account key for the backend, and confirmation that no Firebase Analytics is added.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. Everything except Firebase is built now behind the `PushTokenProvider` + `PushReceiver` seam.
2. The permission is asked in context only; "Not now" is final.
3. Preferences are online-only with one Save, never queued; a 409 replaces the edits with the server's `current`.
4. A tap on a record not yet on the phone runs a forced read on an "Opening…" screen (≤10 s with Cancel); a tap only navigates, never acts.
5. Sign-out does a best-effort `DELETE` capped at 2 s before the session is wiped; a session ended elsewhere never sends a `DELETE`.
6. Re-registration on foreground at most daily; the token is never stored, only an FNV-1a fingerprint.
7. Six channels, private on the lock screen; foreground messages show exactly the received text with the collapse key; `notification_id` dedupe.
8. Quiet hours use a new quarter-hour `TimeOfDayField` component.
9. The invitation tap opens a "check your email" notice; developer tools are reachable from Account in dev builds.

### Implementation

10. `rememberOpenAppNotificationSettings()` is a composable (Android needs a Context); the permission requester uses `ComponentActivity.activityResultRegistry`, so `:shared` needs no `activity-compose`.
11. A one-method `PushReconciler` interface lets the worker be tested with a fake; `devOrUnavailablePushProvider()` is both the default and what `MainApplication` passes.
12. `NotificationOpenKey` carries a `nonce` so a newer tap replaces an older "Opening…" screen; a Home target trims the stack back to the existing Home entry.
13. An unknown type that names no category posts to the `recurring_due` channel; one string `notifications_discard_body` was added because `AppAlertDialog` requires body text.
14. `RuleEditorEffect.Created` is sent before `Close` on a create only (one existing editor test updated); `BudgetsLayeringTest` allows `notifications` to import `BudgetsService`.
15. The Android 12 branch of the permission state is tested by setting `SDK_INT` (only API 35 images are cached offline); `androidApp` has no host tests, so the tap intake lives in `PushTapExtras.intake`.
16. Registration waits 60 s when `Retry-After` is missing; `NOT_FOUND` retries once, and only if the stored session changed; the time zone is shown as the IANA id.
17. The existing `api` debug logger prints request URLs, so the registration call logs the device session id (unchanged client behaviour); the `push` tag logs only an outcome or a type, and a log-capture test checks that no address, fingerprint, payload id, title or text appears.
18. The fixtures file is `CapturedPushResponses.kt` (not the spec's `CapturedNotificationsResponses.kt`).

### Owner review

19. The Arabic copy.

## Remaining risks

- Real FCM delivery, the system permission dialog, the lock screen and TalkBack are unverified until the owner's device runs and the Firebase phase.
- The backend's FCM message sets no `android.notification.visibility` (lock-screen privacy relies on the client's channels) and no `click_action`, so background taps arrive on the launcher activity, which other apps can also open; the client validates the data and only navigates. The collapse key is not in the data block (the client derives it from the contract table); a registration cannot be read back; the payload carries no account id (the client clears all notifications at sign-out); push text follows the profile language, which can differ from the app's language override; there is no test-push endpoint.
- Server-side registration removal after sign-out and session end is queue-delayed, so the row briefly outlives the session; the preferences `PUT` limit (30/h) counts refused attempts too.
- Robolectric may not reproduce `NotificationManager` faithfully (tags, public version, channel visibility); a device spot check is listed (CHK044).
- Phase 9 will touch build files that branch 018 (Google Play billing) may also edit, so rebase it last.
- `ReportsPerformanceTest` can flake under machine load.
