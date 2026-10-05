package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.sharing.domain.model.EmailProblem

/**
 * The address of an email invitation (spec 013 R-07, FR-003): trimmed, 3 to 254 characters, one `@`, a domain with a dot, no
 * spaces; never the person's own. The server compares after NFKC and lowercasing; the phone only needs to warn, and a miss
 * merely skips a warning.
 */
object InviteValidation {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 254

    fun clean(email: String): String = email.trim()

    fun validate(email: String, ownEmail: String?): EmailProblem? {
        val address = clean(email)
        if (address.length !in MIN_LENGTH..MAX_LENGTH) return EmailProblem.Invalid
        if (address.any { it.isWhitespace() }) return EmailProblem.Invalid
        if (address.count { it == '@' } != 1) return EmailProblem.Invalid
        val local = address.substringBefore('@')
        val domain = address.substringAfter('@')
        if (local.isEmpty() || domain.isEmpty() || !domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) {
            return EmailProblem.Invalid
        }
        if (ownEmail != null && sameAddress(address, ownEmail)) return EmailProblem.OwnAddress
        return null
    }

    /** Two addresses are the same when equal after trimming, ignoring case. */
    fun sameAddress(a: String, b: String): Boolean = clean(a).equals(clean(b), ignoreCase = true)
}
