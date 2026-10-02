# Contract: Categories domain API (for C6, C7, C8 and this feature's screens)

Package `com.fares.saveme.feature.categories.domain`. Other features use only this package
(P-10.4). Visual pieces (`CategoryChoiceSheet`, `CategoryRow`, `CategoryTile`) are in
`designsystem` and take plain values.

```kotlin
interface CategoriesService {
    fun observeOverview(kind: CategoryKind): Flow<CategoriesOverview>
    fun observeCategory(id: String): Flow<CategoryView?>             // null when gone or hidden by a waiting deletion

    /** FR-018: own active (by name), then active defaults (platform order); [current] kept whatever its state. */
    fun observeChoices(kind: CategoryKind, currentCategoryId: String? = null): Flow<CategoryChoices>

    /** FR-019: one snapshot per screen; resolve(id) is own → default → [walletId]'s references → Unavailable. */
    fun observeDisplays(walletId: String? = null): Flow<CategoryDisplays>

    suspend fun create(draft: CategoryDraft): CreateResult           // Created(id) | NotVerified | Invalid(errors)
    suspend fun save(id: String, draft: CategoryDraft, baseVersion: Long): SaveResult
        // Saved | NotVerified | NoLongerAvailable | Invalid(errors); kind is always the stored kind
    suspend fun archive(id: String): SaveResult
    suspend fun delete(id: String): SaveResult                       // + InUse when the R-06 rule says no
    suspend fun archiveInstead(id: String)                           // after a CATEGORY_IN_USE refusal
    suspend fun keepCategory(id: String)                             // after a CATEGORY_IN_USE refusal
    suspend fun keepMine(id: String)
    suspend fun useTheirs(id: String)                                // also "Accept the deletion" / "Keep the category"
    suspend fun tryAgain(id: String)
    suspend fun discard(id: String)
    suspend fun refresh(force: Boolean = false): RefreshOutcome      // Done | Offline | Failed; 60 s floor unless forced
    suspend fun refreshReferences(walletId: String, force: Boolean = false): RefreshOutcome
}

/** C6 (and later C7/C8) rebind this so Delete is not offered for a category an unsent record uses. C5 binds none. */
interface PendingCategoryUses { fun usedLocally(): Flow<Set<String>> }
```

Rules for consumers:

1. A new or re-categorised income or expense MUST take its category from `observeChoices(kind, current)`; never offer a partner's category, an archived own category or a retired default (the server refuses them, transactions.yaml).
2. Show a transaction's category through `observeDisplays(walletId)`; never look up categories by name, and never treat `Unavailable` as an error.
3. When a screen of one wallet's transactions opens, call `refreshReferences(walletId, force = true)`; misses also refresh, under the floor.
4. Category ids are opaque strings; a default and an own category share one id space (ADR-0014 rule 1).
5. `CategoryDisplay.name` for a default is already in the app language; do not translate own or partner names.
