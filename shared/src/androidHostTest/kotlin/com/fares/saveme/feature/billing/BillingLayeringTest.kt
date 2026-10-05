package com.fares.saveme.feature.billing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P-10 for billing (spec 015 added redeem): ui -> domain -> data; billing data imports no auth data and no domain; billing
 * reaches auth only through its domain; core imports no feature; no client source builds the claim URL (P-12.4).
 */
class BillingLayeringTest {

    private val main = File("src/commonMain/kotlin/com/fares/saveme")
    private val billing = File(main, "feature/billing")
    private val base = "com.fares.saveme.feature."

    private fun imports(root: File): List<Pair<String, String>> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file -> file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() } }
        .toList()

    @Test
    fun theSourceTreeIsWhereTheTestExpectsIt() = assertTrue(billing.isDirectory, "run from :shared: ${billing.absolutePath}")

    @Test
    fun billingDataImportsNoAuthDataAndNoBillingDomainOrUi() {
        val data = imports(File(billing, "data"))
        assertTrue(data.isNotEmpty())
        val offenders = data.filter { (_, i) ->
            i.startsWith(base + "auth.data") || i.startsWith(base + "billing.domain") || i.startsWith(base + "billing.ui")
        }
        assertTrue(offenders.isEmpty(), "billing data reaches beyond itself: $offenders")
    }

    @Test
    fun billingReachesAuthOnlyThroughItsDomain() {
        val foreign = imports(billing).filter { (_, i) -> i.startsWith(base + "auth.") && !i.startsWith(base + "auth.domain") && !i.startsWith(base + "auth.ui") }
        assertTrue(foreign.isEmpty(), "billing imports auth internals: $foreign")
    }

    @Test
    fun billingUiImportsNoBillingData() {
        val data = imports(File(billing, "ui")).filter { (_, i) -> i.startsWith(base + "billing.data") }
        assertTrue(data.isEmpty(), "billing ui imports data: $data")
    }

    @Test
    fun coreImportsNoFeature() {
        // The Room registry (core.database) lists every feature's entities by design; nothing else in core may.
        val offenders = File(main, "core").listFiles().orEmpty().filter { it.name != "database" }.flatMap { imports(it) }.filter { (_, i) -> i.startsWith(base) }
        assertTrue(offenders.isEmpty(), "core imports a feature: $offenders")
        val design = imports(File(main, "designsystem")).filter { (_, i) -> i.startsWith(base) }
        assertTrue(design.isEmpty(), "designsystem imports a feature: $design")
    }

    @Test
    fun noClientSourceBuildsTheClaimUrl() {
        val offenders = main.walkTopDown().filter { it.isFile && it.extension == "kt" && it.readText().contains("instapay/claims") }.map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "builds the claim URL: $offenders")
    }
}
