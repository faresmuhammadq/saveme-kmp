# Feature Specification: Sync Engine (Offline Outbox)

**Feature Branch**: `004-sync-engine`

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "C14" — client brief `specs/briefs/C14-sync-engine.md`, wave 0 (the engine on per-record writes). Wave 2 (switching to the batch upload and change feed, backend B14) is a later spec.

**Backend contract**: `saveme-backend@56b44fb`

**Sources**:
- API contract: `saveme-backend/docs/api-conventions.md` (Syncable resources, Writes, Version conflicts, Deleting and restoring), `docs/adr/0008-sync-changes-feed.md`, `docs/openapi/error-catalog.yaml`.
- Builds on spec 002, whose profile sync (save on the device, push with the last server version, the person resolves conflicts) is the first behaviour this engine takes over, unchanged for the person.

## User Scenarios & Testing *(mandatory)*

Today only the profile is edited offline. Wallets, categories and transactions (briefs C4–C6) will all
be, and money edits must never be lost. This feature gives every synced record one shared way of
leaving the phone. The stories are told through the profile, the one synced record people can edit
today, and must hold for every synced record added later.

### User Story 1 - Changes made offline are never lost (Priority: P1)

A person edits something with no connection. The change is saved on the phone at once and sent to
the server automatically when a connection returns, even if they closed the app or the phone
restarted in between.

**Why this priority**: A lost edit destroys trust in a finance app; this is the reason the engine exists (constitution P-04).

**Independent Test**: Edit the profile with the phone offline, close the app, go online, reopen: the edit is on the server without the person doing anything else.

**Acceptance Scenarios**:

1. **Given** no connection, **When** the person saves a change, **Then** it is saved on the phone at once and shows as waiting to sync.
2. **Given** changes waiting to sync, **When** a connection returns, **Then** they are sent without any action from the person, and each stops showing as waiting once the server accepts it.
3. **Given** changes waiting to sync, **When** the app is closed or the phone restarts before a connection returns, **Then** they are still sent once a connection returns, without the app being opened.
4. **Given** the same record is edited several times while offline, **When** a connection returns, **Then** the server receives the latest version of that record, and nothing typed is lost.
5. **Given** changes to several records waiting, **When** they are sent, **Then** changes to the same record reach the server in the order they were made.
6. **Given** sending fails because the server is unreachable or busy, **When** it fails, **Then** the app tries again later on its own, waiting longer between tries, and never gives up while the change is still waiting.

---

### User Story 2 - See what hasn't synced yet (Priority: P1)

A person can tell which of their changes are only on this phone, and when the app last heard from
the server.

**Why this priority**: Without it, a person who switches phones or signs out cannot know whether their changes are safe.

**Independent Test**: Make a change offline; the record shows "waiting to sync" and the app shows how many changes are waiting; go online and both clear.

**Acceptance Scenarios**:

1. **Given** a record has a change waiting to sync, **When** it is shown anywhere, **Then** it is marked "waiting to sync".
2. **Given** any change is waiting, **When** the person looks at their account area, **Then** it shows how many changes are waiting and when the app last synced.
3. **Given** changes are waiting and a connection exists, **When** the person chooses "Sync now", **Then** the app tries at once instead of waiting for its next scheduled try.

---

### User Story 3 - Decide when a change clashes with another device (Priority: P1)

When a waiting change reaches the server after the same record changed on another device, the
server refuses it. The person sees both versions and chooses; nothing is merged or overwritten
without them.

**Why this priority**: The server never merges (backend ADR-0008 rule 9), so every clash must reach the person or the change is lost.

**Independent Test**: Edit the profile on the phone while offline, edit it from another device, go online: the phone shows both versions with "Keep mine" and "Use the other device's".

**Acceptance Scenarios**:

1. **Given** a waiting change to a record that has since changed on another device, **When** it is sent, **Then** it is refused, kept on the phone, and the person is shown their version and the other device's version.
2. **Given** that choice, **When** the person keeps theirs, **Then** it is sent again against the other device's version and wins.
3. **Given** that choice, **When** the person takes the other device's version, **Then** their change is discarded and the phone shows the other device's version.
4. **Given** a clash on one record, **When** other records have changes waiting, **Then** those are still sent; one clash never blocks unrelated changes.
5. **Given** the other device deleted the record, **When** the change is refused, **Then** the person is told it was deleted elsewhere and can restore it with their change or accept the deletion; an edit never silently brings a deleted record back.

