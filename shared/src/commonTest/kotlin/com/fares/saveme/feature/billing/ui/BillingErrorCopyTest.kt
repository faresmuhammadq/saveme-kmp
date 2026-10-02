package com.fares.saveme.feature.billing.ui

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.RetryClass
import com.fares.saveme.feature.auth.ui.message
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_feature_not_entitled
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
}
