# Implementation Plan: Sharing & Invitations (client)

**Branch**: `013-sharing` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-sharing/spec.md`

## Summary

A new bounded context `feature.sharing` (data, domain, ui, di) lets an owner invite by email or by
link with a role, offline-first: invitations are phone-created syncable records (UUIDv7) sent per
record through the existing sync engine (`PUT`/`DELETE /wallets/{id}/invitations/{iid}`, never
batched) and read back from the wallet feed (`invitation` rows, new applier). A link invitation's
one-time `meta.share_url` is captured by the handler and held only in memory for sharing (system
share sheet, sensitive clipboard); a lost link is replaced. Invitees open `/invite?token=` links
(same per-flavor host as the spec 003 mail links) into an invitation screen — preview, join,
decline — with the secret kept in memory, including across sign-in. Role changes, removals and
leaving are online-only actions (as C4's ownership transfer), applied to the C4 `wallet_member`
table through a new wallets domain API `WalletMemberships`; own member rows now also set the
wallet's role. Losing access purges invitations too (new wallet-deletion participant). A
members screen, an invite screen, an invitation screen and an activity screen (online-only, not
stored) are added; the wallet detail, transactions list and Home gain small hook-driven additions
(pending count, viewer and plan notes, held-invitation card). Room 9 → 10 adds `invitation` and
forgets every wallet scope's position once. Core gains four additive pieces (`Problem.feature`,
`Refusal.feature`, `ApiClient.callWithMeta`, `SyncEngine.pendingIn`); no engine behaviour change,
no new dependency.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Multiplatform), JVM target 11

**Primary Dependencies**: existing stack only (Compose Multiplatform 1.12.1, MVI base, Ktor 3.6.0,
kotlinx.serialization, coroutines, kotlinx-datetime, Koin 4.2.2, Room KMP 2.8.5 + bundled SQLite,
Navigation 3, Kermit). Android share/clipboard from the framework. **No new dependency.**

**Storage**: Room `saveme.db` v10: `invitation`; every wallet scope's feed cursor, read stamp and
position cleared once (data-model §5). Secrets, links, previews and activity are memory-only.

**Testing**: kotlin-test, coroutines-test, Turbine, Ktor `MockEngine` (contract tests from the
`sharing.yaml` examples), fakes in `commonTest`; Room DAO, migration 9 → 10, sign-out wipe, graph
and layering tests and Roborazzi in `androidHostTest`; captured live-backend answers
(`CapturedSharingResponses.kt`). No emulator, Maestro or device run (owner policy); Maestro flows
written and marked deferred.

**Target Platform**: Android minSdk 24 / targetSdk 37; iOS compiles (no-op `LinkSharer`).

**Project Type**: mobile-app (KMP: `:shared` + `:androidApp` + `iosApp`)

**Performance Goals**: members screen with 5 members and 20 pending invitations renders from Room
within 1 s; an activity page of 50 resolves names from the phone within 300 ms on host; the
invitation preview shows within the network round trip (no extra reads).

**Constraints**: P-04 (invitations), P-09.2 (token, link, address never stored/logged/shown),
P-12.1 (gate on features), backend uniform answers (one message for every token 404), token calls
and member actions never auto-retried, owner's plan decides (the server is final), share text
without amounts or wallet name.

**Scale/Scope**: 1 migration, 1 entity + DAO, 3 APIs, 1 sync handler, 1 feed applier, 1 service +
5 pure parts + 3 memory holders, 4 MVI screens, wallet detail / transactions / Home additions, 4
hook/API additions in other features (`WalletMemberships`, `SharingGlance`, `observeJoining`,
`HeldInvitationGlimpse`), 4 small core additions, manifest filters, ~90 strings EN/AR.

## Constitution Check

*Constitution v1.3.0 (`.specify/memory/constitution.md`). GATE: before Phase 0 and again after Phase 1.*

**Pre-design (Phase 0 entry)**: no principle fails on the spec alone. Sharing is MVP (brief C9,
wave 3); no capability from the scope guard is added; no new dependency; the only plan gates are
`sharing.enabled` and `sharing.max_members_per_wallet` (features, never the plan label).

**Post-design (after Phase 1)**:

| Principle | Result | Evidence / justification |
|---|---|---|
| P-01 Ubiquitous language | PASS | Code: `Invitation`, `WalletMember…`, `ActivityEntry…`, `InvitedRole`; UI "Invitation"/"دعوة", "Members"/"الأعضاء", "Activity"/"النشاط" (contracts/ui-and-e2e.md); the sharing string test rejects "invite link", "join request", "participant", "collaborator", "friend", "group", "household", "family", "token" |
| P-02 API contract fidelity | PASS | Shapes from `sharing.yaml` v1.9 (contracts/sharing-api-client.md A1–A10); bodies carry exactly the declared members (explicit null address; `{token}` in the body only); unknown members ignored, unknown actions skipped; branches on code, then `feature` / pointer; standard headers; token calls and member actions never retried automatically; engine retries invitations per catalogue class |
| P-03 Money | N-A | No amount is read, stored or shown by this feature (activity carries none; FR-025) |
| P-04 Offline-first | PASS | Rule 1: invitations and members render from Room (S1); preview and activity are not syncable (ADR-0020; `InvitationPreview` "not a syncable record"). Rule 2: invitation create/withdraw are local writes queued per record (R-03, R-05). Rule 3: UUIDv7 invitation ids on the phone. Rule 4: version 0 on create, stored version on withdraw; a closed invitation's 409 takes the server's tombstone (immutable record, nothing of the person's is overwritten). Rule 5: withdraw is a queued deletion; a never-sent create is dropped locally. Membership writes (role, remove, leave, accept) are server-written records the phone never creates — online-only as C4's ownership transfer (spec 006 R-15; R-11 here) |
| P-05 Time | PASS | `expires_at`, `occurred_at` held as `Instant`; shown in the person's time zone; "Expired" from `expires_at <= now` |
| P-06 Localisation & RTL | PASS | ~90 strings EN/AR (contracts/ui-and-e2e.md); Western digits; screenshots EN LTR, AR RTL, 2.0; no server `detail` shown |
| P-07 Design system only | PASS | Screens use `MemberRow`, `RolePill`, list items, sheets, dialogs, `EmptyState`, banners; no new component planned (if one is needed it goes to `designsystem` first with catalogue and goldens) |
| P-08 Accessibility | PASS | 48 dp actions with labels; roles and states in words, never colour alone; 200 % font screenshots |
| P-09 Security & privacy | PASS | Token and link only in memory holders and store state with redacted `toString` (R-04, R-08, R-09); never persisted (no column; back stack not saved); `ApiClient` logs no body; the address is never logged; clipboard marked sensitive (R-17); share text without amount or wallet name (FR-006); sign-out clears the table and holders; uniform "can't be opened" for every token 404 (rule 4) |
| P-10 Shared-first architecture | PASS | All in `commonMain` except `LinkSharer` (interface in common, Android impl in `androidMain`, iOS no-op). `sharing.data` imports no domain/ui/other feature (link offered through an injected lambda, R-04); sharing reaches wallets, transactions, recurring and billing through their `domain` only; wallets and auth reach sharing through hooks they define (R-15, R-16); `core` gains plain-string members only and imports no feature; `SharingLayeringTest` |
| P-11 Naming | PASS | `feature.sharing`, `SharingService`, `InvitationSyncHandler`, `/invite` path; the app name enters share text only through a string resource |
| P-12 Entitlements & billing | PASS | Gates read `can(SharingEnabled)` and `quota(MembersPerWallet)` only (R-06); `FEATURE_NOT_ENTITLED` triggers the C3 refresh; plan explanations open the Plan screen only — no purchase or web checkout (FR-036) |
| P-13 Observability | PASS | Info logs: operation, outcome code, request id, counts — no invitation or wallet ids (debug only), no token, link or address; unexpected failures show the request id |
| P-14 Quality gates | PASS | Each spec §4 invariant has a failing-when-broken test (tasks); contract tests for A1–A9 incl. every handled code; migration 9 → 10 test; screenshots for 4 screens and changed states; no new dependency |
| P-15 Scope guard | PASS | No iOS-specific work (iOS no-op sharer), no notifications (C16), no FX, no web checkout |
| P-16 MVI presentation | PASS | Four screens with Contract/store/Route/Screen; stores call `SharingService` (and existing services) only; effects carry share text with redacted `toString`; reducer and store tests for each, plus the changed wallet detail, transactions list and Home |

No FAIL → plan not blocked. Complexity Tracking empty.

## Project Structure

### Documentation (this feature)

```text
specs/013-sharing/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── sharing-api-client.md
│   ├── sharing-domain.md
│   └── ui-and-e2e.md
├── checklists/
│   ├── requirements.md
│   └── sharing.md       # /speckit-checklist
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
androidApp/src/main/AndroidManifest.xml              # + <data android:path="/invite"/> (https, autoVerify)
androidApp/src/dev/AndroidManifest.xml               # + <data android:path="/invite"/> (http + port)
shared/
├── schemas/…AppDatabase/10.json                     # exported v10
└── src/
    ├── commonMain/kotlin/com/fares/saveme/
    │   ├── core/network/                            # ProblemDetails.feature, ApiClient.callWithMeta
    │   ├── core/sync/                               # Refusal.feature, SyncEngine.pendingIn (+ PendingChangeDao query)
    │   ├── core/database/                           # AppDatabase v10 (+InvitationEntity, InvitationDao), MIGRATION_9_10, DatabaseCleaner
    │   ├── feature/sharing/
    │   │   ├── data/                                # InvitationApi, MemberApi, ActivityApi, dto/, local/ (InvitationEntity, InvitationDao),
    │   │   │                                        #   InvitationRepository, InvitationSyncHandler, InvitationFeedApplier, InvitationPayload
    │   │   ├── domain/                              # SharingService, InviteGate, InviteValidation, InvitationLinkParser, HeldInvitation,
    │   │   │                                        #   ShareLinks, PlanDemotionNotes, ShareText, ActivityMapping, InvitationsWalletCleanup,
    │   │   │                                        #   DefaultSharingGlance, DefaultHeldInvitationGlimpse, LinkSharer (interface), model/
    │   │   ├── ui/                                  # members/, invite/, invitation/, activity/ (Contract, ViewModel, Route, Screen each), SharingCopy
    │   │   └── di/SharingModule.kt
    │   ├── feature/wallets/                         # domain: WalletMemberships (+impl), SharingGlance (+default), observeJoining;
    │   │                                            #   data: storeMember sets my_role (WalletDao.updateRole); ui/detail: members row,
    │   │                                            #   activity row, viewer note, joining state, access-lost / left messages
    │   ├── feature/transactions/ui/                 # viewer note; FORBIDDEN copy "You can now only view this wallet."
    │   ├── feature/recurring/ui/                    # FORBIDDEN copy reuse
    │   ├── feature/auth/domain + ui/home/           # HeldInvitationGlimpse (+default), Home card
    │   ├── core/di/                                 # sharingModule registered
    │   └── navigation/                              # WalletMembersKey, InviteKey, InvitationKey, WalletActivityKey; AppNavHost link + held handling
    ├── androidMain/kotlin/…/feature/sharing/        # AndroidLinkSharer (ACTION_SEND chooser, sensitive clip)
    ├── iosMain/kotlin/…/feature/sharing/            # no-op LinkSharer
    ├── commonMain/composeResources/values*/strings.xml  # + Sharing block (EN, AR)
    ├── commonTest/…/feature/sharing/                # gate, validation, parser, holders, mapping, reducers, stores, service, handler, applier, contract, captured
    └── androidHostTest/…                            # Migration9To10Test, InvitationDaoTest, SignOutWipeTest, AppGraphTest, SharingLayeringTest,
                                                     #   log-capture test, screenshots
