package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Budget Period dates the server reported (`GET /reports/trend`, FR-019) for the signed-in account and the session; memory
 * only. They are tied to the profile's start day and "applies from" date they were learned under: when either changes
 * they are dropped (data-model §4).
 */
class LearnedPeriods {
    private data class Learned(val accountId: String, val profile: PeriodProfile, val spans: List<BudgetPeriodSpan>)

    private val learned = MutableStateFlow<Learned?>(null)

    fun observe(accountId: String, profile: PeriodProfile): Flow<List<BudgetPeriodSpan>> =
        learned.map { it.matching(accountId, profile) }.distinctUntilChanged()

    fun get(accountId: String, profile: PeriodProfile): List<BudgetPeriodSpan> = learned.value.matching(accountId, profile)

    /** Keeps [spans] with what was learned before under the same account and profile; a different one replaces it. */
    fun store(accountId: String, profile: PeriodProfile, spans: List<BudgetPeriodSpan>) {
        learned.update { old ->
            val kept = old.matching(accountId, profile).filter { known -> spans.none { it.start <= known.end && it.end >= known.start } }
            Learned(accountId, profile, (kept + spans).sortedBy { it.start })
        }
    }

    fun clear() {
        learned.value = null
    }

    private fun Learned?.matching(accountId: String, profile: PeriodProfile): List<BudgetPeriodSpan> =
        if (this != null && this.accountId == accountId && this.profile == profile) spans else emptyList()
}
