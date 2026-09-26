# Quickstart: validate Design System & Auth

Validation guide proving the feature end to end. Shapes and rules are in [contracts/](contracts/) and [data-model.md](data-model.md); this file only says how to run and what to expect.

## Prerequisites

- JDK 17+, Android SDK (compileSdk 37), an Android device or emulator on the **same LAN** as the dev machine.
- Backend running from `../saveme-backend` in its container, port published on the host (e.g. `8080`). Its dev environment must accept plain HTTP and send mail to a local catcher (e.g. Mailpit) whose inbox you can open.
- Dev API host set once, outside git — in `~/.gradle/gradle.properties` or `local.properties`:

  ```properties
  devApiHost=192.168.1.20:8080
  ```

  Find the IP with `ip -4 addr show` (Linux). Check the phone can reach it: open `http://192.168.1.20:8080/api/v1/` in the phone's browser and expect a JSON problem body, not a timeout.

## Build & automated checks

```bash
./gradlew :shared:allTests                 # unit, contract (MockEngine), Compose UI tests
./gradlew :shared:verifyRoborazziAndroidHostTest   # screenshot tests: en / ar / fontScale 2.0
./gradlew :androidApp:installDevDebug      # dev flavor on the connected device
```

Expected: all green; screenshot diffs empty (record baselines with `recordRoborazzi…` on first run and review them against the design canvas).

## Manual scenarios

| # | Scenario | Steps | Expected |
|---|---|---|---|
| 1 | Component catalogue (US1) | Dev tools → Components. Toggle en/ar and font scale 2.0 | Matches *Mobile components* board; RTL mirrors; nothing clipped |
| 2 | Register (US3) | Create account with a fresh address | "Check your inbox" naming the address; mail arrives in catcher |
| 3 | Register existing (US3-1) | Repeat with the same address | Identical screen; catcher shows "you already have an account" mail |
| 4 | Verify via paste (US3-4, FR-027b) | Copy link from mail → Dev tools → Paste link | Verified screen |
| 5 | Verify via tap (dev) | Open the mail on the phone, tap link, choose Save Me in chooser | Same Verified screen |
| 6 | Sign in (US2-1) | Sign in with the new account | Signed-in home; no verify prompt if verified |
| 7 | Relaunch (US2-4, SC-006) | Force-stop, reopen | Signed-in home in < 2 s, no auth screen flash |
| 8 | Wrong password ×5 (US2-2/3) | Wrong password five times | Same "incorrect" message ×4, then Locked with minutes |
| 9 | Forgot/reset (US4) | Forgot password → link from mail → new password | Sign-in screen with email prefilled; new password works; lock cleared |
| 10 | Remote revoke (US2-5) | While signed in, reset password from another device/curl, then act in app | Returned to sign-in with "session ended" explanation |
| 11 | Sign out offline (US5-2) | Airplane mode → Sign out | Sign-in screen; relaunch stays signed out |
| 12 | Old app version (edge) | Build with `versionName` below server minimum | Blocking update screen naming the minimum version |
| 13 | Arabic (FR-007) | Set device language to Arabic, repeat 2 and 6 | All copy Arabic, layout mirrored, error copy from app not server |

## Release-build checks

- `./gradlew :androidApp:assembleProdRelease` then inspect merged manifest: no `http` intent filter, no cleartext permission, no Paste link / Components entry points.
