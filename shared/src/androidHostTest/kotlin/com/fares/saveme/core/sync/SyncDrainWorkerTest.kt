package com.fares.saveme.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

/** The background half of US1: a scheduled drain waits for a network and runs the sync runner (FR-006, spec 009 FR-027). */
@RunWith(RobolectricTestRunner::class)
class SyncDrainWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val handler = FakeHandler()
    private val dao = FakePendingChangeDao()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val scopes = FakeSyncScopeDao()
        val api = FakeSyncApi()
        val lock = SyncLock()
        val engine = SyncEngine(dao, scopes, listOf(handler), FakeSyncScheduler(), currentAccountId = { "u1" }, syncApi = api, lock = lock)
        val puller = FeedPuller(api, scopes, { emptyList() }, { null }, lock, { "u1" })
        val runner: SyncRunner = DefaultSyncRunner(engine, puller, { null }, { "u1" }, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        startKoin { modules(module { single { engine }; single { runner }; single { api } }) }
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

    @Test
    fun theWorkerReadsTheScopesTheUploadWroteToAndNothingElse() = runBlocking {
        handler.answer = { EngineHarness.ok(2) }
        val koin = org.koin.core.context.GlobalContext.get()
        val api = koin.get<FakeSyncApi>()
        koin.get<SyncEngine>().enqueue("note", "n1", SyncScope(ScopeKind.User, "u1"), 1, "{}")
        val worker = TestListenableWorkerBuilder<SyncDrainWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals("the person's scope was written to, so it is read once", listOf<String?>(null), api.userCalls)
        assertEquals(emptyList<Pair<String, String?>>(), api.walletCalls)
    }
}
