package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetAccount
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.data.BudgetPayload
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.CreateResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Create works offline, queues in the user scope at version 0 and refuses what the server would (FR-001 to FR-004). */
class BudgetsServiceCreateTest {

    private val draft = BudgetDraft("c1", "EGP", 600_000)

    @Test
    fun aValidCreateWritesAPlaceholderAndQueuesOneChangeAtVersionZeroInTheUserScope() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        val id = assertIs<CreateResult.Created>(env.service.create(draft)).id
        assertEquals("b-new-1", id)
        val row = assertNotNull(env.budgetDao.rows.value[id])
        assertEquals(0L, row.version)
        assertEquals(600_000L, row.amountMinor)
        assertEquals("c1", row.categoryId)
        assertEquals("monthly", row.periodType)
        val change = assertNotNull(env.pendingDao.rows.value["budget" to id])
        assertEquals(1, env.pendingDao.rows.value.size)
        assertEquals(0L, change.baseVersion)
        assertEquals(ScopeKind.User.wire, change.scopeKind)
        assertEquals("u1", change.scopeId)
        val payload = AppJson.decodeFromString(BudgetPayload.serializer(), change.payloadJson)
        assertEquals(BudgetPayload("c1", "EGP", 600_000, archive = false), payload)
        assertTrue(env.scheduler.enqueued.isNotEmpty())
        assertTrue(env.runner.uploads > 0, "the upload is asked for at once")
    }

    @Test
    fun allExpensesIsAnExplicitNullCategory() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        val id = assertIs<CreateResult.Created>(env.service.create(BudgetDraft(null, "EGP", 1))).id
        assertEquals(null, env.budgetDao.rows.value.getValue(id).categoryId)
    }

    @Test
    fun newBudgetsCarryAClientMadeUuidV7() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        val service = DefaultBudgetsService(
            repository = env.repository, progress = env.progress, api = env.api, engine = { env.engine }, authState = env.authState,
            categories = env.categories, wallets = env.wallets, spending = env.spending, profile = env.profile,
            deviceZoneId = { "UTC" }, runner = env.runner,
        )
        val id = assertIs<CreateResult.Created>(service.create(draft)).id
        assertEquals(36, id.length)
        assertEquals('7', id[14], "UUID version nibble of $id")
        assertNotNull(env.budgetDao.rows.value[id])
    }

    @Test
    fun anUnverifiedPersonQueuesNothing() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.authState.value = AuthState.SignedIn(budgetAccount.copy(emailVerified = false))
        assertEquals(CreateResult.NotVerified, env.service.create(draft))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(emptyMap(), env.budgetDao.rows.value)
    }

    @Test
    fun aLimitOfZeroNegativeOrTooLargeIsRefusedAndNothingIsSaved() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        assertEquals(
            CreateResult.Invalid(mapOf(BudgetField.Limit to BudgetFieldProblem.LimitNotPositive)),
            env.service.create(draft.copy(limitMinor = 0)),
        )
        assertEquals(
            CreateResult.Invalid(mapOf(BudgetField.Limit to BudgetFieldProblem.LimitNotPositive)),
            env.service.create(draft.copy(limitMinor = -5)),
        )
        assertEquals(
            CreateResult.Invalid(mapOf(BudgetField.Limit to BudgetFieldProblem.LimitOutOfRange)),
            env.service.create(draft.copy(limitMinor = BudgetMath.MAX_MINOR + 1)),
        )
        assertIs<CreateResult.Created>(env.service.create(draft.copy(limitMinor = BudgetMath.MAX_MINOR)))
        assertEquals(1, env.budgetDao.rows.value.size)
    }

    @Test
    fun aCurrencyThatIsNotThreeCapitalLettersIsRefused() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        assertEquals(
            CreateResult.Invalid(mapOf(BudgetField.Currency to BudgetFieldProblem.Server("INVALID_CURRENCY"))),
            env.service.create(draft.copy(currency = "egp")),
        )
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun aSecondActiveBudgetForTheSameCategoryAndCurrencyIsRefusedButAnotherCurrencyOrAllExpensesIsNot() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1", currency = "EGP"))
        assertEquals(CreateResult.AlreadyBudgeted, env.service.create(draft))
        assertIs<CreateResult.Created>(env.service.create(draft.copy(currency = "USD")))
        assertIs<CreateResult.Created>(env.service.create(BudgetDraft(null, "EGP", 5_000)))
        assertEquals(CreateResult.AlreadyBudgeted, env.service.create(BudgetDraft(null, "EGP", 5_000)))
        assertEquals(CreateResult.AlreadyBudgeted, env.service.create(draft.copy(limitMinor = 10)), "a waiting budget counts as well")
    }

    @Test
    fun anArchivedOrDeletedBudgetNoLongerBlocksItsCategory() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("archived", categoryId = "c1", archivedAt = "2026-09-20T00:00:00Z"))
        assertIs<CreateResult.Created>(env.service.create(draft))
        env.seed(budgetJson("c2budget", categoryId = "c2", version = 2))
        env.engine.enqueueDelete("budget", "c2budget", com.fares.saveme.core.sync.SyncScope(ScopeKind.User, "u1"), 2)
        assertIs<CreateResult.Created>(env.service.create(BudgetDraft("c2", "EGP", 100)))
    }

    @Test
    fun offlineDoesNotBlockAndTheChangeStaysWaiting() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        val id = assertIs<CreateResult.Created>(env.service.create(draft)).id
        env.engine.drain()
        assertEquals("Waiting", env.pendingDao.rows.value.getValue("budget" to id).state)
        assertNotNull(env.budgetDao.rows.value[id])
    }
}
