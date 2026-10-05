package com.fares.saveme.feature.billing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** P-12 and the glossary: no redeem copy names the payment path, a price or a banned word; every key exists in both languages. */
class RedeemStringsTest {

    private val resources = File("src/commonMain/composeResources")
    private val banned = listOf("instapay", "إنستاباي", "token", "voucher", "coupon", "promo", "gift card", "http", "www.") +
        listOf("EGP", "USD", "EUR", "SAR", "AED", "جنيه")

    private fun strings(dir: String): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File(resources, "$dir/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun isRedeemKey(key: String) = key.startsWith("redeem_") || key == "plan_enter_code" || key.startsWith("error_redeem_code_")

    @Test
    fun everyRedeemKeyExistsInBothLanguages() {
        val en = strings("values").keys.filter(::isRedeemKey).toSet()
        val ar = strings("values-ar").keys.filter(::isRedeemKey).toSet()
        assertTrue(en.size >= 27, "found $en")
        assertTrue(en == ar, "missing in one language: ${en.symmetricDifference(ar)}")
    }

    @Test
    fun noRedeemCopyMentionsThePaymentPathAPriceOrABannedWord() {
        listOf("values", "values-ar").forEach { dir ->
            strings(dir).filterKeys(::isRedeemKey).forEach { (key, text) ->
                banned.forEach { word -> assertTrue(!text.contains(word, ignoreCase = true), "$dir/$key mentions \"$word\"") }
            }
        }
    }

    private fun <T> Set<T>.symmetricDifference(other: Set<T>) = (this - other) + (other - this)
}
