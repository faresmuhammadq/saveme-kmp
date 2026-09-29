# C13 · Reports (client)

**Pairs with**: backend [B13](../../../saveme-backend/specs/briefs/B13-reports.md) · **Call with**: `/speckit-specify specs/briefs/C13-reports.md`

## Description

Per-wallet and cross-wallet reports: period summary (income, expenses, net, balance start/end),
spending by category, and a six-month trend, with charts that work in Arabic RTL.

## Consumes

`GET /wallets/{id}/reports/summary|by-category|trend` and `/reports/*`, including
`meta.excluded_wallets` and `include_planned`.

## Can build before the backend ships

Chart components in the design system (catalogued, screenshot-tested in EN/AR/2x), report
screens, and an offline fallback computed from local transactions for the current period.

## Needs from the backend

Response shapes (`{ label, value }` arrays), the `Period` definition shared with budgets, and
whether `reports.advanced` gates any of it.

## Considerations

- Reports are read models: cache the last server answer, label it with its time when offline.
- Cross-wallet totals only within one currency; show excluded wallets instead of silently
  dropping them.
- Charts need accessible text alternatives (P-08).

## Integration checkpoint

E2E: seeded transactions produce the expected summary and category shares; offline shows the
cached report with its "Updated …" time.
