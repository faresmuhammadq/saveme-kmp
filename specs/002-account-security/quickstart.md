# Quickstart: Account Security & Profile

Validation guide. Details: [contracts/account-api-client.md](contracts/account-api-client.md), [contracts/ui-and-components.md](contracts/ui-and-components.md), [data-model.md](data-model.md).

## Prerequisites

- Backend `saveme-backend` running locally with spec 008 (`/me`, `/me/profile`) present; mail catcher (mailpit) up.
- `local.properties`: `devApiHost=localhost:43871`; phone on USB: `adb reverse tcp:43871 tcp:43871`.
- A verified test account; a second device or emulator (emulator: `devApiHost=10.0.2.2:43871` build) for multi-device checks.

## Automated

```bash
./gradlew allTests                              # unit, MVI, contract (MockEngine), Room DAO tests
./gradlew verifyRoborazziAndroidHostTest        # new components + screens: en / ar RTL / font scale 2
./gradlew :androidApp:assembleDevDebug
```

Expected: all green; no screenshot diffs after recording once with `recordRoborazziAndroidHostTest`.

## Manual scenarios (dev build, phone A + device B signed into the same account)

1. **Devices (US1)**: A → Account → Devices signed in: both listed, A marked "This device" with no end action. End B, confirm → B gone; any action on B returns it to sign-in with the session-ended message. Sign B in again, then "Sign out all other devices" on A → only A remains.
2. **Offline list**: A in airplane mode → open Devices signed in → last stored list shows with "Updated …" time; ending is refused with the offline message.
3. **Change password (US2)**: wrong current password → field error, **A stays signed in**. Correct → confirmation; B returns to sign-in; old password fails on sign-in, new works.
4. **Profile (US3)**: edit display name + budget day → saved; reopen on B → same values. Set language Arabic → A switches to Arabic RTL instantly and after restart; server messages arrive in Arabic.
5. **Offline edit**: A offline, change display name, save → "Waiting to sync"; back online → mark clears; B shows new name after refresh.
6. **Conflict**: A offline, edit name to "A"; B edits name to "B" and saves; A online → conflict with both versions. "Keep mine" → server has "A"; repeat and choose "Use the other device's" → A shows "B".
7. **Currency lock**: account with a transaction (or backend fixture) → currency read-only with explanation.
8. **Deletion (US4)**: Delete account → explanation → wrong password → error, still signed in; correct → date shown; Home shows the notice on every launch; "Cancel deletion" → notice gone. Request again, sign in on B → deletion cancelled message.
9. **Sign-out wipe**: sign out on A → sign in as another account → no previous profile/sessions visible; app language returns to device language while signed out.
