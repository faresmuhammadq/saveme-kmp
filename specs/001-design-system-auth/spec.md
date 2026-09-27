# Feature Specification: Design System Components & Authentication Flows

**Feature Branch**: `001-design-system-auth` (spec directory; work currently on `main`)

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "build ui compose reusable components from this artifact https://claude.ai/artifact/T312v5J2QNbUMiVwFQep3c , then build auth flows screens and integrate the api the docs are here @../saveme-backend/docs/"

**Backend contract**: `saveme-backend@5e5e969`

**Sources**:
- Design: "Save Me Mobile Design System" canvas — boards *Mobile foundations* (tokens, type, spacing, shape, money, RTL) and *Mobile components*.
- API contract: `saveme-backend/docs/openapi/paths/identity.yaml`, `docs/api-conventions.md`, `docs/security-baseline.md`, `docs/openapi/error-catalog.yaml`, `docs/glossary.md`.

## Clarifications

### Session 2026-09-26

- Q: Is live sign-in from iOS in scope while the API accepts only `android`? → A: No. iOS won't work yet; Android is the only live-API target for this feature.
- Q: Which host do the verification and reset links in the emails use? → A: The same host as the API base URL; tapping one in the phone's browser or mail app should open the app.
- Q: What is the API base URL for the development environment? → A: The dev machine's LAN IP plus the backend container's published port (e.g. `http://192.168.x.x:<port>`), configurable per environment.
- Q: In the dev environment, how should the verification and reset links open the app? → A: Verified HTTPS links in staging and production; in dev the app also claims the IP-based links (Android shows a chooser), plus a dev-only "paste link" screen as a fallback.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Shared design foundation and component library (Priority: P1)

The team builds every future Save Me screen from one shared set of visual foundations (colour roles, type scale, spacing, shape, elevation) and reusable components that match the design canvas, in English (left-to-right) and Arabic (right-to-left). A person using the app sees one consistent look on every screen.

**Why this priority**: Every auth screen in this feature, and every later feature, is assembled from these parts. Building screens first would mean rebuilding them.

**Independent Test**: Open a component catalogue screen in the app that shows every component in each of its states, in English and Arabic, and compare side-by-side with the design canvas.

**Acceptance Scenarios**:

1. **Given** the component catalogue in English, **When** a reviewer compares each component with the *Mobile components* board, **Then** colours, sizes, corner radii, type sizes and spacing match the board's stated values.
2. **Given** the device language is Arabic, **When** the catalogue opens, **Then** the whole layout mirrors (navigation, chevrons, back arrows, progress fill direction), Arabic text uses the Arabic face at line height 1.6 with no letter-spacing or uppercase, and numbers stay Western digits rendered left-to-right.
3. **Given** the system font scale is set to 200%, **When** any component renders, **Then** no text is clipped or overlapping; amounts shrink to fit before truncating.
4. **Given** a primary button in its disabled or loading state, **When** a person taps it, **Then** nothing is submitted and the state is visible without relying on colour alone.
5. **Given** a money amount with currency EGP or KWD, **When** it is shown, **Then** it has 2 or 3 fraction digits respectively, uses the true minus sign for negatives, and uses tabular digits.

---

### User Story 2 - Sign in on this device (Priority: P1)

A person with an account enters their email address and password and lands in the app, signed in on this phone. The app remembers them between launches until they sign out or the session ends.

**Why this priority**: Nothing else in the app works without a signed-in session.

**Independent Test**: With a known account on the backend, sign in, force-close the app, reopen it, and confirm the person is still signed in and lands past the auth screens.

**Acceptance Scenarios**:

