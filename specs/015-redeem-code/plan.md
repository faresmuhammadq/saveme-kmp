# Implementation Plan: Redeem Code (client)

**Branch**: `015-redeem-code` | **Date**: 2026-10-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/015-redeem-code/spec.md`

## Summary

The Plan screen's reserved billing place (spec 005) gains "Enter code", opening a new redeem
screen in the existing `feature.billing` context. The person types or pastes the mailed code into
a new design-system `CodeField` that normalises as typed (upper-case, `O`→`0`, `I`/`L`→`1`,
Crockford alphabet only, 16 characters) and shows four LTR groups; the screen is excluded from
screenshots and recents (`SecureWindow`, Android FLAG_SECURE), the field offers paste only and
asks the keyboard not to learn. "Redeem" is gated on the phone's verified flag (existing verify
prompt), then sends `POST /billing/redeem` once with an in-memory attempt identifier as
`Idempotency-Key` (reused on retry of the same code, replaced on edit). Online-only, never
queued, never retried automatically. Success applies the answer's `entitlements` (new
`SessionManager.applyEntitlements`) and `governing` (existing `SubscriptionRepository.save`) for
the same signed-in person, then triggers C3's `refreshAfterBillingAction()`; the confirmation
names the governing plan, explains when an existing subscription still applies, and warns that
a renewing Google Play subscription keeps billing. Every refusal maps by code to its own copy;
three catalogue codes and two idempotency codes join `ApiErrorCode`. No Room change, no new
dependency.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, MVI base, Ktor 3.6.0,
kotlinx.serialization, coroutines, Koin 4.2.2, Navigation 3, Kermit). Android `WindowManager`
flag from the framework. **No new dependency.**

**Storage**: none new. The answer updates the existing account snapshot (entitlements) and the
`billing_subscription` key-value record. The code and attempt identifier are memory-only.

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor `MockEngine` (contract tests from the
`instapay.yaml` examples), fakes in `commonTest`; Robolectric (`SecureWindow` actual), layering and
string-scan tests and Roborazzi in `androidHostTest`; captured live-backend answers
(`CapturedRedeemResponses.kt`). No emulator, Maestro or device run (owner policy); Maestro flows
written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles (`SecureWindow` no-op).

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: typing and pasting update the field within one frame (pure normaliser over
at most a few hundred pasted characters); the confirmation shows within the network round trip
(no extra read before it; the C3 refresh runs after).

**Constraints**: P-09.2 (code and key never logged, stored, printed or put on the clipboard),
P-12.3/12.4 (Enter code only; no claim call; no InstaPay, price or web link), P-02.4/P-06.5
(copy by code), P-02.6 (no automatic retry), backend uniform answers (mismatch never names the
address; unknown and revoked share one message).

**Scale/Scope**: 1 API method + 4 DTOs, 1 pure normaliser, 1 confirmation mapper, 1 service, 1 MVI
screen, 1 design-system component, 2 core additions (`idempotencyKey`, `SecureWindow`), 1 auth
domain method, Plan screen button + effect, nav key, 5 error codes, ~30 strings EN/AR, 4 E2E seeds.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails on the spec alone. "Enter code" is one of the
two billing actions P-12.3 allows; the claim stays web-only (P-12.4); no scope-guard capability
(the app links to no checkout at all); no new dependency.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code: `RedeemCode`, `RedeemService`, `RedeemOutcome`, `Subscription`, `Plan`; UI "code"/"رمز", "Plan"/"خطة", "billing period"; string-scan test rejects "InstaPay", "voucher", "coupon", "promo", "token" (contracts/ui-and-e2e.md) |
| P-02 API contract fidelity | PASS | Shapes from `instapay.yaml` v1.12 (contracts/redeem-api-client.md); body carries only `code`; unknown members ignored; outcomes branch on `code` only (R-08); standard headers plus `Idempotency-Key`; no automatic retry (RATE_LIMITED and IN_PROGRESS would allow one; the feature takes none) |
| P-03 Money | N-A | No amount is read, stored or shown; prices are never shown on this screen (FR-002) |
| P-04 Offline-first | N-A | Nothing syncable: redemption is an account action (rule 6), online-only and never queued (spec assumption 4); the Plan screen still renders from stored records |
| P-05 Time | PASS | Billing-period dates parsed to `Instant`, shown in the profile time zone (as the Plan screen); "starts later" compared against the injected clock |
| P-06 Localisation & RTL | PASS | ~30 strings EN/AR; Western digits; the code forced LTR inside RTL (R-03); no server `detail` shown; screenshots EN LTR, AR RTL, 2.0 |
| P-07 Design system only | PASS | `CodeField` added to `designsystem` first with catalogue entry and screenshots (R-03); the screen uses `AppScreen`, top bar, `InlineAlert`, buttons; no raw tokens |
| P-08 Accessibility | PASS | 48 dp buttons; labelled field; code read as characters; live-region messages; text, not colour, for every state; 200 % screenshots |
| P-09 Security & privacy | PASS | Rule 2: code and attempt in store state only with redacted `toString` (R-10), no persistence, no logs (log-capture test), paste-only field, FLAG_SECURE on the screen (R-04), no clipboard writes; rule 4: mismatch copy shows only the person's own address; rule 5: 401 → existing session end |
| P-10 Shared-first architecture | PASS | All in `commonMain` except the `SecureWindow` actuals (expect/actual, rule 2); billing ui → billing domain → billing data; billing reaches auth only through `SessionManager` (auth domain) and never imports auth `data` (own `RedeemEntitlementsDto`, R-06); `core` additions import no feature (rule 7); layering test |
| P-11 Naming | PASS | No codename in identifiers; `RedeemCodeKey`, `CodeField`, `SecureWindow` |
| P-12 Entitlements & billing | PASS | Rule 1: capabilities after redemption come from the answer's features via the C3 gate; the plan label is display-only. Rule 3: the only new billing action is "Enter code". Rule 4: the claim endpoint is never called (test). Rule 2 unaffected (no purchase) |
| P-13 Observability | PASS | Fresh correlation id per request (existing); unexpected failures and `KeyReused` show the request id; logs carry outcome and request id only |
| P-14 Quality gates | PASS | Each spec §4 invariant has a test (tasks); contract tests for `redeem` and every handled code; screenshot tests for `CodeField` and the redeem screen; no new dependency |
| P-15 Scope guard | PASS | No iOS-specific work (no-op actual); no web checkout; no refunds |
| P-16 MVI presentation | PASS | `RedeemContract.kt` (State/Intent/Effect/Change, pure `reduce`), `RedeemViewModel` (key generation, clock and timers in intent handling), `RedeemRoute`, stateless `RedeemScreen`; reducer and store tests; Plan screen gains one intent and one effect with tests |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/015-redeem-code/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── redeem-api-client.md
│   ├── redeem-domain.md
│   └── ui-and-e2e.md
├── checklists/
│   ├── requirements.md
│   └── redeem.md        # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/src/commonMain/kotlin/com/fares/saveme/
├── core/network/ApiErrorCode.kt                     # + 5 codes
├── core/network/HttpClientFactory.kt                # + HttpRequestBuilder.idempotencyKey()
├── core/platform/SecureWindow.kt                    # expect @Composable fun SecureWindow()
├── designsystem/component/Fields.kt                 # + CodeField (+ code text style token if missing)
├── designsystem/catalog/CatalogSections.kt          # + CodeField entry
├── feature/auth/domain/SessionManager.kt            # + applyEntitlements(...)
├── feature/billing/data/BillingApi.kt               # + redeem(code, attempt)
├── feature/billing/data/dto/RedeemDtos.kt           # request/result/redeemed/entitlements DTOs
├── feature/billing/domain/RedeemCode.kt             # normaliser
├── feature/billing/domain/RedeemService.kt          # + RedeemOutcome, AttemptKey
├── feature/billing/domain/RedeemConfirmation.kt     # mapper (R-07)
├── feature/billing/ui/BillingErrorCopy.kt           # + 3 codes
├── feature/billing/ui/plan/{PlanContract,PlanViewModel,PlanScreen,PlanRoute}.kt   # Enter code
├── feature/billing/ui/redeem/{RedeemContract,RedeemViewModel,RedeemRoute,RedeemScreen}.kt
├── feature/billing/di/BillingModule.kt              # + RedeemService, RedeemViewModel
└── navigation/{Routes,AppNavHost}.kt                # + RedeemCodeKey, OpenRedeem, RequireVerification
shared/src/commonMain/composeResources/values{,-ar}/strings.xml   # block <!-- Redeem code (spec 015) -->
shared/src/androidMain/kotlin/com/fares/saveme/core/platform/SecureWindow.android.kt
shared/src/iosMain/kotlin/com/fares/saveme/core/platform/SecureWindow.ios.kt
shared/src/commonTest/kotlin/com/fares/saveme/feature/billing/…   # normaliser, confirmation, service, contract, live replay, store, reducer
shared/src/androidHostTest/kotlin/com/fares/saveme/…                # screenshots, SecureWindow, string scan, log capture, layering
.maestro/015-redeem-code/*.yaml                                     # deferred
.specify/scripts/bash/run-e2e.sh                                    # + redeem seeds
```

