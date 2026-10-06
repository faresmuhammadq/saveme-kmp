# Feature Specification: Export & Deletion Completion (client)

**Feature Branch**: `016-export-deletion`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "specs/briefs/C15-export-deletion.md" — client brief C15, wave 4. Pairs with backend B15 → spec 022 (contract designed and frozen; implementation in progress, uncommitted at the time of writing). Extends spec 002's deletion request/cancel.

**Backend contract**: `saveme-backend@776e5b2`

> The spec 022 contract this spec binds to (`docs/openapi/paths/exports.yaml`, the `/account/deletion-request` edits in `paths/identity.yaml`, `openapi.yaml` v1.13, ADR-0024) sits **uncommitted** on top of `b63e917`. The contract check must be rerun once spec 022 is committed (integration checkpoint, protocol step 4).

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/exports.yaml` (v1.13: `POST /exports` → 202 `Export`, `GET /exports` cursor-paged, `GET /exports/{id}`), `paths/identity.yaml` (v1.13: new `GET /account/deletion-request` → `DeletionPlan`; `POST` body `DeletionRequest {password, successors?}`; `DeletionSchedule.successors`), `components/responses.yaml` (Conflict lists `EXPORT_IN_PROGRESS`), `components/schemas.yaml` (`Problem.current` also on `EXPORT_IN_PROGRESS`), `error-catalog.yaml` (`EXPORT_IN_PROGRESS` 409 automatic, since v1.1; `RATE_LIMITED`; `NOT_FOUND`; `VALIDATION_FAILED`).
- Backend behaviour: `saveme-backend/specs/022-export-data-deletion/` (`spec.md` user stories 1–4 and edge cases, `plan.md` decisions A-1…A-22, `contracts/export-deletion-api.md` "What C15 binds to"), `docs/adr/0024-account-deletion-keeps-shared-history.md` (successor order, `purging` state, the departed id is the "Former member" marker).
- Glossary (`saveme-backend/docs/glossary.md`): **Export** (new in spec 022), **Successor** (new; UI "new owner"), **Wallet**, **Wallet Member** (UI "member"), **Owner**/**Editor**/**Viewer**, **Transaction**, **Recurring Rule**, **Device Session**. Banned in user-facing copy: "token", "XLSX"/"Excel" (no such format is produced), "encrypted" (the archive is not encrypted, A-8), any claim that other members' names are included.
- Builds on spec 001 (design system, error copy by code, request id on unexpected errors, session-ended return to sign-in and the sign-out wipe), 002 (Account screen, Delete account screen with Explain → Confirm → Scheduled, Home pending-deletion notice with "Cancel deletion", account state refresh), 005 (Entitlements gate — **not** used here, see FR-004), 006 (wallet detail, members read), 008 (transaction "Added by …" caption), 010 (rule detail author line), 013 (members, activity "A former member", online-only membership actions never queued or retried, loss-of-access purge), 015 (online-only account actions; memory-only secrets with redacted printing).

## Clarifications

### Session 2026-10-06

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Does `export.enabled` (C3) gate exports, as the brief says it "may"? → A: **No.** The backend does not gate export by plan, feature flag or address verification (spec 022 A-5, P-09 rule 5: export is a privacy right) and serves it during the deletion grace period. The brief's "may gate" is superseded; the app reads no entitlement for export. (FR-004)
- Q: How does a ready export leave the app — system browser, share sheet, or both? → A: **System browser only** ("Open download"), with a link read from the server immediately before opening. No share sheet, no "copy link": the signed link is a 15-minute bearer secret to the person's whole financial history, and handing it to another app or person is not needed to save the file (the browser's own download does that). The file never touches app storage. (FR-013–FR-016)
- Q: How often does the app check an export that is being prepared, and when does it stop? → A: **Every 5 seconds while the export's screen is in the foreground and the export is queued or running**; it stops on a final state, when the screen leaves the foreground, when the connection is lost, and 10 minutes after the request (then "Still preparing — we'll email you when it's ready" with a manual "Check again"). The exports list never polls. (FR-010, FR-011)
- Q: When the person reviews new owners, does the app send a choice for every wallet where one is expected, or only where they changed the server's suggestion? → A: **Every wallet where a choice is expected**, with the member shown on the review (the server's suggestion unless changed). The person has explicitly confirmed each one, and what they saw is what the server stores. Wallets with one other member are shown but carry no choice (that member always becomes owner). (FR-024, FR-025)
- Q: How do transactions, rules and activity name an author who has left (including a deleted account)? → A: **"A former member"** wherever the author's id matches no member the phone holds for that wallet, **provided the phone holds that wallet's member list**; a member present without a name stays "another member"; before the member list has been read the app keeps "another member". Activity already does this (spec 013). (FR-033–FR-035)

## User Scenarios & Testing *(mandatory)*

Online-only throughout: exports and the deletion plan are server resources, not syncable data
(P-04.6). Nothing of either is stored on the phone, and nothing is queued.

### User Story 1 - Export all my data and download it (Priority: P1)

A person opens Account → "Export my data", asks for an export of everything, watches it move from
"Waiting" to "Preparing" to "Ready", and taps "Open download": the system browser downloads the ZIP
file. The app never holds the file.

**Why this priority**: data portability is a legal and store-policy expectation; it is also the
advice the deletion flow and the backend's reminder mail give ("export first").

**Independent Test**: with a signed-in account on the local backend (queue worker running), request
an account export, wait on its screen until "Ready", tap "Open download" and see the browser open
the signed link; inspect the app's storage: no ZIP, no link.

**Acceptance Scenarios**:

1. **Given** a signed-in person on any plan (verified or not, active or pending deletion), **When** they open Account → "Export my data", **Then** the Exports screen lists their exports of the last 30 days, newest first, each with its kind ("Everything" or the wallet's name), status and request date, and offers "New export".
2. **Given** the Exports screen, **When** they tap "New export", choose "Everything in my account" and confirm, **Then** the request is sent once and the export's screen opens showing "Waiting to start".
3. **Given** an export's screen with the export queued or running, **When** it stays in the foreground, **Then** the status is read every 5 seconds and shown in words ("Waiting to start", "Preparing your file"), with a progress indicator that is not the only signal.
4. **Given** the export becomes ready, **When** the screen shows it, **Then** it says "Ready", the file size, when it was prepared and until when it can be downloaded (24 hours), and offers "Open download"; polling stops.
5. **Given** a ready export, **When** "Open download" is tapped, **Then** the app reads the export again, and if it is still ready opens its fresh `download_url` in the system browser; the link is not shown, copied, stored or logged.
6. **Given** a ready export whose file has meanwhile expired, **When** "Open download" is tapped, **Then** the screen changes to "Expired" with "New export" and the browser is not opened.
7. **Given** the explanation on the request screen, **When** read, **Then** it says the file is a ZIP with the data in JSON files and the transactions in a CSV spreadsheet file, that other members appear as "other member" without their names, that it is kept for 24 hours, and that an email is also sent when it is ready; it promises no other format and no encryption.

---

### User Story 2 - Export one wallet (Priority: P2)

A member of a wallet (owner, editor or viewer) opens the wallet and taps "Export this wallet"; the
request screen opens with that wallet chosen.

**Why this priority**: a common request ("send my accountant this wallet") and backend-supported
for every role, but the account export already covers the data.

**Independent Test**: as a viewer of a shared wallet, open it → "Export this wallet" → confirm; the
export's screen shows the wallet's name and reaches "Ready".

**Acceptance Scenarios**:

1. **Given** a wallet the server already has (not a create still waiting on this phone), **When** any member opens its detail, **Then** "Export this wallet" is offered, also for archived wallets.
2. **Given** the request screen opened from a wallet, **When** shown, **Then** "One wallet" is selected with that wallet, and the person may switch to "Everything in my account" or pick another wallet they belong to.
3. **Given** a wallet export request for a wallet the person no longer belongs to (removed or deleted meanwhile), **When** the server answers `NOT_FOUND`, **Then** the screen says "You no longer have this wallet" and nothing is created.
4. **Given** a wallet export that failed with `wallet_unavailable`, **When** shown, **Then** it says "This wallet was no longer available when the file was prepared."
5. **Given** an export of a wallet the phone no longer holds, **When** listed, **Then** it is named "A wallet you no longer have".

---

### User Story 3 - Understand why an export can't start or didn't finish (Priority: P2)

The person asks for an export while one is already being prepared, or after reaching the daily
limit, or the export fails; each case says what happened and what to do.

**Why this priority**: the one-active-export and 5-per-24-hours rules are certain to be met by
people who tap twice; a silent failure would look like a lost request.

**Independent Test**: request two exports in a row → the second shows the conflict with "See it"
opening the first; with a backend at its daily limit, the request shows the wait.

**Acceptance Scenarios**:

1. **Given** an export queued or running, **When** another is requested (any scope), **Then** the server's `EXPORT_IN_PROGRESS` shows "An export is already being prepared. Wait for it to finish before asking for another." with "See it", which opens the export the answer carries in `current`.
2. **Given** 5 accepted requests in the last 24 hours, **When** another is requested, **Then** `RATE_LIMITED` shows "You've asked for the most exports allowed in a day. Try again in <n> hours" (or minutes under an hour; "later" without a wait), and the request action stays unavailable until the wait passes.
3. **Given** a failed export (`generation_failed`, `timed_out`, or an unknown failure code), **When** shown, **Then** it says "We couldn't prepare this file." and offers "Try again", which requests a new export of the same scope.
4. **Given** no connection or a timeout, **When** the person requests, opens a download or opens either screen, **Then** the screen says it needs a connection and offers "Try again"; nothing is queued or retried automatically.
5. **Given** an export's screen polling for 10 minutes since the request without a final state, **When** the cap is reached, **Then** polling stops and the screen says "Still preparing. We'll email you when it's ready." with "Check again".
6. **Given** changes still waiting on this phone (the sync status says so), **When** the request screen opens, **Then** it notes that changes not yet sent are not in the export.

---

### User Story 4 - Choose who keeps my shared wallets before deleting my account (Priority: P1)

A person who owns shared wallets asks to delete their account. After the explanation, the app
shows each shared wallet they own and who will become its owner. Where the wallet has more than
one other member, they pick the new owner (the server's suggestion is preselected). They review,
confirm with their password, and see the date and the new owners.

**Why this priority**: the owner's decision of 2026-09-25 — one member leaving never destroys
others' history — depends on this step; the backend falls back silently, so asking is the only
way the person's wish is honoured.

**Independent Test**: owner O of wallet A (one viewer) and wallet B (an editor and a viewer):
Delete account → Continue → "New owners" lists A → the viewer (no choice) and B with a choice
defaulting to the editor; pick the viewer; Review; password; Scheduled shows the date, A → viewer,
B → viewer; `GET /account/deletion-request` answers B `reason: chosen`.

**Acceptance Scenarios**:

1. **Given** the explanation step, **When** shown, **Then** it states the 30-day grace period (as spec 002), that shared wallets the person owns pass to another member and are not deleted, that wallets only they use are deleted with the account, that their entries in shared wallets stay and show as "a former member", that a Google Play subscription is **not** cancelled by deleting the account and must be cancelled in Google Play, and offers "Export my data first".
2. **Given** "Continue" on the explanation, **When** the app reads the deletion plan, **Then** while reading it shows progress; offline it says it needs a connection and stays on the explanation with "Try again"; the person cannot reach the password step without a plan.
3. **Given** a plan with no successors, **When** read, **Then** the "New owners" step is skipped and the review says no shared wallet is affected.
4. **Given** a plan entry with `choice_expected: false`, **When** shown, **Then** the wallet's name and "<member> will become the owner" appear without a choice.
5. **Given** a plan entry with `choice_expected: true`, **When** shown, **Then** the wallet offers a choice among its other members on this phone (name or "Member without a name", role, joined date), with the server's `successor_user_id` preselected and labelled "Suggested".
6. **Given** the review step, **When** shown, **Then** it lists each shared wallet and its new owner, says the account will be deleted after 30 days (the exact date is the server's and is shown once scheduled), shows the Google Play notice, and "Delete my account" leads to the password step.
7. **Given** the correct password, **When** confirmed, **Then** the request carries the password and one choice per wallet with `choice_expected: true`, and the scheduled step shows the server's date and the new owners from the answer.
8. **Given** a choice the server refuses (`VALIDATION_FAILED` at `/successors/…`: the wallet or member changed meanwhile), **When** answered, **Then** the app says "Your shared wallets changed. Check the new owners again.", reads the plan again and returns to the "New owners" step with the new answer; nothing is scheduled.
9. **Given** a wrong password, a lock, or any other refusal, **When** answered, **Then** spec 002's handling applies unchanged.

---

### User Story 5 - See and change my pending deletion (Priority: P2)

During the grace period the person opens Account → "Delete account" and sees the date, what will
happen to each shared wallet, and can change the new owners (with their password) or cancel.

**Why this priority**: members join and leave during 30 days; the backend accepts replacement
choices while pending, and the person should see what will happen.

**Independent Test**: with a pending deletion and a chosen successor, open Delete account → the
pending view shows the date and the choice; change it with the password → the date is unchanged and
the new choice shows; cancel → active, nothing lost.

**Acceptance Scenarios**:

1. **Given** a pending deletion, **When** Delete account opens, **Then** it reads the plan and shows "Your account will be deleted on <date>", each shared wallet with its new owner and why ("the only other member", "your choice", "longest-standing editor", "longest-standing viewer"), "Change new owners" (when any wallet expects a choice), "Export my data", the Google Play notice, and "Cancel deletion".
2. **Given** "Change new owners", **When** the person picks and confirms with their password, **Then** the request is repeated with the new choices; the date shown does not change.
3. **Given** "Cancel deletion", **When** confirmed, **Then** spec 002's cancel applies and the confirmation adds that the new-owner choices were dropped.
4. **Given** a pending deletion whose date has passed (the server has not started yet), **When** shown on Home or here, **Then** the notice reads "Your account is being deleted." and "Cancel deletion" stays offered; the server decides.
5. **Given** the deletion begins on the server, **When** the app makes its next request, **Then** the session has ended (`UNAUTHENTICATED`) and the existing session-ended handling signs out and wipes the phone (spec 001/002); no deletion-specific message is shown.

---

### User Story 6 - See who left in shared wallets (Priority: P3)

A member of a shared wallet whose owner deleted their account opens the app: they are now the
owner, the wallet's history is intact, and the departed person's entries read "Added by a former
member".

**Why this priority**: what the deletion promised the remaining members; the backend already does
the transfer, the app only renders it.

**Independent Test**: after the purge of the owner (artisan, backend quickstart), the partner's
phone syncs: wallet detail shows them as owner; the departed person's transactions show "Added by a
former member"; activity shows "A former member"; the members list no longer lists them.

**Acceptance Scenarios**:

1. **Given** a transaction whose author is no longer among the wallet's members on this phone (and the phone holds the wallet's members), **When** shown in the editor's caption, **Then** it reads "Added by a former member".
2. **Given** a recurring rule whose author is no longer a member, **When** its detail shows, **Then** the author line reads "Added by a former member".
3. **Given** the wallet's ownership passed to this person, **When** the wallet feed is read, **Then** the wallet shows them as owner with owner actions (existing spec 006/013 behaviour) and no message about the departed person is shown beyond the activity entry.
4. **Given** a member present without a name (for example during the moment between the departed person's name being erased and their membership ending), **When** shown, **Then** the existing "another member" / "Member without a name" copy applies.

---

### Edge Cases

- **Two taps on "Request"**: the button is unavailable while a request is in flight; a second request after a lost answer meets `EXPORT_IN_PROGRESS` with `current` and opens that export (no duplicate).
- **Ready export, new request**: allowed (a ready export is not active); both are listed.
- **Export screen opened for an id that is gone** (record deleted after 30 days, or another person's): `NOT_FOUND` → "This export is no longer available." with Back.
- **Ready answer without `download_url`** (contract breach): the browser is not opened; the screen keeps showing "Ready" with the message "Couldn't open the download. Try again." and the next "Open download" makes a new read.
- **Link expiry**: the link is valid 15 minutes; it is never reused — every "Open download" reads a fresh one.
- **No browser can open the link**: "Couldn't open the download. Install or enable a web browser." The link is not shown.
- **App backgrounded while preparing**: polling stops; on return to the foreground the status is read once and polling resumes if still active.
- **Signed out or session ended while a screen is open**: polling stops; nothing is kept.
- **Deletion pending**: exports keep working; the date never moves because of them.
- **Plan lists a wallet the phone does not hold** (not yet read, or archived and hidden): named "A shared wallet"; no choice is offered for it and no choice is sent; the server's rule applies; the review says so.
- **Suggested successor not among the phone's members** (members not read yet): shown as "Another member" and preselected; the other candidates on the phone are offered; if the phone holds no other member for the wallet, no choice is offered and none sent.
- **A candidate the phone lists has left meanwhile**: the server answers `VALIDATION_FAILED`; the plan is re-read (US4-8).
- **More than 100 wallets expecting a choice**: unreachable within plan quotas; the app sends at most 100 choices (the first 100 in the plan's order) and the review says the rest follow the server's rule.
- **Plan read during the grace period after members changed**: the pending view always reads a fresh plan; a stored choice whose member left is shown with the server's resolved successor.
- **Deletion requested on another device**: the account state refresh of spec 002 picks it up; the pending view reads the plan on open.
- **Sign-in during the grace period** cancels the deletion (spec 002/007) and drops the choices (server); the existing "deletion cancelled" message applies.
- **The person is not an owner of any shared wallet**: the "New owners" step is skipped; the explanation still mentions wallets only they use are deleted.
- **A member's own deletion pending**: the app cannot see it (no field); the server applies its rule.
- **Unknown `reason` or `state` value in the plan**: an unknown reason shows no reason line; an unknown state is handled as pending without actions other than "Cancel deletion" and a general note to try again later.
- **Unknown export status or failure code**: an unknown status shows "Status unavailable" with "Check again"; an unknown failure code reads as `generation_failed`.

## Requirements *(mandatory)*

### Functional Requirements

**Exports — entry points**

- **FR-001**: The Account screen MUST offer "Export my data", opening the Exports screen, for every signed-in person.
- **FR-002**: A wallet's detail MUST offer "Export this wallet" to every member role (owner, editor, viewer), also for archived wallets, but only for a wallet the server already has; it opens the request screen with that wallet chosen.
- **FR-003**: The pending-deletion view and the deletion explanation MUST offer "Export my data", opening the Exports screen.
- **FR-004**: Exporting MUST NOT be gated by plan, entitlement (`export.enabled` is not read), address verification or deletion state.

**Exports — list and request**

- **FR-005**: The Exports screen MUST read the person's exports (newest first, 20 per page, "Show more" for the next page) each time it opens and on "Refresh", and show each with its kind ("Everything" / wallet name / "A wallet you no longer have"), status in words, and request date and time in the person's time zone; tapping a row opens that export's screen; a refused page position (`INVALID_CURSOR`) restarts from the first page once; an empty list says "No exports in the last 30 days."
- **FR-006**: The request screen MUST offer two scopes, "Everything in my account" (default from Account) and "One wallet" (a choice among the wallets on this phone the person is a member of and the server already has, including archived), the explanation of FR-018, and "Request export".
- **FR-007**: "Request export" MUST send exactly one request per tap (`{scope: account}` or `{scope: wallet, wallet_id}`), be unavailable while one is in flight, and on `202` open the export's screen.
- **FR-008**: Exports are online-only: requests and reads MUST NOT be queued or retried automatically, and nothing about an export (record, link, file) is stored on the phone.
- **FR-009**: When the phone has changes waiting to sync, the request screen MUST say that changes not yet sent are not included.

**Exports — status**

- **FR-010**: The export's screen MUST read the export when it opens and, while it is `queued` or `running` and the screen is in the foreground with a connection, every 5 seconds; it MUST show `queued` as "Waiting to start" and `running` as "Preparing your file", each with an indeterminate progress indicator and the request time.
- **FR-011**: Polling MUST stop on `ready`, `failed`, `expired` or an unknown status; when the screen leaves the foreground; on sign-out; on no connection or timeout (showing the offline note with "Check again"); and once the export was requested more than 10 minutes ago by the phone's clock or 120 reads were made in this visit, showing "Still preparing. We'll email you when it's ready." with "Check again" (which reads once and resumes polling if still active). Returning to the foreground reads once and resumes polling if the export is still active and the cap is not reached.
- **FR-012**: The export's screen MUST name its kind (as FR-005). `ready` MUST show "Ready", the size (under 1 MB in KB rounded up, otherwise in MB with one decimal; Western digits), when it was prepared, "Available until <time, date>" from `expires_at`, and "Open download"; `expired` MUST show "Expired. Files are kept for 24 hours." with "New export"; `failed` MUST show the failure copy of FR-022 with "Try again".

**Exports — download**

- **FR-013**: "Open download" MUST read the export again and, only if it is still `ready` with a `download_url`, open that URL in the system browser; if it is now `expired` the screen updates instead.
- **FR-014**: The download link MUST NOT be stored (in state, database, preferences, logs, crash reports or analytics), shown, copied to the clipboard or passed to the share sheet; it exists only between the read and the browser hand-off, and every type that carries it MUST print it redacted.
- **FR-015**: The app MUST NOT download, cache or write the export file itself.
- **FR-016**: When no app can open the link, the screen MUST say "Couldn't open the download. Install or enable a web browser." without showing the link.

**Exports — refusals (chosen by code — P-02.4, P-06.5)**

- **FR-017**: `EXPORT_IN_PROGRESS` → FR-019's message with "See it" opening the export in the answer's `current`; when `current` is missing or unreadable, "See it" opens the Exports screen refreshed.
- **FR-018**: The request screen's explanation MUST say: the file is a ZIP with the data in JSON files and the transactions in a CSV spreadsheet file; amounts are in the currency's smallest unit (for example piastres); other members appear as "other member", without their names; the file can be downloaded for 24 hours after it is ready; an email is also sent when it is ready. It MUST NOT mention XLSX/Excel, encryption, or other members' names.
- **FR-019**: Copy: "An export is already being prepared. Wait for it to finish before asking for another."
- **FR-020**: `RATE_LIMITED` → "You've asked for the most exports allowed in a day. Try again in <n> hours." (minutes, rounded up, under an hour; "Try again later." without a wait time), with "Request export" unavailable until the wait passes (memory only).
- **FR-021**: `NOT_FOUND` on a wallet request → "You no longer have this wallet."; on reading an export → "This export is no longer available."
- **FR-022**: Failure codes: `wallet_unavailable` → "This wallet was no longer available when the file was prepared."; `generation_failed`, `timed_out` and any unknown code → "We couldn't prepare this file." "Try again" requests a new export of the same scope (FR-007 rules apply).
- **FR-023**: `UNAUTHENTICATED` and update-required follow the app-wide handling; any other refusal shows the general failure with the server's request id (P-13.2).

**Deletion — new owners**

- **FR-024**: After the explanation (spec 002 FR-017, extended by FR-030), the deletion flow MUST read `GET /account/deletion-request` before the password step; without a plan (offline, error) the password step is not reachable; offline shows the connection note with "Try again", other failures the general failure with the request id.
- **FR-025**: When the plan lists successors, a "New owners" step MUST list each wallet: wallets with `choice_expected: false` show "<member> will become the owner"; wallets with `choice_expected: true` offer a single choice among that wallet's other members on this phone (excluding the person), each shown with name (or "Member without a name"), role and joined date, the plan's `successor_user_id` preselected, marked "Suggested" and listed first, the others by joined date (earliest first). Names come from the phone's member records only. A choice is offered only when the phone holds the wallet and at least one of its other members; a suggested member the phone does not hold is shown as "Another member".
- **FR-026**: A review step MUST list every wallet and its new owner, state that the account is deleted after 30 days and that wallets only the person uses are deleted with it, show the Google Play notice, and lead to the password step ("Delete my account"). It MUST NOT compute or show a deletion date of its own.
- **FR-027**: The deletion request MUST carry the password and `successors` with one `{wallet_id, member_user_id}` per wallet that had a choice offered (at most 100, plan order); when no choice was offered it MUST omit `successors`. The scheduled step MUST show the server's date and the answer's `successors`.
- **FR-028**: `VALIDATION_FAILED` with an error pointer under `/successors` MUST show "Your shared wallets changed. Check the new owners again.", read the plan again and return to the "New owners" step with choices reset to the new suggestions; nothing is scheduled. Password errors keep spec 002 FR-018 handling.
- **FR-029**: Deletion requests, plan reads and cancels MUST NOT be queued or retried automatically (spec 002 FR-024).

**Deletion — explanation, pending view, end**

- **FR-030**: The explanation MUST add to spec 002's text: shared wallets you own pass to another member and are not deleted; wallets only you use are deleted with the account; your entries in shared wallets stay and show as "a former member"; "Deleting your account does not cancel a Google Play subscription. Cancel it in Google Play to stop payments."; and "Export my data first".
- **FR-031**: While pending, Delete account MUST open a pending view that reads the plan and shows the date (person's time zone), each shared wallet with its new owner and reason, "Change new owners" (only when a wallet expects a choice; it reuses FR-025–FR-028 with the password and never moves the date), "Export my data", the Google Play notice and "Cancel deletion" (spec 002 FR-020; the confirmation adds "Your choices of new owners were dropped." when any were stored). Saving changed owners returns to the pending view with "New owners saved." and the same date. When the plan cannot be read, the view shows the date from the account snapshot, the connection or general note with "Try again", "Export my data" and "Cancel deletion", and hides "Change new owners".
- **FR-032**: When the scheduled date has passed while still pending, Home's notice and the pending view MUST read "Your account is being deleted." and keep "Cancel deletion". The `purging` state is not visible to the app: the ended session's `UNAUTHENTICATED` triggers the existing sign-out and wipe of every table; no deletion-specific message is shown.

**Former member**

- **FR-033**: A transaction whose author id is not the person and matches no member record the phone holds for its wallet MUST read "Added by a former member", **only when the phone holds at least one member record for that wallet**; otherwise the existing "Added by another member" stays.
- **FR-034**: A recurring rule's author line MUST follow FR-033 ("Added by a former member").
- **FR-035**: Activity keeps spec 013's "A former member"; no other screen shows the departed person's id.

**Common**

- **FR-036**: The client's error-code list MUST add `EXPORT_IN_PROGRESS` (retry class automatic; not retried by this feature) with English and Arabic copy; the problem's `current` MUST be decoded as an export for this code.
- **FR-037**: The deletion schedule decoding MUST accept the new `successors` member, and the request body MUST carry only `password` and, when present, `successors` (P-02.2); the request type MUST print the password redacted.
- **FR-038**: All new screens and steps MUST use design-system components only; work in Arabic RTL and English LTR; stay usable at 200% font; give 48 dp targets; carry stable test tags; never convey status by colour alone; and announce status changes and refusals to screen readers.
- **FR-039**: The Exports screen, the request screen and the export's screen MUST each follow MVI (P-16), as must the extended Delete account flow.

### Key Entities *(include if feature involves data)*

- **Export (server-owned, read on demand)**: id, scope (account or wallet), wallet id, status (queued, running, ready, failed, expired), failure code, requested/completed/expires instants, size; a short-lived download link only when ready. Never stored on the phone; the link is held only between a read and the browser hand-off.
- **Deletion plan (server-owned, read on demand)**: state (active or pending deletion), requested and scheduled instants, and per owned shared wallet the successor's user id, the reason and whether a choice is expected. Never stored; names come from the phone's member records.
- **Successor choice**: wallet id + member user id, held by the deletion flow in memory until sent.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Gives a person their data on demand and completes account deletion so that leaving never takes
shared wallets away from others: the person chooses who keeps each shared wallet, sees what will
happen during the grace period, and remaining members see the departed person as "a former member".

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Export** (UI "export" / "تصدير"), **Successor** (UI "new
owner" / "المالك الجديد"), **Wallet**, **Wallet Member** ("member" / "عضو"), **Owner** / **Editor** /
**Viewer**, **Transaction**, **Recurring Rule**. "A former member" / "عضو سابق" (existing spec 013
string). No new glossary term on the client. Never: "token", "XLSX", "Excel", "encrypted",
"account" for a wallet.

### 3. Data model

No new syncable entity and **no Room change** (Room stays v10). Exports and the deletion plan are
server resources read on demand (P-04.6); nothing of them is cached.

| Data | Where it lives on the device | Cleared |
|---|---|---|
| Export list page, export status | Screen state, memory only (without the link) | Screen closed, sign-out |
| Download link | A local value between the fresh read and the browser hand-off | Immediately |
| Deletion plan, successor choices | Delete account flow state, memory only | Flow closed, sign-out |
| Rate-limit wait | Request screen state, memory only | Screen closed |
| Account state and date | Existing account snapshot (spec 001/002) | Sign-out (existing) |

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| The download link is never stored, shown, logged, copied or shared; types carrying it print it redacted | P-09.2, FR-014 | redacted `toString` tests; log-capture test over a ready→open flow; state contains no URL |
| The app never writes an export file | FR-015 | layering/scan test: export code uses no file or download API |
| Every "Open download" uses a link from a read made for that tap | FR-013 | store test: two taps → two reads, each URL used once |
| Polling: 5 s, only foreground + active status; stops on final state, background, offline, sign-out, 10-minute cap | FR-010, FR-011 | store tests with a virtual clock |
| Exports are online-only: one request per tap, never queued or retried | FR-007, FR-008, P-02.6 | service/store tests |
| Export is not gated (no entitlement read) | FR-004 | test: request reaches the API with every feature off and unverified |
| `EXPORT_IN_PROGRESS` opens `current` | FR-017 | contract + store test |
| The password step is unreachable without a plan | FR-024 | store test |
| A choice is sent exactly for wallets that offered one; none otherwise; max 100 | FR-027 | store/use-case tests over the identity.yaml examples |
| Successor refusals re-read the plan and schedule nothing | FR-028 | store test |
| "Former member" only when the author matches no member and the wallet's members are known | FR-033, FR-034 | mapping tests |
| Copy never claims XLSX, encryption or others' names; Play notice present | FR-018, FR-030 | string scan test |

### 5. API

All under `/api/v1`, frozen in backend spec 022 (`openapi.yaml` v1.13, committed as `saveme-backend@776e5b2`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Request | `POST /exports` | `{scope}` / `{scope, wallet_id}` | 202 `Export`; 401; 404 `NOT_FOUND` (wallet); 409 `EXPORT_IN_PROGRESS` + `current`; 422 `VALIDATION_FAILED`; 426; 429 `RATE_LIMITED` (+Retry-After); 400/406/415/500 generic |
| List | `GET /exports?limit=20&cursor=` | — | 200 collection (`next_cursor`, `has_more`); 400 `INVALID_CURSOR` → restart from the first page; 401; 426; others generic |
| Status / fresh link | `GET /exports/{id}` | — | 200 `Export`; 401; 404 `NOT_FOUND`; 422 generic |
| Deletion plan | `GET /account/deletion-request` | — | 200 `DeletionPlan`; 401; 426; others generic |
| Request deletion (changed) | `POST /account/deletion-request` | `{password, successors?}` | 200 `DeletionSchedule` (+`successors`); 401 `INVALID_CREDENTIALS`; 422 `VALIDATION_FAILED` (`/password`, `/successors…`); 423 `ACCOUNT_LOCKED`; as spec 002 |
| Cancel (unchanged) | `DELETE /account/deletion-request` | — | 204; as spec 002 |
| Download (web, signed) | `download_url` (outside `/api/v1`, not in OpenAPI) | opened by the system browser only | — |

No `Idempotency-Key` on any of these (spec 022 A-9). All API requests carry the standard headers
(P-02.5); the browser download carries none.

#### Contract changes since last spec

Baseline `saveme-backend@b63e917` (spec 015) → `776e5b2` (spec 022 committed; checked at implementation, T001: the contract files are unchanged from the frozen design):

- **Error codes**: none added or removed. `EXPORT_IN_PROGRESS` (409, automatic, since v1.1) has its meaning reworded ("the body's `current` is that export"); it was never in the client's list — FR-036, FR-017.
- **Shared responses**: Conflict lists `EXPORT_IN_PROGRESS`; `Problem.current` is also sent on it, under the `getExport` schema — FR-017, FR-036.
- **New paths** (v1.13): `POST/GET /exports`, `GET /exports/{id}` — FR-005–FR-023.
- **Identity** (v1.13): new `GET /account/deletion-request` (`DeletionPlan`) — FR-024, FR-031; `POST` body now `DeletionRequest {password, successors?}` — FR-027, FR-028, FR-037; `DeletionSchedule.successors` now required in the answer — FR-027, FR-037.
- **Client mismatches**: none (every client code exists in the catalogue with the same retry class).

### 6. Authorization and entitlements

Exports: any signed-in person, own exports only; a wallet export requires an active membership of
any role (server). Not plan-gated, not verification-gated, allowed while pending deletion
(FR-004; the brief's "`export.enabled` may gate" is superseded by spec 022 A-5). Deletion: any
signed-in person with their password (spec 002). The successor list is decided by the server;
the app only offers choices the server says are expected.

### 7. Events

Not applicable: the client publishes no domain events. It consumes the app-wide session-ended
(the purge ends every session) and update-required events, and the wallet and user feeds that
carry the ownership transfer and the departed member's tombstone (existing spec 009/013 handling).

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Export everything | Unit (status mapping, polling, link handling) + contract (MockEngine from `exports.yaml` examples) + captured live answers + E2E | tests; `.maestro/016-export-deletion/US1-*` (deferred) |
| US2 Export a wallet | Unit + contract + captured + E2E | tests; `US2-*` (deferred) |
| US3 Refusals | Unit (refusal mapping, rate-limit wait) + contract per code + captured + E2E | tests; `US3-*` (deferred) |
| US4 New owners | Unit (plan mapping, choice building) + contract (`identity.yaml` examples) + captured + E2E | tests; `US4-*` (deferred) |
| US5 Pending view | Unit + store + E2E | tests; `US5-*` (deferred) |
| US6 Former member | Unit (author mapping) + E2E after the backend purge (artisan) | tests; `US6-*` (deferred) |
| Screens | Reducer + store + screenshot (EN LTR, AR RTL, 200 %) | screen tests |

Owner policy: no emulator, Maestro or device run; flows are written and marked "deferred: E2E on
owner's device". Host tests plus a live-backend contract QA (curl against the local backend with
throwaway accounts, after backend spec 022 is implemented and committed; real answers captured as
fixtures replayed through the real client), as specs 007–015.

### 9. Open questions

- **Backend contract gaps (non-blocking)**: see Assumptions "Backend gaps".
- **Arabic copy** for the new strings: assumed defaults for owner review. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From the Account screen, a person asks for an export of everything in 3 taps (Export my data, New export, Request export).
- **SC-002**: A ready export opens in the browser in 1 tap from its screen; 100% of opens use a link read for that tap.
- **SC-003**: 0 export files and 0 download links in app storage, logs or crash reports across the feature's tests.
- **SC-004**: 100% of polling sequences stop on a final state, on leaving the screen, offline, at sign-out, or at the 10-minute cap; no screen polls more often than every 5 seconds.
- **SC-005**: Every export refusal and failure the feature handles (in progress, rate limit, wallet gone, export gone, 3 failure codes, no connection) shows its own message in both languages; 0 show the server's text.
- **SC-006**: 100% of owned shared wallets with more than one other member show a choice before the password is asked, preselected to the server's suggestion; 0 deletions are requested without the plan having been shown.
- **SC-007**: After a co-owner's purge, 100% of their transactions and rules on a partner's phone read "a former member".

## Assumptions

### Design defaults

1. Exports live in the Account area ("Export my data") and on wallet detail ("Export this wallet"); Home gets nothing. The pending-deletion view and the explanation link to the Exports screen.
2. Three export screens (list, request, status) rather than a sheet: room for 200 % text and the explanation.
3. Download through the system browser only; no share sheet or copy (clarification 2). The browser saves the file through its own download handling.
4. Polling every 5 s in the foreground only, 10-minute cap, list never polls (clarification 3); the ready mail covers the person who leaves.
5. The rate-limit wait disables "Request export" in memory only (as spec 015).
6. "Try again" on a failed export requests a new export of the same scope (failures are not active; spec 022 edge case).
7. The plan is required before the password step; the deletion flow is online-only (as spec 002).
8. A choice is sent for every wallet that offered one (clarification 4); successor names come only from the phone's member records (the plan carries none, by design).
9. The pending view lets the person change choices with their password (the server's replace-on-repeat), never moving the date.
10. The `purging` state has no UI of its own; the ended session signs out and wipes as today. No "your account was deleted" message (a 401 cannot say why).
11. "A former member" is computed from the phone's member list and only when that list is known (clarification 5).
12. No Room change; no new dependency; no new design-system component unless the plan finds one missing.
13. Exports and deletion live in the existing `feature.account` context (the backend's Identity module owns both).

### Backend gaps (non-blocking)

1. The contract is uncommitted (spec 022 implementation in progress); the contract check and the live QA rerun when it is committed.
2. `DeletionPlan` carries no names, and a wallet the phone has not read cannot be named; the app falls back to "A shared wallet" / "Another member".
3. `purging` is not exposed; the app cannot tell the person their account is being deleted now, nor tell a 401 caused by the purge from any other.
4. The signed download route is outside OpenAPI; its error page (expired or purged) is the server's web answer, shown by the browser, not chosen by the app.
5. The plan does not say whether a candidate is themselves pending deletion; the server's rule handles it, the app cannot show it.
6. `Export` has no wallet name; exports of wallets the phone no longer holds are unnamed.
7. No push when an export is ready (C16 not built); the mail is the out-of-app signal.
