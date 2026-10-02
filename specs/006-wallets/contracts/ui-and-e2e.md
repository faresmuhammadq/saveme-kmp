# Contract: Screens, test tags, copy and E2E

## Navigation

Home → row "Wallets" (`home.wallets`) → `WalletsKey` (list) → card → `WalletDetailKey(id)` →
Edit → `WalletEditorKey(id)`. List FAB "New wallet" → `WalletEditorKey(null)`. Back returns one
step. `AppNavHost` passes `onRequireVerification` to the three Routes; it shows the existing
`EmailNotVerifiedSheet`.

## Screens (design-system components only, P-07)

| Screen | Content |
|---|---|
| Wallets list | `AppTopAppBar` ("Wallets", back); `SyncBanner` when offline with waiting changes; totals as one `BalanceHero` per currency (caption "N wallets"); `WalletCard` per active wallet; "Archived" `DateHeader`-style section header and archived cards; `EmptyState` with "Create your first wallet"; `AppFab` "New wallet"; pull to refresh; "Updated %1$s" caption offline |
| Wallet detail | `BalanceHero` (displayed balance, caption type · currency); status `InlineAlert` (waiting / changed on another device with side-by-side lines / deleted elsewhere / not saved + reason); archived pill; members section (`MemberRow` + `RolePill`, "You"); owner actions (`AppListItem` rows: Edit, Archive, Transfer ownership, Delete as `DestructiveButton`); "No transactions yet." `EmptyState` (C6 replaces it); confirm dialogs (`AppAlertDialog`); transfer picker (`AppBottomSheet`) |
| Wallet editor | type choice (`ChoiceGrid`, create only; read-only row on edit); name (`AppTextField`); currency (`AppSelectField`, read-only + note when locked); amount (`AppTextField`, decimal keyboard, label "Opening balance" or "Amount owed"); icon and colour (`ChoiceGrid`); `PrimaryButton` Save |

## Test tags

| Element | Tag |
|---|---|
| Home row | `home.wallets` |
| List root / FAB / empty state | `wallets.list` / `wallets.create` / `wallets.empty` |
| Total per currency | `wallets.total.<CUR>` |
| Card (active, archived) | `wallets.card.<index>`, `wallets.archived.<index>` (0-based, display order) |
| Card status mark | `wallets.card.<index>.status` |
| Archived header | `wallets.archivedHeader` |
| Not-entitled notice | `wallets.notEntitled` |
| Detail balance / status / members | `walletDetail.balance` / `walletDetail.status` / `walletDetail.member.<index>` |
| Detail actions | `walletDetail.edit`, `walletDetail.archive`, `walletDetail.delete`, `walletDetail.transfer` |
| Clash and refusal choices | `walletDetail.keepMine`, `walletDetail.useTheirs`, `walletDetail.acceptDeletion`, `walletDetail.deleteAnyway`, `walletDetail.keepWallet`, `walletDetail.tryAgain`, `walletDetail.discard` |
| Confirm dialog buttons | `walletDetail.confirm`, `walletDetail.cancel` |
| Editor fields | `walletEditor.type.<wire>`, `walletEditor.name`, `walletEditor.currency`, `walletEditor.amount`, `walletEditor.icon.<key>`, `walletEditor.color.<index>`, `walletEditor.save` |
| Field errors | `walletEditor.<field>.error` |

## Copy (new keys, English and Arabic)

