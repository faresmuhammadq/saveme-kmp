package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Budgets count a confirmation waiting on the phone for its rule's author (spec 012 R-05, backend ADR-0019). */
class RecurringLinksRuleAuthorTest {

    @Test
    fun theAuthorIsTheRulesCreatorWhenTheRuleIsOnThePhone() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seedRule(ruleJson(id = "mine"))
        env.seedRule(ruleJson(id = "partners").replace("\"created_by\":\"u1\"", "\"created_by\":\"u2\""))
        assertEquals("u1", env.links.ruleAuthorOf("mine"))
        assertEquals("u2", env.links.ruleAuthorOf("partners"))
    }

    @Test
    fun anUnknownRuleHasNoKnownAuthor() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.signedIn()
        assertNull(env.links.ruleAuthorOf("nope"))
    }
}
