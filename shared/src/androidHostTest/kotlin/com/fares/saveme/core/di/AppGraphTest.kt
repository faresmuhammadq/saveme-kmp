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
import org.junit.Assert.assertFalse
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
    fun theExportAndDeletionServicesAndStoresResolveWithoutEagerlyResolvingTheEngine() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules + org.koin.dsl.module {
                single<com.fares.saveme.core.database.AppDatabase> {
                    androidx.room.Room.inMemoryDatabaseBuilder<com.fares.saveme.core.database.AppDatabase>(context)
                        .setDriver(androidx.sqlite.driver.AndroidSQLiteDriver()).build()
                }
            },
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        assertTrue(koin.get<com.fares.saveme.feature.account.domain.export.ExportService>() is com.fares.saveme.feature.account.domain.export.DefaultExportService)
        assertNotNull(koin.get<com.fares.saveme.feature.account.domain.deletion.DeletionPlanService>())
        assertNotNull(koin.get<com.fares.saveme.feature.account.ui.export.list.ExportsViewModel>())
        assertNotNull(koin.get<com.fares.saveme.feature.account.ui.export.request.ExportRequestViewModel> { org.koin.core.parameter.parametersOf("") })
        assertNotNull(koin.get<com.fares.saveme.feature.account.ui.export.request.ExportRequestViewModel> { org.koin.core.parameter.parametersOf("w1") })
        assertNotNull(koin.get<com.fares.saveme.feature.account.ui.export.detail.ExportDetailViewModel> { org.koin.core.parameter.parametersOf("e1") })
        assertNotNull(koin.get<com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountViewModel>())
        assertNotNull(koin.get<com.fares.saveme.feature.auth.ui.home.HomeViewModel>())
        assertNotNull(koin.get<SyncEngine>())
    }

    @Test
    fun theReportsModuleResolvesItsHooks() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        // The sync engine is built from every handler: a reports class that resolved it eagerly would loop at start.
        assertNotNull(koin.get<SyncEngine>())
        assertTrue(koin.get<com.fares.saveme.feature.transactions.domain.ReportLedger>() is com.fares.saveme.feature.transactions.domain.TransactionReportLedger)
        assertNotNull(koin.get<com.fares.saveme.feature.recurring.domain.ScheduledMoney>())
        assertTrue(koin.get<com.fares.saveme.feature.recurring.domain.ScheduledMoney>() is com.fares.saveme.feature.recurring.domain.OccurrenceScheduledMoney)
        assertTrue(koin.get<com.fares.saveme.feature.reports.domain.ReportsService>() is com.fares.saveme.feature.reports.domain.DefaultReportsService)
        assertTrue("the development build compares", koin.get<com.fares.saveme.feature.reports.domain.ReportParityProbe>() is com.fares.saveme.feature.reports.domain.DevReportParityProbe)
        assertTrue(koin.getAll<UserDataCleaner>().any { it is com.fares.saveme.feature.reports.domain.ReportsCleaner })
        // Reports are computed, not synced: no handler and no applier of their own.
        assertTrue(koin.getAll<SyncHandler>().none { "report" in it.recordType })
        assertTrue(koin.getAll<FeedApplier>().none { "report" in it.rowType })
        assertNotNull(koin.get<CategoriesService>())
    }

    @Test
    fun theReleaseGraphBindsNoParityProbe() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Prod, isDebugTools = false),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        assertTrue("release never calls the report figure endpoints (FR-028)", koin.get<com.fares.saveme.feature.reports.domain.ReportParityProbe>() === com.fares.saveme.feature.reports.domain.NoParityProbe)
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

    @Test
    fun thePushModuleStartsWithItsReceiverCoordinatorParticipantAndCleanerAndTheFakeProviderInDevelopment() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        // The session manager's cleaners and the sign-out use case are built from the push classes: a cycle would overflow the stack at start.
        assertNotNull(koin.get<SyncEngine>())
        assertNotNull(koin.get<com.fares.saveme.feature.notifications.domain.PushRegistrationService>())
        assertNotNull(koin.get<com.fares.saveme.feature.notifications.domain.PushReceiver>())
        assertNotNull(koin.get<com.fares.saveme.feature.notifications.domain.PushCoordinator>())
        assertEquals(1, koin.getAll<com.fares.saveme.core.session.SignOutParticipant>().count { it is com.fares.saveme.feature.notifications.domain.PushSignOutParticipant })
        assertEquals(1, koin.getAll<UserDataCleaner>().count { it is com.fares.saveme.feature.notifications.domain.PushDataCleaner })
        assertNotNull(koin.get<com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase>())
        assertTrue(koin.get<com.fares.saveme.feature.notifications.domain.PushTokenProvider>() is com.fares.saveme.devtools.push.DevFakePushTokenProvider)
        assertTrue(koin.get<com.fares.saveme.feature.notifications.domain.PushTokenProvider>().isAvailable)
        assertNotNull(koin.get<com.fares.saveme.feature.notifications.domain.NotificationOpenService>())
        assertNotNull(koin.get<com.fares.saveme.feature.notifications.domain.NotificationPreferencesService>())
        // The account screen offers both new rows in a development build.
        val account = koin.get<com.fares.saveme.feature.account.ui.account.AccountViewModel>()
        assertTrue(account.state.value.notificationsAvailable)
        assertTrue(account.state.value.devToolsAvailable)
        assertNotNull(koin.get<com.fares.saveme.devtools.push.SimulatePushViewModel>())
    }

    @Test
    fun theReleaseGraphBindsNoPushProviderAndShowsNeitherRow() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Prod, isDebugTools = false),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        assertTrue(koin.get<com.fares.saveme.feature.notifications.domain.PushTokenProvider>() === com.fares.saveme.feature.notifications.domain.UnavailablePushTokenProvider)
        assertFalse(koin.get<com.fares.saveme.feature.notifications.domain.PushTokenProvider>().isAvailable)
        val account = koin.get<com.fares.saveme.feature.account.ui.account.AccountViewModel>()
        assertFalse(account.state.value.notificationsAvailable)
        assertFalse(account.state.value.devToolsAvailable)
        assertNotNull(koin.get<SyncEngine>())
    }

    /** Spec 018: the verifier needs the session manager, which needs the billing cleaner; the cleaner must not resolve the verifier while the graph is built. */
    @Test
    fun theGooglePlayGraphResolvesWithoutACycleAndTheDevBuildWrapsThePortWithTheScriptedOne() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }

        val koin = GlobalContext.get()
        assertTrue(koin.get<com.fares.saveme.feature.billing.data.play.PlayBillingClient>() is com.fares.saveme.devtools.playbilling.SwitchingPlayBillingClient)
        assertTrue(koin.get<com.fares.saveme.feature.billing.data.play.PlayBillingClient>().platformSupported)
        assertNotNull(koin.get<com.fares.saveme.feature.billing.domain.play.PlayPurchaseVerifier>())
        assertNotNull(koin.get<com.fares.saveme.feature.billing.domain.play.PlayRestoreService>())
        assertNotNull(koin.get<com.fares.saveme.feature.billing.domain.play.PlayBillingNotes>())
        assertNotNull(koin.get<com.fares.saveme.feature.billing.domain.BillingSyncCoordinator>())
        assertTrue(koin.getAll<UserDataCleaner>().any { it is com.fares.saveme.feature.billing.domain.play.PlayBillingCleaner })
        assertNotNull(koin.get<com.fares.saveme.devtools.playbilling.PlayBillingDevViewModel>())
    }

    /** FR-056, FR-004: a release graph binds the unavailable port (Android until the Play Billing wiring); the scripted client is not in it. */
    @Test
    fun theReleaseGraphBindsTheUnavailablePortAndNoScriptedClient() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Prod, isDebugTools = false),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }

        val port = GlobalContext.get().get<com.fares.saveme.feature.billing.data.play.PlayBillingClient>()
        assertTrue(port is com.fares.saveme.feature.billing.data.play.UnavailablePlayBillingClient)
        assertTrue(port.platformSupported)
        assertFalse(port is com.fares.saveme.devtools.playbilling.SwitchingPlayBillingClient)
        assertNotNull(GlobalContext.get().get<com.fares.saveme.feature.billing.domain.play.PlayPurchaseVerifier>())
    }
}
