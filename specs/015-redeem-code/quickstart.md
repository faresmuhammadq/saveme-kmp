# Quickstart: Redeem Code (client)

Validation guide. Contracts: [redeem-api-client.md](contracts/redeem-api-client.md),
[redeem-domain.md](contracts/redeem-domain.md), [ui-and-e2e.md](contracts/ui-and-e2e.md).
Owner policy: no emulator, Maestro or device run — §1 and §2 are the gate; §3 is for the owner's
device later.

## 1. Host tests

```bash
./gradlew :shared:testAndroidHostTest --tests '*Redeem*' --tests '*ApiErrorCode*' --tests '*Plan*' --tests '*SessionManager*' --tests '*CodeField*' --tests '*SecureWindow*'
./gradlew :shared:verifyRoborazziAndroidHostTest
./gradlew :androidApp:assembleDevDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Expect: normaliser table, store, reducer, service, contract, live-replay, string-scan, log-capture,
layering and screenshot tests green.

## 2. Live-backend contract QA (curl, local backend)

Prerequisites (backend repo): `dev project open`; billing seeded (plans with InstaPay prices);
`BILLING_KEYS`/`BILLING_ACTIVE_KEY` set; a queue worker running; mail in Mailpit
(`http://mailpit.test`). From this repo:

1. Register two throwaway verified accounts A (`qa015-a-<ts>@example.com`) and B, and one unverified C, with the `run-e2e.sh` tinker helpers; sign A, B, C in over HTTP (`POST /auth/sign-in`, `X-Client-Platform: android`) for bearer credentials.
2. For A: submit and approve a claim through `ClaimService` (research R-12); read the code from Mailpit.
3. `POST /billing/redeem` as A with `Idempotency-Key: qa015-<uuid>` and the code typed lower-case without hyphens → 200, `governing.provider = instapay`; same key again → 200 + `Idempotency-Replayed: true`; new key → 200, same `subscription.id`.
4. A second code for A redeemed as B → 403 `REDEEM_CODE_EMAIL_MISMATCH` (body never names A's address); as C after re-approving a claim for C's address → 403 `EMAIL_NOT_VERIFIED`.
5. `AAAA-BBBB-CCCC-DDDD` → 404; revoked (`dev billing:claim:revoke-code <claim> --operator=<id>`) → 404; expired (tinker `expires_at` past) → 410; used (tinker row edit, R-12) → 409 `REDEEM_CODE_USED`; `7KQ2-MX4D` → 422 `/code`; a code with `O`/`I`/`L` in place of `0`/`1` → accepted.
6. Grant A Plus with no end (`OperatorSubscriptions::grant`, as `grant_plus 0`), redeem a new code → 200 with `governing` ≠ redeemed (`current_period_end` null).
7. Eleven bad attempts as B within an hour → 429 with `Retry-After`.
8. Save each answer (status, headers that matter, body) into `CapturedRedeemResponses.kt`; run `RedeemLiveReplayTest`. Record results in `qa-report.md`.

## 3. Device E2E (deferred: E2E on owner's device)

```bash
.specify/scripts/bash/run-e2e.sh 015-redeem-code
```

Flows and seeds: [ui-and-e2e.md](contracts/ui-and-e2e.md#e2e-deferred-e2e-on-owners-device).
Spot checks by hand: Arabic at 200 % font (code stays LTR in four groups); screenshot attempt on the
redeem screen is blocked and the recent-apps preview is blank; long-press on the field offers
Paste but not Copy; the keyboard shows no suggestions.
