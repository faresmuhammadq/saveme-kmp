# Feature Specification: Sharing & Invitations (client)

**Feature Branch**: `013-sharing`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "specs/briefs/C09-sharing.md" — client brief C9, wave 3. Pairs with backend B9 → spec 016 (built, committed `e4dc4c6`; `sharing.yaml` v1.9, frozen for C9 and unchanged since).

**Backend contract**: `saveme-backend@26e7c44`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/sharing.yaml` (v1.9: `GET`/`PUT`/`DELETE /wallets/{id}/invitations[/{iid}]`, `POST /invitations/preview|accept|decline` with `{token}` in the body, `PATCH`/`DELETE /wallets/{id}/members/{uid}`, `POST /wallets/{id}/leave`, `GET /wallets/{id}/activity`), `paths/sync.yaml` (`invitation` rows on the **wallet** feed, never a batch item; own `wallet_member` rows on the user feed; a tombstoned own membership = purge), `components/schemas.yaml` (`Problem.feature` on `FEATURE_NOT_ENTITLED`), `docs/api-conventions.md`, `docs/openapi/error-catalog.yaml` (no new code in spec 016).
- Backend behaviour: `saveme-backend/specs/016-sharing-invitations/summary.md`, `contracts/sharing-api.md` ("What C9 asked for, answered"), `docs/adr/0017` (memberships on the member's user feed), `docs/adr/0020` (Activity Entry), `docs/privacy-commitments.md` ("Who can see a shared wallet", including the honest limit that a removed member's phone keeps what it already downloaded).
- Glossary (`saveme-backend/docs/glossary.md`): **Invitation**, **Wallet Member**, **Activity Entry**, **Plan Feature**; roles owner / editor / viewer. Banned in any user-facing string: "invite link", "join request", "collaborator", "participant", "friend", "group", "household", "family", "token".
- Builds on spec 001 (design system: `MemberRow`, `RolePill`), 003 (mail links into the app: `DeepLinkBus`, link host per flavor, Paste link dev tool), 004 (sync engine: queue, refusals, status), 005 (C3 `Entitlements`: `can(sharing.enabled)`, `quota(sharing.max_members_per_wallet)`, refresh on `FEATURE_NOT_ENTITLED`), 006 (C4 wallets: `wallet_member` table, members list, `my_role`, owner-only actions, online-only ownership transfer), 007/008/010 (categories, transactions and recurring rules shown through the activity), 009 and 011 (C14 feeds, appliers, `WalletScopes`, heads, equal-version member re-stamps; `invitation` rows are skipped as unknown until this feature, which clears every wallet scope's feed position once so they are read from the start) and 012 (budgets: a lost wallet stops counting).

## Clarifications

### Session 2026-10-05

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Should creating or withdrawing an invitation work without a connection? → A: Yes. Both are written on the phone and queued (P-04.2, P-04.5), sent one by one and never inside a batch upload, by the foreground or the background sync alike. A link invitation's link can be shared only when the server's answer reaches the app while it runs; if that answer arrived while the app was closed, or was lost, the invitation offers "Replace link". (FR-004, FR-005, FR-007)
- Q: Should changing a role, removing a member, leaving, joining and declining work without a connection? → A: No. They are online-only actions that answer "Needs a connection" offline, are never queued and never retried automatically — as ownership transfer already is (spec 006): the server writes memberships and decides the plan and the people count at that moment. (FR-013, FR-017–FR-020)
- Q: How does a person get back to an invitation they opened but did not answer, including when nobody was signed in? → A: The secret is held in memory for the current app session only — never stored. Signed out: the entry screen says to sign in or create an account, and the invitation screen opens right after the next sign-in. "Not now": a Home card "You have an invitation to answer" reopens it. Closing the app drops it; tapping the link again restores it. (FR-011, FR-013)
- Q: Where do the members and activity screens open from? → A: From the wallet's detail only: its members section (spec 006) gains "Manage members" for the owner or "See members" for others, showing the number of pending invitations, plus an "Activity" row for every member. No new Home row; Home only gains the held-invitation card. (FR-016, FR-024)
- Q: How is read-only access explained to someone the owner's plan turned into a viewer? → A: Every viewer sees a note on the wallet's detail and transactions screens that they can view but not change the wallet; the note names the owner's plan only when an activity page read in this app session shows the plan made that change. The owner sees the plan's number of people on the members screen. A change queued before the demotion and refused with `FORBIDDEN` shows the viewer explanation instead of a bare failure. (FR-027, FR-028)

## User Scenarios & Testing *(mandatory)*

A wallet is often shared: a couple's household spending, a gam'eya organiser's ledger, a parent
following a student's allowance. The owner invites someone with a role — editor (records
transactions) or viewer (reads only) — by email or by a link sent in a chat; the invitee opens it
in the app and joins; from then on both see the same wallet. The owner can change roles, remove
people and withdraw invitations; members can leave; everyone can see who did what. When someone
loses access, the wallet's data leaves their phone on the next sync. Sharing is a plan feature:
the owner's plan decides whether invitations may be sent and how many people a wallet may have.

### User Story 1 - Invite someone to my wallet by email (Priority: P1)

As a wallet owner on a plan that includes sharing, I open my wallet's members, choose "Invite",
enter the person's email address and a role, and send. The invitation appears as pending at once
(offline too) and is sent when the phone is online; the server mails the link.

**Why this priority**: It is the brief's first step and the most common way to share; without it
nothing else in the feature can happen.

**Independent Test**: As an owner on Plus, invite `karim@example.com` as an editor; the members
screen lists "karim@example.com · Editor · Pending · Expires <date>"; the backend holds the
invitation and the mail is queued. Offline, the same invitation shows "Waiting to sync" and is sent
when the connection returns.

**Acceptance Scenarios**:

1. **Given** an owner with a verified address on a plan with sharing and room for another person, **When** they invite an address as editor or viewer, **Then** the invitation is listed as pending with its address, role and expiry date, and it is sent to the server.
2. **Given** the same owner offline, **When** they send an invitation, **Then** it is listed with "Waiting to sync" and sent once the phone is online, without the owner doing anything else.
3. **Given** the owner's plan does not include sharing, **When** they open the invite action, **Then** the app explains that sharing is not included in their plan, offers to open the Plan screen, and sends nothing.
4. **Given** the wallet already has as many people as the owner's plan allows (owner, members and unexpired pending invitations counted), **When** the owner opens the invite action, **Then** the app explains that the wallet has reached the number of people the plan allows and that withdrawing a pending invitation frees a place.
5. **Given** an owner whose address is not verified, **When** they try to invite, **Then** the verify prompt appears and nothing is queued.
6. **Given** a pending invitation to the same address in this wallet, **When** the owner invites that address again, **Then** the app asks to confirm that the earlier invitation stops working, and on confirmation sends the new one.
7. **Given** the server refuses a queued invitation (plan, number of people, verification, role), **When** the answer arrives, **Then** the invitation shows "Not sent" with the reason in the app's own words and offers "Discard"; a plan or verification reason also offers "Try again".
8. **Given** an editor or viewer of the wallet, **When** they open the members screen, **Then** they see the members and pending invitations but no invite, revoke, role or remove controls.

---

### User Story 2 - Join a wallet from an invitation (Priority: P1)

As the invited person, I tap the link in the mail or chat; the app opens on an invitation screen
that shows the wallet's name, who invited me, my role and when the invitation ends; I join (or
decline). The wallet appears in my list and fills in as it syncs.

**Why this priority**: It completes the brief's integration checkpoint (invite, accept as the
second account, both see a new transaction). Without it an invitation is useless.

**Independent Test**: With a second account signed in, open an invitation link from account A;
the invitation screen shows "Home · Mona invited you as an editor · Until <date>"; tap "Join";
the wallet "Home" opens and its transactions appear after sync; a transaction A adds later appears
on B's phone after a refresh, and one B adds appears on A's.

**Acceptance Scenarios**:

1. **Given** a signed-in person, **When** they open a valid invitation link, **Then** the app shows the invitation screen with the wallet name, the inviter's name (or "Someone" when they set none), the role in words with what it allows, and the end date, and offers "Join", "Decline" and "Not now".
2. **Given** that screen, **When** they tap "Join", **Then** they become a member with the invitation's role, the wallet opens and shows "Getting the wallet…" until its first read completes, and the wallet is in their wallet list from then on.
3. **Given** that screen, **When** they tap "Decline" and confirm, **Then** the invitation is declined and the screen closes with "Invitation declined".
4. **Given** no one is signed in, **When** the link opens the app, **Then** the app says "Sign in or create an account to answer this invitation" over the entry screen, keeps the invitation only in memory, and opens the invitation screen as soon as someone signs in during the same app session.
5. **Given** the person is already a member of that wallet, **When** they open the link, **Then** the screen says "You're already in this wallet" and offers "Open wallet"; nothing is sent.
6. **Given** a link that is expired, used, withdrawn, declined, of a deleted wallet, or addressed to another account, **When** it opens, **Then** the app shows one message — "This invitation can't be opened. It may have ended, been used or withdrawn, or be meant for another account." — and never says which.
7. **Given** the wallet owner's plan no longer allows another person, **When** the invitee taps "Join", **Then** the app explains the owner's plan does not allow another person in this wallet right now; the invitation is not used up.
8. **Given** an unverified signed-in person, **When** they open, join, decline or (later) leave, **Then** it works; writing to the wallet afterwards brings the verify prompt as everywhere else.
9. **Given** no connection, **When** the link opens or the person taps "Join" or "Decline", **Then** the app says the invitation needs a connection, keeps the screen with "Try again", and sends nothing.
10. **Given** a link for another environment, host or path, or one whose token is malformed, **When** it opens the app, **Then** nothing is sent to the server and the app shows "This link isn't a valid invitation".

---

### User Story 3 - Share an invitation by link (Priority: P1)

As a wallet owner, I create an invitation by link with a role and share it through my phone's
share sheet (for example, in a chat); anyone signed in who opens it can join with that role.

**Why this priority**: In the target market most invitations travel by chat, not email; the
channel is also the only one that works without knowing the person's address.

**Independent Test**: As an owner on Plus, choose "Share a link" as viewer; the link is ready
after the server answers and the share sheet opens with a message that names no wallet and no
amount; the invitation shows as "Link · Viewer · Pending". A second account opens the link and
joins as viewer.

**Acceptance Scenarios**:

1. **Given** an owner who may invite, **When** they choose "Share a link" with a role, **Then** the invitation is listed as pending and, as soon as the server answers, the app offers "Share" (system share sheet) and "Copy"; the link itself is never printed on screen.
2. **Given** the owner is offline, **When** they create a link invitation, **Then** it is listed as "Waiting to sync" and the screen says the link will be ready when the phone is back online; once the answer arrives during the same app session, "Share" and "Copy" become available on the invitation.
3. **Given** a link invitation whose link this app session never received or no longer holds (the app was closed, or the answer was lost), **When** the owner opens it, **Then** it says "This link can't be shown again" and offers "Replace link", which withdraws it and creates a new one with the same role.
4. **Given** the share text, **When** it is built, **Then** it contains the app's name, a short sentence and the link, and never an amount, a balance or the wallet's name.
5. **Given** the link was copied, **When** it is put on the clipboard, **Then** it is marked sensitive so the system does not preview it.

---

### User Story 4 - Manage members and pending invitations (Priority: P2)

As a wallet owner, I change a member between editor and viewer, remove a member, withdraw a
pending invitation, and — now that members exist — hand ownership to a member (built in C4).

**Why this priority**: Sharing must be reversible and adjustable; people leave households and
change responsibilities. It depends on US1–US3 having produced members.

**Independent Test**: As owner with Karim as editor, make Karim a viewer (online): his row shows
"Viewer"; withdraw a pending invitation: it disappears; remove Karim: his row disappears and his
phone loses the wallet on its next sync.

**Acceptance Scenarios**:

1. **Given** the owner online, **When** they change a member's role between editor and viewer, **Then** the member's row shows the new role after the server answers; offline, the action says it needs a connection and changes nothing.
2. **Given** the owner's plan does not allow another editor (no sharing, or more people than the plan allows), **When** they try to make someone an editor, **Then** the app explains why and changes nothing; making someone a viewer is never refused for the plan.
3. **Given** the owner, **When** they remove a member and confirm (the confirmation says the person loses access and that what their phone already downloaded may stay on it), **Then** the member disappears from the list after the server answers.
4. **Given** a pending invitation, **When** the owner withdraws it and confirms, **Then** it disappears from the list at once (offline too) and the withdrawal is sent when online; its link stops working and its place is freed.
5. **Given** another member exists and no change to the wallet waits, **When** the owner opens the wallet, **Then** "Transfer ownership" (C4) is offered; after a transfer the old owner sees editor controls and the new owner's plan may turn editors beyond its number of people into viewers (shown as normal role changes).
6. **Given** the member's record changed on another phone since this phone read it, **When** the owner changes or removes them, **Then** the app re-reads the wallet and asks the owner to look again; nothing is overwritten.
7. **Given** the owner's own row, **When** the members screen shows it, **Then** it offers no role change or removal; ownership moves only by transfer.

---

### User Story 5 - Leave a wallet, and lose access cleanly (Priority: P2)

As a member who is not the owner, I leave a wallet I no longer need; and whenever I lose access —
I left, I was removed, or the wallet was deleted — the wallet and everything of it leaves my phone.

**Why this priority**: The brief's second checkpoint ("remove the member → their local copy is
purged on next sync") and a privacy promise.

**Independent Test**: As Karim (editor), leave "Home" online: the wallet disappears from his list
with its transactions, rules and invitations. Separately, Mona removes Karim: on Karim's next
sync the wallet disappears from his phone.

**Acceptance Scenarios**:

1. **Given** a non-owner member online, **When** they choose "Leave wallet" and confirm, **Then** the wallet and all its data leave the phone as soon as the server answers, and the app returns to the wallet list with "You left <wallet>".
2. **Given** that member has changes to this wallet still waiting to sync, **When** they choose "Leave wallet", **Then** the app first tries to send them; if any still wait, the confirmation names how many will be discarded.
3. **Given** the owner, **When** they open the wallet, **Then** "Leave wallet" is not offered; a note says to transfer ownership first.
4. **Given** a member who was removed, or whose wallet was deleted, **When** their phone next syncs, **Then** the wallet and all its data leave the phone, and an open screen of that wallet closes with "You no longer have access to this wallet".
5. **Given** a repeated leave (the server already ended the membership), **When** the answer is "not found", **Then** the app treats it as done and purges.

---

### User Story 6 - See who did what in a shared wallet (Priority: P3)

As any member, I open a wallet's activity and see, newest first, who added, edited, deleted,
confirmed or skipped what, who joined, left or was removed, role changes (including those made
because of the owner's plan) and invitations sent or withdrawn.

**Why this priority**: Trust in a shared ledger; valuable but not needed to share.

**Independent Test**: After Karim adds a transaction in "Home", Mona opens Activity: "Karim added
Groceries · 10:20" is first, "Karim joined as editor" below it.

**Acceptance Scenarios**:

1. **Given** a member online, **When** they open a wallet's activity, **Then** entries are listed newest first with the actor's name, the action in words, what it was done to (named from the phone's own copy) and the time, and more load as they scroll.
2. **Given** an entry whose record is not on the phone (deleted since, or never read), **When** it is shown, **Then** it names the kind ("a transaction", "a recurring rule") instead.
3. **Given** an entry made by the server (automatic confirmation, plan change), **When** it is shown, **Then** the actor reads "Automatically", and a `role_limited_by_plan` entry reads "<name> became a viewer because of the owner's plan".
4. **Given** an actor who is no longer a member, **When** their entry is shown, **Then** the actor reads "A former member".
5. **Given** no connection, **When** the activity opens, **Then** it says activity needs a connection, with "Try again"; nothing stale is shown as current.
6. **Given** an action the app does not know, **When** a page contains it, **Then** the entry is skipped.

---

### User Story 7 - Shared wallets explain themselves on every phone (Priority: P3)

As anyone in a shared wallet, I can see my role and what it lets me do; as a viewer — including
one turned viewer by a plan change — I am told why I can only look, rather than meeting failures;
and pending invitations, roles and memberships are the same on all my phones.

**Why this priority**: Required by C3's downgrade rule and the brief's role-gated UI; small but
cross-cutting.

**Independent Test**: Lapse the owner's plan so an editor becomes a viewer: on the editor's phone
the wallet shows "You can view this wallet" with the plan explanation, and add/edit controls are
gone after the next sync; a second phone of the owner lists the same pending invitations.

**Acceptance Scenarios**:

1. **Given** a wallet the person does not own, **When** it appears in the wallet list or detail, **Then** their role is shown in words.
2. **Given** a viewer, **When** they open the wallet, **Then** a note says they can view but not change it and that only the owner can change their role; if the latest role change for them came from the owner's plan, the note says so; no add, edit, delete, confirm or skip control is offered.
3. **Given** the owner of a wallet with more people than their plan now allows, **When** they open its members, **Then** a note says the plan allows N people and that the others can view but not change the wallet.
4. **Given** the person's role changes on the server, **When** the phone next syncs, **Then** every screen of that wallet uses the new role.
5. **Given** a phone updated from the previous version, **When** it first syncs, **Then** pending invitations created earlier appear (every wallet is read from the start once).
6. **Given** sign-out, **When** it completes, **Then** every invitation and any invitation held in memory are gone from the phone.

---

### Edge Cases

- An invitation is accepted, declined, revoked or expires: it leaves the pending list when the feed brings its tombstone; until the hourly sweep, an invitation past its end date is shown as "Expired" (not "Pending"), is not counted toward the plan's number of people, and can still be withdrawn to tidy the list.
- The owner invites their own address: refused on the phone ("That's your own address").
- The invitee opens a link while signed in to a different account than the one an email invitation was sent to: the uniform "can't be opened" message (US2-S6).
- The same link is opened twice, or "Join" is tapped twice / retried after a lost answer: the server answers with the existing membership; the app shows the wallet, never an error.
- A link arrives while another invitation screen is open: the new one replaces it.
- Too many invitation attempts (429): the create waits and retries after the server's wait time; preview, join and decline say "Too many attempts. Try again in <minutes>" and are not retried automatically.
- The wallet is deleted while an invitation screen is open: joining answers "can't be opened".
- A role change arrives while the person has the transaction editor open: saving follows the existing refusal path (spec 008), which now explains the viewer role (FR-027).
- An editor turned viewer has changes queued from before: each is refused with `FORBIDDEN` and shows "You can now only view this wallet" with "Discard" (FR-027).
- The person is removed while changes to that wallet wait: the wallet stays until those changes are settled (spec 009 rule), then goes.
- The member list holds only the owner: "Transfer ownership" and "Leave" are not offered; the members screen invites the owner to share.
- An activity entry about a member uses the member's user id: names come from the server's display name, then the phone's member list, else "A former member".
- A queued invitation is still waiting when the wallet is deleted or access is lost: the queued change is discarded with the wallet.
- Two phones of the owner create link invitations offline: both are created; each phone can share only the link it received.
- The share sheet is dismissed without sharing: the invitation stays pending; "Share" remains available while this app session holds the link.

## Requirements *(mandatory)*

### Functional Requirements

**Invitations (owner)**

- **FR-001**: The app MUST let the owner of a wallet create an invitation with a role (editor or viewer; viewer preselected) and either an email address (email invitation) or none (link invitation), from the wallet's members screen. "Invite" is offered for active wallets only (not archived ones); withdrawing, role changes and removals stay available on archived wallets. An invitation to a wallet created on this phone and not yet accepted by the server waits until the wallet is accepted.
- **FR-002**: Before creating, the app MUST check, in this order: a verified address (else the verify prompt, nothing queued); `can(sharing.enabled)` (else the "not included in your plan" explanation with a way to the Plan screen); and that owner + active members + pending, unexpired invitations of the wallet (including invitations waiting to sync) + 1 does not exceed `quota(sharing.max_members_per_wallet)` (null = unlimited; else the "reached the number of people" explanation).
- **FR-003**: An email address MUST be trimmed, at most 254 characters and of the form local@domain; the app MUST refuse the person's own address. A pending invitation to the same address (compared case-insensitively) in the same wallet MUST be named in a confirmation before the new one is sent.
- **FR-004**: Creating an invitation MUST be written locally first with a client-generated UUIDv7 and queued for sending; it MUST NOT need a connection. Invitations are sent one by one, never in a batch upload. The queued create is sent as version 0 and, once accepted, the server's copy replaces the local one. What the app shows after sending MUST NOT say or suggest whether the address belongs to an account (P-09.4: the server answers the same either way).
- **FR-005**: For a link invitation, the link from the server's creation answer MUST be held only in memory for the current app session, keyed by the invitation, and offered through "Share" (system share sheet) and "Copy" (clipboard marked sensitive). It MUST NOT be stored, logged, sent to crash reporting or analytics, or printed on screen. A link invitation whose link is not held MUST offer "Replace link" (withdraw, then create a new link invitation with the same role).
- **FR-006**: The share text MUST contain only the app's display name, a fixed sentence in the app's language and the link; never an amount, a balance or the wallet's name.
- **FR-007**: Withdrawing a pending invitation (owner only, after confirmation) MUST hide it at once, forget any link held for it, and queue a withdrawal carrying its version; it MUST work offline and whatever the owner's plan. Withdrawing an invitation never sent to the server removes it and its queued create without sending anything.
- **FR-008**: A refused invitation create MUST show "Not sent" with copy chosen by code and, for `FEATURE_NOT_ENTITLED`, by its `feature` member (`sharing.enabled` → "Sharing isn't included in your plan"; `sharing.max_members_per_wallet` → "This wallet has reached the number of people your plan allows"; absent or unknown → the general plan message), and offer "Discard"; plan and verification reasons also offer "Try again". `FEATURE_NOT_ENTITLED` MUST also trigger the C3 entitlements refresh. `FORBIDDEN` (the person is no longer the owner) → "Only the owner can invite" with "Discard". `RATE_LIMITED` MUST keep the invitation "Waiting to sync" and retry after the server's wait time. `VERSION_CONFLICT` on a create (the invitation was already closed) MUST take the server's copy without asking.

**Invitations (invitee)**

- **FR-009**: The app MUST open links of the form `{scheme}://{linkHost}[:{linkPort}]/invite?token=<43 characters from A–Z, a–z, 0–9, "_" and "-">` — the same scheme, host and port per build flavor as the mail links of spec 003 — into the invitation screen, signed in or not; anything else MUST be ignored or, for a matching path with a malformed token, show "This link isn't a valid invitation" without calling the server. The development flavor's Paste link tool MUST accept invitation links too.
- **FR-010**: The invitation secret MUST travel only in request bodies, MUST live only in memory (the invitation screen's state and, while no one is signed in, one in-memory holder), and MUST NOT be stored, logged, sent to crash reporting or analytics, or shown. Every object holding it MUST print it redacted.
- **FR-011**: When no one is signed in, the app MUST keep the secret in memory, show "Sign in or create an account to answer this invitation" on the entry screen, and open the invitation screen right after the next successful sign-in in the same app session; leaving that screen (back) keeps it held; a new link replaces it and sign-out drops it.
- **FR-012**: The invitation screen MUST read the invitation's preview (online) and show the wallet's name, the inviter's display name (or "Someone"), the role with one line on what it allows, and the end date in the person's time zone; it MUST NOT show any amount. When the preview says the person is already a member, it MUST show "You're already in this wallet" with "Open wallet" and offer no join.
- **FR-013**: "Join" MUST accept the invitation; on success the app MUST store the returned membership, read that wallet from the start, open the wallet (showing "Getting the wallet…" until the first read lands; if that read is offline or fails, the same state offers "Try again" and the next sync completes it) and keep it in the wallet list. "Decline" MUST ask for confirmation, then decline; success closes the screen with "Invitation declined". "Not now" closes the screen and keeps the secret in memory for this app session, shown as a Home card "You have an invitation to answer".
- **FR-014**: Preview, join and decline outcomes MUST be chosen by code: `NOT_FOUND` → the single uniform "can't be opened" message, never distinguishing causes; `FEATURE_NOT_ENTITLED` → "The wallet owner's plan doesn't allow another person in this wallet right now" (both `feature` values); `RATE_LIMITED` → "Too many attempts. Try again in <n> minutes" from the server's wait time; no connection or timeout → "Needs a connection" with "Try again"; `VALIDATION_FAILED` → "This link isn't a valid invitation"; anything else → the general failure with the request id. These calls MUST NOT be retried automatically.
- **FR-015**: Preview, join, decline and leave MUST work for an unverified signed-in person (no verify gate on the phone).

**Members**

- **FR-016**: Each wallet MUST have a members screen, reached from the wallet detail's members section ("Manage members" for the owner, "See members" for others, with the number of pending invitations when above zero), listing members (owner first, then joining order, the person's own row marked "You", each with a role in words and the spec 006 unnamed-member wording when they set no name) and pending invitations (address or "Link", role, "Expires <date>" or "Expired", "Waiting to sync" / "Not sent" marks; "Expired" re-evaluated at least once a minute while the screen is open), with the owner's controls for owners only.
- **FR-017**: The owner MUST be able to change another member's role between editor and viewer, online only. Raising to editor MUST first check `can(sharing.enabled)` and that the wallet does not have more people than the quota; lowering to viewer MUST never be checked against the plan. The request MUST carry the member's last-seen version.
- **FR-018**: The owner MUST be able to remove another member, online only, after a confirmation stating they lose access and that what their phone already downloaded may stay on it. The request MUST carry the member's last-seen version.
- **FR-019**: A non-owner member MUST be able to leave a wallet, online only, after confirmation. Before leaving, the app MUST upload what waits; changes of that wallet still waiting after that MUST be counted in the confirmation and discarded on leaving. On success, or on `NOT_FOUND` (already ended), the app MUST remove the wallet and everything of it from the phone at once. The owner MUST NOT be offered "Leave"; a note MUST say to transfer ownership first.
- **FR-020**: Member actions MUST branch on codes: `VERSION_CONFLICT` → re-read the wallet and "This member changed. Look again."; `FORBIDDEN` → re-read and "Only the owner can do this" (or, for leave, "The owner can't leave"); `FEATURE_NOT_ENTITLED` → the plan explanation by `feature` and the C3 refresh; `NOT_FOUND` → re-read and "This member is no longer in the wallet" (leave: treated as done); `EMAIL_NOT_VERIFIED` → the verify prompt; `VALIDATION_FAILED` with `IMMUTABLE_FIELD` at `/role` → "Ownership moves only by transfer"; no connection → "Needs a connection". Member actions MUST NOT be retried automatically and MUST NOT be offered while a previous one for the same wallet is in flight.
- **FR-021**: The owner's own row MUST offer no role change or removal. Ownership transfer (spec 006) MUST be offered from the wallet detail once another active member exists, unchanged otherwise.
- **FR-022**: A member row returned by a role change, removal or leave MUST be applied to the phone's copy immediately (the same version guard as the feed); a member row on the feed for the person themself MUST also update the wallet's stored role, so every screen gates on the new role at once.

