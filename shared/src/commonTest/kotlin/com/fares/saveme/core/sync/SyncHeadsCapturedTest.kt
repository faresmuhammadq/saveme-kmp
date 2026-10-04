package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.CapturedHeadsResponses.CATEGORY_A
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_AFTER_PARTNER_WRITE
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_AFTER_RENAME
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_ARCHIVED_AND_NEW
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_B_AFTER_LEAVE
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_B_BEFORE_LEAVE
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_CURSOR_REFUSED
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_DELETED_ABSENT
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_EMPTY
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_FILTER_REFUSED
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_LIMIT_1_PAGE_1
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_LIMIT_1_PAGE_2
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_LIMIT_1_PAGE_3
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_LIMIT_REFUSED
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_NOT_ACCEPTABLE
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_PURGE_AFTER
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_SORT_REFUSED
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_THREE_WALLETS
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_UNAUTHENTICATED
import com.fares.saveme.core.sync.CapturedHeadsResponses.HEADS_VERSION_REFUSED
import com.fares.saveme.core.sync.CapturedHeadsResponses.PERSON_A
import com.fares.saveme.core.sync.CapturedHeadsResponses.PERSON_B
import com.fares.saveme.core.sync.CapturedHeadsResponses.USER_FIRST_A
import com.fares.saveme.core.sync.CapturedHeadsResponses.USER_QUIET_A
import com.fares.saveme.core.sync.CapturedHeadsResponses.W1
import com.fares.saveme.core.sync.CapturedHeadsResponses.W2
import com.fares.saveme.core.sync.CapturedHeadsResponses.W3
import com.fares.saveme.core.sync.CapturedHeadsResponses.W5
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_1_AFTER_PARTNER_WRITE
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_1_AFTER_RENAME
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_1_AFTER_SHARE
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_1_FIRST
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_1_LIMIT_3_PAGE_1
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_1_LIMIT_3_PAGE_2
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_1_QUIET
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_2_FIRST
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_3_AFTER_SHARE
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_3_B_LEFT
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_3_FIRST
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_FIRST
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_FROM_START
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_FROM_START_LIMIT_1
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_FROM_START_LIMIT_2
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_FROM_START_LIMIT_5
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_MID_READ_PAGE_1
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_MID_READ_PAGE_2_EXPIRED
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_OLD_CURSOR
import com.fares.saveme.core.sync.CapturedHeadsResponses.WALLET_5_RESTART_AFTER_MID_READ_PURGE
import com.fares.saveme.core.sync.CapturedSyncResponses.Answer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Spec 011 against the live backend's answers (`saveme-backend@64ca1ea`, sync.yaml v1.10, contract QA T030,
 * [CapturedHeadsResponses]) through the real wire client ([HttpSyncApi]), the real puller, the real runner and every
 * feature's applier on fakes for Room: heads, `meta.sync_position`, quiet and moved refreshes, a partner's rename, and
 * reads across a purge horizon (T031, T032).
 */
class SyncHeadsCapturedTest {

    private fun TestScope.foreground() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    private class World(scope: CoroutineScope, val person: String = PERSON_A) {
        val server = CapturedSyncServer()
        val tx = TransactionTestEnv(scope)
        val categories = CategoryTestEnv(scope) { jsonResponse(200, "{}") }
        val account = AccountTestEnv { jsonResponse(200, "{}") }
        val walletScopes = FakeWalletScopes()
        val lock = SyncLock()
        val engine = SyncEngine(FakePendingChangeDao(), tx.scopeDao, emptyList(), FakeSyncScheduler(), currentAccountId = { person }, clock = tx.clock, syncApi = server.api, lock = lock)
        val puller = FeedPuller(
            server.api, tx.scopeDao,
            {
                listOf(
                    OwnMembershipFeedApplier(tx.walletRepository), WalletFeedApplier(tx.walletRepository), WalletMemberFeedApplier(tx.walletRepository),
                    TransactionFeedApplier(tx.repository), CategoryFeedApplier(categories.repository), CategoryReferenceFeedApplier(categories.references),
                    ProfileFeedApplier(account.profiles, account.languages) { false }, DeviceSessionFeedApplier(account.sessions),
                )
            },
            { walletScopes }, lock, { person }, tx.clock,
        )
        val runner = DefaultSyncRunner(engine, puller, { walletScopes }, { person }, scope, tx.clock)
        val user = SyncScope(ScopeKind.User, person)

        fun wallet(id: String) = SyncScope(ScopeKind.Wallet, id)

        /** The place the phone holds after it read [page] (its cursor and its stated position). */
        suspend fun placeAfter(scope: SyncScope, page: Answer) = tx.scopeDao.storeFeedPage(scope.key, syncCursorOf(page), positionOf(page))

        fun place(scope: SyncScope) = tx.scopeDao.rows.value[scope.key]?.let { FeedPlaceOf(it.feedCursor, it.feedPosition) }
    }

