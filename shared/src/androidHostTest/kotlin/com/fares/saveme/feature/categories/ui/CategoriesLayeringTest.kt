package com.fares.saveme.feature.categories.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10: dependencies flow ui → domain → data. The category UI never imports a data package, data
 * imports nothing from the domain, the UI or another feature (stricter than wallets: the queue payload
 * lives in data), and the domain imports no Ktor, Compose or Room. Other features are reached only
 * through their `domain` package; wallets never imports categories; the design system imports no feature.
 */
class CategoriesLayeringTest {

    private val main = File("src/commonMain/kotlin/com/fares/saveme")
    private val feature = File(main, "feature")
    private val categories = File(feature, "categories")
    private val base = "com.fares.saveme.feature."

    private fun imports(root: File): List<Pair<String, String>> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
        .toList()

    private fun imports(dir: String) = imports(File(categories, dir))

    private fun otherFeatureImports(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> i.startsWith(base) && !i.startsWith(base + "categories.") }

    private fun beyondDomain(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> !i.substringAfter(base).substringAfter('.').startsWith("domain") }

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(categories.isDirectory, "run from :shared: ${categories.absolutePath}")

    @Test
    fun uiImportsNoDataPackageAndOtherFeaturesOnlyThroughTheirDomain() {
        val ui = imports("ui")
        assertTrue(ui.isNotEmpty())
        val data = ui.filter { (_, i) -> i.startsWith(base + "categories.data") }
        assertTrue(data.isEmpty(), "ui imports data: $data")
        val foreign = beyondDomain(otherFeatureImports(ui))
        assertTrue(foreign.isEmpty(), "ui reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun dataImportsNothingFromDomainUiOrOtherFeatures() {
        val data = imports("data")
        assertTrue(data.none { (_, i) -> i.startsWith(base + "categories.ui") }, "data imports ui")
        assertTrue(data.none { (_, i) -> i.startsWith(base + "categories.domain") }, "data imports domain: ${data.filter { (_, i) -> i.startsWith(base + "categories.domain") }}")
        assertTrue(otherFeatureImports(data).isEmpty(), "data reaches another feature: ${otherFeatureImports(data)}")
    }

    @Test
    fun domainImportsNoNetworkComposeOrDatabaseLibrariesAndOtherFeaturesOnlyThroughDomain() {
        val domain = imports("domain")
        val libs = domain.filter { (_, i) -> listOf("io.ktor", "androidx.compose", "androidx.room", "org.jetbrains.compose").any { i.startsWith(it) } }
        assertTrue(libs.isEmpty(), "domain imports a forbidden library: $libs")
        assertTrue(domain.none { (_, i) -> i.startsWith(base + "categories.ui") }, "domain imports ui")
        val foreign = beyondDomain(otherFeatureImports(domain))
        assertTrue(foreign.isEmpty(), "domain reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun walletsImportsNothingFromCategories() {
        val offenders = imports(File(feature, "wallets")).filter { (_, i) -> i.startsWith(base + "categories") }
        assertTrue(offenders.isEmpty(), "wallets imports categories: $offenders")
    }

    @Test
    fun theDesignSystemImportsNoFeature() {
        val offenders = imports(File(main, "designsystem")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(offenders.isEmpty(), "designsystem imports a feature: $offenders")
    }
}