**Access lost**

- **FR-023**: When the person's own membership ends (a tombstone on their user feed, a successful leave, or a wallet feed answering not found), the wallet MUST be removed from the phone with its members, invitations, transactions, recurring rules, occurrences, category references, stored positions and waiting changes (the spec 009 rule that a wallet with a waiting change to its own record stays until settled still applies). Open screens of that wallet MUST close with "You no longer have access to this wallet", except right after the person's own leave ("You left <wallet>").

**Activity**

- **FR-024**: Each wallet's detail MUST offer "Activity" to every member. The activity screen MUST read the wallet's activity online, 50 entries a page, newest first, loading the next page on scroll, and keep no entries after it closes (only the in-memory plan-demotion flag of FR-027 survives, for the app session).
- **FR-025**: Each entry MUST show the actor (server display name; else the phone's member name for that user id; else "A former member"; null actor → "Automatically"), the action in words (every action of the contract's list; unknown actions skipped), the subject named from the phone's copy (transaction: its note, else its category name, else "a transaction"; recurring rule and occurrence: the rule's note or category; member: the member's name; invitation: "an invitation as <role>"; wallet: the wallet's name), the role where the entry carries one, and the time in the person's time zone. Entries MUST NOT show amounts. A tapped transaction entry whose transaction is on the phone MUST open it.
- **FR-026**: Activity outcomes: no connection → "Activity needs a connection" with "Try again"; `NOT_FOUND` → the access-lost handling of FR-023 is triggered by a wallet re-read and the screen closes; anything else → the general failure with the request id. A note MUST say activity covers the last 90 days.

