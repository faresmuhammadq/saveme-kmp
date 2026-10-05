package com.fares.saveme.feature.sharing.domain

/**
 * The platform's share sheet and clipboard (spec 013 R-17). The text carries the link, so implementations never log it, and a
 * copied link is marked sensitive so the system does not preview it. iOS has no implementation yet (P-15).
 */
interface LinkSharer {
    fun share(text: String)
    fun copy(text: String)
}
