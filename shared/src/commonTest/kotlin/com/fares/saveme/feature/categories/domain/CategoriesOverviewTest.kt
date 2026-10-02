package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CategoriesOverviewTest {
    private val stamp = Instant.parse("2026-10-02T10:00:00Z")

    private fun overview(
        kind: CategoryKind,
        views: List<com.fares.saveme.feature.categories.domain.model.CategoryView> = emptyList(),
        defaults: List<com.fares.saveme.feature.categories.domain.model.DefaultCategory> = emptyList(),
        language: AppLanguage = AppLanguage.En,
        own: Instant? = stamp,
        defaultsAt: Instant? = stamp,
    ) = buildOverview(kind, views, defaults, language, own, defaultsAt)

    @Test
    fun eachKindListsOnlyItsOwnAndSplitsOutArchived() {
        val views = listOf(
            ownView("a", "Coffee"), ownView("b", "Salary", kind = "income"), ownView("c", "Gym", archived = true),
            ownView("d", "Tips", kind = "income", archived = true),
        )
        val expense = overview(CategoryKind.Expense, views)
        assertEquals(listOf("a"), expense.own.map { it.id })
        assertEquals(listOf("c"), expense.archived.map { it.id })
        val income = overview(CategoryKind.Income, views)
        assertEquals(listOf("b"), income.own.map { it.id })
        assertEquals(listOf("d"), income.archived.map { it.id })
    }

    @Test
    fun ownCategoriesAreOrderedByNameIgnoringCaseThenByCreation() {
        val views = listOf(
            ownView("1", "banana"), ownView("2", "Apple"), ownView("3", "cherry"),
            ownView("4", "apple", created = "2026-08-01T00:00:00Z"),
        )
        assertEquals(listOf("4", "2", "1", "3"), overview(CategoryKind.Expense, views).own.map { it.id })
    }

    @Test
    fun defaultsFollowThePlatformOrderAndLeaveOutRetiredAndOtherKinds() {
        val defaults = listOf(
            defaultCategory("g2", position = 2), defaultCategory("g1", position = 1), defaultCategory("old", position = 3, retired = true),
            defaultCategory("sal", kind = "income", position = 20), defaultCategory("odd", kind = "transfer", position = 30),
        )
        assertEquals(listOf("g1", "g2"), overview(CategoryKind.Expense, defaults = defaults).defaults.map { it.id })
        assertEquals(listOf("sal"), overview(CategoryKind.Income, defaults = defaults).defaults.map { it.id })
    }

    @Test
    fun defaultNamesFollowTheLanguageAndOwnNamesAreNeverTranslated() {
        val defaults = listOf(defaultCategory("g1", en = "Groceries", ar = "بقالة"))
        val views = listOf(ownView("a", "Coffee"))
        val en = overview(CategoryKind.Expense, views, defaults, AppLanguage.En)
        val ar = overview(CategoryKind.Expense, views, defaults, AppLanguage.Ar)
        assertEquals("Groceries", en.defaults.single().name)
        assertEquals("بقالة", ar.defaults.single().name)
        assertEquals("Coffee", en.own.single().shown.name)
        assertEquals("Coffee", ar.own.single().shown.name)
    }

    @Test
    fun aCategoryHiddenByAWaitingDeletionIsLeftOutButAClashedOneIsShown() {
        val views = listOf(
            ownView("gone", pendingDelete = true, state = SyncState.Waiting),
            ownView("clash", pendingDelete = true, state = SyncState.Clash(null, true)),
        )
        assertEquals(listOf("clash"), overview(CategoryKind.Expense, views).own.map { it.id })
    }

    @Test
    fun anUnknownKindIsListedNowhere() {
        val views = listOf(ownView("x", kind = "transfer"))
        assertEquals(emptyList(), overview(CategoryKind.Expense, views).own)
        assertEquals(emptyList(), overview(CategoryKind.Income, views).own)
    }

    @Test
    fun aWaitingRenameShowsTheNewName() {
        val views = listOf(ownView("a", "Coffee", pending = CategoryDraft("expense", "Café", null, null), state = SyncState.Waiting))
        assertEquals("Café", overview(CategoryKind.Expense, views).own.single().shown.name)
    }

    @Test
    fun aPendingArchiveMovesTheCategoryToTheArchivedGroup() {
        val views = listOf(ownView("a", pending = CategoryDraft("expense", "a", null, null, archive = true), state = SyncState.Waiting))
        val o = overview(CategoryKind.Expense, views)
        assertEquals(emptyList(), o.own)
        assertEquals(listOf("a"), o.archived.map { it.id })
    }

    @Test
    fun defaultsMissingWhenTheCachedSetIsEmpty() {
        assertTrue(overview(CategoryKind.Expense).defaultsMissing)
        assertFalse(overview(CategoryKind.Expense, defaults = listOf(defaultCategory("g"))).defaultsMissing)
    }

    @Test
    fun theUpdatedTimeIsTheOlderStampAndLoadedWaitsForAReadOrARow() {
        val older = Instant.parse("2026-10-01T10:00:00Z")
        assertEquals(older, overview(CategoryKind.Expense, own = stamp, defaultsAt = older).updatedAt)
        assertEquals(older, overview(CategoryKind.Expense, own = older, defaultsAt = stamp).updatedAt)
        assertEquals(stamp, overview(CategoryKind.Expense, own = stamp, defaultsAt = null).updatedAt)
        val before = overview(CategoryKind.Expense, own = null, defaultsAt = null)
        assertFalse(before.loaded)
        assertNull(before.updatedAt)
        assertTrue(overview(CategoryKind.Expense, listOf(ownView("a")), own = null, defaultsAt = null).loaded)
        assertTrue(overview(CategoryKind.Expense, own = stamp, defaultsAt = null).loaded)
    }
}
