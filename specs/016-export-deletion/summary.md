# Summary: Export & Deletion Completion (spec 016, C15)

**Date**: 2026-10-06 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner, committed on branch `016-export-deletion` (a510632) · **Backend contract**: `saveme-backend@776e5b2` (spec 022, summary e8c308a; `exports.yaml`, the `identity.yaml` deletion edits, OpenAPI v1.13).

## What was built

- **Exports (`feature.account`, nothing stored, Room stays v10)**: an "Export my data" row on Account opens an Exports screen (the last 30 days, 20 per page with "Show more", refresh). "New export" asks for everything in the account or one wallet; wallet detail has an "Export this wallet" row for every role, shown only once the server holds the wallet. A status screen reads the export when it opens and then every 5 seconds while it is queued or running and the screen is in the foreground; polling stops on a final state, offline, sign-out, after 10 minutes or 120 reads ("we'll email you", with "Check again").
- **Download**: "Open download" reads a fresh link on every tap and hands it to the system browser only. The link is never stored, kept in screen state, shown, logged, copied or shared, and the app never writes the file.
- **Export refusals**: `EXPORT_IN_PROGRESS` ("an export is already being prepared") opens the export in `current`; the rate limit keeps the request button disabled for the wait; a missing wallet and failure codes have their own copy; "Try again" on a failed export requests a new export of the same scope. Export is not gated by plan, `export.enabled` or address verification (the brief's "may gate" is superseded by backend spec 022 A-5).
- **Deletion flow extended**: Delete account now reads the plan (`GET /account/deletion-request`) before the password. Explanation (shared wallets pass on, "a former member", the Google Play notice, export first) → new owners (offered only where `choice_expected` and the phone holds the wallet and at least one other member; the server's suggestion preselected) → review → password → scheduled step naming the owners. Successor validation errors re-read the plan and schedule nothing.
- **Pending view**: shows the date and each owner with the reason; "Change new owners" (with the password; the date never moves), "Export my data", "Cancel deletion" (choices dropped). After the date it reads "being deleted", and so does Home's notice. The `purging` state has no UI of its own: the ended session signs out and wipes the phone as before, with no "your account was deleted" message.
- **"A former member"** for a transaction or recurring rule whose author is not among the members the phone holds for that wallet (only once that wallet's member list is held). `RuleView.authorName` and the new `RuleAuthor` are filled.
- `ApiErrorCode.EXPORT_IN_PROGRESS`, about 80 new English and Arabic strings, 3 navigation keys, no Room change, no new dependency, no new design-system component.
- **E2E assets (written, not run)**: Maestro flows in `.maestro/016-export-deletion/` (US1 ×2, US2, US3 ×2, US4, US5, US6 in two parts), `common/open-exports.yaml`, five `api.js` actions, a review-step change to `common/request-deletion.yaml`, and `run-e2e.sh` seeds `+shared-owner`, `+export-limit` and `e2e-between: purge`.
- About 111 new goldens (`screen_export_*`, `screen_delete_*`, former-member editor and rule detail); changed on purpose: Account, `delete_explain`, `delete_scheduled`, eight wallet-detail states and three Sharing wallet-detail cases. Wallet detail and Home were re-recorded once after the C13 merge and verified by Roborazzi.

## Results

- **Host QA** (`qa-report.md`): 36/36 acceptance scenarios, 39/39 FRs, 7/7 SCs and 20/20 edge cases PASS. 3,626 host tests, 0 failures; the four-task gate (`--rerun-tasks`) and `cleanAllTests build allTests --continue` green (`iosSimulatorArm64Test` skipped: this host is not a Mac). Two gaps closed: FR-038 (screen-reader announcements, tags, 48 dp targets, 200 % font) now has `ExportAccessibilityTest` (5), and T063 (ownership transfer arrives as an ordinary feed, the departed member is a tombstone) now has `FormerMemberLiveReplayTest`.
- **Live contract QA** (96 backend answers recorded, 64 kept as fixtures, replayed by 30 host tests: `ExportLiveReplayTest` 14, `DeletionPlanLiveReplayTest` 12, `FormerMemberLiveReplayTest` 4; six throwaway accounts that the feature purged itself): export requests queued → running → ready with the real queue worker; same-second link repeats and a later read gives a fresh link; `expired` carries no link; 409 `EXPORT_IN_PROGRESS` with `current`; the sixth request in 24 hours 429 (86,386 s wait); non-member wallet 404; 422 and `INVALID_CURSOR`; cursor paging newest first; other members' names absent from the ZIP; deletion plan reasons `only_member`, `chosen`, `oldest_editor`, `oldest_viewer`; `choice_expected` stays true after a choice (the client reads a made choice from `reason: chosen`); `successors` validated before the password with a 422 pointer; a repeated request with a list replaces all stored choices, cancel drops them; after the purge the old token is 401 `UNAUTHENTICATED`, sign-in 401 `INVALID_CREDENTIALS`, the account is a husk, its exports are gone and its old signed link is 404; the departed member's row is a tombstone in partners' feeds (`deleted_at` set, `display_name` null), transactions keep `created_by`, and the partners' member list no longer lists them.
- Fixtures use fake 64-hex signatures and `example.com` addresses; no bearer token or password is committed.
- No backend bug that affects the client and no client defect; no product code changed in QA.

## Deferred to the owner's device

T027, T038, T044, T052, T060, T068, T076 (flow runs, quickstart, full E2E report).

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. Export is not gated by plan, `export.enabled` or address verification, and works while deletion is pending.
2. Exports and deletion both live in the existing `feature.account`; nothing is stored.
3. Entry points: "Export my data" on Account; "Export this wallet" on wallet detail for every role (archived too) once the wallet has reached the server; links to exports from the deletion explanation and the pending view. Three export screens: list, request, status.
4. Downloads go through the system browser only (the brief said "or share sheet"): no share sheet and no copy-link.
5. Polling every 5 s only while the export screen is foregrounded and the export is queued or running; the list never polls; "Check again" starts a fresh 10-minute window.
6. The one-active-export conflict offers "See it"; the rate limit keeps the request button disabled until the wait passes (memory only); "Try again" on a failed export requests a new export of the same scope.
7. Deletion flow: explanation → plan read (required before the password) → new owners → review → password → scheduled. A choice is sent for every wallet where one was offered (up to 100). Opening Delete account while pending reads the plan: an `active` plan falls back to the explanation; an unknown plan state shows only "Cancel deletion" with a general note; `pending_deletion` found while starting a deletion shows the pending view.
8. The pending view lets the person change owners with their password (the date never moves) or cancel; the cancel confirmation says the choices were dropped.
9. The `purging` state has no UI; after the scheduled date Home and the pending view say "Your account is being deleted."
10. "A former member" is shown when the author is not among the members the phone holds, but only once the phone holds that wallet's member list; activity keeps spec 013's wording.
11. No new dependency and no new design-system component.

### Implementation

12. `export_limit_hours` / `export_limit_minutes` are plurals (Arabic has all six forms); sizes are KB rounded up under 1 MB, otherwise MB with one decimal, in Western digits.
13. `DeletionOutcome.Scheduled(date, owners)` carries `ScheduledOwner(walletId, ownerUserId, reason)`; the store resolves names from the plan it holds.
14. `RuleView` gains `author: RuleAuthor` and keeps `authorName` as a derived read; `WalletDetailState.exportable` is a derived property.
15. Home's "being deleted" uses an inline minute ticker in `AccountModule`, so account does not import sharing; the "Export my data" row uses the existing `AppIcons.Sync`; progress rings are the platform `CircularProgressIndicator`.
16. `DeleteAccountState`, `PasswordChanged` and `Password` print the password redacted.
17. `ExportLayeringTest` checks only spec 016's own files (older account files already import across layers).
18. One existing assertion changed: a transaction whose author is absent from a non-empty member list now reads "former member" instead of "another member" (FR-033).
19. Extra string keys beyond the contract: `exports_refresh`, `export_wallet_label`, `export_wallet_hint`, `export_size_kb`, `export_size_mb`, `delete_owners_next`, `delete_review_solo`, `delete_pending_unavailable`.

### Owner review

20. The Arabic copy (about 80 strings).

## Remaining risks

- Not exercised live: sign-in during the grace period cancelling the deletion (unchanged spec 007/002 behaviour), the real `purging` window, a `failed` export (the failure copy is proven by contract examples and `ExportCopyTest`), and more than 20 exports (the 5-per-day limit prevents it).
- The wallet-detail and Home goldens were re-recorded after the C13 and C15 merge and verified by Roborazzi, not by eye on a device. iOS is compile-only here.
- `DeletionPlan` carries no names, so wallets or members the phone does not hold fall back to "A shared wallet" / "Another member"; `Export` has no wallet name, so exports of wallets the phone no longer holds are unnamed; the plan does not say whether a candidate is themselves pending deletion; the plan's candidate list comes only from members the phone holds.
- The signed download route is outside OpenAPI; its expired or purged error page is whatever the server renders, shown by the browser.
- There is no push when an export is ready; the mail is the only signal outside the app (C16 can add it).
- The Maestro flows and `run-e2e.sh` seeds were written from the backend's code and never run; the purge seed depends on the container's queue worker.
- "Former member" depends on the wallet's member list being held on the phone (it could appear wrongly in the short window after a wallet is first read).
- A repeat deletion request without `successors` keeps the stored choices; a choice naming a wallet with exactly one other member is accepted and ignored (`only_member`).
