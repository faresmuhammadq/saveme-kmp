package com.fares.saveme.feature.wallets.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10: dependencies flow ui → domain → data. The wallet UI never imports a data package, data never
 * imports ui (and only the domain's model classes), and the domain imports no Ktor, Compose or Room.
 * Other features are reached only through their `domain` package.
 */
class WalletsLayeringTest {

    private val feature = File("src/commonMain/kotlin/com/fares/saveme/feature")
    private val wallets = File(feature, "wallets")

    private fun imports(dir: String): List<Pair<String, String>> = File(wallets, dir).walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
        .toList()

    private val base = "com.fares.saveme.feature."

    private fun otherFeatureImports(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> i.startsWith(base) && !i.startsWith(base + "wallets.") }

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(wallets.isDirectory, "run from :shared: ${wallets.absolutePath}")

    @Test
    fun uiImportsNoDataPackageAndOtherFeaturesOnlyThroughTheirDomain() {
        val ui = imports("ui")
        assertTrue(ui.isNotEmpty())
        val data = ui.filter { (_, i) -> i.startsWith(base + "wallets.data") }
        assertTrue(data.isEmpty(), "ui imports data: $data")
        val foreign = otherFeatureImports(ui).filter { (_, i) -> !i.substringAfter(base).substringAfter('.').startsWith("domain") }
        assertTrue(foreign.isEmpty(), "ui reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun dataImportsNeitherUiNorDomainServices() {
        val data = imports("data")
        val ui = data.filter { (_, i) -> i.startsWith(base + "wallets.ui") }
        assertTrue(ui.isEmpty(), "data imports ui: $ui")
        val domain = data.filter { (_, i) -> i.startsWith(base + "wallets.domain") && !i.startsWith(base + "wallets.domain.model.") }
        assertTrue(domain.isEmpty(), "data imports domain beyond its model classes: $domain")
        assertTrue(otherFeatureImports(data).isEmpty(), "data reaches another feature: ${otherFeatureImports(data)}")
    }

    @Test
    fun domainImportsNoNetworkComposeOrDatabaseLibrariesAndOtherFeaturesOnlyThroughDomain() {
        val domain = imports("domain")
        val libs = domain.filter { (_, i) -> listOf("io.ktor", "androidx.compose", "androidx.room", "org.jetbrains.compose").any { i.startsWith(it) } }
        assertTrue(libs.isEmpty(), "domain imports a forbidden library: $libs")
        val ui = domain.filter { (_, i) -> i.startsWith(base + "wallets.ui") }
        assertTrue(ui.isEmpty(), "domain imports ui: $ui")
        val foreign = otherFeatureImports(domain).filter { (_, i) -> !i.substringAfter(base).substringAfter('.').startsWith("domain") }
        assertTrue(foreign.isEmpty(), "domain reaches another feature beyond its domain: $foreign")
    }
}
