# E2E report: 002-account-security

**Run**: 2026-09-29 · device emulator-5554 (Pixel_10_Pro_Fold, API 37) · saveme-kmp@aae19e3 (+ uncommitted) · saveme-backend@cf8489b (+ uncommitted Billing work)
**Result**: 24 passed · 2 failed · 2 manual · 28 scenarios

"Device B", and the other devices, are real backend sessions of the same account. `.maestro/common/api.js` signs them in over the API, so the multi-device scenarios run on one emulator. The spec numbers Story 3's offline scenario "10" and places it between 4 and 5. It keeps that number here (US3-S10).

| Scenario | Flow | Result | Notes |
|---|---|---|---|
| US1-S1 | US1-S1-list-devices.yaml | pass | Device B is listed above "This device", and only B has "Sign out" |
| US1-S2 | US1-S2-end-one-device.yaml | pass | The server confirms B's session ended |
| US1-S3 | US1-S3-ended-device-returns-to-sign-in.yaml | pass | The phone plays device B |
| US1-S4 | US1-S4-end-all-other-devices.yaml | pass | Three API devices |
| US1-S5 | US1-S5-cancel-ends-nothing.yaml | pass | Both dialogs; the server confirms B is still signed in |
| US1-S6 | US1-S6-only-this-device.yaml | pass | |
| US2-S1 | US2-S1-change-password.yaml | pass | The server confirms B was signed out; this device survives a relaunch |
| US2-S2 | US2-S2-wrong-current-password.yaml | pass | The server confirms the old password still works |
| US2-S3 | US2-S3-lock-after-wrong-current-passwords.yaml | pass | Submit is disabled while locked |
| US2-S4 | US2-S4-new-password-rules.yaml | pass | Too short, too long, and equal to the email |
| US2-S5 | US2-S5-only-new-password-works.yaml | pass | Old password 401, new password 200 over the API |
| US3-S1 | US3-S1-profile-shows-stored-values.yaml | FAIL (app) | Step `assertVisible "Asia/Tokyo"`. The screen shows the stored values, but the select fields' values ("Asia/Tokyo", "KWD", "12") are not in the accessibility tree: `AppSelectField`'s click overlay is an unlabeled Button that hides the field. Screen readers can't read them either |
| US3-S2 | US3-S2-edit-and-save.yaml | pass | The saved name is shown on reopen and on the server |
| US3-S3 | US3-S3-save-disabled-without-changes.yaml | pass | |
| US3-S4 | US3-S4-conflict-with-other-device.yaml | pass | Both versions and both choices show; the server still holds the other device's value |
| US3-S10 | — | manual | external: the flow can't take the app offline |
| US3-S5 | — | manual | not-built: locking the currency needs a first transaction, and there's no transaction API or screen yet |
| US3-S6 | US3-S6-budget-start-day-applies-from.yaml | FAIL (app) | Step `assertVisible "Applies from .+"`. The save works and "Applies from 15 Oct 2026" is on screen, but the field's supporting text is hidden from the accessibility tree by the same `AppSelectField` overlay |
| US3-S7 | US3-S7-clear-display-name.yaml | pass | The server holds `display_name = null`. Not asserted: greetings (no screen greets by name yet) |
| US3-S8 | US3-S8-switch-to-arabic.yaml | pass | Arabic copy shows at once and after a restart. RTL mirroring itself is covered by the `*_ar` screenshot tests |
| US3-S9 | US3-S9-arabic-profile-after-sign-in.yaml | pass | |
| US4-S1 | US4-S1-deletion-explanation.yaml | pass | |
| US4-S2 | US4-S2-schedule-deletion.yaml | pass | The server has `deletion_scheduled_for` set; Home shows the notice |
| US4-S3 | US4-S3-wrong-password-and-lock.yaml | pass | Nothing scheduled on the server; the lock disables the confirm button |
| US4-S4 | US4-S4-persistent-deletion-notice.yaml | pass | The notice survives navigation and a relaunch |
| US4-S5 | US4-S5-cancel-deletion.yaml | pass | The server shows the account active again |
| US4-S6 | US4-S6-repeat-request-keeps-date.yaml | pass | The same date shows on screen and on the server |
| US4-S7 | US4-S7-sign-in-cancels-deletion.yaml | pass | |
