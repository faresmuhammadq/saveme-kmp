package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.budgets.data.BudgetPayload
import com.fares.saveme.feature.budgets.data.BudgetRepository
import com.fares.saveme.feature.budgets.data.BudgetSyncHandler
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The categories named by a budget on this phone, synced or waiting: they count as in use, so C5 offers no Delete for
 * them (FR-021, research R-10). The server also keeps a category in use while any budget, even a deleted one, names it;
 * C5 then offers "Archive instead". All expenses names no category.
 *
 * @param engine resolved on use: the engine is built from every sync handler (spec 006 lesson).
 */
class BudgetCategoryUses(
    private val repository: BudgetRepository,
    private val engine: () -> SyncEngine,
    private val authState: StateFlow<AuthState>,
) : PendingCategoryUses {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun usedLocally(): Flow<Set<String>> = authState
        .map { (it as? AuthState.SignedIn)?.account?.userId }
        .distinctUntilChanged()
        .flatMapLatest { account ->
            if (account == null) {
                flowOf(emptySet())
            } else {
                combine(repository.observeAll(account), engine().observeAll(BudgetSyncHandler.RECORD_TYPE)) { rows, changes ->
                    val stored = rows.mapNotNull { it.categoryId }
                    val waiting = changes.filter { !it.isDelete }.mapNotNull {
                        runCatching { AppJson.decodeFromString(BudgetPayload.serializer(), it.payloadJson) }.getOrNull()?.categoryId
                    }
                    (stored + waiting).toSet()
                }
            }
        }
}
