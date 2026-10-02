# Contract: Screens, test tags, copy and E2E

## Navigation

- Home → button "Add transaction" (`home.addTransaction`) → `TransactionEditorKey(null, null)` (the editor preselects `TransactionsService.defaultWalletId()`).
- Wallets → wallet detail → row "Transactions" (`walletDetail.transactions`, replaces `walletDetail.noTransactions`) → `TransactionsKey(walletId)`. Wallet detail also gets "Add transaction" (`walletDetail.addTransaction`) for Owner/Editor of an active wallet → `TransactionEditorKey(null, walletId)`.
- Transactions → FAB "Add transaction" → `TransactionEditorKey(null, walletId)`; row → `TransactionEditorKey(id, walletId)` (the editor is also the detail; `walletId` is the side it is viewed from).
- Editor "Create a wallet first" → `WalletEditorKey(null)`. Back returns one step. `AppNavHost` passes `onRequireVerification` to both Routes (existing `EmailNotVerifiedSheet`). C4's `WalletDetailRoute` gets `onOpenTransactions(walletId)` and `onAddTransaction(walletId)` callbacks from `AppNavHost` (P-10.4).

## Screens (design-system components only, P-07)

| Screen | Content |
|---|---|
| Transactions (one wallet) | `AppTopAppBar` (wallet name, back); balance line (`MoneyText`, the wallet's displayed balance); search `AppTextField` with clear; "Filters" `AppFilterChip` opening a filter `AppBottomSheet` (date presets + custom from/to via `AppDatePickerDialog`; categories as `CategoryRow` checkboxes; kinds); active filter chips with "Clear all"; `SyncBanner` offline with waiting changes; refresh-failed `InlineAlert`; `LazyColumn` of sticky `DateHeader`s and `TransactionRow`s (category tile leading, title, subtitle, signed amount, status text); empty note "No transactions yet. Add your first one." / "No transactions match."; `AppFab` "Add transaction" (Owner/Editor, active wallet); pull to refresh; "Updated %1$s" offline; `Skeleton` rows before the first read |
| Transaction editor (create / edit / detail) | status `InlineAlert` (waiting / changed on another device with side-by-side lines / deleted elsewhere / not saved + reason, with its choices); wallet `AppSelectField` (create only; read-only otherwise); kind `SegmentedControl` Expense / Income / Transfer (create) or read-only text (edit); amount `AmountField` (large, currency suffix, focused on open); category field (`CategoryRow` look, opens `CategoryChoiceSheet`) or destination `AppSelectField` (transfer) with the "no wallet can receive" note; date field (`AppDatePickerDialog`); note `AppTextField`; "More details" expandable: details (multi-line), person, cleared `SettingsSwitchRow`; added-by and "From a recurring rule" captions (detail); `PrimaryButton` Save; edit mode `DestructiveButton` "Delete transaction"; confirm `AppAlertDialog`; no-wallet `EmptyState` with "Create a wallet" |

## Design-system additions (catalogue + screenshots EN, AR RTL, 2× font)

- `AppDatePickerDialog(initial, min, max, onPick, onDismiss)` and `DateField(label, value, onClick)` — Western digits in Arabic (research R-16).
- `AmountField` — the large amount input with the currency code, `KeyboardType.Decimal`, LTR digits inside RTL.
- `TransactionRow` gains optional `leading` (a `CategoryTile`), `status: String?` (text mark, P-08.3; the dot stays) and `testTag`-friendly modifier; existing call sites and baselines unchanged (new parameters default to today's look).
- `MoneyKind` unchanged: Income for money in, Expense for money out (U+2212, neutral ink), Balance for unknown kinds (unsigned).

## Test tags

| Element | Tag |
|---|---|
| Home button | `home.addTransaction` |
| Wallet detail | `walletDetail.transactions`, `walletDetail.addTransaction` |
| Transactions root / FAB / balance | `transactions.list` / `transactions.add` / `transactions.balance` |
| Day header / rows | `transactions.day.<yyyy-MM-dd>`, `transactions.row.<index>` (0-based, display order) |
| Row amount / status | `transactions.row.<index>.amount`, `transactions.row.<index>.status` |
| Search / filters | `transactions.search`, `transactions.filters`, `transactions.filter.thisMonth`, `transactions.filter.lastMonth`, `transactions.filter.custom`, `transactions.filter.kind.<expense|income|transfer>`, `transactions.filter.category.<index>`, `transactions.filter.apply`, `transactions.chip.<index>`, `transactions.clearAll` |
| Notes | `transactions.empty`, `transactions.noMatches`, `transactions.refreshFailed`, `transactions.updatedAt` |
| Editor fields | `txEditor.wallet`, `txEditor.kind.<expense|income|transfer>`, `txEditor.amount`, `txEditor.category`, `txEditor.destination`, `txEditor.destinationsEmpty`, `txEditor.date`, `txEditor.note`, `txEditor.more`, `txEditor.details`, `txEditor.person`, `txEditor.cleared`, `txEditor.save` |
| Editor status and choices | `txEditor.status`, `txEditor.keepMine`, `txEditor.useTheirs`, `txEditor.restoreWithMine`, `txEditor.acceptDeletion`, `txEditor.deleteAnyway`, `txEditor.keepTransaction`, `txEditor.tryAgain`, `txEditor.discard` |
| Editor actions and captions | `txEditor.delete`, `txEditor.addedBy`, `txEditor.recurring`, `txEditor.noWallet`, `txEditor.createWallet` |
| Category sheet (C5 component) | `categoryChoice.current`, `categoryChoice.own.<index>`, `categoryChoice.default.<index>` |

Dialog buttons are not tagged for Maestro (dialog windows don't expose tags, spec 006); flows tap
them by text.

## Copy (new keys, English / Arabic)

Block `<!-- Transactions (spec 008) -->` at the end of both `strings.xml`:

`home_add_transaction` ("Add transaction" / "إضافة معاملة"), `wallet_transactions` ("Transactions"
/ "المعاملات"), `transactions_add` ("Add transaction" / "إضافة معاملة"), `transactions_empty`
("No transactions yet. Add your first one." / "لا توجد معاملات بعد. أضف أول معاملة."),
`transactions_empty_viewer` ("No transactions yet."), `transactions_no_matches` ("No transactions
match." / "لا توجد معاملات مطابقة."), `transactions_today` ("Today" / "اليوم"),
`transactions_yesterday` ("Yesterday" / "أمس"), `transactions_updated_at` ("Updated %1$s"),
`transactions_refresh_failed` ("Couldn't update the transactions. Showing what's on this phone."),
`transactions_search` ("Search notes, details and people" / "ابحث في الملاحظات والتفاصيل
والأشخاص"), `transactions_filters` ("Filters" / "تصفية"), `filter_this_month` ("This month" /
"هذا الشهر"), `filter_last_month` ("Last month" / "الشهر الماضي"), `filter_custom` ("Custom
dates" / "تواريخ مخصصة"), `filter_from` ("From"), `filter_to` ("To"), `filter_kind` ("Kind" /
"النوع"), `filter_category` ("Category" / "الفئة"), `filter_apply` ("Show results"),
`filter_clear_all` ("Clear all" / "مسح الكل"), `tx_kind_expense` ("Expense" / "مصروف"),
`tx_kind_income` ("Income" / "دخل"), `tx_kind_transfer` ("Transfer" / "تحويل"),
`tx_kind_unknown` ("Other" / "أخرى"), `tx_transfer_to` ("To %1$s" / "إلى %1$s"),
`tx_transfer_from` ("From %1$s" / "من %1$s"), `tx_another_wallet` ("another wallet" / "محفظة
أخرى"), `tx_field_wallet` ("Wallet" / "المحفظة"), `tx_field_amount` ("Amount" / "المبلغ"),
`tx_field_category` ("Category" / "الفئة"), `tx_field_destination` ("To wallet" / "إلى
المحفظة"), `tx_field_date` ("Date" / "التاريخ"), `tx_field_note` ("Note" / "ملاحظة"),
`tx_more` ("More details" / "تفاصيل أكثر"), `tx_field_details` ("Details" / "تفاصيل"),
`tx_field_person` ("Person" / "الشخص"), `tx_field_cleared` ("Cleared" / "تمت المطابقة"),
`tx_cleared_help` ("Matches your bank or statement."), `tx_kind_locked_note` ("This can't
change after the transaction is saved."), `tx_wallets_locked_note` ("The wallets of a transfer
can't change. Delete it and add a new one."), `tx_destinations_empty` ("No wallet can receive
this transfer: you need another active wallet in %1$s that you can add to."),
`tx_added_by` ("Added by %1$s" / "أضافها %1$s"), `tx_added_by_other` ("Added by another
member"), `tx_from_recurring` ("From a recurring rule" / "من قاعدة متكررة"), `tx_no_wallet`
("Create a wallet first to add transactions." / "أنشئ محفظة أولًا لإضافة المعاملات."),
`tx_create_wallet` ("Create a wallet"), `tx_save` ("Save" / "حفظ"), `tx_delete` ("Delete
transaction" / "حذف المعاملة"), `tx_delete_title` ("Delete this transaction?"),
`tx_delete_body` ("%1$s on %2$s will be removed from this wallet's balance."), `tx_gone` ("This
transaction is no longer available."), errors: `tx_error_amount_empty` ("Enter an amount."),
`tx_error_amount_positive` ("Enter an amount greater than zero."), `tx_error_amount_digits`
("Use at most %1$d digits after the decimal point."), `tx_error_amount_range` ("This amount is
too large."), `tx_error_date_range` ("Choose a date between 1900 and 2999."),
`tx_error_category_required` ("Choose a category."), `tx_error_destination_required` ("Choose
the wallet to transfer to."), `tx_error_too_long` ("Use at most %1$d characters."),
statuses and reasons: `tx_status_clash` ("Changed on another device."), `tx_status_deleted`
("Deleted on another device."), `tx_status_not_saved` ("Not saved."), `tx_reason_not_verified`,
`tx_reason_gone` ("This transaction or its wallet is no longer available."),
`tx_reason_no_right` ("You can't add or change transactions in this wallet."),
`tx_reason_no_right_other` ("You can't add or change transactions in the other wallet."),
`tx_reason_category` ("This category can't be used any more. Choose another."),
`tx_reason_destination` ("The wallet you transferred to is no longer available."),
`tx_reason_currency_changed` ("This wallet's currency changed to %1$s. Enter the amount
again."), `tx_reason_currencies_differ` ("The two wallets now use different currencies."),
`tx_reason_invalid` ("Something in this transaction needs fixing."), `tx_reason_other` ("Not
saved. Reference: %1$s"), `tx_this_phone` ("This phone: %1$s"), `tx_other_device` ("Other
device: %1$s"), `action_restore_with_mine` ("Restore with my change"), `action_keep_transaction`
("Keep the transaction"); "Keep mine", "Use the other device's", "Accept the deletion", "Delete
anyway", "Try again", "Discard" and "Waiting to sync" reuse the spec 006/007 keys. Arabic avoids
"نسخة" (spec 007 wording) and every banned synonym.

## E2E (`.maestro/008-transactions/`, `/speckit-e2e`) — run deferred to the owner's device

Seeds: `verified-user`, `unverified-user` (existing). Second session through `common/api.js` slot
`B` with new actions `listTransactions`, `createTransaction` (any kind, transfer included),
`saveTransaction`, `deleteTransaction` (`DELETE` with a JSON body), `restoreTransaction`,
`getWallet`; reuses `createWallet`, `createExpense`, `createCategory`. New common flows
`common/open-transactions.yaml` (Home → Wallets → first wallet → Transactions) and
`common/add-expense.yaml` (amount, first default category, save). Runner actions `network-off` /
`network-on` (spec 006).

| Flow | Scenario |
|---|---|
| `US1-S1-editor-defaults.yaml` | wallet preselected, Expense, today, amount focused |
| `US1-S2-record-online.yaml` | 45.50 with "Groceries"; listed under Today; balance −45.50 |
| `US1-S2-record-offline.part1/2/3.yaml` | network off → record → "Waiting to sync" + balance → network on → mark clears; slot B lists it; balance unchanged |
| `US1-S3-category-choice-groups.yaml` | own + defaults groups only (C5 US4-S1) |
| `US1-S4-amount-and-category-errors.yaml` | empty, 0, 3 decimals in EGP, no category |
| `US1-S5-backdated.yaml` | yesterday's date; listed under Yesterday |
| `US1-S6-more-details.yaml` | note, details, person, cleared saved; 101-char note refused |
| `US1-S7-home-add.yaml` | Home → Add transaction → last wallet preselected; switch wallet |
| `US1-S8-unverified-asks-to-verify.yaml` | verify prompt, nothing queued |
| `US1-S10-currency-locks.yaml` | after the first transaction, wallet editor shows currency read-only |
| `US2-S1-days-and-totals.yaml` | slot B seeds 3 on 2 dates; two headers with totals |
| `US2-S3-pending-balance.part1/2.yaml` | offline record; detail + list balances include it |
| `US2-S5-other-device-after-pull.yaml` | slot B edits / deletes; pull; changes shown |
| `US2-S6-offline-last-known.part1/2.yaml` | network off; list with "Updated" |
| `US2-S7-empty.yaml` | new wallet: empty note with add |
| `US3-S1-edit-fields.yaml` | change amount and category; kind read-only |
| `US3-S2-archived-category-kept.yaml` | slot B archives the category; edit keeps it selected (C5 checkpoint SC-007) |
| `US3-S4-S5-delete.yaml` | confirmation text; row and balance update |
| `US3-S5-delete-never-sent.part1/2/3.yaml` | offline record + delete sends nothing |
| `US3-S6-clash-keep-mine.part1/2.yaml`, `US3-S6-clash-use-theirs.part1/2.yaml` | network off → edit → slot B edits → network on → choices (brief checkpoint) |
| `US3-S7-deleted-elsewhere-restore.part1/2.yaml` | slot B deletes; phone's edit → "Restore with my change" → slot B lists it with the phone's values |
| `US3-S8-delete-clash.part1/2.yaml` | phone deletes offline; slot B edits; "Delete anyway" / "Keep the transaction" |
| `US3-S9-refused-not-verified.yaml` | slot-B-unverified path where seedable, else manual |
| `US4-S1-destination-choices.yaml` | same-currency writable wallets only; empty note in a single-wallet account |
| `US4-S2-transfer-both-wallets.part1/2/3.yaml` | offline transfer Cash → Bank; both lists and balances; online; slot B lists one transaction in both |
| `US4-S3-transfer-wallets-read-only.yaml` | edit: wallets read-only |
| `US5-S1-search.yaml` | "plumb" matches note/details/person in any case |
| `US5-S2-S3-filters-and-chips.yaml` | This month + Expense; chips; clear all |
| `US5-S4-offline-search.part1/2.yaml` | network off; search works |

Manual only (not seedable without C9 or a second device): US1-S9 and US3-S10 (viewer), US2-S8
(partner's category), US2-S9 (added by another member, recurring), US4-S4 (403 other wallet),
US4-S5 (currencies differ), US4-S6 (transfer from a partner's wallet), US2-S4 (no balance flicker:
watched by eye; proved by host tests).
