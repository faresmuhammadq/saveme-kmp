package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.domain.model.RuleAuthor
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The author line of a rule follows the transaction rule: a former member only when the wallet's members are known (FR-034). */
class RuleAuthorTest {

    private suspend fun RecurringServiceEnv.member(user: String, name: String?) =
        memberDao.insertAll(listOf(WalletMemberEntity("m-w1-$user", "w1", user, name, "editor", 1, "2026-09-01T00:00:00Z")))

    private suspend fun RecurringServiceEnv.authorOf(createdBy: String): RuleAuthor {
        seedRule(ruleJson(id = "r-$createdBy").replace("\"created_by\":\"u1\"", "\"created_by\":\"$createdBy\""))
        return service.observeRule("r-$createdBy").first()!!.author
    }

    @Test
    fun anAuthorMissingFromTheHeldMembersIsAFormerMember() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.member("u2", "Omar")
        assertEquals(RuleAuthor.FormerMember, env.authorOf("u9"))
    }

    @Test
    fun aHeldMemberIsNamedOrAnotherMemberWithoutAName() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.member("u2", "Omar")
        env.member("u3", null)
        assertEquals(RuleAuthor.Member("Omar"), env.authorOf("u2"))
        assertEquals("Omar", env.service.observeRule("r-u2").first()!!.authorName)
        assertEquals(RuleAuthor.AnotherMember, env.authorOf("u3"))
    }

    @Test
    fun noHeldMembersMeansAnotherMember() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(RuleAuthor.AnotherMember, env.authorOf("u9"))
    }

    @Test
    fun theOwnRuleIsYouWithNoLine() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.member("u2", "Omar")
        assertEquals(RuleAuthor.You, env.authorOf("u1"))
        assertNull(env.service.observeRule("r-u1").first()!!.authorName)
    }
}
