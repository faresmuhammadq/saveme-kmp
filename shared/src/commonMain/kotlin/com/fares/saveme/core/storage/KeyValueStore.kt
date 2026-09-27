package com.fares.saveme.core.storage

/** Small non-secret values (device id, account snapshot). Backup-excluded on Android. */
interface KeyValueStore {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun remove(key: String)
}
