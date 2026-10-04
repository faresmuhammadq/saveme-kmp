# Quickstart: Sync Heads Adoption (client)

How to validate this feature. Owner policy: no emulator, Maestro or device runs here; steps 1–2
are host-only and live-backend curl, step 3 is deferred to the owner's phone.

## Prerequisites

- `saveme-backend` dev container running (port 43871) at or after `c0406b0` (`sync.yaml` v1.10),
  migrations applied, default categories seeded, **a queue worker running** (renames are re-sent by
  a queued job).
- Two accounts registered through the API (factory users have no profile; spec 009 note 21), one
  sharing a wallet with the other (spec 016 invitations).

## 1. Host gates

```bash
./gradlew :shared:testAndroidHostTest             # heads decision, puller, runner, engine pair, Room 7→8
./gradlew :shared:verifyRoborazziAndroidHostTest  # no golden changes expected (no UI change)
./gradlew :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64
```

Expected: green; `shared/schemas/…/8.json` exported; no golden changed.

## 2. Contract QA against the live local backend (curl)

App headers on every request (`X-Client-Platform: android`, `X-Client-Version`, `Accept-Language`,
`X-Correlation-Id`). Real answers become fixtures in `CapturedSyncResponses.kt`.

| Check | Expected |
|---|---|
| `GET /sync/wallets/heads?limit=200` for a person in 3 wallets | 200, 3 entries ordered by `wallet_id`, `has_more: false` |
| Same with `limit=1`, following `next_cursor` | 3 pages, same entries |
| `GET /sync/wallets/heads?sort=wallet_id` / `?filter[x]=1` / `?cursor=garbage` | 400 `SORT_FIELD_NOT_SUPPORTED` / `FILTER_FIELD_NOT_SUPPORTED` / `INVALID_CURSOR` |
| Read each wallet feed to the end; compare `meta.sync_position` with its head | equal (a quiet refresh would skip all) |
| Add a transaction in one wallet from the other account; heads again | that wallet's `change_seq` > stored position; others equal |
| Partner renames (`PUT /me/profile` display_name); wait for the queue; read the wallet feed from the stored cursor | one `wallet_member` row, same `version`, new `display_name`; head moved |
| Batch: delete a transaction from account B, then from A `restore` at n + `upsert` at n + 1 | both `applied`; record live, version n + 2, A's content |
| Same pair sent again (lost answer replay) | record the real answers (R-10); no duplicate, no further bump expected |
| Pair where the transaction was edited after deletion by B | item 1 `conflict` 409 with `current`; item 2 `conflict` |
| Pair with an invalid amount in item 2 | item 1 `applied`, item 2 `rejected` 422 |
| Age a wallet's tombstones and run `sync:purge-tombstones` (QA rows only), then read that wallet from no cursor at `limit=1`, `2`, `5` | pages to the end, no 410 |
| Heads `purged_change_seq` for that wallet vs an old stored position | greater → the client reads from the start without a 410 |

## 3. Device (deferred: E2E on owner's device)

- Ten wallets, foreground after a minute: two requests in the proxy/log.
- Partner rename visible in the members list after a refresh.
- "Restore with my change" with other edits waiting leaves in one upload.
