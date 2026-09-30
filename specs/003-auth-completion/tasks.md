---
description: "Task list for 003-auth-completion"
---

# Tasks: Auth Completion (Register, Verify, Reset)

**Input**: Design documents from `specs/003-auth-completion/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (identity-api-client.md, deep-links.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0

**Tests**: Included. P-14 requires contract tests per endpoint (every handled code), a test per invariant (spec §4) and screenshot tests per screen; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Organization**: Phase 2 adds the API client, link parsing and deep-link intake that every story uses. US1–US3 are then independent. This supersedes spec 001 tasks T082–T104 (Phase 6 marks them).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US3 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`)
- Screenshot tests: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Android app: `androidApp/src/…`
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Spec 001's standing rules apply: strings in both languages (P-06), design-system components only (P-07), never log passwords or link secrets (P-09.2, FR-009), layering (P-10), naming (P-11).
- **MVI screen recipe (P-16)**, as in specs 001/002: `<Screen>Contract.kt` (immutable State, sealed Intent/Effect/Change, pure top-level `reduce`), `<Screen>ViewModel.kt` on `MviViewModel` taking use-case lambdas (never repositories), `<Screen>Route.kt` (`koinViewModel { parametersOf(args) }`, effects in `LaunchedEffect`), stateless `<Screen>Screen.kt` + `@Preview`; tests `<Screen>ReducerTest.kt` + `<Screen>ViewModelTest.kt` with `runMviTest`.
- No automatic retry of any operation in this feature (001 R-04, FR-020); submit is disabled while a request runs.
- Every interactive element gets the `testTag` listed in contracts/ui-and-e2e.md (FR-022), passed through the component's `modifier`.
- UI says "link", never "token".

---

## Phase 1: Setup

**Purpose**: configuration the foundation needs.

- [X] T001 Add `val linkPort: Int?` to `AppConfig` in `cm/core/config/AppConfig.kt`; in `androidApp/build.gradle.kts` add `buildConfigField("String", "LINK_PORT", …)` per flavor (dev: `devLinkPort`, staging/prod: `""`); in `androidApp/src/main/kotlin/com/fares/saveme/MainApplication.kt` pass `BuildConfig.LINK_PORT.toIntOrNull()`; update every `AppConfig(...)` call in `ct/` (e.g. `ct/feature/auth/AuthFixtures.kt`) with `linkPort = 8080`
- [X] T002 [P] Add `EMAIL_ALREADY_REGISTERED(RetryClass.Never)` to `cm/core/network/ApiErrorCode.kt`, map it in `ApiErrorCode.message()` in `cm/feature/auth/ui/AuthErrorCopy.kt` to new key `error_email_already_registered` ("This email already has an account." / Arabic), and extend `ct/feature/auth/ui/AuthErrorCopyTest.kt`

---

## Phase 2: Foundational (blocking)

**Purpose**: API client, link parsing, navigation keys and deep-link intake shared by all stories.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T003 [P] Contract tests in `ct/feature/auth/data/AuthCompletionContractTest.kt` using `AuthTestEnv`, one test per row of contracts/identity-api-client.md:
  - `register`: POST `…/api/v1/auth/register`, no Authorization header, body keys exactly `email,password,timezone` (+`display_name` only when non-blank); 202 → `Success`; 409 `EMAIL_ALREADY_REGISTERED` → `Failure(code=EMAIL_ALREADY_REGISTERED)`; 422 with pointers `/email`, `/password`, `/display_name`; 429 with `retry_after_seconds`
  - `verifyEmail`: POST `auth/verify-email`, body exactly `{token}`, public; 200 `{email_verified:true}`; 422 `VERIFICATION_TOKEN_INVALID`
  - `resendVerification`: POST `auth/resend-verification`, Authorization present, no body; 202; 429
  - `forgotPassword`: public, body exactly `{email}`; 202; 422 `/email`; 429
  - `resetPassword`: public, body exactly `{token,password}`; 204; 422 `RESET_TOKEN_INVALID`; 422 `VALIDATION_FAILED` at `/password` and at `/token`; 429
