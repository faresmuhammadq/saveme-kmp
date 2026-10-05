package com.fares.saveme.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProblemFeatureTest {

    @Test
    fun theFeatureOfAFeatureNotEntitledAnswerIsRead() {
        val problem = AppJson.decodeFromString(
            ProblemDetails.serializer(),
            """{"type":"t","title":"x","status":403,"code":"FEATURE_NOT_ENTITLED","request_id":"r","feature":"sharing.max_members_per_wallet"}""",
        )
        assertEquals("sharing.max_members_per_wallet", problem.feature)
    }

    @Test
    fun anAbsentFeatureIsNull() {
        val problem = AppJson.decodeFromString(
            ProblemDetails.serializer(),
            """{"type":"t","title":"x","status":403,"code":"FORBIDDEN","request_id":"r"}""",
        )
        assertNull(problem.feature)
    }
}
