package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.data.CapturedBudgetResponses as C
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import com.fares.saveme.feature.budgets.data.dto.BudgetProgressDto
import com.fares.saveme.feature.budgets.data.dto.BudgetWriteRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live backend's budget answers (spec 012 contract QA, [CapturedBudgetResponses]) through the real [BudgetApi]: the
 * body the client builds equals the body that was sent live, and every real answer decodes to the handling the spec asks for.
 */
class BudgetCapturedContractTest {

    private fun parse(json: String): JsonObject = AppJson.parseToJsonElement(json).jsonObject

    private suspend fun <T> BudgetTestEnv.call(status: Int, body: String, block: suspend (BudgetApi) -> T): Pair<T, JsonObject?> {
        val answer = block(api)
        val sent = requests.last().let { runCatching { it.jsonBody() }.getOrNull() }
        return answer to sent
    }

    private fun env(scope: kotlinx.coroutines.CoroutineScope, status: Int, body: String) = BudgetTestEnv(scope) { jsonResponse(status, body) }

    private fun failure(result: ApiResult<*>): ApiResult.Failure = assertIs<ApiResult.Failure>(result)

    private fun errors(f: ApiResult.Failure) = f.problem.errors.orEmpty().map { it.pointer to it.code }

    @Test
    fun aCreateWithACategoryAndAnAllExpensesCreateSendTheLiveBodiesAndStoreTheLiveCopies() = runTest {
        val categoryEnv = env(backgroundScope, 201, C.PUT_CREATED_CATEGORY_201).also { it.signedIn() }
        val (created, sent) = categoryEnv.call(201, "") { it.put(C.BUDGET_GROCERIES, BudgetWriteRequest(C.CATEGORY_GROCERIES, "monthly", 600_000, "EGP", 0)) }
        assertEquals(parse(C.PUT_CREATED_CATEGORY_201_REQUEST), sent)
        val dto = assertIs<ApiResult.Success<BudgetDto>>(created).value
        assertEquals(1L, dto.version)
        assertEquals(C.CATEGORY_GROCERIES, dto.categoryId)

        val allEnv = env(backgroundScope, 201, C.PUT_CREATED_ALL_EXPENSES_201).also { it.signedIn() }
        val (all, allSent) = allEnv.call(201, "") { it.put(C.BUDGET_ALL, BudgetWriteRequest(null, "monthly", 1_000_000, "EGP", 0)) }
        assertEquals(parse(C.PUT_CREATED_ALL_EXPENSES_201_REQUEST), allSent)
        assertTrue(allSent!!["category_id"].toString() == "null", "an explicit null on the wire")
        assertNull(assertIs<ApiResult.Success<BudgetDto>>(all).value.categoryId)
    }

    @Test
    fun anIdenticalReplayIs200AndALimitEditIsASaveOnTheShownVersion() = runTest {
        val replay = env(backgroundScope, 200, C.PUT_REPLAY_200).also { it.signedIn() }
        val (r, sent) = replay.call(200, "") { it.put(C.BUDGET_GROCERIES, BudgetWriteRequest(C.CATEGORY_GROCERIES, "monthly", 600_000, "EGP", 0)) }
        assertEquals(parse(C.PUT_REPLAY_200_REQUEST), sent)
        assertEquals(1L, assertIs<ApiResult.Success<BudgetDto>>(r).value.version)

        val edit = env(backgroundScope, 200, C.PUT_LIMIT_EDIT_200).also { it.signedIn() }
        val (e, editSent) = edit.call(200, "") { it.put(C.BUDGET_GROCERIES, BudgetWriteRequest(C.CATEGORY_GROCERIES, "monthly", 650_000, "EGP", 1)) }
        assertEquals(parse(C.PUT_LIMIT_EDIT_200_REQUEST), editSent)
        val saved = assertIs<ApiResult.Success<BudgetDto>>(e).value
        assertEquals(2L, saved.version)
        assertEquals(650_000L, saved.amountMinor)
    }

    @Test
    fun aStaleSaveAndAStaleCreateAreVersionConflictsCarryingTheLiveCurrentCopy() = runTest {
        for ((body, request, version) in listOf(Triple(C.PUT_STALE_CREATE_409, C.PUT_STALE_CREATE_409_REQUEST, 0L), Triple(C.PUT_STALE_LIMIT_409, C.PUT_STALE_LIMIT_409_REQUEST, 1L))) {
            val env = env(backgroundScope, 409, body).also { it.signedIn() }
            val (result, sent) = env.call(409, "") { it.put(C.BUDGET_GROCERIES, BudgetWriteRequest(C.CATEGORY_GROCERIES, "monthly", if (version == 0L) 700_000 else 660_000, "EGP", version)) }
            assertEquals(parse(request), sent)
            val f = failure(result)
            assertEquals(ApiErrorCode.VERSION_CONFLICT, f.code)
            val current = AppJson.decodeFromJsonElement(BudgetDto.serializer(), f.problem.current!!)
            assertEquals(version + 1, current.version, "the live copy is the one the stale write missed")
            assertNull(current.deletedAt)
        }
    }

