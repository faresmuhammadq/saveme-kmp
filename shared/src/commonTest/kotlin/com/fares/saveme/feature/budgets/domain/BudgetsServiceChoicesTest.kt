package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetBody
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.CreateResult
import com.fares.saveme.feature.budgets.domain.model.RefusalKind
import com.fares.saveme.feature.budgets.domain.model.SyncState
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Clash and refusal choices (FR-007). */
class BudgetsServiceChoicesTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    private fun clashEnv(scope: kotlinx.coroutines.CoroutineScope, current: String) = BudgetTestEnv(scope) { request ->
        if (request.method == HttpMethod.Put && request.jsonBody()["version"].toString() == "3") {
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$current"""))
        } else {
            jsonResponse(200, budgetBody(budgetJson("b1", version = 7, amount = 700_000)))
        }
    }

    private suspend fun BudgetTestEnv.clash() {
        signedIn()
        seed(budgetJson("b1", amount = 600_000, version = 3))
        service.saveLimit("b1", 700_000, 3)
        engine.drain()
    }

    @Test
    fun aClashShowsTheOtherDevicesCopyAndKeepMineResendsOnTheNewerVersion() = runTest {
        val env = clashEnv(backgroundScope, budgetJson("b1", amount = 900_000, version = 6))
        env.clash()
        val view = env.service.observeBudget("b1").first()!!
        val state = assertIs<SyncState.Clash>(view.syncState)
        assertEquals(900_000, state.theirs!!.limitMinor)
        assertEquals(false, state.deletedElsewhere)
        env.service.keepMine("b1")
        advanceUntilIdle()
        env.engine.drain()
        val resent = env.requests.last()
        assertEquals("6", resent.jsonBody()["version"].toString(), "re-sent on the newer version")
        assertEquals("700000", resent.jsonBody()["amount_minor"].toString())
        assertNull(env.pendingDao.rows.value["budget" to "b1"])
    }

    @Test
    fun useTheirsAppliesTheOtherDevicesCopyAndDropsTheChange() = runTest {
        val env = clashEnv(backgroundScope, budgetJson("b1", amount = 900_000, version = 6))
        env.clash()
        env.service.useTheirs("b1")
        assertNull(env.pendingDao.rows.value["budget" to "b1"])
        assertEquals(900_000L, env.budgetDao.rows.value.getValue("b1").amountMinor)
        assertEquals(6L, env.budgetDao.rows.value.getValue("b1").version)
    }

    @Test
    fun aClashWithADeletionOnlyOffersToAcceptItAndAcceptingRemovesTheBudgetAndItsProgress() = runTest {
        val env = clashEnv(backgroundScope, budgetJson("b1", version = 6, deletedAt = "2026-10-01T00:00:00Z"))
        env.clash()
        env.seedProgress("b1")
        val state = assertIs<SyncState.Clash>(env.service.observeBudget("b1").first()!!.syncState)
        assertEquals(true, state.deletedElsewhere)
        assertNull(state.theirs)
        env.service.keepMine("b1")
        assertEquals("Clash", env.pendingDao.rows.value.getValue("budget" to "b1").state, "deletion is final: no restore")
        env.service.useTheirs("b1")
        assertNull(env.budgetDao.rows.value["b1"])
        assertNull(env.progressDao.rows.value["b1"])
        assertNull(env.service.observeBudget("b1").first())
    }

    @Test
    fun aRefusalShowsItsKindByCodeAndPointerAndTryAgainRequeues() = runTest {
        var refuse = true
        val env = BudgetTestEnv(backgroundScope) {
            if (refuse) {
                jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/category_id","code":"INVALID_ENUM_VALUE"}]"""))
            } else {
                jsonResponse(200, budgetBody(budgetJson("b1", version = 4, amount = 700_000)))
            }
        }
        env.signedIn()
        env.seed(budgetJson("b1", version = 3))
        env.service.saveLimit("b1", 700_000, 3)
        env.engine.drain()
        val refused = assertIs<SyncState.Refused>(env.service.observeBudget("b1").first()!!.syncState)
        assertEquals(RefusalKind.CategoryUnusable, refused.reason.kind)
        assertEquals(mapOf(BudgetField.Category to "INVALID_ENUM_VALUE"), refused.fields)
        refuse = false
        env.service.tryAgain("b1")
        advanceUntilIdle()
        env.engine.drain()
        assertNull(env.pendingDao.rows.value["budget" to "b1"])
        assertEquals(4L, env.budgetDao.rows.value.getValue("b1").version)
    }

    @Test
    fun discardingARefusedNeverSentBudgetRemovesItAndDiscardingASyncedOneShowsTheServerCopyAgain() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        val id = assertIs<CreateResult.Created>(env.service.create(BudgetDraft("c1", "EGP", 100))).id
        env.seed(budgetJson("b1", amount = 600_000, version = 3))
        env.service.saveLimit("b1", 700_000, 3)
        env.engine.drain()
        assertEquals(RefusalKind.NoLongerAvailable, assertIs<SyncState.Refused>(env.service.observeBudget(id).first()!!.syncState).reason.kind)
        env.service.discard(id)
        assertNull(env.budgetDao.rows.value[id], "a placeholder has no server copy to fall back to")
        env.service.discard("b1")
        assertEquals(600_000, env.service.observeBudget("b1").first()!!.limitMinor)
    }

    @Test
    fun anUnverifiedAnswerIsRetryableUnchanged() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "EMAIL_NOT_VERIFIED")) }
        env.signedIn()
        env.seed(budgetJson("b1", version = 3))
        env.service.saveLimit("b1", 700_000, 3)
        env.engine.drain()
        val refused = assertIs<SyncState.Refused>(env.service.observeBudget("b1").first()!!.syncState)
        assertEquals(RefusalKind.NotVerified, refused.reason.kind)
        assertTrue(refused.reason.canRetryUnchanged)
    }
}
