# C3 · Plans & entitlements (client)

**Pairs with**: backend [B3](../../../saveme-backend/specs/briefs/B03-plans-entitlements.md) → spec 009 (built, not yet committed there) · **Call with**: `/speckit-specify specs/briefs/C03-plans-entitlements.md`

## Description

Show the user their plan and what it allows, and gate features on entitlements. Read
`GET /me/entitlements` and `GET /me/subscription`, cache them locally with the account snapshot,
refresh on sign-in, foreground and after any billing action. A "Plan" screen lists the catalogue
from `GET /plans` with localised prices, the current plan and period end, and the only two
billing actions the app may offer: **Restore purchases** (C10) and **Enter code** (C11).
Provide one `Entitlements` gate (`can(key)`, `quota(key)`) for every later feature.

## Consumes

`GET /plans`, `GET /me/entitlements`, `GET /me/subscription`; `entitlements` in the sign-in
response (`plan: PlanRef {id, name}`, `features`).

## Can build before the backend ships

Built already on the backend side. The client can start immediately.

## Needs from the backend

- The `Features` schema keys and types (`sharing.enabled`, `sharing.max_members_per_wallet`,
  `export.enabled`, `reports.advanced`, `wallets.max`), and which are nullable ("unlimited").
- Commit the Billing work in saveme-backend so the contract baseline is a real commit.

## Considerations

- Gate on `features`, never on the plan name (P-12.1); the plan name is display only.
- Downgrade: members beyond the Free quota become viewers, not removed; the UI must explain
  read-only access rather than show a failure.
- No purchase buttons that open a web checkout (P-12.2, P-12.4).

## Integration checkpoint

E2E: Free user sees the Free plan and its limits; an operator-granted Plus (B3 admin grant) shows
Plus after refresh without re-sign-in.
