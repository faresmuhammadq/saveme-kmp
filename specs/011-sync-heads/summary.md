# Summary: Sync Heads Adoption (spec 011, C14 wave 3)

**Date**: 2026-10-05 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner, committed on branch `011-sync-heads` (a6e940d) · **Backend contract**: `saveme-backend@192e3ab` recorded; live QA ran against `64ca1ea` (`sync.yaml` v1.10, backend spec 017).

## What was built

- **Room v8**: nullable `sync_scope.feed_position`, written in the same statement as the cursor and cleared with it. `MIGRATION_7_8` only adds the column and keeps every stored cursor (no re-read from the start on update); `8.json` exported. The opaque cursor is never parsed and the position is never sent to the server.
- **Heads API**: `SyncApi.walletHeads` with `WalletHeadDto` and `HeadsPageDto` (meta reuses `core.network.CollectionMeta`), and `FeedMetaDto.syncPosition`.
- **Quiet refresh**: `FeedPuller.heads`, `markUnchanged` and `forget`, plus the pure `HeadsDecision` (`Read`, `ReadFromStart`, `Skip`). A "refresh everything" run now costs 2 requests (user feed + heads) when nothing moved, or 2 + k when k wallets moved. When a wallet's purge horizon is above the stored position the app forgets its place and reads from the start straight away, without waiting for a 410.
- **Reads that bypass the heads** (always read): wallets the upload just wrote to, unconfirmed wallets, wallets with no stored place or position, and wallets named by a `ReadTarget.Wallet` in the same run. A failed heads read reads every wallet; an offline one stops the run's remaining reads. A wallet missing from the heads is never a removal signal.
- **Restore pair**: `BatchOp.Restore` and `SyncHandler.batchItems`. The engine now handles a list of items per queued change, never splits a pair across requests, and settles it as one answer (the first item not applied, else the last). `TransactionSyncHandler` sends restore (n) followed by upsert (n + 1) in one upload, replacing the per-record PATCH + PUT two-step.
- **Member renames**: the member guard already applies equal-version re-stamps, so only a KDoc and tests (`WalletMemberRestampTest`) were added; the comment on the "fail once per run" 410 safeguard now reflects backend spec 017.
- **E2E assets**: two Maestro flows in `.maestro/011-sync-heads/` (`US2-partner-rename`, `US3-restore-with-my-change` in three parts), deferred.

## Results

- **Host QA**: each FR and SC mapped to a test (`qa-report.md`); 2,092 host tests green with `--rerun-tasks`; Roborazzi verify, `assembleDevDebug` and the iOS compile pass; no golden changed; `AppGraphTest` green, no Koin binding added.
- **Live contract QA** (150 HTTP calls; three throwaway users — owner on Plus, editor, viewer — and five wallets):
  - **Heads**: paging at limit 1 and 200 (500 clamped, default 50), ordered by `wallet_id`; archived listed; deleted and left wallets absent (the left wallet's own feed is 404); every refusal (400 sort/filter/cursor/query, 401, 406, 426); a person in no wallet gets `[]`.
  - **Positions**: `meta.sync_position` equals the head after a full read; an intermediate page states the position its `next_cursor` resumes from; a quiet re-read returns the same cursor and position. A partner's write moved only that wallet's head.
  - **Rename**: the member row was re-stamped about 1 s later by the queue — same version, new `change_seq`, new `display_name`, heads of the affected wallets moved.
  - **Purge**: reads from no cursor at limits 1, 2, 5 and 200 paged across the horizon to the end with no 410 (the backend bug is fixed); a stored cursor below the horizon got 410; a purge landing mid-read gave exactly one 410 and the restart completed. Only QA tombstones were aged (checked first).
  - **Restore pair**: applied (v3, v4); **replay after a lost answer with the record already at n + 2 → both items `applied` with the unchanged record, nothing bumped, no 409** (a restore against a live record is a no-op `applied`; the version check lives on the save); replay after a later edit → item 1 applied, item 2 409 with the live copy; replay after re-delete → both 409 with the tombstone; stale pair → 409; viewer → both 403; invalid item 2 → item 1 applied, item 2 422 `OUT_OF_RANGE@/amount_minor`.
  - The engine's reduction settles every recorded answer correctly; no client or engine defect found; no backend bug.
  - 21 new tests driven by recorded real answers (`SyncHeadsCapturedTest` 13, `SyncRestorePairCapturedTest` 8; `CapturedHeadsResponses.kt`, 14 request ids).

## Deferred to the owner's device

The two Maestro flows (`US2-partner-rename`, `US3-restore-with-my-change`) and the quickstart.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. Forced "refresh everything" runs also let the heads skip unchanged wallets; forcing only lifts the once-a-minute limit.
2. A wallet whose purge horizon exceeds the stored position is read from the start immediately (no 410 first).
3. If the restore applies but the edit is refused, the change settles exactly as the per-record path does today; the restored record is not stored early.
4. The 7 → 8 migration keeps every stored cursor; positions fill in on each wallet's next read.
5. The "fail once per run" 410 safeguard stays unchanged (a purge can still land mid-read).
6. Heads are read only by "refresh everything" runs; single-wallet reads, the balance-carry read and "Use the other device's" for a named wallet never use them.
7. Offline heads → the run's remaining reads stop; any other heads failure, including on a later page, reads every wallet.
8. A skipped wallet counts as read at the moment the heads request started (its "Updated …" time and the 60 s floor move).
9. The position is stored for every scope, though only wallet positions are compared.
10. The restore and the edit are one queued change sent as two consecutive items in one upload; if the edit can't be decoded, only the restore is sent.

### Implementation

11. About 60 existing test calls `storeFeedPage(scope, cursor)` became `storeFeedPage(scope, cursor, null)` mechanically; `FakeSyncScopeDao` mirrors the new SQL.
12. `SyncEngineBatchOrderTest.anArchiveAndARestoreInTheMiddleOfTenEditsKeepTheirPlace` was rewritten on purpose as `...ARestoreRidesTheBatchAsAPairAtItsPlace` (log: `batch 8: e5, e6, e7, restore, restore, e8, e9, e10`); the per-record restore scenario stays as a separate test; every other existing engine test passes unchanged.
13. When the stored transaction row is gone (a feed tombstone removed it), the pair's upsert takes kind, currency and counter wallet from the payload, as the per-record save does.
14. The `US3-restore-with-my-change` flow is split into three parts (the network is toggled between them); `US2-partner-rename` uses the person's own member row because the app has no sharing screens yet.
15. Summary log line: `wallets: N followed, S unchanged, R read, F from the start (purge horizon), D direct`.

## Remaining risks

- A restore replay is idempotent only while the record is still the pair's own result; after any other change it becomes an ordinary clash that the person resolves.
- A rename reaches partners only when the backend queue runs.
- The user feed has no head, so the user read always costs one request per refresh.
- After the 7 → 8 migration each wallet is read once more from its own cursor before heads can skip it.
- A purge landing between the heads read and the wallet read still gives a 410, handled by the unchanged restart path.
- The two Maestro flows are unrun.
