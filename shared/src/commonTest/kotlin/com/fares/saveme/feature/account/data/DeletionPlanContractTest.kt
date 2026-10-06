package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.dto.DeletionPlanDto
import com.fares.saveme.feature.account.data.dto.DeletionRequestDto
import com.fares.saveme.feature.account.data.dto.DeletionScheduleDto
import com.fares.saveme.feature.account.data.dto.SuccessorChoiceDto
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Contract tests from the `identity.yaml` deletion-request examples (backend spec 022). */
class DeletionPlanContractTest {

    private val activeWithChoice = """{"data":{"state":"active","deletion_requested_at":null,"deletion_scheduled_for":null,"successors":[{"wallet_id":"0192f79e-0000-7000-8000-000000000001","successor_user_id":"0192f700-0000-7000-8000-000000000002","reason":"only_member","choice_expected":false},{"wallet_id":"0192f79e-0000-7000-8000-000000000003","successor_user_id":"0192f700-0000-7000-8000-000000000004","reason":"oldest_editor","choice_expected":true}]},"meta":{}}"""
    private val pendingChosen = """{"data":{"state":"pending_deletion","deletion_requested_at":"2026-10-06T10:00:00.000000Z","deletion_scheduled_for":"2026-11-05T10:00:00.000000Z","successors":[{"wallet_id":"0192f79e-0000-7000-8000-000000000003","successor_user_id":"0192f700-0000-7000-8000-000000000005","reason":"chosen","choice_expected":true}]},"meta":{}}"""

    @Test
    fun planDecodesBothExamples() = runTest {
        var calls = 0
        val env = AccountTestEnv { jsonResponse(200, if (++calls == 1) activeWithChoice else pendingChosen) }
        env.signedIn()
        val api = AccountApi(env.auth.client)
        val active = assertIs<ApiResult.Success<DeletionPlanDto>>(api.deletionPlan()).value
        assertEquals("active", active.state)
        assertEquals(listOf(false, true), active.successors.map { it.choiceExpected })
        assertEquals("oldest_editor", active.successors[1].reason)
        val pending = assertIs<ApiResult.Success<DeletionPlanDto>>(api.deletionPlan()).value
        assertEquals("pending_deletion", pending.state)
        assertEquals("2026-11-05T10:00:00.000000Z", pending.deletionScheduledFor)
        assertEquals(HttpMethod.Get, env.requests.first().method)
        assertTrue(env.requests.first().url.encodedPath.endsWith("/api/v1/account/deletion-request"))
    }

    @Test
    fun passwordOnlyBodyHasNoSuccessors() = runTest {
        val env = AccountTestEnv { jsonResponse(200, """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T10:00:00Z"},"meta":{}}""") }
        env.signedIn()
        val schedule = assertIs<ApiResult.Success<DeletionScheduleDto>>(AccountApi(env.auth.client).requestDeletion(DeletionRequestDto("pw"))).value
        assertEquals(emptyList(), schedule.successors)
        assertEquals(setOf("password"), env.requests.single().jsonBody().keys)
    }

    @Test
    fun withChoiceBodyMatchesTheExample() = runTest {
        val env = AccountTestEnv {
            jsonResponse(200, """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T10:00:00Z","successors":[{"wallet_id":"w3","successor_user_id":"u5","reason":"chosen","choice_expected":true}]},"meta":{}}""")
        }
        env.signedIn()
        val body = DeletionRequestDto("pw", listOf(SuccessorChoiceDto("0192f79e-0000-7000-8000-000000000003", "0192f700-0000-7000-8000-000000000005")))
        val schedule = assertIs<ApiResult.Success<DeletionScheduleDto>>(AccountApi(env.auth.client).requestDeletion(body)).value
        assertEquals("u5", schedule.successors.single().successorUserId)
        val expected = Json.parseToJsonElement(
            """{"password":"pw","successors":[{"wallet_id":"0192f79e-0000-7000-8000-000000000003","member_user_id":"0192f700-0000-7000-8000-000000000005"}]}""",
        ).jsonObject
        assertEquals<JsonObject>(expected, env.requests.single().jsonBody())
    }

    @Test
    fun theRequestNeverPrintsThePassword() {
        val dto = DeletionRequestDto("hunter2-secret", listOf(SuccessorChoiceDto("w", "u")))
        assertFalse(dto.toString().contains("hunter2"))
        assertFalse("$dto".contains("hunter2"))
    }

    @Test
    fun aSuccessorPointerParses() = runTest {
        val env = AccountTestEnv {
            jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/successors/0/member_user_id","code":"INVALID_ID"}]"""))
        }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(AccountApi(env.auth.client).requestDeletion(DeletionRequestDto("pw", listOf(SuccessorChoiceDto("w", "u")))))
        assertEquals("/successors/0/member_user_id", failure.problem.errors!!.single().pointer)
        assertEquals("deletion", AppJson.decodeFromString(DeletionPlanDto.serializer(), """{"state":"deletion"}""").state)
    }
}
