package com.fares.saveme.feature.categories.domain

import com.fares.saveme.feature.categories.data.CategoryReferenceRepository
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant

/** A wallet that is deleted, or leaves the phone, takes its category references with it (FR-020). */
class CategoryReferencesCleanup(private val references: CategoryReferenceRepository) : WalletDeletionParticipant {
    override suspend fun onWalletDeleted(walletId: String) {
        references.removeFor(walletId)
    }
}
