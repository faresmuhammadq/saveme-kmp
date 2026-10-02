package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-018 and the Edge Cases around the category choice. */
class CategoryChoicesTest {
    private val own = listOf(
        ownView("b", "Beans"), ownView("a", "apple"), ownView("arch", "Old", archived = true),
        ownView("inc", "Salary", kind = "income"), ownView("odd", "Odd", kind = "transfer"),
        ownView("del", "Deleting", pendingDelete = true, state = SyncState.Waiting),
    )
    private val defaults = listOf(
        defaultCategory("g2", position = 2), defaultCategory("g1", position = 1), defaultCategory("old", position = 3, retired = true),
        defaultCategory("sal", kind = "income", position = 20),
    )

    private fun build(current: String? = null, kind: CategoryKind = CategoryKind.Expense) =
        buildChoices(kind, current, own, defaults, listOf(reference("p1")), AppLanguage.En)

    @Test
    fun ownActiveByNameThenActiveDefaultsByPosition() {
        val choices = build()
        assertEquals(listOf("a", "b"), choices.own.map { it.id })
        assertEquals(listOf("g1", "g2"), choices.defaults.map { it.id })
        assertNull(choices.current)
    }

    @Test
    fun neverArchivedRetiredOtherKindUnknownKindOrHiddenOnes() {
        val ids = build().own.map { it.id } + build().defaults.map { it.id }
        listOf("arch", "old", "inc", "sal", "odd", "del", "p1").forEach { assertFalse(it in ids, it) }
        assertEquals(listOf("inc"), build(kind = CategoryKind.Income).own.map { it.id })
    }

    @Test
    fun theCurrentCategoryStaysListedWhateverItsState() {
        val archived = build(current = "arch").current!!
        assertEquals("arch", archived.id)
        assertTrue(archived.archived)
        val retired = build(current = "old").current!!
        assertEquals(CategorySource.Default, retired.source)
        assertTrue(retired.archived)
        val partner = build(current = "p1").current!!
        assertEquals(CategorySource.Partner, partner.source)
        assertEquals("Partner p1", partner.name)
    }

    @Test
    fun aCurrentCategoryThatIsAlreadyChoosableIsNotListedTwice() {
        assertNull(build(current = "a").current)
        assertNull(build(current = "g1").current)
    }

    @Test
    fun anUnresolvableCurrentIdIsListedAsUnavailable() {
        assertEquals(CategorySource.Unavailable, build(current = "nope").current!!.source)
    }

    @Test
    fun nothingToOfferIsEmpty() {
        val empty = buildChoices(CategoryKind.Expense, null, emptyList(), emptyList(), emptyList(), AppLanguage.En)
        assertTrue(empty.isEmpty)
        assertFalse(build().isEmpty)
    }

    @Test
    fun defaultNamesFollowTheLanguage() {
        val ar = buildChoices(CategoryKind.Expense, null, own, defaults, emptyList(), AppLanguage.Ar)
        assertEquals(listOf("ع-g1", "ع-g2"), ar.defaults.map { it.name })
    }
}
