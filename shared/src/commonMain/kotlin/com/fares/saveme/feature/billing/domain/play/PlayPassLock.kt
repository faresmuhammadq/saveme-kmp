package com.fares.saveme.feature.billing.domain.play

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One Google Play pass at a time per person, verify and restore alike, so a purchase is never sent twice at once (FR-011).
 * A trigger arriving during a pass waits for it and then runs once more if anything changed.
 */
class PlayPassLock {
    private val mutex = Mutex()
    private val dirty = MutableStateFlow(false)

    /** Runs [pass] now, or after the pass in progress; coalesces triggers that arrive meanwhile into one more run. */
    suspend fun runPass(pass: suspend () -> Unit) {
        while (true) {
            if (mutex.tryLock()) {
                try {
                    do {
                        dirty.value = false
                        pass()
                    } while (dirty.value)
                } finally {
                    mutex.unlock()
                }
                return
            }
            dirty.value = true
            mutex.withLock { }
            if (!dirty.value) return
        }
    }

    /** Runs [block] alone, after any pass in progress; verify passes triggered meanwhile run once afterwards. */
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
