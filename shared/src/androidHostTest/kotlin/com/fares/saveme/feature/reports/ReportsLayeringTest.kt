package com.fares.saveme.feature.reports

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10: dependencies flow ui -> domain -> data. The reports UI never imports a data package, data imports only `core`, the
 * domain imports no Ktor, Compose or Room, and other features are reached only through their `domain` package; no feature
 * but reports imports reports - what reports needs from lower features goes through hooks they own (research R-02, R-03,
 * R-05, R-08); the sync core imports no feature; nothing in reports references Room, a notification API or `reports.advanced`
 * (FR-024).
 */
class ReportsLayeringTest {

    private val main = File("src/commonMain/kotlin/com/fares/saveme")
    private val feature = File(main, "feature")
    private val reports = File(feature, "reports")
    private val base = "com.fares.saveme.feature."

    private fun imports(root: File): List<Pair<String, String>> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
        .toList()

    private fun imports(dir: String) = imports(File(reports, dir))

    private fun otherFeatureImports(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> i.startsWith(base) && !i.startsWith(base + "reports.") }

    private fun beyondDomain(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> !i.substringAfter(base).substringAfter('.').startsWith("domain") }

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(reports.isDirectory, "run from :shared: ${reports.absolutePath}")

    @Test
    fun uiImportsNoDataPackageAndOtherFeaturesOnlyThroughTheirDomain() {
        val ui = imports("ui")
        assertTrue(ui.isNotEmpty())
        val data = ui.filter { (_, i) -> i.startsWith(base + "reports.data") }
        assertTrue(data.isEmpty(), "ui imports data: $data")
        val foreign = beyondDomain(otherFeatureImports(ui))
        assertTrue(foreign.isEmpty(), "ui reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun dataImportsOnlyCore() {
        val data = imports("data")
        assertTrue(data.isNotEmpty())
        assertTrue(data.none { (_, i) -> i.startsWith(base + "reports.ui") }, "data imports ui")
        assertTrue(data.none { (_, i) -> i.startsWith(base + "reports.domain") }, "data imports domain")
        assertTrue(otherFeatureImports(data).isEmpty(), "data reaches another feature: ${otherFeatureImports(data)}")
    }

    @Test
    fun domainImportsNoNetworkComposeOrDatabaseLibrariesAndOtherFeaturesOnlyThroughDomain() {
        val domain = imports("domain")
        assertTrue(domain.isNotEmpty())
        val libs = domain.filter { (_, i) -> listOf("io.ktor", "androidx.compose", "androidx.room", "org.jetbrains.compose").any { i.startsWith(it) } }
        assertTrue(libs.isEmpty(), "domain imports a forbidden library: $libs")
        assertTrue(domain.none { (_, i) -> i.startsWith(base + "reports.ui") }, "domain imports ui")
        val foreign = beyondDomain(otherFeatureImports(domain))
        assertTrue(foreign.isEmpty(), "domain reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun noFeatureOtherThanReportsImportsReportsAndCoreImportsNone() {
        feature.listFiles().orEmpty().filter { it.isDirectory && it.name != "reports" }.forEach { dir ->
            val offenders = imports(dir).filter { (_, i) -> i.startsWith(base + "reports") }
            assertTrue(offenders.isEmpty(), "${dir.name} imports reports: $offenders")
        }
        val core = File(main, "core").listFiles().orEmpty().flatMap { imports(it) }.filter { (_, i) -> i.startsWith(base + "reports") }
        assertTrue(core.isEmpty(), "core imports reports: $core")
        val design = imports(File(main, "designsystem")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(design.isEmpty(), "designsystem imports a feature: $design")
    }

    @Test
    fun theSyncCoreImportsNoFeature() {
        val sync = imports(File(main, "core/sync")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(sync.isEmpty(), "core.sync imports a feature: $sync")
    }

    @Test
    fun reportsReachesLowerFeaturesOnlyThroughTheirHooksAndServices() {
        val all = imports(reports)
        val lower = all.filter { (_, i) -> listOf("transactions", "recurring", "categories", "wallets", "budgets", "account", "auth").any { i.startsWith(base + it + ".") } }
        assertTrue(lower.isNotEmpty())
        assertTrue(lower.all { (_, i) -> i.substringAfter(base).substringAfter('.').startsWith("domain") }, "reports reads a lower feature beyond its domain: $lower")
        // Reports never reads a table: no DAO, entity or database import anywhere.
        val tables = all.filter { (_, i) -> i.contains(".data.local.") || i.startsWith("androidx.room") || i.startsWith("com.fares.saveme.core.database") }
        assertTrue(tables.isEmpty(), "reports reads a table: $tables")
        // Budget Periods come from budgets' own pure object, so the two features can never disagree.
        assertTrue(all.any { (_, i) -> i == "com.fares.saveme.feature.budgets.domain.BudgetPeriods" })
    }

    @Test
    fun nothingInReportsTouchesNotificationsOrTheReservedAdvancedFeature() {
        val text = reports.walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(!Regex("""\b(NotificationManager|NotificationCompat|WorkManager|UNUserNotification)\b""").containsMatchIn(text))
        assertTrue("reports.advanced" !in text, "reports must not read the reserved plan feature (FR-024)")
        assertTrue(!Regex("""\bEntitlements\b""").containsMatchIn(text), "no plan feature gates reports")
    }

    @Test
    fun theLookupAndTheProbeAreTheOnlyCallersOfTheReportEndpoints() {
        val callers = reports.walkTopDown().filter { it.isFile && it.extension == "kt" && "ReportsApi" in it.readText() && "domain" in it.path }.map { it.name }.toSet()
        assertTrue(callers.all { it in setOf("ReportsService.kt", "ReportParityProbe.kt") }, "unexpected callers of ReportsApi: $callers")
    }
}
