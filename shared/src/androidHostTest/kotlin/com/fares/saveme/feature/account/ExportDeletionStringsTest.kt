package com.fares.saveme.feature.account

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Spec 016 copy: every key in both languages, no banned word, the Play notice on its three surfaces (FR-018, FR-030, R-12). */
class ExportDeletionStringsTest {

    private val resources = File("src/commonMain/composeResources")
    private val banned = listOf("xlsx", "excel", "encrypt", "token", "إكسل", "مشفر")

    private fun block(dir: String): String {
        val text = File(resources, "$dir/strings.xml").readText()
        return text.substring(text.indexOf("<!-- Export & deletion (spec 016) -->"))
    }

    private fun keys(dir: String): Set<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(block(dir)).map { it.groupValues[1] }.toSet()

    private fun texts(dir: String): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(block(dir)).associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun everySpec016KeyExistsInBothLanguages() {
        val en = keys("values")
        val ar = keys("values-ar")
        assertTrue(en.size >= 70, "found ${en.size}")
        assertTrue(en == ar, "missing in one language: ${(en - ar) + (ar - en)}")
        val contract = listOf(
            "account_export", "wallet_export", "exports_title", "exports_new", "export_explain", "export_in_progress", "delete_explain_shared",
            "delete_play_notice", "delete_export_first", "delete_owners_title", "delete_pending_title", "delete_being_deleted",
            "tx_added_by_former", "recurring_author_other", "recurring_author_former", "export_limit_hours", "export_limit_minutes",
        )
        assertTrue(en.containsAll(contract), "missing ${contract - en}")
    }

    @Test
    fun noSpec016CopyClaimsAFormatEncryptionOrAToken() {
        listOf("values", "values-ar").forEach { dir ->
            val all = block(dir)
            banned.forEach { word -> assertTrue(!all.contains(word, ignoreCase = true), "$dir mentions \"$word\"") }
        }
    }

    @Test
    fun theExplanationNamesZipJsonCsvAndTheTwentyFourHours() {
        val en = texts("values").getValue("export_explain")
        listOf("ZIP", "JSON", "CSV", "24 hours", "email").forEach { assertTrue(en.contains(it), "export_explain lacks $it") }
        val ar = texts("values-ar").getValue("export_explain")
        listOf("ZIP", "JSON", "CSV", "24").forEach { assertTrue(ar.contains(it), "arabic export_explain lacks $it") }
    }

    @Test
    fun thePlayNoticeIsShownOnTheExplanationTheReviewAndThePendingView() {
        val screen = File("src/commonMain/kotlin/com/fares/saveme/feature/account/ui/deleteaccount/DeleteAccountScreen.kt").readText()
        listOf("delete.explain.play", "delete.review.play", "delete.pending.play").forEach { tag ->
            assertTrue(screen.contains("\"$tag\""), "no Play notice tagged $tag")
        }
        assertTrue(Regex("""PlayNotice\(Modifier""").findAll(screen).count() >= 3)
    }

    @Test
    fun arabicUsesWesternDigitsOnly() {
        val arabicIndic = Regex("[٠-٩۰-۹]")
        assertTrue(!arabicIndic.containsMatchIn(block("values-ar")), "Arabic-Indic digits in the spec 016 block")
    }
}
