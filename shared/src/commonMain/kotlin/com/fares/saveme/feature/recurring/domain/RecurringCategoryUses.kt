package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.data.RecurringRuleSyncHandler
import com.fares.saveme.feature.recurring.domain.model.decodeOccurrencePayload
import com.fares.saveme.feature.recurring.domain.model.decodeRulePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The categories unsent rule and occurrence changes use (incl. the payout category), so C5 offers no Delete for them (FR-032). */
class RecurringCategoryUses(private val engine: () -> SyncEngine) : PendingCategoryUses {
    override fun usedLocally(): Flow<Set<String>> = combine(
        engine().observeAll(RecurringRuleSyncHandler.RECORD_TYPE),
        engine().observeAll(OccurrenceSyncHandler.RECORD_TYPE),
    ) { ruleChanges, occurrenceChanges ->
        val fromRules = ruleChanges.filter { !it.isDelete }.mapNotNull { decodeRulePayload(it.payloadJson) }
            .flatMap { listOfNotNull(it.categoryId, it.payoutCategoryId) }
        val fromOccurrences = occurrenceChanges.filter { !it.isDelete }.mapNotNull { decodeOccurrencePayload(it.payloadJson)?.categoryId }
        (fromRules + fromOccurrences).toSet()
    }
}
