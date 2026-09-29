# C10 · Google Play billing (client)

**Pairs with**: backend [B10](../../../saveme-backend/specs/briefs/B10-billing-google-play.md) · **Call with**: `/speckit-specify specs/briefs/C10-billing-google-play.md`

## Description

Buy a subscription or the lifetime product with Play Billing from the Plan screen (C3), post the
purchase token to the backend, and show the new entitlements. **Restore purchases** sends all
owned tokens after sign-in and on demand.

## Consumes

`POST /billing/google/purchases`, `POST /billing/google/restore`; product ids from
`plan_prices.provider_product_id` via `GET /plans`.

## Can build before the backend ships

Play Billing Library integration (use the `play-billing-library-version-upgrade` skill for the
current version), purchase flow, pending/cancelled states, a fake billing client for tests.

## Needs from the backend

Request/response shapes, `PURCHASE_ALREADY_LINKED` and verification error codes, and whether the
server acknowledges (it must, within 3 days) so the client never double-acknowledges.

## Considerations

- Android only (iOS out of MVP scope). Keep billing behind an `expect/actual` boundary.
- A purchase whose server call fails stays pending and retries; never grant locally.
- License testers and test tokens for E2E; real purchases are never automated.

## Integration checkpoint

Manual with a license tester: buy monthly, entitlements switch to Plus; reinstall, sign in,
restore links the purchase.
