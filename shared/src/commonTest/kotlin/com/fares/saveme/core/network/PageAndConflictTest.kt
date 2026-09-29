package com.fares.saveme.core.network

import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@Serializable
private data class Item(val id: String)

class PageAndConflictTest {

    @Test
    fun callPageDecodesItemsAndCursor() = runTest {
        val env = AuthTestEnv {
            jsonResponse(200, """{"data":[{"id":"a","extra":1},{"id":"b"}],"meta":{"next_cursor":"c2","has_more":true,"limit":2}}""")
        }
        val result = env.client.callPage<Item> { url("things") }
        val page = assertIs<ApiResult.Success<Page<Item>>>(result).value
        assertEquals(listOf(Item("a"), Item("b")), page.items)
        assertEquals("c2", page.nextCursor)
        assertEquals(true, page.hasMore)
    }

    @Test
    fun lastPageHasNoCursor() = runTest {
        val env = AuthTestEnv { jsonResponse(200, """{"data":[],"meta":{"next_cursor":null,"has_more":false,"limit":50}}""") }
        val page = assertIs<ApiResult.Success<Page<Item>>>(env.client.callPage<Item> { url("things") }).value
        assertEquals(emptyList(), page.items)
        assertEquals(null, page.nextCursor)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun versionConflictCarriesCurrent() = runTest {
        val env = AuthTestEnv {
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", extra = ""","current":{"id":"p1","version":6}"""))
        }
        val failure = assertIs<ApiResult.Failure>(env.client.call<Unit> { method = HttpMethod.Put; url("me/profile") })
        assertEquals(ApiErrorCode.VERSION_CONFLICT, failure.code)
        assertEquals(RetryClass.AfterChange, failure.code.retryClass)
        val current = assertNotNull(failure.problem.current)
        assertEquals(6, current.jsonObject["version"]!!.jsonPrimitive.int)
    }
}
