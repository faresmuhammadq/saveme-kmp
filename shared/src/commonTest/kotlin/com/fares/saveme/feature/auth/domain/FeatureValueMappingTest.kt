package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.data.dto.AccountSnapshotDto
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FeatureValueMappingTest {

    private fun snapshot(features: String, planId: String = "plan-1") = AppJson.decodeFromString(
        AccountSnapshotDto.serializer(),
        """{"account":{"id":"u1","email":"a@b.c","email_verified":true,"state":"active"},
            "entitlements":{"plan":{"id":"$planId","name":"Plus"},"features":$features}}""",
    ).toDomain()

    @Test
    fun nullQuotaIsUnlimitedNotZero() {
        val features = snapshot("""{"wallets.max":null}""").features
        assertEquals(FeatureValue.Quota(null), features["wallets.max"])
    }

    @Test
    fun flagsAndQuotasKeepTheirType() {
        val features = snapshot("""{"sharing.enabled":true,"export.enabled":false,"sharing.max_members_per_wallet":5}""").features
        assertEquals(FeatureValue.Flag(true), features["sharing.enabled"])
        assertEquals(FeatureValue.Flag(false), features["export.enabled"])
        assertEquals(FeatureValue.Quota(5), features["sharing.max_members_per_wallet"])
    }

    @Test
    fun aStringValueIsDroppedSoItReadsAsMissing() {
        val features = snapshot("""{"sharing.enabled":"yes","wallets.max":"3"}""").features
        assertFalse("sharing.enabled" in features)
        assertFalse("wallets.max" in features)
    }

    @Test
    fun theGoverningPlanIdIsMapped() {
        assertEquals("plan-1", snapshot("{}").planId)
        assertEquals("Plus", snapshot("{}").plan)
    }

    @Test
    fun aSnapshotStoredBeforeThisChangeStillDecodes() = runTest {
        val env = AuthTestEnv { error("no calls") }
        env.keyValues.values["account_snapshot"] =
            """{"account":{"id":"u1","email":"a@b.c","email_verified":true,"state":"active"},"entitlements":{"plan":{"id":"p","name":"Free"},"features":{"wallets.max":null,"sharing.enabled":false}}}"""
        val restored = env.snapshots.read()!!.toDomain()
        assertEquals(FeatureValue.Quota(null), restored.features["wallets.max"])
        assertEquals(FeatureValue.Flag(false), restored.features["sharing.enabled"])
    }
}
