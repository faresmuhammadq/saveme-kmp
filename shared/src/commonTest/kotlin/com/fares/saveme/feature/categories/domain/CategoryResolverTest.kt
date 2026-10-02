package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.SyncState
import com.fares.saveme.feature.categories.platformJson
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.referenceJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FR-019, SC-006: own → default → the wallet's references → placeholder, offline. */
class CategoryResolverTest {
    private val own = listOf(ownView("o", "Mine"), ownView("arch", "Shelved", archived = true))
    private val defaults = listOf(defaultCategory("d", en = "Groceries", ar = "بقالة"), defaultCategory("old", retired = true))
    private val references = listOf(reference("p"), reference("o", name = "Same id as mine"))

    private fun resolver(language: AppLanguage = AppLanguage.En, own: List<com.fares.saveme.feature.categories.domain.model.CategoryView> = this.own) =
        CategoryResolver(own, defaults, references, language)

    @Test
    fun resolvesInTheDocumentedOrder() {
        assertEquals(CategorySource.Own, resolver().resolve("o").source)
        assertEquals("Mine", resolver().resolve("o").name)
        assertEquals(CategorySource.Default, resolver().resolve("d").source)
        assertEquals(CategorySource.Partner, resolver().resolve("p").source)
        assertEquals(CategorySource.Unavailable, resolver().resolve("zzz").source)
    }

    @Test
    fun archivedOwnAndRetiredDefaultsStillResolve() {
        assertTrue(resolver().resolve("arch").archived)
        assertTrue(resolver().resolve("old").archived)
        assertEquals(CategorySource.Default, resolver().resolve("old").source)
    }

    @Test
    fun aWaitingRenameShowsTheNewName() {
        val renamed = listOf(ownView("o", "Mine", pending = CategoryDraft("expense", "Renamed", "bus", "#000000"), state = SyncState.Waiting))
        val display = resolver(own = renamed).resolve("o")
        assertEquals("Renamed", display.name)
        assertEquals("bus", display.iconKey)
    }

    @Test
    fun defaultNamesFollowTheLanguageAndOthersAreNeverTranslated() {
        assertEquals("Groceries", resolver(AppLanguage.En).resolve("d").name)
        assertEquals("بقالة", resolver(AppLanguage.Ar).resolve("d").name)
        assertEquals("Mine", resolver(AppLanguage.Ar).resolve("o").name)
        assertEquals("Partner p", resolver(AppLanguage.Ar).resolve("p").name)
    }

    @Test
    fun anUnknownKindStillResolves() {
        val odd = listOf(ownView("x", "Odd", kind = "transfer"))
        val display = resolver(own = odd).resolve("x")
        assertEquals("Odd", display.name)
        assertEquals("transfer", display.kindWire)
    }

    @Test
    fun aDeletionThatWaitsStillResolvesForExistingRecords() {
        val deleting = listOf(ownView("x", "Going", pendingDelete = true, state = SyncState.Waiting))
        assertEquals(CategorySource.Own, resolver(own = deleting).resolve("x").source)
    }

    @Test
    fun theServiceResolvesFromThePhonesCopyAndAMissAsksForThatWalletsReferencesOnce() = runTest {
        var reads = 0
        val env = CategoryTestEnv(backgroundScope) {
            reads++
            jsonResponse(200, categoryPage(listOf(referenceJson(categoryId = "p9", name = "Learned"))))
        }
        // The read runs on the HTTP engine's own threads, so the test waits for its result instead of for the scheduler.
        env.signedIn()
        env.seed(categoryJson("o", name = "Mine"))
        env.seedDefaults(platformJson("d"))
        val displays = env.service.observeDisplays("w1").first()
        assertEquals("Mine", displays.resolve("o").name)
        assertEquals(CategorySource.Default, displays.resolve("d").source)
        assertEquals(CategorySource.Unavailable, displays.resolve("p9").source)
        displays.resolve("p9")
        displays.resolve("other-miss")
        env.referenceDao.rows.first { it.isNotEmpty() }
        advanceUntilIdle()
        assertEquals(1, reads)
        assertEquals("Learned", env.service.observeDisplays("w1").first().resolve("p9").name)
        assertFalse(env.service.observeDisplays(null).first().resolve("p9").source == CategorySource.Partner)
    }
}
