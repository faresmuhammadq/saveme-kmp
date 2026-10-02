# Summary: Transactions (spec 008, C6)

**Date**: 2026-10-02 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner's phone, committed on branch `008-transactions` (919cea3) · **Backend contract**: `saveme-backend@f6d66eb` (`transactions.yaml` v1.7, unchanged since c0e9844).

## What was built

- **`feature.transactions`** (data, domain, ui, di) with Room v5: table `transactions` (plural; TRANSACTION is an SQL keyword), `MIGRATION_4_5`, exported `5.json`; cleared at sign-out. The last wallet recorded in lives in `KeyValueStore` and is cleared too.
- **`TransactionApi`** (list in pages of 200, PUT, PATCH restore, DELETE with a JSON body) and **`TransactionSyncHandler`** (record type `transaction`, scope = source wallet, `canRestore = true`): PUT for create and save, DELETE, and "Restore with my change" = PATCH `{deleted_at: null}` then a PUT of the phone's values.
- **`TransactionRepository`** (whole-wallet read, progressive, all-or-nothing removals) and an additive `SyncEngine.observeAll(type)`.
- **`DefaultTransactionsService`**: gates, role and transfer rules, destination choices, local search and filters with the server's rules, day grouping with net totals, clash and refusal mapping by code and pointer.
- **Hooks replacing the C4/C5 placeholders**: `TransactionPostings` (pending balance delta plus a "carry" so the balance never jumps back after a send) with `BalanceReconciler`, `TransactionCategoryUses`, `TransactionsWalletCleanup`, `TransactionsSyncCoordinator`. The wallet editor locks the currency while a transaction is unsent, and until the wallet is re-read after a sync.
- **Two MVI screens**: the transactions list (balance, search, filter sheet and chips, sticky day headers, status marks, pull to refresh, offline "Updated …") and the editor (create, edit, detail with clash/restore/refusal choices, delete confirmation, read-only for viewers and unknown kinds, transfers). Entry points: Home "Add transaction", a "Transactions" card on wallet detail, and the list. The C5 category picker is placed here.
- **Design system**: `AppDatePickerDialog` / `DateField` with Western digits in Arabic, `AmountField`, a richer `TransactionRow`, catalogue section. About 85 English/Arabic strings.
- **E2E assets**: 50 Maestro flows in `.maestro/008-transactions/` (31 scenarios), `api.js` transaction actions incl. PATCH, three common flows.

## Results

- **Host QA**: 40/40 acceptance scenarios, 36/36 FRs, 19/19 edge cases PASS (`qa-report.md`). 1,293 host tests green with `--rerun-tasks`; Roborazzi verify, `assembleDevDebug` and the iOS compile pass. 102 new goldens; 33 re-recorded on purpose (Home and wallet-detail states).
- **Contract QA against the live local backend**: every call the app makes replayed with the app's headers and bodies — create, replay, edit, stale clash, deletion clash, restore, delete, transfers (both lists and both balances, destination-path writes, deleted source or destination), list paging and repeated filters, every field and auth refusal code, wallet currency lock (incl. an incoming transfer alone, and after all deletions), CATEGORY_IN_USE, category references for a member's own category. No drift, no backend bug. 51 real responses are fixtures driving 15 tests through the real engine, handler, repository, postings and service.
- **Fixed during QA**:
  1. "Keep typing when the open transaction vanishes" implemented: typed changes are kept, Delete is withdrawn, Save sends on the opened copy and the server answers "Deleted on another device" or a refusal; with nothing typed the editor closes with "no longer available".
  2. The wallet currency could be changed between a sync and the next wallet read; a wallet now counts as having pending transactions until that re-read.
  3. New tests for oldest-first sending with independent refusals and for re-sending a never-synced create in the wallet's new currency (FR-026).
  4. Maestro: seed dates default to today; "yesterday" handles the 1st of the month; one Back count corrected.
  5. A flaky refresh test fixed (test-only).

## Deferred to the owner's phone

T040, T050, T059, T067, T074 (flow runs), T087, T088 (quickstart and full E2E report).

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. Entry points: a wallet's transactions screen, Home "Add transaction" (last-used wallet, else the first writable one), and wallet detail. The editor opens on Expense, today, amount focused.
2. Deletion asks for confirmation, no undo. Restore happens only through a clash ("Restore with my change").
3. Search and filters run on the phone with the server's rules (literal, case-insensitive; "or" within a filter, "and" across). Server filter parameters are not used.
4. Opening a wallet's transactions reads every page (`limit=200`, at most once a minute unless pulled); removals only after a complete read.
5. Transfer destinations: active wallets on the phone, same currency, owner or editor, not the source. Archived wallets are excluded even though the backend accepts them.
6. Archived wallets: no new transactions offered; existing ones stay editable.
7. Whole-record PUT for edits; PATCH only to restore.
8. Balance = server balance + unsent effects + a carry after each send until a wallet re-read that started after the server confirmed.
9. When a wallet leaves the phone, transactions touching no remaining wallet are removed and their queued changes discarded.
10. Refusal copy: a 403 on a transfer reads "no right in the other wallet"; on income or expense, "no right in this wallet". After `CURRENCY_MISMATCH` at `/currency`, editing asks for the amount again.
11. The balance-effect rule lives in `data` (P-10.6: data may not import domain).
12. Design-system date picker with Western digits; no new dependencies.
13. **UI words for owner review**: English Note (description), Details (memo), Person (counterparty), Cleared; Arabic ملاحظة / تفاصيل / الشخص / تمت المطابقة, معاملة / المعاملات, "من قاعدة متكررة".

### Implementation

14. Filtering runs in Kotlin over values with pending changes overlaid, so a waiting edit moves a row in or out of a filter correctly.
15. Balance projection reads the queue and one query together (separate flows briefly double-counted).
16. The balance reconciler starts after sign-in.
17. Wallet cleanup discards queue rows from a launched job (inline would deadlock on the engine lock).
18. The handler sends the stored currency only for a record the server already has; a never-sent create sends the wallet's present currency.
19. Date-picker locale via `DatePickerState(locale = westernDigitsLocale(...))` (expect/actual; iOS compiled only).
20. "Use the other device's" triggers a full wallets read (no single-wallet read exists).
21. Category reference refresh obeys the 60 s floor.
22. A wallet's "waiting" mark stays until its balance is re-read after a sync (usually about a second).

## Remaining risks

- None of the 50 flows or the quickstart checks have run on a device.
- Dialog and sheet buttons are tapped by text; "Delete transaction" is both the editor button and the dialog confirm.
- The date picker's Western-digit override is unrun on iOS; the Material 3 picker is an experimental API.
- Flows take "today" from the host clock; the app uses the profile's time zone.
- If a restore's second step is refused, the phone shows its pre-deletion copy until the next read.
- The balance carry is lost if the process dies mid-send; the balance can be stale until the next wallet read.
- Opening a 10,000-transaction wallet re-reads about 50 pages until the C14 wave 2 feed switch.
- Shared-wallet scenarios (viewer, partner category, 403 on a transfer) need C9 for end-to-end proof.
- QA accounts remain in the dev database.