    private data class FeedPlaceOf(val cursor: String?, val position: Long?)

    private companion object {
        fun positionOf(page: Answer): Long = AppJson.parseToJsonElement(page.body).jsonObject.getValue("meta").jsonObject.getValue("sync_position").jsonPrimitive.content.toLong()

        fun headsOf(page: Answer): Map<String, Pair<Long, Long>> =
            AppJson.parseToJsonElement(page.body).jsonObject.getValue("data").let { it as kotlinx.serialization.json.JsonArray }.associate {
                val o = it.jsonObject
                o.getValue("wallet_id").jsonPrimitive.content to (o.getValue("change_seq").jsonPrimitive.content.toLong() to o.getValue("purged_change_seq").jsonPrimitive.content.toLong())
            }
    }

    // ---- The heads endpoint (T006, FR-009, FR-010) ----

    @Test
    fun theRealHeadsAnswerDecodesAndIsOrderedByWalletId() = runTest {
        val w = World(backgroundScope)
        w.server.signedIn()
        w.server.heads = { HEADS_THREE_WALLETS }
        val page = assertIs<ApiResult.Success<HeadsPageDto>>(w.server.api.walletHeads(null)).value
        assertEquals(3, page.data.size)
        assertEquals(listOf(W3, W2, W1), page.data.map { it.walletId }, "ordered by wallet id")
        assertEquals(listOf(3L, 7L, 9L), page.data.map { it.changeSeq })
        assertTrue(page.data.all { it.purgedChangeSeq == 0L })
        assertEquals(false, page.meta.hasMore)
    }

    @Test
    fun aHeadsReadFollowsEveryPageOfTheRealLimitOneAnswers() = runTest {
        val w = World(backgroundScope)
        w.server.signedIn()
        w.server.heads = { cursor ->
            when (cursor) {
                null -> HEADS_LIMIT_1_PAGE_1
                nextCursorOf(HEADS_LIMIT_1_PAGE_1) -> HEADS_LIMIT_1_PAGE_2
                nextCursorOf(HEADS_LIMIT_1_PAGE_2) -> HEADS_LIMIT_1_PAGE_3
                else -> error("no more pages")
            }
        }
        val listed = assertIs<HeadsRead.Listed>(w.puller.heads(ReadRun(PERSON_A)))
        assertEquals(setOf(W1, W2, W3), listed.heads.keys)
        assertEquals(listOf("sync/wallets/heads", "sync/wallets/heads?cursor", "sync/wallets/heads?cursor"), w.server.requests)
        assertEquals(9L, listed.heads.getValue(W1).changeSeq)
    }

    @Test
    fun aPersonInNoWalletGetsAnEmptyListAndEveryRealRefusalIsItsCode() = runTest {
        val w = World(backgroundScope)
        w.server.signedIn()
        w.server.heads = { HEADS_EMPTY }
        assertEquals(emptyList(), assertIs<ApiResult.Success<HeadsPageDto>>(w.server.api.walletHeads(null)).value.data)

        val refusals = listOf(
            HEADS_SORT_REFUSED to "SORT_FIELD_NOT_SUPPORTED", HEADS_FILTER_REFUSED to "FILTER_FIELD_NOT_SUPPORTED",
            HEADS_CURSOR_REFUSED to "INVALID_CURSOR", HEADS_LIMIT_REFUSED to "INVALID_QUERY_PARAMETER",
            HEADS_UNAUTHENTICATED to "UNAUTHENTICATED", HEADS_NOT_ACCEPTABLE to "NOT_ACCEPTABLE", HEADS_VERSION_REFUSED to "CLIENT_VERSION_UNSUPPORTED",
        )
        for ((answer, code) in refusals) {
            w.server.heads = { answer }
            val failure = assertIs<ApiResult.Failure>(w.server.api.walletHeads(null), code)
            assertEquals(code, failure.problem.code)
            assertEquals(answer.status, failure.problem.status)
        }
        w.server.heads = { HEADS_CURSOR_REFUSED }
        assertEquals(HeadsRead.Failed, w.puller.heads(ReadRun(PERSON_A)), "a refused heads read reads every wallet")
    }

    // ---- sync_position on both feeds (FR-001) ----

