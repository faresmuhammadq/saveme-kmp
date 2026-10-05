package com.fares.saveme.feature.sharing.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a waiting invitation create holds in `pending_change.payload_json` (spec 013 data-model §4). It is not a wire type; the
 * domain maps its draft to and from it so `data` imports nothing from `domain`. The address is in it, so it never prints (P-09.2).
 */
@Serializable
data class InvitationPayload(
    val role: String,
    @SerialName("invitee_email") val email: String? = null,
) {
    override fun toString() = "InvitationPayload(role=$role)"
}
