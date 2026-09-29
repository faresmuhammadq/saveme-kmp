# C4 · Wallets & membership (client)

**Pairs with**: backend [B4](../../../saveme-backend/specs/briefs/B04-wallets-membership.md) · **Call with**: `/speckit-specify specs/briefs/C04-wallets.md`

## Description

Create, edit, archive and delete wallets locally first: type (glossary enum, savings shown as
حصالة), name, currency (defaulting to the profile's default currency), opening balance,
icon/colour. A wallet list with balances and a total per currency, and a wallet detail screen as
the entry point for transactions (C6). Show the member list read-only (only owners exist until
C9) and the user's role on each wallet, which gates every edit action.

## Consumes

`GET /wallets`, `PUT /wallets/{id}`, `PATCH /wallets/{id}`, `POST /wallets/{id}/archive`,
`DELETE /wallets/{id}`, `GET /wallets/{id}/members`, `POST /wallets/{id}/transfer-ownership`.

## Can build before the backend ships

Room entity and DAO with the syncable fields (`id` UUIDv7, `version`, `change_seq`,
`deleted_at`); repository writing through the C14 outbox; list/detail/editor screens with the
existing `WalletCard` and money components; contract tests from the B4 OpenAPI examples.

## Needs from the backend

The wallet schema (field names, `type` enum, `balance_minor`, `opening_balance_minor`), the
permission matrix table, the error codes for currency change after the first transaction and for
role violations, and whether `wallets.max` is enforced.

## Considerations

- The displayed balance is the server's cached balance plus local pending postings; say which in
  the UI when offline ("pending sync").
- Currency becomes read-only after the first transaction; mirror the server rule locally.
- Deleting a wallet tombstones its transactions locally too (P-04.5).
- Money in minor units with the currency's fraction digits (P-03); no FX totals across currencies.

## Integration checkpoint

E2E: create a wallet offline, it syncs when online; edit on a second session (API) shows on the
phone after pull; a stale edit shows the conflict choice.
