package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.referenceJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CategoryReferenceRepositoryTest {

    @Test
    fun aCompleteReadReplacesTheWalletsSetAndLeavesOtherWalletsAlone() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(listOf(referenceJson("r1", categoryId = "p1"), referenceJson("r2", categoryId = "p2")))) }
        env.signedIn()
        env.seedReference("w1", "old")
        env.seedReference("w2", "other")
        assertIs<ApiResult.Success<Unit>>(env.references.refresh("w1"))
        assertEquals(setOf("p1", "p2"), env.referenceDao.get("w1").map { it.categoryId }.toSet())
        assertEquals(listOf("other"), env.referenceDao.get("w2").map { it.categoryId })
    }

    @Test
    fun aFailedReadKeepsWhatIsThereAndANotFoundRemovesIt() = runTest {
        var status = 503
        val env = CategoryTestEnv(backgroundScope) {
            if (status == 503) jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) else jsonResponse(404, problemBody(404, "NOT_FOUND"))
        }
        env.signedIn()
        env.seedReference("w1", "p1")
        assertIs<ApiResult.Failure>(env.references.refresh("w1"))
        assertEquals(1, env.referenceDao.get("w1").size)
        status = 404
        assertIs<ApiResult.Failure>(env.references.refresh("w1"))
        assertEquals(0, env.referenceDao.get("w1").size)
    }

    @Test
    fun readingEveryPageIsRequiredBeforeAnythingIsReplaced() = runTest {
        val env = CategoryTestEnv(backgroundScope) { request ->
            if (request.url.parameters["cursor"] == null) jsonResponse(200, categoryPage(listOf(referenceJson("r1", categoryId = "p1")), nextCursor = "c"))
            else jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
        }
        env.signedIn()
        env.seedReference("w1", "old")
        env.references.refresh("w1")
        assertEquals(listOf("old"), env.referenceDao.get("w1").map { it.categoryId })
    }

    @Test
    fun removeForAndClearEmptyTheTable() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        env.seedReference("w1", "a")
        env.seedReference("w2", "b")
        env.references.removeFor("w1")
        assertEquals(listOf("w2"), env.referenceDao.rows.value.map { it.walletId })
        env.references.clear()
        assertEquals(emptyList(), env.referenceDao.rows.value)
    }
}
