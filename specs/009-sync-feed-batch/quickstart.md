# Quickstart: Sync Feeds and Batch Upload (client)

How to validate this feature. Owner policy: no emulator, Maestro or device runs here; steps 1–3
are host-only, step 4 is deferred to the owner's phone.

## Prerequisites

- `saveme-backend` dev container running (port 43871), at or after `6b8e290` (`sync.yaml` v1.6+),
  migrations applied, default categories seeded.
- `devApiHost` set (README "Development backend") for step 4 only.

## 1. Host gates

```bash
./gradlew :shared:testAndroidHostTest          # engine, puller, appliers, Room, migration 5→6
./gradlew :shared:verifyRoborazziAndroidHostTest  # no screen changes expected (SC-008)
./gradlew :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64
```

Expected: green; `shared/schemas/…/6.json` exported and committed; no golden changed.

## 2. Contract QA against the live local backend (curl)

With a fresh verified account (bearer from `POST /auth/login`) and the app's headers
(`X-Client-Platform: android`, `X-Client-Version`, `Accept-Language`, `X-Correlation-Id`):

| Check | Expected |
|---|---|
| `GET /sync/user/changes?limit=200` with no cursor | `200`, profile + device session rows, `sync_cursor` non-null |
| Same with the returned cursor | `200`, `data: []`, same position |
| `POST /sync/batch` with a wallet create, a category create, a transaction in that wallet using that category (in that order) | three `applied`, `201` |
| Same batch again | three `applied`, versions unchanged |
| Batch item with a stale version | `conflict`, `409`, `problem.current` |
| Batch item with an invalid amount | `rejected`, `422`, pointer `/amount_minor` |
| Batch with a transaction in an unknown wallet | `rejected`, `404` |
| Batch with 0 or 51 items | request `422`, `OUT_OF_RANGE` at `/items` |
| `GET /sync/wallets/{id}/changes` for the new wallet | wallet, own member, transaction rows; wallet re-sent after each change |
| Transfer between two wallets | identical record on both wallet feeds |
| `GET /sync/user/changes?cursor=garbage` | `400 INVALID_CURSOR` |
| Wallet feed for another account's wallet | `404 NOT_FOUND` |
| Delete a wallet, then the user feed from the stored cursor | the own `wallet_member` row tombstoned |
| Aged tombstones purged on the dev stack (backend QA method), then an old cursor | `410 SYNC_CURSOR_EXPIRED` |

Save each real answer into `CapturedSyncResponses.kt` and run the captured-fixture tests through the
real engine, puller and appliers.

## 3. Scenario checks on the host

The acceptance scenarios of US1–US5 each map to a named host test (tasks.md); run them with
`--tests '*SyncEngineBatch*' '*FeedPuller*' '*SyncRunner*' '*Applier*'`.

## 4. Deferred: E2E on the owner's device

Maestro flows under `.maestro/009-sync-feed-batch/` (written, **not run**): the brief's checkpoint —
20 offline edits across entities sync in order after reconnecting; a forced conflict shows the
choice; kill the app mid-sync and it resumes — plus a wallet created and deleted from a second
session appearing and leaving within one refresh. Each flow header says
`# deferred: E2E on owner's device`.
