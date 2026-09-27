package com.fares.saveme.feature.auth.domain

/** Checks run before any request (FR-019). Server rules: identity.yaml Email/Password schemas. */
enum class FieldError {
    Required, EmailInvalid, EmailTooLong, PasswordTooShort, PasswordTooLong, PasswordEqualsEmail, DisplayNameTooLong,
}

object ClientValidation {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validateEmail(email: String): FieldError? {
        val value = email.trim()
        return when {
            value.isEmpty() -> FieldError.Required
            value.length > 254 -> FieldError.EmailTooLong
            !emailPattern.matches(value) -> FieldError.EmailInvalid
            else -> null
        }
    }

    /** For sign-in: existing passwords are only required to be present. */
    fun validateExistingPassword(password: String): FieldError? =
        if (password.isEmpty()) FieldError.Required else null

    /** For registration and reset: 8–128 characters, not equal to the address. */
    fun validateNewPassword(password: String, email: String?): FieldError? = when {
        password.isEmpty() -> FieldError.Required
        password.length < 8 -> FieldError.PasswordTooShort
        password.length > 128 -> FieldError.PasswordTooLong
        email != null && password.trim().equals(email.trim(), ignoreCase = true) -> FieldError.PasswordEqualsEmail
        else -> null
    }

    fun validateDisplayName(name: String): FieldError? =
        if (name.trim().length > 100) FieldError.DisplayNameTooLong else null
}
