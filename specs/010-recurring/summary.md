# Summary: Recurring Transactions (spec 010, C7)

**Date**: 2026-10-04 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner, committed on branch `010-recurring` (f2edd7c) · **Backend contract**: `saveme-backend@192e3ab` (`recurring.yaml` v1.7, unchanged since c0e9844).

## What was built

- **`feature.recurring`** (data, domain, ui, di) with Room v7: tables `recurring_rule` and `occurrence`, a `transactions.pending_confirmation` column, `MIGRATION_6_7`, exported `7.json`; the migration also forgets every wallet scope's feed position once, so the recurring rows are read from the start.
- **Sync engine additions (default-off, all existing engine tests unchanged)**: `SyncHandler.acceptsClash` (suspend; takes the server's answer when the occurrence is already confirmed) and `RecordKey.untilSettled` (keeps the new rule of a "change from a date" waiting until ending the old rule is accepted, even within the same batch).
- **`RecurringApi`** (rule and occurrence writes, catch-up), two sync handlers and two feed appliers (`recurring_rule`, `occurrence` rows from the wallet feed; no list or read endpoints used).
- **Schedule domain**: `RuleDates`, `Allocation`, `RuleToday`, `OccurrenceStates`, `TodayTicker`. The phone's date preview equals the server's for monthly month-end, yearly 29 Feb, weekly and daily (checked against 50+ live rounds).
- **`RecurringService`**: a confirmation is one queued occurrence write (versioned PUT carrying a client UUIDv7 `transaction_id`) plus a phone-only transaction row that counts in the balance; the server's copy replaces it in place using the spec 008 carry, with no balance flicker. Skip, undo, edit one occurrence, pause, resume, end, delete, split ("Change from a date"), catch-up.
- **Four MVI screens**: rule list, rule editor (presets gam'eya and installment), rule detail, occurrence.
- **Design system**: `OccurrenceRow`, `RuleRow`, `DueCard`, `PreviewDateList`; `LocalPickerToday` (see below).
- **Entry points**: "Recurring" row on wallet detail; Due section (at most 3) on the wallet's transactions list; Home "Due" card (owner/editor wallets only); "Make recurring", "Confirm … instead" (match window ±7 days) and "Open the rule" in the transaction editor. All reach recurring through hooks owned by the lower features (`RecurringLinks`, `OccurrenceTransactions`, `DueSummary`, `MakeRecurringDraft`, category-use union, `WalletDeletionParticipant`).
- 196 English/Arabic strings. 19 Maestro files in `.maestro/010-recurring/` (12 flows, some in parts), each marked deferred; `api.js` gained `createRule`, `deleteRule`, `materialise`, `listOccurrences`, `confirmOccurrence`.

### Fixed during QA

- **Date-dependent goldens**: the three `ComponentScreenshotTest [transactions]` goldens failed on any day after 2 Oct (they fail on a clean `main` too) because Material's date picker marks "today" from the system clock. A `LocalPickerToday` CompositionLocal (default: system clock) now pins it in the catalogue; goldens no longer depend on the real date. No other screenshot reads the real clock.
- The occurrence screen kept showing the auto-confirm refusal reason while a skip or edit waited; it is now hidden while any save of the occurrence waits (FR-027).
- Maestro: the rule screen's "End rule" / "Delete rule" and the dialog confirms had identical text; the confirms now read "Yes, end it" / "Yes, delete it" (Arabic "نعم، أنهِها" / "نعم، احذفها").

## Results

- **Host QA**: 26/26 acceptance scenarios, 39/39 FRs, 17/17 edge cases PASS (`qa-report.md`, incl. the §4 invariants → tests list); six gaps closed. 1,984 host tests green with `--rerun-tasks`; Roborazzi verify, `assembleDevDebug` and the iOS compile pass. 69 existing goldens changed on purpose (transactions editor/list, wallet detail); about 130 new goldens.
- **Live contract QA** against `saveme-backend@192e3ab`: all 10 rule shapes, 14 invalid creates, every frozen-field refusal, preset `IMMUTABLE_FIELD`s, delete with body, materialise, confirm (links and balance effect), replay 200, second confirm 409, skip/undo/edit, auto-confirm via the job with no back-posting, a phone confirm racing it (409), all three refusal reasons, transfer rules, wallet feed rows, batch items. No backend bug. 157 live answers are fixtures driving `RecurringCapturedResponsesTest` (24 tests) through the real API, engine, handlers, appliers and service.

## Deferred to the owner's device

T048, T060, T069, T075, T080, T086, T100, T101 (flow runs, quickstart, full E2E report).

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. "Make recurring" turns the draft into a new rule only; the draft is not also saved as a transaction.
2. Automatic confirmation is off for every new rule.
3. Due items: at most 3 at the top of the wallet's transactions screen, a Home "Due" card (owner or editor wallets only), and the recurring screen. The editor offers "Confirm <rule> instead" for a received, unconfirmed occurrence matching wallet, kind, and category or destination within ±7 days.
4. If another device or the server already confirmed an occurrence, the phone accepts that, drops its local transaction and shows "Already confirmed"; it never resends under a new id.
5. "Check for new dates" is manual, online only, for owners and editors, followed by a wallet read; never in the background.
6. "Change from a date" ends the old rule the day before and creates a new rule that waits until the end is accepted; the date must fall after the last confirmed or skipped round.
7. Once any round is confirmed or skipped, the editor locks the schedule fields (and, for gam'eya or total-amount rules, the amount and count).
8. "Today" uses the rule's time zone; a rule not yet on the server uses the profile time zone, then the device's.
9. Unknown values from a later server make a rule read-only.
10. A waiting confirmation can be changed (same transaction id) or cancelled.
11. Rules and occurrences come only from the wallet feed.
12. Presets and total-basis rules cannot "Change from a date" (read-only): the backend gives presets no `end_date` and a total rule cannot drop its count. The old rule's end replaces its count when splitting.
13. Presets are forced to monthly, every 1, no end date; the anchor day follows the start date; income and expense rules require a category.
14. A waiting confirmation cannot become a skip; values equal to the server's discard the waiting change; a transfer confirm is refused when the person cannot write the counter wallet.
15. Editor: rule type, amount basis and end mode are wrapping chips (segmented controls clip at 2× text). The occurrence screen closes after a confirm or edit and stays after skip and undo.
16. "Change from a date" is offered only for custom per-round rules the server holds; "Check for new dates" only for writers on active or paused rules the server holds; pause and resume act at once, ending and deleting ask first.
17. "Make recurring" with a blank amount hands over 0; the confirm-instead offer needs a valid typed amount.

### Implementation

18. Two further `OccurrenceTransactions` members beyond the design: `dropConfirmationOf` and `sweepConfirmations`.
19. Hook additions, all default-off: `RecurringLinks.observeRuleOnPhone`, `MakeRecurringDraft`, `DueSummary.observeFirstDue` with `DueGlimpse`.
20. `PendingCategoryUses` now has two sources (transactions and recurring); two `AppGraphTest` assertions changed accordingly.
21. `speckit-contract-check` was unavailable; "no drift" rests on a diff of `recurring.yaml` plus the contract tests.
22. Dialog confirms for ending/deleting a rule read "Yes, end it" / "Yes, delete it".

### Owner review

23. Arabic: دفعة (occurrence), دفعة القبض (payout round), عدد الدفعات (number of rounds), التكرار (how often), الموعد الأصلي (originally), مستحقة (due), فائتة (missed), تأكيد تلقائي (automatic confirmation), إنهاء / استئناف / إيقاف مؤقت (end / resume / pause), and the two new dialog confirms.

## Remaining risks

- No flow has run on a device; Maestro timings and dialog taps are verified by static review only.
- Material's date picker still reads the real clock on screen; the pin affects only the catalogue and goldens.
- The server accepts overlapping rules; the no-overlap guarantee of a split relies on the client's ordering.
- A rule on a transfer's destination wallet is not on that wallet's feed (backend open item).
- Auto-confirmation timing depends on the hourly server job.
- A phone cannot confirm an occurrence it has not received, so a rule created offline can't have its first round confirmed until it syncs; a hand-recorded transaction can't be linked to an occurrence afterwards.
- The first sync after the update re-reads every wallet from the start.
- QA accounts (`qa010-*@example.test`) and one manual Plus subscription remain in the dev database.
