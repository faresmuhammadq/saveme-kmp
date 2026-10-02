package com.fares.saveme.core.sync

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.jsonResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An item refused inside a batch raises the same app-wide signal as the single write (spec 009 FR-033). */
class SyncEngineBatchEventsTest {

    private fun refused(code: String) =
        """{"data":[{"type":"note","id":"n1","outcome":"rejected","status":403,"record":null,"problem":{"status":403,"code":"$code","request_id":"req-7"}}],"meta":{}}"""

    @Test
    fun anUnverifiedAddressItemPromptsVerificationAndIsRefused() = runTest {
        val env = AuthTestEnv { jsonResponse(200, refused("EMAIL_NOT_VERIFIED")) }
        env.events.events.test {
            val dao = FakePendingChangeDao()
            env.secureStore.write(com.fares.saveme.core.storage.StoredSession(CREDENTIAL, "a", kotlin.time.Instant.parse("2026-09-26T10:00:00Z")))
            val engine = SyncEngine(
                dao, FakeSyncScopeDao(), listOf(BatchHandler()), FakeSyncScheduler(), currentAccountId = { "u1" },
                syncApi = HttpSyncApi(env.client, env.events, batchTimeoutMillis = null),
            )
            engine.enqueue("note", "n1", SyncScope(ScopeKind.User, "u1"), 1, "{}")
            engine.drain()
            assertEquals(ApiEvent.EmailNotVerified, awaitItem())
            val row = dao.rows.value["note" to "n1"]!!
            assertEquals("Refused", row.state)
            assertTrue(row.refusalJson!!.contains("EMAIL_NOT_VERIFIED"))
        }
    }

    @Test
    fun aPlanLimitItemRaisesTheEntitlementSignalAndIsRefused() = runTest {
        val env = AuthTestEnv { jsonResponse(200, refused("FEATURE_NOT_ENTITLED")) }
        env.events.events.test {
            env.secureStore.write(com.fares.saveme.core.storage.StoredSession(CREDENTIAL, "a", kotlin.time.Instant.parse("2026-09-26T10:00:00Z")))
            val dao = FakePendingChangeDao()
            val engine = SyncEngine(
                dao, FakeSyncScopeDao(), listOf(BatchHandler()), FakeSyncScheduler(), currentAccountId = { "u1" },
                syncApi = HttpSyncApi(env.client, env.events, batchTimeoutMillis = null),
            )
            engine.enqueue("note", "n1", SyncScope(ScopeKind.User, "u1"), 1, "{}")
            engine.drain()
            assertEquals(ApiEvent.FeatureNotEntitled, awaitItem())
            assertEquals("Refused", dao.rows.value["note" to "n1"]!!.state)
        }
    }
}
