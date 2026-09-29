# C15 · Export & deletion completion (client)

**Pairs with**: backend [B15](../../../saveme-backend/specs/briefs/B15-export-data-deletion.md) · **Call with**: `/speckit-specify specs/briefs/C15-export-deletion.md`

## Description

Request an export of a wallet or all data (CSV/XLSX), see its progress, and open the 24-hour
download link. Extend account deletion (built in 002) with the shared-wallet step: before
leaving, the user chooses the new owner of each shared wallet they own with more than one other
member.

## Consumes

`POST /exports` and its status/download endpoints; the B15 ownership-choice contract on the
deletion request.

## Can build before the backend ships

Export request and status screens, the owner-choice step in the existing deletion flow, the
Play-subscription notice ("cancel in Google Play; deletion does not cancel it").

## Needs from the backend

Export job shape and states, the one-active-export error, the ownership-choice fields, and the
fallback rules when the only other member is a viewer.

## Considerations

- `export.enabled` may gate exports (C3).
- Download via the system browser or share sheet; don't keep export files in app storage.

## Integration checkpoint

E2E: request an export, it completes, the link opens; deletion with a shared wallet asks for the
new owner, and the other member sees themselves as owner after the grace job runs (artisan).
