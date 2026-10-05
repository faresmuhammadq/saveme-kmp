package com.fares.saveme.feature.sharing

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.fares.saveme.appFeatureModules
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.di.androidPlatformModule
import com.fares.saveme.core.di.initKoin
import com.fares.saveme.core.sync.PendingChangeEntity
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import com.fares.saveme.feature.sharing.data.local.InvitationEntity
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.dto.WalletMemberDto
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.domain.WalletMemberships
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

/**
 * Every way of losing a wallet purges it from the phone with everything it holds, through the real participants of the app's
 * graph (spec 013 FR-023, SC-003): the person's own membership ending on the user feed, leaving (answer 200 and answer 404)
 * and the wallet feed answering 404.
 */
@RunWith(RobolectricTestRunner::class)
class AccessLostPurgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val stamp = "2026-09-28T10:00:00Z"
    private lateinit var db: AppDatabase
    private lateinit var repository: WalletRepository

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            // The graph's own database uses the bundled native driver, which host tests cannot load: an in-memory one stands in.
            featureModules = appFeatureModules + module {
                single<AppDatabase> { Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(AndroidSQLiteDriver()).build() }
            },
        ) { androidContext(context) }
        val koin = GlobalContext.get()
        db = koin.get()
        repository = koin.get()
    }

    @After
    fun tearDown() {
        db.close()
        stopKoin()
    }

    private suspend fun seedWallet(id: String) {
        db.walletDao().upsert(WalletEntity(id, "u1", "cash", "Home $id", "EGP", 100, 100, null, null, null, false, "editor", 3, 1, stamp, stamp, stamp))
        db.walletMemberDao().insertAll(
            listOf(WalletMemberEntity("m-$id-1", id, "u1", "Sara", "editor", 1, stamp), WalletMemberEntity("m-$id-2", id, "u2", "Omar", "owner", 1, stamp)),
        )
        db.categoryReferenceDao().replace(id, listOf(CategoryReferenceEntity("r-$id", id, "p1", "u2", "expense", "Partner", null, null, 1, 1, stamp)))
        db.transactionDao().upsert(
            TransactionEntity("t-$id", "u1", id, null, "expense", "2026-09-28", 4_550, "EGP", null, null, null, null, false, "u2", null, null, 1, 1, stamp, stamp, null, stamp),
        )
        db.recurringRuleDao().upsert(
            RecurringRuleEntity(
                "rule-$id", "u1", id, "custom", "active", "expense", 450_000, "per_occurrence", "EGP", null, null, "Rent", null, null, false,
                "monthly", 1, 1, "2026-11-01", null, null, null, null, "Africa/Cairo", "u2", 1, 1, stamp, stamp, stamp,
            ),
        )
        db.occurrenceDao().upsert(
            OccurrenceEntity(
                "occ-$id", "u1", id, "rule-$id", 1, false, "2026-11-01", "2026-11-01", "scheduled", "expense", 450_000, "EGP", null, null, "Rent",
                null, null, false, null, null, 1, 1, stamp, stamp, stamp,
            ),
        )
        db.invitationDao().upsert(InvitationEntity("inv-$id", "u1", id, "viewer", "email", "karim@example.com", "u2", "pending", "2026-10-09T10:00:00Z", 1, 1, stamp, stamp, stamp))
        db.pendingChangeDao().upsert(PendingChangeEntity("invitation", "inv-new-$id", "u1", "wallet", id, 0, "{}", false, false, 0, 1, "Waiting", 0, null, null, null, stamp))
        db.pendingChangeDao().upsert(PendingChangeEntity("transaction", "t-$id", "u1", "wallet", id, 0, "{}", false, false, 0, 2, "Waiting", 0, null, null, null, stamp))
        db.syncScopeDao().storeFeedPage("wallet:$id", "position-$id", 12L)
    }

    private suspend fun assertPurged(id: String) {
        // The invitations' waiting changes are dropped from a launched job (spec 008 lesson): give it a moment.
        withTimeout(10_000) {
            while (db.pendingChangeDao().get("invitation", "inv-new-$id") != null || db.pendingChangeDao().get("transaction", "t-$id") != null) delay(20)
        }
        assertNull(db.walletDao().get(id))
        assertEquals(emptyList<WalletMemberEntity>(), db.walletMemberDao().get(id))
        assertEquals(emptyList<InvitationEntity>(), db.invitationDao().getForWallet(id))
        assertEquals(emptyList<CategoryReferenceEntity>(), db.categoryReferenceDao().get(id))
        assertEquals(emptyList<TransactionEntity>(), db.transactionDao().getAll().filter { it.walletId == id })
        assertNull(db.recurringRuleDao().get("rule-$id"))
        assertEquals(emptyList<OccurrenceEntity>(), db.occurrenceDao().getForWallet(id))
        assertNull(db.pendingChangeDao().get("transaction", "t-$id"))
        assertFeedForgotten(id)
    }

    private suspend fun assertFeedForgotten(id: String) {
        assertNull(db.syncScopeDao().feedPlace("wallet:$id")?.position)
        assertNull(db.syncScopeDao().feedCursor("wallet:$id"))
    }

    private suspend fun assertUntouched(id: String) {
        assertNotNull(db.walletDao().get(id))
        assertEquals(2, db.walletMemberDao().get(id).size)
        assertEquals(1, db.invitationDao().getForWallet(id).size)
        assertNotNull(db.recurringRuleDao().get("rule-$id"))
        assertNotNull(db.syncScopeDao().feedPlace("wallet:$id")?.position)
        assertNotNull(db.pendingChangeDao().get("invitation", "inv-new-$id"))
    }

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun theOwnMembershipEndingOnTheUserFeedPurgesTheWallet() = run {
        seedWallet("w1"); seedWallet("w2")
        repository.endOwnMembership(WalletMemberDto("m-w1-1", "w1", "u1", "Sara", "editor", 2, stamp, stamp))
        assertPurged("w1")
        assertUntouched("w2")
    }

    @Test
    fun aLeaveThatTheServerAnsweredWith200PurgesTheWallet() = run {
        seedWallet("w1"); seedWallet("w2")
        GlobalContext.get().get<WalletMemberships>().leftWallet("w1")
        assertPurged("w1")
        assertUntouched("w2")
    }

    @Test
    fun aLeaveThatTheServerAnsweredWith404PurgesTheWalletToo() = run {
        seedWallet("w1"); seedWallet("w2")
        // The same door as the 200: the person's membership is already over, so the wallet goes.
        GlobalContext.get().get<WalletMemberships>().leftWallet("w1")
        GlobalContext.get().get<WalletMemberships>().leftWallet("w1")
        assertPurged("w1")
        assertUntouched("w2")
    }

    @Test
    fun theWalletFeedAnswering404PurgesTheWallet() = run {
        seedWallet("w1"); seedWallet("w2")
        repository.accessLost("w1")
        assertPurged("w1")
        assertUntouched("w2")
    }

    @Test
    fun aWalletWithAWaitingChangeToItsOwnRecordStaysUntilThatSettles() = run {
        seedWallet("w1")
        db.pendingChangeDao().upsert(PendingChangeEntity("wallet", "w1", "u1", "user", "u1", 3, "{}", false, false, 0, 9, "Waiting", 0, null, null, null, stamp))
        repository.accessLost("w1")
        assertNotNull(db.walletDao().get("w1"))
        assertFeedForgotten("w1")
        assertTrue(db.invitationDao().getForWallet("w1").isNotEmpty())
    }
}
