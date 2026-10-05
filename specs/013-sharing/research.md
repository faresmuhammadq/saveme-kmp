# Research: Sharing & Invitations (client)

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-10-05

Every decision below was taken with the owner away (delegated authority): the conservative,
constitution-first option. Code facts were read on `main` `48bd79f` (Room v9).

## R-01 A new bounded context `feature.sharing`, wallets keeps the members

- **Decision**: New `feature.sharing` (`data`, `domain`, `ui`, `di`). It owns the `invitation` table, the sharing endpoints (`InvitationApi`, `MemberApi`, `ActivityApi`), `InvitationSyncHandler`, `InvitationFeedApplier`, the in-memory holders, and four screens (members, invite, invitation, activity). The `wallet_member` table stays in `feature.wallets` (C4); sharing reaches it only through a new wallets domain API **`WalletMemberships`** (observe members with versions, apply a member record, own role, `accessLost`). Wallets reaches sharing only through default-off hooks defined in wallets domain (`SharingGlance`, R-15). Home reaches it through an auth-domain hook (`HeldInvitationGlimpse`, R-16).
- **Rationale**: P-10.4 (cross-feature access through `domain`), P-10.6 (data imports no domain). The backend splits the same way (Sharing writes memberships through Wallets' `MembershipService`). A cycle is avoided because wallets never imports sharing; the hooks are interfaces in wallets/auth domain with defaults, bound by sharing's Koin module (the spec 010/012 pattern).
- **Alternatives**: put everything in `feature.wallets` (rejected: wallets is already the largest feature and the API file `sharing.yaml` is its own context); move `wallet_member` to sharing (rejected: every wallet-scoped feature reads roles from wallets).

## R-02 Room 9 → 10: `invitation`, wallet positions forgotten once

- **Decision**: `MIGRATION_9_10` creates `invitation` (data-model §1) with an index on `(wallet_id, status)` and runs `UPDATE sync_scope SET feed_cursor = NULL, feed_read_at = NULL, feed_position = NULL WHERE scope LIKE 'wallet:%'`. User scope untouched. `10.json` exported. `DatabaseCleaner` clears the table.
- **Rationale**: spec 009 skipped `invitation` rows as unknown; only a read from the start brings pending invitations created before the update (FR-030). Cursor, read stamp and position are written and cleared together (spec 011). Wallets with no position are read regardless of heads (spec 011), so the next "refresh everything" reads each wallet from the start once.
- **Alternatives**: clear only wallets the person owns (rejected: every member sees pending invitations, S-01); keep cursors and rely on a full sweep (rejected: rows before the cursor are never resent).

## R-03 Invitations through the sync engine, per record

- **Decision**: `InvitationSyncHandler` (record type `invitation`, scope = its wallet):
  - `send`: a create (`baseVersion == 0`, not delete) → `PUT /wallets/{w}/invitations/{id}` with `{role, invitee_email, version: 0}` (explicit `null` address for a link); a delete → `DELETE …/{id}` with `{version}`.
  - `batchItems` → `null` (per record only; invitations are not batch items, `sync.yaml`).
  - `canRestore = false`; `acceptsClash = true` (an invitation is immutable; a 409 means it was closed — take the tombstone, drop the change).
  - `refusalFields`: `/role` → `role`, `/invitee_email` → `email`.
  - `applySentAnswer`/`applyServerCopy`: store the server's row (tombstone removes it).
  - **No engine change.** A waiting create of a wallet made on this phone is held by `prerequisites` = the wallet record (spec 009 held dependents).
- **Rationale**: P-04.2/4/5 (local-first create, version on every write, deletion as a queued tombstone). The engine already sends per-record changes at their place in the order (archive precedent).
- **Alternatives**: online-only invitations like ownership transfer (rejected: an invitation is a phone-created syncable record with a client UUIDv7 — P-04.2 non-negotiable); batch items (rejected: the backend refuses them).

## R-04 The one-time link: `meta.share_url` captured by the handler, held in memory

- **Decision**: `ApiClient` gains `callWithMeta<T>()` returning `ApiResult<Envelope<T>>` (additive; `call` unchanged). `InvitationApi.put` uses it; `InvitationSyncHandler.send` reads `meta.share_url` from a `201` and passes it to an injected callback `onShareLink(invitationId, url)` before returning the record. The callback is `ShareLinks.offer` in sharing domain (memory `MutableStateFlow<Map<String, ShareLink>>`; `ShareLink` redacts `toString`), wired in `SharingModule` as a lambda (data imports no domain, P-10.6). `ShareLinks` is cleared at sign-out (a `UserDataCleaner`). A `200` replay has no link → nothing offered. A link invitation without a held link shows "This link can't be shown again" with "Replace link" = withdraw (queued delete) + create a new link invitation with the same role (new UUIDv7).
- **Rationale**: FR-005, P-09.2 (never stored or logged: `ApiClient` logs only method and URL, the body is never logged). The engine's interface stays unchanged.
- **Risks**: an answer received by the background worker while no screen is open is kept only for the process's life; a lost link occupies a seat until replaced (spec Assumptions, backend gap 2).
- **Alternatives**: persist the link encrypted (rejected: P-09.2 and backend "returned only once, share as is"); send link invitations only while the app is in the foreground (rejected: needs an engine change for one record type).

## R-05 Local rows for waiting invitations

- **Decision**: A create inserts a placeholder `invitation` row (`version = 0`, `status = pending`, `invited_by` = the person, `invitee_email` = the address or null, `expires_at` = null until the server answers, `created_at` = now) and enqueues the change with the draft `{role, invitee_email}` as payload. The list shows "Waiting to sync" instead of "Expires …" while `version == 0`. Withdrawing a never-sent invitation removes the placeholder and discards the queued create (nothing sent); withdrawing a sent one enqueues the delete with the stored version and hides the row while it waits. A refused create ("Not sent") keeps the placeholder with the refusal; "Discard" removes both.
- **Rationale**: P-04.1 (render from Room), P-04.5; mirrors wallets' placeholder (spec 006 R-06) and budgets.
- **Alternatives**: render the queue's payload without a row (rejected: the list and the people count read one table).

## R-06 The invite gate and the people count

- **Decision**: Pure `InviteGate.check(verified, sharingEnabled, quota, peopleCount)` → `Allowed | NotVerified | SharingNotIncluded | Full(max)`, evaluated in that order (FR-002). `peopleCount` = active member rows of the wallet (the owner included; tombstones are not stored) + invitation rows with `status = pending`, not past `expires_at` (a null `expires_at` counts), not waiting to be withdrawn. Allowed when `quota == Unlimited` or `peopleCount + 1 <= max`. The same count with `> max` drives the owner's plan note (FR-028); `can(sharing.enabled) == false` counts the quota as 1 (backend `EnforceMemberQuota`).
- **Rationale**: matches backend `MemberQuota::canInvite(active, livePending, quota)` and "pending invitations that have not expired" (`countLivePending`). P-12.1: gate on features only.
- **Alternatives**: let the server decide (rejected: FR-002 wants an explanation before anything is queued).

## R-07 Email validation and duplicates

- **Decision**: `InviteValidation`: trim; length 3–254; exactly one `@` with non-empty local part and a domain containing a `.` with no spaces; equal (case-insensitive, after trim) to the signed-in address → `OwnAddress`. A pending invitation in the same wallet with the same address (case-insensitive) → the store asks to confirm the supersede (the server closes the old one as revoked; its tombstone arrives on the feed).
- **Rationale**: the server compares after trim, NFKC and lowercasing; the phone does not need NFKC to warn — a miss only skips the warning, the server still supersedes.

## R-08 Invitation links into the app

- **Decision**: `InvitationLinkParser(scheme, linkHost, linkPort)` in sharing domain, built from the same `AppConfig` values as `AuthLinkParser` (spec 003). Accepts `{scheme}://{linkHost}[:{linkPort}]/invite?token=…` → `InvitationLink.Valid(token)` when the token matches `^[A-Za-z0-9_-]{43}$`, `InvitationLink.Malformed` when the path matches but the token does not; anything else → null. `AppNavHost` tries the auth parser, then the invitation parser. Manifest: `<data android:path="/invite" />` added to the main (`https`, `autoVerify`) and dev (`http` + port) filters. Paste link (dev) forwards to `DeepLinkBus` already, so it covers invitations. `InvitationLink.Valid` and `InvitationKey` redact `toString`.
- **Rationale**: FR-009; the backend link is `{SHARING_INVITATION_URL}?token=…`; using the existing per-flavor host keeps one App Links domain (`assetlinks.json`) and the release build free of `http` filters (Quality gate 5).
- **Dev environment**: the backend default `http://saveme-backend.test/invite` is not reachable from a phone; for device runs set `SHARING_INVITATION_URL=http://<devApiHost>/invite` in the backend `.env` (configuration only; quickstart). Host QA does not need it (tokens are read from the API answer or Mailpit).
- **Alternatives**: a custom scheme `saveme://invite` (rejected: P-11 codename, and links in mail must be https); a separate host (rejected: second `assetlinks.json`).

## R-09 Held invitation and the signed-out flow

- **Decision**: `HeldInvitation` (sharing domain, memory `MutableStateFlow<InvitationLink.Valid?>`, cleared at sign-out). `AppNavHost` on an invitation link: signed in → `appStack.add(InvitationKey(token))`; signed out → `held.hold(link)` and `authStack.add(InvitationKey(token))`. The invitation screen's store sees no session and shows "Sign in or create an account to answer this invitation" with "Sign in" and "Create an account" (effects to the auth screens). After the transition to signed in (the existing `resetTo(HomeKey)` effect), `AppNavHost` pushes `InvitationKey(held.token)` when one is held. "Not now" keeps it held (Home card); "Join"/"Decline" success, a `NOT_FOUND` answer or a new link replaces/clears it.
- **Rationale**: FR-010/FR-011; tokens never persisted (deep-links contract of spec 003: "The token lives in the target ViewModel only").
- **Alternatives**: persist the token until sign-in (rejected: P-09.2); a separate sign-in prompt screen (rejected: one store per screen is enough with two modes).

## R-10 The invitation screen and joining

- **Decision**: `InvitationViewModel` on `SharingService.preview(token)`, `join(token)`, `decline(token)`. Join success: `WalletMemberships.store(record)` (own live member row → the wallet is followed, R-12), `runner.refresh(ReadTarget.Wallet(walletId), force = true)` launched in the app scope (not awaited), `held.clear()`, effect `OpenWallet(walletId)`. The wallet detail shows "Getting the wallet…" while the person holds an own member row and the wallet row is absent: `WalletsService.observeJoining(id): Flow<Boolean>` (new) and a `WalletDetailState.joining` flag; it never shows "Gone" in that state. Token operations are never retried automatically and show the server's wait time on 429 (`retryAfterSeconds` → minutes, rounded up).
- **Rationale**: FR-012–FR-015; P-02.6; accept answers `WalletMember` only — the wallet record arrives from the wallet feed.

## R-11 Member actions are online-only

- **Decision**: `MemberApi.changeRole(walletId, userId, role, version)`, `remove(walletId, userId, version)`, `leave(walletId, version)`; `SharingService` validates (owner, not self, not owner row; raise-to-editor gate = `can(sharing.enabled)` and people count ≤ quota), calls, then `WalletMemberships.store(returned)` (tombstone deletes the row). Leave: `runner.upload()` first; `engine.pendingIn(SyncScope(Wallet, id))` counts what still waits (new additive DAO query + engine function, read-only); on success or `NOT_FOUND` → `WalletMemberships.leftWallet(id)` = `accessLost(id)` (participants discard their queued changes, R-13). Outcomes by code (FR-020); one action per wallet at a time (store `busy` flag).
- **Rationale**: Wallet Member is server-written (glossary: "written by Sharing through WalletService"); the phone never creates one; spec 006 R-15 made ownership transfer online-only on the same ground and the owner accepted it. PATCH/DELETE member and leave are not batch items. P-04 rule 1 still holds (the list renders from Room; the answer is stored first).
- **Alternatives**: queue role changes (rejected: a raise-to-editor queued offline may be refused for the plan later, and a removal queued behind a stale version produces clashes on a record the person cannot edit back).

## R-12 Own member rows set the wallet's role

- **Decision**: `WalletRepository.storeMember` (both appliers and `WalletMemberships.store`) updates `wallet.my_role` when `member_user_id` is the signed-in person and the stored wallet exists and the row is live (version guard unchanged, equal versions applied — spec 011 R-08). The wallet record (re-stamped by the backend's "final stamp" on every membership write, `MembershipService`) also brings `my_role`; whichever arrives first wins and they agree.
- **Rationale**: FR-022; a demotion read from the user feed alone (heads not yet read) gates screens at once.

## R-13 Losing access purges invitations too

- **Decision**: `InvitationsWalletCleanup` (a `WalletDeletionParticipant`) deletes the wallet's invitation rows and discards their queued changes (from a launched job, the spec 008 deadlock lesson). It runs on every `accessLost`/`remove` path (own tombstone on the user feed, wallet feed 404, leave). `AppGraphTest` asserts the participant list.
- **Rationale**: FR-023; the existing participants already drop transactions, rules, occurrences and references; budgets read member wallets only.

## R-14 FORBIDDEN after a demotion

- **Decision**: Transactions' `RefusalKind.NoRightHere` copy becomes "You can now only view this wallet." (EN/AR) with "Discard"; recurring rule/occurrence refusals with `FORBIDDEN` reuse it. Copy only; mapping by code unchanged.
- **Rationale**: FR-027, C3 brief ("explain read-only access rather than show a failure").

## R-15 Hooks from wallets into sharing

- **Decision**: wallets domain defines `SharingGlance` (default implementation: no invitations, no plan note):
  - `observePendingCount(walletId): Flow<Int>`
  - `observePlanLimited(walletId): Flow<Boolean>` (true when an activity page read this session shows `role_limited_by_plan` with `subject_id` = the person, newer than any later `role_changed` about them)

  Sharing binds its implementation (`DefaultSharingGlance`: invitation DAO count via domain + `PlanDemotionNotes` memory set, cleared at sign-out). Wallet detail shows "Manage members" / "See members" with "(n pending)", "Activity" and the viewer note; "Leave wallet" and the owner's "transfer ownership first" note live on the members screen (contracts/ui-and-e2e.md S1). The transactions list shows the viewer note through the same hook (transactions already depend on wallets domain).
- **Rationale**: P-10.4 without a cycle; spec 010/012 hook pattern; default-off keeps existing tests green.

## R-16 Home card

- **Decision**: auth domain gains `HeldInvitationGlimpse` (`observeHeld(): Flow<Boolean>`, default false). Home shows a card "You have an invitation to answer" with "Open"; `HomeIntent.InvitationClicked` → `HomeEffect.OpenInvitation`; `AppNavHost` pushes `InvitationKey` from `HeldInvitation`.
- **Rationale**: FR-013, clarification 3.

## R-17 Sharing a link and the clipboard

- **Decision**: commonMain interface `LinkSharer { fun share(text: String); fun copy(text: String) }` with an Android implementation in `androidMain` (`Intent.ACTION_SEND` chooser, `ClipboardManager` with `ClipDescription.EXTRA_IS_SENSITIVE` on API 33+ and the `android.content.extra.IS_SENSITIVE` extra below) and an iOS no-op (compile only, P-15). Called from the invite and members `…Route` on an `Effect.Share(text)` / `Effect.Copy(text)` whose `toString` is redacted. Share text: `invite_share_text` = "<app name>: I'd like to share a wallet with you. Open this link to join: %1$s" (EN/AR), no wallet name or amount (FR-006).
- **Rationale**: P-10.2 (platform behind an interface), P-16.5 (one-shot effects), P-09.2.
- **Alternatives**: Compose `LocalClipboardManager` (rejected: cannot mark sensitive).

## R-18 Activity

- **Decision**: `ActivityApi.page(walletId, cursor)` = `GET /wallets/{id}/activity?limit=50[&cursor=]` via `callPage<ActivityEntryDto>`. `ActivityService.page(walletId, cursor)` maps entries to `ActivityItem` (actor, action, subject, role, at) resolving names through `WalletsService.observeMembers`, `TransactionsService.observeTransaction(id, walletId)`, `RecurringService.observeRule/observeOccurrence` (`first()`, one pass per page; unresolved → kind words). Unknown `action` or `subject_type` → skipped. Nothing stored; `INVALID_CURSOR` restarts once from the first page; `NOT_FOUND` → `runner.refresh(ReadTarget.Wallet(id), force = true)` (the feed 404 path purges) and the screen closes. Each page read records plan demotions about the person into `PlanDemotionNotes` (R-15).
- **Rationale**: FR-024–FR-026; ADR-0020 (identifiers only, not a change feed; not syncable, so P-04.1 does not apply).

## R-19 `Problem.feature` and refusals

- **Decision**: `ProblemDetails` gains `val feature: String? = null`; `core.sync.Refusal` gains `val feature: String? = null` (stored JSON stays decodable: default null). The engine copies it from the problem when it records a refusal (one line, plain string — `core` never names a feature type). Sharing maps `sharing.enabled` / `sharing.max_members_per_wallet` / other to copy (FR-008, FR-014, FR-020).
- **Rationale**: P-02.4 (branch on code, then on the declared member); the C3 refresh already reacts to `FEATURE_NOT_ENTITLED`.

## R-20 Navigation

- **Decision**: new keys `WalletMembersKey(walletId)`, `InviteKey(walletId, replacing: String? = null)`, `InvitationKey(token)` (redacted `toString`), `WalletActivityKey(walletId)`. Wallet detail effects `NavigateMembers`, `NavigateActivity`, `Left(walletName)`; members effects `OpenInvite`, `OpenTransfer`; invitation effects `OpenWallet(id)`, `NavigateSignIn`, `NavigateRegister`, `Close(message)`.

## R-21 Testing strategy (owner policy)

- **Decision**: host tests only: reducers/stores for 4 screens + changed Home/wallet detail/transactions; `InviteGate`, `InviteValidation`, `InvitationLinkParser`, `ShareLinks`, `HeldInvitation`, activity mapping; `InvitationSyncHandler` + applier over the real engine with fakes; contract tests (MockEngine, examples from `sharing.yaml`) for all 10 used operations and each handled code; Room `Migration9To10Test`, `InvitationDaoTest`, `SignOutWipeTest`, `AppGraphTest`, `SharingLayeringTest`, log-capture test (no token, link, address); Roborazzi for the 4 screens (EN LTR, AR RTL, 2.0). **Live contract QA**: curl against the local backend with three throwaway accounts (owner on Plus, invitee, third) covering every call, code and feed row; real answers captured as `CapturedSharingResponses.kt` fixtures replayed through the real client, engine, handler, appliers and services. Maestro flows under `.maestro/013-sharing/` written and marked `# deferred: E2E on owner's device`.

## R-22 No new dependency

- **Decision**: none. Platform share/clipboard use Android framework classes already available.
