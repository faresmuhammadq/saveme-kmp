# Implementation Plan: Wallets & Membership (client)

**Branch**: `006-wallets` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-wallets/spec.md`

## Summary

A new `feature.wallets` context keeps wallets and their members in Room (schema v3) and renders
three MVI screens from it: the wallet list (balances, per-currency totals, archived section), the
wallet detail (balance, members, role-gated owner actions, clash and refusal choices) and the
wallet editor. Every create, edit, archive and delete goes through the spec 004 sync engine via a
`WalletSyncHandler` (record type `wallet`, scope `SyncScope(Wallet, id)`, `canRestore = false`):
PUT for create/save, then POST archive when requested, DELETE with a JSON body for deletion.
Lists are refreshed per screen and on sign-in/foreground (wave 1); transfer of ownership is an
online-only action. Creation is gated by C3's `Entitlements` (`wallets.max`) and by the verified
address.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only: Compose Multiplatform 1.12.1 + Material 3, MVI base (`MviViewModel`), Navigation 3, Ktor 3.6.0, kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite, WorkManager (spec 004, Android), Kermit. No new dependency (research R-18).

**Storage**: Room `saveme.db` v3: new `wallet`, `wallet_member`; `MIGRATION_2_3` in `core/database/Migrations.kt`; exported `shared/schemas/…AppDatabase/3.json`. Queue rows in the existing `pending_change` (record type `wallet`).

**Testing**: kotlin-test, coroutines-test, Turbine; Ktor `MockEngine` contract tests; fakes for `WalletApi`, the engine's DAO/scheduler and `Entitlements`; Room DAO + migration test in `androidHostTest` (Robolectric, same approach as spec 004's migration test); Roborazzi screenshots (EN, AR RTL, 2× font); Maestro via `/speckit-e2e`.

**Target Platform**: Android minSdk 24 / targetSdk 37 (live API); iOS compiles.

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: list of 50 wallets rendered from Room in < 1 s (SC-005); a save is one local transaction + one queue write (< 50 ms); a refresh is ⌈wallets/50⌉ GETs; foreground refresh at most once per 60 s.

**Constraints**:
- P-03 money (`Long`, currency digits, no cross-currency sums).
- P-04 (every rule): local-first writes, base version on every send, clashes to the person, deletion as a queued tombstone.
- Deletion final: no restore path (`canRestore = false`).
- The server's permission matrix is mirrored only to hide controls.
- Merge hotspots shared with C3 and C5/C6 (below).

**Scale/Scope**: 2 tables + 1 migration, 1 API client (7 calls), 1 sync handler, 1 domain service, 3 screens, design-system additions (`WalletCard` options, `WalletAccent` tokens, `WalletIcons`, `ChoiceGrid`), ~75 string keys × 2 languages, 35 acceptance scenarios (28 automatable end to end).

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: all principles PASS or N-A on the spec alone. P-03, P-04 and P-12
are central (money, offline-first wallets, `wallets.max`). No FAIL; proceed.

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | `Wallet`, `WalletMember`, `WalletRole` (owner/editor/viewer), `WalletType` wire values from the glossary; copy keys in contracts/ui-and-e2e.md say "Wallet"/محفظة and "حصالة" for savings; no "account"/"fund", no "tombstone"/"version" in UI |
| P-02 API contract fidelity | PASS | DTOs mirror wallets.yaml (data-model §2) with explicit `null` for required-nullable `icon`/`color`; unknown members ignored; outcomes branch on `code` via the engine and the `RefusalReason` mapping (R-08); headers from `HttpClientFactory`; the engine owns retry per catalogue class; transfer never auto-retried (contracts/wallets-api-client.md) |
| P-03 Money | PASS | `Long` minor units in Room, DTOs, domain and state; `AmountInput` parser with `MoneyFormatter.digitsFor` (R-12); totals grouped by currency, no FX (R-11); liabilities stored negative and shown as owed via `MoneyKind.Liability` |
| P-04 Offline-first | PASS | Rule 1: screens read `WalletView` from Room (R-03). Rule 2: create/edit/archive/delete are `engine.enqueue*` local writes (R-04, R-06). Rule 3: `Uuid.generateV7()` ids. Rule 4: base version on PUT/archive/DELETE; clashes shown with choices (R-07). Rule 5: deletion is a queued deletion; rows removed only on the server's tombstone or for never-sent creates (R-06) |
| P-05 Time | PASS | `archived_at`, `created_at`, refresh stamps as UTC `Instant`; "Updated …" shown through `formatDateTime` in the profile timezone |
| P-06 Localisation & RTL | PASS | ~75 keys in `values/` + `values-ar/` (contracts/ui-and-e2e.md); refusal copy chosen by code, never `detail`; RTL screenshots; amount input accepts the Arabic decimal separator and shows Western digits |
| P-07 Design system only | PASS | Screens use `WalletCard`, `BalanceHero`, `MemberRow`, `RolePill`, `InlineAlert`, `EmptyState`, `AppFab`, fields and dialogs; new visuals (`WalletAccent` tokens, `WalletIcons`, `ChoiceGrid`, `WalletCard` options) are added to `designsystem` first with catalogue entries and screenshots (R-13) |
| P-08 Accessibility | PASS | Status marks are text + icon, not colour; icon/colour choices have content descriptions and 48 dp targets; selection shown by a check mark, not colour only; 2× font screenshots of all three screens |
| P-09 Security & privacy | PASS | No amount, name or payload logged (the handler logs record id and outcome only); other members' emails are never requested or shown (FR-021); wallet tables cleared by `DatabaseCleaner`; queue rows tagged by account (spec 004) |
| P-10 Shared-first architecture | PASS | All in `commonMain`; `feature.wallets.ui` imports only its own domain; domain imports `core`, `account.domain`, `auth.domain`, `billing.domain`; data never imports domain/ui; the verify sheet is reached through an `AppNavHost` callback, not an auth UI import (R-14); C6 extends through `PendingPostings` / `WalletDeletionParticipant` (contracts/wallets-domain.md) |
| P-11 Naming | PASS | `feature.wallets`, `WalletsService`, `WalletSyncHandler`, `WalletsKey`; no codename |
| P-12 Entitlements & billing | PASS | Creation gated by `Entitlements.allowsAnother(QuotaFeature.Wallets, ownedLiveCount)` — features, never the plan name (R-14); no purchase UI; the person's own plan never blocks actions in someone else's wallet |
| P-13 Observability | PASS | Unexpected refusals show the server `request_id` with the generic "Not saved" copy; correlation id per request unchanged |
| P-14 Quality gates | PASS | Contract tests per call and handled code; a test per spec §4 invariant; screenshot tests for new components and screens; migration test; no new dependency |
| P-15 Scope guard | PASS | No FX, no cross-currency transfers; wave 2 sync (batch, change feed, ADR-0017 membership on the user feed) explicitly out of scope |
| P-16 MVI presentation | PASS | Three screens each with `Contract.kt` (State/Intent/Effect/Change + pure `reduce`), a store on `MviViewModel` taking domain lambdas, `…Route`, stateless `…Screen` + preview; reducer and store tests (data-model §5) |

No FAIL. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/006-wallets/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── wallets-api-client.md
│   ├── wallets-domain.md
│   └── ui-and-e2e.md
├── checklists/          # requirements.md (specify), wallets.md (checklist)
├── tasks.md             # /speckit-tasks
└── summary.md           # last task
```

