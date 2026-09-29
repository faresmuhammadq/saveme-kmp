# Data Model: Account Security & Profile

**Feature**: [spec.md](spec.md) · **Research**: [research.md](research.md)

## 1. Database (Room, `core.database.AppDatabase`, schema v1)

### `profile` — one row per signed-in user

| Column | Type | Notes |
|---|---|---|
| `user_id` | TEXT PK | from `/me` account id |
| `id` | TEXT | profile id (UUIDv7, server) |
| `version` | INTEGER | server version of the server copy |
| `display_name` | TEXT? | server copy |
| `locale` | TEXT | `ar` \| `en` |
| `timezone` | TEXT | IANA id |
| `default_currency` | TEXT | ISO 4217 |
| `default_currency_locked` | INTEGER (bool) | read-only |
| `budget_period_start_day` | INTEGER | 1–28 |
| `budget_period_start_day_from` | TEXT? | ISO date, read-only |
| `refreshed_at` | TEXT | UTC instant of last pull |
| `pending_json` | TEXT? | `ProfileDraft` awaiting push (null = in sync) |
| `pending_base_version` | INTEGER? | version the draft is based on |
| `conflict_json` | TEXT? | server `current` from a 409, awaiting the person's choice |
| `rejection_json` | TEXT? | field errors from a 422 on push (e.g. IMMUTABLE_FIELD) |

States (derived): **Synced** (`pending_json` null) → **Pending** (draft saved, not yet accepted) → **Synced** on 200; Pending → **Conflict** on 409 → Pending ("Keep mine", base := conflict version) or Synced ("Use the other device's"); Pending → **Rejected** on 422 → Pending after the person edits, or Synced on discard.

### `device_session`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | session id |
| `user_id` | TEXT | owner |
| `device_name` | TEXT | |
| `platform` | TEXT | |
| `app_version` | TEXT | |
| `state` | TEXT | `active` \| `revoked` \| `expired` (only active fetched) |
| `signed_in_at` | TEXT | UTC instant |
| `last_used_at` | TEXT? | UTC instant |
| `current` | INTEGER (bool) | this device |

### `sync_scope`

| Column | Type | Notes |
|---|---|---|
| `scope` | TEXT PK | `profile`, `device_sessions` |
| `refreshed_at` | TEXT | last successful pull (shown offline: "Updated …") |

Rules: a session pull replaces all `device_session` rows inside one transaction; a profile pull upserts server-copy columns only and never touches `pending_json`/`conflict_json`; sign-out runs `clearAllTables()`.

## 2. Domain models (`feature.account.domain.model`)

- `Profile(displayName: String?, locale: AppLanguage, timezoneId: String, defaultCurrency: String, currencyLocked: Boolean, budgetStartDay: Int, budgetStartDayFrom: LocalDate?, version: Long)`
- `ProfileDraft(displayName: String?, locale, timezoneId, defaultCurrency, budgetStartDay)` — the whole writable profile (ProfileWrite minus `version`).
- `ProfileView(server: Profile, pending: ProfileDraft?, conflict: Profile?, rejection: Map<ProfileField, String>, refreshedAt: Instant?)`; `effective` = pending ?: server-as-draft.
- `ProfileField { DisplayName, Locale, Timezone, DefaultCurrency, BudgetStartDay }` (pointers `/display_name`, `/locale`, `/timezone`, `/default_currency`, `/budget_period_start_day`).
- `DeviceSession(id, deviceName, platform, appVersion, signedInAt: Instant, lastUsedAt: Instant?, isCurrent: Boolean)`; `DeviceSessionList(items, refreshedAt: Instant?)`.
- `AccountSnapshot` (spec 001) — updated from `/me` and deletion endpoints via new `SessionManager.updateAccount(AccountDto)` / `updateDeletion(state, date)`.
- `AppLanguage { Ar, En }` in `core.locale`.

Outcomes (pattern of `SignInOutcome`):
- `ChangePasswordOutcome { Success, WrongCurrentPassword, Locked(seconds), Validation(fields), RateLimited(seconds), Offline, Error(code, requestId) }`
- `DeletionOutcome { Scheduled(date), WrongPassword, Locked(seconds), Validation(fields), Offline, Error }`, `CancelDeletionOutcome { Done, Offline, Error }`
- `EndSessionOutcome { Done, Offline, Error }` (NOT_FOUND ⇒ Done)
- `RefreshOutcome { Done, Offline, Error }`, `SyncOutcome { Synced, Conflict, Rejected, Offline, Error }`

## 3. Validation

- New password: 8–128 chars, ≠ account email (trimmed, case-insensitive) — reuse `ClientValidation.validateNewPassword`.
- Current password / deletion password: non-empty, ≤ 128.
- Display name: ≤ 100 after trim; empty ⇒ null.
- Budget start day: 1–28 (picker offers only these).
- Currency: `^[A-Z]{3}$`, from the known list; read-only when locked.
- Timezone: non-empty IANA id.

## 4. Screen state (MVI, P-16)

| Screen | State | Intents | Effects |
|---|---|---|---|
| Account | email, emailVerified, deletionPending(date?) | ProfileClicked, DevicesClicked, ChangePasswordClicked, DeleteAccountClicked, SignOutClicked/Confirmed/Dismissed | Navigate(target) |
| DeviceSessions | sessions, refreshedAt, refreshing, confirm: None/One(id,name)/Others, ending, banner? | Refresh, EndClicked(id), EndOthersClicked, ConfirmEnd, DismissConfirm | ShowMessage |
| ChangePassword | current, new, visibility×2, fieldErrors, submitting, lockedUntil?, banner? | …Changed, Toggle…, Submit | Done(otherDevicesSignedOut) |
| Profile | view: ProfileView, form: ProfileDraft, dirty, pickers (timezone/currency open?), savedMessage? | FieldChanged(field,value), Save, KeepMine, UseTheirs, OpenPicker, ClosePicker, Retry | ShowMessage |
| DeleteAccount | step: Explain/Confirm/Scheduled(date), password, visible, error?, submitting | Continue, PasswordChanged, ToggleVisible, Confirm, Done | Finished |
| Home (changed) | + deletionPending(date?), cancellingDeletion | + AccountClicked, CancelDeletion | NavigateAccount, ShowMessage |
