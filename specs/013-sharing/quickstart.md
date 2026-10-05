# Quickstart: Sharing & Invitations (client)

## Prerequisites

- JDK and Android SDK as in the README; no device needed for the automated checks.
- For live QA: `../saveme-backend` dev container running (`dev project open`), API reachable on the port in `devApiHost`, the queue worker and Mailpit running (email invitations), three throwaway users registered through `POST /auth/register` and verified as in specs 009–012; the owner given Plus through the operator grant used by spec 005's E2E runner (`plus_monthly`).
- For a later device run only: set `SHARING_INVITATION_URL=http://<devApiHost>/invite` in the backend `.env` and restart the backend (the container's PHP workers and queue) so mailed and shared links open the dev app (research R-08). The backend default is `http://saveme-backend.test/invite` (seen in the live QA: the mail and `meta.share_url` carry that host), which the dev app's `/invite` filter (`http://<linkHost>:<port>`) does not match, so a tapped mail or shared link would open the browser instead. `devApiHost` is the phone-reachable host:port of the dev flavor (`localhost:43871` through `adb reverse`, as the other flows use).
- The sharing flows' seed is `# e2e-seed: verified-user+plus+partner`: `.specify/scripts/bash/run-e2e.sh` creates the owner (verified, Plus) and a second verified Plus account, passed to Maestro as `E2E_PARTNER_EMAIL` / `E2E_PARTNER_PASSWORD`. Not run (owner policy).

## Automated checks (host only)

```bash
./gradlew :shared:testAndroidHostTest --tests '*Sharing*' --tests '*Invitation*' --tests '*Invite*' --tests '*Members*' --tests '*Activity*'
./gradlew :shared:testAndroidHostTest --tests '*Migration9To10Test' --tests '*AppGraphTest' --tests '*LayeringTest' --tests '*SignOutWipeTest'
./gradlew :shared:verifyRoborazziAndroidHostTest
./gradlew :shared:testAndroidHostTest --rerun-tasks
./gradlew :androidApp:assembleDevDebug :androidApp:processProdReleaseMainManifest :shared:compileKotlinIosSimulatorArm64
```

Check the merged prod release manifest has the `https` `/invite` path and no `http` filter (Quality gate 5).

## Live contract QA (curl, owner policy)

1. As owner (Plus): create a wallet; `PUT …/invitations/{uuidv7}` email (201, `meta: {}`) and link (201, `meta.share_url`); replay both (200, no link); different role on the same id (422 `IMMUTABLE_FIELD`); Free owner (403 `FEATURE_NOT_ENTITLED`, `feature: sharing.enabled`); quota full (`feature: sharing.max_members_per_wallet`); unverified (403 `EMAIL_NOT_VERIFIED`); editor caller (403 `FORBIDDEN`).
2. Wallet feed shows `invitation` rows; `DELETE` withdraw (200 tombstone; again 200; stale 409); feed tombstone `status: revoked`.
3. As invitee: `preview` (200; already member; 404 for used/withdrawn/other address; 422 malformed), `accept` (200 member; repeat 200; owner plan full 403), `decline` (204; repeat 204); user feed shows the own `wallet_member` row; the wallet feed reads from the start.
4. As owner: `PATCH` role (200; raise on Free 403; owner 422 `IMMUTABLE_FIELD`; stale 409), `DELETE` member (200; repeat 200); removed member's user feed has the tombstone, their wallet feed 404.
5. As member: `leave` (200; repeat 404; owner 403).
6. `GET /wallets/{id}/activity` pages at limit 1 and 50; every action present; `INVALID_CURSOR`.
7. Rate limits: 21st create in 24 h → 429 with Retry-After; 31st token call in an hour → 429 (use a dedicated account).
8. Capture answers into `CapturedSharingResponses.kt` (request ids kept, tokens and links replaced by fixed fake values of the same shape) and run `SharingCapturedResponsesTest`.

## Deferred: E2E on owner's device

`.maestro/013-sharing/` flows (ui-and-e2e.md) and the brief's checkpoint: invite, accept as the
second account, both see a new transaction; remove → the member's copy is purged on next sync.