1. **Given** a registered address and the right password, **When** the person submits, **Then** they are signed in and taken to the app's home destination.
2. **Given** a wrong password or an unregistered address, **When** the person submits, **Then** the same message is shown in both cases ("email or password is incorrect") and the password field is cleared.
3. **Given** five consecutive failed attempts, **When** the person tries again, **Then** the app says the account is temporarily locked and shows how many minutes to wait, distinct from the wrong-password message, and offers "Forgot password".
4. **Given** a signed-in person, **When** they relaunch the app, **Then** they skip the auth screens without re-entering credentials.
5. **Given** the stored session was ended elsewhere (signed out remotely, password changed, expired), **When** the app's next request is refused as unauthenticated, **Then** local credentials are removed and the person returns to sign-in with a short explanation.
6. **Given** an account pending deletion, **When** the person signs in, **Then** they are signed in and told that the scheduled deletion was cancelled.
7. **Given** no connection, **When** the person submits, **Then** the app says it is offline and keeps the typed email.

---

### User Story 3 - Create an account and verify the email address (Priority: P2)

A new person creates an account with email, password and optional display name, is told to check their inbox, and confirms the address by opening the link from the mail, which opens the app and marks the address verified. Until verified, they can use the app but see a prompt to verify, with a way to resend the mail.

**Why this priority**: Required for growth, but existing test accounts let P1 ship and be tested first.

**Independent Test**: Register a fresh address, open the mailed link on the phone, and confirm the app shows the address as verified and the verify prompt disappears.

**Acceptance Scenarios**:

1. **Given** the registration form, **When** the person submits a valid email and a password of 8–128 characters that is not their email, **Then** the app shows a "check your inbox" confirmation naming the address, identical whether or not the address was already registered.
2. **Given** a password shorter than 8 characters or equal to the email, **When** the person tries to submit, **Then** the field shows an inline error and nothing is sent.
3. **Given** the server rejects a field, **When** the response arrives, **Then** the error appears under the matching field, not as a generic alert.
4. **Given** the person opens the verification link on the phone, **When** the app opens from it, **Then** it confirms the address and shows success; this works whether or not they are signed in.
5. **Given** an expired, used or superseded link, **When** the app opens from it, **Then** it explains the link no longer works and offers to send a new one (if signed in) or to sign in first.
6. **Given** a signed-in, unverified person, **When** they tap "Resend email", **Then** a new mail is requested and the button is briefly disabled; a fourth request within an hour shows a "try again later" message with the wait time.
7. **Given** a signed-in, unverified person attempts an action the server refuses with "email not verified", **Then** the app shows the verify prompt with "Resend email", never a bare denial.

---

### User Story 4 - Recover a forgotten password (Priority: P2)

A person who forgot their password asks for a reset link, opens it from the mail on the phone, chooses a new password, and signs in with it.

**Why this priority**: Without it a locked-out or forgetful person cannot get back in; also the only way to clear a lock early.

**Independent Test**: Request a reset for a known address, open the link, set a new password, sign in with the new password.

**Acceptance Scenarios**:

1. **Given** the forgot-password screen, **When** the person submits any well-formed address, **Then** the app shows "if that address is registered, a link is on its way", whether or not it is registered.
2. **Given** the person opens the reset link, **When** the app opens from it, **Then** it shows a new-password screen; after a valid new password is submitted, it confirms and takes the person to sign-in with the email prefilled when known.
3. **Given** the link is expired (older than 60 minutes), used, or superseded, **When** the person submits a new password, **Then** the app says the link no longer works and offers to request a new one.
4. **Given** a successful reset, **When** it completes, **Then** any session held on this phone is treated as ended and the person must sign in again.
5. **Given** a fourth reset request for the same address within an hour, **When** submitted, **Then** the app shows a "try again later" message with the wait time.

---

### User Story 5 - Sign out (Priority: P3)

A signed-in person signs out of this phone. Their other devices stay signed in.

**Why this priority**: Needed for shared phones and testing, but not blocking first use.

**Independent Test**: Sign in, sign out, relaunch: the sign-in screen appears and the old credential no longer works against the backend.

**Acceptance Scenarios**:

1. **Given** a signed-in person, **When** they confirm sign-out, **Then** the server session ends, local credentials and cached account data are removed, and the sign-in screen shows.
2. **Given** no connection at sign-out, **When** the person confirms, **Then** local credentials are still removed and the person is signed out on this phone; the app does not keep them signed in.

---

