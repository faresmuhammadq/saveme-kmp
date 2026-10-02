# Research: Wallets & Membership (client)

Each decision lists what was chosen, why, and what was rejected. The owner was away with delegated
authority; decisions marked **(assumed default)** took the most conservative option.

## R-01 Package and boundaries

- **Decision**: New bounded context `feature.wallets` (`data`, `domain`, `ui`, `di`), named after the backend's Wallets & Ledger module. It depends on `core` (`sync`, `network`, `database`, `presentation`), `designsystem`, and other features' `domain` only: `feature.account.domain` (profile default currency), `feature.auth.domain` (`SessionManager` for the signed-in user id and `emailVerified`), `feature.billing.domain` (C3 `Entitlements`).
- **Rationale**: P-10.4; C5/C6 will read wallets through `feature.wallets.domain` the same way.
- **Alternatives**: putting wallets under `feature.ledger` (rejected: the glossary's money container is "Wallet", and C6 owns the ledger).

## R-02 Room schema v3 (assumed default: two tables, one migration)

- **Decision**: `AppDatabase` version 2 → 3 with `MIGRATION_2_3` in `core/database/Migrations.kt` creating `wallet` and `wallet_member` (data-model §1); schema exported to `shared/schemas/…AppDatabase/3.json`. Entities and DAOs live in `feature.wallets.data.local` and are registered in `AppDatabase` like the account tables. `DatabaseCleaner` deletes both tables.
- **Rationale**: P-04.1 needs the local copy; C3 adds no Room change, so this is the next version. A pure create-table migration keeps every existing row (spec 004 FR-005).
- **Alternatives**: destructive migration (rejected: loses waiting changes); JSON blob in `KeyValueStore` (rejected: wallets are syncable and queried by C5/C6).

## R-03 Local row = server copy; pending content overlays it

- **Decision**: The `wallet` row holds only what the server last said. A wallet created on the phone gets a **placeholder row** with `version = 0`, the person as `owner`, `balance_minor = opening_balance_minor`, `currency_locked = false`. Screens read a `WalletView` = row + the spec 004 `pending_change` row for `("wallet", id)` (payload overlay, clash, refusal), exactly as `ProfileView` does.
- **Rationale**: the engine's `applyServerCopy` must never touch queued content; keeping the two apart makes a clash and a "use theirs" trivial and keeps refresh merges safe.
- **Alternatives**: writing edits into the row and keeping a "dirty" flag (rejected: a refresh would overwrite unsent edits, which spec 002/004 already ruled out).

## R-04 `WalletSyncHandler` (record type `wallet`, `canRestore = false`)

- **Decision**:
  - Payload `WalletDraft` (JSON): `type, name, currency, opening_balance_minor, icon, color, archive: Boolean`.
  - `send(change)`:
    - `isDelete` → `DELETE /wallets/{id}` with body `{"version": base}`.
    - else → `PUT /wallets/{id}` (`WalletWrite` with `version = base`, 0 = create), **skipped** when `base > 0` and the draft's fields equal the stored row's; then, when `archive` is true and the wallet isn't archived yet, `POST /wallets/{id}/archive` with the version just returned (or `base` when the PUT was skipped). The last response is returned.
  - `applyServerCopy(json)`: `deleted_at != null` → delete the wallet row and its members and run every registered `WalletDeletionParticipant` (C6 hooks in); else upsert the row.
  - `onDiscarded(id)`: if the row's `version == 0` (never synced), delete the placeholder and its members.
  - `refusalFields`: pointers `/name`, `/currency`, `/type`, `/opening_balance_minor`, `/icon`, `/color` → `WalletField`.
  - Bound in Koin with `bind SyncHandler::class` in `walletsModule`.
- **Rationale**: one queue row per wallet (spec 004 FR-003) carries the edit and the archive together, so an archive never overtakes or loses an edit. A retry after a lost answer is safe: an identical PUT is answered 200 before the version check (wallets.yaml precedence), and archiving an archived wallet returns it unchanged. `canRestore = false` because deletion is final (backend spec 010, assumed default 4).
- **Alternatives**: separate record type `wallet_archive` (rejected: two rows for one record could be sent out of order, FR-007 of spec 004 is per record); PATCH for edits (rejected, R-05).

## R-05 PUT only, never PATCH (assumed default)

- **Decision**: Every save is a whole-record `PUT`. `PATCH /wallets/{id}` is not called.
- **Rationale**: the engine's contract is a per-record PUT with a base version; the whole record is always known locally; PUT creates and saves through one path, and its replay rule makes timeouts safe. Lost updates are prevented by the version either way.
- **Alternatives**: PATCH of changed fields (rejected: a second request shape, and a PATCH never creates, so a never-synced wallet would still need PUT).

## R-06 Deletion is a queued local tombstone; data hidden, then removed

- **Decision**: Confirming Delete calls `engine.enqueueDelete("wallet", id, SyncScope(Wallet, id), version)`.
  - Never-synced wallet (`version == 0`): the engine drops the queue row (spec 004 FR-004) and the repository deletes the placeholder and its members at once.
  - Otherwise every screen hides a wallet whose queue row is a delete in `Waiting`/`Sending`; the server's tombstone (or `useTheirs` on a tombstone clash) removes it for good through `applyServerCopy`.
  - A delete in `Clash` or `Refused` makes the wallet visible again with its mark (US4-S6).
  - C6 joins the same rule for transactions ("hidden while the wallet's deletion waits") and registers a `WalletDeletionParticipant` to drop them on confirmation.
- **Rationale**: P-04.5 (the queued deletion is the local tombstone); the person sees the wallet gone at once (FR-020) yet "Keep the wallet" after a clash brings everything back without having destroyed it locally.
- **Alternatives**: deleting rows at once (rejected: a delete clash resolved as "Keep the wallet" would leave the phone without its data until the next refresh, and C6's transactions could not come back).

## R-07 Clash choices per change kind

- **Decision**: The detail store reads the queue row's `clashJson`:
  - edit vs live current → "Keep mine" (`engine.keepMine`) / "Use the other device's" (`engine.useTheirs`);
  - delete vs live current → "Delete anyway" (`keepMine`) / "Keep the wallet" (`useTheirs`);
  - anything vs tombstone current → only "Accept the deletion" (`useTheirs`). The engine already refuses `keepMine` on a tombstone when `canRestore` is false.
  - The side-by-side lists only fields that differ (name, opening balance, currency, icon, colour, archived).
- **Rationale**: FR-016, FR-017; reuses spec 004 copy keys `sync_deleted_elsewhere`, `action_accept_deletion`.

## R-08 Refusal reasons and "Try again"

- **Decision**: Map the queue row's `Refusal.code` to copy: `EMAIL_NOT_VERIFIED` → "Verify your email address to save this"; `FEATURE_NOT_ENTITLED` → C3's `error_feature_not_entitled`; `FORBIDDEN` → "Only the wallet's owner can change it"; `NOT_FOUND` → "This wallet is no longer available to you"; `VALIDATION_FAILED` → field messages via `refusalFields`; anything else → generic "Not saved" with the request id (P-13.2). "Try again" (for the first two) re-enqueues the unchanged payload with the same base version (`engine.enqueue`, which clears `Refused`). "Discard" calls `engine.discard`.
- **Rationale**: FR-018; spec 004 never retries a refusal on its own, so a person who verified their address or upgraded needs an explicit resend.
- **Alternatives**: automatic retry after verification (rejected: changes the engine's no-auto-retry invariant).

## R-09 Refresh from the server (wave 1)

- **Decision**: `WalletRepository.refreshAll()` reads `GET /wallets` page by page (limit 50). Only when **every** page succeeds does it merge:
  - upsert each listed wallet as the server copy;
  - delete rows with `version > 0` that were not listed **and** have no `pending_change` row, with their members (FR-008);
  - stamp `sync_scope` key `wallets` with the refresh time.
  - `refreshMembers(id)` replaces the wallet's member rows on a complete read; a 404 removes the wallet unless a change waits.
- **Triggers**: a new `WalletsSyncCoordinator` (domain) on sign-in and foreground with a 60 s floor (as C3's `EntitlementsRefresher`); pull-to-refresh and list open call it directly (no floor); the detail refreshes members on open.
- **Rationale**: the change feed (B14) is not built; one list read per trigger is cheap at 50 wallets (spec 010 sizing target).
- **Alternatives**: per-wallet `GET /wallets/{id}` polling (rejected: N requests); deleting on a partial read (rejected: would drop wallets on a flaky page).

## R-10 Displayed balance

- **Decision**: `displayedBalance = row.balanceMinor + (draft.openingBalanceMinor − row.openingBalanceMinor) + pendingPostings.deltaFor(walletId)`, where `PendingPostings` is a `feature.wallets.domain` interface bound to a zero implementation in C4 and rebound by C6. For a placeholder row it reduces to the draft's opening balance. A "Waiting to sync" mark shows whenever the wallet's queue row exists (or `PendingPostings.hasPending(walletId)` from C6).
- **Rationale**: brief consideration 1 and FR-004 without C4 knowing C6's tables.
- **Alternatives**: recomputing balances locally from transactions (rejected: `balance_minor` is the server's; the client never re-derives truth).

## R-11 Totals per currency

- **Decision**: Pure domain function over visible, non-archived `WalletView`s: group by currency, sum `displayedBalance` as `Long` (liabilities negative), order by the profile's default currency first, then by code. No FX.
- **Rationale**: FR-003, P-03, constitution scope guard (no FX).

## R-12 Money entry

- **Decision**: A pure `AmountInput` parser in `feature.wallets.domain` turning the typed text into minor units with `MoneyFormatter.digitsFor(currency)` digits: Western digits and one decimal separator (`.` or the Arabic `٫` normalised), no more fraction digits than the currency allows, optional leading `−`/`-` for asset types only, range ±9,007,199,254,740,991. Liability types take the owed amount ≥ 0 and store its negation. Changing the currency re-validates the text.
- **Rationale**: P-03.1–3, FR-013; `Long` end to end.
- **Alternatives**: `Double` parsing (forbidden by P-03.2).

## R-13 Icon and colour sets (assumed default)

- **Decision**: In `designsystem`:
  - `WalletIcons`: icon keys `cash`, `bank`, `savings`, `card`, `loan`, `investment` mapping to the existing `AppIcons`.
  - `WalletAccent`: eight role-named accent tokens in `AppColors`, each with an uppercase `#RRGGBB` wire key.
  - `WalletCard` gains optional `iconKey`, `accentKey`, a `status` slot (waiting/clash/not saved), an archived style, and a role label.
  - A new `ChoiceGrid` picker component for icons and accents (48 dp targets, content descriptions, selected state not by colour alone).
  - Each addition gets a catalogue entry and screenshots (EN, AR RTL, 2× font). Unknown keys fall back to the type icon / default tile colour (FR-015).
- **Rationale**: P-07.2–3 (no raw colours in feature code); the backend stores keys without interpreting them. The canvas "Save Me Mobile Design System" has no wallet palette as far as known; the eight accents are built from existing tonal families and listed in `summary.md` for the owner's design review.
- **Alternatives**: free colour picker (rejected: raw colours, accessibility); no icon/colour (rejected: in the brief).

## R-14 Gates before creating or editing

- **Decision**: The editor store asks two domain checks before opening (create) and before saving (create/edit):
  - `SessionManager.state` → `emailVerified`; when false, emit `Effect.RequireVerification`, which the Route turns into the existing `EmailNotVerifiedSheet` through a callback from `AppNavHost` (wallets UI can't import auth UI, P-10.4).
  - Create only: `Entitlements.allowsAnother(QuotaFeature.Wallets, ownedLiveCount)`, `ownedLiveCount` = rows with `my_role == owner` not pending deletion (archived included, backend assumed default 1); when false, the list shows an `InlineAlert` with C3's not-entitled copy instead of opening the editor.
- **Rationale**: FR-024, FR-025; the server stays the authority (R-08 handles its refusals).

## R-15 Transfer ownership (online only)

- **Decision**: `TransferOwnershipUseCase` in domain → `WalletApi.transferOwnership(id, userId, version)`. Shown only when `my_role == owner`, no change is waiting for the wallet, and the member list has another active member. Outcomes: 200 → store the returned wallet (`my_role: editor`) and refresh members; `Offline` → "Needs a connection"; 409 / 422 `INVALID_ENUM_VALUE@/user_id` / 404 → message, `refreshOne(id)` + `refreshMembers(id)`.
- **Rationale**: membership is written only by the server (`WalletMember` has no write operation), so it is not a syncable edit the phone owns; spec 002 treats ending a session the same way. A waiting change would be sent with a stale version after the transfer, so transfer waits for it.
- **Alternatives**: queueing the transfer (rejected: not a per-record write of a phone-owned record; the engine has no shape for it).

## R-16 Navigation

- **Decision**: New keys `WalletsKey`, `WalletDetailKey(walletId)`, `WalletEditorKey(walletId: String? = null)`. Home gets one "Wallets" row (`home.wallets`) that navigates to `WalletsKey` via a new `HomeEffect.NavigateWallets` (minimal, append-only edit of a hotspot). Home stays the start screen (spec Assumptions).
- **Rationale**: Home is a placeholder in `feature.auth.ui`; a separate screen avoids reshaping it while C3 and later features also touch it.

## R-17 E2E strategy

- **Decision**: Flows in `.maestro/006-wallets/` per the `speckit-e2e` skill:
  - `api.js` gains wallet actions for the second session (`createWallet`, `getWallet`, `saveWallet`, `deleteWallet` via `http.request` with method `DELETE` and a JSON body, `listWallets`).
  - Offline scenarios reuse C3's two-part runner mechanism (`# e2e-between:`) with two new runner actions: `network-off` (`adb reverse --remove tcp:<port>`) and `network-on` (re-add it). Part 1 cuts the route, part 2 acts offline and asserts "Waiting to sync", part 3 (after `network-on`) asserts it synced.
  - If the device keeps reaching the backend another way (Wi-Fi host), those scenarios fall back to manual (`external`).
- **Rationale**: spec 004 could not drive offline end to end; cutting the reverse port is the one lever the runner has. The brief's checkpoint (offline create syncs; second-session edit shows; stale edit clashes) becomes executable.
- **Risks**: Maestro's `http.delete` may not send a body — use `http.request`; the archive/transfer paths need a second member for transfer (C9), so US5-S3..S5 stay manual (`not-built`).

## R-18 No new dependencies

- **Decision**: none. UUIDv7 from `kotlin.uuid.Uuid.generateV7()` (already used for correlation ids); Ktor sends a body on `DELETE` with `setBody` on an `HttpMethod.Delete` request.
