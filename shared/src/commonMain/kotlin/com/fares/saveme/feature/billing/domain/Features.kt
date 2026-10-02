package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.domain.model.FeatureValue

/** Boolean capabilities a plan grants. Wire keys live here and nowhere else (research R-04). */
enum class FlagFeature(val wireKey: String) {
    SharingEnabled("sharing.enabled"),
    ExportEnabled("export.enabled"),
    ReportsAdvanced("reports.advanced"),
}

/** Counted capabilities a plan grants. */
enum class QuotaFeature(val wireKey: String) {
    MembersPerWallet("sharing.max_members_per_wallet"),
    Wallets("wallets.max"),
}

sealed interface QuotaLimit {
    data object Unlimited : QuotaLimit
    data class Limited(val max: Long) : QuotaLimit
}

/**
 * What a plan allows. A missing key, or one of the wrong type, means not granted (false, or a
 * quota of 0); only an explicit null quota is unlimited (billing.yaml Features).
 */
data class EntitlementSet(val features: Map<String, FeatureValue>) {

    fun can(flag: FlagFeature): Boolean = (features[flag.wireKey] as? FeatureValue.Flag)?.enabled ?: false

    fun quota(quota: QuotaFeature): QuotaLimit = when (val value = features[quota.wireKey]) {
        is FeatureValue.Quota -> value.limit?.let { QuotaLimit.Limited(it) } ?: QuotaLimit.Unlimited
        else -> QuotaLimit.Limited(0)
    }

    fun allowsAnother(quota: QuotaFeature, currentCount: Int): Boolean = when (val limit = quota(quota)) {
        QuotaLimit.Unlimited -> true
        is QuotaLimit.Limited -> currentCount < limit.max
    }
}
