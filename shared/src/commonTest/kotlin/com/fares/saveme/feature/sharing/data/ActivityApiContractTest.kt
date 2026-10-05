package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.activityJson
import com.fares.saveme.feature.sharing.activityPageBody
import com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A9 of contracts/sharing-api-client.md (P-14.3). */
class ActivityApiContractTest {

    @Test
    fun theFirstPageAsksForFiftyAndTheNextOneFollowsTheCursor() = runTest {
        val env = SharingTestEnv(backgroundScope) { request ->
            if (request.url.parameters["cursor"] == null) jsonResponse(200, activityPageBody(listOf(activityJson("a1")), next = "next-1"))
            else jsonResponse(200, activityPageBody(listOf(activityJson("a2"))))
        }
        env.signedIn()
        val first = assertIs<ApiResult.Success<Page<ActivityEntryDto>>>(env.activityApi.page("w1", null)).value
        assertEquals("next-1", first.nextCursor)
        assertTrue(first.hasMore)
        val second = assertIs<ApiResult.Success<Page<ActivityEntryDto>>>(env.activityApi.page("w1", "next-1")).value
        assertTrue(!second.hasMore)
        val (one, two) = env.requests
        assertEquals(HttpMethod.Get, one.method)
        assertTrue(one.url.encodedPath.endsWith("/wallets/w1/activity"))
        assertEquals(setOf("limit"), one.url.parameters.names())
        assertEquals("50", one.url.parameters["limit"])
        assertEquals(setOf("limit", "cursor"), two.url.parameters.names())
        assertEquals("next-1", two.url.parameters["cursor"])
    }

    @Test
    fun anEntryWithNoActorOrRoleDecodesAndUnknownMembersAreIgnored() = runTest {
        val env = SharingTestEnv(backgroundScope) {
            jsonResponse(200, activityPageBody(listOf(activityJson("a1", actorId = null, actorName = null, action = "confirmed", subjectType = "occurrence"))))
        }
        env.signedIn()
        val entry = assertIs<ApiResult.Success<Page<ActivityEntryDto>>>(env.activityApi.page("w1", null)).value.items.single()
        assertNull(entry.actorUserId)
        assertNull(entry.actorDisplayName)
        assertNull(entry.role)
        assertEquals("occurrence", entry.subjectType)
    }

    @Test
    fun anUnknownActionStillDecodesAndTheDomainSkipsIt() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, activityPageBody(listOf(activityJson("a1", action = "something_new")))) }
        env.signedIn()
        assertEquals("something_new", assertIs<ApiResult.Success<Page<ActivityEntryDto>>>(env.activityApi.page("w1", null)).value.items.single().action)
    }

    @Test
    fun anInvalidCursorAndAMissingWalletAreReadByCode() = runTest {
        val bad = SharingTestEnv(backgroundScope) { jsonResponse(400, problemBody(400, "INVALID_CURSOR")) }
        bad.signedIn()
        assertEquals(ApiErrorCode.INVALID_CURSOR, assertIs<ApiResult.Failure>(bad.activityApi.page("w1", "old")).code)
        val gone = SharingTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        gone.signedIn()
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(gone.activityApi.page("w1", null)).code)
    }
}
