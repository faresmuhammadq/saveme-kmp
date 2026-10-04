package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetAccount
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.categories.domain.UnionCategoryUses
import com.fares.saveme.feature.categories.FakePendingUses
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A category named by a budget on the phone is in use on the phone (FR-021). */
class BudgetCategoryUsesTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    private fun uses(env: BudgetTestEnv) = BudgetCategoryUses(env.repository, { env.engine }, env.authState)

    @Test
    fun syncedAndWaitingBudgetsCategoriesAreInUseAndAllExpensesAddsNothing() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        assertEquals(emptySet(), uses(env).usedLocally().first())
        env.seed(budgetJson("a", categoryId = "c1"))
        env.seed(budgetJson("all", categoryId = null))
        assertEquals(setOf("c1"), uses(env).usedLocally().first())
        env.repository.insertPlaceholder("w", "u1", "c2", "EGP", 100)
        env.engine.enqueue("budget", "w", user, 0, """{"categoryId":"c2","currency":"EGP","amountMinor":100}""")
        assertEquals(setOf("c1", "c2"), uses(env).usedLocally().first())
    }

    @Test
    fun aBudgetStillWaitingForItsDeletionKeepsItsCategoryInUse() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("a", categoryId = "c1", version = 2))
        env.engine.enqueueDelete("budget", "a", user, 2)
        assertEquals(setOf("c1"), uses(env).usedLocally().first(), "the server keeps it in use while any budget names it")
    }

    @Test
    fun itJoinsTheUnionOfEverySource() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("a", categoryId = "c1"))
        val other = FakePendingUses()
        other.used.value = setOf("c9")
        assertEquals(setOf("c1", "c9"), UnionCategoryUses(listOf(other, uses(env))).usedLocally().first())
    }

    @Test
    fun nothingIsInUseWhenSignedOut() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.seed(budgetJson("a", categoryId = "c1"))
        env.authState.value = AuthState.SignedOut(SignOutReason.None)
        assertEquals(emptySet(), uses(env).usedLocally().first())
        env.authState.value = AuthState.SignedIn(budgetAccount)
        assertEquals(setOf("c1"), uses(env).usedLocally().first())
    }
}
