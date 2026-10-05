package com.fares.saveme.core.network

import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.request.url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Serializable
private data class Thing(val id: String)

class CallWithMetaTest {

    @Test
    fun callWithMetaKeepsTheMetaOfTheEnvelope() = runTest {
        val env = AuthTestEnv { jsonResponse(201, """{"data":{"id":"a"},"meta":{"share_url":"https://x/invite?token=abc"}}""") }
        val envelope = assertIs<ApiResult.Success<Envelope<Thing>>>(env.client.callWithMeta<Thing> { url("things") }).value
        assertEquals(Thing("a"), envelope.data)
        assertEquals("https://x/invite?token=abc", envelope.meta.jsonObject["share_url"]!!.jsonPrimitive.content)
    }

    @Test
    fun anEmptyMetaIsAnEmptyObject() = runTest {
        val env = AuthTestEnv { jsonResponse(200, """{"data":{"id":"a"},"meta":{}}""") }
        val envelope = assertIs<ApiResult.Success<Envelope<Thing>>>(env.client.callWithMeta<Thing> { url("things") }).value
        assertTrue(envelope.meta.isEmpty())
    }

    @Test
    fun callIsUnchanged() = runTest {
        val env = AuthTestEnv { jsonResponse(200, """{"data":{"id":"a"},"meta":{"ignored":1}}""") }
        assertEquals(Thing("a"), assertIs<ApiResult.Success<Thing>>(env.client.call<Thing> { url("things") }).value)
    }

    @Test
    fun aFailureStillCarriesTheProblemWithItsFeature() = runTest {
        val env = AuthTestEnv { jsonResponse(403, problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.enabled"""")) }
        val failure = assertIs<ApiResult.Failure>(env.client.callWithMeta<Thing> { url("things") })
        assertEquals("sharing.enabled", failure.problem.feature)
    }
}
