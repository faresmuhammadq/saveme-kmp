package com.fares.saveme.feature.billing.ui

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.RetryClass
import com.fares.saveme.feature.auth.ui.message
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_feature_not_entitled
import saveme.shared.generated.resources.error_redeem_code_email_mismatch
import saveme.shared.generated.resources.error_redeem_code_expired
import saveme.shared.generated.resources.error_redeem_code_used
import saveme.shared.generated.resources.redeem_in_progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BillingErrorCopyTest {

    @Test
    fun featureNotEntitledHasItsOwnCopy() {
        assertEquals(ApiErrorCode.FEATURE_NOT_ENTITLED, ApiErrorCode.from("FEATURE_NOT_ENTITLED"))
        assertEquals(RetryClass.Never, ApiErrorCode.FEATURE_NOT_ENTITLED.retryClass)
        assertEquals(Res.string.error_feature_not_entitled, ApiErrorCode.FEATURE_NOT_ENTITLED.entitlementMessage())
        assertEquals(Res.string.error_feature_not_entitled, ApiErrorCode.FEATURE_NOT_ENTITLED.message())
        assertNull(ApiErrorCode.FORBIDDEN.entitlementMessage())
    }

    @Test
    fun theRedeemCodesHaveTheirOwnCopyAndNoOtherCodeDoes() {
        assertEquals(Res.string.error_redeem_code_email_mismatch, ApiErrorCode.REDEEM_CODE_EMAIL_MISMATCH.redeemMessage())
        assertEquals(Res.string.error_redeem_code_used, ApiErrorCode.REDEEM_CODE_USED.redeemMessage())
        assertEquals(Res.string.error_redeem_code_expired, ApiErrorCode.REDEEM_CODE_EXPIRED.redeemMessage())
        assertEquals(Res.string.error_redeem_code_used, ApiErrorCode.REDEEM_CODE_USED.message())
        assertEquals(Res.string.redeem_in_progress, ApiErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS.message())
        assertNull(ApiErrorCode.NOT_FOUND.redeemMessage())
        assertNull(ApiErrorCode.IDEMPOTENCY_KEY_REUSED.redeemMessage())
    }
}