**Roles everywhere**

- **FR-027**: The wallet list and detail MUST show the person's role in words for wallets they do not own. A viewer MUST see on the wallet detail and transactions screen a note: "You can view this wallet but not change it. Only its owner can change your role." When an activity page read in this app session shows that the latest role change for the person was made by the owner's plan (`role_limited_by_plan` about them), the note MUST say "The owner's plan changed, so you can now view this wallet but not change it." Add, edit, delete, confirm and skip controls stay hidden for viewers (specs 008, 010). A change of the person's queued before they became a viewer and refused with `FORBIDDEN` MUST show "You can now only view this wallet" with "Discard", not a bare failure.
- **FR-028**: The owner's members screen MUST show, when the wallet has more people than `quota(sharing.max_members_per_wallet)` allows or sharing is not included, "Your plan allows <n> people in a wallet. The others can view it but not change it."

**Sync, storage and privacy**

- **FR-029**: Invitation rows from the wallet feed MUST be applied with the version guard, tombstones removing the row unless a change to it waits, and the sweep after a complete read; the person's own feed MUST continue to drive wallets gained and lost (spec 009).
- **FR-030**: The on-device database MUST move from version 9 to 10 with a migration that keeps all data, adds the invitation table and clears every wallet scope's feed cursor, read stamp and position once, so invitations created before the update are read from the start; the schema MUST be exported.
- **FR-031**: Sign-out MUST remove every invitation and drop any invitation secret or link held in memory.
- **FR-032**: Requests MUST carry only the members the contract declares (`role`, `invitee_email`, `version` on create; `{version}` on withdraw, remove and leave; `{role, version}` on role change; `{token}` on preview, join and decline). The error model MUST read the problem's `feature` member.
- **FR-033**: Screens MUST render invitations and members only from the phone's copy (P-04.1); the invitation preview and the activity are not syncable records and are shown from the answer.
- **FR-034**: Logs MUST NOT contain invitation secrets, share links, invited addresses, amounts or notes; invitation ids and wallet ids appear only at debug level.
- **FR-035**: All new copy MUST exist in English and Arabic, avoid the glossary's banned synonyms (including "invite link", "participant", "group", "token"), use Western digits and mirror in RTL.
- **FR-036**: The feature MUST NOT offer a purchase; the plan explanations may open the Plan screen (spec 005) only.