---

### User Story 4 - A refused change is never silently dropped (Priority: P2)

If the server refuses a change for good (for example a value it no longer accepts), the change stays
visible with the reason, and the person can fix it or discard it.

**Why this priority**: Rare today, but with money records a silently dropped change is a wrong balance.

**Independent Test**: Force a server refusal of a waiting change; it shows as "not saved" with the reason and the options to edit or discard.

**Acceptance Scenarios**:

1. **Given** a waiting change the server refuses for a reason other than a clash or a connection problem, **When** the answer arrives, **Then** the change is marked "not saved" with the reason, and is not retried automatically.
2. **Given** a "not saved" change, **When** the person edits it, **Then** it waits to sync again; **When** they discard it, **Then** the phone shows the server's version.

---

### User Story 5 - Signing out doesn't lose changes by surprise (Priority: P2)

Signing out removes the account's data from the phone. If changes are still waiting, the person is
told first and can wait for them to sync.

**Why this priority**: Sign-out wipes local data (spec 002, P-09); without a warning it would wipe unsynced edits.

**Independent Test**: Make a change offline, choose Sign out: a warning says one change hasn't synced; going online then signing out shows no warning.

**Acceptance Scenarios**:

1. **Given** changes are waiting to sync, **When** the person chooses to sign out, **Then** the confirmation says how many changes haven't synced and that signing out now discards them.
2. **Given** that warning, **When** a connection exists, **Then** the person can choose "Sync now" first; **When** they sign out anyway, **Then** the waiting changes are discarded with the rest of the account's data.
3. **Given** the phone is signed out for any other reason (session ended elsewhere, password reset), **When** it happens, **Then** waiting changes are discarded with the account's data, since they can no longer be sent.

### Edge Cases

- A change made while a send of the same record is in flight: the in-flight send completes against the old base; the new change waits and is sent next, against the version the first send returned.
- The server applied a write but the answer never arrived (timeout): sending it again is safe, because the server treats an identical repeat as already applied (backend Writes: replay).
- A record deleted on the phone while offline: the deletion waits like any other change and is sent as a deletion, never as a removal from the phone only (P-04.5).
- A record created and then deleted on the phone before it was ever sent: nothing needs to reach the server for it.
- The phone's clock is wrong: ordering never depends on the phone's clock, only on the order changes were made on this phone.
- Access to the record is lost (for example removed from a shared wallet, later): the server answers "not found"; the change is treated as refused (US4) and the record is removed from the phone when that scope is purged.
- The person signs in as a different account on the same phone: nothing from the previous account, including waiting changes, is ever sent under the new account.
- App updated while changes are waiting: they survive the update and are sent afterwards.

## Requirements *(mandatory)*

### Functional Requirements

**Saving and queuing**

- **FR-001**: Every change to a synced record MUST be saved on the phone first and succeed without a connection (P-04.2).
- **FR-002**: Each saved change MUST be queued with the record's identity, its scope (user or wallet), the version the person last saw from the server, and the order in which it was made on this phone.
- **FR-003**: Several changes to the same record before it is sent MUST collapse into one waiting change carrying the latest content and the oldest unsent base version.
- **FR-004**: A deletion MUST be queued like any other change and sent as a deletion (P-04.5). A record created and deleted before its first send MUST leave nothing to send.
- **FR-005**: Waiting changes MUST survive the app closing, the process being killed, the phone restarting, and an app update.

**Sending**

- **FR-006**: Waiting changes MUST be sent automatically whenever a connection is available and the account is signed in, including while the app is closed.
- **FR-007**: Changes to the same record MUST be sent one at a time, in the order they were made. Changes to different records MAY be sent in any order.
- **FR-008**: Each send MUST carry the base version (P-04.4). On success, the waiting change MUST be cleared and the local record replaced by the server's answer.
- **FR-009**: When a send fails because of the connection, a timeout, or a server answer the error catalogue marks as retryable, the change MUST stay waiting and be retried with increasing delays (starting at seconds, capped at minutes), honouring any wait the server asks for. A timed-out send MUST be safe to repeat.
- **FR-010**: A person MUST be able to ask for an immediate send ("Sync now") when changes are waiting.

