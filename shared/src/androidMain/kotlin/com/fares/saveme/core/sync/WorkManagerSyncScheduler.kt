package com.fares.saveme.core.sync

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Drains the sync queue through WorkManager, so changes leave the phone even when the app is
 * closed or after a restart, as soon as a connection exists (FR-005, FR-006, research R-03).
 */
class WorkManagerSyncScheduler(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context)

    /** When the pending run is due; null when nothing is pending (or after a restart). */
    private var plannedAt: Long? = null

    override fun enqueue(delay: Duration) {
        val runAt = now() + delay.inWholeMilliseconds
        val policy = synchronized(this) {
            val planned = plannedAt
            when {
                // Nothing pending, or the pending run has started: queue after it. REPLACE would
                // cancel a drain that is sending right now.
                planned == null || planned <= now() -> ExistingWorkPolicy.APPEND_OR_REPLACE
                // A sooner run supersedes a later one.
                runAt < planned -> ExistingWorkPolicy.REPLACE
                else -> return
            }.also { plannedAt = runAt }
        }
        val request = OneTimeWorkRequestBuilder<SyncDrainWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .apply {
                when {
                    delay.isPositive() -> setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    // JobScheduler batches ordinary jobs of a closed app for up to ~30 min even when
                    // they are ready; expedited work runs as soon as a connection exists (SC-003).
                    // Below API 31 expedited work needs a foreground notification, so it stays ordinary.
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    override fun cancel() {
        synchronized(this) { plannedAt = null }
        workManager.cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "sync-drain"
    }
}
