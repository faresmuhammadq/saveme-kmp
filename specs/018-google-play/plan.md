# Implementation Plan: Google Play Billing (client)

**Branch**: `018-google-play` | **Date**: 2026-10-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/018-google-play/spec.md`

## Summary

Every Google Play purchase for this app reaches the person's account through one pipe: a
`PlayPurchaseVerifier` in `feature.billing.domain.play` lists Google Play's purchases (through a
narrow `PlayBillingClient` port) on sign-in, app start, foreground, Plan screen open/refresh and
each purchase update, and sends each **unacknowledged** one to `POST /billing/google/purchases`
with an `Idempotency-Key` derived one-way from account, purchase and Google Play's state — stable
across retries and process death, nothing stored. The answer's entitlements and subscription are
applied through one `BillingAnswerApplier` (extracted from spec 015) and C3's refresh follows; a
pending answer is never granted; `PURCHASE_ALREADY_LINKED` / `PURCHASE_NOT_VERIFIED` are not
retried; `503`/`429`/in-progress retry with the same key. "Restore purchases" (Plan screen, and
silently after a real sign-in when Google Play has purchases) sends all owned purchases to
`POST /billing/google/restore` in batches of 25 and reports a combined result. The Plan screen
gains Google Play's own prices (micro-units → integer minor units), "Manage in Google Play" and
"Fix payment" links to Google Play's subscription centre, and memory-only notes (pending, another
account, hold/paused, test). The app never acknowledges and never grants locally. No Room change.

Gates (research R-1, R-11):

- **Core (phases 1–9)**: buildable and testable now with a fake port; ships with an
  `UnavailablePlayBillingClient` on Android until G1 and on iOS for good.
- **Live QA (phase 10)**: when backend spec 024 is built and committed.
- **G2 — Subscribe in the app (phase 11)**: blocked on the owner's P-12.3 amendment; built behind
  `AppConfig.inAppSubscribe = false`; no dependency needed.
- **G1 — Play Billing wiring (phase 12, final)**: blocked on the owner's approval of
  `com.android.billingclient:billing-ktx` (P-14.5) and on Play Console setup.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only for phases 1–11 (Compose Multiplatform 1.12.1, MVI
base, Ktor 3.6.0, kotlinx.serialization, coroutines, Koin 4.2.2, Kermit). **Phase 12 (G1) adds
`com.android.billingclient:billing-ktx` to `:shared` androidMain — NOT APPROVED; owner decision
required before that phase starts.** SHA-256 through platform APIs (`MessageDigest`, CommonCrypto).

**Storage**: none new (Room stays v10). Answers update the account snapshot and the
`billing_subscription` key-value record (spec 005). Notes, prices and the account reference are
memory only.

**Testing**: kotlin-test, coroutines-test (virtual time for backoff), Turbine, Ktor `MockEngine`
contract tests from `google-play.yaml` v1.15 examples, `FakePlayBillingClient` in `commonTest`;
Roborazzi, layering, string, logging scans and `AppGraphTest` in `androidHostTest`; captured live
answers after backend spec 024 is built. No emulator, Maestro or device run (owner policy); Maestro
flows written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles (port unavailable).

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: SC-001 — plan shown within 5 s of a trigger when the server is healthy
(one Google Play list + one verify round trip; backend p95 3 s); a pass never blocks the UI thread;
product details at most once a minute.

**Constraints**: P-12.3 (two billing actions; Subscribe switched off), backend "client never
acknowledges", P-09.2 (tokens, keys, references, prices never logged or stored), P-02.6 (retry
classes, `Retry-After`), budgets 20 verifies/h and 10 restores/h per person, P-10.2/P-10.6 (port in
`data`, plain types; no Android type in common code).

**Scale/Scope**: 1 port + 2 implementations (+1 dev, +1 gated), 3 API methods + 7 DTOs, 2 error
codes, key derivation, verifier, restore service, notes holder, applier extraction, price mapping,
Plan screen state/intents/effects, ~25 strings EN/AR, `AppConfig` +2 fields, `sha256` expect/actual,
Dev tools section, 7 Maestro flows (deferred).

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

### Pre-design (before Phase 0)

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Plan, Subscription, billing period from the glossary; "purchase" only for Google Play (glossary ban table allows it); no "token" in copy (spec §2) |
| P-02 API contract fidelity | PASS | Bound to `google-play.yaml` v1.15; DTOs carry only declared members; branch on `code`; pending read from `purchase.state`, not status; retries per catalogue class |
| P-03 Money | PASS | Play price micros (`Long`) → integer minor units via currency digits; no `Double` (R-10) |
| P-04 Offline-first | N-A | No syncable entity: billing answers are account data (P-04.6); online-only by contract |
| P-05 Time | PASS | `current_period_end`, grace end as `Instant`; shown in the profile zone (existing Plan screen) |
| P-06 Localisation & RTL | PASS | All copy EN/AR (contracts/ui-and-e2e.md); Western digits from the app formatter, not Play's string |
| P-07 Design system only | PASS | Existing `InlineAlert`, buttons, `PlanCard`, snackbar; no new component |
| P-08 Accessibility | PASS | Alerts carry text and icon; 48 dp; 200 % goldens |
| P-09 Security & privacy | PASS | Token/key/reference redacted value classes; nothing persisted; log scan test (FR-050) |
| P-10 Shared-first | PASS | Port in `billing.data.play` with plain types; logic in commonMain; Android adapter in androidMain (G1); `sha256` expect/actual |
| P-11 Naming | PASS | No codename in new identifiers (`PlayBillingClient`, `PlayPurchaseVerifier`, …) |
| P-12 Entitlements & billing | PASS | Rule 1: capabilities via C3 gate. Rule 2: purchases only through Google Play Billing. Rule 3: billing actions = Restore + Enter code; Subscribe gated off pending amendment (R-1); "Manage in Google Play" / "Fix payment" read as links to Google Play's own subscription centre that start, change or record no payment in the app (the ADR-0026 reasoning), **flagged for owner confirmation** (spec §9 Q1) — removing them is two buttons and their tests. Rule 4: no InstaPay |
| P-13 Observability | PASS | Fixed log events with codes; request id on tapped generic errors; correlation id via existing client |
| P-14 Quality gates | PASS (core) / **BLOCKED (G1)** | Core: contract tests per code, invariant tests, screenshot tests, no new dependency. G1 needs the owner's dependency approval — the phase does not start without it (rule 5) |
| P-15 Scope guard | PASS | No iOS billing; no web checkout; refunds handled by the server |
| P-16 MVI presentation | PASS | Plan screen keeps one store; new intents/effects; pure reducer; `OpenUrl` as an effect; no platform object in the store |

### Post-design (after Phase 1)

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 | PASS | Copy table (contracts/ui-and-e2e.md) checked against the glossary; string scan bans "token", "InstaPay", "acknowledge", "order" |
| P-02 | PASS | contracts/google-play-api-client.md: request shape test, every example decoded, one test per handled code; `IDEMPOTENCY_KEY_*` handled |
| P-03 | PASS | `PlayPrices.toMoney` exact-only conversion; `CataloguePlan.price` no longer from `amount_minor` |
| P-04 | N-A | No Room change (R-14); nothing queued in the outbox |
| P-05 | PASS | Grace end formatted with the profile zone (FR-031) |
| P-06 | PASS | ~25 keys EN/AR; RTL goldens |
| P-07 | PASS | Layout uses existing components only |
| P-08 | PASS | Tags and roles listed; 200 % goldens for each new alert |
| P-09 | PASS | `PurchaseToken`, `AccountReference` value classes; keys derived and dropped; logging capture test |
| P-10 | PASS | Layering test extended: `billing.data.play` imports no domain; no `android.`/`com.android.billingclient` import in commonMain; `core` gains only `sha256` and `AppConfig` fields (no feature import) |
| P-11 | PASS | — |
| P-12 | PASS | Plan screen state test: actions = Restore + Enter code; Subscribe absent while `inAppSubscribe = false` (switch-off test) |
| P-13 | PASS | `Logger` events: `play_pass outcome=… count=…`; no identifiers beyond request id |
| P-14 | PASS (core) / **BLOCKED (G1)** | As pre-design; G1 tasks carry the approval gate |
| P-15 | PASS | iOS binds `UnavailablePlayBillingClient(false)`; Restore hidden |
| P-16 | PASS | Reducer/store tests for every new intent and change |

## Project Structure

### Documentation (this feature)

```text
specs/018-google-play/
├── spec.md
├── plan.md                      # this file
├── research.md                  # R-1 … R-15
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── google-play-api-client.md
│   ├── play-billing-port.md
│   └── ui-and-e2e.md
├── checklists/
│   ├── requirements.md
│   └── billing.md               # speckit-checklist
├── tasks.md                     # speckit-tasks
└── summary.md                   # written by the parent after implementation
```

### Source Code (repository root)

```text
shared/src/commonMain/kotlin/com/fares/saveme/
├── core/
│   ├── config/AppConfig.kt                      # + applicationId, inAppSubscribe (default false)
│   ├── network/ApiErrorCode.kt                  # + PURCHASE_ALREADY_LINKED, PURCHASE_NOT_VERIFIED
│   └── platform/Sha256.kt                       # expect fun sha256(ByteArray): ByteArray
├── feature/billing/
│   ├── data/
│   │   ├── BillingApi.kt                        # + verifyPlayPurchase, restorePlayPurchases, playAccountReference
│   │   ├── dto/GooglePlayDtos.kt
│   │   └── play/
│   │       ├── PlayBillingClient.kt             # port + plain types
│   │       └── UnavailablePlayBillingClient.kt
│   ├── domain/
│   │   ├── BillingAnswerApplier.kt              # extracted from RedeemService
│   │   ├── BillingSyncCoordinator.kt            # + purchase pass triggers, sign-in restore, update collection
│   │   ├── model/{Subscription,CataloguePlan}.kt  # + provider, playProductId; price no longer from catalogue
│   │   └── play/
│   │       ├── PlayKeys.kt
│   │       ├── PlayBillingNotes.kt
│   │       ├── PlayPurchaseVerifier.kt
│   │       ├── PlayRestoreService.kt
│   │       ├── PlayPrices.kt
│   │       ├── PlayStoreLinks.kt
│   │       └── PlaySubscribeFlow.kt             # G2
│   ├── ui/plan/{PlanContract,PlanViewModel,PlanScreen,PlanRoute}.kt
│   └── di/BillingModule.kt
├── devtools/playbilling/                        # dev flavour only
│   ├── ScriptedPlayBillingClient.kt
│   └── PlayBillingDevSection.kt (+ contract/store)
shared/src/androidMain/kotlin/com/fares/saveme/
├── core/platform/Sha256.android.kt
├── core/di/PlatformModule.android.kt            # binds PlayBillingClient (Unavailable now; Google in G1)
├── core/platform/CurrentActivityHolder.kt       # G1
└── feature/billing/data/play/GooglePlayBillingClient.kt   # G1
shared/src/iosMain/kotlin/com/fares/saveme/core/platform/Sha256.ios.kt
shared/src/iosMain/kotlin/com/fares/saveme/core/di/PlatformModule.ios.kt   # binds Unavailable(false)
shared/src/commonMain/composeResources/values{,-ar}/strings.xml
androidApp/src/main/kotlin/com/fares/saveme/{MainApplication,MainActivity}.kt   # applicationId into AppConfig; G1: activity holder
shared/src/commonTest/kotlin/com/fares/saveme/feature/billing/
├── play/FakePlayBillingClient.kt, GooglePlayFixtures.kt
├── data/GooglePlayContractTest.kt
├── domain/play/{PlayKeysTest,PlayPurchaseVerifierTest,PlayRestoreServiceTest,PlayBillingNotesTest,PlayPricesTest,PlayStoreLinksTest,PlaySubscribeFlowTest}.kt
├── domain/BillingAnswerApplierTest.kt
└── ui/plan/{PlanReducerTest,PlanViewModelTest}.kt   # extended
shared/src/androidHostTest/kotlin/com/fares/saveme/feature/billing/
├── BillingLayeringTest.kt (extended), GooglePlayStringsTest.kt, GooglePlayLoggingTest.kt, PlanScreenScreenshotTest.kt (extended)
└── data/play/GooglePlayErrorMappingTest.kt      # G1
.maestro/018-google-play/*.yaml                  # deferred
.specify/scripts/bash/run-e2e.sh                 # + fake-script seeds
```

**Structure Decision**: everything lives in the existing `feature.billing` context (spec 005/015);
the port sits in `data` because it is an external data source and must not import `domain` (P-10.6);
domain services orchestrate it. `devtools` keeps the scripted client out of release builds. `core`
gains only context-free pieces (`sha256`, two config fields, two error codes).

**Koin**: the verifier and restore service are `single`s in `billingModule`; the coordinator
(already `createdAtStart`) gains them as constructor parameters and collects
`PlayBillingClient.purchaseUpdates` in `start()`. No cycle: neither depends on the sync engine or on
`BillingSyncCoordinator`; `AppGraphTest` resolves `PlayPurchaseVerifier` and `PlayBillingClient` in
the real graph (the 006 Koin-cycle lesson). The port is bound in each platform module.

## Phases and gates

| Phase | Content | Gate |
|---|---|---|
| 1 Setup | error codes, `sha256`, `AppConfig` fields, strings | none |
| 2 Foundational | port + unavailable + fake, DTOs, API, contract tests, keys, notes, applier extraction, Koin, `AppGraphTest` | none |
| 3 US1 | verifier pipeline + coordinator triggers | none |
| 4 US2 | restore service + Plan action and result | none |
| 5 US3 | another-account handling and note | none |
| 6 US4 | provider, links, problem/pending/test notes | none |
| 7 US5 | prices | none |
| 8 Dev & E2E assets | scripted client, Dev tools section, Maestro flows (deferred), `run-e2e.sh` | none |
| 9 Polish | scans, goldens, README/briefs, gates run by the implementer | none |
| 10 Live QA | captured answers, replay test | backend spec 024 built and committed |
| 11 G2 Subscribe | flow, eligibility, account reference, switch | **owner amends P-12.3** |
| 12 G1 Play Billing wiring | dependency, adapter, activity holder, binding, owner device check | **owner approves the dependency + Play Console setup** |

## What the owner must approve or supply

1. **P-12.3 amendment** (and backend P-10.2, ADR-0026) to allow a Subscribe action that only opens Google Play's sheet — unblocks G2.
2. **Dependency approval**: `com.android.billingclient:billing-ktx` (current stable major at wiring time) in `:shared` androidMain — unblocks G1.
3. **Play Console**: the app on an internal testing track with Google Play app signing (same application id); subscriptions `plus_monthly` and `plus_yearly`, one auto-renewing base plan each, prices; license testers; and backend spec 024 §9 Q1 (service account with Play Console access, RTDN Pub/Sub topic and authenticated push subscription, reference prices for the seeder).
4. **Arabic copy review** for ~25 strings.
5. **Backend gaps** (non-blocking): hold/paused and test not readable from `/me/subscription`; ADR-0026 to write; catalogue codes to add.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| G1 needs a new dependency (P-14.5 / Quality Gate 6) | Google Play Billing cannot be reached without the Play Billing Library | Isolated behind the port; the phase is gated and does not start without approval, so the plan itself does not break the rule |
| US6 code exists while P-12.3 forbids its UI | The brief's headline; cheap to build and test against the fake now | Shipped switched off with a test proving no Subscribe is reachable; turning it on requires the amendment |

## Implementation notes (core, T001-T048)

- The port (`feature.billing.data.play.PlayBillingClient`) has no acknowledge/consume member; `UnavailablePlayBillingClient` is bound on Android (`platformSupported = true`) and iOS (`false`); the dev flavour binds `SwitchingPlayBillingClient(real, scripted)` through `devOrRealPlayClient()`. No dependency was added (`gradle/libs.versions.toml` and the build files are unchanged); `devDebugRuntimeClasspath` has no billingclient artifact.
- `PlayBillingCleaner` (a `UserDataCleaner`) resolves the verifier lazily so the Koin graph has no cycle (`AppGraphTest`).
- `AppConfig.inAppSubscribe` stays `false` in every flavour; no Subscribe state, intent or button exists (G2 not started).
- Catalogue prices are no longer shown (FR-037): the Plan goldens that showed them changed on purpose.
- Maestro flows (`.maestro/018-google-play/`, not run) and the `run-e2e.sh` seeds (`+play-subscription`, `# e2e-play-script:`) were written; `api.js` gained `playVerify` and `playAccountReference`.
