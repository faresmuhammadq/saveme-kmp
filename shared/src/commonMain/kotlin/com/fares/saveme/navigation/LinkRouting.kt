package com.fares.saveme.navigation

import androidx.navigation3.runtime.NavKey
import com.fares.saveme.feature.auth.domain.AuthLink
import com.fares.saveme.feature.auth.domain.AuthLinkParser
import com.fares.saveme.feature.sharing.domain.InvitationLink
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser

/** Where a raw link from the host goes (spec 003 research R-04; spec 013 R-08, R-09). */
sealed interface LinkRoute {
    /** The link is not ours (another site, environment or path): nothing happens. */
    data object Ignore : LinkRoute

    /**
     * Push [key]. An invitation link opened while nobody is signed in is [hold] in memory so the invitation screen opens right
     * after the next sign-in (FR-011); a malformed one opens in its "not a valid invitation" mode without any call (FR-009).
     */
    data class Open(val key: NavKey, val hold: InvitationLink.Valid? = null) : LinkRoute
}

/** Auth links first, as before, then invitation links; pure so it can be tested without a back stack. */
fun routeLink(raw: String, signedIn: Boolean, auth: AuthLinkParser, invitations: InvitationLinkParser): LinkRoute {
    when (val link = auth.parse(raw)) {
        is AuthLink.VerifyEmail -> return LinkRoute.Open(VerifyEmailKey(link.token))
        is AuthLink.ResetPassword -> return LinkRoute.Open(ResetPasswordKey(link.token))
        null -> Unit
    }
    return when (val link = invitations.parse(raw)) {
        is InvitationLink.Valid -> LinkRoute.Open(InvitationKey(link.token), hold = link.takeUnless { signedIn })
        InvitationLink.Malformed -> LinkRoute.Open(InvitationKey(""))
        null -> LinkRoute.Ignore
    }
}
