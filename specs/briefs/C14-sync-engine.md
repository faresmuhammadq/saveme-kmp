# C14 · Sync engine (client)

**Pairs with**: backend [B14](../../../saveme-backend/specs/briefs/B14-sync-protocol.md) · **Call with**: `/speckit-specify specs/briefs/C14-sync-engine.md` (engine, wave 0) and again for the batch switch (wave 2)

## Description

One sync engine for every syncable entity. **Wave 0**: a local outbox of pending writes, pushed as
per-record `PUT`s with the last-seen `version`; per-scope pull state (extending `sync_scope`);
conflict capture that hands both versions to the feature's UI; retry with backoff; a visible
"pending sync" state. **Wave 2**: switch pull to `GET /sync/changes` with opaque per-scope cursors
and push to `POST /sync/batch` with per-item results, once B14 is implemented.

## Consumes

Wave 0: each entity's `PUT` and list endpoints. Wave 2: `/sync/changes`, `/sync/batch`, the
`Idempotency-Key`, membership change rows and tombstones.

## Can build before the backend ships

The whole wave-0 engine; it only needs the per-record `PUT` rule every syncable endpoint already
follows (spec 003). `AccountSyncCoordinator` becomes its first client.

## Needs from the backend

For wave 2: batch size limit, per-item vs all-or-nothing semantics, ordering, how the batch key
relates to per-item idempotency, whether per-record `PUT` stays, and the cursor per scope (user
scope and wallet scopes).

## Considerations

- The server never merges (P-07 backend, P-04 client): every conflict reaches the phone.
- Tombstones live 90 days server-side; a phone offline longer must full-resync that scope.
- Sync must survive process death (WorkManager on Android behind an `expect/actual`).
- Sign-out and access loss purge the scope's local data.

## Integration checkpoint

E2E: 20 offline edits across entities sync in order after reconnecting; a forced conflict shows
the choice; kill the app mid-sync and it resumes.
