# Client feature briefs

The mobile client's half of each backend feature brief, recorded 2026-09-29. Each client brief
`Cn` pairs with backend brief `Bn` in
[`saveme-backend/specs/briefs/`](../../../saveme-backend/specs/briefs/README.md), and the two are
built **in parallel**: the backend owns the contract, the client builds against it before the
endpoints ship, and the pair meets at an integration checkpoint.

**To start a feature**, run `/speckit-specify specs/briefs/C04-wallets.md` (for example); the
skill does not resolve brief IDs, so pass the path. The brief is the input; its
"Needs from the backend" list is what must be frozen in the backend spec first. As with the
backend, spec directories take the next free number, so `C4` will not become `specs/004-…`.

## Pairing and state

| Client | Backend | Backend state (2026-09-29) | Client state | Client needs first |
|---|---|---|---|---|
| [C1](C01-auth-completion.md) Auth completion | B1 → spec 007 | Built | Sign-in, sign-out, sessions done (001, 002); **register, verify, reset not built** | — |
| — Profile & preferences | B2 → spec 008 | Built | **Done** in spec 002 (US3) | — |
| [C3](C03-plans-entitlements.md) Plans & entitlements | B3 → spec 009 | Built, uncommitted | `EntitlementSummaryDto` only | C1 |
| [C4](C04-wallets.md) Wallets & membership | B4 | Brief | — | C3, C14 engine |
| [C5](C05-categories.md) Categories | B5 | Brief | — | C4 |
| [C6](C06-transactions.md) Transactions ledger | B6 | Brief | Design-system rows and money formatting exist | C4, C5 |
| [C14](C14-sync-engine.md) Sync engine | B14 | Brief | Profile/sessions pull only (`AccountSyncCoordinator`) | — (engine), B14 (batch) |
| [C7](C07-recurring.md) Recurring transactions | B7 | Brief | — | C6 |
| [C8](C08-budgets.md) Budgets | B8 | Brief | `BudgetProgress` component exists | C5, C6 |
| [C9](C09-sharing.md) Sharing & invitations | B9 | Brief | `WalletMembers` catalog sample | C3, C4 |
| [C10](C10-billing-google-play.md) Google Play billing | B10 | Brief | — | C3 |
| [C11](C11-redeem-code.md) Redeem code | B11 (app side only) | Brief | — | C3 |
| [C13](C13-reports.md) Reports | B13 | Brief | — | C6, C8 |
| [C15](C15-export-deletion.md) Export & deletion completion | B15 | Brief | Deletion request/cancel done (002) | C6, C9 |
| [C16](C16-push.md) Push notifications | B16 | Brief | — | C1, C7, C9 |

No client brief: **B12** back-office and **D1/D2** admin dashboard are operator web surfaces; the
InstaPay claim itself (**B11**) is web-only by policy (P-12 rule 4).

## Waves

Pairs in the same wave run in parallel on both repos. A wave's client work starts when the
backend spec of its pair has a frozen API section (see protocol), not when it is implemented.

| Wave | Backend | Client | Why together |
|---|---|---|---|
| 0 | B1–B3 done; review | **C1**, **C3**, **C14 engine** | Close the auth gap, read entitlements, build the outbox before the first syncable money entity |
| 1 | B4 → B5 → B6, B14 contract | **C4**, **C5**, **C6** (+ C14 on per-record PUT) | The core loop: wallet → category → transaction, local-first |
| 2 | B14 impl, B7, B8 | **C14 batch**, **C7**, **C8** | Batch upload and changes feed replace per-record sync; recurring and budgets read the ledger |
| 3 | B9, B10, B11, B13 | **C9**, **C10**, **C11**, **C13** | Monetisation and sharing; reports on top of budgets |
| 4 | B15, B16 | **C15**, **C16** | Account end-of-life and notifications need everything above |

## Parallel protocol

1. **Contract first.** The backend spec for `Bn` writes its OpenAPI fragment and error codes
   before implementation. The client spec for `Cn` starts once that fragment is reviewed; the
   `before_specify` hook (`speckit-contract-check`) records the backend commit it was written
   against in `**Backend contract**`.
2. **Build without the server.** Everything the client can own is built before the endpoint
   exists: Room schema mirroring the syncable shape (P-04), domain, MVI screens, and contract tests
   on a mock HTTP engine seeded from the OpenAPI examples (P-14.3). Local-first writes queue in the
   outbox (C14) and simply stay pending until the endpoint answers.
3. **Freeze list.** Each brief's "Needs from the backend" lists the fields and codes the client
   binds to. Changing one after freeze is a backend spec amendment **and** a client change in the
   same wave. The `entitlements.plan` string → object change that crashed every sign-in on
   2026-09-29 is the failure this rule prevents.
4. **Integration checkpoint.** When `Bn` is built on the local dev backend: rerun
   `speckit-contract-check`, fix drift, then run `/speckit-e2e` for `Cn`. The pair is done when the
   E2E report has no `FAIL (app)` or `FAIL (env)` rows attributable to the contract.
5. **One side blocked.** If the backend slips, the client ships behind the local-only path (data
   stays on the phone, pending sync) rather than faking server responses in production code.
   If the client slips, the backend is unaffected: its contract tests stand alone.

## Conventions

- Brief IDs mirror backend IDs; the text is the client's scope only. The backend brief remains the
  authority on behaviour and data; a client brief must not restate server rules as client rules.
- Names follow the backend glossary (P-01): wallet, member, category, transaction, posting.
- Money is integer minor units plus currency code on the wire and in Room (P-03).