### Edge Cases

- App version below the server's minimum: every auth request is refused as "update required"; the app shows a blocking update prompt naming the required version.
- Server rate limit or temporary unavailability: the app shows a "try again" message, honours any wait time the server gives, and never loops retries without backoff.
- Server returns a message in English though the app is in Arabic: the app shows its own localised copy chosen by error code, never the server's message text.
- Verification or reset link opened when the app is not installed: the link falls back to the web (out of scope here, backend/landing-page concern).
- Dev link opened in the browser instead of the app (unverified IP host): the tester copies it into the dev-only "paste link" screen, which opens the same verify or reset screen.
- Pasted text that is not a recognised verification or reset link: the paste screen says so and routes nowhere.
- Reset link opened while a different account is signed in on the phone: the reset proceeds; afterwards the phone's session is treated as ended and the person signs in again.
- Person rotates the device or the app is backgrounded mid-form: typed values (except passwords) survive.
- Double-tap on submit: only one request is sent.
- Sign-in retried from the same install: the app reuses the same device session identity, so a retry never creates a second session.
- Very long display names or email addresses: fields scroll; nothing overflows the screen.
- Unknown error code from the server: generic "something went wrong" message plus a reference id the person can quote to support.

## Requirements *(mandatory)*

### Functional Requirements

**Foundations & components**

