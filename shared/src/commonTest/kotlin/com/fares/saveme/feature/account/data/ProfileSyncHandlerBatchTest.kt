package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.account.profileJson
import com.fares.saveme.feature.auth.jsonResponse
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** The profile item is `PUT /me/profile`'s body with the person's profile id (spec 009 FR-024). */
class ProfileSyncHandlerBatchTest {
    private val draft = ProfileDraft(null, "ar", "Africa/Cairo", "EGP", 5)

    private fun change(base: Long) = PendingChange(
        recordType = "profile", recordId = "p1", accountId = "u1", scope = SyncScope(ScopeKind.User, "u1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(ProfileDraft.serializer(), draft), isDelete = false,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    @Test
    fun theItemCarriesTheProfileIdAndTheBodyOfThePutIncludingACleanedDisplayName() = runTest {
        val env = AccountTestEnv { jsonResponse(200, """{"data":${profileJson(version = 4)},"meta":{}}""") }
        env.signedIn()
        val item = env.profileHandler.batchItem(change(3))!!
        env.profileHandler.send(change(3))
        assertEquals("profile", item.type)
        assertEquals(BatchOp.Upsert, item.op)
        assertEquals("p1", item.id)
        assertNull(item.walletId)
        assertEquals(env.requests.single { it.method == HttpMethod.Put }.jsonBody(), item.data)
        assertEquals("null", (item.data as kotlinx.serialization.json.JsonObject)["display_name"].toString(), "required-nullable stays explicit")
    }
}
