package com.fares.saveme.feature.wallets.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** C6 rebinds this so balances include unsent transactions (spec FR-004). C4 binds [ZeroPendingPostings]. */
interface PendingPostings {
    fun deltaFor(walletId: String): Flow<Long>
    fun hasPending(walletId: String): Flow<Boolean>
}

/** Until transactions exist on the phone there is nothing unsent to add. */
object ZeroPendingPostings : PendingPostings {
    override fun deltaFor(walletId: String): Flow<Long> = flowOf(0L)
    override fun hasPending(walletId: String): Flow<Boolean> = flowOf(false)
}
