package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FakeSyncApi
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.row
import com.fares.saveme.core.sync.FeedPuller
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.ScopeRead
import com.fares.saveme.core.sync.SyncLock
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.invitationJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * After the 9 to 10 reset every wallet scope is read from the start, so invitations created before the update (which the
 * old app skipped) are stored, and what the server no longer lists is swept (spec 013 FR-029, FR-030, US7-S5).
 */
class InvitationsAfterUpgradeTest {

    private fun fields(json: String) = (AppJson.parseToJsonElement(json) as JsonObject).filterKeys { it != "id" && it != "version" && it != "deleted_at" }

    @Test
    fun eachWalletIsReadFromTheStartAndPendingInvitationsAreStored() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet("w1")
        env.seedOwnerWallet("w2")
        // The phone after the migration: positions forgotten, data kept.
        env.scopeDao.clearFeed("wallet:w1")
        env.scopeDao.clearFeed("wallet:w2")
        env.seed(invitationJson("gone", walletId = "w1", version = 2))
        env.seed(invitationJson("queued", walletId = "w1", version = 2))
        env.repository.insertPlaceholder("fresh", "w1", "u1", "viewer", "a@b.co")
        env.engine.enqueue("invitation", "queued", SyncScope(ScopeKind.Wallet, "w1"), 2, "{}")

        val api = FakeSyncApi()
        api.wallet = { walletId, cursor ->
            assertNull(cursor, "read from the start")
            val rows = when (walletId) {
                "w1" -> listOf(
                    row("invitation", "old", version = 1, extra = fields(invitationJson("old", walletId = "w1", email = null))),
                    row("invitation", "queued", version = 3, extra = fields(invitationJson("queued", walletId = "w1"))),
                )
                else -> listOf(row("invitation", "other", version = 1, extra = fields(invitationJson("other", walletId = "w2", role = "viewer"))))
            }
            ApiResult.Success(page(rows, sync = "pos-$walletId", position = 7))
        }
        val puller = FeedPuller(api, env.scopeDao, { listOf(InvitationFeedApplier(env.repository)) }, { null }, SyncLock(), { "u1" }, env.clock)

        assertIs<ScopeRead.Done>(puller.read(SyncScope(ScopeKind.Wallet, "w1")))
        assertIs<ScopeRead.Done>(puller.read(SyncScope(ScopeKind.Wallet, "w2")))

        assertEquals(listOf<Pair<String, String?>>("w1" to null, "w2" to null), api.walletCalls)
        val stored = env.invitationDao.rows.value
        assertEquals("link", stored.getValue("old").channel)
        assertEquals("viewer", stored.getValue("other").role)
        assertNull(stored["gone"], "a synced invitation the server did not return is swept")
        assertNotNull(stored["queued"], "an invitation with a waiting change is kept")
        assertNotNull(stored["fresh"], "a placeholder is kept")
        assertEquals("pos-w1", env.scopeDao.rows.value["wallet:w1"]!!.feedCursor)
        assertEquals(7L, env.scopeDao.rows.value["wallet:w2"]!!.feedPosition)
    }
}
