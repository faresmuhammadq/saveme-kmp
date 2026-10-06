# Data Model: Export & Deletion Completion (client)

No Room change (stays v10), no key-value record, no syncable entity. Everything below is either a
wire DTO (`data`) or an in-memory domain/UI type.

## Wire DTOs (`feature.account.data.dto`)

| DTO | Mirrors | Members | Notes |
|---|---|---|---|
| `ExportRequestDto` | `ExportRequest` | `scope: String` (`account`/`wallet`), `walletId: String?` (`wallet_id`) | `wallet_id` omitted for `account` (`explicitNulls=false`) |
| `ExportDto` | `Export` | `id`, `scope`, `walletId?`, `status: String`, `failureCode: String?`, `requestedAt: String`, `completedAt?`, `expiresAt?`, `sizeBytes: Long?`, `downloadUrl: String?`, `downloadUrlExpiresAt?` | `toString()` prints `downloadUrl=***`; status/failure kept raw |
| `DeletionSuccessorDto` | `DeletionSuccessor` | `walletId`, `successorUserId`, `reason: String`, `choiceExpected: Boolean` | reason raw |
| `DeletionPlanDto` | `DeletionPlan` | `state: String`, `deletionRequestedAt?`, `deletionScheduledFor?`, `successors: List<DeletionSuccessorDto> = []` | |
| `SuccessorChoiceDto` | `DeletionRequest.successors[]` | `walletId`, `memberUserId` (`member_user_id`) | |
| `DeletionRequestDto` | `DeletionRequest` | `password: String`, `successors: List<SuccessorChoiceDto>? = null` | null → member omitted; `toString()` redacts the password |
| `DeletionScheduleDto` (changed) | `DeletionSchedule` | + `successors: List<DeletionSuccessorDto> = []` | default keeps older answers decodable |

## Domain types

### Export (`feature.account.domain.export`)

```text
Export(id, scope: ExportScope, walletId: String?, status: ExportStatus, failure: ExportFailure?,
       requestedAt: Instant, completedAt: Instant?, expiresAt: Instant?, sizeBytes: Long?)
ExportScope   = Account | Wallet
ExportStatus  = Queued | Running | Ready | Failed | Expired | Unknown(raw)
ExportFailure = WalletUnavailable | GenerationFailed | TimedOut | Unknown(raw)   // Unknown renders as GenerationFailed copy
DownloadLink(value: String)  // value class; toString() = "DownloadLink(***)"
```

`Export` never carries the URL. `ExportStatus.isActive = Queued || Running`.

State transitions (server-owned, read-only): `queued → running → ready → expired`, or
`queued|running → failed`. The client never changes a status.

### Outcomes

```text
RequestOutcome  = Created(Export) | InProgress(current: Export?) | RateLimited(retryAfterSeconds: Long?)
                | WalletGone | Offline | Failed(code: ApiErrorCode, requestId: String?)
ReadOutcome     = Read(Export) | NotFound | Offline | Failed(code, requestId)
PageOutcome     = Page(items: List<Export>, nextCursor: String?, hasMore) | CursorInvalid | Offline | Failed(code, requestId)
LinkOutcome     = Open(link: DownloadLink) | NotReady(Export) | NotFound | Offline | Failed(code, requestId)
WatchEvent      = Status(Export) | StillPreparing(Export) | NotFound | Offline | Failed(code, requestId)
```

### Deletion plan (`feature.account.domain.deletion`)

```text
DeletionPlan(state: PlanState, requestedAt: Instant?, scheduledFor: Instant?, wallets: List<OwnedSharedWallet>)
PlanState = Active | PendingDeletion | Unknown(raw)
OwnedSharedWallet(walletId, walletName: String?,              // null → "A shared wallet"
                  successorUserId: String, reason: SuccessorReason,
                  choiceExpected: Boolean, choiceOffered: Boolean,
                  candidates: List<Candidate>)                 // other members on the phone (+ synthetic suggestion)
SuccessorReason = OnlyMember | Chosen | OldestEditor | OldestViewer | Unknown(raw)
Candidate(userId, displayName: String?, role: WalletRole?, joinedAt: Instant?, unknown: Boolean)
PlanOutcome = Loaded(DeletionPlan) | Offline | Failed(code, requestId)
```

