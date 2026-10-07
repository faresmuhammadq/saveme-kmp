package com.fares.saveme.feature.billing.ui

import com.fares.saveme.core.network.ApiErrorCode
import org.jetbrains.compose.resources.StringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.billing_play_another_account
import saveme.shared.generated.resources.billing_play_not_verified
import saveme.shared.generated.resources.error_feature_not_entitled
import saveme.shared.generated.resources.error_redeem_code_email_mismatch
import saveme.shared.generated.resources.error_redeem_code_expired
import saveme.shared.generated.resources.error_redeem_code_used

/**
 * Copy for a refusal because of the plan, for later features to reuse (spec 005 FR-007). Chosen by
 * code alone, never server text (P-02.4, P-06.5). Null for any other code.
 */
fun ApiErrorCode.entitlementMessage(): StringResource? =
    if (this == ApiErrorCode.FEATURE_NOT_ENTITLED) Res.string.error_feature_not_entitled else null

/** Copy for a redeem refusal that has its own catalogue code (spec 015 FR-028); null for any other code. */
fun ApiErrorCode.redeemMessage(): StringResource? = when (this) {
    ApiErrorCode.REDEEM_CODE_EMAIL_MISMATCH -> Res.string.error_redeem_code_email_mismatch
    ApiErrorCode.REDEEM_CODE_USED -> Res.string.error_redeem_code_used
    ApiErrorCode.REDEEM_CODE_EXPIRED -> Res.string.error_redeem_code_expired
    else -> null
}

/** Copy for a Google Play purchase refusal that has its own catalogue code (spec 018 FR-052); null for any other code. */
fun ApiErrorCode.playMessage(): StringResource? = when (this) {
    ApiErrorCode.PURCHASE_ALREADY_LINKED -> Res.string.billing_play_another_account
    ApiErrorCode.PURCHASE_NOT_VERIFIED -> Res.string.billing_play_not_verified
    else -> null
}
