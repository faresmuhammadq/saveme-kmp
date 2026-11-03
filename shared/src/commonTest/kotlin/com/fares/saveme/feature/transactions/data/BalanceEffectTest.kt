package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.transactions.data.dto.TransactionFields
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import kotlin.test.Test
import kotlin.test.assertEquals

/** The effect rule on primitives (research R-04, R-09, R-22). */
class BalanceEffectTest {
    private fun effect(kind: String, forWallet: String, deleted: Boolean = false, wallet: String = "w1", counter: String? = null) =
        BalanceEffect.of(kind, 500, wallet, counter, deleted, forWallet)

    @Test
    fun incomeAddsAndExpenseSubtractsInTheirOwnWalletOnly() {
        assertEquals(500, effect("income", "w1"))
        assertEquals(-500, effect("expense", "w1"))
        assertEquals(0, effect("income", "w2"))
        assertEquals(0, effect("expense", "w2"))
    }

    @Test
    fun anAdjustmentRaisesOrLowersItsOwnWalletOnly() {
        assertEquals(500, effect("adjustment_in", "w1"))
        assertEquals(-500, effect("adjustment_out", "w1"))
        assertEquals(0, effect("adjustment_in", "w2"))
        assertEquals(0, effect("adjustment_out", "w1", deleted = true))
    }

    @Test
    fun aTransferMovesBothWalletsByItsDirection() {
        assertEquals(-500, effect("transfer", "w1", counter = "w2"))
        assertEquals(500, effect("transfer", "w2", counter = "w2".let { it }))
        assertEquals(0, effect("transfer", "w3", counter = "w2"))
    }

    @Test
    fun aDeletedStateAndAnUnknownKindHaveNoEffect() {
        assertEquals(0, effect("expense", "w1", deleted = true))
        assertEquals(0, effect("transfer", "w1", deleted = true, counter = "w2"))
        assertEquals(0, effect("refund", "w1"))
        assertEquals(0, effect("", "w1"))
    }

    @Test
    fun theQueuePayloadKeepsExplicitNullsOutOfTheJsonButDecodesThemBack() {
        val payload = TransactionPayload("expense", "2026-10-01", 4_550, "EGP", categoryId = "c1")
        val back = AppJson.decodeFromString(TransactionPayload.serializer(), AppJson.encodeToString(TransactionPayload.serializer(), payload))
        assertEquals(payload, back)
        assertEquals(null, back.counterWalletId)
    }

    @Test
    fun everyPointerOfTheContractMapsToAFieldName() {
        assertEquals(11, TransactionFields.byPointer.size) // the 10 of spec 008 and `/shared_budget_id` (backend ADR-0027)
        assertEquals("amount", TransactionFields.byPointer["/amount_minor"])
        assertEquals("destination", TransactionFields.byPointer["/counter_wallet_id"])
    }
}
