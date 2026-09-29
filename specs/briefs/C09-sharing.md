# C9 · Sharing & invitations (client)

**Pairs with**: backend [B9](../../../saveme-backend/specs/briefs/B09-sharing-invitations.md) · **Call with**: `/speckit-specify specs/briefs/C09-sharing.md`

## Description

Owners invite by email or share link with a role (editor/viewer), see pending invitations, revoke
them, change roles and remove members; invitees accept in the app (deep link); members can leave.
A per-wallet activity feed shows who added, edited or deleted what. When access is lost, the
wallet's local data is purged.

## Consumes

`POST/GET/DELETE /wallets/{id}/invitations`, `POST /invitations/{token}/accept`,
`PATCH/DELETE /wallets/{id}/members/{uid}`, `POST /wallets/{id}/leave`, the activity feed, and
the membership change rows in the sync feed (C14).

## Can build before the backend ships

Member and invitation screens using the `WalletMembers` catalog component, role-gated UI
everywhere (reading the role cached with the wallet), link parsing, the local purge routine.

## Needs from the backend

The purge signal (which feed row says "you lost access"), invitation link format, quota error
codes (`sharing.enabled`, `sharing.max_members_per_wallet`, pending invites counted), and the
activity feed shape.

## Considerations

- Gate the invite action on entitlements (C3); when the plan lapses, extra members turn viewer
  and the UI explains it.
- A partner's categories show through category references (C5).
- Share-link text must not include amounts or wallet balances.

## Integration checkpoint

E2E with a second API session: invite, accept as the second account, both see a new transaction;
remove the member → their local copy is purged on next sync.
