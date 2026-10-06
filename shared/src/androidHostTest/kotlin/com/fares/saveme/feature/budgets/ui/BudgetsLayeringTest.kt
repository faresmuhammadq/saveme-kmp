package com.fares.saveme.feature.budgets.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10: dependencies flow ui -> domain -> data. The budgets UI never imports a data package, data imports nothing from its
 * domain, the UI or another feature (queue payloads live in data), and the domain imports no Ktor, Compose or Room. Other
 * features are reached only through their `domain` package; no feature but budgets imports budgets - what lower features
 * need goes through hooks they own (research R-01, R-05, R-10, R-11); the sync core imports no feature; nothing in budgets
 * touches a notification API (FR-026).
 */
class BudgetsLayeringTest {

    private val main = File("src/commonMain/kotlin/com/fares/saveme")
    private val feature = File(main, "feature")
    private val budgets = File(feature, "budgets")
    private val base = "com.fares.saveme.feature."

    private fun imports(root: File): List<Pair<String, String>> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
        .toList()

    private fun imports(dir: String) = imports(File(budgets, dir))

    private fun otherFeatureImports(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> i.startsWith(base) && !i.startsWith(base + "budgets.") }

    private fun beyondDomain(list: List<Pair<String, String>>) =
        list.filter { (_, i) -> !i.substringAfter(base).substringAfter('.').startsWith("domain") }

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(budgets.isDirectory, "run from :shared: ${budgets.absolutePath}")

    @Test
    fun uiImportsNoDataPackageAndOtherFeaturesOnlyThroughTheirDomain() {
        val ui = imports("ui")
        assertTrue(ui.isNotEmpty())
        val data = ui.filter { (_, i) -> i.startsWith(base + "budgets.data") }
        assertTrue(data.isEmpty(), "ui imports data: $data")
        val foreign = beyondDomain(otherFeatureImports(ui))
        assertTrue(foreign.isEmpty(), "ui reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun dataImportsNothingFromDomainUiOrOtherFeatures() {
        val data = imports("data")
        assertTrue(data.isNotEmpty())
        assertTrue(data.none { (_, i) -> i.startsWith(base + "budgets.ui") }, "data imports ui")
        assertTrue(data.none { (_, i) -> i.startsWith(base + "budgets.domain") }, "data imports domain: ${data.filter { (_, i) -> i.startsWith(base + "budgets.domain") }}")
        assertTrue(otherFeatureImports(data).isEmpty(), "data reaches another feature: ${otherFeatureImports(data)}")
    }

    @Test
    fun domainImportsNoNetworkComposeOrDatabaseLibrariesAndOtherFeaturesOnlyThroughDomain() {
        val domain = imports("domain")
        assertTrue(domain.isNotEmpty())
        val libs = domain.filter { (_, i) -> listOf("io.ktor", "androidx.compose", "androidx.room", "org.jetbrains.compose").any { i.startsWith(it) } }
        assertTrue(libs.isEmpty(), "domain imports a forbidden library: $libs")
        assertTrue(domain.none { (_, i) -> i.startsWith(base + "budgets.ui") }, "domain imports ui")
        val foreign = beyondDomain(otherFeatureImports(domain))
        assertTrue(foreign.isEmpty(), "domain reaches another feature beyond its domain: $foreign")
    }

    @Test
    fun noFeatureOtherThanBudgetsImportsBudgetsAndCoreImportsOnlyItsDatabaseRegistry() {
        feature.listFiles().orEmpty().filter { it.isDirectory && it.name != "budgets" }.forEach { dir ->
            // Reports (spec 014) reuses budgets' pure period derivation and day ticker through budgets' domain, so the two can never
            // disagree on a Budget Period (research R-07); nothing else of budgets, and no other feature.
            val allowed = if (dir.name == "reports") listOf("com.fares.saveme.feature.budgets.domain.BudgetPeriods", "com.fares.saveme.feature.budgets.domain.DayTicker") else emptyList()
            val offenders = imports(dir).filter { (_, i) -> i.startsWith(base + "budgets") && i !in allowed }
            assertTrue(offenders.isEmpty(), "${dir.name} imports budgets: $offenders")
        }
        // The Room registry (core.database) lists every feature's entities by design; nothing else in core may.
        val core = File(main, "core").listFiles().orEmpty().filter { it.name != "database" }.flatMap { imports(it) }.filter { (_, i) -> i.startsWith(base + "budgets") }
        assertTrue(core.isEmpty(), "core imports budgets: $core")
        val design = imports(File(main, "designsystem")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(design.isEmpty(), "designsystem imports a feature: $design")
    }

    @Test
    fun theSyncCoreImportsNoFeature() {
        val sync = imports(File(main, "core/sync")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(sync.isEmpty(), "core.sync imports a feature: $sync")
    }

    @Test
    fun theSyncExtensionPointsAreImplementedInDataAndImportOnlyTheirCoreContract() {
        val data = File(budgets, "data")
        mapOf("BudgetFeedApplier" to "FeedApplier", "BudgetSyncHandler" to "SyncHandler").forEach { (name, contract) ->
            val source = data.walkTopDown().firstOrNull { it.isFile && it.extension == "kt" && "class $name" in it.readText() }
            assertTrue(source != null, "$name is declared in budgets.data")
            val imports = source!!.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }
            assertTrue("com.fares.saveme.core.sync.$contract" in imports, "$name implements the core $contract")
            assertTrue(imports.none { it.startsWith(base + "budgets.domain") || it.startsWith(base + "budgets.ui") }, "$name imports domain or ui")
        }
    }

    @Test
    fun budgetsReadsTransactionsOnlyThroughTheOwnSpendingHookAndNeverTouchesNotifications() {
        val all = imports(budgets)
        val transactions = all.filter { (_, i) -> i.startsWith(base + "transactions") }
        assertTrue(transactions.isNotEmpty())
        assertTrue(transactions.all { (_, i) -> i.startsWith(base + "transactions.domain.") }, "budgets reads transactions beyond its domain: $transactions")
        val notifications = all.filter { (_, i) -> i.contains("otification") }
        assertTrue(notifications.isEmpty(), "budgets references a notification API: $notifications")
        val text = budgets.walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(!Regex("""\b(NotificationManager|NotificationCompat|WorkManager|UNUserNotification)\b""").containsMatchIn(text))
    }
}
