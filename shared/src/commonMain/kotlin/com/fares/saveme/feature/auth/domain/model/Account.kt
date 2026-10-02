package com.fares.saveme.feature.auth.domain.model

import kotlin.time.Instant

enum class AccountState { Active, PendingDeletion }

sealed interface FeatureValue {
    data class Flag(val enabled: Boolean) : FeatureValue
    /** [limit] null means unlimited (billing.yaml Features). */
    data class Quota(val limit: Long?) : FeatureValue
}

/** Read-only copy of the server's User plus the plan's features. Gate on [features], never [plan] (P-12.1). */
data class AccountSnapshot(
    val userId: String,
    val email: String,
    val emailVerified: Boolean,
    val state: AccountState,
    val deletionScheduledFor: Instant?,
    val plan: String,
    val features: Map<String, FeatureValue>,
    /** Display-only, like [plan]; never a gate (P-12.1). Last so positional callers keep compiling. */
    val planId: String = "",
)

enum class AuthField { Email, Password, DisplayName, Token }
