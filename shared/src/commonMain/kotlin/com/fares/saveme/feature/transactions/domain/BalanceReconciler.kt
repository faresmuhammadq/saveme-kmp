package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.feature.transactions.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Settles the balance carry (research R-09 of spec 008, R-09 of spec 009): while some synced change's effect
 * still waits to be included in a wallet read, it waits a second (a burst of sends becomes one read), then
 * reads each wallet those changes touch, one feed at a time, and for each read that completed clears that
 * wallet's part of every carry the server confirmed before the read started. A failed read keeps the carry
 * and is tried again later.
 */
class BalanceReconciler(
    private val repository: TransactionRepository,
    private val runner: SyncRunner,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val debounce: Duration = 1.seconds,
    private val retryAfter: Duration = 30.seconds,
) {
    private val readLock = Mutex()
    private var watching: Job? = null

    /** Starts watching for carries that wait for a wallet read; calling it again while watching does nothing. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (watching?.isActive == true) return
        watching = scope.launch {
            repository.observeCarried()
                .map { rows -> rows.maxOfOrNull { it.carryConfirmedAt.orEmpty() } }
                .distinctUntilChanged()
                .collectLatest { newest ->
                    if (newest == null) return@collectLatest
                    delay(debounce)
                    while (!settle()) delay(retryAfter)
                }
        }
    }

    /** One read of every wallet a carry waits on; true when all of them completed (so the carry they covered is gone). */
    suspend fun settle(): Boolean = readLock.withLock {
        var allRead = true
        for (walletId in repository.carriedWalletIds()) {
            val startedAt = clock.now()
            if (runner.refresh(ReadTarget.Wallet(walletId), force = true) == ReadOutcome.Done) {
                repository.clearCarryFor(walletId, startedAt)
            } else {
                allRead = false
            }
        }
        allRead
    }

    /** Foreground and connectivity: try now instead of waiting for the retry. */
    fun kick() {
        scope.launch { if (repository.hasCarry()) settle() }
    }

    /** "Use the other device's" and the like: one read of the wallets concerned (all of them when none is named), no carry involved. */
    fun requestWalletRead(vararg walletIds: String) {
        scope.launch {
            if (walletIds.isEmpty()) {
                runner.refresh(ReadTarget.Everything, force = true)
            } else {
                walletIds.distinct().forEach { runner.refresh(ReadTarget.Wallet(it), force = true) }
            }
        }
    }
}
