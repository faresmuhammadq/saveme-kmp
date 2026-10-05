# Summary: Sharing & Invitations (spec 013, C9)

**Date**: 2026-10-05 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner, committed on branch `013-sharing` (ecd8aa3) · **Backend contract**: `saveme-backend@26e7c44` (`sharing.yaml` v1.9, `sync.yaml` unchanged).

## What was built

- **Sharing and invitations on the phone.** An owner invites by email or by a one-time link, with a role, offline or online. Invitations are phone-created records sent one at a time through the sync engine (never in a batch) and read back from the wallet feed (`invitation` rows).
- **The link is held in memory only**, captured from the create answer's `meta.share_url`, and offered through the system share sheet or a clipboard marked sensitive. It is never stored, logged or shown as text. If it is lost, the owner taps "Replace link" (withdraw and recreate).
- **Invitees** open `/invite?token=` links into an invitation screen to preview, join or decline. The secret survives sign-in and registration (memory only, app session); Home shows a card for a held invitation. The token has a redacted `toString`, a shape check (43 characters) before anything reaches the server, and App Link intent filters on both manifests (per-flavor host as in spec 003; prod release has the `https` `/invite` path and no `http` filter). The dev Paste link tool covers invitations.
- **Online-only membership actions**: role change, removal, leaving, join and decline say "Needs a connection" offline and are never retried automatically. Any loss of access (removal, leave, user-feed tombstone, wallet-feed 404) purges the wallet and everything it holds; leaving uploads waiting changes first and the confirmation says how many will be discarded.
- **Screens (four)**: members (with the pending count), invite, invitation (preview/join/decline) and an online-only activity screen (50 per page, names resolved from the phone, never amounts). Wallet detail, transactions and Home gained their hooks; viewer and plan notes appear everywhere ("You can now only view this wallet").
- **Room v9 → v10**: `invitation` table; `MIGRATION_9_10` clears every wallet scope's feed position once so existing invitations are read; `10.json` exported.
- **Small core additions** (no sync-engine behaviour change): `Problem.feature`, `Refusal.feature`, `ApiClient.callWithMeta`, `SyncEngine.pendingIn`; plus `SharingNotices`, `awaitDelivery` (3 s window) and `LeftWallets` (suppresses "access lost" after an own leave).
- About 134 English/Arabic strings; 17 Maestro flows in `.maestro/013-sharing/`; `api.js` additions; the `+partner` seed (a second verified Plus account, `E2E_PARTNER_EMAIL`/`E2E_PARTNER_PASSWORD`) in `.specify/scripts/bash/run-e2e.sh`.

## Results

