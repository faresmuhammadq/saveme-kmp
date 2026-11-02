package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.dto.TransactionFields
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.data.dto.TransactionWriteRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Shared Budget a transaction counts toward on the wire (backend ADR-0027 rule 4). */
class TransactionSharedBudgetWireTest {

    private fun request(budget: String?, send: Boolean) = TransactionWriteRequest(
        kind = "expense", date = "2026-10-05", amountMinor = 1200, currency = "EGP", categoryId = "c1", counterWalletId = null,
        description = null, memo = null, counterparty = null, cleared = false, version = 0, sharedBudgetId = budget, sendSharedBudget = send,
    )

    @Test
    fun theMemberIsSentWhenThereIsSomethingToSay() {
        assertEquals(JsonPrimitive("s1"), request("s1", true).toJson()["shared_budget_id"])
    }

    @Test
    fun clearingALinkIsAnExplicitNull() {
        assertEquals(JsonNull, request(null, true).toJson()["shared_budget_id"])
    }

    @Test
    fun aTransactionWithNoBudgetNeverSendsTheMemberSoAnOlderServerAcceptsIt() {
        assertFalse(request(null, false).toJson().containsKey("shared_budget_id"))
    }

    @Test
    fun theServersCopyAndAnOlderPayloadDecode() {
        val dto = AppJson.decodeFromString(
            TransactionDto.serializer(),
            """{"id":"t1","wallet_id":"w1","kind":"expense","date":"2026-10-05","amount_minor":1200,"currency":"EGP","shared_budget_id":"s1","version":1,"created_at":"t","updated_at":"t"}""",
        )
        assertEquals("s1", dto.sharedBudgetId)
        assertNull(AppJson.decodeFromString(TransactionPayload.serializer(), """{"kind":"expense","date":"2026-10-05","amountMinor":1,"currency":"EGP"}""").sharedBudgetId)
    }

    @Test
    fun aRefusalAtTheBudgetPointerIsTheBudgetField() {
        assertTrue(TransactionFields.byPointer["/shared_budget_id"] == TransactionFields.BUDGET)
    }
}
