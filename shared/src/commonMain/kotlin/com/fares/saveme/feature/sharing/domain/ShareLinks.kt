package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.session.UserDataCleaner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A link invitation's one-time link. Printed redacted (P-09.2). */
class ShareLink(val url: String) {
    override fun toString() = "ShareLink(url=<redacted>)"
}

/**
 * The one-time links the server returned for link invitations created on this phone, keyed by invitation, for this app
 * session only (spec 013 R-04, FR-005). Never stored, logged or shown as text.
 */
class ShareLinks : UserDataCleaner {
    private val links = MutableStateFlow<Map<String, ShareLink>>(emptyMap())

    fun offer(invitationId: String, url: String) {
        links.value = links.value + (invitationId to ShareLink(url))
    }

    fun get(invitationId: String): String? = links.value[invitationId]?.url

    fun forget(invitationId: String) {
        links.value = links.value - invitationId
    }

    /** The invitations whose link is held; the links themselves are not exposed. */
    fun observeHeldIds(): Flow<Set<String>> = links.map { it.keys }

    override suspend fun clear() {
        links.value = emptyMap()
    }

    override fun toString() = "ShareLinks(count=${links.value.size})"
}
