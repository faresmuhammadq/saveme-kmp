package com.fares.saveme.feature.reports.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-06, P-01, FR-026, R-14: every reports string exists in English and Arabic, with the glossary's words and none of the banned ones. */
class ReportStringsTest {

    private val dir = File("src/commonMain/composeResources")
    private val marker = "<!-- Reports (spec 014) -->"

    private fun block(lang: String): String = File(dir, "$lang/strings.xml").readText().substringAfter(marker).substringBefore("</resources>")
        // Only this feature's block: a later feature appends its own after it.
        .let { rest -> Regex("<!-- [^>]*\\(spec \\d+\\) -->").find(rest)?.let { rest.substring(0, it.range.first) } ?: rest }

    private fun names(block: String) = Regex("""<(?:string|plurals|string-array) name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()

    private fun value(block: String, name: String) = Regex("""<string name="$name">([^<]*)</string>""").find(block)?.groupValues?.get(1)

    private fun texts(lang: String) = Regex(""">([^<>]*[^<>\s][^<>]*)</""").findAll(block(lang)).map { it.groupValues[1] }.toList()

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        val en = names(block("values"))
        val ar = names(block("values-ar"))
        assertTrue(en.size >= 45, "expected the reports block: ${en.size}")
        assertEquals(emptySet(), en - ar, "missing in Arabic")
        assertEquals(emptySet(), ar - en, "missing in English")
        assertTrue(en.all { it.startsWith("reports_") || it.startsWith("report_categories_") || it == "catalog_section_reports" }, "every key is prefixed: $en")
    }

    @Test
    fun theGlossaryWordsAreUsed() {
        val en = block("values")
        val ar = block("values-ar")
        assertEquals("Reports", value(en, "reports_title"))
        assertEquals("التقارير", value(ar, "reports_title"))
        assertEquals("Report", value(en, "reports_wallet_action"))
        assertEquals("التقرير", value(ar, "reports_wallet_action"))
        assertEquals("Transfers in", value(en, "reports_transfers_in"))
        assertEquals("تحويلات واردة", value(ar, "reports_transfers_in"))
        assertEquals("Transfers out", value(en, "reports_transfers_out"))
        assertEquals("تحويلات صادرة", value(ar, "reports_transfers_out"))
        assertEquals("Include scheduled", value(en, "reports_include_scheduled"))
        assertEquals("تضمين المجدول", value(ar, "reports_include_scheduled"))
        assertEquals("Custom range", value(en, "reports_custom"))
        assertEquals("فترة مخصصة", value(ar, "reports_custom"))
        assertEquals("Spending by category", value(en, "reports_by_category"))
        assertEquals("Includes changes waiting to sync", value(en, "reports_waiting"))
        assertEquals("Add a wallet to see reports", value(en, "reports_no_wallets"))
        assertEquals("Not included: %1\$s", value(en, "reports_not_included"))
        assertEquals("See %1\$s report", value(en, "reports_see_currency"))
        assertEquals("Pick up to 366 days, start before end", value(en, "reports_custom_problem"))
        assertEquals("Scheduled money is known up to %1\$s", value(en, "reports_scheduled_known_until"))
    }

    @Test
    fun noBannedWordReachesThePerson() {
        // P-01: a wallet is never an "account"; nothing is "advanced"; nothing user-facing says version, scope, cursor or key (R-14, FR-024).
        val english = listOf("account", "advanced", "version", "scope", "cursor", "key", "payload", "percentage of plan")
        texts("values").forEach { text ->
            english.forEach { word -> assertFalse(Regex("\\b$word", RegexOption.IGNORE_CASE).containsMatchIn(text), "'$word' in '$text'") }
        }
        val arabic = listOf("حساب", "متقدم", "نسخة")
        texts("values-ar").forEach { text -> arabic.forEach { word -> assertFalse(text.contains(word), "'$word' in '$text'") } }
    }

    @Test
    fun noExclamationMarksAndWesternDigitsOnly() {
        (texts("values") + texts("values-ar")).forEach { text -> assertFalse(text.contains('!') || text.contains('！'), "exclamation in '$text'") }
        assertFalse(Regex("[٠-٩]").containsMatchIn(block("values-ar")))
    }

    @Test
    fun placeholdersMatchBetweenLanguages() {
        val placeholder = Regex("""%\d\$[sd]""")
        val en = Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(block("values")).associate { it.groupValues[1] to it.groupValues[2] }
        val ar = Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(block("values-ar")).associate { it.groupValues[1] to it.groupValues[2] }
        en.forEach { (key, text) ->
            assertEquals(placeholder.findAll(text).map { it.value }.toSet(), placeholder.findAll(ar.getValue(key)).map { it.value }.toSet(), "placeholders of $key")
        }
    }

    @Test
    fun theKeysTheScreensNeedExist() {
        val needed = setOf(
            "reports_title", "reports_home_row", "reports_wallet_action", "reports_all_wallets", "reports_currency", "reports_previous", "reports_next", "reports_current",
            "reports_custom", "reports_custom_problem", "reports_income", "reports_expenses", "reports_net", "reports_transfers_in", "reports_transfers_out",
            "reports_balance_start", "reports_balance_end", "reports_owed", "reports_by_category", "reports_see_all", "reports_no_spending", "reports_share_under_one",
            "reports_trend", "reports_trend_periods", "reports_trend_summary", "reports_period_column", "reports_include_scheduled", "reports_scheduled_amount",
            "reports_scheduled_known_until", "reports_not_included", "reports_see_currency", "reports_waiting", "reports_offline_updated", "reports_failed_updated",
            "reports_online_only", "reports_too_large", "reports_no_wallets", "reports_add_wallet", "report_categories_title", "reports_scheduled_legend",
            "report_categories_expenses", "report_categories_income",
        )
        assertEquals(emptySet(), needed - names(block("values")))
        assertEquals(emptySet(), needed - names(block("values-ar")))
    }
}
