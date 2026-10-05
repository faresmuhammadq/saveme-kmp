# Feature Specification: Redeem Code (client)

**Feature Branch**: `015-redeem-code`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "specs/briefs/C11-redeem-code.md" — client brief C11, wave 3. Pairs with backend B11 → spec 020 (built, committed `7155d6d`; `instapay.yaml` v1.12, frozen for C11 and unchanged since). App side only: the InstaPay claim is web-only (P-12.4).

**Backend contract**: `saveme-backend@b63e917`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/instapay.yaml` (v1.12: `POST /billing/redeem` with `RedeemRequest {code}`, `RedeemResult {subscription, governing, entitlements}`; `Idempotency-Key` required), `paths/billing.yaml` (`MySubscription`, `EntitlementSummary`, `PlanRef`), `docs/openapi/error-catalog.yaml` (`REDEEM_CODE_EMAIL_MISMATCH`, `REDEEM_CODE_USED`, `REDEEM_CODE_EXPIRED` since v1.12; `EMAIL_NOT_VERIFIED`, `RATE_LIMITED`, `NOT_FOUND`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`), `docs/api-conventions.md` (Idempotency).
- Backend behaviour: `saveme-backend/specs/020-instapay-claims/summary.md` ("Notes for … the mobile client"), `contracts/instapay-api.md` ("What C11 binds to"), `docs/adr/0023` (claim-bound redeem codes; the subscription is created at redemption; InstaPay periods chain; the governing subscription follows ADR-0013 rule 14: the latest billing-period end wins, no end beats any end).
- Glossary (`saveme-backend/docs/glossary.md`): **Redeem Code**, **Subscription**, **Plan**, **Entitlement**, **billing period**. Banned in any user-facing string: "InstaPay", "voucher", "coupon", "gift card", "promo", "token", any price, any payment address or web checkout link.
- Builds on spec 001 (design system, error copy by code, request id on unexpected errors), 002 (account screen), 003 (verify prompt `EmailNotVerifiedSheet`, app-wide `EMAIL_NOT_VERIFIED` event, resend), 005 (C3: Plan screen with the place reserved for "Enter code", `Entitlements.refreshAfterBillingAction()`, `SessionManager.refreshEntitlements()`, the stored governing subscription), and 013 (pattern: online-only actions never queued or retried automatically, secrets held only in memory with redacted `toString`, sensitive clipboard).

## Clarifications

### Session 2026-10-06

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Where does "Enter code" appear — the Plan screen only, or also Account or Home? → A: **The Plan screen only**, in the place spec 005 reserved, for every signed-in person on any plan. Account keeps its "Plan" row as the way there; Home gets nothing. One billing surface (P-12.3) and no promotion of an off-store payment path. (FR-001)
- Q: Should an unverified person be able to open "Enter code", and what happens when they tap "Redeem"? → A: **They can open it and type; the screen notes that a verified address is needed, and "Redeem" opens the existing verify prompt without sending** (the phone's own flag, like C9's invite gate). It spares the 10-an-hour budget; a stale flag is corrected by the server's `EMAIL_NOT_VERIFIED`, which shows the same prompt. (FR-003, FR-021)
- Q: How far does the app go to keep the code out of screenshots, the clipboard and keyboards? → A: **The whole redeem screen is excluded from screenshots, recordings and the recent-apps preview while shown; the field offers paste only (no copy or cut); the keyboard is asked not to learn or suggest; the code is memory-only and printed redacted.** The code is a mailed secret (P-09.2); blocking the person's own screenshot is the accepted cost. (FR-011, FR-029)
- Q: After a rate-limit refusal, may the person tap "Redeem" again before the wait ends? → A: **No.** "Redeem" stays unavailable until the server's wait time passes (memory only, so reopening the screen forgets it and the server answers again); without a wait time it stays available. Nothing is retried automatically. (FR-007, FR-022)
- Q: Where does success show — a message on the Plan screen, or a confirmation on the redeem screen? → A: **A confirmation on the redeem screen** (it has room for the governing-plan and Google Play notes), with "Done" returning to the Plan screen, which already shows the applied plan. (FR-013–FR-016)

## User Scenarios & Testing *(mandatory)*

