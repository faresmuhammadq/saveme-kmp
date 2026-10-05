---
description: "Task list for 013-sharing"
---

# Tasks: Sharing & Invitations (client)

**Input**: Design documents from `specs/013-sharing/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (sharing-api-client.md, sharing-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; `main` `48bd79f` (specs 004–012: engine, entitlements, wallets, categories, transactions, feeds + batch + heads, recurring, budgets; Room v9)

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per screen; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Testing policy (owner decision)**: no emulator, Maestro or device run. Flows are written under `.maestro/013-sharing/`, each starting `# deferred: E2E on owner's device`; every task that runs them is marked **deferred: E2E on owner's device**. Host QA (Phase 10) replays every call against the live local backend with `curl` and turns the real answers into fixtures, as specs 007–012 did.

**Organization**: Phase 2 builds storage, core additions, the wire clients, the handler and applier, the pure domain, the memory holders, the wallets/auth hooks and navigation keys. US1 (invite by email) is the MVP; US2 (join) completes the brief's checkpoint; US3 links; US4 manage; US5 leave and purge; US6 activity; US7 roles everywhere.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US7 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`); sharing feature `cm/feature/sharing/…` (`fs/…`); wallets `cm/feature/wallets/…` (`fw/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`); sharing tests `ct/feature/sharing/…` (`st/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: sharing ui → sharing domain → sharing data; `fs/data` imports no domain, ui or other feature; sharing domain reaches wallets, transactions, recurring, billing and auth only through their `domain`; wallets and auth reach sharing only through the hooks they define; `core` imports no feature), naming (P-11).
- Glossary (P-01): Invitation, Wallet Member, Activity; no copy uses "invite link", "join request", "participant", "collaborator", "friend", "group", "household", "family", "token"; nothing user-facing says "version", "tombstone", "scope" or "cursor".
- **Secrets (P-09.2)**: the invitation token and the share link live only in memory (holders, store state, effects); every class holding one overrides `toString` with `<redacted>`; never in Room, `KeyValueStore`, logs, crash reports or a URL path/query; the invited address is never logged. Info logs: operation, outcome code, request id, counts; ids at debug only.
- Token calls (preview, accept, decline) and member actions (role, remove, leave) are never retried automatically (P-02.6); invitation creates/withdrawals retry only through the engine.
- **MVI screen recipe (P-16)** as specs 001–012: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas, `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Koin**: repositories and services resolve `SyncEngine` lazily (spec 006 lesson); queue discards from participants run in a launched job (spec 008 lesson); `sharingModule` is listed after `authModule`, `walletsModule`, `transactionsModule`, `recurringModule` and `budgetsModule` so its hook rebinding wins; `AppGraphTest` stays green.
- **Merge hotspots**: `AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt`, `ProblemDetails.kt`, `HttpClientFactory.kt` (`ApiClient`), `PendingChange.kt`, `SyncEngine.kt`, `PendingChangeDao.kt`, `WalletRepository.kt`, `WalletEntity.kt`, `WalletsService.kt`, wallet detail contract/VM/screen, transactions list contract/screen, `TransactionMapping.kt` copy, Home contract/VM/screen, `AuthModule.kt`, `AppModules.kt`, `Routes.kt`, `AppNavHost.kt`, both manifests: additive only. New strings in one append-only block `<!-- Sharing (spec 013) -->` at the end of each `strings.xml`.
- Every interactive element gets the `testTag` of contracts/ui-and-e2e.md; dialog buttons are tapped by text in flows.

---

## Phase 1: Setup

**Purpose**: confirm prerequisites, create the package, strings.

- [X] T001 Confirm on `013-sharing` (from `main` `48bd79f`): `AppDatabase` version 9 with `MIGRATION_8_9` and `shared/schemas/…/9.json`; `sync_scope.feed_position`; `FeedApplier`, `SyncRunner`/`ReadTarget`, `SyncHandler.batchItems`/`prerequisites`/`acceptsClash`/`onDiscarded` in `cm/core/sync/`; `WalletRepository.storeMember`/`endOwnMembership`/`accessLost`, `WalletMemberFeedApplier`, `OwnMembershipFeedApplier`, `WalletDeletionParticipant` in `fw/`; `Entitlements.can`/`quota` with `FlagFeature.SharingEnabled` and `QuotaFeature.MembersPerWallet` in `cm/feature/billing/domain/`; `AuthLinkParser` and `DeepLinkBus`; `MemberRow`/`RolePill` in `cm/designsystem/component/Lists.kt`; `member_unnamed`, `role_*` strings; `.maestro/common/api.js`. If any is missing, stop and report
- [X] T002 Create `fs/di/SharingModule.kt` with an empty `val sharingModule = module { }` and append it at the end of `appFeatureModules` in `cm/AppModules.kt` (hotspot: one list element)
- [X] T003 [P] Add the string block `<!-- Sharing (spec 013) -->` with every key of contracts/ui-and-e2e.md "Copy" (one key per activity action and subject kind) plus screen titles, empty states, confirmations, content descriptions and the transactions/recurring `refusal_now_viewer` copy (about 90) to the end of `values/strings.xml` and `values-ar/strings.xml` (spec §2 words; Western digits; no exclamation marks)

---

## Phase 2: Foundational (blocking)

**Purpose**: Room v10, core additions, wire clients, handler, applier, pure domain, holders, hooks, navigation keys, manifests.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T004 [P] Migration test `ht/core/database/Migration9To10Test.kt`: from a v9 database with a `user:<id>` scope and two `wallet:<id>` scopes (cursor, read stamp, position set), after `MIGRATION_9_10` table `invitation` exists with data-model §1 columns and index `index_invitation_account_id_wallet_id_status`; both wallet scopes have `feed_cursor`, `feed_read_at`, `feed_position` NULL; the user scope keeps all three; every other row kept; schema equals `10.json` (FR-030)
- [X] T005 [P] DAO test `ht/feature/sharing/data/InvitationDaoTest.kt`: upsert/observe pending by wallet and account (oldest first, then id), delete by id, delete for a wallet, count of unexpired pending per wallet at a given instant (null `expires_at` counted), no secret-like column in the entity (FR-033, SC-004)
- [X] T006 [P] Contract tests `st/data/InvitationApiContractTest.kt` (MockEngine, `sharing.yaml` examples): A1 PUT email and link bodies (exact members, explicit `"invitee_email": null`), 201 with `meta.share_url` read through `callWithMeta`, 200 replay with `meta: {}`; A2 DELETE with `{"version": n}`; A3–A5 POST bodies `{"token": …}` with the token never in the URL; preview decoding incl. null inviter name and `already_member`; decline 204; every answer row of contracts/sharing-api-client.md A1–A5 mapped by `code` (and `feature`) (FR-032)
- [X] T007 [P] Contract tests `st/data/MemberApiContractTest.kt`: A6 PATCH `{role, version}`, A7 DELETE `{version}`, A8 POST leave `{version}`; 200 member and tombstone decoding; 409 `current`; 403 `FORBIDDEN` / `FEATURE_NOT_ENTITLED` + `feature` / `EMAIL_NOT_VERIFIED`; 404; 422 `IMMUTABLE_FIELD /role`
- [X] T008 [P] Contract tests `st/data/ActivityApiContractTest.kt`: A9 `GET /wallets/{id}/activity?limit=50` then `&cursor=…`; entry decoding with null actor and role; unknown member ignored; 400 `INVALID_CURSOR`; 404
- [X] T009 [P] Core tests: `ct/core/network/ProblemFeatureTest.kt` (`feature` decoded; absent → null), `ct/core/network/CallWithMetaTest.kt` (meta kept; `call` unchanged), `ct/core/sync/RefusalFeatureTest.kt` (a stored refusal JSON without `feature` still decodes; the engine records `feature` from a `FEATURE_NOT_ENTITLED` answer), `ht/core/database/PendingChangeDaoTest.kt` + `ct/core/sync/SyncEnginePendingInTest.kt` (`pendingIn(scope)` returns only that scope's rows)
- [X] T010 [P] Handler tests `st/data/InvitationSyncHandlerTest.kt`: record type `invitation`; `batchItems` null for create and delete; create → PUT with version 0 and the payload's members; delete → DELETE with the stored version; a 201 with `share_url` calls `onShareLink(id, url)` before returning the record, a 200 replay does not; `acceptsClash` true; `canRestore` false; `prerequisites` = `RecordKey("wallet", walletId)` when the wallet's stored version is 0; `refusalFields` maps `/role`, `/invitee_email`; `onDiscarded` removes a placeholder only (research R-03, R-04)
- [X] T011 [P] Applier tests `st/data/InvitationFeedApplierTest.kt`: `ScopeKind.Wallet`, row type `invitation`; older version never replaces newer; tombstone (each closed `status`) removes the row unless a change waits; unknown members and an unknown role kept; `sweep` removes synced rows of the scope not seen, never placeholders or rows with a waiting change (FR-029)
- [X] T012 [P] Gate tests `st/domain/InviteGateTest.kt` (table): order verified → sharing included → count; quota Unlimited; Limited(1) with owner only → Full(1); Limited(5) with owner + 2 members + 1 unexpired pending + 1 unsent placeholder → Allowed, + 1 more → Full; expired pending not counted; waiting withdrawals not counted; `sharing.enabled` false → SharingNotIncluded regardless of quota (FR-002, research R-06)
- [X] T013 [P] Validation tests `st/domain/InviteValidationTest.kt`: trim; 3–254 characters; one `@`; domain with a dot; spaces refused; own address (case-insensitive) → OwnAddress; duplicate pending address detection case-insensitive (FR-003)
- [X] T014 [P] Parser tests `st/domain/InvitationLinkParserTest.kt` (table per flavor config): `https://host/invite?token=<43>` valid; dev `http://host:port/invite?token=…` valid only with the port; wrong scheme/host/port/path → null; `/invite` with 42 or 44 chars, `+`, `/`, `=`, empty, missing → Malformed; extra parameters and fragments tolerated; `toString` of `Valid` redacts the token (FR-009)
- [X] T015 [P] Holder tests `st/domain/SharingHoldersTest.kt`: `HeldInvitation` hold/replace/clear and cleared by `clear()` at sign-out; `ShareLinks` offer/get/forget, cleared at sign-out; `PlanDemotionNotes` record/observe, cleared at sign-out; every `toString` redacts token and url (FR-005, FR-010, FR-031)
- [X] T016 [P] Share text tests `st/domain/ShareTextTest.kt`: contains the app name, the sentence and the link; built for a wallet named "Home" with a balance, the text contains neither the name nor any digit of the balance (FR-006)
- [X] T017 [P] Wallets tests `ct/feature/wallets/domain/WalletMembershipsTest.kt`: `store` applies equal and newer versions, ignores older; a tombstone deletes the row; the person's own live row sets `wallet.my_role` when the wallet row exists and leaves a missing wallet alone; `leftWallet` runs `accessLost` (participants called); `observeJoining` true only while an own member row exists and the wallet row does not; `SharingGlance` default emits 0 / false (FR-022, research R-12)
- [X] T018 [P] Mapping tests `st/domain/ActivityMappingTest.kt`: every contract action × subject kind maps to words; unknown action or subject type → skipped; actor: server name, else member name from the phone, else FormerMember, null → Automatic; subject names: transaction note → category → "a transaction"; rule/occurrence note or category; member name; "an invitation as <role>"; wallet name; no amount field exists on `ActivityItem`; `role_limited_by_plan` about the person → plan flag (FR-025)

