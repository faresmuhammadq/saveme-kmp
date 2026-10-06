package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.budgets.domain.BudgetsService
import com.fares.saveme.feature.recurring.domain.RecurringService
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.flow.first

/** Is the record a tap opens already on the phone (FR-032)? False for targets that are not one record. */
fun interface RecordPresence {
    suspend fun has(target: PushTarget): Boolean
}

/** Reads other features only through their domain (P-10.4); services are resolved on use (spec 006 lesson). */
class DefaultRecordPresence(
    private val wallets: () -> WalletsService,
    private val recurring: () -> RecurringService,
    private val budgets: () -> BudgetsService,
) : RecordPresence {
    override suspend fun has(target: PushTarget): Boolean = when (target) {
        is PushTarget.Wallet -> wallets().observeWallet(target.id).first() != null
        is PushTarget.Occurrence -> recurring().observeOccurrence(target.id).first() != null
        is PushTarget.Budget -> budgets().observeBudget(target.id).first() != null
        else -> false
    }
}
