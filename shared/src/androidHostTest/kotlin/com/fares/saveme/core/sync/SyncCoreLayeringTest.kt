package com.fares.saveme.core.sync

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10.7: `core.sync` imports no feature. Features implement `SyncHandler`, `FeedApplier` and `WalletScopes`; the
 * puller and the runner learn wallets, appliers and outcomes only through those (spec 009 research R-01).
 */
class SyncCoreLayeringTest {

    private val sync = File("src/commonMain/kotlin/com/fares/saveme/core/sync")

    @Test
    fun theSyncCoreIsWhereTheTestExpectsIt() = assertTrue(sync.isDirectory, "run from :shared: ${sync.absolutePath}")

    @Test
    fun coreSyncImportsNoFeaturePackage() {
        val offenders = sync.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
            .filter { (_, import) -> import.startsWith("com.fares.saveme.feature.") }
            .toList()
        assertTrue(offenders.isEmpty(), "core.sync imports a feature: $offenders")
    }

    @Test
    fun coreSyncNamesNoFeatureType() {
        // The sync core knows rows by their wire `type` strings only, never a feature's table or DTO.
        val featureTypes = listOf("WalletEntity", "TransactionEntity", "CategoryEntity", "ProfileEntity", "WalletDto", "TransactionDto", "RefreshOutcome")
        val offenders = sync.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }.map { file.name to it } }
            .filter { (_, line) -> featureTypes.any { it in line } }
            .toList()
        assertTrue(offenders.isEmpty(), "core.sync mentions a feature type: $offenders")
    }
}
