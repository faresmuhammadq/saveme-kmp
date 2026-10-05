# Summary: Redeem Code (spec 015, C11)

**Date**: 2026-10-06 · **Status**: implemented, host QA and live-backend contract QA green, device E2E deferred to the owner, committed on branch `015-redeem-code` (24e1229) · **Backend contract**: `saveme-backend@b63e917` (`instapay.yaml` v1.12, spec 020 committed 7155d6d).

## What was built

- **"Enter code" on the Plan screen** (`plan.enterCode`, the place spec 005 reserved) opens `RedeemCodeKey` → an MVI redeem screen in `feature.billing`. For any signed-in person; the app never submits claims (web-only by policy, P-12) and never builds the claim URL.
- **`CodeField`** (design system): normalises typed and pasted text (upper-case, O→0, I/L→1, Crockford alphabet only, 16 characters, Arabic-Indic digits dropped, a code extracted from a pasted email line), draws four groups of four always left to right (also in Arabic) with an exact offset mapping, uses a password-variation keyboard without masking, offers paste only (copy and cut filtered out through `Modifier.filterTextContextMenuComponents`), announces characters separately for accessibility.
- **Code privacy**: `SecureWindow` (expect/actual; FLAG_SECURE on Android while the screen is composed, restoring the prior state; no-op on iOS) blocks screenshots, recordings and the recents preview. The code lives in memory only: never logged, stored, put on the clipboard or restored after the app closes (`BillingLayeringTest` proves nothing saves it).
- **`RedeemService`**: one request per call, never retried or queued; the request and the apply run in `scope.async`, so leaving the screen cannot lose a success. A success applies entitlements (`SessionManager.applyEntitlements`, same guards as `refreshEntitlements`), saves the governing subscription (`SubscriptionRepository.save`), launches the C3 `refreshAfterBillingAction()` and returns `Redeemed(confirmation)`; a different or signed-out person gets `SignedOut` and nothing is applied. The store owns the attempt's `Idempotency-Key` (`core.network.idempotencyKey` helper): one per code, reused on "Try again", dropped when the code is edited or on `IDEMPOTENCY_KEY_REUSED`.
- **Confirmation from `governing`** (`RedeemConfirmation`): the plan that now applies with its end date, grace end, "no end date", the redeemed plan's later start date for an early renewal, and a warning when Google Play still renews.
- **Gates and refusals**: an unverified person gets the existing verify prompt and nothing is sent. A message per refusal in English and Arabic: offline and in-progress offer Try again; rate limit disables Redeem until the wait passes. The mismatch alert shows only the person's own address.
- **Five new `ApiErrorCode` entries** with en/ar copy (`REDEEM_CODE_USED`, `REDEEM_CODE_EXPIRED`, `REDEEM_CODE_EMAIL_MISMATCH`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`, `IDEMPOTENCY_KEY_REUSED`); 27 strings per language; `BillingApi.redeem(code, key)` takes plain strings (P-10.6); four DTOs.
- **E2E assets** (written, not run): Maestro flows in `.maestro/015-redeem-code/`, `common/open-redeem.yaml`, api.js `mySubscription` and `redeemCode`, and the four `+redeem-code*` seeds in `run-e2e.sh`.
- No Room change and no new dependency.

## Results

- **Host QA** (`qa-report.md`): 19 acceptance scenarios, 31 FRs, 7 SCs and 20 edge cases map to a passing host test or a named code path. FR-029 (keyboard hints, system paste menu) and SC-001 timing are device measures. One gap closed: FR-011 "not persisted, no clipboard" now has a test.
- **Gates**: 3,009 host tests, 0 failures; Roborazzi verify, `assembleDevDebug` and the iOS simulator compile green with `--rerun-tasks`; `./gradlew cleanAllTests build allTests --continue` green (the iOS simulator test task is skipped on this Linux host).
- **Live contract QA** (20 cases, 18 real answers replayed by `RedeemLiveReplayTest` through the real ApiClient, BillingApi, RedeemService and stores): success 200 `{subscription, governing, entitlements}`; same key twice → 200 with `Idempotency-Replayed: true`; same account again with a new key → the same subscription id; lowercase, hyphenated, spaced, raw O/I/l all accepted by the server (it folds them itself); a second code redeemed while one is active starts when the first ends and `governing` points to the later-ending one; an operator-granted manual lifetime and a renewing `google_play` subscription govern over a redeemed monthly one; wrong account → 403 `REDEEM_CODE_EMAIL_MISMATCH` (no address named; outranks unverified); unverified with its own code → 403 `EMAIL_NOT_VERIFIED`; already redeemed by another account → 409 `REDEEM_CODE_USED` (outranks the mismatch); expired → 410; revoked and unknown → 404; malformed → 422 at `/code` (`INVALID_TYPE`, or `REQUIRED`); the 429 came on the 11th refused attempt in the hour (`Retry-After` and `retry_after_seconds` agree) and successes and replays never hit it; missing key → 400 `IDEMPOTENCY_KEY_REQUIRED`; same key with a different body → 409 `IDEMPOTENCY_KEY_REUSED`. `IDEMPOTENCY_REQUEST_IN_PROGRESS` could not be provoked (covered by contract and store tests). Fixtures: `CapturedRedeemResponses.kt` (codes and tokens redacted).
- **Open checklist items settled**: the approve command is `dev billing:claim:approve --operator=<active operator uuid> <claim id>`; `ClaimService::approve("00000000-0000-7000-8000-0000000000e2", <claim id>)` also works (the service does not look the operator up), so `run-e2e.sh`'s fixed operator id is fine. Mailpit is v1.30.0 at `127.0.0.1:8025` (also `mailpit.test`); `GET /api/v1/search?query=to:<address> subject:"code is ready"` then `GET /api/v1/message/<ID>`; the code is in `.Text` and matches `[0-9A-Z]{4}(-[0-9A-Z]{4}){3}`; the mail arrives about 4 s after approval with the queue worker running; `run-e2e.sh`'s `read_mailed_code` returned the code verbatim.
- **Flaky test fixed (test only)**: `BillingSyncCoordinatorTest.aFeatureNotEntitledRefusalTriggersANeverSkippedRefresh` raced with the sign-in refresh (by design `refresh(NotEntitled)` joins an in-flight refresh, so the `/me/entitlements` read never happened and the 10 s wait timed out). The test now settles the sign-in refresh first; 16/16 isolated reruns under load.
- No client defect and no backend bug turned up; no production code changed during QA.

## Deferred to the owner's device

T029, T035, T044 (flow runs, quickstart, full E2E report) and T051 (device spot checks): FLAG_SECURE in screenshots and recents, the paste-only system menu, the keyboard not learning, focus and keyboard-open on arrival, Arabic at 200 %, SC-001 timing. The device E2E reads the code from Mailpit, so the runner must reach it.

## Assumed defaults

Owner away with delegated authority; each the conservative option.

### Design

1. "Enter code" is on the Plan screen only (not Account or Home).
2. An unverified person can open the screen and type, but "Redeem" opens the verify prompt instead of sending (saves their 10-an-hour budget).
3. The whole redeem screen is blocked from screenshots, recordings and recents (this also blocks the person's own screenshot for support).
4. After a rate limit, "Redeem" stays unavailable until the server's wait time passes; nothing is retried automatically. Under 60 s of wait reads "a minute"; no wait time reads "later" and Redeem stays available.
5. Success shows a confirmation on the redeem screen; "Done" returns to the Plan screen.
6. The answer is applied directly and then re-read through the existing refresh; a failed re-read never undoes it.
7. Google Play is named only in the renewal warning; the mismatch message has no "Sign out" button.
8. Arabic-Indic digits are dropped from the code field rather than converted.
9. Online-only, never queued; one `Idempotency-Key` per attempt.

### Implementation

10. No new typography token: `CodeField` uses `numeralL` (every `AppTypography` style already has tabular digits) with left-to-right text direction.
11. The paste-only menu uses `Modifier.filterTextContextMenuComponents` (the research R-03 fallback) as the only mechanism; its predicate is unit-tested, whether the Android system menu honours it is a device check.
12. Typed and pasted text both go through `RedeemCode.fromPaste` (hyphen-grouped codes first, then space-grouped, so ordinary words in an email line are never read as a code), so `CodeField` has no billing import.
13. `EMAIL_NOT_VERIFIED` shows the verify note, not a second alert; the mismatch alert carries the signed-in address on a second line under one `redeem.message` tag.
14. `AuthErrorCopy.message()` is exhaustive over `ApiErrorCode`, so the five new codes were added there too.
15. An unreadable 200 body becomes `Failed(null)` and shows the generic error.
16. A billing layering test also checks that no source builds the claim URL.
17. Arabic minutes use the sentence form (no plural resource).

### Owner review

18. The Arabic copy for the redeem screen and refusals.

## Remaining risks

- Device-only checks above are unverified (T051).
- `IDEMPOTENCY_REQUEST_IN_PROGRESS` was never seen live; refusals are not replayed, so a retry after a refusal counts again against the 10-per-hour budget; the "only refusals count" rule is inferred from one account.
- The backend's `MySubscription` has no id, so "governing is the redeemed one" is inferred from provider, plan and period end (two InstaPay subscriptions with identical plan and end would be indistinguishable).
- "Used" outranks "wrong address": a person on the wrong account sending an already-used code sees "already used" (no address leaks).
- Expired codes can only point to support, and the app has no support contact yet.
- The approval mail is the only place the code exists in plain form, and the dev claim budget (5 per hour per origin) can block repeated seeding over HTTP.
- `PlanScreen` goldens (en/ar, plus fs2 for `stale_unavailable`) and `fields_*` changed on purpose for the new row and the `CodeField` catalogue entry.
- QA accounts `qa015-<a..e>-*@example.com` remain in the dev database (one unredeemed code from an earlier run was left alone).
