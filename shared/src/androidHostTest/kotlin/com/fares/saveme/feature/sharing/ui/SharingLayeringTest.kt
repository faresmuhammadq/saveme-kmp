package com.fares.saveme.feature.sharing.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10: dependencies flow ui -> domain -> data. Sharing's UI never imports its data package, its data imports nothing from its
 * domain, UI or any other feature (the link reaches the holder through an injected lambda), its domain imports no Ktor, Compose
 * or Room. Other features are reached only through their `domain` package; no feature but sharing imports sharing - what
 * wallets and auth need goes through hooks they own (research R-15, R-16); the sync core imports no feature (P-10.6, P-10.7).
 */
class SharingLayeringTest {

    private val main = File("src/commonMain/kotlin/com/fares/saveme")
    private val feature = File(main, "feature")
    private val sharing = File(feature, "sharing")
    private val base = "com.fares.saveme.feature."

    private fun imports(root: File): List<Pair<String, String>> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
        .toList()

    private fun imports(dir: String) = imports(File(sharing, dir))

    private fun otherFeatureImports(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> i.startsWith(base) && !i.startsWith(base + "sharing.") }

    private fun beyondDomain(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> !i.substringAfter(base).substringAfter('.').startsWith("domain") }

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(sharing.isDirectory, "run from :shared: ${sharing.absolutePath}")

    @Test
    fun uiImportsNoDataPackageAndOtherFeaturesOnlyThroughTheirDomain() {
        val ui = imports("ui")
        assertTrue(ui.isNotEmpty())
        val data = ui.filter { (_, i) -> i.startsWith(base + "sharing.data") }
        assertTrue(data.isEmpty(), "ui imports data: $data")
        val foreign = beyondDomain(otherFeatureImports(ui))
        assertTrue(foreign.isEmpty(), "ui reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun dataImportsNothingFromDomainUiOrOtherFeatures() {
        val data = imports("data")
        assertTrue(data.isNotEmpty())
        assertTrue(data.none { (_, i) -> i.startsWith(base + "sharing.ui") }, "data imports ui")
        assertTrue(data.none { (_, i) -> i.startsWith(base + "sharing.domain") }, "data imports domain: ${data.filter { (_, i) -> i.startsWith(base + "sharing.domain") }}")
        assertTrue(otherFeatureImports(data).isEmpty(), "data reaches another feature: ${otherFeatureImports(data)}")
    }

    @Test
    fun domainImportsNoNetworkComposeOrDatabaseLibrariesAndOtherFeaturesOnlyThroughDomain() {
        val domain = imports("domain")
        assertTrue(domain.isNotEmpty())
        val libs = domain.filter { (_, i) -> listOf("io.ktor", "androidx.compose", "androidx.room", "org.jetbrains.compose").any { i.startsWith(it) } }
        assertTrue(libs.isEmpty(), "domain imports a forbidden library: $libs")
        assertTrue(domain.none { (_, i) -> i.startsWith(base + "sharing.ui") }, "domain imports ui")
        val foreign = beyondDomain(otherFeatureImports(domain))
        assertTrue(foreign.isEmpty(), "domain reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun noFeatureOtherThanSharingImportsSharingAndCoreImportsOnlyItsDatabaseRegistry() {
        feature.listFiles().orEmpty().filter { it.isDirectory && it.name != "sharing" }.forEach { dir ->
            val offenders = imports(dir).filter { (_, i) -> i.startsWith(base + "sharing") }
            assertTrue(offenders.isEmpty(), "${dir.name} imports sharing: $offenders")
        }
        // The Room registry (core.database) lists every feature's entities by design; nothing else in core may.
        val core = File(main, "core").listFiles().orEmpty().filter { it.name != "database" }.flatMap { imports(it) }.filter { (_, i) -> i.startsWith(base) }
        assertTrue(core.isEmpty(), "core imports a feature: $core")
        val design = imports(File(main, "designsystem")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(design.isEmpty(), "designsystem imports a feature: $design")
    }

    @Test
    fun theSyncCoreImportsNoFeatureAndTheExtensionPointsAreImplementedInData() {
        val sync = imports(File(main, "core/sync")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(sync.isEmpty(), "core.sync imports a feature: $sync")
        val data = File(sharing, "data")
        mapOf("InvitationFeedApplier" to "FeedApplier", "InvitationSyncHandler" to "SyncHandler").forEach { (name, contract) ->
            val source = data.walkTopDown().firstOrNull { it.isFile && it.extension == "kt" && "class $name" in it.readText() }
            assertTrue(source != null, "$name is declared in sharing.data")
            val imports = source!!.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }
            assertTrue("com.fares.saveme.core.sync.$contract" in imports, "$name implements the core $contract")
            assertTrue(imports.none { it.startsWith(base + "sharing.domain") || it.startsWith(base + "sharing.ui") }, "$name imports domain or ui")
        }
    }

    @Test
    fun theGateReadsEntitlementFeaturesAndNeverThePlansName() {
        val text = sharing.walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(!Regex("""\bplanName\b|\bplan\.name\b|\.planId\b|"free"|"plus"|"pro"""").containsMatchIn(text), "a plan label gates something (P-12.1)")
    }
}