    @Test
    fun everyRealRefusalKeepsItsPointerAndCode() = runTest {
        val cases = listOf(
            Triple(C.PUT_IMMUTABLE_CATEGORY_422, C.PUT_IMMUTABLE_CATEGORY_422_REQUEST, listOf("/category_id" to "IMMUTABLE_FIELD")),
            Triple(C.PUT_IMMUTABLE_CATEGORY_TO_NULL_422, C.PUT_IMMUTABLE_CATEGORY_TO_NULL_422_REQUEST, listOf("/category_id" to "IMMUTABLE_FIELD")),
            Triple(C.PUT_IMMUTABLE_CURRENCY_422, C.PUT_IMMUTABLE_CURRENCY_422_REQUEST, listOf("/currency" to "IMMUTABLE_FIELD")),
            Triple(C.PUT_PERIOD_TYPE_422, C.PUT_PERIOD_TYPE_422_REQUEST, listOf("/period_type" to "INVALID_ENUM_VALUE")),
            Triple(C.PUT_INCOME_CATEGORY_422, C.PUT_INCOME_CATEGORY_422_REQUEST, listOf("/category_id" to "INVALID_ENUM_VALUE")),
            Triple(C.PUT_LIMIT_ZERO_422, C.PUT_LIMIT_ZERO_422_REQUEST, listOf("/amount_minor" to "OUT_OF_RANGE")),
            Triple(C.PUT_LIMIT_TOO_LARGE_422, C.PUT_LIMIT_TOO_LARGE_422_REQUEST, listOf("/amount_minor" to "OUT_OF_RANGE")),
            Triple(C.PUT_CURRENCY_INVALID_422, C.PUT_CURRENCY_INVALID_422_REQUEST, listOf("/currency" to "INVALID_CURRENCY")),
            Triple(C.PUT_UNKNOWN_CATEGORY_422, C.PUT_UNKNOWN_CATEGORY_422_REQUEST, listOf("/category_id" to "INVALID_ENUM_VALUE")),
        )
        for ((body, request, expected) in cases) {
            val env = env(backgroundScope, 422, body).also { it.signedIn() }
            val sentBody = parse(request)
            val write = BudgetWriteRequest(
                sentBody["category_id"]?.toString()?.trim('"')?.takeIf { it != "null" },
                sentBody.getValue("period_type").toString().trim('"'),
                sentBody.getValue("amount_minor").toString().toLong(),
                sentBody.getValue("currency").toString().trim('"'),
                sentBody.getValue("version").toString().toLong(),
            )
            val (result, sent) = env.call(422, "") { it.put("b", write) }
            assertEquals(sentBody, sent)
            val f = failure(result)
            assertEquals("VALIDATION_FAILED", f.problem.code)
            assertEquals(expected, errors(f))
        }
    }

