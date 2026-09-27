# Data Model: Design System Components & Authentication Flows

**Feature**: [spec.md](spec.md) · **Research**: [research.md](research.md)

The server owns User, Profile and Device Session (see `saveme-backend/docs/glossary.md`). Everything below is client-side: local persistence, in-memory state, and the wire shapes the client sends and reads. Wire shapes mirror `saveme-backend/docs/openapi/paths/identity.yaml` exactly; that file wins on any disagreement.

## 1. Persisted (on device)

### StoredSession — `core.storage.SecureStore` (encrypted)

| Field | Type | Rule |
|---|---|---|
| credential | String | `{session_id}\|{secret}`, pattern `^[0-9a-f-]{36}\|[A-Za-z0-9]{64}$`; never logged, never shown |
| sessionId | Uuid | the `session.id` returned at sign-in |
| issuedAt | Instant | device clock at sign-in |

Lifecycle: `absent → present` on sign-in success; `present → absent` on sign-out, any 401 on a credentialed call, or reset-password success. No update in place — re-sign-in replaces the whole record.

### DeviceIdentity — private DataStore (backup-excluded)

| Field | Type | Rule |
|---|---|---|
| deviceId | String | random UUID on first launch; matches `^[A-Za-z0-9._:-]{1,64}$`; never shown |

Lifecycle: created once; lives until uninstall or data clear.

### AccountSnapshot — private DataStore (cleared on sign-out)

| Field | Type | Source |
|---|---|---|
| userId | Uuid | `account.id` |
| email | String | `account.email` |
| emailVerified | Boolean | `account.email_verified`; set true locally after verify-email 200 |
| state | `Active` \| `PendingDeletion` | `account.state` |
| deletionScheduledFor | Instant? | `account.deletion_scheduled_for` |
| plan | String | `entitlements.plan` (display only) |
| features | Map<String, FeatureValue> | `entitlements.features`; `FeatureValue` = Bool \| Int |

Kept so relaunch (SC-006) renders the signed-in area and the verify prompt without a network call.

## 2. In-memory state

### AuthState — `feature.auth.SessionManager`

```text
Unknown ──(store read)──▶ SignedOut(reason?) ◀──(sign-out | 401 | reset)── SignedIn(account)
                     └──▶ SignedIn(account) ──(verify success)──▶ SignedIn(account.emailVerified = true)
```

`SignedOut.reason`: `None` | `SessionEnded` (401) | `PasswordReset` | `UserSignedOut` — picks the explanation text on the sign-in screen (US2-5).

### Per-screen MVI contracts (constitution P-16)

Each screen has `<Screen>Contract.kt` with an immutable `State`, a sealed `Intent` (everything the user or lifecycle can do), a sealed `Effect` (one-shot: navigation, snackbar, open URL) and a pure `reduce(state, change)`. Navigation always leaves the store as an `Effect`, never as state.

| Screen | State fields | Intents |
|---|---|---|
| SignIn | email, password, passwordVisible, emailError?, submitting, banner? (InvalidCredentials / Locked(minutes) / Offline / Generic(requestId)), deletionCancelledNotice | EmailChanged, PasswordChanged, ToggleVisibility, Submit, ForgotPassword, CreateAccount |
| Register | email, password, displayName, fieldErrors: Map<Field, ErrorCode>, submitting, banner? | …Changed, Submit, GoToSignIn |
| CheckInbox | email, kind (`Verification` \| `Reset`) | OpenMailApp, GoToSignIn |
| VerifyEmail | status: `Verifying` \| `Verified` \| `LinkInvalid` \| `Error(requestId)`, canResend | Retry, Resend, Continue |
| ForgotPassword | email, emailError?, submitting, banner? | EmailChanged, Submit |
| ResetPassword | token, password, passwordVisible, fieldError?, submitting, status (`Editing` \| `Done` \| `LinkInvalid`) | PasswordChanged, Submit, RequestNewLink |
| SignedInHome (placeholder) | email, emailVerified, resendCooldownUntil?, banner? | Resend, SignOut, ConfirmSignOut |
| PasteLink (dev) | text, parseError | TextChanged, Open |
| Entry | isDebugTools | SignInClicked, CreateAccountClicked, DevToolsClicked |
| UpdateRequired | minimumVersion | OpenStoreClicked |
| ComponentCatalog (dev) | language (en/ar), fontScale (1.0/1.5/2.0) | LanguageSelected, FontScaleSelected, Back |
| DevTools (dev) | — | ComponentsClicked, PasteLinkClicked |

