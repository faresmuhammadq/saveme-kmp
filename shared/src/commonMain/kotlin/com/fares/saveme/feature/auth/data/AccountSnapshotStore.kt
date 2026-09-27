package com.fares.saveme.feature.auth.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.feature.auth.data.dto.AccountSnapshotDto

class AccountSnapshotStore(private val store: KeyValueStore) {

    suspend fun read(): AccountSnapshotDto? = store.get(KEY)?.let {
        runCatching { AppJson.decodeFromString(AccountSnapshotDto.serializer(), it) }.getOrNull()
    }

    suspend fun write(snapshot: AccountSnapshotDto) {
        store.set(KEY, AppJson.encodeToString(AccountSnapshotDto.serializer(), snapshot))
    }

    suspend fun setEmailVerified() {
        val current = read() ?: return
        write(current.copy(account = current.account.copy(emailVerified = true)))
    }

    suspend fun clear() = store.remove(KEY)

    private companion object {
        const val KEY = "account_snapshot"
    }
}