`wallets_title` ("Wallets"), `home_wallets` ("Wallets"), `wallets_new` ("New wallet"),
`wallets_empty_title` ("No wallets yet"), `wallets_empty_body` ("Create your first wallet to start
tracking your money."), `wallets_total_caption` (plural "%1$d wallets"), `wallets_archived`
("Archived"), `wallets_updated_at` ("Updated %1$s"),
`wallet_type_cash` ("Cash" / "نقدي"), `wallet_type_checking` ("Checking" /
"بنكي"; not "حساب", which reads as "account", a forbidden synonym for wallet under P-01.2 — owner review), `wallet_type_savings` ("Savings" / "حصالة"), `wallet_type_credit_card` ("Credit card"),
`wallet_type_loan` ("Loan"), `wallet_type_investment` ("Investment"), `wallet_type_unknown` ("Other"),
`wallets_refresh_failed` ("Couldn't update your wallets. Showing what's on this phone."),
`wallet_gone` ("This wallet is no longer available."), `wallet_transfer_confirm` ("Transfer"),
`wallet_field_type`, `wallet_field_name`, `wallet_field_currency`, `wallet_field_opening_balance`
("Opening balance"), `wallet_field_amount_owed` ("Amount owed"), `wallet_field_icon`,
`wallet_field_color`, `wallet_none` ("None"), `wallet_currency_locked_note` ("The currency can't
change once transactions exist."), `wallet_error_name_blank`, `wallet_error_name_long`,
`wallet_error_amount_invalid`, `wallet_error_amount_digits` ("Use at most %1$d decimal places"),
`wallet_error_currency_locked`, `wallet_error_type_immutable`,
`wallet_status_waiting` (reuse spec 004 copy "Waiting to sync"), `wallet_status_clash` ("Changed on
another device."), `wallet_status_not_saved` ("Not saved."), `wallet_this_phone` ("This phone: %1$s"),
`wallet_other_device` ("Other device: %1$s"), `wallet_keep_mine` ("Keep mine"), `wallet_use_theirs` ("Use the other
device’s"; same wording as spec 002's profile keys), `action_delete_anyway` ("Delete anyway"), `action_keep_wallet` ("Keep the
wallet"), `action_try_again` ("Try again"), `action_discard` ("Discard"),
`wallet_refusal_not_verified` ("Verify your email address to save this wallet."),
`wallet_refusal_not_owner` ("Only the wallet's owner can change it."), `wallet_refusal_gone` ("This
wallet is no longer available to you."), `wallet_archive` ("Archive"), `wallet_archive_title`
("Archive %1$s?"), `wallet_archive_body` ("Archived wallets keep their balance and members and stay
editable. This can't be undone."), `wallet_delete` ("Delete wallet"), `wallet_delete_title`
("Delete %1$s?"), `wallet_delete_body` ("Deleting is final. The wallet and everything recorded in it
will be removed for everyone."), `wallet_edit` ("Edit"), `wallet_members` ("Members"), `member_you`
("You"), `member_unnamed` ("Member without a name"), `wallet_transfer` ("Transfer ownership"),
`wallet_transfer_body` ("%1$s becomes the owner. You'll become an editor."),
`wallet_transfer_offline` ("Transferring ownership needs a connection."),
`wallet_transfer_not_member` ("That person is no longer a member of this wallet."),
`wallet_transfer_changed` ("This wallet changed. Check it and try again."),
`wallet_no_transactions` ("No transactions yet."), `cd_wallet_icon_<key>`, `cd_wallet_color_<index>`.
The `role_owner/editor/viewer` and spec 004 `sync_deleted_elsewhere`, `action_accept_deletion`
keys are reused. Arabic uses محفظة, حصالة, Western digits; no "token", "version" or "tombstone".

## E2E (`.maestro/006-wallets/`, `/speckit-e2e`)

Seeds: `verified-user`, `unverified-user` (existing). Second session through `common/api.js` slot `B`
(same account) with new actions `createWallet`, `getWallet`, `saveWallet`, `archiveWallet`,
`deleteWallet` (`http.request` method `DELETE` with JSON body), `listWallets`. Runner actions
between flow parts (on top of C3's `# e2e-between:`): `network-off`, `network-on` (research R-17).

| Scenario | Automatable | How / reason |
|---|---|---|
| US1-S1, S2, S4, S5, S6 | yes | UI only (S4 switches app language to Arabic) |
| US1-S3 | yes (3 parts) | `network-off` → create → "Waiting to sync" → `network-on` → mark clears, `getWallet` B finds it |
| US1-S7 | manual | `external`: no launch plan limits `wallets.max`; needs a data change |
| US1-S8 | yes | `unverified-user` seed → verify prompt shown |
| US2-S1, S2, S6, S7 | yes | wallets seeded through slot B before the list opens |
| US2-S3 | yes (3 parts) | offline opening-balance edit → mark and adjusted balance |
| US2-S4 | yes | slot B `saveWallet` → pull to refresh → new name |
| US2-S5 | yes | slot B `deleteWallet` → refresh → card gone |
| US2-S8 | yes (2 parts) | `network-off` → list and detail still shown with "Updated …" |
| US3-S1, S2, S6 | yes | UI + slot B check |
| US3-S3, S4 | manual | `not-built`: locking a currency needs a recorded transaction (C6 / B6 seeding) |
| US3-S5 | yes | detail open, slot B saves, phone edits → clash card with both names |
| US3-S7 | yes | detail open, slot B deletes, phone edits → "Accept the deletion" only |
| US3-S8 | manual | `not-built`: needs an editor/viewer member (C9) |
| US4-S1..S4 | yes | UI + slot B check (`archived_at`, 404/tombstone) |
| US4-S5 | yes (3 parts) | offline create + delete, `network-on`, slot B `listWallets` has no such wallet |
| US4-S6 | yes | slot B saves, phone deletes → "Delete anyway" / "Keep the wallet" |
| US5-S1, S2 | yes | members section: "You", "Owner"; no transfer action |
| US5-S3, S4, S5 | manual | `not-built`: needs a second member (C9); covered by store and contract tests |

If `network-off` cannot cut the route on the test device, the offline parts fall back to manual
(`external`) and are proved by unit and persistence tests (spec §8).
