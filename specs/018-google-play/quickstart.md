# Quickstart: Google Play Billing (client) — spec 018

## 1. Host checks (no device)

```bash
./gradlew :shared:testAndroidHostTest --tests '*billing*'      # pipeline, restore, prices, Plan screen, contract
./gradlew :shared:verifyRoborazziAndroidHostTest               # Plan screen goldens (EN, AR, 200 %)
./gradlew :shared:testAndroidHostTest --tests '*AppGraphTest*' # real Koin graph resolves the verifier and the port
./gradlew :androidApp:assembleDevDebug :shared:compileKotlinIosSimulatorArm64
```

## 2. Local end to end without Google (dev flavour)

1. Backend (`../saveme-backend`, spec 024 built): `.env` → `BILLING_PLAY_DRIVER=fake`,
   `BILLING_PLAY_PRICE_PLUS_MONTHLY=14900` (so the seeder maps `plus_monthly`),
   `BILLING_PLAY_FAKE_SCRIPT=<json>` scripting token `t-1` → subscription `plus_monthly`, `ACTIVE`,
   expiry +30 days, obfuscated reference = the test account's (read it with
   `GET /api/v1/billing/google/account-reference`). See backend `specs/024-billing-google-play/quickstart.md` §3.
2. App: dev build, Dev tools → "Google Play (scripted)" on → add purchase `t-1`, `plus_monthly`,
   purchased, not acknowledged.
3. Return to the app (foreground) → Plan shows Plus; the backend log shows `billing_play_verified`.
4. Script `t-2` as `PENDING` in both → Plan shows the pending note; flip both to purchased → Plus.
5. Sign out, sign in as another account → the "another account" note (backend answers `409`).
6. Tap "Restore purchases" → result message.

## 3. Live contract QA (after backend spec 024 is committed)

curl each endpoint against the local backend with throwaway accounts and the fake gateway
scripted per token; capture answers (tokens and references redacted) into
`CapturedGooglePlayResponses.kt`; `GooglePlayLiveReplayTest` replays them through the real
`ApiClient` → `BillingApi` → services. Cases: 200 active, 200 `acknowledged:false`, same key replay
(`Idempotency-Replayed`), same token new key, 202 pending, 409 linked elsewhere (by reference and by
first link), 422 not verified, 422 unknown product, 400 missing key, 409 key reused, 429 on the 21st
verify / 11th restore, 503 (`failNext`), restore with mixed outcomes, empty restore, account
reference twice (same value).

## 4. Owner checkpoint (after G1, with a license tester)

Buy monthly (needs G2 for the in-app button; otherwise subscribe from a test build with the switch
on, or resubscribe from Google Play) → Plus; uninstall, reinstall, sign in → Plus comes back (or tap
Restore); a second account on the same phone sees the "another account" note; grace with a declined
test card → warning and Fix payment opens Google Play's subscription centre.
