package com.fares.saveme.feature.transactions.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-06, P-01: every transaction string exists in English and Arabic, with the glossary's words and no banned ones. */
class TransactionStringsTest {

    private val dir = File("src/commonMain/composeResources")

    private fun block(lang: String): String {
        val text = File(dir, "$lang/strings.xml").readText()
        return text.substringAfter("<!-- Transactions (spec 008) -->").substringBefore("</resources>")
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
        assertTrue(en.size > 70, "expected the transactions block: ${en.size}")
        assertEquals(emptySet(), en - ar, "missing in Arabic")
        assertEquals(emptySet(), ar - en, "missing in English")
    }

    @Test
    fun theGlossaryTermsAreUsed() {
        val ar = block("values-ar")
        val en = block("values")
        assertEquals("المعاملات", value(ar, "wallet_transactions"))
        assertEquals("إضافة معاملة", value(ar, "transactions_add"))
        assertEquals("مصروف", value(ar, "tx_kind_expense"))
        assertEquals("دخل", value(ar, "tx_kind_income"))
        assertEquals("تحويل", value(ar, "tx_kind_transfer"))
        assertEquals("Transactions", value(en, "wallet_transactions"))
        assertEquals("Add transaction", value(en, "home_add_transaction"))
        assertEquals("Transfer", value(en, "tx_kind_transfer"))
    }

    @Test
    fun noBannedWordsReachThePerson() {
        // Glossary banned synonyms and internal terms (spec FR-034): matched at word starts.
        val english = listOf("entry", "record", "movement", "operation", "posting", "version", "tombstone", "token", "scope", "counter wallet")
        texts("values").forEach { text ->
            english.forEach { word -> assertFalse(Regex("\\b$word", RegexOption.IGNORE_CASE).containsMatchIn(text), "'$word' in '$text'") }
        }
        val arabic = listOf("قيد", "سجل", "نسخة", "حساب")
        texts("values-ar").forEach { text -> arabic.forEach { word -> assertFalse(text.contains(word), "'$word' in '$text'") } }
    }

    @Test
    fun arabicTextUsesWesternDigits() {
        assertFalse(Regex("[٠-٩]").containsMatchIn(block("values-ar")))
    }

    @Test
    fun everyKeyOfTheContractExists() {
        val en = names(block("values"))
        listOf(
            "home_add_transaction", "wallet_transactions", "transactions_add", "transactions_empty", "transactions_empty_viewer",
            "transactions_no_matches", "transactions_today", "transactions_yesterday", "transactions_updated_at", "transactions_refresh_failed",
            "transactions_search", "transactions_filters", "filter_this_month", "filter_last_month", "filter_custom", "filter_from", "filter_to",
            "filter_kind", "filter_category", "filter_apply", "filter_clear_all", "tx_kind_expense", "tx_kind_income", "tx_kind_transfer",
            "tx_kind_unknown", "tx_transfer_to", "tx_transfer_from", "tx_another_wallet", "tx_field_wallet", "tx_field_amount", "tx_field_category",
            "tx_field_destination", "tx_field_date", "tx_field_note", "tx_more", "tx_field_details", "tx_field_person", "tx_field_cleared",
            "tx_cleared_help", "tx_kind_locked_note", "tx_wallets_locked_note", "tx_destinations_empty", "tx_added_by", "tx_added_by_other",
            "tx_from_recurring", "tx_no_wallet", "tx_create_wallet", "tx_save", "tx_delete", "tx_delete_title", "tx_delete_body", "tx_gone",
            "tx_error_amount_empty", "tx_error_amount_positive", "tx_error_amount_digits", "tx_error_amount_range", "tx_error_date_range",
            "tx_error_category_required", "tx_error_destination_required", "tx_error_too_long", "tx_status_clash", "tx_status_deleted",
            "tx_status_not_saved", "tx_reason_not_verified", "tx_reason_gone", "tx_reason_no_right", "tx_reason_no_right_other", "tx_reason_category",
            "tx_reason_destination", "tx_reason_currency_changed", "tx_reason_currencies_differ", "tx_reason_invalid", "tx_reason_other",
            "tx_this_phone", "tx_other_device", "action_keep_transaction",
        ).forEach { assertTrue(it in en || it in File(dir, "values/strings.xml").readText(), "missing $it") }
    }
}
