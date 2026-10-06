package com.fares.saveme.feature.account.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** exports.yaml `ExportRequest`: `wallet_id` is left out for the account scope (`explicitNulls = false`). */
@Serializable
data class ExportRequestDto(
    val scope: String,
    @SerialName("wallet_id") val walletId: String? = null,
)

/**
 * exports.yaml `Export`. [status] and [failureCode] stay raw so an unknown value never fails decoding (P-02.3).
 * [downloadUrl] is a 15-minute bearer link to the person's history: [toString] never prints it (P-09.2).
 */
@Serializable
data class ExportDto(
    val id: String,
    val scope: String,
    @SerialName("wallet_id") val walletId: String? = null,
    val status: String,
    @SerialName("failure_code") val failureCode: String? = null,
    @SerialName("requested_at") val requestedAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("download_url_expires_at") val downloadUrlExpiresAt: String? = null,
) {
    override fun toString() =
        "ExportDto(id=$id, scope=$scope, walletId=$walletId, status=$status, failureCode=$failureCode, requestedAt=$requestedAt, " +
            "completedAt=$completedAt, expiresAt=$expiresAt, sizeBytes=$sizeBytes, " +
            "downloadUrl=${downloadUrl?.let { "***" }}, downloadUrlExpiresAt=${downloadUrlExpiresAt?.let { "***" }})"
}
