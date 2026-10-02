package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.data.CapturedCategoryResponses as Captured
import com.fares.saveme.feature.categories.data.dto.PlatformCategoryDto
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryField
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CreateResult
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SaveResult
import com.fares.saveme.feature.categories.domain.model.SyncState
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The client's decoding and sync handling over responses captured from the real local backend
 * (spec 007 QA, qa-report.md "Contract QA"), rather than hand-written fixtures.
 */
class CategoryCapturedResponsesTest {

    private class Answer(var status: Int, var body: String)

    private fun env(scope: CoroutineScope, answer: Answer) =
        CategoryTestEnv(scope) { jsonResponse(answer.status, answer.body) }

    private val createdId = "01a0fcb1-0000-7000-8000-0000000000c1"
    private val inUseId = "01a0fcb1-0000-7000-8000-0000000000c3"
    private val typoId = "01a0fcb1-0000-7000-8000-0000000000c4"
    private val walletId = "01a0fcb1-0000-7000-8000-0000000000a1"

    @Test
    fun theRealDefaultSetDecodesWithEveryIconAndColourKnownAndTheRenamedHomeSupplies() = runTest {
        val env = env(backgroundScope, Answer(200, Captured.PLATFORM_CATEGORIES_200))
        env.signedIn()
        val set = assertIs<ApiResult.Success<Page<PlatformCategoryDto>>>(env.api.listDefaults()).value.items
        assertEquals(26, set.size)
        assertEquals(19, set.count { it.kind == "expense" && it.archivedAt == null })
        assertEquals(7, set.count { it.kind == "income" && it.archivedAt == null })
        val household = set.single { it.systemKey == "household" }
        assertEquals("Home supplies", household.names.en)
        assertEquals("مستلزمات البيت", household.names.ar)
        assertEquals("Groceries", set.first().names.en)
        set.forEach {
            assertNotNull(CategoryIcons.find(it.icon), "icon ${it.icon}")
            assertTrue(CategoryColors.isValid(it.color), "colour ${it.color}")
        }
        assertEquals(CategoryIcons.keys.toSet(), set.map { it.icon }.toSet())

        assertIs<ApiResult.Success<Unit>>(env.repository.refreshDefaults())
        env.language.value = com.fares.saveme.core.locale.AppLanguage.Ar
        val overview = env.service.observeOverview(CategoryKind.Expense).first { it.defaults.isNotEmpty() }
        assertEquals(19, overview.defaults.size)
        assertEquals("بقالة", overview.defaults.first().name)
    }

    @Test
    fun theRealListDecodesInUseAndArchivedThroughTheFeedApplier() = runTest {
        val env = env(backgroundScope, Answer(200, Captured.CATEGORIES_LIST_200))
        env.signedIn()
        // The list rows are the records a user feed carries; the applier stores each as the server copy.
        val records = com.fares.saveme.core.network.AppJson.parseToJsonElement(Captured.CATEGORIES_LIST_200).jsonObject.getValue("data").jsonArray
        CategoryFeedApplier(env.repository).apply(SyncScope(ScopeKind.User, "u1"), records)
        val used = env.categoryDao.rows.value.getValue(inUseId)
        assertTrue(used.inUse)
        val archived = env.categoryDao.rows.value.getValue(createdId)
        assertNotNull(archived.archivedAt)
        assertNull(archived.icon)
        assertEquals(records.size, env.categoryDao.rows.value.size)
    }

    @Test
    fun aCreateAnsweredWith201StoresTheServerCopyAndClearsTheQueue() = runTest {
        val env = env(backgroundScope, Answer(201, Captured.PUT_CREATED_201))
        env.signedIn()
        val created = assertIs<CreateResult.Created>(env.service.create(CategoryDraft("expense", "Coffee", "restaurant", "#0F766E")))
        env.engine.drain()
        assertTrue(env.pendingDao.rows.value.isEmpty())
        // The handler stores the copy the server answered with (its id is the captured one).
        assertEquals(1L, env.categoryDao.rows.value.getValue(createdId).version)
        assertNotNull(created.id)
    }

