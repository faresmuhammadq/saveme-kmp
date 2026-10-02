# Contract: `WalletApi` (feature.wallets.data)

All credentialed, under `/api/v1`, through the shared `ApiClient` (headers of P-02.5 added by
`HttpClientFactory`). Shapes: data-model §2. Every member here is covered by a contract test on a
Ktor `MockEngine` with fixtures copied from `wallets.yaml` and the backend spec 010 QA evidence
(P-14.3).

| Function | Request | Returns | Contract-test cases |
|---|---|---|---|
| `list(cursor: String?)` | `GET wallets?limit=50[&cursor=]` | `ApiResult<Page<WalletDto>>` | 200 two pages (cursor passed back verbatim); empty `data: []`; unknown member ignored; unknown `type` value decodes (kept as raw string); offline → `Offline`; 401 → `ApiEvent.SessionEnded` |
| `get(id)` | `GET wallets/{id}` | `ApiResult<WalletDto>` | live; tombstone (`deleted_at` set); 404 `NOT_FOUND` |
| `put(id, WalletWriteRequest)` | `PUT wallets/{id}` JSON | `ApiResult<WalletDto>` | body has exactly `type,name,currency,opening_balance_minor,icon,color,version` with `icon`/`color` as explicit `null`; 201 create; 200 save; 200 replay; 409 `VERSION_CONFLICT` with live `current`; 409 with tombstone `current`; 422 `VALIDATION_FAILED` with `IMMUTABLE_FIELD@/currency`, `IMMUTABLE_FIELD@/type`, `OUT_OF_RANGE@/name`; 403 `FORBIDDEN`; 403 `EMAIL_NOT_VERIFIED` (publishes `ApiEvent.EmailNotVerified`); 403 `FEATURE_NOT_ENTITLED` (publishes C3's event); 404; 503 with `retry_after_seconds` |
| `archive(id, version)` | `POST wallets/{id}/archive` `{"version":n}` | `ApiResult<WalletDto>` | 200 archived; 200 already archived; 409; 403 |
| `delete(id, version)` | `DELETE wallets/{id}` **with** JSON body `{"version":n}` and `Content-Type: application/json` | `ApiResult<WalletDto>` | body present on DELETE; 200 tombstone; 200 repeat; 409 live `current`; 403; 404 |
| `members(id, cursor)` | `GET wallets/{id}/members?limit=50[&cursor=]` | `ApiResult<Page<WalletMemberDto>>` | 200 with `display_name: null`; two pages; 404 |
| `transferOwnership(id, userId, version)` | `POST wallets/{id}/transfer-ownership` `{"user_id","version"}` | `ApiResult<WalletDto>` | 200 (`my_role: editor`); 422 `INVALID_ENUM_VALUE@/user_id`; 409; 403 |

Not called: `PATCH wallets/{id}` (research R-05).

Retry and failure handling follow the existing `ApiClient`: credentialed reads use its retry policy;
writes made through the sync engine are never retried by the client itself (the engine owns
backoff, spec 004); `transferOwnership` is never retried automatically.

## `WalletSyncHandler` (feature.wallets.data, `SyncHandler`)

| Member | Value / behaviour |
|---|---|
| `recordType` | `"wallet"` |
| `canRestore` | `false` |
| `send` | research R-04 (DELETE / PUT-then-archive; PUT skipped when unchanged) |
| `applyServerCopy` | upsert, or remove on tombstone with members + `WalletDeletionParticipant`s |
| `onDiscarded` | remove a `version = 0` placeholder and its members |
| `refusalFields` | `/name`, `/currency`, `/type`, `/opening_balance_minor`, `/icon`, `/color` → `WalletField.name` |

Koin: `single { WalletSyncHandler(get(), get(), getAll()) } bind SyncHandler::class` in `walletsModule`.

Handler tests (commonTest, fake `WalletApi`): create sends PUT with version 0; edit sends PUT with
the base; archive-only sends no PUT and archives with the base; edit+archive sends PUT then archive
with the PUT's version; a PUT failure never archives; delete sends DELETE with the base; an
unknown icon/colour from the server is sent back unchanged; refusal pointers map to fields.
