package com.fares.saveme.feature.billing.ui.plan

import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.billing.domain.EntitlementSet
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
import kotlin.test.Test
import kotlin.test.assertEquals

class CapabilityLinesTest {

    private fun lines(vararg features: Pair<String, FeatureValue>) = capabilityLines(EntitlementSet(mapOf(*features)))

    @Test
    fun sharingOffHidesTheMembersQuota() {
        val lines = lines(
            "sharing.enabled" to FeatureValue.Flag(false),
            "sharing.max_members_per_wallet" to FeatureValue.Quota(1),
            "wallets.max" to FeatureValue.Quota(null),
        )
        assertEquals(CapabilityLine(Res.string.plan_cap_sharing_off, included = false), lines[1])
        assertEquals(false, lines.any { it.text == Res.string.plan_cap_sharing_members })
    }

    @Test
    fun sharingOnShowsMembersOrUnlimited() {
        val limited = lines("sharing.enabled" to FeatureValue.Flag(true), "sharing.max_members_per_wallet" to FeatureValue.Quota(5))
        assertEquals(CapabilityLine(Res.string.plan_cap_sharing_members, 5), limited[1])
        val unlimited = lines("sharing.enabled" to FeatureValue.Flag(true), "sharing.max_members_per_wallet" to FeatureValue.Quota(null))
        assertEquals(CapabilityLine(Res.string.plan_cap_sharing_unlimited), unlimited[1])
    }

    @Test
    fun walletsUnlimitedOrUpToN() {
        assertEquals(CapabilityLine(Res.string.plan_cap_wallets_unlimited), lines("wallets.max" to FeatureValue.Quota(null))[0])
        assertEquals(CapabilityLine(Res.string.plan_cap_wallets_max, 3), lines("wallets.max" to FeatureValue.Quota(3))[0])
    }

    @Test
    fun exportAndReportsOnOff() {
        val on = lines("export.enabled" to FeatureValue.Flag(true), "reports.advanced" to FeatureValue.Flag(true))
        assertEquals(listOf(Res.string.plan_cap_export_on, Res.string.plan_cap_reports_on), on.takeLast(2).map { it.text })
        assertEquals(listOf(true, true), on.takeLast(2).map { it.included })
        val off = lines("export.enabled" to FeatureValue.Flag(false))
        assertEquals(listOf(Res.string.plan_cap_export_off, Res.string.plan_cap_reports_off), off.takeLast(2).map { it.text })
        assertEquals(listOf(false, false), off.takeLast(2).map { it.included })
    }

    @Test
    fun unknownKeysProduceNoLine() {
        assertEquals(lines(), lines("future.thing" to FeatureValue.Flag(true), "other.max" to FeatureValue.Quota(3)))
    }
}
