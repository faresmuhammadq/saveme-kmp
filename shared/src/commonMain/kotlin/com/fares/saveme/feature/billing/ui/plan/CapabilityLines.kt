package com.fares.saveme.feature.billing.ui.plan

import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.FlagFeature
import com.fares.saveme.feature.billing.domain.QuotaFeature
import com.fares.saveme.feature.billing.domain.QuotaLimit
import org.jetbrains.compose.resources.StringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.plan_cap_export_off
import saveme.shared.generated.resources.plan_cap_export_on
import saveme.shared.generated.resources.plan_cap_reports_off
import saveme.shared.generated.resources.plan_cap_reports_on
import saveme.shared.generated.resources.plan_cap_sharing_members
import saveme.shared.generated.resources.plan_cap_sharing_off
import saveme.shared.generated.resources.plan_cap_sharing_unlimited
import saveme.shared.generated.resources.plan_cap_wallets_max
import saveme.shared.generated.resources.plan_cap_wallets_unlimited

/**
 * One line of "what this plan allows": a string resource, its number if it has one, and whether
 * the line grants something ([included] false for "not included" lines).
 */
data class CapabilityLine(val text: StringResource, val count: Long? = null, val included: Boolean = true)

/**
 * What a plan allows, in display order. A missing capability reads as not granted, like the gate;
 * a capability this app does not know produces no line (billing.yaml: ignore unknown keys).
 */
fun capabilityLines(entitlements: EntitlementSet): List<CapabilityLine> = buildList {
    when (val wallets = entitlements.quota(QuotaFeature.Wallets)) {
        QuotaLimit.Unlimited -> add(CapabilityLine(Res.string.plan_cap_wallets_unlimited))
        is QuotaLimit.Limited -> add(CapabilityLine(Res.string.plan_cap_wallets_max, wallets.max))
    }
    if (entitlements.can(FlagFeature.SharingEnabled)) {
        when (val members = entitlements.quota(QuotaFeature.MembersPerWallet)) {
            QuotaLimit.Unlimited -> add(CapabilityLine(Res.string.plan_cap_sharing_unlimited))
            is QuotaLimit.Limited -> add(CapabilityLine(Res.string.plan_cap_sharing_members, members.max))
        }
    } else {
        add(CapabilityLine(Res.string.plan_cap_sharing_off, included = false))
    }
    val export = entitlements.can(FlagFeature.ExportEnabled)
    add(CapabilityLine(if (export) Res.string.plan_cap_export_on else Res.string.plan_cap_export_off, included = export))
    val reports = entitlements.can(FlagFeature.ReportsAdvanced)
    add(CapabilityLine(if (reports) Res.string.plan_cap_reports_on else Res.string.plan_cap_reports_off, included = reports))
}
