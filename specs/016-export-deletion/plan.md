# Implementation Plan: Export & Deletion Completion (client)

**Branch**: `016-export-deletion` | **Date**: 2026-10-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/016-export-deletion/spec.md`

## Summary

Two additions to the existing `feature.account` context (backend Identity), both online-only and
with no Room change:

- **Exports.** Account → "Export my data" opens an Exports screen (cursor-paged list of the last
  30 days); "New export" opens a request screen (everything, or one wallet; wallet detail opens it
  preset to that wallet for any member role); the export's screen reads `GET /exports/{id}` every
  5 s while queued/running and in the foreground, stops on a final state, background, offline,
  sign-out or after 10 minutes ("we'll email you"), and on "Open download" reads a fresh
  15-minute link and hands it to the system browser — the link is never stored, shown, shared or
  logged, and the app never writes the file. `EXPORT_IN_PROGRESS` (new `ApiErrorCode`) opens the
  export in `current`; `RATE_LIMITED` blocks the request until the wait passes. Not gated by plan,
  `export.enabled` or verification (spec 022 A-5 supersedes the brief).
- **Deletion completion.** The spec 002 Delete account flow gains: an extended explanation (shared
  wallets pass on, solo wallets are deleted, "a former member", Google Play is not cancelled,
  export first), a plan read (`GET /account/deletion-request`) required before the password, a
  "New owners" step (choice among the wallet's members on the phone, server's suggestion
  preselected) for wallets with `choice_expected`, a review, the request with `successors`, a
  scheduled step with the new owners, and a pending view (plan, change owners with the password,
  export, cancel). `purging` has no UI: the ended session signs out and wipes as today.
  Transactions and recurring rules name a departed author "a former member".

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, lifecycle-compose,
MVI base, Ktor 3.6.0, kotlinx.serialization, coroutines, Koin 4.2.2, Navigation 3, Kermit).
**No new dependency.**

**Storage**: none new; Room stays v10. Exports, links and the deletion plan live in store state
(memory) only; the account snapshot's state/date is updated through the existing
`SessionManager.updateDeletion`.

**Testing**: kotlin-test, coroutines-test (virtual time for polling), Turbine, Ktor `MockEngine`
contract tests from the `exports.yaml`/`identity.yaml` examples, fakes in `commonTest`;
string-scan, layering, log-capture and Roborazzi (EN LTR, AR RTL, 2.0) in `androidHostTest`;
`AppGraphTest` extended; captured live-backend answers after spec 022 is committed. No emulator,
Maestro or device run (owner policy); Maestro flows written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles (`LocalUriHandler` is common).

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: at most one status read per 5 s per visible export screen; at most 120 reads
per visit; screens render from memory instantly after each answer.

**Constraints**: P-09.2 (download link never stored/logged/shown/shared; no file in app storage),
P-02.4/P-06.5 (copy by code), P-02.6 (no automatic retry), P-04.6 (account data online-only),
P-12.1 (no entitlement read — export is not gated), P-10.4/10.6 (account reaches wallets,
transactions, recurring and auth only through their `domain`; data takes plain strings/DTOs and
never imports domain), sync core untouched (no feature type in `core.sync`), Koin: no eager
resolution of `SyncEngine` from new definitions (the spec 006 `AppGraphTest` lesson).

**Scale/Scope**: 3 export API calls + 2 deletion API changes, ~8 DTOs, 2 domain services + 1 pure
choice builder, 3 new MVI screens + 1 extended flow, wallet-detail and Account entries, Home notice
variant, author mapping in transactions and recurring, 1 error code, ~70 strings EN/AR, 3 nav keys.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails on the spec alone. Exports and the plan are
account data (P-04.6) and stay online-only; the link is a secret under P-09.2; no scope-guard
capability (no XLSX, no background work); no new dependency.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code: `Export`, `ExportService`, `DeletionPlan`, `Successor`, `WalletRole`; UI "export"/"تصدير", "new owner"/"المالك الجديد", "member", "a former member"/"عضو سابق"; string scan rejects "token", "XLSX", "Excel", "encrypt" (R-12) |
| P-02 API contract fidelity | PASS | Shapes from `exports.yaml`/`identity.yaml` v1.13 ([contracts/export-deletion-client.md](contracts/export-deletion-client.md)); request bodies carry only `scope`/`wallet_id` and `password`/`successors`; unknown statuses, failure codes and reasons decode as raw strings; outcomes branch on `code`; no automatic retry (EXPORT_IN_PROGRESS and RATE_LIMITED would allow one; none taken) |
| P-03 Money | N-A | No amount read, stored or shown (export content is never opened by the app) |
| P-04 Offline-first | PASS | Rule 6: exports and the plan are account data, online-only; nothing syncable is written; the "former member" rendering reads Room through existing services |
| P-05 Time | PASS | Instants parsed to `Instant`, shown in the person's time zone; polling cap and Home's "being deleted" use the injected clock |
| P-06 Localisation & RTL | PASS | ~70 strings EN/AR; Western digits for sizes and dates; no server `detail`; screenshots EN LTR, AR RTL, 2.0 |
| P-07 Design system only | PASS | Screens from `AppScreen`, bars, `AppListItem`, `SelectionList`/`SelectionSheet`, `InlineAlert`, buttons, `EmptyState`, progress from `EmptyAndLoading`; no new component (status shown as text + existing progress indicator) |
| P-08 Accessibility | PASS | 48 dp targets; status in words, never colour alone; live-region announcements for status changes and refusals; 200 % screenshots |
| P-09 Security & privacy | PASS | Rule 2: link only in `DownloadLink` (redacted) inside a one-shot Effect, never in State, DB, prefs, logs; `ExportDto.toString` redacted; no file written (scan test); no share/copy; rule 5: 401 → existing session end and wipe |
| P-10 Shared-first architecture | PASS | All in `commonMain`; account ui → account domain → account data; account domain uses wallets/auth domain APIs only; `ExportApi`/`AccountApi` take DTOs/strings (rule 6); no `core` change beyond `ApiErrorCode`; layering test |
| P-11 Naming | PASS | No codename; `ExportsKey`, `ExportRequestKey`, `ExportDetailKey` |
| P-12 Entitlements & billing | PASS | Rule 1 respected by not gating at all (spec 022 A-5); no billing surface; Play notice is copy only |
| P-13 Observability | PASS | Fresh correlation id per request (existing); unexpected failures show the request id; logs carry export id and outcome only |
| P-14 Quality gates | PASS | A test per spec §4 invariant (tasks); contract tests per call and handled code; screenshot tests per new screen/step; no new dependency |
| P-15 Scope guard | PASS | No iOS-specific work; no XLSX, no background download |
| P-16 MVI presentation | PASS | `ExportsContract`/`ExportRequestContract`/`ExportDetailContract` with pure reducers, stores, Routes, stateless Screens; Delete account contract extended; reducer and store tests |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/016-export-deletion/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── export-deletion-client.md   # API binding (requests, answers, codes)
│   ├── account-domain.md           # domain API the stores use
│   └── ui-and-e2e.md               # screens, tags, copy, deferred flows
├── checklists/
│   ├── requirements.md
│   └── export-deletion.md          # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
shared/src/commonMain/kotlin/com/fares/saveme/
├── core/network/ApiErrorCode.kt                          # + EXPORT_IN_PROGRESS
├── core/network/ProblemDetails.kt                        # KDoc: current also on EXPORT_IN_PROGRESS
├── feature/account/data/ExportApi.kt                     # new
├── feature/account/data/AccountApi.kt                    # + deletionPlan(), requestDeletion(password, successors)
├── feature/account/data/dto/ExportDtos.kt                # new (ExportDto redacted toString, ExportRequestDto)
├── feature/account/data/dto/AccountDtos.kt               # + DeletionPlanDto, DeletionSuccessorDto, DeletionRequestDto, SuccessorChoiceDto; DeletionScheduleDto.successors
├── feature/account/domain/export/{Export,ExportService,DownloadLink}.kt
├── feature/account/domain/deletion/{DeletionPlan,DeletionPlanService,SuccessorChoices}.kt
├── feature/account/domain/usecase/DeletionUseCases.kt    # RequestDeletionUseCase(password, successors)
├── feature/account/domain/model/Outcomes.kt              # DeletionOutcome.SuccessorsChanged, Scheduled(date, owners)
├── feature/account/ui/AccountErrorCopy.kt                # + export copy
├── feature/account/ui/account/*                          # + "Export my data" row
├── feature/account/ui/export/list/{ExportsContract,ExportsViewModel,ExportsRoute,ExportsScreen}.kt
├── feature/account/ui/export/request/{ExportRequestContract,ExportRequestViewModel,ExportRequestRoute,ExportRequestScreen}.kt
├── feature/account/ui/export/detail/{ExportDetailContract,ExportDetailViewModel,ExportDetailRoute,ExportDetailScreen}.kt
├── feature/account/ui/export/ExportCopy.kt               # status, failure, size, wait text
├── feature/account/ui/deleteaccount/*                    # new steps, pending view
├── feature/account/di/AccountModule.kt                   # + ExportApi, ExportService, DeletionPlanService, 3 view models
├── feature/auth/ui/AuthErrorCopy.kt                      # + EXPORT_IN_PROGRESS (exhaustive when)
├── feature/auth/ui/home/{HomeContract,HomeViewModel,HomeScreen}.kt   # "being deleted" variant
├── feature/wallets/ui/detail/*                           # + "Export this wallet" + OpenExport effect
├── feature/transactions/domain/{TransactionMapping,model/TransactionView}.kt   # AddedBy.FormerMember
├── feature/transactions/ui/editor/TransactionEditorScreen.kt                   # caption
├── feature/recurring/domain/{RecurringService,model/Views}.kt                  # author resolution
├── feature/recurring/ui/rule/RuleDetailScreen.kt                               # author line
└── navigation/{Routes,AppNavHost}.kt                     # + ExportsKey, ExportRequestKey(walletId?), ExportDetailKey(id)
shared/src/commonMain/composeResources/values{,-ar}/strings.xml   # block <!-- Export & deletion (spec 016) -->
shared/src/commonTest/kotlin/com/fares/saveme/feature/account/…   # DTO, contract, service (watch), choices, plan join, stores, reducers, captured replay
shared/src/commonTest/kotlin/com/fares/saveme/feature/{transactions,recurring}/…   # author mapping
shared/src/androidHostTest/kotlin/com/fares/saveme/…            # screenshots, string scan, layering (no file/share APIs in export code), log capture, manifest /exports, AppGraphTest
.maestro/016-export-deletion/*.yaml                               # deferred
.specify/scripts/bash/run-e2e.sh                                  # + seeds (+shared-owner, purge helper)
```

**Structure Decision**: extend `feature.account` (R-01); additive edits to merge hotspots
(`ApiErrorCode.kt`, `AuthErrorCopy.kt`, `Routes.kt`, `AppNavHost.kt`, both `strings.xml`,
`run-e2e.sh`, wallet detail contract). The parallel C13 branch (spec 014, `../saveme-kmp-014`)
also touches `strings.xml`, `Routes.kt`, `AppNavHost.kt` and possibly `ApiErrorCode.kt`:
append-only blocks, merge last.

## Phase 0 & 1 outputs

- [research.md](research.md): R-01…R-14.
- [data-model.md](data-model.md): DTOs, domain types, state per screen, step machine.
- [contracts/](contracts/): API binding, domain API, UI and E2E.
- [quickstart.md](quickstart.md): host gates, live contract QA, deferred device checks.

## Complexity Tracking

None.

## Implementation notes (filled during implementation)
