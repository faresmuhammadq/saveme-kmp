package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.wallets.domain.WalletsService
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
 * Settles the balance carry (research R-09): while some synced change's effect still waits to be
 * included in a wallet read, it waits a second (a burst of sends becomes one read), notes the start
 * time, re-reads the wallets and, only when that read completed, clears every carry the server
 * confirmed at or before the start. A failed read keeps the carry and is tried again later.
 */
class BalanceReconciler(
    private val repository: TransactionRepository,
    private val wallets: WalletsService,
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

    /** One read; true when it completed (so the carry it covered is gone). */
    suspend fun settle(): Boolean = readLock.withLock {
        val startedAt = clock.now()
        if (wallets.refresh(force = true) != RefreshOutcome.Done) return@withLock false
        repository.clearCarryConfirmedUpTo(startedAt)
        true
    }

    /** Foreground and connectivity: try now instead of waiting for the retry. */
    fun kick() {
        scope.launch { if (repository.hasCarry()) settle() }
    }

    /** "Use the other device's" and a list read that changed rows: one read of the wallets, no carry involved. */
    fun requestWalletRead() {
        scope.launch { wallets.refresh(force = true) }
    }
}
