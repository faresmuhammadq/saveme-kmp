package com.fares.saveme.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AuthLinkParserTest {

    private val dev = AuthLinkParser(linkHost = "localhost", linkPort = 43871, scheme = "http")
    private val prod = AuthLinkParser(linkHost = "api.example", linkPort = null, scheme = "https")

    @Test
    fun parsesBothLinkKinds() {
        assertEquals(AuthLink.VerifyEmail("abc"), dev.parse("http://localhost:43871/verify-email?token=abc"))
        assertEquals(AuthLink.ResetPassword("abc"), dev.parse("http://localhost:43871/reset-password?token=abc"))
        assertEquals(AuthLink.ResetPassword("abc"), prod.parse("https://api.example/reset-password?token=abc"))
    }

    @Test
    fun trimsPastedWhitespace() {
        assertEquals(AuthLink.VerifyEmail("abc"), dev.parse("  http://localhost:43871/verify-email?token=abc \n"))
    }

    @Test
    fun keepsOtherQueryParametersOutOfTheToken() {
        assertEquals(AuthLink.VerifyEmail("abc"), dev.parse("http://localhost:43871/verify-email?token=abc&utm=x"))
    }

    @Test
    fun rejectsLinksForAnotherSchemeHostPortOrPath() {
        assertNull(dev.parse("https://localhost:43871/verify-email?token=abc"))
        assertNull(dev.parse("http://evil.example:43871/verify-email?token=abc"))
        assertNull(dev.parse("http://localhost:8080/verify-email?token=abc"))
        assertNull(dev.parse("http://localhost/verify-email?token=abc"))
        assertNull(dev.parse("http://localhost:43871/other?token=abc"))
        assertNull(prod.parse("https://api.example:444/verify-email?token=abc"))
        assertNull(prod.parse("http://api.example/verify-email?token=abc"))
    }

    @Test
    fun rejectsMissingEmptyOrOverlongTokens() {
        assertNull(dev.parse("http://localhost:43871/verify-email"))
        assertNull(dev.parse("http://localhost:43871/verify-email?token="))
        assertNull(dev.parse("http://localhost:43871/verify-email?token=" + "a".repeat(129)))
        assertEquals(AuthLink.VerifyEmail("a".repeat(128)), dev.parse("http://localhost:43871/verify-email?token=" + "a".repeat(128)))
    }

    @Test
    fun rejectsNonLinks() {
        assertNull(dev.parse(""))
        assertNull(dev.parse("not a link"))
    }

    @Test
    fun neverPrintsTheSecret() {
        val link = dev.parse("http://localhost:43871/reset-password?token=very-secret")!!
        assertFalse("very-secret" in link.toString())
    }
}
