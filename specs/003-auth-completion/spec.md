# Feature Specification: Auth Completion (Register, Verify, Reset)

**Feature Branch**: `003-auth-completion`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description: "specs/briefs/C01-auth-completion.md" — client brief C1, paired with backend brief B1 (spec 007, built).

**Backend contract**: `saveme-backend@56b44fb`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/identity.yaml` (`/auth/register`, `/auth/verify-email`, `/auth/resend-verification`, `/auth/forgot-password`, `/auth/reset-password`), `docs/openapi/error-catalog.yaml`, `docs/api-conventions.md`, `config/identity.php` (`links`: mailed link URLs and lifetimes).
- Finishes spec 001 User Stories 3 and 4 (tasks T082–T104), which were specified but never built; the 2026-09-29 E2E report lists their 12 scenarios as `not-built`. This spec supersedes 001's FR-025–FR-032 where they differ; 001 keeps the design system, sign-in and sign-out.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create an account (Priority: P1)

A new person taps "Create account", enters email, password and an optional display name, and is
told to check their inbox for a link. If the address already has an account, they are told so and
offered sign-in or a password reset instead.

**Why this priority**: Today nobody can join: "Create account" does nothing. Every later feature
needs new accounts.

**Independent Test**: Register a fresh address on the dev backend and see the "check your inbox"
screen naming the address; register the same address again and see the "already registered"
choice.

**Acceptance Scenarios**:

1. **Given** the registration form, **When** the person submits a valid email and a password of 8–128 characters that is not their email, **Then** the app shows a "check your inbox" confirmation naming the address and offering to go to sign-in with the email prefilled.
2. **Given** an address that already has an account, **When** the person submits, **Then** the app says the address is already registered and offers "Sign in" (email prefilled) and "Reset password"; nothing else happens.
3. **Given** a password shorter than 8 characters, longer than 128, or equal to the email, or a malformed email, or a display name over 100 characters, **When** the person tries to submit, **Then** the matching field shows its specific error and nothing is sent.
4. **Given** the server rejects a field, **When** the response arrives, **Then** the error appears under the matching field, not as a generic alert.
5. **Given** the registration limit for this network was reached, **When** the person submits, **Then** the app says to try again later, with the wait time when the server gives one.

---

### User Story 2 - Verify the email address (Priority: P1)

The person opens the link from the welcome mail on the phone; the app opens on a verification
screen that confirms the address, signed in or not. A signed-in, unverified person sees a
persistent prompt with "Resend email" until they verify.

**Why this priority**: Verification is required before any wallet write (security baseline), so
without it a new account is a dead end once wallets (C4) ship.

**Independent Test**: Register, open the mailed link on the phone, see success; sign in and see no
verify prompt.

**Acceptance Scenarios**:

1. **Given** a valid verification link, **When** the app opens from it, **Then** it confirms the address and shows success; this works whether or not anyone is signed in, and if the verified person is signed in, the verified state updates without signing in again.
2. **Given** an expired (older than 24 hours), used or superseded link, **When** the app opens from it, **Then** it explains the link no longer works and offers "Resend email" when signed in, or "Sign in" when not.
3. **Given** a signed-in, unverified person, **When** they are on the signed-in home or account screen, **Then** a persistent prompt says to verify, with "Resend email".
4. **Given** that prompt, **When** they tap "Resend email", **Then** a new mail is requested, a confirmation shows, and the button is disabled briefly; a fourth request within an hour shows "try again later" with the wait time.
5. **Given** a signed-in, unverified person attempts an action the server refuses with "email not verified", **Then** the app shows the verify prompt with "Resend email", never a bare denial.
6. **Given** a development build, **When** the person pastes a verification link into the development "Paste link" screen, **Then** it routes to the same verification screen as tapping the link.

---

### User Story 3 - Recover a forgotten password (Priority: P2)

A person who forgot their password asks for a reset link, opens it from the mail on the phone,
chooses a new password, and signs in with it.

**Why this priority**: Without it a person who forgets their password, or locks the account, has
no way back in; "Forgot password?" currently does nothing.

**Independent Test**: Request a reset for a known address, open the link, set a new password,
sign in with it.

**Acceptance Scenarios**:

1. **Given** the forgot-password screen (from sign-in, or from the "account locked" message), **When** the person submits any well-formed address, **Then** the app shows "if that address is registered, a link is on its way", whether or not it is registered.
2. **Given** a valid reset link, **When** the app opens from it, **Then** it shows a new-password screen; after a valid new password is submitted, it confirms and takes the person to sign-in, with the email prefilled when the app knows it.
3. **Given** the link is expired (older than 60 minutes), used, or superseded, **When** the person submits a new password, **Then** the app says the link no longer works and offers to request a new one.
4. **Given** a successful reset of the account this phone is signed in to, **When** the phone next talks to the server, **Then** it is signed out with the "session ended" explanation and the person must sign in again (the server ended every session of that account).
5. **Given** a fourth reset request for the same address within an hour, **When** submitted, **Then** the app shows "try again later" with the wait time.
6. **Given** a new password shorter than 8 characters or longer than 128, **When** the person tries to submit, **Then** the field shows the specific rule and nothing is sent.

### Edge Cases

- A link opened while a different account is signed in: verification still verifies the link's address; if that is not the signed-in account, the signed-in account's state does not change. A reset link signs nobody in and ends the linked account's sessions on the server; this phone is signed out on its next request only if it held that account (FR-019).
- A link opened with no network: the screen says it is offline and offers to try again; the secret is kept on screen until then, never stored.
- The same verification link tapped twice: the second answer is "already used"; if the address is already verified, the app shows success rather than an error.
- Registration or reset submitted twice by a double tap: one request only (the submit is disabled while a request runs; neither operation is auto-retried).
- The app is killed on the "check your inbox" screen: on relaunch the person lands on the entry screen; nothing is lost because nothing was stored.
- A link from another environment (a staging mail opened on a dev build): not claimed by the app, or rejected by the paste screen as not for this environment.
- App language vs mail language: mails follow the language sent at registration (`Accept-Language`); the app sends its current display language.

## Requirements *(mandatory)*

### Functional Requirements

**Registration**

- **FR-001**: The entry screen's "Create account" and the sign-in screen's "New here? Create account" MUST open the registration screen.
- **FR-002**: Registration MUST collect email, password and an optional display name (1–100 characters), and MUST send the device's timezone.
- **FR-003**: The app MUST validate before sending: well-formed email up to 254 characters, password 8–128 characters and not equal to the email (case-insensitive), display name at most 100 characters; each failure shows under its field and blocks the request.
- **FR-004**: On acceptance the app MUST show the "check your inbox" confirmation naming the address, with "Go to sign in" prefilling the email.
- **FR-005**: On `EMAIL_ALREADY_REGISTERED` the app MUST say the address already has an account and offer "Sign in" (email prefilled) and "Reset password" (email prefilled). This replaces spec 001 FR-026's uniform answer: the backend owner decided registration is the one surface that discloses an existing account (identity.yaml `registerAccount`).
- **FR-006**: The client's error-code list MUST include `EMAIL_ALREADY_REGISTERED` with localised copy in Arabic and English.

**Links**

- **FR-007**: The app MUST open verification links (`<verify_url>?token=…`) and reset links (`<reset_url>?token=…`) directly into the matching in-app screen, carrying the secret, signed in or not. The link host and path per environment come from build configuration, never from screens.
- **FR-008**: Development builds MUST keep the "Paste link" screen (Dev tools) working for both link kinds, routing to the same screens as tapping. The pasted link may be shown in that screen's field (dev-only exemption, constitution P-09.2 v1.3.0); it is never stored or logged.
- **FR-009**: The link secret MUST NOT be stored on the device or written to logs; it lives only in the open screen's state.

**Verification**

- **FR-010**: Opening a verification link MUST call verify and show success or a recoverable failure (`VERIFICATION_TOKEN_INVALID` → "this link no longer works", with "Resend email" if signed in, "Sign in" if not).
- **FR-011**: After a successful verification while signed in, the app MUST re-read the account from the server and update the cached account summary from that answer, so the address shows as verified without signing in again when the link was for the signed-in account, and nothing changes when it was for another account. The app MUST NOT set the verified flag from the verify answer alone (it doesn't say whose address it was).
- **FR-012**: A signed-in, unverified person MUST see a persistent verify prompt with "Resend email" on the signed-in home and account screens.
- **FR-013**: "Resend email" MUST request a new mail, confirm it, and disable the action while the request runs and for 30 seconds after; `RATE_LIMITED` MUST show the wait time when given.
- **FR-014**: Any `EMAIL_NOT_VERIFIED` refusal MUST surface the verify prompt with "Resend email".

**Password recovery**

- **FR-015**: "Forgot password?" on the sign-in screen, and the "Forgot password?" action on the account-locked message, MUST open the forgot-password screen with the typed email prefilled.
- **FR-016**: Forgot-password MUST show the same confirmation for any well-formed address; `RATE_LIMITED` shows the wait time.
- **FR-017**: The reset screen MUST accept a new password of 8–128 characters, validate it before sending, and on success confirm and route to sign-in with the email prefilled when known.
- **FR-018**: `RESET_TOKEN_INVALID` MUST show "this link no longer works" with "Request a new link".
- **FR-019**: A successful reset MUST NOT sign this phone out by itself: the server ends every session of the reset account, and the app's existing handling of an unauthenticated refusal (spec 001) signs the phone out on its next request if it held that account. The reset screen then routes to sign-in.

**Common**

- **FR-020**: Register, verify, resend, forgot and reset MUST never be retried automatically (spec 001 research R-04); submit actions are disabled while a request runs.
- **FR-021**: Every screen in this spec MUST be built from design-system components only, in Arabic RTL and English LTR, readable at 200% font scale.
- **FR-022**: Every interactive element these screens add MUST carry a stable test tag so the E2E flows can select it without positions.

### Key Entities *(include if feature involves data)*

- **Link (transient)**: kind (verify or reset) and secret, parsed from a tapped or pasted URL. Never stored.
- **Account summary (existing client cache)**: gains nothing new; its verified flag now changes after verification.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets new people join Save Me and prove their address, and lets anyone who forgot their password
get back in, from the phone. Closes the gap spec 001 left: its registration, verification and
recovery stories were specified but never built.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **User**, **Profile** (created with the account), **Device
Session**. The UI says "link" for the mailed secret; "token" is not shown. No new glossary terms.

### 3. Data model

No new stored data. The link secret is transient (FR-009). The existing account summary's
`email verified` flag is updated after verification (FR-011). A reset stores and clears nothing;
the server ends the account's sessions and the existing 401 handling follows (FR-019).

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| A link secret is never persisted or logged, and shown only in the dev Paste link field | P-09.2 (v1.3.0), FR-008, FR-009 | link-parser and logging tests |
| Register, verify, resend, forgot and reset are never auto-retried | P-02.6, R-04 (spec 001) | API client unit test |
| Client-side validation blocks the request on any field error | FR-003, FR-017 | register and reset view-model tests |
| An existing address is disclosed only on registration | identity.yaml `registerAccount` vs `requestPasswordReset` | forgot-password view-model test (same answer for any address) |
| A reset never clears the local session itself; only a 401 from the server does | identity.yaml `resetPassword`, FR-019, P-09.5 | reset use-case test |
| Every code handled maps to localised copy | spec 001 FR-014, P-02.4 | error-copy mapping test |

### 5. API

All under `/api/v1`, defined in `saveme-backend/docs/openapi/paths/identity.yaml`.

| Operation | Method & path | Session | Codes the app handles |
|---|---|---|---|
| Register | POST `/auth/register` (`email`, `password`, `display_name?`, `timezone`) | none | 202; EMAIL_ALREADY_REGISTERED (409), VALIDATION_FAILED, RATE_LIMITED |
| Verify address | POST `/auth/verify-email` (`token`) | none | 200 `email_verified: true`; VERIFICATION_TOKEN_INVALID, VALIDATION_FAILED, RATE_LIMITED |
| Resend verification | POST `/auth/resend-verification` | required | 202; RATE_LIMITED, UNAUTHENTICATED |
| Forgot password | POST `/auth/forgot-password` (`email`) | none | 202; VALIDATION_FAILED, RATE_LIMITED |
| Reset password | POST `/auth/reset-password` (`token`, `password`) | none | 204; RESET_TOKEN_INVALID, VALIDATION_FAILED, RATE_LIMITED |

Every operation may also return the shared refusals, handled generically as in spec 001.
Mailed links are `config('identity.links.verify_url')?token=…` (24 h, single use, superseded by a
newer one) and `config('identity.links.reset_url')?token=…` (60 min).

#### Contract changes since last spec

- **API conventions, Localisation**: with no `Accept-Language`, a signed-in request is answered in the profile language. No client change: the app sends `Accept-Language` on every request (spec 001, spec 002 FR-016).
- **Error catalogue**: no changes since `5e5e969`. `EMAIL_ALREADY_REGISTERED` already existed but the client never handled it; FR-005 and FR-006 add it.
- **Registration answer**: the contract now refuses an existing address with 409 instead of the uniform 202 spec 001 assumed; handled by FR-005.

### 6. Authorization and entitlements

Register, verify, forgot and reset are public (no session). Resend needs the session. No wallet
roles or entitlement gates. Verification is the precondition the backend enforces for wallet
writes (security baseline); this spec only surfaces it (FR-014).

### 7. Events

Not applicable: the client publishes no domain events. Internally, a successful verification
updates the account summary. A reset has no local effect; the server's session end reaches the
phone as a 401 and the existing "session ended" clean-up runs.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Register | Unit (validation, 409 branch) + contract (register) + UI flow + E2E | register tests, `.maestro/003-…/US1-*` |
| US2 Verify | Unit (link parser, success/invalid, resend throttle) + contract (verify, resend) + E2E | verify tests, `.maestro/003-…/US2-*` |
| US3 Reset | Unit (same-answer, reset success clears session) + contract (forgot, reset) + E2E | reset tests, `.maestro/003-…/US3-*` |
| New screens | Screenshot tests (LTR, RTL, 200% scale) | screen screenshot tests |

### 9. Open questions

- **Dev link host**: the backend's dev default link host is `saveme-backend.test`, which the phone cannot resolve. Proposed: the backend dev `.env` sets `IDENTITY_VERIFY_URL=http://localhost:43871/verify-email` and `IDENTITY_RESET_URL=http://localhost:43871/reset-password`, which the app reaches through `adb reverse` and claims in the dev manifest. Decided by: backend owner. Non-blocking: the Paste link screen covers it (FR-008).
- **E2E access to mailed links**: resolved in planning (research R-07): the E2E runner issues real links through the backend's `LinkRepository` and opens them with Maestro `openLink`; no mail reading, no backend change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new person goes from opening the app to a verified account in under 3 minutes, excluding mail delivery time.
- **SC-002**: A person who forgot their password is signed in again with a new one in under 3 minutes, excluding mail delivery time.
- **SC-003**: 100% of expired, used or superseded links lead to a screen with a way forward (resend, request a new link, or sign in); zero dead ends.
- **SC-004**: Zero link secrets appear in device storage or logs.
- **SC-005**: Every new screen passes review in Arabic, English and at 200% font scale with no clipped or overlapping text.
- **SC-006**: All 17 acceptance scenarios in this spec that don't need a second device are covered by passing E2E flows, replacing the 12 `not-built` rows in spec 001's E2E report.

## Assumptions

- Android is the only live-API target, as in specs 001 and 002.
- Registration does not sign the person in (the contract answers 202 with no credential); they sign in after, with the email prefilled.
- The app keeps following the device language before sign-in (spec 002); registration sends it as `Accept-Language`, which sets the new profile's language and the mail's language.
- The display name is optional and editable later in the profile (spec 002).
- Staging and production link hosts are not defined yet (spec 001 TODO); release builds claim them once they are.
- Google sign-in, phone/OTP and two-factor stay out of scope (backend extension point only).
- 001 tasks T082–T104 are the starting implementation plan; `/speckit-plan` may reuse them.