    @Test
    fun aStaleSaveBecomesAClashWithTheOtherDevicesValues() = runTest {
        val answer = Answer(201, Captured.PUT_CREATED_201)
        val env = env(backgroundScope, answer)
        env.signedIn()
        env.seed(Captured.PUT_CREATED_201.substringAfter("\"data\":").substringBeforeLast(",\"meta\""))
        assertEquals(SaveResult.Saved, env.service.save(createdId, CategoryDraft("expense", "Stale", null, null), baseVersion = 1))
        answer.status = 409
        answer.body = Captured.PUT_VERSION_CONFLICT_409
        env.engine.drain()
        val clash = assertIs<SyncState.Clash>(env.service.observeCategory(createdId).first()!!.syncState)
        assertFalse(clash.deletedElsewhere)
        assertEquals("Coffee beans", clash.theirs!!.name)
        assertEquals(2L, clash.theirs!!.version)

        // "Keep mine" resends on top of the other device's version.
        env.service.keepMine(createdId)
        assertEquals(2L, env.pendingDao.rows.value.getValue("category" to createdId).baseVersion)

        // The same answer clashes again; "Use the other device's" stores the server's copy and drops the change.
        env.engine.drain()
        assertIs<SyncState.Clash>(env.service.observeCategory(createdId).first()!!.syncState)
        env.service.useTheirs(createdId)
        assertTrue(env.pendingDao.rows.value.isEmpty())
        val shown = env.service.observeCategory(createdId).first()!!
        assertEquals("Coffee beans", shown.shown.name)
        assertEquals(2L, shown.server.version)
    }

    @Test
    fun aSaveAfterADeletionElsewhereOffersOnlyAcceptingIt() = runTest {
        val answer = Answer(409, Captured.PUT_AFTER_DELETE_CONFLICT_409)
        val env = env(backgroundScope, answer)
        env.signedIn()
        env.seed(Captured.PUT_UNKNOWN_ICON_KEY_201.substringAfter("\"data\":").substringBeforeLast(",\"meta\""))
        assertEquals(SaveResult.Saved, env.service.save(typoId, CategoryDraft("income", "Back", null, null), baseVersion = 1))
        env.engine.drain()
        val clash = assertIs<SyncState.Clash>(env.service.observeCategory(typoId).first()!!.syncState)
        assertTrue(clash.deletedElsewhere)
    }

    @Test
    fun anUnknownIconKeyTheServerAcceptedIsKeptOnSave() = runTest {
        val answer = Answer(200, Captured.PUT_UNKNOWN_ICON_KEY_201)
        val env = env(backgroundScope, answer)
        env.signedIn()
        env.seed(Captured.PUT_UNKNOWN_ICON_KEY_201.substringAfter("\"data\":").substringBeforeLast(",\"meta\""))
        val stored = env.service.observeCategory(typoId).first()!!.shown
        assertEquals("zzz_unknown", stored.icon)
        assertNull(CategoryIcons.find(stored.icon))
        env.service.save(typoId, stored.copy(name = "Typo 2"), baseVersion = 1)
        env.engine.drain()
        val body = env.requests.last { it.method == HttpMethod.Put }.jsonBody()
        assertEquals("\"zzz_unknown\"", body["icon"].toString())
        assertEquals("\"#ABCDEF\"", body["color"].toString())
    }

    @Test
    fun realFieldRefusalsLandOnTheirFields() = runTest {
        val answer = Answer(422, Captured.PUT_KIND_IMMUTABLE_422)
        val env = env(backgroundScope, answer)
        env.signedIn()
        env.seed(Captured.PUT_CREATED_201.substringAfter("\"data\":").substringBeforeLast(",\"meta\""))
        env.service.save(createdId, CategoryDraft("expense", "Coffee beans", null, null), baseVersion = 1)
        env.engine.drain()
        var refused = assertIs<SyncState.Refused>(env.service.observeCategory(createdId).first()!!.syncState)
        assertEquals(RefusalReason.Invalid, refused.reason)
        assertEquals(mapOf(CategoryField.Kind to "IMMUTABLE_FIELD"), refused.fields)

        for ((body, expected) in listOf(
            Captured.PUT_NAME_TOO_LONG_422 to mapOf(CategoryField.Name to "OUT_OF_RANGE"),
            Captured.PUT_ICON_COLOR_INVALID_TYPE_422 to mapOf(CategoryField.Icon to "INVALID_TYPE", CategoryField.Color to "INVALID_TYPE"),
            Captured.PUT_UNKNOWN_FIELD_422 to emptyMap(),
        )) {
            env.service.discard(createdId)
            env.service.save(createdId, CategoryDraft("expense", "Coffee beans 2", null, null), baseVersion = 1)
            answer.body = body
            env.engine.drain()
            refused = assertIs<SyncState.Refused>(env.service.observeCategory(createdId).first()!!.syncState)
            assertEquals(RefusalReason.Invalid, refused.reason)
            assertEquals(expected, refused.fields)
        }
    }

