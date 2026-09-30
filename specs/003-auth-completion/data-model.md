# Data Model: Auth Completion

No persisted data is added (spec §3). This file fixes the transient models, the request/response
shapes, and each screen's MVI contract. Where spec 001's data-model already defines a model, it
is referenced, not repeated.

## 1. Transient models

| Model | Fields | Rules |
|---|---|---|
| `AuthLink` (001) | `VerifyEmail(token)` · `ResetPassword(token)` | token 1–128 chars; parsed only by `AuthLinkParser`; never stored or logged (FR-009) |
| `AuthLinkParser` | `linkHost`, `linkPort: Int?`, `scheme` | accepts `{scheme}://{linkHost}[:{linkPort}]/verify-email?token=…` and `/reset-password?token=…` (research R-03); trims pasted whitespace |
| `AppConfig` (existing) | **+ `linkPort: Int?`** | dev: from `devApiHost`'s port; staging/prod: null |

## 2. Wire shapes (mirror identity.yaml exactly, P-02.2)

| DTO | Members | Notes |
|---|---|---|
| `RegisterRequest` | `email`, `password`, `display_name?`, `timezone` | `display_name` omitted when blank (`explicitNulls = false`) |
| `TokenRequest` | `token` | verify-email |
| `VerifyEmailDataDto` | `email_verified` | always `true` on 200 |
| `EmailRequest` | `email` | forgot-password |
| `ResetPasswordRequest` | `token`, `password` | reset-password |
| `AcceptedDto` (existing) | `status` | the 202 answers |

## 3. Domain outcomes (`feature/auth/domain/model/Outcomes.kt`)

| Outcome | Cases |
|---|---|
| `RegisterOutcome` | `Accepted` · **`AlreadyRegistered`** (R-02) · `Validation(fields)` · `RateLimited(seconds?)` · `Offline` · `Error(code, requestId)` |
| `VerifyOutcome` | `Verified` · `LinkInvalid` · `Offline` · `Error(code, requestId)` |
| `ResendOutcome` | `Sent` · `RateLimited(seconds?)` · `Offline` · `Error(code, requestId)` |
| `ForgotOutcome` | `Sent` · `Validation(fields)` · `RateLimited(seconds?)` · `Offline` · `Error(code, requestId)` |
| `ResetOutcome` | `Done` · `LinkInvalid` · `Validation(fields)` · `RateLimited(seconds?)` · `Offline` · `Error(code, requestId)` |

`VerifyEmailUseCase` only calls the API. On `Verified` while signed in, the VerifyEmail ViewModel
calls an injected `refreshAccount` lambda (wired in Koin to the account feature's
`AccountRefresher.refresh()`, which re-reads `GET /me` and updates the summary). The verify
answer doesn't say whose address it was, so the flag is never set from it (FR-011). A lambda keeps
`feature.auth` free of an import of `feature.account` (P-10.4; account already depends on auth).
`ResetPasswordUseCase` does not touch
the local session on `Done` (FR-019): the server ended the account's sessions, and the next
credentialed request's 401 runs the existing session-ended flow.

## 4. Screens (MVI, P-16)

Each screen has a Contract (State, Intent, Effect, Change + pure `reduce`), a ViewModel on
`MviViewModel`, a Route, and a stateless Screen.

| Screen | State | Intents | Effects |
|---|---|---|---|
| Register | email, password, passwordVisible, displayName, fieldErrors, submitting, notice (`AlreadyRegistered` \| banner) | EmailChanged, PasswordChanged, DisplayNameChanged, TogglePasswordVisibility, Submit, SignInClicked, ResetPasswordClicked | NavigateCheckInbox(email), NavigateSignIn(email?), NavigateForgotPassword(email) |
| CheckInbox | email, kind (`Verification` \| `Reset`) | GoToSignInClicked | NavigateSignIn(email) |
| VerifyEmail | status (`Verifying` \| `Verified` \| `LinkInvalid` \| `Offline` \| `Error`), signedIn | Started, Retry, ResendClicked, ContinueClicked, SignInClicked | NavigateHome, NavigateSignIn |
| ForgotPassword | email (prefilled), emailError, submitting, banner | EmailChanged, Submit | NavigateCheckInbox(email, Reset) |
| ResetPassword | password, passwordVisible, fieldError, submitting, status (`Editing` \| `Done` \| `LinkInvalid`), knownEmail? | PasswordChanged, TogglePasswordVisibility, Submit, RequestNewLinkClicked, ContinueClicked | NavigateSignIn(email?), NavigateForgotPassword |
| PasteLink (dev) | text, parseError | TextChanged, OpenClicked | SubmitLink(uri) |
| Home (existing) | **+ resendState (`Idle` \| `Sending` \| `Cooldown(seconds)`), resendMessage?** | **+ ResendClicked, CooldownTick** | **+ ShowMessage(VerificationSent \| RateLimited(min))** |

Tokens enter through route arguments (`VerifyEmailKey(token)`, `ResetPasswordKey(token)`) and
live only in the ViewModel.

## 5. Navigation keys (`navigation/Routes.kt`)

`RegisterKey(prefillEmail?)`, `CheckInboxKey(email, kind)`, `VerifyEmailKey(token)`,
`ForgotPasswordKey(prefillEmail?)`, `ResetPasswordKey(token)`, `PasteLinkKey`.

## 6. Error pointer mapping (001, unchanged)

`/email` → Email, `/password` → Password, `/display_name` → DisplayName, `/token` → link-invalid
state; anything else → form banner.
