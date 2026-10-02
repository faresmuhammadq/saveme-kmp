package com.fares.saveme.feature.transactions.data.dto

import kotlinx.serialization.Serializable

/**
 * What a transaction's waiting change holds in `pending_change.payload_json` (research R-04). It is
 * not a wire type; the domain maps its draft to and from it so `data` imports nothing from `domain`.
 * The source wallet is the queue row's scope.
 */
@Serializable
data class TransactionPayload(
    val kind: String,
    /** `YYYY-MM-DD`. */
    val date: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String? = null,
    val counterWalletId: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val counterparty: String? = null,
    val cleared: Boolean = false,
)

/** The plain field names [com.fares.saveme.feature.transactions.data.TransactionSyncHandler.refusalFields] returns. */
object TransactionFields {
    const val AMOUNT = "amount"
    const val DATE = "date"
    const val CATEGORY = "category"
    const val DESTINATION = "destination"
    const val CURRENCY = "currency"
    const val KIND = "kind"
    const val NOTE = "note"
    const val DETAILS = "details"
    const val PERSON = "person"
    const val CLEARED = "cleared"

    val byPointer: Map<String, String> = mapOf(
        "/amount_minor" to AMOUNT,
        "/date" to DATE,
        "/category_id" to CATEGORY,
        "/counter_wallet_id" to DESTINATION,
        "/currency" to CURRENCY,
        "/kind" to KIND,
        "/description" to NOTE,
        "/memo" to DETAILS,
        "/counterparty" to PERSON,
        "/cleared" to CLEARED,
    )
}
