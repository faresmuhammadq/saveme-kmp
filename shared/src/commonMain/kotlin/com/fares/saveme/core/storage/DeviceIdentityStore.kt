package com.fares.saveme.core.storage

import kotlin.uuid.Uuid

/** A stable identifier per app installation, sent at sign-in and never shown (R-07). */
class DeviceIdentityStore(private val store: KeyValueStore) {

    suspend fun deviceId(): String =
        store.get(KEY) ?: Uuid.random().toString().also { store.set(KEY, it) }

    private companion object {
        const val KEY = "device_id"
    }
}
