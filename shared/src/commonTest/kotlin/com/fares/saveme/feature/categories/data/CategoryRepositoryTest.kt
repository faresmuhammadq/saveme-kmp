package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryBody
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.platformJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryRepositoryTest {

    @Test
    fun aPlaceholderIsVersionZeroAndNotInUse() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody()) }
        env.repository.insertPlaceholder("p1", "u1", "income", "Tips", "gift", "#B04375")
        val row = env.categoryDao.rows.value.getValue("p1")
        assertEquals(0L, row.version)
        assertEquals(false, row.inUse)
        assertEquals("income", row.kind)
        assertEquals("u1", row.accountId)
    }

    @Test
    fun theDefaultSetIsReplacedOnSuccessAndRetiredEntriesAreKept() = runTest {
        var fail = false
        val env = CategoryTestEnv(backgroundScope) {
            if (fail) jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
            else jsonResponse(200, categoryPage(listOf(platformJson("d1"), platformJson("d2", key = "old", position = 2, archivedAt = "2026-09-30T00:00:00Z")), limit = 26))
        }
        env.signedIn()
        env.seedDefaults(platformJson("stale", key = "stale"))
        assertIs<ApiResult.Success<Unit>>(env.repository.refreshDefaults())
        assertEquals(listOf("d1", "d2"), env.platformDao.rows.value.map { it.id })
        assertNotNull(env.platformDao.rows.value[1].archivedAt)
        assertNotNull(env.scopeDao.rows.value["platform_categories"])
        fail = true
        assertIs<ApiResult.Failure>(env.repository.refreshDefaults())
        assertEquals(2, env.platformDao.rows.value.size)
    }

    @Test
    fun markInUseFlagsTheRowAndATombstoneCopyRemovesIt() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody()) }
        env.signedIn()
        env.seed(categoryJson("a"))
        env.repository.markInUse("a")
        assertTrue(env.categoryDao.rows.value.getValue("a").inUse)
        assertTrue(env.repository.storeServerCopy(com.fares.saveme.core.network.AppJson.decodeFromString(com.fares.saveme.feature.categories.data.dto.CategoryDto.serializer(), categoryJson("a", deletedAt = "2026-10-02T00:00:00Z", version = 4))))
        assertNull(env.categoryDao.rows.value["a"])
    }
}
