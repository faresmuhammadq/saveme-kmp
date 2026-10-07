package com.fares.saveme.feature.billing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** P-01, P-12, SC-005: every Google Play key is in both languages and none says what the glossary bans or names a price. */
class GooglePlayStringsTest {

    private val resources = File("src/commonMain/composeResources")
    private val banned = listOf("token", "instapay", "إنستاباي", "acknowledge", "order", "verify", "تحقق") +
        listOf("EGP", "USD", "EUR", "SAR", "AED", "جنيه", "http", "www.")
    private val priceLiteral = Regex("""\d+[.,]\d{2}""")

    private fun strings(dir: String): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File(resources, "$dir/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** The player-facing keys; the development-only screen's `play_dev_*`, `dev_tools_*` keys are exempt from the glossary. */
    private fun isPlayKey(key: String) = key in setOf("plan_restore", "plan_current_test", "plan_activated") ||
        key.startsWith("plan_play_") || key.startsWith("plan_restore_") || key.startsWith("billing_play_") || key.startsWith("plan_subscribe")

    private fun isDevKey(key: String) = key.startsWith("play_dev_") || key == "dev_tools_play_billing"

    @Test
    fun everyGooglePlayKeyExistsInBothLanguages() {
        val en = strings("values").keys.filter { isPlayKey(it) || isDevKey(it) }.toSet()
        val ar = strings("values-ar").keys.filter { isPlayKey(it) || isDevKey(it) }.toSet()
        assertTrue(en.size >= 30, "found $en")
        assertTrue(en == ar, "missing in one language: ${(en - ar) + (ar - en)}")
    }

    @Test
    fun theCopyNamesNoBannedWordNoPaymentPathAndNoPrice() {
        listOf("values", "values-ar").forEach { dir ->
            strings(dir).filterKeys(::isPlayKey).forEach { (key, text) ->
                banned.forEach { word -> assertTrue(!text.contains(word, ignoreCase = true), "$dir/$key mentions \"$word\"") }
                assertTrue(!priceLiteral.containsMatchIn(text), "$dir/$key carries a price literal")
            }
        }
    }

    @Test
    fun everyRefusalAndRestoreOutcomeHasItsOwnKey() {
        val keys = strings("values").keys
        listOf(
            "plan_restore_done", "plan_restore_nothing", "plan_restore_pending", "plan_restore_unavailable", "plan_play_not_available",
            "plan_restore_offline", "plan_restore_busy", "billing_play_another_account", "billing_play_not_verified", "plan_play_open_failed",
        ).forEach { assertTrue(it in keys, "missing $it") }
    }

    /** FR-001, FR-040: the Subscribe keys exist for the gated flow but no source references them while the switch is off. */
    @Test
    fun theSubscribeKeysAreReferencedByNoSource() {
        val sources = File("src").walkTopDown().filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/androidHostTest/") && !it.invariantSeparatorsPath.contains("/commonTest/") }.toList()
        val offenders = sources.filter { file -> file.readText().let { "plan_subscribe" in it } }.map { it.name }
        assertTrue(offenders.isEmpty(), "a Subscribe string is referenced: $offenders")
    }

    @Test
    fun theBlockIsAppendOnlyAtTheEndOfBothFiles() {
        listOf("values", "values-ar").forEach { dir ->
            val text = File(resources, "$dir/strings.xml").readText()
            val marker = text.indexOf("<!-- Google Play billing (spec 018) -->")
            assertTrue(marker > 0, "$dir has no spec 018 block")
            val after = text.substring(marker)
            assertTrue(Regex("""<string name="([^"]+)">""").findAll(after).all { isPlayKey(it.groupValues[1]) || isDevKey(it.groupValues[1]) }, "$dir: something else sits in the block")
        }
    }
}
