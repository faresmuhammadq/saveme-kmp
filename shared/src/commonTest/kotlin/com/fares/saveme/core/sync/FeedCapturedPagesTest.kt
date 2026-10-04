package com.fares.saveme.core.sync

import com.fares.saveme.core.sync.CapturedSyncResponses.B_USER_FIRST
import com.fares.saveme.core.sync.CapturedSyncResponses.B_USER_LEFT
import com.fares.saveme.core.sync.CapturedSyncResponses.B_USER_ROLE
import com.fares.saveme.core.sync.CapturedSyncResponses.B_WALLET_1_FIRST
import com.fares.saveme.core.sync.CapturedSyncResponses.B_WALLET_1_LEFT
import com.fares.saveme.core.sync.CapturedSyncResponses.B_WALLET_1_ROLE
import com.fares.saveme.core.sync.CapturedSyncResponses.CATEGORY_1
import com.fares.saveme.core.sync.CapturedSyncResponses.EXPENSE_1
import com.fares.saveme.core.sync.CapturedSyncResponses.EXPENSE_2
import com.fares.saveme.core.sync.CapturedSyncResponses.PERSON_A
import com.fares.saveme.core.sync.CapturedSyncResponses.PERSON_B
import com.fares.saveme.core.sync.CapturedSyncResponses.PROFILE_A
import com.fares.saveme.core.sync.CapturedSyncResponses.TRANSFER
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_AFTER_BATCH
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_BUDGET
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_FIRST
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_PAGE_1
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_PAGE_2
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_PAGE_3
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_PAGE_EMPTY
import com.fares.saveme.core.sync.CapturedSyncResponses.USER_WALLET_2_DELETED
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_EMPTY
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_EXPIRED
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_FIRST
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_MEMBERS_GONE
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_REREAD
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_SHARED
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_SMALL_PAGE_1
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_1_SMALL_PAGE_2_EXPIRED
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_2
import com.fares.saveme.core.sync.CapturedSyncResponses.WALLET_2_FIRST
import com.fares.saveme.core.sync.CapturedSyncServer.Companion.nextCursorOf
import com.fares.saveme.core.sync.CapturedSyncServer.Companion.syncCursorOf
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.DeviceSessionFeedApplier
import com.fares.saveme.feature.account.data.ProfileFeedApplier
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.data.CategoryFeedApplier
import com.fares.saveme.feature.categories.data.CategoryReferenceFeedApplier
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.TransactionFeedApplier
import com.fares.saveme.feature.wallets.data.OwnMembershipFeedApplier
import com.fares.saveme.feature.wallets.data.WalletFeedApplier
import com.fares.saveme.feature.wallets.data.WalletMemberFeedApplier
import com.fares.saveme.feature.wallets.domain.DefaultWalletScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live backend's feed pages (spec 009 contract QA, [CapturedSyncResponses]) through the real wire client, the
 * real puller, every feature's applier and the runner, on fakes for Room (T038, T069): what the server really sends
 * leaves the tables the screens read in the state the spec asks for.
 */
class FeedCapturedPagesTest {

    private class World(scope: CoroutineScope, person: String) {
        val server = CapturedSyncServer()
        val tx = TransactionTestEnv(scope)
        val categories = CategoryTestEnv(scope) { jsonResponse(200, "{}") }
        val account = AccountTestEnv { jsonResponse(200, "{}") }
        val walletScopes = DefaultWalletScopes(tx.walletRepository) { person }
        val puller = FeedPuller(
            server.api, tx.scopeDao,
            {
                listOf(
                    OwnMembershipFeedApplier(tx.walletRepository), WalletFeedApplier(tx.walletRepository), WalletMemberFeedApplier(tx.walletRepository),
                    TransactionFeedApplier(tx.repository), CategoryFeedApplier(categories.repository), CategoryReferenceFeedApplier(categories.references),
                    ProfileFeedApplier(account.profiles, account.languages) { false }, DeviceSessionFeedApplier(account.sessions),
                )
            },
            { walletScopes }, SyncLock(), { person }, tx.clock,
        )
        val user = SyncScope(ScopeKind.User, person)
        val wallet1 = SyncScope(ScopeKind.Wallet, WALLET_1)
        val wallet2 = SyncScope(ScopeKind.Wallet, WALLET_2)

