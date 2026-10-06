# Feature Specification: Reports (client)

**Feature Branch**: `014-reports`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "specs/briefs/C13-reports.md" — client brief C13, wave 3. Pairs with backend B13 → spec 019 (built, committed `d7ed413`; `reports.yaml` v1.11, frozen for C13 and unchanged since).

**Backend contract**: `saveme-backend@b63e917`

**Sources**:
- API contract: `saveme-backend/docs/openapi/paths/reports.yaml` (v1.11: `GET /wallets/{id}/reports/{summary,by-category,trend}` and `GET /reports/{summary,by-category,trend}`; parameters `date`, `from`/`to` (inclusive, at most 366 days), `months` (1–12, default 6), `include_planned`, `currency` (cross-wallet, uppercase ISO 4217); no new error code; not syncable), `docs/api-conventions.md` ("Reports"), `docs/openapi/error-catalog.yaml`.
- Backend behaviour: `saveme-backend/specs/019-reports/summary.md`, `specs/019-reports/contracts/reports-api.md` ("What C13 binds to") and `docs/adr/0022-reports-are-wallet-views-computed-on-read.md`: a wallet report counts every member's live transactions in that wallet; a cross-wallet report covers the live wallets the caller belongs to now (any role, archived included) in one currency, other currencies listed in `meta.excluded_wallets`, never converted, and a transfer between two covered wallets appears nowhere; transfers are never income or expense; `balance_start`/`balance_end` = opening balance + postings dated before `from` / through `to`; the default Report Range is the caller's Budget Period containing `date` (default today in the profile time zone); trend buckets are consecutive Budget Periods labelled by their start date; planned figures come only from already generated scheduled occurrences (each rule's today + 35 days) and never enter actual figures; `label` is a machine key (platform `system_key`, category id, or period start date) and no share or percentage is sent; nothing is gated (`reports.advanced` stays reserved).
- Glossary (`saveme-backend/docs/glossary.md`): **Report**, **Report Range** (covered wallet, excluded wallet), **Budget Period**, **Wallet**, **Transaction** (income, expense, transfer), **Category** (platform category, `system_key`), **Category Reference**, **Occurrence** (scheduled), **Profile** (time zone, default currency, budget start day).
- Builds on spec 002 (profile: time zone, default currency, budget start day and its "applies from" date), 004 (sync engine: queue and waiting changes), 005 (C3 entitlements), 006 (C4 wallets: memberships, opening balance, archived and liability wallets), 007 (C5 categories: displays for own, platform and partners' categories), 008 (C6 transactions: the phone's copy of every transaction of every wallet the person belongs to, with waiting changes), 009 and 011 (C14 feeds and read stamps), 010 (C7 recurring: occurrences on the phone), 012 (C8 budgets: Budget Periods from the server's profile, `DayTicker`) and 013 (C9 sharing: losing a wallet purges it from the phone).

## Clarifications

### Session 2026-10-06

The owner was away with delegated authority; each answer is the recommended, most conservative
option, recorded as an assumed default.

- Q: Where do the figures a person sees come from: the server's report endpoints (with an offline copy of the last answer) or the phone's own computation from the transactions it already holds? → A: **The phone computes every figure itself** from its copy of the covered wallets' transactions, occurrences and opening balances, applying the server's rules (ADR-0022) with changes waiting to sync included. Server report figures are never shown. The phone holds exactly the wallets a cross-wallet report covers and every transaction in them, so after a sync its figures equal the server's; it works offline, for any range, at once, and agrees with the budgets screen and the wallet balance. Opening a report asks for a sync read of the covered wallets (at most once a minute; pull to refresh forces it); offline the figures stay with "Updated <time>" (the last successful read). The server is read at run time only to learn Budget Period dates the phone cannot derive (a start day change in the past or pending). Parity is proven by host tests that load live captured feeds into the phone and compare its figures with the server's captured answers for the same data, and, in the development build only, by a parity probe that compares the phone's synced-only figures with the server's after each read and logs which figure differs (never an amount). (FR-001, FR-002, FR-019, FR-020, FR-028)
- Q: Where do reports appear and how many screens are there? → A: Two screens. **Reports** (one per scope: all wallets, from a Home "Reports" row; one wallet, from a "Report" action on the wallet detail) shows the summary, the top expense categories with their shares, and the trend; **Spending by category** shows every category of the report's expenses or income. No Home card; no report on the budgets screen. (FR-021, FR-022)
- Q: Which ranges can a person choose? → A: The **Budget Period** (default: the current one) with previous and next steps — back as far as wanted, forward up to the period containing today + 35 days (where scheduled money ends) — or a **custom range** of up to 366 days, both ends inclusive. The trend shows 3, 6 (default) or 12 consecutive Budget Periods ending with the period that contains the range's last day. The choice lasts while the app runs and is not stored. (FR-008, FR-009, FR-010)
- Q: How is scheduled (planned) money shown? → A: A "Include scheduled" switch, **off by default**; when on, scheduled money in the range is shown **beside** the actual figures ("Scheduled: …"), never added to them or to the balances, per category and per trend period as a separate outlined bar. The switch lasts while the app runs. (FR-016)
- Q: How does a cross-wallet report handle several currencies? → A: One currency per report: the profile's default currency first (EGP when unknown), with a currency choice listing the currencies of the person's wallets on the phone. Wallets in other currencies are named in a notice ("Not included: Savings (USD)") with a one-tap switch to that currency's report; nothing is converted or summed across currencies. A wallet report is always in its wallet's currency. (FR-012, FR-013)

## User Scenarios & Testing *(mandatory)*

People record every transaction but cannot see where their money went. A report answers it for a
Budget Period (or a chosen range): how much came in, went out, moved between wallets, how the
balance changed, which categories took the most, and how this period compares with the ones before.
The figures are the same the server would compute, offline too, and every member of a shared wallet
sees the same figures for it.

### User Story 1 - See where my money went this period (Priority: P1)

From Home, a person opens Reports and sees, for the current Budget Period across their wallets in
their default currency: income, expenses, net, transfers in and out, the balance at the start and the
end of the period, and the expense categories ranked with each one's share.

**Why this priority**: It is the brief's integration checkpoint ("seeded transactions produce the expected summary and category shares") and the core value of the feature.

**Independent Test**: With three EGP wallets holding 10,000 EGP of salary and 3,500 EGP groceries, 1,200 EGP transport and 800 EGP of an own category this period, Reports shows income 10,000, expenses 5,500, net 4,500, and Groceries 64%, Transport 22%, the own category 15%, in that order.

**Acceptance Scenarios**:

1. **Given** a person with wallets on the phone, **When** they tap "Reports" on Home, **Then** the Reports screen opens for all their wallets in the profile's default currency and the current Budget Period, showing the period's dates ("1 Oct – 31 Oct").
2. **Given** the covered wallets, **When** the summary is shown, **Then** it shows income, expenses, net (income − expenses), transfers in, transfers out, balance at the start and balance at the end, each in the report currency with its digits, and `balance at the end = balance at the start + income − expenses + transfers in − transfers out` holds.
3. **Given** a transfer between two of the person's wallets in the report currency, **When** the cross-wallet summary is shown, **Then** the transfer is in neither transfers in nor transfers out, and the balances are unchanged by it; a transfer to or from a wallet not covered counts in its direction.
4. **Given** expenses in several categories, **When** the "Spending by category" section is shown, **Then** the top 5 expense categories are listed by amount (largest first, ties by their key), each with its localised name, icon, amount and share of total expenses as a whole percent, plus "See all" when there are more; the shares are computed on the phone.
5. **Given** a platform category, **When** shown, **Then** its name is the app's name for it in the current language (Arabic or English); a person's own category shows its name; a partner's category in a shared wallet shows the name from the wallet's category references; an unresolvable category shows "Category unavailable".
6. **Given** no transaction in the range, **When** the report is shown, **Then** every figure is zero, the category section says "No spending in this period", and the balances still show.
7. **Given** "See all", **When** tapped, **Then** the Spending by category screen lists every expense category with amount and share, and an "Income" choice lists the income categories the same way.

---

### User Story 2 - The report works offline and agrees with the server (Priority: P1)

The person records an expense offline and opens Reports: the expense is already counted. After the
phone syncs, every figure equals what the server computes for the same wallets and range.

**Why this priority**: The offline-first promise (P-04) and the brief's second checkpoint ("offline shows the report with its 'Updated …' time").

**Independent Test**: Offline, record a 200 EGP Groceries expense; Reports shows expenses and Groceries 200 EGP higher at once with "Includes changes waiting to sync" and "You're offline · Updated 10:42". Go online and sync: the note goes, nothing jumps, and the summary, categories and trend equal `GET /reports/*` for the same range.

**Acceptance Scenarios**:

1. **Given** no connection, **When** the person opens Reports, **Then** the report is computed from the phone's copy and shown at once, with "You're offline · Updated <time>" where the time is the last successful read of the covered wallets.
2. **Given** a transaction created, edited or deleted on the phone and not yet synced, **When** a report covering it is shown, **Then** the figures include the change and the screen says "Includes changes waiting to sync"; when the change is accepted the note goes and the figures do not change.
3. **Given** a connection, **When** the Reports screen opens, **Then** the phone reads the latest changes of the covered wallets (at most once a minute) and the figures update by themselves; pull to refresh forces the read.
4. **Given** a sync that completed, **When** the figures are compared with the server's report for the same wallets, currency and range, **Then** every figure (summary, every category item and its order, every trend point) is equal.
5. **Given** a partner's expense in a shared wallet that the phone has not read yet, **When** the read completes, **Then** the figures include it.
6. **Given** a refresh that fails for a reason other than being offline, **When** shown, **Then** "Couldn't refresh" appears with the last "Updated <time>" and the figures stay.

---

### User Story 3 - One wallet's report (Priority: P1)

From a wallet's detail, a person opens that wallet's report: the same sections for the wallet alone,
counting every member's transactions, so every member sees the same figures.

**Why this priority**: A shared household wallet or a credit card is often what people want to review; the backend's wallet reports exist for it.

**Independent Test**: In a shared wallet where the person spent 1,000 EGP and a partner 500 EGP this period, the wallet's report shows expenses 1,500 EGP; the partner's phone shows the same.

**Acceptance Scenarios**:

1. **Given** a wallet detail, **When** the person taps "Report", **Then** the Reports screen opens for that wallet alone in its currency, titled with the wallet's name, without a currency choice or excluded-wallets notice.
2. **Given** a shared wallet, **When** its report is shown, **Then** every member's live transactions count (not only the person's), unlike budgets.
3. **Given** a transfer out of the wallet to any other wallet, **When** shown, **Then** it is in transfers out (and a transfer in, in transfers in), never in income or expenses.
4. **Given** a liability wallet (credit card), **When** its balances are negative, **Then** they show as amounts owed ("Owed 4,200 EGP"), as the wallet screens show them.
5. **Given** an archived wallet, **When** its report is opened, **Then** it shows like any other; a viewer member sees the same report as the owner.
6. **Given** the wallet is purged from the phone (the person left or lost access), **When** its report is open, **Then** the screen closes as the wallet detail does.

---

### User Story 4 - Compare with earlier periods (Priority: P2)

The person sees a trend: income and expenses for the last six Budget Periods as bars, with net per
period, and a table of the same numbers.

**Why this priority**: Trends turn a single period into a habit check; the brief asks for six months.

**Independent Test**: With income of 10,000 in both September and October, expenses of 12,000 in September and 3,500 in October, and nothing in August, the 3-period trend shows Aug 0/0, Sep 10,000/12,000 (net −2,000), Oct 10,000/3,500 (net 6,500), oldest first.

**Acceptance Scenarios**:

1. **Given** a report, **When** the trend is shown, **Then** it covers 6 consecutive Budget Periods ending with the period containing the range's last day, oldest first in reading direction, each with income, expenses and net; empty periods show zero.
2. **Given** the trend choice, **When** the person picks 3, 6 or 12 periods, **Then** the trend changes accordingly.
3. **Given** the trend chart, **When** read by a screen reader, **Then** a text summary is announced and a table below the chart lists each period's dates, income, expenses and net.
4. **Given** Arabic, **When** shown, **Then** the chart mirrors (oldest at the right edge), digits are Western, and amounts read left to right inside the right-to-left layout.
5. **Given** transfers, **When** the trend is computed, **Then** they are not in it.

---

### User Story 5 - Choose the period or a custom range (Priority: P2)

The person steps back to earlier Budget Periods, forward to the next one, or picks a custom range of
up to a year; Budget Periods follow their start day, as on the budgets screen.

**Why this priority**: Reviewing last month is the second most common question; start days other than 1 must agree with budgets.

**Independent Test**: With start day 25, on 2 Oct the default range is 25 Sep – 24 Oct; "Previous" shows 25 Aug – 24 Sep. A custom range 1 Jan – 1 Jan next year (366 days) is accepted; one more day is refused.

**Acceptance Scenarios**:

1. **Given** the Reports screen, **When** the person taps "Previous" or "Next", **Then** the range becomes the Budget Period before or after the shown one; "Next" stops at the period containing today + 35 days.
2. **Given** a custom range, **When** the person picks a start and an end, **Then** the range is accepted when start ≤ end and it spans at most 366 days inclusive; otherwise the picker says "Pick up to 366 days, start before end" and the range does not change.
3. **Given** a start day d without a past or pending change, **When** periods are derived, **Then** each starts on day d and ends the day before the next day d, as on the budgets screen and the server.
4. **Given** a start day change whose "applies from" date is after a period's dates (a past change, or a pending one), **When** that period is needed, **Then** the phone uses the dates the server reports for it; offline without them, the section says "Shown when you're online" instead of figures, and no guessed dates are used.
5. **Given** midnight in the profile's time zone, **When** the app is open, **Then** "today", the current period and the forward limit update without a restart.

---

### User Story 6 - Several currencies (Priority: P2)

A person with EGP wallets and a USD savings wallet sees one EGP report, with a notice that the USD
wallet is not included and a way to see the USD report.

**Why this priority**: Cross-currency totals would be wrong money (P-03); the brief asks to show excluded wallets instead of silently dropping them.

**Independent Test**: With three EGP wallets and one USD wallet, Reports in EGP covers the three and says "Not included: Savings (USD)"; tapping "See USD report" shows the USD wallet's figures and names the EGP wallets as not included.

**Acceptance Scenarios**:

1. **Given** wallets in several currencies, **When** Reports opens, **Then** it is in the profile's default currency (EGP when unknown) and offers a currency choice listing every currency of the person's wallets on the phone.
2. **Given** wallets in other currencies, **When** a cross-wallet report is shown, **Then** a notice names each excluded wallet with its currency, and each other currency has a "See <code> report" action.
3. **Given** a currency with no wallet (the profile default with no wallet in it), **When** shown, **Then** every figure is zero and every wallet is named as not included.
4. **Given** any figure, **When** shown, **Then** it never adds amounts of two currencies and nothing is converted.

---

### User Story 7 - Scheduled money (Priority: P3)

The person turns on "Include scheduled" to see what their recurring rules still plan in the range,
beside what already happened.

**Why this priority**: Useful for looking ahead, but optional; the actual figures stand alone.

**Independent Test**: With a 5,000 EGP rent rule scheduled on 28 Oct and nothing recorded, the October report with "Include scheduled" on shows expenses 0 and "Scheduled: 5,000 EGP", Rent with 0 and "Scheduled 5,000", and the trend's October bar with an outlined scheduled part; the balance at the end is unchanged.

**Acceptance Scenarios**:

1. **Given** "Include scheduled" on, **When** a report is shown, **Then** scheduled income, expenses, transfers in and out of the covered wallets dated in the range (occurrences not yet confirmed or skipped, missed and due ones included) are shown separately, in the report currency only.
2. **Given** scheduled money, **When** shown, **Then** it is never added to the actual figures, shares or balances.
3. **Given** an occurrence confirmed or skipped on this phone and not yet synced, **When** shown, **Then** it is no longer scheduled (a confirmation counts as an actual transaction).
4. **Given** the switch off, **When** shown, **Then** no scheduled figure appears anywhere.
5. **Given** dates beyond today + 35 days, **When** the switch is on, **Then** the screen says "Scheduled money is known up to <date>".

---

### Edge Cases

- **Opening balance**: counted in every range's balances (it has no date); a waiting change to it on this phone counts at once.
- **Transfer between covered wallets** (cross-wallet): appears nowhere; between a covered wallet and an uncovered one (another currency is impossible in the MVP, so a wallet the person is not in, or a deleted wallet): counts in its direction.
- **Deleted transaction**: never counts; a waiting deletion removes it at once.
- **Transaction dated in the future**: counts in the range containing its date; the balance at the end of the current period includes it.
- **Liability wallet**: negative balances render as an amount owed in a wallet report; a cross-wallet balance that is negative renders with U+2212 (it is a net amount, not one wallet's debt).
- **Archived wallet**: covered and reported like any other; an **archived or retired category** is reported like any other.
- **Left or purged wallet**: gone from the phone, so it drops out of every cross-wallet range, past ones too, exactly as on the server (memberships now, not then).
- **Wallet joined today**: counts for past ranges too, as on the server.
- **Wallet created on this phone and not yet synced**: covered at once (it is on the phone) with its opening balance and transactions; the server's figures include it after it syncs, so parity is compared only without waiting changes (FR-028).
- **Category kinds**: a category has one kind, so it appears only in the expense or the income group; a transaction whose category the phone cannot resolve still counts under its id and shows "Category unavailable".
- **Shares**: total zero → no shares; a share is `value × 100 ÷ total` rounded half up; a non-zero item under 0.5% shows "<1%"; shares need not add up to 100 and the screen never forces them to.
- **Very large amounts**: sums are `Long`; a sum beyond 2^53 − 1 cannot happen with the server's per-transaction limit over realistic counts, but the phone uses checked addition and shows "Too large to show" instead of a wrong number if it ever overflows.
- **Three-decimal and zero-decimal currencies** (KWD, JPY): digits from the currency.
- **Range at the 366-day limit**: inclusive; 367 days refused in the picker.
- **Trend with a transitional period** (after a start day change): one period may be shorter or longer than a month; the server's dates are used.
- **Day turns while open**: today, current period, forward limit and "Scheduled money is known up to" update.
- **Process killed while offline**: nothing is lost; a report is never stored, it is recomputed.
- **Large data**: 20,000 transactions in the range across 20 wallets with a 12-period trend show within 1 second from the phone's copy.
- **Unknown values from a later server** (a transaction kind or wallet type this app does not know): ignored in every figure, as balances ignore them (spec 008).
- **Sign-out**: nothing to remove; reports keep nothing on the phone beyond the session's learned period dates, which are dropped.

## Requirements *(mandatory)*

### Functional Requirements

**Source of figures**

- **FR-001**: Every figure MUST be computed on the phone from its copy of the covered wallets (opening balances), their transactions and their occurrences, with every change waiting to sync overlaid as the transaction list shows it (a waiting edit replaces the stored values, a waiting deletion removes the row, a waiting create adds it, a refused or clashing change overlays too, a confirmation waiting on the phone is an actual transaction and no longer a scheduled occurrence). Server report figures MUST NOT be shown.
- **FR-002**: The screen MUST say "Includes changes waiting to sync" when any of these waits: a transaction change touching a covered wallet (on either its stored or its shown values) dated in the range or before its end (balances); a change to a covered wallet itself (create, opening balance edit, archive, delete); or, with "Include scheduled" on, an occurrence change in a covered wallet dated in the range. Accepting the change MUST NOT change the shown figures.
- **FR-003**: A **wallet report** MUST count every member's live transactions in that wallet (not only the person's): income → income; expense → expenses; transfer out of the wallet → transfers out; transfer into the wallet → transfers in. A transaction belongs to the range by its calendar date alone.
- **FR-004**: A **cross-wallet report** MUST cover every wallet on the phone (the wallets the person belongs to: any role, archived included) whose currency is the report's currency, and MUST equal the sum of those wallets' own figures except that a transfer whose source and destination are both covered MUST be in neither transfers in nor transfers out.
- **FR-005**: `balance at the start` MUST be the covered wallets' opening balances plus the effect of every live transaction dated before the range's first day; `balance at the end` the same through its last day; `net` = income − expenses. The identity `end = start + income − expenses + transfers in − transfers out` MUST hold for every report; income, expenses, transfers in and out are never negative.
- **FR-006**: **By category** MUST group income and expense transactions separately: a platform category by its `system_key`, any other category by its id (never merged by name); each item carries its key, the category id, the system key or none, and its sum; items with a zero sum (and no scheduled sum when scheduled is on) MUST be left out; items MUST be sorted by sum descending then key ascending; each group's total MUST equal the sum of its items. Transfers appear in neither group.
- **FR-007**: **Shares** MUST be computed on the phone as `value × 100 ÷ total` rounded half up with integer arithmetic; a non-zero item rounding to 0 MUST show "<1%"; no share when the total is zero. No floating-point arithmetic on amounts (P-03).

**Ranges and periods**

- **FR-008**: The default range MUST be the Budget Period containing today, "today" in the profile's time zone (the device's until the profile is known). "Previous" and "Next" MUST move by one Budget Period; "Next" MUST stop at the period containing today + 35 days.
- **FR-009**: A custom range MUST have its first day on or before its last day, span at most 366 days inclusive, and lie within 1900-01-01 to 2999-12-31; the picker MUST refuse anything else with one sentence.
- **FR-010**: The trend MUST cover 3, 6 (default) or 12 consecutive Budget Periods, the last containing the range's last day, oldest first; each period carries income, expenses and net (zero when empty); transfers MUST NOT be in it.
- **FR-011**: Budget Periods MUST be derived on the phone from the server's copy of the profile exactly as budgets derive them (spec 012 FR-009): with start day d and no "applies from" date, every period starts on day d and ends the day before the next day d; with an "applies from" date F, periods starting on or after F are derived the same way. A period containing any date before F (a past or pending change) MUST use the dates the server reported for it (FR-019); without them, offline, the section MUST say "Shown when you're online" and show no figures. A start day changed on this phone MUST NOT affect periods until the server's copy of the profile carries it.
- **FR-012**: The range, trend length, currency and "Include scheduled" choices MUST last while the app runs (per scope) and MUST NOT be stored.

