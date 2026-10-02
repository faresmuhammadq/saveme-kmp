package com.fares.saveme.feature.categories.domain.model

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import com.fares.saveme.feature.categories.data.dto.CategoryWriteRequest
import com.fares.saveme.feature.categories.domain.toDraft
import com.fares.saveme.feature.categories.domain.toPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CategoryModelTest {
    private val category = Category("c1", "expense", "Coffee", "restaurant", "#1F7F8C", null, false, 3, Instant.parse("2026-09-01T00:00:00Z"))

    @Test
    fun theKindRoundTripsAndAnUnknownOneReadsAsNullButIsKept() {
        assertEquals(CategoryKind.Expense, CategoryKind.fromWire("expense"))
        assertEquals(CategoryKind.Income, CategoryKind.fromWire("income"))
        assertNull(CategoryKind.fromWire("transfer"))
        assertNull(CategoryKind.fromWire(null))
        val unknown = category.copy(kindWire = "transfer")
        assertNull(unknown.kind)
        assertEquals("transfer", unknown.toDraft().kind)
    }

    @Test
    fun aDraftAndItsPayloadRoundTripWithExplicitNullsForIconAndColour() {
        val draft = CategoryDraft("income", "Salary", null, null, archive = true)
        assertEquals(draft, draft.toPayload().toDraft())
        val request = CategoryWriteRequest("income", "Salary", null, null, 0).toJson().toString()
        assertTrue("\"icon\":null" in request && "\"color\":null" in request, request)
        val payload = AppJson.decodeFromString(CategoryPayload.serializer(), AppJson.encodeToString(CategoryPayload.serializer(), draft.toPayload()))
        assertEquals(draft.toPayload(), payload)
    }

    @Test
    fun sameFieldsAsIgnoresTheArchiveFlag() {
        val draft = category.toDraft()
        assertTrue(draft.copy(archive = true).sameFieldsAs(category))
        assertFalse(draft.copy(name = "Tea").sameFieldsAs(category))
        assertFalse(draft.copy(icon = null).sameFieldsAs(category))
        assertFalse(draft.copy(color = "#000000").sameFieldsAs(category))
        assertFalse(draft.copy(kind = "income").sameFieldsAs(category))
    }

    @Test
    fun fieldsAreFoundByTheNamesTheDataLayerReports() {
        assertEquals(CategoryField.Name, CategoryField.fromName("name"))
        assertEquals(CategoryField.Kind, CategoryField.fromName("kind"))
        assertEquals(CategoryField.Icon, CategoryField.fromName("icon"))
        assertEquals(CategoryField.Color, CategoryField.fromName("color"))
        assertNull(CategoryField.fromName("parent_id"))
    }

    @Test
    fun anArchivedServerCopyGivesAnArchivedDraft() {
        assertTrue(category.copy(archivedAt = Instant.parse("2026-09-02T00:00:00Z")).toDraft().archive)
    }
}
