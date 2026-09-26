# Contract: Identity API — client consumption

**Source of truth**: `saveme-backend/docs/openapi/paths/identity.yaml`, `docs/api-conventions.md`, `docs/openapi/error-catalog.yaml`. This file records only how the app **uses** them; it never redefines a shape.

## Base

- Base URL: `AppConfig.baseUrl` + `/api/v1` (dev: `http://<LAN-IP>:<container-port>/api/v1`).
- Headers on every request: `Accept: application/json`, `X-Client-Platform: android`, `X-Client-Version: <versionName, semver>`, `Accept-Language: ar|en`, `X-Correlation-Id: <UUIDv7>`.
- `Authorization: Bearer <credential>` only on credentialed operations below.
- Success: `Envelope<T>`; failure: `ProblemDetails` (`application/problem+json`). Decode by status class, branch by `code`.

## Operations used

| Client function | HTTP | Auth | Success | Codes handled specifically → UI outcome |
|---|---|---|---|---|
| `register(email, password, displayName?, timezone)` | POST `/auth/register` | public | 202 `{status: accepted}` → CheckInbox(Verification) | VALIDATION_FAILED → field errors via pointer; RATE_LIMITED → "try again in N min" |
| `verifyEmail(token)` | POST `/auth/verify-email` | public | 200 `{email_verified: true}` → Verified; if signed in, snapshot.emailVerified = true | VERIFICATION_TOKEN_INVALID → LinkInvalid; VALIDATION_FAILED (`/token`) → LinkInvalid |
| `resendVerification()` | POST `/auth/resend-verification` | bearer | 202 → "Email sent" snackbar, 60 s button cooldown | RATE_LIMITED → wait message; UNAUTHENTICATED → session ended |
| `signIn(email, password, device)` | POST `/auth/login` | public | 200 `SignInResult` → store session + snapshot → SignedIn; `deletion_cancelled` → notice | INVALID_CREDENTIALS → uniform message, clear password; ACCOUNT_LOCKED (423) → Locked(minutes from Retry-After); VALIDATION_FAILED → field errors; RATE_LIMITED → wait message |
| `signOut()` | POST `/auth/logout` | bearer | 204 | any failure incl. offline/401 → still clear locally (FR-024) |
| `forgotPassword(email)` | POST `/auth/forgot-password` | public | 202 → CheckInbox(Reset) | VALIDATION_FAILED → email field; RATE_LIMITED → wait message |
| `resetPassword(token, password)` | POST `/auth/reset-password` | public | 204 → clear local session → SignIn(reason = PasswordReset) | RESET_TOKEN_INVALID → LinkInvalid; VALIDATION_FAILED → password field or LinkInvalid for `/token` |

## Codes handled on every operation

| Code | Status | Retry class | UI |
|---|---|---|---|
| CLIENT_VERSION_UNSUPPORTED | 426 | never | blocking UpdateRequired screen with `minimum_version` |
| CLIENT_VERSION_REQUIRED / CLIENT_VERSION_INVALID | 400 | never | generic error + request id (a client bug) |
| UNAUTHENTICATED | 401 | never | on credentialed call: clear session → SignIn(SessionEnded) |
| EMAIL_NOT_VERIFIED | 403 | never | verify prompt with Resend (FR-029) |
| RATE_LIMITED | 429 | automatic* | wait message using `retry_after_seconds` |
| SERVICE_UNAVAILABLE / INTERNAL_ERROR | 503 / 500 | automatic* | "Something went wrong, try again" + request id |
| MALFORMED_JSON, NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, UNKNOWN_FIELD | 400/406/415/422 | never | generic error + request id (a client bug) |
| any unknown code | — | never | generic error + request id |
| transport failure (no response) | — | — | Offline banner; keep form input |

\* Not auto-retried on auth POSTs (research R-04); shown to the person with a manual retry.

## Contract tests (MockEngine, `commonTest`)

For each operation: request method/path/headers/body match the table above and the OpenAPI schema (no extra members); each listed code decodes to the stated `ApiErrorCode` and UI outcome; unknown response members are ignored.
