package com.fares.saveme.feature.account.domain.usecase

import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoice
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.model.PasswordField
import com.fares.saveme.feature.account.domain.model.ScheduledOwner
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class RequestDeletionUseCaseTest {

    private val ok = """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-11-05T10:00:00Z","successors":[{"wallet_id":"w3","successor_user_id":"u5","reason":"chosen","choice_expected":true}]},"meta":{}}"""

    private fun useCase(env: AccountTestEnv, scope: CoroutineScope) =
        RequestDeletionUseCase(env.api, SessionManager(env.auth.repository, env.auth.entitlementsApi, env.events, scope))

    @Test
    fun sendsSuccessorsWhenGivenAndReturnsTheServersOwners() = runTest {
        val env = AccountTestEnv { jsonResponse(200, ok) }
        env.signedIn()
        val outcome = useCase(env, backgroundScope)("pw", listOf(SuccessorChoice("w3", "u5")))
        assertEquals(
            DeletionOutcome.Scheduled(Instant.parse("2026-11-05T10:00:00Z"), listOf(ScheduledOwner("w3", "u5", SuccessorReason.Chosen))),
            outcome,
        )
        val body = env.requests.single().jsonBody()
        assertEquals(setOf("password", "successors"), body.keys)
        val entry = body.getValue("successors").jsonArray.single().jsonObject
        assertEquals(mapOf("wallet_id" to JsonPrimitive("w3"), "member_user_id" to JsonPrimitive("u5")), entry.toMap())
    }

    @Test
    fun omitsSuccessorsWhenNull() = runTest {
        val env = AccountTestEnv { jsonResponse(200, ok) }
        env.signedIn()
        useCase(env, backgroundScope)("pw", null)
        assertEquals(setOf("password"), env.requests.single().jsonBody().keys)
    }

    @Test
    fun aSuccessorsPointerMeansTheWalletsChangedAndNothingIsRetried() = runTest {
        val env = AccountTestEnv {
            jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/successors/0/member_user_id","code":"INVALID_ID"}]"""))
        }
        env.signedIn()
        assertEquals(DeletionOutcome.SuccessorsChanged, useCase(env, backgroundScope)("pw", listOf(SuccessorChoice("w3", "u5"))))
        assertEquals(1, env.requests.size)
    }

    @Test
    fun aPasswordPointerKeepsSpec002Handling() = runTest {
        val env = AccountTestEnv {
            jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/password","code":"REQUIRED"}]"""))
        }
        env.signedIn()
        assertEquals(
            DeletionOutcome.Validation(mapOf(PasswordField.Current to "REQUIRED")),
            useCase(env, backgroundScope)("", listOf(SuccessorChoice("w3", "u5"))),
        )
    }

    @Test
    fun wrongPasswordStillMapsAndNeverPrintsThePassword() = runTest {
        val env = AccountTestEnv { jsonResponse(401, problemBody(401, "INVALID_CREDENTIALS")) }
        env.signedIn()
        val outcome = useCase(env, backgroundScope)("hunter2-secret", null)
        assertEquals(DeletionOutcome.WrongPassword, outcome)
        assertFalse(outcome.toString().contains("hunter2"))
    }
}
