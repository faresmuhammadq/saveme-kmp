package com.fares.saveme.core.sync

/**
 * What the feed puller needs to know about wallets, implemented by the wallets feature so
 * `core.sync` imports no feature (spec 009 research R-01, R-03).
 */
interface WalletScopes {
    /** Wallets to read: those the server has accepted, plus every wallet with a live own membership. */
    suspend fun followed(): List<String>

    /** Whether the server knows the wallet: it holds a server version, or a live own membership exists. */
    suspend fun isAccepted(walletId: String): Boolean

    /**
     * After a complete read of the person's scope without a position: held wallets whose own
     * membership was not among [seenWalletIds]. Their feeds are read in the same run; only a `404`
     * removes one (FR-011).
     */
    suspend fun unconfirmed(seenWalletIds: Set<String>): List<String>

    /**
     * The person no longer has the wallet (ended membership, or its feed answered `404`): removes
     * it with its data and clears its position, except a wallet made on this phone and not yet
     * accepted, and except the wallet record itself while a change to it waits (then only the
     * position goes and settling that change removes the wallet).
     */
    suspend fun accessLost(walletId: String)
}
