# Contract: Account API client

Source of truth: `saveme-backend/docs/openapi/paths/identity.yaml` (baseline `saveme-backend@5e5e969`; `/me` and `/me/profile` from the backend working tree, spec 008). All requests carry the credential (`Authorization: Bearer …`) and the spec 001 client headers; `Accept-Language` = effective app language (research R-06).

| Client method | Request | Success | Codes mapped |
|---|---|---|---|
| `listSessions(cursor?)` | GET `auth/sessions?filter[state]=active&limit=50[&cursor=…]` | 200 `Envelope<List<DeviceSessionDto>>` + `CollectionMeta` | UNAUTHENTICATED → session ended |
| `endOtherSessions()` | DELETE `auth/sessions` | 204 | UNAUTHENTICATED |
| `endSession(id)` | DELETE `auth/sessions/{id}` | 204 | NOT_FOUND → Done; VALIDATION_FAILED; UNAUTHENTICATED |
| `changePassword(current, new)` | POST `auth/change-password` `{current_password, new_password}` | 204 | INVALID_CREDENTIALS → WrongCurrentPassword (**no sign-out**); ACCOUNT_LOCKED (+ retry after); VALIDATION_FAILED (`/current_password`, `/new_password`); RATE_LIMITED |
| `requestDeletion(password)` | POST `account/deletion-request` `{password}` | 200 `Envelope<DeletionScheduleDto{state, deletion_scheduled_for}>` | INVALID_CREDENTIALS → WrongPassword (**no sign-out**); ACCOUNT_LOCKED; VALIDATION_FAILED (`/password`) |
| `cancelDeletion()` | DELETE `account/deletion-request` | 204 | UNAUTHENTICATED |
| `getMe()` | GET `me` | 200 `Envelope<MeDto{account, profile}>` | UNAUTHENTICATED |
| `saveProfile(draft, version)` | PUT `me/profile` `{display_name, locale, timezone, default_currency, budget_period_start_day, version}` | 200 `Envelope<ProfileDto>` | VERSION_CONFLICT → Conflict(`current` as ProfileDto); VALIDATION_FAILED incl. field IMMUTABLE_FIELD at `/default_currency` → Rejected |

Shared refusals (MALFORMED_JSON, NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE, RATE_LIMITED, CLIENT_VERSION_UNSUPPORTED → update required, INTERNAL_ERROR, SERVICE_UNAVAILABLE) map generically as in spec 001. Transport failure → Offline; timeout → Offline for writes (never retried automatically, FR-024) except profile push, which the syncer retries with backoff.

## DTOs (request bodies mirror the schema exactly — no extra members)

- `DeviceSessionDto(id, user_id, version, device_name, platform, app_version, state, end_reason?, signed_in_at, last_used_at?, ended_at?, current)`; unknown members ignored.
- `CollectionMetaDto(next_cursor?, has_more, limit)`.
- `ChangePasswordRequest(current_password, new_password)`; `PasswordConfirmation(password)`.
- `MeDto(account: AccountDto, profile: ProfileDto)`; `ProfileDto(id, user_id, version, display_name?, locale, timezone, default_currency, default_currency_locked, budget_period_start_day, budget_period_start_day_from?, …syncable members ignored)`.
- `ProfileWriteRequest(display_name?, locale, timezone, default_currency, budget_period_start_day, version)` — `display_name` is sent explicitly as `null` when cleared (field is required).

## Contract tests (MockEngine, `commonTest/feature/account/data/`)

For each method: method + path (+ query for `listSessions`); `Authorization` present; body key set exactly as above; fixtures with an unknown member decode; each listed code maps to the stated outcome; a 401 INVALID_CREDENTIALS on `changePassword`/`requestDeletion` does **not** publish `ApiEvent.SessionEnded`, while a 401 UNAUTHENTICATED on any of them does; a 409 body's `current` decodes to `ProfileDto`.
