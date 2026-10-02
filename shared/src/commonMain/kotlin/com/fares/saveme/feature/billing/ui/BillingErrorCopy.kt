package com.fares.saveme.feature.billing.ui

import com.fares.saveme.core.network.ApiErrorCode
import org.jetbrains.compose.resources.StringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_feature_not_entitled

/**
 * Copy for a refusal because of the plan, for later features to reuse (spec 005 FR-007). Chosen by
 * code alone, never server text (P-02.4, P-06.5). Null for any other code.
 */
fun ApiErrorCode.entitlementMessage(): StringResource? =
    if (this == ApiErrorCode.FEATURE_NOT_ENTITLED) Res.string.error_feature_not_entitled else null
