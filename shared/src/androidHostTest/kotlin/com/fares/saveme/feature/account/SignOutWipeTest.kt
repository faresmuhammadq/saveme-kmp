package com.fares.saveme.feature.account

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.DatabaseCleaner
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Nothing of one person survives sign-out into the next person's session (P-09; quickstart 9). */
@RunWith(RobolectricTestRunner::class)
class SignOutWipeTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    @Test
    fun signOutEmptiesTheDatabaseAndTheLanguage() = runTest {
        var signedIn = false
        val runner = com.fares.saveme.core.sync.FakeSyncRunner()
        val env = AuthTestEnv {
            if (!signedIn) { signedIn = true; jsonResponse(200, signInSuccessBody()) } else respond("", HttpStatusCode.NoContent)
        }
        val languages = AppLanguageStore(env.keyValues, com.fares.saveme.core.network.FakeClientInfo(language = "en"))
        val heldInvitation = com.fares.saveme.feature.sharing.domain.HeldInvitation()
        val shareLinks = com.fares.saveme.feature.sharing.domain.ShareLinks()
        val planNotes = com.fares.saveme.feature.sharing.domain.PlanDemotionNotes()
        val reportChoices = com.fares.saveme.feature.reports.domain.ReportChoicesStore()
        val learnedPeriods = com.fares.saveme.feature.reports.domain.LearnedPeriods()
        val sessions = SessionManager(env.repository, env.entitlementsApi, env.events, backgroundScope, cleaners = listOf(heldInvitation, shareLinks, planNotes, com.fares.saveme.feature.reports.domain.ReportsCleaner(reportChoices, learnedPeriods), com.fares.saveme.core.sync.SyncSignOutCleaner { runner }, DatabaseCleaner(db), languages, com.fares.saveme.core.session.UserDataCleaner { env.keyValues.remove("transactions.lastWallet") }))
        SignInUseCase(env.clientInfo, DeviceIdentityStore(env.keyValues), env.repository, sessions)(
            "sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        )
        db.profileDao().upsert(
            ProfileEntity("u1", "p1", 3, "Sara", "ar", "Africa/Cairo", "EGP", false, 1, null, "2026-09-28T10:00:00Z"),
        )
        // A waiting edit (spec 004): it goes with the account, never sent under another (FR-018).
        db.pendingChangeDao().upsert(
            com.fares.saveme.core.sync.PendingChangeEntity(
                "profile", "p1", "u1", "user", "u1", 3, "{}", false, false, 0, 1, "Waiting", 0, null, null, null,
                "2026-09-28T10:00:00Z",
            ),
        )
        // Wallets and their members are wiped too (spec 006 FR-026).
        db.walletDao().upsert(
            com.fares.saveme.feature.wallets.data.local.WalletEntity(
                "w1", "u1", "cash", "Pocket", "EGP", 100, 100, null, null, null, false, "owner", 1, 1,
                "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z",
            ),
        )
        db.walletMemberDao().insertAll(
            listOf(com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", "Sara", "owner", 1, "2026-09-28T10:00:00Z")),
        )
        // Categories, the cached default set and partners' references are wiped too (spec 007 FR-023).
        val stamp = "2026-09-28T10:00:00Z"
        db.categoryDao().upsert(
            com.fares.saveme.feature.categories.data.local.CategoryEntity("c1", "u1", "expense", "Coffee", null, null, null, false, 1, 1, stamp, stamp, stamp),
        )
        db.platformCategoryDao().replaceAll(
            listOf(com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity("d1", "groceries", "expense", "بقالة", "Groceries", "cart", "#2E7D32", 1, null, stamp)),
        )
        db.categoryReferenceDao().replace(
            "w1",
            listOf(com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity("r1", "w1", "p1", "u2", "expense", "Partner", null, null, 1, 1, stamp)),
        )
        // Transactions are wiped too, and so is the last wallet recorded in (spec 008 FR-032).
        db.transactionDao().upsert(
            com.fares.saveme.feature.transactions.data.local.TransactionEntity(
                "t1", "u1", "w1", null, "expense", "2026-09-28", 4_550, "EGP", "c1", null, null, null, false, "u1", null, null, 1, 1,
                stamp, stamp, null, stamp,
            ),
        )
        env.keyValues.set("transactions.lastWallet", "w1")
        // Recurring rules and occurrences are wiped too (spec 010 FR-033).
        db.recurringRuleDao().upsert(
            com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity(
                "r1", "u1", "w1", "custom", "active", "expense", 450_000, "per_occurrence", "EGP", "c1", null, "Rent", null, null, false,
                "monthly", 1, 1, "2026-11-01", null, null, null, null, "Africa/Cairo", "u1", 1, 1, stamp, stamp, stamp,
            ),
        )
        db.occurrenceDao().upsert(
            com.fares.saveme.feature.recurring.data.local.OccurrenceEntity(
                "o1", "u1", "w1", "r1", 1, false, "2026-11-01", "2026-11-01", "scheduled", "expense", 450_000, "EGP", "c1", null, "Rent",
                null, null, false, null, null, 1, 1, stamp, stamp, stamp,
            ),
        )
        // Budgets, their stored progress and a waiting budget change are wiped too (spec 012 FR-022).
        db.budgetDao().upsert(
            com.fares.saveme.feature.budgets.data.local.BudgetEntity("b1", "u1", "c1", "monthly", 600_000, "EGP", null, 1, 1, stamp, stamp, stamp),
        )
        db.budgetProgressDao().upsertAll(
            listOf(com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity("b1", "u1", "2026-10-01", "2026-10-31", 31, 600_000, 45_000, "EGP", "", 45_000, "", stamp)),
        )
        db.pendingChangeDao().upsert(
            com.fares.saveme.core.sync.PendingChangeEntity(
                "budget", "b1", "u1", "user", "u1", 1, "{}", false, false, 0, 2, "Waiting", 0, null, null, null, stamp,
            ),
        )
        // Invitations, their waiting changes and the memory-only secrets are wiped too (spec 013 FR-031).
        db.invitationDao().upsert(
            com.fares.saveme.feature.sharing.data.local.InvitationEntity("i1", "u1", "w1", "editor", "email", "karim@example.com", "u1", "pending", "2026-10-09T10:00:00Z", 1, 1, stamp, stamp, stamp),
        )
        db.pendingChangeDao().upsert(
            com.fares.saveme.core.sync.PendingChangeEntity(
                "invitation", "i2", "u1", "wallet", "w1", 0, "{}", false, false, 0, 3, "Waiting", 0, null, null, null, stamp,
            ),
        )
        heldInvitation.hold(com.fares.saveme.feature.sharing.domain.InvitationLink.Valid("t".repeat(43)))
        shareLinks.offer("i1", "https://app.example/invite?token=" + "t".repeat(43))
        planNotes.record("w1", true)
        // The session's report choices and the Budget Period dates the server reported are memory only and go too (spec 014 FR-029).
        reportChoices.update("u1", com.fares.saveme.feature.reports.domain.model.ReportScope.AllWallets) { it.copy(trendLength = 12, currency = "USD") }
        learnedPeriods.store(
            "u1", com.fares.saveme.feature.reports.domain.PeriodProfile(25, null),
            listOf(com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan(kotlinx.datetime.LocalDate(2026, 8, 25), kotlinx.datetime.LocalDate(2026, 9, 24))),
        )
        db.deviceSessionDao().replaceAll(listOf(DeviceSessionEntity("a", "u1", "Pixel", "android", "1.0.0", "active", "2026-09-28T09:00:00Z", null, true)))
        db.syncScopeDao().upsert(SyncScopeEntity(SyncScopeEntity.PROFILE, "2026-09-28T10:00:00Z"))
        // Feed positions and read stamps are the account's too (spec 009 FR-028).
        db.syncScopeDao().storeFeedPage("user:u1", "position-1", 11L)
        db.syncScopeDao().markFeedRead("user:u1", "2026-09-28T10:00:00.000Z")
        db.syncScopeDao().storeFeedPage("wallet:w1", "position-2", 12L)
        languages.set(AppLanguage.Ar)

        SignOutUseCase(env.repository, sessions, serverTimeout = null)()

        assertNull(db.profileDao().get())
        assertNull(db.pendingChangeDao().get("profile", "p1"))
        assertNull(db.walletDao().get("w1"))
        assertEquals(emptyList<com.fares.saveme.feature.wallets.data.local.WalletMemberEntity>(), db.walletMemberDao().get("w1"))
        assertNull(db.categoryDao().get("c1"))
        assertEquals(emptyList<com.fares.saveme.feature.transactions.data.local.TransactionEntity>(), db.transactionDao().getAll())
        assertNull(env.keyValues.values["transactions.lastWallet"])
        assertEquals(emptyList<com.fares.saveme.feature.budgets.data.local.BudgetEntity>(), db.budgetDao().getAll())
        assertEquals(emptyList<com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity>(), db.budgetProgressDao().getAll())
        assertNull(db.pendingChangeDao().get("budget", "b1"))
        assertEquals(emptyList<com.fares.saveme.feature.sharing.data.local.InvitationEntity>(), db.invitationDao().getAll())
        assertNull(db.pendingChangeDao().get("invitation", "i2"))
        assertNull(heldInvitation.held.value)
        assertNull(shareLinks.get("i1"))
        assertEquals(com.fares.saveme.feature.reports.domain.model.ReportChoices(), reportChoices.get("u1", com.fares.saveme.feature.reports.domain.model.ReportScope.AllWallets))
        assertEquals(emptyList<com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan>(), learnedPeriods.get("u1", com.fares.saveme.feature.reports.domain.PeriodProfile(25, null)))
        assertEquals(false, planNotes.observe("w1").first())
        assertNull(db.recurringRuleDao().get("r1"))
        assertEquals(emptyList<com.fares.saveme.feature.recurring.data.local.OccurrenceEntity>(), db.occurrenceDao().getForWallet("w1"))
        assertEquals(emptyList<com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity>(), db.platformCategoryDao().getAll())
        assertEquals(emptyList<com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity>(), db.categoryReferenceDao().get("w1"))
        assertEquals(emptyList<DeviceSessionEntity>(), db.deviceSessionDao().observeAll().first())
        assertNull(db.syncScopeDao().observe(SyncScopeEntity.PROFILE).first())
        assertNull(db.syncScopeDao().feedCursor("user:u1"))
        assertNull(db.syncScopeDao().feedCursor("wallet:w1"))
        assertNull("no integer position survives sign-out either (spec 011 FR-021)", db.syncScopeDao().feedPlace("user:u1"))
        assertNull(db.syncScopeDao().feedPlace("wallet:w1"))
        assertNull(db.syncScopeDao().observeFeedReadAt("user:u1").first())
        assertNull(db.syncScopeDao().observe("user:u1").first())
        assertEquals("the sync run in progress is stopped, so it sends and applies nothing afterwards", 1, runner.cancelled)
        assertNull(env.keyValues.values["app_language"])
        assertEquals(AppLanguage.En, languages.effective())
        assertNull(env.secureStore.read())
    }
}
