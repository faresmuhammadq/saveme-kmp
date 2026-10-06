package com.fares.saveme.feature.notifications

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.fares.saveme.feature.notifications.domain.PushReconciler
import com.fares.saveme.feature.notifications.domain.ReconcileOutcome
import com.fares.saveme.feature.notifications.domain.ReconcileTrigger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/** The Android adapters of spec 017: permission state, the tap extras, the registration job and its worker (R-05, R-14, FR-044). */
@RunWith(RobolectricTestRunner::class)
class AndroidAdaptersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var outcome: ReconcileOutcome = ReconcileOutcome.Registered
    private val triggers = mutableListOf<ReconcileTrigger>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        startKoin { modules(module { single<PushReconciler> { PushReconciler { trigger -> triggers += trigger; outcome } } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.VANILLA_ICE_CREAM)
    }

    private fun sdk(level: Int) = ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", level)

    @Test
    fun onAndroid13PlusAllowedNeedsThePermissionAndNotificationsNotBlocked() {
        val state = AndroidNotificationPermissionState(context)
        assertTrue(state.canAskAtRuntime())
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(state.notificationsAllowed())
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(state.notificationsAllowed())
    }

    @Test
    fun onAndroid12AndEarlierThereIsNoRuntimePermissionToAsk() {
        sdk(Build.VERSION_CODES.S_V2)
        val state = AndroidNotificationPermissionState(context)
        assertFalse(state.canAskAtRuntime())
        // Allowed follows the app's notification switch alone, which is on by default.
        assertTrue(state.notificationsAllowed())
    }

    @Test
    fun tapExtrasKeepOnlyContractKeysWithStringValues() {
        val bundle = Bundle().apply {
            putString("type", "budget_exceeded")
            putString("notification_id", "n-1")
            putString("budget_id", "b1")
            putString("google.message_id", "m")
            putString("evil", "x")
            putInt("schema", 1)
            putBoolean("category", true)
        }
        val tap = PushTapExtras.from(bundle)!!
        assertEquals(mapOf("type" to "budget_exceeded", "notification_id" to "n-1", "budget_id" to "b1"), tap)
    }

    @Test
    fun aLauncherIntentWithoutATypeAndAnIdIsNotATap() {
        assertNull(PushTapExtras.from(null))
        assertNull(PushTapExtras.from(Bundle()))
        assertNull(PushTapExtras.from(Bundle().apply { putString("type", "budget_exceeded") }))
        assertNull(PushTapExtras.from(Bundle().apply { putString("notification_id", "n-1") }))
    }

    @Test
    fun theIntakeSubmitsOneTapAndStripsItsKeysSoARedeliveryCannotRepeatIt() {
        val intent = Intent().putExtra("type", "budget_exceeded").putExtra("notification_id", "n-1").putExtra("google.message_id", "m").putExtra("keep", "me")
        val submitted = mutableListOf<Map<String, String>>()
        assertTrue(PushTapExtras.intake(intent) { submitted += it })
        assertEquals(1, submitted.size)
        assertFalse(PushTapExtras.intake(intent) { submitted += it })
        assertEquals(1, submitted.size)
        assertEquals("me", intent.getStringExtra("keep"))
        assertFalse(intent.hasExtra("type") || intent.hasExtra("google.message_id"))
    }

    @Test
    fun aPlainLauncherIntentSubmitsNothing() {
        val submitted = mutableListOf<Map<String, String>>()
        assertFalse(PushTapExtras.intake(Intent(Intent.ACTION_MAIN)) { submitted += it })
        assertFalse(PushTapExtras.intake(null) { submitted += it })
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun theSchedulerEnqueuesOneUniqueJobThatNeedsANetwork() {
        val scheduler = WorkManagerPushRegistrationScheduler(context)
        scheduler.request(ReconcileTrigger.SignedIn)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerPushRegistrationScheduler.UNIQUE_WORK).get()
        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
        scheduler.cancel()
        val after = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerPushRegistrationScheduler.UNIQUE_WORK).get()
        assertTrue(after.all { it.state == WorkInfo.State.CANCELLED })
    }

    private fun run(trigger: ReconcileTrigger = ReconcileTrigger.Foreground): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<PushRegistrationWorker>(context)
            .setInputData(workDataOf(WorkManagerPushRegistrationScheduler.KEY_TRIGGER to trigger.name))
            .build()
            .doWork()
    }

    @Test
    fun theWorkerPassesItsTriggerAndMapsEachOutcome() {
        outcome = ReconcileOutcome.Registered
        assertEquals(ListenableWorker.Result.success(), run(ReconcileTrigger.TokenChanged))
        assertEquals(listOf(ReconcileTrigger.TokenChanged), triggers)
        listOf(ReconcileOutcome.Nothing, ReconcileOutcome.Unregistered, ReconcileOutcome.Stopped).forEach {
            outcome = it
            assertEquals(ListenableWorker.Result.success(), run())
        }
        outcome = ReconcileOutcome.RetryLater
        assertEquals(ListenableWorker.Result.retry(), run())
    }

    @Test
    fun aRateLimitedRunSucceedsAndQueuesANewRunAfterTheWait() {
        outcome = ReconcileOutcome.RetryAfter(300)
        assertEquals(ListenableWorker.Result.success(), run(ReconcileTrigger.SignedIn))
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerPushRegistrationScheduler.UNIQUE_WORK).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }
}
