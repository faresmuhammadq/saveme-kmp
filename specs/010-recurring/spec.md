# Feature Specification: Recurring Transactions (client)

**Feature Branch**: `010-recurring`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "specs/briefs/C07-recurring.md" — client brief C7, wave 2. Pairs with backend B7 → spec 014 (built, committed `c0e9844`; `recurring.yaml` v1.7, unchanged since).

**Backend contract**: `saveme-backend@2a3b414`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/recurring.yaml` (v1.7, frozen for C7: rule list/read/PUT/DELETE, `POST …/materialise`, occurrence list/read/PUT), `paths/sync.yaml` (`recurring_rule` and `occurrence` rows on the wallet feed; batch item types `recurring_rule` upsert/delete and `occurrence` upsert only), `paths/transactions.yaml` (read-only `recurring_rule_id`, `occurrence_id`; a transaction written by hand is never linked), `docs/api-conventions.md`, `docs/openapi/error-catalog.yaml` (no new code in spec 014).
- Backend behaviour: `saveme-backend/specs/014-recurring-transactions/summary.md` and `docs/adr/0018-occurrences-materialised-by-the-server.md` (only the server creates occurrences; occurrence key; no postings on an occurrence; confirm = one versioned write carrying a client transaction id; due/missed derived; "today" in the rule's timezone; no back-posting by automatic confirmation), `docs/glossary.md` (Recurring Rule, Occurrence, gam'eya جمعية and installment قسط as presets; banned synonyms "schedule, repeat, recurrence" for Recurring Rule and "instance, run" for Occurrence).
- Wallet permission matrix rows R-01 (read, any member), R-02 (write, owner or editor), T-02 in a transfer rule's destination: `saveme-backend/specs/010-wallets-membership/spec.md` §6.
- Builds on spec 004 (sync engine: queue, clashes, refusals, status), 006 (C4 wallets: roles, `WalletDeletionParticipant`, balances), 007 (C5 categories: choices, displays, `PendingCategoryUses`), 008 (C6 transactions: editor, list, balance projection and carry, destination choices, "From a recurring rule" caption) and 009 (C14 wave 2: wallet feeds, `FeedApplier`, batch items, held dependents; `recurring_rule` and `occurrence` rows are skipped until this feature, which clears the wallet-scope positions once so they are read from the start).

## Clarifications

### Session 2026-10-02

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: When a person sets up a recurring rule from the transaction entry screen, is the transaction they were typing also recorded? → A: No. "Make recurring" turns the draft into a new rule with the draft's values (wallet, kind, amount, category or destination, texts, date as the start); nothing else is saved. The rule's first date is the draft's date, and that payment is recorded by confirming its occurrence (or automatically if the person turns automatic confirmation on). One action never produces two records for one payment. (FR-002, FR-003)
- Q: Is automatic confirmation on or off for a new rule? → A: Off by default for every preset; the person turns it on per rule. Money only enters the books when the person confirms, unless they chose otherwise. (FR-011)
- Q: Where do due occurrences appear, so the person confirms them instead of recording the payment by hand? → A: In three places: a "Due" section at the top of the wallet's transactions screen, a "Due" card on Home whenever anything is due in any wallet, and the recurring screen (per wallet, or all wallets from Home). In addition, when the person records a transaction by hand that resembles a received due or near occurrence (same wallet and kind, same category or destination, within 7 days), the editor offers "Confirm <rule> instead". (FR-018, FR-019, FR-031)
- Q: What happens when the person confirms an occurrence on the phone, but the server or another member already confirmed it? → A: The phone accepts the confirmed occurrence, drops its own pending transaction for it (never re-sent under a new id), and shows a short note on the occurrence ("Already confirmed"). There is no choice to make because a confirmed occurrence can never change. (FR-024)
- Q: When does the phone ask the server to catch a rule up ("materialise")? → A: Only when the person asks, with "Check for new dates" on the rule (online only, owners and editors). The server already materialises on every rule write and hourly; the phone never generates occurrences itself and never calls it in the background. (FR-028)

## User Scenarios & Testing *(mandatory)*

People in Egypt pay rent, school fees, a gam'eya (جمعية, a rotating savings group) and
installments (قسط) every month, and receive salaries. Recording each by hand is tedious and easy
to forget, and forgetting breaks the balance. A recurring rule is set up once; the server lists
each dated occurrence ahead of time; the person confirms each one (or lets it confirm itself),
skips one, adjusts one, or changes the rule. An occurrence never counts in a balance; only the
transaction it produces does.

### User Story 1 - Set up a recurring rule and see what is coming (Priority: P1)

A person sets up "Rent, 4,500 EGP, on the 1st of every month" in a wallet, from the transaction
entry screen or the wallet's recurring screen, with or without a connection. They immediately see
the next dates it will fall on; once it has synced, the upcoming occurrences are listed.

**Why this priority**: It is the brief's integration checkpoint ("monthly rule created, next occurrence visible as upcoming") and the entry point to everything else.

**Independent Test**: Offline, create a monthly rule starting next month on day 31; its preview shows the 31st, then the last day of the shorter months. Go online: the rule syncs and its upcoming occurrences appear with the same dates.

**Acceptance Scenarios**:

1. **Given** an owner or editor of an active wallet, **When** they create a rule (kind, amount, category or destination, how often, every how many, the day, start, and optionally an end date or a number of times), **Then** the rule is listed at once in the wallet's recurring screen with "Waiting to sync" until the server accepts it.
2. **Given** a rule being created or edited, **When** the person changes how often it repeats or its dates, **Then** the next five dates are previewed on the screen using the same rule the server uses (a day 29–31 falls on a shorter month's last day, counted from the start every time, never drifting).
3. **Given** a rule accepted by the server, **When** the phone next reads the wallet, **Then** its upcoming occurrences are listed (through about five weeks ahead, and always at least the next one), visibly different from transactions and never counted in any balance or day total.
4. **Given** a rule created on this phone that has not synced yet, **When** the person opens it, **Then** its dates are shown as a preview marked "Dates appear after sync", with no confirm or skip offered.
5. **Given** the transaction entry screen with a draft, **When** the person chooses "Make recurring", **Then** the rule editor opens with the draft's wallet, kind, amount, category or destination, texts and its date as the start, and saving creates only the rule.
6. **Given** a viewer, an archived wallet, or an unverified address, **When** the person looks for "Add recurring", **Then** it is not offered (viewer, archived) or leads to the verification prompt (unverified), as for transactions.
7. **Given** a rule created on another device or by another member, **When** the phone reads the wallet, **Then** the rule and its occurrences appear.

---

### User Story 2 - Confirm, skip or adjust what is due (Priority: P1)

On the 1st, the rent is due. The person sees it at the top of the wallet's transactions and on
Home, confirms it (changing the amount or date if what they actually paid differs), and it becomes
a transaction in the wallet with the balance moving at once, even offline. Or they skip this
month, or undo a skip.

**Why this priority**: Confirming is how a rule's money enters the books; a missed confirm or a double recording is a wrong balance.

**Independent Test**: With a received occurrence due today, go offline, confirm it with a different amount: a transaction appears in the list marked "Waiting to sync" and the balance moves. Go online: the server records exactly one transaction with that amount, linked to the rule.

**Acceptance Scenarios**:

1. **Given** a received occurrence dated on or before the rule's today and still unconfirmed, **When** the person looks at the wallet's transactions screen, Home or the recurring screen, **Then** it is shown as "Due" (or "Missed" when a later round of the same rule is also due), with its amount and date.
2. **Given** a due or upcoming occurrence, **When** the person confirms it (optionally changing date, amount, category or texts), **Then** a transaction with those values appears in the wallet's list at once, the balance includes it, the occurrence shows as confirmed, and both are marked "Waiting to sync" until the server accepts.
3. **Given** the person confirmed offline, **When** the confirmation is accepted, **Then** exactly one transaction exists on the server, with the same id the phone showed, linked to the rule and the occurrence; the balance never jumps back or counts it twice.
4. **Given** an occurrence, **When** the person skips it, **Then** it leaves the upcoming and due lists at once and shows as skipped in the rule's history; **When** they undo the skip, **Then** it returns.
5. **Given** a confirmed occurrence, **When** the person opens it, **Then** it is read-only and leads to its transaction; the transaction is corrected or deleted through the transaction editor (deleting it does not un-confirm the occurrence).
6. **Given** a rule with automatic confirmation whose occurrence the server could not confirm (the author lost the right to write, the destination wallet is gone, or currencies no longer match), **When** the person views it, **Then** it is shown as due with the reason in plain words and can be confirmed by hand.
7. **Given** a rule with automatic confirmation, **When** the server confirms its occurrence, **Then** after the next read the occurrence shows as confirmed and its transaction is in the list with "From a recurring rule".

---

### User Story 3 - Change a rule or one occurrence (Priority: P1)

The rent goes up from January. The person changes the rule "from 1 January", and only January
onwards changes; earlier rounds keep their values. Or they change only one month's amount, pause
the rule, end it, or delete it.

**Why this priority**: It is the brief's third checkpoint ("series edit changes later ones only"), and rules that can't follow real life get abandoned.

**Independent Test**: With a rule whose November round is confirmed, change it "from 1 January" to 5,000: the rule ends on 31 December, a new rule starts 1 January at 5,000; December still shows 4,500.

**Acceptance Scenarios**:

1. **Given** a rule, **When** the person edits its template (amount, category, texts, automatic confirmation) for all upcoming rounds, **Then** after sync every round still unconfirmed, unskipped and not individually edited shows the new values; confirmed, skipped and individually edited rounds keep theirs.
2. **Given** a rule, **When** the person chooses "Change from a date" and picks a date D, **Then** the existing rule is set to end on the day before D and a new rule with the person's changes starts on D; rounds before D are untouched. D must be after the last confirmed or skipped round.
3. **Given** a rule with any round confirmed or skipped, **When** the person edits it, **Then** how often it repeats, the interval, the day, the start, the amount basis and the payout round can no longer be changed (shown disabled with a reason, and "Change from a date" offered instead); for a gam'eya or a "total" installment the amount and the number of rounds are also fixed.
4. **Given** one occurrence, **When** the person edits its date, amount, category or texts, **Then** only that occurrence changes and it is marked edited; later rule edits leave it alone.
5. **Given** a rule, **When** the person pauses it, **Then** it shows "Paused" and no automatic confirmation happens while paused; resuming never confirms earlier dates automatically. **When** they end it, **Then** later unconfirmed rounds disappear after sync, and the rule can no longer change (only be deleted).
6. **Given** a rule, **When** the person deletes it (after a confirmation naming that its transactions stay), **Then** it and its unconfirmed rounds disappear at once, and transactions it already produced stay in the wallet; deletion is final.
7. **Given** the person's change to a rule or an occurrence clashes with a change from another device or member, **When** the answer arrives, **Then** the person sees the same choices as for other records ("Keep mine" / "Use the other device's"), or for a rule deleted elsewhere "Accept the deletion".

---

### User Story 4 - Gam'eya and installments in one step (Priority: P2)

A person joins a 10-month gam'eya at 1,000 EGP a month and takes the payout in month 4; or buys
a fridge for 10,000 EGP over 12 monthly installments. Each preset asks only for what it needs and
shows the resulting amounts before saving.

**Why this priority**: These are the two recurring arrangements the brief names for the Egyptian market; without presets they need arithmetic and a separate income rule.

**Independent Test**: Create a gam'eya of 10 rounds at 1,000 with payout round 4: the preview shows ten 1,000 contributions and one 10,000 payout on round 4's date. Create a 10,000 installment over 12: the preview shows 833.34 for the first four and 833.33 for the other eight, summing exactly to 10,000.

**Acceptance Scenarios**:

1. **Given** the gam'eya preset, **When** the person enters the monthly contribution, the number of rounds (2–120), the start date, the payout round and an income category for the payout, **Then** the preview lists each contribution and one payout worth the sum of every round on the payout round's date.
2. **Given** the installment preset, **When** the person enters a per-installment amount or a total, the number of installments (2–120) and the start date (expense or transfer), **Then** the preview lists each installment; with a total, it is split evenly with the remainder one minor unit at a time to the earliest installments, summing exactly to the total.
3. **Given** a gam'eya's payout occurrence, **When** it is due, **Then** it is shown as income ("Payout") and confirmed like any other occurrence.
4. **Given** a preset rule, **When** the person edits it, **Then** the preset itself cannot change, and the fixed values of US3-S3 apply once a round is resolved.

---

### User Story 5 - Don't record the same payment twice (Priority: P2)

A person forgets the rent is set up and records it by hand. The app notices the resemblance and
offers to confirm the due rent instead, so the books hold one transaction, linked to the rule.

**Why this priority**: A hand-recorded transaction is never linked to an occurrence (backend ADR-0018 rule 6), so an automatic or later confirmation would record the payment again.

**Independent Test**: With "Rent 4,500" due today, open "Add transaction", choose Expense, the rent category and type 4,500: the editor offers "Confirm Rent (due 1 Nov) instead"; accepting confirms the occurrence with the typed values and records nothing else.

**Acceptance Scenarios**:

1. **Given** a received unconfirmed occurrence in the same wallet, of the same kind, with the same category (or the same destination for a transfer), dated within 7 days of the draft's date, **When** the person records a transaction by hand, **Then** the editor shows "Confirm <description> (<date>) instead" before saving.
2. **Given** that offer, **When** the person accepts it, **Then** the occurrence is confirmed with the typed amount, date and texts, and no separate transaction is recorded.
3. **Given** that offer, **When** the person ignores it and saves, **Then** the transaction is recorded by hand as today (unlinked), and the occurrence stays due.
4. **Given** a rule created on this phone that has not synced (no occurrence received yet), **When** the person records its payment by hand, **Then** no offer is shown (nothing can be confirmed yet), and the rule's screen says "Dates appear after sync".

---

### User Story 6 - Catch up and follow shared wallets (Priority: P3)

A person returns after a month without opening the app; the server has kept materialising and
(for automatic rules) confirming. When something looks behind, they ask the server to check the
rule now. In a shared wallet every member sees the same rules and occurrences.

**Why this priority**: The server does the work hourly; this is a manual escape hatch and a consistency guarantee rather than a daily flow.

**Independent Test**: With a rule whose last listed occurrence is in the past, tap "Check for new dates" online: the next occurrences appear after the follow-up read.

**Acceptance Scenarios**:

1. **Given** an online owner or editor on a rule, **When** they tap "Check for new dates", **Then** the server catches the rule up and the phone reads the wallet; new occurrences (and automatic confirmations) appear.
2. **Given** no connection, **When** the person looks at the rule, **Then** "Check for new dates" says it needs a connection; nothing is queued.
3. **Given** a shared wallet, **When** a member confirms or skips an occurrence on another phone, **Then** this phone shows the new state after its next read; a viewer sees everything read-only.
4. **Given** the person loses the wallet, **When** the phone learns it, **Then** the wallet's rules and occurrences leave the phone with it.

---

### Edge Cases

- **Month-end anchors**: a rule on day 31 falls on 30 April, 28/29 February, back to 31 in March (always counted from the start); a yearly rule on 29 February falls on 28 February in other years.
- **Today across time zones**: "due" and "today" use the rule's own time zone (the author's profile time zone when it was created, never changing), not the phone's clock zone. A rule created offline uses the person's profile time zone until the server's copy arrives.
- **Rule created offline**: no occurrence exists until the server accepts the rule and the phone reads; the rule shows its preview only (US1-S4, US5-S4).
- **Confirm racing the server's automatic confirmation or another member**: the second arrival is refused as a clash carrying the confirmed occurrence; the phone accepts it and drops its own pending transaction (FR-024).
- **Confirm racing a rule edit** (the template changed the occurrence's version meanwhile): a clash with the choices of US3-S7; "Keep mine" re-sends the same confirmation with the same transaction id.
- **Confirm refused** (destination wallet gone, currencies differ, no right in the destination): "Not saved" with the reason; the pending transaction stays visible as not saved until the person discards or retries, as other refused records do.
- **Rule deleted elsewhere while this phone confirmed or skipped one of its occurrences**: the occurrence's change meets the deletion; only "Accept the deletion" is offered and the pending transaction is dropped.
- **Ended rule**: read-only except "Delete"; no edit, pause or end offered.
- **A wallet's currency changed while a new rule waits**: a never-sent rule is sent in the wallet's present currency; a server refusal (currency mismatch) shows "Not saved".
- **A category deleted or archived after a rule uses it**: a rule waiting to sync keeps the category from deletion on this phone; the server keeps referenced categories in use.
- **Transfer rule**: lives in the source wallet; destinations are the same choices as for transfers (spec 008); confirming needs the right to write in both wallets.
- **Split ("Change from a date") refused**: the new rule waits until ending the old one is accepted; if ending the old one is refused or discarded, the new rule is discarded with it, so two overlapping rules never exist.
- **Occurrence outside the rule's pattern** (edited date, or pattern changed later): kept as edited; listed by its effective date.
- **Many rules**: a wallet with 50 rules and 2,000 occurrences opens its recurring screen without delay.
- **Process killed after confirming offline**: the confirmation and its pending transaction survive and are sent later.
- **Sign-out**: rules, occurrences and pending confirmations are removed with everything else.
- **Unknown values** (a new frequency, preset or refusal reason from a later server): shown generically, never crash; the rule is read-only on this phone.

## Requirements *(mandatory)*

### Functional Requirements

**Rules**

- **FR-001**: The app MUST show every rule, occurrence and due item only from the phone's copy (P-04.1); new rules MUST be created with a client-generated UUIDv7 and written locally first, then synced through the sync engine, with or without a connection.
- **FR-002**: Owners and editors of an active wallet with a verified address MUST be able to create a rule from the wallet's recurring screen and from the transaction editor's "Make recurring" (create mode only). Viewers MUST NOT be offered it; archived wallets MUST NOT offer new rules. Every rule or occurrence write by a person with an unverified address MUST show the verification prompt and queue nothing.
- **FR-003**: "Make recurring" MUST open the rule editor prefilled from the draft (wallet, kind, amount, category or destination, note, details, person, the draft's date as start) and MUST NOT also record the draft as a transaction.
- **FR-004**: The rule editor MUST offer: preset (custom, gam'eya, installment), kind (income, expense, transfer for custom; expense for gam'eya; expense or transfer for installment), amount, amount basis (per round, or total for a counted rule), category (income/expense) or destination wallet (transfer), note, details, person, how often (daily, weekly, monthly, yearly), every how many (1–365), day of month (monthly, yearly; defaulting to the start date's day), start date, and an end: never, on a date, or after a number of times (1–1000; 2–120 for presets), and automatic confirmation.
- **FR-005**: The editor MUST validate locally with the server's rules before saving (amount positive in the wallet currency's minor units; interval 1–365; day 1–31; count ranges; end not before start; dates 1900–2999; a total at least one minor unit per round; payout round within the count; a gam'eya payout not above 2^53 − 1; texts within the transaction limits) and show the error on the field.
- **FR-006**: The editor MUST preview the next five dates (and, for presets, every round's amount and the payout) computed on the phone with the backend's schedule: daily every n days from the start; weekly every n weeks on the start's weekday; monthly every n months on the day, clamped to the month's last day; yearly every n years in the start's month on the day, clamped; each date computed from the start, never from the previous one; dates stop at the end date or count.
- **FR-007**: A rule's currency MUST be the wallet's; a rule never sent MUST be sent in the wallet's present currency (as for transactions, spec 008 FR-026).
- **FR-008**: Destinations for a transfer rule MUST be the same choices as for a transfer (spec 008): active wallets on the phone in the same currency where the person is owner or editor, other than the source.
- **FR-009**: Category choices MUST be C5's choices for the rule's kind; the gam'eya payout category MUST be an income category.
- **FR-010**: The gam'eya preset MUST set monthly, every 1, expense, no end date, count 2–120, payout round 1..count and a payout income category; the installment preset MUST set monthly, every 1, expense or transfer, no end date, count 2–120. Preset values MUST be shown as fixed in the editor.
- **FR-011**: Automatic confirmation MUST default to off for every new rule; the editor MUST explain that when on, the server records each round on its date as the rule's author, and never for dates before the day it is switched on.

**Editing a rule**

- **FR-012**: Editing a rule MUST save the whole rule with the version shown when the editor opened (P-04.4); preset, kind, currency and destination MUST NOT be editable after creation.
- **FR-013**: Once any occurrence of the rule on the phone is confirmed or skipped, the editor MUST disable frequency, interval, day, start date, amount basis and payout round, and for a gam'eya or a total-basis rule also the amount and the count, each with a one-line reason and the "Change from a date" option; an end that would cut off a resolved round MUST be refused locally.
- **FR-014**: "Change from a date" MUST, as one action, queue a save of the existing rule ending on D − 1 and queue a new rule (new UUIDv7) with the person's changes starting on D; D MUST be after the last resolved round's date and on or after the old rule's start. The new rule MUST NOT be sent before the end of the old rule is accepted; if that end is refused, clashes and is then discarded, or is discarded, the new rule MUST be discarded with it.
- **FR-015**: The rule detail MUST offer Pause / Resume (active ⇄ paused), End (with a confirmation that unconfirmed rounds dated after the rule's today go away, while rounds already due stay to be confirmed or skipped) and Delete (with a confirmation that its transactions stay and that deletion is final). An ended rule MUST offer only Delete.
- **FR-016**: Deleting a rule MUST hide it and its unconfirmed occurrences at once and queue a deletion carrying its version; a rule never sent MUST be removed at once with nothing sent. Transactions it produced MUST stay.

**Occurrences**

- **FR-017**: Occurrences MUST come only from the server (wallet feed); the phone MUST NOT create one. A rule without received occurrences MUST show its preview labelled "Dates appear after sync" and offer no confirm or skip.
- **FR-018**: "Due" MUST mean scheduled and dated on or before the rule's today; "missed" MUST mean due while a later non-payout occurrence of the same rule is also due. Both MUST be derived on the phone, never stored, with "today" taken in the rule's time zone (the person's profile time zone for a rule not yet accepted).
- **FR-019**: Due and missed occurrences MUST be listed (a) at the top of the wallet's transactions screen in a "Due" section, (b) on Home in a "Due" card showing the count across wallets when at least one is due, opening the all-wallets recurring screen, and (c) in the recurring screen's "Due" section. The transactions screen's "Due" section MUST show at most 3 items (missed first, then oldest) with "See all (n)" opening the wallet's recurring screen. The Home card MUST count only occurrences in wallets where the person is owner or editor. Upcoming (scheduled, after today) MUST be listed on the recurring screen and each rule's detail. Viewers MUST see due and upcoming occurrences read-only.
- **FR-020**: Occurrences MUST be visually distinct from transactions (label and treatment, not colour alone, P-08.3) and MUST NOT count in any balance, day total, search or filter result of the transactions screen.
- **FR-021**: Confirming MUST open a confirm form prefilled with the occurrence's values (date, amount, category, note, details, person; kind, currency and destination fixed) and, on save, queue one occurrence write with status confirmed, a new client UUIDv7 transaction id and the base version, and at the same time show a transaction with that id, those values and the rule and occurrence links in the wallet (both wallets for a transfer), counted in the balance as unsent. Category choices MUST be C5's choices for the kind with the occurrence's current category kept. While the confirmation waits to sync, the person MAY change its values (the pending transaction follows, the transaction id stays) or cancel it (the pending transaction goes with it).
- **FR-022**: A confirmation MUST be sent as the occurrence write only; the phone MUST NOT send the transaction itself. The shown transaction MUST be replaced by the server's copy of the same id when it arrives, without the balance jumping back or counting it twice (spec 008 carry rule). Until then, opening that transaction MUST open the occurrence (its status and choices), not the transaction editor.
- **FR-023**: Skip MUST queue status skipped (transaction id null); undo skip MUST queue status scheduled; a skipped occurrence MAY also be confirmed directly. Editing one occurrence MUST queue its new date, amount, category or texts; the occurrence's scheduled date and round number never change.
- **FR-024**: When a confirmation (or skip or edit) meets a clash whose current occurrence is confirmed, the phone MUST apply the server's occurrence, drop its own pending transaction (never re-sending under a new id) and show "Already confirmed" on the occurrence, without asking. Any other clash MUST show "Keep mine" (re-send the same write, same transaction id, on the newer version) / "Use the other device's"; a deleted occurrence or rule MUST offer only "Accept the deletion".
- **FR-025**: A refused confirmation MUST show "Not saved" with copy chosen by code and pointer (destination gone or no longer a membership at `/status`, currency mismatch at `/status`, no right in the other wallet `FORBIDDEN`, category refused at `/category_id`, out of range), with "Try again" (after editing) and "Discard"; discarding MUST drop the pending transaction.
- **FR-026**: A confirmed occurrence MUST be read-only and link to its transaction; a confirmed occurrence whose transaction was deleted MUST still show as confirmed.
- **FR-027**: An occurrence with an automatic-confirmation refusal MUST show it as due with the reason (not permitted, other wallet unavailable, currency mismatch); any manual save of it clears the reason.

**Catch-up, sync and housekeeping**

- **FR-028**: Rule detail MUST offer "Check for new dates" to owners and editors of a rule not ended, online only: it calls the materialise endpoint once, then reads the wallet feed; offline it MUST say it needs a connection; nothing is queued; the phone MUST NOT call it automatically.
- **FR-029**: Rules and occurrences MUST be read from the wallet feed (`recurring_rule`, `occurrence` rows) with the version guard, deletion rule and sweep of spec 009, and uploaded as batch items (`recurring_rule` upsert/delete, `occurrence` upsert); a change needing a wallet or category created on this phone and not yet accepted MUST be held (spec 009 FR-022). Pull to refresh on the recurring screens MUST read the wallet scopes shown (forced, spec 009 floors otherwise).
- **FR-030**: On update to this version, the phone MUST forget every wallet scope's feed position once, so every wallet is read from the start and rules and occurrences that were skipped before appear; data already on the phone stays shown meanwhile.
- **FR-031**: The transaction editor (create mode) MUST offer "Confirm <description> (<date>) instead" when a received unconfirmed occurrence in the same wallet matches the draft (same kind; same category, or same destination for a transfer; dated within 7 days of the draft's date). Accepting MUST discard the draft and confirm the occurrence with the draft's amount, date and texts; ignoring it MUST record the transaction as today.
- **FR-032**: Categories used by a rule or occurrence change waiting to sync MUST count as in use on the phone (C5 `PendingCategoryUses`).
- **FR-033**: When a wallet leaves the phone, its rules, occurrences and their waiting changes MUST leave with it (C4 `WalletDeletionParticipant`); sign-out MUST remove all rules and occurrences.
- **FR-034**: The rule's writes and the occurrence writes MUST carry only the members declared in `RecurringRuleWrite` / `OccurrenceWrite` with explicit nulls; read-only members (timezone, author, links) MUST NOT be sent; unknown response members and unknown enum values MUST be tolerated (shown generically, rule read-only).
- **FR-035**: Every failure MUST be handled by `code` and pointer (P-02.4); refusal copy MUST map `IMMUTABLE_FIELD`, `CURRENCY_MISMATCH`, `INVALID_ENUM_VALUE`, `OUT_OF_RANGE`, `REQUIRED`, `FORBIDDEN`, `EMAIL_NOT_VERIFIED`, `NOT_FOUND` to plain words naming the field.
- **FR-036**: Logs MUST NOT contain amounts, texts or payloads (P-09.2); they may contain record types, ids, outcomes, codes and request ids.
- **FR-037**: All new text MUST exist in English and Arabic with Western digits, mirrored correctly in RTL (P-06); UI words MUST follow the glossary (Recurring rule / قاعدة متكررة; gam'eya / جمعية; installment / قسط) and MUST NOT use "schedule", "repeat", "recurrence", "instance" or "run" as names for these.
- **FR-038**: Opening a rule's produced transaction MUST show "From a recurring rule" (spec 008) and offer "Open the rule" when the rule is on the phone.
- **FR-039**: While an edit of a rule waits to sync, its occurrences MUST keep showing the values the phone holds, and the rule MUST say "Upcoming rounds update after sync".

### Key Entities *(include if feature involves data)*

- **Recurring Rule**: wallet-scoped, client-created; template (kind, amount and basis, currency, category or destination, texts, automatic confirmation), schedule (frequency, interval, day, start, end date or count), preset and gam'eya payout members, status (active, paused, ended), the rule's time zone and author (read-only), version and feed position.
- **Occurrence**: wallet-scoped, server-created child of a rule: round number, payout flag, scheduled date (fixed) and effective date, status (scheduled, confirmed, skipped), its own amount/category/texts, edited flag, the transaction it produced, the automatic-confirmation refusal.
- **Transaction** (C6): gains nothing new; a confirmed occurrence's transaction carries the rule and occurrence links read-only.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a person set up recurring income, expenses and transfers once — including the gam'eya and
installment presets — see what is due and coming, confirm, skip or adjust each occurrence (offline
too), and change a rule from a date without rewriting history, while keeping the books free of
double recordings and the balance exact.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Recurring Rule** (never "schedule", "repeat",
"recurrence"), **Occurrence** (never "instance", "run"), **gam'eya** (جمعية) and **installment**
(قسط) as presets, **payout**, **anchor day**, **materialise** (internal), **horizon** (internal),
**occurrence key** (internal), **Transaction**, **Wallet**, **Category**, **counter wallet**
(internal; UI says "To wallet"). UI words: "Recurring" / "متكررة", "Recurring rule" / "قاعدة
متكررة", "Due" / "مستحقة", "Missed" / "فائتة", "Upcoming" / "القادمة", "Confirm" / "تأكيد",
"Skip" / "تخطي", "Paused" / "متوقفة مؤقتًا", "Ended" / "منتهية", "Payout" / "القبض",
"Automatic confirmation" / "تأكيد تلقائي", "Check for new dates", "Change from a date",
"Dates appear after sync", "Already confirmed". The noun for one occurrence in the UI is the date
and description itself ("Rent · 1 Nov"); where a noun is unavoidable, "Occurrence" / "دفعة" (owner
review).

### 3. Data model

Adds two phone-side tables, **recurring_rule** and **occurrence**, holding every field of
`RecurringRule` and `Occurrence` (including `version`, `change_seq`, `created_at`, `updated_at`;
a tombstone removes the row unless a change to it waits, as in spec 009), plus the phone's `account_id` and `refreshed_at`; cleared at
sign-out. Unsent changes use the spec 004 queue with record types `recurring_rule` and
`occurrence` in the wallet scope. A pending confirmation's transaction is a phone-only row in the
C6 `transactions` table with the produced id and both links, version 0, marked as a pending
confirmation (new column), not queued as its own change. The on-device database goes from version 6 to 7 with a migration that keeps all data,
creates the two tables, and clears the feed position and last-read stamp of every wallet scope
once (FR-030); the schema is exported.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Rule, occurrence and due screens render only from the phone's copy | P-04.1, FR-001 | store/service tests over fakes; no API in UI/store |
| Rule create/edit/delete and occurrence confirm/skip/edit work offline and queue in the wallet scope with the base version | P-04.2, P-04.4, FR-001 | service tests with the network off; queue contents asserted |
| The phone never creates an occurrence; no occurrence create or delete is ever sent | ADR-0018 rule 1, FR-017 | handler test (no `delete` item, upsert only on server-known ids); service test |
| One confirmation = one occurrence write carrying one transaction id; the transaction itself is never sent; a clash with a confirmed occurrence drops the local transaction and never re-sends under a new id | ADR-0018 rules 4–6, FR-021, FR-022, FR-024 | handler + engine tests; captured-answer test |
| An occurrence never counts in a balance; a pending confirmation counts exactly once until the server's copy and a covering wallet copy arrive | P-05 (backend), FR-020, FR-022 | balance projection tests over confirm → send → feed sequence |
| Phone preview dates equal the server's schedule (month-end clamp from the origin, all frequencies) | FR-006, `RuleDates` | table-driven date tests incl. 31st, 29 Feb, intervals; compared with captured server occurrences |
| Total split sums exactly, remainder to the earliest rounds; payout = sum of rounds | FR-006, FR-010 | allocation tests (EGP, KWD, JPY) |
| Due/missed derived in the rule's time zone; never stored or sent | FR-018, ADR-0018 rules 8–9 | derivation tests across zones and midnight |
| Frozen members cannot be edited once a round is resolved; preset, kind, currency, destination never | FR-012, FR-013 | editor reducer tests |
| A split never leaves two overlapping rules on the server | FR-014 | engine hold + discard cascade tests |
| Writes carry only declared members with explicit nulls; read-only members never sent | P-02.2, FR-034 | contract request-shape tests |
| Failures branch on code and pointer only | P-02.4, FR-035 | refusal-mapping tests |
| Wallet removal and sign-out remove rules and occurrences | FR-033, P-09 | participant test; sign-out wipe test |
| Migration 6 → 7 keeps data and forgets wallet positions only | FR-030 | migration test 6 → 7 |
| No amounts, texts or payloads in logs | P-09.2, FR-036 | log-capture test |
| Hand-recorded match offer appears only for received, unconfirmed, matching occurrences | FR-031 | editor store tests |

### 5. API

All under `/api/v1`, frozen in `recurring.yaml` v1.7 (backend spec 014, `c0e9844`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Rule create / save (sync engine, batch item `recurring_rule` upsert) | `PUT /wallets/{id}/recurring-rules/{rid}` | `RecurringRuleWrite` (all members, explicit nulls, `version`: 0 to create) | 200/201; `409 VERSION_CONFLICT` (+`current`, live or tombstone); `422 VALIDATION_FAILED` (`IMMUTABLE_FIELD`, `CURRENCY_MISMATCH` at `/currency` or `/counter_wallet_id`, `INVALID_ENUM_VALUE`, `REQUIRED`, `OUT_OF_RANGE`, `UNKNOWN_FIELD`), `422 INVALID_ID`; `403 EMAIL_NOT_VERIFIED`; `403 FORBIDDEN` (viewer, or no T-02 in the destination); `404 NOT_FOUND`; retryable per catalogue |
| Rule delete (sync engine, batch item `recurring_rule` delete) | `DELETE /wallets/{id}/recurring-rules/{rid}` with a JSON body | `RuleVersionOnly` | 200 (tombstone; also already deleted); 409 (+`current`); 403; 404 |
| Occurrence confirm / skip / un-skip / edit (sync engine, batch item `occurrence` upsert) | `PUT /wallets/{id}/occurrences/{oid}` | `OccurrenceWrite`: `status, date, amount_minor, category_id, description, memo, counterparty, transaction_id, version` | 200; 409 (+`current`: confirmed → FR-024 silent accept; scheduled/skipped → clash; tombstone → accept deletion); 422 (`IMMUTABLE_FIELD`, `REQUIRED` / `INVALID_ENUM_VALUE` at `/transaction_id`, `INVALID_ENUM_VALUE` / `CURRENCY_MISMATCH` at `/status`, `INVALID_ENUM_VALUE` at `/category_id`, `OUT_OF_RANGE`); 403; 404 |
| Catch up (online only) | `POST /wallets/{id}/recurring-rules/{rid}/materialise` (no body) | — | 200 → read the wallet feed; 403 (`EMAIL_NOT_VERIFIED`, `FORBIDDEN`); 404 (rule or wallet gone → read the feed); 409 (tombstone → read the feed); offline/timeout/5xx → "needs a connection / try later" |
| Reads | `GET /sync/wallets/{id}/changes` (spec 009) | — | `recurring_rule` and `occurrence` rows now applied |

The list and single-record reads (`GET …/recurring-rules[/{rid}]`, `GET …/occurrences[/{oid}]`)
are not used: the wallet feed carries both (spec 009 decision). `transactions.yaml`'s read-only
`recurring_rule_id` and `occurrence_id` are already stored (spec 008).

#### Contract changes since last spec

Baseline `saveme-backend@6b8e290` (spec 009) → `2a3b414` (+ uncommitted backend spec 017 in the working tree):

- **`feature` member on `FEATURE_NOT_ENTITLED`** (v1.9, committed): the capability key a refusal names. Follow-up: not in scope of this spec (C9 sharing / C3; recurring has no entitlement).
- **Invitations are not batch items** (v1.9, convention text): follow-up: not in scope of this spec (C9).
- **`meta.sync_position` and `GET /sync/wallets/heads`** (v1.10, uncommitted backend spec 017): follow-up: not in scope of this spec (a later C14 follow-up; spec 009 risk 3).
- **`wallet_member` re-stamps on a display-name change** (v1.10, uncommitted): already applied by spec 009's equal-version rule; no change.
- Error catalogue: no change; the client's codes match the catalogue (no client mismatch).

### 6. Authorization and entitlements

Following the backend wallet permission matrix (spec 010 §6, not copied here): any active member
reads rules and occurrences (R-01); owners and editors create, edit, delete and materialise rules
and confirm, skip and edit occurrences (R-02); a transfer rule and confirming its occurrences also
need T-02 in the destination. The app offers actions by the person's role as last read (C4) and
treats `FORBIDDEN` as the authority. Every write needs a verified address. No plan feature gates
recurring rules (backend spec 014 default 17).

### 7. Events

Not applicable: the client publishes no domain events. It consumes the sync engine's queue and
status, the wallet feed, C4's wallet views and deletion hook, C5's choices and displays, and C6's
transaction list, editor, balance projection and destination choices. Reminders for due
occurrences are C16.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Set up | Unit (editor reducer/store, schedule preview, service create, handler PUT, applier) + contract tests + E2E | tests; `.maestro/010-recurring/US1-*` (deferred) |
| US2 Due & confirm | Unit (due/missed derivation, confirm queue + pending transaction, balance projection, clash handling) + contract + captured answers + E2E | tests; `US2-*` (deferred) |
| US3 Change | Unit (frozen members, split hold/cascade, pause/end/delete, clash choices) + contract + E2E | tests; `US3-*` (deferred) |
| US4 Presets | Unit (preset shapes, allocation, payout) + screenshots + E2E | tests; `US4-*` (deferred) |
| US5 No double | Unit (match rule, editor offer, accept path) + E2E | tests; `US5-*` (deferred) |
| US6 Catch up & shared | Unit (materialise call + read, offline) + contract + live QA | tests; `US6-*` (deferred) |

Owner policy: no emulator, Maestro or device run; flows are written and marked "deferred: E2E on
owner's device". Host tests plus a live-backend contract QA (curl against the local backend,
real answers captured as fixtures replayed through the real client, engine, handlers and
appliers), as specs 007–009.

### 9. Open questions

- **Backend contract gaps (non-blocking)**: (1) no way to confirm an occurrence the phone has not received, so a rule created offline cannot have its first round confirmed until it syncs (mitigated by FR-017, FR-031); (2) a hand-recorded transaction cannot later be linked to an occurrence ("this is it" after the fact), so the client can only prevent, not repair, a duplicate; (3) a split ("from date D") is two writes with no atomic server operation, so the client orders and holds them (FR-014); (4) rules on a transfer's destination feed are not listed there, so the destination wallet's members see the produced transactions but not the rule (backend spec 014 open item).
- **Arabic words** "دفعة" (occurrence, where a noun is unavoidable), "القبض" (payout), "مستحقة" / "فائتة" (due / missed): assumed defaults for owner review. Non-blocking.
- **Reminders for due occurrences**: C16. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can set up a monthly rule from the transaction entry screen in under 30 seconds, online or offline, and sees its next dates before saving.
- **SC-002**: The phone's preview dates equal the server's occurrence dates for 100% of the schedules in the test table (every frequency, intervals 1–3, days 28–31, 29 February, end date and count).
- **SC-003**: 100% of confirmations, skips and edits made offline reach the server, or are shown as needing the person's decision, once a connection returns.
- **SC-004**: Zero duplicate transactions are produced by the phone for one occurrence, including after retries, lost answers, process death and races with automatic confirmation.
- **SC-005**: The displayed balance always equals the server's balance plus this phone's unsent effects, including pending confirmations, and never shows an intermediate value while a confirmation syncs.
- **SC-006**: Every due occurrence on the phone is visible within one tap of Home (the "Due" card) and at the top of its wallet's transactions screen.
- **SC-007**: The brief's integration checkpoint passes: a monthly rule created, its next occurrence visible as upcoming; skipping it removes it from upcoming; a series edit from a date changes later rounds only.
- **SC-008**: A wallet with 50 rules and 2,000 occurrences opens its recurring screen within 1 second from the phone's copy.

## Assumptions

- Backend B7 is built (`c0e9844`); recurring.yaml v1.7 is unchanged at `2a3b414`.
- Rules and occurrences reach the phone only through the wallet feed (spec 009); the dedicated list and read endpoints are not used.
- A recurring screen per wallet is reached from a "Recurring" row on the wallet detail and from the wallet's transactions screen; the same screen for all wallets is reached from Home's "Due" card.
- New rules are not offered in archived wallets; existing rules there stay visible and editable (the backend decides).
- Reminders and notifications for due occurrences are out of scope (C16); budgets and reports read only transactions (C8, C13).
- Shared-wallet scenarios need a second member, seeded through the API (C9 sharing exists on the backend).
- No new dependency.
