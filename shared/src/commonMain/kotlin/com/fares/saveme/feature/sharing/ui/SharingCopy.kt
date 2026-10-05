package com.fares.saveme.feature.sharing.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.MemberRole
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusal
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusalKind
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.gate_full
import saveme.shared.generated.resources.gate_not_included
import saveme.shared.generated.resources.invitation_refusal_full
import saveme.shared.generated.resources.invitation_refusal_gone
import saveme.shared.generated.resources.invitation_refusal_invalid
import saveme.shared.generated.resources.invitation_refusal_not_owner
import saveme.shared.generated.resources.invitation_refusal_not_verified
import saveme.shared.generated.resources.invitation_refusal_other
import saveme.shared.generated.resources.invitation_refusal_other_plain
import saveme.shared.generated.resources.invitation_refusal_plan
import saveme.shared.generated.resources.invite_not_verified
import saveme.shared.generated.resources.role_editor
import saveme.shared.generated.resources.role_member
import saveme.shared.generated.resources.role_owner
import saveme.shared.generated.resources.role_viewer

/** The words of a role as the server sent it; a value this app does not know reads as "Member". */
fun roleLabelRes(roleWire: String): StringResource = when (roleWire) {
    WalletRole.Owner.wire -> Res.string.role_owner
    WalletRole.Editor.wire -> Res.string.role_editor
    WalletRole.Viewer.wire -> Res.string.role_viewer
    else -> Res.string.role_member
}

fun roleWireToMemberRole(roleWire: String): MemberRole = when (roleWire) {
    WalletRole.Owner.wire -> MemberRole.Owner
    WalletRole.Editor.wire -> MemberRole.Editor
    else -> MemberRole.Viewer
}

/** Copy for a refusal is chosen from its kind (code and `feature`), never from the server's text (P-02.4, P-06.5). */
fun InvitationRefusalKind.messageRes(): StringResource = when (this) {
    InvitationRefusalKind.NotVerified -> Res.string.invitation_refusal_not_verified
    InvitationRefusalKind.SharingNotIncluded -> Res.string.gate_not_included
    InvitationRefusalKind.PlanFull -> Res.string.invitation_refusal_full
    InvitationRefusalKind.PlanOther -> Res.string.invitation_refusal_plan
    InvitationRefusalKind.NotOwner -> Res.string.invitation_refusal_not_owner
    InvitationRefusalKind.NoLongerAvailable -> Res.string.invitation_refusal_gone
    InvitationRefusalKind.Invalid -> Res.string.invitation_refusal_invalid
    InvitationRefusalKind.Other -> Res.string.invitation_refusal_other_plain
}

@Composable
fun InvitationRefusal.text(): String =
    if (kind == InvitationRefusalKind.Other && requestId != null) stringResource(Res.string.invitation_refusal_other, requestId) else stringResource(kind.messageRes())

/** The explanation of a gate that is not open; null when inviting is allowed. */
@Composable
fun InviteCheck.explanation(): String? = when (this) {
    InviteCheck.Allowed -> null
    InviteCheck.NotVerified -> stringResource(Res.string.invite_not_verified)
    InviteCheck.SharingNotIncluded -> stringResource(Res.string.gate_not_included)
    is InviteCheck.Full -> stringResource(Res.string.gate_full, max)
}

/** The explanation comes from the owner's plan, so "See your plan" is offered (FR-036: it only opens the Plan screen). */
val InviteCheck.isPlanReason: Boolean get() = this == InviteCheck.SharingNotIncluded || this is InviteCheck.Full