    @Test
    fun anUnverifiedAddressAndAnInvalidIdAreRefusalsWithTheirReasons() = runTest {
        val answer = Answer(403, Captured.PUT_EMAIL_NOT_VERIFIED_403)
        val env = env(backgroundScope, answer)
        env.signedIn()
        env.seed(Captured.PUT_CREATED_201.substringAfter("\"data\":").substringBeforeLast(",\"meta\""))
        env.service.save(createdId, CategoryDraft("expense", "Renamed", null, null), baseVersion = 1)
        env.engine.drain()
        assertEquals(RefusalReason.NotVerified, assertIs<SyncState.Refused>(env.service.observeCategory(createdId).first()!!.syncState).reason)

        env.service.discard(createdId)
        env.service.save(createdId, CategoryDraft("expense", "Renamed again", null, null), baseVersion = 1)
        answer.status = 422
        answer.body = Captured.PUT_INVALID_ID_422
        env.engine.drain()
        val other = assertIs<SyncState.Refused>(env.service.observeCategory(createdId).first()!!.syncState).reason
        assertEquals(RefusalReason.Other("01a0fcb0-5ee3-70c8-917d-06f183335db9"), other)
    }

    @Test
    fun anInUseDeletionComesBackMarkedNotDeleted() = runTest {
        val answer = Answer(409, Captured.DELETE_CATEGORY_IN_USE_409)
        val env = env(backgroundScope, answer)
        env.signedIn()
        env.seed(
            """{"id":"$inUseId","user_id":"u1","version":1,"change_seq":6,"created_at":"2026-10-02T12:56:55.5Z","updated_at":"2026-10-02T12:56:55.5Z","deleted_at":null,"kind":"expense","name":"Used","icon":null,"color":null,"archived_at":null,"in_use":false}""",
        )
        assertEquals(SaveResult.Saved, env.service.delete(inUseId))
        env.engine.drain()
        val view = assertNotNull(env.service.observeCategory(inUseId).first())
        assertEquals(RefusalReason.InUse, assertIs<SyncState.Refused>(view.syncState).reason)
        val failure = assertIs<ApiResult.Failure>(env.api.delete(inUseId, 1))
        assertEquals(ApiErrorCode.CATEGORY_IN_USE, failure.code)
    }

    @Test
    fun aConfirmedDeletionRemovesTheCategoryAndAStaleOneClashes() = runTest {
        val answer = Answer(200, Captured.DELETE_TOMBSTONE_200)
        val env = env(backgroundScope, answer)
        env.signedIn()
        env.seed(Captured.PUT_UNKNOWN_ICON_KEY_201.substringAfter("\"data\":").substringBeforeLast(",\"meta\""))
        env.service.delete(typoId)
        env.engine.drain()
        assertNull(env.categoryDao.rows.value[typoId])
        assertTrue(env.pendingDao.rows.value.isEmpty())

        env.seed(Captured.PUT_UNKNOWN_ICON_KEY_201.substringAfter("\"data\":").substringBeforeLast(",\"meta\""))
        env.service.delete(typoId)
        answer.status = 409
        answer.body = Captured.DELETE_VERSION_CONFLICT_409
        env.engine.drain()
        val view = assertNotNull(env.service.observeCategory(typoId).first())
        assertIs<SyncState.Clash>(view.syncState)
    }

    @Test
    fun anArchiveAnswerCarriesTheArchivedInstant() = runTest {
        val env = env(backgroundScope, Answer(200, Captured.ARCHIVE_200))
        env.signedIn()
        val dto = assertIs<ApiResult.Success<com.fares.saveme.feature.categories.data.dto.CategoryDto>>(env.api.archive(createdId, 2)).value
        assertEquals(3L, dto.version)
        assertNotNull(dto.archivedAt)
    }

    @Test
    fun aWalletsReferencesAreStoredFromItsFeedRows() = runTest {
        val env = env(backgroundScope, Answer(200, Captured.CATEGORY_REFERENCES_200))
        env.signedIn()
        val records = com.fares.saveme.core.network.AppJson.parseToJsonElement(Captured.CATEGORY_REFERENCES_200).jsonObject.getValue("data").jsonArray
        CategoryReferenceFeedApplier(env.references).apply(SyncScope(ScopeKind.Wallet, walletId), records)
        val stored = env.referenceDao.rows.value.single()
        assertEquals(inUseId, stored.categoryId)
        assertEquals("Used", stored.name)
    }
}