**Currencies**

- **FR-013**: A cross-wallet report MUST be in one currency: the profile's default currency (EGP when unknown) until the person picks another from the currencies of their wallets on the phone; a wallet report MUST be in its wallet's currency. Amounts in different currencies MUST NOT be added or converted.
- **FR-014**: A cross-wallet report MUST name every wallet on the phone in another currency ("Not included: <wallet> (<code>)"), sorted by currency then name, and offer "See <code> report" for each other currency; with no wallet in the report's currency, every figure is zero and every wallet is named.

**Display**

- **FR-015**: Labels MUST be localised on the phone: a platform category by the app's name for its system key in the current language, a person's category by its name (own categories, or the covered wallets' category references for partners'), "Category unavailable" when unresolvable; a period by its dates ("25 Sep – 24 Oct"), in the app language with Western digits.
- **FR-016**: With "Include scheduled" on (off by default), the phone MUST show, separately, the scheduled sums of the covered wallets' occurrences that are still scheduled (not confirmed or skipped, on the phone or waiting), dated in the range and in the report's currency: in the summary (income, expenses, transfers in and out, with the covered-transfer rule of FR-004), per category, and per trend period; never added to actual figures, shares or balances. The screen MUST say "Scheduled money is known up to <today + 35 days>" when the range ends after it.
- **FR-017**: Amounts MUST render with the currency's fraction digits, tabular, U+2212 for negatives (net, cross-wallet balances); a wallet report of a liability wallet MUST render negative balances as amounts owed; expenses in neutral ink, never the error colour (P-03).
- **FR-018**: Charts MUST be design-system components drawn without a new dependency, mirrored in RTL (category bars fill from the start edge; the trend's oldest period at the start edge), with a written value beside every bar, state never by colour alone (income and expenses also differ by legend words and the table), and a text alternative: each chart is announced as a summary, and the trend is followed by a table of every period's dates, income, expenses and net. Usable at 200% font scale without clipped text.

**Freshness and the server**

- **FR-019**: When a needed Budget Period cannot be derived (FR-011) and the phone is online, it MUST read `GET /reports/trend` (no `currency`, `include_planned` absent, `months` covering the needed periods, `date` in the last one) and keep the returned periods' dates (not its figures) in memory for the session, for the signed-in account only. A failure MUST leave "Shown when you're online".
- **FR-020**: Opening a report screen MUST ask the sync engine to read the person's own changes and every covered wallet's changes (at most once a minute per scope; pull to refresh forces it). The screen MUST show "You're offline · Updated <time>" offline and "Couldn't refresh · Updated <time>" on another failure, where the time is the oldest last successful read among the covered scopes; nothing when the read succeeds.

**Where it shows**

- **FR-021**: Home MUST offer a "Reports" row opening the cross-wallet Reports screen; the wallet detail MUST offer "Report" opening the wallet's Reports screen. Both are available to every role and every plan.
- **FR-022**: The Reports screen MUST show, in order: the scope and currency (cross-wallet: currency choice), the range with Previous/Next and "Custom range", the excluded-wallets notice (cross-wallet), the freshness and waiting notes, the summary, the top 5 expense categories with "See all", the trend with its 3/6/12 choice and table, and the "Include scheduled" switch. The Spending by category screen MUST list every item of the chosen group (Expenses default, Income) with amount, share and scheduled sum, for the same scope, range, currency and switch.
- **FR-023**: An empty report (no wallet at all) MUST say "Add a wallet to see reports" with an action to the wallets screen.

**Entitlements, privacy, quality**

- **FR-024**: No plan feature gates reports; `reports.advanced` MUST NOT be read by this feature (backend P-09 rule 5; the Plan screen's existing "Advanced reports" line is unchanged).
- **FR-025**: Logs MUST NOT contain amounts, figures, shares, category names or payloads (P-09.2); they may contain report kind, wallet ids, counts, outcomes, codes and request ids.
- **FR-026**: All new text MUST exist in English and Arabic with Western digits, mirrored in RTL, usable at 200% font scale (P-06, P-08); UI words MUST follow the glossary ("Report" / "تقرير", "Budget period" / "فترة الميزانية", "Transfer" / "تحويل", "Wallet" / "محفظة").
- **FR-027**: Every failure of a server read MUST be handled by `code` (P-02.4) and retried per the catalogue (P-02.6); `INVALID_QUERY_PARAMETER` on a period lookup MUST be logged as a client defect and leave "Shown when you're online".
- **FR-028**: In the development build only, after a successful read with no waiting change (transaction, occurrence or wallet) in the covered wallets, the phone MUST compare its synced-only figures with the server's report for the same scope, currency and range (all three reports) and log each differing member by name (never a value); release builds MUST NOT call the report figure endpoints except the period lookup of FR-019.
- **FR-029**: Reports MUST NOT store any figure on the phone and MUST add no table; sign-out MUST drop the session's learned period dates.

### Key Entities *(include if feature involves data)*

- **Report Range** (derived): first and last calendar date; a Budget Period (with its source: derived on the phone or reported by the server) or a custom range.
- **Report scope**: one wallet (its currency) or all wallets in one currency (covered wallets and excluded wallets).
- **Report summary** (computed): income, expenses, net, transfers in, transfers out, balance at the start, balance at the end, optional scheduled summary; plus whether a waiting change is included.
- **Category breakdown** (computed): per kind, a total, an optional scheduled total and items (key, category id, system key or none, sum, optional scheduled sum); shares derived for display.
- **Trend** (computed): consecutive Budget Periods with income, expenses, net and optional scheduled income and expenses per period.
- **Learned periods** (memory only): Budget Period dates the server reported, for the signed-in account and the session.
- **Wallet**, **Transaction**, **Occurrence**, **Category**, **Profile**: read only (C4, C6, C7, C5, spec 002).

## Project Sections *(mandatory for feature specs)*

### 1. Purpose

Shows a person where their money went in a Budget Period or a chosen range — per wallet or across
their wallets in one currency — with a summary, a category breakdown with shares and a trend over
earlier periods, offline, instantly, and equal to the server's figures after sync.

### 2. Glossary terms

From `saveme-backend/docs/glossary.md`: **Report** ("تقرير"), **Report Range** (covered wallet,
excluded wallet), **Budget Period** ("فترة الميزانية"), **Wallet** ("محفظة"), **Transaction**,
**income**, **expense**, **transfer** ("تحويل"), **Category**, **Category Reference**, **Occurrence**
(scheduled). UI words: "Reports" / "التقارير", "Income" / "الدخل", "Expenses" / "المصروفات",
"Net" / "الصافي", "Transfers in" / "تحويلات واردة", "Transfers out" / "تحويلات صادرة",
"Balance at start" / "الرصيد في البداية", "Balance at end" / "الرصيد في النهاية",
"Spending by category" / "المصروفات حسب الفئة", "Trend" / "الاتجاه", "Include scheduled" /
"تضمين المجدول", "Scheduled" / "مجدول", "Not included" / "غير مشمولة", "Custom range" /
"فترة مخصصة". Arabic wording is an assumed default for owner review. "Advanced reports" is not used.

### 3. Data model

No schema change: Room stays at version 10 and no table, column or migration is added. Every figure
is computed on read from existing tables (`wallet`, `transactions`, `occurrence`, `category`,
`platform_category`, `category_reference`, `sync_scope` read stamps, the profile) plus the sync queue. The only
state kept is in memory: the session's choices per scope (range, trend length, currency, scheduled
switch) and the Budget Period dates learned from the server (FR-019), both dropped at sign-out or
when the app process ends. Nothing is synced.

### 4. Invariants

| Invariant | Principle or decision | Test that fails when broken |
|---|---|---|
| Figures come only from the phone's copy with waiting changes overlaid; no server figure is shown | P-04.1 spirit, FR-001 | calculator tests over fakes; layering test (ui/store never sees report DTOs) |
| Wallet report = every member's live transactions; transfers by direction, never income/expense | ADR-0022 rule 1, FR-003 | table-driven calculator tests |
| Cross-wallet covers the phone's wallets in the currency; covered↔covered transfers appear nowhere; other currencies excluded and named | ADR-0022 rule 2, FR-004, FR-014 | calculator tests; excluded-wallets tests |
| Balance identity holds; balances = opening + postings before/through | ADR-0022 rule 4, FR-005 | property-style calculator test over random ledgers |
| Items grouped by system key or id, sorted by sum desc then key asc, zero items left out, totals = item sums | ADR-0022 rule 5, FR-006 | breakdown tests |
| Shares integer half-up, "<1%", none for zero total; never from the server | P-03, FR-007 | share tests; DTO test (no share member) |
| Periods equal the server's; underivable periods never guessed | FR-011, FR-019 | period tests vs captured server periods; offline "Shown when you're online" test |
| Planned figures separate, scheduled only, report currency only, never in actual figures or balances | FR-016 | planned tests |
| Phone figures equal the server's after sync for the captured QA set | SC-002 | captured-answer comparison test |
| No amounts in logs; parity probe logs member names only, dev build only | P-09.2, FR-025, FR-028 | log-capture test; flavor test |
| No schema change; nothing stored | FR-029 | schema export unchanged (10.json); `AppGraphTest` |
| State never by colour alone; RTL mirrored; 200% font | P-08, P-06, FR-018 | screenshot tests EN LTR, AR RTL, 2.0; accessibility semantics tests |

### 5. API

All under `/api/v1`, frozen in `reports.yaml` v1.11 (backend spec 019, `d7ed413`; unchanged at `b63e917`).

| Use | Endpoint | Request | Codes handled |
|---|---|---|---|
| Budget Period lookup (release and dev, FR-019) | `GET /reports/trend` | `months` 1–12, `date` (`YYYY-MM-DD`); no `currency`, no `include_planned` | 200 (only `data.periods` used); 400 `INVALID_QUERY_PARAMETER` (client defect, logged); 401 (session ends, P-09.5); 426 (update required); offline/timeout/5xx → "Shown when you're online" |
| Parity probe (dev build only, FR-028) | `GET /wallets/{id}/reports/{summary,by-category,trend}`, `GET /reports/{summary,by-category,trend}` | `date` or `from`/`to`, `months`, `include_planned`, `currency` (cross-wallet) as the screen shows | 200 (compared, never shown); 404 `NOT_FOUND` / 422 `INVALID_ID` (wallet gone: probe skipped); 400 (logged); others ignored |
| Freshness (FR-020) | `GET /sync/user/changes`, `GET /sync/wallets/{id}/changes` (specs 009, 011) through the sync engine | — | as specs 009/011 |

Not used for figures: every report endpoint's money members (the phone computes them, FR-001).

#### Contract changes since last spec

Baseline `saveme-backend@26e7c44` (spec 013) → `b63e917`: the error catalogue and API conventions
are unchanged, and the client's error codes match the catalogue (no client mismatch). No OpenAPI
file changed in that span; `reports.yaml` v1.11 dates from `d7ed413` and was already noted by
spec 012 (Reports convention).

### 6. Authorization and entitlements

Any member of a wallet, whatever the role, sees its report; the cross-wallet report covers the
wallets the person belongs to. No plan feature gates reports (backend spec 019 default 11;
`reports.advanced` stays reserved and is not read). Reads need no verified address.

### 7. Events

Not applicable: the client publishes no domain events and shows no notifications. It consumes the
sync engine's queue and read stamps, C4's wallets, C6's transactions with waiting changes, C7's
occurrences, C5's category displays and spec 002's profile.

### 8. Acceptance scenarios

| Story | Layer | Proof |
|---|---|---|
| US1 See | Unit (calculator, breakdown, shares, labels, store) + screenshots + E2E | tests; `.maestro/014-reports/US1-*` (deferred) |
| US2 Offline and parity | Unit (overlay, waiting note, freshness) + captured live answers + E2E | tests; `US2-*` (deferred) |
| US3 Wallet | Unit (wallet scope, transfers, liability) + captured + E2E | tests; `US3-*` (deferred) |
| US4 Trend | Unit (trend, chart semantics) + screenshots + E2E | tests; `US4-*` (deferred) |
| US5 Periods | Unit (periods, lookup, ticker) + live QA | tests; `US5-*` (deferred) |
| US6 Currencies | Unit (covered/excluded) + captured + E2E | tests; `US6-*` (deferred) |
| US7 Scheduled | Unit (planned) + captured + E2E | tests; `US7-*` (deferred) |

Owner policy: no emulator, Maestro or device run; flows are written and marked "deferred: E2E on
owner's device". Host tests plus a live-backend contract QA (curl against the local backend, real
answers captured as fixtures and replayed through the real client, appliers and calculators), as
specs 007–013.

