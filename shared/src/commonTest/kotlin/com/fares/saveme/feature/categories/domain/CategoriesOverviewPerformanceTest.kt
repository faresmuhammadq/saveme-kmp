package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** SC-004, SC-005: 200 own plus the 26 defaults build fast, and a language change touches no storage. */
class CategoriesOverviewPerformanceTest {
    private val own = (1..200).map { ownView("o$it", "Category $it", kind = if (it % 2 == 0) "expense" else "income") }
    private val defaults = (1..26).map { defaultCategory("d$it", position = it, kind = if (it <= 19) "expense" else "income") }

    @Test
    fun buildingTheOverviewAndTheChoiceListIsFast() {
        val start = TimeSource.Monotonic.markNow()
        repeat(10) {
            buildOverview(CategoryKind.Expense, own, defaults, AppLanguage.En, null, null)
            buildChoices(CategoryKind.Expense, null, own, defaults, emptyList(), AppLanguage.En)
        }
        val perRun = start.elapsedNow().inWholeMilliseconds / 10.0
        assertTrue(perRun < 50, "took $perRun ms")
        assertEquals(100, buildOverview(CategoryKind.Expense, own, defaults, AppLanguage.En, null, null).own.size)
    }

    @Test
    fun aLanguageChangeReEmitsWithoutReadingTheDatabaseAgain() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        env.signedIn()
        env.seed(categoryJson("a", name = "Mine"))
        env.seedDefaults(com.fares.saveme.feature.categories.platformJson("d1", en = "Groceries", ar = "بقالة"))
        val before = env.service.observeOverview(CategoryKind.Expense).first()
        assertEquals("Groceries", before.defaults.single().name)
        env.language.value = AppLanguage.Ar
        val after = env.service.observeOverview(CategoryKind.Expense).first()
        assertEquals("بقالة", after.defaults.single().name)
        assertEquals("Mine", after.own.single().shown.name)
        assertEquals(0, env.requests.size)
    }
}