A person paid for a plan by bank transfer on the website; after an operator checked the payment,
they received an email with a code like `7KQ2-MX4D-9TRB-H3WE`. In the app they open their Plan,
choose "Enter code", type or paste the code, and the plan attaches to their account. The code
only works for the account whose email address it was sent to, once, within 30 days. The app
never mentions how or where the plan was paid for.

### User Story 1 - Redeem a code and see my new plan (Priority: P1)

A signed-in person with a verified address opens Plan → "Enter code", pastes or types the code
from their email, taps "Redeem", and sees a confirmation naming the plan that now applies and
until when. Back on the Plan screen, the new plan and its capabilities show at once.

**Why this priority**: It is the whole feature: without it a person who paid outside Google Play
cannot get what they paid for in the app.

**Independent Test**: An operator approves a claim for `mona@example.com`; Mona, signed in and
verified, enters the mailed code; the confirmation says "You're now on Plus · Monthly" with its end
date; the Plan screen shows Plus and sharing included, without signing in again.

**Acceptance Scenarios**:

1. **Given** a verified, signed-in person on the free plan and a valid code sent to their address, **When** they enter it and tap "Redeem", **Then** the app confirms the code was redeemed, names the plan that now applies and when its billing period ends (or "No end date"), and the Plan screen and every capability answer show that plan immediately.
2. **Given** the person pastes the code with spaces, lower-case letters or the hyphens from the email, **When** it lands in the field, **Then** the field shows it as four groups of four upper-case characters, and "Redeem" becomes available once all 16 are present.
3. **Given** the person types the code by hand, **When** they type, **Then** each group of four is separated automatically, letters show upper-case, the letter O is read as zero and I or L as one, and characters that can never be part of a code are not accepted.
4. **Given** fewer than 16 characters, **When** the person looks at the screen, **Then** "Redeem" is not available and nothing is sent.
5. **Given** the same person enters a code they already redeemed on this account, **When** they tap "Redeem", **Then** the app shows the same confirmation as the first time (nothing is added twice).
6. **Given** a successful redemption, **When** the person taps "Done", **Then** they return to the Plan screen showing the new plan.

---

### User Story 2 - Understand which plan applies when I already have one (Priority: P1)

A person who already has a subscription that ends later (bought on Google Play, or granted)
redeems a code. The app says plainly that the redemption worked, that their existing subscription
still applies because it ends later, how long the redeemed plan runs, and — when the existing one
is a Google Play subscription that renews — that Google Play will keep billing it until they
cancel it there.

**Why this priority**: The backend decides which subscription governs (latest end wins); saying
"You're now on Plus · Monthly" when a yearly Google Play subscription still applies would be wrong
and could make a person pay twice without knowing it.

**Independent Test**: A person with Plus granted with no end date redeems a Plus · Monthly code;
the confirmation says the code was redeemed, that their current plan (no end date) stays in
place, and that the redeemed plan runs until its end date.

**Acceptance Scenarios**:

1. **Given** the answer's governing subscription is the one just redeemed, **When** the confirmation shows, **Then** it says "You're now on <plan>" with its billing-period end, or "No end date".
2. **Given** the answer's governing subscription is a different one (another channel, or the same channel ending later), **When** the confirmation shows, **Then** it says the code was redeemed, names the plan that applies and its end (or "No end date"), and states that the redeemed plan runs until its own end date.
3. **Given** the governing subscription is a Google Play subscription set to renew, **When** the confirmation shows, **Then** it adds that Google Play will keep charging for it until it is cancelled in Google Play.
4. **Given** an early renewal (the redeemed billing period starts after the current one ends), **When** the confirmation shows, **Then** it says when the redeemed billing period starts.

---

### User Story 3 - Be told clearly why a code did not work (Priority: P1)

When a code cannot be redeemed, the person sees, in their language, what happened and what to do:
the code is not valid, it was already used, it has expired, it was sent to a different email
address, their address is not verified yet, they tried too many times, or there is no
connection.

**Why this priority**: Every refusal here has a different remedy (check the code, sign in with
another account, verify, wait, contact support); a generic failure leaves a paying person stuck.

