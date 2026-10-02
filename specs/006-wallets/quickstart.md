# Quickstart: Wallets & Membership (client)

## Prerequisites

- Spec 005 (C3) merged into `main` and this branch rebased on it (`Entitlements`, `FEATURE_NOT_ENTITLED`).
- saveme-backend dev container running with spec 010 (`310aa24` or later) migrated; `devApiHost` set (README).
- One emulator in `adb devices`, English locale; Maestro installed for E2E.

## Automated checks

```bash
./gradlew :shared:testAndroidHostTest          # unit, contract, Room DAO + migration 2→3, screenshots verify
./gradlew :shared:verifyRoborazziAndroidHostTest
./gradlew :shared:compileKotlinIosSimulatorArm64   # iOS still compiles (P-10)
.specify/scripts/bash/run-e2e.sh 006-wallets       # or /speckit-e2e 006-wallets
```

Expected: all green; `shared/schemas/…AppDatabase/3.json` exists and is committed with the change.

## Manual checks (dev build)

1. **Create offline** — Settings → airplane mode (or `adb reverse --remove tcp:<port>`). Home →
   Wallets → New wallet → Cash, "Pocket", EGP preselected, 250.00 → Save. The card shows 250.00 EGP
   and "Waiting to sync". Restore the network: within a minute the mark clears; `GET /wallets` on the
   backend lists it with `opening_balance_minor: 25000`.
2. **Totals** — add a USD wallet and a credit card (amount owed 1,000.00 EGP). The list shows an EGP
   total of −750.00 and a separate USD total; the card shows "1,000.00 owed" in neutral ink.
3. **Clash** — open "Pocket"; from another session `PUT /wallets/{id}` a new name with the current
   version; on the phone rename and save: "Changed on another device." with both names; "Keep mine"
   wins on the server.
4. **Deleted elsewhere** — delete a wallet from another session, edit it on the phone: only
   "Accept the deletion" is offered; accepting removes it.
5. **Archive and delete** — archive a wallet: it moves under "Archived", leaves the totals, and the
   confirmation said it can't be undone. Delete another: confirmation names it; it disappears; the
   backend returns it as a tombstone.
6. **Roles** — open an owned wallet: the members section shows "You · Owner" and no "Transfer
   ownership". Editor/viewer gating is proved by the detail reducer tests until C9 can add members.
7. **Arabic** — switch the app to Arabic: "حصالة" for savings, RTL layout, Western digits.
8. **Sign-out** — with a waiting wallet change, sign out: the spec 004 warning counts it; after
   signing out and in as another account, no wallet of the first account is shown.
9. **Upgrade** — install the `main` build, sign in, edit the profile offline, install this build:
   the app opens (migration 2→3), the profile edit is still waiting and syncs.
10. **Scale** — seed 50 wallets through the API (slot B script or `curl` loop), refresh, close and
    reopen the list offline: it shows all 50 with totals within 1 second (SC-005).
