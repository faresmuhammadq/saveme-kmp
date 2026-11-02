package com.fares.saveme.feature.sharedbudgets

import com.fares.saveme.feature.auth.domain.AuthLinkParser
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetLinkParser
import com.fares.saveme.feature.sharing.domain.InvitationLink
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser
import com.fares.saveme.navigation.LinkRoute
import com.fares.saveme.navigation.SharedBudgetInvitationKey
import com.fares.saveme.navigation.routeLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val token = "a".repeat(43)

/** The Shared Budget link has its own path and never reaches the wallet screen, nor the other way round. */
class SharedBudgetLinkTest {
    private val budgets = SharedBudgetLinkParser("localhost", 43871, "http")
    private val wallets = InvitationLinkParser("localhost", 43871, "http")
    private val auth = AuthLinkParser("localhost", 43871, "http")
    private val base = "http://localhost:43871"

    @Test
    fun eachParserTakesOnlyItsOwnPath() {
        assertEquals(InvitationLink.Valid(token), budgets.parse("$base/invite/shared-budget?token=$token"))
        assertNull(budgets.parse("$base/invite?token=$token"))
        assertNull(wallets.parse("$base/invite/shared-budget?token=$token"))
        assertEquals(InvitationLink.Malformed, budgets.parse("$base/invite/shared-budget?token=short"))
        assertNull(budgets.parse("https://elsewhere.example/invite/shared-budget?token=$token"))
    }

    @Test
    fun aBudgetLinkWhileSignedOutIsHeldForAfterSignIn() {
        val out = assertIs<LinkRoute.Open>(routeLink("$base/invite/shared-budget?token=$token", false, auth, wallets, budgets))
        assertEquals(SharedBudgetInvitationKey(token), out.key)
        assertEquals(InvitationLink.Valid(token), out.holdSharedBudget)
        assertNull(out.hold)
        val signedIn = assertIs<LinkRoute.Open>(routeLink("$base/invite/shared-budget?token=$token", true, auth, wallets, budgets))
        assertNull(signedIn.holdSharedBudget)
    }

    @Test
    fun aMalformedBudgetLinkOpensTheInvalidScreenWithoutAToken() {
        val route = assertIs<LinkRoute.Open>(routeLink("$base/invite/shared-budget?token=nope", true, auth, wallets, budgets))
        assertEquals(SharedBudgetInvitationKey(""), route.key)
    }

    @Test
    fun aWalletLinkStillGoesToTheWalletScreen() {
        val route = assertIs<LinkRoute.Open>(routeLink("$base/invite?token=$token", true, auth, wallets, budgets))
        assertEquals(com.fares.saveme.navigation.InvitationKey(token), route.key)
    }
}
