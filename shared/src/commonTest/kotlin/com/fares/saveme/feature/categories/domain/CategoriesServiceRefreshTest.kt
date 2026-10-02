package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.platformJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Own categories come from the person's change feed through the runner (spec 009); the default set is the
 * one list no feed carries and is still read here (FR-016). The floors and outcomes keep their meaning.
 */
class CategoriesServiceRefreshTest {

    private fun defaults(request: io.ktor.client.request.HttpRequestData) = categoryPage(listOf(platformJson()), limit = 26)

    @Test
    fun theOwnCategoriesAreReadThroughTheRunnerAndTheDefaultsFromTheirOwnList() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, defaults(it)) }
        env.signedIn()
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = true))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.User to true), env.runner.refreshes)
        assertEquals(1, env.requests.size)
        assertEquals(true, env.requests.single().url.encodedPath.endsWith("/platform-categories"))
        assertEquals(1, env.platformDao.rows.value.size)
    }

    @Test
    fun theCallersForceReachesTheRunnerAndTheDefaultsKeepTheirSixtySecondFloor() = runTest {
        var reads = 0
        val env = CategoryTestEnv(backgroundScope) { reads++; jsonResponse(200, defaults(it)) }
        env.signedIn()
        env.service.refresh(force = true)
        assertEquals(1, reads)
        env.clock.now = env.clock.now + 30.seconds
        env.service.refresh(force = false)
        assertEquals(1, reads, "the defaults are not read again within a minute")
        env.service.refresh(force = true)
        assertEquals(2, reads)
        env.clock.now = env.clock.now + 61.seconds
        env.service.refresh(force = false)
        assertEquals(3, reads)
        assertEquals(listOf(true, false, true, false), env.runner.refreshes.map { it.second })
    }

    @Test
    fun theScreensOutcomeIsDoneOnlyWhenBothReadsCompletedOfflineWhenEitherWasUnreachableAndFailedOtherwise() = runTest {
        var defaultsMode = "ok"
        val env = CategoryTestEnv(backgroundScope) {
            when (defaultsMode) {
                "offline" -> throw kotlinx.io.IOException("offline")
                "fail" -> jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
                else -> jsonResponse(200, defaults(it))
            }
        }
        env.signedIn()
        env.runner.outcome = ReadOutcome.Offline
        assertEquals(RefreshOutcome.Offline, env.service.refresh(force = true))
        env.runner.outcome = ReadOutcome.Failed
        defaultsMode = "offline"
        assertEquals(RefreshOutcome.Offline, env.service.refresh(force = true))
        env.seedDefaults(platformJson("kept"))
        defaultsMode = "fail"
        assertEquals(RefreshOutcome.Failed, env.service.refresh(force = true))
        assertEquals("kept", env.platformDao.rows.value.single().id, "a failed read keeps the cached set")
        env.runner.outcome = ReadOutcome.Done
        defaultsMode = "ok"
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = true))
    }

    @Test
    fun aForcedRefreshAlsoAsksTheEngineToSend() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, defaults(it)) }
        env.signedIn()
        env.service.refresh(force = false)
        assertEquals(0, env.scheduler.enqueued.size)
        env.clock.now = env.clock.now + 5.seconds
        env.service.refresh(force = true)
        assertEquals(1, env.scheduler.enqueued.size)
    }
}
