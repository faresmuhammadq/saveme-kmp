package com.fares.saveme.feature.wallets.domain.model

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.wallets.data.dto.WalletWriteRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class WalletModelTest {
    private val wallet = Wallet(
        "w1", "cash", "Pocket", "EGP", 25_000, 25_000, "cash", "#4C6FD6", null, false, WalletRole.Owner, 3,
        Instant.parse("2026-09-01T00:00:00Z"),
    )

    @Test
    fun typeAndRoleRoundTripTheirWireValues() {
        WalletType.selectable.forEach { assertEquals(it, WalletType.fromWire(it.wire)) }
        WalletRole.entries.forEach { assertEquals(it, WalletRole.fromWire(it.wire)) }
        assertTrue(WalletType.CreditCard.isLiability && WalletType.Loan.isLiability)
        assertFalse(WalletType.Cash.isLiability)
        assertTrue(WalletRole.Owner.canEdit)
        assertFalse(WalletRole.Editor.canEdit)
    }

    @Test
    fun anUnknownTypeIsUnknownButIsNeverSentBackDifferently() {
        val future = wallet.copy(typeWire = "gold_bar")
        assertEquals(WalletType.Unknown, future.type)
        assertEquals("gold_bar", future.toDraft().type)
        assertEquals(WalletRole.Viewer, WalletRole.fromWire("auditor"))
    }

    @Test
    fun theWriteRequestCarriesIconAndColourAsExplicitNulls() {
        val json = WalletWriteRequest("cash", "Pocket", "EGP", 25_000, null, null, version = 0).toJson()
        assertEquals(setOf("type", "name", "currency", "opening_balance_minor", "icon", "color", "version"), json.keys)
        assertEquals(JsonNull, json["icon"])
        assertEquals(JsonNull, json["color"])
        // The stored draft is internal; only the request body needs the nulls.
        val draft = AppJson.decodeFromString(WalletDraft.serializer(), AppJson.encodeToString(WalletDraft.serializer(), wallet.toDraft()))
        assertEquals(wallet.toDraft(), draft)
    }

    @Test
    fun sameFieldsIgnoresTheArchiveFlag() {
        val draft = wallet.toDraft()
        assertTrue(draft.copy(archive = true).sameFieldsAs(wallet))
        assertFalse(draft.copy(name = "Other").sameFieldsAs(wallet))
        assertFalse(draft.copy(openingBalanceMinor = 1).sameFieldsAs(wallet))
        assertFalse(draft.copy(icon = null).sameFieldsAs(wallet))
    }

    @Test
    fun fieldPointersMapToFields() {
        mapOf(
            "/name" to WalletField.Name, "/currency" to WalletField.Currency, "/type" to WalletField.Type,
            "/opening_balance_minor" to WalletField.OpeningBalance, "/icon" to WalletField.Icon, "/color" to WalletField.Color,
        ).forEach { (pointer, field) -> assertEquals(field, WalletField.fromPointer(pointer)) }
        assertEquals(null, WalletField.fromPointer("/version"))
        assertEquals(null, WalletField.fromPointer(null))
    }

    @Test
    fun theDraftOfAnArchivedWalletKeepsArchiveTrue() {
        assertTrue(wallet.copy(archivedAt = Instant.parse("2026-09-02T00:00:00Z")).toDraft().archive)
        assertFalse(wallet.toDraft().archive)
        assertEquals(setOf("type", "name"), AppJson.encodeToJsonElement(WalletDraft.serializer(), wallet.toDraft()).jsonObject.keys.intersect(setOf("type", "name")))
    }
}
