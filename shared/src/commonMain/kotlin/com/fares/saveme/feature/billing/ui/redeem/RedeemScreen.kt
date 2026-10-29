package com.fares.saveme.feature.billing.ui.redeem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmSubmitButton
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.CalmAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowCheckCircle
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.billing.domain.RedeemConfirmation
import com.fares.saveme.feature.billing.ui.redeemMessage
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.authb_redeem_heading
import saveme.shared.generated.resources.authb_redeem_intro
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.redeem_done_button
import saveme.shared.generated.resources.redeem_done_current_stays_no_end
import saveme.shared.generated.resources.redeem_done_current_stays_until
import saveme.shared.generated.resources.redeem_done_now_on_no_end
import saveme.shared.generated.resources.redeem_done_now_on_until
import saveme.shared.generated.resources.redeem_done_play_renews
import saveme.shared.generated.resources.redeem_done_redeemed_no_end
import saveme.shared.generated.resources.redeem_done_redeemed_until
import saveme.shared.generated.resources.redeem_done_starts_on
import saveme.shared.generated.resources.redeem_done_title
import saveme.shared.generated.resources.redeem_field_hint
import saveme.shared.generated.resources.redeem_field_label
import saveme.shared.generated.resources.redeem_in_progress
import saveme.shared.generated.resources.redeem_intro
import saveme.shared.generated.resources.redeem_invalid_format
import saveme.shared.generated.resources.redeem_not_valid
import saveme.shared.generated.resources.redeem_offline
import saveme.shared.generated.resources.redeem_rate_limited_later
import saveme.shared.generated.resources.redeem_rate_limited_minute
import saveme.shared.generated.resources.redeem_rate_limited_minutes
import saveme.shared.generated.resources.redeem_retry
import saveme.shared.generated.resources.redeem_signed_in_as
import saveme.shared.generated.resources.redeem_submit
import saveme.shared.generated.resources.redeem_title
import saveme.shared.generated.resources.redeem_verify_note

