package com.fares.saveme.core.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.fares.saveme.appFeatureModules
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.FeedPuller
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncSignOutCleaner
import com.fares.saveme.core.sync.WalletScopes
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.feature.account.domain.AccountSyncCoordinator
import com.fares.saveme.feature.wallets.domain.DefaultWalletScopes
import com.fares.saveme.feature.categories.data.CategorySyncHandler
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.CategoryReferencesCleanup
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.domain.TransactionCategoryUses
import com.fares.saveme.feature.transactions.domain.TransactionPostings
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.transactions.domain.TransactionsSyncCoordinator
import com.fares.saveme.feature.transactions.domain.TransactionsWalletCleanup
import com.fares.saveme.feature.wallets.domain.PendingPostings
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import com.fares.saveme.feature.wallets.domain.WalletsService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

/**
 * The app's real Koin graph starts and resolves the sync engine and the wallets service. A
 * dependency cycle between a feature's sync handler and the engine overflows the stack at start
 * (the 006 E2E run crashed this way), which no feature-level test with hand-built objects sees.
 */
@RunWith(RobolectricTestRunner::class)
class AppGraphTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun theAppGraphStartsAndResolvesTheSyncEngine() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }

        val koin = GlobalContext.get()
        assertNotNull(koin.get<SyncEngine>())
        assertNotNull(koin.get<WalletsService>())
    }

    @Test
    fun theCategoryHandlerAndItsWalletDeletionParticipantAreRegistered() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }

        val koin = GlobalContext.get()
        assertNotNull(koin.get<CategoriesService>())
        assertTrue(koin.getAll<SyncHandler>().any { it is CategorySyncHandler && it.recordType == "category" })
        assertTrue(koin.getAll<WalletDeletionParticipant>().any { it is CategoryReferencesCleanup })
    }

    @Test
    fun theTransactionsHandlerHooksAndCleanupAreRegisteredOnceEach() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }

        val koin = GlobalContext.get()
        assertNotNull(koin.get<TransactionsService>())
        assertTrue(koin.getAll<SyncHandler>().any { it is TransactionSyncHandler && it.recordType == "transaction" })
        // C4's and C5's zero bindings are gone: the hooks resolve to the transactions implementations.
        assertTrue(koin.get<PendingPostings>() is TransactionPostings)
        assertTrue(koin.get<PendingCategoryUses>() is TransactionCategoryUses)
        assertTrue(koin.getAll<WalletDeletionParticipant>().any { it is TransactionsWalletCleanup })
        assertEquals(1, koin.getAll<PendingPostings>().size)
        assertEquals(1, koin.getAll<PendingCategoryUses>().size)
        assertNotNull(koin.get<TransactionsSyncCoordinator>())
    }

    @Test
    fun theFeedGraphHasOneApplierPerConsumedPairOneWalletScopesAndTheRunnerAndPullerResolveWithoutACycle() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }

        val koin = GlobalContext.get()
        assertNotNull(koin.get<SyncRunner>())
        assertNotNull(koin.get<FeedPuller>())
        assertNotNull(koin.get<SyncEngine>())
        val appliers = koin.getAll<FeedApplier>()
        val pairs = appliers.map { it.scopeKind to it.rowType }
        assertEquals(8, appliers.size)
        assertEquals(
            setOf(
                ScopeKind.User to "profile", ScopeKind.User to "device_session", ScopeKind.User to "category", ScopeKind.User to "wallet_member",
                ScopeKind.Wallet to "wallet", ScopeKind.Wallet to "wallet_member", ScopeKind.Wallet to "category_reference", ScopeKind.Wallet to "transaction",
            ),
            pairs.toSet(),
        )
        assertEquals("one applier per (scope kind, row type)", pairs.size, pairs.toSet().size)
        assertEquals(1, koin.getAll<WalletScopes>().size)
        assertTrue(koin.get<WalletScopes>() is DefaultWalletScopes)
        assertTrue(koin.getAll<UserDataCleaner>().any { it is SyncSignOutCleaner })
        assertNotNull(koin.get<AccountSyncCoordinator>())
    }
}
