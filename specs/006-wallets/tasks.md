---
description: "Task list for 006-wallets"
---

# Tasks: Wallets & Membership (client)

**Input**: Design documents from `specs/006-wallets/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (wallets-api-client.md, wallets-domain.md, ui-and-e2e.md), quickstart.md; constitution v1.3.0; spec 005 (C3) merged on `main`

**Tests**: Included. P-14 requires contract tests per endpoint and handled code, a test per invariant (spec §4) and screenshot tests per screen and component; P-16 requires reducer + ViewModel tests per screen. Write each story's tests first and confirm they fail.

**Organization**: Phase 2 builds the storage, API, sync handler and design-system pieces every story uses. US1 (create, with a basic list) is the MVP; US2 completes the list and adds the detail; US3–US5 add editing and clashes, archive/delete, and members.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US5 from spec.md

## Path Conventions

- Shared code: `shared/src/commonMain/kotlin/com/fares/saveme/…` (`cm/…`)
- Shared tests: `shared/src/commonTest/kotlin/com/fares/saveme/…` (`ct/…`)
- Host (JVM) tests and screenshots: `shared/src/androidHostTest/kotlin/com/fares/saveme/…` (`ht/…`)
- Strings: `shared/src/commonMain/composeResources/values/strings.xml` **and** `values-ar/strings.xml`
- Wallets feature: `cm/feature/wallets/…` (`fw/…`), tests `ct/feature/wallets/…` (`tw/…`)

## Standing rules for every task

- Strings in both languages (P-06), design-system components only (P-07), layering (P-10: wallets UI → wallets domain → wallets data; other features only through their `domain`), naming (P-11).
- Money is `Long` minor units everywhere (P-03); never `Double`/`Float`.
- Never log wallet names, amounts or payloads; log record id, outcome and request id only (P-09.2).
- **MVI screen recipe (P-16)**, as in specs 001–005: `…Contract.kt` (immutable State, sealed Intent/Effect/Change, pure top-level `reduce`), `…ViewModel.kt` on `MviViewModel` taking domain lambdas (never repositories), `…Route.kt`, stateless `…Screen.kt` + `@Preview`; tests `…ReducerTest.kt` + `…ViewModelTest.kt` with `runMviTest`.
- **Merge hotspots** (plan.md): edits to `AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt`, `AppModules.kt`, `App.kt`, `Routes.kt`, `AppNavHost.kt`, Home and `Cards.kt` are additive only. New strings go in one append-only block at the end of each `strings.xml` under `<!-- Wallets (spec 006) -->`.
- Every interactive element gets the `testTag` from contracts/ui-and-e2e.md.

---

## Phase 1: Setup

**Purpose**: confirm the C3 prerequisite and create the package.

- [X] T001 Rebase `006-wallets` on `main` and confirm spec 005 (C3) is merged: `cm/feature/billing/domain/Entitlements.kt` exposes `allowsAnother` and `QuotaFeature.Wallets`, `ApiErrorCode.FEATURE_NOT_ENTITLED` and `ApiEvent.FeatureNotEntitled` exist, `FeatureValue.Quota(limit: Long?)` reads `null` as unlimited, and `run-e2e.sh` supports `# e2e-between:`. If any is missing, stop and report (spec Assumptions)
- [X] T002 Create `fw/di/WalletsModule.kt` with an empty `val walletsModule = module { }` and add it to the feature module list in `cm/AppModules.kt` (hotspot: one list element)
- [X] T003 [P] Add the string block `<!-- Wallets (spec 006) -->` with every key from contracts/ui-and-e2e.md "Copy" to the end of `values/strings.xml` and `values-ar/strings.xml` (Arabic: محفظة, حصالة for savings, Western digits, no "token"/"version"/"tombstone")

---

## Phase 2: Foundational (blocking)

