package com.fares.saveme.feature.sharedbudgets.domain

import com.fares.saveme.feature.sharing.domain.InvitationLink
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser

/**
 * Accepts exactly `{scheme}://{linkHost}[:{linkPort}]/invite/shared-budget?token=<43 characters>`: the wallet link's scheme, host
 * and port with the Shared Budget path. Anything else is null, so a link from another site or path never reaches the screen.
 */
class SharedBudgetLinkParser(linkHost: String, linkPort: Int?, scheme: String) {
    private val parser = InvitationLinkParser(linkHost, linkPort, scheme, InvitationLinkParser.SHARED_BUDGET_PATH)

    fun parse(raw: String): InvitationLink? = parser.parse(raw)
}
