package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.session.UserDataCleaner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The wallets where the latest role change about the person that an activity page read this app session showed was the owner's
 * plan lowering them (spec 013 FR-027, R-15). Memory only; the phone does not read activity in the background.
 */
class PlanDemotionNotes : UserDataCleaner {
    private val limited = MutableStateFlow<Set<String>>(emptySet())

    fun record(walletId: String, limitedByPlan: Boolean) {
        limited.value = if (limitedByPlan) limited.value + walletId else limited.value - walletId
    }

    fun observe(walletId: String): Flow<Boolean> = limited.map { walletId in it }

    override suspend fun clear() {
        limited.value = emptySet()
    }
}
