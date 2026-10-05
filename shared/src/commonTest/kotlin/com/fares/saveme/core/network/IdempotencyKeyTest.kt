package com.fares.saveme.core.network

import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.jsonResponse
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IdempotencyKeyTest {

    @Test
    fun setsExactlyOneIdempotencyKeyAndTheStandardHeadersStay() = runTest {
        val env = AuthTestEnv { jsonResponse(200, """{"data":{},"meta":{}}""") }
        env.client.call<kotlinx.serialization.json.JsonObject> {
            method = HttpMethod.Post
            url("x")
            idempotencyKey("0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")
        }
        val headers = env.requests.single().headers
        assertEquals(listOf("0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70"), headers.getAll("Idempotency-Key"))
        listOf("Accept", "X-Client-Platform", "X-Client-Version", "Accept-Language", "X-Correlation-Id").forEach {
            assertNotNull(headers[it], it)
        }
    }
}
