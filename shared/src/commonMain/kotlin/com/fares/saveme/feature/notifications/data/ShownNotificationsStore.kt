package com.fares.saveme.feature.notifications.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private class ShownIds(
    @SerialName("user_id") val userId: String,
    val ids: List<String>,
)

/** The last [LIMIT] `notification_id`s the app showed for the signed-in person, newest last (FR-024). */
class ShownNotificationsStore(private val store: KeyValueStore) {
    private val lock = Mutex()

    /** Records [notificationId] for [userId]; false when it was already shown (the caller drops the message). */
    suspend fun markShown(userId: String, notificationId: String): Boolean = lock.withLock {
        val known = read()?.takeIf { it.userId == userId }?.ids.orEmpty()
        if (notificationId in known) return@withLock false
        val updated = (known + notificationId).takeLast(LIMIT)
        store.set(KEY, AppJson.encodeToString(ShownIds.serializer(), ShownIds(userId, updated)))
        true
    }

    suspend fun clear() = lock.withLock { store.remove(KEY) }

    private suspend fun read(): ShownIds? =
        store.get(KEY)?.let { runCatching { AppJson.decodeFromString(ShownIds.serializer(), it) }.getOrNull() }

    companion object {
        const val KEY = "push_shown_ids"
        const val LIMIT = 100
    }
}
