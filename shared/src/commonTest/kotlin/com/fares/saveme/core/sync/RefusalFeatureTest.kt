package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefusalFeatureTest {

    @Test
    fun aStoredRefusalWithoutAFeatureStillDecodes() {
        val refusal = AppJson.decodeFromString(Refusal.serializer(), """{"code":"FORBIDDEN","fields":{"role":"x"},"requestId":"r1"}""")
        assertEquals("FORBIDDEN", refusal.code)
        assertNull(refusal.feature)
        assertEquals("r1", refusal.requestId)
    }

    @Test
    fun theFeatureRoundTrips() {
        val refusal = Refusal("FEATURE_NOT_ENTITLED", emptyMap(), "r", "sharing.enabled")
        assertEquals(refusal, AppJson.decodeFromString(Refusal.serializer(), AppJson.encodeToString(Refusal.serializer(), refusal)))
    }

    @Test
    fun theEngineRecordsTheFeatureOfAFeatureNotEntitledAnswer() = runTest {
        val h = EngineHarness()
        h.handler.answer = {
            ApiResult.Failure(
                ProblemDetails(status = 403, code = "FEATURE_NOT_ENTITLED", requestId = "req-9", feature = "sharing.max_members_per_wallet"),
                ApiErrorCode.FEATURE_NOT_ENTITLED, null,
            )
        }
        h.save("n1", """{"a":1}""")
        h.engine.drain()
        val refusal = h.engine.get("note", "n1")!!.refusal!!
        assertEquals("FEATURE_NOT_ENTITLED", refusal.code)
        assertEquals("sharing.max_members_per_wallet", refusal.feature)
        assertEquals("req-9", refusal.requestId)
    }

    @Test
    fun aRefusalWithoutAFeatureKeepsItNull() = runTest {
        val h = EngineHarness()
        h.handler.answer = { EngineHarness.failure(ApiErrorCode.FORBIDDEN) }
        h.save("n1", """{"a":1}""")
        h.engine.drain()
        assertNull(h.engine.get("note", "n1")!!.refusal!!.feature)
    }
}
