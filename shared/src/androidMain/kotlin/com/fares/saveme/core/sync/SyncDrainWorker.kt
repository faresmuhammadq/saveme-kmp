package com.fares.saveme.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Runs one drain. The engine schedules its own follow-ups; retry() is only for the unexpected. */
class SyncDrainWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result = try {
        engine.drain()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.withTag("sync").e(e) { "drain failed" }
        Result.retry()
    }
}
