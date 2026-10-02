package com.fares.saveme.feature.billing.data

import com.fares.saveme.feature.billing.BillingTestEnv
import kotlinx.coroutines.test.runTest
import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import com.fares.saveme.feature.billing.data.dto.PlanDto
import com.fares.saveme.feature.billing.data.dto.PlanRefDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BillingDataCleanerTest {

    @Test
    fun clearRemovesBothRecordsAndLeavesOtherKeys() = runTest {
        val env = BillingTestEnv { error("no calls") }
        env.auth.keyValues.values["other"] = "kept"
        env.subscriptions.save(MySubscriptionDto(PlanRefDto("p", "Free")))
        env.catalogue.save("en", listOf(PlanDto("p", "Free")))
        assertEquals(setOf("other", "billing_subscription", "billing_catalogue"), env.auth.keyValues.values.keys)

        BillingDataCleaner(env.subscriptions, env.catalogue).clear()

        assertEquals(setOf("other"), env.auth.keyValues.values.keys)
        assertNull(env.subscriptions.stored.value)
        assertNull(env.catalogue.stored.value)
    }
}
