# E2E report: 001-design-system-auth

**Run**: 2026-09-29 · device emulator-5554 (Pixel_10_Pro_Fold, API 37) · saveme-kmp@aae19e3 (+ uncommitted) · saveme-backend@cf8489b (+ uncommitted Billing work)
**Result**: 18 passed · 0 failed · 8 manual · 26 scenarios (US3/US4 rows updated 2026-09-29 from spec 003's run)

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
| US3-S1 | 003: US1-S1-register-fresh-address.yaml | pass | Built in spec 003; register → check your inbox. The "identical whether or not registered" part is superseded: an existing address answers 409 (constitution v1.2.0 P-09.4, 003 US1-S2) |
| US3-S2 | 003: US1-S3-register-client-rules.yaml | pass | Built in spec 003 |
| US3-S3 | — | manual | Built in spec 003; no server-only field rejection reachable from the UI (003 report US1-S4) |
| US3-S4 | 003: US2-S1-verify-link-signed-in.yaml, US2-S6-paste-link.yaml | pass | Built in spec 003; signed in and signed out |
| US3-S5 | 003: US2-S2-verify-link-no-longer-works.yaml | pass | Built in spec 003 |
| US3-S6 | 003: US2-S4-resend-throttled.yaml | pass | Built in spec 003; the cooldown is 30 s (003 FR-013) |
| US3-S7 | — | manual | Built in spec 003; no endpoint returns EMAIL_NOT_VERIFIED until B4 (003 report US2-S5) |
| US4-S1 | 003: US3-S1-forgot-same-answer.yaml | pass | Built in spec 003 |
| US4-S2 | 003: US3-S2-reset-with-link.yaml | pass | Built in spec 003; sign-in isn't prefilled on this path |
| US4-S3 | 003: US3-S3-reset-link-no-longer-works.yaml | pass | Built in spec 003 |
| US4-S4 | 003: US3-S4-reset-ends-this-phone-session.yaml | pass | Built in spec 003; the phone is signed out on its next request, not at once (003 FR-019) |
| US4-S5 | 003: US3-S5-forgot-rate-limit.yaml | pass | Built in spec 003 |
| US5-S1 | US5-S1-sign-out.yaml | pass | The server session ends. The app returns to the Entry screen, not the sign-in form the spec names |
| US5-S2 | — | manual | external: the flow can't take the app offline |
