package com.fares.saveme.core.storage

import kotlin.time.Instant

/** The device-session credential, `{session_id}|{secret}`. Lives only in [SecureStore] (P-09.1). */
data class StoredSession(
    val credential: String,
    val sessionId: String,
    val issuedAt: Instant,
) {
    override fun toString() = "StoredSession(sessionId=$sessionId, issuedAt=$issuedAt, credential=<redacted>)"
}

interface SecureStore {
    suspend fun read(): StoredSession?
    suspend fun write(session: StoredSession)
    suspend fun clear()
}
