package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetAccount
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.data.BudgetPayload
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.CreateResult
import com.fares.saveme.feature.budgets.domain.model.SaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Limit edits, archive and delete queue with the base version and work offline (FR-005, FR-006). */
class BudgetsServiceChangeTest {

    private fun payload(env: BudgetTestEnv, id: String = "b1") =
        AppJson.decodeFromString(BudgetPayload.serializer(), env.pendingDao.rows.value.getValue("budget" to id).payloadJson)

    @Test
    fun aLimitSaveQueuesTheStoredCategoryAndCurrencyWithTheNewLimitOnTheShownVersion() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1", currency = "EGP", amount = 600_000, version = 3))
        assertEquals(SaveResult.Saved, env.service.saveLimit("b1", 700_000, baseVersion = 3))
        val change = env.pendingDao.rows.value.getValue("budget" to "b1")
        assertEquals(3L, change.baseVersion)
        assertEquals("user", change.scopeKind)
        assertEquals(BudgetPayload("c1", "EGP", 700_000, archive = false), payload(env))
        assertEquals(700_000, env.service.observeBudget("b1").first()!!.limitMinor, "the bar moves at once")
        assertTrue(env.runner.uploads > 0)
    }

    @Test
    fun aSecondEditKeepsTheOldestBaseVersion() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", version = 3))
        env.service.saveLimit("b1", 700_000, 3)
        env.service.saveLimit("b1", 800_000, 3)
        assertEquals(3L, env.pendingDao.rows.value.getValue("budget" to "b1").baseVersion)
        assertEquals(800_000L, payload(env).amountMinor)
    }

    @Test
    fun anArchivedBudgetsLimitStaysEditableAndIsNotArchivedAgain() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", archivedAt = "2026-09-20T00:00:00Z", version = 3))
        assertEquals(SaveResult.Saved, env.service.saveLimit("b1", 700_000, 3))
        assertFalse(payload(env).archive, "no second archive: it is already archived")
        assertEquals(listOf("b1"), env.service.observeOverview().first().archived.map { it.id })
    }

    @Test
    fun anInvalidLimitIsRefusedAndNothingIsQueued() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", version = 3))
        assertEquals(SaveResult.Invalid(mapOf(BudgetField.Limit to BudgetFieldProblem.LimitNotPositive)), env.service.saveLimit("b1", 0, 3))
        assertEquals(SaveResult.Invalid(mapOf(BudgetField.Limit to BudgetFieldProblem.LimitOutOfRange)), env.service.saveLimit("b1", BudgetMath.MAX_MINOR + 1, 3))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun archiveQueuesTheArchiveWithTheShownLimitAndMovesTheBudgetToArchived() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", amount = 600_000, version = 3))
        assertEquals(SaveResult.Saved, env.service.archive("b1"))
        assertEquals(BudgetPayload("c1", "EGP", 600_000, archive = true), payload(env))
        assertEquals(3L, env.pendingDao.rows.value.getValue("budget" to "b1").baseVersion)
        val overview = env.service.observeOverview().first()
        assertEquals(emptyList(), overview.active.map { it.id })
        assertEquals(listOf("b1"), overview.archived.map { it.id })
    }

    @Test
    fun archiveAfterAWaitingLimitEditIsOneChangeThatPutsThenArchives() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", amount = 600_000, version = 3))
        env.service.saveLimit("b1", 700_000, 3)
        env.service.archive("b1")
        assertEquals(1, env.pendingDao.rows.value.size)
        assertEquals(BudgetPayload("c1", "EGP", 700_000, archive = true), payload(env))
        // A later limit edit keeps the waiting archive.
        env.service.saveLimit("b1", 800_000, 3)
        assertEquals(BudgetPayload("c1", "EGP", 800_000, archive = true), payload(env))
    }

    @Test
    fun archivingTwiceOrAnArchivedBudgetQueuesNothingMore() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("a", archivedAt = "2026-09-20T00:00:00Z", version = 3))
        env.seed(budgetJson("b", categoryId = null, version = 3))
        assertEquals(SaveResult.Saved, env.service.archive("a"))
        assertNull(env.pendingDao.rows.value["budget" to "a"])
        env.service.archive("b")
        val first = env.pendingDao.rows.value.getValue("budget" to "b")
        assertEquals(SaveResult.Saved, env.service.archive("b"))
        assertEquals(first, env.pendingDao.rows.value.getValue("budget" to "b"))
    }

    @Test
    fun deleteQueuesTheDeletionWithTheVersionAndHidesTheBudget() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", version = 4))
        assertEquals(SaveResult.Saved, env.service.delete("b1"))
        val change = env.pendingDao.rows.value.getValue("budget" to "b1")
        assertTrue(change.isDelete)
        assertEquals(4L, change.baseVersion)
        assertNull(env.service.observeBudget("b1").first())
        assertEquals(emptyList(), env.service.observeOverview().first().active)
        assertTrue(env.runner.uploads > 0)
    }

    @Test
    fun aBudgetNeverSentIsRemovedWithNothingSent() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        val id = assertIs<CreateResult.Created>(env.service.create(BudgetDraft("c1", "EGP", 100))).id
        val uploadsBefore = env.runner.uploads
        assertEquals(SaveResult.Saved, env.service.delete(id))
        assertNull(env.budgetDao.rows.value[id])
        assertEquals(emptyMap(), env.pendingDao.rows.value, "the queued create is discarded with it")
        assertEquals(uploadsBefore, env.runner.uploads, "no upload is asked for")
        assertEquals(0, env.requests.size)
    }

    @Test
    fun anUnverifiedPersonQueuesNothingForAnyWrite() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", version = 3))
        env.authState.value = AuthState.SignedIn(budgetAccount.copy(emailVerified = false))
        assertEquals(SaveResult.NotVerified, env.service.saveLimit("b1", 700_000, 3))
        assertEquals(SaveResult.NotVerified, env.service.archive("b1"))
        assertEquals(SaveResult.NotVerified, env.service.delete("b1"))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun aBudgetThePhoneDoesNotHoldIsNoLongerAvailable() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        assertEquals(SaveResult.NoLongerAvailable, env.service.saveLimit("zz", 1, 1))
        assertEquals(SaveResult.NoLongerAvailable, env.service.archive("zz"))
        assertEquals(SaveResult.NoLongerAvailable, env.service.delete("zz"))
    }

    @Test
    fun aBudgetOfAnUnknownPeriodTypeIsReadOnly() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("w", periodType = "weekly", version = 3))
        assertEquals(SaveResult.ReadOnly, env.service.saveLimit("w", 1, 3))
        assertEquals(SaveResult.ReadOnly, env.service.archive("w"))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertNotNull(env.budgetDao.rows.value["w"])
    }
}
