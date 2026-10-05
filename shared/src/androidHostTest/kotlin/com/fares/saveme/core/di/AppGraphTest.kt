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
        // Spec 010 R-10: the hook is a union of every bound source (transactions' and recurring's).
        assertTrue(koin.getAll<PendingCategoryUses>().any { it is TransactionCategoryUses })
        assertTrue(koin.getAll<WalletDeletionParticipant>().any { it is TransactionsWalletCleanup })
        assertEquals(1, koin.getAll<PendingPostings>().size)
        // Transactions' and recurring's, and budgets' (spec 012 FR-021).
        assertEquals(3, koin.getAll<PendingCategoryUses>().size)
        assertNotNull(koin.get<TransactionsSyncCoordinator>())
    }

    @Test
    fun theRecurringModuleStartsWithBothHandlers() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        assertNotNull(koin.get<SyncEngine>())
        val types = koin.getAll<SyncHandler>().map { it.recordType }
        assertTrue("recurring_rule" in types)
        assertTrue("occurrence" in types)
        assertTrue(koin.get<com.fares.saveme.feature.transactions.domain.OccurrenceTransactions>() is com.fares.saveme.feature.transactions.domain.DefaultOccurrenceTransactions)
    }

    @Test
    fun theRecurringHooksOverrideTheDefaultsAndTheServiceResolves() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        assertTrue(koin.get<com.fares.saveme.feature.recurring.domain.RecurringService>() is com.fares.saveme.feature.recurring.domain.DefaultRecurringService)
        assertTrue(koin.get<com.fares.saveme.feature.transactions.domain.RecurringLinks>() is com.fares.saveme.feature.recurring.domain.RecurringLinksImpl)
        assertTrue(koin.get<com.fares.saveme.feature.auth.domain.DueSummary>() is com.fares.saveme.feature.recurring.domain.RecurringDueSummary)
        assertTrue(koin.getAll<PendingCategoryUses>().any { it is com.fares.saveme.feature.recurring.domain.RecurringCategoryUses })
        assertTrue(koin.getAll<WalletDeletionParticipant>().any { it is com.fares.saveme.feature.recurring.domain.RecurringWalletCleanup })
        assertNotNull(koin.get<com.fares.saveme.feature.categories.domain.CategoriesService>())
    }

    @Test
    fun theBudgetsModuleStartsWithItsHandlerAndApplierAndRebindsTheHooks() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        // The sync engine is built from every handler: a budgets class that resolved it eagerly would loop at start.
        assertNotNull(koin.get<SyncEngine>())
        assertTrue(koin.getAll<SyncHandler>().any { it is com.fares.saveme.feature.budgets.data.BudgetSyncHandler && it.recordType == "budget" })
        assertTrue(koin.getAll<FeedApplier>().any { it is com.fares.saveme.feature.budgets.data.BudgetFeedApplier && it.rowType == "budget" && it.scopeKind == ScopeKind.User })
        assertTrue(koin.get<com.fares.saveme.feature.transactions.domain.OwnSpending>() is com.fares.saveme.feature.transactions.domain.TransactionOwnSpending)
        assertTrue(koin.get<com.fares.saveme.feature.auth.domain.BudgetSummary>() is com.fares.saveme.feature.budgets.domain.BudgetsHomeSummary)
        assertTrue(koin.getAll<PendingCategoryUses>().any { it is com.fares.saveme.feature.budgets.domain.BudgetCategoryUses })
        assertTrue(koin.get<com.fares.saveme.feature.budgets.domain.BudgetsService>() is com.fares.saveme.feature.budgets.domain.DefaultBudgetsService)
        assertNotNull(koin.get<com.fares.saveme.feature.budgets.domain.BudgetsSyncCoordinator>())
        assertNotNull(koin.get<CategoriesService>())
    }

    @Test
    fun theSharingModuleStartsWithItsHandlerApplierAndParticipant() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        // The sync engine is built from every handler: a sharing class that resolved it eagerly would loop at start.
        assertNotNull(koin.get<SyncEngine>())
        assertTrue(koin.getAll<SyncHandler>().any { it is com.fares.saveme.feature.sharing.data.InvitationSyncHandler && it.recordType == "invitation" })
        assertTrue(koin.getAll<FeedApplier>().any { it is com.fares.saveme.feature.sharing.data.InvitationFeedApplier && it.rowType == "invitation" && it.scopeKind == ScopeKind.Wallet })
        assertTrue(koin.getAll<WalletDeletionParticipant>().any { it is com.fares.saveme.feature.sharing.domain.InvitationsWalletCleanup })
        assertTrue(koin.get<com.fares.saveme.feature.sharing.domain.SharingService>() is com.fares.saveme.feature.sharing.domain.DefaultSharingService)
        // The hooks other features own are rebound by sharing's module (listed last), and the memory holders are sign-out cleaners.
        assertTrue(koin.get<com.fares.saveme.feature.wallets.domain.SharingGlance>() is com.fares.saveme.feature.sharing.domain.DefaultSharingGlance)
        assertTrue(koin.get<com.fares.saveme.feature.auth.domain.HeldInvitationGlimpse>() is com.fares.saveme.feature.sharing.domain.DefaultHeldInvitationGlimpse)
        val cleaners = koin.getAll<UserDataCleaner>()
        assertTrue(cleaners.any { it is com.fares.saveme.feature.sharing.domain.HeldInvitation })
        assertTrue(cleaners.any { it is com.fares.saveme.feature.sharing.domain.ShareLinks })
        assertTrue(cleaners.any { it is com.fares.saveme.feature.sharing.domain.PlanDemotionNotes })
        assertTrue(koin.get<com.fares.saveme.feature.wallets.domain.WalletMemberships>() is com.fares.saveme.feature.wallets.domain.DefaultWalletMemberships)
        assertNotNull(koin.get<com.fares.saveme.feature.sharing.domain.InvitationLinkParser>())
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
        assertEquals(12, appliers.size)
        assertEquals(
            setOf(
                ScopeKind.User to "profile", ScopeKind.User to "device_session", ScopeKind.User to "category", ScopeKind.User to "wallet_member",
                ScopeKind.Wallet to "wallet", ScopeKind.Wallet to "wallet_member", ScopeKind.Wallet to "category_reference", ScopeKind.Wallet to "transaction",
                ScopeKind.Wallet to "recurring_rule", ScopeKind.Wallet to "occurrence", ScopeKind.User to "budget",
                ScopeKind.Wallet to "invitation",
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