**Independent Test**: With the live backend: a mistyped code, a code another account already
redeemed, an expired code, a code sent to a different address, and an eleventh attempt within an
hour each show their own message in English and Arabic; none names the other email address.

**Acceptance Scenarios**:

1. **Given** a code the server does not know (or one an operator withdrew), **When** redeemed, **Then** the app says the code isn't valid and to check it, the same message for both causes.
2. **Given** a code another account already redeemed, **When** redeemed, **Then** the app says this code has already been used.
3. **Given** a code past its 30 days, **When** redeemed, **Then** the app says the code has expired and to contact support.
4. **Given** a code sent to an address other than the signed-in account's, **When** redeemed, **Then** the app says the code was sent to a different email address and to sign in with the address it was sent to, shows which address the person is signed in with, and never shows or hints at the other address.
5. **Given** a signed-in person whose address is not verified, **When** they open "Enter code", **Then** the screen says a verified address is needed, and tapping "Redeem" opens the verify prompt with "Resend email" and sends nothing; if the server still answers "not verified" (the phone's copy was stale), the same prompt appears.
6. **Given** the person's attempts are spent (10 an hour), **When** the server refuses, **Then** the app says "Too many attempts. Try again in <n> minutes" from the server's wait time and keeps "Redeem" unavailable until then.
7. **Given** no connection or a timeout, **When** they tap "Redeem", **Then** the app says it needs a connection and offers "Try again"; nothing is queued and nothing is retried automatically.
8. **Given** a retry after a timeout whose first request did reach the server, **When** they tap "Try again" with the same code, **Then** the server recognises the same attempt and the person sees one redemption, never a duplicate or a "used" refusal for their own attempt.
9. **Given** any other refusal, **When** it arrives, **Then** the general failure message shows with the server's request id.

### Edge Cases

- **Which account**: the code works only from an account whose address equals the one the code was mailed to. The app never reveals that address (backend uniform answers, P-09.4); the mismatch message asks the person to sign in with the address the code was sent to, and shows the address they are signed in with now (their own, already shown on the account screen).
- **Unverified and wrong address together**: the server reports the wrong address first. A person the phone believes unverified is stopped by the phone's verify prompt before any request, so after verifying they may then see the mismatch message; this order is accepted.
- **Stale verified flag**: verified on another device or the website while the phone still says unverified — the phone's gate shows the verify prompt; the account summary refreshes on the next account read (spec 003); the verify prompt's own flow is unchanged.
- **Double tap / impatient retry**: "Redeem" is unavailable while a request runs; a retry with the same code reuses the same attempt identifier, so a request that reached the server is never run twice.
- **Server still working on the same attempt** (`IDEMPOTENCY_REQUEST_IN_PROGRESS`): "Still checking this code. Try again in a moment." with "Try again".
- **Code changed after a failure**: editing the code starts a new attempt (a new identifier); the old one is forgotten.
- **Leaving during a request**: a success that arrives after the person left the screen is still applied to the plan the app shows; refusals that arrive then are dropped.
- **Leaving the screen**: the typed code is dropped when the screen closes, when the app process ends, and at sign-out; it is never stored, logged, written to crash reports or analytics, or restored after process death.
- **Screenshots and the app switcher**: while the redeem screen is shown, the system does not capture its content in screenshots, screen recordings or the recent-apps preview.
- **Clipboard**: the app never puts the code on the clipboard; the field offers paste but not copy or cut.
- **Sign-out or session end during a request**: the answer is discarded; nothing of it is applied to the next person.
- **Answer applied, refresh fails**: the plan from the answer stays applied; the follow-up re-read failing offline only shows the Plan screen's existing "may be out of date" note.
- **Lifetime plan** (no billing-period end): the confirmation says "No end date".
- **Unknown status or provider values** in the answer: shown as no status; a provider the app does not know is treated as "another subscription" (no Google Play note).
- **Pasted text with surrounding words** (a whole line from the email): if it contains one code in the mailed grouped form, that code is taken; otherwise the usual character filter applies and anything beyond 16 characters is dropped.
- **Large font / RTL**: the code always reads left to right in four groups, also inside an Arabic layout; the screen stays usable at 200% font.
- **Pending account deletion**: no special handling; the server decides.

## Requirements *(mandatory)*

### Functional Requirements

**Entry and screen**

- **FR-001**: The Plan screen MUST offer "Enter code" in the place spec 005 reserved for billing actions, to every signed-in person whatever their plan. It opens the redeem screen. No other screen offers it.
- **FR-002**: The redeem screen MUST show a short explanation ("Enter the code from your email. It works only on the account with the email address it was sent to."), one code field (focused, with the keyboard open, when the screen opens), and a "Redeem" action. It MUST NOT show any price, payment method, payment address, website link, or the word "InstaPay" (P-12.3, P-12.4).
- **FR-003**: When the phone's account summary says the address is not verified, the redeem screen MUST show a note that a verified address is needed, and "Redeem" MUST open the existing verify prompt (spec 003) instead of sending.

**Code input**

- **FR-004**: The code field MUST hold at most 16 code characters and show them as `XXXX-XXXX-XXXX-XXXX` (separators inserted automatically, never typed), upper-case, read left to right in every language.
- **FR-005**: Every character entered (typed or pasted) MUST be normalised: letters upper-cased; `O` read as `0`; `I` and `L` read as `1`; spaces, hyphens and line breaks dropped; any character outside the code alphabet (digits and A–Z without I, L, O, U, after the readings above) dropped.
- **FR-006**: Pasted text that contains a code in the mailed grouped form (four groups of four code characters separated by hyphens or spaces, any case) MUST yield that code; any other pasted text goes through FR-005 and is cut to 16 characters.
- **FR-007**: "Redeem" MUST be available only with exactly 16 code characters, no request in flight, and no wait time from a rate limit still running. The keyboard's done action does the same as "Redeem" when it is available.
- **FR-008**: The app MUST send the code as the 16 normalised characters in the grouped form `XXXX-XXXX-XXXX-XXXX` (accepted by the server).

**Request**

- **FR-009**: Redeeming MUST be online-only: never queued, never retried automatically, and offline or on timeout it MUST say it needs a connection and offer "Try again".
- **FR-010**: Each attempt MUST carry an attempt identifier (the server's `Idempotency-Key`): generated when "Redeem" is first tapped for a given code, reused by every retry of the same code ("Try again", or "Redeem" again after any failure), and replaced by a new one when the code is edited. It lives in memory only.
- **FR-011**: The code and the attempt identifier MUST NOT be logged, stored, sent to crash reporting or analytics, put on the clipboard by the app, or kept across process death; the state that holds them MUST print redacted.

**Success**

- **FR-012**: On success (first or repeated redemption by this account) the app MUST, only when the same person is still signed in: replace the stored entitlements with the answer's `entitlements` and the stored governing subscription with the answer's `governing`, so every capability answer and the Plan screen change at once; then ask for the C3 refresh after a billing action, whose failure leaves the applied answer in place. Leaving the screen while a request runs MUST NOT stop a success from being applied (the confirmation is then not shown).
- **FR-013**: The confirmation MUST say the code was redeemed and name the plan that now applies and its billing-period end in the person's time zone (or "No end date"), taken from the answer's `governing`, never from the redeemed subscription alone. A governing subscription in its grace period is named with its grace end date instead.
- **FR-014**: When the governing subscription is not the redeemed one (it differs in provider, plan or billing-period end), the confirmation MUST also say that the existing subscription stays in place because it ends later (or has no end) and give the redeemed plan's name and billing-period end; when the governing provider is Google Play with automatic renewal on, it MUST add that Google Play keeps charging until it is cancelled there.
- **FR-015**: When the redeemed billing period starts after the moment of redemption (an early renewal), the confirmation MUST say the date it starts.
- **FR-016**: "Done" and Back MUST return to the Plan screen; the confirmation MUST NOT show the code.

**Refusals (chosen by code, never by the server's text — P-02.4, P-06.5)**

- **FR-017**: `NOT_FOUND` → "This code isn't valid. Check it and try again." (unknown and withdrawn codes alike).
- **FR-018**: `REDEEM_CODE_USED` → "This code has already been used."
- **FR-019**: `REDEEM_CODE_EXPIRED` → "This code has expired. Contact support for help."
- **FR-020**: `REDEEM_CODE_EMAIL_MISMATCH` → "This code was sent to a different email address. Sign in with that address to use it." plus "You're signed in as <own address>." The other address MUST NOT be shown or hinted.
- **FR-021**: `EMAIL_NOT_VERIFIED` → the verify prompt (the existing app-wide handling) and the screen's "verify your address first" note.
- **FR-022**: `RATE_LIMITED` → "Too many attempts. Try again in <n> minutes." (rounded up; "in a minute" under 60 seconds) from the server's wait time, with "Redeem" unavailable until it passes; without a wait time, "Too many attempts. Try again later." and "Redeem" stays available.
- **FR-023**: `VALIDATION_FAILED` at `/code` → "This isn't a valid code. Codes have 16 letters and digits."
- **FR-024**: `IDEMPOTENCY_REQUEST_IN_PROGRESS` → "Still checking this code. Try again in a moment." with "Try again" (same attempt identifier).
- **FR-025**: `IDEMPOTENCY_KEY_REUSED` → the general failure with the request id, and the next attempt gets a new identifier.
- **FR-026**: `UNAUTHENTICATED` and an update-required answer follow the existing app-wide handling; any other refusal shows the general failure with the server's request id (P-13.2), or without it when the answer carries none.
- **FR-027**: A refusal message MUST stay until the code is edited or the person tries again, and MUST be announced by screen readers.

**Common**

- **FR-028**: The client's error-code list MUST include `REDEEM_CODE_EMAIL_MISMATCH` (retry class after-change), `REDEEM_CODE_USED` (never) and `REDEEM_CODE_EXPIRED` (never) with Arabic and English copy, and `IDEMPOTENCY_REQUEST_IN_PROGRESS` (automatic) and `IDEMPOTENCY_KEY_REUSED` (never).
- **FR-029**: While the redeem screen is shown, its content MUST be excluded from screenshots, screen recordings and the recent-apps preview (Android); the code field MUST offer paste but not copy or cut, and MUST ask the keyboard not to learn or suggest from it.
- **FR-030**: The redeem screen MUST be built from design-system components only, in Arabic RTL and English LTR, readable at 200% font, with 48 dp touch targets, every interactive element and message carrying a stable test tag, and the field announced as "Redeem code" with the code read as characters.
- **FR-031**: Signing out or the session ending while the screen is open MUST drop the code, the attempt identifier and any answer still arriving.

### Key Entities *(include if feature involves data)*

- **Redeem Code (typed)**: up to 16 normalised characters held in memory by the screen only; never persisted.
- **Redemption attempt**: the attempt identifier bound to one normalised code; memory only.
- **Redemption answer**: the redeemed subscription (plan, billing-period start and end, status), the governing subscription (plan, provider, status, billing-period end, renews flag) and the resulting entitlements. Applied to the existing C3 records; not stored on its own.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a person who paid outside Google Play attach their plan to their account by entering the
mailed code, tells them exactly which plan now applies, and explains every refusal in their own
language — while the app itself never offers or names any payment path other than Google Play.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Redeem Code** (UI "code" / "رمز"), **Plan** ("خطة"),
**Subscription** (only in the Google Play note: "اشتراك Google Play"), **billing period**,
**Entitlement** (never shown). UI action names: "Enter code" / "أدخل رمزًا", "Redeem" / "استرداد".
No new glossary terms. Never: "InstaPay", "voucher", "coupon", "gift card", "promo code", "token",
a price, "pay on the website".

### 3. Data model

No new syncable entity and no Room change: redemption is an account action, not syncable data
(P-04.6). The answer updates two existing records owned by C3 (spec 005).

| Data | Where it lives on the device | Cleared |
|---|---|---|
| Typed code, attempt identifier, wait-until time | The redeem screen's state, memory only | Screen closed, process death, sign-out |
| Entitlements from the answer | The existing account snapshot (spec 001/005) | At sign-out (existing) |
| Governing subscription from the answer | The existing stored subscription record (spec 005) | At sign-out (existing) |

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| The code and attempt identifier are never logged, stored or printed | P-09.2, FR-011 | log-capture test over a full redemption; redacted `toString` tests; no persistence of screen state |
| Normalisation: case, O→0, I/L→1, separators dropped, alphabet only, max 16; grouped display; pasted grouped code extracted | FR-004–FR-006, backend format | normaliser table tests |
| One attempt identifier per code; reused on retry; new on edit; new after `IDEMPOTENCY_KEY_REUSED` | FR-010, FR-025 | store tests |
| Online-only: no queue, no automatic retry | FR-009, P-02.6 | service tests (one request per tap), store tests offline |
| "Now on …" comes from `governing`; Play-renews note only for Google Play with auto-renew on | FR-013, FR-014 | confirmation mapping tests over the two contract examples |
| The answer is applied only for the same signed-in person | FR-012, FR-031, spec 005 FR-008 | service test with sign-out mid-flight |
| Every refusal branches on code to its own copy; the mismatch copy never contains another address | FR-017–FR-026, P-09.4 | refusal mapping tests; copy test |
| No price, payment path or "InstaPay" on the redeem screen or in its strings | P-12.3, P-12.4, FR-002 | string scan test |
| Unverified (phone's copy) never sends | FR-003 | store test |

### 5. API

All under `/api/v1`, frozen in `instapay.yaml` v1.12 (backend spec 020, `7155d6d`; unchanged at `b63e917`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Redeem | `POST /billing/redeem` (bearer; `Idempotency-Key` required) | `{code}` | 200 `RedeemResult` (also a repeat by this account; `Idempotency-Replayed: true` on a replay); 401; 403 `REDEEM_CODE_EMAIL_MISMATCH`, `EMAIL_NOT_VERIFIED`; 404 `NOT_FOUND`; 409 `REDEEM_CODE_USED`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_REQUEST_IN_PROGRESS` (+Retry-After); 410 `REDEEM_CODE_EXPIRED`; 422 `VALIDATION_FAILED` `/code`; 426; 429 `RATE_LIMITED` (+Retry-After); 400/406/415/500 generic |
| Re-read after success (existing, C3) | `GET /me/entitlements`, `GET /me/subscription` | — | as spec 005 |

Not used, ever: `POST /billing/instapay/claims` (web only; it answers 404 to the app, P-12.4).
All requests carry the standard headers (P-02.5).

#### Contract changes since last spec

Baseline `saveme-backend@26e7c44` (spec 013) → `b63e917` (+ uncommitted work in the backend tree for spec 022, exports):

- **Error codes**: none added or removed between the baseline and `b63e917`. The three redeem codes (`REDEEM_CODE_EMAIL_MISMATCH` 403 after-change, `REDEEM_CODE_USED` 409 never, `REDEEM_CODE_EXPIRED` 410 never) predate the baseline (recorded by spec 013 as a C11 follow-up) — handled here by FR-018–FR-020, FR-028.
- **Shared responses (uncommitted backend work)**: the Conflict response lists `EXPORT_IN_PROGRESS`; `Problem.current` is also sent on it — follow-up: not in scope of this spec (C15 export).
- **Client mismatches**: none (every client code exists in the catalogue with the same retry class).
- **New to the client**: the `Idempotency-Key` header (first client operation that requires it) — FR-010; `IDEMPOTENCY_REQUEST_IN_PROGRESS` and `IDEMPOTENCY_KEY_REUSED` enter the client's list — FR-024, FR-025, FR-028.

### 6. Authorization and entitlements

Any signed-in person may redeem; the server requires a verified address equal to the code's
address. The phone gates only on its copy of "verified" (FR-003); the server stays the authority.
Redeeming is not plan-gated. The capabilities that result come from the answer's `entitlements`
and are read through C3's gate only (P-12.1). The plan label is display-only.

### 7. Events

Not applicable: the client publishes no domain events. It consumes the app-wide
`EMAIL_NOT_VERIFIED`, session-ended and update-required events, and triggers C3's refresh after a
billing action.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Redeem | Unit (normaliser, store, service apply) + contract (MockEngine from the `instapay.yaml` examples) + captured live answers + E2E | tests; `.maestro/015-redeem-code/US1-*` (deferred) |
| US2 Governing | Unit (confirmation mapping over `redeemedGoverns` and `playStillGoverns`) + captured live answer with an operator-granted lifetime plan + E2E | tests; `US2-*` (deferred) |
| US3 Refusals | Unit (refusal mapping, rate-limit wait, attempt identifier) + contract per code + captured live answers + E2E | tests; `US3-*` (deferred) |
| Screen | Reducer + store + screenshot (EN LTR, AR RTL, 200 %) | redeem screen tests |

Owner policy: no emulator, Maestro or device run; flows are written and marked "deferred: E2E on
owner's device". Host tests plus a live-backend contract QA (curl against the local backend with
throwaway accounts; a claim approved through the backend's own service and the code read from the
dev mail catcher; real answers captured as fixtures replayed through the real client), as specs
007–013.

### 9. Open questions

- **Backend contract gaps (non-blocking)**: see Assumptions "Backend gaps".
- **Arabic copy** for the new strings: assumed defaults for owner review. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person with the mailed code in their clipboard redeems it in under 30 seconds and 3 taps from the Plan screen (Enter code, paste, Redeem).
- **SC-002**: After a successful redemption, the Plan screen and every capability answer show the plan that now applies with 0 sign-ins and 0 extra steps, also when the follow-up re-read fails.
- **SC-003**: Every refusal the feature handles (8 codes plus no connection) shows its own message in both languages; 0 show the server's text or a bare failure.
- **SC-004**: 0 occurrences of the code or attempt identifier in logs, stored data or crash reports across the feature's test suite.
- **SC-005**: 0 prices, payment paths or mentions of InstaPay on the redeem screen and in its strings.
- **SC-006**: When an existing subscription still applies, 100% of confirmations name that plan, never the redeemed one as "now on".
- **SC-007**: A retry after a timeout never produces a second redemption or a "used" refusal for the person's own attempt.

## Assumptions

### Design defaults

1. "Enter code" lives only on the Plan screen, in the place spec 005 reserved (P-12.3: the in-app billing surface is "restore purchases" and "enter code"); the Account screen keeps its existing "Plan" row as the way there.
2. The redeem screen is a full screen (not a sheet), so the keyboard, paste menu and error messages have room at 200% font.
3. The app normalises as typed (FR-005) and sends the grouped canonical form; it does not validate a check digit (the format has none).
4. Online-only and never queued: a redemption is a server decision at that moment (as C4's ownership transfer and C9's member actions); P-04 does not apply (account data, P-04.6).
5. The phone's "verified" flag gates before sending (like C9's invite gate), saving the person's 10-an-hour budget; the server's refusal stays the authority.
6. The answer's `entitlements` and `governing` are applied directly (FR-012) and then re-read through C3's billing-action refresh; the applied answer is never rolled back by a failed re-read.
7. The confirmation names Google Play only when the governing subscription is Google Play's and renews; other channels are described as "your current plan" (spec 005: the channel is otherwise not shown).
8. No "Sign out" button on the mismatch message: the person signs out from Account if they want to (avoids an accidental sign-out from an error message).
9. The redeem screen is excluded from screenshots and the recent-apps preview (FR-029), the strongest of the owner's listed protections, accepted although it also blocks the person's own screenshot for support.
10. No automatic retry, including for `IDEMPOTENCY_REQUEST_IN_PROGRESS` and `RATE_LIMITED`, which the catalogue would allow: every send is a tap (as C9's token calls).
11. Device E2E obtains a code by submitting and approving a claim through the backend's own `ClaimService` inside the dev container, then reading the approval mail from the dev mail catcher (the code is stored only as a hash); flows are written but not run (owner policy).

### Backend gaps (non-blocking)

1. `RedeemResult.governing` (`MySubscription`) carries no subscription id, so "is the governing subscription the redeemed one?" is inferred by comparing provider, plan and billing-period end (FR-014). An id on `MySubscription` would make it exact.
2. A repeat redemption by the same account answers 200 without saying it is a repeat (only a same-key replay carries `Idempotency-Replayed`); the app shows the same confirmation either way.
3. No re-issue of an expired code (backend risk): the expired message can only point to support; there is no in-app support contact yet.
4. The approval mail is the only place the code exists in plain form; E2E depends on the dev mail catcher being reachable from the runner.
5. Fold `O`→`0` was not tested by the backend's QA (only I/L); the app folds before sending, so it does not depend on it.