**Clashes**

- **FR-011**: A `VERSION_CONFLICT` answer MUST keep the change, store the server's current version returned with the answer, stop retrying that record, and hand both versions to the record's screen for the person to choose (P-04.4). Other records MUST keep sending.
- **FR-012**: "Keep mine" MUST re-send the person's change against the server's current version; "Use the other device's" MUST discard the change and keep the server's version.
- **FR-013**: When the current server version is a deletion, the person MUST be offered "Restore with my change" (an explicit restore, backend Deleting and restoring) or "Accept the deletion". An ordinary resend MUST NOT restore a deleted record.

**Refusals**

- **FR-014**: Any other refusal (validation, not found, forbidden, read-only) MUST mark the change "not saved" with a reason mapped from the error code, stop retrying it, and keep it until the person edits or discards it.

**Status**

- **FR-015**: Each record with a waiting, clashing or refused change MUST expose that state to its screens ("waiting to sync", "changed on another device", "not saved").
- **FR-016**: The account area MUST show the number of waiting changes, the number needing the person's decision, and the last time the app synced successfully.

**Sign-out**

- **FR-017**: A person-initiated sign-out with waiting changes MUST warn with the count and offer "Sync now" (when online) or signing out anyway.
- **FR-018**: Every sign-out, whatever the cause, MUST discard waiting changes together with the account's other data (P-09), and a queued change MUST never be sent under a different account than the one that made it.

**Adoption**

- **FR-019**: Profile sync MUST move onto the engine with no change the person can see: spec 002's profile scenarios (save offline, "Waiting to sync", conflict with "Keep mine" / "Use the other device's", rejection of the currency after the first transaction) keep passing.
- **FR-020**: Adding a new synced record type MUST need only that record's local storage, its send call and its screens' use of the status; the queue, sending, retries, clashes, status and sign-out handling are shared.

### Key Entities *(include if feature involves data)*

