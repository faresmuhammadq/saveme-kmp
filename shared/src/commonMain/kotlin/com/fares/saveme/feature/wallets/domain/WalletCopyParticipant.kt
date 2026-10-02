package com.fares.saveme.feature.wallets.domain

/**
 * C6 registers one to learn that a server copy of a wallet was stored (an answer to this phone's own write, or a
 * change feed row) at scope position [changeSeq]: whatever that copy's balance already counts needs no carry
 * (spec 009 FR-013).
 */
fun interface WalletCopyParticipant {
    suspend fun onWalletCopy(walletId: String, changeSeq: Long)
}