Effects per screen (examples): SignIn → `NavigateHome`, `NavigateForgotPassword(email)`, `NavigateRegister`, `NavigateUpdateRequired(minimumVersion)`; Register → `NavigateCheckInbox(email)`, `NavigateSignIn(email)`; ResetPassword → `NavigateSignIn(email?)`, `NavigateForgotPassword`; Home → `ShowSnackbar(message)`. Screens that have nothing to hold keep an empty-ish `State` and exist only to turn clicks into `Effect`s — still one store per screen (P-16.1).

### ApiEvents — `core.network` (application scope)

`SharedFlow<ApiEvent>`: `SessionEnded` (401 on a credentialed call) · `UpdateRequired(minimumVersion)` (426) · `EmailNotVerified` (403 EMAIL_NOT_VERIFIED). Emitted by the HTTP layer; observed by `SessionManager` (domain) and `AppNavHost` (navigation). `core` never imports a feature (P-10.7).

`ClientValidation` (shared by forms, FR-019): email well-formed and ≤ 254; password 8–128 and not equal (case-insensitive, trimmed) to email; display name blank or 1–100.

### AuthLink — parsed deep link

| Variant | Field | Source |
|---|---|---|
| VerifyEmail | token: String (1–128) | `https://{linkHost}/verify-email?token=…` (path TBC, research R-12) |
| ResetPassword | token: String (1–128) | `https://{linkHost}/reset-password?token=…` |

Anything else parses to `null`.

## 3. Wire shapes (client DTOs)

Requests (encode only these members — extra members are 422 UNKNOWN_FIELD):

| DTO | Members |
|---|---|
| RegisterRequest | email, password, display_name?, timezone? (device IANA zone) |
| SignInRequest | email, password, device{session_id, device_id, name, platform=`android`, app_version} |
| TokenRequest | token |
| EmailRequest | email |
| ResetPasswordRequest | token, password |

Responses (decode with unknown keys ignored):

| DTO | Members |
|---|---|
| Envelope<T> | data: T, meta: object |
| AcceptedData | status = `accepted` |
| VerifyEmailData | email_verified = true |
| SignInResult | credential, session: DeviceSessionDto, account: AccountDto, entitlements: EntitlementSummaryDto, deletion_cancelled |
| ProblemDetails | type, title, status, detail, code, request_id, instance?, errors?[{pointer, code, …}], retry_after_seconds?, minimum_version? |

`errors[].pointer` → form field: `/email` → Email, `/password` → Password, `/display_name` → DisplayName, `/token` → link invalid screen; anything else → form banner.

## 4. Design tokens — `designsystem.tokens`

| Group | Members (values in spec FR-001–FR-004) |
|---|---|
| AppColors | primary, primaryContainer, onPrimaryContainer, background, surface, surfaceContainer (sunken), surfaceContainerHigh (sunken-2), outline, outlineVariant, onSurface (ink), onSurfaceVariant (ink-2), caption, error, errorContainer; mapped onto a Material 3 `ColorScheme` |
| MoneyColors (CompositionLocal) | positive/positiveContainer, pending/pendingContainer, over/overContainer |
| AppTypography | numeralHero, numeralL, headline, title, itemTitle, body, label, caption; Latin and Arabic variants |
| Spacing | xxs 4, xs 8, s 12, m 16, l 20, xl 24, xxl 32; gutter 16, sectionGap 24 |
| Shapes | control 10, tile 14, card 18, sheet 22 (top), hero 22 |
| Elevation | flat, raised, overlay (shadow specs from Main board) |
| Sizes | touchTarget 48, button 52, field 52, fab 56, topBar 64, row 64/72, navBar 80, chip 36 |
