package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.FakePendingUses
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CreateResult
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SaveResult
import com.fares.saveme.feature.categories.domain.model.SyncState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoriesServiceArchiveDeleteTest {
    private fun env(scope: kotlinx.coroutines.CoroutineScope, uses: FakePendingUses = FakePendingUses()) =
        // The upload the service requests parks at the server for good: these tests set the queue's state by hand,
        // and an answer racing in from the engine's drain on another thread would overwrite it.
        CategoryTestEnv(scope, uses) { awaitCancellation() }

    private fun payloadOf(env: CategoryTestEnv, id: String = "c1") =
        AppJson.decodeFromString(CategoryPayload.serializer(), env.pendingDao.rows.value.getValue("category" to id).payloadJson)

    private suspend fun refuseDelete(env: CategoryTestEnv) {
        val row = env.pendingDao.rows.value.getValue("category" to "c1")
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("category" to "c1") to row.copy(
            state = ChangeState.Refused.name, refusalJson = """{"code":"CATEGORY_IN_USE","requestId":"r1"}""",
        ))
    }

    @Test
    fun archiveQueuesTheShownDraftWithArchiveTrueOnTheRowsVersion() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3, icon = "restaurant", color = "#1F7F8C"))
        assertEquals(SaveResult.Saved, env.service.archive("c1"))
        val change = env.pendingDao.rows.value.getValue("category" to "c1")
        assertEquals(3L, change.baseVersion)
        val draft = payloadOf(env)
        assertTrue(draft.archive)
        assertEquals("Coffee", draft.name)
        assertTrue(env.service.observeCategory("c1").first()!!.archived)
    }

    @Test
    fun deleteIsOfferedOnlyWhenNothingUsesTheCategory() = runTest {
        val uses = FakePendingUses()
        val env = env(backgroundScope, uses)
        env.signedIn()
        env.seed(categoryJson("used", inUse = true, version = 2))
        env.seed(categoryJson("local", version = 2))
        env.seed(categoryJson("free", version = 2))
        uses.used.value = setOf("local")
        assertEquals(SaveResult.InUse, env.service.delete("used"))
        assertEquals(SaveResult.InUse, env.service.delete("local"))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(SaveResult.Saved, env.service.delete("free"))
        assertTrue(env.pendingDao.rows.value.getValue("category" to "free").isDelete)
    }

    @Test
    fun anArchivedUnusedCategoryCanBeDeleted() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 2, archivedAt = "2026-09-05T00:00:00Z"))
        val view = env.service.observeCategory("c1").first()!!
        assertTrue(view.canDelete(emptySet()))
        assertEquals(SaveResult.Saved, env.service.delete("c1"))
    }

    @Test
    fun aDefaultCanNeverBeDeleted() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seedDefaults()
        assertEquals(SaveResult.NoLongerAvailable, env.service.delete("d1"))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun deletingANeverSentCategoryRemovesItAndQueuesNothing() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val id = (env.service.create(CategoryDraft("expense", "Typo", null, null)) as CreateResult.Created).id
        assertEquals(SaveResult.Saved, env.service.delete(id))
        assertNull(env.categoryDao.rows.value[id])
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(0, env.requests.size)
    }

    @Test
    fun aWaitingDeletionHidesTheCategoryAndAClashOrRefusalShowsItAgain() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3))
        env.service.delete("c1")
        val change = env.pendingDao.rows.value.getValue("category" to "c1")
        assertTrue(change.isDelete)
        assertEquals(3L, change.baseVersion)
        assertNull(env.service.observeCategory("c1").first())
        assertNotNull(env.categoryDao.rows.value["c1"])
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("category" to "c1") to change.copy(
            state = ChangeState.Clash.name, clashJson = categoryJson(version = 5, name = "Web"),
        ))
        val clash = env.service.observeCategory("c1").first()!!
        assertTrue(clash.pendingDelete)
        assertIs<SyncState.Clash>(clash.syncState)
        refuseDelete(env)
        assertEquals(RefusalReason.InUse, assertIs<SyncState.Refused>(env.service.observeCategory("c1").first()!!.syncState).reason)
    }

    @Test
    fun archiveInsteadTurnsTheRefusedDeletionIntoAWaitingArchiveOnTheSameBase() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3))
        env.service.delete("c1")
        refuseDelete(env)
        env.service.archiveInstead("c1")
        val change = env.pendingDao.rows.value.getValue("category" to "c1")
        assertFalse(change.isDelete)
        assertEquals(3L, change.baseVersion)
        assertEquals(ChangeState.Waiting.name, change.state)
        assertTrue(payloadOf(env).archive)
        assertTrue(env.categoryDao.rows.value.getValue("c1").inUse)
        assertFalse(env.service.observeCategory("c1").first()!!.canDelete(emptySet()))
    }

    @Test
    fun keepTheCategoryDropsTheDeletionAndMarksItInUse() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3))
        env.service.delete("c1")
        refuseDelete(env)
        env.service.keepCategory("c1")
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertTrue(env.categoryDao.rows.value.getValue("c1").inUse)
        assertEquals("Coffee", env.service.observeCategory("c1").first()!!.shown.name)
    }

    @Test
    fun aDeleteClashOffersDeleteAnywayOrKeepAndATombstoneOnlyAcceptance() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3))
        env.service.delete("c1")
        val change = env.pendingDao.rows.value.getValue("category" to "c1")
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("category" to "c1") to change.copy(
            state = ChangeState.Clash.name, clashJson = categoryJson(version = 5, name = "Web"),
        ))
        env.service.useTheirs("c1")
        assertEquals("Web", env.service.observeCategory("c1").first()!!.shown.name)
        assertEquals(emptyMap(), env.pendingDao.rows.value)

        env.service.delete("c1")
        val again = env.pendingDao.rows.value.getValue("category" to "c1")
        env.pendingDao.rows.value = env.pendingDao.rows.value + (("category" to "c1") to again.copy(
            state = ChangeState.Clash.name, clashJson = categoryJson(version = 6, deletedAt = "2026-10-01T00:00:00Z"),
        ))
        env.service.keepMine("c1")
        // A final deletion can't be undone: "keep mine" does nothing, only accepting works.
        assertEquals(ChangeState.Clash.name, env.pendingDao.rows.value.getValue("category" to "c1").state)
        env.service.useTheirs("c1")
        assertNull(env.categoryDao.rows.value["c1"])
    }
}
