package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.SaveResult
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.signedInAccount
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletsServiceSaveTest {
    private fun env(scope: kotlinx.coroutines.CoroutineScope) = WalletTestEnv(scope) { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }
    private val draft = WalletDraft("cash", "Renamed", "EGP", 25_000, null, null)

    private fun payload(env: WalletTestEnv, id: String = "w1") =
        AppJson.decodeFromString(WalletDraft.serializer(), env.pendingDao.rows.value.getValue("wallet" to id).payloadJson)

    @Test
    fun saveQueuesOnTheEditorsBaseVersionEvenIfARefreshMovedTheRow() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.seed(walletJson(version = 4, name = "Moved"))
        assertEquals(SaveResult.Saved, env.service.save("w1", draft, baseVersion = 3))
        assertEquals(3L, env.pendingDao.rows.value.getValue("wallet" to "w1").baseVersion)
    }

    @Test
    fun aSecondSaveCollapsesIntoTheSameRowKeepingTheOldestBase() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.service.save("w1", draft, 3)
        env.service.save("w1", draft.copy(name = "Again"), 4)
        assertEquals(1, env.pendingDao.rows.value.size)
        assertEquals(3L, env.pendingDao.rows.value.getValue("wallet" to "w1").baseVersion)
        assertEquals("Again", payload(env).name)
    }

    @Test
    fun nonOwnersUnverifiedAndMissingWalletsAreTurnedAway() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson("viewer", role = "viewer"))
        env.seed(walletJson("editor", role = "editor"))
        assertEquals(SaveResult.NotOwner, env.service.save("viewer", draft, 1))
        assertEquals(SaveResult.NotOwner, env.service.save("editor", draft, 1))
        assertEquals(SaveResult.NotOwner, env.service.delete("editor"))
        assertEquals(SaveResult.Gone, env.service.save("missing", draft, 1))
        env.authState.value = AuthState.SignedIn(signedInAccount.copy(emailVerified = false))
        env.seed(walletJson("w1"))
        assertEquals(SaveResult.NotVerified, env.service.save("w1", draft, 1))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun unknownIconAndColourSurviveASave() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3, icon = "rocket", color = "#123456"))
        val shown = assertNotNull(env.service.observeWallet("w1").first()).shown
        env.service.save("w1", shown.copy(name = "Renamed"), 3)
        assertEquals("rocket", payload(env).icon)
        assertEquals("#123456", payload(env).color)
    }

    @Test
    fun aSaveAfterAWaitingArchiveKeepsArchiveTrue() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.service.archive("w1")
        env.service.save("w1", draft.copy(archive = false), 3)
        assertTrue(payload(env).archive)
        assertEquals("Renamed", payload(env).name)
    }

    @Test
    fun tryAgainReEnqueuesTheUnchangedPayloadOnTheSameBase() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.service.save("w1", draft, 3)
        val row = env.pendingDao.rows.value.getValue("wallet" to "w1")
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("wallet" to "w1") to row.copy(
            state = ChangeState.Refused.name, refusalJson = """{"code":"EMAIL_NOT_VERIFIED","requestId":"r1"}""",
        ))
        val refused = assertIs<SyncState.Refused>(env.service.observeWallet("w1").first()!!.syncState)
        assertEquals(RefusalKind.NotVerified, refused.reason.kind)
        assertTrue(refused.reason.canRetryUnchanged)
        env.service.tryAgain("w1")
        val after = env.pendingDao.rows.value.getValue("wallet" to "w1")
        assertEquals(ChangeState.Waiting.name, after.state)
        assertEquals(3L, after.baseVersion)
        assertEquals(row.payloadJson, after.payloadJson)
    }

    @Test
    fun discardingANeverSyncedCreateRemovesTheWallet() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val id = (env.service.create(draft) as com.fares.saveme.feature.wallets.domain.model.CreateResult.Created).id
        env.service.discard(id)
        assertNull(env.walletDao.rows.value[id])
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun refusalFieldsReachTheViewByField() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(walletJson(version = 3))
        env.service.save("w1", draft, 3)
        val row = env.pendingDao.rows.value.getValue("wallet" to "w1")
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("wallet" to "w1") to row.copy(
            state = ChangeState.Refused.name, refusalJson = """{"code":"VALIDATION_FAILED","fields":{"Currency":"IMMUTABLE_FIELD"}}""",
        ))
        val refused = assertIs<SyncState.Refused>(env.service.observeWallet("w1").first()!!.syncState)
        assertEquals(mapOf(WalletField.Currency to "IMMUTABLE_FIELD"), refused.fields)
        assertEquals(RefusalKind.Invalid, refused.reason.kind)
    }
}
