# C16 · Push notifications (client)

**Pairs with**: backend [B16](../../../saveme-backend/specs/briefs/B16-devices-push.md) · **Call with**: `/speckit-specify specs/briefs/C16-push.md`

## Description

Register the FCM token for this device session, keep it current, and drop it on sign-out.
Notification preferences screen. Handle the notification types: recurring occurrence due,
invitation received, role changed or removed, subscription expiring/expired, InstaPay claim
approved/rejected. Tapping one deep-links to the right screen.

## Consumes

`PUT /devices/{id}/push-token`, the preferences endpoint, and the push payload schema.

## Can build before the backend ships

Firebase Messaging integration (needs dependency approval, as on the backend), channels, the
Android 13+ notification permission prompt at a meaningful moment, deep-link routing, the
preferences screen.

## Needs from the backend

Payload schema (type, ids, no amounts), the preference keys, and quiet-hours behaviour.

## Considerations

- No amounts on the lock screen; the payload carries ids, the app renders text.
- Token tied to the device session; revoked sessions stop receiving.

## Integration checkpoint

E2E (emulator with Play services): an invitation to the test account produces a notification
that opens the invitation.
