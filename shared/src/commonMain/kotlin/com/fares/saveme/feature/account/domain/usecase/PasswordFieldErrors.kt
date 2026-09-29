package com.fares.saveme.feature.account.domain.usecase

import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.feature.account.domain.model.PasswordField

/** `errors[].pointer` → password fields; `/password` is the deletion confirmation's current password. */
internal fun ProblemDetails.passwordFieldErrors(): Map<PasswordField, String> =
    errors.orEmpty().mapNotNull { error ->
        when (error.pointer) {
            "/current_password", "/password" -> PasswordField.Current
            "/new_password" -> PasswordField.New
            else -> null
        }?.let { it to error.code }
    }.toMap()
