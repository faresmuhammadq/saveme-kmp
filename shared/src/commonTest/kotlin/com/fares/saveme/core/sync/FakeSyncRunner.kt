package com.fares.saveme.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A runner for the features' tests: records what was asked and answers with [outcome]. Given an
 * [engine] (and a [scope] for fire-and-forget uploads) an upload really drains the queue, so a
 * saved change is sent as it is with the real runner.
 */
class FakeSyncRunner(
    var outcome: ReadOutcome = ReadOutcome.Done,
    private val engine: SyncEngine? = null,
    private val scope: CoroutineScope? = null,
) : SyncRunner {
    val refreshes = mutableListOf<Pair<ReadTarget, Boolean>>()
    var uploads = 0
    var cancelled = 0

    /** Runs before each answer: lets a test change the phone's copy the way a feed read would. */
    var onRefresh: suspend (ReadTarget, Boolean) -> Unit = { _, _ -> }

    override suspend fun refresh(target: ReadTarget, force: Boolean): ReadOutcome {
        refreshes += target to force
        onRefresh(target, force)
        return outcome
    }

    override suspend fun upload() {
        uploads++
        engine?.drain()
    }

    override fun requestUpload() {
        uploads++
        val engine = engine
        val scope = scope
        if (engine != null && scope != null) scope.launch { engine.drain() }
    }

    override suspend fun runBackground() = upload()

    override fun cancel() {
        cancelled++
    }
}
