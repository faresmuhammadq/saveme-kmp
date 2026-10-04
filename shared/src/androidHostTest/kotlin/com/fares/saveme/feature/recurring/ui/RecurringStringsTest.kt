package com.fares.saveme.feature.recurring.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-06, P-01: every recurring string exists in English and Arabic, with the glossary's words and none of the banned ones. */
class RecurringStringsTest {

    private val dir = File("src/commonMain/composeResources")
    private val marker = "<!-- Recurring (spec 010) -->"

    private fun block(lang: String): String = File(dir, "$lang/strings.xml").readText().substringAfter(marker).substringBefore("</resources>")

    private fun names(block: String) = Regex("""<(?:string|plurals|string-array) name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()

    private fun value(block: String, name: String) = Regex("""<string name="$name">([^<]*)</string>""").find(block)?.groupValues?.get(1)

    private fun texts(lang: String) = Regex(""">([^<>]*[^<>\s][^<>]*)</""").findAll(block(lang)).map { it.groupValues[1] }.toList()

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        val en = names(block("values"))
        val ar = names(block("values-ar"))
        assertTrue(en.size > 150, "expected the recurring block: ${en.size}")
        assertEquals(emptySet(), en - ar, "missing in Arabic")
        assertEquals(emptySet(), ar - en, "missing in English")
        assertTrue(en.all { it.startsWith("recurring_") }, "every key is prefixed")
    }

    @Test
    fun arabicPluralsCarryEveryCategory() {
        val ar = block("values-ar")
        Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(ar).forEach { match ->
            listOf("zero", "one", "two", "few", "many", "other").forEach { q ->
                assertTrue("""quantity="$q"""" in match.groupValues[2], "${match.groupValues[1]} lacks $q")
            }
        }
    }

    @Test
    fun theGlossaryWordsAreUsed() {
        val en = block("values")
        val ar = block("values-ar")
        assertEquals("Recurring rule", value(en, "recurring_rule_title"))
        assertEquals("قاعدة متكررة", value(ar, "recurring_rule_title"))
        assertEquals("Due", value(en, "recurring_state_due"))
        assertEquals("مستحقة", value(ar, "recurring_state_due"))
        assertEquals("فائتة", value(ar, "recurring_state_missed"))
        assertEquals("القبض", value(ar, "recurring_payout"))
        assertEquals("جمعية", value(ar, "recurring_preset_gameya"))
        assertEquals("قسط", value(ar, "recurring_preset_installment"))
        assertEquals("تأكيد تلقائي", value(ar, "recurring_auto_confirm"))
        assertEquals("Dates appear after sync", value(en, "recurring_dates_after_sync"))
        assertEquals("Already confirmed", value(en, "recurring_notice_already"))
        assertEquals("Check for new dates", value(en, "recurring_action_catch_up"))
    }

    @Test
    fun noBannedWordsReachThePerson() {
        // Glossary synonyms for a rule and an occurrence, and internal terms (FR-037).
        val english = listOf(
            "schedule", "repeat", "recurrence", "instance", "run", "version", "tombstone", "materialise", "materialize", "scope", "token",
            "counter wallet", "entry", "record", "label", "bucket",
        )
        texts("values").forEach { text ->
            english.forEach { word -> assertFalse(Regex("\\b$word", RegexOption.IGNORE_CASE).containsMatchIn(text), "'$word' in '$text'") }
        }
        val arabic = listOf("قيد", "سجل", "نسخة", "حساب", "تصنيف")
        texts("values-ar").forEach { text -> arabic.forEach { word -> assertFalse(text.contains(word), "'$word' in '$text'") } }
    }

    @Test
    fun arabicTextUsesWesternDigits() {
        assertFalse(Regex("[٠-٩]").containsMatchIn(block("values-ar")))
    }

    @Test
    fun placeholdersMatchBetweenLanguages() {
        val placeholder = Regex("""%\d\$[sd]""")
        val en = Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(block("values")).associate { it.groupValues[1] to it.groupValues[2] }
        val ar = Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(block("values-ar")).associate { it.groupValues[1] to it.groupValues[2] }
        en.forEach { (key, text) ->
            assertEquals(
                placeholder.findAll(text).map { it.value }.toSet(),
                placeholder.findAll(ar.getValue(key)).map { it.value }.toSet(),
                "placeholders of $key",
            )
        }
    }

    @Test
    fun theKeysTheScreensNeedExist() {
        val en = names(block("values"))
        listOf(
            "recurring_title", "recurring_add", "recurring_empty", "recurring_section_due", "recurring_section_upcoming",
            "recurring_state_missed", "recurring_cadence_monthly", "recurring_weekdays", "recurring_field_preset", "recurring_auto_help",
            "recurring_frozen_reason", "recurring_reason_other", "recurring_action_change_from", "recurring_end_body", "recurring_confirm",
            "recurring_match_offer", "recurring_due_count", "recurring_catalog_section",
        ).forEach { assertTrue(it in en, "missing $it") }
    }
}
