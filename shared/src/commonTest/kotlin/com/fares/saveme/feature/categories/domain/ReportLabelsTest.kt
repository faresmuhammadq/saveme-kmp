package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Spec 014 research R-08: the grouping key and the names a report shows, across every covered wallet. */
class ReportLabelsTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = CategoryTestEnv(scope) { jsonResponse(200, categoryPage(emptyList())) }

    @Test
    fun theKeyIsTheSystemKeyForAPlatformCategoryAndTheIdOtherwise() = runTest {
        val env = env(backgroundScope)
        env.seedDefaults(com.fares.saveme.feature.categories.platformJson("d1", "groceries"))
        env.seed(categoryJson(id = "own1"))
        val labels = env.service.observeReportLabels(setOf("w1")).first()
        assertEquals("groceries", labels.keyOf("d1"))
        assertEquals("own1", labels.keyOf("own1"))
        assertEquals("partner-category", labels.keyOf("partner-category"))
    }

    @Test
    fun namesResolveOwnThenPlatformInTheAppLanguageThenTheReferencesOfEveryCoveredWallet() = runTest {
        val env = env(backgroundScope)
        env.seedDefaults(com.fares.saveme.feature.categories.platformJson("d1", "groceries", en = "Groceries", ar = "بقالة"))
        env.seed(categoryJson(id = "own1", name = "Pets"))
        env.seedReference("w1", "p1", "Coffee beans")
        env.seedReference("w2", "p2", "Gym")
        env.seedReference("w9", "p9", "Not covered")
        val labels = env.service.observeReportLabels(setOf("w1", "w2")).first()
        assertEquals("Pets", labels.display("own1").name)
        assertEquals(CategorySource.Own, labels.display("own1").source)
        assertEquals("Groceries", labels.display("d1").name)
        assertEquals("Coffee beans", labels.display("p1").name)
        assertEquals("Gym", labels.display("p2").name)
        assertEquals(CategorySource.Partner, labels.display("p2").source)
        assertEquals(CategorySource.Unavailable, labels.display("p9").source, "a wallet the report does not cover is not consulted")
        env.language.value = AppLanguage.Ar
        assertEquals("بقالة", env.service.observeReportLabels(setOf("w1")).first().display("d1").name)
    }

    @Test
    fun aMissAsksForTheReferencesOfEachCoveredWalletUnderTheFloor() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        val labels = env.service.observeReportLabels(setOf("w1", "w2")).first()
        assertEquals(CategorySource.Unavailable, labels.display("nobody").source)
        labels.display("nobody")
        runCurrent()
        assertEquals(setOf<Any>(ReadTarget.Wallet("w1"), ReadTarget.Wallet("w2")), env.runner.refreshes.map { it.first }.toSet())
        assertEquals(2, env.runner.refreshes.size, "the second miss is inside the floor")
    }

    @Test
    fun theDefaultBodyKeysEveryIdAsItself() = runTest {
        val labels = ReportLabels.identity()
        assertEquals("anything", labels.keyOf("anything"))
        assertEquals(CategorySource.Unavailable, labels.display("anything").source)
    }
}
