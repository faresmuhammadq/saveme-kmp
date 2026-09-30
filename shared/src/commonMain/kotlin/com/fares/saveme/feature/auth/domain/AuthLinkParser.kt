package com.fares.saveme.feature.auth.domain

/** A mailed link the app can open. The secret is never printed (FR-009, P-09.2). */
sealed interface AuthLink {
    val token: String

    data class VerifyEmail(override val token: String) : AuthLink {
        override fun toString() = "VerifyEmail(token=<redacted>)"
    }

    data class ResetPassword(override val token: String) : AuthLink {
        override fun toString() = "ResetPassword(token=<redacted>)"
    }
}

/**
 * Accepts exactly `{scheme}://{linkHost}[:{linkPort}]/verify-email?token=…` and
 * `/reset-password?token=…` (contracts/deep-links.md, research R-03). Anything else is null,
 * so a link from another site or another environment can never open these screens.
 *
 * Plain string handling on purpose: domain code may not use Ktor's URL type (P-10.3).
 */
class AuthLinkParser(
    private val linkHost: String,
    private val linkPort: Int?,
    private val scheme: String,
) {
    fun parse(raw: String): AuthLink? {
        val text = raw.trim()
        val schemeEnd = text.indexOf("://").takeIf { it > 0 } ?: return null
        if (!text.substring(0, schemeEnd).equals(scheme, ignoreCase = true)) return null

        val rest = text.substring(schemeEnd + 3)
        val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.takeIf { it >= 0 } ?: return null
        if (!authorityMatches(rest.substring(0, pathStart))) return null

        val pathAndQuery = rest.substring(pathStart).substringBefore('#')
        val path = pathAndQuery.substringBefore('?')
        val token = pathAndQuery.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("$TOKEN_PARAM=") }
            ?.removePrefix("$TOKEN_PARAM=")
            ?.takeIf { it.length in 1..MAX_TOKEN_LENGTH && it.none(Char::isWhitespace) }
            ?: return null

        return when (path) {
            VERIFY_PATH -> AuthLink.VerifyEmail(token)
            RESET_PATH -> AuthLink.ResetPassword(token)
            else -> null
        }
    }

    private fun authorityMatches(authority: String): Boolean {
        val host = authority.substringBeforeLast(':')
        val port = authority.substringAfterLast(':', "").let { if (it.isEmpty()) null else it.toIntOrNull() ?: return false }
        return host.equals(linkHost, ignoreCase = true) && port == linkPort
    }

    private companion object {
        const val VERIFY_PATH = "/verify-email"
        const val RESET_PATH = "/reset-password"
        const val TOKEN_PARAM = "token"
        const val MAX_TOKEN_LENGTH = 128
    }
}
