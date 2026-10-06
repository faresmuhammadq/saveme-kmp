package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.domain.model.ReportChoices
import com.fares.saveme.feature.reports.domain.model.ReportScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The session's choices per account and scope: range, trend length, currency and the scheduled switch (FR-012). Memory
 * only, never stored; the detail screen and a return to Reports find them again. Cleared at sign-out and when another
 * account signs in.
 */
class ReportChoicesStore {
    private data class Key(val accountId: String, val scopeKey: String)

    private val choices = MutableStateFlow<Map<Key, ReportChoices>>(emptyMap())

    fun observe(accountId: String, scope: ReportScope): Flow<ReportChoices> {
        val key = Key(accountId, scope.key)
        return choices.map { it[key] ?: ReportChoices() }.distinctUntilChanged()
    }

    fun get(accountId: String, scope: ReportScope): ReportChoices = choices.value[Key(accountId, scope.key)] ?: ReportChoices()

    fun update(accountId: String, scope: ReportScope, transform: (ReportChoices) -> ReportChoices) {
        val key = Key(accountId, scope.key)
        choices.update { it + (key to transform(it[key] ?: ReportChoices())) }
    }

    /** Another account signing in forgets what the previous one chose. */
    fun dropOtherAccounts(accountId: String) {
        if (choices.value.keys.all { it.accountId == accountId }) return
        choices.update { all -> all.filterKeys { it.accountId == accountId } }
    }

    fun clear() {
        choices.value = emptyMap()
    }
}