- **Host QA** (`qa-report.md`): 47 acceptance scenarios, 15 edge cases, 36 FRs and 14 invariants PASS; 6 of 7 SCs PASS (SC-001's timing is device-only). 2,853 host tests green with `--rerun-tasks`; Roborazzi verify, `assembleDevDebug` and the iOS simulator compile pass. 105 new goldens (35 screens × en, ar, en at 2× text); existing wallet-detail, occurrence-refused and editor-refused goldens re-recorded because they legitimately changed. The two known flaky wallet tests did not flake (2 full runs, 5 forced reruns).
- **Live contract QA** (about 290 calls, 7 throwaway accounts, reached through `127.0.0.1:43871` because the `.test` name flaked): email and link invitations, `meta.share_url` only in the 201 and no link on a replay, every token 404 cause byte-identical apart from `request_id`, 429 on the 21st invitation in 24 h and the 31st token call in an hour (`retry_after_seconds` + `Retry-After`), the Plus cap of 5 including pending invitations, demotion by lapsing Plus (editor → viewer through ordinary member rows on both feeds plus a `role_limited_by_plan` activity entry), expiry and the sweep, removal and leaving (user-feed tombstone, wallet-feed 404), rejoin with a new member row id. Real answers (tokens replaced by fakes of the right shape; no real token or credential committed) drive `SharingCapturedWireTest` (22) and `SharingCapturedResponsesTest` (18) through the real clients, engine, handler, appliers and service.
- **Gaps closed**: the sensitive-clipboard flag and share chooser now have a test (`AndroidLinkSharerTest`); the live answers run through the real client.

## Deferred to the owner's device

T049, T062, T071, T081, T089, T097, T107, T122, T123 (flow runs, quickstart, full E2E report), SC-001 timing. Device runs need the backend's `SHARING_INVITATION_URL=http://<devApiHost>/invite` (the default points at `saveme-backend.test`, which a phone cannot reach) and the `+partner` seed.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. Invitations work offline: create and withdraw go through the sync queue, one record at a time.
2. Membership actions are online-only (role change, remove, leave, join, decline), like C4's ownership transfer.
3. The one-time link lives only in memory; Share and Copy only; "Replace link" if lost.
4. Link format `{scheme}://{linkHost}[:{linkPort}]/invite?token=<43 chars>`, same per-flavor host as the spec 003 mail links.
5. A signed-out invitee's token is held in memory for the app session; the invitation screen opens after sign-in; "Not now" leaves a Home card "You have an invitation to answer"; closing the app drops it and tapping the link again restores it.
6. Plan check before inviting: verified address, then sharing included, then the people count (owner + members + unexpired pending invitations, including unsent ones, + 1 ≤ quota). Viewer is the preselected role.
7. Inviting your own address is refused; re-inviting an address with a pending invitation asks to confirm the replacement; invite is not offered on archived wallets.
8. Expired invitations show as "Expired" (computed from `expires_at`), do not count toward the people limit and can still be withdrawn.
9. The person's own member row also updates the wallet's stored role.
10. Losing access purges everything, including invitations.
11. Activity is online-only and not stored.
12. Viewers see a read-only note, mentioning the owner's plan only if an activity page read this session shows the plan made the change; a server `FORBIDDEN` on a transaction or recurring change reads "You can now only view this wallet."
13. Entry points are on the wallet detail ("Manage members" / "See members" with the pending count, "Activity"); Home only gains the held-invitation card.
14. The share text names neither the wallet nor any amount; the remove confirmation says what the removed member's phone already downloaded may stay on it.
15. Room 9 → 10; no new dependency.

### Implementation

16. The invitation view model checks the 43-character token shape client-side.
17. Only server `FORBIDDEN` refusals use `refusal_now_viewer`; local "not allowed" messages keep their old strings.
18. `AccessLostPurgeTest` runs the real Koin graph with an in-memory Room database (the bundled SQLite driver can't load on the host).
19. Maestro flows tap dialog buttons by test tag (`members.confirm`), because "Leave wallet" and "Withdraw" also label non-dialog buttons.
20. Two existing copy tests moved to the new `refusal_now_viewer` key (`TransactionCopyTest`, `RecurringCopyTest`).

### Owner review

21. Arabic: دعوة, الأعضاء, النشاط, مشاهد, محرر, معلّقة, منتهية, سحب الدعوة.

## Remaining risks

- **`./gradlew build allTests --continue` is not green** — four failures, none in code this spec touched (T116 stays open): `:shared:compileCommonMainKotlinMetadata` on `Dates.kt` (`NSLocale`/`CalendarLocale` unresolved in common code), the iOS `compileTestKotlinIosArm64` and `compileTestKotlinIosSimulatorArm64` on `TransactionsListPerformanceTest` (`String.format` is JVM-only), and `:androidApp:lintDevDebug` (three errors at the spec-003 manifest lines: port/host split, `exported`). The four-task gate above stays green. A follow-up should fix the iOS-compatible code and either fix the lint errors or add a lint baseline.
- The server does not refuse an invitation to the owner's own address or an existing member's address (201); the app's own-address rule is the only guard.
- A lost answer to a link create means withdraw and recreate, and the lost invitation holds a seat until then.
- A removed member who rejoins gets a new member row id; the user feed carries the old tombstone then the new live row; the client purges and rereads the wallet from the start (end state correct and pinned by a test); a removed member rejoining offline gets a purge and a full re-read.
- The signed-out invitation message shows on the invitation screen, not the entry screen as the spec says.
- `InvitationKey` is `@Serializable` but the back stack is not saved, so the token never persists; revisit if navigation state saving is ever added.
- A demoted editor keeps editing rights for up to about an hour until the backend's demotion runs.
- Staging and production App Links need `assetlinks.json` on the backend host (existing dependency from spec 003).
- No device run; the invitation-link flows depend on `SHARING_INVITATION_URL`.
