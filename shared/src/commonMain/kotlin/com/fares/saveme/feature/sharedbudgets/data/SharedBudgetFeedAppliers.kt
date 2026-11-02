package com.fares.saveme.feature.sharedbudgets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetEntryDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetLineDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetMemberDto
import kotlinx.serialization.json.JsonElement

/** The budget record from its own feed. A complete read from the start that never returned it says the person lost it. */
class SharedBudgetFeedApplier(private val repository: SharedBudgetRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.SharedBudget
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeFeedCopy(AppJson.decodeFromJsonElement(SharedBudgetDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepBudget(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "shared_budget"
    }
}

/** The members of one budget from its feed; another member's departure removes only that member. */
class SharedBudgetMemberFeedApplier(private val repository: SharedBudgetRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.SharedBudget
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeMember(AppJson.decodeFromJsonElement(SharedBudgetMemberDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepMembers(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "shared_budget_member"
    }
}

/** The category lines of one budget; a removed line goes, and past entries keep their category. */
class SharedBudgetLineFeedApplier(private val repository: SharedBudgetRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.SharedBudget
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeLine(AppJson.decodeFromJsonElement(SharedBudgetLineDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepLines(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "shared_budget_line"
    }
}

/** The entries of one budget: every member's spending in it, without wallets. */
class SharedBudgetEntryFeedApplier(private val repository: SharedBudgetRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.SharedBudget
    override val rowType: String = ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) repository.storeEntry(AppJson.decodeFromJsonElement(SharedBudgetEntryDto.serializer(), record))
    }

    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = repository.sweepEntries(scope.id, seenIds)

    companion object {
        const val ROW_TYPE = "shared_budget_entry"
    }
}

/**
 * The person's own memberships from their user feed (backend ADR-0027 rule 9, as ADR-0017 does for wallets). A live row makes
 * the budget followed; an ended one removes the budget and everything of it from the phone.
 */
class OwnSharedBudgetMembershipFeedApplier(private val repository: SharedBudgetRepository) : FeedApplier {
    override val scopeKind: ScopeKind = ScopeKind.User
    override val rowType: String = SharedBudgetMemberFeedApplier.ROW_TYPE

    override suspend fun apply(scope: SyncScope, records: List<JsonElement>) {
        for (record in records) {
            val dto = AppJson.decodeFromJsonElement(SharedBudgetMemberDto.serializer(), record)
            // The user feed carries only the person's own memberships; anything else is not this applier's to act on.
            if (dto.userId != scope.id) continue
            repository.storeOwnMembership(dto)
        }
    }

    /** A budget whose own membership was not returned is not removed on that evidence: its own feed decides. */
    override suspend fun sweep(scope: SyncScope, seenIds: Set<String>) = Unit
}
