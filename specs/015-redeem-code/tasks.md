---
description: "Task list for 015-redeem-code"
---

# Tasks: Redeem Code (client)

**Input**: Design documents from `specs/015-redeem-code/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (redeem-api-client.md, redeem-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `bee8e65` (specs 001–013; C3 Plan screen and entitlements gate from spec 005)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per screen/component; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/015-redeem-code/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Host QA (Phase 6) replays the call against the live local backend with `curl` and turns the real answers into fixtures, as specs 007–013 did.

**Organization**: Phase 2 builds the core additions, the design-system field, the wire client, the normaliser and the auth hook. US1 (redeem and see the plan) is the MVP and the brief's checkpoint; US2 adds the governing-plan explanations; US3 the refusals.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US3 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); billing feature `cm/feature/billing/…` (`fb/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); billing tests `ct/feature/billing/…` (`bt/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: billing ui → billing domain → billing data; billing reaches auth only through `SessionManager`; `fb/data` never imports auth `data`; `core` imports no feature), naming (P-11).
- **P-12**: no copy, code path or test fixture in the app mentions "InstaPay", a price, a payment address or a website; `POST /billing/instapay/claims` is never built.
- **Secrets (P-09.2)**: the code and the attempt identifier live only in the redeem store's state, its intents and the request; every holder overrides `toString` with a redaction; never in `KeyValueStore`, Room, logs, crash reports, a URL, or the clipboard. Info logs: outcome name and request id only.
- The redeem call is never retried automatically (P-02.6) and never queued.
- **MVI screen recipe (P-16)** as specs 001–013: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas, `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Merge hotspots** (parallel C13 branch): `ApiErrorCode.kt`, `HttpClientFactory.kt`, `SessionManager.kt`, `Fields.kt`, `CatalogSections.kt`, `Routes.kt`, `AppNavHost.kt`, `BillingModule.kt`, `run-e2e.sh`: additive only. New strings in one append-only block `<!-- Redeem code (spec 015) -->` at the end of each `strings.xml`.
- Every interactive element and message gets the `testTag` of contracts/ui-and-e2e.md.

---

## Phase 1: Setup

- [X] T001 Rerun `.specify/scripts/bash/check-backend-contract.sh` (with `SAVEME_BACKEND_DIR` if needed); if `instapay.yaml`, the three redeem codes or the idempotency conventions changed since `b63e917`, update spec §5 "Contract changes since last spec" and the affected FRs before coding
- [X] T002 [P] Add the `<!-- Redeem code (spec 015) -->` string block to both `strings.xml` files with every key of contracts/ui-and-e2e.md and the three `error_redeem_code_*` keys of contracts/redeem-api-client.md (EN and AR, Western digits)

---

## Phase 2: Foundational (blocking all stories)

### Tests first

- [X] T003 [P] `ct/core/network/ApiErrorCodeTest.kt`: extend — `REDEEM_CODE_EMAIL_MISMATCH` AfterChange, `REDEEM_CODE_USED` Never, `REDEEM_CODE_EXPIRED` Never, `IDEMPOTENCY_REQUEST_IN_PROGRESS` Automatic, `IDEMPOTENCY_KEY_REUSED` Never; `from()` maps each (FR-028)
- [X] T004 [P] `ct/core/network/IdempotencyKeyTest.kt`: `idempotencyKey(key)` sets exactly one `Idempotency-Key` header; the `ClientHeaders` plugin still adds the standard four (MockEngine)
- [X] T005 [P] `bt/domain/RedeemCodeTest.kt`: normaliser table (R-02) — lower-case, `O`/`o`→`0`, `I`/`i`/`L`/`l`→`1`, `U` and punctuation dropped, spaces/hyphens/line breaks dropped, Arabic-Indic digits dropped, cut at 16; `fromPaste` takes the grouped code out of a whole email line (hyphen and space separators, any case) and falls back to `normalise`; `grouped` and `isComplete`
- [X] T006 [P] `ct/feature/auth/domain/SessionManagerApplyEntitlementsTest.kt`: `applyEntitlements` replaces plan id/name and features in the snapshot and state for the same user; returns false and writes nothing when signed out or another user is signed in; `null` quota stays unlimited (spec 005 FR-003)
- [X] T007 [P] `bt/data/RedeemContractTest.kt` (MockEngine; fixtures from the `instapay.yaml` examples `redeemedGoverns`, `playStillGoverns`, a lifetime variant with null ends, and one with an unknown member): POST `billing/redeem`; `Authorization` present; `Idempotency-Key` equals the given key; body exactly `{"code":"XXXX-XXXX-XXXX-XXXX"}`; `Accept-Language` sent; both examples decode; unknown member ignored; a test asserts no `BillingApi` method targets `billing/instapay/claims` (P-12.4)
- [X] T008 [P] `ht/designsystem/CodeFieldScreenshotTest.kt` + `ht/designsystem/CodeFieldTest.kt`: goldens empty / partial (`7KQ2MX`) / complete / error in EN LTR, AR RTL and font scale 2.0 (code stays LTR in four groups); the visual hyphen offset mapping keeps the cursor right when typing and deleting; the context menu offers paste and not copy or cut; semantics announce the label and the characters separately (FR-004, FR-029, FR-030)
- [X] T009 [P] `ht/core/platform/SecureWindowTest.kt` (Robolectric): `FLAG_SECURE` set on the activity window while `SecureWindow()` is composed and cleared on dispose; left set if it was already set before

### Implementation

- [X] T010 [P] `cm/core/network/ApiErrorCode.kt`: add the five codes with their retry classes and KDoc (spec 015, FR-028)
- [X] T011 [P] `cm/core/network/HttpClientFactory.kt`: add `fun HttpRequestBuilder.idempotencyKey(key: String)` beside `publicEndpoint()` (research R-05); no logging of headers
- [X] T012 [P] `cm/core/platform/SecureWindow.kt` (`expect @Composable fun SecureWindow()`), `shared/src/androidMain/…/core/platform/SecureWindow.android.kt` (FLAG_SECURE via the activity unwrapped from `LocalContext`, `DisposableEffect`, restore prior state), `shared/src/iosMain/…/core/platform/SecureWindow.ios.kt` (no-op) (research R-04)
- [X] T013 `cm/designsystem/component/Fields.kt`: `CodeField` (research R-03: password-variation keyboard without masking, capitalization characters, autocorrect off, IME done action; LTR text; grouping `VisualTransformation` with exact `OffsetMapping`; paste-only `LocalTextToolbar` wrapper, or `filterTextContextMenuComponents` if the toolbar is bypassed; character-by-character semantics; `errorText` like `AppTextField`); add a `code` text style token to `AppTypography` only if no tabular/monospace style exists; catalogue entry in `cm/designsystem/catalog/CatalogSections.kt`
- [X] T014 [P] `cm/feature/auth/domain/SessionManager.kt`: `applyEntitlements(forUserId, planId, planName, features)` with the same guards and write path as `refreshEntitlements()` (research R-06)
- [X] T015 [P] `fb/data/dto/RedeemDtos.kt`: `RedeemRequestDto` (redacted `toString`), `RedeemResultDto`, `RedeemedSubscriptionDto`, `RedeemEntitlementsDto` (data-model §1); `fb/data/BillingApi.kt`: `redeem(code: String, idempotencyKey: String)` using `idempotencyKey` (plain strings: `data` never imports `domain`, P-10.6)
- [X] T016 [P] `fb/domain/RedeemCode.kt`: the normaliser object (research R-02); `AttemptKey` value class with redacted `toString` in `fb/domain/RedeemService.kt` (file created here, service body in T019)
- [X] T017 [P] `cm/navigation/Routes.kt`: `data object RedeemCodeKey : NavKey` (nothing carried in the key)

**Checkpoint**: T003–T009 pass.

---

## Phase 3: User Story 1 - Redeem a code and see my new plan (Priority: P1) 🎯 MVP

**Goal**: Plan → Enter code → paste → Redeem → confirmation → Done → Plan shows the new plan.

**Independent Test**: spec US1 Independent Test (live QA §2 step 3 and flow `US1-redeem`, deferred).

### Tests first

- [X] T018 [P] [US1] `bt/domain/RedeemServiceTest.kt` (fakes): success applies entitlements then saves `governing` then launches the billing-action refresh (order asserted), returns `Redeemed`; a refresh failure leaves the applied answer; sign-out or a different user before the answer → nothing applied, `SignedOut`; cancelling the caller after the request started still applies a success (FR-012); exactly one request per call, no retry; offline/timeout → `Offline`; sends `grouped(raw)`
- [X] T019 [US1] `fb/domain/RedeemService.kt`: `RedeemService` and `RedeemOutcome` (contracts/redeem-domain.md; success and offline paths; request and apply inside `scope.async`), plus `fb/domain/RedeemConfirmation.kt` with `from(result, now)` for the governing-is-redeemed case (US2 extends)
- [X] T020 [P] [US1] `bt/ui/redeem/RedeemReducerTest.kt`: `CodeChanged` stores the normalised code and clears `attempt` and `message`; `canRedeem` only at 16 characters, not sending, no wait; `SendStarted` → Sending; `Redeemed` → Done(confirmation); `SessionSignedOut` clears code and attempt; state and intent `toString` are redacted (R-10)
- [X] T021 [P] [US1] `bt/ui/redeem/RedeemViewModelTest.kt`: `Redeem` creates an attempt key once and passes it; a second `Redeem` while sending is ignored; unverified → `RequireVerification` and no service call (FR-003); `Done` and `Back` on the confirmation → `NavigateBack`; paste of a whole email line yields the code
- [X] T022 [P] [US1] `bt/ui/plan/PlanViewModelTest.kt` + `PlanReducerTest.kt`: extend — `EnterCode` → `PlanEffect.OpenRedeem`

### Implementation

- [X] T023 [US1] `fb/ui/redeem/RedeemContract.kt` (State per data-model §3 with redacting `toString`, Intents, Effects, Changes, pure `reduce`) and `RedeemViewModel.kt` (key generation, verified check, service call, sign-out observation; lambdas from DI) — make T020/T021 pass
- [X] T024 [US1] `fb/ui/redeem/RedeemScreen.kt` (stateless; layout, copy and tags of contracts/ui-and-e2e.md; `CodeField`; confirmation block for the governing-is-redeemed case) and `RedeemRoute.kt` (`SecureWindow()`, collects state, handles effects, focuses the field on open)
- [X] T025 [US1] `fb/ui/plan/PlanContract.kt`, `PlanViewModel.kt`, `PlanScreen.kt`, `PlanRoute.kt`: "Enter code" secondary button (`plan.enterCode`) in the reserved `Column`; `PlanIntent.EnterCode`, `PlanEffect.OpenRedeem` (research R-11)
- [X] T026 [US1] `fb/di/BillingModule.kt`: `RedeemService` (application `CoroutineScope`), `RedeemViewModel` with `isEmailVerified`, `signedInEmail`, time zone (as `PlanViewModel`), clock; `cm/navigation/AppNavHost.kt`: `entry<RedeemCodeKey>` with `NavigateBack` → pop and `RequireVerification` → `emailNotVerifiedVisible = true`; `PlanEffect.OpenRedeem` → push `RedeemCodeKey`; `AppGraphTest` stays green
- [X] T027 [P] [US1] `ht/feature/billing/RedeemScreenScreenshotTest.kt`: editing (empty, complete), sending, unverified note, confirmation (now on, until) in EN LTR, AR RTL, 2.0; update `PlanScreenScreenshotTest` goldens for the new button
- [X] T028 [P] [US1] `.maestro/015-redeem-code/US1-redeem.yaml` (seed `verified-user+redeem-code`; types `${E2E_CODE}` lower-case without hyphens; asserts grouped display, confirmation, Plan shows Plus)
- [ ] T029 [US1] Run US1 flow — **deferred: E2E on owner's device**

**Checkpoint**: US1 host tests green; the MVP redeems and shows the plan.

---

## Phase 4: User Story 2 - Understand which plan applies (Priority: P1)

**Goal**: The confirmation names the governing plan, explains an existing subscription that stays, the redeemed plan's end, a later start, and Google Play's continued billing.

**Independent Test**: spec US2 Independent Test (live QA §2 step 6; flow `US2-current-stays`, deferred).

- [X] T030 [P] [US2] `bt/domain/RedeemConfirmationTest.kt`: over `redeemedGoverns` → governing is redeemed, no Play note; over `playStillGoverns` → not redeemed, `playRenews` true; Play with `auto_renew` false → no note; manual lifetime governing (null end) → "no end" and not redeemed; grace governing → grace end used; redeemed start after `now` → `redeemedStartsAt` set, otherwise null; unknown provider → no Play note; both-null ends compare equal (research R-07)
- [X] T031 [US2] `fb/domain/RedeemConfirmation.kt`: complete the mapping (R-07)
- [X] T032 [US2] `fb/ui/redeem/RedeemScreen.kt`: confirmation lines `redeem_done_current_stays_*`, `redeem_done_redeemed_*`, `redeem_done_starts_on`, `redeem_done_play_renews` with their tags; dates in the profile time zone (P-05)
- [X] T033 [P] [US2] Screenshot states in `RedeemScreenScreenshotTest`: current stays (no end), Play renews, starts later — EN LTR, AR RTL, 2.0
- [X] T034 [P] [US2] `.maestro/015-redeem-code/US2-current-stays.yaml` (seed `verified-user+plus-lifetime+redeem-code`)
- [ ] T035 [US2] Run US2 flow — **deferred: E2E on owner's device**

---

## Phase 5: User Story 3 - Be told clearly why a code did not work (Priority: P1)

**Goal**: Every refusal has its own message; rate limit disables Redeem until the wait passes; retries reuse the attempt.

**Independent Test**: spec US3 Independent Test (live QA §2 steps 4, 5, 7; flows `US3-*`, deferred).

### Tests first

- [X] T036 [P] [US3] `bt/domain/RedeemServiceRefusalTest.kt`: each code of research R-08 → its outcome (404, 409 used, 409 in progress, 409 key reused with request id, 410, 403 mismatch, 403 not verified, 422 any pointer, 429 with `retry_after_seconds` and with only the `Retry-After` header and with neither, 401, 426, 500 with and without request id); no outcome is retried (FR-017–FR-026)
- [X] T037 [P] [US3] `bt/ui/redeem/RedeemReducerTest.kt` + `RedeemViewModelTest.kt`: extend — each outcome sets its `RedeemMessage`; `RateLimited(s)` sets `waitUntil`, disables Redeem, and a `WaitOver` after `s` seconds (virtual time) re-enables it; minutes rounded up, "a minute" under 60 s; `Offline` and `InProgress` keep the attempt and "Try again" reuses it; `KeyReused` drops it so the next Redeem creates a new one; editing after a refusal clears the message and the attempt; `OtherAddress` carries the signed-in address and nothing else
- [X] T038 [P] [US3] `bt/ui/BillingErrorCopyTest.kt`: extend — the three redeem codes map to their keys; every `RedeemMessage` resolves to a string in EN and AR

### Implementation

- [X] T039 [US3] `fb/domain/RedeemService.kt`: refusal mapping (R-08)
- [X] T040 [US3] `fb/ui/BillingErrorCopy.kt`: `ApiErrorCode.redeemMessage()` for the three codes; `fb/ui/redeem/RedeemContract.kt` + `RedeemViewModel.kt`: messages, wait timer (clock and delay in intent handling, never in `reduce`), attempt rules
- [X] T041 [US3] `fb/ui/redeem/RedeemScreen.kt`: message area (`InlineAlert` error, polite live region, `redeem.message`), "Try again" (`redeem.retry`) for offline and in-progress, verify note, mismatch second line `redeem_signed_in_as` (FR-020, FR-021); the message stays until the code is edited or retried (FR-027)
- [X] T042 [P] [US3] Screenshot states: not valid, used, expired, other address, rate limited (minutes), offline with Try again — EN LTR, AR RTL, 2.0
- [X] T043 [P] [US3] `.maestro/015-redeem-code/`: `US3-other-address.yaml`, `US3-used.yaml`, `US3-expired.yaml`, `US3-not-valid.yaml`, `US3-unverified.yaml`, `US3-offline.part1.yaml`/`.part2.yaml`/`.part3.yaml` (seeds and `e2e-between` of contracts/ui-and-e2e.md)
- [ ] T044 [US3] Run US3 flows — **deferred: E2E on owner's device**

---

## Phase 6: Polish & Cross-Cutting

- [X] T045 [P] `ht/feature/billing/RedeemStringsTest.kt`: scan every `redeem_*`, `plan_enter_code` and `error_redeem_code_*` string in both languages for "InstaPay", "إنستاباي", any ISO currency code, "token", "voucher", "coupon", "promo"; assert every key exists in both files (FR-002, P-12)
- [X] T046 [P] `bt/RedeemLoggingTest.kt`: Kermit capture writer over a full redemption (success and each refusal, via MockEngine) — the code (raw and grouped) and the attempt key never appear in any log line (SC-004)
- [X] T047 [P] Layering: extend the existing layering/architecture test so `feature/billing/data` imports no `feature/auth/data` and `core` imports no feature (P-10.4, P-10.7)
- [X] T048 `.specify/scripts/bash/run-e2e.sh`: seeds `+redeem-code`, `+redeem-code-other`, `+redeem-code-used`, `+redeem-code-expired`, composable after the existing seeds (research R-12: `ClaimService` submit + approve with `E2E_OPERATOR_ID` inside the container; Mailpit search and code extraction with a 30 s poll; `E2E_CODE`; `MAILPIT_API` env with default); update the header comment; confirm `approve` accepts the fixed operator id and the Mailpit API path (checklist CHK036, CHK037) with one dry run of the seeding functions against the local backend (no device) (seeds written and syntax-checked; not executed, per run instructions)
- [ ] T049 Live-backend contract QA (quickstart §2): throwaway accounts, claims approved through `ClaimService`, codes from Mailpit, every answer of steps 3–7 captured; write `ct/feature/billing/CapturedRedeemResponses.kt` and `RedeemLiveReplayTest.kt` (real `ApiClient` → `RedeemService`); write `specs/015-redeem-code/qa-report.md`; clean up the QA accounts and claims where the classifier allows (QA step; left unchecked)
- [X] T050 Gates: `./gradlew :shared:testAndroidHostTest`, `:shared:verifyRoborazziAndroidHostTest`, `:androidApp:assembleDevDebug`, `:shared:compileKotlinIosSimulatorArm64`; `check-backend-contract.sh` clean (client codes vs catalogue)
- [ ] T051 Quickstart §3 spot checks (FLAG_SECURE, paste-only menu, keyboard suggestions, Arabic 200 %) — **deferred: E2E on owner's device**
- [X] T052 Update `specs/briefs/README.md` client state for C11 and plan.md "Implementation notes"
- [ ] T053 Write specs/015-redeem-code/summary.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 → US1 (Phase 3) → US2 (Phase 4) and US3 (Phase 5) → Phase 6.
- US2 and US3 both edit `RedeemScreen.kt` and `RedeemContract.kt`; run them one after the other (or coordinate) — their tests can be written in parallel.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T003–T009 all [P]; implementation T010–T012 and T014–T017 in parallel; T013 alone (design system).
- US2's T030 and US3's T036–T038 can be written while US1's screen is being built.

## Parallel Example: Phase 2

```text
T003 codes | T004 header | T005 normaliser | T006 applyEntitlements | T007 contract | T008 CodeField | T009 SecureWindow
then: T010 | T011 | T012 | T014 | T015 | T016 | T017   and   T013 (CodeField)
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: a code redeems through the MockEngine and the live replay; the Plan screen shows the applied plan; host tests green.

### Incremental Delivery

1. US1 (brief checkpoint) → US2 (governing explanations) → US3 (refusals).
2. Polish: string scan, log capture, layering, E2E seeds, live QA with captured fixtures, gates, summary; device E2E and spot checks deferred to the owner.
