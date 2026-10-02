package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryPage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** A wallet's partner categories arrive with its feed; a screen full of misses still asks once a minute (spec 009 FR-001). */
class CategoryReferencesRefreshTest {

    @Test
    fun aWalletsReferencesAreReadThroughItsFeedWithTheFloorPerWalletAndForceBypassingIt() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        env.signedIn()
        env.service.refreshReferences("w1")
        env.service.refreshReferences("w1")
        env.service.refreshReferences("w2")
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to false, ReadTarget.Wallet("w2") to false), env.runner.refreshes)
        env.service.refreshReferences("w1", force = true)
        assertEquals(ReadTarget.Wallet("w1") to true, env.runner.refreshes.last())
        env.clock.now = env.clock.now + 61.seconds
        env.service.refreshReferences("w2")
        assertEquals(4, env.runner.refreshes.size)
        assertEquals(0, env.requests.size, "no list of references is read any more")
    }

    @Test
    fun aFailedAttemptStillCountsForTheFloorSoAScreenOfMissesAsksOnce() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        env.signedIn()
        env.runner.outcome = ReadOutcome.Offline
        assertEquals(RefreshOutcome.Offline, env.service.refreshReferences("w1"))
        assertEquals(RefreshOutcome.Done, env.service.refreshReferences("w1"))
        assertEquals(1, env.runner.refreshes.size)
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
