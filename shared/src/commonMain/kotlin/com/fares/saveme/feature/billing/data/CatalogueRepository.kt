package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.feature.billing.data.dto.CatalogueRecord
import com.fares.saveme.feature.billing.data.dto.PlanDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The last catalogue and the language it was requested in (R-07, FR-017). */
class CatalogueRepository(
    private val api: BillingApi,
    private val store: KeyValueStore,
) {
    private val mutableStored = MutableStateFlow<CatalogueRecord?>(null)
    val stored: StateFlow<CatalogueRecord?> = mutableStored.asStateFlow()

    suspend fun fetch(): ApiResult<List<PlanDto>> = api.listPlans()

    suspend fun load() {
        mutableStored.value = store.get(KEY)?.let {
            runCatching { AppJson.decodeFromString(CatalogueRecord.serializer(), it) }.getOrNull()
        }
    }

    suspend fun save(language: String, plans: List<PlanDto>) {
        val record = CatalogueRecord(language, plans)
        store.set(KEY, AppJson.encodeToString(CatalogueRecord.serializer(), record))
        mutableStored.value = record
    }

    suspend fun clear() {
        store.remove(KEY)
        mutableStored.value = null
    }

    companion object {
        const val KEY = "billing_catalogue"
    }
}
