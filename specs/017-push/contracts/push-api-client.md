# Contract (client side): Devices & Notification Preferences API

Source of truth: `saveme-backend/docs/openapi/paths/notifications.yaml` v1.14 (backend spec 023,
uncommitted at `e8c308a`) and `specs/023-devices-push/contracts/push-api.md`. This page states what
the client sends and how it reads each answer. Shapes are not restated.

## `NotificationsApi` (`feature/notifications/data/NotificationsApi.kt`)

All calls through `ApiClient` (standard headers, fresh correlation id, P-02.5); plain values in and
out (P-10.6).

| Method | HTTP | Body | Returns |
|---|---|---|---|
| `registerDevice(sessionId: String, token: String)` | `PUT me/devices/{sessionId}` | `{"push_token": token}` | `ApiResult<PushRegistrationDto>` |
| `unregisterDevice(sessionId: String)` | `DELETE me/devices/{sessionId}` | — | `ApiResult<Unit>` (204) |
| `preferences()` | `GET me/notification-preferences` | — | `ApiResult<NotificationPreferencesDto>` |
| `savePreferences(write: NotificationPreferencesWriteDto)` | `PUT me/notification-preferences` | whole object with `version` | `ApiResult<NotificationPreferencesDto>` |

No `Idempotency-Key` (both writes are idempotent by construction). No automatic retry inside the
API; the registration job retries per the table below; the preferences screen never retries.

## Outcome table

| Call | Answer | Client outcome |
|---|---|---|
| register | 200 | record saved → `Registered` |
| register | 404 `NOT_FOUND` | re-read session once, else `Stopped` (FR-005) |
| register | 429 `RATE_LIMITED` | `RetryAfter(Retry-After or retry_after_seconds, default 60 s)` |
| register | offline, timeout, 500 | `RetryLater` (WorkManager backoff) |
| register | 422 `VALIDATION_FAILED`, 400, 406, 415 | `Stopped` (contract bug; outcome logged by name) |
| register / unregister / preferences | 401 | app-wide session end (P-09.5) |
| any | 426 | app-wide update-required |
| unregister (job) | 204, 404 | record cleared → `Unregistered` |
| unregister (job) | 429 / offline / 500 | `RetryAfter` / `RetryLater` |
| unregister (sign-out participant) | anything, or 2 s elapsed | ignored |
| preferences GET | 200 | `Loaded` |
| preferences GET | offline, timeout | `Offline` |
| preferences GET | other | `Failed(request_id)` |
| preferences PUT | 200 | `Saved(answer)` |
| preferences PUT | 409 `VERSION_CONFLICT` | `Conflict(current)` (unreadable `current` → `Failed`) |
| preferences PUT | 429 `RATE_LIMITED` | `RateLimited(seconds?)` |
| preferences PUT | 422 `VALIDATION_FAILED` | `Invalid(request_id)` → general failure with id |
| preferences PUT | offline, timeout | `Offline` (edits kept) |
| preferences PUT | other | `Failed(request_id)` |

## Contract tests (MockEngine, `commonTest/.../feature/notifications/data/`)

Seeded from the `notifications.yaml` examples (`registered`, `rotated`, `defaults`,
`quieterNights`, `saved`, the five `PushPayload` examples):

1. register: method `PUT`, path `/api/v1/me/devices/{session id}`, JSON body exactly `{"push_token": "…"}` (no other member), standard headers; 200 decodes; an extra unknown member in the answer is ignored.
2. register 404, 422, 429 (`Retry-After` and `retry_after_seconds`), 500 → outcomes above.
3. unregister: `DELETE`, no body, 204 → `Unit`; 404 → `NOT_FOUND`.
4. preferences GET: the `defaults` example decodes to version 1, all on, 22:00–08:00, `updated_at` null.
5. preferences PUT: body from `quieterNights` (every key present, no `updated_at`), 200 `saved` decodes; 409 with `current` decodes `current` with the same DTO; 422 with `errors[].pointer` `/quiet_hours/start`; 429.
6. Payload: the five `PushPayload` examples parse to their `PushMessage` cases (domain test, R-03).

## Live QA (after backend spec 023 is committed)

curl against the local backend (`127.0.0.1:43871`), throwaway accounts `qa017-<a..c>-*@example.com`:
register with a fake token (`dev-fake-push-<uuid>`) → 200; same again → 200, `refreshed_at`
moves; another token → 200 (rotation); `PUT` with another session's id → 404; with
`platform` in the body → 422 `UNKNOWN_FIELD`; 21st write in an hour → 429 with `Retry-After`;
`DELETE` → 204, again → 204; GET defaults; PUT with version 1 → 200 version 2; PUT with version 1
again → 409 + `current`; `start == end` → 422 at `/quiet_hours/start` or `/quiet_hours/end`; 31st save
→ 429. Sign out the session → the registration is gone (check through the backend's admin count or
database, read-only). Answers captured (tokens replaced by fakes of the same shape) into
`CapturedNotificationsResponses.kt` and replayed by `NotificationsLiveReplayTest` through the real
`ApiClient`, `NotificationsApi`, services and stores.