    @Test
    fun everyRealFeedPageStoresItsPositionWithItsCursorAndTheLimitThreePagesResumeWhereTheyEnd() = runTest {
        val w = World(backgroundScope)
        w.server.signedIn()
        w.server.user = { USER_FIRST_A }
        w.server.wallet = { _, _ -> WALLET_1_FIRST }
        assertIs<ScopeRead.Done>(w.puller.read(w.user))
        assertEquals(FeedPlaceOf(syncCursorOf(USER_FIRST_A), 6), w.place(w.user))
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet(W1)))
        assertEquals(FeedPlaceOf(syncCursorOf(WALLET_1_FIRST), 9), w.place(w.wallet(W1)), "equals the wallet's head in HEADS_THREE_WALLETS")

        // The same wallet at limit=3: the intermediate page states the position its next_cursor resumes from (5), the last page 9.
        val w2 = World(backgroundScope)
        w2.server.signedIn()
        w2.server.wallet = { _, cursor -> if (cursor == null) WALLET_1_LIMIT_3_PAGE_1 else WALLET_1_LIMIT_3_PAGE_2 }
        assertIs<ScopeRead.Done>(w2.puller.read(w2.wallet(W1)))
        assertEquals(FeedPlaceOf(syncCursorOf(WALLET_1_LIMIT_3_PAGE_2), 9), w2.place(w2.wallet(W1)))
        assertEquals(5L, positionOf(WALLET_1_LIMIT_3_PAGE_1))
    }

    // ---- A quiet refresh, a moved head (FR-004 - FR-006, SC-001, SC-002) ----

    private suspend fun World.followThree() {
        server.signedIn()
        walletScopes.followed = listOf(W1, W2, W3)
        placeAfter(user, USER_FIRST_A)
        placeAfter(wallet(W1), WALLET_1_FIRST)
        placeAfter(wallet(W2), WALLET_2_FIRST)
        placeAfter(wallet(W3), WALLET_3_FIRST)
    }

    @Test
    fun aQuietRefreshOfThreeWalletsOnTheRealAnswersIsTwoRequestsAndKeepsEveryPosition() = runTest {
        val w = World(foreground())
        w.followThree()
        val before = listOf(W1, W2, W3).associateWith { w.place(w.wallet(it)) }
        w.server.user = { USER_QUIET_A }
        w.server.heads = { HEADS_THREE_WALLETS }

        assertEquals(ReadOutcome.Done, w.runner.refresh(ReadTarget.Everything, force = false))

        assertEquals(listOf("sync/user/changes?cursor", "sync/wallets/heads"), w.server.requests)
        assertEquals(before, listOf(W1, W2, W3).associateWith { w.place(w.wallet(it)) })
        assertTrue(listOf(W1, W2, W3).all { w.tx.scopeDao.rows.value["wallet:$it"]?.feedReadAt != null }, "a skipped wallet counts as read")
        assertEquals(headsOf(HEADS_THREE_WALLETS).mapValues { it.value.first }, before.mapValues { it.value!!.position }, "head == stored position is exactly what the skip rests on")
    }

    @Test
    fun aQuietWalletReadAgainStatesTheSamePositionSoTheNextHeadsStillMatch() = runTest {
        val w = World(backgroundScope)
        w.server.signedIn()
        w.placeAfter(w.wallet(W1), WALLET_1_FIRST)
        w.server.wallet = { _, _ -> WALLET_1_QUIET }
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet(W1)))
        assertEquals(9L, w.place(w.wallet(W1))?.position)
        assertEquals(syncCursorOf(WALLET_1_FIRST), w.place(w.wallet(W1))?.cursor, "an empty page answers the same cursor")
    }

    @Test
    fun aPartnersWriteMovesOnlyThatWalletsHeadAndOnlyThatWalletIsRead() = runTest {
        val w = World(foreground())
        w.server.signedIn()
        w.walletScopes.followed = listOf(W1, W2, W3)
        w.placeAfter(w.user, USER_FIRST_A)
        w.placeAfter(w.wallet(W1), WALLET_1_AFTER_SHARE) // position 19
        w.placeAfter(w.wallet(W2), WALLET_2_FIRST) // 7
        w.placeAfter(w.wallet(W3), WALLET_3_AFTER_SHARE) // 8
        w.server.user = { USER_QUIET_A }
        w.server.heads = { HEADS_AFTER_PARTNER_WRITE }
        w.server.wallet = { id, cursor ->
            assertEquals(W1, id)
            assertEquals(syncCursorOf(WALLET_1_AFTER_SHARE), cursor)
            WALLET_1_AFTER_PARTNER_WRITE
        }

        assertEquals(ReadOutcome.Done, w.runner.refresh(ReadTarget.Everything, force = false))

        assertEquals(listOf("sync/user/changes?cursor", "sync/wallets/heads", "sync/wallets/$W1/changes?cursor"), w.server.requests)
        assertEquals(FeedPlaceOf(syncCursorOf(WALLET_1_AFTER_PARTNER_WRITE), 23), w.place(w.wallet(W1)))
        assertEquals(23L, headsOf(HEADS_AFTER_PARTNER_WRITE).getValue(W1).first, "the stored position now equals the head the phone saw")
        assertEquals(4_000L, w.tx.transactionDao.rows.value.values.single().amountMinor, "B's transaction arrived")
    }

    // ---- A partner's rename (US2, FR-012, FR-013) ----

    @Test
    fun aPartnersRenameArrivesAsTheSameVersionRowAndReplacesTheNameInTheSameRefresh() = runTest {
        val w = World(foreground())
        w.server.signedIn()
        w.walletScopes.followed = listOf(W1)
        w.placeAfter(w.user, USER_FIRST_A)
        w.server.user = { USER_QUIET_A }
        w.server.heads = { HEADS_AFTER_PARTNER_WRITE }

        // The phone has read the sharing: B and C are members of W1 under their first names.
        w.server.wallet = { _, _ -> WALLET_1_AFTER_SHARE }
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet(W1)))
        val before = w.tx.memberDao.rows.value.single { it.memberUserId == PERSON_B }
        assertEquals("QA 011 B", before.displayName)
        w.placeAfter(w.wallet(W1), WALLET_1_AFTER_PARTNER_WRITE) // position 23

        // The re-stamp moved the head to 25; the feed row has the same version and the new name.
        w.server.heads = { HEADS_AFTER_RENAME }
        w.server.wallet = { _, cursor ->
            assertEquals(syncCursorOf(WALLET_1_AFTER_PARTNER_WRITE), cursor)
            WALLET_1_AFTER_RENAME
        }
        assertEquals(ReadOutcome.Done, w.runner.refresh(ReadTarget.Everything, force = true))

        val after = w.tx.memberDao.rows.value.single { it.memberUserId == PERSON_B }
        assertEquals("QA 011 B Renamed", after.displayName)
        assertEquals(before.version, after.version, "same version: the name changed, the version did not")
        assertEquals(25L, w.place(w.wallet(W1))?.position)
    }

    // ---- Wallets that are archived, deleted or left (FR-008) ----

    @Test
    fun anArchivedWalletIsListedADeletedOrLeftOneIsNotAndNeitherChangesWhatThePhoneFollows() = runTest {
        val archived = headsOf(HEADS_ARCHIVED_AND_NEW)
        assertEquals(4, archived.size, "the archived wallet is still listed, with the new one")
        assertTrue(W2 in archived)
        assertEquals(3, headsOf(HEADS_DELETED_ABSENT).size)
        assertTrue(CapturedHeadsResponses.W4 !in headsOf(HEADS_DELETED_ABSENT), "a deleted wallet is absent")
        assertEquals(setOf(W1, W3), headsOf(HEADS_B_BEFORE_LEAVE).keys)
        assertEquals(setOf(W1), headsOf(HEADS_B_AFTER_LEAVE).keys, "a wallet the person left is absent")

        // B still follows W3 until its feed or its own membership says otherwise: the absence is no removal signal.
        val w = World(foreground(), PERSON_B)
        w.server.signedIn()
        w.walletScopes.followed = listOf(W1, W3)
        w.placeAfter(w.user, USER_FIRST_A)
        w.placeAfter(w.wallet(W1), WALLET_1_AFTER_SHARE)
        w.placeAfter(w.wallet(W3), WALLET_3_FIRST)
        w.server.user = { USER_QUIET_A }
        w.server.heads = { HEADS_B_AFTER_LEAVE }
        w.server.wallet = { id, _ -> if (id == W3) WALLET_3_B_LEFT else WALLET_1_AFTER_SHARE }

        w.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf(W3), w.walletScopes.lost, "only W3's own 404 removed it; listed W1 (head 73 > 19) is read and stays")
        assertEquals(listOf("sync/user/changes?cursor", "sync/wallets/heads", "sync/wallets/$W1/changes?cursor", "sync/wallets/$W3/changes?cursor"), w.server.requests)
    }

    // ---- Purge horizon (US4, FR-005 rule 3, FR-018, FR-019, SC-006) ----

    @Test
    fun aWalletBehindTheRealPurgeHorizonIsReadFromTheStartAndNeverFromTheExpiredCursor() = runTest {
        val w = World(foreground())
        w.server.signedIn()
        w.walletScopes.followed = listOf(W5)
        w.placeAfter(w.user, USER_FIRST_A)
        w.placeAfter(w.wallet(W5), WALLET_5_FIRST) // position 21; the horizon is 26
        w.server.user = { USER_QUIET_A }
        w.server.heads = { HEADS_PURGE_AFTER }
        w.server.wallet = { _, cursor ->
            assertNull(cursor, "the expired cursor is never sent")
            WALLET_5_FROM_START
        }

        assertEquals(ReadOutcome.Done, w.runner.refresh(ReadTarget.Everything, force = false))

        assertEquals(listOf("sync/user/changes?cursor", "sync/wallets/heads", "sync/wallets/$W5/changes"), w.server.requests)
        assertEquals(FeedPlaceOf(syncCursorOf(WALLET_5_FROM_START), 27), w.place(w.wallet(W5)))
        assertEquals(5, w.tx.transactionDao.rows.value.size, "the five live transactions; the three purged ones were never held or are swept")
    }

    private fun pagesByCursor(pages: List<Answer>): (String?) -> Answer {
        val byCursor = pages.dropLast(1).withIndex().associate { (index, page) -> nextCursorOf(page) to pages[index + 1] }
        return { cursor -> if (cursor == null) pages.first() else byCursor.getValue(cursor) }
    }

    @Test
    fun aReadFromNoCursorAcrossTheRealHorizonPagesToTheEndAtEveryPageSize() = runTest {
        for (pages in listOf(WALLET_5_FROM_START_LIMIT_1, WALLET_5_FROM_START_LIMIT_2, WALLET_5_FROM_START_LIMIT_5)) {
            val w = World(backgroundScope)
            w.server.signedIn()
            val answer = pagesByCursor(pages)
            w.server.wallet = { _, cursor -> answer(cursor) }

            val read = assertIs<ScopeRead.Done>(w.puller.read(w.wallet(W5)))

            assertEquals(pages.size, w.server.feedReads.size, "every page, no 410")
            assertEquals(FeedPlaceOf(syncCursorOf(pages.last()), 27), w.place(w.wallet(W5)))
            assertNotNull(read.startedAt)
            assertEquals(5, w.tx.transactionDao.rows.value.size)
        }
    }

    @Test
    fun aStoredCursorOlderThanTheHorizonGetsA410AndTheScopeIsReadFromTheStartOnce() = runTest {
        val w = World(backgroundScope)
        w.server.signedIn()
        w.placeAfter(w.wallet(W5), WALLET_5_FIRST)
        val fromStart = pagesByCursor(WALLET_5_FROM_START_LIMIT_2)
        w.server.wallet = { _, cursor -> if (cursor == syncCursorOf(WALLET_5_FIRST)) WALLET_5_OLD_CURSOR else fromStart(cursor) }

        assertIs<ScopeRead.Done>(w.puller.read(w.wallet(W5)))

        assertEquals(1 + WALLET_5_FROM_START_LIMIT_2.size, w.server.feedReads.size, "the refused read, then the whole read from the start")
        assertEquals(FeedPlaceOf(syncCursorOf(WALLET_5_FROM_START_LIMIT_2.last()), 27), w.place(w.wallet(W5)))
    }

    @Test
    fun aPurgeLandingMidReadIsOne410ForThatScopeThisRunAndTheNextRunCompletes() = runTest {
        val w = World(backgroundScope)
        w.server.signedIn()
        w.server.wallet = { _, cursor ->
            when (cursor) {
                null -> WALLET_5_MID_READ_PAGE_1
                nextCursorOf(WALLET_5_MID_READ_PAGE_1) -> WALLET_5_MID_READ_PAGE_2_EXPIRED
                else -> error("no more pages")
            }
        }

        assertEquals(ScopeRead.Failed, w.puller.read(w.wallet(W5)))
        assertEquals(2, w.server.feedReads.size, "the safeguard: no restart within the run")
        assertNull(w.place(w.wallet(W5))?.cursor, "nothing stored from a read that did not finish")

        w.server.wallet = pagesByCursor(WALLET_5_RESTART_AFTER_MID_READ_PURGE).let { pages -> { _, cursor -> pages(cursor) } }
        assertIs<ScopeRead.Done>(w.puller.read(w.wallet(W5)))
        assertEquals(FeedPlaceOf(syncCursorOf(WALLET_5_RESTART_AFTER_MID_READ_PURGE.last()), 31), w.place(w.wallet(W5)))
        assertNotEquals(WALLET_5_MID_READ_PAGE_1.body, WALLET_5_RESTART_AFTER_MID_READ_PURGE.first().body, "the restart read a newer first page")
    }
}
