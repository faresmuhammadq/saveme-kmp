package com.fares.saveme.feature.categories.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-06, P-01: every category string exists in English and Arabic, with the glossary's words and no banned ones. */
class CategoryStringsTest {

    private val dir = File("src/commonMain/composeResources")

    private fun block(lang: String): String {
        val text = File(dir, "$lang/strings.xml").readText()
        return text.substringAfter("<!-- Categories (spec 007) -->").substringBefore("</resources>")
    }

    private fun names(block: String) = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()

    private fun value(block: String, name: String) =
        Regex("""<string name="$name">([^<]*)</string>""").find(block)?.groupValues?.get(1)

    private fun texts(lang: String) =
        Regex("""<string name="[^"]+">([^<]*)</string>""").findAll(block(lang)).map { it.groupValues[1] }.toList()

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        val en = names(block("values"))
        val ar = names(block("values-ar"))
        assertTrue(en.size > 60, "expected the categories block: ${en.size}")
        assertEquals(emptySet(), en - ar, "missing in Arabic")
        assertEquals(emptySet(), ar - en, "missing in English")
    }

    @Test
    fun everyKeyOfTheContractExistsAndTheIconAndColourDescriptionsAreComplete() {
        val en = names(block("values"))
        listOf(
            "home_categories", "categories_title", "category_kind_expense", "category_kind_income", "categories_new", "categories_own",
            "categories_own_empty", "categories_defaults", "category_default", "categories_defaults_missing", "categories_archived",
            "category_archived", "categories_updated_at", "categories_refresh_failed", "category_unavailable", "category_gone",
            "category_field_kind", "category_field_name", "category_field_icon", "category_field_color", "category_none",
            "category_kind_locked_note", "category_error_name_blank", "category_error_name_long", "category_status_clash",
            "category_status_not_saved", "category_status_in_use", "category_this_phone", "category_other_device", "category_keep_mine",
            "category_use_theirs", "action_keep_category", "action_archive_instead", "category_refusal_not_verified",
            "category_refusal_gone", "category_refusal_other", "category_archive", "category_archive_title", "category_archive_body",
            "category_delete", "category_delete_title", "category_delete_body", "category_in_use_note", "category_choice_title",
        ).forEach { assertTrue(it in en, "missing $it") }
        assertEquals(24, en.count { it.startsWith("cd_category_icon_") })
        assertEquals(8, en.count { it.startsWith("cd_category_color_") })
    }

    @Test
    fun theGlossaryTermsAreUsed() {
        val ar = block("values-ar")
        assertEquals("الفئات", value(ar, "categories_title"))
        assertEquals("افتراضية", value(ar, "category_default"))
        assertEquals("فئة جديدة", value(ar, "categories_new"))
        assertEquals("Waiting to sync", value(block("values"), "category_status_waiting"))
        assertEquals("Not deleted: this category is in use.", value(block("values"), "category_status_in_use"))
    }

    @Test
    fun noBannedWordsReachThePerson() {
        val english = listOf("tag", "label", "bucket", "record", "entry", "version", "tombstone", "token")
        texts("values").forEach { text ->
            english.forEach { word -> assertFalse(Regex("\\b$word", RegexOption.IGNORE_CASE).containsMatchIn(text), "'$word' in '$text'") }
        }
        val arabic = listOf("وسم", "ملصق", "تصنيف", "نسخة")
        texts("values-ar").forEach { text -> arabic.forEach { word -> assertFalse(text.contains(word), "'$word' in '$text'") } }
    }

    @Test
    fun arabicTextUsesWesternDigits() {
        assertFalse(Regex("[٠-٩]").containsMatchIn(block("values-ar")))
    }
}
