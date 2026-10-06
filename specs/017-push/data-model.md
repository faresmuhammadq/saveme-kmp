# Data Model: Push Notifications (client)

Phase 1 for [plan.md](plan.md). **No Room change** (stays v10): nothing here is syncable (P-04.6;
backend: Push Registration and Notification Preferences are "Sync: no"). Wire shapes are in
`saveme-backend/docs/openapi/paths/notifications.yaml` v1.14 and are not restated beyond what the
client binds to.

## 1. Wire DTOs (`feature/notifications/data/dto/`)

| DTO | Members (wire names) | Notes |
|---|---|---|
| `PushRegistrationWriteDto` | `push_token: String` | The only member (FR-039); `toString` redacted |
| `PushRegistrationDto` | `session_id`, `registered_at`, `refreshed_at` | Read and ignored except for the outcome; unknown members ignored |
| `NotificationCategoriesDto` | `recurring_due`, `budget_exceeded`, `sharing`, `billing`, `export_ready`, `deletion_reminder` (all `Boolean`) | Every key required on write |
| `QuietHoursDto` | `enabled: Boolean`, `start: String`, `end: String` (`HH:MM`) | |
| `NotificationPreferencesDto` | `version: Long`, `categories`, `quiet_hours`, `updated_at: String?` | GET/PUT answer and `ProblemDetails.current` on 409 |
| `NotificationPreferencesWriteDto` | `version`, `categories`, `quiet_hours` | No `updated_at` (FR-039) |

## 2. Domain types (`feature/notifications/domain/`)

| Type | Shape | Rules |
|---|---|---|
| `NotificationCategory` | enum: `RecurringDue`, `BudgetExceeded`, `Sharing`, `Billing`, `ExportReady`, `DeletionReminder`; `wire` = the category id | Order = screen order (FR-015) |
| `QuarterHour` | value class `minutes: Int` | 0..1425, multiple of 15; `parse("HH:MM")`, `format()`; invalid → `null` |
| `QuietHours` | `enabled`, `start: QuarterHour`, `end: QuarterHour` | `isValid = start != end` |
| `NotificationPreferences` | `version: Long`, `categories: Map<NotificationCategory, Boolean>` (all six), `quietHours`, `updatedAt: Instant?` | Defaults (screen never invents them; read from the server) |
| `PushMessage` | sealed, one case per type (research R-03) + `Malformed(type)` + `Unknown`; each with `notificationId: String?`, `category: NotificationCategory?` | `toString` = type only; `collapseKey` per contract table |
| `PushRead` | `None`, `User`, `Wallet(id)`, `Everything`, `Entitlements`, `Account` | |
| `PushTarget` | `Occurrence(id)`, `RecurringAll`, `Budget(id)`, `Budgets`, `InvitationNotice`, `Wallet(id)`, `Wallets`, `Plan`, `Export(id)`, `Exports`, `AccountDeletion`, `Home` | Ids are validated UUID strings |
| `PushRoute` | `read: PushRead`, `target: PushTarget` | From `PushRouter` (spec FR-031 table) |
| `ReconcileTrigger` | `SignedIn`, `TokenChanged`, `PermissionChanged`, `Foreground` | Only `Foreground` honours the 24 h skip |
| `ReconcileOutcome` | `Nothing`, `Registered`, `Unregistered`, `Stopped`, `RetryLater`, `RetryAfter(seconds)` | Worker maps to WorkManager results |
| `PostedNotification` | `channel: String`, `tag: String`, `title`, `body`, `tap: Map<String, String>` | `toString` = channel only |
| `ChannelLabels` | name + description per category | From string resources |
| `PermissionStatus` | `Allowed`, `Askable`, `Blocked` | UI seam (research R-14) |

## 3. Phone records (key-value store, `feature/notifications/data/`)

| Key | Content | Written | Cleared |
|---|---|---|---|
| `push_registration` | JSON `{user_id, session_id, fingerprint, registered_at, stopped_for?}` | after a 200 (or the `NOT_FOUND` stop marker) | 204/404 on `DELETE`; sign-out; session end |
| `push_shown_ids` | JSON list of up to 100 `notification_id` strings, newest last, with the `user_id` they belong to | each foreground post | sign-out; session end; another `user_id` |
| `push_prompt_offered` | `"true"` | when the explanation sheet is first shown | never (per installation) |
| `push_permission_asked` | `"true"` | first runtime request | never (per installation) |
| `dev_fake_push_token` | the dev fake token | dev builds only, first read | `discard()` (sign-out), rotate |

The push token itself is never written by the app (FR-004); `fingerprint` is FNV-1a 64 hex of it.

## 4. Lifecycles

**Registration (per signed-in session)**

```text
none ──(allowed & token & trigger)──▶ PUT ──200──▶ registered(fp, at)
registered ──(Foreground & <24h & same fp & same session)──▶ registered (no call)
registered ──(new token | ≥24h | other session | non-Foreground trigger)──▶ PUT ──200──▶ registered
registered ──(not allowed)──▶ DELETE ──204/404──▶ none
PUT ──404──▶ re-read session ──differs──▶ PUT (once) │ same ──▶ stopped_for(session) until sign-in/new token
PUT ──429──▶ retry after Retry-After │ offline/5xx ──▶ retry with backoff │ 422 ──▶ stopped
sign-out ──▶ DELETE (≤2 s, best effort) ──▶ server sign-out ──▶ cleaners: cancel all, clear records, discard token
session ended remotely ──▶ cleaners only
```

**Preferences (screen state)**

```text
Loading ──200──▶ Ready(read = edited = answer)
Loading ──offline/failed──▶ NotLoaded(message) ──Try again──▶ Loading
Ready ──edit──▶ Ready(dirty) ──Save──▶ Saving ──200──▶ Ready(read = edited = answer, "Saved")
Saving ──409──▶ Ready(read = edited = current, conflict message)
Saving ──429/offline/422/other──▶ Ready(edits kept, message)
```

**Tap**

```text
intake (launcher extras / foreground PendingIntent) ──▶ PushTapBus ──signed out──▶ dropped
signed in ──▶ parse ──▶ route ──read None──▶ target screen
                       └─ read needed ──record present──▶ target screen + background read
                                       └─ otherwise ──▶ "Opening…" ──read done/failed/offline/10 s──▶ target screen (replaces)
```

## 5. Channels (Android 8+)

| Channel id | Name (EN / AR) | Importance | Lock screen |
|---|---|---|---|
| `recurring_due` | Recurring items due / عناصر متكررة مستحقة | default | private |
| `budget_exceeded` | Budget alerts / تنبيهات الميزانية | default | private |
| `sharing` | Shared wallets / المحافظ المشتركة | default | private |
| `billing` | Plan and billing / الخطة والفوترة | default | private |
| `export_ready` | Exports / التصدير | default | private |
| `deletion_reminder` | Account deletion / حذف الحساب | default | private |
