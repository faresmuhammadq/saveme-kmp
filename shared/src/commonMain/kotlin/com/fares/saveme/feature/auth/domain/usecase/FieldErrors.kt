package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.feature.auth.domain.model.AuthField

/** Maps `errors[].pointer` to form fields (FR-015); unknown pointers are dropped to the banner. */
internal fun ProblemDetails.fieldErrors(): Map<AuthField, String> =
    errors.orEmpty().mapNotNull { error ->
        val field = when (error.pointer) {
            "/email" -> AuthField.Email
            "/password", "/new_password" -> AuthField.Password
            "/display_name" -> AuthField.DisplayName
            "/token" -> AuthField.Token
            else -> null
        }
        field?.let { it to error.code }
    }.toMap()