### Source Code (repository root)

```text
shared/
├── schemas/com.fares.saveme.core.database.AppDatabase/3.json     # exported
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/database/{AppDatabase.kt, Migrations.kt, DatabaseCleaner.kt}   # v3, MIGRATION_2_3, clear wallets
    │   ├── designsystem/
    │   │   ├── component/Cards.kt                 # WalletCard: iconKey, accentKey, status, archived, role label
    │   │   ├── component/ChoiceGrid.kt            # NEW picker
    │   │   ├── tokens/AppColors.kt                # WalletAccent tokens
    │   │   ├── icon/WalletIcons.kt                # NEW icon keys → AppIcons
    │   │   └── catalog/CatalogSections.kt         # entries
    │   ├── feature/wallets/                       # NEW
    │   │   ├── data/{WalletApi, WalletRepository, WalletSyncHandler}.kt
    │   │   ├── data/dto/WalletDtos.kt
    │   │   ├── data/local/{WalletEntity, WalletMemberEntity, WalletDao, WalletMemberDao}.kt
    │   │   ├── domain/{WalletsService, WalletsSyncCoordinator, AmountInput, Totals, PendingPostings, WalletDeletionParticipant}.kt
    │   │   ├── domain/model/{Wallet, WalletDraft, WalletView, Member, WalletType, WalletRole, WalletField, Results}.kt
    │   │   ├── ui/{WalletErrorCopy, WalletTypeCopy}.kt
    │   │   ├── ui/list/{WalletsContract, WalletsViewModel, WalletsRoute, WalletsScreen}.kt
    │   │   ├── ui/detail/{WalletDetailContract, WalletDetailViewModel, WalletDetailRoute, WalletDetailScreen}.kt
    │   │   ├── ui/editor/{WalletEditorContract, WalletEditorViewModel, WalletEditorRoute, WalletEditorScreen}.kt
    │   │   └── di/WalletsModule.kt
    │   ├── feature/auth/ui/home/{HomeContract, HomeScreen, HomeRoute, HomeViewModel}.kt   # + Wallets row (hotspot)
    │   ├── navigation/{Routes.kt, AppNavHost.kt}  # 3 keys + entries + verify callback
    │   └── {AppModules.kt, App.kt}                # walletsModule; coordinator foreground hook
    ├── commonMain/composeResources/values{,-ar}/strings.xml   # append-only block "Wallets (spec 006)"
    ├── commonTest/kotlin/com/fares/saveme/feature/wallets/…   # contract, handler, repository, domain, reducers, stores
    └── androidHostTest/kotlin/com/fares/saveme/
        ├── core/database/Migration2To3Test.kt
        ├── feature/wallets/data/WalletDaoTest.kt
        ├── feature/wallets/ui/WalletsScreenshotTest.kt
        └── designsystem/ComponentScreenshotTest.kt
.maestro/006-wallets/                              # flows via /speckit-e2e
.maestro/common/api.js                             # wallet actions for slot B
.specify/scripts/bash/run-e2e.sh                   # network-off / network-on between-actions
```

