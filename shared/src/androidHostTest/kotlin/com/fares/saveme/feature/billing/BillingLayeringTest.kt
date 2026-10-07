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

    /** FR-011, FR-029: the typed code and the attempt key are never saved, restored, stored or put on the clipboard. */
    @Test
    fun theRedeemCodePathSavesNothingStoresNothingAndWritesNoClipboard() {
        val redeemFiles = billing.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name.startsWith("Redeem") }.toList() +
            File(main, "designsystem/component/Fields.kt")
        assertTrue(redeemFiles.size >= 6, "found ${redeemFiles.map { it.name }}")
        val forbidden = listOf("rememberSaveable", "SavedStateHandle", "KeyValueStore", "Clipboard", "setText(", "println(")
        val offenders = redeemFiles.flatMap { file ->
            val lines = file.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }
            forbidden.filter { token -> lines.any { token in it } }.map { file.name to it }
        }
        assertTrue(offenders.isEmpty(), "the redeem path persists or copies: $offenders")
    }

    /** Spec 018 R-2, P-10.6: the Google Play port sits in data, imports no domain, and no Android or Play Billing type reaches common code. */
    @Test
    fun theGooglePlayPortImportsNoDomainAndNoPlatformTypeReachesCommonCode() {
        val port = imports(File(billing, "data/play"))
        assertTrue(port.isNotEmpty())
        val offenders = port.filter { (_, i) -> i.startsWith(base + "billing.domain") || i.startsWith(base + "billing.ui") || i.startsWith(base + "auth.") }
        assertTrue(offenders.isEmpty(), "the port reaches into domain: $offenders")
        val platform = imports(main).filter { (_, i) -> i.startsWith("android.") || i.startsWith("com.android.billingclient") }
        assertTrue(platform.isEmpty(), "an Android or Play Billing type in commonMain: $platform")
    }

    /** FR-002, FR-004: nothing under `shared/src` (or the host app) acknowledges or consumes a Google Play purchase. */
    @Test
    fun noSourceAcknowledgesOrConsumesAPurchaseAndThePortHasNoSuchMember() {
        val roots = listOf(File("src"), File("../androidApp/src"))
        val forbidden = listOf("acknowledgePurchase", "consumeAsync", "consumePurchase", "AcknowledgePurchaseParams", "ConsumeParams")
        val offenders = roots.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/androidHostTest/") }
                .filter { file -> file.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }.any { line -> forbidden.any { it in line } } }
                .map { it.name }.toList()
        }
        assertTrue(offenders.isEmpty(), "acknowledges or consumes: $offenders")
        val members = File(billing, "data/play/PlayBillingClient.kt").readLines()
            .dropWhile { !it.contains("interface PlayBillingClient") }.takeWhile { it.trim() != "}" }
            .filter { it.trimStart().startsWith("suspend fun ") || it.trimStart().startsWith("fun ") || it.trimStart().startsWith("val ") }
        assertTrue(members.isNotEmpty() && members.none { it.contains("acknowledge", ignoreCase = true) || it.contains("consume", ignoreCase = true) }, "$members")
    }

    /** FR-001, FR-040, SC-007: while the switch is off no code path can start a purchase: only the (gated) port member and the unread switch exist. */
    @Test
    fun noReachableCodePathLaunchesAPurchaseOrReadsTheAccountReference() {
        val callers = main.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }.any { "launchPurchase(" in it || "playAccountReference(" in it || "inAppSubscribe" in it } }
            .map { it.name }.toSet()
        // The port, its three implementations, the API method and the config field declare them; nothing else may call them.
        val declaring = setOf(
            "PlayBillingClient.kt", "UnavailablePlayBillingClient.kt", "ScriptedPlayBillingClient.kt", "BillingApi.kt", "AppConfig.kt",
        )
        assertTrue(callers.all { it in declaring }, "a purchase path is reachable: ${callers - declaring}")
    }

    /** FR-003: billing calls are online-only; nothing in billing touches the sync engine or its outbox. */
    @Test
    fun noBillingSourceTouchesTheSyncEngineOrItsOutbox() {
        val offenders = imports(billing).filter { (_, i) -> i.startsWith("com.fares.saveme.core.sync") || i.contains("PendingChange") }
        assertTrue(offenders.isEmpty(), "billing queues work in the outbox: $offenders")
    }
}
