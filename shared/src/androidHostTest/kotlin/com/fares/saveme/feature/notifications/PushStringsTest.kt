package com.fares.saveme.feature.notifications

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Glossary and P-06 for spec 017's copy: no engineering words, both languages, Western digits (FR-041, spec §2). */
class PushStringsTest {

    private val resources = File("src/commonMain/composeResources")
    private val marker = "<!-- Push (spec 017) -->"
    private val banned = listOf("token", "fcm", "firebase", "instapay", "إنستاباي", "توكن")

    private fun block(dir: String): Map<String, String> {
        val text = File(resources, "$dir/strings.xml").readText()
        val start = text.indexOf(marker)
        assertTrue(start >= 0, "$dir has the spec 017 block")
        return Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text.substring(start))
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun everyKeyExistsInBothLanguages() {
        val en = block("values")
        val ar = block("values-ar")
        assertTrue(en.size >= 55, "found ${en.size}")
        assertEquals(en.keys, ar.keys, "missing in one language: ${(en.keys - ar.keys) + (ar.keys - en.keys)}")
    }

    @Test
    fun noCopyNamesTheEngineeringOrPaymentWords() {
        listOf("values", "values-ar").forEach { dir ->
            block(dir).forEach { (key, text) ->
                banned.forEach { word -> assertTrue(!text.contains(word, ignoreCase = true), "$dir/$key mentions \"$word\"") }
            }
        }
    }

    @Test
    fun noDigitIsArabicIndicAndPlaceholdersMatchBetweenLanguages() {
        val en = block("values")
        val ar = block("values-ar")
        ar.forEach { (key, text) ->
            assertTrue(text.none { it in '٠'..'٩' || it in '۰'..'۹' }, "$key has non-Western digits")
            val placeholders = { s: String -> Regex("""%\d\$[sd]""").findAll(s).map { it.value }.sorted().toList() }
            assertEquals(placeholders(en.getValue(key)), placeholders(text), "$key placeholders differ")
        }
    }

    @Test
    fun noKeyIsDefinedTwiceInTheBlockOrAlsoAbove() {
        listOf("values", "values-ar").forEach { dir ->
            val text = File(resources, "$dir/strings.xml").readText()
            val names = Regex("""<string name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList()
            assertEquals(names.size, names.toSet().size, "$dir defines a key twice: ${names.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
    }
}
