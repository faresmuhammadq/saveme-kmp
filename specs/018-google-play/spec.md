# Feature Specification: Google Play Billing (client)

**Feature Branch**: `018-google-play`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "specs/briefs/C10-billing-google-play.md" — client brief C10, wave 3. Pairs with backend B10 → spec 024 (designed and frozen, implementation to follow; `google-play.yaml` v1.15, written in the backend working tree and not yet committed). Buy a subscription with Play Billing from the Plan screen (C3), post the purchase to the backend, show the new entitlements; "Restore purchases" sends every owned purchase after sign-in and on demand.

**Backend contract**: `saveme-backend@e8c308a`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/google-play.yaml` (v1.15, uncommitted at `e8c308a`: `GET /billing/google/account-reference`, `POST /billing/google/purchases`, `POST /billing/google/restore`; `Idempotency-Key` required on both posts), `paths/billing.yaml` (`EntitlementSummary`, `MySubscription` with `provider`, `GET /plans` `prices[]` with `channel = google_play` and `product_id`), `docs/api-conventions.md` (Idempotency: key of 16–255 characters from `[A-Za-z0-9._:-]`, scoped to the user, only `2xx` stored, 24 hours).
- Backend behaviour: `saveme-backend/specs/024-billing-google-play/` — `spec.md` (FR-001–FR-062; section 5 "Frozen for C10"), `contracts/google-play-api.md` ("what C10 binds to"), `research.md`, `plan.md` (ADR-0026, not yet written: verifying one purchase is the single-item form of "restore purchases"; the account-reference read starts nothing).
- Glossary (`saveme-backend/docs/glossary.md`): **Plan**, **Subscription**, **billing period**, **Entitlement**; spec 024 adds **Play Purchase**, **Play Account Reference**, **Purchase Token** (code only). "Purchase" is allowed only for a Google Play purchase (glossary ban table).
- Builds on spec 005 (C3: Plan screen, catalogue, Google Play price only, the reserved billing-action place, `refreshAfterBillingAction()`, stored subscription and catalogue), 015 (C11: "Enter code" in that place, `Idempotency-Key` helper, error copy by code, refusal patterns, apply-then-refresh), 004 (the app-wide foreground signal), 016 (C15, in flight on its own branch: the static "Deleting your account does not cancel a Google Play subscription" notice).

## Clarifications

### Session 2026-10-06

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an **assumed default** for owner review.

- Q: The brief says "buy from the Plan screen"; constitution P-12.3 says "The in-app billing surface MUST expose only 'restore purchases' and 'enter code'". May the app show a "Subscribe" (buy) action? → A: **Not until P-12.3 is amended.** Read as written, rule 3 allows exactly two billing actions in the app, and spec 005 (FR-010, SC-004) already enforces "no purchase action anywhere". The backend reads its mirror rule (backend P-10.2, "in-app **API** surface") through ADR-0026 so that verify is the single-item form of restore — that reading covers the **API calls**, not a third button. So this feature ships "Restore purchases" and "Enter code" as the only billing actions, plus links that open **Google Play's own** subscription centre (Manage, Fix payment), which start no payment. The full in-app buying path (Subscribe per plan, Play's purchase sheet) is specified as **User Story 6**, built and tested against a fake, and **switched off and gated** on an owner amendment of P-12.3 (and backend P-10.2) — flagged for the owner, recommended wording in section 9. *Assumed default.* (FR-001, FR-040–FR-049)
- Q: Without a Subscribe action, how does someone without a subscription upgrade? → A: **No in-app path until the amendment.** Google Play's store page does not sell an app's subscriptions, so a "Get Plus on Google Play" link would mislead. Purchases that start in Google Play itself (resubscribing from Google Play's subscription centre, a Google Play promotion, a payment that finishes later) still reach the account through this feature. Someone with a Google Play subscription gets "Manage in Google Play". *Assumed default.* (FR-001, FR-030)
- Q: Where do prices come from? → A: **Google Play's own price for the person's Google Play country**, as Google Play reports it for the product named in the catalogue, formatted by the app from integer minor units with Western digits (P-03, P-06). The catalogue's reference amount is **never** shown (contract: "display Play's localised price, not `amount_minor`"); when Google Play cannot answer, the plan shows no price. *Assumed default.* (FR-036–FR-038)
- Q: How is a purchase retried without being granted twice or lost after the app is killed? → A: **Google Play is the record.** On app start, on return to the foreground and when the Plan screen opens (with in-run retries after a failed send), the app asks Google Play for this phone's purchases and sends every one the server has not acknowledged. Each send carries an `Idempotency-Key` **derived from the account, the purchase and Google Play's state**, so a retry after a timeout or a killed process reuses the same key; a recheck after a pending answer gets a new key once an hour. Nothing about a purchase is stored on the phone. *Assumed default.* (FR-010–FR-016)
- Q: Restore after sign-in — automatic, or only on tap? → A: **Both**, as the brief and backend spec 024 say: silently once after each sign-in **when Google Play reports at least one purchase** (so a person with none spends nothing of the 10-an-hour budget), and on tap of "Restore purchases" (which also sends an empty list, letting the server re-read the person's linked purchases). *Assumed default.* (FR-020–FR-026)

### Session 2026-10-06 (clarify pass)

- Q: Should a background verify that changes the plan tell the person? → A: **Only on the Plan screen, if it is showing**: a short message "You're on <plan>." from the answer's governing subscription; elsewhere silent — the plan itself changes everywhere. No system notification (C16 owns push). *Assumed default.* (FR-017)
- Q: Does the Plan screen's existing refresh action also look for Google Play purchases? → A: **Yes**: refresh runs C3's refresh and a purchase pass together (still one pass at a time). *Assumed default.* (FR-010)
- Q: How many automatic sends may one purchase cost in one run of the app? → A: **At most 6** (backoff `Retry-After`, else 30 s doubling to 15 min); then it waits for the next trigger. Keeps a stuck purchase well inside the 20-an-hour budget and leaves room for taps. *Assumed default.* (FR-016)
- Q: What may the app log about billing, and what does an unexpected error show? → A: **Fixed event names with outcome codes and counts only** (no token, key, reference, order id, product price); an unexpected refusal of a **tapped** restore or subscribe shows the generic error with the server's request id (P-13.2); background passes show nothing. *Assumed default.* (FR-024, FR-050)
- Q: Does the "another account" note offer "Sign out"? → A: **No**, as spec 015's mismatch message: the person signs out from Account if they want to, so an error note never signs anyone out by accident. *Assumed default.* (FR-032)

## User Scenarios & Testing *(mandatory)*

Google Play is the only way to pay for a plan inside the app. The phone never decides that a
purchase is paid: every purchase Google Play reports goes to the server, which checks it with
Google, ties it to exactly one account, acknowledges it so Google does not refund it, and answers
with the person's plan. The app never acknowledges a purchase itself and never grants anything
on its own.

### User Story 1 - A Google Play purchase reaches my account (Priority: P1)

A person's Google Play account has a purchase for this app that the server has not yet confirmed:
they resubscribed from Google Play's subscription centre, a slow payment method just completed,
or the app was closed while a purchase was being confirmed. The next time the app starts, returns
to the foreground or opens the Plan screen, the purchase is sent to the
server and the person's plan switches to Plus a moment later — without any tap.

**Why this priority**: it is the pipe every Google Play purchase goes through, whatever started
it; without it a paid purchase is refunded by Google after 3 days and the person loses their plan.

**Independent Test**: with a fake Google Play that reports one unacknowledged purchase of
`plus_monthly` and a mock server answering `200` from the contract example, start the app signed
in; see exactly one verify request with an `Idempotency-Key`, the entitlements and subscription
from the answer applied, the C3 refresh run, and no acknowledgement call to Google Play.

**Acceptance Scenarios**:

1. **Given** Google Play reports a purchased, unacknowledged purchase, **When** the app starts signed in (or returns to the foreground, or the Plan screen opens), **Then** the purchase is sent once with its product, the answer's entitlements and subscription are applied, and the Plan screen shows the new plan; the app never acknowledges the purchase.
2. **Given** the send fails for lack of a connection, a `503`, a `429` or `IDEMPOTENCY_REQUEST_IN_PROGRESS`, **When** it is retried (after the server's wait, or on the next trigger), **Then** it carries the **same** `Idempotency-Key`, also after the app was killed in between.
3. **Given** a purchase Google Play reports as pending (slow payment method), **When** first seen, **Then** it is sent once (the server answers `202`), nothing is granted, and the Plan screen shows "Payment pending in Google Play"; **When** Google Play later reports it purchased, **Then** it is sent again with a new key and Plus applies.
4. **Given** the server answers `202` for a purchase Google Play reports as purchased, **When** the next trigger comes at least an hour later, **Then** the app rechecks it with a new key; in between, the server may grant it on its own, which the C3 refresh shows.
5. **Given** the server answers `422 PURCHASE_NOT_VERIFIED`, **When** handled, **Then** that purchase is not sent again in this run of the app and nothing is granted.
6. **Given** the server answers `200` with `acknowledged: false`, **When** handled, **Then** the app applies the plan and does nothing else (the server keeps acknowledging).
7. **Given** Google Play reports only acknowledged purchases, **When** a trigger runs, **Then** no verify request is sent.

---

### User Story 2 - Restore my purchases (Priority: P1)

A person reinstalls the app, or signs in on a new phone with the same Google account. Their plan
comes back by itself after sign-in; if it does not, they tap "Restore purchases" on the Plan
screen and the app tells them what it found.

**Why this priority**: required by Google Play policy and by P-12.3 ("restore purchases" is one of
the two in-app billing actions); otherwise a reinstall looks like a lost payment.

**Independent Test**: a fake Google Play reporting two purchases; sign in; see one restore request
with both items and a fresh key, the answer's plan applied. Tap "Restore purchases"; see a second
request with a new key and the result message.

**Acceptance Scenarios**:

1. **Given** Google Play reports one or more purchases for this app, **When** the person signs in, **Then** the app sends them all in one restore request (at most 25) and applies the answer's entitlements and subscription; no message is shown unless a purchase belongs to another account.
2. **Given** Google Play reports no purchase, **When** the person signs in, **Then** no restore request is sent.
3. **Given** the Plan screen, **When** the person taps "Restore purchases", **Then** the button shows progress, the app sends every purchase Google Play reports (or an empty list) with a new key, applies the answer, and shows one result message: restored, nothing to restore, belongs to another account, payment pending, could not be confirmed, or Google Play not reachable — several may combine.
4. **Given** the restore request fails for lack of a connection, a `503`, a `429` or `IDEMPOTENCY_REQUEST_IN_PROGRESS`, **When** the person is told, **Then** the message says why (with the wait for a rate limit) and "Try again" reuses the same key.
5. **Given** Google Play itself cannot be reached (not installed, not signed in to Google Play, disconnected), **When** the person taps "Restore purchases", **Then** the app says Google Play is not available on this phone and sends nothing.
6. **Given** the person is offline, **When** they tap "Restore purchases", **Then** the app says it needs a connection and sends nothing.

---

### User Story 3 - One purchase, one account (Priority: P1)

A Google Play purchase belongs to the account that bought it, forever. When someone signs in to a
different account on the same phone (same Google account), the app does not hand the first
account's plan to the second, and tells them how to get it.

**Why this priority**: the server refuses it anyway; the app's job is to explain, not to loop.

**Independent Test**: mock server answering `409 PURCHASE_ALREADY_LINKED` to a verify and
`linked_elsewhere` to a restore item; see the "belongs to another account" message once, no retry
of that purchase in this run, nothing about the other account shown.

**Acceptance Scenarios**:

1. **Given** a verify answered `409 PURCHASE_ALREADY_LINKED`, **When** handled, **Then** the Plan screen shows "A Google Play purchase on this phone belongs to another account. Sign in with the account that bought it to use it." and the purchase is not sent again in this run of the app.
2. **Given** a restore with an item answered `linked_elsewhere`, **When** the result shows, **Then** it includes the same sentence, and the other items' outcomes still apply.
3. **Given** account A signs out and account B signs in on the same phone, **When** the app starts its triggers, **Then** nothing about A's purchases, pending state or messages from A's session is shown to B.

---

### User Story 4 - Keep my Google Play subscription healthy (Priority: P2)

A person's Google Play payment fails. While Google Play retries it (grace), the Plan screen warns
them and offers "Fix payment", which opens Google Play's subscription centre for this app. A
person with a Google Play subscription can always open it there to change or cancel it. When a
payment is pending, the Plan screen says so; a test purchase made by a license tester is marked as
a test.

**Why this priority**: it keeps paying people paying, and is Google Play's recommended handling of
grace and account hold; it involves no new server call.

**Independent Test**: a stored subscription `provider: google_play, status: grace` → the warning
and "Fix payment" show and emit an "open Google Play subscriptions" effect for the product; a
subscription with `provider: instapay` shows neither.

**Acceptance Scenarios**:

1. **Given** the governing subscription is Google Play's and in grace, **When** the Plan screen shows, **Then** it warns "Google Play couldn't take your payment. Fix it by <grace end> to keep <plan>." with "Fix payment", which opens Google Play's subscription centre for this app's product.
2. **Given** the governing subscription is Google Play's (any status), **When** the Plan screen shows, **Then** it offers "Manage in Google Play", which opens Google Play's subscription centre; for any other provider, neither link shows.
3. **Given** a verify answer whose purchase is `on_hold` or `paused`, **When** handled in this run, **Then** the Plan screen says the Google Play subscription is on hold (or paused) and offers "Fix payment" (hold) or "Manage in Google Play" (paused).
4. **Given** a Google Play purchase that is pending (Google Play says so, or the server answered `202`), **When** the Plan screen shows, **Then** it says "Payment pending in Google Play. Plus starts when Google Play confirms the payment." and nothing is granted.
5. **Given** a verify answer with `test: true`, **When** the plan applies, **Then** the Plan screen marks it "Test purchase" for the rest of this run.
6. **Given** a Google Play subscription that was refunded or revoked, **When** the C3 refresh reads the free plan, **Then** the Plan screen shows the free plan, every wallet and record stays readable (spec 005 FR-016), and no message blames the person.

---

### User Story 5 - See Google Play's prices (Priority: P2)

On the Plan screen each plan sold on Google Play shows the price Google Play will charge this
person, in their Google Play country's currency, written the way the app writes money.

**Why this priority**: it replaces spec 005's reference amount with the only price the person can
actually pay; it needs no server change.

**Independent Test**: fake Google Play reporting `plus_monthly` at 149.00 EGP and the catalogue
naming `plus_monthly`; the card shows "EGP 149.00" (Arabic: "149.00 ج.م" with Western digits);
with the fake unavailable, no price shows.

**Acceptance Scenarios**:

1. **Given** a catalogue plan with a `google_play` price naming a product Google Play knows, **When** the Plan screen shows, **Then** the plan shows Google Play's recurring price for its base plan, formatted by the app with Western digits and the currency's fraction digits.
2. **Given** Google Play cannot answer, does not know the product, or the plan has no `google_play` price, **When** the Plan screen shows, **Then** that plan shows no price (never the catalogue's reference amount).
3. **Given** the app language changes, **When** the Plan screen shows again, **Then** prices are formatted for the new language.

---

### User Story 6 - Subscribe with Google Play in the app (Priority: P3, **gated: P-12.3 amendment**)

**Switched off until the owner amends P-12.3** (see Clarifications). With the amendment, each plan
sold on Google Play gets "Subscribe"; tapping it opens Google Play's own purchase sheet for that
plan, tied to the person's account; when the person pays, the purchase goes through User Story 1's
pipe and the plan shows Plus; when they back out, nothing changes.

**Why this priority**: it is the brief's headline but is not allowed by the constitution as
written; everything it needs except the button is built by User Stories 1–5.

**Independent Test**: with the switch on and a fake Google Play: tap "Subscribe" on Plus monthly;
see the account reference read, the sheet launched with that reference and the monthly base
plan, then — scripted — purchased → one verify → Plus; cancelled → no request, no message;
already owned → "Manage in Google Play".

**Acceptance Scenarios**:

1. **Given** the switch is off (the shipped default), **When** the Plan screen shows, **Then** there is no Subscribe action anywhere in the app.
2. **Given** the switch is on, the person has no governing paid subscription, and Google Play knows the plan's product, **When** the Plan screen shows, **Then** that plan offers "Subscribe".
3. **Given** "Subscribe" is tapped, **When** the app is online, **Then** it reads the person's account reference, opens Google Play's sheet for the plan's base plan with that reference, and shows progress until the sheet opens; offline, it says a connection is needed and opens nothing.
4. **Given** the person pays, **When** Google Play reports the purchase, **Then** User Story 1 applies (verify, apply, refresh) and the Plan screen confirms "You're on <plan>."; pending → the pending note.
5. **Given** the person closes the sheet, **When** Google Play reports a cancellation, **Then** nothing is sent and no message shows.
6. **Given** the person already has a governing paid subscription (any provider) or Google Play reports they already own the product, **When** the Plan screen shows, **Then** no "Subscribe" shows; a Google Play subscription offers "Manage in Google Play".
7. **Given** Google Play refuses to open the sheet (billing unavailable, product unavailable, an error), **When** handled, **Then** the app shows a message per cause and sends nothing.

---

### Edge Cases

- **Process killed after paying, before the server answered**: the next app start sends the purchase with the same key (FR-012); the server replays or completes it.
- **Process killed while Google Play's sheet is open (US6)**: Google Play still records the purchase; the next trigger sends it.
- **Pending purchase that never completes**: Google Play stops reporting it; the app stops showing the pending note on the next trigger. Nothing to clean up on the phone.
- **Pending purchase and sign-out**: the next account sees nothing of it; the account that made it sees it again when it signs back in.
- **Signed out**: no trigger sends anything; purchases wait in Google Play.
- **Account deletion pending**: purchases are sent and honoured as for any account (backend FR-007). Deleting the account does not cancel a Google Play subscription; spec 016's notice says so (this feature changes nothing there).
- **Two triggers at once** (start and foreground, or a tap during a background pass): one pass runs at a time; a second waits for it and then finds nothing left to send.
- **More than 25 purchases on the phone**: restore sends them in order of most recent first, 25 per request, one request per batch (FR-022).
- **A purchase for a product the catalogue does not list** (`422 VALIDATION_FAILED` at `/product_id`): treated like `PURCHASE_NOT_VERIFIED` for this run; nothing granted; the app's catalogue is reloaded once.
- **The server answers with an unknown purchase state or restore outcome**: the plan from `entitlements` still applies; the unknown value adds no message.
- **Rate limit on verify (20 an hour) or restore (10 an hour)**: automatic sends wait for the server's `Retry-After`; a tap shows the wait.
- **Google Play's price currency differs from the person's default currency**: shown in Google Play's currency; no conversion.
- **Google Play reports a price that does not fit the currency's fraction digits**: no price is shown for that plan.
- **The person's governing subscription is InstaPay or manual and they also own a Google Play subscription**: both apply on the server; the Plan screen follows the governing one (spec 005/015); "Manage in Google Play" shows only when Google Play's governs. US6 never offers Subscribe while any paid subscription governs.
- **Upgrade or downgrade between monthly and yearly** (a replacement purchase): not offered in the app; done in Google Play's subscription centre, and the new purchase arrives through US1.
- **iOS**: the app compiles; Google Play is reported unavailable; no billing action other than "Enter code" shows.
- **Google Play shows a purchase made with another account's reference while B is signed in**: sent and answered `409`/`linked_elsewhere`; the app explains (US3) and does not loop.

## Requirements *(mandatory)*

### Functional Requirements

**Billing surface**

- **FR-001**: The Plan screen's billing actions MUST be exactly "Restore purchases" (this feature) and "Enter code" (spec 015), in the place spec 005 reserved, plus "Manage in Google Play" (only when the governing subscription is Google Play's, FR-030) and "Fix payment" (only when FR-031 applies), which open Google Play's own subscription centre and start no payment. No Subscribe, buy, checkout or price-entry action MAY appear while the US6 switch is off (P-12.2–P-12.4).
- **FR-002**: The app MUST NOT acknowledge or consume any Google Play purchase, and MUST NOT grant, extend or display a plan from a Google Play purchase on its own; the plan always comes from the server's answer or the C3 refresh (backend spec 024 "Frozen for C10").
- **FR-003**: Every billing request MUST be online-only and never queued in the sync outbox; automatic retries follow the error catalogue's retry class and `Retry-After` (P-02.6).
- **FR-004**: Google Play access MUST sit behind one narrow platform port (connect, product details, launch the purchase sheet, list purchases, purchase updates); everything else in this feature MUST be shared code testable with a fake port. Where no real port is installed (iOS, or Android before the Play Billing dependency is approved), the port MUST report "Google Play unavailable" and the feature MUST degrade to: no prices, "Restore purchases" answering "Google Play is not available on this phone", no automatic sends.

**Sending purchases (verify)**

- **FR-010**: While a person is signed in, the app MUST list Google Play's purchases for this app (subscriptions and one-time products) on app start, on return to the foreground (at most once a minute), when the Plan screen opens or is refreshed (no floor), and on every purchase update Google Play delivers; and send each purchase that Google Play reports as **not acknowledged** to `POST /billing/google/purchases` with its purchase token and product id. Acknowledged purchases MUST NOT be sent by these triggers (only by restore).
- **FR-011**: One pass MUST run at a time per signed-in person, verify and restore passes included (so one purchase is never sent twice at once); a trigger arriving during a pass MUST wait for it, then run once more if anything changed. A pass's answers MUST be discarded when the signed-in person changed or signed out meanwhile.
- **FR-012**: Each verify MUST carry an `Idempotency-Key` derived one-way from the signed-in account, the purchase and Google Play's state of it (pending or purchased), so the same purchase in the same state always gets the same key — across retries, foreground passes and process death; after a `202` for a purchase Google Play reports as purchased, rechecks MUST use a key that also includes the current UTC hour. The key and the purchase token MUST NOT be logged, shown or stored.
- **FR-013**: A pending purchase MUST be sent once per app run while pending (to tie it to the account on the server) and again when Google Play reports it purchased; it MUST NOT be granted locally.
- **FR-014**: On `200`, the app MUST apply the answer's entitlements and subscription through C3's existing apply path (same person check), launch C3's billing-action refresh, and keep the answer's purchase `state` and `test` flag in memory for the Plan screen (FR-031, FR-034). `acknowledged: false` changes nothing on the phone.
- **FR-015**: On `202`, the app MUST keep the purchase marked pending for the Plan screen and recheck it under FR-012 no sooner than an hour later.
- **FR-016**: Refusals MUST be handled by `code`: `PURCHASE_ALREADY_LINKED` → the "another account" note (FR-032), not sent again in this run; `PURCHASE_NOT_VERIFIED` and `VALIDATION_FAILED` → not sent again in this run, nothing shown unless the person tapped (restore), and on `VALIDATION_FAILED` the catalogue is reloaded once; `SERVICE_UNAVAILABLE`, `RATE_LIMITED`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`, no connection → retried with the same key after `Retry-After` (or 30 s, doubling to at most 15 minutes) within the run, at most 6 automatic sends per purchase per run, and on the next trigger; `IDEMPOTENCY_KEY_REUSED` → logged as a client defect (code only) and not retried in this run; `UNAUTHENTICATED` → the existing session-ended handling; anything else → retried on the next trigger only.
- **FR-017**: A background verify MUST NOT show a dialog or interrupt another screen; its outcomes appear on the Plan screen (FR-030–FR-035) and through the plan itself. When a `200` changes the plan while the Plan screen is showing, the screen MUST show "You're on <plan>." once (from the answer's subscription).

**Restore purchases**

- **FR-020**: After each sign-in (once per session start), the app MUST list Google Play's purchases and, when there is at least one, send them all to `POST /billing/google/restore` with a new key; it MUST apply the answer's entitlements and subscription as FR-014 and MUST show nothing unless an item is `linked_elsewhere` (FR-032). On a sign-in this restore replaces that trigger's verify pass; a cold start that resumes a stored session is not a sign-in and runs only the verify pass.
- **FR-021**: On Android, the Plan screen MUST offer "Restore purchases" to every signed-in person (also when the port reports Google Play unavailable, FR-025). On tap the app MUST list Google Play's purchases and send them all — including any refused earlier in this run — (an empty list when there are none) with a new key, showing progress on the button and disabling it until the answer.
- **FR-022**: A restore MUST send at most 25 purchases per request, each token once, most recent first; more MUST be sent in further requests of 25, each with its own key.
- **FR-023**: After a tapped restore, the app MUST show one result message assembled from the item outcomes and the plan: "Your purchases are restored." (any `linked`/`already_yours`), "No Google Play purchases to restore for this Google account." (none sent, or all `not_verified`), the "another account" sentence (`linked_elsewhere`), "A payment is still pending in Google Play." (`pending`), "Google Play couldn't be reached for some purchases. Try again later." (`unavailable`); unknown outcomes add nothing. The message stays until the person dismisses it, taps "Restore purchases" again or leaves the Plan screen, and is announced to screen readers.
- **FR-024**: Restore refusals MUST be handled by code like FR-016, except that a tapped restore shows each refusal (no connection, rate limit with its wait, service unavailable, in progress) with "Try again", which reuses the attempt's key; a tapped restore is never retried without a tap. An unexpected refusal shows the generic error with the server's request id (P-13.2).
- **FR-025**: When the port reports Google Play unavailable, a tapped restore MUST say "Google Play isn't available on this phone." and send nothing; the sign-in restore MUST do nothing.
- **FR-026**: Restore MUST NOT require a verified email address and MUST NOT be gated on any plan capability (backend FR-004).

**Plan screen status**

- **FR-030**: When the governing subscription's provider is `google_play`, the Plan screen MUST offer "Manage in Google Play", opening Google Play's subscription centre for this app and the subscription's product (or for this app when the product is unknown).
- **FR-031**: When the governing Google Play subscription is in grace, the Plan screen MUST show a warning naming the grace end (in the profile time zone, P-05) with "Fix payment" (FR-030's link); when a verify answer in this run reported `on_hold`, it MUST show the hold note with "Fix payment"; `paused` shows the paused note with "Manage in Google Play".
- **FR-032**: After a `PURCHASE_ALREADY_LINKED` or a `linked_elsewhere` outcome in this run, the Plan screen MUST show the "another account" note; it MUST NOT name, hint at or link to the other account, and MUST NOT offer "Sign out".
- **FR-033**: While Google Play reports a pending purchase for this app, or a verify in this run answered `202` and Google Play has not since reported the purchase gone, the Plan screen MUST show the pending note; nothing is granted.
- **FR-034**: When a verify answer in this run carried `test: true` for the purchase behind the governing subscription (governing provider `google_play` and its plan is the catalogue plan of that purchase's product), the Plan screen MUST label the current plan "Test purchase".
- **FR-035**: Every note in FR-030–FR-034 MUST be state, not a one-shot message: it shows whenever the Plan screen shows while its condition holds, and disappears when it no longer holds; all of them are cleared at sign-out.

**Prices**

- **FR-036**: For each catalogue plan with a `google_play` price, the app MUST ask Google Play for that product's details and show the recurring price of its first auto-renewing base plan (a one-time product: its price), converted from Google Play's micro-units to integer minor units of Google Play's currency (exact only; otherwise no price) and formatted with the app's money formatter (P-03, P-06). No floating-point type may hold it. Until Google Play answers, the plan shows no price (no placeholder).
- **FR-037**: The catalogue's `amount_minor` for `google_play` MUST NOT be shown anywhere in the app; a plan with no Google Play answer shows no price. No other channel's price is ever shown (spec 005 FR-014 narrowed).
- **FR-038**: Product details MUST be requested when the Plan screen opens and when the catalogue changes, at most once a minute, and held in memory only.

**Account, privacy, platform**

- **FR-050**: Purchase tokens, idempotency keys, account references, order ids and prices MUST NOT be logged, sent to crash reporting or analytics, persisted, or shown (P-09.2, P-13.3); in-memory types holding them MUST print redacted. Billing logs MUST be fixed event names with outcome codes and counts only.
- **FR-051**: Sign-out MUST clear every in-memory billing note, pending mark, refused-purchase set and price; nothing of this feature is persisted, so nothing else needs clearing.
- **FR-052**: The new error codes `PURCHASE_ALREADY_LINKED` (retry never) and `PURCHASE_NOT_VERIFIED` (retry never) MUST be added to the client's code list with English and Arabic copy; `IDEMPOTENCY_KEY_REQUIRED` and `IDEMPOTENCY_KEY_INVALID` need no copy (the app always sends a valid key) and fall to the generic error.
- **FR-053**: Every string MUST exist in English and Arabic; the Plan screen additions MUST mirror in RTL, read at 200% font, have 48 dp targets, and carry stable test tags; state MUST NOT be conveyed by colour alone (P-06, P-08).
- **FR-054**: Opening Google Play's subscription centre MUST use a plain `https://play.google.com/store/account/subscriptions` link with `package` (and `sku` when known), through the platform's link opener; when nothing can open it, the app shows "Couldn't open Google Play."
- **FR-055**: iOS MUST compile with the port reporting Google Play unavailable and MUST NOT show "Restore purchases", "Manage in Google Play" or "Fix payment" (P-15: no iOS billing in MVP).
- **FR-056**: The development flavour MUST offer a development-only scripted Google Play (excluded from release builds) whose purchases carry tokens the local backend's `fake` gateway is scripted with, so the whole flow can be exercised against the local backend without Google.

**Subscribe in the app (US6 — gated on the P-12.3 amendment)**

- **FR-040**: A build-time switch, off in every flavour, MUST control US6; while off, no Subscribe action, no account-reference read and no purchase-sheet launch MAY exist in any reachable code path. Turning it on requires the owner's P-12.3 (and backend P-10.2) amendment recorded in the plan.
- **FR-041**: With the switch on, a catalogue plan MUST offer "Subscribe" only when: the person is signed in, Google Play is available and knows the plan's product, no paid subscription governs (spec 005 status `active`/`grace`), Google Play reports no owned or pending purchase for this app, and no purchase pass is running.
- **FR-042**: "Subscribe" MUST read `GET /billing/google/account-reference` (once per signed-in account per run, kept in memory, cleared at sign-out) and launch Google Play's sheet for the plan's first auto-renewing base plan with that value as the obfuscated account id; it MUST NOT set an obfuscated profile id.
- **FR-043**: The subscribe flow MUST move through: idle → connecting → launching → (cancelled → idle) | (failed → idle with message) | (purchased → verifying → done | pending | another account | not verified | retrying). Only one flow at a time; the button shows progress while connecting, launching or verifying.
- **FR-044**: A cancellation by the person MUST show nothing and send nothing; Google Play refusals (billing unavailable, item unavailable, already owned, developer error, network, service unavailable, unknown) MUST each map to a message; "already owned" MUST run a restore pass instead of a message.
- **FR-045**: The account-reference read MUST be refused by code like any read (no connection, rate limit, session ended, generic with request id) and opens no sheet when it fails.
- **FR-046**: On "done", the Plan screen MUST confirm "You're on <plan>." using the answer's governing subscription, adding "Test purchase" when `test` is true.
- **FR-047**: A purchase completed while the Plan screen is not shown MUST still go through FR-010–FR-016 (the update listener lives outside the screen).
- **FR-048**: Upgrading or downgrading an existing Google Play subscription MUST NOT be offered in the app; "Manage in Google Play" covers it.
- **FR-049**: No introductory or trial offer MUST be chosen by the app; the base plan's default offer is used and Google Play shows any offer it applies.

### Key Entities

- **Google Play purchase (as Google Play reports it)**: product id, purchase token (secret), state (pending or purchased), acknowledged flag, obfuscated account id if any, purchase time. Read from Google Play on demand; never stored by the app.
- **Google Play product details**: product id, base plan id, recurring price in micro-units and currency, billing period, offer token (US6). Memory only.
- **Verify answer**: purchase state (`pending`, `active`, `grace`, `canceled`, `on_hold`, `paused`, `expired`, `revoked`), acknowledged, auto-renew, billing-period end, test; plus entitlements and subscription applied to C3's records.
- **Restore answer**: one outcome per item (`linked`, `already_yours`, `linked_elsewhere`, `not_verified`, `pending`, `unavailable`), entitlements and subscription.
- **Billing notes**: per signed-in person, memory only — pending products, "another account" seen, hold/paused seen, test flag, refused purchases for this run.
- **Play Account Reference (US6 only)**: 64 lowercase hex characters, read from the server, memory only.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a person's Google Play purchases become their plan in the app — whatever started the
purchase — and brings them back after a reinstall or on a new phone, while the app never decides on
its own that a purchase is paid, never acknowledges one, never shows a price other than Google
Play's, and offers no billing action beyond "Restore purchases" and "Enter code" until the owner
allows a Subscribe button.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Plan** ("خطة"), **Subscription** ("اشتراك", used in
"Google Play subscription"), **billing period**, **grace** (UI: "payment problem" wording, never
"grace"), **Entitlement** (never shown). Spec 024 adds **Play Purchase**, **Play Account Reference**
and **Purchase Token** — used in code and docs only; the UI says "Google Play purchase" /
"عملية شراء من Google Play" (the glossary allows "purchase" for Google Play only). UI action
names: "Restore purchases" / "استعادة المشتريات", "Manage in Google Play" / "الإدارة في Google Play",
"Fix payment" / "إصلاح الدفع", gated "Subscribe" / "اشترك". Never in UI copy: "token", "acknowledge",
"InstaPay", "order", a reference value. No new glossary term.

### 3. Data model

No Room change, no new key-value record and no syncable entity: Google Play is the record of a
purchase, the server is the record of the plan, and the phone keeps only memory notes (P-04.6:
account data). The answers update two existing records owned by C3 (spec 005).

| Data | Where it lives on the device | Cleared |
|---|---|---|
| Google Play purchases and product details | Read from Google Play on demand; memory only | Process death; sign-out |
| Billing notes (pending, another account, hold/paused, test, refused set) | Memory, per signed-in person | Sign-out; process death (rebuilt from Google Play and the next answers) |
| Idempotency keys | Derived per send; never stored (FR-012) | — |
| Entitlements from an answer | The existing account snapshot (spec 001/005) | At sign-out (existing) |
| Subscription from an answer | The existing stored subscription record (spec 005) | At sign-out (existing) |
| Account reference (US6) | Memory | Sign-out |

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| The app never acknowledges or consumes a purchase | backend 024 "Frozen for C10", FR-002 | fake port records no acknowledge/consume call across every pipeline test; port has no such operation (architecture test) |
| Nothing is granted without a server answer | FR-002, brief | pipeline test: a purchased purchase with the server down leaves entitlements unchanged |
| Same account + purchase + state → same key; after `202` a new key per hour; restore: new key per attempt, reused on retry | FR-012, FR-015, FR-024 | key derivation tests; pipeline tests across a simulated process death |
| Only unacknowledged purchases are sent by triggers | FR-010 | pipeline test |
| One pass at a time; answers for another person are discarded | FR-011 | pipeline test with sign-out mid-flight |
| `PURCHASE_ALREADY_LINKED` / `PURCHASE_NOT_VERIFIED` are never retried in the run | FR-016 | pipeline tests |
| Tokens, keys, references, order ids, prices are never logged or persisted | P-09.2, FR-050 | log-capture test over a full pass; redacted `toString`; persistence scan |
| Billing actions are exactly Restore and Enter code (+ Google Play links); no Subscribe while the switch is off | P-12.3, FR-001, FR-040 | Plan screen state test; string/code scan; switch-off build test |
| Prices come only from Google Play, in integer minor units | P-03, FR-036, FR-037 | price conversion tests; Plan screen mapping test with a catalogue amount and no Play answer |
| `data` imports no `domain`; the platform port has no Android type in common code | P-10.2, P-10.6 | `BillingLayeringTest` extended |
| Sign-out clears every note | FR-051 | notes-holder sign-out test |

### 5. API

All under `/api/v1`, frozen in `google-play.yaml` v1.15 (backend spec 024; uncommitted in the
backend tree at `e8c308a`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Send one purchase | `POST /billing/google/purchases` (bearer; `Idempotency-Key`) | `{purchase_token, product_id}` | 200 `PlayPurchaseResult` (also a replay, `Idempotency-Replayed: true`); 202 pending; 401; 409 `PURCHASE_ALREADY_LINKED`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_REQUEST_IN_PROGRESS` (+Retry-After); 422 `PURCHASE_NOT_VERIFIED`, `VALIDATION_FAILED`; 426; 429 `RATE_LIMITED` (+Retry-After); 503 `SERVICE_UNAVAILABLE` (+Retry-After); 400/406/415/500 generic |
| Restore | `POST /billing/google/restore` (bearer; `Idempotency-Key`) | `{purchases: [{purchase_token, product_id}] (0–25)}` | 200 `PlayRestoreResult`; 401; 409 `IDEMPOTENCY_*`; 422 `VALIDATION_FAILED`; 426; 429; 503; generic |
| Account reference (US6, gated) | `GET /billing/google/account-reference` (bearer) | — | 200 `{obfuscated_account_id}`; 401; 426; 429; generic |
| Product ids (existing, C3) | `GET /plans` | — | `prices[]` with `channel = google_play` → `product_id` |
| Re-read after an answer (existing, C3) | `GET /me/entitlements`, `GET /me/subscription` | — | as spec 005 |

Not used, ever: `POST /webhooks/google-play` (Google only), `POST /billing/instapay/claims` (web
only, P-12.4). All requests carry the standard headers (P-02.5).

#### Contract changes since last spec

Baseline `saveme-backend@b63e917` (spec 015) → `e8c308a` (+ uncommitted work in the backend tree):

- **Error codes (catalogue)**: none added or removed in `error-catalog.yaml`. `PURCHASE_ALREADY_LINKED` (409, never) and `PURCHASE_NOT_VERIFIED` (422, never) are declared by `google-play.yaml` v1.15 and enter the catalogue with spec 024's first implementation task — handled by FR-016, FR-052; contract tests are written against the v1.15 fragment and rechecked when spec 024 is committed.
- **API conventions**: two new sections — "A versioned `/me` resource that is not syncable" (`/me/notification-preferences`, spec 023) and "The device id on `/me/devices/{id}`" — follow-up: not in scope of this spec (C16 push).
- **Shared responses**: Conflict lists `EXPORT_IN_PROGRESS`; `Problem.current` also on it — follow-up: not in scope of this spec (C15 export, spec 016).
- **Client mismatches**: none (every client code exists in the catalogue with the same retry class).
- **New to the client**: `GET /billing/google/account-reference`, `POST /billing/google/purchases`, `POST /billing/google/restore` (FR-010–FR-026, FR-042); `202` as a success status on a billing post (FR-015); `MySubscription.provider` read for the first time to decide Google Play links (FR-030).

### 6. Authorization and entitlements

Any signed-in person may send and restore their own purchases; there is no email-verification gate
and no plan gate (backend FR-004). The resulting capabilities come from the answers' `entitlements`
and are read through C3's gate only (P-12.1); the plan label is display-only. US6's Subscribe is
offered to anyone without a governing paid subscription.

### 7. Events

Not applicable: the client publishes no domain events. It consumes the app-wide session-ended and
update-required events, the app-wide foreground signal (`App` → `BillingSyncCoordinator.onForeground`),
and triggers C3's refresh after a billing answer.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Purchase reaches account | Unit (key derivation, pipeline with fake port and mock HTTP) + contract (MockEngine from `google-play.yaml` examples) + live replay once spec 024 is built | pipeline and contract tests; `.maestro/018-google-play/US1-*` (deferred) |
| US2 Restore | Unit (restore service, result message assembly) + store + contract | tests; `US2-*` (deferred) |
| US3 One account | Unit (refusal mapping, notes) + contract per code | tests; `US3-*` (deferred) |
| US4 Healthy subscription | Reducer + store (notes, links, effects) + screenshot | tests |
| US5 Prices | Unit (micro-units conversion, mapping) + screenshot | tests |
| US6 Subscribe (gated) | Unit (flow state machine, eligibility, launch params) with the fake port; switch-off test | tests; device check with a license tester (owner) |
| Platform adapter (gated on dependency) | Robolectric/unit over a wrapped Play Billing client; device check with a license tester | owner |

Owner policy: no emulator, Maestro or device run; flows are written and marked "deferred: E2E on
owner's device". Host tests plus a live-backend contract QA once backend spec 024 is built and
committed (curl against the local backend with the `fake` gateway scripted per token, real answers
captured as fixtures and replayed through the real client). Real purchases are never automated; the
brief's checkpoint (buy monthly with a license tester, reinstall, restore) is the owner's.

### 9. Open questions

1. **P-12.3 amendment (owner; blocks US6 only)**: allow a Subscribe action that only opens Google Play's purchase sheet. Recommended wording (client v1.4.0, MINOR; mirror backend P-10.2 the same way): "3. The in-app billing surface MUST expose only Google Play's own purchase sheet, 'restore purchases' and 'enter code'; links to Google Play's subscription centre are allowed." Until then US6 stays switched off. The same answer should **confirm the reading used now** that "Manage in Google Play" and "Fix payment" (links to Google Play's own subscription centre, no payment started in the app) are not billing actions under rule 3; if the owner disagrees, they are removed (two buttons).
2. **Play Billing dependency (owner; blocks the real Google Play port)**: approve `com.android.billingclient:billing-ktx` (current major, chosen with the `play-billing-library-version-upgrade` skill at wiring time) for `:shared` `androidMain` (P-14.5). Until then the shipped port reports "unavailable".
3. **Play Console and Google Cloud setup (owner)**: subscription products `plus_monthly` and `plus_yearly` with one auto-renewing base plan each and prices; license testers; Google Play app signing; the app published to an internal testing track (Play Billing works only for an app uploaded to Play with the same application id and signing); backend spec 024 §9 Q1 (service account, RTDN topic and push subscription).
4. **Backend contract gaps (non-blocking)**: see Assumptions "Backend gaps".
5. **Arabic copy** for the new strings: assumed defaults for owner review. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Google Play purchase that the server confirms shows as the person's plan within 5 seconds of the next app start, foreground or Plan screen visit, with 0 taps.
- **SC-002**: After a reinstall, a person with a Google Play subscription sees their plan again after sign-in with 0 taps, or with 1 tap on "Restore purchases".
- **SC-003**: 0 acknowledgements or local grants by the app across the feature's test suite.
- **SC-004**: A retry after a timeout or a killed process never produces a second verify with a different key for the same purchase in the same state (suite: 100% of retries reuse the key).
- **SC-005**: Every refusal and restore outcome the feature handles shows its own message in both languages; 0 show the server's text.
- **SC-006**: 0 purchase tokens, keys, account references or prices in logs, stored data or crash reports across the suite.
- **SC-007**: 0 Subscribe, buy, checkout or non-Google-Play price elements in the shipped app while the US6 switch is off.
- **SC-008**: The Plan screen passes review in Arabic, English and at 200% font with no clipped text.

## Assumptions

### Design defaults

1. P-12.3 read as written: two billing actions plus Google Play subscription-centre links, which start no payment and are what Google Play's policy expects for managing subscriptions; US6 is built behind a switch that ships off (Clarifications).
2. No Room change and no stored purchase state: Google Play's purchase list, re-read on every trigger, is the record; keys are derived, not stored (FR-012). After a process death the notes are rebuilt from Google Play and the next answers.
3. Background verifies are silent; results surface on the Plan screen and through the plan (FR-017). The only message a background pass can cause is the "another account" note.
4. The sign-in restore is silent unless something belongs to another account, and is skipped when Google Play reports no purchase (saves the 10-an-hour budget).
5. Prices are Google Play's, formatted by the app from integer minor units in Google Play's currency; the catalogue's reference amount is never shown, so before the Play Billing dependency is approved the Plan screen shows no prices (spec 005 showed the catalogue's Google Play amount; none is seeded today).
6. "Manage in Google Play" and "Fix payment" use the plain Google Play subscription-centre web link (opens the Play Store app when installed); no library needed.
7. Account hold and pause cannot be read from `/me/subscription` (the server reports them as expired); the app shows the hold or paused note only when a verify answer in the same run says so (backend gap 1).
8. "Test purchase" shows only after a verify answer in the same run said `test: true` (backend gap 2).
9. Lifetime (one-time) products are listed and sent like subscriptions (the server maps them if configured); none is sold at launch (backend spec 024 Q3), and US6 offers only subscriptions.
10. Upgrades/downgrades and offers are left to Google Play's subscription centre (FR-048, FR-049).
11. Retry backoff within a run: `Retry-After` when given, otherwise 30 s doubling to 15 minutes, at most 6 automatic sends per purchase per run; after that, the next trigger.
12. The development flavour's scripted Google Play (FR-056) is the dev and E2E story: the backend's `fake` gateway is scripted with the same tokens (`BILLING_PLAY_FAKE_SCRIPT`), so verify and restore run end to end locally; Maestro never buys.

### Backend gaps (non-blocking)

1. **Hold/paused not readable**: `MySubscription.status` is `active|grace|null`; a Google Play subscription on hold or paused reads as no subscription, so the app cannot keep showing "Fix payment" for hold after the run that saw it. Proposed: expose the latest Play state of the caller's linked purchases (e.g. `provider_state` on `MySubscription`, or `GET /billing/google/purchases`).
2. **Test flag not readable**: `MySubscription` has no `test`; the label lasts only for the run.
3. **Product of the governing Play subscription**: `MySubscription` names the plan, not the Play product id, so "Manage in Google Play" maps plan → product through the catalogue (`prices[].product_id`); exact when one product per plan (true at launch).
4. **ADR-0026 not yet written**: the reading "verify = single-item restore" lives in spec 024's plan only; the client's P-12 reconciliation cites it.
5. **`PURCHASE_ALREADY_LINKED`/`PURCHASE_NOT_VERIFIED` not yet in `error-catalog.yaml`**: added by spec 024's first implementation task; the client's contract-check will flag a mismatch until then.
6. **No subscription id on `MySubscription`** (spec 015 gap): unchanged.
