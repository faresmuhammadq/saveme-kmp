# Contract: Account domain API used by the stores

Stores import only these (P-16.6); types in [data-model.md](../data-model.md).

## ExportService

```kotlin
interface ExportService {
    suspend fun request(scope: ExportScope, walletId: String?): RequestOutcome   // one POST, never retried
    suspend fun page(cursor: String?): PageOutcome                               // 20 per page
    suspend fun read(id: String): ReadOutcome
    suspend fun openableLink(id: String): LinkOutcome                            // fresh GET; the only way a URL leaves the domain
    fun watch(id: String, requestedAt: Instant?): Flow<WatchEvent>               // R-03: 5 s, 10-minute / 120-read cap
}
```

Rules: no entitlement, verification or deletion-state check (FR-004). `request` with
`ExportScope.Wallet` requires `walletId`. `watch` emits at least one event and completes after a
final status, `StillPreparing`, `NotFound`, `Offline` or `Failed`. Nothing is cached between calls.

## DeletionPlanService and use cases

```kotlin
interface DeletionPlanService {
    suspend fun read(): PlanOutcome          // GET plan + wallet names + members on the phone; updates the account snapshot's state/date
}

object SuccessorChoices {
    fun build(plan: DeletionPlan, picks: Map<String, String>): List<SuccessorChoice>?   // R-08; null = omit
}

class RequestDeletionUseCase {             // changed
    suspend operator fun invoke(password: String, successors: List<SuccessorChoice>?): DeletionOutcome
}
class CancelDeletionUseCase                // unchanged
```

`SuccessorChoice(walletId, memberUserId)` is a domain type; the use case maps it to
`SuccessorChoiceDto` (data never imports domain, P-10.6).

## Cross-feature reads (other features' domain only, P-10.4)

| Need | API |
|---|---|
| Wallet names, archived flag, `version` | `WalletsService.observeList()` |
| Members with role and joined date | `WalletMemberships.observe(walletId)` |
| Signed-in person, deletion state, sign-out | `SessionManager` / `AuthState` |
| Unsent changes | the `SyncStatus` flow the Account store already observes |

## Koin

`ExportApi`, `ExportService`, `DeletionPlanService` and the three new view models join
`AccountModule`. None resolves `SyncEngine` eagerly; `AppGraphTest` starts the real graph and
resolves the new view models.
