package com.fares.saveme.feature.sharing

import com.fares.saveme.feature.sharing.domain.LinkSharer

/** iOS compiles but has no share sheet yet (P-15: no iOS-specific work in the MVP). */
class NoLinkSharer : LinkSharer {
    override fun share(text: String) = Unit
    override fun copy(text: String) = Unit
}
