# E2E report: 001-design-system-auth

**Run**: 2026-09-29 · device emulator-5554 (Pixel_10_Pro_Fold, API 37) · saveme-kmp@aae19e3 (+ uncommitted) · saveme-backend@cf8489b (+ uncommitted Billing work)
**Result**: 8 passed · 0 failed · 18 manual · 26 scenarios

Precondition fixed during this run: every sign-in crashed the app with `JsonConvertException ... at path: $.data.entitlements.plan`. The backend's uncommitted Billing change turns `entitlements.plan` from a string into `PlanRef {id, name}`. The client now follows the new shape (`PlanRefDto` in `IdentityDtos.kt`). The results below are from after that fix.

| Scenario | Flow | Result | Notes |
|---|---|---|---|
| US1-S1 | — | manual | external: visual comparison with the design board; screenshot tests cover the rendering |
| US1-S2 | — | manual | external: mirroring and Arabic typography are visual; covered by the `*_ar` screenshot tests |
| US1-S3 | — | manual | external: clipping at 200% font scale is visual; covered by the `*_fs2` screenshot tests |
| US1-S4 | — | manual | external: "nothing is submitted" can't be observed from the catalog, and the non-colour state cue is visual |
| US1-S5 | US1-S5-money-fraction-digits.yaml | pass | EGP 2 and KWD 3 fraction digits, U+2212 minus before the code. Not asserted: tabular digits (a font feature) |
| US2-S1 | US2-S1-sign-in-valid.yaml | pass | |
| US2-S2 | US2-S2-wrong-credentials-same-message.yaml | pass | Same message for a wrong password and for an unregistered address; the password is cleared both times |
| US2-S3 | US2-S3-lockout-after-five-failures.yaml | pass | The locked message shows the minutes and "Forgot password?", with no wrong-password message |
| US2-S4 | US2-S4-relaunch-stays-signed-in.yaml | pass | |
| US2-S5 | US2-S5-session-ended-elsewhere.yaml | pass | The session is ended from a second API session; "Your session ended" shows, and a relaunch lands signed out |
| US2-S6 | US2-S6-pending-deletion-cancelled-on-sign-in.yaml | pass | The server confirms the deletion was cancelled |
| US2-S7 | — | manual | external: the flow can't cut the app's connection (the backend is reached through adb reverse) |
| US3-S1 | — | manual | not-built: there is no registration screen ("Create account" does nothing) |
| US3-S2 | — | manual | not-built: no registration screen |
| US3-S3 | — | manual | not-built: no registration screen |
| US3-S4 | — | manual | not-built: no verification deep link handling |
| US3-S5 | — | manual | not-built: no verification deep link handling |
| US3-S6 | — | manual | not-built: no "Resend email" action in the app |
| US3-S7 | — | manual | not-built: no verify prompt |
| US4-S1 | — | manual | not-built: "Forgot password?" does nothing |
| US4-S2 | — | manual | not-built: no reset screen |
| US4-S3 | — | manual | not-built: no reset screen |
| US4-S4 | — | manual | not-built: no reset flow |
| US4-S5 | — | manual | not-built: no forgot-password screen |
| US5-S1 | US5-S1-sign-out.yaml | pass | The server session ends. The app returns to the Entry screen, not the sign-in form the spec names |
| US5-S2 | — | manual | external: the flow can't take the app offline |