- [X] T004 [P] `AuthLinkParser` tests in `ct/feature/auth/domain/AuthLinkParserTest.kt`:
  - accepts `http://localhost:43871/verify-email?token=abc` (linkHost `localhost`, linkPort 43871, scheme `http`) → `AuthLink.VerifyEmail("abc")`
  - accepts `https://api.example/reset-password?token=abc` (linkPort null) → `ResetPassword`
  - trims surrounding whitespace
  - rejects other scheme, host, port or path; a missing or empty token; a token over 128 chars
  - the parsed value's `toString()` doesn't contain the token (FR-009)

### Implementation

- [X] T005 Add DTOs to `cm/feature/auth/data/dto/IdentityDtos.kt` per data-model §2: `RegisterRequest(email, password, @SerialName("display_name") displayName: String? = null, timezone: String)`, `TokenRequest(token)`, `VerifyEmailDataDto(@SerialName("email_verified") emailVerified: Boolean)`, `EmailRequest(email)`, `ResetPasswordRequest(token, password)`; blank display names are sent as `null` so they are omitted
- [X] T006 Add `register`, `verifyEmail`, `resendVerification`, `forgotPassword`, `resetPassword` to `cm/feature/auth/data/IdentityApi.kt`, following `signIn`'s pattern (`publicEndpoint()` on all but `resendVerification`), and pass-throughs returning `ApiResult` in `cm/feature/auth/data/AuthRepository.kt` (depends on T005)
- [X] T007 [P] Implement `AuthLink` (sealed: `VerifyEmail(token)`, `ResetPassword(token)`, `toString()` redacting the token) and `AuthLinkParser(linkHost, linkPort: Int?, scheme)` with `parse(raw: String): AuthLink?` in `cm/feature/auth/domain/AuthLinkParser.kt` per contracts/deep-links.md; register it in `cm/feature/auth/di/AuthModule.kt` built from `AppConfig` (scheme `http` when `environment == Dev`, else `https`)
- [X] T008 [P] Add outcomes `RegisterOutcome`, `VerifyOutcome`, `ResendOutcome`, `ForgotOutcome`, `ResetOutcome` to `cm/feature/auth/domain/model/Outcomes.kt`, exactly the cases in data-model §3
- [X] T009 [P] Add navigation keys `RegisterKey(prefillEmail: String?)`, `CheckInboxKey(email: String, kind: CheckInboxKind)`, `VerifyEmailKey(token: String)`, `ForgotPasswordKey(prefillEmail: String?)`, `ResetPasswordKey(token: String)`, `PasteLinkKey` to `cm/navigation/Routes.kt`, with `toString()` redacting tokens
- [X] T010 Deep-link intake, Android:
  - `androidApp/src/main/AndroidManifest.xml`: `android:launchMode="singleTask"` on MainActivity, plus an `autoVerify="true"` VIEW/DEFAULT/BROWSABLE filter for `https`, host `${linkHost}`, paths `/verify-email` and `/reset-password`
  - `androidApp/src/dev/AndroidManifest.xml`: an `http` filter with host `${linkHost}`, port `${linkPort}`, same paths
  - `androidApp/src/main/kotlin/com/fares/saveme/MainActivity.kt`: forward `intent?.dataString` to `DeepLinkBus.submit` in `onCreate` (only when `savedInstanceState == null`) and in `onNewIntent`
- [X] T011 Deep-link routing in `cm/navigation/AppNavHost.kt`: collect `DeepLinkBus.links`, parse with `AuthLinkParser`, then push `VerifyEmailKey(token)` or `ResetPasswordKey(token)`. On the auth stack the link screen replaces the top entry (keeping `EntryKey` at the bottom); on the signed-in stack it is pushed. Ignore unparsable links (depends on T007, T009, T010)

**Checkpoint**: contract and parser tests pass; a link opened with `adb shell am start -d "http://localhost:43871/verify-email?token=x"` reaches `AppNavHost` (no screen yet).

