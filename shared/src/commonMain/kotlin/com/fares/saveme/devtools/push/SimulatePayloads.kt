package com.fares.saveme.devtools.push

/** Identifiers taken from records on the phone; null where there is none. */
data class PhoneIds(val walletId: String?, val occurrenceId: String?, val budgetId: String?)

/** One row of the tool: the wire type, and whether an id had to be invented. */
data class SimulateRow(val type: String, val notOnPhone: Boolean)

/** The data block of one simulated message, exactly the contract's keys for its type (contract PushPayload). */
object SimulatePayloads {
    /** The 12 contract types, then "Unknown type" (shown last, wire `future_type`). */
    val types: List<String> = listOf(
        "occurrence_due", "occurrence_auto_confirm_refused", "occurrences_due_summary", "budget_exceeded", "invitation_received",
        "member_role_changed", "member_removed", "wallet_ownership_received", "subscription_ending", "subscription_lapsed",
        "export_ready", "deletion_reminder", UNKNOWN,
    )

    const val UNKNOWN = "future_type"

    private val category = mapOf(
        "occurrence_due" to "recurring_due", "occurrence_auto_confirm_refused" to "recurring_due", "occurrences_due_summary" to "recurring_due",
        "budget_exceeded" to "budget_exceeded", "invitation_received" to "sharing", "member_role_changed" to "sharing",
        "member_removed" to "sharing", "wallet_ownership_received" to "sharing", "subscription_ending" to "billing",
        "subscription_lapsed" to "billing", "export_ready" to "export_ready", "deletion_reminder" to "deletion_reminder", UNKNOWN to "sharing",
    )

    /** Which ids a type carries. */
    private fun needs(type: String): List<String> = when (type) {
        "occurrence_due", "occurrence_auto_confirm_refused" -> listOf("wallet_id", "occurrence_id")
        "budget_exceeded" -> listOf("budget_id")
        "invitation_received" -> listOf("invitation_id")
        "member_role_changed" -> listOf("wallet_id", "role")
        "member_removed", "wallet_ownership_received" -> listOf("wallet_id")
        "export_ready" -> listOf("export_id")
        else -> emptyList()
    }

    /** A row is "not on this phone" when any id it carries had to be invented. */
    fun rows(ids: PhoneIds): List<SimulateRow> =
        types.map { type -> SimulateRow(type, notOnPhone = needs(type).any { it != "role" && fromPhone(it, ids) == null }) }

    private fun fromPhone(key: String, ids: PhoneIds): String? = when (key) {
        "wallet_id" -> ids.walletId
        "occurrence_id" -> ids.occurrenceId
        "budget_id" -> ids.budgetId
        else -> null
    }

    /** [randomId] supplies a fresh UUID for ids the phone does not have and for `notification_id`. */
    fun build(type: String, ids: PhoneIds, randomId: () -> String): Map<String, String> {
        val data = linkedMapOf("schema" to "1", "type" to type, "category" to (category[type] ?: "sharing"), "notification_id" to randomId())
        needs(type).forEach { key ->
            data[key] = if (key == "role") "editor" else fromPhone(key, ids) ?: randomId()
        }
        return data
    }
}
