package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryFieldProblem
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CreateResult
import com.fares.saveme.feature.categories.domain.model.SaveResult
import com.fares.saveme.feature.categories.platformJson
import com.fares.saveme.feature.categories.signedInAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CategoriesServiceCreateSaveTest {
    private val draft = CategoryDraft("expense", "  Coffee  ", "restaurant", "#1F7F8C")

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = CategoryTestEnv(scope) { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }

    @Test
    fun anUnverifiedPersonQueuesNothing() = runTest {
        val env = env(backgroundScope)
        env.authState.value = AuthState.SignedIn(signedInAccount.copy(emailVerified = false))
        assertEquals(CreateResult.NotVerified, env.service.create(draft))
        env.seed(categoryJson(version = 2))
        assertEquals(SaveResult.NotVerified, env.service.save("c1", draft, 2))
        assertEquals(SaveResult.NotVerified, env.service.archive("c1").let { if (it == SaveResult.NotVerified) it else it })
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(1, env.categoryDao.rows.value.size)
    }

    @Test
    fun aValidCreateSavesAPlaceholderAndQueuesVersionZeroInTheUserScope() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val id = assertIs<CreateResult.Created>(env.service.create(draft)).id
        assertEquals("c-new-1", id)
        val row = assertNotNull(env.categoryDao.rows.value[id])
        assertEquals("Coffee", row.name)
        assertEquals(0L, row.version)
        val change = assertNotNull(env.pendingDao.rows.value["category" to id])
        assertEquals(0L, change.baseVersion)
        assertEquals(ScopeKind.User.wire, change.scopeKind)
        assertEquals("u1", change.scopeId)
        val payload = AppJson.decodeFromString(CategoryPayload.serializer(), change.payloadJson)
        assertEquals("Coffee", payload.name)
        assertEquals("restaurant", payload.icon)
        assertEquals(false, payload.archive)
        assertTrue(env.scheduler.enqueued.isNotEmpty())
    }

    @Test
    fun newCategoriesCarryAClientMadeUuidV7() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val service = DefaultCategoriesService(
            env.repository, env.references, env.engine, env.authState, NoPendingCategoryUses, env.language, backgroundScope, env.runner,
        )
        val id = assertIs<CreateResult.Created>(service.create(draft)).id
        assertEquals(36, id.length)
        assertEquals('7', id[14], "UUID version nibble of $id")
        assertNotNull(env.categoryDao.rows.value[id])
    }

    @Test
    fun blankAndTooLongNamesAreRefusedAndFiftyCodePointsWithAnEmojiAreAccepted() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        assertEquals(
            CreateResult.Invalid(mapOf(CategoryField.Name to CategoryFieldProblem.NameBlank)),
            env.service.create(draft.copy(name = "   ")),
        )
        assertEquals(
            CreateResult.Invalid(mapOf(CategoryField.Name to CategoryFieldProblem.NameTooLong)),
            env.service.create(draft.copy(name = "x".repeat(51))),
        )
        // 49 letters and one emoji: 50 code points, 51 UTF-16 units.
        assertIs<CreateResult.Created>(env.service.create(draft.copy(name = "x".repeat(49) + "😀")))
        assertEquals(1, env.categoryDao.rows.value.size)
    }

    @Test
    fun anUnknownKindIsRefusedOnCreate() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        assertEquals(
            CreateResult.Invalid(mapOf(CategoryField.Kind to CategoryFieldProblem.Server("INVALID_ENUM_VALUE"))),
            env.service.create(draft.copy(kind = "transfer")),
        )
    }

    @Test
    fun duplicateNamesAreAccepted() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        assertIs<CreateResult.Created>(env.service.create(draft))
        assertIs<CreateResult.Created>(env.service.create(draft))
        assertEquals(2, env.categoryDao.rows.value.size)
    }

    @Test
    fun saveUsesTheEditorsBaseVersionAndTheStoredKind() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3, kind = "expense"))
        assertEquals(SaveResult.Saved, env.service.save("c1", draft.copy(kind = "income", name = "Beans"), baseVersion = 2))
        val change = env.pendingDao.rows.value.getValue("category" to "c1")
        assertEquals(2L, change.baseVersion)
        val payload = AppJson.decodeFromString(CategoryPayload.serializer(), change.payloadJson)
        assertEquals("expense", payload.kind)
        assertEquals("Beans", payload.name)
    }

    @Test
    fun anEditKeepsAWaitingArchive() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3))
        env.service.archive("c1")
        env.service.save("c1", draft.copy(name = "Beans"), 3)
        val payload = AppJson.decodeFromString(CategoryPayload.serializer(), env.pendingDao.rows.value.getValue("category" to "c1").payloadJson)
        assertTrue(payload.archive)
        assertEquals("Beans", payload.name)
    }

    /** Edge case: an archived category stays editable, its edit is queued, and it stays archived and out of the choice. */
    @Test
    fun anArchivedCategoryStaysEditableArchivedAndOutOfTheChoice() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 4, archivedAt = "2026-09-05T00:00:00Z"))
        assertEquals(SaveResult.Saved, env.service.save("c1", CategoryDraft("expense", "Old coffee", null, null), 4))
        val change = env.pendingDao.rows.value.getValue("category" to "c1")
        assertEquals(4L, change.baseVersion)
        assertEquals("Old coffee", AppJson.decodeFromString(CategoryPayload.serializer(), change.payloadJson).name)
        val view = env.service.observeCategory("c1").first()!!
        assertTrue(view.archived)
        assertEquals("Old coffee", view.shown.name)
        val choices = env.service.observeChoices(CategoryKind.Expense).first()
        assertTrue(choices.own.none { it.id == "c1" })
        assertEquals("c1", env.service.observeChoices(CategoryKind.Expense, currentCategoryId = "c1").first().current?.id)
    }

    @Test
    fun anUnknownIconAndColourArePreserved() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson(version = 3, icon = "rocket", color = "#123456"))
        env.service.save("c1", CategoryDraft("expense", "Renamed", "rocket", "#123456"), 3)
        val payload = AppJson.decodeFromString(CategoryPayload.serializer(), env.pendingDao.rows.value.getValue("category" to "c1").payloadJson)
        assertEquals("rocket", payload.icon)
        assertEquals("#123456", payload.color)
    }

    @Test
    fun aDefaultsIdIsNeverQueued() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seedDefaults(platformJson("d1"))
        assertEquals(SaveResult.NoLongerAvailable, env.service.save("d1", draft, 0))
        assertEquals(SaveResult.NoLongerAvailable, env.service.archive("d1"))
        assertEquals(SaveResult.NoLongerAvailable, env.service.delete("d1"))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun categoriesAreNotGatedByThePlan() = runTest {
        // The service takes no entitlements: a free-plan account creates and saves like any other (FR-022).
        val env = env(backgroundScope)
        env.authState.value = AuthState.SignedIn(signedInAccount.copy(plan = "free"))
        env.signedIn()
        repeat(30) { assertIs<CreateResult.Created>(env.service.create(draft)) }
        assertEquals(30, env.categoryDao.rows.value.size)
    }
}
