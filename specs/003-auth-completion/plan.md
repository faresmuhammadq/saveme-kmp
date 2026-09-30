# Implementation Plan: Auth Completion (Register, Verify, Reset)

**Branch**: `003-auth-completion` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-auth-completion/spec.md`

## Summary

Build the registration, email verification and password recovery flows that spec 001 specified
but never built. Registration adds a new screen, a "check your inbox" confirmation, and an
"already registered" branch for the backend's 409. Verification adds a deep-link screen, a
persistent verify prompt with a throttled "Resend email" on Home, and a verify prompt for any
`EMAIL_NOT_VERIFIED` refusal. Recovery adds forgot and reset screens; after a reset the server ends the
account's sessions and the existing 401 handling signs the phone out.

Links arrive through Android deep links (`singleTask` activity → existing `DeepLinkBus` →
`AppNavHost`) and the dev-only Paste link screen, and are parsed against the environment's link
host and port. Most of the design is 001's (tasks T082–T104); the deltas are the 409 branch
(R-02), confirmed link paths plus a dev port (R-03), a simpler manifest layout (R-04), a 30 s
resend cooldown (R-06), and E2E seeding of real links through the backend (R-07).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only: Compose Multiplatform 1.12.1 + Material 3, MVI base (`MviViewModel`), Navigation 3, Ktor 3.6.0, kotlinx.serialization, coroutines, Koin 4.2.2, Kermit. No new dependencies.

**Storage**: none added. The existing account snapshot's verified flag is updated; a reset clears the existing Keystore credential and snapshot.

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor MockEngine contract tests (`AuthTestEnv`), Roborazzi screenshots (new `AuthScreensScreenshotTest`), Maestro E2E via `/speckit-e2e`.

**Target Platform**: Android minSdk 24 / targetSdk 37 (live API); iOS compiles (no live sign-in, spec 001).

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: every new screen interactive in < 100 ms after navigation; one network round trip per submit.

**Constraints**:
- No automatic retry of any auth POST (001 R-04).
- The link secret is never persisted or logged (FR-009).
- Uniform answers everywhere except registration (P-09.4, v1.2.0).
- Cleartext link filter in the dev flavor only (P-09.3).
- EN + AR with RTL.

**Scale/Scope**: 6 new screens + Home prompt, 5 endpoints, 1 new error code, ~45 new string keys × 2 languages, 17 E2E scenarios.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: P-09 rule 4 was **FAIL (non-negotiable)**. Spec FR-005 reveals that an address already has an account, following the backend's 409. It was resolved before design by the owner's decision (2026-09-29): amend P-09.4 (v1.1.0 → v1.2.0) to mirror the backend security baseline's recorded exception for registration.

**Post-design (after Phase 1)**: evidence cites Phase 1 artifacts.

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Glossary terms User, Profile, Device Session; UI says "link", never "token" (spec §2, contracts/ui-and-e2e.md Copy) |
| P-02 API contract fidelity | PASS | DTOs mirror identity.yaml with `display_name` omitted when blank (data-model §2); outcomes branch on `code` (contracts/identity-api-client.md); no retry of any operation here (R-01, 001 R-04) |
| P-03 Money | N-A | Handles no money |
| P-04 Offline-first | N-A | Auth and account data are exempt (rule 6); no syncable entity touched |
| P-05 Time | PASS | Registration sends the device's IANA timezone (`ClientInfo.timezoneId()`); no timestamps displayed |
| P-06 Localisation & RTL | PASS | All copy in `values/` + `values-ar/` (contracts/ui-and-e2e.md); screenshots in AR RTL (R-08) |
| P-07 Design system only | PASS | New screens use existing components only (`AppTextField`, `PasswordField`, `PrimaryButton`, `InlineAlert`, `AppTextButton`, `AppScreen`); no new components needed |
| P-08 Accessibility | PASS | Test tags on every control (R-08); 2× font screenshots; alerts carry text, not colour only |
| P-09 Security & privacy | PASS | Link secret only in ViewModel state, never stored or logged (data-model §4, deep-links.md Rules); the dev Paste link field may show a pasted link (rule 2 exemption, v1.3.0; FR-008); cleartext `http` filter in the dev manifest only (R-04); uniform forgot-password answer (ForgotOutcome.Sent for any address); registration disclosure is the rule 4 exception (v1.2.0, R-02); after a reset the server ends sessions and the existing 401 handling signs the phone out (rule 5, FR-019) |
| P-10 Shared-first architecture | PASS | Parser, use cases, screens in `commonMain`; intent forwarding only in `androidApp` `MainActivity`; `core.navigation.DeepLinkBus` stays feature-free, parsing lives in `feature.auth.domain` |
| P-11 Naming | PASS | Screens and keys follow existing patterns (`RegisterKey`, `VerifyEmailViewModel`, `AuthLinkParser`) |
| P-12 Entitlements & billing | N-A | No gated capability |
| P-13 Observability | PASS | Correlation id per request unchanged; generic errors show `request_id` |
| P-14 Quality gates | PASS | Contract test per operation and handled code (contracts/identity-api-client.md); invariant tests per spec §4; screenshots per new screen; E2E per scenario (quickstart §2) |
| P-15 Scope guard | PASS | Google sign-in, OTP and 2FA excluded (spec Assumptions) |
| P-16 MVI presentation | PASS | Each new screen has Contract + pure `reduce` + ViewModel on `MviViewModel` + Route + stateless Screen (data-model §4) |

No FAIL after the amendment, so the plan is not blocked. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-auth-completion/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── identity-api-client.md
│   ├── deep-links.md
│   └── ui-and-e2e.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
androidApp/
├── src/main/AndroidManifest.xml        # singleTask + https link filter (${linkHost})
├── src/dev/AndroidManifest.xml         # + http link filter (${linkHost}, ${linkPort})
└── src/main/kotlin/com/fares/saveme/
    ├── MainActivity.kt                 # forward intent data to DeepLinkBus (onCreate, onNewIntent)
    └── MainApplication.kt              # AppConfig.linkPort from BuildConfig
shared/src/
├── commonMain/kotlin/com/fares/saveme/
│   ├── core/config/AppConfig.kt        # + linkPort
│   ├── core/network/ApiErrorCode.kt    # + EMAIL_ALREADY_REGISTERED
│   ├── feature/auth/
│   │   ├── data/                       # IdentityApi + register/verify/resend/forgot/reset; dto/ additions
│   │   ├── domain/                     # AuthLinkParser; usecase/Register…, VerifyEmail…, Resend…, ForgotPassword…, ResetPassword…; model/Outcomes.kt
│   │   ├── ui/register/, checkinbox/, verify/, password/forgot/, password/reset/
│   │   ├── ui/home/                    # verify prompt + resend
│   │   ├── ui/AuthErrorCopy.kt         # + EMAIL_ALREADY_REGISTERED copy
│   │   └── di/AuthModule.kt
│   ├── devtools/pastelink/             # PasteLink screen (dev only)
│   └── navigation/                     # Routes (+6 keys), AppNavHost (DeepLinkBus collector, EmailNotVerified sheet, placeholders wired)
├── commonTest/kotlin/com/fares/saveme/feature/auth/…   # contract, parser, reducer, ViewModel tests
└── androidHostTest/…/feature/auth/AuthScreensScreenshotTest.kt
.maestro/003-auth-completion/            # E2E flows (via /speckit-e2e)
.specify/scripts/bash/run-e2e.sh         # + verify-link / reset-link seeds (R-07)
```

