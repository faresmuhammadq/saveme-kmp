# C6 · Transactions ledger (client)

**Pairs with**: backend [B6](../../../saveme-backend/specs/briefs/B06-transactions-ledger.md) · **Call with**: `/speckit-specify specs/briefs/C06-transactions.md`

## Description

The app's core loop: record income, expenses and transfers in a wallet, fast and offline. Entry
screen (amount, kind, category, date, note, optional counterparty), transaction list per wallet
with date headers, search and filters (date range, category, kind), edit and delete. Transfers
between two same-currency wallets the user may write to.

## Consumes

`GET /wallets/{id}/transactions` (cursor, filters, search), `PUT/PATCH/DELETE
/wallets/{id}/transactions/{tid}`; the changes-feed row shape B6 fixes for B14.

## Can build before the backend ships

The whole local ledger: Room entities for transactions (and postings only if the client needs
them; prefer transaction-level rows), local balance projection, entry and list screens with the
existing `TransactionRow`, `DateHeader` and money components, local search. Writes queue in C14.

## Needs from the backend

The transaction schema, `kind` enum, how a transfer is represented on the wire (one record with
two wallets, or two linked records), the role needed on each side of a transfer, error codes
(`CURRENCY_MISMATCH`-style for cross-currency transfers, version conflict), and the feed row shape.

## Considerations

- Entry must be one screen and a few taps; this is where retention is won.
- Backdated transactions are allowed and re-cut budget progress (C8).
- Local balance = server balance + pending local changes; reconcile on pull.
- Conflicts resolved on the phone (P-04.4): show both versions, never overwrite silently.
- Dates are calendar dates (`YYYY-MM-DD`) in the user's timezone (P-05).

## Integration checkpoint

E2E: record 3 transactions offline, go online, all sync; transfer moves both balances; edit on
the API session → conflict choice on the phone.
