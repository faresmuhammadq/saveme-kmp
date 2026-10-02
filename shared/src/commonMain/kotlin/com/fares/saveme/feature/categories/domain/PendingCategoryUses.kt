package com.fares.saveme.feature.categories.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * C6 (and later C7/C8) rebind this so Delete is not offered for a category an unsent record
 * uses. C5 binds [NoPendingCategoryUses]: until transactions exist on the phone nothing can use one.
 */
interface PendingCategoryUses {
    fun usedLocally(): Flow<Set<String>>
}

object NoPendingCategoryUses : PendingCategoryUses {
    override fun usedLocally(): Flow<Set<String>> = flowOf(emptySet())
}
