# Quickstart: validating Auth Completion

How to prove the feature works. Shapes are in [contracts/](contracts/) and
[data-model.md](data-model.md); this file only lists what to run and what to expect.

## Prerequisites

- saveme-backend dev container running on `127.0.0.1:43871` (`/up` answers 200).
- An Android emulator (or phone) in `adb devices`, English system language, Maestro installed,
  Maestro Studio closed.
- `adb reverse tcp:43871 tcp:43871` (the E2E runner does this).

## 1. Automated checks

```bash
./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest
```

Expect: contract tests for the five operations (every handled code), `AuthLinkParser` tests,
reducer and ViewModel tests for Register, VerifyEmail, ForgotPassword, ResetPassword, PasteLink
and the Home resend prompt, and screenshots of the new screens in EN, AR and 2× font, all green.

## 2. E2E

```bash
.specify/scripts/bash/run-e2e.sh 003-auth-completion
```

Or `/speckit-e2e 003`, which also writes `specs/003-auth-completion/e2e-report.md`.
Expect 17 scenarios. The one that needs a development build (US2-S6 Paste link) runs on the dev
flavor like the rest; none should be `manual` (spec SC-006).

## 3. Manual spot checks (dev build)

| # | Do | Expect |
|---|---|---|
| 1 | Entry → Create account → fresh address, valid password | "Check your inbox" naming the address |
| 2 | Same address again | "Already has an account" with Sign in / Reset password, email prefilled in both |
| 3 | Dev tools → Paste link → a verify link from the backend log | Verified screen; signed-in Home loses the verify prompt |
| 4 | Signed in, unverified → Resend email twice quickly | One request; button disabled ~30 s |
| 5 | Sign in → Forgot password? → any address | Same confirmation for registered and unknown addresses |
| 6 | Paste a reset link → new password → Continue | Sign-in with email prefilled; old password refused, new one works |
| 7 | Paste the same reset link again | "This link no longer works" + Request a new link |
| 8 | Arabic system language, repeat 1 and 5 | Mirrored layout, Arabic copy, no clipped text at 200% font |

## 4. Where a failure points

- A contract test fails: the DTO or code mapping drifted from identity.yaml; rerun
  `speckit-contract-check`.
- A link opens the browser instead of the app (dev): check the dev manifest placeholders
  (`linkHost`, `linkPort`) match `devApiHost`.
- E2E seeding fails: the backend's `LinkRepository` or `RegisterAccount` signature changed.
