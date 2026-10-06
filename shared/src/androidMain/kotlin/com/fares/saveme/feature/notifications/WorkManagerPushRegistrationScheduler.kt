package com.fares.saveme.feature.notifications

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import com.fares.saveme.feature.notifications.domain.PushReconciler
import com.fares.saveme.feature.notifications.domain.PushRegistrationScheduler
import com.fares.saveme.feature.notifications.domain.ReconcileOutcome
import com.fares.saveme.feature.notifications.domain.ReconcileTrigger
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * One unique, network-bound job that reconciles the registration (research R-05): it survives process death, waits for
 * a connection and backs off exponentially from 30 s. The trigger travels as input data.
 */
class WorkManagerPushRegistrationScheduler(context: Context) : PushRegistrationScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun request(trigger: ReconcileTrigger) = enqueue(workManager, trigger, delaySeconds = 0)

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "push-registration"
        const val KEY_TRIGGER = "trigger"

        internal fun enqueue(workManager: WorkManager, trigger: ReconcileTrigger, delaySeconds: Long) {
            val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInputData(Data.Builder().putString(KEY_TRIGGER, trigger.name).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .apply {
                    when {
                        delaySeconds > 0 -> setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                        // Expedited runs as soon as a connection exists; below API 31 it would need a foreground notification.
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/** Runs one [PushReconciler.reconcile] and maps its outcome to WorkManager's (research R-05). */
class PushRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val reconciler: PushReconciler by inject()

    override suspend fun doWork(): Result {
        val trigger = inputData.getString(WorkManagerPushRegistrationScheduler.KEY_TRIGGER)
            ?.let { name -> ReconcileTrigger.entries.firstOrNull { it.name == name } }
            ?: ReconcileTrigger.Foreground
        return try {
            when (val outcome = reconciler.reconcile(trigger)) {
                ReconcileOutcome.RetryLater -> Result.retry()
                is ReconcileOutcome.RetryAfter -> {
                    // The server's wait: a new run after it, then this one is done (never a tight retry).
                    WorkManagerPushRegistrationScheduler.enqueue(WorkManager.getInstance(applicationContext), trigger, outcome.seconds)
                    Result.success()
                }
                ReconcileOutcome.Nothing, ReconcileOutcome.Registered, ReconcileOutcome.Unregistered, ReconcileOutcome.Stopped -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.withTag("push").e { "reconcile failed (${e::class.simpleName})" }
            Result.retry()
        }
    }
}
