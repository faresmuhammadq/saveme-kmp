# E2E report: 006-wallets (partial, deferred)

**Status**: PARTIAL. The owner stopped emulator testing mid-run (2026-10-02); the full run moves to
the owner's physical phone. The results below are from the emulator runs made before that, and are
not the T078 sign-off.

**Run**: 2026-10-02 · device emulator-5554 · saveme-kmp@c6610b1 + this feature's uncommitted tree · saveme-backend@8aee567 (dev container; its tree also holds uncommitted recurring-transactions work)
**Result**: 21 passed · 3 failed (flow, fixed, not rerun) · 4 not run · 7 manual · 35 scenarios

## How the runs went

1. **Run 1**: every flow failed at launch. The app crashed on start with a Koin `StackOverflowError`:
   the sync engine is built from every `SyncHandler`, `WalletSyncHandler` needs `WalletRepository`,
   and the repository's definition resolved `SyncEngine` eagerly. **FAIL (app), fixed**: the
   repository now resolves the engine when it checks the queue (`WalletsModule.kt`); new
   `AppGraphTest` starts the real Koin graph and fails with the old wiring.
2. **Run 2** (after the fix): exposed the defects below; stopped once `seed-wallet.yaml` was found to
   fail parsing in every API-seeded flow.
3. **Run 3** (all fixes so far): the results in the table. Stopped by the owner's change of plan
   after US4-S4.

Offline parts use the runner's `network-off` (removes `adb reverse`). It does take the app offline:
US1-S3 part 2 showed "Waiting to sync" on a wallet made while cut off and part 3 saw it sync once the
route came back. US2-S8 showed the "Updated …" caption, which only appears when the app is offline.

| Scenario | Flow | Result | Notes |
|---|---|---|---|
| US1-S1 | US1-S1-create-online.yaml | pass | server has `opening_balance_minor` 25000 |
| US1-S2 | US1-S2-currency-preselected.yaml | FAIL (flow) | fixed, not rerun. Run 2: `assertVisible "EGP"` failed because the currency select field showed no value to accessibility (**app**, fixed: `AppSelectField` overlay now carries label and value, `SelectFieldSemanticsTest`). Run 3: EGP passed; `tapOn "USD"` failed because USD is below the fold in the sheet; the flow now scrolls to it |
| US1-S3 | US1-S3-create-offline.part1–3.yaml | pass | 3 parts, network-off/on |
| US1-S4 | US1-S4-savings-in-arabic.yaml | pass | "حصالة" |
| US1-S5 | US1-S5-liability-amount-owed.yaml | pass | after a flow fix: the card renders " owed" (leading space) as its own text node |
| US1-S6 | US1-S6-blank-name-error.yaml | pass | after a flow fix (keyboard covered Save); flow now also checks the over-long (101 characters) error |
| US1-S7 | — | manual | external: no launch plan limits `wallets.max` |
| US1-S8 | US1-S8-unverified-asks-to-verify.yaml | pass | |
| US2-S1 | US2-S1-list-shows-cards.yaml | pass | Run 2 failed: `seed-wallet.yaml` did not parse (unquoted `${…: …}` default); fixed in run 3 |
| US2-S2 | US2-S2-totals-per-currency.yaml | pass | |
| US2-S3 | US2-S3-offline-opening-balance-edit.part1–2.yaml | pass | 2 parts |
| US2-S4 | US2-S4-other-device-edit-after-pull.yaml | pass | |
| US2-S5 | US2-S5-other-device-delete-after-pull.yaml | pass | |
| US2-S6 | US2-S6-empty-state.yaml | pass | |
| US2-S7 | US2-S7-wallet-detail.yaml | pass | |
| US2-S8 | US2-S8-offline-shows-last-known.part1–2.yaml | pass | 2 parts |
| US3-S1 | US3-S1-edit-name.yaml | pass | |
| US3-S2 | US3-S2-type-is-read-only.yaml | pass | |
| US3-S3 | — | manual | not-built: locking a currency needs a recorded transaction (C6) |
| US3-S4 | — | manual | not-built: as US3-S3 |
| US3-S5 | US3-S5-clash-with-other-device.yaml | pass | brief checkpoint |
| US3-S6 | US3-S6-keep-mine.yaml, US3-S6-use-theirs.yaml | pass | both choices |
| US3-S7 | US3-S7-deleted-elsewhere-accept.yaml | pass | |
| US3-S8 | — | manual | not-built: needs an editor/viewer member (C9) |
| US4-S1 | US4-S1-archive.yaml | FAIL (flow) | fixed, not rerun: `walletDetail.confirm` not found. The confirmation is a dialog window, where test tags are not exposed (`testTagsAsResourceId` is set on the activity window only); dialog buttons are now tapped by text |
| US4-S2 | US4-S2-archive-confirmation-says-final.yaml | FAIL (flow) | fixed, not rerun: same cause (`walletDetail.cancel`) |
| US4-S3 | US4-S3-delete-confirmation-names-wallet.yaml | pass | ran with the text-selector fix |
| US4-S4 | US4-S4-delete.yaml | pass | ran with the text-selector fix |
| US4-S5 | US4-S5-delete-never-sent.part1–3.yaml | not run | deferred: E2E on owner's device |
| US4-S6 | US4-S6-delete-clash.yaml | not run | deferred: E2E on owner's device |
| US5-S1 | US5-S1-members-section.yaml | not run | deferred: E2E on owner's device |
| US5-S2 | US5-S2-no-transfer-with-one-member.yaml | not run | deferred: E2E on owner's device |
| US5-S3 | — | manual | not-built: needs a second member (C9) |
| US5-S4 | — | manual | not-built: as US5-S3 |
| US5-S5 | — | manual | not-built: as US5-S3 |

## Still to do on the owner's device (T078)

Run `.specify/scripts/bash/run-e2e.sh 006-wallets` in full, including the flows not rerun after
their fix (US1-S2, US4-S1, US4-S2) and those not run (US4-S5, US4-S6, US5-S1, US5-S2), and overwrite
this report. Quickstart manual checks (T076) were not run.

No backend defect was found; every server-side check (`api.js` slot B) answered as the contract says.
