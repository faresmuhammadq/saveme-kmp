# Data Model: Sync Engine

## 1. Tables (Room, schema v2)

### `pending_change` (new, `core.sync`)

| Column | Type | Rules |
|---|---|---|
| `record_type` | TEXT, PK part | handler key, e.g. `profile` |
| `record_id` | TEXT, PK part | the record's UUIDv7 id |
| `account_id` | TEXT | user id that made the change; sends require it to equal the signed-in user (R-07) |
| `scope_kind` | TEXT | `user` or `wallet` |
| `scope_id` | TEXT | user id or wallet id |
| `base_version` | INTEGER | last server version the person saw; `0` = never sent (create) |
| `payload_json` | TEXT | the record's full content as the handler encodes it |
| `is_delete` | INTEGER (bool) | sent as a deletion |
| `restore` | INTEGER (bool) | send `deleted_at: null` (Restore with my change, R-05) |
| `revision` | INTEGER | bumped on every local edit; guards in-flight sends (R-02) |
| `created_order` | INTEGER | monotonically increasing; drain order across records |
| `state` | TEXT | `Waiting` · `Sending` · `Clash` · `Refused` |
| `attempts` | INTEGER | failed tries since the last success or edit |
| `next_attempt_at` | TEXT (UTC instant) or NULL | not before this |
| `clash_json` | TEXT or NULL | server `current` when `state = Clash` |
| `refusal_json` | TEXT or NULL | `{code, errors[]}` when `state = Refused` |
| `updated_at` | TEXT (UTC instant) | last local change to the row |

### `profile` (changed)

Drops `pending_json`, `pending_base_version`, `conflict_json`, `rejection_json` (moved to
`pending_change`, R-08). Other columns unchanged.

### `sync_scope` (changed)

Adds `last_pushed_at` TEXT (UTC instant) or NULL: last successful send in that scope (R-10).

## 2. State transitions of a waiting change

```text
            enqueue (local edit)
                   │
                   ▼
   ┌──────────► Waiting ◄───────────────┬─────────────── edit (from Refused / Clash kept by editing)
   │               │ drain              │
   │               ▼                    │ keepMine (rebase on current.version; restore if tombstone)
   │            Sending ──409──► Clash ─┘
   │               │    ──422/404/403/405──► Refused ──discard──► (row deleted, onDiscarded)
   │  offline/timeout/429/5xx               │
   └───────────────┘ (attempts+1, next_attempt_at by backoff)
                   │ 200/201, revision unchanged
                   ▼
            (row deleted, applyServerCopy, sync_scope.last_pushed_at = now)
                   │ 200/201, revision changed during send
                   ▼
            Waiting with base_version = returned version

Clash ──useTheirs──► (applyServerCopy(current), row deleted, onDiscarded)
Any state ──sign-out──► (table emptied)
```

`Sending` rows found at start-up (process killed mid-send) return to `Waiting`; a repeat is safe
(backend Writes: replay).

## 3. Domain types (`core.sync`)

| Type | Fields / members |
|---|---|
| `PendingChange` | the row above as an immutable value |
| `ChangeState` | `Waiting`, `Sending`, `Clash`, `Refused` |
| `SyncStatus` | `waiting: Int`, `needsDecision: Int` (Clash + Refused), `lastSyncedAt: Instant?` |
| `Refusal` | `code: String`, `fields: Map<String, String>` (from the handler's `refusalFields`) |
| `SyncHandler` | see contracts/sync-engine.md |
| `SyncScheduler` | `enqueue(delay: Duration = ZERO)`, `cancel()`; Android: WorkManager, iOS: no-op |

## 4. Profile view (unchanged shape)

`ProfileView(server, pending, conflict, rejection, refreshedAt)` stays as spec 002 defines it; it is
now built from the `profile` row plus the `pending_change` row for `("profile", profile.id)`:
`pending` ← `payload_json`, `conflict` ← `clash_json` when `Clash`, `rejection` ←
`refusal_json` fields when `Refused`.
