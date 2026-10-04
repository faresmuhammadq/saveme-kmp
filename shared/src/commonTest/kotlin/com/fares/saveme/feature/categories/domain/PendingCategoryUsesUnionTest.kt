package com.fares.saveme.feature.categories.domain

import com.fares.saveme.feature.categories.FakePendingUses
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** C5 sees the categories every bound source (transactions, recurring) still uses (spec 010 FR-032). */
class PendingCategoryUsesUnionTest {
    @Test
    fun theUnionFollowsEverySource() = runTest {
        val transactions = FakePendingUses()
        val recurring = FakePendingUses()
        val union = UnionCategoryUses(listOf(transactions, recurring))
        assertEquals(emptySet(), union.usedLocally().first())
        transactions.used.value = setOf("c1")
        recurring.used.value = setOf("c2", "c1")
        assertEquals(setOf("c1", "c2"), union.usedLocally().first())
        transactions.used.value = emptySet()
        assertEquals(setOf("c1", "c2"), union.usedLocally().first())
        recurring.used.value = emptySet()
        assertEquals(emptySet(), union.usedLocally().first())
    }

    @Test
    fun noSourceMeansNothingInUse() = runTest {
        assertEquals(emptySet(), UnionCategoryUses(emptyList()).usedLocally().first())
    }
}
