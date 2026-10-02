package com.fares.saveme.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import com.fares.saveme.feature.account.FakeSyncScopeDao

/** The background half of US1: a scheduled drain waits for a network and runs the engine (FR-006). */
@RunWith(RobolectricTestRunner::class)
class SyncDrainWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val handler = FakeHandler()
    private val dao = FakePendingChangeDao()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val engine = SyncEngine(dao, FakeSyncScopeDao(), listOf(handler), FakeSyncScheduler(), currentAccountId = { "u1" })
        startKoin { modules(module { single { engine } }) }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun schedulingEnqueuesOneDrainThatNeedsANetwork() {
        WorkManagerSyncScheduler(context).enqueue()
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerSyncScheduler.UNIQUE_WORK).get()
        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    }

    @Test
    fun theWorkerDrainsTheQueue() = runBlocking {
        val engine = org.koin.core.context.GlobalContext.get().get<SyncEngine>()
        engine.enqueue("note", "n1", SyncScope(ScopeKind.User, "u1"), 1, "{}")
        val worker = TestListenableWorkerBuilder<SyncDrainWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(listOf("n1"), handler.sent.map { it.recordId })
    }
}