- **FR-001**: The app MUST define one shared set of colour roles matching the *Mobile foundations* board: primary #4C6FD6, primary container #E9EEFC, on-primary-container / pressed #3553B0, background #EDF0F5, surface #FFFFFF, sunken #F4F6FA, sunken-2 #E9EDF3, outline #8A93A2, outline-variant #E3E7EE, ink #161A22, ink-2 #5B6472, caption #6F7886, error #A9503A / error container #F7E9E3.
- **FR-002**: The app MUST provide an extended money-status palette — positive (#2C7A5B on #E6F3EC), pending (#8F5E12 on #FBF1DE), over (#A9503A on #F7E9E3) — available to every component.
- **FR-003**: The app MUST define the type scale: numeral hero 40/800, numeral large 24/800, headline 26/800, title 18/700, item title 16/600, body 15/500, label 14/600, caption 12/600; Latin face Hanken Grotesk, Arabic face IBM Plex Sans Arabic; tabular numerals throughout.
- **FR-004**: The app MUST define spacing steps (4, 8, 12, 16, 20, 24, 32), screen gutter 16, card padding 16–20, section gap 24; shape radii control 10, tile 14, card 18, sheet 22 (top corners); and three elevations (flat, raised, overlay).
- **FR-005**: Every interactive element MUST have a touch target of at least 48×48.
- **FR-006**: The app MUST provide these reusable components, each with the variants and states shown on the *Mobile components* board:
  - Buttons: primary (52 high, full width), secondary/tonal, text, destructive, icon button (48), floating action button (56, extended form); states enabled, pressed, disabled, loading.
  - App bars: top app bar (64), bottom navigation bar (80, pill indicator 56×30 on primary container).
  - Selection: segmented control (48), filter chip (36 visual, 48 touch), switch/settings row with title and supporting text.
  - Text fields (52): label, value, supporting text, error text, password variant with show/hide, disabled state.
  - Amount display and money text honouring per-currency fraction digits, the U+2212 minus, income in positive colour, expenses in neutral ink (never red), negative balances in error colour, liabilities shown with "owed".
  - Cards: balance hero (radius 22, solid primary, no gradient), wallet card with type icon tile.
  - Lists: transaction row (72) with pending-sync dot, date header with day total, member row with role pill (Owner, Editor, Viewer).
  - Budget progress bar: positive under 80%, pending 80–100%, over above 100%, percentage always written.
  - Feedback: sync banner, inline alert (info, warning, error), snackbar with optional action (e.g. Undo).
  - Modal bottom sheet (top radius 22, drag handle, scrim ink at 38%).
  - Empty state (title, body, optional action) and loading skeleton.
- **FR-007**: Every component MUST render correctly in both left-to-right and right-to-left layouts and at system font scales up to 200%.
- **FR-008**: The app MUST include a component catalogue screen, reachable in development builds only, showing every component in every state in both languages.
- **FR-009**: Only a light theme is required; colour roles MUST be defined so a dark theme can later redefine the same roles without changing components.

**Authentication — general**

- **FR-010**: The app MUST provide these auth screens built only from the FR-006 components: Welcome/entry, Sign in, Create account, Check your inbox, Verify email result, Forgot password, Reset password, and a signed-in placeholder home with a Sign out action and verify-email prompt.
- **FR-011**: The app MUST decide on launch whether a stored session exists and route to the signed-in area or to the entry screen, with no visible flash of the wrong screen.
- **FR-012**: The app MUST store the session credential only in the platform's secure, encrypted storage, never in plain preferences, logs, crash reports or screenshots-visible UI.
- **FR-013**: Every request MUST identify the client platform, the app version, and the person's language (Arabic or English), and MUST send a correlation identifier.
- **FR-014**: The app MUST choose user-facing error copy by the server's error code, never by its message text, with localised copy for at least: invalid credentials, account locked, email not verified, verification link invalid, reset link invalid, validation failed, rate limited, unauthenticated, client version unsupported, service unavailable, internal error, and a generic fallback.
- **FR-015**: Field-level validation errors from the server MUST be shown beneath the field they point to.
- **FR-016**: Unexpected errors MUST show the server-provided request reference so the person can quote it to support.
- **FR-017**: Retries MUST follow the code's retry class: automatic retry with backoff only for retryable codes, honouring any wait time the server returns; never auto-retry sign-in or password submissions.
- **FR-018**: On "update required", the app MUST block auth actions with an update prompt naming the minimum version.
- **FR-019**: All auth form submissions MUST be disabled while one is in flight, and client-side validation (well-formed email, password 8–128 characters, password not equal to email) MUST run before any request.

**Sign in / sign out**

- **FR-020**: Sign-in MUST send the email, password and a device description (a per-sign-in session identity generated on the phone, a stable per-install device identity, device name, platform, app version). A retry of the same attempt MUST reuse the same session identity.
- **FR-021**: On success the app MUST keep the credential, the account summary (email, verified flag, state) and the plan's feature flags, and show a notice when the sign-in cancelled a pending deletion.
- **FR-022**: Wrong password and unknown address MUST show the same message; a lock MUST show a distinct message with the remaining minutes.
- **FR-023**: Any request refused as unauthenticated while signed in MUST clear local credentials and return to sign-in with an explanation.
- **FR-024**: Sign-out MUST end the server session when reachable and MUST always clear local credentials and cached account data.

**Registration & verification**

- **FR-025**: Registration MUST collect email, password and optional display name (1–100 characters), and send the device's timezone.
- **FR-026**: After registration the app MUST show the same "check your inbox" confirmation regardless of outcome and offer to go to sign-in with the email prefilled.
- **FR-027**: The app MUST open verification and reset links from mail directly into the matching in-app screen, carrying the link's secret, whether or not the person is signed in. Links use the same host as the environment's API base URL.
- **FR-027a**: The API base URL (and therefore the link host) MUST be set per environment at build time, not hard-coded in screens. The development environment points at the dev machine's LAN IP and the backend container's port over plain HTTP; plain HTTP MUST be allowed in development builds only, never in release builds.
- **FR-027b**: In staging and production, mail links MUST open the app directly as verified links for that environment's HTTPS host. In development builds, the app MUST also accept the IP-based links (opening via the system chooser is acceptable) and MUST offer a development-only "paste link" screen that routes a pasted verification or reset link into the same in-app screen as tapping it. The paste screen MUST NOT exist in release builds.
- **FR-028**: A signed-in, unverified person MUST see a persistent verify-email prompt with "Resend email"; the verified state MUST update after successful verification without re-signing in.
- **FR-029**: An "email not verified" refusal from any action MUST surface the verify prompt with resend.

**Password recovery**

- **FR-030**: Forgot-password MUST show the same confirmation for any well-formed address.
- **FR-031**: Reset-password MUST accept a new password meeting FR-019 rules, confirm success, clear any local session, and route to sign-in.
- **FR-032**: Invalid, expired or superseded reset or verification links MUST show a recoverable screen offering a fresh request.

### Key Entities *(include if feature involves data)*

- **Stored session (client-side)**: the credential issued at sign-in, the session identity it belongs to, and when it was issued. Lives only in secure storage; removed on sign-out, unauthenticated refusal, or password reset.
- **Device identity (client-side)**: a stable identifier per app installation, sent at sign-in, never shown to the person.
- **Account summary (client cache)**: email, verified flag, account state (active or pending deletion), scheduled deletion date. Read-only copy of the server's User.
- **Entitlement summary (client cache)**: plan label and feature flags from sign-in; later features gate on the flags, never the label.
- **Design tokens**: colour roles, type scale, spacing, shape and elevation values shared by all components.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Gives the Save Me mobile app its visual foundation and component library, drawn from the approved design canvas, and lets people create an account, verify their address, sign in on their phone, recover a forgotten password and sign out, against the live Identity API. Serves every Save Me user in Arabic and English, and every later feature team that builds screens.

### 2. Glossary terms

Terms from `saveme-backend/docs/glossary.md`: **User** (Identity), **Profile** (display name, locale, timezone), **Device Session** ("devices signed in"; "token" and "PAT" are banned as product vocabulary — UI copy says "session" or "device"). Plan/Entitlement terms from the glossary's *Plans & Billing* section are referenced but not owned here. No new glossary terms introduced; this client repository has no glossary of its own yet.

### 3. Data model

Client-only; the server owns User, Profile and Device Session.

| Entity | Fields | Scope | Lifecycle | Owner |
|---|---|---|---|---|
| Stored session | credential, session id, issued at | this install | absent → present → cleared | auth module |
| Device identity | device id | this install | created on first launch, kept until uninstall | auth module |
| Account summary | user id, email, email verified, state, deletion scheduled for | signed-in user | refreshed on sign-in and verification; cleared on sign-out | auth module |
| Entitlement summary | plan label, feature flags | signed-in user | set at sign-in; cleared on sign-out | auth module |
| Design tokens | colours, type, spacing, shape, elevation | app | static | design system module |

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Credential never written outside secure storage | security-baseline: Device sessions | auth storage unit test |
| Error copy chosen by code, never by message text | api-conventions: Branch on `code` | error-mapping unit test |
| Wrong password and unknown address show identical copy | security-baseline: Uniform answers | sign-in view-model test |
| Unauthenticated refusal clears local session | security-baseline: revocation on next request | auth session repository test |
| Sign-in retries reuse the same session identity | identity.yaml `signIn` idempotency | sign-in request builder test |
| Amount fraction digits come from the currency, never fixed at 2 | ADR-0003 money minor units | money formatter unit test |
| Every component renders in RTL and at 200% font scale | design board: Arabic & RTL, type scale note | component screenshot tests |
| Touch targets ≥ 48 | design board: Touch & spacing | component accessibility test |

### 5. API

All under `/api/v1`, defined in `saveme-backend/docs/openapi/paths/identity.yaml`.

| Operation | Method & path | Codes the app handles |
|---|---|---|
| Register | POST `/auth/register` | 202; VALIDATION_FAILED, RATE_LIMITED, CLIENT_VERSION_UNSUPPORTED |
| Verify email | POST `/auth/verify-email` | 200; VERIFICATION_TOKEN_INVALID, VALIDATION_FAILED, RATE_LIMITED |
| Resend verification | POST `/auth/resend-verification` | 202; UNAUTHENTICATED, RATE_LIMITED |
| Sign in | POST `/auth/login` | 200; INVALID_CREDENTIALS, ACCOUNT_LOCKED, VALIDATION_FAILED, RATE_LIMITED, CLIENT_VERSION_UNSUPPORTED |
| Sign out | POST `/auth/logout` | 204; UNAUTHENTICATED (treated as already signed out) |
| Forgot password | POST `/auth/forgot-password` | 202; VALIDATION_FAILED, RATE_LIMITED |
| Reset password | POST `/auth/reset-password` | 204; RESET_TOKEN_INVALID, VALIDATION_FAILED, RATE_LIMITED |

Every operation may also return the shared refusals (MALFORMED_JSON, NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, INTERNAL_ERROR, SERVICE_UNAVAILABLE), handled generically. Session list, change password, account deletion and Google sign-in are not used by this feature.

### 6. Authorization and entitlements

Public (no credential): register, verify email, sign in, forgot password, reset password. Credential required: resend verification, sign out. Unverified accounts may use these auth flows fully; wallet writes are refused server-side with EMAIL_NOT_VERIFIED, which this feature surfaces (FR-029). No wallet roles or entitlement gates apply to auth itself; the entitlement summary is only stored for later features.

### 7. Events

Not applicable — the client publishes no domain events. Internally, a "session ended" signal routes the app to sign-in (FR-023).

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Design system | UI screenshot tests (LTR, RTL, 200% scale) + manual canvas comparison | component catalogue tests |
| US2 Sign in | Unit (view model, error mapping, storage) + contract (request/response against OpenAPI examples) + UI flow | sign-in tests |
| US3 Register & verify | Unit (incl. link parsing for tapped and pasted links) + contract + deep-link UI test | registration and verification tests |
| US4 Password recovery | Unit (incl. link parsing for tapped and pasted links) + contract + deep-link UI test | password recovery tests |
| US5 Sign out | Unit + UI flow | sign-out tests |

### 9. Open questions

- **iOS live sign-in**: out of scope for this feature (clarified 2026-09-26); revisit when the backend accepts an `ios` platform value. Non-blocking.
- **Link paths** for verification and reset mails (host = API base URL host, per clarification): the exact paths and the name of the secret parameter depend on the backend's mail templates. Decided by: backend owner. Blocks: FR-027 end-to-end testing, not screen work.
- **Staging and production base URLs**: not yet defined; development uses LAN IP + container port. Non-blocking; configurable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of components on the *Mobile components* board exist in the catalogue, and a design review finds no visual mismatch in colour, size, radius or type against the board.
- **SC-002**: Every component and auth screen passes review in English and Arabic and at 200% font scale with zero clipped or overlapping text.
- **SC-003**: A returning person signs in from the entry screen in under 30 seconds.
- **SC-004**: A new person creates an account and reaches the "check your inbox" confirmation in under 90 seconds.
- **SC-005**: A person who forgot their password is signed in with a new one within 3 minutes of receiving the mail.
- **SC-006**: Relaunching a signed-in app reaches the signed-in area in under 2 seconds with no auth screen shown.
- **SC-007**: Every error code the identity endpoints can return maps to a specific, localised message; zero raw server messages or stack traces appear on screen.
- **SC-008**: Every auth screen built for this feature uses only library components — no one-off styling of colours, type or spacing.

## Assumptions

- Android is the only live-API target; iOS compiles and shows the same screens, but iOS sign-in is out of scope until the backend accepts an `ios` platform value.
- The development backend accepts plain HTTP from the LAN (the production rule refusing plaintext with INSECURE_TRANSPORT is assumed relaxed in the dev environment).
- Light theme only for this release, per the design board.
- Supported languages are Arabic and English; the app language follows the device language.
- After registration the person signs in explicitly (email prefilled); the app does not sign them in automatically, because registration issues no session.
- Unverified people may enter the app; the verify prompt stays until the address is verified.
- The home destination after sign-in is a placeholder until the Home feature is built; it hosts the verify prompt and sign-out.
- Google sign-in, two-factor, device session management, change password and account deletion are out of scope.
- Verification and reset mails contain links the phone can route into the app; if the app is not installed the web handles them (outside this repo).
- Typography uses Hanken Grotesk and IBM Plex Sans Arabic bundled with the app so it works offline.
- Screenshots of auth screens are not blocked; password fields mask input by default.
