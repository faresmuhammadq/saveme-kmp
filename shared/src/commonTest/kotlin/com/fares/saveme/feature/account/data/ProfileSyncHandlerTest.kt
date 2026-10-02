package com.fares.saveme.feature.account.data

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.account.meBody
import com.fares.saveme.feature.account.profileJson
import com.fares.saveme.feature.auth.jsonResponse
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** The profile's way through the engine (spec 004 T028): spec 002's contract, unchanged. */
class ProfileSyncHandlerTest {

    private val draft = ProfileDraft("Sara M", "ar", "Africa/Cairo", "EGP", 5)

    private fun change(base: Long) = PendingChange(
        recordType = "profile", recordId = "p1", accountId = "u1", scope = SyncScope(ScopeKind.User, "u1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(ProfileDraft.serializer(), draft), isDelete = false,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    @Test
    fun sendsTheDraftWithTheBaseVersion() = runTest {
        val env = AccountTestEnv { request ->
            if (request.method == HttpMethod.Get) jsonResponse(200, meBody()) else jsonResponse(200, """{"data":${profileJson(version = 4)},"meta":{}}""")
        }
        env.signedIn()
        val result = env.profileHandler.send(change(base = 3))
        assertIs<ApiResult.Success<*>>(result)
        val body = env.requests.single { it.method == HttpMethod.Put }.jsonBody()
        assertEquals("3", body["version"].toString())
        assertEquals("\"Sara M\"", body["display_name"].toString())
        assertEquals("5", body["budget_period_start_day"].toString())
    }

    @Test
    fun appliesTheServerCopyAndRestoresTheLanguageOnDiscard() = runTest {
        val env = AccountTestEnv { jsonResponse(200, meBody()) }
        env.profileHandler.applyServerCopy(AppJson.parseToJsonElement(profileJson(version = 6, displayName = "Web", locale = "ar")))
        assertEquals("Web", env.profileDao.row.value!!.displayName)
        env.profileHandler.onDiscarded("p1")
        assertEquals(AppLanguage.Ar, env.languages.effective())
    }

    @Test
    fun mapsRefusedPointersToProfileFields() {
        val env = AccountTestEnv { jsonResponse(200, meBody()) }
        val fields = env.profileHandler.refusalFields(
            ProblemDetails(code = "VALIDATION_FAILED", errors = listOf(ProblemError("/default_currency", "IMMUTABLE_FIELD"))),
        )
        assertEquals(mapOf("DefaultCurrency" to "IMMUTABLE_FIELD"), fields)
    }
}