### Implementation

- [X] T019 `ProblemDetails.feature` in `cm/core/network/ProblemDetails.kt`; `ApiClient.callWithMeta<T>()` in `cm/core/network/HttpClientFactory.kt` (additive; `call` unchanged) (makes T009 network parts pass)
- [X] T020 `Refusal.feature: String? = null` in `cm/core/sync/PendingChange.kt` and the one line in `SyncEngine` that copies `problem.feature` when recording a refusal; `PendingChangeDao.forScope(scopeKey)` and `SyncEngine.pendingIn(scope)` (read-only) (makes T009 sync parts pass)
- [X] T021 [P] `fs/data/local/InvitationEntity.kt` + `InvitationDao` (data-model §1 verbatim; index `(account_id, wallet_id, status)`; `toString` id + version only)
- [X] T022 `MIGRATION_9_10` in `cm/core/database/Migrations.kt` (data-model §5 SQL; KDoc citing FR-030), `AppDatabase` v10 with the entity and DAO, `buildAppDatabase` adds the migration, `DatabaseCleaner` deletes `invitation`; build exports `shared/schemas/…/10.json` (depends on T021; makes T004, T005 pass)
- [X] T023 [P] DTOs `fs/data/dto/SharingDtos.kt`: `InvitationDto` (all `Invitation` members), `InvitationWriteRequest` (explicit-null `invitee_email`), `VersionRequest`, `TokenRequest` (redacted), `InvitationCreateMetaDto` (redacted), `InvitationPreviewDto`, `MemberDto`, `MemberRoleRequest`, `ActivityEntryDto`
- [X] T024 [P] `fs/data/InvitationApi.kt`: `put` (via `callWithMeta`), `delete` (JSON body), `preview`, `accept`, `decline` (depends on T019, T023; makes T006 pass)
- [X] T025 [P] `fs/data/MemberApi.kt`: `changeRole`, `remove`, `leave` (depends on T023; makes T007 pass)
- [X] T026 [P] `fs/data/ActivityApi.kt`: `page(walletId, cursor)` via `callPage` (depends on T023; makes T008 pass)
- [X] T027 `fs/data/InvitationPayload.kt` (serializable `{role, invitee_email}`, redacted) and `fs/data/InvitationRepository.kt` (observe by wallet, placeholder insert, `storeServerCopy`/`storeFeedCopy` with the version guard, tombstone rule, `sweep`, `removePlaceholder`, `deleteForWallet`, lazy `hasQueuedChange`) (depends on T021, T022)
- [X] T028 `fs/data/InvitationSyncHandler.kt` per research R-03/R-04 with an injected `onShareLink: (String, String) -> Unit` and a wallet-version lookup lambda for `prerequisites` (depends on T024, T027; makes T010 pass)
- [X] T029 `fs/data/InvitationFeedApplier.kt` (depends on T027; makes T011 pass)
- [X] T030 [P] Pure domain in `fs/domain/`: `InviteGate.kt`, `InviteValidation.kt`, `InvitationLinkParser.kt` (+ `InvitationLink`), `ShareText.kt`, `ActivityMapping.kt`, models in `fs/domain/model/` (data-model §7) (makes T012–T014, T016, T018 pass)
- [X] T031 [P] Holders in `fs/domain/`: `HeldInvitation.kt`, `ShareLinks.kt`, `PlanDemotionNotes.kt`, each also a `UserDataCleaner` (makes T015 pass)
- [X] T032 Wallets additions: `fw/domain/WalletMemberships.kt` (interface + `MemberRecord`) and `DefaultWalletMemberships`; `WalletDao.updateRole(id, role)` in `fw/data/local/WalletEntity.kt`; `WalletRepository.storeMember` sets the own role (account id lambda); `WalletsService.observeJoining`; `fw/domain/SharingGlance.kt` (interface + `NoSharingGlance`) bound in `fw/di/WalletsModule.kt` (makes T017 pass)
- [X] T033 [P] Auth hook `cm/feature/auth/domain/HeldInvitationGlimpse.kt` (+ `NoHeldInvitation`), bound in `cm/feature/auth/di/AuthModule.kt`
- [X] T034 [P] `fs/domain/LinkSharer.kt` (interface); `shared/src/androidMain/kotlin/com/fares/saveme/feature/sharing/AndroidLinkSharer.kt` (ACTION_SEND chooser with `FLAG_ACTIVITY_NEW_TASK`; `ClipboardManager` with `ClipDescription.EXTRA_IS_SENSITIVE` on API 33+ and `android.content.extra.IS_SENSITIVE` below); `shared/src/iosMain/kotlin/com/fares/saveme/feature/sharing/NoLinkSharer.kt`; platform Koin binding (research R-17)
- [X] T035 `fs/domain/InvitationsWalletCleanup.kt` (`WalletDeletionParticipant`: delete the wallet's invitation rows, forget their links, discard their queued changes from a launched job) (research R-13)
- [X] T036 Koin `fs/di/SharingModule.kt`: APIs, repository (lazy engine), handler bind `SyncHandler` (`onShareLink = ShareLinks::offer`), applier bind `FeedApplier`, cleanup bind `WalletDeletionParticipant`, holders bind `UserDataCleaner`, `InvitationLinkParser` from `AppConfig`; `ht/core/di/AppGraphTest.kt` gains `theSharingModuleStartsWithItsHandlerApplierAndParticipant` (handler type `invitation`, wallet applier `invitation`, participant list includes the cleanup) (depends on T028, T029, T031, T035)
- [X] T037 [P] Navigation keys in `cm/navigation/Routes.kt`: `WalletMembersKey(walletId)`, `InviteKey(walletId, replacing: String? = null)`, `InvitationKey(token)` with redacted `toString`, `WalletActivityKey(walletId)`
- [X] T038 [P] Manifests: `<data android:path="/invite" />` in the `https` filter of `androidApp/src/main/AndroidManifest.xml` and the `http` filter of `androidApp/src/dev/AndroidManifest.xml` (research R-08)

**Checkpoint**: storage, sync, wire, pure domain, holders and hooks ready; all Phase 2 tests green.

---

## Phase 3: User Story 1 - Invite someone to my wallet by email (Priority: P1) 🎯 MVP

**Goal**: an owner invites an address with a role, offline or online; the invitation is listed and sent.

**Independent Test**: as an owner on Plus, invite an address as editor; the members screen lists it pending; offline it waits and is sent later.

### Tests for User Story 1

- [X] T039 [P] [US1] Service tests `st/domain/SharingServiceInviteTest.kt`: invite queues one `invitation` change in `wallet:<id>` with base version 0, a UUIDv7 id and a placeholder; gate results in order (`NotVerified`, `SharingNotIncluded`, `Full`) with nothing queued; `OwnAddress`, `Invalid`; `SupersedeNeeded` then `inviteConfirmed` queues; archived wallet → not offered; offline does not block; refusal by `feature` maps to the right reason; `discard` removes placeholder and change; `tryAgain` re-queues; the sent/saved message is identical whatever the address (no account hint, P-09.4) (FR-001–FR-004, FR-008)
- [X] T040 [P] [US1] Members reducer + ViewModel tests `st/ui/members/MembersReducerTest.kt`, `MembersViewModelTest.kt` (owner and editor/viewer views): rows and invitations from the service; owner sees Invite enabled/disabled with the gate's explanation and "See your plan"; editor/viewer see no owner controls; "Waiting to sync" / "Not sent" / "Expired" marks; "Expired" re-evaluated by a ticker; "See your plan" emits only `OpenPlan` and no intent or effect of the members or invite contracts offers a purchase (FR-016, FR-036)
- [X] T041 [P] [US1] Invite reducer + ViewModel tests `st/ui/invite/InviteReducerTest.kt`, `InviteViewModelTest.kt` (email channel): viewer preselected, email error after touch, supersede confirmation, send → close with the online/offline message, verify prompt effect

### Implementation for User Story 1

- [X] T042 [US1] `fs/domain/SharingService.kt` (`DefaultSharingService`): `observeInvitations`, `observeMembers`, `observeInviteCheck`, `invite`, `inviteConfirmed`, `discard`, `tryAgain` (contracts/sharing-domain.md; lazy engine; verification from `SessionManager`; entitlements from `Entitlements`) (makes T039 pass)
- [X] T043 [US1] Members screen `fs/ui/members/` (Contract, ViewModel, Route, Screen): members section, pending invitations section, Invite entry with gate explanation, tags (makes T040 pass)
- [X] T044 [US1] Invite screen `fs/ui/invite/` (Contract, ViewModel, Route, Screen) with the email channel, role choice with hints, supersede dialog, tags (makes T041 pass)
- [X] T045 [US1] Wallet detail members section: "Manage members" (owner) / "See members" with "(n pending)" from `SharingGlance.observePendingCount`; `WalletDetailIntent.MembersClicked`, `WalletDetailEffect.NavigateMembers` in `fw/ui/detail/`; reducer/VM tests updated in `ct/feature/wallets/ui/detail/`
- [X] T046 [US1] `DefaultSharingGlance.observePendingCount` in `fs/domain/` bound over the wallets default; service and both ViewModels in `fs/di/SharingModule.kt`; `AppNavHost` entries for `WalletMembersKey` and `InviteKey`, `OpenPlan` → `PlanKey`
- [X] T047 [P] [US1] Screenshots `ht/feature/sharing/ui/SharingScreenshotTest.kt`: members (owner with one pending email invitation, editor view, invite disabled for plan), invite email form — EN LTR, AR RTL, 2.0
- [X] T048 [P] [US1] Maestro flows `.maestro/013-sharing/US1-invite-email.yaml`, `US1-invite-offline.part1/2/3.yaml`, `US1-not-included.yaml` (`# deferred: E2E on owner's device`); `api.js` gains `createInvitation`, `acceptInvitation`, `changeMemberRole`, `removeMember`, `previewInvitation`
- [ ] T049 [US1] Run the US1 flows — **deferred: E2E on owner's device**

**Checkpoint**: an owner can invite by email; host tests green.

---

## Phase 4: User Story 2 - Join a wallet from an invitation (Priority: P1)

**Goal**: an invitation link opens the app; the person previews, joins or declines; the wallet arrives.

**Independent Test**: with a second account, open an invitation link, join, and see the wallet; partner transactions appear after refresh both ways.

### Tests for User Story 2

- [X] T050 [P] [US2] Service tests `st/domain/SharingServiceJoinTest.kt`: preview/join/decline outcomes per contracts A3–A5 (Unavailable for every 404, OwnerPlanFull for both `feature` values, InvalidLink, TooMany with minutes rounded up from `retryAfterSeconds`, Offline, Failed with request id); join stores the member row through `WalletMemberships`, requests `ReadTarget.Wallet(id)` forced, clears the held invitation; no call retried automatically (each fake API called once) (FR-012–FR-015)
- [X] T051 [P] [US2] Invitation reducer + ViewModel tests `st/ui/invitation/InvitationReducerTest.kt`, `InvitationViewModelTest.kt`: modes SignedOut / Loading / Ready / AlreadyMember / Unavailable / InvalidLink / Offline / TooMany / OwnerPlanFull / Failed; Join → `OpenWallet`; Decline confirm → `Close(declined)`; Not now → held kept; state `toString` has no token
- [X] T052 [P] [US2] Routing tests `ct/navigation/InvitationLinkRoutingTest.kt` over a pure helper `routeLink(raw, signedIn, parsers)`: auth links unchanged; valid invitation signed in → push `InvitationKey`; signed out → hold + push on the auth stack; Malformed → `InvitationKey` in InvalidLink mode without a server call; after sign-in with a held invitation → push `InvitationKey`; a second link while an `InvitationKey` is on top replaces it (FR-009, FR-011, Edge Cases)
- [X] T053 [P] [US2] Wallet detail tests in `ct/feature/wallets/ui/detail/`: joining state shows "Getting the wallet…" and "Try again", never `Gone`; becomes the normal detail when the wallet row lands
- [X] T054 [P] [US2] Home tests in `ct/feature/auth/ui/home/`: card shown while `HeldInvitationGlimpse` emits true; `InvitationClicked` → `OpenInvitation`

### Implementation for User Story 2

- [X] T055 [US2] `SharingService.preview`, `join`, `decline` (makes T050 pass)
- [X] T056 [US2] Invitation screen `fs/ui/invitation/` (Contract, ViewModel, Route, Screen) with all modes and tags (makes T051 pass)
- [X] T057 [US2] `AppNavHost`: invitation parser after the auth parser through `routeLink`, `HeldInvitation` on the signed-out path, push after the signed-in transition, effects `OpenWallet` → `WalletDetailKey`, `NavigateSignIn`/`NavigateRegister`; confirm the dev Paste link tool forwards invitation links (makes T052 pass)
- [X] T058 [US2] Wallet detail joining state in `fw/ui/detail/` using `WalletsService.observeJoining` (makes T053 pass)
- [X] T059 [US2] Home card: `DefaultHeldInvitationGlimpse` in `fs/domain/` bound over the auth default; `HomeIntent.InvitationClicked`, `HomeEffect.OpenInvitation`, card (tag `home.invitation`) in `cm/feature/auth/ui/home/`; nav pushes `InvitationKey` from the held link (makes T054 pass)
- [X] T060 [P] [US2] Screenshots: invitation Ready (editor, named inviter), Ready (unnamed inviter), SignedOut, AlreadyMember, Unavailable; wallet detail joining; Home card — EN LTR, AR RTL, 2.0
- [X] T061 [P] [US2] Maestro flows `US2-join.part1/2.yaml` (second session creates a link invitation via `api.js`, `openLink` the dev URL, join, partner transaction visible after refresh — the brief's checkpoint), `US2-signed-out.yaml`, `US2-unavailable.yaml`
- [ ] T062 [US2] Run the US2 flows — **deferred: E2E on owner's device**

**Checkpoint**: invite (US1) + join (US2) complete the brief's first checkpoint on host tests.

---

## Phase 5: User Story 3 - Share an invitation by link (Priority: P1)

**Goal**: a link invitation's one-time link is shared or copied; a lost link is replaced.

**Independent Test**: create a link invitation as viewer; once the server answers, Share/Copy work; the text names no wallet and no amount.

### Tests for User Story 3

- [X] T063 [P] [US3] Service tests `st/domain/SharingServiceLinkTest.kt`: a link invitation queues with `invitee_email` null; the handler's `onShareLink` makes `shareTextFor(id)` non-null; a 200 replay leaves it null; `replaceLink` withdraws the old (queued delete, link forgotten) and queues a new link invitation with the same role; nothing logged contains the url (FR-005, FR-006)
- [X] T064 [P] [US3] Invite and Members ViewModel tests: link channel waits ("Getting the link…" / offline message), becomes ready when the link arrives, `Share(text)` and `Copy(text)` effects with redacted `toString`; members row offers Share/Copy when held, "Replace link" when not

### Implementation for User Story 3

- [X] T065 [US3] `SharingService.shareTextFor`, `replaceLink`, link-channel `invite` (makes T063 pass)
- [X] T066 [US3] Invite screen link channel: waiting, offline and ready states, Share/Copy/Done (tags `invite.share`, `invite.copy`) (makes T064 invite part pass)
- [X] T067 [US3] Members invitation rows: Share/Copy or "This link can't be shown again" + "Replace link" (makes T064 members part pass)
- [X] T068 [US3] Invite and Members `…Route`s call `LinkSharer.share`/`copy` on the effects; "Copied" confirmation
- [X] T069 [P] [US3] Screenshots: invite link waiting, link ready; members row with a held link and with "Replace link"
- [X] T070 [P] [US3] Maestro flow `US3-share-link.yaml`
- [ ] T071 [US3] Run the US3 flow — **deferred: E2E on owner's device**

---

## Phase 6: User Story 4 - Manage members and pending invitations (Priority: P2)

**Goal**: change roles, remove members, withdraw invitations; ownership transfer reachable.

**Independent Test**: make an editor a viewer, withdraw an invitation, remove a member.

### Tests for User Story 4

- [X] T072 [P] [US4] Service tests `st/domain/SharingServiceMembersTest.kt`: `changeRole` raise checks `can(sharing.enabled)` and the people count, lower never; sends the stored member version; every A6/A7 answer → `MemberActionResult` (Changed stores `current`, Gone re-reads the wallet, OwnerRole, OnlyOwner, NotEntitled(feature) and the C3 refresh event, NotVerified, Offline, Failed); a second action for the same wallet while one runs → `Busy`; own row and owner row refused locally; `withdraw` of a sent invitation queues the delete with its version and hides it, of a never-sent one removes it with nothing sent, forgets its link (FR-007, FR-017, FR-018, FR-020, FR-021)
- [X] T073 [P] [US4] Members ViewModel tests: owner's member sheet (Make editor/Make viewer/Remove), confirmations (remove text with the honest limit; withdraw), busy state, messages per result
- [X] T074 [P] [US4] Plan note tests: `observePlanNote` returns the plan's max when people exceed the quota or sharing is not included (quota counted as 1), else null (FR-028)
- [X] T075 [P] [US4] Wallet detail test: "Transfer ownership" offered once a second member row exists and nothing waits; hidden with only the owner (FR-021, US4-S5)

### Implementation for User Story 4

- [X] T076 [US4] `SharingService.changeRole`, `remove`, `withdraw`, `observePlanNote` (makes T072, T074 pass)
- [X] T077 [US4] Members screen: member action sheet, confirmations, withdraw action, plan note (tag `members.planNote`), messages (makes T073 pass)
- [X] T078 [US4] Confirm the C4 transfer gate on wallet detail with members present; adjust only if T075 fails
- [X] T079 [P] [US4] Screenshots: owner member sheet, remove confirmation, plan note
- [X] T080 [P] [US4] Maestro flows `US4-role-and-remove.yaml`, `US4-withdraw.yaml`
- [ ] T081 [US4] Run the US4 flows — **deferred: E2E on owner's device**

---

## Phase 7: User Story 5 - Leave a wallet, and lose access cleanly (Priority: P2)

**Goal**: members leave; any loss of access purges the wallet from the phone.

**Independent Test**: an editor leaves and the wallet is gone with its data; a removed member's wallet is gone after the next sync.

### Tests for User Story 5

- [X] T082 [P] [US5] Service tests `st/domain/SharingServiceLeaveTest.kt`: `leaveCheck` uploads first, then counts `pendingIn(wallet scope)`; owner → `Owner`; offline → `Offline`; `leave` sends the own membership version; 200 and 404 → `Left(name)` and `WalletMemberships.leftWallet`; 403 → `OwnerCannotLeave`; 409 stores `current` → `Changed` (FR-019)
- [X] T083 [P] [US5] Room purge test `ht/feature/sharing/AccessLostPurgeTest.kt`: for each signal (own tombstone on the user feed, leave 200, leave 404, wallet feed 404) the wallet, its members, invitations, transactions, recurring rules, occurrences, category references, scope position and their queued changes are gone; another wallet untouched; a wallet with a waiting change to its own record stays until settled (FR-023, SC-003)
- [X] T084 [P] [US5] Wallet detail tests: wallet vanishes → "You no longer have access to this wallet"; after the person's own leave → no access message (the members screen shows "You left <wallet>")

### Implementation for User Story 5

- [X] T085 [US5] `SharingService.leaveCheck`, `leave` (makes T082 pass)
- [X] T086 [US5] Members screen: "Leave wallet" (non-owner) with the confirmation (waiting count line), owner note "To leave, transfer ownership first"; effect `Left(name)` → `AppNavHost` pops to `WalletsKey` with the message (tag `members.leave`)
- [X] T087 [US5] Wallet detail access-lost message (reuse `DetailMessage.NoLongerMember` copy updated to `access_lost`) and suppression after the own leave (makes T084 pass); confirm T083 passes with T035's participant
- [X] T088 [P] [US5] Maestro flows `US5-leave.yaml`, `US5-removed.part1/2.yaml` (the brief's second checkpoint)
- [ ] T089 [US5] Run the US5 flows — **deferred: E2E on owner's device**

---

## Phase 8: User Story 6 - See who did what in a shared wallet (Priority: P3)

**Goal**: an online, paged activity list with names from the phone.

**Independent Test**: after a partner adds a transaction, the activity lists it first and the join below it.

### Tests for User Story 6

- [X] T090 [P] [US6] Service tests `st/domain/SharingServiceActivityTest.kt`: pages of 50 resolved through fakes of `WalletsService`, `TransactionsService`, `RecurringService`; unknown actions skipped; `INVALID_CURSOR` restarts once from the first page then fails; 404 → forced wallet read and `Gone`; offline → `Offline`; plan demotions about the person recorded in `PlanDemotionNotes` (newest role change wins) (FR-024–FR-026)
- [X] T091 [P] [US6] Activity reducer + ViewModel tests `st/ui/activity/ActivityReducerTest.kt`, `ActivityViewModelTest.kt`: first page, load more on scroll end, no duplicate loads, errors with Try again, transaction tap → `OpenTransaction`, Gone → close

### Implementation for User Story 6

- [X] T092 [US6] `SharingService.activityPage` (makes T090 pass)
- [X] T093 [US6] Activity screen `fs/ui/activity/` (Contract, ViewModel, Route, Screen) with day headers, 90-day note, tags (makes T091 pass)
- [X] T094 [US6] Wallet detail "Activity" row (`WalletDetailIntent.ActivityClicked`, `WalletDetailEffect.NavigateActivity`) for every member; `AppNavHost` entry for `WalletActivityKey`; `OpenTransaction` → `TransactionEditorKey`
- [X] T095 [P] [US6] Screenshots: activity with mixed entries (named, former member, automatic, plan demotion), offline state
- [X] T096 [P] [US6] Maestro flow `US6-activity.yaml`
- [ ] T097 [US6] Run the US6 flow — **deferred: E2E on owner's device**

---

## Phase 9: User Story 7 - Shared wallets explain themselves on every phone (Priority: P3)

**Goal**: roles in words, viewer and plan notes, demoted-write explanation, multi-phone consistency.

**Independent Test**: an editor turned viewer by the owner's plan sees the note and no write controls after sync.

### Tests for User Story 7

- [X] T098 [P] [US7] Wallet detail and transactions list tests: viewer → `viewer_note`; `SharingGlance.observePlanLimited` true → `viewer_note_plan`; editors and owners see no note; add controls absent for viewers (FR-027, SC-006)
- [X] T099 [P] [US7] Refusal copy tests in `ct/feature/transactions/` and `ct/feature/recurring/`: `FORBIDDEN` on a non-transfer transaction and on rule/occurrence writes shows `refusal_now_viewer` with Discard (FR-027)
- [X] T100 [P] [US7] `ht/core/database/SignOutWipeTest.kt` additions: invitations removed; `HeldInvitation`, `ShareLinks`, `PlanDemotionNotes` empty after sign-out (FR-031)
- [X] T101 [P] [US7] Feed test `st/data/InvitationsAfterUpgradeTest.kt`: after `MIGRATION_9_10` a "refresh everything" run reads each wallet from the start and stores pending invitations created before the update (fake `SyncApi` pages) (FR-030, US7-S5)

### Implementation for User Story 7

- [X] T102 [US7] `DefaultSharingGlance.observePlanLimited` from `PlanDemotionNotes`; viewer notes in `fw/ui/detail/` and the transactions list screen (makes T098 pass)
- [X] T103 [US7] `NoRightHere` copy → `refusal_now_viewer` in transactions; recurring `FORBIDDEN` refusals reuse it (makes T099 pass)
- [X] T104 [US7] Confirm the wallet list's role caption (C4) for non-owned wallets; no change expected (US7-S1)
- [X] T105 [P] [US7] Screenshots: wallet detail viewer note (plain and plan), transactions list viewer note
- [X] T106 [P] [US7] Maestro flow `US7-viewer-note.yaml`
- [ ] T107 [US7] Run the US7 flow — **deferred: E2E on owner's device**

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T108 [P] Arabic copy review of the sharing block (دعوة، الأعضاء، النشاط، مشاهد، محرر، معلّقة، منتهية; Western digits); list words for owner review in `summary.md`
- [X] T109 [P] Layering test `ht/feature/sharing/ui/SharingLayeringTest.kt` modelled on `RecurringLayeringTest`: `feature/sharing/ui` imports no `data`; `feature/sharing/data` imports nothing from its domain/ui and no other feature; other features only through `…domain`; no feature other than sharing imports `feature/sharing`; `core` imports no feature (P-10)
- [X] T110 [P] String test `ht/feature/sharing/SharingStringsTest.kt`: the `<!-- Sharing (spec 013) -->` block exists in both files with the same keys; rejects the banned words of contracts/ui-and-e2e.md and Eastern Arabic digits (FR-035)
- [X] T111 [P] Accessibility test `ht/feature/sharing/ui/SharingAccessibilityTest.kt`: roles and states in words, icon buttons described, 48 dp, no clipped text at 2× on the four screens (P-08)
- [X] T112 [P] Privacy test `st/SharingLoggingTest.kt`: an invite (email and link), a share, a join, a decline, a refusal and a leave log no token, link or address (captured Kermit writer), and every holder/DTO/effect/key `toString` is redacted (FR-010, FR-034, SC-004)
- [X] T113 [P] Performance test `ht/feature/sharing/SharingPerformanceTest.kt`: members screen state with 5 members and 20 invitations emitted under 1 s; an activity page of 50 mapped under 300 ms (plan Performance Goals)
- [X] T114 Trace every spec §4 invariant to its passing test (list in plan.md "Implementation notes"; parent copies into `summary.md`) (P-14.2)
- [X] T115 [P] `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS compiles with the no-op sharer)
- [ ] T116 (partly met: the four-task gate is green; `build allTests --continue` fails only on four pre-existing, non-sharing tasks, see plan implementation notes) `./gradlew build allTests` and `:shared:verifyRoborazziAndroidHostTest` green; `10.json` exported; merged prod release manifest has the `https` `/invite` path and no `http` filter (Quality gate 5)
- [X] T117 Host QA without a device (quickstart "Live contract QA"): map every acceptance scenario, FR and edge case to a passing host test in `specs/013-sharing/qa-report.md`; replay every call of contracts/sharing-api-client.md with `curl` against the live local backend with the app's headers and bodies (owner on Plus, invitee, third account, an unverified account, a Free owner): creates (email, link, replay, immutable, plan, quota, unverified, editor, 429), withdraw (again, stale), feed rows and tombstones per status, preview/accept/decline (every 404 cause, already member, repeat, owner plan full, 429), role change (raise on Free, owner 422, stale), remove (repeat), leave (repeat 404, owner 403), user-feed own tombstone and wallet-feed 404 after removal, activity paging and `INVALID_CURSOR` — **QA step** (done 2026-10-05, `qa-report.md`; about 290 HTTP calls against `saveme-backend@26e7c44`)
- [X] T118 Turn the captured answers into fixtures `st/CapturedSharingResponses.kt` (tokens and links replaced by fake values of the same shape) and drive them through the real APIs, engine, handler, appliers and `SharingService` in `st/SharingCapturedResponsesTest.kt` (depends on T117) — **QA step** (done: `CapturedSharingResponses.kt`, `SharingCapturedWireTest`, `SharingCapturedResponsesTest`)
- [X] T119 Static review of the Maestro flows (selectors vs test tags, dialog buttons by text, `network-off`/`network-on` parts, `openLink` with the dev invitation URL, `# deferred` header) and fix what it finds
- [X] T120 Rerun `speckit-contract-check`; confirm `sharing.yaml` and the sharing parts of `sync.yaml` unchanged since `26e7c44`; record drift as FRs or follow-ups in spec §5
- [X] T121 Update README "On-device database" (Room v10; invitations; every wallet re-read once) and "Development backend" (`SHARING_INVITATION_URL` for device runs), and the C9 row of `specs/briefs/README.md`
- [ ] T122 Run quickstart.md manual checks on the dev build — **deferred: E2E on owner's device**
- [ ] T123 Run the full `/speckit-e2e 013-sharing` and write `specs/013-sharing/e2e-report.md` incl. the brief's checkpoints — **deferred: E2E on owner's device**
- [X] T124 Write specs/013-sharing/summary.md

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2 (blocks all stories) → US1 (MVP) → US2 → US3 (needs US1's invite screen and US2's link routing for its flow); US4 after US1 (members screen); US5 after US4 (member actions in the service); US6 after US1 (wallet detail rows); US7 after US6 (plan notes come from activity) → Polish.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; flows last; flow runs deferred.

### Parallel Opportunities

- Phase 2 tests T004–T018 all [P]; T021, T023–T026, T030, T031, T033, T034, T037, T038 in parallel.
- US6 (activity) can run beside US4/US5 once US1's wallet detail change exists (different files except the service; coordinate).
- Within each story the test tasks are [P]; screenshot and flow tasks run beside the next story's tests.

## Parallel Example: User Story 2

```text
T050 service  |  T051 invitation store  |  T052 routing  |  T053 wallet detail  |  T054 Home
then: T055 service  →  T056 screen  →  T057 nav host  →  T058 joining  →  T059 Home card  →  T060 screenshots  →  T061 flows  →  T062 (deferred)
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: an invitation created offline is queued per record in the wallet scope and sent as a PUT; an invitation from another phone is applied from the wallet feed; host tests green.

### Incremental Delivery

1. US1 → US2 (join; the brief's first checkpoint) → US3 (links).
2. US4 manage, US5 leave and purge (second checkpoint), US6 activity, US7 roles everywhere.
3. Polish: host QA with live contract checks and captured fixtures, contract recheck, summary; device E2E and quickstart deferred to the owner.