### Key Entities *(include if feature involves data)*

- **Invitation**: wallet-scoped, client-created (UUIDv7); role (editor/viewer), channel (email/link), invited address (while pending email), inviter's user id, status, end time; version and feed position; immutable once created; withdrawn by a queued deletion.
- **Invitation preview** (not stored): invitation id, wallet id and name, role, inviter's display name, end time, "already a member".
- **Held invitation** (memory only): the invitation secret from a link, until answered, replaced, dropped at sign-out, or the app session ends.
- **Held share link** (memory only): a link invitation's one-time link, keyed by invitation id, for the app session.
- **Wallet Member** (C4, now written through this feature's actions): user id, display name, role, version; the person's own row also sets the wallet's stored role.
- **Activity Entry** (not stored): actor, action, subject kind and id, role, time.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets an owner share a wallet by email or by link with a role, manage the people and pending
invitations, lets invitees join from a link in the app, lets members leave, shows who did what,
and guarantees that losing access removes the wallet from the phone — all within the owner's
plan, explained in words rather than failures.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Invitation** ("دعوة"), **Wallet Member** ("عضو"),
**Activity Entry** (UI: "Activity" / "النشاط"), **Wallet**, **owner / editor / viewer** (existing
strings "المالك" / "محرر" / "مشاهد"), **Plan**, **Transaction**, **Recurring Rule**. UI words:
"Members" / "الأعضاء" (existing), "Invite" / "دعوة", "Share a link" / "مشاركة رابط", "Pending" /
"معلّقة", "Expired" / "منتهية", "Withdraw" / "سحب الدعوة", "Join" / "انضمام", "Decline" / "رفض",
"Not now" / "ليس الآن", "Leave wallet" / "مغادرة المحفظة", "Remove from wallet" / "إزالة من
المحفظة", "Make editor" / "اجعله محررًا", "Make viewer" / "اجعله مشاهدًا", "A former member" /
"عضو سابق", "Automatically" / "تلقائيًا". Never "invite link", "join request", "participant",
"collaborator", "friend", "group", "household", "family", "token". Arabic wording is an assumed
default for owner review.

### 3. Data model

Adds one phone-side table, **invitation** (every member of `Invitation`: id, wallet id, role,
channel, invited address, inviter, status, end time, version, change sequence, created/updated
times; plus the phone's `account_id` and `refreshed_at`; a tombstone removes the row unless a
change to it waits, as in spec 009). The `wallet_member` table (C4) is unchanged; own member rows
now also update `wallet.my_role`. Invitation creates and withdrawals use the spec 004 queue with
record type `invitation` in the wallet scope, sent per record. The database goes from version 9
to 10: a migration creates the table and clears every wallet scope's feed cursor, read stamp and
position once (FR-030); the schema is exported. Sign-out clears the table. The invitation preview,
the activity and the held secret and link are never stored.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Invitations and members render only from the phone's copy | P-04.1, FR-033 | store/service tests over fakes; no API in UI/store |
| An invitation create and withdrawal work offline, are queued per record (never batched) and carry version 0 / the stored version | P-04.2, P-04.4, P-04.5, FR-004, FR-007 | service tests offline; handler `batchItems` returns null; request-shape tests |
| The invitation secret and the share link are never stored, logged, shown or put in a URL path/query | P-09.2, FR-005, FR-010, FR-034 | log-capture test; redacted `toString` tests; request tests (body only); DAO/schema has no such column |
| The share text never contains an amount, balance or wallet name | brief, FR-006 | share-text test over a wallet with name and balance |
| The invite gate follows verified → sharing.enabled → people count (owner + members + unexpired pending + 1 ≤ quota, null = unlimited) | P-12.1, FR-002 | gate table tests |
| Raising to editor is plan-checked, lowering never | FR-017 | service tests |
| Losing access (feed tombstone, leave answer, leave 404, wallet 404) removes the wallet with invitations, transactions, rules, occurrences, references, positions and waiting changes | FR-019, FR-023 | purge tests per signal; `AppGraphTest` participant list |
| An own member row updates the wallet's stored role | FR-022 | repository test |
| Failures branch on code (and `feature`, pointer) only; token calls and member actions are never retried automatically | P-02.4, P-02.6, FR-014, FR-020 | refusal-mapping and retry tests |
| Preview, join, decline answers never reveal why a link can't be opened | P-09.4, FR-014 | single-copy test for every NOT_FOUND cause |
| Only links of the flavor's scheme, host, port and `/invite` path with a 43-character token are accepted | FR-009 | parser table tests |
| Migration 9 → 10 keeps data and forgets every wallet scope's position, not the user scope's | FR-030 | migration test 9 → 10 |
| Sign-out removes invitations and the held secret and links | FR-031 | sign-out wipe test |
| Activity shows no amount and skips unknown actions | FR-025 | activity mapping tests |
| No purchase action anywhere in the feature | P-12.3, FR-036 | screen tests (no purchase intent) |

### 5. API

All under `/api/v1`, frozen in `sharing.yaml` v1.9 (backend spec 016, `e4dc4c6`; unchanged at `26e7c44`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Create invitation (sync engine, per record) | `PUT /wallets/{id}/invitations/{iid}` | `role`, `invitee_email` (string or explicit null), `version: 0` | 201 (`meta.share_url` for a link) / 200 replay (no link); 403 `EMAIL_NOT_VERIFIED`, `FORBIDDEN`, `FEATURE_NOT_ENTITLED` (+`feature`); 404; 409 `VERSION_CONFLICT` (closed); 422 `VALIDATION_FAILED` (`INVALID_ENUM_VALUE /role`, `INVALID_TYPE`/`OUT_OF_RANGE /invitee_email`, `IMMUTABLE_FIELD`), `INVALID_ID`; 429 `RATE_LIMITED` (+Retry-After); retryable per catalogue |
| Withdraw invitation (sync engine, per record) | `DELETE /wallets/{id}/invitations/{iid}` with a JSON body | `{version}` | 200 tombstone (also already closed); 403; 404; 409 (+`current`); 422 |
| Preview | `POST /invitations/preview` | `{token}` | 200 `InvitationPreview`; 404; 422; 429 |
| Join | `POST /invitations/accept` | `{token}` | 200 `WalletMember`; 403 `FEATURE_NOT_ENTITLED` (+`feature`); 404; 422; 429 |
| Decline | `POST /invitations/decline` | `{token}` | 204; 404; 422; 429 |
| Change role | `PATCH /wallets/{id}/members/{uid}` | `{role, version}` | 200 member; 403 `EMAIL_NOT_VERIFIED`, `FORBIDDEN`, `FEATURE_NOT_ENTITLED` (+`feature`); 404; 409 (+`current`); 422 (`IMMUTABLE_FIELD /role`, `INVALID_ENUM_VALUE /role`, `INVALID_ID`) |
| Remove member | `DELETE /wallets/{id}/members/{uid}` with a JSON body | `{version}` | 200 tombstone; 403; 404; 409; 422 |
| Leave | `POST /wallets/{id}/leave` | `{version}` (own membership's) | 200 tombstone; 403 (owner); 404 (= done); 409; 422 |
| Activity | `GET /wallets/{id}/activity?limit=50[&cursor=]` | — | 200 page; 400 (`INVALID_CURSOR`: restart from the first page once); 404; 401; 426 |
| Reads | `GET /sync/wallets/{id}/changes`, `GET /sync/user/changes` (spec 009) | — | `invitation` rows now applied; own `wallet_member` rows also set the wallet's role |

Not used: `GET /wallets/{id}/invitations` and `GET /wallets/{id}/invitations/{iid}` (the wallet
feed carries invitations, spec 009 decision). All requests carry the standard headers (P-02.5).

#### Contract changes since last spec

Baseline `saveme-backend@64ca1ea` (spec 012) → `26e7c44`:

- **Error codes added**: `REDEEM_CODE_EMAIL_MISMATCH` (403), `REDEEM_CODE_EXPIRED` (410), `REDEEM_CODE_USED` (409) — follow-up: not in scope of this spec (C11 redeem code).
- **Idempotency convention**: a public operation (InstaPay claim submission) keys in one anonymous scope — follow-up: not in scope of this spec (web only, P-12.4); the client sends no `Idempotency-Key` for any C9 call.
- **Shared responses**: descriptions of 403/404/409/410 now mention redeem codes — no client change.
- **Client mismatches**: none (every client code exists in the catalogue with the same retry class).
- **Already in v1.9 but new to the client**: `Problem.feature` on `FEATURE_NOT_ENTITLED` — handled by FR-008, FR-014, FR-020, FR-032.

### 6. Authorization and entitlements

Follows the backend permission matrix (spec 010 §6, rows S-01–S-07): every member reads members,
pending invitations and activity; only the owner invites, withdraws, changes roles and removes;
every non-owner may leave; nobody changes the owner's role. Owner actions need a verified
address; preview, join, decline and leave do not. A shared wallet is governed by its **owner's**
entitlements: the owner's phone gates on its own `can(sharing.enabled)` and
`quota(sharing.max_members_per_wallet)`; an invitee's or editor's phone cannot know the owner's
plan and relies on the server's `FEATURE_NOT_ENTITLED` answer. Withdraw, remove and lowering a
role are never plan-gated. A non-member's request on a wallet is 404.

### 7. Events

Not applicable: the client publishes no domain events and shows no notifications (C16). It
consumes the sync engine's queue and status, the wallet and user feeds, C3's entitlements, C4's
wallets and members, and C5–C7's records to name activity subjects.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Invite by email | Unit (gate, validation, service create, handler PUT, applier, refusal mapping) + contract + E2E | tests; `.maestro/013-sharing/US1-*` (deferred) |
| US2 Join | Unit (link parser, held invitation, invitation store, outcomes) + contract + E2E with a second API session | tests; `US2-*` (deferred) |
| US3 Share a link | Unit (link holder, share text, replace) + contract + E2E | tests; `US3-*` (deferred) |
| US4 Manage | Unit (members store, role/remove outcomes, withdraw) + contract + E2E | tests; `US4-*` (deferred) |
| US5 Leave / lose access | Unit (leave, purge per signal) + captured live answers + E2E | tests; `US5-*` (deferred) |
| US6 Activity | Unit (mapping, paging, resolution) + contract | tests; `US6-*` (deferred) |
| US7 Roles everywhere | Unit (role note, plan note, migration, sign-out) + screenshots | tests; `US7-*` (deferred) |

Owner policy: no emulator, Maestro or device run; flows are written and marked "deferred: E2E on
owner's device". Host tests plus a live-backend contract QA (curl against the local backend with
two or three throwaway accounts, real answers captured as fixtures replayed through the real
client, engine, handler, appliers and services), as specs 007–012.

### 9. Open questions

- **Backend contract gaps (non-blocking)**: see Assumptions "Backend gaps".
- **Arabic words** listed in §2: assumed defaults for owner review. Non-blocking.
- **Push for new invitations or removals**: C16 / B16. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An owner can invite someone by email or share a link in under 30 seconds from opening the wallet.
- **SC-002**: An invitee who taps a valid link while signed in joins with two taps and sees the wallet in their list immediately, with its transactions after the first sync.
- **SC-003**: After removal, leaving or wallet deletion, 100% of that wallet's data is gone from the person's phone after its next successful sync (immediately for their own leave).
- **SC-004**: Zero invitation secrets, share links or invited addresses appear in logs, crash reports or stored data across the whole feature's test suite.
- **SC-005**: Every refusal code the feature handles produces its own message in both languages; none shows a server `detail` or a bare failure.
- **SC-006**: A viewer — including one demoted by the owner's plan — is never offered a write control in that wallet, and sees why.
- **SC-007**: Pending invitations, members and roles are identical on every phone of the same account after a sync.

## Assumptions

### Design defaults

1. Invitations are local-first (created and withdrawn through the sync queue, per record); member role changes, removals, leaving, joining and declining are online-only actions, as ownership transfer is in C4 (research R-15 of spec 006: the server writes memberships; the phone never creates one).
2. A link invitation's link exists only in this app session's memory; when it is lost the owner replaces the link. It is never shown as text.
3. New invitations preselect the viewer role (least privilege).
4. The invitation link path is `/invite` on the same scheme, host and port as the spec 003 mail links, per flavor; the development backend must set `SHARING_INVITATION_URL` to `http://<devApiHost>/invite` for links to open the dev app (the backend's default `http://saveme-backend.test/invite` does not), and the Paste link tool covers the rest.
5. A signed-out invitee's secret is kept in memory only, until sign-in in the same app session; after the app is closed the person taps the link again.
6. The Home card "You have an invitation to answer" appears only while a secret is held after "Not now" or before sign-in; there is no list of invitations addressed to the person (none exists in the API).
7. Every member sees the number of pending invitations on the wallet detail's members row and the pending list on the members screen (the contract makes it readable by every member); only the owner gets controls.
8. Expired invitations (by end time) are shown as "Expired", excluded from the people count, and can be withdrawn.
9. Activity is online-only and not stored; 50 per page; amounts are never shown.
10. The share text names neither the wallet nor any amount.
11. The removal confirmation states the privacy commitment's honest limit (what their phone already downloaded may stay on it).
12. "Owner's plan" explanations for viewers use the activity (`role_limited_by_plan` about the person) when the person has opened it in this session; otherwise the general viewer note is shown. The phone does not read activity in the background to find out.

### Backend gaps (non-blocking)

1. No endpoint lists invitations addressed to the signed-in person, so the app cannot show a pending-invitation badge for invitees beyond links it has opened.
2. A link invitation's link is returned once; a lost answer (or a background send while the app is closed) means withdraw and recreate (backend risk acknowledged in spec 016).
3. Whether a role change was made by the plan is only in the activity (`role_limited_by_plan`); the member row carries no reason.
4. The dev default `SHARING_INVITATION_URL` points at `saveme-backend.test`, not the phone-reachable dev host; device tests need the environment variable set (configuration, not code).
5. Staging/production App Links need the backend host to serve `assetlinks.json` with the release certificate (existing dependency from spec 003).
6. Expired invitations stay `pending` on the feed until the hourly sweep; the phone derives "Expired" from the end time.
