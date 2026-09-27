package com.fares.saveme.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientValidationTest {

    @Test
    fun email() {
        assertEquals(FieldError.Required, ClientValidation.validateEmail("  "))
        assertEquals(FieldError.EmailInvalid, ClientValidation.validateEmail("sara@"))
        assertEquals(FieldError.EmailTooLong, ClientValidation.validateEmail("a".repeat(250) + "@x.io"))
        assertNull(ClientValidation.validateEmail(" sara@example.com "))
    }

    @Test
    fun newPasswordRules() {
        assertEquals(FieldError.PasswordTooShort, ClientValidation.validateNewPassword("1234567", "a@b.co"))
        assertEquals(FieldError.PasswordTooLong, ClientValidation.validateNewPassword("x".repeat(129), "a@b.co"))
        assertEquals(FieldError.PasswordEqualsEmail, ClientValidation.validateNewPassword("Sara@Example.com", "sara@example.com"))
        assertNull(ClientValidation.validateNewPassword("12345678", "a@b.co"))
        assertNull(ClientValidation.validateNewPassword("x".repeat(128), null))
    }

    @Test
    fun existingPasswordOnlyNeedsToBePresent() {
        assertEquals(FieldError.Required, ClientValidation.validateExistingPassword(""))
        assertNull(ClientValidation.validateExistingPassword("short"))
    }

    @Test
    fun displayName() {
        assertNull(ClientValidation.validateDisplayName(""))
        assertEquals(FieldError.DisplayNameTooLong, ClientValidation.validateDisplayName("x".repeat(101)))
    }
}
