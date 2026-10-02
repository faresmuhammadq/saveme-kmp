package com.fares.saveme.feature.wallets.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-06: every wallet string exists in English and Arabic, with the glossary's words and no forbidden ones. */
class WalletStringsTest {

    private val dir = File("src/commonMain/composeResources")

    private fun block(lang: String): String {
        val text = File(dir, "$lang/strings.xml").readText()
        return text.substringAfter("<!-- Wallets (spec 006) -->").substringBefore("</resources>")
    }

    private fun names(block: String) = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()

    private fun value(block: String, name: String) =
        Regex("""<string name="$name">([^<]*)</string>""").find(block)?.groupValues?.get(1)

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        val en = names(block("values"))
        val ar = names(block("values-ar"))
        assertTrue(en.size > 60, "expected the wallet block: ${en.size}")
        assertEquals(emptySet(), en - ar, "missing in Arabic")
        assertEquals(emptySet(), ar - en, "missing in English")
    }

    @Test
    fun theGlossaryTermsAreUsed() {
        val ar = block("values-ar")
        assertEquals("حصالة", value(ar, "wallet_type_savings"))
        assertEquals("بنكي", value(ar, "wallet_type_checking"))
        assertEquals("المحافظ", value(ar, "wallets_title"))
        assertEquals("Waiting to sync", value(block("values"), "wallet_status_waiting"))
    }

    @Test
    fun noForbiddenWordsReachThePerson() {
        val forbidden = listOf("token", "version", "tombstone", "حساب")
        listOf("values", "values-ar").forEach { lang ->
            val texts = Regex("""<string name="[^"]+">([^<]*)</string>""").findAll(block(lang)).map { it.groupValues[1] }.toList()
            texts.forEach { text -> forbidden.forEach { word -> assertFalse(text.contains(word, ignoreCase = true), "'$word' in '$text' ($lang)") } }
        }
    }

    @Test
    fun arabicTextUsesWesternDigits() {
        val arabicIndic = Regex("[٠-٩]")
        assertFalse(arabicIndic.containsMatchIn(block("values-ar")))
    }
}
