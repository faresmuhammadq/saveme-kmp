package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.sharing.FAKE_TOKEN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Only links of the flavor's scheme, host, port and `/invite` path with a 43-character secret are accepted (FR-009). */
class InvitationLinkParserTest {

    private val prod = InvitationLinkParser("app.example", null, "https")
    private val dev = InvitationLinkParser("192.168.1.20", 8080, "http")

    @Test
    fun aValidProductionLinkIsAccepted() {
        assertEquals(InvitationLink.Valid(FAKE_TOKEN), prod.parse("https://app.example/invite?token=$FAKE_TOKEN"))
        assertEquals(InvitationLink.Valid(FAKE_TOKEN), prod.parse("  HTTPS://APP.EXAMPLE/invite?token=$FAKE_TOKEN "))
    }

    @Test
    fun theDevelopmentFlavorNeedsItsPort() {
        assertEquals(InvitationLink.Valid(FAKE_TOKEN), dev.parse("http://192.168.1.20:8080/invite?token=$FAKE_TOKEN"))
        assertNull(dev.parse("http://192.168.1.20/invite?token=$FAKE_TOKEN"))
        assertNull(dev.parse("http://192.168.1.20:9090/invite?token=$FAKE_TOKEN"))
        assertNull(prod.parse("https://app.example:8443/invite?token=$FAKE_TOKEN"))
    }

    @Test
    fun anotherSchemeHostOrPathIsNull() {
        assertNull(prod.parse("http://app.example/invite?token=$FAKE_TOKEN"))
        assertNull(prod.parse("https://other.example/invite?token=$FAKE_TOKEN"))
        assertNull(prod.parse("https://app.example/verify-email?token=$FAKE_TOKEN"))
        assertNull(prod.parse("https://app.example/invite/extra?token=$FAKE_TOKEN"))
        assertNull(prod.parse("https://app.example/?token=$FAKE_TOKEN"))
        assertNull(prod.parse("saveme://invite?token=$FAKE_TOKEN"))
        assertNull(prod.parse("not a link"))
        assertNull(prod.parse(""))
    }

    @Test
    fun aMatchingPathWithAMalformedSecretIsMalformed() {
        val short = FAKE_TOKEN.dropLast(1)
        val long = FAKE_TOKEN + "a"
        listOf(
            "https://app.example/invite?token=$short",
            "https://app.example/invite?token=$long",
            "https://app.example/invite?token=${FAKE_TOKEN.dropLast(1)}+",
            "https://app.example/invite?token=${FAKE_TOKEN.dropLast(1)}/",
            "https://app.example/invite?token=${FAKE_TOKEN.dropLast(1)}=",
            "https://app.example/invite?token=",
            "https://app.example/invite",
            "https://app.example/invite?other=$FAKE_TOKEN",
        ).forEach { assertEquals(InvitationLink.Malformed, prod.parse(it), it) }
    }

    @Test
    fun extraParametersAndFragmentsAreTolerated() {
        assertEquals(InvitationLink.Valid(FAKE_TOKEN), prod.parse("https://app.example/invite?utm=x&token=$FAKE_TOKEN&z=1"))
        assertEquals(InvitationLink.Valid(FAKE_TOKEN), prod.parse("https://app.example/invite?token=$FAKE_TOKEN#section"))
    }

    @Test
    fun theValidLinkNeverPrintsItsSecret() {
        val link = assertIs<InvitationLink.Valid>(prod.parse("https://app.example/invite?token=$FAKE_TOKEN"))
        assertFalse(link.toString().contains(FAKE_TOKEN))
        assertEquals("InvitationLink.Valid(token=<redacted>)", link.toString())
    }
}