### 9. Open questions

- **Backend contract gaps (non-blocking)**: (1) the start day history is server-internal, so the phone needs the server for periods before the profile's "applies from" date (FR-011, FR-019) — only the trend gives several periods in one read; (2) a scheduled transfer **into** a covered wallet from a wallet the person does not belong to lives on the other wallet's rule; whether its occurrence reaches the phone is to be confirmed in live QA — if not, the phone's scheduled transfers in under-count by it, and the gap is recorded (planned figures only); (3) no endpoint reports the read time of the data, so "Updated" is the phone's own read stamp.
- **Arabic words** listed in §2: assumed defaults for owner review. Non-blocking.
- **Chart design**: the design canvas has no chart; the new components follow its tokens and await the owner's design review. Non-blocking.
- **Pending start day change, offline at app start**: the current period is underivable until the server is reached once, so the report says "Shown when you're online" (budgets keep a stored server period; reports keep none, by design). Non-blocking.
- **`reports.advanced`**: reserved; a later spec decides what it gates. Non-blocking.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From Home, a person reaches the current period's report in one tap and sees its summary within 1 second, online or offline.
- **SC-002**: After a sync, 100% of the figures (summary members, every category item and its order, every trend point, scheduled figures, excluded wallets) equal the server's reports for the live QA set (cross-wallet and wallet scopes; shared wallet with a partner's spending; transfers between covered wallets, to an uncovered wallet and from a deleted wallet; liability wallet; archived wallet; deleted and future-dated transactions; scheduled occurrences; a second currency; start day 1 and a non-1 start day).
- **SC-003**: After recording, editing or deleting a transaction offline, the report shows the change within 1 second, marked as including a change waiting to sync, and does not change when the change is accepted.
- **SC-004**: The phone's Budget Periods equal the server's for 100% of the test table (start days 1, 15, 25, 28 across 28–31-day months, a past change and a pending change).
- **SC-005**: 20,000 transactions in the range across 20 wallets, with a 12-period trend, show within 1 second from the phone's copy.
- **SC-006**: 100% of report screens pass screenshot checks in English, Arabic (mirrored) and at 200% text, and every chart has a text alternative.
- **SC-007**: The brief's integration checkpoint passes: seeded transactions produce the expected summary and category shares; offline shows the report with its "Updated …" time.

## Assumptions

- Backend B13 is built (`d7ed413`); `reports.yaml` v1.11 is unchanged at `b63e917`.
- The phone already holds every wallet the person belongs to, every transaction of those wallets (including transfers whose other wallet the person is not in) and their occurrences, through C4, C6, C7 and the C14 feeds — the premise of the backend's offline-fallback note (contract item 8).
- Opening balances are on the phone (C4); a waiting change to one is overlaid like a waiting transaction.
- The design system has no chart component yet (spec 001 catalogue); this feature adds them there first (P-07.3).
- No new dependency; no change to the sync engine; Room stays at version 10.
- Shared-wallet scenarios use the `+partner` seed of spec 013 for live QA.
