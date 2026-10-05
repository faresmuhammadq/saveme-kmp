package com.fares.saveme.feature.sharing

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-06, P-01: every sharing string exists in English and Arabic, with the glossary's words and none of the banned ones (FR-035). */
class SharingStringsTest {

    private val dir = File("src/commonMain/composeResources")
    private val marker = "<!-- Sharing (spec 013) -->"

    private fun block(lang: String): String = File(dir, "$lang/strings.xml").readText().substringAfter(marker).substringBefore("</resources>")
        // Only this feature's block: a later feature appends its own after it.
        .let { rest -> Regex("<!-- [^>]*\\(spec \\d+\\) -->").find(rest)?.let { rest.substring(0, it.range.first) } ?: rest }

    private fun names(block: String) = Regex("""<(?:string|plurals|string-array) name="([^"]+)"""").findAll(block).map { it.groupValues[1] }.toSet()

    private fun entries(lang: String) = Regex("""<string name="([^"]+)">([^<]*)</string>""").findAll(block(lang)).associate { it.groupValues[1] to it.groupValues[2] }

    private fun texts(lang: String) = entries(lang).values.toList()

    @Test
    fun bothLanguagesHaveTheSameKeys() {
        val en = names(block("values"))
        val ar = names(block("values-ar"))
        assertTrue(en.size > 100, "expected the sharing block: ${en.size}")
        assertEquals(emptySet(), en - ar, "missing in Arabic")
        assertEquals(emptySet(), ar - en, "missing in English")
    }

    @Test
    fun theContractsKeysExistInBothLanguages() {
        val en = entries("values")
        val ar = entries("values-ar")
        listOf(
            "members_title", "members_manage", "members_see", "members_pending_count", "invite_action", "invite_by_email", "invite_by_link",
            "invite_role_editor_hint", "invite_role_viewer_hint", "invite_send", "invite_saved_offline", "invite_link_wait_offline", "invite_share",
            "invite_copy", "invite_copied", "invite_share_text", "invite_supersede", "invite_own_address", "invite_email_invalid", "gate_not_included",
            "gate_full", "gate_see_plan", "invitation_pending", "invitation_expires", "invitation_expired", "invitation_link", "invitation_withdraw",
            "invitation_withdraw_confirm", "invitation_link_gone", "invitation_replace_link", "invitation_not_sent", "join_title", "join_someone",
            "join_until", "join_action", "join_decline", "join_decline_confirm", "join_later", "join_declined", "join_already", "join_open_wallet",
            "join_signed_out", "join_unavailable", "join_invalid_link", "join_owner_plan_full", "join_too_many", "needs_connection", "home_invitation",
            "wallet_joining", "member_make_editor", "member_make_viewer", "member_remove", "member_remove_confirm", "member_changed", "member_only_owner",
            "member_gone", "member_owner_role", "leave_action", "leave_confirm", "leave_confirm_waiting", "leave_owner_note", "leave_done", "access_lost",
            "viewer_note", "viewer_note_plan", "refusal_now_viewer", "plan_note", "activity_title", "activity_window", "activity_offline",
            "activity_automatic", "activity_former_member", "activity_created", "activity_edited", "activity_deleted", "activity_restored",
            "activity_archived", "activity_ownership_transferred", "activity_confirmed", "activity_skipped", "activity_joined", "activity_left",
            "activity_removed", "activity_role_changed", "activity_role_limited_by_plan", "activity_invited", "activity_invitation_revoked",
            "activity_subject_transaction", "activity_subject_rule", "activity_subject_occurrence", "activity_subject_member",
            "activity_subject_invitation", "activity_subject_wallet",
        ).forEach {
            assertTrue(it in en, "missing in English: $it")
            assertTrue(it in ar, "missing in Arabic: $it")
        }
    }

    @Test
    fun theGlossaryWordsAreUsed() {
        val en = entries("values")
        val ar = entries("values-ar")
        assertEquals("Members", en["members_title"])
        assertEquals("الأعضاء", ar["members_title"])
        assertEquals("Activity", en["activity_title"])
        assertEquals("النشاط", ar["activity_title"])
        assertEquals("Invite", en["invite_action"])
        assertEquals("دعوة", ar["invite_action"])
        assertEquals("Expired", en["invitation_expired"])
        assertEquals("منتهية", ar["invitation_expired"])
        assertEquals("Pending", en["invitation_pending"])
        assertEquals("معلّقة", ar["invitation_pending"])
    }

    @Test
    fun noBannedWordReachesThePerson() {
        // P-01: the glossary's Invitation, Wallet Member and Activity; nothing user-facing says version, tombstone, scope or cursor.
        val english = listOf("invite link", "join request", "participant", "collaborator", "friend", "group", "household", "family", "token", "tombstone", "scope", "cursor", "payload")
        texts("values").forEach { text ->
            english.forEach { word -> assertFalse(Regex("\\b$word", RegexOption.IGNORE_CASE).containsMatchIn(text), "'$word' in '$text'") }
            assertFalse(Regex("\\bversion\\b", RegexOption.IGNORE_CASE).containsMatchIn(text), "'version' in '$text'")
        }
        val arabic = listOf("مشارك", "متعاون", "صديق", "مجموعة", "عائلة", "نسخة")
        // Whole words only: "مشاركة" (sharing) is wanted and merely starts with "مشارك" (participant).
        texts("values-ar").forEach { text ->
            arabic.forEach { word -> assertFalse(Regex("(^|[\\s،.:])$word($|[\\s،.:])").containsMatchIn(text), "'$word' in '$text'") }
        }
    }

    @Test
    fun theWordAccountIsNeverUsedForAWallet() {
        texts("values").filter { Regex("\\baccount\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }.forEach {
            assertFalse(Regex("wallet account|account of the wallet", RegexOption.IGNORE_CASE).containsMatchIn(it), it)
        }
    }

    @Test
    fun noExclamationMarksAndArabicUsesWesternDigits() {
        (texts("values") + texts("values-ar")).forEach { text ->
            assertFalse(text.contains('!') || text.contains('！'), "exclamation in '$text'")
        }
        assertFalse(Regex("[٠-٩]").containsMatchIn(block("values-ar")))
    }

    @Test
    fun placeholdersMatchBetweenLanguages() {
        val placeholder = Regex("""%\d\$[sd]""")
        val en = entries("values")
        val ar = entries("values-ar")
        en.forEach { (key, text) ->
            assertEquals(
                placeholder.findAll(text).map { it.value }.toSet(),
                placeholder.findAll(ar.getValue(key)).map { it.value }.toSet(),
                "placeholders of $key",
            )
        }
    }

    @Test
    fun theShareTextNamesNoWalletAndNoAmount() {
        // FR-006: the sentence takes the app name and the link only.
        val text = entries("values").getValue("invite_share_text")
        assertEquals(setOf("%1\$s", "%2\$s"), Regex("""%\d\$[sd]""").findAll(text).map { it.value }.toSet())
    }
}