        fun position(scope: SyncScope) = tx.scopeDao.rows.value[scope.key]?.feedCursor
        fun wallet(id: String) = tx.walletDao.rows.value[id]
        fun members(walletId: String) = tx.memberDao.rows.value.filter { it.walletId == walletId }
        fun snapshot() = listOf(
            tx.walletDao.rows.value, tx.memberDao.rows.value.toSet(), tx.transactionDao.rows.value, categories.categoryDao.rows.value,
            categories.referenceDao.rows.value.toSet(), account.profileDao.row.value, account.sessionDao.rows.value.toSet(),
        )

        /** Person A's reads after the first uploads: the person's scope twice, then both wallets with the transfer. */
        suspend fun readAsAAfterTheFirstUploads() {
            server.signedIn()
            server.user = { cursor -> if (cursor == null) USER_FIRST else USER_AFTER_BATCH }
            server.wallet = { id, _ -> if (id == WALLET_1) WALLET_1_FIRST else WALLET_2_FIRST }
            assertIs<ScopeRead.Done>(puller.read(user))
            assertIs<ScopeRead.Done>(puller.read(user))
            assertIs<ScopeRead.Done>(puller.read(wallet1))
            assertIs<ScopeRead.Done>(puller.read(wallet2))
        }
    }

    /** A scope on the test dispatcher whose work `runTest` waits for (not `backgroundScope`). */
    private fun TestScope.foreground() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    @Test
    fun theFirstReadsStoreTheProfileTheSessionTheOwnMembershipsAndTheCategory() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.server.signedIn()
        w.server.user = { cursor -> if (cursor == null) USER_FIRST else USER_AFTER_BATCH }

        assertIs<ScopeRead.Done>(w.puller.read(w.user))
        assertEquals(1L, w.account.profileDao.row.value?.version)
        assertEquals(1, w.account.sessionDao.rows.value.size)
        assertEquals(syncCursorOf(USER_FIRST), w.position(w.user))
        assertEquals(emptyList(), w.walletScopes.followed())

