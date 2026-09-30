# E2E report: 003-auth-completion

**Run**: 2026-09-29 · device emulator-5554 (Pixel_10_Pro_Fold, API 37) · saveme-kmp@a3924f9 (+ spec 003, uncommitted) · saveme-backend@56b44fb
**Result**: 14 passed · 0 failed · 3 manual · 17 scenarios

Links are real: the runner issues them through the backend's `LinkRepository` (research R-07), and flows open them with Maestro `openLink`, which goes through the app's deep-link intake. The first run had four failures:

| Flows | Side | Cause and fix |
|---|---|---|
| US2-S1, US2-S6 | env | The welcome-mail job on the backend queue issued its own verification link after the runner's and superseded it. The runner now waits for the welcome link before issuing. |
| US3-S1, US3-S5 | flow | Typing went into a prefilled email field. The flows now relaunch or rely on the prefill. |

All four pass on rerun. No app defects were found.

| Scenario | Flow | Result | Notes |
|---|---|---|---|
| US1-S1 | US1-S1-register-fresh-address.yaml | pass | Registers over HTTP; check-inbox names the address; sign-in is prefilled |
| US1-S2 | US1-S2-register-existing-address.yaml | pass | 409 → "already has an account" with sign-in (prefilled) and reset |
| US1-S3 | US1-S3-register-client-rules.yaml | pass | Malformed email, short password, password equal to email. "Nothing is sent" is proved by `RegisterViewModelTest`. |
| US1-S4 | — | manual | not-built on the server side: the backend accepts every value the client's own rules accept, so no field can be rejected server-side from the UI. Covered by `RegisterViewModelTest.serverFieldErrorsLandOnFields`. |
| US1-S5 | — | manual | environment: needs 10 registrations from this machine within an hour, which would block every other registration flow for that hour (analysis C3). Covered by `RegisterReducerTest.rateLimitShowsMinutes`. |
| US2-S1 | US2-S1-verify-link-signed-in.yaml | pass | Signed in: prompt shown → link opened → confirmed → prompt gone without signing in again. The signed-out path is in US2-S6. |
| US2-S2 | US2-S2-verify-link-no-longer-works.yaml | pass | Superseded link, signed out → "no longer works" + Sign in. The signed-in variant is in `VerifyEmailViewModelTest`. |
| US2-S3 | US2-S3-verify-prompt.yaml | pass | Home prompt persists across a relaunch. The account-screen prompt is open finding C1. |
| US2-S4 | US2-S4-resend-throttled.yaml | pass | Sent → hidden during the 30 s cooldown → fourth request shows "Too many requests. Try again in N minutes." |
| US2-S5 | — | manual | not-built: no backend endpoint returns `EMAIL_NOT_VERIFIED` yet (the `address.verified` middleware is registered but unused until wallets, B4). The sheet is wired to `ApiEvent.EmailNotVerified`. |
| US2-S6 | US2-S6-paste-link.yaml | pass | Dev Paste link → confirmed, signed out; the server confirms `email_verified` |
| US3-S1 | US3-S1-forgot-same-answer.yaml | pass | Registered and unknown addresses get the same confirmation |
| US3-S2 | US3-S2-reset-with-link.yaml | pass | New password set; sign-in works with it; the old one is refused (401 over the API). Sign-in isn't prefilled (analysis U1). |
| US3-S3 | US3-S3-reset-link-no-longer-works.yaml | pass | Superseded link → "no longer works" → Request a new link; the password is unchanged |
| US3-S4 | US3-S4-reset-ends-this-phone-session.yaml | pass | The phone stays signed in after the reset, then its next request shows "Your session ended" (FR-019) |
| US3-S5 | US3-S5-forgot-rate-limit.yaml | pass | Fourth request in an hour → "Too many requests. Try again in N minutes." |
| US3-S6 | US3-S6-reset-password-rules.yaml | pass | Short and over-long passwords refused on the phone; the old password still works |