- **Waiting change**: one per record with unsent content: record type, record id, scope, base version, content, kind (upsert or delete), order made, state (waiting, sending, clashing, refused), attempts, next try, refusal reason. Lives only on the phone; removed on success, discard or sign-out.
- **Clash**: the server's current version held beside a waiting change until the person chooses.
- **Scope sync state (existing, extended)**: per scope, the last successful sync time (and, in wave 2, the change-feed cursor).

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Gives every synced record in the app one reliable path from the phone to the server, so changes made
offline are never lost, clashes are always the person's decision, and later money features (wallets,
categories, transactions) can be built on it instead of each writing their own.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md` and `api-conventions.md`: **syncable resource**, **scope**
(user, wallet), **version**, **tombstone**, **change feed**. UI copy says "waiting to sync", "changed
on another device", "not saved"; never "outbox", "tombstone" or "version". "Waiting change" is a
client-only term for this spec.

### 3. Data model

Adds the phone-only waiting-change queue (Key Entities) to the on-device database, and extends the
existing per-scope sync state with the last successful sync time. Profile's current pending-edit
fields (spec 002) move onto the shared queue. No server data changes.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| A save never needs a connection | P-04.2, FR-001 | queue unit test with the network off |
| Every send carries the base version; a clash is never resolved without the person | P-04.4, FR-008, FR-011 | engine test on a 409 answer |
| Same-record changes are sent in order, one at a time | FR-007 | engine ordering test |
| A clash or refusal on one record never blocks another | FR-011, FR-014 | engine test with two records |
| A deletion is always sent as a deletion | P-04.5, FR-004 | queue test |
| Waiting changes survive process death | FR-005 | persistence test that reopens the database |
| Nothing is ever sent under another account | FR-018, P-09 | sign-out/sign-in test |
| Refused changes are never retried automatically | FR-014 | engine test on 422/404 |
| Profile behaviour is unchanged | FR-019 | spec 002 profile tests and E2E flows |

### 5. API

No new endpoints. The engine sends each record through its own per-record upsert, following
`api-conventions.md` Writes for every syncable resource:

| Answer | Engine action |
|---|---|
| 200 / 201 | clear the waiting change; store the returned record |
| `409 VERSION_CONFLICT` (with `current`) | keep the change; store `current` as the clash; wait for the person |
| `422 VALIDATION_FAILED` / `IMMUTABLE_FIELD` / `INVALID_ID`, `404 NOT_FOUND`, `403 FORBIDDEN`, `405 METHOD_NOT_ALLOWED` | mark refused with the reason; stop |
| `429 RATE_LIMITED`, `5xx`, offline, timeout | stay waiting; retry with backoff, honouring `retry_after_seconds` |
| `401 UNAUTHENTICATED` | existing session-ended flow (spec 001); the queue is discarded with the sign-out |

Pulling in wave 0 stays per record type (re-reading its endpoints, as the profile does today). The
change feed (`/sync/changes`, cursors, `410 SYNC_CURSOR_EXPIRED`) and batch upload arrive with
backend B14, in the wave 2 spec.

#### Contract changes since last spec

None: error catalogue and API conventions are unchanged since `56b44fb`. Follow-up, not in scope of
this spec: `SYNC_CURSOR_EXPIRED` (410) exists in the catalogue but not in the client's error codes;
it is only returned by the change feed, which arrives in wave 2.

### 6. Authorization and entitlements

Sends use the signed-in session; the engine adds no rights. A change is tagged with the account that
made it and discarded at sign-out (FR-018). Wallet roles (C4) will decide which changes a screen
lets the person make; the engine sends whatever was queued and reports refusals. No entitlement
gates.

### 7. Events

Not applicable: the client publishes no domain events. Internally the engine emits status changes
that screens observe, and listens to the existing sign-out signal to discard the queue.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Never lost | Unit (queue, collapse, ordering, backoff) + persistence (reopen database) + background-run test | engine tests |
| US2 Status | Unit (counts, per-record state) + UI | status tests, screenshots |
| US3 Clashes | Unit (409 handling, keep/use-theirs, tombstone) + profile screen | engine + profile tests |
| US4 Refusals | Unit (422/404 handling, edit/discard) | engine tests |
| US5 Sign-out | Unit (warning count, discard, account tagging) + UI | sign-out tests |
| Profile unchanged | spec 002 tests + E2E (`.maestro/002-account-security/US3-*`) | existing suites |

Offline scenarios can't be driven end to end today (the E2E runner reaches the backend through
`adb reverse`, which airplane mode doesn't cut); they are proved by unit and persistence tests, and
the clash scenario (US3-S1) by the existing API-session E2E flow for spec 002 US3-S4.

### 9. Open questions

- **Background sending on Android**: the platform scheduler may delay background work under battery saver; the spec requires "eventually, once a connection exists", not a deadline. Decided by: plan. Non-blocking.
- **Wave 2 contract**: batch size, per-item semantics, cursor per scope (brief C14). Decided by: backend owner in B14. Non-blocking for this spec.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of changes made offline reach the server, or are shown to the person as a clash or refusal, once a connection returns, including after the app was closed or the phone restarted.
- **SC-002**: Zero changes are silently overwritten or dropped: every clash and every refusal is shown to the person.
- **SC-003**: Once a connection returns, waiting changes reach the server within 1 minute while the app is open, and eventually while it is closed.
- **SC-004**: A person can tell in one glance at the account area whether any change is still only on the phone.
- **SC-005**: No person loses waiting changes at sign-out without having been told how many first (person-initiated sign-outs).
- **SC-006**: Every spec 002 profile scenario still passes unchanged after the profile moves onto the engine.
- **SC-007**: A new synced record type (C4 wallets) is added without changing the engine's queue, sending, retry, clash or sign-out behaviour.

## Assumptions

- Wave 0 only: per-record sends and per-record-type pulls. The change feed, cursors, `410` handling and batch upload come with backend B14 in a later spec (brief C14 wave 2).
- Profile is the only synced record people can edit today; device sessions are read-only on the phone and stay outside the queue (ending one is an online-only credential action, spec 002).
- Retries use increasing delays from seconds up to a few minutes, and never stop while a change is waiting; exact delays are a planning detail.
- Background sending uses the platform's deferred-work facility on Android; iOS stays a compile target (no live API, spec 001).
- "Sync now" and the waiting counts live in the account area until a settings screen exists.
- A person-initiated sign-out warns; forced sign-outs (session ended, reset) can't be prevented and discard silently, as spec 002 already does for the rest of the data.
