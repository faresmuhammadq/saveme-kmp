package com.fares.saveme.devtools.push

import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Development builds only (FR-035): a stable fake push address per installation, in the shape the backend accepts
 * (printable ASCII, at most 4,096 characters). It reaches nobody; it only lets registration and display be exercised
 * without Firebase. [onNewToken] is how a rotation tells the receiver; resolved lazily by the module (spec 006 lesson).
 */
class DevFakePushTokenProvider(
    private val store: KeyValueStore,
    private val onNewToken: () -> Unit,
) : PushTokenProvider {
    private val lock = Mutex()

    override val isAvailable: Boolean = true

    override suspend fun currentToken(): String = lock.withLock {
        store.get(KEY) ?: (PREFIX + Uuid.random()).also { store.set(KEY, it) }
    }

    override suspend fun discard() = lock.withLock { store.remove(KEY) }

    /** A new address, announced the way a real push service announces one (exercises the `TokenChanged` trigger). */
    suspend fun rotate() {
        discard()
        onNewToken()
    }

    companion object {
        const val KEY = "dev_fake_push_token"
        const val PREFIX = "dev-fake-push-"
    }
}