    @Test
    fun anUnverifiedAddressAnotherPersonsBudgetAndAMissingRecordAreReportedByCode() = runTest {
        val unverified = env(backgroundScope, 403, C.PUT_UNVERIFIED_403).also { it.signedIn() }
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, failure(unverified.api.put("b", BudgetWriteRequest("c", "monthly", 1000, "EGP", 0))).code)
        val archiveUnverified = env(backgroundScope, 403, C.ARCHIVE_UNVERIFIED_403).also { it.signedIn() }
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, failure(archiveUnverified.api.archive("b", 1)).code)
        val deleteUnverified = env(backgroundScope, 403, C.DELETE_UNVERIFIED_403).also { it.signedIn() }
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, failure(deleteUnverified.api.delete("b", 2)).code)
        val other = env(backgroundScope, 404, C.PUT_OTHER_PERSON_404).also { it.signedIn() }
        assertEquals(ApiErrorCode.NOT_FOUND, failure(other.api.put("b", BudgetWriteRequest("c", "monthly", 1000, "EGP", 1))).code)
        val missing = env(backgroundScope, 404, C.PUT_NO_RECORD_404).also { it.signedIn() }
        assertEquals(ApiErrorCode.NOT_FOUND, failure(missing.api.put("b", BudgetWriteRequest("c", "monthly", 1000, "EGP", 3))).code)
        val invalidId = env(backgroundScope, 422, C.PUT_INVALID_ID_422).also { it.signedIn() }
        assertEquals("INVALID_ID", failure(invalidId.api.put("nope", BudgetWriteRequest("c", "monthly", 1000, "EGP", 0))).problem.code)
        val unknownMember = env(backgroundScope, 422, C.PUT_UNKNOWN_MEMBER_422).also { it.signedIn() }
        assertEquals(listOf("/user_id" to "UNKNOWN_FIELD"), errors(failure(unknownMember.api.put("b", BudgetWriteRequest("c", "monthly", 1000, "EGP", 0)))))
    }

    @Test
    fun theLargestLimitIsAcceptedAndComesBackUnrounded() = runTest {
        val env = env(backgroundScope, 201, C.PUT_LIMIT_MAX_201).also { it.signedIn() }
        val (result, sent) = env.call(201, "") { it.put(C.BUDGET_MAX, BudgetWriteRequest(null, "monthly", 9_007_199_254_740_991, "USD", 0)) }
        assertEquals(parse(C.PUT_LIMIT_MAX_201_REQUEST), sent)
        assertEquals(9_007_199_254_740_991, assertIs<ApiResult.Success<BudgetDto>>(result).value.amountMinor)
    }

    @Test
    fun archiveIsIdempotentAndALimitCanBeEditedAfterwards() = runTest {
        val archived = env(backgroundScope, 200, C.ARCHIVE_200).also { it.signedIn() }
        val (a, sent) = archived.call(200, "") { it.archive(C.BUDGET_USD_ARCHIVED, 1) }
        assertEquals(parse(C.ARCHIVE_200_REQUEST), sent)
        val first = assertIs<ApiResult.Success<BudgetDto>>(a).value
        assertTrue(first.archivedAt != null)
        val again = env(backgroundScope, 200, C.ARCHIVE_AGAIN_200).also { it.signedIn() }
        val second = assertIs<ApiResult.Success<BudgetDto>>(again.api.archive(C.BUDGET_USD_ARCHIVED, first.version)).value
        assertEquals(first.version, second.version, "an already archived budget is returned unchanged")
        val stale = env(backgroundScope, 200, C.ARCHIVE_STALE_ON_ARCHIVED_200).also { it.signedIn() }
        assertEquals(first.version, assertIs<ApiResult.Success<BudgetDto>>(stale.api.archive(C.BUDGET_USD_ARCHIVED, 1)).value.version)
        val edit = env(backgroundScope, 200, C.PUT_LIMIT_AFTER_ARCHIVE_200).also { it.signedIn() }
        val edited = assertIs<ApiResult.Success<BudgetDto>>(edit.api.put(C.BUDGET_USD_ARCHIVED, BudgetWriteRequest(C.CATEGORY_GROCERIES, "monthly", 60_000, "USD", first.version))).value
        assertEquals(60_000L, edited.amountMinor)
        assertTrue(edited.archivedAt != null, "archive is one-way")
    }

    @Test
    fun deleteSendsAJsonBodyAndATombstoneIsFinal() = runTest {
        val deleted = env(backgroundScope, 200, C.DELETE_200).also { it.signedIn() }
        val (d, sent) = deleted.call(200, "") { it.delete(C.BUDGET_DELETED, 1) }
        assertEquals(parse(C.DELETE_200_REQUEST), sent)
        assertTrue(assertIs<ApiResult.Success<BudgetDto>>(d).value.deletedAt != null)
        val again = env(backgroundScope, 200, C.DELETE_AGAIN_200).also { it.signedIn() }
        assertTrue(assertIs<ApiResult.Success<BudgetDto>>(again.api.delete(C.BUDGET_DELETED, 1)).value.deletedAt != null)
        for (body in listOf(C.PUT_ON_TOMBSTONE_409, C.ARCHIVE_ON_TOMBSTONE_409)) {
            val env = env(backgroundScope, 409, body).also { it.signedIn() }
            val f = failure(env.api.put(C.BUDGET_DELETED, BudgetWriteRequest(C.CATEGORY_TRANSPORT, "monthly", 2000, "EGP", 1)))
            assertEquals(ApiErrorCode.VERSION_CONFLICT, f.code)
            assertTrue(AppJson.decodeFromJsonElement(BudgetDto.serializer(), f.problem.current!!).deletedAt != null, "deleted elsewhere")
        }
        val stale = env(backgroundScope, 409, C.DELETE_STALE_409).also { it.signedIn() }
        assertTrue(AppJson.decodeFromJsonElement(BudgetDto.serializer(), failure(stale.api.delete(C.BUDGET_GROCERIES, 1)).problem.current!!).deletedAt == null)
    }

    @Test
    fun theLiveProgressListHasNoPercentageAndDecodesEveryMemberTheClientUses() = runTest {
        val env = env(backgroundScope, 200, C.PROGRESS_A_200).also { it.signedIn() }
        val page = assertIs<ApiResult.Success<Page<BudgetProgressDto>>>(env.api.progressPage(null)).value
        assertEquals(5, page.items.size)
        assertTrue("percent" !in C.PROGRESS_A_200 && "percentage" !in C.PROGRESS_A_200, "the server sends no share (FR-013)")
        val groceries = page.items.single { it.budgetId == C.BUDGET_GROCERIES }
        assertEquals(70_550, groceries.spent.amountMinor)
        assertEquals(listOf("USD"), groceries.otherCurrencies)
        assertEquals(5, groceries.period.daysElapsed)
        assertEquals(setOf("limit"), env.requests.single().url.parameters.names())
        assertTrue(page.items.none { it.budgetId == C.BUDGET_USD_ARCHIVED }, "an archived budget is absent from the list")
    }

    @Test
    fun theProgressTypeHasNoPercentageMemberSoAServerShareIsNeverRead() {
        val names = (0 until BudgetProgressDto.serializer().descriptor.elementsCount).map { BudgetProgressDto.serializer().descriptor.getElementName(it) }
        assertTrue(names.none { "percent" in it }, "$names")
        val withShare = AppJson.decodeFromString(BudgetProgressDto.serializer(), parse(C.PROGRESS_A_200).getValue("data").toString().let { AppJson.parseToJsonElement(it).toString().removePrefix("[").removeSuffix("]").substringBefore("},{\"budget_id\"") + "}" }.replace("\"over_limit\"", "\"percentage\":42,\"over_limit\""))
        assertEquals(C.BUDGET_GROCERIES, withShare.budgetId)
    }

    @Test
    fun theLiveLimitOnePagesAreFollowedByTheirCursorToTheEndAndTheWalkDecodesEveryPage() = runTest {
        val pages = listOf(C.PROGRESS_A_LIMIT1_PAGE1, C.PROGRESS_A_LIMIT1_PAGE2, C.PROGRESS_A_LIMIT1_PAGE3, C.PROGRESS_A_LIMIT1_PAGE4, C.PROGRESS_A_LIMIT1_PAGE5)
        var n = 0
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, pages[n++]) }
        env.signedIn()
        var cursor: String? = null
        val seen = mutableListOf<String>()
        do {
            val page = assertIs<ApiResult.Success<Page<BudgetProgressDto>>>(env.api.progressPage(cursor)).value
            seen += page.items.map { it.budgetId }
            cursor = page.nextCursor
        } while (cursor != null)
        assertEquals(5, seen.toSet().size)
        assertEquals(5, n)
    }

    @Test
    fun anInvalidCursorIsReportedByCodeAndAProgressOfOneBudgetIs404WhenNotTheirs() = runTest {
        val invalid = env(backgroundScope, 400, C.PROGRESS_CURSOR_OF_ANOTHER_PERIOD_400).also { it.signedIn() }
        assertEquals(ApiErrorCode.INVALID_CURSOR, failure(invalid.api.progressPage("x")).code)
        val garbage = env(backgroundScope, 400, C.PROGRESS_CURSOR_GARBAGE_400).also { it.signedIn() }
        assertEquals(ApiErrorCode.INVALID_CURSOR, failure(garbage.api.progressPage("garbage")).code)
        for (body in listOf(C.PROGRESS_ONE_DELETED_404, C.PROGRESS_ONE_OTHER_PERSON_404)) {
            val env = env(backgroundScope, 404, body).also { it.signedIn() }
            assertEquals(ApiErrorCode.NOT_FOUND, failure(env.api.progressOne("b")).code)
        }
        val archived = env(backgroundScope, 200, C.PROGRESS_ONE_ARCHIVED_200).also { it.signedIn() }
        val one = assertIs<ApiResult.Success<BudgetProgressDto>>(archived.api.progressOne(C.BUDGET_USD_ARCHIVED)).value
        assertEquals(C.BUDGET_USD_ARCHIVED, one.budgetId)
        assertEquals(setOf<String>(), archived.requests.single().url.parameters.names())
    }

    @Test
    fun aCursorSentWithoutADateFollowsThePeriodItWasIssuedFor() = runTest {
        // Observed live: the period is carried inside the cursor; the client only ever walks the period it started in.
        val env = env(backgroundScope, 200, C.PROGRESS_CURSOR_ALONE_FOLLOWS_ITS_PERIOD_200).also { it.signedIn() }
        val page = assertIs<ApiResult.Success<Page<BudgetProgressDto>>>(env.api.progressPage("c")).value
        assertEquals(false, page.items.single().isCurrent)
    }
}