**Purpose**: domain types, wire client, Room v3, the sync handler, the repository, design-system pieces, navigation.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T004 [P] Model tests in `tw/domain/model/WalletModelTest.kt`: `WalletType`/`WalletRole` wire round-trips; an unknown type value maps to an "unknown" type that is never re-encoded differently; `WalletDraft` serialises `icon`/`color` as explicit nulls; `sameFieldsAs` ignores `archive`; `WalletField.fromPointer` for all six pointers (data-model §3)
- [X] T005 [P] Contract tests in `tw/data/WalletApiContractTest.kt` (Ktor `MockEngine`, fixtures from `wallets.yaml` and backend spec 010 QA evidence) covering every row of contracts/wallets-api-client.md: request method/path/body for list, get, put (exact members, explicit nulls), archive, delete (**body on DELETE** with JSON content type), members, transfer; 200/201 decoding with an unknown member; 409 with live and tombstone `current`; 422 field codes (`IMMUTABLE_FIELD@/currency`, `@/type`, `OUT_OF_RANGE@/name`, `INVALID_ENUM_VALUE@/user_id`); 403 `FORBIDDEN`, `EMAIL_NOT_VERIFIED` (publishes `ApiEvent.EmailNotVerified`), `FEATURE_NOT_ENTITLED` (publishes `ApiEvent.FeatureNotEntitled`); 404; 503 with `retry_after_seconds`; offline; 401 → `SessionEnded` (P-14.3)
- [X] T006 [P] Handler tests in `tw/data/WalletSyncHandlerTest.kt` with a fake `WalletApi` (research R-04): create → PUT version 0; edit → PUT with base; archive-only (fields equal the row) → no PUT, archive with base; edit+archive → PUT then archive with the PUT's version; PUT failure never archives; delete → DELETE with base; unknown icon/colour from the row sent back unchanged; `applyServerCopy` upserts a live wallet and removes a tombstone with its members and calls every `WalletDeletionParticipant`; `onDiscarded` removes a `version = 0` placeholder only; `refusalFields` maps pointers; `canRestore == false`
- [X] T007 [P] DAO test in `ht/feature/wallets/data/WalletDaoTest.kt` (Robolectric, in-memory Room): upsert/observe ordering (active by `created_at`, archived after), member set replacement, delete with members, `deleteAll`
- [X] T008 [P] Migration test in `ht/core/database/Migration2To3Test.kt`: a v2 file built from the exported v2 definitions (as spec 004's migration test) with a profile row and a `pending_change` row migrates to v3; both rows survive; `wallet` and `wallet_member` exist and Room validates the schema
- [X] T009 [P] Repository tests in `tw/data/WalletRepositoryTest.kt` (fake API, in-memory DAO fakes): placeholder row for a create (owner, balance = opening, version 0); `WalletView` overlay from a queue row (pending payload, clash, refusal); `refreshAll` merges only when every page succeeds; an unlisted wallet with `version > 0` and no queue row is removed with its members; an unlisted wallet with a queue row and any `version = 0` row are kept (FR-008); `refreshMembers` replaces the set; 404 on `get` removes the wallet unless a change waits
- [X] T010 [P] Extend the `DatabaseCleaner` test (find it under `ht/core/database/`) so sign-out empties `wallet` and `wallet_member` (FR-026)
- [X] T011 [P] Copy tests in `tw/ui/WalletCopyTest.kt`: every `WalletType` (and unknown) has an English and Arabic label (savings → "حصالة"); every `RefusalReason` and field error code maps to a wallet key, never a generic message (P-06.5)

### Implementation

- [X] T012 [P] Domain models in `fw/domain/model/` (`Wallet`, `WalletDraft`, `WalletView`, `SyncState`, `RefusalReason`, `Member`, `WalletType`, `WalletRole`, `WalletField`, `CurrencyTotal`, `WalletList`, result types) per data-model §3
- [X] T013 [P] DTOs in `fw/data/dto/WalletDtos.kt` per data-model §2 (`encodeDefaults`/explicit nulls for `icon` and `color` in `WalletWriteRequest`)
- [X] T014 `fw/data/WalletApi.kt`: the seven calls of contracts/wallets-api-client.md on `ApiClient` (`callPage` for lists, DELETE with `contentType(Json)` + `setBody`) (depends on T013)
- [X] T015 [P] Room entities and DAOs in `fw/data/local/` (`WalletEntity`, `WalletMemberEntity`, `WalletDao`, `WalletMemberDao`) per data-model §1
- [X] T016 `cm/core/database/AppDatabase.kt` version 3 with both entities and DAO accessors; `MIGRATION_2_3` in `cm/core/database/Migrations.kt`; `buildAppDatabase` adds it; build once to export `shared/schemas/com.fares.saveme.core.database.AppDatabase/3.json` (depends on T015)
- [X] T017 `cm/core/database/DatabaseCleaner.kt`: delete wallet members and wallets (depends on T016)
- [X] T018 [P] `fw/domain/PendingPostings.kt` (interface + `ZeroPendingPostings`) and `fw/domain/WalletDeletionParticipant.kt`; bind `ZeroPendingPostings` in `walletsModule` (contracts/wallets-domain.md)
- [X] T019 `fw/data/WalletRepository.kt`: Room reads as flows joined with the engine's `observe("wallet", id)` into `WalletView` inputs, placeholder insert, server-copy upsert/remove (+ participants), `refreshAll` (paged, all-or-nothing merge, `sync_scope` key `wallets`), `refreshMembers`, `refreshOne` (research R-03, R-09) (depends on T014, T016)
- [X] T020 `fw/data/WalletSyncHandler.kt` per research R-04 and register `single { … } bind SyncHandler::class` in `walletsModule` (depends on T019)
- [X] T021 `fw/domain/WalletsService.kt` skeleton implementing `observeList`, `observeWallet`, `observeMembers`, `refresh`, `refreshMembers` over the repository and engine; other members added per story (contracts/wallets-domain.md); register in `walletsModule` (depends on T019)
- [X] T022 [P] `fw/ui/WalletTypeCopy.kt` and `fw/ui/WalletErrorCopy.kt` (type labels, refusal reasons, field errors) to pass T011
- [X] T023 [P] Design system (research R-13): `WalletAccent` tokens in `cm/designsystem/tokens/AppColors.kt` with wire keys; `cm/designsystem/icon/WalletIcons.kt`; `cm/designsystem/component/ChoiceGrid.kt` (48 dp, content descriptions, check-mark selection); catalogue entries in `cm/designsystem/catalog/CatalogSections.kt`; screenshots in `ht/designsystem/ComponentScreenshotTest.kt` (EN, AR RTL, 2×). Check the "Save Me Mobile Design System" canvas first; if it has no wallet palette, build from existing tonal families and list the palette in `summary.md` for the owner's design review (P-07.3)
- [X] T024 [P] `WalletCard` options in `cm/designsystem/component/Cards.kt`: `iconKey`, `accentKey` (unknown → defaults), status slot (waiting / changed / not saved, text + icon), archived style, role label; defaults keep today's look so existing baselines don't change; new catalogue states + screenshots (P-07.3, P-08.3)
- [X] T025 Navigation: `WalletsKey`, `WalletDetailKey(walletId)`, `WalletEditorKey(walletId: String? = null)` in `cm/navigation/Routes.kt`; entries in `cm/navigation/AppNavHost.kt` passing `onRequireVerification` (shows the existing `EmailNotVerifiedSheet`) — Routes are wired as each screen lands (research R-16)
- [X] T026 Home row: `HomeIntent.WalletsClicked`, `HomeEffect.NavigateWallets`, a "Wallets" `AppListItem` tagged `home.wallets` in `cm/feature/auth/ui/home/{HomeContract,HomeViewModel,HomeScreen,HomeRoute}.kt`; extend the Home reducer/VM tests; re-record the Home screenshots on purpose (depends on T025)

**Checkpoint**: storage, API, handler and navigation ready; `./gradlew :shared:testAndroidHostTest` green for T004–T011.

---

## Phase 3: User Story 1 - Create a wallet, even offline (Priority: P1) 🎯 MVP

**Goal**: create a wallet locally (any connection), see it in a basic list, and have it sync.

**Independent Test**: offline, create "Cash" 250.00 EGP → card with "Waiting to sync"; online → mark clears and the server has it.

### Tests for User Story 1

- [X] T027 [P] [US1] `tw/domain/AmountInputTest.kt` (research R-12): EGP "250" → 25000, "250.5" → 25050; JPY no decimals; KWD 3 digits; too many digits → error with the digit count; Arabic decimal separator; asset "−12.00" → −1200; liability owed "1000" → −100000 and a minus refused; ±9,007,199,254,740,991 bounds; empty → error
- [X] T028 [P] [US1] `tw/domain/WalletsServiceCreateTest.kt`: not verified → `NotVerified`, nothing queued (FR-025); not entitled (owned live count incl. archived, excl. pending deletion) → `NotEntitled` (FR-024); valid → UUIDv7 id, placeholder row, `engine.enqueue("wallet", id, SyncScope(Wallet, id), 0, payload)`; name trimmed, blank/101 chars → `Invalid` (FR-009, FR-014); `canCreateAnother` with `QuotaLimit.Unlimited` → true
- [X] T029 [P] [US1] Editor reducer + VM tests (create mode) in `tw/ui/editor/WalletEditorReducerTest.kt`, `WalletEditorViewModelTest.kt`: currency preselected from the profile default (US1-S2); type choice switches the amount label to "Amount owed" for liabilities (US1-S5); field errors shown and save disabled for blank name (US1-S6); `RequireVerification` and `NotEntitled` effects; `Saved(id)` then back
- [X] T030 [P] [US1] List reducer + VM tests (basic) in `tw/ui/list/WalletsReducerTest.kt`, `WalletsViewModelTest.kt`: cards from `observeList`, waiting mark (US1-S3), empty state (US2-S6), create blocked by the gate shows the notice (US1-S7), unverified create → `RequireVerification` (US1-S8)

### Implementation for User Story 1

- [X] T031 [P] [US1] `fw/domain/AmountInput.kt` (pure; uses `MoneyFormatter.digitsFor`) to pass T027
- [X] T032 [US1] `WalletsService.create` and `canCreateAnother` (C3 `Entitlements.allowsAnother(QuotaFeature.Wallets, n)`, `SessionManager` for `emailVerified`, `ProfileSyncer` default currency) in `fw/domain/WalletsService.kt`; after enqueue ask the engine to drain as `ProfileSyncer.saveLocally` does (depends on T021, T031)
- [X] T033 [US1] Wallet editor, create mode: `fw/ui/editor/{WalletEditorContract,WalletEditorViewModel,WalletEditorRoute,WalletEditorScreen}.kt` (type `ChoiceGrid`, name, currency `AppSelectField` over `knownCurrencies`, amount, icon and colour `ChoiceGrid`s, Save; tags from contracts/ui-and-e2e.md); Koin `viewModel`; wire `WalletEditorKey(null)` in `AppNavHost`
- [X] T034 [US1] Wallet list, basic: `fw/ui/list/{WalletsContract,WalletsViewModel,WalletsRoute,WalletsScreen}.kt` (active `WalletCard`s with marks and role label, `EmptyState`, `AppFab` "New wallet", not-entitled `InlineAlert`); wire `WalletsKey` and Home → Wallets
- [X] T035 [P] [US1] Screenshots in `ht/feature/wallets/ui/WalletsScreenshotTest.kt`: editor (asset, liability, field error) and list (empty, one waiting wallet) in EN, AR RTL, 2× font
- [X] T036 [US1] E2E plumbing: add `createWallet`, `getWallet`, `listWallets`, `saveWallet`, `archiveWallet`, `deleteWallet` actions to `.maestro/common/api.js` (`deleteWallet` through `http.request` with method `DELETE` and a JSON body); add `network-off` / `network-on` between-actions to `.specify/scripts/bash/run-e2e.sh` (`adb reverse --remove tcp:<port>` / `adb reverse tcp:<port> tcp:<port>`) on top of C3's `# e2e-between:` (research R-17)
- [ ] T037 [US1] Run `/speckit-e2e 006-wallets` for US1: flows `.maestro/006-wallets/US1-S1-…`, `S2`, `S3` (three parts with `network-off`/`network-on`), `S4` (Arabic "حصالة"), `S5`, `S6`, `S8` (`unverified-user` seed); US1-S7 recorded as manual (`external`) **Not run here**: flows `.maestro/006-wallets/US1-*` written (S1, S2, S3 three parts, S4, S5, S6, S8; S7 manual `external`); the E2E run is a separate step. **deferred: E2E on owner's device** (partial emulator results in `e2e-report.md`).

**Checkpoint**: MVP — a person can create wallets online and offline and see them listed.

---

## Phase 4: User Story 2 - See my wallets and what they hold (Priority: P1)

**Goal**: full list (totals, archived section, refresh) and the wallet detail.

**Independent Test**: three API-seeded wallets in two currencies → two totals; open one → detail with balance, type, currency, members.

### Tests for User Story 2

- [X] T038 [P] [US2] `tw/domain/TotalsTest.kt`: per-currency sums in `Long`, liabilities negative, archived and hidden wallets excluded, default currency first then by code, never mixed currencies (FR-003, SC-004)
- [X] T039 [P] [US2] `tw/domain/DisplayedBalanceTest.kt`: server balance + (pending opening − server opening) + `PendingPostings` delta; placeholder → pending opening (FR-004, research R-10)
- [X] T040 [P] [US2] `tw/domain/WalletsSyncCoordinatorTest.kt`: refresh on sign-in; foreground at most once per 60 s; forced refresh ignores the floor; nothing when signed out (FR-007)
- [X] T041 [P] [US2] Extend list tests: loading before the first refresh vs empty after it; totals; archived section; pull to refresh; offline "Updated …"; refresh failed message keeps cards (FR-002, FR-007)
- [X] T042 [P] [US2] Detail reducer + VM tests in `tw/ui/detail/WalletDetailReducerTest.kt`, `WalletDetailViewModelTest.kt`: balance, type, currency, archived pill, members, waiting mark; owner sees Edit/Archive/Delete, editor and viewer see none (FR-022, US3-S8); wallet gone → `NavigateBack` + message (Edge Cases); members refreshed on open

### Implementation for User Story 2

- [X] T043 [US2] Totals and displayed balance in `fw/domain/Totals.kt` and `WalletsService.observeList`/`observeWallet` (depends on T038, T039)
- [X] T044 [US2] `fw/domain/WalletsSyncCoordinator.kt` (sign-in + foreground with a 60 s floor) registered in `walletsModule`, started and called from `cm/App.kt`'s existing foreground hook next to the account and billing coordinators (hotspot: one call each)
- [X] T045 [US2] Complete the list screen: forced refresh when the list opens, totals as `BalanceHero` per currency (`wallets.total.<CUR>`), "Archived" section, pull to refresh, loading skeleton, "Updated …" offline, `SyncBanner` when offline with waiting changes, refresh-failed note
- [X] T046 [US2] Wallet detail: `fw/ui/detail/{WalletDetailContract,WalletDetailViewModel,WalletDetailRoute,WalletDetailScreen}.kt` (balance hero, status line, archived pill, members via `MemberRow`/`RolePill`, owner action rows, "No transactions yet." empty state); wire `WalletDetailKey`
- [X] T047 [P] [US2] Screenshots: list with totals and archived section (including a credit card shown as an amount owed in neutral ink, FR-005), offline list, detail (owner, viewer) in EN, AR RTL, 2× font
- [ ] T048 [US2] Run `/speckit-e2e 006-wallets` for US2: `US2-S1`…`S8` (seed wallets through api.js slot B; S3 and S8 use `network-off`; S4 slot B `saveWallet` + pull to refresh; S5 slot B `deleteWallet` + refresh) **Not run here**: flows `US2-S1`…`S8` written (S3 three parts, S8 two parts); the E2E run is a separate step. **deferred: E2E on owner's device** (partial emulator results in `e2e-report.md`).

**Checkpoint**: list and detail complete; US1 and US2 work independently.

---

## Phase 5: User Story 3 - Edit a wallet, and decide when it clashes (Priority: P1)

**Goal**: owner edits offline; clashes and refusals are shown with choices.

**Independent Test**: rename offline; rename via slot B; online → both names with "Keep mine" / "Use the other device's".

### Tests for User Story 3

- [X] T049 [P] [US3] `tw/domain/WalletsServiceSaveTest.kt`: save enqueues with the editor's base version even if a refresh moved the row meanwhile (FR-010); a second save collapses; non-owner → `NotOwner`; unverified → `NotVerified`; unknown icon/colour preserved (FR-015); a save after a waiting archive keeps `archive = true` in the collapsed draft (Edge Cases); `tryAgain` re-enqueues the unchanged payload; `discard` of a never-synced create removes the wallet
- [X] T050 [P] [US3] `tw/domain/WalletSyncStateTest.kt`: queue row → `SyncState` (Waiting; Clash with the differing fields of `clash_json`; Clash `deletedElsewhere` for a tombstone; Refused with `RefusalReason` per code: `EMAIL_NOT_VERIFIED`, `FEATURE_NOT_ENTITLED`, `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_FAILED` (fields), other (request id)) (FR-016–FR-018, research R-07, R-08)
- [X] T051 [P] [US3] Editor tests (edit mode): type shown read-only (FR-011); currency read-only with the note when `currencyLocked` (FR-012); refusal field errors (`IMMUTABLE_FIELD@/currency`) shown next to the field (US3-S4)
- [X] T052 [P] [US3] Detail VM tests: `KeepMine`/`UseTheirs`/`AcceptDeletion`/`TryAgain`/`Discard` call the service; a tombstone clash offers only "Accept the deletion" (FR-017)

### Implementation for User Story 3

- [X] T053 [US3] `WalletsService.save`, `keepMine`, `useTheirs`, `tryAgain`, `discard` and the `SyncState` mapping (depends on T050)
- [X] T054 [US3] Editor edit mode: opened with `WalletEditorKey(id)`, captures the shown version as the base, read-only type and locked currency, field errors from refusals
- [X] T055 [US3] Detail status area: `InlineAlert` for waiting / changed on another device (this phone vs other device lines for each differing field) / deleted elsewhere / not saved with reason, and the choice buttons with tags
- [X] T056 [P] [US3] Screenshots: editor edit mode (locked currency), detail clash, deleted-elsewhere, not-saved states in EN, AR RTL, 2× font
- [ ] T057 [US3] Run `/speckit-e2e 006-wallets` for US3: `US3-S1`, `S2`, `S5` (slot B saves while the detail is open, phone edits), `S6`, `S7` (slot B deletes, phone edits → only "Accept the deletion"); S3, S4, S8 manual (`not-built`) **Not run here**: flows `US3-S1`, `S2`, `S5`, `S6` (keep mine and use theirs), `S7` written; S3, S4, S8 stay manual (`not-built`); the E2E run is a separate step. **deferred: E2E on owner's device** (partial emulator results in `e2e-report.md`).

**Checkpoint**: editing with clash and refusal handling works.

---

## Phase 6: User Story 4 - Archive or delete a wallet (Priority: P2)

**Goal**: owner archives (one-way) or deletes (final), offline too.

**Independent Test**: archive → "Archived" section, out of totals; delete → gone from phone and server.

### Tests for User Story 4

- [X] T058 [P] [US4] `tw/domain/WalletsServiceArchiveDeleteTest.kt`: archive sets `archive = true` in the queued draft with the row's version; delete of a never-synced wallet drops the queue row and the placeholder with nothing to send (US4-S5); delete of a synced wallet hides it while `Waiting`/`Sending` (R-06); a delete `Clash` or `Refused` makes it visible again with its mark (US4-S6); the server tombstone removes rows and calls participants
- [X] T059 [P] [US4] Detail reducer + VM tests: archive and delete confirmations (copy names the wallet, says final / can't be undone), confirm → service call → back on delete; delete clash shows "Delete anyway" / "Keep the wallet"; no "unarchive" control

### Implementation for User Story 4

- [X] T060 [US4] `WalletsService.archive` and `delete` (research R-06), hidden-while-waiting rule in `observeList`/`observeWallet`
- [X] T061 [US4] Detail actions: Archive and Delete rows, `AppAlertDialog` confirmations, delete-clash choices (`walletDetail.deleteAnyway`, `walletDetail.keepWallet`)
- [X] T062 [P] [US4] Screenshots: archive and delete dialogs, archived wallet detail, delete clash in EN, AR RTL, 2× font
- [ ] T063 [US4] Run `/speckit-e2e 006-wallets` for US4: `US4-S1`…`S6` (S5 three parts with `network-off`/`network-on`, then slot B `listWallets` has no such wallet; S6 slot B saves, phone deletes) **Not run here**: flows `US4-S1`…`S6` written (S5 three parts); the E2E run is a separate step. **deferred: E2E on owner's device** (partial emulator results in `e2e-report.md`).

**Checkpoint**: full wallet lifecycle on the phone.

---

## Phase 7: User Story 5 - See who is in a wallet (Priority: P2)

**Goal**: read-only member list, role gating, transfer of ownership when possible.

**Independent Test**: detail shows "You · Owner"; no transfer action with a single member.

### Tests for User Story 5

- [X] T064 [P] [US5] `tw/domain/TransferOwnershipTest.kt` (research R-15): offline → `Offline`, nothing changes; 200 → wallet stored with `my_role: editor`, members refreshed; 422 `INVALID_ENUM_VALUE@/user_id` → `NoLongerMember` + refresh; 409 → `Changed` + refresh; 403 → `NotOwner`; never retried automatically
- [X] T065 [P] [US5] Detail tests: members ordered as the server lists them, "You" marker, placeholder for a null display name, no email anywhere in state (FR-021); transfer offered only to the owner with another active member and no waiting change (FR-023); confirm sheet lists candidates

### Implementation for User Story 5

- [X] T066 [US5] `WalletsService.transferOwnership` (online only) in `fw/domain/WalletsService.kt`
- [X] T067 [US5] Members section and transfer `AppBottomSheet` + confirmation in the detail screen; messages for offline / no longer a member / changed
- [X] T068 [P] [US5] Screenshots: members section (owner only; owner + editor + viewer from a literal state), transfer sheet in EN, AR RTL, 2× font
- [ ] T069 [US5] Run `/speckit-e2e 006-wallets` for US5: `US5-S1`, `S2`; S3–S5 manual (`not-built`: needs a second member, C9) **Not run here**: flows `US5-S1`, `S2` written; S3–S5 stay manual (`not-built`); the E2E run is a separate step. **deferred: E2E on owner's device** (partial emulator results in `e2e-report.md`).

**Checkpoint**: all user stories functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T070 [P] Arabic copy review of the wallet block (glossary terms, حصالة, Western digits, RTL of amounts inside sentences)
- [X] T071 [P] Confirm the layering/import-rule test (if present under `ht/`) covers `feature.wallets`; otherwise add a source-scan assertion that `feature/wallets/ui` imports no `data` package and `feature/wallets/data` no `domain`/`ui` (P-10)
- [X] T072 [P] Accessibility semantics test in `ht/feature/wallets/ui/WalletsAccessibilityTest.kt`: sync marks, role and archived state exposed as text; icon/colour choices and icon-only buttons have content descriptions; touch targets ≥ 48 dp; no clipped text at 2× font on list, detail and editor (FR-028, P-08)
- [X] T073 Trace every spec §4 invariant to its passing test (list them in `summary.md`) (P-14.2)
- [X] T074 [P] `./gradlew :shared:compileKotlinIosSimulatorArm64` (iOS compiles)
- [X] T075 `./gradlew build allTests` and `:shared:verifyRoborazziAndroidHostTest` green; schema `3.json` committed with the change
- [ ] T076 Run quickstart.md manual checks 1–10 on the dev build; record deviations in plan.md "Implementation notes" **Not run here**: needs a device; covered by the automated tests plus the E2E flows; run before release. **deferred: E2E on owner's device**.
- [X] T077 Rerun `speckit-contract-check`; confirm `wallets.yaml` is unchanged since `310aa24`; record drift as FRs or follow-ups in spec §5
- [ ] T078 Run the full `/speckit-e2e 006-wallets` (brief integration checkpoint: US1-S3, US2-S4, US3-S5) and write `specs/006-wallets/e2e-report.md`; no `FAIL (app)` or `FAIL (env)` attributable to the contract (SC-007) **Not run here**: the full E2E run and `e2e-report.md` are a separate step. **deferred: E2E on owner's device** (partial emulator results in `e2e-report.md`).
- [X] T079 Update the C4 row in `specs/briefs/README.md` ("Client state") to built
- [X] T080 Write `specs/006-wallets/summary.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (C3 gate); T002, T003 after.
- **Foundational (Phase 2)**: after Setup; blocks all stories. Inside: T012/T013/T015 → T014, T016 → T017, T019 → T020, T021; T025 → T026.
- **US1 (Phase 3)**: after Phase 2.
- **US2 (Phase 4)**: after Phase 2; extends the list screen from T034, so run after US1 when staffed by one person.
- **US3 (Phase 5)**: after US2's detail (T046).
- **US4 (Phase 6)**: after US2's detail (T046); independent of US3 except the shared detail files.
- **US5 (Phase 7)**: after US2's detail (T046).
- **Polish (Phase 8)**: after all stories; T080 is last.

### User Story Dependencies

- US1: none beyond Foundation (MVP).
- US2: uses US1's list screen.
- US3, US4, US5: use US2's detail screen; otherwise independent of each other.

### Within Each User Story

- Tests first, failing; domain before UI; screenshots after the screen; E2E last.

### Parallel Opportunities

- Phase 2 tests T004–T011 all [P]; T012, T013, T015, T018, T022, T023, T024 in parallel.
- Within each story the test tasks are [P]; screenshot tasks run beside the next story's tests.
- US3, US4 and US5 can proceed in parallel after T046 if different people own `WalletDetailScreen.kt` sections (merge carefully).

## Parallel Example: User Story 1

```text
T027 AmountInputTest   |  T028 WalletsServiceCreateTest  |  T029 editor tests  |  T030 list tests
then: T031 AmountInput  →  T032 service.create  →  T033 editor  +  T034 list  →  T035 screenshots  →  T036/T037 E2E
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. Stop and validate: create offline and online, basic list, US1 E2E.

### Incremental Delivery

1. US1 → US2 (the brief's "list with balances and totals", detail as C6's entry point).
2. US3 (edits and clashes: the checkpoint's stale-edit scenario).
3. US4 → US5.
4. Polish: contract recheck, full E2E report, summary.