`choiceOffered = choiceExpected && walletName != null && candidates.any { !it.unknown }` (research R-07). Candidates: the suggested one first, then by `joinedAt` ascending.

### Deletion outcomes (extended `feature.account.domain.model.DeletionOutcome`)

```text
Scheduled(date: Instant, owners: List<DeletionSuccessorDto→ mapped OwnedSharedWallet>)   // changed: + owners
SuccessorsChanged                                                                          // new: 422 at /successors…
WrongPassword | Locked | Validation | Offline | Error                                       // unchanged (spec 002)
```

## Screen state (memory only)

### Exports list (`ExportsState`)

`items: List<ExportRow>` (Export + resolved wallet name or "gone"), `nextCursor`, `hasMore`,
`loading`, `loadingMore`, `banner: Offline | Error(code, requestId)?`. Never a URL.

### Export request (`ExportRequestState`)

`scope: Account | Wallet`, `walletId: String?`, `wallets: List<WalletChoice(id, name, archived)>`,
`unsentChanges: Boolean`, `submitting`, `blockedUntil: Instant?`, `waitText` input
(`retryAfterSeconds`), `message: InProgress(currentId?) | RateLimited(seconds?) | WalletGone | Offline | Error(code, requestId)?`.

### Export detail (`ExportDetailState`)

`export: Export?`, `walletName: String?`, `walletGone: Boolean`, `polling: Boolean`,
`stillPreparing: Boolean`, `opening: Boolean`, `message: Offline | NotFound | OpenFailed | Error(code, requestId)?`.
Effect `OpenDownload(link: DownloadLink)` is the only carrier of the URL.

### Delete account (`DeleteAccountState`, extended)

`step: Explain | LoadingPlan | NewOwners | Review | Confirm | Scheduled(date, owners) | Pending`,
`plan: DeletionPlan?`, `picks: Map<walletId, userId>`, `changing: Boolean` (pending → change owners),
`planBanner: Offline | Error?`, `successorsChanged: Boolean` (message), plus spec 002's password,
visibility, error, submitting, waitMinutes, banner.

Step machine:

```text
Explain --Continue--> LoadingPlan --Loaded--> NewOwners (any successor) | Review (none)
LoadingPlan --Offline/Error--> Explain (+banner, Try again)
NewOwners --Next--> Review --Delete my account--> Confirm --Scheduled--> Scheduled
Confirm --SuccessorsChanged--> LoadingPlan → NewOwners (picks reset)
(open while pending) --> LoadingPlan --Loaded--> Pending
Pending --Change new owners--> NewOwners (changing) → Review → Confirm → Pending (date unchanged)
Pending --Cancel deletion--> (spec 002 cancel) → finished
Back: NewOwners→Explain, Review→NewOwners|Explain, Confirm→Review, changing NewOwners→Pending
```

## Rendering changes in other features

| Where | Today | After |
|---|---|---|
| `AddedBy` (transactions) | `You`, `Member(name)`, `AnotherMember` | + `FormerMember` when the wallet's member map is non-empty and lacks the author |
| `RuleView` (recurring) | `authorName: String?` never filled | `author: RuleAuthor` = `You` (no line) / `Member(name)` / `AnotherMember` / `FormerMember` |
| `HomeState` | `deletionPendingOn` | + `now: Instant` (minute tick) → "being deleted" when `deletionPendingOn <= now` |
| `WalletDetailState` | — | + `exportable: Boolean` (`server.version > 0`), intent `ExportClicked`, effect `OpenExport(walletId)` |
| `AccountState` | rows | + destination `Exports` |
