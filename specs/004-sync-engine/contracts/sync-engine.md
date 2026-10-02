# Contract: Sync engine (Kotlin API inside `:shared`)

Consumers are feature modules; they depend on `core.sync` only (P-10).

## SyncEngine

| Member | Behaviour |
|---|---|
| `suspend fun enqueue(type, id, scope, baseVersion, payloadJson)` | Upsert the record's row (R-02): new row → `Waiting`, base = `baseVersion`; existing row → new content, keep base, bump `revision`, back to `Waiting` (clears Refused). Then `scheduler.enqueue()` |
| `suspend fun enqueueDelete(type, id, scope, baseVersion)` | As above with `is_delete`; if the row's base is `0`, delete the row instead (FR-004) |
| `suspend fun drain()` | Load the saved session if none is known (`CurrentAccount.load()`), reset stale `Sending` rows, then send due `Waiting` rows of the signed-in account per R-03/R-05/R-06; if rows remain that aren't due yet, schedule a run for the earliest |
| `suspend fun keepMine(type, id)` | `Clash` → rebase on `current.version`, set `restore` if `current` is a tombstone, `Waiting`, schedule |
| `suspend fun useTheirs(type, id)` | `Clash` → handler `applyServerCopy(current)`, delete row, `onDiscarded` |
| `suspend fun discard(type, id)` | Any state → delete row, `onDiscarded` |
| `suspend fun syncNow()` | Reset `next_attempt_at` on due-later `Waiting` rows, `scheduler.enqueue()` |
| `fun observe(type, id): Flow<PendingChange?>` | One record's row |
| `val status: Flow<SyncStatus>` | Counts and last sync time (R-10) |

## SyncHandler (one per record type, registered in Koin)

| Member | Behaviour |
|---|---|
| `val recordType: String` | Key stored in `pending_change.record_type` |
| `val canRestore: Boolean` (default true) | False when the server's deletion is final (wallets, backend spec 010): `keepMine` on a tombstone clash is refused; only `useTheirs` ("Accept the deletion") |
| `suspend fun send(change: PendingChange): ApiResult<JsonElement>` | The record's per-record `PUT` with `version = change.baseVersion` (0/absent = create), `deleted_at` when `isDelete`, `deleted_at: null` when `restore`. Returns the server's record on success |
| `suspend fun applyServerCopy(json: JsonElement)` | Writes the server record into the feature's table without touching other local state |
| `suspend fun onDiscarded(recordId: String)` | Side effects of dropping the local change (profile: restore the app language) |
| `fun refusalFields(problem: ProblemDetails): Map<String, String>` | Maps `errors[].pointer` to the feature's field names |

## Classification (engine, by `code`)

| Answer | State change |
|---|---|
| Success | row deleted (or rebased if `revision` moved), `applyServerCopy`, `last_pushed_at` |
| `VERSION_CONFLICT` with `current` | `Clash`, `clash_json = current` |
| `VERSION_CONFLICT` without `current` | treated as retryable (defensive; the contract always sends it) |
| `UNAUTHENTICATED` | unchanged; session-ended flow runs |
| retry class `Automatic`, `Offline`, `Timeout` | `Waiting`, `attempts + 1`, `next_attempt_at` by backoff (offline/timeout: no delay, scheduler waits for network) |
| anything else | `Refused`, `refusal_json` |

## SyncScheduler

| Member | Android | iOS |
|---|---|---|
| `enqueue(delay)` | unique one-time work `sync-drain`, `REPLACE` only if the new delay is shorter, constraint `CONNECTED`; expedited when the delay is zero (API 31+) | no-op |
| `cancel()` | cancel unique work | no-op |

The Android worker gets the engine from Koin and calls `drain()`; it returns `retry()` only on an
unexpected exception (the engine schedules its own follow-ups).
