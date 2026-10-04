# Summary: Budgets (spec 012, C8)

**Date**: 2026-10-05 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner, committed on branch `012-budgets` (0e786b9) · **Backend contract**: `saveme-backend@64ca1ea` (`budgets.yaml` v1.8, unchanged since bb7e61b).

## What was built

- **`feature.budgets`** (data, domain, ui, di), no sync-engine change and no new dependency. Room v9: tables `budget` and `budget_progress`; `MIGRATION_8_9` clears only the user scope's feed cursor, read stamp and position once (so `budget` rows are read from the start); wallet scopes untouched; `9.json` exported.
- **`BudgetSyncHandler`** (record type `budget`, user scope): batch upsert and delete with no `wallet_id`; archive per record after a whole save; category, currency and period type sent from the stored row. A budget whose category was created on the phone and is not yet accepted waits for it. **`BudgetFeedApplier`** applies `budget` rows from the user feed with the version guard, the deletion rule and the sweep.
- **Offline progress**: the phone computes progress itself through a new transactions hook, `OwnSpending`, applying the server's attribution rules with waiting changes overlaid — own live expenses only (`created_by`), wallets the person belongs to, the budget's currency, category and period; a waiting recurring confirmation counts for its rule's author (`RecurringLinks.ruleAuthorOf`). The server's `GET /budget-progress` figure is stored beside the phone's synced-only figure and `ProgressSelection` shows it only while all four hold: the stored period is still current, no waiting change matches, the synced-only sum is unchanged since the read, and the limit is unchanged. Accepting a waiting change never makes the figure jump back. Percentage, remaining and the month-end projection (rounded up) are always derived on the phone with integer maths; the server sends no percentage.
- **Periods** follow the server's copy of the profile's start day and "applies from" date; while a start-day change is pending the last stored server period is used, or "Progress appears after sync". A `DayTicker` re-derives the period at midnight in the profile's time zone.
- **Screens**: budgets screen (period line, pending start-day line, active and archived lists, pull to refresh, offline "Updated …"), budget editor (create, limit edit, archive, delete), clash and refusal choices, a Home "Budgets" row and a card with up to 3 active budgets (highest share first) through a `BudgetSummary` hook. Categories named by a budget count as in use (`BudgetCategoryUses`, a third source of `PendingCategoryUses`).
- **Design system**: one additive change, `CategoryChoiceItem.enabled` (disabled "Already budgeted" rows), plus the helper `formatDayMonth`. About 85 English/Arabic strings.
- **E2E assets**: 15 Maestro files in `.maestro/012-budgets/` (each marked deferred), `api.js` budget actions, `common/open-budgets.yaml`, `common/seed-budget.yaml`.

## Results