.maestro/013-sharing/                                # written, deferred: E2E on owner's device
```

**Structure Decision**: one new feature package following specs 006–012; cross-feature reach
through domain APIs and default-off hooks; the sync engine's behaviour is unchanged.

## Phase 0 / Phase 1 outputs

- [research.md](research.md): R-01 … R-22 (all decisions; no NEEDS CLARIFICATION left).
- [data-model.md](data-model.md): `invitation` table, member/role changes, queue rows, migration 9 → 10, memory-only state, transitions.
- [contracts/sharing-api-client.md](contracts/sharing-api-client.md): A1–A10 with every handled answer.
- [contracts/sharing-domain.md](contracts/sharing-domain.md): `SharingService`, holders, pure parts, hooks, Koin.
- [contracts/ui-and-e2e.md](contracts/ui-and-e2e.md): four screens, changed screens, copy EN/AR, tags, Maestro flows (deferred).
- [quickstart.md](quickstart.md): host gates, live contract QA script, deferred device run.

## Complexity Tracking

None.

## Implementation notes

Written during implementation (owner away, delegated authority). Each entry is a choice made or a finding.

- **Resume audit.** The production side was already built and compiling (Room v10 + migration, core additions, wire clients, handler and
  applier, pure domain, holders, `SharingService`, four screens, wallet detail / transactions / Home / wallets-list additions,
  `LinkRouting`, navigation, manifests, strings, `AndroidLinkSharer`) with only the T004-T018 tests; no task was ticked. The remaining
  work was tests, goldens, flows and docs. Nothing existing was discarded.
- **Added beyond the contract (kept):** `SharingNotices` (one-shot "Invitation sent / saved / declined" carried across screens),
  `observeLinkHeld`, `awaitDelivery` (3 s window: "sent" vs "saved" message), `LeftWallets` (suppresses "access lost" after an own leave and
  feeds "You left <wallet>" on the wallet list), `HeldInvitation.drop()` for non-suspending clears.
- **Client-side token shape check** (43 chars of `A-Za-z0-9_-`) in `InvitationViewModel`: a malformed secret never reaches the server.
- **`FORBIDDEN` copy:** server refusals use `refusal_now_viewer`; the local "not allowed" messages of the editors keep their older strings.
  Two existing copy tests (`TransactionCopyTest`, `RecurringCopyTest`) were updated to the new key.
- **Purge test** runs the app's real Koin graph with an in-memory Room database substituted (the graph's bundled SQLite driver cannot load on
  the host).
- **Timing in tests:** online delivery in service tests calls `engine.drain()` directly (Ktor's mock runs on real threads; a virtual-time
  `withTimeout` would otherwise expire first). `LogCapture` (commonTest) records Kermit lines for the privacy tests.
- **Goldens:** 35 new sharing screens x 3 (en, ar, en at 2.0); legitimately changed existing: wallets `detail_*` (members row, Activity row,
  viewer note), `recurring occurrence_refused`, `transactions editor_refused_no_right` (new viewer copy). Every change inspected.
- **Maestro:** flows need a `+partner` seed in `.specify/scripts/bash/run-e2e.sh` (second verified Plus account, `E2E_PARTNER_EMAIL/PASSWORD`)
  and the backend's `SHARING_INVITATION_URL` on the dev host; neither was run (owner policy).
- **Flake check:** `WalletsServiceArchiveDeleteTest`, `WalletsServiceSaveTest` passed in 2 full runs and 5 forced reruns; no flake seen.
- **T116:** full gate green (`testAndroidHostTest`, `verifyRoborazziAndroidHostTest`, `assembleDevDebug`, iOS simulator compile); the merged
  prod release manifest has the `https` `/invite` path and no `http` filter. `./gradlew build allTests --continue` was run at QA: everything that builds passes except four tasks that fail on code this
  spec did not touch: `:shared:compileCommonMainKotlinMetadata` (and so the iOS `compileTestKotlinIos*` it feeds) on `Dates.kt`
  (`CalendarLocale` / `platform.Foundation.NSLocale` unresolved in the common-metadata compile), the same iOS test compile on
  `TransactionsListPerformanceTest.kt` (`String.format` is JVM-only), and `:androidApp:lintDevDebug` on three `AppLinkUrlError` /
  `IntentFilterExportedReceiver` errors at the existing port/host/exported lines of the spec-003 manifests (the `/invite` path
  lines added by this spec raise none). The host test run (2853 tests, 0 failures), Roborazzi verify, `assembleDevDebug` and the
  iOS simulator main compile are green with `--rerun-tasks`; T116 stays open for those pre-existing failures.

### Spec section 4 invariants traced to tests

| Invariant | Test |
|---|---|
| Render from the phone's copy | `MembersViewModelTest`, `SharingServiceInviteTest` (observe*) |
| Offline create/withdraw, per record, version 0 / stored | `SharingServiceInviteTest`, `SharingServiceMembersTest`, `InvitationSyncHandlerTest` |
| Secret/link never stored, logged, shown, in a URL | `SharingLoggingTest`, `SharingServiceLinkTest`, `InvitationApiContractTest`, `InvitationDaoTest`, `Migration9To10Test` (no secret column) |
| Share text without amount or wallet name | `ShareTextTest`, `SharingStringsTest` |
| Gate order verified, included, count | `InviteGateTest`, `SharingServiceInviteTest` |
| Raise plan-checked, lowering never | `SharingServiceMembersTest` |
| Loss of access purges everything | `AccessLostPurgeTest` (4 signals), `AppGraphTest` |
| Own member row sets wallet role | `WalletMembershipsTest` |
| Branch on code/feature; no auto retry | `SharingServiceJoinTest`, `MemberApiContractTest`, `InvitationApiContractTest` |
| Uniform answer for every token 404 | `SharingServiceJoinTest` |
| Only the flavor's `/invite` link, 43 chars | `InvitationLinkParserTest`, `InvitationLinkRoutingTest` |
| Migration forgets wallet positions only | `Migration9To10Test`, `MigrationTest`, `InvitationsAfterUpgradeTest` |
| Sign-out clears table and secrets | `SignOutWipeTest` |
| Activity: no amount, unknown skipped | `ActivityMappingTest`, `SharingServiceActivityTest` |
| No purchase action | `MembersViewModelTest` (plan screen only), `SharingLayeringTest` |
