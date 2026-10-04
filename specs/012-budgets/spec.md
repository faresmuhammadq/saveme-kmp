# Feature Specification: Budgets (client)

**Feature Branch**: `012-budgets`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "specs/briefs/C08-budgets.md" — client brief C8, wave 2. Pairs with backend B8 → spec 015 (built, committed `bb7e61b`; `budgets.yaml` v1.8, frozen for C8 and unchanged since).

**Backend contract**: `saveme-backend@64ca1ea`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/budgets.yaml` (v1.8: `PUT`/`PATCH`/`DELETE /budgets/{id}`, `POST /budgets/{id}/archive`, `GET /budgets[/{id}]`, `GET /budgets/{id}/progress?date=`, `GET /budget-progress?date=`), `paths/sync.yaml` (`budget` rows on the **user** feed, never a wallet feed; batch item type `budget` with `upsert` and `delete` only, no `wallet_id`; archive is per record), `paths/identity.yaml` (`budget_period_start_day` 1–28 and read-only `budget_period_start_day_from` on the profile), `docs/api-conventions.md`, `docs/openapi/error-catalog.yaml` (no new code in spec 015).
- Backend behaviour: `saveme-backend/specs/015-budgets/summary.md`, `specs/015-budgets/contracts/budgets-api.md` ("What C8 binds to") and `docs/adr/0019-budget-spending-attributed-by-author-computed-on-read.md`: a budget is personal (user-scoped), one currency fixed at creation, `category_id` null = all expenses, category/currency/period type immutable, limit editable even after archive, archive one-way, delete final; spending = the owner's own live **expenses** (`created_by`; a recurring rule's transaction counts for the rule's author) in the budget's currency and category, dated in the Budget Period, in wallets the owner currently belongs to (any role); other currencies named in `other_currencies`, never summed; progress computed on every read, no percentage returned; no overrun notification (B16).
- Glossary (`saveme-backend/docs/glossary.md`): **Budget** (never "limit plan", "envelope"), **Budget Period**, **Budget Progress**, **budget start day**; out of MVP: rollover, per-period rows.
- Builds on spec 002 (profile with budget start day and its "Applies from" date), 004 (sync engine: queue, clashes, refusals, status), 006 (C4 wallets: memberships, roles), 007 (C5 categories: choices, displays, `PendingCategoryUses`), 008 (C6 transactions: the phone's copy of every wallet's transactions plus waiting changes), 009 and 011 (C14: user feed, `FeedApplier`, batch items, held dependents, positions; `budget` rows are skipped as unknown until this feature, which clears the **user** scope's feed position once so they are read from the start), 010 (C7 recurring: phone-only pending confirmations, `RecurringLinks`) and 001 (design system `BudgetProgress` component).

## Clarifications

### Session 2026-10-05

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Which figure does the phone show for a budget's progress, given the server computes it on every read but the phone holds the same transactions? → A: The phone always computes the current period's progress itself from the transactions it holds, with the server's rules and including changes waiting to sync, so it moves the moment an expense is recorded, offline too. After each progress read it keeps the server's figures; while nothing that feeds a budget has changed on the phone since that read, the server's figures are shown instead of the phone's (they cover anything the phone has not received yet). A budget whose shown figure includes a change waiting to sync says so. (FR-012, FR-013, FR-014)
- Q: Where does budget progress appear besides the budgets screen? → A: On Home, in a "Budgets" card listing up to 3 active budgets of the current period, highest share spent first, with "See all" opening the budgets screen; the card is hidden when the person has no active budget and offers "Set a budget" from the empty budgets screen instead. No per-wallet summary: a budget is personal and spans every wallet of its currency. (FR-016, FR-017)
- Q: Can the person create a second active budget for the same category and currency? → A: No, not from this phone: the category picker marks a category (or "All expenses") that already has an active budget in the chosen currency as "Already budgeted" and does not offer it. An all-expenses budget beside category budgets is allowed. Budgets that overlap anyway (two phones offline) are both shown and both count; nothing is merged. (FR-004)
- Q: Does this feature show past Budget Periods? → A: No. Budgets show the current Budget Period only; history by period belongs to reports (C13). The phone never asks the server for a date other than today's period. (FR-011)
- Q: What does the phone show while a start day change is pending (saved, but its "applies from" date not reached) or not yet synced? → A: Periods always follow the server's copy of the profile: the current start day and the date it applies from. Before that date, the current period is the one the server last reported (its start and end are kept from the last progress read); if the phone has none, the budget shows its limit with "Progress appears after sync" and no estimate. The budgets screen states the current period's dates and, when a change is pending, "From <date>, periods start on day <n>". A start day changed on this phone and not yet synced changes nothing until the server answers. (FR-009, FR-010)

## User Scenarios & Testing *(mandatory)*

People overspend on a few categories (groceries, dining out, transport) without noticing until the
month is gone. A budget caps one category, or all expenses, per Budget Period, in one currency.
The person sets it once, offline or online, and sees at a glance how much is spent, how much is
left, the share spent and whether the current pace ends above the limit. Figures are neutral:
numbers and words, never shaming.

### User Story 1 - Set a monthly budget for a category or for all expenses (Priority: P1)

A person sets "Groceries, 6,000 EGP per month" from the budgets screen, with or without a
connection. It appears at once with its progress for the current period, and reaches the server
and their other phones on the next sync.

**Why this priority**: It is the brief's integration checkpoint ("budget for Groceries") and the entry point to everything else.

**Independent Test**: Offline, create a Groceries budget of 6,000 EGP; it is listed at once with the current period and the spending already recorded in Groceries this period. Go online: it syncs and is unchanged.

**Acceptance Scenarios**:

1. **Given** a person with a verified address, **When** they create a budget (category or "All expenses", currency, monthly limit), **Then** it is listed at once on the budgets screen with "Waiting to sync" until the server accepts it, and its progress for the current period is shown immediately.
2. **Given** the budget editor, **When** the person picks a category, **Then** only expense categories are offered (their own active ones and the active defaults, as for transactions, C5), plus "All expenses"; income categories are never offered.
3. **Given** the budget editor, **When** the person picks the currency, **Then** the choices are the currencies of their wallets on the phone and their profile's default currency, defaulting to the profile's default currency; the amount field follows the chosen currency's fraction digits.
4. **Given** an active budget for Groceries in EGP, **When** the person creates another budget, **Then** Groceries in EGP is shown as "Already budgeted" and cannot be chosen; Groceries in USD and "All expenses" in EGP can.
5. **Given** a limit of zero, a negative or a too-large amount, or more fraction digits than the currency has, **When** the person saves, **Then** the field shows the problem and nothing is saved.
6. **Given** an unverified address, **When** the person tries to create a budget, **Then** the verification prompt is shown and nothing is queued.
7. **Given** a budget created on another phone, **When** this phone next reads the person's changes, **Then** the budget appears with its progress.

---

### User Story 2 - See where I stand this period (Priority: P1)

On Home and on the budgets screen the person sees, for each active budget: its category (or "All
expenses"), spent of limit, remaining (or how much over), the share spent as a whole percentage,
and the amount the current pace ends at. Spending in other currencies that would otherwise count
is named, never added.

**Why this priority**: It is the value of the feature; without it a budget is a number in a list.

**Independent Test**: With a 6,000 EGP Groceries budget and 3,500 EGP of Groceries expenses on day 8 of a 30-day period, the budget shows "3,500 EGP of 6,000", "2,500 EGP left", 58% and "On pace for 13,125 EGP".

**Acceptance Scenarios**:

1. **Given** an active budget, **When** the person opens the budgets screen, **Then** each active budget shows its name, spent of limit, remaining, the percentage (spent ÷ limit, rounded to the nearest whole percent) and a bar, using the design system's budget progress element; the percentage and words are always written, so the state never relies on colour.
2. **Given** spending above the limit, **When** shown, **Then** the budget says how much it is over (for example "500 EGP over") and its percentage above 100, in the design board's neutral "over" treatment with no alarm wording, icon or exclamation.
3. **Given** the current period with at least one day elapsed and some spending, **When** shown, **Then** "On pace for <amount>" appears (spent × days in period ÷ days elapsed, rounded up to the minor unit), and nothing appears when no spending exists yet or the projection would exceed 2^53 − 1.
4. **Given** Groceries expenses in a USD wallet beside an EGP Groceries budget, **When** shown, **Then** the EGP budget counts none of them and says "Also spent in USD (not counted)".
5. **Given** several active budgets, **When** Home is shown, **Then** a "Budgets" card lists up to 3 of them (highest percentage first, then oldest), with "See all" opening the budgets screen; with none, the card is hidden.
6. **Given** the budgets screen, **When** shown, **Then** it states the current Budget Period ("25 Sep – 24 Oct") and the days left, in the profile's time zone and the app language with Western digits.
7. **Given** an archived budget, **When** the person opens the budgets screen, **Then** it is listed under "Archived" with its current-period figures and never on Home.

---

### User Story 3 - Progress moves offline at once and matches the server after sync (Priority: P1)

The person records an expense in Groceries while offline; the Groceries budget (and "All
expenses") moves immediately. After the phone syncs, the shown figures equal the server's.

**Why this priority**: It is the brief's integration checkpoint and the product's offline-first promise (P-04).

**Independent Test**: Offline, record a 200 EGP Groceries expense; the Groceries budget's spent rises by 200 EGP at once, marked as including a change waiting to sync. Go online and sync: the mark goes and the figures equal `GET /budget-progress`.

**Acceptance Scenarios**:

1. **Given** a budget, **When** the person records, edits or deletes an expense that matches it (their own, in its currency and category, dated in the current period), offline or online, **Then** spent, remaining, percentage, projection and over-limit update immediately.
2. **Given** a shown figure that includes a transaction change waiting to sync, **When** shown, **Then** the budget says "Includes changes waiting to sync"; when the change is accepted the note goes and the figure does not jump.
3. **Given** a progress read from the server, **When** nothing that feeds a budget has changed on the phone since, **Then** the server's figures are shown (they also cover spending the phone has not received yet).
4. **Given** a transfer, an income, a partner's expense in a shared wallet, an expense in another currency, an expense dated outside the period, or a deleted transaction, **When** progress is computed on the phone, **Then** none of them count, exactly as on the server.
5. **Given** a recurring rule's occurrence the person confirmed on this phone and not yet synced, **When** it is an expense matching a budget, **Then** it counts for the rule's author (the person, for their own rules) as the server will count it.
6. **Given** a wallet the person leaves or loses, **When** the phone learns it, **Then** its transactions stop counting, as on the server.
7. **Given** a budget created on this phone and not yet synced, **When** shown, **Then** its progress is the phone's estimate; the server's figures replace it after it syncs and the next progress read.

---

### User Story 4 - Change the limit, archive or delete a budget (Priority: P2)

The person raises a limit, archives a budget they no longer follow, or deletes one, offline or
online. A clash with another phone or a refusal from the server is shown with clear choices.

**Why this priority**: Budgets change with life; without editing, people delete and re-create, losing nothing but trust.

**Independent Test**: Offline, raise Groceries to 7,000 EGP; the bar and percentage move at once. Archive it: it moves to "Archived". Delete another budget: it disappears. Sync: all three reach the server.

**Acceptance Scenarios**:

1. **Given** a budget, **When** the person opens it, **Then** its category, currency and period are shown read-only with "Can't be changed after the budget is created", and only the limit is editable (also for an archived budget).
2. **Given** a changed limit, **When** saved, **Then** the budget shows the new limit and recomputed figures at once and queues a save carrying the version shown when it was opened.
3. **Given** an active budget, **When** the person archives it and confirms ("Archived budgets leave Home and can't be made active again"), **Then** it moves to "Archived" at once and the archive is queued.
4. **Given** a budget, **When** the person deletes it and confirms ("This can't be undone"), **Then** it disappears at once and the deletion is queued with its version; a budget never sent is removed with nothing sent.
5. **Given** a save that meets a newer version from another phone, **When** the server refuses it as a clash, **Then** the budget shows "Changed on another device" with "Keep mine" (re-send on the newer version) and "Use the other device's".
6. **Given** a save, archive or delete that meets a budget deleted on another phone, **When** the server answers with the deletion, **Then** only "Accept the deletion" is offered (deletion is final).
7. **Given** a refused write (for example the category can no longer be budgeted), **When** shown, **Then** the budget says "Not saved" with plain words chosen by the problem code and field, "Try again" and "Discard"; discarding a never-sent budget removes it.

---

### User Story 5 - Budget Periods follow my start day (Priority: P2)

A person paid on the 25th sets their budget start day to 25 in their profile (spec 002). Their
budgets then run 25th to 24th. When they change the start day, the period under way keeps its
start and the new day applies from the date the profile shows.

**Why this priority**: Periods are the unit of every figure, but day 1 works for most people.

**Independent Test**: With start day 25 since always, on 2 Oct the period shown is 25 Sep – 24 Oct and counts expenses dated 25 Sep onward. Change the start day to 1 online on 10 Oct (applies from 1 Nov): the period shown stays 25 Sep – 31 Oct, and the screen says "From 1 Nov, periods start on day 1".

**Acceptance Scenarios**:

1. **Given** start day d and no pending change, **When** the current period is computed on the phone, **Then** it starts on the latest day d on or before today and ends the day before the next day d, as the server computes it.
2. **Given** a start day change whose "applies from" date is still ahead, **When** shown, **Then** the current period is the one the server last reported (kept from the last progress read) and the budgets screen says "From <date>, periods start on day <n>".
3. **Given** a pending change and no stored server period for today, **When** shown, **Then** each budget shows its limit with "Progress appears after sync" and no figures.
4. **Given** the "applies from" date is reached, **When** the day turns, **Then** the new periods apply without any action.
5. **Given** a start day changed on this phone and not yet synced, **When** budgets are shown, **Then** periods keep following the server's start day until the server answers.
6. **Given** midnight in the profile's time zone at a period's end, **When** the app is open, **Then** the budgets move to the new period without a restart.

---

### User Story 6 - Budgets on every phone (Priority: P3)

Budgets are part of the person's own data: they reach every phone signed in to the account, survive
reinstalling, and leave the phone at sign-out. The update to this version reads the person's
budgets that the phone skipped until now.

**Why this priority**: Required for completeness, but reuses the sync engine and feeds (specs 009, 011).

**Independent Test**: Create a budget through the API; on a phone updated from the previous version, the first sync reads the person's changes from the start and the budget appears.

**Acceptance Scenarios**:

1. **Given** a phone updated from the previous version, **When** it first syncs, **Then** it reads the person's own changes from the start once, so budgets created earlier appear; data already on the phone stays shown meanwhile.
2. **Given** a budget changed, archived or deleted on another phone, **When** this phone reads the person's changes, **Then** the change appears (a deletion removes the budget unless a change to it waits here).
3. **Given** budget changes waiting with category or transaction changes, **When** the phone syncs, **Then** they travel in the same batch upload; a budget naming a category created on this phone and not yet accepted waits for it.
4. **Given** sign-out, **When** it completes, **Then** every budget, its stored progress and waiting changes are removed from the phone.

---

### Edge Cases

- **Period boundaries**: start day 1 → calendar months; start day 28 → 28th to 27th, including February; days in period 28–31 follow the calendar.
- **Today across time zones**: "today" (which period is current, days elapsed) uses the profile's time zone (the device's until the profile is known); a transaction belongs to a period by its calendar date only.
- **Day turns while the screen is open**: days elapsed, projection and, at a period's end, the period itself update without a restart.
- **Budget created offline**: progress is the phone's estimate until it syncs and the next progress read; the per-budget read is not attempted before the server has it.
- **Two budgets for the same category and currency** (created on two phones offline): both are shown and both count the same spending; nothing is merged or refused.
- **Archived or deleted category**: a budget keeps counting its category; its name shows as the category's current name, or "Category unavailable" when it cannot be resolved; an archived category is never offered for a new budget.
- **Category deletion**: a category named by any budget on the phone (waiting or synced) is in use on the phone and cannot be deleted from it; the server also keeps it in use while any budget, even a deleted one, names it (C5 then offers "Archive instead").
- **Limit edited after archiving**: allowed; an archived budget is never re-activated.
- **Past limit changes**: the current period always compares with the current limit (the server has no per-period limits).
- **Shared wallet**: only transactions the person recorded count; a partner re-categorising the person's transaction moves it with the category, as on the server. A transaction the person recorded in a wallet where they are now a viewer still counts.
- **Recurring**: occurrences never count; only the transactions they produce; a confirmation waiting on this phone counts for its rule's author, or for the person when the rule is not on the phone.
- **Process killed after an offline edit**: the change survives and is sent later.
- **Large data**: 50 budgets over 20,000 transactions in the current period show within 1 second from the phone's copy.
- **Limit at the top of the range** (2^53 − 1): accepted; the projection is omitted when it would overflow it.
- **Unknown values from a later server** (another period type, unknown members): tolerated; a budget with an unknown period type is shown read-only with its limit and "Shown in full on newer versions of the app", without figures.
- **Sign-out**: budgets and stored progress are removed with everything else.

## Requirements *(mandatory)*

### Functional Requirements

**Budgets**

- **FR-001**: The app MUST show budgets and their progress only from the phone's copy (P-04.1); new budgets MUST be created with a client-generated UUIDv7 and written locally first, then synced through the sync engine in the person's own (user) scope, with or without a connection.
- **FR-002**: A person with a verified address MUST be able to create a budget from the budgets screen (an "Add budget" action, and "Set a budget" on the empty screen). Every budget write by a person with an unverified address MUST show the verification prompt and queue nothing. No plan feature gates budgets.
- **FR-003**: The budget editor MUST offer: category (C5 expense choices — own active categories and active defaults — plus "All expenses" first), currency (the currencies of the person's wallets on the phone plus the profile's default currency, defaulting to the default currency), limit per period (an amount in the currency's minor units, 1 to 2^53 − 1, validated locally with the currency's fraction digits), and show the period as "Monthly, starting on day <n>" (read-only, the server's start day).
- **FR-004**: The editor MUST NOT offer a category (or "All expenses") that already has an active budget on the phone in the chosen currency; it MUST mark it "Already budgeted". Changing the currency MUST clear a chosen category that is already budgeted in the new currency. Budgets that overlap anyway MUST both be shown and both count.
- **FR-005**: Editing MUST allow only the limit; category, currency and period MUST be shown read-only with a one-line reason. A save MUST queue the whole budget with the version shown when the editor opened (P-04.4). An archived budget's limit MUST stay editable.
- **FR-006**: Archive MUST ask for confirmation, then move the budget to "Archived" at once and queue the archive with its version; there is no way back. Delete MUST ask for confirmation, then remove the budget at once and queue the deletion with its version; a budget never sent MUST be removed with nothing sent.
- **FR-007**: A clash MUST show "Changed on another device" with "Keep mine" (re-send the phone's change on the newer version) and "Use the other device's"; a budget deleted on another device MUST offer only "Accept the deletion". A refusal MUST show "Not saved" with copy chosen by code and pointer (`INVALID_ENUM_VALUE` at `/category_id` → "This category can't have a budget any more"; `OUT_OF_RANGE` / `AMOUNT_NOT_INTEGER` at `/amount_minor` → the limit; `INVALID_CURRENCY` at `/currency`; `IMMUTABLE_FIELD`; `EMAIL_NOT_VERIFIED`; `NOT_FOUND`), with "Try again" and "Discard".
- **FR-008**: Budget writes MUST carry only the members declared in `BudgetWrite` (`category_id` with an explicit null for all expenses, `period_type` `monthly`, `amount_minor`, `currency`, `version`) or `BudgetVersionOnly` (`version`) for delete and archive; read-only members MUST NOT be sent; unknown response members MUST be ignored.

**Periods**

- **FR-009**: The current Budget Period MUST be computed on the phone from the server's copy of the profile: with start day d and no pending change (the "applies from" date absent or not after today), it MUST start on the latest day d on or before today and end the day before the next day d; days in period and days elapsed (start through today, inclusive) follow. "Today" MUST be taken in the profile's time zone (the device's until the profile is known); until the phone holds the profile, the start day MUST be 1 with no pending change (the server's default without a profile).
- **FR-010**: While a start day change is pending (its "applies from" date after today), the current period MUST be the one stored from the last server progress read whose period contains today; without one, budgets MUST show their limit with "Progress appears after sync" and no figures. The budgets screen MUST state "From <date>, periods start on day <n>" while a change is pending. A start day changed on this phone MUST NOT affect periods until the server's copy of the profile carries it.
- **FR-011**: Budgets MUST show the current period only; the phone MUST NOT read progress for other periods.

**Progress**

- **FR-012**: The phone MUST compute each budget's current-period progress from the transactions it holds, with the server's rules (ADR-0019): sum the amounts of **expense** transactions, live (not deleted), recorded by the person (`created_by`; a confirmation waiting on this phone counts for its rule's author when the rule is on the phone, else for the person), in wallets on the phone where the person is a member (any role), dated within the period, in the budget's currency, in the budget's category (any category when the budget has none); changes waiting to sync MUST be included (a waiting edit replaces the stored values, a waiting deletion removes the row, a waiting create adds it). Other currencies with matching spending MUST be collected as a sorted list of codes, never summed.
- **FR-013**: From that sum the phone MUST derive: remaining = limit − spent (negative when over); over limit exactly when spent > limit; percentage = spent ÷ limit rounded to the nearest whole percent (half up); projected end = spent × days in period ÷ days elapsed, rounded up to the minor unit, for the current period only, absent when it would exceed 2^53 − 1. No floating-point arithmetic on amounts (P-03).
- **FR-014**: After each progress read (FR-018) the phone MUST store, per budget, the server's figures and period together with the phone's **synced-only** figures at that moment (FR-012 without waiting changes). The shown spent and other currencies MUST be the server's only while all hold: (a) the stored period is the current one, (b) no waiting transaction change (or confirmation waiting on the phone) matches the budget on either its old or new values, (c) the phone's synced-only figures still equal those stored, and (d) the budget's limit equals the stored limit; otherwise the phone's own figures (with waiting changes) MUST be shown. Remaining, over limit, percentage and projection are always derived on the phone from the shown spent and the budget's limit (FR-013). A budget for which (b) fails MUST say "Includes changes waiting to sync".
- **FR-015**: Progress MUST be shown with the design system's budget progress element (name, percentage, bar, "spent of limit · left" or "over"), plus "On pace for <amount>" when a projection exists and spent is above zero, and "Also spent in <codes> (not counted)" when other currencies exist. Presentation MUST be neutral: no alarm wording, warning icon or exclamation; state MUST never rely on colour alone (P-08.3); amounts render with the currency's digits, tabular, U+2212 for negatives (P-03).

**Where it shows**

- **FR-016**: The budgets screen MUST list active budgets (oldest first, as the server orders them), then an "Archived" section; it MUST state the current period's dates and the days left (end − today; "Last day of the period" on the end date), the pending start day change (FR-010), each budget's sync status mark, pull to refresh (offline → "Updated …" stays and "You're offline"; another failure → "Couldn't refresh", as on the other lists). Its empty state MUST explain budgets in one line and offer "Set a budget".
- **FR-017**: Home MUST show a "Budgets" card when at least one active budget exists: up to 3 active budgets (highest percentage first, then oldest), each with the budget progress element (and "Waiting to sync" when its figures include a waiting change; "Progress appears after sync" with the limit when the period is unknown, those listed after the others), and "See all" opening the budgets screen; Home MUST also offer a "Budgets" row to the screen. Opening a budget from either MUST open its editor.

**Sync and housekeeping**

- **FR-018**: The phone MUST read `GET /budget-progress` (every page, today's period, no `date`) after the sync reads (the person's own changes and their wallets) on sign-in, app foreground, opening the budgets screen (at most once a minute) and pull to refresh (forced), and `GET /budgets/{id}/progress` when a budget the server holds is opened (at most once a minute per budget; also for archived budgets). A progress read MUST never block or replace a local change; offline it MUST keep the stored figures. A 404 on a per-budget read MUST be ignored (the feed decides). `INVALID_CURSOR` on a later page MUST restart the walk once.
- **FR-019**: Budgets MUST be read from the user feed (`budget` rows) with the version guard, deletion rule and sweep of spec 009, and uploaded as batch items (`budget` upsert with the `BudgetWrite` body, `budget` delete with the version; never a `wallet_id`); archive MUST go per record (`POST /budgets/{id}/archive`), after a whole save when both wait. A budget naming a category created on this phone and not yet accepted MUST be held (spec 009 FR-022).
- **FR-020**: On update to this version the phone MUST forget the user scope's feed position once, so the person's own changes are read from the start and budgets skipped before appear; data already on the phone stays shown meanwhile; wallet scopes keep their positions.
- **FR-021**: Categories named by any budget on the phone (synced or waiting) MUST count as in use on the phone (C5 `PendingCategoryUses`).
- **FR-022**: Sign-out MUST remove all budgets and stored progress; deleting a budget MUST remove its stored progress.
- **FR-023**: Every failure MUST be handled by `code` and pointer (P-02.4); retries MUST follow the catalogue (P-02.6).
- **FR-024**: Logs MUST NOT contain amounts, limits, spending, projections or payloads (P-09.2); they may contain record types, ids, outcomes, codes and request ids.
- **FR-025**: All new text MUST exist in English and Arabic with Western digits, mirrored correctly in RTL (the bar fills from the right), usable at 200% font scale (P-06, P-08); UI words MUST follow the glossary ("Budget" / "ميزانية", "Budget period" / "فترة الميزانية") and MUST NOT use "envelope" or "plan" for a budget.
- **FR-026**: No notification of any kind about budgets (B16 owns overrun notifications).

### Key Entities *(include if feature involves data)*

- **Budget**: user-scoped, client-created; category (or none = all expenses), period type (monthly), limit (minor units), currency, archived time; version and feed position; read-only owner.
- **Budget Progress (stored copy)**: per budget, the server's last figures for one period (period start/end, days in period, spent, limit, other currencies, when read) and the phone's synced-only figures at that moment; a phone-side cache, never synced.
- **Budget Period** (derived): start and end dates, days in period, days elapsed; from the profile's start day and "applies from" date, or the stored server period while a change is pending.
- **Transaction** (C6), **Category** (C5), **Profile** (spec 002), **Wallet membership** (C4): read only.

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Lets a person cap their own spending per category (or overall) per Budget Period in one currency,
and see — offline, instantly, and equal to the server after sync — how much is spent, left, the
share spent and the pace, on Home and on a budgets screen, without shaming.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Budget** ("ميزانية"; never "envelope", "plan"),
**Budget Period** ("فترة الميزانية"), **Budget Progress**, **budget start day**, **Category**,
**Transaction**, **Wallet**, **expense**. UI words: "Budgets" / "الميزانيات", "All expenses" /
"كل المصروفات", "Limit" / "الحد", "Spent" / "المصروف", "Left" / "المتبقي", "Over" / "تجاوز",
"On pace for" / "بهذا المعدل ستصل إلى", "Archived" / "مؤرشفة", "Already budgeted" / "له ميزانية
بالفعل", "Includes changes waiting to sync", "Progress appears after sync". Arabic wording is an
assumed default for owner review.

### 3. Data model

Adds two phone-side tables: **budget** (every member of `Budget`: id, category id or null,
period type, amount, currency, archived time, version, change sequence, created/updated times;
plus the phone's `account_id` and `refreshed_at`; a tombstone removes the row unless a change to
it waits, as in spec 009) and **budget_progress** (per budget: the server's last figures and
period, and the phone's synced-only figures when they were read; never synced). Both are cleared at
sign-out. Unsent changes use the spec 004 queue with record type `budget` in the user scope. The
on-device database goes from version 8 to 9 with a migration that keeps all data, creates the two
tables and clears the user scope's feed position, last-read stamp and integer position once
(FR-020); the schema is exported.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Budgets and progress render only from the phone's copy | P-04.1, FR-001 | store/service tests over fakes; no API in UI/store |
| Create, limit edit, archive and delete work offline and queue in the user scope with the base version | P-04.2, P-04.4, FR-001, FR-005, FR-006 | service tests with the network off; queue contents asserted |
| Category, currency and period are never changed after creation | FR-005, backend immutability | editor reducer tests; handler request test (stored values sent) |
| Phone spending equals the server's rules: own expenses only, live, in period, currency and category, member wallets, waiting changes overlaid; other currencies named, never summed | ADR-0019, FR-012 | table-driven calculator tests; captured-answer comparison against live `GET /budget-progress` |
| Remaining, over limit, percentage and projection are integer arithmetic with the server's rounding; projection absent above 2^53 − 1 | P-03, FR-013 | calculator tests (EGP, KWD, JPY, overflow) |
| Server figures are shown only while the period is current, no waiting change matches, the synced-only figures and the limit are unchanged since the read; accepting a waiting change never makes the figure jump back | FR-014 | progress selection and offline-to-server sequence tests |
| Current period equals the server's for every start day 1–28, month lengths and the pending-change case | FR-009, FR-010 | period tests vs captured server periods |
| Writes carry only declared members with an explicit null category; archive never batched; no `wallet_id` | P-02.2, FR-008, FR-019 | contract request-shape tests; handler batch tests |
| Failures branch on code and pointer only | P-02.4, FR-007, FR-023 | refusal-mapping tests |
| Migration 8 → 9 keeps data and forgets only the user scope's position | FR-020 | migration test 8 → 9 |
| Sign-out removes budgets and stored progress | FR-022, P-09 | sign-out wipe test |
| A category named by a budget on the phone is in use on the phone | FR-021 | category-uses test; `AppGraphTest` union |
| No amounts or payloads in logs | P-09.2, FR-024 | log-capture test |
| No percentage is ever read from the server; state never by colour alone | FR-013, FR-015, P-08.3 | DTO test (no member); screenshot tests show the written percentage |

### 5. API

All under `/api/v1`, frozen in `budgets.yaml` v1.8 (backend spec 015, `bb7e61b`; unchanged at `64ca1ea`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Create / save (sync engine, batch item `budget` upsert) | `PUT /budgets/{id}` | `BudgetWrite`: `category_id` (explicit null = all expenses), `period_type: monthly`, `amount_minor`, `currency`, `version` (0 to create) | 200/201; `409 VERSION_CONFLICT` (+`current`, live or tombstone); `422 VALIDATION_FAILED` (`INVALID_ENUM_VALUE` at `/category_id`, `IMMUTABLE_FIELD`, `OUT_OF_RANGE`, `AMOUNT_NOT_INTEGER`, `INVALID_CURRENCY`, `REQUIRED`, `UNKNOWN_FIELD`, `INVALID_TYPE`); `422 INVALID_ID`; `403 EMAIL_NOT_VERIFIED`; `404 NOT_FOUND`; retryable per catalogue |
| Delete (sync engine, batch item `budget` delete) | `DELETE /budgets/{id}` with a JSON body | `BudgetVersionOnly` | 200 (tombstone; also already deleted); 409 (+`current`); 403; 404 |
| Archive (sync engine, per record only) | `POST /budgets/{id}/archive` | `BudgetVersionOnly` | 200 (also already archived); 409 (+`current`, stale or tombstone); 403; 404 |
| Progress of active budgets, today's period | `GET /budget-progress` (no `date`; `limit=200`; every page) | — | 200 (`data: []` when none); `400 INVALID_CURSOR` (restart once); 401; 426; offline/timeout/5xx keep the stored figures |
| Progress of one budget (archived included), today's period | `GET /budgets/{id}/progress` (no `date`) | — | 200; 404 (ignored; the feed decides); offline/timeout/5xx keep the stored figures |
| Reads | `GET /sync/user/changes` (spec 009) | — | `budget` rows now applied |

Not used: `GET /budgets`, `GET /budgets/{id}` (the user feed carries budgets, spec 009 decision)
and `PATCH /budgets/{id}` (a limit edit is a whole save, which the batch carries). The `date`
parameter is never sent (FR-011).

#### Contract changes since last spec

Baseline `saveme-backend@192e3ab` (spec 011) → `64ca1ea`:

- **Reports convention** (`docs/api-conventions.md` "Reports", spec 019 / ADR-0022): reports are computed on read, Money only, no percentage, range by `date` or `from`/`to`. Follow-up: not in scope of this spec (C13 reports); it confirms the same "derive the share on the client" rule FR-013 applies to budgets.
- Error catalogue: no change; the client's codes match the catalogue (no client mismatch).

### 6. Authorization and entitlements

A budget is personal: only its owner reads or writes it, and another person's id is 404. No wallet
role is involved; spending counts from every wallet the person belongs to, whatever their role.
Every write needs a verified address (`EMAIL_NOT_VERIFIED`). No entitlement or plan gate (backend
spec 015 default 18).

### 7. Events

Not applicable: the client publishes no domain events and shows no notifications (FR-026; C16).
It consumes the sync engine's queue and status, the user feed, C5's choices, displays and
category-use hook, C6's transactions with their waiting changes, C4's memberships, C7's rule
authors, and spec 002's profile (start day, "applies from", time zone).

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 Set | Unit (editor reducer/store, choices, duplicate rule, service create, handler PUT, applier) + contract tests + E2E | tests; `.maestro/012-budgets/US1-*` (deferred) |
| US2 See | Unit (calculator, list and Home stores) + screenshots (EN LTR, AR RTL, 2.0) + E2E | tests; `US2-*` (deferred) |
| US3 Offline → server | Unit (overlay, selection rule, carry-free switch) + captured live answers + E2E | tests; `US3-*` (deferred) |
| US4 Change | Unit (limit save, archive, delete, clash and refusal choices) + contract + E2E | tests; `US4-*` (deferred) |
| US5 Periods | Unit (period table, pending change, midnight ticker) + live QA | tests; `US5-*` (deferred) |
| US6 Every phone | Unit (migration 8 → 9, applier, sweep, batch, sign-out) + live QA | tests; `US6-*` (deferred) |

Owner policy: no emulator, Maestro or device run; flows are written and marked "deferred: E2E on
owner's device". Host tests plus a live-backend contract QA (curl against the local backend,
real answers captured as fixtures replayed through the real client, engine, handler, applier and
calculator), as specs 007–011.

### 9. Open questions

- **Backend contract gaps (non-blocking)**: (1) the start day history is server-internal, so the phone cannot compute a period before the profile's "applies from" date — while a change is pending it relies on the last server period (FR-010); (2) `GET /budget-progress` omits archived budgets, so their server figures need one read each (FR-018); (3) a budget not yet on the server has no server progress (404), so it shows the phone's estimate until it syncs; (4) archive is not a batch operation (per record, FR-019); (5) the profile's `budget_period_start_day_from` for a start day change made on this phone is known only after the server answers (FR-010).
- **Arabic words** listed in §2: assumed defaults for owner review. Non-blocking.
- **Overrun notifications**: C16 / B16. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person can set a monthly budget in under 30 seconds, online or offline, and sees its progress on the budgets screen as soon as it is saved.
- **SC-002**: After recording, editing or deleting an expense, the matching budgets show the new figures within 1 second, with no connection.
- **SC-003**: After a sync, the shown spent, remaining, over-limit, projection and other currencies equal the server's progress for 100% of budgets in the live QA set (category, all expenses, other currency, shared wallet with a partner's spending, transfer, income, deleted, recurring, period edges).
- **SC-004**: The phone's current period equals the server's for 100% of the test table (start days 1, 15, 25, 28 across 28–31-day months, and a pending start day change).
- **SC-005**: 100% of budget changes made offline reach the server, or are shown as needing the person's decision, once a connection returns.
- **SC-006**: Every active budget is within one tap of Home (the card or "See all").
- **SC-007**: The brief's integration checkpoint passes: a Groceries budget, an expense recorded offline moves its progress at once, and the figures match the server after sync.
- **SC-008**: 50 budgets over 20,000 transactions in the current period are shown within 1 second from the phone's copy.

## Assumptions

- Backend B8 is built (`bb7e61b`); `budgets.yaml` v1.8 is unchanged at `64ca1ea`.
- Budgets reach the phone only through the user feed (spec 009); the list and single-record budget reads are not used.
- The phone already holds every transaction of every wallet the person belongs to (C6 + C14 wave 2), which is what makes the server's figure reproducible offline (backend ADR-0019).
- The budgets screen is reached from a Home "Budgets" row and the Home "Budgets" card; there is no per-wallet budget summary.
- The board's `BudgetProgress` colours (positive, pending from 80%, over above 100%) are kept as the design board defines them; the percentage and words are always written.
- No new dependency; no change to the sync engine is expected.
- Shared-wallet scenarios need a second member, seeded through the API (sharing exists on the backend; C9 not built on the phone).
