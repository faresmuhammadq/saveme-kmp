package com.fares.saveme.feature.sharing.domain

/** An invitation link the app can open. The secret is never printed (FR-010, P-09.2). */
sealed interface InvitationLink {
    data class Valid(val token: String) : InvitationLink {
        override fun toString() = "InvitationLink.Valid(token=<redacted>)"
    }

    /** The path matches but the secret does not have the right form: the app says so without calling the server. */
    data object Malformed : InvitationLink
}

/**
 * Accepts exactly `{scheme}://{linkHost}[:{linkPort}]/invite?token=<43 characters from A-Z, a-z, 0-9, "_" and "-">` - the
 * same scheme, host and port per flavor as the mail links of spec 003 (spec 013 R-08, FR-009). Anything else is null, so a
 * link from another site, environment or path never reaches the invitation screen. Extra parameters and fragments are
 * tolerated. Plain string handling on purpose: domain code may not use Ktor's URL type (P-10.3).
 */
class InvitationLinkParser(
    private val linkHost: String,
    private val linkPort: Int?,
    private val scheme: String,
) {
    fun parse(raw: String): InvitationLink? {
        val text = raw.trim()
        val schemeEnd = text.indexOf("://").takeIf { it > 0 } ?: return null
        if (!text.substring(0, schemeEnd).equals(scheme, ignoreCase = true)) return null

        val rest = text.substring(schemeEnd + 3)
        val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.takeIf { it >= 0 } ?: return null
        if (!authorityMatches(rest.substring(0, pathStart))) return null

        val pathAndQuery = rest.substring(pathStart).substringBefore('#')
        if (pathAndQuery.substringBefore('?') != PATH) return null

        val token = pathAndQuery.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("$TOKEN_PARAM=") }
            ?.removePrefix("$TOKEN_PARAM=")
        return if (token != null && token.length == TOKEN_LENGTH && token.all(::isTokenChar)) InvitationLink.Valid(token) else InvitationLink.Malformed
    }

    private fun isTokenChar(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-'

    private fun authorityMatches(authority: String): Boolean {
        val host = authority.substringBeforeLast(':')
        val port = authority.substringAfterLast(':', "").let { if (it.isEmpty()) null else it.toIntOrNull() ?: return false }
        return host.equals(linkHost, ignoreCase = true) && port == linkPort
    }

    private companion object {
        const val PATH = "/invite"
        const val TOKEN_PARAM = "token"
        const val TOKEN_LENGTH = 43
    }
}
