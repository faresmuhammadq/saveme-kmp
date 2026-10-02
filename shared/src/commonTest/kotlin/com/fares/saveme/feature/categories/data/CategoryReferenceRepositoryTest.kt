package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.referenceJson
import com.fares.saveme.feature.categories.data.dto.CategoryReferenceDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The partner references table, kept current from a wallet's feed (spec 009; the on-demand read is retired). */
class CategoryReferenceRepositoryTest {

    private fun dto(json: String) = AppJson.decodeFromString(CategoryReferenceDto.serializer(), json)

    @Test
    fun aFeedReferenceIsStoredAndANewOneForTheSameCategoryReplacesTheOld() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        assertTrue(env.references.storeFeedCopy(dto(referenceJson("r1", categoryId = "p1"))))
        assertTrue(env.references.storeFeedCopy(dto(referenceJson("r2", categoryId = "p1", name = "Renamed"))))
        assertEquals(listOf("r2"), env.referenceDao.get("w1").map { it.id })
        assertEquals("Renamed", env.referenceDao.get("w1").single().name)
    }

    @Test
    fun anOlderVersionIsIgnoredAndADeletionRemovesTheRow() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        env.references.storeFeedCopy(dto(referenceJson("r1").replace("\"version\":1", "\"version\":4")))
        assertFalse(env.references.storeFeedCopy(dto(referenceJson("r1", name="Stale"))))
        assertEquals("Partner's coffee", env.referenceDao.get("w1").single().name)
        env.references.storeFeedCopy(dto(referenceJson("r1").replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T00:00:00Z\"").replace("\"version\":1", "\"version\":5")))
        assertEquals(emptyList(), env.referenceDao.get("w1"))
    }

    @Test
    fun aSweepRemovesOnlyTheWalletsUnseenReferences() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        env.seedReference("w1", "keep")
        env.seedReference("w1", "gone")
        env.seedReference("w2", "other")
        env.references.sweep("w1", setOf("r-w1-keep"))
        assertEquals(listOf("keep"), env.referenceDao.get("w1").map { it.categoryId })
        assertEquals(listOf("other"), env.referenceDao.get("w2").map { it.categoryId })
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
