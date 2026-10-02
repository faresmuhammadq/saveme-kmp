package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.domain.model.FeatureValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementSetTest {

    private val free = EntitlementSet(
        mapOf(
            "sharing.enabled" to FeatureValue.Flag(false),
            "sharing.max_members_per_wallet" to FeatureValue.Quota(1),
            "wallets.max" to FeatureValue.Quota(null),
        ),
    )
    private val plus = EntitlementSet(
        mapOf(
            "sharing.enabled" to FeatureValue.Flag(true),
            "sharing.max_members_per_wallet" to FeatureValue.Quota(5),
            "wallets.max" to FeatureValue.Quota(null),
        ),
    )

    @Test
    fun flagsAnswerTrueFalseMissingAndWrongType() {
        assertTrue(plus.can(FlagFeature.SharingEnabled))
        assertFalse(free.can(FlagFeature.SharingEnabled))
        assertFalse(free.can(FlagFeature.ExportEnabled), "a missing flag is off")
        assertFalse(EntitlementSet(mapOf("sharing.enabled" to FeatureValue.Quota(3))).can(FlagFeature.SharingEnabled), "a quota where a flag belongs is off")
    }

    @Test
    fun quotasAnswerUnlimitedLimitedAndMissing() {
        assertEquals(QuotaLimit.Unlimited, free.quota(QuotaFeature.Wallets))
        assertEquals(QuotaLimit.Limited(5), plus.quota(QuotaFeature.MembersPerWallet))
        assertEquals(QuotaLimit.Limited(0), EntitlementSet(emptyMap()).quota(QuotaFeature.Wallets), "a missing quota is 0")
        assertEquals(QuotaLimit.Limited(0), EntitlementSet(mapOf("wallets.max" to FeatureValue.Flag(true))).quota(QuotaFeature.Wallets))
    }

    @Test
    fun allowsAnotherAtBelowAndAboveTheLimitAndWhenUnlimited() {
        assertTrue(plus.allowsAnother(QuotaFeature.MembersPerWallet, 4))
        assertFalse(plus.allowsAnother(QuotaFeature.MembersPerWallet, 5))
        assertFalse(plus.allowsAnother(QuotaFeature.MembersPerWallet, 9))
        assertTrue(free.allowsAnother(QuotaFeature.Wallets, 10_000))
    }

    @Test
    fun aDowngradeChangesOnlyTheSharingAnswers() {
        assertTrue(plus.can(FlagFeature.SharingEnabled))
        assertFalse(free.can(FlagFeature.SharingEnabled))
        assertEquals(plus.quota(QuotaFeature.Wallets), free.quota(QuotaFeature.Wallets))
    }
}
