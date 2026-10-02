package com.fares.saveme.feature.categories.domain

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.platformJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class CategoriesServiceRefreshTest {

    private fun ok(request: io.ktor.client.request.HttpRequestData) =
        if (request.url.encodedPath.endsWith("/platform-categories")) categoryPage(listOf(platformJson()), limit = 26)
        else categoryPage(listOf(categoryJson()))

    @Test
    fun bothReadsRunAndStoreTheirResults() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, ok(it)) }
        env.signedIn()
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = true))
        assertEquals(2, env.requests.size)
        assertNotNull(env.categoryDao.rows.value["c1"])
        assertEquals(1, env.platformDao.rows.value.size)
    }

    @Test
    fun theSixtySecondFloorAppliesUnlessForced() = runTest {
        var reads = 0
        val env = CategoryTestEnv(backgroundScope) { reads++; jsonResponse(200, ok(it)) }
        env.signedIn()
        env.service.refresh(force = true)
        assertEquals(2, reads)
        env.clock.now = env.clock.now + 30.seconds
        env.service.refresh(force = false)
        assertEquals(2, reads)
        env.service.refresh(force = true)
        assertEquals(4, reads)
        env.clock.now = env.clock.now + 61.seconds
        env.service.refresh(force = false)
        assertEquals(6, reads)
    }

    @Test
    fun offlineAndFailedKeepTheLocalDataAndDoNotStartTheFloor() = runTest {
        var mode = "offline"
        var reads = 0
        val env = CategoryTestEnv(backgroundScope) {
            reads++
            when (mode) {
                "offline" -> throw kotlinx.io.IOException("offline")
                "fail" -> jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
                else -> jsonResponse(200, ok(it))
            }
        }
        env.signedIn()
        env.seed(categoryJson("keep"))
        env.seedDefaults(platformJson("kept"))
        assertEquals(RefreshOutcome.Offline, env.service.refresh())
        mode = "fail"
        assertEquals(RefreshOutcome.Failed, env.service.refresh())
        assertNotNull(env.categoryDao.rows.value["keep"])
        assertEquals("kept", env.platformDao.rows.value.single().id)
        mode = "ok"
        assertEquals(RefreshOutcome.Done, env.service.refresh())
    }

    @Test
    fun aForcedRefreshAlsoAsksTheEngineToSend() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, ok(it)) }
        env.signedIn()
        env.service.refresh(force = false)
        assertEquals(0, env.scheduler.enqueued.size)
        env.clock.now = env.clock.now + 5.seconds
        env.service.refresh(force = true)
        assertEquals(1, env.scheduler.enqueued.size)
    }
}
