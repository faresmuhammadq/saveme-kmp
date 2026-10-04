package com.fares.saveme.feature.budgets.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-06, P-01: every budgets string exists in English and Arabic, with the glossary's words and none of the banned ones (FR-015, FR-025). */
class BudgetStringsTest {

    private val dir = File("src/commonMain/composeResources")
    private val marker = "<!-- Budgets (spec 012) -->"

    private fun block(lang: String): String = File(dir, "$lang/strings.xml").readText().substringAfter(marker).substringBefore("</resources>")

    private fun names(block: String) = Regex("""<(?:string|plurals|string-array) name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()

    private fun value(block: String, name: String) = Regex("""<string name="$name">([^<]*)</string>""").find(block)?.groupValues?.get(1)

    private fun texts(lang: String) = Regex(""">([^<>]*[^<>\s][^<>]*)</""").findAll(block(lang)).map { it.groupValues[1] }.toList()

    @Test
    fun bothLanguagesHaveTheSameKeysAllPrefixed() {
        val en = names(block("values"))
        val ar = names(block("values-ar"))
        assertTrue(en.size > 70, "expected the budgets block: ${en.size}")
        assertEquals(emptySet(), en - ar, "missing in Arabic")
        assertEquals(emptySet(), ar - en, "missing in English")
        assertTrue(en.all { it.startsWith("budgets_") }, "every key is prefixed")
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
        assertEquals("Budgets", value(en, "budgets_title"))
        assertEquals("الميزانيات", value(ar, "budgets_title"))
        assertEquals("Budget period", value(en, "budgets_field_period"))
        assertEquals("فترة الميزانية", value(ar, "budgets_field_period"))
        assertEquals("All expenses", value(en, "budgets_all_expenses"))
        assertEquals("كل المصروفات", value(ar, "budgets_all_expenses"))
        assertEquals("Limit", value(en, "budgets_field_limit"))
        assertEquals("الحد", value(ar, "budgets_field_limit"))
        assertEquals("On pace for %1\$s", value(en, "budgets_on_pace"))
        assertEquals("بهذا المعدل ستصل إلى %1\$s", value(ar, "budgets_on_pace"))
        assertEquals("Already budgeted", value(en, "budgets_already_budgeted"))
        assertEquals("له ميزانية بالفعل", value(ar, "budgets_already_budgeted"))
        assertEquals("Includes changes waiting to sync", value(en, "budgets_includes_waiting"))
        assertEquals("Progress appears after sync", value(en, "budgets_unknown_progress"))
        assertEquals("From %1\$s, periods start on day %2\$d", value(en, "budgets_pending_start"))
        assertEquals("من %1\$s تبدأ الفترات في اليوم %2\$d", value(ar, "budgets_pending_start"))
        assertEquals("Also spent in %1\$s (not counted)", value(en, "budgets_other_currencies"))
    }

    @Test
    fun noBannedWordReachesThePerson() {
        // P-01: a budget is never an envelope or a plan; nothing user-facing says version, tombstone, scope or cursor (FR-025).
        val english = listOf("envelope", "plan", "tombstone", "scope", "cursor", "payload", "limit plan")
        texts("values").forEach { text ->
            english.forEach { word -> assertFalse(Regex("\\b$word", RegexOption.IGNORE_CASE).containsMatchIn(text), "'$word' in '$text'") }
            assertFalse(Regex("\\bversion\\b", RegexOption.IGNORE_CASE).containsMatchIn(text), "'version' in '$text'")
        }
        val arabic = listOf("ظرف", "خطة", "نسخة")
        texts("values-ar").forEach { text -> arabic.forEach { word -> assertFalse(text.contains(word), "'$word' in '$text'") } }
    }

    @Test
    fun noAlarmWordingAndNoExclamationMarks() {
        (texts("values") + texts("values-ar")).forEach { text ->
            assertFalse(text.contains('!') || text.contains('！'), "exclamation in '$text'")
        }
        val alarm = listOf("warning", "danger", "alert", "overspent", "exceeded", "careful", "stop")
        texts("values").forEach { text -> alarm.forEach { word -> assertFalse(Regex("\\b$word", RegexOption.IGNORE_CASE).containsMatchIn(text), "'$word' in '$text'") } }
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
            "budgets_title", "budgets_cd_add", "budgets_set", "budgets_empty", "budgets_section_archived", "budgets_days_left", "budgets_pending_start",
            "budgets_on_pace", "budgets_other_currencies", "budgets_includes_waiting", "budgets_unknown_progress", "budgets_already_budgeted",
            "budgets_immutable", "budgets_archive_body", "budgets_delete_body", "budgets_keep_mine", "budgets_use_theirs", "budgets_accept_deletion",
            "budgets_refusal_category", "budgets_home_see_all", "budgets_catalog_all", "budgets_delete_anyway",
        ).forEach { assertTrue(it in en, "missing $it") }
    }
}
