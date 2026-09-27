package com.fares.saveme.core.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

class InMemorySecureStore : SecureStore {
    var stored: StoredSession? = null
    override suspend fun read() = stored
    override suspend fun write(session: StoredSession) { stored = session }
    override suspend fun clear() { stored = null }
}

class SessionStoreTest {

    private val credential = "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70|" + "a".repeat(64)
    private val session = StoredSession(credential, "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70", Instant.fromEpochSeconds(1_790_000_000))

    @Test
    fun roundTripsAndClears() = runTest {
        val store = InMemorySecureStore()
        store.write(session)
        assertEquals(session, store.read())
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun toStringRedactsTheCredential() {
        assertFalse(session.toString().contains("a".repeat(64)))
        assertFalse(session.toString().contains(credential))
    }
}
