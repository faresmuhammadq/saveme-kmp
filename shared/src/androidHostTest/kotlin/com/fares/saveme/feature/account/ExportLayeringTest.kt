package com.fares.saveme.feature.account

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** P-10 and P-09.2 for spec 016: the export code writes no file, shares nothing, reads no entitlement; layers hold (FR-004, FR-015). */
class ExportLayeringTest {

    private val main = File("src/commonMain/kotlin/com/fares/saveme")
    private val account = File(main, "feature/account")
    private val base = "com.fares.saveme.feature."

    private fun sources(root: File) = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun imports(root: File): List<Pair<String, String>> = sources(root)
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }

    private fun code(file: File) = file.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(account.isDirectory, account.absolutePath)

    @Test
    fun exportCodeUsesNoFileDownloadShareOrClipboardApi() {
        val exportFiles = sources(account).filter { "/export/" in it.path.replace('\\', '/') || it.name.startsWith("Export") }
        assertTrue(exportFiles.size >= 15, "found ${exportFiles.map { it.name }}")
        val forbidden = listOf(
            "okio", "java.io.File", "FileSystem", "DownloadManager", "LinkSharer", "ClipboardManager", "LocalClipboard", "setText(",
            "Intent.ACTION_SEND", "rememberSaveable", "SavedStateHandle", "KeyValueStore", "println(",
        )
        val offenders = exportFiles.flatMap { file -> forbidden.filter { token -> code(file).any { token in it } }.map { file.name to it } }
        assertTrue(offenders.isEmpty(), "export code writes, shares or persists: $offenders")
    }

    @Test
    fun exportCodeReadsNoEntitlementOrFeature() {
        val exportFiles = sources(account).filter { "/export/" in it.path.replace('\\', '/') || it.name.startsWith("Export") }
        val offenders = exportFiles.flatMap { file -> listOf("Entitlements", "FeatureValue", "export.enabled", "emailVerified").filter { t -> code(file).any { t in it } }.map { file.name to it } }
        assertTrue(offenders.isEmpty(), "export is not gated: $offenders")
    }

    @Test
    fun accountDataImportsNoDomain() {
        val mine = listOf("ExportApi.kt", "ExportDtos.kt", "AccountApi.kt")
        val offenders = imports(File(account, "data")).filter { (file, _) -> file in mine }.filter { (_, i) -> i.startsWith(base + "account.domain") || i.startsWith(base + "account.ui") }
        assertTrue(offenders.isEmpty(), "account data imports domain or ui: $offenders")
    }

    @Test
    fun accountReachesOtherFeaturesOnlyThroughTheirDomain() {
        // Spec 016's own files: export and deletion code, the account API (older files keep their own history).
        val mineDirs = listOf("/export/", "/deletion/", "ExportApi.kt", "ExportDtos.kt")
        val foreign = sources(account).filter { f -> mineDirs.any { it in f.path.replace('\\', '/') } }
            .flatMap { f -> f.readLines().filter { it.startsWith("import ") }.map { f.name to it.removePrefix("import ").trim() } }.filter { (_, i) ->
            i.startsWith(base) && !i.startsWith(base + "account.") &&
                listOf("auth", "wallets", "transactions", "recurring").any { f -> i.startsWith("$base$f.") } &&
                !listOf("domain", "ui").any { layer -> listOf("auth", "wallets", "transactions", "recurring").any { f -> i.startsWith("$base$f.$layer") } }
        }
        assertTrue(foreign.isEmpty(), "account imports another feature's internals: $foreign")
    }

    @Test
    fun theSyncCoreKnowsNoFeatureType() {
        val offenders = imports(File(main, "core/sync")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(offenders.isEmpty(), "core.sync imports a feature: $offenders")
    }

    @Test
    fun coreImportsNoFeature() {
        val offenders = File(main, "core").listFiles().orEmpty().filter { it.name != "database" }.flatMap { imports(it) }.filter { (_, i) -> i.startsWith(base) }
        assertTrue(offenders.isEmpty(), "core imports a feature: $offenders")
    }
}
