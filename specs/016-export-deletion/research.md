# Research: Export & Deletion Completion (client)

Phase 0 for [plan.md](plan.md). Each decision resolves a choice the spec left to planning; all
follow the existing code at `547eed5` (main, merged 015) and the backend contract at
`saveme-backend@b63e917` plus the uncommitted spec 022 contract (`openapi.yaml` v1.13).

## R-01 Where the feature lives

- **Decision**: Inside the existing `feature.account` context (spec 002), which already owns the deletion request/cancel and the Account screen:
  - `data`: new `ExportApi` (`requestExport`, `listExports`, `getExport`) and `dto/ExportDtos.kt`; `AccountApi` gains `deletionPlan()` and `requestDeletion(password, successors)`; `dto/AccountDtos.kt` gains `DeletionPlanDto`, `DeletionSuccessorDto`, `DeletionRequestDto`, `SuccessorChoiceDto`, and `DeletionScheduleDto.successors`.
  - `domain/export/`: `Export` model (no link), `ExportStatus`, `ExportFailure`, `DownloadLink`, `ExportService` (request, page, read, `openableLink`, `watch`).
  - `domain/deletion/`: `DeletionPlan` view model types, `DeletionPlanService` (read + join with wallet names and members), `SuccessorChoices` (pure builder of the request's `successors`), extended `RequestDeletionUseCase`.
  - `ui/export/{list,request,detail}/` (three MVI screens); `ui/deleteaccount/` extended.
- **Rationale**: the backend puts both in Identity ("account deletion and export orchestration", tag `Identity`); one bounded context per backend module (constitution, Architecture). Account already depends on auth domain (`SessionManager`) and may depend on wallets domain (P-10.4).
- **Alternatives**: a new `feature.exports` package (rejected: a second identity context; the Delete account flow links to it anyway); putting exports in `feature.wallets` (rejected: account scope is not a wallet concern).

## R-02 Export DTOs and link redaction

- **Decision**: `ExportDto` mirrors `exports.yaml` `Export` (all members nullable where the schema allows; `status` and `failure_code` kept as raw strings and mapped in domain so an unknown value never fails decoding). `downloadUrl` is a `String?` but `ExportDto.toString()` is overridden to print `download_url=***` (and `download_url_expires_at`). The domain `Export` has **no** URL member; the URL leaves `ExportService` only as `DownloadLink(value: String)` — a `value class` whose `toString()` is `DownloadLink(***)` — returned by `openableLink(id)` and placed in the store's one-shot `Effect.OpenDownload(link)`, never in `State`. The Route calls `LocalUriHandler.current.openUri(link.value)` inside `runCatching`; a failure sends `Intent.OpenFailed` (FR-016).
- **Rationale**: FR-014; P-09.2 (a 15-minute bearer link to the person's history). The existing `ApiClient` logs only request method and URL, never response bodies (checked `HttpClientFactory.kt`), and the download URL is never requested through it.
- **Alternatives**: keep the URL in State so a second tap reuses it (rejected: FR-013 wants a fresh read; State is printed in store tests and debugging); Android `CustomTabsIntent` (rejected: new dependency, P-14.5; `ACTION_VIEW` through `LocalUriHandler` opens the default browser, which downloads the `Content-Disposition: attachment` ZIP itself).
- **App Links**: the app claims only the `/invite` path (spec 013); `/exports/{id}/file` on the API host is not claimed, so `ACTION_VIEW` reaches the browser, not the app. A host test asserts no manifest filter covers `/exports`.

## R-03 Polling

- **Decision**: `ExportService.watch(id: String, requestedAt: Instant?): Flow<WatchEvent>` (domain, cold flow):
  1. reads `GET /exports/{id}` at once;
  2. while the result is `queued` or `running`, waits `5.seconds` (injected `delay`/`TimeSource` via coroutines virtual time in tests) and reads again;
  3. ends after emitting a final status (`ready`, `failed`, `expired`, unknown), a `NotFound`, an `Offline`/`Timeout`, any other failure, or `StillPreparing` once `clock.now() - requestedAt > 10.minutes` or after 120 reads.
  The store collects it in a `Job` started by `Intent.Started` (sent from the Route's `LifecycleStartEffect`, the spec 008 pattern) and cancelled by `Intent.Stopped`, sign-out (store observes `AuthState`), or the flow's end. "Check again" restarts it.
- **Rationale**: FR-010/FR-011 and SC-004 testable with virtual time; P-16.6 (side work in the store through domain); foreground-only by construction (Started/Stopped); the backend's "every 5 seconds is plenty" and "stop after 10 minutes".
- **Alternatives**: a WorkManager background poll (rejected: platform code, keeps polling after the person left, the mail covers it); exponential back-off (rejected: 10 minutes × 5 s = 120 small reads is bounded and matches the backend's advice).

## R-04 Request outcomes and the conflict's `current`

- **Decision**: `ExportService.request(scope): RequestOutcome` = `Created(Export)` | `InProgress(Export?)` | `RateLimited(retryAfterSeconds: Long?)` | `WalletGone` | `Offline` | `Failed(code, requestId)`. `InProgress` decodes `ProblemDetails.current` (already a `JsonElement`) with `ExportDto.serializer()` under `runCatching`; unreadable → `InProgress(null)`. `ApiErrorCode` gains `EXPORT_IN_PROGRESS(RetryClass.Automatic)`; `ProblemDetails.current`'s KDoc names the new use. `AuthErrorCopy.message()` is exhaustive over `ApiErrorCode` (spec 015 note 14), so the new code gets copy there too.
- **Rationale**: FR-017, FR-020, FR-021, FR-036; P-02.4 (branch on code).
- **Rate-limit text**: `waitText(seconds)` → minutes rounded up under 3,600 s, else hours rounded up; null → "later". The store keeps `blockedUntil: Instant?` (memory) and re-enables at that instant through a one-shot delay.

## R-05 Export list paging

- **Decision**: `ExportService.page(cursor: String?): PageOutcome` via `ApiClient.callPage<ExportDto>` with `limit=20`. The list store holds the accumulated items (without links — mapped to domain `Export` right away) and `nextCursor`; "Show more" appends; "Refresh" and every open restart from the first page; `INVALID_CURSOR` restarts from the first page once.
- **Wallet names**: the list and detail stores observe `WalletsService.observeList()` (active + archived) to name wallet exports; a wallet the phone does not hold → "A wallet you no longer have" (FR-005, US2-5).
- **Rationale**: FR-005; no Room (P-04.6, spec §3).

## R-06 Request screen's wallet choice and the "unsent changes" note

- **Decision**: the request store observes `WalletsService.observeList()` and offers wallets with `server.version > 0` and not `hidden` (active then archived), using `SelectionSheet`/`AppSelectField` (existing). Wallet detail shows "Export this wallet" under the same condition (FR-002). The note of FR-009 reads `SyncEngine`'s `SyncStatus.pending > 0` through the existing account-screen source (`AccountSyncCoordinator`/status flow the Account store already observes).
- **Rationale**: a wallet with `version == 0` never reached the server (Wallet KDoc) and would be `404`.

## R-07 Deletion plan view

- **Decision**: `DeletionPlanService.read(): PlanOutcome` calls `AccountApi.deletionPlan()` and joins each `DeletionSuccessorDto` with:
  - the wallet name from `WalletsService.observeList().first()` (active + archived; missing → `walletName = null` → "A shared wallet");
  - the candidates from `WalletMemberships.observe(walletId).first()` (wallets domain API, carries `createdAt` = membership creation, which the backend's "oldest" rule uses), excluding the signed-in person, each `Candidate(userId, displayName?, role, joinedAt)`;
  - `suggested = successor_user_id`; when the suggested id is not among the candidates, a synthetic `Candidate(userId, displayName = null, role = null, unknown = true)` is added and preselected ("Another member").
  - `choiceOffered = choice_expected && walletKnown && candidates.any { !it.unknown }` (spec FR-025 and edge cases); candidates ordered suggested first, then by `joinedAt` ascending.
  It also refreshes the account snapshot's state and date with `SessionManager.updateDeletion(...)` from the plan's `state`/`deletion_scheduled_for` (an unknown state leaves the snapshot alone).
- **Reasons**: `reason` mapped to an enum with `Unknown` (no line shown).
- **Rationale**: FR-024, FR-025; names only from the phone (the plan carries none, by design); P-10.4.

## R-08 Building and sending the choices

- **Decision**: pure `SuccessorChoices.build(plan: DeletionPlan, picks: Map<walletId, userId>): List<SuccessorChoiceDto>?` — one entry per wallet with `choiceOffered`, using the pick or the suggestion, plan order, truncated to 100; `null` when the list is empty (the body then omits `successors`, `encodeDefaults=false`/`explicitNulls=false` already). A synthetic "Another member" pick is sent with its id (it is the server's own suggestion). `RequestDeletionUseCase(password, successors)` posts `DeletionRequestDto`; on success it updates the account snapshot as today and returns `Scheduled(date, successors)`; `VALIDATION_FAILED` whose `errors` contain a pointer starting with `/successors` → `DeletionOutcome.SuccessorsChanged`; pointers on `/password` keep the spec 002 path.
- **Rationale**: FR-027, FR-028, FR-037; clarification 4.

## R-09 Delete account flow (MVI)

- **Decision**: `DeleteStep` becomes: `Explain` → `LoadingPlan` → `NewOwners` (skipped when no successor) → `Review` → `Confirm` (password, existing) → `Scheduled(date, owners)`; plus `Pending(plan)` (opened when the account snapshot says pending) and `ChangeOwners` (NewOwners + Review + Confirm reused with `changing = true`, the date shown unchanged). The store reads the plan on `Continue` (and on open while pending); `Back` walks the steps back; `OpenExports` is a new Effect. Cancel from the pending view reuses `CancelDeletionUseCase`; the confirmation adds the "choices dropped" line when the plan had any `reason == chosen`.
- **Rationale**: keeps spec 002's screen and its tests; one store (P-16.1); the plan is required before `Confirm` (FR-024, invariant).
- **Home "being deleted"**: `HomeState` gains `now: Instant` set by a `Change.Tick` from the existing `MinuteTicker` pattern (sharing domain has one; Home's store gets an injected `Clock`), so the reducer stays pure and the screen compares `deletionPendingOn <= now` (FR-032).

## R-10 "A former member" (FR-033–FR-035)

- **Decision**:
  - Transactions: `addedByOf(createdBy, accountId, memberNames)` gains a branch: when `memberNames` is non-empty and does not contain `createdBy` → `AddedBy.FormerMember` ("Added by a former member", new string `tx_added_by_former`); a present member without a name keeps `AnotherMember`; an empty map (members not read yet) keeps `AnotherMember`.
  - Recurring: `RuleView.authorName` is declared but never filled today (`Views.kt`). `RecurringService` gains the wallet's members (through `WalletsService.observeMembers`, as transactions do) and fills an `author: RuleAuthor` (`You` | `Member(name)` | `AnotherMember` | `FormerMember`) with the same rule; `RuleDetailScreen` renders all four (`recurring_author` plus new `recurring_author_other`, `recurring_author_former`). `You` shows no line (today's behaviour for the person's own rules is no line).
  - Activity: unchanged (`Actor.FormerMember`, spec 013).
- **Rationale**: ADR-0024 rule 3 ("clients show an author without a live membership as 'Former member'"); clarification 5; members are tombstoned on the wallet feed, so a departed member's row is gone from `wallet_member`.

## R-11 Purge and the sign-out wipe

- **Decision**: no new code path. The purge ends every session (`device_sessions` ended, reason `account_deleted`); the next request answers `UNAUTHENTICATED`; `ApiEvents` publishes session-ended; `SessionManager` signs out and `DatabaseCleaner` + `UserDataCleaner`s wipe every table (spec 001/002, P-09.5). A host test pins it for a pending-deletion snapshot (no deletion-specific copy, FR-032).
- **Rationale**: `purging` is not in `DeletionPlan.state` (enum `active | pending_deletion`) and sign-in answers a uniform `INVALID_CREDENTIALS` (backend US4-9).

## R-12 Strings and copy

- **Decision**: one block `<!-- Export & deletion (spec 016) -->` appended to both `values/strings.xml` and `values-ar/strings.xml` (~70 strings each). A string-scan test (androidHostTest) asserts the spec 016 strings contain no "XLSX", "Excel", "encrypt", "token" (en) or their Arabic counterparts ("مشفر", "إكسل"), and that the deletion explanation, review and pending view each reference the Google Play string.
- **Arabic**: تصدير (export), المالك الجديد (new owner), عضو سابق (existing), "أضافها عضو سابق" (added by a former member) — owner review.

## R-13 Tests and QA fixtures

- **Decision**: contract tests on `MockEngine` seeded from the `exports.yaml` examples (`queued`, `readyAndExpired`, `running`, `ready`, `failed`) and the `identity.yaml` examples (`activeWithChoice`, `pendingChosen`, `passwordOnly`, `withChoice`), plus one per handled code (409 with `current`, 429 with `Retry-After`, 404, 422 with `/successors/0/member_user_id`). Live QA (after spec 022 is committed): curl against the local backend with throwaway accounts, capturing answers into `CapturedExportResponses.kt` (download URLs and signatures replaced by fakes) replayed through the real `ApiClient`, `ExportApi`, `ExportService` and stores (pattern of `RedeemLiveReplayTest`). The purge for US6 is run with the backend's artisan command after time travel (backend quickstart).
- **Device**: Maestro flows written under `.maestro/016-export-deletion/`, each marked "deferred: E2E on owner's device"; no emulator or device run (owner policy).

## R-14 Dependencies

No new dependency (P-14.5). `LocalUriHandler` (Compose UI), `LifecycleStartEffect` (lifecycle-compose) and Ktor `MockEngine` are already used.