**Structure Decision**: one new feature package with the standard split; the engine, database and
navigation are touched only additively. Repositories own Room + API; `WalletsService` (domain)
orchestrates gates, the engine and refreshes; stores call domain lambdas only.

## Merge hotspots

| File | Change here | Overlap | Merge note |
|---|---|---|---|
| `core/database/AppDatabase.kt`, `Migrations.kt`, `DatabaseCleaner.kt` | v3, 2 entities/DAOs, `MIGRATION_2_3`, clear | C5/C6 bump to v4/v5 later | Additive; C5 must start from v3 |
| `strings.xml` (both) | append-only block `<!-- Wallets (spec 006) -->` | C3, C5 | Keep both blocks |
| `AppModules.kt`, `App.kt` | `walletsModule`; `WalletsSyncCoordinator` start + `onForeground()` | C3 adds billing equivalents | One list element / one call each |
| `navigation/Routes.kt`, `AppNavHost.kt` | 3 keys + entries + `onRequireVerification` | C3 (`PlanKey`) | Additive |
| `feature/auth/ui/home/*` | `HomeIntent.WalletsClicked`, `HomeEffect.NavigateWallets`, one row | C3 avoided Home; a later Home feature | Minimal; Home tests and screenshots updated on purpose |
| `designsystem/component/Cards.kt`, `CatalogSections.kt`, `ComponentScreenshotTest.kt` | `WalletCard` options (defaults keep today's look), new entries | C3 (`PlanCard`) | Additive; existing baselines must not change |
| `.maestro/common/api.js`, `run-e2e.sh` | wallet actions; `network-off` / `network-on` | C3 (`+plus` seeds, `e2e-between`) | Builds on C3's mechanism; additive |

## Dependencies on C3 (spec 005) code

- `feature/billing/domain/Entitlements.kt`: `Entitlements.allowsAnother`, `QuotaFeature.Wallets`, `QuotaLimit`.
- `core/network/ApiErrorCode.FEATURE_NOT_ENTITLED` (retry class never), `ApiEvent.FeatureNotEntitled` and C3's refresher reacting to it.
- String key `error_feature_not_entitled` (C3 copy, reused for the notice and the refusal).
- `FeatureValue.Quota(Long?)` (null = unlimited), without which the gate would read "0 wallets".
- C3's `run-e2e.sh` `# e2e-between:` two-part flow mechanism.

If C3 is not on `main` when implementation starts, T001 stops the work (spec Assumptions).

## Implementation phases (input to /speckit-tasks)

1. **Setup**: rebase on C3; package, Koin module, string block.
2. **Foundation**: domain models; DTOs, `WalletApi`, contract tests; Room entities/DAOs, v3 migration, schema, cleaner; `WalletSyncHandler` + tests; `WalletRepository` (overlay, refresh merge); `WalletsService`; design-system additions; navigation keys; Home row.
3. **US1 Create**: amount parser, gates, editor (create mode), E2E.
4. **US2 List & detail**: totals, displayed balance, list and detail screens, refresh coordinator, members refresh, E2E.
5. **US3 Edit & clash**: editor (edit mode), currency/type rules, refusal mapping, clash UI, E2E.
6. **US4 Archive & delete**: archive flag, delete flow, hidden-while-waiting, delete clash, E2E.
7. **US5 Members**: members section, role gating, transfer (online), E2E for S1/S2.
8. **Polish**: Arabic review, screenshots, quickstart, contract recheck, E2E run, summary.

## External dependencies (not in this repo)

- C3 (spec 005) merged first.
- Backend spec 010 on the dev stack (`310aa24`, built and committed). B14 (batch, change feed) is not needed.
- Backend contract gaps: none blocking; notes in `summary.md`.

## Complexity Tracking

None.

## Analysis record (`/speckit-analyze`, 2026-10-02)

No CRITICAL or HIGH findings. MEDIUM findings, all fixed in this session:

| ID | Category | Finding | Fix |
|---|---|---|---|
| A1 | Coverage | FR-028 (accessibility) had no task | T072 semantics test |
| A2 | Coverage | SC-005 (50 wallets < 1 s) had no check | quickstart check 10, T076 |
| A3 | Inconsistency | FR-020/US4-S4 said "removed from the phone at once", research R-06 hides until the server confirms | spec reworded: gone from every screen at once, removed on confirmation (never-sent: at once) |
| A4 | Terminology (P-01.2) | Arabic "حساب جاري" for `checking` contains "حساب" (account) | Arabic label "بنكي", flagged for owner review |
| A5 | Underspecification | A save after a waiting archive could drop `archive = true` | T049 asserts it is kept |
| A6 | Underspecification | `SyncState` had no mapping for the engine's `Sending` | data-model: Sending maps to Waiting |
| A7 | Coverage | FR-007 "refresh when the list opens" not in a task | T045 |
| A8 | Coverage | FR-005 liability display not asserted | T047 screenshot includes a credit card |

LOW, left as is: SC-001 (create under 30 s) is a usability target checked by the quickstart, not a test;
the `wallet.account_id` column is defensive (sign-out already empties the table).

## Implementation notes

Recorded while implementing (2026-10-02, owner away; each is an assumed default).

- **Refusal carries the request id.** `core.sync.Refusal` gained an optional `requestId` (set by the engine from the problem body) so a refused wallet can show "Not saved. Reference: …" (P-13.2). Additive: older stored rows decode with `null`.
- **Data may import `domain.model`.** As `ProfileSyncHandler` does, `feature.wallets.data` imports the domain's model classes; it imports no domain service, no `ui`, and no other feature (checked by `WalletsLayeringTest`). `WalletDeletionParticipant` stays in `feature.wallets.domain` as the contract says; the repository receives a plain callback and `walletsModule` resolves the participants at call time, so C6 registering later works.
- **Wallet-removed participants also run on a refresh that drops a wallet**, not only on a server tombstone (the transactions of a wallet the person lost must go too).
- **A forced refresh also nudges the engine** (`engine.syncNow()`) so a pull or opening the list sends waiting changes without waiting for the scheduler.
- **Editor field-error tags**: the contract's `walletEditor.<field>.error` cannot be attached to the text inside a Material text field, so the error text is asserted by its words in the E2E flows; the fields keep their `walletEditor.<field>` tags.
- **Unknown wallet type** shows the cash icon and the "Other" label; the raw value is kept and sent back unchanged.
- **Gone wallet message**: the detail emits the "no longer available" message and then goes back; the message is shown on the detail's own snackbar and may vanish with the screen on a fast pop.
- **Colour palette**: eight accents (`#4C6FD6`, `#2C7A5B`, `#8F5E12`, `#A9503A`, `#6B52B8`, `#1F7F8C`, `#B04375`, `#5B6472`), built from existing tonal families plus violet, teal and rose; owner design review pending (P-07.3).
- **Contract recheck (T077)**: `wallets.yaml` is unchanged since `310aa24`. Backend `error-catalog.yaml` gained `CATEGORY_IN_USE` and `CURRENCY_MISMATCH` after it; neither is returned by a wallet endpoint, so no change here.
- **Koin cycle at start (found by the first E2E run).** `WalletRepository`'s definition resolved `SyncEngine` eagerly while the engine is built from every `SyncHandler`, `WalletSyncHandler` included, so the app overflowed the stack on launch. The repository now resolves the engine when it checks the queue; `AppGraphTest` starts the real graph so a cycle fails a host test.
- **Select field accessibility.** `AppSelectField`'s click overlay covers the text field, so accessibility services saw an unlabelled button with no value (the E2E could not find the preselected "EGP"). The overlay now carries the label as its description and the value as its text and state (`SelectFieldSemanticsTest`); this also fixes the profile screen's pickers.
- **Dialog test tags.** Confirmation dialogs open in their own window, where `testTagsAsResourceId` (set by `MainActivity` on its window) does not apply, so `walletDetail.confirm` / `walletDetail.cancel` are invisible to Maestro; the flows tap the dialog buttons by their text.
- **E2E deferred.** The owner moved the device E2E run and the quickstart checks (T076) to their physical phone; partial emulator results are in `e2e-report.md`.
