package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.sync.WalletScopes
import com.fares.saveme.feature.wallets.data.WalletRepository

/** What the feed puller asks of wallets (spec 009 research R-03); placeholders are never read or removed by a read. */
class DefaultWalletScopes(
    private val repository: WalletRepository,
    private val accountId: () -> String?,
) : WalletScopes {
    override suspend fun followed(): List<String> = repository.followedIds(accountId().orEmpty())

    override suspend fun isAccepted(walletId: String): Boolean = repository.isAccepted(walletId, accountId().orEmpty())

    override suspend fun unconfirmed(seenWalletIds: Set<String>): List<String> =
        repository.heldIds().filter { it !in seenWalletIds }

    override suspend fun accessLost(walletId: String) = repository.accessLost(walletId)
}