        // The next read starts from the stored position: the server answers what changed since (the uploads' results).
        assertIs<ScopeRead.Done>(w.puller.read(w.user))
        assertEquals(listOf("user", "user@" + syncCursorOf(USER_FIRST).takeLast(6)), w.server.feedReads)
        assertEquals(2L, w.account.profileDao.row.value?.version)
        assertEquals("QA Nine", w.account.profileDao.row.value?.displayName)
        assertEquals(setOf(WALLET_1, WALLET_2), w.walletScopes.followed().toSet(), "both wallets are followed from their own memberships")
        assertEquals(setOf("owner"), w.tx.memberDao.rows.value.map { it.role }.toSet())
        assertEquals("QA Pharmacy", w.categories.categoryDao.rows.value.getValue(CATEGORY_1).name)
        assertEquals(syncCursorOf(USER_AFTER_BATCH), w.position(w.user))
    }

    @Test
    fun aTransferFromBothWalletFeedsIsStoredOnceAndTheBalancesAreTheServers() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.readAsAAfterTheFirstUploads()

        val rows = w.tx.transactionDao.rows.value
        assertEquals(setOf(EXPENSE_1, EXPENSE_2, TRANSFER), rows.keys)
        assertEquals(WALLET_2, rows.getValue(TRANSFER).counterWalletId)
        assertEquals(WALLET_1, rows.getValue(TRANSFER).walletId)
        assertEquals(81_800L, w.wallet(WALLET_1)?.balanceMinor)
        assertEquals(5_000L, w.wallet(WALLET_2)?.balanceMinor)
        assertEquals("owner", w.wallet(WALLET_1)?.myRole)
        assertEquals(0L, w.tx.carryFor(WALLET_1) + w.tx.carryFor(WALLET_2), "feed rows never add a carry")
        assertEquals(1, w.categories.referenceDao.rows.value.size)

        // Reading wallet 2 from the start again (the transfer arrives a second time) changes nothing.
        val before = w.snapshot()
        w.tx.scopeDao.clearFeed(w.wallet2.key)
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet2))
        assertEquals(before, w.snapshot())
    }

    @Test
    fun kindsTheAppDoesNotConsumeAreSkippedAndThePositionStillAdvances() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.readAsAAfterTheFirstUploads()
        val before = w.tx.transactionDao.rows.value
        w.server.user = { USER_BUDGET }
        w.server.wallet = { _, _ -> WALLET_1_SHARED }

        assertIs<ScopeRead.Done>(w.puller.read(w.user))
        assertEquals(syncCursorOf(USER_BUDGET), w.position(w.user), "a page of one budget row still moves the position")
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet1))
        assertEquals(syncCursorOf(WALLET_1_SHARED), w.position(w.wallet1))
        // recurring_rule, occurrence x2 and invitation x2 are skipped; B's new member row and the wallet record apply.
        assertEquals(setOf(PERSON_A, PERSON_B), w.members(WALLET_1).map { it.memberUserId }.toSet())
        assertEquals(before, w.tx.transactionDao.rows.value, "an occurrence is never taken for a transaction")
    }

    @Test
    fun aMemberWhoLeftOrWasRemovedTakesOnlyTheirOwnRowWithThem() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.readAsAAfterTheFirstUploads()
        w.server.wallet = { _, cursor -> if (cursor == syncCursorOf(WALLET_1_FIRST)) WALLET_1_SHARED else WALLET_1_MEMBERS_GONE }
        w.puller.read(w.wallet1)
        assertEquals(2, w.members(WALLET_1).size)

        assertIs<ScopeRead.Done>(w.puller.read(w.wallet1))
        assertEquals(listOf(PERSON_A), w.members(WALLET_1).map { it.memberUserId }, "B's tombstone removes B; C's, never held, stores nothing")
        assertNotNull(w.wallet(WALLET_1))
        assertEquals(3, w.tx.transactionDao.rows.value.size)
    }

    @Test
    fun aDeletedWalletLeavesThePhoneAndTheTransferThatTouchesAKeptWalletStays() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.readAsAAfterTheFirstUploads()
        w.server.user = { USER_WALLET_2_DELETED }

        assertIs<ScopeRead.Done>(w.puller.read(w.user))
        testScheduler.runCurrent()
        assertNull(w.wallet(WALLET_2))
        assertNull(w.position(w.wallet2), "the lost wallet's position is forgotten")
        assertEquals(listOf(WALLET_1), w.walletScopes.followed())
        assertNotNull(w.tx.transactionDao.rows.value[TRANSFER], "the transfer still touches wallet 1")
        assertNotNull(w.wallet(WALLET_1))
    }

    @Test
    fun aWalletGainedByInvitationIsReadFromTheStartInTheSameRunItsRoleFollowsAndLeavingRemovesIt() = runTest {
        val w = World(foreground(), PERSON_B)
        w.server.signedIn()
        w.server.user = { cursor ->
            when (cursor) {
                null -> B_USER_FIRST
                syncCursorOf(B_USER_FIRST) -> B_USER_ROLE
                else -> B_USER_LEFT
            }
        }
        w.server.wallet = { _, cursor -> if (cursor == null) B_WALLET_1_FIRST else B_WALLET_1_ROLE }
        val runner = DefaultSyncRunner(w.tx.engine, w.puller, { w.walletScopes }, { PERSON_B }, w.tx.scope, w.tx.clock)

        assertEquals(ReadOutcome.Done, runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(listOf("user", "wallet $WALLET_1"), w.server.feedReads, "the person's scope first, then the gained wallet from the start")
        assertEquals("editor", w.wallet(WALLET_1)?.myRole)
        assertEquals(3, w.tx.transactionDao.rows.value.size)
        assertEquals(setOf(PERSON_A, PERSON_B), w.members(WALLET_1).map { it.memberUserId }.toSet())
        assertEquals(1, w.categories.referenceDao.rows.value.size)

        // A made B a viewer: the own membership and the wallet record say so.
        assertEquals(ReadOutcome.Done, runner.refresh(ReadTarget.Everything, force = true))
        assertEquals("viewer", w.wallet(WALLET_1)?.myRole)
        assertEquals("viewer", w.members(WALLET_1).single { it.memberUserId == PERSON_B }.role)

        // B left: the wallet and everything of it leaves the phone within that refresh, and its feed is not read again.
        w.server.wallet = { _, _ -> B_WALLET_1_LEFT }
        assertEquals(ReadOutcome.Done, runner.refresh(ReadTarget.Everything, force = true))
        testScheduler.runCurrent()
        assertNull(w.wallet(WALLET_1))
        assertTrue(w.members(WALLET_1).isEmpty())
        assertTrue(w.tx.transactionDao.rows.value.isEmpty(), "none of its transactions touches a wallet B keeps")
        assertEquals(emptyList(), w.walletScopes.followed())
        assertEquals(2, w.server.feedReads.count { it.startsWith("wallet") })
    }

    @Test
    fun aWalletFeedAnsweringNotFoundRemovesTheWallet() = runTest {
        val w = World(backgroundScope, PERSON_B)
        w.server.signedIn()
        w.server.user = { B_USER_FIRST }
        w.server.wallet = { _, _ -> B_WALLET_1_FIRST }
        w.puller.read(w.user)
        w.puller.read(w.wallet1)
        assertNotNull(w.wallet(WALLET_1))

        w.server.wallet = { _, _ -> B_WALLET_1_LEFT }
        val read = assertIs<ScopeRead.Done>(w.puller.read(w.wallet1))
        testScheduler.runCurrent()
        assertTrue(read.lost)
        assertNull(w.wallet(WALLET_1))
        assertNull(w.position(w.wallet1))
    }

    @Test
    fun anExpiredWalletPositionIsReadAgainFromTheStartAndWhatThePurgeRemovedLeaves() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.readAsAAfterTheFirstUploads()
        w.server.wallet = { _, _ -> WALLET_1_SHARED }
        w.puller.read(w.wallet1)
        assertEquals(2, w.members(WALLET_1).size)
        val userPosition = w.position(w.user)
        val wallet2Position = w.position(w.wallet2)

        // B's membership tombstone was aged and purged on the server: every older position of wallet 1 expired.
        w.server.wallet = { _, cursor -> if (cursor == null) WALLET_1_REREAD else WALLET_1_EXPIRED }
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet1))

        assertEquals(syncCursorOf(WALLET_1_REREAD), w.position(w.wallet1))
        assertEquals(listOf(PERSON_A), w.members(WALLET_1).map { it.memberUserId }, "B's row, gone from the server, is swept")
        assertEquals(3, w.tx.transactionDao.rows.value.size)
        assertEquals(81_800L, w.wallet(WALLET_1)?.balanceMinor)
        assertEquals(userPosition, w.position(w.user), "the person's scope is untouched")
        assertEquals(wallet2Position, w.position(w.wallet2), "the other wallet is untouched")
    }

    @Test
    fun anInvalidPositionIsForgottenAndTheScopeReadFromTheStart() = runTest {
        for (refusal in listOf(
            CapturedSyncResponses.INVALID_CURSOR_GARBAGE, CapturedSyncResponses.INVALID_CURSOR_OTHER_ACCOUNT,
            CapturedSyncResponses.INVALID_CURSOR_USER_ON_WALLET, CapturedSyncResponses.INVALID_CURSOR_OTHER_WALLET,
        )) {
            val w = World(backgroundScope, PERSON_A)
            w.server.signedIn()
            w.tx.scopeDao.storeFeedPage(w.user.key, "a-position-the-server-refuses", null)
            w.server.user = { cursor -> if (cursor == null) USER_FIRST else refusal }
            assertIs<ScopeRead.Done>(w.puller.read(w.user))
            assertEquals(syncCursorOf(USER_FIRST), w.position(w.user))
            assertEquals(PROFILE_A, w.account.profileDao.row.value?.id)
        }
    }

    @Test
    fun aReadFromTheStartFollowsEveryPageAndAnEmptyPageKeepsThePosition() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.server.signedIn()
        w.server.user = { cursor ->
            when (cursor) {
                null -> USER_PAGE_1
                nextCursorOf(USER_PAGE_1) -> USER_PAGE_2
                nextCursorOf(USER_PAGE_2) -> USER_PAGE_3
                else -> USER_PAGE_EMPTY
            }
        }
        assertIs<ScopeRead.Done>(w.puller.read(w.user))
        assertEquals(3, w.server.feedReads.size)
        assertEquals(syncCursorOf(USER_PAGE_3), w.position(w.user))
        assertEquals(setOf(WALLET_1, WALLET_2), w.walletScopes.followed().toSet())
        assertEquals(2L, w.account.profileDao.row.value?.version)

        assertIs<ScopeRead.Done>(w.puller.read(w.user))
        assertEquals(syncCursorOf(USER_PAGE_EMPTY), w.position(w.user))
        assertEquals(syncCursorOf(USER_PAGE_3), syncCursorOf(USER_PAGE_EMPTY), "an empty page answers the same position")

        w.server.wallet = { _, _ -> WALLET_1_EMPTY }
        w.tx.scopeDao.storeFeedPage(w.wallet1.key, "stored", null)
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet1))
        assertEquals(syncCursorOf(WALLET_1_EMPTY), w.position(w.wallet1))
    }

    @Test
    fun aReadFromTheStartWhoseNextPageTheServerExpiresFailsOnceAndKeepsTheCopy() = runTest {
        val w = World(backgroundScope, PERSON_A)
        w.readAsAAfterTheFirstUploads()
        val before = w.tx.transactionDao.rows.value
        // The live answer (backend bug, qa-report.md §5): page 1 of a read from the start, then 410 for its own next page.
        w.server.wallet = { _, cursor ->
            when (cursor) {
                null -> WALLET_1_SMALL_PAGE_1
                nextCursorOf(WALLET_1_SMALL_PAGE_1) -> WALLET_1_SMALL_PAGE_2_EXPIRED
                else -> WALLET_1_EXPIRED
            }
        }
        w.server.feedReads.clear()

        assertEquals(ScopeRead.Failed, w.puller.read(w.wallet1))
        assertEquals(3, w.server.feedReads.size, "the stored position, one read from the start, its expired page 2; no second full read")
        assertNull(w.position(w.wallet1))
        assertEquals(before, w.tx.transactionDao.rows.value, "nothing is swept by an incomplete read")
        assertNotNull(w.wallet(WALLET_1))
    }

    @Test
    fun feedRefusalsThatAreClientDefectsFailTheScopeAndKeepItsPosition() = runTest {
        for (refusal in listOf(CapturedSyncResponses.WALLET_ID_MALFORMED, CapturedSyncResponses.LIMIT_ZERO, CapturedSyncResponses.FILTER_SENT)) {
            val w = World(backgroundScope, PERSON_A)
            w.server.signedIn()
            w.tx.scopeDao.storeFeedPage(w.wallet1.key, "stored", null)
            w.server.wallet = { _, _ -> refusal }
            assertEquals(ScopeRead.Failed, w.puller.read(w.wallet1))
            assertEquals("stored", w.position(w.wallet1))
        }
    }
}
