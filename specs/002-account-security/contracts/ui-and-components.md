# Contract: Screens, navigation and new components

## Navigation (`navigation/Routes.kt`, `AppNavHost.kt`, signed-in stack)

| Key | Screen | Reached from |
|---|---|---|
| `AccountKey` | Account hub | Home top bar "Account" icon |
| `ProfileKey` | Profile | Account → Profile |
| `DeviceSessionsKey` | Devices signed in | Account → Devices signed in |
| `ChangePasswordKey` | Change password | Account → Change password |
| `DeleteAccountKey` | Delete account (explain → confirm → scheduled) | Account → Delete account |

Sign out stays on Account (and Home) using spec 001's confirm sheet. All keys are `@Serializable NavKey`s pushed onto `appStack`; back pops.

## Home changes

- Top bar action: `AppIconButton(AppIcons.Person, cd_account)` → `AccountKey`.
- When `account.state == PendingDeletion`: `InlineAlert(Warning, "Your account will be deleted on {date}", actionLabel = "Cancel deletion")` at the top, always visible (FR-019).

## New design-system components (`designsystem/component/`)

| Component | Signature (shape) | Guarantees |
|---|---|---|
| `AppAlertDialog` | `(title, text, confirmLabel, onConfirm, dismissLabel, onDismiss, destructive = false, confirmLoading = false)` | confirm uses `DestructiveButton` tone when destructive; RTL; font scale 2 wraps; 48 dp targets |
| `AppListItem` | `(headline, modifier, supporting?, leadingIcon?, trailing: ListTrailing = None/Chevron/Text(s)/Custom, onClick?)` | whole row is one touch target ≥ 48 dp; chevron mirrors in RTL; merges semantics |
| `AppSelectField` | `(label, value, onClick, modifier, supportingText?, errorText?, enabled)` | looks like `AppTextField`, read-only, announces as button |
| `SelectionSheet<T>` | `(title, options, label: (T)->String, selected, onSelect, onDismiss, searchable = true)` | `AppBottomSheet`; search field filters; selected item checked |

New `AppIcons`: `Person`, `Devices`, `Lock`, `Delete`, `Logout`, `Language`. Each component gets a section in `CatalogSections.kt` and so screenshot cases in en / ar-RTL / font-scale-2.

## Copy (new string keys, en + ar)

`account_title`, `account_profile`, `account_devices`, `account_change_password`, `account_delete`, `account_sign_out`; `sessions_title`, `sessions_this_device`, `sessions_signed_in_at`, `sessions_last_used`, `sessions_not_used_yet`, `sessions_end`, `sessions_end_others`, `sessions_confirm_end_title/body`, `sessions_confirm_end_others_title/body`, `sessions_none_other`, `sessions_updated_at`, `sessions_ended_done`; `change_password_*` (title, current, new, submit, done_other_devices, error_current_incorrect); `profile_*` (title, email, verified/unverified, display_name, language, timezone, currency, currency_locked, budget_day, budget_day_from, save, saved, waiting_to_sync, conflict_title, conflict_body, keep_mine, use_theirs, mine_label, theirs_label); `delete_*` (title, explain_body, continue, password, confirm, scheduled_body, error_password_incorrect); `home_deletion_pending`, `home_cancel_deletion`, `home_deletion_cancelled_done`; `field_error_immutable`; `cd_account`. UI never says "token" (FR-026).
