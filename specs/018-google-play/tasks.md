---
description: "Task list for 018-google-play"
---

# Tasks: Google Play Billing (client)

**Input**: Design documents from `specs/018-google-play/`

**Prerequisites**: plan.md, spec.md, research.md (R-1 … R-15), data-model.md, contracts/ (google-play-api-client.md, play-billing-port.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `e0eef36` (specs 001–015 incl. sync engine, C3 Plan screen and entitlements gate, C11 redeem code, C13 reports); backend contract `saveme-backend@e8c308a` with `google-play.yaml` v1.15 (uncommitted).

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per changed screen; P-16 requires reducer + ViewModel tests. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/018-google-play/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Live contract QA (Phase 10) replays the calls against the local backend once backend spec 024 is built and committed.

**Gates** (see plan "Phases and gates"):
- Phases 1–9: no gate; **no new dependency** (P-14.5).
- Phase 10: **gate — backend spec 024 built and committed** (B16 implementation).
- Phase 11 (G2): **gate — owner amends P-12.3** (and backend P-10.2). Buildable with the fake; no dependency.
- Phase 12 (G1): **gate — owner approves `com.android.billingclient:billing-ktx` and supplies the Play Console setup. "Play Billing wiring (blocked on owner approval)". Do not start, and do not add the dependency, without that approval recorded in plan.md.**

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US6 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); billing feature `cm/feature/billing/…` (`fb/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); billing tests `ct/feature/billing/…` (`bt/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Android/iOS source sets: `shared/src/androidMain/kotlin/com/fares/saveme/…` (`am/…`), `shared/src/iosMain/kotlin/com/fares/saveme/…` (`im/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: billing ui → billing domain → billing data; **`fb/data/play` imports no domain and no Android or Play Billing type in commonMain**; billing reaches auth only through `SessionManager`; `core` imports no feature), naming (P-11).
- **Never acknowledge, never consume, never grant locally** (backend spec 024 "Frozen for C10"). The port has no such operation.
- **P-12.3**: the only billing actions are "Restore purchases" and "Enter code" (+ "Manage in Google Play" / "Fix payment" links). No Subscribe, buy or checkout action is reachable while `AppConfig.inAppSubscribe` is `false` (every flavour). No InstaPay, no catalogue `amount_minor` shown.
- **Secrets (P-09.2)**: purchase tokens, idempotency keys, account references, order ids and prices are never logged, persisted (no `KeyValueStore`, no Room) or shown; holders are value classes with redacted `toString`. Logs: fixed event names, outcome codes, counts, request id only.
- Retries only per the catalogue's retry class with `Retry-After` (P-02.6); billing calls are never queued in the sync outbox.
- **MVI (P-16)** as specs 001–015: Contract (immutable State, sealed Intent/Effect/Change, pure `reduce`), ViewModel on `MviViewModel` taking domain lambdas, Route, stateless Screen + `@Preview`; `…ReducerTest` + `…ViewModelTest` with `runMviTest`.
- **Merge hotspots** (parallel 016/017 branches): `ApiErrorCode.kt`, `AppConfig.kt`, `BillingModule.kt`, `PlatformModule.*.kt`, `PlanContract.kt`/`PlanScreen.kt`, `MainApplication.kt`, `run-e2e.sh`, both `strings.xml`: additive only. New strings in one append-only block `<!-- Google Play billing (spec 018) -->` at the end of each `strings.xml`.
- Every interactive element and message gets the `testTag` of contracts/ui-and-e2e.md.
- Do not run Gradle in parallel with other worktrees' builds on this machine without coordination (shared daemon/cache).

---

## Phase 1: Setup

- [ ] T001 [P] Add `PURCHASE_ALREADY_LINKED` (retry `Never`) and `PURCHASE_NOT_VERIFIED` (retry `Never`) to `cm/core/network/ApiErrorCode.kt` with KDoc citing backend spec 024 / v1.15; extend the exhaustive `AuthErrorCopy.message()` and `BillingErrorCopy` mappings (FR-052); test in `ct/core/network/ApiErrorCodeTest.kt` (or the existing codes test)
- [ ] T002 [P] `expect fun sha256(bytes: ByteArray): ByteArray` in `cm/core/platform/Sha256.kt`; actuals `am/core/platform/Sha256.android.kt` (`MessageDigest`) and `im/core/platform/Sha256.ios.kt` (CommonCrypto `CC_SHA256`); test vectors (empty string, "abc") in `ct/core/platform/Sha256Test.kt`
- [ ] T003 [P] `AppConfig` gains `applicationId: String = ""` and `inAppSubscribe: Boolean = false` (`cm/core/config/AppConfig.kt`); `androidApp/src/main/kotlin/com/fares/saveme/MainApplication.kt` passes `BuildConfig.APPLICATION_ID`; iOS `MainViewController.kt` leaves defaults; no flavour sets `inAppSubscribe`
- [ ] T004 [P] Strings block `<!-- Google Play billing (spec 018) -->` in both `strings.xml` with every key of contracts/ui-and-e2e.md "Copy" (US6 keys included, referenced only from the gated path)

---

## Phase 2: Foundational (blocking all stories)

### Tests first

- [ ] T005 [P] `bt/play/FakePlayBillingClient.kt` implementing the port (scripted connection, purchases, products, updates via `MutableSharedFlow`, launch results; records calls) and `bt/play/GooglePlayFixtures.kt` (the `google-play.yaml` v1.15 examples verbatim: `active`, `ackPending`, `pending`, `restored`, `reference`, plus problem bodies for each handled code)
- [ ] T006 [P] Contract tests `bt/data/GooglePlayContractTest.kt` per contracts/google-play-api-client.md §"Contract tests" items 1–6 (MockEngine; request shape; examples decode; one test per code; restore 0/1/25 items; unknown values; reference pattern) (P-14.3)
- [ ] T007 [P] `bt/domain/play/PlayKeysTest.kt`: same inputs → same 68-char `gp1:<hex>` key; differs by user, by state, by recheck hour; charset `[A-Za-z0-9._:-]`; key never contains the token; `attempt()` keys are random and redacted (FR-012, research R-3)
- [ ] T008 [P] `bt/domain/play/PlayBillingNotesTest.kt`: notes per user, reset on user change and sign-out cleaner, pending recomputed from a pass, one-shot `activated` consumed once (FR-035, FR-051, R-9)
- [ ] T009 [P] `bt/domain/BillingAnswerApplierTest.kt`: applies entitlements + subscription for the same signed-in person, refuses for another person or signed out (writes nothing), launches `refreshAfterBillingAction()` (R-6)

### Implementation

- [ ] T010 Port and plain types in `fb/data/play/PlayBillingClient.kt` (`PlayBillingClient`, `PlayConnection`, `PlayUnavailable`, `PlayResult`, `PlayError`, `PlayPurchase`, `PurchaseToken` value class with redacted `toString`, `PlayPurchaseState`, `PlayProductKind`, `PlayProduct`, `PlayPurchaseUpdate`, `PlayLaunchRequest`, `AccountReference` value class, `PlayLaunchResult`) per contracts/play-billing-port.md — **no acknowledge/consume member**
- [ ] T011 [P] `fb/data/play/UnavailablePlayBillingClient.kt` (`platformSupported` constructor flag; `connect()` → `Unavailable(NotSupported|NotWired)`; empty lists; empty updates); bind it in `am/core/di/PlatformModule.android.kt` (`true`) and `im/core/di/PlatformModule.ios.kt` (`false`)
- [ ] T012 [P] DTOs `fb/data/dto/GooglePlayDtos.kt` (data-model §2; redacted `toString` on request DTOs; unknown members ignored)
- [ ] T013 `BillingApi` gains `verifyPlayPurchase(token, productId, idempotencyKey)`, `restorePlayPurchases(items: List<Pair<String, String>>, idempotencyKey)`, `playAccountReference()` (plain strings, P-10.6) in `fb/data/BillingApi.kt` (depends on T012)
- [ ] T014 [P] `fb/domain/play/PlayKeys.kt` (R-3) using `sha256`
- [ ] T015 [P] `fb/domain/play/PlayBillingNotes.kt` (`PlayNotes`, `StateFlow`, mutators, `activated` one-shot) + `PlayNotesCleaner` bound as `UserDataCleaner` in `fb/di/BillingModule.kt`
- [ ] T016 Extract `fb/domain/BillingAnswerApplier.kt` from `RedeemService.applied` and make `RedeemService` use it; spec 015's redeem tests stay green unchanged (R-6)
- [ ] T017 `Subscription` gains `provider: String?` and `CataloguePlan` gains `playProductId: String?` (`fb/domain/model/…`); `CataloguePlan.price` is no longer filled from `amount_minor` (FR-037); update `CataloguePlanTest` and spec 005 Plan tests that asserted the catalogue price (now no price without Google Play)
- [ ] T018 Koin: register `BillingAnswerApplier`, `PlayBillingNotes` in `fb/di/BillingModule.kt`; extend `ht/core/di/AppGraphTest.kt` to resolve `PlayBillingClient` and `PlayBillingNotes` from the real graph (cycle guard)

**Checkpoint**: contract tests, keys, notes and applier green; nothing user-visible changed except catalogue prices disappearing (no Google Play answer yet).

---

## Phase 3: User Story 1 - A Google Play purchase reaches my account (Priority: P1) 🎯 MVP

**Goal**: unacknowledged Google Play purchases are verified on every trigger with a stable key; answers applied; pending never granted.

**Independent Test**: fake port with one unacknowledged purchased `plus_monthly` + MockEngine `active` → one verify with key, entitlements/subscription applied, refresh launched, no acknowledge call.

### Tests first

- [ ] T019 [P] [US1] `bt/domain/play/PlayPurchaseVerifierTest.kt`: sends only unacknowledged purchases (FR-010); same key across a retry and across a simulated process death (new verifier instance, same fake) (FR-012, SC-004); pending sent once per run then again on purchased with a new key (FR-013); `202`-for-purchased recheck no sooner than 1 h with an hour-bucket key (FR-015); `503`/`429`/in-progress/offline → same key after `Retry-After` or 30 s doubling, max 6 per run (virtual time) (FR-016); `409 PURCHASE_ALREADY_LINKED` and `422` → refused for the run, `VALIDATION_FAILED` triggers one catalogue reload; `IDEMPOTENCY_KEY_REUSED` → refused, code logged; 401/426 stop the pass; one pass at a time with a dirty re-run (FR-011); answer discarded after sign-out or user change; `acknowledged:false` changes nothing; no acknowledge call ever and no grant without an answer (FR-002, SC-003); never queued in the outbox (FR-003); `200` applied through the applier (FR-014); `activated` note when the plan changed (FR-017); with a healthy mock, the plan applies within one pass (SC-001 host proxy)
- [ ] T020 [P] [US1] `bt/domain/BillingSyncCoordinatorTest.kt` (extend): cold start (`Unknown → SignedIn`) runs a verify pass, not a restore; foreground floor 60 s; a purchase update runs a pass; signed out → nothing (R-4)

### Implementation

- [ ] T021 [US1] `fb/domain/play/PlayPurchaseVerifier.kt` (R-4, R-5): `Mutex` + dirty flag, `pass(trigger)`, per-purchase send with `PlayKeys`, outcome mapping to `VerifyOutcome`, notes updates (pending, hold/paused/test from `purchase.state`/`test`), backoff timers in the coordinator scope cancelled at sign-out, fixed log events `play_pass trigger=… sent=… outcome=…`
- [ ] T022 [US1] `fb/domain/BillingSyncCoordinator.kt`: inject the verifier; on `SignedIn` from `Unknown` → verify pass; collect `PlayBillingClient.purchaseUpdates` → pass; `onForeground()` → pass with the 60 s floor (existing `App` call); Koin wiring in `fb/di/BillingModule.kt` (coordinator stays `createdAtStart`)
- [ ] T023 [US1] Plan screen: `PlanIntent.Opened`/`Refresh` also request a pass (domain lambda `runPurchasePass`); `PlanState.notes` collected from `PlayBillingNotes`; `activated` → `PlanEffect.ShowActivated(planName)` → snackbar `plan.activated` in `PlanRoute.kt` (FR-017); reducer + ViewModel tests in `bt/ui/plan/PlanReducerTest.kt`, `PlanViewModelTest.kt`

**Checkpoint**: US1 independently green on host with the fake port.

---

## Phase 4: User Story 2 - Restore my purchases (Priority: P1)

**Goal**: silent restore after a real sign-in; "Restore purchases" on the Plan screen with a combined result.

**Independent Test**: fake with two purchases → sign-in sends one restore with both; tap → second request, new key, result message.

### Tests first

- [ ] T024 [P] [US2] `bt/domain/play/PlayRestoreServiceTest.kt`: sign-in with no purchases → no request; sign-in with purchases → one request, silent unless `linked_elsewhere`; tap sends all (refused ones included) or `[]`; 26 purchases → 25 + 1 with different keys, most recent first, tokens distinct; refusal stops remaining chunks and "Try again" resends the failed chunk with its key; each refusal → `RestoreResult`; Google Play unavailable → `GooglePlayUnavailable`, nothing sent; summary counts per outcome incl. unknown; answer applied via the applier; no email-verified or plan check before sending (FR-020, FR-021, FR-022, FR-024, FR-025, FR-026, SC-002)
- [ ] T025 [P] [US2] Plan tests: `RestorePurchases` → `Running` (button disabled, progress) → `Result(message)` from a summary (each FR-023 sentence and combinations) or `Refused(kind, wait, requestId)`; `RetryRestore` reuses the attempt; `DismissRestoreResult`; Restore hidden when `playSupported` is false (FR-055) — `bt/ui/plan/PlanReducerTest.kt`, `PlanViewModelTest.kt`

### Implementation

- [ ] T026 [US2] `fb/domain/play/PlayRestoreService.kt` (R-7) sharing the verifier's mutex (FR-011)
- [ ] T027 [US2] Coordinator: on a transition from `SignedOut` (or another `userId`) to `SignedIn` → `restore(SignIn)` instead of the verify pass (FR-020, R-4)
- [ ] T028 [US2] Plan screen: "Restore purchases" (`plan.restore`) above "Enter code" in the reserved place, progress state, result/refusal `InlineAlert` (`plan.restore.result`, `plan.restore.retry`) announced as a live region; rate-limit wait and generic-with-request-id via spec 015's helpers — `fb/ui/plan/PlanContract.kt`, `PlanViewModel.kt`, `PlanScreen.kt`, `fb/di/BillingModule.kt`

**Checkpoint**: US1 + US2 green; the brief's "reinstall, sign in, restore" path works against the fake.

---

## Phase 5: User Story 3 - One purchase, one account (Priority: P1)

**Goal**: `PURCHASE_ALREADY_LINKED` and `linked_elsewhere` explained once, never looped, nothing about the other account shown.

**Independent Test**: MockEngine 409 on verify and `linked_elsewhere` on restore → one note, no further send in the run.

- [ ] T029 [P] [US3] Tests: verifier and restore set `anotherAccount`; the purchase is not resent by triggers in the run; a tapped restore still sends it; account switch A → B clears A's notes and pending before B's pass (FR-032, FR-051) — `bt/domain/play/PlayPurchaseVerifierTest.kt`, `PlayRestoreServiceTest.kt`, `PlayBillingNotesTest.kt`
- [ ] T030 [US3] Plan screen "another account" `InlineAlert` (`plan.play.anotherAccount`) with no Sign out action; reducer/ViewModel tests (FR-032)

---

## Phase 6: User Story 4 - Keep my Google Play subscription healthy (Priority: P2)

**Goal**: grace/hold/paused/pending/test notes and the subscription-centre links.

**Independent Test**: stored subscription `google_play` + `grace` → warning + Fix payment → `OpenUrl` effect with `package` and `sku`; `instapay` → neither.

### Tests first

- [ ] T031 [P] [US4] `bt/domain/play/PlayStoreLinksTest.kt`: URL with `package` and `sku`, without `sku` when unknown, encoding (R-8, FR-054)
- [ ] T032 [P] [US4] Plan tests: `showManage` only for provider `google_play` and `playSupported`; grace warning with the grace end in the profile zone; hold note + Fix payment, paused note + Manage, only after a verify answer this run; pending note from notes; "Test purchase" label per FR-034 mapping (FR-031, FR-033, FR-034); `OpenManage`/`OpenFixPayment` → `OpenUrl(url)`; open failure → `plan_play_open_failed` snackbar (FR-030–FR-035) — `bt/ui/plan/PlanReducerTest.kt`, `PlanViewModelTest.kt`

### Implementation

- [ ] T033 [US4] `fb/domain/play/PlayStoreLinks.kt`; `MySubscriptionDto.provider` mapped into `Subscription.provider` (T017); governing plan → catalogue `playProductId` (`manageProductId`)
- [ ] T034 [US4] Plan screen: problem/pending alerts, Manage text button, test label, `OpenUrl` handled in `PlanRoute.kt` via `LocalUriHandler` with failure snackbar; tags per contracts/ui-and-e2e.md

---

## Phase 7: User Story 5 - See Google Play's prices (Priority: P2)

**Goal**: Google Play's price, exact integer minor units, app formatter; none when Play cannot answer.

**Independent Test**: fake `plus_monthly` 149,000,000 micros EGP → "EGP 149.00" / Arabic with Western digits; unavailable → no price.

- [ ] T035 [P] [US5] `bt/domain/play/PlayPricesTest.kt`: micros → minor for EGP (2), KWD (3), JPY (0); inexact → null; digits > 6 → null; no `Double` (FR-036, R-10)
- [ ] T036 [P] [US5] Plan tests: prices requested on open and when the product-id set changes, at most once a minute; card price from `playPrices[playProductId]`; catalogue `amount_minor` never shown; no placeholder while loading (FR-036–FR-038)
- [ ] T037 [US5] `fb/domain/play/PlayPrices.kt` + Plan screen wiring (`loadPlayPrices` domain lambda; `PlanState.playPrices`; `PlanCardUi.price` from it)

---

## Phase 8: Dev tools and E2E assets

- [ ] T038 [P] `cm/devtools/playbilling/ScriptedPlayBillingClient.kt` + a `SwitchingPlayBillingClient(real, scripted)` bound only when `AppConfig.isDebugTools` (dev flavour); Dev tools section "Google Play (scripted)" (on/off, add purchase with token/product/state/acknowledged, emit update, mark unavailable, add product price) as an MVI section with reducer + ViewModel tests in `ct/devtools/playbilling/` (FR-056, R-12); release graph test proves the scripted client is not bound
- [ ] T039 [P] `.specify/scripts/bash/run-e2e.sh`: seeds that write the backend's `BILLING_PLAY_FAKE_SCRIPT` for a flow's tokens (`e2e-<flow>-…`) with the fresh account's reference, and a grace seed for a `google_play` subscription
- [ ] T040 [P] Maestro flows `.maestro/018-google-play/US1-background-verify.yaml`, `US1-pending-then-purchased.yaml`, `US2-restore-tap.yaml`, `US2-restore-nothing.yaml`, `US3-another-account.yaml`, `US4-grace-fix-payment.yaml`, `US5-prices.yaml` (+ `common/` helpers to open Dev tools and script a purchase), each starting `# deferred: E2E on owner's device`
- [ ] T041 Run the 018 flows and write `e2e-report.md` — **deferred: E2E on owner's device**

---

## Phase 9: Polish & Cross-Cutting

- [ ] T042 [P] Extend `ht/feature/billing/BillingLayeringTest.kt`: `fb/data/play` imports no domain; no `android.`/`com.android.billingclient` import under commonMain; no source under `shared/src` calls `acknowledgePurchase`, `consumeAsync` or `consumePurchase`; the port interface has no member named like acknowledge/consume (FR-002, FR-004)
- [ ] T043 [P] `ht/feature/billing/GooglePlayStringsTest.kt`: every new key in both languages; no "token", "InstaPay", "acknowledge", "order", "verify" or digit-currency price literal in the new strings; Subscribe keys referenced only from the gated path; every refusal and restore outcome has its own key (P-01, P-12, SC-005)
- [ ] T044 [P] `ct/feature/billing/GooglePlayLoggingTest.kt`: log capture over a full verify pass, a restore and a refusal — no token, key, reference or price; only fixed event names, codes, counts, request id (FR-050, SC-006); `toString` redaction of `PurchaseToken`, `AccountReference`, request DTOs
- [ ] T045 [P] Plan screen switch-off test: with `inAppSubscribe = false` no state ever carries a Subscribe offer and the screen renders no `plan.card.N.subscribe`; the billing actions are exactly Restore + Enter code (+ Manage/Fix payment only under FR-030/FR-031) (FR-001, FR-040, SC-007)
- [ ] T046 Roborazzi goldens in `ht/feature/billing/PlanScreenScreenshotTest.kt` (EN LTR, AR RTL, 200 %; FR-053, SC-008): restore running/result/refused, grace warning, hold, pending, another account, Manage + test label, Google Play prices; update existing goldens that change on purpose (catalogue price gone) and say so in the summary
- [ ] T047 README "On-device database" stays v10 (state "spec 018 adds no table"); `specs/briefs/README.md` C10 row (client state); plan.md "Implementation notes"
- [ ] T048 Gates (implementer, coordinated with other worktrees): `./gradlew :shared:testAndroidHostTest`, `verifyRoborazziAndroidHostTest`, `:androidApp:assembleDevDebug`, iOS simulator compile, `./gradlew build allTests`; confirm `git diff gradle/libs.versions.toml */build.gradle.kts` adds no dependency (P-14.5)

---

## Phase 10: Live contract QA (gate: backend spec 024 built and committed)

- [ ] T049 Re-run `speckit-contract-check`; record the new backend commit in spec.md and plan.md; confirm `PURCHASE_ALREADY_LINKED`/`PURCHASE_NOT_VERIFIED` are in `error-catalog.yaml` (no `NOT IN CATALOGUE` line); fix drift first
- [ ] T050 curl QA against the local backend (`BILLING_PLAY_DRIVER=fake`, scripted tokens) for every case of quickstart.md §3; capture answers (tokens/references redacted) in `bt/CapturedGooglePlayResponses.kt`; `bt/GooglePlayLiveReplayTest.kt` replays them through the real `ApiClient` → `BillingApi` → verifier/restore; record results in `qa-report.md`

---

## Phase 11: G2 — Subscribe in the app (gate: owner amends P-12.3; Priority P3)

**Do not start until the amendment is ratified and recorded in plan.md. No dependency needed; buildable with the fake.**

- [ ] T051 [P] [US6] `bt/domain/play/PlaySubscribeFlowTest.kt`: eligibility (switch, signed in, Play available, product known, no paid governing, no owned/pending purchase, not busy) (FR-041); account reference read once per account per run, refused reads by code, bad pattern refused (FR-042, FR-045); flow states idle → connecting → launching → cancelled/failed/purchased → verifying → done/pending/another account/not verified/retrying (FR-043); cancel shows nothing; each `PlayError` → message; `ItemAlreadyOwned` → restore pass (FR-044); no Subscribe while a Google Play subscription governs — upgrades/downgrades stay in Google Play (FR-048); launch request carries the reference as obfuscated **account** id and the base plan's offer token (FR-042, FR-049)
- [ ] T052 [US6] `fb/domain/play/PlaySubscribeFlow.kt` + account-reference holder (memory, cleared by the notes cleaner); purchases from the sheet flow through the US1 verifier (FR-047)
- [ ] T053 [US6] Plan screen: per-card "Subscribe" (`plan.card.N.subscribe`) when eligible, progress, messages, "You're on <plan>." with test label (FR-046); reducer/ViewModel tests; goldens; set `inAppSubscribe = true` in `MainApplication.kt` only with the amendment's version noted in plan.md
- [ ] T054 [US6] Maestro flow `US6-subscribe-scripted.yaml` driving the scripted client (cancel, purchased, already owned) — **deferred: E2E on owner's device**

---

## Phase 12: G1 — Play Billing wiring (blocked on owner approval)

**Gate: the owner approves `com.android.billingclient:billing-ktx` (P-14.5) and supplies the Play Console setup (plan "What the owner must approve or supply" 2–3). Record the approval and the chosen version in plan.md before T055. Everything in Phases 1–11 works without this phase.**

- [ ] T055 Choose the current stable Play Billing Library version with the `play-billing-library-version-upgrade` skill; add `billing-ktx` to `gradle/libs.versions.toml` and `shared/build.gradle.kts` `androidMain` dependencies only; record approval + version in plan.md
- [ ] T056 [P] `ht/feature/billing/data/play/GooglePlayErrorMappingTest.kt`: `playErrorOf(responseCode)` table (contracts/play-billing-port.md); purchase mapping from `Purchase(json, signature)` fixtures (state, acknowledged, obfuscated account id, products, time; `UNSPECIFIED_STATE` dropped)
- [ ] T057 `am/feature/billing/data/play/GooglePlayBillingClient.kt` per research R-11 (pending purchases enabled incl. one-time; single reused connection; SUBS + INAPP queries; base-plan price; listener → buffered flow; `launchBillingFlow` with `setObfuscatedAccountId`; **no acknowledge, no consume**)
- [ ] T058 `am/core/platform/CurrentActivityHolder.kt` registered via `ActivityLifecycleCallbacks` in `MainApplication.kt`; `launchPurchase` uses the resumed Activity, none → `Failed(Unknown)`
- [ ] T059 Bind `GooglePlayBillingClient` in `am/core/di/PlatformModule.android.kt` (replacing `Unavailable(true)`); `AppGraphTest` resolves it under Robolectric without connecting; dev flavour's switching client wraps it
- [ ] T060 Gates again (T048) plus `:androidApp:assembleProdRelease` (minified) to confirm the library's consumer rules suffice; release variant still has no dev tools
- [ ] T061 Owner checkpoint with a license tester on an internal-track build (quickstart.md §4): resubscribe or (with G2) buy monthly → Plus; reinstall → restore; second account → note; declined test card → grace + Fix payment — **deferred: owner's device**

---

## Final

- [ ] T062 Write specs/018-google-play/summary.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 → US1 (Phase 3) → US2 (Phase 4) → US3 (Phase 5); US4 (Phase 6) and US5 (Phase 7) need Phase 2 only (T017) and can follow US1 in either order; Phase 8 after US1–US2; Phase 9 last of the core.
- Phase 10 needs Phases 1–9 **and** the backend gate.
- Phase 11 (G2) needs Phases 1–5 **and** the P-12.3 amendment; independent of Phase 12.
- Phase 12 (G1) needs Phases 1–9 **and** the dependency approval; the final gated phase. T061 also needs Play Console setup; with G2 it can buy in the app, without G2 it tests resubscribe/restore.
- T062 after whichever phases ran.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.
- US2–US6 all edit `PlanContract.kt`/`PlanScreen.kt`: run their UI tasks one after the other.

### Parallel Opportunities

- Phase 1: T001–T004 all [P].
- Phase 2: tests T005–T009 [P]; implementation T011, T012, T014, T015 [P] after T010.
- US1 tests T019/T020 [P]; US2 tests T024/T025 [P]; US4 T031/T032 [P]; US5 T035/T036 [P]; Phase 8 T038–T040 [P]; Phase 9 T042–T045 [P].

## Parallel Example: Phase 2

```text
T005 fake + fixtures | T006 contract | T007 keys | T008 notes | T009 applier
then: T010 port → T011 | T012 → T013 | T014 | T015 ; T016 ; T017 ; T018
```

## Implementation Strategy

### MVP First (User Story 1)

1. Phases 1–3. Stop and validate: an unacknowledged purchase on the fake becomes Plus through the MockEngine; same key across a simulated process death; no acknowledge.

### Incremental Delivery

1. US1 → US2 (brief checkpoint "reinstall, restore") → US3 → US4 → US5 → dev tools/E2E assets → polish.
2. Live QA when backend spec 024 lands.
3. G2 when P-12.3 is amended; G1 when the dependency is approved — G1 is what makes the feature real on a phone; until then the shipped Android build reports Google Play unavailable.

### Task counts

- Total: **62** (T001–T062).
- Core, no gate: T001–T048 (**48**; T041 deferred to the owner's device).
- Gated on backend spec 024 built: T049–T050 (**2**).
- Gated on the P-12.3 amendment (G2): T051–T054 (**4**; T054 deferred device run).
- **Gated on the Play Billing dependency approval (G1): T055–T061 (7**; T061 owner device check).
- Final: T062 (parent writes the summary).
