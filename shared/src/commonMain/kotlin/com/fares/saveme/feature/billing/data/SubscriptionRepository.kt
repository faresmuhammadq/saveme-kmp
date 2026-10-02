package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The last subscription read, kept so the Plan screen works offline (R-07). Not syncable (P-04.6). */
class SubscriptionRepository(
    private val api: BillingApi,
    private val store: KeyValueStore,
) {
    private val mutableStored = MutableStateFlow<MySubscriptionDto?>(null)
    val stored: StateFlow<MySubscriptionDto?> = mutableStored.asStateFlow()

    suspend fun fetch(): ApiResult<MySubscriptionDto> = api.mySubscription()

    /** Reads the persisted record into [stored]. */
    suspend fun load() {
        mutableStored.value = store.get(KEY)?.let {
            runCatching { AppJson.decodeFromString(MySubscriptionDto.serializer(), it) }.getOrNull()
        }
    }

    suspend fun save(subscription: MySubscriptionDto) {
        store.set(KEY, AppJson.encodeToString(MySubscriptionDto.serializer(), subscription))
        mutableStored.value = subscription
    }

    suspend fun clear() {
        store.remove(KEY)
        mutableStored.value = null
    }

    companion object {
        const val KEY = "billing_subscription"
    }
}
