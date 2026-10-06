package com.fares.saveme.feature.notifications.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.KeyValueStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * What the phone remembers of its own registration (data-model §3): which session was last registered, a
 * non-reversible fingerprint of the push address and when it last succeeded. Never the address itself (FR-004).
 * [stoppedFor] is set when the server answered `NOT_FOUND` for this session and the app stopped trying (FR-005).
 */
@Serializable
data class PushRegistrationRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String,
    val fingerprint: String,
    @SerialName("registered_at") val registeredAtEpochSeconds: Long,
    @SerialName("stopped_for") val stoppedFor: String? = null,
) {
    val registeredAt: Instant get() = Instant.fromEpochSeconds(registeredAtEpochSeconds)

    override fun toString() = "PushRegistrationRecord(${if (stoppedFor != null) "stopped" else "registered"})"
}

/** The `push_registration` record over the key-value store. */
class PushRegistrationStore(private val store: KeyValueStore) {

    suspend fun read(): PushRegistrationRecord? =
        store.get(KEY)?.let { runCatching { AppJson.decodeFromString(PushRegistrationRecord.serializer(), it) }.getOrNull() }

    suspend fun write(record: PushRegistrationRecord) {
        store.set(KEY, AppJson.encodeToString(PushRegistrationRecord.serializer(), record))
    }

    suspend fun clear() = store.remove(KEY)

    companion object {
        const val KEY = "push_registration"
    }
}
