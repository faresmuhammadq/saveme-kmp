package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.session.UserDataCleaner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * The wallets the person left on purpose this app session (spec 013 FR-023): the wallet's own screen then closes without
 * "You no longer have access to this wallet", and the wallet list says "You left <wallet>" once. Memory only.
 */
class LeftWallets : UserDataCleaner {
    private val ids = MutableStateFlow<Set<String>>(emptySet())
    private val pendingNotice = MutableStateFlow<String?>(null)

    /** The name of the wallet just left, until the wallet list has shown it. */
    val notice: StateFlow<String?> = pendingNotice

    fun mark(walletId: String, name: String?) {
        ids.value = ids.value + walletId
        if (name != null) pendingNotice.value = name
    }

    fun observe(walletId: String): Flow<Boolean> = ids.map { walletId in it }

    fun dismissNotice() {
        pendingNotice.value = null
    }

    override suspend fun clear() {
        ids.value = emptySet()
        pendingNotice.value = null
    }
}
