package com.fares.saveme.feature.billing.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors saveme-backend/docs/openapi/paths/google-play.yaml (v1.15). Responses ignore unknown members (P-02.3); requests
// carry only the declared members (P-02.2). The purchase token is a secret: request types print redacted (P-09.2).

@Serializable
data class PlayPurchaseRequestDto(
    @SerialName("purchase_token") val purchaseToken: String,
    @SerialName("product_id") val productId: String,
) {
    override fun toString() = "PlayPurchaseRequestDto(product=$productId, token=<redacted>)"
}

@Serializable
data class PlayRestoreRequestDto(val purchases: List<PlayPurchaseRequestDto>) {
    override fun toString() = "PlayRestoreRequestDto(count=${purchases.size})"
}

/** The purchase as the server now records it. `state` stays a string so a value added later is not a decoding failure. */
@Serializable
data class PlayPurchaseStateDto(
    @SerialName("product_id") val productId: String,
    val state: String,
    val acknowledged: Boolean = false,
    @SerialName("auto_renew") val autoRenew: Boolean = false,
    @SerialName("current_period_end") val currentPeriodEnd: String? = null,
    val test: Boolean = false,
)

@Serializable
data class PlayPurchaseResultDto(
    val purchase: PlayPurchaseStateDto,
    val entitlements: RedeemEntitlementsDto,
    val subscription: MySubscriptionDto,
)

/** `outcome` stays a string: an unknown value maps to `RestoreOutcome.Unknown` in the domain. */
@Serializable
data class PlayRestoreItemResultDto(
    val index: Int,
    @SerialName("product_id") val productId: String,
    val outcome: String,
)

@Serializable
data class PlayRestoreResultDto(
    val results: List<PlayRestoreItemResultDto> = emptyList(),
    val entitlements: RedeemEntitlementsDto,
    val subscription: MySubscriptionDto,
)

/** US6 only; a value that is not 64 lowercase hex characters is treated as a failed read by the caller. */
@Serializable
data class PlayAccountReferenceDto(@SerialName("obfuscated_account_id") val obfuscatedAccountId: String) {
    /** `^[0-9a-f]{64}$`: anything else is an unreadable answer, not a reference. */
    val isWellFormed: Boolean get() = obfuscatedAccountId.length == 64 && obfuscatedAccountId.all { it in '0'..'9' || it in 'a'..'f' }

    override fun toString() = "PlayAccountReferenceDto(<redacted>)"
}
