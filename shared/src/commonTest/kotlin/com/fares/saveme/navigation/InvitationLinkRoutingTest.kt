package com.fares.saveme.navigation

import com.fares.saveme.feature.auth.domain.AuthLinkParser
import com.fares.saveme.feature.sharing.FAKE_TOKEN
import com.fares.saveme.feature.sharing.domain.InvitationLink
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Which screen a tapped link opens, signed in or not (spec 013 FR-009, FR-011, research R-08, R-09). */
class InvitationLinkRoutingTest {
    private val auth = AuthLinkParser("localhost", 43871, "http")
    private val invitations = InvitationLinkParser("localhost", 43871, "http")
    private val base = "http://localhost:43871"

    @Test
    fun mailLinksAreUnchangedAndComeFirst() {
        val verify = routeLink("$base/verify-email?token=abc", signedIn = true, auth, invitations)
        assertIs<LinkRoute.Open>(verify)
        assertIs<VerifyEmailKey>(verify.key)
        assertNull(verify.hold)
    }

    @Test
    fun aValidInvitationWhileSignedInOpensTheInvitationScreen() {
        val route = assertIs<LinkRoute.Open>(routeLink("$base/invite?token=$FAKE_TOKEN", signedIn = true, auth, invitations))
        assertEquals(InvitationKey(FAKE_TOKEN), route.key)
        assertNull(route.hold)
    }

    @Test
    fun signedOutTheSecretIsHeldInMemoryAndTheScreenStillOpens() {
        val route = assertIs<LinkRoute.Open>(routeLink("$base/invite?token=$FAKE_TOKEN", signedIn = false, auth, invitations))
        assertEquals(InvitationKey(FAKE_TOKEN), route.key)
        assertEquals(InvitationLink.Valid(FAKE_TOKEN), route.hold)
    }

    @Test
    fun aMalformedSecretOpensTheInvalidModeWithoutHoldingAnything() {
        val route = assertIs<LinkRoute.Open>(routeLink("$base/invite?token=short", signedIn = true, auth, invitations))
        assertEquals(InvitationKey(""), route.key)
        assertNull(route.hold)
    }

    @Test
    fun linksThatAreNotOursAreIgnored() {
        assertEquals(LinkRoute.Ignore, routeLink("https://elsewhere.example/invite?token=$FAKE_TOKEN", signedIn = true, auth, invitations))
        assertEquals(LinkRoute.Ignore, routeLink("$base/other", signedIn = true, auth, invitations))
    }

    @Test
    fun theKeyAndTheLinkNeverPrintTheSecret() {
        assertFalse(InvitationKey(FAKE_TOKEN).toString().contains(FAKE_TOKEN))
        assertFalse(InvitationLink.Valid(FAKE_TOKEN).toString().contains(FAKE_TOKEN))
        val route = routeLink("$base/invite?token=$FAKE_TOKEN", signedIn = false, auth, invitations)
        assertFalse(route.toString().contains(FAKE_TOKEN))
    }
}
