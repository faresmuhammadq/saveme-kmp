# Summary: Wallets & Membership (spec 006, C4)

**Date**: 2026-10-02 · **Status**: implemented, host gates green, partial device E2E (rest deferred to the owner's phone), committed on branch `006-wallets` (769d349) · **Backend contract**: `saveme-backend@a42f5d0` (`wallets.yaml` unchanged since 310aa24).

## What was built

- **`feature.wallets`** (data, domain, ui, di) with Room schema v3: tables `wallet` and `wallet_member`, `MIGRATION_2_3`, exported `3.json`; cleared at sign-out.
- **`WalletApi`**: seven calls (list, get, put, archive, delete with a JSON body, members, transfer-ownership). `PATCH` is not used.
- **`WalletSyncHandler`** on the spec 004 engine (record type `wallet`, `canRestore = false`): PUT creates and saves; archive follows the PUT, and an archive-only change skips the PUT; DELETE carries the version in its body; refusal pointers map to `WalletField`.
- **`DefaultWalletsService`**: create gated by a verified address and `Entitlements.allowsAnother(QuotaFeature.Wallets, …)` (C3); forced refresh plus a 60 s foreground floor; online-only ownership transfer; clash, refusal and retry choices; per-currency totals; `PendingPostings` and `WalletDeletionParticipant` hooks for C6.
- **Three MVI screens** (list, detail, editor): offline create, edit, archive and delete; archived section; totals; "Waiting to sync / Changed / Not saved" marks; members; ownership transfer. Reached from a new `home.wallets` row; `WalletsSyncCoordinator`.
- **Design system**: `WalletAccent` tokens, `WalletIcons`, `ChoiceGrid`, `WalletCard` options with `WalletStatusLine`, catalogue section, test tags on `AppAlertDialog`.
- About 100 English/Arabic strings ("حصالة" for savings).
- **E2E plumbing**: 35 Maestro flows in `.maestro/006-wallets/`, wallet actions in `api.js`, `run-e2e.sh` network-off/on steps (adb reverse) and up to nine parts per flow.
- `core.sync.Refusal` gained an optional `requestId`, so "Not saved. Reference: …" can show the server's request id.

### Fixed during the device run

1. **Crash at launch**: a Koin dependency loop (`SyncEngine` → wallet handler → `WalletRepository` → `SyncEngine`) caused a `StackOverflowError`. The repository now resolves the engine lazily; `core/di/AppGraphTest` starts the real graph and fails on the old wiring.
2. **Accessibility**: `AppSelectField`'s click overlay was an unlabelled button with no value. It now carries the label, value and state (`SelectFieldSemanticsTest`); this also fixes the profile pickers.
3. Flow fixes: `common/seed-wallet.yaml` parse error, " owed" text match, keyboard covering Save, scroll to USD, dialog buttons tapped by text.

## Results

- Gradle gates green: `testAndroidHostTest` (714+ tests, incl. `AppGraphTest`, `SelectFieldSemanticsTest`), `verifyRoborazziAndroidHostTest`, `assembleDevDebug`, iOS simulator compile.
- **Partial emulator E2E** (stopped by the owner's decision to defer device testing): 21 of 28 automatable scenarios passed, including the brief checkpoints US1-S3, US2-S4 and US3-S5. 3 failed on the flow, were fixed and not rerun (US1-S2, US4-S1, US4-S2); 4 not run (US4-S5, US4-S6, US5-S1, US5-S2); 7 manual. Report: `e2e-report.md`.
- Offline simulation by removing the adb reverse route was confirmed to take the app offline.
- No backend bugs found.

## Deferred to the owner's phone

T037, T048, T057, T063, T069, T078 (full E2E run) and T076 (quickstart manual checks); regression of the 005 plans flows and the 001–003 Home/Account flows.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. Archiving is queued offline like an edit (PUT, then POST archive with the returned version). Archive is one-way.
2. Archived wallets keep their balance but are left out of per-currency totals.
3. Icons and colours come from a fixed set (six type icons, eight accents, "none" default); unknown server values are kept unchanged.
4. Credit cards and loans: the person enters the amount owed (≥ 0), stored negated. Other types may have a negative opening balance.
5. An unverified person gets the verify prompt and nothing is queued. A server refusal (verification or plan) shows "Not saved" with "Try again" and "Discard".
6. Whole-record PUT only; PATCH is never called (differs from the brief's "Consumes" list).
7. Ownership transfer is online-only, owner-only, offered when another member exists and nothing is waiting. Not reachable until C9.
8. Deletion is a queued local tombstone: hidden at once, removed when the server confirms. A never-sent wallet is removed at once. Deleted elsewhere → only "Accept the deletion".
9. Edits are based on the version shown when the editor opened, so a refresh during editing gives a clash, not a silent overwrite.
10. Refresh on sign-in, foreground (at most once a minute), opening the list and pull-to-refresh. Wallets missing from a complete read, with no waiting change, are removed.
11. The wallet list is its own screen, reached from a "Wallets" row on Home.
12. The `wallets.max` count includes owned live wallets (archived too), not ones pending deletion.
13. Room v2 → v3.
14. **Arabic "بنكي" for `checking`** ("حساب جاري" contains "حساب", a forbidden synonym under P-01). **Owner review.**

### Implementation

15. `Refusal.requestId` added in `core.sync`; old stored rows still decode.
16. Data imports `domain.model` only; deletion participants are resolved at call time through a callback and also run when a refresh drops a wallet (`WalletsLayeringTest`).
17. A forced refresh also calls `engine.syncNow()`.
18. Unknown wallet type shows the cash icon and "Other"; the raw value is sent back unchanged.
19. **Eight accent colours** `#4C6FD6`, `#2C7A5B`, `#8F5E12`, `#A9503A`, `#6B52B8`, `#1F7F8C`, `#B04375`, `#5B6472`. **Owner design review.**
20. Currency preselected from the profile, EGP fallback; amount input accepts Western or Arabic-Indic digits and `.` or `٫`.
21. Save disabled for a blank name; error shown once the name is touched.
22. Detail shows the "gone" message, then goes back.
23. English clash body reads "Choose which one to keep." (the wallet string-parity test rejects "version" in user copy).
24. Editor field-error tags can't attach to Material's error text; flows assert the words. Dialog buttons are tapped by text (tags inside dialog windows aren't visible to Maestro).
25. E2E accounts (`e2e+2026100212594*-`, `e2e+20261002130736-`, `e2e+20261002132914-`) were left in the dev DB; the backend has no purge use case.

## Remaining risks

- Full device E2E and manual checks are deferred (offline delete-before-sync, delete clash, members flows).
- Upgrade from the previous build (migration 2→3 on a device) and the 50-wallet performance check (SC-005) are proved only by host tests.
- Ownership transfer and editor/viewer behaviour can't be tested end to end until C9; the currency lock needs C6.
- Colour palette and `ChoiceGrid` need design review.
