package com.fares.saveme.feature.account.domain.deletion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuccessorChoicesTest {

    private fun wallet(id: String, suggested: String = "s-$id", offered: Boolean = true, expected: Boolean = true, others: List<String> = listOf("x-$id")) =
        OwnedSharedWallet(
            walletId = id, walletName = if (offered) id else null, successorUserId = suggested, reason = SuccessorReason.OldestEditor,
            choiceExpected = expected, choiceOffered = offered,
            candidates = listOf(Candidate(suggested, "S", null, null)) + others.map { Candidate(it, it, null, null) },
        )

    private fun plan(vararg wallets: OwnedSharedWallet) = DeletionPlan(PlanState.Active, null, null, wallets.toList())

    @Test
    fun oneEntryPerOfferedWalletInPlanOrderUsingThePickOrTheSuggestion() {
        val result = SuccessorChoices.build(plan(wallet("a"), wallet("b"), wallet("c")), mapOf("b" to "x-b"))
        assertEquals(listOf(SuccessorChoice("a", "s-a"), SuccessorChoice("b", "x-b"), SuccessorChoice("c", "s-c")), result)
    }

    @Test
    fun walletsWithoutAnOfferedChoiceAreNeverIncluded() {
        val result = SuccessorChoices.build(plan(wallet("a", offered = false), wallet("b", expected = false, offered = false), wallet("c")), emptyMap())
        assertEquals(listOf(SuccessorChoice("c", "s-c")), result)
    }

    @Test
    fun noneMeansNull() {
        assertNull(SuccessorChoices.build(plan(), emptyMap()))
        assertNull(SuccessorChoices.build(plan(wallet("a", offered = false)), emptyMap()))
    }

    @Test
    fun aPickThatIsNotACandidateFallsBackToTheSuggestion() {
        assertEquals(listOf(SuccessorChoice("a", "s-a")), SuccessorChoices.build(plan(wallet("a")), mapOf("a" to "stranger")))
    }

    @Test
    fun atMostAHundredInPlanOrder() {
        val wallets = (1..105).map { wallet("w$it") }.toTypedArray()
        val result = SuccessorChoices.build(plan(*wallets), emptyMap())!!
        assertEquals(100, result.size)
        assertEquals("w1", result.first().walletId)
        assertEquals("w100", result.last().walletId)
    }
}