**Structure Decision**: Stay in the single `:shared` module and the existing `feature.auth`
package (spec 001 R-01). The Paste link screen lives in `devtools`, next to the Dev tools screen
that opens it. Parsing a link is auth domain logic; `core.navigation.DeepLinkBus` only carries
raw strings, so `core` still imports no feature (P-10.7).

## Implementation phases (input to /speckit-tasks)

1. **Foundation**
   - `EMAIL_ALREADY_REGISTERED` code + copy
   - `AppConfig.linkPort` + BuildConfig
   - DTOs and the five `IdentityApi` methods with contract tests
   - `AuthLinkParser` + tests
   - navigation keys
   - deep-link intake (`MainActivity`, manifests, `AppNavHost` collector)
2. **US1 Register**: use case, Register and CheckInbox screens, wiring from Entry and Sign in, already-registered branch.
3. **US2 Verify**:
   - VerifyEmail screen and use case
   - Home verify prompt with resend and cooldown
   - `EMAIL_NOT_VERIFIED` sheet
   - Paste link screen
4. **US3 Reset**:
   - ForgotPassword screen (from Sign in and the Locked banner)
   - ResetPassword screen and use case
   - local sign-out on success
5. **E2E**:
   - `run-e2e.sh` link seeds (R-07)
   - `/speckit-e2e 003` generates and runs the 17 flows
   - replace spec 001's 12 `not-built` rows in its report
6. **Polish**: Arabic copy review, screenshots, quickstart run, update spec 001's tasks T082–T104 as done here.

## External dependencies (not in this repo)

- Optional: the backend dev `.env` sets `IDENTITY_VERIFY_URL` / `IDENTITY_RESET_URL` to `http://localhost:43871/…` so tapped mail links open the dev app (R-05). E2E doesn't need it (R-07).
- Staging/prod `assetlinks.json` before release (001 dependency, unchanged).

## Complexity Tracking

None.