- **Host QA** (`qa-report.md`): 38/38 acceptance scenarios, 16/16 edge cases, 26/26 FRs, 13/13 invariants, 7/8 SCs (SC-001's 30-second timing is device-only). 2,441 host tests green with `--rerun-tasks`; Roborazzi verify, `assembleDevDebug` and the iOS compile pass; 63 new goldens (21 states × 3, pinned through explicit state, none depend on the real date); 15 existing goldens changed on purpose (categories "Already budgeted" row, budget row captions, Home "Budgets" row).
- **Live contract QA** (about 140 HTTP calls, 3 throwaway accounts): every budget write and error matched the contract — create 201, replay 200, stale 409 with `current`, `IMMUTABLE_FIELD`, `OUT_OF_RANGE` at 0 and 2^53, `INVALID_ENUM_VALUE` for an income category, `EMAIL_NOT_VERIFIED`, 404 for another person's id, archive and delete repeatable; batch items (a `wallet_id` or an `archive` op → 422 for the whole request); `budget` rows on the user feed incl. tombstones; `/budget-progress` omits archived budgets and pages at limit 1; a cursor used with a different `date` is `INVALID_CURSOR`. No backend bug.
- **SC-003/SC-004**: the phone's spent, remaining, over-limit, projection, share, other currencies and period equal `GET /budget-progress` for all 5 live budgets (own, shared-wallet, recurring-attributed, transfer, income, deleted, other-currency, pending start-day change, leaving a wallet). The stored phone sums equal the server's at the read, so the figure does not jump. 20 tests drive 157-style captured answers through the real puller, appliers and calculators (`BudgetCapturedContractTest` 13, `BudgetCapturedBatchTest` 3, `BudgetCapturedProgressTest` 4; `CapturedBudgetResponses.kt`).
- **Flaky test fixed**: `CategoriesServiceArchiveDeleteTest.aWaitingDeletionHidesTheCategoryAndAClashOrRefusalShowsItAgain` raced with the service's background upload (a 503 on a Ktor thread rewrote the hand-set queue row). The test environment now parks the handler forever (`awaitCancellation()`); 300 in-process repetitions and 15 consecutive isolated Gradle runs are green. `WalletsServiceArchiveDeleteTest` and `WalletsServiceSaveTest` use the same pattern and may flake; a fix there broke a test that needs the answer and was reverted.
- **Maestro fixes (static)**: `US4-clash.part2` now passes the budget id; `US5-start-day` rewritten around the pending case (a start day already in force cannot be produced live); `US1-duplicate-blocked` taps `.*Food run.*` and asserts the field stays empty; `US2-home-card` uses `above:` selectors; `US2-over-and-pace` asserts specific words. A test tag on the disabled row is not possible (tags inside the bottom sheet are not exposed to Maestro).
- Spec wording: FR-016 now says "as on the other lists" instead of showing a request id.

## Deferred to the owner's device

T043, T054, T062, T072, T078, T085, T099, T100 (flow runs, quickstart, full E2E report), including SC-001's 30-second set-up timing.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. The phone always computes current-period progress itself; the server's figure is shown only while the four-condition rule holds.
2. Home gets a "Budgets" row and a card with up to 3 active budgets, highest share first; no per-wallet summary.
3. The phone will not create a second active budget for the same category (or all expenses) in the same currency; the picker marks it "Already budgeted". Overlaps created on other phones are shown and counted, not merged.
4. Only the current period is shown; history belongs to C13.
5. Periods follow the server's copy of the profile. A pending start-day change uses the last stored server period or shows "Progress appears after sync"; an unsynced start-day change has no effect; before the phone holds a profile the start day is 1.
6. Limit edits are whole PUTs (no PATCH); archive is one-way, delete is final; archived budgets show under "Archived" and never on Home.
7. Currency choices are the person's wallet currencies plus the profile default (preselected).
8. Waiting refused or clashing transaction changes are counted the way the transaction list shows them.
9. Progress is read after sync reads on sign-in, foreground, opening the screen (60 s floor) and pull to refresh (forced); one budget's progress is read when its editor opens; `INVALID_CURSOR` restarts the walk once.
10. "On pace for" is hidden when nothing is spent; a budget with an unknown period type is read-only with no figures.
11. The board's `BudgetProgress` colours are kept; words and the percentage are always written; no alarm copy and no notifications.
12. Budgets get their own `DayTicker` rather than depending on recurring.

### Implementation

13. `BudgetGlimpse.spentMinor` is nullable so Home can show a budget whose period is unknown.
14. With no profile default currency and no wallet the editor falls back to EGP.
15. The service gains `observeStartDay()` and `refreshOne(id, force)`; `BudgetChoices.forCurrency` stops a currency switch keeping a category chosen from stale choices.
16. The empty-state button is tagged `budgets.empty.add`, separate from the top bar's `budgets.add`.
17. "Updated" offline is the latest progress read time.
18. A delete that meets a newer copy reads "Delete anyway" / "Keep the budget".
19. The phone's synced-only figure excludes placeholders and phone-only confirmations, to avoid the jump-back; a waiting change in another currency does not flip the shown figure.
20. Progress read floors reset when another account signs in; a failed walk does not start its floor, a failed per-budget read does; sign-in and foreground call `refresh(force = false)`.
21. Existing tests changed on purpose: migration tests chain `MIGRATION_8_9` (`Migration6To7Test`/`Migration7To8Test` now expect the user scope's cursor and read stamp to be null after the chain); the wallet, category, transaction and recurring string tests stop scanning at the next `<!-- … (spec nnn) -->` marker; `FakeTransactionDao` gains `observeForSpending`.

### Owner review

22. Arabic: ميزانية, فترة الميزانية, كل المصروفات, "استخدم ما على الجهاز الآخر", "احذفها على أي حال", "احتفظ بالميزانية", "حُذفت على جهاز آخر", "لم تُحفظ", plus the spec's table.
23. The "over budget" colour (`money.over`) is the same hex as the error colour (`#A9503A`), the design board's choice; a more neutral colour may be preferable.

## Remaining risks

- No Maestro, device or timing run (SC-001).
- A start day already in force other than 1 is proven only by the rule table in `BudgetPeriodsTest`: a fresh account cannot reach one live before its applies-from date (a second test account's day 25 takes effect on 2026-10-25 if a recheck is wanted).
- A progress cursor sent without `date` is answered for the period it was issued for, so a walk crossing midnight keeps its old period; the client checks the stored period against today.
- Budgets created offline on two phones can overlap and are not merged; past limits are not stored by the server (current limit only), which matters once history exists in C13.
- The backend leaves archived budgets out of `/budget-progress`; archive cannot go in a batch; a budget not yet on the server has no server progress (shows the phone's figure until it syncs); the start-day history is server-only.
- Two wallet tests use the 503 background-upload pattern that made the categories test flaky.
- The first sync after the update re-reads the user feed from the start.
- Arabic wording is unreviewed.
