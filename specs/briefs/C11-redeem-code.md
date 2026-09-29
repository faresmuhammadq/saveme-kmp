# C11 · Redeem code (client)

**Pairs with**: backend [B11](../../../saveme-backend/specs/briefs/B11-billing-instapay.md) (app side only) · **Call with**: `/speckit-specify specs/briefs/C11-redeem-code.md`

## Description

An **Enter code** action on the Plan screen (C3): the user types the redeem code emailed after an
InstaPay claim is approved; the subscription attaches to their account and entitlements refresh.
Nothing else from B11 is in the app: no InstaPay address, amount, claim form or link to them
(P-12.3, P-12.4).

## Consumes

`POST /billing/redeem` (with `Idempotency-Key`).

## Can build before the backend ships

Screen, validation, idempotency key handling, contract tests.

## Needs from the backend

Code format (for input masking), error codes (expired, used, email mismatch, rate limit), and the
response (new entitlements or subscription).

## Considerations

- Codes are bound to the claim's email: the error for a mismatch must tell the user to sign in
  with that address, without revealing the address.
- Overlap with an active Play subscription follows B3's precedence; show which plan now governs.

## Integration checkpoint

E2E: operator approves a claim (back-office or artisan), the code is read from the dev mail log,
entered in the app, and Plus appears.
