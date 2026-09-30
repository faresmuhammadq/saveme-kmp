# Contract: Identity API client (auth completion)

Source of truth: `saveme-backend/docs/openapi/paths/identity.yaml` at `saveme-backend@56b44fb`.
All paths under `/api/v1`. No operation here is retried automatically (001 R-04, P-02.6).

| Client method | Request | Session | Success | Handled codes → client result |
|---|---|---|---|---|
| `register(email, password, displayName?, timezone)` | POST `auth/register` · `RegisterRequest` | public | 202 → `Accepted` | EMAIL_ALREADY_REGISTERED (409) → `AlreadyRegistered` · VALIDATION_FAILED → field errors by pointer · RATE_LIMITED → wait minutes |
| `verifyEmail(token)` | POST `auth/verify-email` · `TokenRequest` | public | 200 `{email_verified:true}` → `Verified` | VERIFICATION_TOKEN_INVALID → `LinkInvalid` · VALIDATION_FAILED `/token` → `LinkInvalid` · RATE_LIMITED |
| `resendVerification()` | POST `auth/resend-verification` | bearer | 202 → `Sent` | RATE_LIMITED → wait minutes · UNAUTHENTICATED → session ended (existing event) |
| `forgotPassword(email)` | POST `auth/forgot-password` · `EmailRequest` | public | 202 → `Sent` (same for any address) | VALIDATION_FAILED `/email` · RATE_LIMITED |
| `resetPassword(token, password)` | POST `auth/reset-password` · `ResetPasswordRequest` | public | 204 → `Done` | RESET_TOKEN_INVALID → `LinkInvalid` · VALIDATION_FAILED `/password` → field, `/token` → `LinkInvalid` · RATE_LIMITED |

Every call also handles the shared refusals generically, as spec 001 does: 426 → update required,
`EMAIL_NOT_VERIFIED` → verify prompt event, offline/timeout → "try again", 5xx → generic with
request id.

## Contract tests (MockEngine, `AuthTestEnv`)

For each row: URL suffix, method, exact body keys (no extra members; `display_name` absent when
blank), Authorization header present only for `resendVerification`, and one test per handled
code asserting the client result.

## New client error code

`EMAIL_ALREADY_REGISTERED` → retry class `Never`; copy "This email already has an account." in
English and Arabic (FR-006).