**Structure Decision**: extend `feature.billing` (R-01); additive edits only to merge hotspots
(`ApiErrorCode.kt`, `HttpClientFactory.kt`, `SessionManager.kt`, `Fields.kt`, catalogue,
`Routes.kt`, `AppNavHost.kt`, both `strings.xml`, `run-e2e.sh`). The parallel C13 branch (spec
014) also touches `strings.xml`, `Routes.kt` and `AppNavHost.kt`: append-only blocks.

## Complexity Tracking

None.

## Implementation notes (filled during implementation)

Choices made during implementation (owner away, full delegated authority); each is consistent with spec, plan and research.

1. **Code text style**: no new `code` typography token. Every `AppTypography` style already carries tabular digits (`tnum`), so `CodeField` uses `numeralL` (Latin face, 24 sp) with `TextDirection.Ltr`; this keeps the design-system token set unchanged (research R-03's "only if none exists").
2. **Paste-only menu**: used `Modifier.filterTextContextMenuComponents` (Compose MP 1.12.1, the research R-03 fallback) as the single mechanism instead of a `LocalTextToolbar` wrapper: it is the supported API for the new text context menu and is one line. The predicate (`isAllowedInCodeMenu`) is unit-tested; that the Android system menu honours it is a device spot check (quickstart §3, T051, deferred).
3. **Normalisation of typed and pasted text**: one function, `RedeemCode.fromPaste`, for both (typed text has no separators so it falls through to `normalise`); `CodeField` therefore needs no paste detection and stays free of billing imports. `fromPaste` tries hyphen-separated groups first and space-separated groups second, so ordinary words in a pasted line are never taken for a code.
4. **Message for `EMAIL_NOT_VERIFIED`**: the screen shows the verify note (set by the outcome flipping `verified` to false), not a second alert; the app-wide sheet is raised by `ApiClient`.
5. **`redeem_done_*` wording** follows contracts/ui-and-e2e.md; Arabic minutes use the sentence form (no plural resource), as the contract allowed.
6. **Mismatch copy**: the own address goes on a second line of the same alert (one live region, one `redeem.message` tag).
7. **Error copy tables**: `AuthErrorCopy.message()` is exhaustive over `ApiErrorCode`, so the five new codes were added there too (the three redeem codes and `IDEMPOTENCY_REQUEST_IN_PROGRESS` map to their keys, `IDEMPOTENCY_KEY_REUSED` to the generic reference copy); `BillingErrorCopy.redeemMessage()` covers the three catalogue codes as planned.
8. **Service robustness**: an unreadable 200 body is `Failed(null)` (no crash), request and apply run in one `scope.async` with the exception caught inside.
9. **Layering test**: added a billing-specific `BillingLayeringTest` (the existing layering tests are per feature) covering data -> no auth data/domain, billing -> auth only through domain/ui, core and designsystem importing no feature (the Room registry excepted, as in `SharingLayeringTest`), and no source building the claim URL (that last check lives here, not in `commonTest`, because it reads files).
10. **T048 dry run**: the +redeem-code* seeds are written and `bash -n` clean; per the run instructions neither the runner nor the seeding functions were executed (CHK036/CHK037 stay open for the QA step).
11. **Screenshots**: new goldens for `CodeField` (empty, partial, complete, error x EN/AR/2.0) and the redeem screen (15 states x 3); changed goldens: Plan screen (Enter code row) and the catalogue `fields` section (CodeField entry).

