package com.fares.saveme.feature.categories.domain

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.referenceJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CategoryReferencesRefreshTest {

    @Test
    fun theFloorIsPerWalletAndForceBypassesIt() = runTest {
        val reads = mutableListOf<String>()
        val env = CategoryTestEnv(backgroundScope) { request ->
            reads += request.url.encodedPath.substringAfter("wallets/").substringBefore("/")
            jsonResponse(200, categoryPage(listOf(referenceJson(walletId = "w1"))))
        }
        env.signedIn()
        env.service.refreshReferences("w1")
        env.service.refreshReferences("w1")
        env.service.refreshReferences("w2")
        assertEquals(listOf("w1", "w2"), reads)
        env.service.refreshReferences("w1", force = true)
        assertEquals(listOf("w1", "w2", "w1"), reads)
        env.clock.now = env.clock.now + 61.seconds
        env.service.refreshReferences("w2")
        assertEquals(4, reads.size)
    }

    @Test
    fun aNotFoundRemovesTheWalletsReferences() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        env.seedReference("w1", "p1")
        env.seedReference("w2", "p2")
        assertEquals(RefreshOutcome.Failed, env.service.refreshReferences("w1", force = true))
        assertEquals(listOf("w2"), env.referenceDao.rows.value.map { it.walletId })
    }

    @Test
    fun aFailedAttemptStillCountsForTheFloorSoAScreenOfMissesAsksOnce() = runTest {
        var reads = 0
        val env = CategoryTestEnv(backgroundScope) { reads++; throw kotlinx.io.IOException("offline") }
        env.signedIn()
        assertEquals(RefreshOutcome.Offline, env.service.refreshReferences("w1"))
        assertEquals(RefreshOutcome.Done, env.service.refreshReferences("w1"))
        assertEquals(1, reads)
    }

    @Test
    fun aWalletDeletionRemovesItsReferences() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        env.seedReference("w1", "p1")
        env.seedReference("w2", "p2")
        CategoryReferencesCleanup(env.references).onWalletDeleted("w1")
        assertEquals(listOf("w2"), env.referenceDao.rows.value.map { it.walletId })
    }
}