---

## Phase 3: User Story 1 - Create an account (Priority: P1) 🎯 MVP

**Goal**: new people can register; an existing address is told so with sign-in / reset choices.

**Independent Test**: register a fresh address → "check your inbox" naming it; again → "already registered" with both actions prefilled.

### Tests for User Story 1

- [X] T012 [P] [US1] Use-case tests in `ct/feature/auth/domain/RegisterUseCaseTest.kt`: sends `ClientInfo.timezoneId()`; 202 → `Accepted`; 409 → `AlreadyRegistered`; 422 → `Validation` with `AuthField.Email/Password/DisplayName`; 429 → `RateLimited(seconds)`; offline/timeout → `Offline`; the request is never retried (1 call on failure)
- [X] T013 [P] [US1] `ct/feature/auth/ui/register/RegisterReducerTest.kt` + `RegisterViewModelTest.kt`:
  - client validation blocks the request: email malformed or over 254 chars, password under 8 or over 128, password equal to email (case-insensitive), display name over 100
  - a double Submit sends once
  - `Accepted` → `NavigateCheckInbox(email)`
  - `AlreadyRegistered` → notice state; `SignInClicked` / `ResetPasswordClicked` emit `NavigateSignIn(email)` / `NavigateForgotPassword(email)`
  - server 422 lands on fields by pointer
- [X] T014 [P] [US1] `ct/feature/auth/ui/checkinbox/CheckInboxReducerTest.kt`: `GoToSignInClicked` → `NavigateSignIn(email)` for both kinds

### Implementation for User Story 1

- [X] T015 [US1] `RegisterUseCase(clientInfo, repository)` in `cm/feature/auth/domain/usecase/RegisterUseCase.kt` returning `RegisterOutcome` (field errors via existing `ProblemDetails.fieldErrors()`); register as a factory in `cm/feature/auth/di/AuthModule.kt`
- [X] T016 [US1] Register screen in `cm/feature/auth/ui/register/` (Contract, ViewModel, Route, Screen) per data-model §4:
  - fields Email, Password (`PasswordField`), Display name (optional hint)
  - `PrimaryButton` "Create account", `AppTextButton` "Already have an account? Sign in"
  - already-registered notice as `InlineAlert(Info)`, with actions "Sign in" and "Reset password"
  - tags `register.*`; validation via `ClientValidation.validateEmail/validateNewPassword/validateDisplayName`
- [X] T017 [P] [US1] CheckInbox screen in `cm/feature/auth/ui/checkinbox/`. Copy: Verification "We sent a link to %1$s. Open it on this phone to confirm your address."; Reset "If that address is registered, a link is on its way." `PrimaryButton` "Go to sign in" (`checkInbox.goToSignIn`)
- [X] T018 [US1] Wire navigation in `cm/navigation/AppNavHost.kt`:
  - `EntryEffect.NavigateRegister` → push `RegisterKey(null)`
  - `SignInEffect.NavigateRegister` → push `RegisterKey(email)`
  - Register → `CheckInboxKey(email, Verification)`, `SignInKey(prefillEmail=email)`, `ForgotPasswordKey(email)`
  - CheckInbox → `resetTo(EntryKey, SignInKey(email))`

  Register the ViewModels in `AuthModule.kt` (depends on T016, T017).
- [X] T019 [P] [US1] Strings for US1 in both `strings.xml` files: register title/button/sign-in link/display-name hint, already-registered notice + two actions, check-inbox copies + button

**Checkpoint**: US1 works on its own against the dev backend.

---

## Phase 4: User Story 2 - Verify the email address (Priority: P1)

**Goal**: mailed and pasted links verify the address; unverified people see a prompt with a throttled resend.

**Independent Test**: register, open the verify link (or paste it in dev), see success; sign in and see no prompt.

### Tests for User Story 2

