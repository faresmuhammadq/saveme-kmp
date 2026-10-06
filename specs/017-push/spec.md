# Feature Specification: Push Notifications (client)

**Feature Branch**: `017-push`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "specs/briefs/C16-push.md" — client brief C16, wave 4. Pairs with backend B16 → spec 023 (designed and frozen; implementation in progress, not yet committed: `docs/openapi/paths/notifications.yaml` v1.14 and `specs/023-devices-push/` are in the backend working tree on top of `e8c308a`).

**Backend contract**: `saveme-backend@e8c308a`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/notifications.yaml` (v1.14, uncommitted at `e8c308a`): `PUT /me/devices/{id}` (`registerPushDevice`, body `{push_token}`), `DELETE /me/devices/{id}` (`unregisterPushDevice`), `GET`/`PUT /me/notification-preferences`, and the `PushPayload` data schema `"1"` (12 message types, identifiers only). `docs/api-conventions.md` sections "A versioned `/me` resource that is not syncable" and "The device id on `/me/devices/{id}`". `docs/openapi/error-catalog.yaml` (no new code: `NOT_FOUND`, `VERSION_CONFLICT`, `VALIDATION_FAILED`, `RATE_LIMITED`).
- Backend behaviour: `saveme-backend/specs/023-devices-push/` — `spec.md` (clarifications, edge cases), `research.md` R-1…R-18 (R-4 registration, R-5 removal paths, R-6 preferences, R-10 payload, R-11 text, R-12 reminders), `contracts/push-api.md` ("Client behaviour", the payload table, "Guarantees").
- Glossary (`saveme-backend/docs/glossary.md`): **Push Registration**, **Notification Preferences**, **Push Notification**, **Device Session**. User-facing text never says "token" (P-01.3); the glossary's user-facing name for the wire `push_token` is "push address", and this feature never needs to show it at all.
- Builds on spec 001 (design system, error copy by code, request id on unexpected errors), 002 (Account screen, device sessions, sign-out, profile time zone), 003 (deep-link intake through `DeepLinkBus`, links held across sign-in), 004/009/011 (sync runner: `ReadTarget.User`, `ReadTarget.Wallet`, `ReadTarget.Everything`), 005 (Plan screen, entitlements refresh), 006 (wallet list and detail), 010 (occurrence screen, the all-wallets recurring screen), 012 (budget screen), 013 (invitation tokens never leave the mail; loss-of-access purge), and the parallel spec 016 (C15 export detail screen, not yet merged).

## Clarifications

### Session 2026-10-06

The owner was away with full delegated authority; each answer is the recommended, most
conservative option, recorded as an **assumed default**.

- Q: The Firebase Cloud Messaging client is a new dependency the owner has not approved. What does this spec build, and what waits? → A: **Everything except the Firebase wiring.** Receiving and registering go through a small "push provider" seam; the dependency-free parts (registration with the backend, the preferences screen, channels, display through the system notification service, tap routing, the permission flow, the developer simulate tool) are built and tested now with a fake provider on the host and in the development build. Adding Firebase Messaging, the Google Services build plugin and a Firebase configuration file per build flavor is a final, separately gated phase that starts only after the owner approves the dependency and supplies the configuration. Until then staging and production builds have no push provider: they show no Notifications entry, never ask for the permission and never register. (FR-045, FR-046, Assumptions "Owner must approve or supply")
- Q: When does the app ask for the Android 13+ notification permission? → A: **In context, never at launch or sign-in.** Two ways only: the "Turn on" button on the Notifications screen, and a short in-app explanation offered once per installation right after a moment where notifications clearly help — the first successful save of a recurring rule, the first budget created, or joining a shared wallet. "Not now" is respected: the explanation is never offered again by itself. When the system will no longer show its dialog, "Open settings" leads to the app's notification settings. (FR-010–FR-012)
- Q: Notification preferences are not synced by the engine. Are edits online-only or queued? → A: **Online-only, with one explicit Save.** The screen reads the preferences when it opens and saves the whole object with the version it read; offline it says so and keeps the edits on screen, nothing is queued or retried. A stale save (`409 VERSION_CONFLICT`) shows the stored preferences from the answer in place of the edits, with a message to check them and save again; nothing is merged and the server is never silently overwritten (P-04.4 spirit, backend R-6). (FR-014–FR-019)
- Q: What does a tap open when the record is not on the phone yet? → A: **The app first asks for a read of the scope that holds it, then opens the screen.** A short "Opening…" screen waits at most 10 seconds (with Cancel); after the read, or offline, or on time-out, the target screen opens and shows its own existing "not available" or loading state if the record is still missing. The tap never confirms, joins or changes anything. (FR-030–FR-033)
- Q: What does "unregister on sign-out" do, given the server already drops the registration when the session ends? → A: **Both, in order.** Before the stored session is wiped the app makes one best-effort `DELETE /me/devices/{id}` (bounded so sign-out never waits longer than today), then the existing server sign-out, then it discards the phone's push address with the provider and clears every displayed notification of the app. If the server could not be reached, the discarded push address makes any later message to it fail and be pruned by the server, so a signed-out phone does not keep receiving. (FR-007)

## User Scenarios & Testing *(mandatory)*

Mona uses the app on her phone. Her rent is a recurring item she confirms by hand; she has a
grocery budget; her brother shares a wallet with her. With this feature her phone tells her,
without her opening the app, when the rent needs confirming, when the grocery budget goes over,
when her role in the shared wallet changes, when her plan is about to end, when the export she
asked for is ready and when her account is about to be deleted. Each notification is a short,
generic sentence in her language — never an amount, a name or a note — and tapping it opens the
right screen. She chooses which kinds she hears about and when she does not want to be disturbed.

### User Story 1 - My phone can receive notifications (Priority: P1)

After Mona signs in, her phone quietly tells the server where it can be reached, keeps that
current, and stops it when she signs out. The app asks for the system's notification permission
only at a moment where it makes sense, after explaining why.

**Why this priority**: Without a registration nothing can reach the phone; every other story
depends on it.

**Independent Test**: On the host with the fake provider and a mock server: sign in → one
`PUT /me/devices/{current session id}` with `{push_token}` only; a new push address → another
`PUT`; foreground twice within a day → no further `PUT`; sign out → `DELETE` before the session is
wiped, then the push address is discarded. Live: the same calls against the local backend answer
`200`/`204`.

**Acceptance Scenarios**:

1. **Given** a person signs in on a phone that can receive push and allows notifications, **When** sign-in completes, **Then** the app registers this phone for the new device session, in the background, without any screen waiting for it.
2. **Given** a signed-in phone, **When** the push provider hands the app a new push address, **Then** the app registers it for the current session (the server replaces the old one).
3. **Given** a registered phone, **When** the app comes to the foreground and the last successful registration of this session with this push address is less than 24 hours old, **Then** nothing is sent; when it is older, the app registers again (keeping the server's 270-day window fresh).
4. **Given** no connection when a registration is due, **When** the connection returns (even with the app closed), **Then** the registration is sent; a server refusal for too many attempts waits the time the server gives.
5. **Given** Android 13 or later and a person who has not been asked, **When** they save their first recurring rule, create their first budget, or join a shared wallet, **Then** the app shows a short explanation with "Turn on" and "Not now"; "Turn on" shows the system dialog; "Not now" closes it and it is never offered again by itself.
6. **Given** the person allows notifications (from the dialog or later in system settings), **When** the app next learns it, **Then** it registers the phone.
7. **Given** a registered phone, **When** the person blocks the app's notifications in system settings, **Then** at the next foreground the app removes this phone's registration (`DELETE`), and registers again when notifications are allowed again.
8. **Given** a signed-in, registered phone, **When** the person signs out, **Then** the app makes one bounded attempt to remove the registration while the session is still valid, signs out as before, discards the phone's push address and clears the app's notifications from the notification shade; sign-out never takes longer than it did before this feature.
9. **Given** the session ended elsewhere (another device ended it, password changed, account purged), **When** the phone learns it, **Then** the app discards the push address and clears its notifications (the server already removed the registration).
10. **Given** the server answers that the session id is not this caller's current session, **When** it happens, **Then** the app re-reads its stored session and registers once for that session if it differs; otherwise it stops trying until the next sign-in or new push address, and the person is not signed out.

---

### User Story 2 - Tapping a notification opens the right place (Priority: P1)

Mona taps "Something is due today" and lands on the rent occurrence, ready to confirm it. She taps
"A budget is over its limit" and sees the grocery budget. A notification about a shared wallet
opens that wallet; one about her plan opens the Plan screen; "You've been invited to a shared
wallet" opens a notice telling her the invitation is in her email.

**Why this priority**: A notification that opens the app's home page leaves the person to hunt;
the brief's integration checkpoint is "a notification that opens the invitation".

**Independent Test**: For each of the 12 message types, feed the sample data block to the tap
handler with the fake provider and check the screen that opens; for a record the phone does not
hold, check that a read of the right scope happens first.

**Acceptance Scenarios**:

1. **Given** an `occurrence_due` or `occurrence_auto_confirm_refused` notification, **When** tapped, **Then** the occurrence screen opens for `occurrence_id` (not its confirm form; nothing is confirmed).
2. **Given** an `occurrences_due_summary` notification, **When** tapped, **Then** the recurring screen across all wallets opens with its due items.
3. **Given** a `budget_exceeded` notification, **When** tapped, **Then** that budget's screen opens.
4. **Given** an `invitation_received` notification, **When** tapped, **Then** a notice opens saying an invitation to a shared wallet is waiting in the person's email (naming the person's own address) and that it is answered from the link in that email; no call is made and no invitation is joined.
5. **Given** a `member_role_changed` or `wallet_ownership_received` notification, **When** tapped, **Then** that wallet's screen opens after the wallet has been read, showing the new role.
6. **Given** a `member_removed` notification, **When** tapped, **Then** the app reads everything first (so the wallet the person lost is removed from the phone) and opens the wallet list.
7. **Given** a `subscription_ending` or `subscription_lapsed` notification, **When** tapped, **Then** the app refreshes what the plan allows and opens the Plan screen.
8. **Given** an `export_ready` notification, **When** tapped, **Then** the export's screen opens for `export_id`.
9. **Given** a `deletion_reminder` notification, **When** tapped, **Then** the app refreshes the account and opens the account deletion screen.
10. **Given** a notification about a record the phone has not read yet, **When** tapped, **Then** an "Opening…" screen shows while the app reads that record's scope (at most 10 seconds, with Cancel), then the target screen opens; still missing → that screen's own "not available" state; offline → the target screen opens with what the phone has. **Given** the record is already on the phone, **Then** its screen opens at once and the read runs in the background.
11. **Given** the app was closed, in the background, or open on another screen, **When** a notification is tapped, **Then** the same screen opens and Back returns to where the person was (or Home when the app was closed).
12. **Given** a notification of a type the app does not know, or whose identifiers are missing or malformed, **When** tapped, **Then** the app opens Home (or the type's list screen when only the identifier is bad) and shows no error.
13. **Given** nobody is signed in when a notification is tapped, **When** the app opens, **Then** it shows the sign-in screen and the tap is forgotten.

---

### User Story 3 - Choose what I hear about, and when not (Priority: P2)

Mona opens Account → Notifications. She turns off budget alerts, moves her quiet hours to 23:00–07:00
and saves once. The screen explains that the settings apply to every phone she is signed in on and
that quiet hours follow her profile's time zone.

**Why this priority**: Without control people turn off all notifications in system settings; but
the defaults (all on, quiet 22:00–08:00) already work without visiting the screen.

**Independent Test**: With the mock server seeded from the contract examples: open the screen →
defaults at version 1 shown; change two things and save → one `PUT` with every member and
`version: 1`; answer `409` with `current` → the stored values show with the conflict message.
Live: the same against the local backend, including a `409` provoked from a second session.

**Acceptance Scenarios**:

1. **Given** a person who never saved preferences, **When** they open Notifications, **Then** every category shows on and quiet hours show on from 22:00 to 08:00, with the profile's time zone named.
2. **Given** the screen open, **When** they change any switch or time, **Then** "Save" becomes available; when nothing differs from what was read, it is unavailable.
3. **Given** changes, **When** they tap "Save" online, **Then** the app sends the whole preferences with the version it read, shows "Saved" and the values the server returned.
4. **Given** the preferences were changed on another phone since the screen read them, **When** they save, **Then** the screen shows the stored preferences from the server's answer instead of the edits and says they were changed on another device and to check them and save again.
5. **Given** no connection, **When** the screen opens, **Then** it says it needs a connection and offers "Try again"; **and given** no connection when they save, **Then** it says so, keeps the edits on screen and sends nothing later by itself.
6. **Given** quiet hours with a start equal to the end, **When** shown, **Then** "Save" is unavailable and the screen says start and end must differ.
7. **Given** too many saves in an hour, **When** the server refuses, **Then** the screen says to try again in the server's wait time.
8. **Given** unsaved changes, **When** the person leaves the screen, **Then** the app asks whether to discard them.
9. **Given** notifications are not allowed for the app on this phone, **When** the screen shows, **Then** a card at the top says this phone will not show notifications and offers "Turn on" (system dialog) or "Open settings" (when the system no longer asks).
10. **Given** the screen, **When** the person wants to change sound or turn a kind off on this phone only, **Then** "Notification settings on this phone" opens the system settings for the app's notifications.

---

### User Story 4 - Notifications stay private and tidy (Priority: P2)

Mona's notifications never show an amount or a name, are hidden on her lock screen when it is
secured, replace each other instead of piling up when they are about the same thing, and vanish
when she signs out. Each kind has its own system channel she can mute.

**Why this priority**: The lock screen is the most public surface of a finance app; privacy
mistakes there cost more trust than a missing notification.

**Independent Test**: With the fake provider: post two messages with the same subject → one
notification remains; post a message with the app open → a notification appears on the category's
channel with the received text; sign out → none remain. Channel definitions are checked on the
host.

**Acceptance Scenarios**:

1. **Given** the app starts, **When** it runs for the first time or the app language changes, **Then** six notification channels exist — Recurring items due, Budget alerts, Shared wallets, Plan and billing, Exports, Account deletion — named in the app's language, each hiding its content on a secure lock screen.
2. **Given** a message arrives while the app is open, **When** it is received, **Then** the app shows it as a normal notification on its category's channel with exactly the received title and text (no in-app banner), and quietly reads the matching data in the background.
3. **Given** two messages about the same subject (the same occurrence, budget, wallet, export, plan or deletion), **When** both arrive, **Then** only the latest is shown.
4. **Given** the same message delivered twice, **When** both reach the app while it is open, **Then** it is shown once.
5. **Given** a secure lock screen, **When** a notification arrives, **Then** the lock screen shows that the app has a notification without its text.
6. **Given** the person signs out or their session ends, **When** it happens, **Then** every notification of the app is removed from the notification shade.

---

### User Story 5 - Developers and testers can exercise push without Firebase (Priority: P3)

A developer on the development build opens Developer tools → "Simulate a notification", picks a
type, and the app posts it through exactly the path a real message takes; tapping it exercises the
routing. The development build registers a fake push address with the local backend so the
registration and preferences endpoints can be checked end to end.

**Why this priority**: Real Firebase delivery cannot be tested until the owner approves the
dependency and supplies the configuration, and Maestro cannot receive a real push; without this the
feature could not be demonstrated at all.

**Independent Test**: On the development build (host tests for the store): every type in the list
produces a notification whose tap opens the screen of User Story 2; the tool is absent from the
staging and production builds.

**Acceptance Scenarios**:

1. **Given** the development build and a signed-in person, **When** they open Account → Developer tools → Simulate a notification, **Then** they see the 12 types plus "Unknown type", each filled with identifiers of records on the phone where the type needs one (or a random identifier when none exists, to exercise the "not on the phone" path).
2. **Given** a type chosen, **When** they tap "Post", **Then** a notification with generic text appears on that type's channel, and tapping it routes as in User Story 2.
3. **Given** the development build signs in, **When** registration runs, **Then** it registers a fake push address that the local backend accepts.
4. **Given** a staging or production build, **When** it is inspected, **Then** the tool cannot be reached and no fake provider is bound.

### Edge Cases

- **No push provider in this build** (staging and production until the Firebase phase lands): no Notifications row, no explanation sheet, no permission request, no registration, no channels shown to the person; the rest of the app is unchanged.
- **Two people share a phone**: A signs out (push address discarded, notifications cleared), B signs in and registers a new push address for B's session; the server moves any address it already knew to B's session. A notification of A's is never shown to B.
- **Sign-out offline**: the `DELETE` and the server sign-out both fail; the push address is still discarded on the phone, so any later message to it fails at the push service and the server prunes the registration; the server ends the session on its own idle expiry.
- **A notification tapped after its account signed out**: the app shows sign-in; nothing routes.
- **A notification shown before a language change**: keeps the text it arrived with; new ones arrive in the profile language. Text follows the profile language, which can differ from an app-language override.
- **Session id answered `404`**: re-read the stored session once; never sign out because of it.
- **Rate limit on registration** (20 per hour per session, shared with removal): the background job waits the server's time; the app itself never sends more than one registration per foreground per day plus one per sign-in, new address or permission change.
- **Permission revoked while signed in**: at the next foreground the registration is removed; nothing is shown to the person apart from the card on the Notifications screen.
- **Channel blocked by the person** (one kind off on this phone only): the app changes nothing; the server still sends; the system drops it. The in-app switch is the account-wide control.
- **Message for a type the app does not know** (a newer server): shown as received when it has text; tap opens Home.
- **Message with `schema` other than "1"**: same as an unknown type.
- **Identifiers missing or not UUIDs**: tap opens the type's list screen (recurring, budgets, wallets, exports) or Home; never an error.
- **Tap while the occurrence was already confirmed or skipped**: the occurrence screen shows its current state; nothing is changed.
- **Tap for a wallet the person no longer belongs to**: after the read, the wallet is gone from the phone; the wallet screen shows its existing "no longer available" state.
- **Budget deleted since**: the budget screen's existing "gone" state.
- **Export expired or gone**: the export screen's existing handling (spec 016).
- **Deletion cancelled since the reminder**: the deletion screen shows the normal (not pending) state.
- **Tap while an "Opening…" read is running for another tap**: the newer tap replaces the older one.
- **Tap while the app requires an update**: dropped; the blocking screen stays.
- **Tap while the app is on a screen with unsaved edits**: the target screen opens on top; the edited screen stays underneath with its edits.
- **Many notifications at once** (for example, after quiet hours end): the system groups them; same-subject ones replace each other; the app adds no summary of its own.
- **Preferences changed on two phones**: the second save gets the conflict message (US3-4).
- **Quiet hours across midnight or not** (22:00–08:00, 13:00–15:00): both are valid; only start equal to end is refused.
- **Profile time zone different from the phone's**: the screen names the profile time zone; quiet hours are applied by the server in it.
- **Account pending deletion**: notifications still arrive (the reminder is the point); the settings screen works.
- **Process killed while a registration is pending**: the background job runs when the system allows.
- **Notification arrives while nobody is signed in** (should not happen: the server sends only to live sessions): in the foreground it is dropped; a tap on one the system showed opens sign-in and is forgotten.
- **Large font / RTL**: every new screen and sheet reads at 200 % font and mirrors in Arabic; times stay Western digits, left to right.
- **Spoofed tap**: another app on the phone could open the app with notification-like data; the app only navigates to a screen of the signed-in person's own data and never acts, so nothing leaks or changes.

## Requirements *(mandatory)*

### Functional Requirements

**Registration**

- **FR-001**: When a person is signed in, this build has a push provider and notifications are allowed for the app on this phone, the app MUST register the phone's push address with `PUT /me/devices/{id}`, where `{id}` is the current stored device session id and the body carries only `push_token`.
- **FR-002**: The app MUST register: after every sign-in; whenever the provider reports a new push address; when notifications become allowed (dialog answer or found allowed at foreground); and at a foreground when the last successful registration of this session and this push address is 24 hours old or more. It MUST NOT register more often than that.
- **FR-003**: Registration and removal MUST run in the background, survive the app being closed, wait for a connection, retry `RATE_LIMITED` after the server's wait and transient failures (offline, timeout, `5xx`) with backoff, and never block or slow a screen. A registration MUST use the session stored when it runs, not when it was requested; when nobody is signed in it does nothing.
- **FR-004**: The phone MUST remember, per signed-in person, only: the session id last registered, a non-reversible fingerprint of the push address, and the time of the last success. It MUST NOT store the push address itself (the provider owns it).
- **FR-005**: On `NOT_FOUND` the app MUST re-read the stored session once: if its id differs from the one sent, register once for it; otherwise stop registering until the next sign-in or new push address. `NOT_FOUND` MUST NOT sign the person out.
- **FR-006**: When notifications are not allowed for the app on this phone (permission refused or app notifications blocked) and this session is registered, the app MUST call `DELETE /me/devices/{id}` once (at the next foreground) and forget the registration; it registers again per FR-002 when they are allowed again.
- **FR-007**: Signing out MUST, in this order: (a) while the stored session is still valid, make one best-effort `DELETE /me/devices/{id}` bounded to 2 seconds and only when this session is registered; (b) the existing best-effort server sign-out; (c) the existing local sign-out, during which the app discards the push address with the provider, removes every displayed notification of the app and clears the registration record and the de-duplication list. The added step MUST NOT make sign-out wait longer than the existing 5-second bound in total plus 2 seconds, and MUST NOT run when the session ended remotely (that path does only (c)).
- **FR-008**: The push address MUST NOT be logged, shown, put in crash reports or analytics, or written anywhere by the app; the app's own records hold only its fingerprint (FR-004). Logs about registration carry only the outcome.
- **FR-009**: Registration MUST NOT be gated on email verification or plan.

**Permission**

- **FR-010**: The app MUST NOT request the notification permission at launch, at sign-in, or without the person's action. On Android 13 and later it MAY request it only (a) from "Turn on" on the Notifications screen or (b) from "Turn on" in the explanation sheet.
- **FR-011**: The explanation sheet MUST be offered at most once per installation, only when the permission can still be asked, right after the first of: a recurring rule created successfully, a budget created, a shared wallet joined or opened from an invitation link the person already accepted. It explains in one or two sentences what the person will be told (things due, budgets over their limit, shared wallets) and that the notifications never show amounts. "Not now" closes it for good. When another sheet or dialog is already showing at that moment, the sheet is not shown and not counted as offered (the next moment may offer it).
- **FR-012**: When the system will no longer show its dialog (refused twice, or blocked in settings) or on Android 12 and earlier with notifications blocked, the app MUST offer "Open settings", opening the system notification settings of the app. The app learns the new state at its next foreground or when the Notifications screen resumes.

**Notification preferences screen**

- **FR-013**: The Account screen MUST offer "Notifications" when this build has a push provider; it opens the Notifications screen.
- **FR-014**: The screen MUST read `GET /me/notification-preferences` when it opens and on "Try again"; while reading it shows loading; offline or on timeout it shows "Needs a connection" with "Try again". Nothing is cached on the phone.
- **FR-015**: The screen MUST show, in this order: the phone-status card when notifications are not allowed on this phone (FR-020); six switches — Recurring items due, Budget alerts, Shared wallets, Plan and billing, Exports, Account deletion — each with a one-line description of what it covers (from the contract's category descriptions, without "InstaPay"); a "Quiet hours" switch with "From" and "To" times; a note naming the profile's time zone (omitted while the profile is not on the phone yet) and that held notifications arrive when quiet hours end; a note that these settings apply to every phone signed in to the account and that emails are not affected; and "Notification settings on this phone" (FR-021).
- **FR-016**: Times MUST be chosen from whole quarter hours (00:00 to 23:45), shown in 24-hour form with Western digits, left to right in every language. Start equal to end MUST make "Save" unavailable with the message "Start and end must be different."; times stay editable while quiet hours are off.
- **FR-017**: "Save" MUST be available only when the values differ from those last read or saved, are valid, and no save is in flight; it sends `PUT /me/notification-preferences` with every member and the last-read `version`, once per tap, never queued and never retried automatically. Success shows the answer's values and "Saved".
- **FR-018**: `VERSION_CONFLICT` MUST replace the edits with the answer's `current` (its version becomes the last-read one) and show "These settings were changed on another device. Check them and save again." `RATE_LIMITED` MUST show "Too many changes. Try again in <n> minutes." (rounded up; "in a minute" under 60 seconds; "later" without a wait time). Offline or timeout MUST show "Needs a connection" and keep the edits. `VALIDATION_FAILED` and any other refusal MUST show the general failure with the request id. `UNAUTHENTICATED` and update-required follow the app-wide handling.
- **FR-019**: Leaving the screen with unsaved changes MUST ask "Discard your changes?" with "Discard" and "Keep editing".
- **FR-020**: When notifications are not allowed on this phone, the screen MUST show a card "Notifications are off on this phone" with "Turn on" (when the system can still ask) or "Open settings" (otherwise). The switches stay usable (they apply to the account).
- **FR-021**: "Notification settings on this phone" MUST open the system notification settings of the app, where each category is a separate channel.

**Channels and display**

- **FR-022**: On Android 8 and later the app MUST create six notification channels whose ids are the category values (`recurring_due`, `budget_exceeded`, `sharing`, `billing`, `export_ready`, `deletion_reminder`), named and described in the app's language, at default importance, with lock-screen visibility "private". It MUST create them at app start (before any message can be shown) and rename them when the app language changes; it MUST never delete them. Below Android 8 there are no channels and messages use the system defaults.
- **FR-023**: A message received while the app is in the foreground MUST be shown as a system notification on the channel named by its `category` (falling back to the type's category from the contract table), with exactly the title and text received, a tag equal to the type's collapse key from the contract table (so a newer message about the same subject replaces it, as the server's background messages do), a public version reading "New notification" for secure lock screens, and auto-cancel on tap. The app MUST NOT show an in-app banner or replace the text.
- **FR-024**: A message whose `notification_id` was already shown by the app MUST NOT be shown again; the app remembers the last 100 shown ids for the signed-in person and forgets them at sign-out.
- **FR-025**: A foreground message whose `type` is unknown or whose `schema` is not "1" MUST still be shown when it carries a title and text, and its tap opens Home. A foreground message with no title or text MUST be ignored.
- **FR-026**: A foreground message of a known type MUST also start a background read of the scope its data concerns (FR-031's table), without any screen change.
- **FR-027**: A foreground message received while nobody is signed in MUST be dropped.
- **FR-028**: The app MUST never compose notification text of its own from records (no names, amounts, dates, notes); it shows only what was received (or, in the developer tool, fixed generic text).

**Tap routing**

- **FR-029**: A tap MUST be handled only while a person is signed in (every notification of a previous person was cleared at their sign-out, FR-043); when nobody is signed in it MUST show sign-in and forget the tap (it is not held across sign-in, unlike invitation links). A tap MUST only navigate: it never confirms, skips, joins, declines or changes anything.
- **FR-030**: A tap's data MUST be validated before use: `type` known, `schema` "1", and each identifier the type needs present and a UUID. An unknown type or wrong schema opens Home; a known type with a missing or malformed identifier opens that type's fallback screen in the table (FR-031).
- **FR-031**: A valid tap MUST open, after the read in FR-032:

  | Type | Read first | Opens | Fallback (bad id) |
  |---|---|---|---|
  | `occurrence_due`, `occurrence_auto_confirm_refused` | the wallet `wallet_id` | the occurrence `occurrence_id` (not its confirm form) | recurring, all wallets |
  | `occurrences_due_summary` | everything | recurring, all wallets (due items) | — |
  | `budget_exceeded` | the person's own scope | the budget `budget_id` | budgets |
  | `invitation_received` | none | the invitation notice (FR-034) | the invitation notice |
  | `member_role_changed`, `wallet_ownership_received` | the wallet `wallet_id` | the wallet `wallet_id` | wallet list |
  | `member_removed` | everything | wallet list | wallet list |
  | `subscription_ending`, `subscription_lapsed` | entitlements refresh | Plan | — |
  | `export_ready` | none | the export `export_id` (spec 016) | exports list (spec 016) |
  | `deletion_reminder` | account refresh | account deletion | — |

- **FR-032**: For a type whose table row names a read, the app MUST run that read forced (not skipped for being recent). When the row opens a record (occurrence, budget, wallet) that is already on the phone, the target screen MUST open at once and the read runs in the background (the screen updates from the phone's data as it lands). Otherwise the app MUST show an "Opening…" screen while the read runs, waiting at most 10 seconds counted from when that screen starts its read; "Cancel" returns to where the person was (the read may finish in the background; nothing opens afterwards); when the read finishes, fails, is offline or times out, the target screen MUST open in place of the "Opening…" screen (so Back returns to where the person was). The target screen shows its own existing state when the record is still missing.
- **FR-033**: A tap arriving while an "Opening…" screen shows MUST replace it. A tap MUST be handled once: the app forgets it after routing, also across activity re-creation. While the app shows its blocking "update required" screen, taps MUST be dropped.
- **FR-034**: The invitation notice MUST say "You've been invited to a shared wallet", "The invitation was sent to <own address>. Open the email and tap the link in it to see and answer the invitation.", with "OK" returning to where the person was. It MUST NOT show or request anything about the invitation (no call, no token, no wallet name).

**Developer tools and E2E**

- **FR-035**: Development builds MUST bind a fake push provider that hands out a stable fake push address per installation (accepted by the backend's format: printable ASCII, at most 4,096 characters) and can be asked to rotate it.
- **FR-036**: Development builds MUST offer "Developer tools" on the Account screen and, in it, "Simulate a notification": the 12 types plus "Unknown type", identifiers filled from records on the phone where available (random UUIDs otherwise, labelled "not on this phone"), and "Post", which hands the data and fixed generic text in the app language to the same receiving path as a real foreground message. In staging and production builds the row and the tool MUST NOT be reachable and the fake provider MUST NOT be bound (the dependency graph binds "no provider", FR-046), as for the existing developer tools.
- **FR-037**: Maestro flows for each story MUST be written (driving the simulate tool for display and routing) and marked "deferred: E2E on owner's device"; none is run by this spec (owner policy).

**Common**

- **FR-038**: Logs MUST carry at most the message type and the outcome; never the push address, a payload value, an identifier, a title or text (P-09.2, P-13.3).
- **FR-039**: Requests MUST carry only the schema's members, the standard headers, and branch only on the problem `code` (P-02). The registration body MUST NOT carry platform, app version or language.
- **FR-040**: Every new screen and sheet MUST be built from design-system components, in English LTR and Arabic RTL, readable at 200 % font, with 48 dp touch targets, content descriptions for icon-only controls, and stable test tags; switches are announced with their title and state.
- **FR-041**: All new text MUST come from English and Arabic string resources, including channel names and descriptions and the developer tool's generic text.
- **FR-042**: iOS MUST compile with no push behaviour: no provider, no channels, no permission, no display (P-15).
- **FR-043**: Displayed notifications, the de-duplication list and the registration record MUST be cleared at sign-out and session end (FR-007), so nothing of one person reaches the next on a shared phone.
- **FR-044**: The app MUST treat the data of a tap as untrusted input (FR-030) because it reaches the app's launcher screen, which other apps can open.
- **FR-045**: The push provider MUST be the only part that knows the push service: the rest of the feature depends on a provider seam (current push address, rotate/discard, new-address notice) and a receiver entry point (a received message's data, title and text), and builds and passes its tests with a fake provider and no Firebase dependency.
- **FR-046**: Until the owner approves the Firebase Messaging dependency and supplies the Firebase configuration, staging and production builds MUST bind "no provider" (FR-013's row hidden, no sheet, no request, no registration). The Firebase wiring — the dependency, the Google Services build plugin, a configuration file per flavor, the messaging service that forwards new addresses and foreground messages to the receiver, and the manifest defaults (fallback channel, small icon) — is a separate, gated phase.

### Key Entities *(include if feature involves data)*

- **Push Registration (client view)**: the record the phone keeps of its own registration: session id registered, push-address fingerprint, last success time. Never the push address. Cleared at sign-out.
- **Notification Preferences**: six category switches, quiet hours (on, from, to), version, last update. Read and saved whole; held only in the screen's state.
- **Push Message**: the data of one received or tapped notification: schema, type, category, notification id, and the identifiers its type carries (wallet, occurrence, budget, invitation, export, role). Never stored, except its notification id in the de-duplication list.
- **Notification Channel**: one per category, on the phone; the person's per-phone control.
- **Permission state**: allowed, can be asked, or must be changed in settings; plus whether the explanation sheet was already offered on this installation.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets the app reach a person who is not looking at it: their phone registers where it can be
reached for as long as they are signed in, shows the server's short, private messages in the right
system channel, and opens the right screen when tapped; and the person chooses which kinds they
hear about and when they do not want to be disturbed. The parts that need Firebase are isolated so
everything else ships and is tested before the dependency is approved.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Push Registration**, **Notification Preferences**
("الإشعارات" screen), **Push Notification**, **Device Session** ("session"/"device"), **Wallet**,
**Budget**, **Occurrence** (UI "recurring item"), **Plan**, **Export**. Category names in the UI:
"Recurring items due" / "عناصر متكررة مستحقة", "Budget alerts" / "تنبيهات الميزانية", "Shared wallets"
/ "المحافظ المشتركة", "Plan and billing" / "الخطة والفوترة", "Exports" / "التصدير", "Account deletion"
/ "حذف الحساب". "Quiet hours" / "ساعات الهدوء". Never in user-facing text: "token", "push token",
"FCM", "Firebase", "InstaPay". No new glossary term (the "push address" wording exists and is not
needed on screen).

### 3. Data model

No new syncable entity and **no Room change** (stays v10). Notification preferences and push
registration are account data, online-only (P-04.6, backend R-6).

| Data | Where it lives on the phone | Cleared |
|---|---|---|
| Registration record (session id, push-address fingerprint, last success) | key-value store | sign-out, session end |
| Recently shown notification ids (≤ 100) | key-value store | sign-out, session end |
| Explanation sheet already offered | key-value store (per installation) | never (app data clear) |
| Push address | the push provider only (Firebase, or the fake in development) | discarded at sign-out |
| Preferences being edited | the screen's state | screen closed |
| Pending tap | memory, until routed | routed, sign-out |
| Notification channels | the system | never deleted |

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| The push address never appears in logs, stored data or `toString` | P-09.2, FR-004, FR-008 | log-capture test over register/rotate/sign-out; registration-store test; redacted `toString` |
| The registration body is exactly `{push_token}` and the path id is the stored session id | P-02.2, FR-001, FR-039 | contract test |
| At most one registration per foreground per 24 h when nothing changed | FR-002 | registration service test on virtual time |
| Sign-out order: `DELETE` (bounded) before the session is wiped; push address discarded and notifications cleared after | FR-007, FR-043 | sign-out use case test; cleaner test |
| No `DELETE` on a remote session end | FR-007 | session-end test |
| `NOT_FOUND` never signs out and retries at most once | FR-005 | registration service test |
| Every one of the 12 types parses to its message; unknown type / schema → Home; bad id → fallback | FR-030, FR-031 | payload parser and routing table tests |
| A tap only navigates (never an action intent or call other than the reads of FR-031) | FR-029 | open-service test with fakes recording calls |
| Foreground display uses the category channel and the contract collapse key as tag | FR-023 | display test (Robolectric) and collapse-key table test |
| A notification id is shown once | FR-024 | receiver test |
| Preferences: whole object with last-read version; 409 → `current` replaces edits; never queued or retried | FR-017, FR-018, P-04.4 | contract tests; store tests |
| Quiet-hour times are quarter hours and start ≠ end before sending | FR-016 | reducer test; field test |
| No permission request without a person's tap; sheet offered once per installation | FR-010, FR-011 | gate test; store tests |
| Channels: six, ids = categories, private lock-screen visibility, never deleted | FR-022 | channel test (Robolectric) |
| No provider, tool or fake in staging/production; no Firebase import outside the gated adapter | FR-036, FR-045, FR-046 | flavor/graph test; layering test |
| `notifications/data` imports no `domain`/`ui`; `core` and the sync core import nothing of `notifications` | P-10.6, P-10.7 | layering test |

### 5. API

All under `/api/v1`, frozen in `notifications.yaml` v1.14 (backend spec 023, uncommitted at `e8c308a`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Register / rotate / refresh | `PUT /me/devices/{id}` (`registerPushDevice`) | `{push_token}` | 200 `PushRegistration`; 401 (app-wide); 404 `NOT_FOUND` (FR-005); 422 `VALIDATION_FAILED` (logged, not retried); 426 (app-wide); 429 `RATE_LIMITED` + `Retry-After` (FR-003); 400/406/415/500 (retried as transient for 5xx only) |
| Remove | `DELETE /me/devices/{id}` (`unregisterPushDevice`) | — | 204; 401; 404 (treated as done); 429 (FR-003); others ignored at sign-out |
| Read preferences | `GET /me/notification-preferences` | — | 200 `NotificationPreferences`; 401; 426; others → general failure |
| Save preferences | `PUT /me/notification-preferences` | `{version, categories{6}, quiet_hours{enabled,start,end}}` | 200; 409 `VERSION_CONFLICT` + `current` (FR-018); 422 `VALIDATION_FAILED`; 429 `RATE_LIMITED`; 401; 426 |
| Push payload (not HTTP) | `PushPayload` data block, schema `"1"` | — | 12 types (FR-031); unknown ignored for routing (FR-025, FR-030) |

Reads used by tap routing are existing calls: the sync runner's reads (specs 004/009/011),
`GET /me/entitlements` (spec 005), the account refresh (spec 002), and the export read (spec 016).

#### Contract changes since last spec

Baseline `saveme-backend@b63e917` (spec 015) → `e8c308a` (+ uncommitted backend work for specs 022 and 023):

- **Error codes**: none added, removed or changed. `EXPORT_IN_PROGRESS`'s meaning now says the body's `current` is the running export — follow-up: not in scope of this spec (C15, spec 016).
- **API conventions**: two new sections — "A versioned `/me` resource that is not syncable" (handled by FR-014, FR-017, FR-018: preferences are read on demand, saved with `version`, `409` with `current`, nothing merged, not on the feed) and "The device id on `/me/devices/{id}`" (handled by FR-001, FR-005: the id is the caller's own current session id; anything else is `404`).
- **Shared components**: the Conflict response lists `EXPORT_IN_PROGRESS`; `Problem.current` is also sent on it — follow-up: not in scope of this spec (C15).
- **New paths (uncommitted)**: `notifications.yaml` v1.14 — this spec's whole API (above). Rerun the contract check after the backend commits spec 023 (task T001) and before the live QA.
- **Client mismatches**: none (every client code exists in the catalogue with the same retry class).
- **Re-checked 2026-10-06 (analyze)**: backend still at `e8c308a`, 023 still uncommitted; the working-tree `openapi.yaml` now says `info.version: v1.15` because backend spec 024 (Google Play billing: `google-play.yaml`, `pubsubPush` scheme) was added on top — unrelated to this spec; `notifications.yaml` is unchanged (still "New in v1.14", same 12 types, collapse keys and schemas).

### 6. Authorization and entitlements

Any signed-in person registers their own current session and reads and saves their own
preferences; nothing is gated on plan or email verification (backend: management of one's own
account). Tap routing opens only screens of the signed-in person's own data; what a screen shows
is governed by that screen's existing rules (wallet role, plan notes). The `role` in
`member_role_changed` is not used for any decision; the wallet's role comes from the read.

### 7. Events

The client publishes no domain events. It consumes: the app-wide session-ended event (discard the
push address, clear notifications), sign-in (register), foreground (re-register at most daily,
notice a permission change), the push provider's new-address notice, and received messages. The
existing screens' "rule saved", "budget created" and "joined" outcomes are the moments for the
explanation sheet (FR-011), observed at the navigation layer.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Registration | Unit (registration service, coordinator, sign-out participant, permission gate) + contract (MockEngine from `notifications.yaml` examples) + live QA (curl, captured fixtures) + E2E (deferred) | tests; `.maestro/017-push/US1-*` |
| US2 Tap routing | Unit (payload parser, routing table, open service, "Opening…" store) + Robolectric (launcher-intent extras → tap) + E2E via the simulate tool (deferred) | tests; `US2-*` |
| US3 Preferences | Reducer + store + contract (GET/PUT, 409, 422, 429) + live QA + screenshots (EN, AR, 200 %) + E2E (deferred) | tests; `US3-*` |
| US4 Display & privacy | Robolectric (channels, posted notification, cancel-all), unit (receiver de-duplication, collapse key), log capture | tests |
| US5 Developer tools | Store + graph/flavor tests; E2E (deferred) | tests; `US5-*` |

Owner policy: no emulator, Maestro or device run. Host tests plus a live-backend contract QA of the
four endpoints (curl with throwaway accounts once backend spec 023 is committed; real answers
captured as fixtures replayed through the real client). Real Firebase delivery is checked on the
owner's device only after the gated FCM phase.

### 9. Open questions

- **Firebase approval and configuration (owner, blocking only the gated FCM phase)**: see Assumptions "Owner must approve or supply".
- **Backend contract gaps (non-blocking)**: see Assumptions "Backend gaps".
- **In-app invitation inbox** (backend spec 023 open question 2): the invitation tap opens a notice only. Non-blocking.
- **Arabic copy** for the new strings: assumed defaults for owner review. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After sign-in with notifications allowed, the phone is registered within 1 minute when online, without any screen waiting for it.
- **SC-002**: 0 permission requests happen without a tap by the person; the explanation sheet is offered at most once per installation.
- **SC-003**: For each of the 12 message types a tap opens the screen in the routing table; 100 % of taps on records the phone already holds open it in under 1 second, and taps needing a read open within 10 seconds or show the target screen's own state.
- **SC-004**: A person changes any notification setting in one screen with one save.
- **SC-005**: 0 occurrences of the push address, a payload value or a notification text in logs, stored data or crash reports across the feature's test suite.
- **SC-006**: After sign-out, 0 notifications of the app remain in the shade and 0 later messages are shown on that phone for the signed-out person.
- **SC-007**: Two messages about the same subject leave exactly one notification; the same message delivered twice is shown once.
- **SC-008**: Sign-out takes no more than 2 seconds longer than before this feature in the worst case (server unreachable), and no longer when the server answers.
- **SC-009**: The feature's host test suite passes with no Firebase dependency on the classpath.

## Assumptions

### Design defaults

1. **The Firebase seam** (clarification 1): a provider interface and a receiver entry point in shared code; Android display, channels and permission through the system notification service (no new dependency); the Firebase adapter lives only in the Android app module and arrives in the gated phase. Staging and production show no push UI until then; development uses the fake provider.
2. **Permission moments** (clarification 2): the Notifications screen and one explanation sheet after the first recurring rule saved, budget created or shared wallet joined. Not at launch, not at sign-in, not after the first transaction.
3. **Preferences online-only with one Save** (clarification 3); no auto-save per switch (each save costs one of 30 per hour and could conflict mid-edit).
4. **Conflict resolution**: the server's `current` replaces the edits; the person re-applies (contract "show current and let the person re-apply").
5. **Tap reads first** (clarification 4), bounded to 10 seconds, then the target screen's own states.
6. **Sign-out** (clarification 5): bounded `DELETE`, then server sign-out, then the push address is discarded and notifications cleared.
7. **Per-phone control is the system's**: there is no in-app "notifications on this phone" switch; the system permission and channels are that control, and the app removes its registration when notifications are off on the phone (FR-006), as the contract asks ("call `DELETE` only when the person turns notifications off for this phone").
8. **Re-registration at most daily** on foreground (contract: "re-register on every app start that has a token"; daily is enough for the 270-day window and spares the 20-per-hour budget).
9. **Foreground messages** show as ordinary system notifications with the received text; no in-app banner; the app never writes its own text (the backend allows replacing it; not doing so keeps one source of copy and of language).
10. **Channels at default importance**, private on the lock screen; no custom group summary; same-subject messages replace each other by the contract's collapse key.
11. **24-hour times on quarter hours** with Western digits, picked from a list (no new date/time picker dependency; the existing Western-digit date components have no time picker, so a quarter-hour field is added to the design system).
12. **Invitation notice** names the person's own address and sends them to their email; no "open email app" button (no new platform integration).
13. **Developer tools on the Account screen** in development builds only (today they are reachable only when signed out), so the simulate tool can run signed in.
14. **No Room change**; small records in the existing key-value store.
15. **Export routing** targets spec 016's export screen; if spec 016 is not merged when this is implemented, the export tap opens the Account screen until it is (task notes).

### Owner must approve or supply (before the gated FCM phase)

1. **Dependency approval** (P-14.5): `com.google.firebase:firebase-messaging` (with the Firebase BoM if versions are managed that way) in `:androidApp` only, and the Gradle plugin `com.google.gms.google-services`. Firebase Messaging brings Firebase Installations and Google Play services' cloud-messaging client transitively.
2. **A Firebase project** (one per environment, or one shared) with an Android app registered for `com.fares.saveme` (all three flavors use this application id), and its **`google-services.json` per flavor** at `androidApp/src/dev/`, `androidApp/src/staging/`, `androidApp/src/prod/`. The same project's service-account key goes to the backend (backend spec 023 open question 1).
3. **Privacy commitments**: Firebase Installations creates an installation identifier on the phone; the backend's privacy commitments already name FCM as the push processor; confirm no Firebase Analytics is added (it is not).
4. **Arabic copy review** of the new strings.

### Brief reconciliation

The brief (C16) predates backend spec 023; where they differ, the frozen contract wins:

1. `PUT /devices/{id}/push-token` → `PUT /me/devices/{id}` with `{id}` = the caller's own current session id, plus `DELETE` (backend R-4).
2. "InstaPay claim approved/rejected" is **not pushed** (claims are web-only and bound to an address, backend FR-031); nothing in this spec handles it.
3. "Invitation received … opens the invitation" → the push carries no token, so the tap opens the invitation notice (backend open question 2); the brief's E2E checkpoint is read as "the notification opens the invitation notice".
4. Added by the backend and handled here: budget exceeded, wallet ownership received, export ready, deletion reminder, the recurring summary, and the refused automatic confirmation.

### Backend gaps (non-blocking)

1. Backend spec 023 is uncommitted; this spec binds to the working-tree `notifications.yaml` v1.14. Recheck after it is committed (T001).
2. The FCM message sets no `android.notification.visibility`; the client relies on its channels' private lock-screen visibility. Setting `visibility: PRIVATE` on the server would make it independent of channel creation.
3. The FCM message sets no `click_action`, so a background notification's tap opens the launcher activity with the data as extras — the exported entry point other apps can also open; the client validates and only navigates (FR-030, FR-044).
4. The data block does not carry the collapse key; the client derives it from the contract's table for foreground messages. A change to the table needs a client change.
5. There is no way to read back a registration (`PUT` only); a registration pruned by the server (invalid address) is restored only by the next daily re-registration or new address.
6. The data block carries no account identifier; a notification shown before an account switch cannot be attributed, so the client clears all of them at sign-out.
7. No test-push endpoint (backend FR-050); real delivery can be checked only with Firebase configured, on a device.
8. Push text follows the profile language, which can differ from the app's language override; the app shows it as received.
