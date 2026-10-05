package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.sharing.domain.model.EmailProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InviteValidationTest {

    private fun problem(email: String, own: String? = "sara@example.com") = InviteValidation.validate(email, own)

    @Test
    fun anAddressIsTrimmedBeforeItIsChecked() {
        assertEquals("karim@example.com", InviteValidation.clean("  karim@example.com \n"))
        assertNull(problem("  karim@example.com  "))
    }

    @Test
    fun theLengthIsThreeTo254() {
        assertEquals(EmailProblem.Invalid, problem("a@"))
        assertNull(problem("a@b.c"))
        val long = "a".repeat(240) + "@example.com"
        assertEquals(252, long.length)
        assertNull(problem(long))
        assertEquals(EmailProblem.Invalid, problem("a".repeat(250) + "@example.com"))
    }

    @Test
    fun exactlyOneAtSignWithALocalPartAndADomainWithADot() {
        listOf("karim", "karim@", "@example.com", "a@b@c.co", "karim@example", "karim@.com", "karim@example.").forEach {
            assertEquals(EmailProblem.Invalid, problem(it), it)
        }
    }

    @Test
    fun spacesAreRefused() {
        assertEquals(EmailProblem.Invalid, problem("ka rim@example.com"))
        assertEquals(EmailProblem.Invalid, problem("karim@exa mple.com"))
    }

    @Test
    fun theOwnAddressIsRefusedIgnoringCase() {
        assertEquals(EmailProblem.OwnAddress, problem("Sara@Example.com"))
        assertEquals(EmailProblem.OwnAddress, problem(" sara@example.com "))
        assertNull(problem("sara@example.com", own = null))
    }

    @Test
    fun aDuplicateAddressIsFoundIgnoringCaseAndSpaces() {
        assertTrue(InviteValidation.sameAddress("Karim@Example.com", " karim@example.com"))
        assertFalse(InviteValidation.sameAddress("karim@example.com", "omar@example.com"))
    }
}
