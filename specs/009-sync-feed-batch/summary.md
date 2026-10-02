# Summary: Change Feeds & Batch Upload (spec 009, C14 wave 2)

**Date**: 2026-10-02 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner's phone, committed on branch `009-sync-feed-batch` (48a1860) · **Backend contract**: `saveme-backend@6b8e290` (sync.yaml v1.6+); contract QA ran against `e4dc4c6` (v1.9).

## What was built

- **Uploads through `POST /sync/batch`**: up to 50 due changes per request, oldest first, 60 s timeout for the batch call only. Each item is exactly the per-record write; archives and "Restore with my change" stay per record at their place in the order (the batch is split around them). Every item's answer goes through the unchanged `settle()`. A wallet or category created on the phone and not yet accepted holds back its dependents (no try counted).
- **Request-level answers**: no connection, timeout, 401, 426, 429 or 5xx apply to every item as a single write would; a shape or endpoint refusal (422, 400, 415, 406, 404, 405) or a mismatched answer falls back to per-record sends in the same run.
- **Reads from `GET /sync/user/changes` and `GET /sync/wallets/{id}/changes`**: one stored position per scope (Room 5 → 6, two nullable columns on `sync_scope`, exported `6.json`). Eight feed appliers across four features apply rows with a version guard, a deletion rule and a sweep after a full read. `GET /me` and `GET /platform-categories` remain direct reads; all other whole-list and single-record reads are removed.
- **Recovery**: `410 SYNC_CURSOR_EXPIRED` and `400 INVALID_CURSOR` are handled per scope (keep showing the copy, re-read from the start, sweep what the server didn't return). Both codes added to `ApiErrorCode`.
- **Wallets gained or lost** through the person's own memberships on the user feed (`WalletScopes`); a missing membership is checked against the wallet's feed before removal.
- **`SyncRunner`** serialises upload-then-read runs and keeps the 60 s floors and outcomes, so no screen changed; the WorkManager worker calls `runBackground`. New `core.sync` parts: `FeedApplier`, `WalletScopes`, `SyncLock`, `BatchItem`, `FeedPuller`, `SyncRunner`, `SyncSignOutCleaner`.
- **Balance carry** now settles per wallet read; a stored wallet copy clears the carries it already counts (`WalletCopyParticipant`).
- Maestro flows in `.maestro/009-sync-feed-batch/` (backlog, conflict, kill mid-sync, second-session wallet), deferred.

## Results

- **Host QA** (owner policy, no device): 26/26 scenarios, 22/22 edge cases, 33/33 FRs, 8/8 SCs pass (`qa-report.md`). 1,480 host tests green with `--rerun-tasks`; no golden changed; APK and iOS compile green.
- **Live contract QA** against `saveme-backend@e4dc4c6`: batch uploads (mixed, replay, conflict, every per-item refusal, held dependent, every whole-request refusal), both feeds (paging, empty pages, incremental reads, all four `INVALID_CURSOR` cases, 410, wallet 404, transfer identical on both feeds), memberships gained, changed and lost through the real sharing endpoints, unknown row kinds present and skipped. 55 real answers are fixtures driven through the real wire client, engine, puller, runner, handlers and appliers (`SyncCapturedResponsesTest`, `FeedCapturedPagesTest`).
- **Fixed during QA**:
  1. Balance counted twice when a stored wallet copy already includes an accepted change (also latent in spec 008): an accepted change adds no carry for its own wallet when the copy is at or past its `change_seq`, and storing a copy clears carries it covers.
  2. A from-the-start read that got 410 on its own next page restarted once more; it now fails once per run.

## Backend bug found (fixed in backend spec 017)

A read from the start could not page past a scope's purge horizon: the server expired its own `next_cursor` (410). Once 90-day purges run, a scope with more rows below its horizon than one page could not be fully read on a new phone or after a 410. Reported to the backend and folded into backend spec 017 (sync follow-ups). Until that lands, the client fails that scope once per run and keeps its copy.

## Deferred to the owner's phone

T071 (device E2E) and the Maestro flows.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. Every list and single-record read moves to the two feeds, except `GET /me` and `GET /platform-categories`.
2. Batch upload is the default (50 items, 60 s, oldest first); archive, archive + edit and restore stay per record.
3. Request-level failures apply to all items; shape/endpoint refusals fall back to per-record sends.
4. A transaction is held while its wallet, transfer destination or category is an unaccepted create; if that create failed earlier in the same batch, the transaction's answer is ignored.
5. Positions are stored on `sync_scope`; the first run after the update reads every scope from the start.
6. 410 / full re-read: keep the copy, re-read the scope, remove what the server didn't return — never rows with waiting changes, unsent creates or carried transactions. `INVALID_CURSOR` resets a scope once per run.
7. A live own membership means read that wallet from the start; an ended one (or a wallet-feed 404) removes the wallet through the spec 006/008 path. A wallet with a waiting edit stays until the person settles it.
8. The balance carry settles per wallet feed read.
9. Unknown row types (`recurring_rule`, `occurrence`, `budget`, `invitation`) are skipped, not stored; C7/C8/C9 clear the affected positions in their own migrations.
10. No new periodic job: reads on sign-in, foreground and screen triggers with today's floors; the background worker uploads, then reads only the scopes it wrote to.
11. Items refused inside a batch raise the same app-wide signals as single writes.

### Implementation

12. A from-the-start read stores its position only after its last page (research R-04 over FR-003); an interrupted full read starts again.
13. `SyncApi` and `SyncRunner` are interfaces for testing.
14. `WalletSyncHandler.onDiscarded` clears the wallet's feed position instead of removing the wallet; the next read decides.
15. A wallet kept for a waiting change is still read each run and answers 404 until settled.
16. The wallet list's "Updated …" time is the oldest feed read across the person's scope and every accepted wallet.
17. Device sessions have no version column, so no version guard applies to them.
18. The 60 s floor counts completed reads only; a scope the upload wrote to is always read; an offline read stops the run's remaining reads; categories keep a floor for the default list and reference misses; nothing is applied after sign-out.
19. Carry rule: a wallet copy at position p counts every change of that wallet up to p.
20. A from-the-start read that gets 410 mid-read fails once per run.
21. QA accounts were registered through the API (factory users have no profile, so their user feed is 404).

## Remaining risks

1. The backend purge-horizon paging bug (until backend spec 017 lands).
2. No device run yet.
3. Each refresh costs one read per followed wallet until the client adopts backend spec 017's `GET /sync/wallets/heads` and `sync_position`.
4. A transfer's destination-side carry waits for the destination wallet's read.
5. The wallet record and the carry clear are two writes with a short window.
6. Partners' display names can be stale until the client consumes backend spec 017's membership re-stamps (they arrive as normal feed rows).
7. The first sync after the update re-reads everything, including large wallets.
