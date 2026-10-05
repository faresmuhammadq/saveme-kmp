# Research: Redeem Code (client)

Phase 0 for [plan.md](plan.md). Each decision resolves a choice the spec left to planning; all
follow the existing code at `bee8e65` and the backend contract at `saveme-backend@b63e917`.

## R-01 Where the feature lives

- **Decision**: Inside the existing `feature.billing` context (spec 005): `data` gains `BillingApi.redeem` and the redeem DTOs; `domain` gains `RedeemCode` (normaliser), `RedeemService` and `RedeemOutcome`/`RedeemConfirmation`; `ui/redeem/` holds `RedeemContract.kt`, `RedeemViewModel`, `RedeemRoute`, `RedeemScreen`. Navigation gains `RedeemCodeKey`; `PlanScreen`'s reserved `Column {}` gains the "Enter code" button and `PlanEffect.OpenRedeem`.
- **Rationale**: Redeem is a Plans & Billing operation (backend tag), it applies to C3's records and must sit next to them; no new bounded context (P-01).
- **Alternatives**: a new `feature.redeem` package (rejected: it would have to reach C3's `SubscriptionRepository`, a data package of another feature, P-10.4).

## R-02 Code normalisation and display

- **Decision**: Pure `object RedeemCode` in billing domain:
  - `ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"` (Crockford base32: no I, L, O, U).
  - `normalise(input: String): String` — for each char: upper-case (ASCII only; non-ASCII letters dropped); `O`→`0`, `I`/`L`→`1`; keep if in `ALPHABET`; drop everything else (spaces, hyphens, line breaks, `U`, punctuation, Arabic-Indic digits); cut to 16.
  - `fromPaste(text: String): String` — first match of `(?i)\b([0-9A-Z]{4})[- ]([0-9A-Z]{4})[- ]([0-9A-Z]{4})[- ]([0-9A-Z]{4})\b` whose `normalise` is 16 characters wins; else `normalise(text)`.
  - `grouped(raw16: String): String` → `XXXX-XXXX-XXXX-XXXX` (the request body, FR-008), and `isComplete(raw) = raw.length == 16`.
- **Field wiring**: the store keeps the raw normalised characters (≤ 16). The field shows them through a `VisualTransformation` that inserts `-` after every 4th character with an exact `OffsetMapping` (cursor stays right in both directions). `onValueChange` receives the field's raw text: when the new text is longer than the old by more than one character it is treated as a paste (`fromPaste`), otherwise `normalise`. Arabic-Indic digits are dropped, not converted (a mailed code is Latin; converting would hide a typing mistake — accepted default).
- **Rationale**: matches the server's folding exactly (instapay.yaml "Format"), so a code the field accepts is never a 422; the visual hyphen is never in state, so deletion behaves.
- **Alternatives**: send the raw typed text and let the server fold (rejected: the owner asked to normalise as typed; and the `O`→`0` fold is untested server-side, backend summary).

## R-03 The code field component

- **Decision**: New design-system component `CodeField(value, onValueChange, label, groupSize = 4, groups = 4, supportingText, errorText, enabled, modifier)` in `designsystem/component/Fields.kt`, built on `AppTextField`'s visuals:
  - `KeyboardOptions(keyboardType = KeyboardType.Password, capitalization = Characters, autoCorrectEnabled = false, imeAction = Done)` with **no** password masking: Android IMEs treat a password-variation field as "do not learn, do not suggest" (FR-029), and offer an ASCII layout.
  - Text direction forced LTR (`CompositionLocalProvider(LocalLayoutDirection provides Ltr)` around the text only; the label and messages follow the app direction) and a tabular/monospace token style from `AppTheme.type` (a new `code` text style token if none exists — added with the component, P-07.3).
  - A paste-only context menu: a `LocalTextToolbar` wrapper that forwards `showMenu` with `onCopyRequested = null` and `onCutRequested = null` (and keeps paste and select-all). If Compose MP 1.12's text context menu ignores `LocalTextToolbar` for this field, use `Modifier.filterTextContextMenuComponents` to drop copy and cut (verify at implementation; both are in the existing Compose dependency).
  - Semantics: `contentDescription` = label; the value announced character by character (`semantics { text = AnnotatedString(spaced) }` with the characters separated) so TalkBack does not read a code as a word.
  - Catalogue entry and screenshot tests (EN LTR, AR RTL, 2.0) with an empty, partial, complete and error state.
- **Rationale**: P-07.3 (a visual need not met goes to `designsystem` first); FR-029 without a new dependency.
- **Alternatives**: `PasswordField` masked (rejected: the person must see the characters to compare with the mail); a plain `AppTextField` (rejected: copy/cut and keyboard learning).

## R-04 Screenshot and recents protection

- **Decision**: `core.platform` gains `@Composable expect fun SecureWindow()` — Android `actual` adds `WindowManager.LayoutParams.FLAG_SECURE` to the hosting activity's window in a `DisposableEffect` and clears it on dispose **only if it was not already set** (activity found by unwrapping `LocalContext` to an `Activity`); iOS `actual` is a no-op (P-15). Called once at the top of `RedeemRoute`.
- **Rationale**: FR-029; P-10.2 (platform API behind `expect`/`actual`); scoped to the screen, so the rest of the app keeps screenshots.
- **Alternatives**: FLAG_SECURE for the whole app (rejected: blocks support screenshots everywhere); none (rejected: owner listed screenshots).
- **Testing**: the Android actual is covered by a Robolectric host test asserting the flag is set while composed and cleared after (androidHostTest); Roborazzi still captures the composable (it renders the node tree, not the window).

## R-05 The attempt identifier (Idempotency-Key)

- **Decision**: `core.network` gains `fun HttpRequestBuilder.idempotencyKey(key: String)` (sets the `Idempotency-Key` header). The **store** owns the key: `attempt: AttemptKey?` in `RedeemState` (value class around a UUIDv7 string, `toString()` = `AttemptKey(***)`), set to `Uuid.generateV7()` on the first `Redeem` for the current code, kept on every later `Redeem`/`TryAgain` while the code is unchanged, cleared by `CodeChanged` (any change to the raw value) and by a `KeyReused` outcome. The key is passed into `RedeemService.redeem(code, attempt)`, which hands `attempt.value` to `BillingApi.redeem(code, idempotencyKey)` (data takes plain strings and never imports domain, P-10.6). UUIDv7 text (36 characters of hex and `-`) satisfies the header's `[A-Za-z0-9._:-]{16,255}`.
- **Rationale**: FR-010 ("one key per tap/attempt, reused on retry of the same tap"); the reducer stays pure — key generation happens in the store's intent handling, then enters through a `Change` (P-16.4).
- **Alternatives**: a key per HTTP send (rejected: a timeout retry would run twice — SC-007); a key derived from the code, e.g. its hash (rejected: puts a code-derived value in a header, an avoidable leak path, and would replay a stored success to anyone holding the code on the same account only — no benefit over a random key).

## R-06 Applying the answer (FR-012)

- **Decision**: 
  1. `SessionManager` (auth domain) gains `suspend fun applyEntitlements(forUserId: String, planId: String, planName: String, features: Map<String, JsonPrimitive>): Boolean` — same guard and write path as `refreshEntitlements()` (snapshot user = signed-in user = `forUserId`, else `false` and nothing written). It builds its own `EntitlementSummaryDto` internally, so billing never imports auth `data` (P-10.4).
  2. `RedeemService` (billing domain): captures the signed-in user id before the call; on `Success` checks the same person is still signed in, calls `applyEntitlements(...)`, then `SubscriptionRepository.save(governing)` (billing's own data, the record the Plan screen reads), then launches `entitlements.refreshAfterBillingAction()` on its scope without awaiting (its outcome only drives the Plan screen's existing stale note). If the person changed, the answer is dropped and the outcome is `SignedOut`. The request and the apply step run as one `scope.async { … }` awaited by the caller, so a store cancelled by leaving the screen does not cancel applying a success (FR-012); the store simply never receives the outcome.
  3. `RedeemResultDto.entitlements` is a billing DTO (`RedeemEntitlementsDto(plan: PlanRefDto, features: Map<String, JsonPrimitive>)`), mirroring `EntitlementSummary`; `governing` reuses `MySubscriptionDto`.
- **Rationale**: SC-002 (correct at once, even if the re-read fails); the owner asked to refresh through the existing hooks — the billing-action refresh is still called; spec 005 FR-008 (answers for another person discarded).
- **Alternatives**: refresh only (rejected: a failed re-read would leave Free showing after a successful redemption, and the Plan screen would hide the status because subscription and entitlements disagree, spec 005 FR-012); apply only (rejected: the owner asked for the hook; a re-read also picks up anything else that changed).

## R-07 Confirmation mapping (FR-013–FR-015)

- **Decision**: Pure `RedeemConfirmation.from(result: RedeemResultDto, now: Instant): RedeemConfirmation`:
  - `governingPlan = governing.plan.name`, `governingEnd = governing.current_period_end` (null → "No end date"); when `governing.status == grace` the date shown is `grace_until` instead (FR-013).
  - `governingIsRedeemed = governing.provider == "instapay" && governing.plan.id == subscription.plan.id && governing.current_period_end == subscription.current_period_end` (instants compared after parsing; both null counts as equal). `MySubscription` has no id (backend gap 1).
  - `playRenews = !governingIsRedeemed && governing.provider == "google_play" && governing.auto_renew == true`.
  - `redeemedPlan`, `redeemedEnd` from `subscription`; `startsLater = subscription.current_period_start > now` → show its date (FR-015).
  - Unknown provider → treated as "another subscription" without the Play note.
- **Rationale**: the two contract examples (`redeemedGoverns`, `playStillGoverns`) are the test table; dates show in the profile time zone (P-05), as the Plan screen does.

## R-08 Refusal mapping (FR-017–FR-026)

- **Decision**: `RedeemService` maps `ApiResult` to `RedeemOutcome`:

| Answer | Outcome | Message key |
|---|---|---|
| 200 | `Redeemed(confirmation)` / `SignedOut` | — |
| Offline, Timeout | `Offline` | `redeem_offline` + Try again |
| 404 `NOT_FOUND` | `NotValid` | `redeem_not_valid` |
| 409 `REDEEM_CODE_USED` | `Used` | `error_redeem_code_used` |
| 410 `REDEEM_CODE_EXPIRED` | `Expired` | `error_redeem_code_expired` |
| 403 `REDEEM_CODE_EMAIL_MISMATCH` | `OtherAddress` | `error_redeem_code_email_mismatch` + `redeem_signed_in_as` |
| 403 `EMAIL_NOT_VERIFIED` | `NotVerified` | screen note; app-wide sheet already raised by `ApiClient` |
| 429 `RATE_LIMITED` | `RateLimited(retryAfterSeconds?)` | `redeem_rate_limited_minutes` / `_minute` / `_later` |
| 422 `VALIDATION_FAILED` (any pointer, `/code` expected) | `InvalidFormat` | `redeem_invalid_format` |
| 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` | `InProgress` | `redeem_in_progress` + Try again |
| 409 `IDEMPOTENCY_KEY_REUSED` | `KeyReused(requestId)` | general failure + request id; store drops the key |
| 401 / 426 | `Failed(null)` (the app-wide events act) | none extra |
| anything else | `Failed(requestId)` | `error_generic_reference` (request id) or `error_generic` |

- The 422 is mapped regardless of pointer (only `/code` exists); the store also shows it if the server's folding ever differs from R-02.
- **Wait time**: `RateLimited(s)` → the store records `waitUntil = now + s` (clock injected into the store's intent handling, not the reducer) and schedules a `WaitOver` change after `s` seconds; minutes shown = `ceil(s / 60)`.
- **Rationale**: P-02.4, P-06.5, P-13.2; one message for unknown and revoked (FR-017).

## R-09 Verified gate (FR-003)

- **Decision**: `RedeemViewModel` takes `isEmailVerified: () -> Boolean` and `signedInEmail: () -> String?` lambdas from DI (as `BudgetsViewModel` does). On `Redeem`: not verified → `RedeemEffect.RequireVerification` (the nav host sets `emailNotVerifiedVisible = true`, the existing sheet), nothing sent. The state's `verified` flag drives the note and is re-read on `Opened` and on each `Redeem`.
- **Rationale**: same pattern as budgets/wallets/sharing; no new verify UI.

## R-10 Lifetime of the code in memory

- **Decision**: `RedeemState` holds `code` (raw ≤ 16) and `attempt`; its `toString()` is overridden to redact both (a `data class` with a custom `toString`). `RedeemIntent.CodeChanged(text)` also redacts. No `SavedStateHandle`, no `rememberSaveable` for the field (state lives in the store only); `RedeemCodeKey` is a `data object` (nothing in the back stack). The store subscribes to `SessionManager.state`; on `SignedOut` it clears the code and attempt, and the nav host's existing sign-out handling removes the screen. `ApiClient` logs method and URL only (no headers, no body) — the request carries the code in the body and the key in a header, so neither reaches Kermit.
- **Test**: a log-capture test over the full redemption (Kermit test writer) asserts the code and key never appear; `toString` tests on state, intents, `AttemptKey`, `RedeemRequestDto`.

## R-11 Plan screen entry

- **Decision**: `PlanScreen` renders a secondary button "Enter code" (test tag `plan.enterCode`) in the reserved `Column`, always visible while signed in; `PlanIntent.EnterCode` → `PlanEffect.OpenRedeem` → nav host pushes `RedeemCodeKey`. On return (`Done`), the Plan screen's existing observation of the stored subscription and entitlements already shows the applied plan; no extra refresh call from the Plan screen.
- **Rationale**: FR-001, spec 005 assumed default 2 ("the screen reserves the place").

## R-12 E2E seeding (deferred device run)

- **Decision**: `run-e2e.sh` gains a seed suffix family, composable with existing seeds:
  - `+redeem-code` — submit a claim for the flow's own address through `ClaimService::submit(new SubmitClaim(email, planId(plus_monthly), its InstaPay price, currency, 'E2E' . random 10 digits, 'en'))` and `approve(E2E_OPERATOR_ID, claimId)` inside the container (tinker, as `grant_plus`), then poll the dev mail catcher's HTTP API (`MAILPIT_API`, default `http://mailpit.test/api/v1`; search `to:<email>`, newest message, plain text) for up to 30 s and extract `[0-9A-Z]{4}(-[0-9A-Z]{4}){3}` → `E2E_CODE`.
  - `+redeem-code-other` — the same for a second, unregistered address (`e2e+…-other@example.com`) → `E2E_CODE` (mismatch flow).
  - `+redeem-code-expired` — `+redeem-code` then set the code row's `expires_at` to the past (tinker; status left `issued`, as before the daily sweep) → `E2E_CODE`. The row is found through its claim (`redeem_codes.claim_id` = the id `submit` returned).
  - `+redeem-code-used` — `+redeem-code` then mark the code row redeemed by another person (tinker: `status = 'redeemed'`, `redeemed_by` and `subscription_id` = fresh UUIDs, `redeemed_at = now()`; the table has no foreign keys on those columns and its check constraint only requires all three together). A genuine "another account holding the same address" cannot exist (addresses are unique), so this row edit is the only way to reach `REDEEM_CODE_USED` end to end; live QA uses the same edit.
  - `run-e2e.sh` needs the approving operator to exist: `ClaimService::approve` takes any operator id string (the CLI checks it is active; the service records it in the audit log as `actor_id`, a uuid), so the fixed `E2E_OPERATOR_ID` works as for grants — verify at implementation.
- **Rationale**: the brief's integration checkpoint ("the code is read from the dev mail log"); the code is stored only as a hash.
- **Alternatives**: read the queued mail job's payload from the `jobs` table (rejected: couples to queue internals and may be encrypted); CLI `billing:claim:approve` (fine for humans, but it needs an active operator and a claim id lookup — tinker gives both in one call).

## R-13 Live-backend contract QA (instead of a device run)

- **Decision**: as specs 007–013: throwaway accounts registered by tinker; claims approved through `ClaimService`; the code read from the mail catcher; curl `POST /billing/redeem` with `X-Client-Platform: android` capturing real answers for: success (Free → Plus), replay (same key → `Idempotency-Replayed: true`), repeat with a new key (200, same subscription id), other account (403 mismatch), unverified account holding the address (403 `EMAIL_NOT_VERIFIED`), unknown (404), revoked (`billing:claim:revoke-code` → 404), expired (tinker → 410), used by another person (tinker row edit, R-12 → 409 `REDEEM_CODE_USED`), malformed (`7KQ2-MX4D` → 422 `/code`), lowercase and spaced and `O`/`I`/`L` folded (200), an operator-granted lifetime Plus then redeem (governing ≠ redeemed), and 11 attempts in an hour (429 + `Retry-After`). Captured bodies go to `CapturedRedeemResponses.kt` and are replayed through the real `ApiClient` → `RedeemService` in `RedeemLiveReplayTest`. Codes and keys in the captured fixtures are throwaway values from deleted dev claims.