/** Enter and redeem a mailed code, then confirm which plan applies (spec 015). Never mentions how the plan was paid for (P-12). */
@Composable
fun RedeemScreen(
    state: RedeemState,
    onIntent: (RedeemIntent) -> Unit,
    focusRequester: FocusRequester = FocusRequester(),
) {
    val done = state.phase as? RedeemPhase.Done
    val sending = state.phase == RedeemPhase.Sending
    CalmScreen(
        title = null,
        spacing = 16.dp,
        onBack = { onIntent(RedeemIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        bottomBar = {
            CalmActionBar {
                if (done != null) {
                    PrimaryButton(stringResource(Res.string.redeem_done_button), { onIntent(RedeemIntent.Done) }, Modifier.testTag("redeem.doneButton"))
                } else if (state.message == RedeemMessage.Offline || state.message == RedeemMessage.InProgress) {
                    PrimaryButton(
                        stringResource(Res.string.redeem_retry),
                        { onIntent(RedeemIntent.TryAgain) },
                        Modifier.testTag("redeem.retry"),
                        enabled = state.canRedeem,
                        loading = sending,
                    )
                } else {
                    CalmSubmitButton(
                        stringResource(Res.string.redeem_submit),
                        { onIntent(RedeemIntent.Redeem) },
                        Modifier.testTag("redeem.submit"),
                        enabled = state.canRedeem,
                        loading = sending,
                    )
                }
            }
        },
    ) {
        if (done != null) Confirmation(done.confirmation, state.timeZoneId) else Form(state, onIntent, focusRequester)
    }
}

@Composable
private fun Form(state: RedeemState, onIntent: (RedeemIntent) -> Unit, focusRequester: FocusRequester) {
    val sending = state.phase == RedeemPhase.Sending
    CalmIconTile(AppIcons.Gift, AppTheme.colors.primary, AppTheme.colors.primaryContainer, size = 60.dp, iconSize = 30.dp, radius = 20.dp)
    CalmTitle(stringResource(Res.string.authb_redeem_heading))
    Text(
        stringResource(Res.string.authb_redeem_intro),
        style = AppTheme.type.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
        color = AppTheme.colors.ink2,
    )
    if (!state.verified) {
        CalmAlert(AlertTone.Info, stringResource(Res.string.redeem_verify_note), Modifier.testTag("redeem.verifyNote"))
    }
    SegmentedCodeField(
        value = state.code,
        onValueChange = { onIntent(RedeemIntent.CodeChanged(it)) },
        label = stringResource(Res.string.redeem_field_label),
        errorText = if (state.message == RedeemMessage.InvalidFormat) stringResource(Res.string.redeem_invalid_format) else null,
        enabled = !sending,
        onDone = { if (state.canRedeem) onIntent(RedeemIntent.Redeem) },
        modifier = Modifier.focusRequester(focusRequester).testTag("redeem.code"),
    )
    PasteChip(
        onText = { onIntent(RedeemIntent.CodeChanged(it)) },
        enabled = !sending,
        modifier = Modifier.testTag("redeem.paste"),
    )
    if (state.showsMessage && state.message != RedeemMessage.InvalidFormat) {
        CalmAlert(AlertTone.Error, state.message!!.text(), Modifier.testTag("redeem.message"))
    }
}

@Composable
private fun RedeemMessage.text(): String = when (this) {
    RedeemMessage.Offline -> stringResource(Res.string.redeem_offline)
    RedeemMessage.NotValid -> stringResource(Res.string.redeem_not_valid)
    RedeemMessage.InvalidFormat -> stringResource(Res.string.redeem_invalid_format)
    RedeemMessage.InProgress -> stringResource(Res.string.redeem_in_progress)
    RedeemMessage.Used -> stringResource(ApiErrorCode.REDEEM_CODE_USED.redeemMessage() ?: Res.string.error_generic)
    RedeemMessage.Expired -> stringResource(ApiErrorCode.REDEEM_CODE_EXPIRED.redeemMessage() ?: Res.string.error_generic)
    is RedeemMessage.OtherAddress -> {
        val mismatch = stringResource(ApiErrorCode.REDEEM_CODE_EMAIL_MISMATCH.redeemMessage() ?: Res.string.error_generic)
        // Only the person's own address is ever named (FR-020).
        if (signedInEmail != null) "$mismatch\n${stringResource(Res.string.redeem_signed_in_as, signedInEmail)}" else mismatch
    }
    RedeemMessage.NotVerified -> stringResource(Res.string.redeem_verify_note)
    is RedeemMessage.RateLimited -> when {
        minutes == null -> stringResource(Res.string.redeem_rate_limited_later)
        minutes <= 1 -> stringResource(Res.string.redeem_rate_limited_minute)
        else -> stringResource(Res.string.redeem_rate_limited_minutes, minutes)
    }
    is RedeemMessage.Failed ->
        if (requestId != null) stringResource(Res.string.error_generic_reference, requestId) else stringResource(Res.string.error_generic)
}

@Composable
private fun Confirmation(confirmation: RedeemConfirmation, timeZoneId: String) {
    Column(Modifier.testTag("redeem.done"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CalmIconTile(
            AppIcons.FlowCheckCircle,
            AppTheme.money.positive,
            AppTheme.money.positiveContainer,
            size = 60.dp,
            iconSize = 30.dp,
            radius = 20.dp,
        )
        CalmTitle(stringResource(Res.string.redeem_done_title))
    }
    val name = confirmation.governingPlanName
    val governingEnd = confirmation.governingEnd?.let { formatDate(it, timeZoneId) }
    val governingLine = when {
        confirmation.governingIsRedeemed && governingEnd != null -> stringResource(Res.string.redeem_done_now_on_until, name, governingEnd)
        confirmation.governingIsRedeemed -> stringResource(Res.string.redeem_done_now_on_no_end, name)
        governingEnd != null -> stringResource(Res.string.redeem_done_current_stays_until, name, governingEnd)
        else -> stringResource(Res.string.redeem_done_current_stays_no_end, name)
    }
    CalmCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Line(governingLine, "redeem.done.governing")
            if (!confirmation.governingIsRedeemed) {
                val end = confirmation.redeemedEnd?.let { formatDate(it, timeZoneId) }
                Line(
                    if (end != null) {
                        stringResource(Res.string.redeem_done_redeemed_until, confirmation.redeemedPlanName, end)
                    } else {
                        stringResource(Res.string.redeem_done_redeemed_no_end, confirmation.redeemedPlanName)
                    },
                    "redeem.done.redeemed",
                )
            }
            confirmation.redeemedStartsAt?.let { Line(stringResource(Res.string.redeem_done_starts_on, formatDate(it, timeZoneId)), "redeem.done.starts") }
            if (confirmation.playRenews) Line(stringResource(Res.string.redeem_done_play_renews), "redeem.done.play")
        }
    }
}

@Composable
private fun Line(text: String, tag: String) {
    Text(text, style = AppTheme.type.body, color = AppTheme.colors.ink, modifier = Modifier.testTag(tag))
}

@Preview
@Composable
private fun RedeemScreenPreview() {
    AppTheme { RedeemScreen(RedeemState(code = "7KQ2MX"), onIntent = {}) }
}
