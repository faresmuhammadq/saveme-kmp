# Data Model: Sharing & Invitations (client)

**Feature**: [spec.md](spec.md) · **Research**: [research.md](research.md) · **Date**: 2026-10-05

Room `saveme.db` goes from v9 to v10. Instants are ISO-8601 UTC text (P-05). No money is stored by
this feature.

## 1. Table `invitation` (new)

The server's last copy of each pending invitation of a wallet the person belongs to (`Invitation`
in `sharing.yaml` v1.9, carried by the wallet feed), or a placeholder (`version = 0`) for one created
on this phone and not yet accepted.

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | TEXT PK | no | client UUIDv7 (created on this or another phone) |
| `account_id` | TEXT | no | the signed-in person; every query filters by it |
| `wallet_id` | TEXT | no | the wallet scope |
| `role` | TEXT | no | `editor` / `viewer`; another value is kept and shown as "Member" (never sent back: immutable) |
| `channel` | TEXT | no | `email` / `link` |
| `invitee_email` | TEXT | yes | the address while pending (email); null for a link. Never logged; `toString` omits it |
| `invited_by` | TEXT | no | inviter's user id |
| `status` | TEXT | no | always `pending` for a stored row (closed rows are tombstones, never stored) |
| `expires_at` | TEXT | yes | server instant; null on a placeholder |
| `version` | INTEGER | no | 0 = placeholder |
| `change_seq` | INTEGER | no | 0 for a placeholder |
| `created_at` | TEXT | no | server's, or the phone's instant for a placeholder (orders the list) |
| `updated_at` | TEXT | no | |
| `refreshed_at` | TEXT | no | when the phone stored this copy |

Index: `(account_id, wallet_id, status)`. A tombstone (`deleted_at` set, any closed `status`)
removes the row unless a change to it waits (then `acceptsClash` takes it, research R-03). The
share link and the invitation secret are **never** columns (P-09.2).

**Derived per row** (domain `InvitationView`): `waiting` (a queued create or withdrawal), `refused`
(with `Refusal.code` and `feature`), `expired` = `expires_at != null && expires_at <= now`,
`linkHeld` = `channel == link && ShareLinks` holds the id, `hidden` = a withdrawal waits.

## 2. Table `wallet_member` (C4, unchanged schema)

Rows still come from the wallet feed and the user feed. Two behaviours change (research R-11,
R-12):

- Rows returned by `PATCH`/`DELETE /wallets/{id}/members/{uid}`, `POST /wallets/{id}/leave` and
  `POST /invitations/accept` are stored through `WalletMemberships.store` with the same version
  guard (`stored.version > incoming.version` → ignored; equal applied); a tombstone deletes the row.
- When the stored row is the signed-in person's own and live, `wallet.my_role` is set to its role
  (if the wallet row exists).

## 3. Table `wallet` (C4, unchanged schema)

`my_role` is now also written from the person's own member row (§2). A wallet whose own member row
exists but whose record has not been read yet is "joining" (`WalletsService.observeJoining`).

## 4. Sync engine rows (`pending_change`, unchanged schema)

| Record type | Scope | Payload | Sent as |
|---|---|---|---|
| `invitation` (create) | `wallet:<wallet_id>` | `InvitationDraft {role, invitee_email}` JSON (address included; the table is device-local and cleared at sign-out; never logged) | `PUT …/invitations/{id}` with `version: 0`, per record |
| `invitation` (withdraw) | `wallet:<wallet_id>` | empty | `DELETE …/invitations/{id}` with `{version}`, per record |

`core.sync.Refusal` gains `feature: String?` (default null; existing stored refusals decode
unchanged). New read-only engine function `pendingIn(scope)` (DAO query by `scope` key) for the
leave confirmation.

## 5. Migration 9 → 10

```sql
CREATE TABLE IF NOT EXISTS `invitation` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL,
  `wallet_id` TEXT NOT NULL, `role` TEXT NOT NULL, `channel` TEXT NOT NULL, `invitee_email` TEXT,
  `invited_by` TEXT NOT NULL, `status` TEXT NOT NULL, `expires_at` TEXT, `version` INTEGER NOT NULL,
  `change_seq` INTEGER NOT NULL, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL,
  `refreshed_at` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE INDEX IF NOT EXISTS `index_invitation_account_id_wallet_id_status`
  ON `invitation` (`account_id`, `wallet_id`, `status`);
UPDATE `sync_scope` SET `feed_cursor` = NULL, `feed_read_at` = NULL, `feed_position` = NULL
  WHERE `scope` LIKE 'wallet:%';
```

Every other row is kept, including the user scope's position. `10.json` is exported. The DDL must
equal Room's generated schema (checked by `Migration9To10Test` against `10.json`).

## 6. Memory-only state (never stored, cleared at sign-out)

| Holder | Content | Lifetime |
|---|---|---|
| `HeldInvitation` | one `InvitationLink.Valid(token)` | until answered, replaced by a new link, sign-out, or process end |
| `ShareLinks` | `invitationId → ShareLink(url)` | until the invitation leaves the phone, sign-out, or process end |
| `PlanDemotionNotes` | wallet ids where the latest role change about the person is `role_limited_by_plan` (from activity pages read) | until sign-out or process end |
| Invitation screen state | the token, the preview | the screen's store |
| Activity screen state | loaded entries and the next cursor | the screen's store |

All five redact secrets in `toString` (`token=<redacted>`, `url=<redacted>`).

## 7. Domain models (sharing)

- `InvitationDraft(role: InvitedRole, email: String?)` — serialisable payload.
- `InvitationView(id, walletId, role, channel, email, expiresAt, waiting, refusal, expired, linkHeld)`.
- `InvitationPreview(invitationId, walletId, walletName, role, inviterName, expiresAt, alreadyMember)`.
- `MemberView` (wallets `Member` + `version`, `isOwner`), from `WalletMemberships.observe(walletId)`.
- `ActivityItem(id, at, actor: Actor(Named|FormerMember|Automatic), action: ActivityAction, subject: Subject(kind, name?, transactionId?), role)`.
- `InviteCheck` = `Allowed | NotVerified | SharingNotIncluded | Full(max)`.
- Results: `InviteResult`, `WithdrawResult`, `MemberActionResult`, `LeaveResult`, `PreviewResult`, `JoinResult`, `DeclineResult`, `ActivityPage` (data-model of contracts/sharing-domain.md).

## 8. State transitions

```text
Invitation (on this phone)
  [create]           → placeholder v0, queued            ("Waiting to sync")
  201 / 200          → stored copy vN, link offered (201 link only)  ("Pending · Expires …")
  refused            → placeholder + refusal             ("Not sent": Discard / Try again)
  [withdraw] v0      → removed, queued create discarded
  [withdraw] vN      → hidden, queued delete → tombstone removes
  feed tombstone     → removed (accepted / declined / revoked / expired / wallet deleted)
  now ≥ expires_at   → shown "Expired", not counted, withdrawable

Own membership
  accept 200         → own member row → wallet followed → wallet read from start ("Getting the wallet…")
  role change        → member row → wallet.my_role
  leave 200 / 404, own tombstone (user feed), wallet feed 404 → accessLost(wallet): wallet + members + invitations + transactions + rules + occurrences + references + position + their queued changes
```
