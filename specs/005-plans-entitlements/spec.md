# Feature Specification: Plans & Entitlements (client)

**Feature Branch**: `005-plans-entitlements`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "specs/briefs/C03-plans-entitlements.md" — client brief C3, paired with backend brief B3 (spec 009, built and committed in `56b44fb`).

**Backend contract**: `saveme-backend@055afa1`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/billing.yaml` (`/plans`, `/me/entitlements`, `/me/subscription`; schemas `PlanRef`, `Features`, `Price`, `Plan`, `EntitlementSummary`, `MySubscription`), `docs/openapi/paths/identity.yaml` (`SignInResult.entitlements`), `docs/openapi/error-catalog.yaml` (`FEATURE_NOT_ENTITLED`), `docs/openapi/paths/wallets.yaml` (first use of `wallets.max` and `FEATURE_NOT_ENTITLED`).
- Backend behaviour: `saveme-backend/specs/009-plans-entitlements/spec.md` (launch catalogue, free plan = no subscription, governing subscription, lapse), `docs/glossary.md` (Plan, Plan Feature, Subscription, Entitlement).
- Existing client code: the sign-in answer's entitlements are already cached with the account (spec 001); this spec makes them readable, refreshable and correct for "unlimited".

## Clarifications

### Session 2026-10-02

Answered by the delegated session (owner away, full delegated authority); each is the most conservative option and is recorded as an assumed default.

- Q: Which price does the Plan screen show, given the catalogue carries prices per channel and today only the InstaPay channel has prices? → A: **Only the Google Play channel's list price**, formatted from minor units and currency. A plan with no Google Play price shows no price. An InstaPay price is never shown in the app (P-12.3/P-12.4, Google Play payments policy: the app must not point to a payment path outside Play). C10 replaces it with Play's own localised price.
- Q: Should the Plan screen show "Restore purchases" and "Enter code" before C10 and C11 build them? → A: **No.** No billing action appears in this feature; the screen reserves the place where C10 and C11 add them, and exposes the refresh they call after a billing action (FR-010). A button that does nothing is worse than none.
- Q: How does the client treat a capability the answer does not mention, or a value of the wrong type? → A: **As off, or a quota of 0** — the backend's own rule for an omitted capability (spec 009 edge cases). Only an explicit `null` quota means unlimited.
- Q: Does the Plan screen show the subscription's channel (Google Play, InstaPay, manual)? → A: **No.** It shows plan, status, dates and renewal only; naming InstaPay in the app is avoided (P-12.4) and the channel adds nothing the person can act on.
- Q: Does this feature explain a downgrade's effect on wallet members (members beyond the quota become viewers)? → A: **No, C9 owns it.** The backend defers that rule to B9 (ADR-0007 rule 10); this spec only guarantees that losing paid access never hides or blocks reading the person's own data on the client (FR-016).
- Q: How often may the app re-read entitlements, given every foreground return triggers a refresh? → A: **At most one refresh in flight; a trigger during a refresh joins it, and a foreground refresh is skipped when the last successful one is under 60 seconds old.** Plan-screen, billing-action and "not entitled" refreshes are never skipped (they follow an explicit act or a server refusal). Keeps the answer fresh (SC-002) without a request burst on app switching.
- Q: How does a plan describe a quota that only matters when its flag is on (members per wallet while sharing is off)? → A: **Hidden while its flag is off.** The free plan reads "Sharing: not included", not "Sharing: not included · 1 member per wallet"; a quota without a governing flag (wallets) always shows ("Unlimited wallets" / "Up to N wallets").

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The app knows what the person may do (Priority: P1)

As soon as a person is signed in, the app knows which capabilities their plan allows and keeps
that answer current: after sign-in, on returning to the foreground, and after any billing action.
Every later feature asks one place — "may this person do X?" and "how many X may they have?" —
and gets the same answer, offline included.

**Why this priority**: Every plan-gated feature (wallets count, sharing, export, advanced reports)
depends on it, and a wrong answer either blocks a paying person or offers a free person something
the server will refuse. Today "unlimited" is misread as zero.

**Independent Test**: Sign in as a Free person and read the answer (sharing off, wallets unlimited);
grant Plus on the backend, bring the app back to the foreground, and read sharing on with 5 members.

**Acceptance Scenarios**:

1. **Given** a newly signed-in person on the free plan, **When** a feature asks the app's answer, **Then** sharing is off, the members-per-wallet quota is the free plan's, and every other capability is on with unlimited wallets.
2. **Given** a capability quota the server reports as unlimited, **When** a feature asks for its quota, **Then** the app answers "unlimited", never zero.
3. **Given** the person's plan changed on the server (an operator grant, a lapse), **When** the person opens or refreshes the Plan screen, or the app returns to the foreground more than a minute after its last refresh, **Then** the app's answer matches the server's without signing in again.
4. **Given** the app has no connection, **When** a feature asks the answer, **Then** it gets the last answer the app received, and the app relaunches with that answer without a network call.
5. **Given** a capability the server's answer does not mention, **When** a feature asks about it, **Then** the app answers "off" (or a quota of 0).
6. **Given** any request the server refuses because the plan lacks the capability, **When** the refusal arrives, **Then** the person sees a message saying their plan does not include this, never a generic failure, and the app re-reads its answer.

---

### User Story 2 - See my plan (Priority: P1)

The person opens "Plan" from the account screen and sees which plan they are on and what it
allows; when they hold a paid subscription, also whether it is active or in its grace period, when
the current billing period ends, and whether it renews.

**Why this priority**: It answers "why can't I share?" and "when does my plan end?", and is where
C10 and C11 put "Restore purchases" and "Enter code".

**Independent Test**: As a Free person, open Plan and see "Free" with its limits and no dates;
after an operator grants Plus for 30 days, refresh and see Plus, active, with its end date.

**Acceptance Scenarios**:

1. **Given** a person on the free plan, **When** they open Plan, **Then** they see the free plan's name, its capabilities in plain words (for example "Sharing: not included"), and no billing dates.
2. **Given** a person with an active paid subscription, **When** they open Plan, **Then** they see its plan name, "Active", the date the current billing period ends, and whether it renews automatically.
3. **Given** a subscription in its grace period, **When** they open Plan, **Then** they see "Payment due" (grace) and the date the grace ends, and their capabilities still show as the paid plan's.
4. **Given** a subscription with no end (granted for life), **When** they open Plan, **Then** they see "No end date" instead of a date.
5. **Given** an operator granted Plus while the app was open, **When** the person refreshes the Plan screen, **Then** it shows Plus without signing in again.
6. **Given** no connection, **When** they open Plan, **Then** it shows the last known plan and subscription with a note that it may be out of date, and a way to try again.

---

### User Story 3 - Browse the plans (Priority: P2)

On the Plan screen the person sees every plan on offer, in catalogue order, each with its name in
the app's language, its billing period, what it includes, and its price when one is available,
with their current plan marked.

**Why this priority**: It tells a Free person what Plus adds; nothing is sold here yet (C10 sells).

**Independent Test**: Open Plan in English and in Arabic; see Free, Plus · Monthly and Plus · Yearly
in order with localised names, the current plan marked, and no InstaPay price.

**Acceptance Scenarios**:

1. **Given** the catalogue, **When** the Plan screen loads, **Then** every active plan is listed in catalogue order with its localised name, billing period (monthly, yearly, lifetime, or every N days) and capabilities in plain words.
2. **Given** the catalogue plan whose identifier equals the person's current plan reference, **When** listed, **Then** that plan is marked "Current plan" in text, not by colour alone.
3. **Given** a plan with a Google Play list price, **When** listed, **Then** its price shows in the app's language with Western digits and the currency's own fraction digits; a plan with no Google Play price shows no price.
4. **Given** the app language changes, **When** the Plan screen is next shown, **Then** plan names follow the new language.
5. **Given** the person is on a retired plan that no longer appears in the catalogue, **When** they open Plan, **Then** their current plan still shows (from their entitlements) and no catalogue row is marked current.
6. **Given** the catalogue cannot be loaded and none was loaded before, **When** the screen opens, **Then** the current-plan section still shows and the catalogue section shows a retry, not an empty list.

### Edge Cases

- **Shared wallets run on the owner's plan** (backend glossary). The app's answer is the signed-in person's own plan; it MUST NOT be used to block actions in a wallet someone else owns. Wallet-scoped gating is C4/C9's, with the server's refusal as the authority.
- **Stale answer**: the app's answer is a hint for showing or hiding controls; the server decides. A refusal for a capability the app thought allowed shows the "not included" message and refreshes the answer (US1 scenario 6).
- **Two refreshes at once** (sign-in and foreground together): one answer wins; the later server answer is kept, never an older one.
- **Sign-out during a refresh**: a refresh answer arriving after sign-out is discarded; nothing of the previous person's plan survives sign-out.
- **Downgrade**: losing paid access never hides, deletes or blocks reading the person's own data in the app (backend P-09 rule 6). The effect on members of shared wallets is C9's to explain.
- **Billing period dates** show in the person's profile timezone (device timezone until the profile is known); a period ending today shows today's date, not "expired".
- **A plan with an unknown billing period type or an unknown capability key**: the plan still shows; the unknown capability is not listed; an unknown period type shows no period line.
- **Subscription and entitlements disagree** (one read finished before a plan change, the other after): the plan name follows the entitlements; the subscription's status and dates are hidden until both name the same plan (FR-012).
- **Large font / RTL**: the plan list and current-plan section stay readable at 200% font and mirror in Arabic.

## Requirements *(mandatory)*

### Functional Requirements

**Entitlements answer (the gate)**

- **FR-001**: The app MUST hold one current entitlements answer per signed-in person: the plan's reference (identifier and display name) and every capability value from the server.
- **FR-002**: The app MUST offer every feature a single way to ask whether a capability is on and what a quota is; no feature reads the plan's name or identifier to decide a capability (P-12.1). While nobody is signed in, every capability answers "off" and every quota 0.
- **FR-003**: A quota the server reports as `null` MUST be "unlimited"; an integer MUST be that limit; a missing capability or a value of the wrong type MUST be "off" for a flag and a limit of 0 for a quota. This fixes the existing cache, which reads `null` as 0.
- **FR-004**: The capabilities the app knows are exactly the backend's fixed list: sharing enabled (flag), members per wallet (quota), export enabled (flag), advanced reports (flag), wallets (quota). An unknown capability key in an answer MUST be kept but ignored by the Plan screen.
- **FR-005**: The answer MUST survive app restarts and be available offline; on relaunch the stored answer MUST be available immediately, before any network call completes (a background refresh may follow).
- **FR-006**: The app MUST refresh the answer: on sign-in (from the sign-in response, then a read of the subscription), each time the app returns to the foreground while signed in, when the Plan screen opens or is refreshed, and when a feature reports a billing action finished (the refresh C10 and C11 call). At most one refresh runs at a time (a trigger during one joins it); a foreground refresh is skipped when the last successful refresh is under 60 seconds old; Plan-screen, billing-action and "not entitled" refreshes (FR-007) are never skipped.
- **FR-007**: When any request is refused with `FEATURE_NOT_ENTITLED`, the app MUST show a localised "your plan doesn't include this" message (never the server's text) and refresh the answer.
- **FR-008**: A refresh answer arriving after sign-out, or for a different person than the one signed in, MUST be discarded. Sign-out MUST clear the stored answer, the stored subscription and nothing else of other people.
- **FR-009**: Refresh failures (offline, server error) MUST keep the last answer and MUST NOT sign the person out, except an unauthenticated refusal, which follows the existing "session ended" handling.

**Plan screen**

- **FR-010**: The account screen MUST offer "Plan", opening the Plan screen. The Plan screen MUST NOT contain any purchase or payment action, and MUST NOT link to a web checkout or to InstaPay (P-12.2–P-12.4). It reserves one place for the two billing actions C10 and C11 add.
- **FR-011**: The Plan screen MUST show the current plan's name and its capabilities in plain words, from the entitlements answer (so it shows offline).
- **FR-012**: The status and dates come from the subscription and are shown only when its plan identifier equals the entitlements' plan identifier (otherwise they are hidden until the next refresh agrees). For a paid subscription the Plan screen MUST show the status ("Active" or "Payment due" for grace), the current billing period's end date (or "No end date"), the grace end date when in grace, and whether it renews automatically. On the free plan it shows no status or dates. The subscription's channel is not shown.
- **FR-013**: The Plan screen MUST list every catalogue plan in catalogue order with its localised name, billing period (none for the default plan, which the catalogue labels lifetime) and capabilities (the members-per-wallet quota only when sharing is on; the wallets quota always, as "Unlimited wallets" or "Up to N wallets"), marking the plan whose identifier equals the current plan reference as "Current plan" in text.
- **FR-014**: A plan's price MUST be its Google Play list price only, formatted from integer minor units and the currency's fraction digits in the app's language with Western digits (P-03, P-06). A plan with no Google Play price shows no price. No other channel's price is ever shown.
- **FR-015**: The Plan screen MUST offer a refresh action, show the last known data with a "may be out of date" note when a refresh fails offline, show the generic error with the server's request id when a refresh fails unexpectedly (P-13.2), show a loading placeholder for the status and dates until the subscription is first read, and show a retry when the catalogue has never loaded.
- **FR-016**: Losing paid access MUST NOT make the app hide, delete or block reading any of the person's data; only plan-gated actions change.
- **FR-017**: The catalogue MUST be requested in the app's current language and re-requested when the language changes; the last loaded catalogue MAY be shown while a new one loads.

**Common**

- **FR-018**: The client's error-code list MUST include `FEATURE_NOT_ENTITLED` (retry class never) with Arabic and English copy.
- **FR-019**: Every screen and component this feature adds MUST be built from design-system components only, in Arabic RTL and English LTR, readable at 200% font scale, and every interactive element MUST carry a stable test tag for E2E flows. Each plan card MUST be announced by screen readers as one item (name, billing period, price if any, "Current plan" if so).
- **FR-020**: No read in this feature MAY be retried automatically except as the error catalogue's retry class allows (P-02.6).
- **FR-021**: Subscription details (status, dates) and prices MUST NOT be written to logs, crash reports or analytics (P-09.2, P-13.3).

### Key Entities *(include if feature involves data)*

- **Entitlements answer**: plan reference (identifier, display name) and capability values (flag on/off, quota limited to N or unlimited). Cached per signed-in person; refreshed, never edited on the device. The server's answer is derived and never stored there; the client copy is a cache of the last answer.
- **Subscription view**: plan reference, status (active, grace, or none for free), billing period start and end (none for lifetime or free), grace end, renews flag. Channel is kept but not shown.
- **Plan (catalogue entry)**: identifier, localised name, billing period type and days, default flag, capability values, prices per channel (integer minor units + currency, store product identifier).

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Gives every later feature one trustworthy answer to "may this person do this?", kept current
without re-signing in, and shows the person their plan, what it allows, and the plans on offer,
without selling anything inside the app.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Plan**, **Plan Feature** (shown as "what's included"),
**Subscription**, **Entitlement**, **billing period** (never just "period"). The UI says "plan",
never "subscription tier" or "package". Grace shows as "Payment due". No new glossary terms.

### 3. Data model

No new syncable entity; nothing here is syncable (backend glossary: Plan and Subscription are read
through endpoints), so P-04 rules 1–5 do not apply (rule 6: account data).

| Data | Where it lives on the device | Cleared |
|---|---|---|
| Entitlements answer | The existing account snapshot (spec 001), now with the plan identifier and correct "unlimited" | At sign-out (existing) |
| Subscription view | A small key-value record owned by this feature | At sign-out (FR-008) |
| Plan catalogue | A key-value record with the language it was loaded in | At sign-out (simplest; it is public data) |

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Capability decisions read features, never the plan name or id | P-12.1, FR-002 | gate unit test; architecture test that no feature outside billing reads the plan reference for gating |
| `null` quota = unlimited; missing / wrong-typed = off or 0 | FR-003, backend spec 009 | feature-value mapping unit test |
| No purchase, checkout or InstaPay action or price in the app | P-12.2–P-12.4, FR-010, FR-014 | Plan screen ViewModel test (only Google Play prices mapped) + screenshot review |
| A refresh answer after sign-out is discarded; sign-out clears subscription and catalogue | P-09, FR-008 | refresher test with sign-out mid-flight; cleaner test |
| Losing paid access changes only plan-gated actions | backend P-09 rule 6, FR-016 | gate test (downgrade keeps every non-gated answer) |
| `FEATURE_NOT_ENTITLED` maps to localised copy and triggers a refresh | P-02.4, P-06.5, FR-007 | error-copy mapping test; refresher trigger test |

### 5. API

All under `/api/v1`, in `saveme-backend/docs/openapi/paths/billing.yaml` (tag Plans & Billing).

| Operation | Method & path | Session | Codes the app handles |
|---|---|---|---|
| List plans | GET `/plans` | none | 200 `Plan[]` (one page, `next_cursor` null) |
| My entitlements | GET `/me/entitlements` | required | 200 `EntitlementSummary`; UNAUTHENTICATED |
| My subscription | GET `/me/subscription` | required | 200 `MySubscription` (all members null on free); UNAUTHENTICATED |
| Sign-in (existing) | POST `/auth/sign-in` | none | `entitlements: EntitlementSummary` (unchanged) |
| Any gated write (later features) | — | required | FEATURE_NOT_ENTITLED (403) → FR-007 |

Every operation may also return the shared refusals (400, 406, 426, 500), handled generically as in
spec 001.

`Features` is an open map: values are boolean flags, or integer quotas with `null` = unlimited.
Keys at this commit: `sharing.enabled` (bool), `sharing.max_members_per_wallet` (int|null),
`export.enabled` (bool), `reports.advanced` (bool), `wallets.max` (int|null). The nullable keys are
inferred from "quotas are integers, or null for unlimited"; see open question 1.

#### Contract changes since last spec

- **Error catalogue**: `CATEGORY_IN_USE` (409) added for categories (spec 011). Follow-up: not in scope of this spec (C5).
- **Shared responses**: the Conflict response lists `CATEGORY_IN_USE`. Follow-up: not in scope of this spec (C5).
- **Client mismatch (not shown by the script)**: `FEATURE_NOT_ENTITLED` is in the catalogue since v1.0 and now returned by wallet create (`wallets.max`), but the client's code list lacks it. Handled by FR-007 and FR-018.
- **Client mismatch**: the cached entitlements read a `null` quota as 0 (would block wallet creation once C4 gates on `wallets.max`). Handled by FR-003.
- **Recheck at implementation (2026-10-02, `saveme-backend@2487f10`)**: the error catalogue gained `CURRENCY_MISMATCH` (422, field-level, after-change), listed in the shared validation response. Follow-up: not in scope of this spec (wallets and transactions, C4/C6); the client's `ApiErrorCode` still matches the catalogue. billing.yaml `Features` is now typed per key (quotas integer or null, three booleans), as this spec assumed; no client change.

### 6. Authorization and entitlements

`/plans` is public; `/me/entitlements` and `/me/subscription` need the session and answer for the
caller only. This feature is the client's entitlement gate (P-12.1): it gates nothing itself and
exposes flags and quotas to later features. Wallet-scoped decisions follow the wallet owner's
plan on the server and are not decided by this gate (Edge Cases).

### 7. Events

Not applicable: the client publishes no domain events. Internally, a changed entitlements answer is
observable by every feature, and C10/C11 trigger a refresh after a billing action.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Gate | Unit (value mapping, gate, refresh triggers, sign-out race) + contract (`/me/entitlements`, `/me/subscription`) + E2E | gate and refresher tests, `.maestro/005-plans-entitlements/US1-*` |
| US2 My plan | Reducer + ViewModel + screenshot + E2E | Plan screen tests, `.maestro/005-…/US2-*` |
| US3 Catalogue | Contract (`/plans`) + ViewModel (price channel filter, current mark) + E2E | catalogue tests, `.maestro/005-…/US3-*` |
| New screen/component | Screenshot tests (LTR, RTL, 200% scale) | Plan screen and component screenshot tests |

### 9. Open questions

1. **Per-key `Features` schema** (backend contract gap): `Features` is `additionalProperties` with keys listed only in prose; which keys are nullable is inferred. Proposed: the backend declares each key with its type and nullability (and adds response examples to `billing.yaml` for contract-test seeding, P-14.3). Decided by: backend owner. Non-blocking: FR-003/FR-004 handle the inferred shape safely.
2. **No Google Play prices in the catalogue**: the launch seed has InstaPay prices only, so the Plan screen shows no price until B10 adds Google Play prices/product ids. Decided by: backend owner (B10). Non-blocking.
3. **Operator grant for E2E**: there is no endpoint; the E2E runner grants Plus through the backend's operator application service, as it already seeds accounts (planning decides the exact call). Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Free person sees their plan and its limits within 2 taps of the home screen, offline included once they have signed in once.
- **SC-002**: After an operator grant, the app shows the new plan on the next Plan-screen open or refresh (or the first foreground return a minute or more after the last refresh), with 0 sign-ins, in 100% of trials.
- **SC-003**: 0 capability decisions in the app read the plan name or identifier (checked by test).
- **SC-004**: 0 purchase, checkout or non-Google-Play price elements appear anywhere in the app.
- **SC-005**: Every "unlimited" quota reads as unlimited in 100% of answers; 0 features see 0 for an unlimited quota.
- **SC-006**: The Plan screen passes review in Arabic, English and at 200% font scale with no clipped or overlapping text.
- **SC-007**: The 11 acceptance scenarios a device run can reach (US1 1 and 3; US2 1–5; US3 1–4) are covered by passing E2E flows, including the brief's checkpoint (Free shows Free; operator-granted Plus shows Plus after refresh without re-sign-in). The other 7 (US1 2, 4, 5, 6; US2 6; US3 5, 6) need offline toggling, a retired plan, an omitted capability or a gated write no client feature issues yet, and are proven by unit and contract tests.

## Assumptions

- Android is the only live-API target, as in specs 001–003.
- C1 (spec 003) is merged: sign-in, registration and verification work.
- Prices shown are list prices from the catalogue; Play's localised price replaces them in C10.
- Restore purchases (C10) and Enter code (C11) are not built here; this feature exposes the refresh they call.
- The members-beyond-quota downgrade explanation is C9's (backend defers it to B9).
- The sync engine (C14, in flight on another branch) is not needed: nothing here is syncable.
- Dates display in the profile timezone (P-05), as the device sessions screen does.
- Plan names come from the server in the requested language; capability descriptions are app copy.
