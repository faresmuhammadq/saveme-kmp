package com.fares.saveme.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock shared by the engine's upload and the feed puller (spec 009 FR-026): a batch answer and
 * a feed row for the same record are never applied at the same time, so the equal-version case
 * cannot race. Not re-entrant: nothing holding it may call something that takes it.
 */
class SyncLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
