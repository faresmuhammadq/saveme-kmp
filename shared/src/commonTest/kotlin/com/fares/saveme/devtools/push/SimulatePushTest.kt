package com.fares.saveme.devtools.push

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.notifications.domain.PushMessage
import com.fares.saveme.feature.notifications.domain.PushPayloadParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimulatePushTest {

    private val phone = PhoneIds(
        walletId = "0192f7a0-0000-7000-8000-0000000000b1",
        occurrenceId = "0192f7a0-0000-7000-8000-0000000000b2",
        budgetId = "0192f7a0-0000-7000-8000-0000000000b3",
    )
    private var counter = 0
    private val random = { "0192f7a0-0000-7000-8000-" + (++counter).toString().padStart(12, '0') }

    @Test
    fun thirteenRowsAndTheOnesWithInventedIdsAreLabelled() {
        val rows = SimulatePayloads.rows(phone)
        assertEquals(13, rows.size)
        assertEquals("occurrence_due", rows.first().type)
        assertEquals(SimulatePayloads.UNKNOWN, rows.last().type)
        assertFalse(rows.single { it.type == "budget_exceeded" }.notOnPhone)
        assertTrue(rows.single { it.type == "export_ready" }.notOnPhone)
        assertTrue(rows.single { it.type == "invitation_received" }.notOnPhone)
        assertFalse(rows.single { it.type == "subscription_ending" }.notOnPhone)
        val empty = SimulatePayloads.rows(PhoneIds(null, null, null))
        assertTrue(empty.single { it.type == "budget_exceeded" }.notOnPhone)
        assertTrue(empty.single { it.type == "occurrence_due" }.notOnPhone)
    }

    @Test
    fun everyBuiltPayloadParsesToItsOwnTypeWithTheContractKeysOnly() {
        SimulatePayloads.types.dropLast(1).forEach { type ->
            val data = SimulatePayloads.build(type, phone, random)
            val message = PushPayloadParser.parse(data)
            assertEquals(type, message.typeName, "$type parses to itself, not Malformed")
            assertTrue(data.keys.all { it in com.fares.saveme.feature.notifications.domain.PushTapKeys.all })
        }
        assertIs<PushMessage.Unknown>(PushPayloadParser.parse(SimulatePayloads.build(SimulatePayloads.UNKNOWN, phone, random)))
    }

    @Test
    fun idsComeFromThePhoneWhenThereAreAnyAndFromRandomOtherwise() {
        val onPhone = SimulatePayloads.build("occurrence_due", phone, random)
        assertEquals(phone.walletId, onPhone["wallet_id"])
        assertEquals(phone.occurrenceId, onPhone["occurrence_id"])
        val invented = SimulatePayloads.build("budget_exceeded", PhoneIds(null, null, null), random)
        assertTrue(invented["budget_id"]!!.startsWith("0192f7a0-0000-7000-8000-"))
    }

    @Test
    fun postHandsTheContractDataAndTheGenericTextToTheReceiver() = runMviTest {
        val posted = mutableListOf<Triple<Map<String, String>, String, String>>()
        val store = SimulatePushViewModel(
            phoneIds = { phone }, texts = { "Test notification" to "This was posted from Developer tools." },
            post = { data, title, body -> posted += Triple(data, title, body) }, rotate = {}, randomId = random,
        )
        store.onIntent(SimulatePushIntent.Opened)
        assertEquals(13, store.state.value.rows.size)
        store.onIntent(SimulatePushIntent.Post("budget_exceeded"))
        val (data, title, body) = posted.single()
        assertEquals("budget_exceeded", data["type"])
        assertEquals(phone.budgetId, data["budget_id"])
        assertEquals("Test notification", title)
        assertEquals("This was posted from Developer tools.", body)
        assertEquals("budget_exceeded", store.state.value.posted)
        // Posting twice uses a fresh notification id, so only the collapse key replaces the first.
        store.onIntent(SimulatePushIntent.Post("budget_exceeded"))
        assertTrue(posted[0].first["notification_id"] != posted[1].first["notification_id"])
    }

    @Test
    fun rotateCallsTheProviderAndBackNavigates() = runMviTest {
        var rotated = 0
        val store = SimulatePushViewModel({ phone }, { "t" to "b" }, { _, _, _ -> }, { rotated++ }, random)
        store.effects.test {
            store.onIntent(SimulatePushIntent.Rotate)
            assertEquals(1, rotated)
            assertTrue(store.state.value.rotated)
            store.onIntent(SimulatePushIntent.Back)
            assertEquals(SimulatePushEffect.NavigateBack, awaitItem())
        }
    }
}
