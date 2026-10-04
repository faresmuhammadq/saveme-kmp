package com.fares.saveme.feature.categories.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** The categories any bound source (transactions, recurring) still uses in an unsent change (spec 010 FR-032). */
class UnionCategoryUses(private val sources: List<PendingCategoryUses>) : PendingCategoryUses {
    override fun usedLocally(): Flow<Set<String>> =
        if (sources.isEmpty()) flowOf(emptySet())
        else combine(sources.map { it.usedLocally() }) { sets -> sets.fold(emptySet()) { all, one -> all + one } }
}