- [X] T020 [P] [US2] Use-case tests in `ct/feature/auth/domain/VerifyEmailUseCaseTest.kt` + `ResendVerificationUseCaseTest.kt`:
  - `VerifyEmailUseCase` never calls `SessionManager.markEmailVerified()` (FR-011: the answer doesn't say whose address it was)
  - `VERIFICATION_TOKEN_INVALID` and 422 `/token` → `LinkInvalid`
  - resend 429 → `RateLimited(seconds)`; no retries
- [X] T021 [P] [US2] `ct/feature/auth/ui/verify/VerifyEmailReducerTest.kt` + `VerifyEmailViewModelTest.kt`:
  - `Started` → Verifying → Verified; on Verified while signed in the injected `refreshAccount` lambda is called once, and not when signed out
  - `LinkInvalid` shows "Resend email" only when signed in, "Sign in" when not
  - Offline → `Retry` re-sends once
  - the token never appears in state `toString()`
- [X] T022 [P] [US2] Home resend tests in `ct/feature/auth/ui/home/HomeViewModelTest.kt`:
  - prompt visible iff signed in and unverified
  - `ResendClicked` → Sending → `Cooldown(30)` counting down via `CooldownTick` (virtual time), then Idle
  - a second click during the cooldown sends nothing
  - 429 → `ShowMessage(RateLimited(minutes))`
- [X] T023 [P] [US2] `ct/devtools/pastelink/PasteLinkReducerTest.kt` + `PasteLinkViewModelTest.kt`: a valid verify/reset link → `SubmitLink(uri)`; anything else → `parseError`

### Implementation for User Story 2

- [X] T024 [US2] `VerifyEmailUseCase(repository)` and `ResendVerificationUseCase(repository)` in `cm/feature/auth/domain/usecase/`; factories in `AuthModule.kt`
- [X] T025 [US2] VerifyEmail screen in `cm/feature/auth/ui/verify/`:
  - the Route sends `Started` once
  - statuses Verifying (loading), Verified ("Your email is confirmed." + `PrimaryButton` "Continue"), LinkInvalid (`error_verification_link_invalid` + "Resend email" when signed in, else "Sign in"), Offline ("Try again")
  - tags `verifyEmail.*`
  - token from `VerifyEmailKey`, held only in the ViewModel
  - the ViewModel takes `refreshAccount: suspend () -> Unit`; register it in `cm/feature/account/di/AccountModule.kt` (not `AuthModule`, so `feature.auth` never imports `feature.account`) with `refreshAccount = { get<AccountRefresher>().refresh() }`, as `HomeViewModel` is registered there (spec 002)
- [X] T026 [US2] Home verify prompt:
  - `cm/feature/auth/ui/home/HomeContract.kt`: `resendState`, `ResendClicked`, `CooldownTick`, `ShowMessage(VerificationSent | RateLimited)`
  - `HomeViewModel.kt`: 30 s cooldown ticker (FR-013)
  - `HomeScreen.kt`: `InlineAlert(Warning, "Verify your email to add transactions. Check your inbox.", action "Resend email")` tagged `home.verifyPrompt` / `home.resend`, shown when `!emailVerified`
  - `HomeRoute.kt`: snackbars for the messages
  - move the catalogue-only strings `catalog_verify_alert` / `catalog_resend_email` to real keys `home_verify_prompt` / `action_resend_email`
- [X] T027 [US2] `EMAIL_NOT_VERIFIED` handling in `cm/navigation/AppNavHost.kt`: collect `ApiEvent.EmailNotVerified` and show an `AppBottomSheet` with `error_email_not_verified` and "Resend email", which calls `ResendVerificationUseCase` and shows the same messages as Home (FR-014)
- [X] T028 [P] [US2] PasteLink screen in `cm/devtools/pastelink/`:
  - `AppTextField` "Link" (`pasteLink.text`; showing the pasted link is allowed in dev only, P-09.2 v1.3.0), `PrimaryButton` "Open" (`pasteLink.open`), error "Not a verification or reset link"; the text is never logged or saved
  - `SubmitLink(uri)` → `DeepLinkBus.submit` in the Route
  - register it in `cm/devtools/di/DevToolsModule.kt`; wire `DevToolsEffect.NavigatePasteLink` → push `PasteLinkKey` in `AppNavHost.kt` (dev only, FR-008)
- [X] T029 [US2] Wire `VerifyEmailKey` in `AppNavHost.kt`: Continue → Home when signed in, else `resetTo(EntryKey, SignInKey)`; "Sign in" → `SignInKey`. The ViewModel is registered in `AccountModule.kt` (T025)
- [X] T030 [P] [US2] Strings for US2 in both files: verify statuses, Continue, verification sent, resend rate-limit, paste-link label/button/error

**Checkpoint**: US2 works on its own; a signed-in unverified person can resend and verify.

---

## Phase 5: User Story 3 - Recover a forgotten password (Priority: P2)

**Goal**: request a reset link, set a new password from it, sign in with it.

**Independent Test**: forgot → paste reset link → new password → sign in with the new password; reuse the link → "no longer works".

### Tests for User Story 3

- [X] T031 [P] [US3] Use-case tests in `ct/feature/auth/domain/ForgotPasswordUseCaseTest.kt` + `ResetPasswordUseCaseTest.kt`:
  - forgot answers `Sent` for any well-formed address (uniform answer, spec §4)
  - reset `Done` does not call `SessionManager.signOutLocally` and leaves the stored session untouched (FR-019: the server ends sessions; the next 401 signs the phone out)
  - `RESET_TOKEN_INVALID` and 422 `/token` → `LinkInvalid`; 422 `/password` → `Validation`; 429 → `RateLimited`
- [X] T032 [P] [US3] `ct/feature/auth/ui/password/ForgotPasswordReducerTest.kt`, `ForgotPasswordViewModelTest.kt`, `ResetPasswordReducerTest.kt`, `ResetPasswordViewModelTest.kt`:
  - email prefilled from the route arg
  - identical `NavigateCheckInbox(email, Reset)` for any address
  - reset enforces 8–128 before sending
  - `Done` → `NavigateSignIn(knownEmail)`; `LinkInvalid` → `RequestNewLinkClicked` → `NavigateForgotPassword`
  - a double Submit sends once

### Implementation for User Story 3

- [X] T033 [US3] `ForgotPasswordUseCase(repository)` and `ResetPasswordUseCase(repository)` (no session access, FR-019) in `cm/feature/auth/domain/usecase/`; factories in `AuthModule.kt`
- [X] T034 [US3] ForgotPassword screen in `cm/feature/auth/ui/password/forgot/`: Email field (`forgotPassword.email`, prefilled), `PrimaryButton` "Send link" (`forgotPassword.submit`); `Sent` → CheckInbox(Reset)
- [X] T035 [US3] ResetPassword screen in `cm/feature/auth/ui/password/reset/`:
  - New password (`resetPassword.password`) with "8 to 128 characters." supporting text; `PrimaryButton` "Set new password" (`resetPassword.submit`)
  - Done: `error_password_reset_done` + "Continue" (`resetPassword.continue`)
  - LinkInvalid: `error_reset_link_invalid` + "Request a new link" (`resetPassword.requestNewLink`)
  - token only in the ViewModel
- [X] T036 [US3] Wire navigation in `AppNavHost.kt`:
  - `SignInEffect.NavigateForgotPassword(email)` → push `ForgotPasswordKey(email)`; this also covers the Locked banner action
  - ForgotPassword → `CheckInboxKey(email, Reset)`
  - ResetPassword Continue → `resetTo(EntryKey, SignInKey(prefillEmail, PasswordReset))`
  - RequestNewLink → `ForgotPasswordKey(null)`

  Register the ViewModels in `AuthModule.kt` (depends on T034, T035).
- [X] T037 [P] [US3] Strings for US3 in both files: forgot title/body/button, reset title/field hint/button, request new link, continue

**Checkpoint**: all three stories work independently.

---

## Phase 6: Polish, E2E & Cross-Cutting

- [X] T038 [P] Screenshot tests in `ht/feature/auth/AuthScreensScreenshotTest.kt`, mirroring `ht/feature/account/AccountScreensScreenshotTest.kt` (EN LTR, AR RTL, EN 2× font): register (empty, errors, already registered), check inbox (both kinds), verify (verifying, verified, link invalid signed in/out), home with verify prompt, forgot, reset (editing, done, link invalid), paste link. Record with `./gradlew :shared:recordRoborazziAndroidHostTest`
- [X] T039 [P] Invariant tests for spec §4 not already covered: link secret never logged (assert Kermit output in `AuthLinkParserTest` / ViewModel tests contains no token); no auto-retry (`ct/core/network` or use-case tests: one request per failure)
- [X] T040 Extend `.specify/scripts/bash/run-e2e.sh` with seeds `unverified-user+verify-link` and `verified-user+reset-link` (contracts/ui-and-e2e.md):
  - after `seed_account`, issue two links via `artisan tinker` `app(\App\Modules\Identity\Domain\Ports\LinkRepository::class)->issue($userId, \App\Modules\Identity\Domain\Account\LinkKind::VerifyAddress|ResetPassword)`; the first becomes `E2E_OLD_LINK` (superseded), the second `E2E_LINK`
  - build the URLs as `http://localhost:$PORT/{verify-email|reset-password}?token=…`
  - update the header comment
- [X] T041 Run `/speckit-e2e 003` to generate `.maestro/003-auth-completion/` flows for all 17 scenarios (using `openLink` with `${E2E_LINK}`) and `specs/003-auth-completion/e2e-report.md`; fix flow defects, report app defects
- [X] T042 Rerun `/speckit-e2e 001` and replace the 12 `not-built` rows (US3-S1…S7, US4-S1…S5) in `specs/001-design-system-auth/e2e-report.md`, pointing at the 003 flows
- [X] T043 [P] Mark spec 001 tasks T082–T104 in `specs/001-design-system-auth/tasks.md` as superseded by 003 (one line each: "done in 003 as T0xx")
- [X] T044 [P] Arabic copy review of every new string in `values-ar/strings.xml` (native phrasing, no "token", Western digits)
- [X] T045 Full run: `./gradlew :shared:testAndroidHostTest :shared:verifyRoborazziAndroidHostTest :androidApp:assembleDevDebug`, then quickstart.md §3 manual checks 1–8

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: needs Phase 1 and blocks every story.
- **US1, US2, US3 (Phases 3–5)**: each needs only Phase 2, and they're independent of each other. US3 reuses US1's CheckInbox screen (T017); if US3 is built first, build T017 with it.
- **Polish (Phase 6)**: needs the stories it covers. T041 needs T040. T042 needs T041.

### Within each story

Tests first (they must fail), then use cases, then screens, then wiring (`AppNavHost`, `AuthModule`), then strings.

### Parallel opportunities

- T001 ∥ T002.
- T003 ∥ T004 ∥ T007 ∥ T008 ∥ T009 (different files).
- Within each story, all `[P]` test tasks run together, and the strings task runs alongside the screens.
- Once Phase 2 is done, US1, US2 and US3 can be built by different people at the same time. Everyone touches `AppNavHost.kt` and `AuthModule.kt`, so those wiring tasks (T018, T027–T029, T036) should be merged one at a time.

## Parallel Example: User Story 1

```text
T012 RegisterUseCaseTest  ∥  T013 Register reducer/VM tests  ∥  T014 CheckInbox reducer test
then T015 RegisterUseCase → T016 Register screen  ∥  T017 CheckInbox screen  ∥  T019 strings
then T018 navigation wiring
```

## Implementation Strategy

### MVP first (US1)

1. Phase 1 + Phase 2.
2. Phase 3 (US1): people can create accounts. Validate on the dev backend.
3. Stop and check: US1 E2E scenarios S1–S5.

### Incremental delivery

1. US1 → people can create accounts.
2. US2 → accounts can be verified (needed before wallets, brief C4).
3. US3 → people can recover access.
4. Phase 6 → E2E coverage replaces 001's 12 `not-built` rows.
