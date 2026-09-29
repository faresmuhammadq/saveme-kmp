# Feature Specification: Account Security & Profile

**Feature Branch**: `002-account-security` (spec directory; branch is the owner's choice)

**Created**: 2026-09-28

**Status**: Draft

**Input**: User description: "Build Fully functional auth flows"

**Backend contract**: `saveme-backend@5e5e969`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/identity.yaml` (sessions, change password, account deletion, `/me`, `/me/profile`), `docs/api-conventions.md`, `docs/security-baseline.md` (Device sessions, Deletion), `docs/openapi/error-catalog.yaml`, `docs/glossary.md`.
- Builds on spec 001 (design system, sign-in, register/verify, password reset, sign-out), which listed device session management, change password and account deletion as out of scope. Scope chosen by the owner on 2026-09-28: the account-security flows 001 left out, plus viewing and editing the profile. Finishing 001's open stories is tracked in 001, not here.

## Clarifications

### Session 2026-09-28

- Q: Does the profile language also switch the app's own display language, or only mails and server messages? → A: It also switches the app's display language. One setting controls both; this replaces spec 001's assumption that the app follows the device language once a person is signed in.
- Q: Profile and Device Session are syncable (backend glossary), so constitution P-04 (offline-first, non-negotiable) applies. How is that met? → A: Add an on-device database. Profile and the device session list are stored on the device and every screen reads them from there; profile edits are saved on the device first and sent to the server when a connection exists; a conflict is resolved by the person choosing. Ending a session stays an online-only action because it revokes a credential (P-04 rule 6).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See and end the devices signed in (Priority: P1)

A signed-in person opens their account area and sees every device currently signed in to their account: device name, when it signed in, and when it was last used. The phone they are holding is marked "This device". They can end any other device's session, or end every other session at once, after confirming. A lost or sold phone stops working on its very next request.

**Why this priority**: The only way to cut off a lost or stolen phone that holds someone's financial records. Highest security value, no dependency on the other stories.

**Independent Test**: Sign in on two devices; on device A open "Devices signed in", end device B; device B's next action returns it to sign-in, device A keeps working.

**Acceptance Scenarios**:

1. **Given** the person is signed in on two devices, **When** they open "Devices signed in" on device A, **Then** both devices are listed, newest sign-in first, and device A is marked "This device" with no end action.
2. **Given** the list shows device B, **When** the person chooses "Sign out" on device B and confirms, **Then** device B disappears from the active list and a confirmation is shown.
3. **Given** device B's session was ended, **When** device B makes any request, **Then** device B returns to sign-in with the "session ended" message (spec 001 FR-023).
4. **Given** three other devices are signed in, **When** the person chooses "Sign out all other devices" and confirms, **Then** only "This device" remains in the list.
5. **Given** the person cancels a confirmation, **When** the dialog closes, **Then** nothing is ended.
6. **Given** only this device is signed in, **When** the list opens, **Then** "Sign out all other devices" is not offered and the list explains no other devices are signed in.

---

### User Story 2 - Change the password while signed in (Priority: P1)

A signed-in person changes their password by entering the current one and a new one. This device stays signed in; every other device is signed out, and the person is told so.

**Why this priority**: The standard response to a suspected leak; together with Story 1 it gives a person full control after losing a device.

**Independent Test**: Signed in on two devices, change the password on device A; A stays signed in, B returns to sign-in, and the new password signs in while the old one does not.

**Acceptance Scenarios**:

1. **Given** the person is signed in, **When** they enter the correct current password and a valid new password and submit, **Then** a confirmation says the password changed and other devices were signed out, and this device stays signed in.
2. **Given** a wrong current password, **When** they submit, **Then** "Current password is incorrect" appears on the current-password field and nothing changes.
3. **Given** repeated wrong current passwords lock the account, **When** they submit again, **Then** the app says the account is temporarily locked and for how many minutes, and disables submit for that time.
4. **Given** a new password shorter than 8 characters, longer than 128, or equal to the email address, **When** they try to submit, **Then** the field shows the specific rule broken before any request is sent.
5. **Given** the password changed, **When** the person later signs in on another device, **Then** only the new password works.

---

### User Story 3 - View and edit the profile (Priority: P2)

A signed-in person sees their account details (email, whether it is verified) and their profile: display name, language, timezone, default currency and the day their budget period starts. They can edit and save these. If the default currency can no longer change, it is shown read-only with the reason. Edits are saved on the phone at once, even offline, and sent to the server when a connection exists. If the profile was changed on another device in the meantime, the person sees both versions and chooses which to keep; nothing is silently overwritten.

**Why this priority**: Needed by later features (budgets, wallets, amounts) but not a security action; the app is usable with server defaults until it lands.

**Independent Test**: Open the profile, change the display name and budget start day, save, reopen on another device and see the same values.

**Acceptance Scenarios**:

1. **Given** the person is signed in, **When** they open "Profile", **Then** email, verification status and all profile fields show the stored values.
2. **Given** edited values, **When** they save, **Then** a confirmation appears and reopening shows the saved values.
3. **Given** no field changed, **When** they look at the screen, **Then** save is disabled.
4. **Given** the profile was saved on another device after this device last synced, **When** this device's edit reaches the server, **Then** the app shows "Your profile changed on another device" with both versions and the choices "Keep mine" and "Use the other device's"; nothing is applied until the person chooses.
10. **Given** the phone is offline, **When** the person edits and saves the profile, **Then** the change shows as saved on this device with a "Waiting to sync" note, and it syncs without further action once online.
5. **Given** the default currency is locked, **When** the profile opens, **Then** the currency is read-only with the note "Can't change after your first transaction".
6. **Given** a new budget start day, **When** saved, **Then** the app shows the date the new start day applies from.
7. **Given** the display name is cleared, **When** saved, **Then** the profile has no display name and greetings fall back to no name.
8. **Given** the app is in English, **When** the person sets the language to Arabic and saves, **Then** the app switches to Arabic right-to-left at once and stays in Arabic after a restart.
9. **Given** the profile language is Arabic and the phone is in English, **When** the person signs in, **Then** the app switches to Arabic after sign-in.

---

### User Story 4 - Request and cancel account deletion (Priority: P2)

A signed-in person asks to delete their account, confirming with their password. The app explains the 30-day grace period before asking, then shows the date deletion will happen. During the grace period the app keeps working, shows a persistent notice with the date, and offers "Cancel deletion", which restores everything.

**Why this priority**: A legal and store-policy expectation (people must be able to leave), but used rarely; it depends on nothing else here.

**Independent Test**: Request deletion with the password, see the scheduled date and the notice; cancel; the notice disappears and the account is active.

**Acceptance Scenarios**:

1. **Given** the person chooses "Delete account", **When** the explanation screen opens, **Then** it states the 30-day grace period, that data is kept and restorable until the date, that signing in again or cancelling stops the deletion, and asks for the password.
2. **Given** the correct password, **When** they confirm, **Then** the app shows the scheduled deletion date and the account shows as pending deletion.
3. **Given** a wrong password, **When** they confirm, **Then** "Password is incorrect" is shown and nothing is scheduled; lock behaves as in Story 2 scenario 3.
4. **Given** the account is pending deletion, **When** the person uses the app, **Then** a persistent notice on the signed-in home shows the deletion date and a "Cancel deletion" action.
5. **Given** a pending deletion, **When** the person cancels it, **Then** the notice disappears, the account is active, and a confirmation says nothing was lost.
6. **Given** deletion was already requested, **When** the person requests again, **Then** the same date is shown; it never moves.
7. **Given** a pending deletion, **When** the person signs in anew on any device, **Then** the deletion is cancelled and the "deletion cancelled" message from spec 001 is shown.

---

### Edge Cases

- Offline or timeout on any action: nothing is assumed done; the screen stays with the entered values and offers retry (auth-changing actions are never retried automatically).
- The session list includes ended or expired sessions from the server: only active sessions are listed; ended ones are not shown in this release.
- Ending a session that another device already ended: treated as success; the list refreshes.
- A session not found (ended and purged meanwhile): list refreshes, no error shown.
- This device's own session is ended from another device while this screen is open: the next request returns to sign-in (spec 001 FR-023).
- Password change or deletion request succeeds but the response is lost (offline after sending): on retry, change password answers "current password is incorrect" if the new one was applied; the app suggests signing in with the new password rather than looping.
- Many devices signed in (more than one page): every page is fetched into the device's copy, so the list is complete.
- Language saved offline: the app switches language at once (the edit is saved on the device); if a later conflict is resolved with the other device's version, the app switches to that language.
- Profile timezone from the server not recognised on the device: shown as stored text, still saveable unchanged.
- Unverified account: may view and edit the profile, manage sessions, change password and request deletion (none sit behind the verification gate).
- Server returns an error code the app does not know: generic message with the request reference (spec 001 FR-016).
- Account deletion date passes while the app is signed in: the next request is refused as unauthenticated and the app returns to sign-in.

## Requirements *(mandatory)*

### Functional Requirements

**Account area**

- **FR-001**: The signed-in home MUST offer an entry to an "Account" area containing: Profile, Devices signed in, Change password, Delete account, and Sign out (sign-out behaviour from spec 001).

**Devices signed in (US1)**

- **FR-002a**: The device session list MUST be stored on the device and the screen MUST read it from there, refreshed from the server when the screen opens and after any session is ended; offline, the last stored list is shown with the time it was last updated.
- **FR-002**: The app MUST list every active device session of the person (all pages the server returns) with device name, sign-in time and last-used time (or "Not used yet"), newest sign-in first, times shown in the person's profile timezone.
- **FR-003**: The current device's session MUST be marked "This device" and MUST NOT offer an end action (signing out this device is Sign out).
- **FR-004**: Ending one session or all other sessions MUST require explicit confirmation naming what will be signed out.
- **FR-005**: After ending a session, the list MUST refresh from the server; ending an already-ended or missing session MUST be treated as done.
- **FR-006**: "Sign out all other devices" MUST be hidden when no other active session exists.

**Change password (US2)**

- **FR-007**: Change password MUST require the current password and a new password, with the new one validated before sending: 8–128 characters and not equal to the account email.
- **FR-008**: A wrong current password MUST show a field message on the current-password field; account lock MUST show the lock duration in minutes and disable submit until it passes.
- **FR-009**: On success, the app MUST stay signed in and tell the person every other device was signed out.

**Profile (US3)**

- **FR-010**: The app MUST show email, verification status and the profile: display name, language, timezone, default currency, budget period start day.
- **FR-011**: The profile MUST be stored on the device and every profile screen MUST read it from there; it MUST refresh from the server when the account area opens, when the app returns to the foreground, and after sign-in. Save MUST be disabled until something changed.
- **FR-011a**: Saving MUST write the edit on the device first, marked "waiting to sync", and succeed without a connection; the app MUST send the whole profile with the last version received from the server as soon as a connection exists, and clear the mark when the server accepts it. A server refresh MUST NOT overwrite an edit still waiting to sync.
- **FR-012**: When the server refuses a sync because the profile changed elsewhere, the app MUST show the person's pending edit and the server's current profile side by side and let them choose "Keep mine" (resend the edit against the server's new version) or "Use the other device's" (discard the edit); it MUST NOT choose on its own or merge fields silently.
- **FR-013**: When the default currency is locked, the currency field MUST be read-only with an explanation; a save refused because the currency is immutable MUST show that message on the currency field.
- **FR-014**: Budget period start day MUST be selectable from 1 to 28 only; after a save the app MUST show the date the new start day applies from when the server returns one.
- **FR-015**: Timezone MUST default its picker to the stored value and offer IANA timezones; currency MUST be a 3-letter code picked from a list.
- **FR-016**: The profile language MUST set the app's display language as well as the language of mails and server messages:
  - saving a new language MUST switch the app's language (including layout direction) immediately, without restarting or signing out;
  - after sign-in, and whenever the profile is loaded, the app MUST adopt the profile language if it differs from the one shown;
  - while signed out, the app MUST follow the device language (Arabic or English, else English), as in spec 001;
  - the chosen language MUST persist on this device across restarts until sign-out.

**Account deletion (US4)**

- **FR-017**: Before asking for the password, the app MUST explain: 30-day grace period, data restorable until the date, signing in again or cancelling stops deletion.
- **FR-018**: Deletion MUST be confirmed with the current password; wrong password and lock follow FR-008.
- **FR-019**: While the account is pending deletion, the signed-in home MUST show a persistent notice with the deletion date (in the person's locale) and a "Cancel deletion" action.
- **FR-020**: Cancelling MUST return the account to active, remove the notice, and confirm that nothing was lost.
- **FR-021**: The account state (active or pending deletion, and the date) MUST be refreshed from the server when the account area opens and when the app returns to the foreground, so a deletion requested or cancelled on another device shows correctly.

**Cross-cutting**

- **FR-022**: Error copy MUST be chosen by error code (spec 001 FR-014); this feature adds localised copy for: profile changed elsewhere, currency locked, current password incorrect.
- **FR-023**: The client's error-code set MUST add VERSION_CONFLICT (retry after change) and field code IMMUTABLE_FIELD; unknown codes keep the generic fallback.
- **FR-027**: A wrong current password (during change password or deletion) MUST NOT sign the person out; only a refusal that says the session itself is no longer valid does (spec 001 FR-023).
- **FR-024**: Password change, session ending and deletion requests MUST NOT be retried automatically and MUST NOT be queued while offline; the person retries when online. Profile sync is the only background write.
- **FR-025**: All new screens MUST use only design-system components, and work in Arabic RTL, English, and at 200% font scale.
- **FR-026**: UI copy MUST say "device" or "session", never "token" (glossary).

### Key Entities

- **Device session (server-owned, shown read-only)**: device name, platform, app version, state (active, revoked, expired), sign-in time, last-used time, whether it is this device.
- **Account summary (client cache, from spec 001)**: email, verified flag, state (active or pending deletion), scheduled deletion date — now refreshed by this feature.
- **Profile (syncable, stored on the device)**: display name, language, timezone, default currency, whether the currency is locked, budget period start day, the date it applies from, the server version, and whether a local edit is waiting to sync.
- **Device session list (syncable, stored on the device, read-only)**: the stored copy of the server's device sessions and when it was last refreshed.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a signed-in Save Me person control their own account from the phone: see every device signed in and cut any of them off, change their password, keep their profile preferences, and leave with a 30-day safety net. Serves every signed-in person, in Arabic and English.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **User** (Identity), **Profile**, **Device Session** (UI says "device" / "devices signed in"; "token" and "PAT" banned), **Budget Period** (start day), **Budget**. Account deletion and its grace period are defined in `docs/security-baseline.md` → Deletion, not the glossary. No new glossary terms.

### 3. Data model

The server owns User, Profile and Device Session. Profile and Device Session are syncable (glossary), so the device keeps its own copy in an on-device database as the UI's source of truth (P-04).

| Entity | Fields | Scope | Lifecycle | Owner |
|---|---|---|---|---|
| Account summary (existing) | user id, email, email verified, state, deletion scheduled for | signed-in user | refreshed on sign-in, account-area open, foreground; cleared on sign-out | auth module |
| App language (existing, now profile-driven) | ar or en | this install | device language while signed out; profile language after sign-in or save; reset to device language on sign-out | account module |
| Profile (device database) | id, display name, locale, timezone, default currency, currency locked, budget start day, applies from, version, pending edit | signed-in user | written on refresh and on local save; pending edit cleared on sync or discard; conflict holds the server copy until the person chooses; deleted on sign-out | account module |
| Device session (device database) | id, device name, platform, app version, state, end reason, signed in at, last used at, ended at, current, refreshed at | signed-in user | replaced on each refresh; deleted on sign-out | account module |

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Current device never offers "end session" | identity.yaml `DeviceSession.current`; FR-003 | sessions view-model test |
| Profile screens never render a network response directly; they read the device database | P-04.1 | profile repository test |
| A refresh never overwrites a pending local edit | P-04.2, FR-011a | profile repository test |
| Profile sync always carries the last server version; conflict applies nothing until the person chooses | P-04.4 | identity.yaml `saveProfile` (409 VERSION_CONFLICT) | profile view-model test |
| Budget start day is always 1–28 | identity.yaml `ProfileWrite` | profile validation unit test |
| Password-changing and deletion requests are never auto-retried | constitution P-02.6, research R-04 (spec 001) | API client unit test |
| Change password never signs this device out | identity.yaml `changePassword` | change-password view-model test |
| Deletion notice shown iff account state is pending deletion | security-baseline: Deletion | home view-model test |
| App language equals profile language while signed in | FR-016 (clarified 2026-09-28) | language view-model / app-locale test |
| Every new code handled maps to localised copy | spec 001 FR-014, P-02.4 | error-copy mapping test |

### 5. API

All under `/api/v1`, defined in `saveme-backend/docs/openapi/paths/identity.yaml`. All require the credential.

| Operation | Method & path | Codes the app handles |
|---|---|---|
| List device sessions | GET `/auth/sessions` (filter state = active, every page by cursor) | 200; UNAUTHENTICATED |
| End other sessions | DELETE `/auth/sessions` | 204; UNAUTHENTICATED |
| End one session | DELETE `/auth/sessions/{id}` | 204; NOT_FOUND (treated as done), VALIDATION_FAILED, UNAUTHENTICATED |
| Change password | POST `/auth/change-password` | 204; INVALID_CREDENTIALS, ACCOUNT_LOCKED, VALIDATION_FAILED, UNAUTHENTICATED |
| Request account deletion | POST `/account/deletion-request` | 200 (scheduled date); INVALID_CREDENTIALS, ACCOUNT_LOCKED, VALIDATION_FAILED, UNAUTHENTICATED |
| Cancel account deletion | DELETE `/account/deletion-request` | 204; UNAUTHENTICATED |
| Read account and profile | GET `/me` | 200; UNAUTHENTICATED |
| Save profile | PUT `/me/profile` | 200; VERSION_CONFLICT (with `current`), VALIDATION_FAILED (incl. field IMMUTABLE_FIELD at `/default_currency`), UNAUTHENTICATED |

The backend has no change feed yet, so "sync from the server" for Profile and Device Session means re-reading `GET /me` and `GET /auth/sessions` into the device database. When the user-scope change feed is specified, it replaces these reads.

Every operation may also return the shared refusals (MALFORMED_JSON, NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, RATE_LIMITED, CLIENT_VERSION_UNSUPPORTED, INTERNAL_ERROR, SERVICE_UNAVAILABLE), handled generically as in spec 001.

#### Contract changes since last spec

- **API conventions, Localisation**: when `Accept-Language` is absent, a signed-in request is answered in the profile language. No client change: the app sends `Accept-Language` on every request (spec 001). The app sends the profile language as `Accept-Language` once signed in (FR-016), so server messages match the app.
- **Error catalogue**: no changes; the client's error codes match the catalogue. VERSION_CONFLICT and IMMUTABLE_FIELD already exist in the catalogue and are newly used here (FR-023).

### 6. Authorization and entitlements

Every operation acts on the caller's own account only; another person's session id is 404 and treated as done. None sit behind the email verification gate. No wallet roles or entitlement gates apply.

### 7. Events

Not applicable: the client publishes no domain events. Internally, the existing "session ended" signal (spec 001) routes to sign-in; account state changes update the cached account summary, which drives the deletion notice.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Devices signed in | Unit (view model, all-pages refresh, confirmation) + contract (list, end one, end others) + UI flow | sessions tests |
| US2 Change password | Unit (validation, lock, success) + contract + UI flow | change-password tests |
| US3 Profile | Unit (dirty tracking, conflict, locked currency, start day) + contract (`/me`, `/me/profile`) + UI flow | profile tests |
| US4 Account deletion | Unit (request, repeat, cancel, notice) + contract + UI flow | deletion tests |
| New screens | Screenshot tests (LTR, RTL, 200% scale) | screen screenshot tests |

### 9. Open questions

- **`/me` and `/me/profile` not yet committed in the backend**: they exist in the backend working tree (spec 008), not at the recorded baseline `5e5e969`. Decided by: backend owner. Blocks US3 contract tests against a committed contract, not screen work.
- **Change feed**: the user-scope sync feed is not yet in the backend contract; interim refresh reads are used (section 5). Decided by: backend owner. Non-blocking.
- **Showing ended sessions** (history of revoked/expired devices): left out of this release. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can find and sign out a lost device within 30 seconds of opening the app.
- **SC-002**: An ended device loses access on its very next action, 100% of the time.
- **SC-003**: A person changes their password in under 60 seconds, and 100% of their other devices are signed out afterwards.
- **SC-004**: Zero profile edits are silently overwritten when two devices edit the profile at the same time.
- **SC-008**: A profile edit made offline is never lost: 100% of offline edits reach the server, or are shown to the person as a conflict, once a connection returns.
- **SC-005**: A person who requested deletion sees the deletion date on every launch until they cancel or the date passes, and can cancel in 2 taps from the home screen.
- **SC-006**: Every new screen passes review in Arabic, English and at 200% font scale with no clipped or overlapping text.
- **SC-007**: Every error code these operations can return shows a specific localised message; zero raw server messages appear.

## Assumptions

- Android is the only live-API target, as in spec 001.
- Supersedes spec 001's assumption "the app language follows the device language" for signed-in people (FR-016); signed out, it still does.
- An on-device database is added for syncable entities (owner decision 2026-09-28), starting with Profile and Device Session.
- Only active sessions are listed; the server's ended/expired sessions are not shown.
- Signing out this device stays the spec 001 Sign out action, not an entry in the device list.
- The account area is reached from the signed-in home placeholder of spec 001 until a real Home/Settings exists.
- Currency list: ISO 4217 codes the app knows how to format (ADR-0003 minor units); timezone list: the device's IANA timezone database.
- After a lock (ACCOUNT_LOCKED) during change password or deletion, this device stays signed in; only password checks are locked.
- Data export, Google sign-in and two-factor are out of scope (no backend support in MVP for the latter two).
