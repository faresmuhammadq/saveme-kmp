package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.session.UserDataCleaner

/** Sign-out wipes the subscription and catalogue records (FR-008, P-09). */
class BillingDataCleaner(
    private val subscriptions: SubscriptionRepository,
    private val catalogue: CatalogueRepository,
) : UserDataCleaner {
    override suspend fun clear() {
        subscriptions.clear()
        catalogue.clear()
    }
}
