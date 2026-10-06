---
description: "Task list for 016-export-deletion"
---

# Tasks: Export & Deletion Completion (client)

**Input**: Design documents from `specs/016-export-deletion/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (export-deletion-client.md, account-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `547eed5` (specs 001–015: Account and Delete account of spec 002, wallets/members of 006/013, transactions 008, recurring 010, sync engine 004/009/011)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per screen; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/016-export-deletion/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Host QA replays the calls against the live local backend with `curl` **after backend spec 022 is implemented and committed**, turning real answers into fixtures, as specs 007–015 did.

**Organization**: Phase 2 builds the wire layer, the error code, domain types and navigation keys. US1 (export everything) and US4 (new owners) are the two P1 stories and the brief's checkpoints; US2, US3, US5 (P2) and US6 (P3) follow.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US6 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); account feature `cm/feature/account/…` (`fa/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); account tests `ct/feature/account/…` (`at/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: account ui → account domain → account data; account reaches wallets, transactions, recurring and auth only through their `domain`; `fa/data` never imports `domain`; `core` imports no feature; `core.sync` untouched), naming (P-11).
- **Link secrecy (P-09.2)**: the download URL exists only in `ExportDto` (redacted `toString`), inside `ExportService.openableLink` and in `DownloadLink` (redacted) carried by one Effect; never in State, Room, `KeyValueStore`, logs, crash reports, UI text, clipboard or share sheet. No export code writes a file. Info logs: export id and outcome name only.
- Export and deletion calls are never retried automatically (P-02.6) and never queued; no entitlement or verification check on export (FR-004).
- **MVI screen recipe (P-16)** as specs 001–015: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas/services, `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin**: new definitions never resolve `SyncEngine` eagerly; `AppGraphTest` must stay green.
- **Merge hotspots** (parallel C13 branch): `ApiErrorCode.kt`, `AuthErrorCopy.kt`, `Routes.kt`, `AppNavHost.kt`, `AccountModule.kt`, wallet detail contract, `run-e2e.sh`: additive only. New strings in one append-only block `<!-- Export & deletion (spec 016) -->` at the end of each `strings.xml`.
- Every interactive element and message gets the `testTag` of contracts/ui-and-e2e.md.

---

## Phase 1: Setup

- [X] T001 Rerun `.specify/scripts/bash/check-backend-contract.sh`; if spec 022 has been committed or `exports.yaml`, the `/account/deletion-request` entry or the error catalogue changed since the spec was written, update spec §5 "Contract changes since last spec" and the affected FRs (and the `**Backend contract**` line) before coding
- [X] T002 [P] Add the `<!-- Export & deletion (spec 016) -->` string block to both `strings.xml` files with every key of contracts/ui-and-e2e.md "Copy" (EN and AR, Western digits; `%1$d` plurals as sentence forms, spec 015 note 17)

---

## Phase 2: Foundational (blocking all stories)

### Tests first

- [X] T003 [P] `ct/core/network/ApiErrorCodeTest.kt`: extend — `EXPORT_IN_PROGRESS` is `Automatic` and `from("EXPORT_IN_PROGRESS")` maps (FR-036)
- [X] T004 [P] `at/data/ExportContractTest.kt` (MockEngine, fixtures from the `exports.yaml` examples `queued`, `readyAndExpired`, `running`, `ready`, `failed`, plus one with an unknown status and an unknown member): `POST exports` bodies exactly `{"scope":"account"}` and `{"scope":"wallet","wallet_id":"…"}`; `GET exports?limit=20` and with `cursor`; `GET exports/{id}`; bearer and standard headers present, no `Idempotency-Key`; every example decodes; `ExportDto.toString()` contains no URL; 409 body's `current` decodes as `ExportDto`; 429 with `retry_after_seconds` and with only `Retry-After`
- [X] T005 [P] `at/data/DeletionPlanContractTest.kt` (MockEngine, `identity.yaml` examples `activeWithChoice`, `pendingChosen`; request examples `passwordOnly`, `withChoice`): `GET account/deletion-request` decodes; `POST` body without `successors` is exactly `{"password":"…"}` and with them matches `withChoice`; `DeletionScheduleDto` decodes with and without `successors`; `DeletionRequestDto.toString()` contains no password; 422 with `/successors/0/member_user_id` parses its pointer
- [X] T006 [P] `at/domain/export/ExportMappingTest.kt`: `ExportDto` → `Export` for every status and failure code, unknown values → `Unknown(raw)`; instants parsed; `Export` has no URL member (reflection-free: compile-time by type, plus `toString` check)

### Implementation

- [X] T007 [P] `cm/core/network/ApiErrorCode.kt`: add `EXPORT_IN_PROGRESS(RetryClass.Automatic)` with KDoc (spec 016, FR-036); `cm/core/network/ProblemDetails.kt`: KDoc of `current` names `EXPORT_IN_PROGRESS`; `cm/feature/auth/ui/AuthErrorCopy.kt`: map the new code (exhaustive `when`)
- [X] T008 [P] `fa/data/dto/ExportDtos.kt` (`ExportRequestDto`, `ExportDto` with redacted `toString`) and `fa/data/ExportApi.kt` (`requestExport`, `listExports` via `callPage`, `getExport`) (contracts/export-deletion-client.md)
- [X] T009 [P] `fa/data/dto/AccountDtos.kt`: `DeletionPlanDto`, `DeletionSuccessorDto`, `SuccessorChoiceDto`, `DeletionRequestDto` (redacted `toString`), `DeletionScheduleDto.successors = emptyList()`; `fa/data/AccountApi.kt`: `deletionPlan()`, `requestDeletion(body: DeletionRequestDto)` (callers updated)
- [X] T010 [P] `fa/domain/export/Export.kt` (Export, ExportScope, ExportStatus, ExportFailure, outcomes of data-model) and `fa/domain/export/DownloadLink.kt` (value class, redacted `toString`); mapping functions from the DTOs
- [X] T011 [P] `fa/domain/deletion/DeletionPlan.kt` (DeletionPlan, PlanState, OwnedSharedWallet, SuccessorReason, Candidate, PlanOutcome; `SuccessorChoice`)
- [X] T012 [P] `cm/navigation/Routes.kt`: `ExportsKey`, `ExportRequestKey(walletId: String? = null)`, `ExportDetailKey(exportId: String)`

**Checkpoint**: T003–T006 pass.

---

## Phase 3: User Story 1 - Export all my data and download it (Priority: P1) 🎯 MVP

**Goal**: Account → Export my data → New export → Everything → status polls to Ready → Open download hands a fresh link to the browser.

**Independent Test**: spec US1 Independent Test (live QA quickstart §2.3; flow `US1-export-everything`, deferred).

### Tests first

- [X] T013 [P] [US1] `at/domain/export/ExportServiceTest.kt` (fake `ExportApi`, virtual time): `request` sends one POST per call, never retries; `Created`; offline/timeout → `Offline`; `page` maps items and cursor; `openableLink` makes a fresh GET each call and returns `Open` only for `ready` with a URL, `NotReady(export)` otherwise; no entitlement or verification source is consulted (constructor takes none) (FR-004, FR-007, FR-013)
- [X] T014 [P] [US1] `at/domain/export/ExportWatchTest.kt` (virtual time): first read at once; reads every 5 s while `queued`/`running`; completes after `ready`/`failed`/`expired`/unknown, `NotFound`, `Offline`, `Failed`; emits `StillPreparing` once `now - requestedAt > 10 min` or after 120 reads; cancelling the collector stops reads (FR-010, FR-011, SC-004)
- [X] T015 [P] [US1] `at/ui/export/ExportsReducerTest.kt` + `ExportsViewModelTest.kt`: open loads the first page; "Show more" appends with the cursor; Refresh restarts; `CursorInvalid` restarts once; rows named "Everything" / wallet name / "gone" from the wallets flow; row tap → `OpenDetail(id)`; New export → `OpenRequest(null)`; offline banner; state never holds a URL
- [X] T016 [P] [US1] `at/ui/export/ExportRequestReducerTest.kt` + `ExportRequestViewModelTest.kt`: default scope Account (Wallet when opened with an id); submit disabled while in flight; `Created` → `OpenDetail(id)` replacing the request screen; unsent-changes flag from the sync status (FR-009)
- [X] T017 [P] [US1] `at/ui/export/ExportDetailReducerTest.kt` + `ExportDetailViewModelTest.kt` (virtual time): `Started` starts watching, `Stopped` cancels (no reads after), sign-out cancels; status texts per state; `StillPreparing` stops and "Check again" restarts; Ready → "Open download" calls `openableLink` and emits `OpenDownload(link)` (link never in State; two taps → two reads); `NotReady(expired)` updates to Expired without an effect; `NotReady` with status ready (no link) keeps Ready, shows `export_link_missing` and emits nothing; `NotFound` on any read → "This export is no longer available." and polling stops; `OpenFailed` intent shows the browser message (FR-010–FR-016, FR-021, Edge Cases)

### Implementation

- [X] T018 [US1] `fa/domain/export/ExportService.kt`: `DefaultExportService` (request success/offline paths, page, read, `openableLink`, `watch` with injected clock and delay) — make T013/T014 pass
- [X] T019 [US1] `fa/ui/export/ExportCopy.kt`: status, kind, size (KB rounded up under 1 MB, else MB one decimal), dates in the person's time zone (FR-005, FR-012)
- [X] T020 [US1] `fa/ui/export/list/{ExportsContract,ExportsViewModel,ExportsRoute,ExportsScreen}.kt` (contracts/ui-and-e2e.md tags and copy) — make T015 pass
- [X] T021 [US1] `fa/ui/export/request/{ExportRequestContract,ExportRequestViewModel,ExportRequestRoute,ExportRequestScreen}.kt`: scopes, wallet choice (`server.version > 0`, not hidden, active then archived), explanation `export_explain`, unsent note, submit — make T016 pass
- [X] T022 [US1] `fa/ui/export/detail/{ExportDetailContract,ExportDetailViewModel,ExportDetailRoute,ExportDetailScreen}.kt`: `LifecycleStartEffect` → `Started`/`Stopped`; `OpenDownload` handled with `LocalUriHandler.openUri` in `runCatching` → `OpenFailed` on failure; live-region status — make T017 pass
- [X] T023 [US1] `fa/ui/account/*`: "Export my data" row (`account.export`), `AccountDestination.Exports` + reducer/store tests extended
- [X] T024 [US1] `fa/di/AccountModule.kt`: `ExportApi`, `ExportService`, the three view models (clock, time zone, wallets flow, sync status); `cm/navigation/AppNavHost.kt`: entries for the three keys, Account → `ExportsKey`, `Created` replaces the request entry with `ExportDetailKey`; extend `ht/core/di/AppGraphTest.kt` to resolve the new view models
- [X] T025 [P] [US1] `ht/feature/account/ExportScreensScreenshotTest.kt`: list (rows of every status, empty, offline), request (account, explanation, unsent note), detail (queued, running, ready, expired, still preparing) in EN LTR, AR RTL, 2.0; update the Account golden for the new row
- [X] T026 [P] [US1] `.maestro/016-export-deletion/US1-export-everything.yaml` and `US1-explain.yaml` (deferred header)
- [ ] T027 [US1] Run US1 flows — **deferred: E2E on owner's device**

**Checkpoint**: US1 host tests green; an export requests, polls and opens through the MockEngine.

---

## Phase 4: User Story 4 - Choose who keeps my shared wallets (Priority: P1)

**Goal**: Explanation (extended) → plan → New owners → Review → password → Scheduled with owners.

**Independent Test**: spec US4 Independent Test (live QA quickstart §2.4; flow `US4-new-owners`, deferred).

### Tests first

- [X] T028 [P] [US4] `at/domain/deletion/DeletionPlanServiceTest.kt` (fakes for `AccountApi`, `WalletsService`, `WalletMemberships`, `SessionManager`): joins wallet names (archived included; missing → null); candidates exclude the person, ordered suggested first then by `joinedAt`; a suggestion not on the phone → synthetic `unknown` candidate; `choiceOffered` only with the wallet known and ≥1 real candidate; unknown reason/state → `Unknown`; updates the snapshot's state/date for known states only; offline → `Offline`; one GET per call (FR-024, FR-025)
- [X] T029 [P] [US4] `at/domain/deletion/SuccessorChoicesTest.kt`: one entry per `choiceOffered` wallet using the pick or the suggestion, plan order, at most 100, `null` when none; `only_member` and unknown wallets never included (FR-027)
- [X] T030 [P] [US4] `at/domain/usecase/RequestDeletionUseCaseTest.kt`: extend — sends `successors` when given and omits them when null; `Scheduled(date, owners)` from the answer; 422 with a `/successors…` pointer → `SuccessorsChanged`; `/password` pointer and other codes keep spec 002 outcomes; never retried
- [X] T031 [P] [US4] `at/ui/deleteaccount/DeleteAccountReducerTest.kt` + `DeleteAccountViewModelTest.kt`: extend — Continue → `LoadingPlan` → `NewOwners` (or `Review` when no successor); offline/error → back to Explain with banner and Try again; the password step is unreachable without a loaded plan; picks default to suggestions and change by intent; Review → Confirm; Confirm sends `SuccessorChoices.build(...)`; `SuccessorsChanged` → message, plan re-read, `NewOwners` with picks reset; Back walks the steps; `OpenExports` effect (FR-024–FR-030)

### Implementation

- [X] T032 [US4] `fa/domain/deletion/DeletionPlanService.kt` and `SuccessorChoices.kt` — make T028/T029 pass
- [X] T033 [US4] `fa/domain/usecase/DeletionUseCases.kt` + `fa/domain/model/Outcomes.kt`: `RequestDeletionUseCase(password, successors)`, `Scheduled(date, owners)`, `SuccessorsChanged` — make T030 pass
- [X] T034 [US4] `fa/ui/deleteaccount/DeleteAccountContract.kt` + `DeleteAccountViewModel.kt`: the step machine of data-model (Explain, LoadingPlan, NewOwners, Review, Confirm, Scheduled) — make T031 pass
- [X] T035 [US4] `fa/ui/deleteaccount/DeleteAccountScreen.kt` + `DeleteAccountRoute.kt`: extended explanation (`delete_explain_shared`, `delete_play_notice`, `delete_export_first`), loading, new owners (fixed rows, radio choices with name/role/joined date and "Suggested", unknown wallet/member copy), review (`delete_after_30_days`, owners, rest note, Play notice), scheduled with owners; `OpenExports` → `ExportsKey` in `AppNavHost`
- [X] T036 [P] [US4] Screenshots: explanation, loading, new owners (fixed + choice + unknown), review, scheduled with owners, successors changed — EN LTR, AR RTL, 2.0 (update the spec 002 explanation goldens)
- [X] T037 [P] [US4] `.maestro/016-export-deletion/US4-new-owners.yaml` (seed `+shared-owner`)
- [ ] T038 [US4] Run US4 flow — **deferred: E2E on owner's device**

---

## Phase 5: User Story 2 - Export one wallet (Priority: P2)

**Goal**: Wallet detail → Export this wallet → request preset → status.

**Independent Test**: spec US2 Independent Test (flow `US2-export-wallet`, deferred).

- [X] T039 [P] [US2] `ct/feature/wallets/ui/detail/WalletDetailReducerTest.kt` + `WalletDetailViewModelTest.kt`: extend — `exportable` true for every role and archived wallets with `version > 0`, false for `version == 0`; `ExportClicked` → `OpenExport(walletId)` (FR-002)
- [X] T040 [P] [US2] `at/domain/export/ExportServiceTest.kt` + `ExportRequestViewModelTest.kt`: extend — wallet request body; 404 → `WalletGone` → "You no longer have this wallet."; detail of a `wallet_unavailable` failure shows its copy; a wallet export of a wallet the phone lacks is named "gone" (US2-3…5)
- [X] T041 [US2] `cm/feature/wallets/ui/detail/*`: "Export this wallet" action (`walletDetail.export`), intent/effect; `AppNavHost`: `OpenExport` → `ExportRequestKey(walletId)`; `fa` request/service: `WalletGone` path and copy
- [X] T042 [P] [US2] Screenshots: wallet detail with the action (update goldens), request with a wallet chosen, wallet-gone message, failed `wallet_unavailable` — EN LTR, AR RTL, 2.0
- [X] T043 [P] [US2] `.maestro/016-export-deletion/US2-export-wallet.yaml` (viewer seed `+partner`)
- [ ] T044 [US2] Run US2 flow — **deferred: E2E on owner's device**

---

## Phase 6: User Story 3 - Why an export can't start or didn't finish (Priority: P2)

**Goal**: in-progress conflict opens `current`; rate limit blocks with a wait; failures retry; offline everywhere.

**Independent Test**: spec US3 Independent Test (live QA §2.3; flows `US3-*`, deferred).

### Tests first

- [X] T045 [P] [US3] `at/domain/export/ExportServiceRefusalTest.kt`: 409 with `current` → `InProgress(export)`; 409 without/with unreadable `current` → `InProgress(null)`; 429 with `retry_after_seconds`, with only `Retry-After`, with neither → `RateLimited(s?)`; 404 → `WalletGone`; 422/400/406/415/500 → `Failed(code, requestId)`; 401/426 left to the app-wide events; none retried (FR-017–FR-023)
- [X] T046 [P] [US3] `at/ui/export/ExportRequestReducerTest.kt` + `ExportRequestViewModelTest.kt` + `ExportDetailViewModelTest.kt`: extend — `InProgress(id)` message with "See it" → `OpenDetail(id)`, `InProgress(null)` → `OpenExports`; `RateLimited(s)` sets `blockedUntil`, disables submit, re-enables after `s` (virtual time); wait text hours/minutes rounded up and "later"; failed export "Try again" → `OpenRequest(scope preset)`; offline messages with Try again/Check again (FR-020, FR-022)
- [X] T047 [P] [US3] `at/ui/AccountErrorCopyTest.kt`: extend — every export message and failure resolves in EN and AR; unknown failure code uses the generic copy

### Implementation

- [X] T048 [US3] `fa/domain/export/ExportService.kt`: refusal mapping and `current` decoding (research R-04) — make T045 pass
- [X] T049 [US3] `fa/ui/AccountErrorCopy.kt` + request/detail contracts, view models and screens: messages, wait timer (clock/delay in intent handling, never in `reduce`), "See it", "Try again" (`exportDetail.retry`), "Check again" — make T046/T047 pass
- [X] T050 [P] [US3] Screenshots: in progress with See it, rate limited (hours, minutes), failed generic, offline — EN LTR, AR RTL, 2.0
- [X] T051 [P] [US3] `.maestro/016-export-deletion/US3-in-progress.yaml`, `US3-rate-limit.yaml` (seed `+export-limit`)
- [ ] T052 [US3] Run US3 flows — **deferred: E2E on owner's device**

---

## Phase 7: User Story 5 - See and change my pending deletion (Priority: P2)

**Goal**: pending view with date, owners and reasons; change owners with the password; cancel drops choices; "being deleted" after the date; purge → existing sign-out wipe.

**Independent Test**: spec US5 Independent Test (live QA §2.4; flow `US5-pending`, deferred).

### Tests first

- [X] T053 [P] [US5] `at/ui/deleteaccount/DeleteAccountViewModelTest.kt` + reducer: extend — opening while pending → `LoadingPlan` → `Pending(plan)`; plan failure → pending view from the snapshot date with Try again, Export and Cancel, no Change; "Change new owners" only when a wallet offers a choice → NewOwners (changing) → Review → Confirm → Pending with "New owners saved." and the same date; Cancel → spec 002 cancel + "choices dropped" when any reason was `chosen`; a plan with an unknown `state` → pending view with only "Cancel deletion" and a general note (FR-031, Edge Cases)
- [X] T054 [P] [US5] `ct/feature/auth/ui/home/HomeReducerTest.kt` + `HomeViewModelTest.kt`: extend — a minute tick sets `now`; `deletionPendingOn <= now` → "being deleted" text with Cancel kept (FR-032)
- [X] T055 [P] [US5] `ht/feature/account/DeletionPurgeSignOutTest.kt` (real Koin graph, in-memory Room as `AccessLostPurgeTest`): a pending-deletion account whose next request answers 401 `UNAUTHENTICATED` is signed out, every table is empty and no deletion-specific message is set (FR-032, research R-11)

### Implementation

- [X] T056 [US5] `fa/ui/deleteaccount/*`: `Pending` step and `changing` flow, copy `delete_pending_title`, `delete_being_deleted`, reasons, `delete_change_owners`, `delete_owners_saved`, `delete_choices_dropped`, `delete.pending.*` tags — make T053 pass
- [X] T057 [US5] `cm/feature/auth/ui/home/{HomeContract,HomeViewModel,HomeScreen}.kt`: `now` from an injected clock with a minute tick; notice variant — make T054 pass
- [X] T058 [P] [US5] Screenshots: pending (owners with reasons), pending plan unavailable, being deleted (pending view and Home), owners saved, cancelled with choices dropped — EN LTR, AR RTL, 2.0
- [X] T059 [P] [US5] `.maestro/016-export-deletion/US5-pending.yaml`
- [ ] T060 [US5] Run US5 flow — **deferred: E2E on owner's device**

---

## Phase 8: User Story 6 - See who left in shared wallets (Priority: P3)

**Goal**: "Added by a former member" on transactions and recurring rules; ownership passed shows through existing feeds.

**Independent Test**: spec US6 Independent Test (live QA §2.5; flow `US6-former-member`, deferred).

### Tests first

- [X] T061 [P] [US6] `ct/feature/transactions/domain/TransactionMappingTest.kt`: extend `addedByOf` — author absent from a non-empty member map → `FormerMember`; present without a name → `AnotherMember`; empty map → `AnotherMember`; self and blank → `You` (FR-033)
- [X] T062 [P] [US6] `ct/feature/recurring/domain/RuleAuthorTest.kt`: the same four cases for rules through `RecurringService` with fake members; own rule → `You` (no line) (FR-034)
- [X] T063 [P] [US6] `ct/feature/wallets/…/OwnershipTransferFeedTest.kt` (or extend the spec 013 feed tests): a wallet feed carrying a new owner member row and the departed member's tombstone leaves the person as owner (`myRole = owner`) and the member list without the departed person (US6-3; existing behaviour pinned)

### Implementation

- [X] T064 [US6] `cm/feature/transactions/domain/TransactionMapping.kt` + `model/TransactionView.kt`: `AddedBy.FormerMember`; `ui/editor/TransactionEditorScreen.kt`: `tx_added_by_former` caption — make T061 pass
- [X] T065 [US6] `cm/feature/recurring/domain/{RecurringService,model/Views}.kt`: `RuleAuthor` resolved from the wallet's members (via `WalletsService.observeMembers`); `ui/rule/RuleDetailScreen.kt`: author line with `recurring_author`, `recurring_author_other`, `recurring_author_former`, tag `ruleDetail.author` — make T062 pass
- [X] T066 [P] [US6] Screenshots: transaction editor caption and rule detail author "former" — EN LTR, AR RTL, 2.0 (update affected goldens)
- [X] T067 [P] [US6] `.maestro/016-export-deletion/US6-former-member.part1.yaml`/`.part2.yaml` (`e2e-between: purge`)
- [ ] T068 [US6] Run US6 flows — **deferred: E2E on owner's device**

---

## Phase 9: Polish & Cross-Cutting

- [X] T069 [P] `ht/feature/account/ExportDeletionStringsTest.kt`: every spec 016 key exists in both files; none contains "XLSX", "Excel", "encrypt", "token", "إكسل", "مشفر"; the Play notice key is used by the explanation, review and pending view (FR-018, FR-030)
- [X] T070 [P] `at/ExportLoggingTest.kt`: Kermit capture over request → watch → ready → `openableLink` and over a deletion request with successors (MockEngine) — no download URL, signature or password appears in any log line, and `toString` of every State, Intent, Effect and outcome along the way contains none (SC-003, FR-014, FR-037)
- [X] T071 [P] `ht/feature/account/ExportLayeringTest.kt`: source scan — `feature/account/**/export/**` uses no file, download or share API (`okio`, `java.io.File`, `FileSystem`, `DownloadManager`, `LinkSharer`, clipboard) and no `Entitlements`; `feature/account/data` imports no `domain`; `core` imports no feature; `core/sync` unchanged by this spec (FR-004, FR-015, P-10)
- [X] T072 [P] `ht/ManifestExportsLinkTest.kt`: no intent filter in the Android manifests (all flavors) covers the `/exports` path (research R-02)
- [X] T073 `.specify/scripts/bash/run-e2e.sh`: seeds `+shared-owner` (owner with wallet A: one viewer; wallet B: editor + viewer, via the partner seeds), `+export-limit` (five accepted exports through the backend's own service), `e2e-between: purge` (time travel past the date and run the purge command in the container); header comment updated; seeds written and syntax-checked (`bash -n`), not executed against a device
- [X] T074 Live-backend contract QA (quickstart §2), **after backend spec 022 is implemented and committed**: rerun the contract check; throwaway accounts; every answer of steps 3–5 captured; write `at/CapturedExportResponses.kt`, `at/CapturedDeletionPlanResponses.kt` (URLs and signatures replaced) and `ExportLiveReplayTest.kt` / `DeletionPlanLiveReplayTest.kt` through the real `ApiClient`, APIs, services and stores; write `specs/016-export-deletion/qa-report.md` — QA step (live-backend contract QA, after implementation)
- [X] T075 Gates: `./gradlew :shared:testAndroidHostTest`, `:shared:verifyRoborazziAndroidHostTest`, `:androidApp:assembleDevDebug`, `:shared:compileKotlinIosSimulatorArm64`; `check-backend-contract.sh` clean (client codes vs catalogue)
- [ ] T076 Quickstart §3 spot checks (browser download, polling stops, Arabic 200 %, TalkBack, partner after purge) — **deferred: E2E on owner's device**
- [X] T077 Update `specs/briefs/README.md` client state for C15 and plan.md "Implementation notes"
- [ ] T078 Write specs/016-export-deletion/summary.md — written by the parent

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 (blocks all stories) → US1 (Phase 3) and US4 (Phase 4) in parallel (different packages: `ui/export` vs `ui/deleteaccount`) → US2, US3 (after US1; both edit the request/detail screens — run one after the other), US5 (after US4; same Delete account files), US6 (independent of the others after Phase 2) → Phase 9.
- T074 (live QA) additionally waits for backend spec 022 to be committed.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T003–T006 all [P]; implementation T007–T012 all [P].
- US1 and US4 can be built by two people; US6 can start any time after Phase 2.
- Polish T069–T072 all [P].

## Parallel Example: Phase 2

```text
T003 code | T004 export contract | T005 deletion contract | T006 mapping
then: T007 | T008 | T009 | T010 | T011 | T012
```

## Implementation Strategy

### MVP First (US1 + US4)

1. Phase 1 → Phase 2 → Phase 3 (export everything) and Phase 4 (new owners).
2. Stop and validate: an export requests, polls and opens through the MockEngine; a deletion with a shared wallet sends the chosen successor; host tests green.

### Incremental Delivery

1. US1 + US4 (the brief's two checkpoints) → US2 (wallet export) → US3 (refusals) → US5 (pending view) → US6 (former member).
2. Polish: string scan, log capture, layering, manifest check, E2E seeds, live QA with captured fixtures (after backend 022 is committed), gates, summary; device E2E and spot checks deferred to the owner.
