package com.fares.saveme.feature.wallets.domain

/** C6 (and later C7/C9) register one to drop their local rows when a wallet's deletion is confirmed. */
fun interface WalletDeletionParticipant {
    suspend fun onWalletDeleted(walletId: String)
}
