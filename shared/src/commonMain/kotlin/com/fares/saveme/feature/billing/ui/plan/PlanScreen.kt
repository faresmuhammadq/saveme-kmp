package com.fares.saveme.feature.billing.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmAlert
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.icon.FlowCrown
import com.fares.saveme.designsystem.icon.FlowOpenInNew
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.PlanCapability
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.play.RestoreMessagePart
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.cd_refresh
import saveme.shared.generated.resources.plan_catalogue_heading
import saveme.shared.generated.resources.plan_catalogue_unavailable
import saveme.shared.generated.resources.plan_catalogue_unavailable_body
import saveme.shared.generated.resources.plan_current_heading
import saveme.shared.generated.resources.billing_play_another_account
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.plan_current_label
import saveme.shared.generated.resources.plan_current_test
import saveme.shared.generated.resources.plan_enter_code
import saveme.shared.generated.resources.plan_play_fix_payment
import saveme.shared.generated.resources.plan_play_grace
import saveme.shared.generated.resources.plan_play_hold
import saveme.shared.generated.resources.plan_play_manage
import saveme.shared.generated.resources.plan_play_not_available
import saveme.shared.generated.resources.plan_play_paused
import saveme.shared.generated.resources.plan_play_pending
import saveme.shared.generated.resources.plan_restore
import saveme.shared.generated.resources.plan_restore_busy
import saveme.shared.generated.resources.plan_restore_done
import saveme.shared.generated.resources.plan_restore_nothing
import saveme.shared.generated.resources.plan_restore_offline
import saveme.shared.generated.resources.plan_restore_pending
import saveme.shared.generated.resources.plan_restore_unavailable
import saveme.shared.generated.resources.redeem_rate_limited_later
import saveme.shared.generated.resources.redeem_rate_limited_minute
import saveme.shared.generated.resources.redeem_rate_limited_minutes
import saveme.shared.generated.resources.plan_period_every_days
import saveme.shared.generated.resources.plan_period_lifetime
import saveme.shared.generated.resources.plan_period_monthly
import saveme.shared.generated.resources.plan_period_yearly
import saveme.shared.generated.resources.plan_stale
import saveme.shared.generated.resources.plan_status_active_ends
import saveme.shared.generated.resources.plan_status_active_renews
import saveme.shared.generated.resources.plan_status_grace
import saveme.shared.generated.resources.plan_status_no_end
import saveme.shared.generated.resources.plan_title

/**
 * The person's plan and the catalogue. No purchase, checkout or payment element: the billing actions are "Restore purchases" and
 * "Enter code", plus links into Google Play's own subscription centre (P-12.3, spec 018 FR-001).
 */
@Composable
fun PlanScreen(
    state: PlanState,
    onIntent: (PlanIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    CalmScreen(
        title = stringResource(Res.string.plan_title),
        onBack = { onIntent(PlanIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        actions = {
            CalmCircleButton(
                AppIcons.Sync,
                stringResource(Res.string.cd_refresh),
                { onIntent(PlanIntent.Refresh) },
                enabled = !state.refreshing,
                modifier = Modifier.testTag("plan.refresh"),
            )
        },
    ) {
        state.current?.let { CurrentSection(it, state, onIntent) }
        if (state.stale) {
            CalmAlert(AlertTone.Warning, stringResource(Res.string.plan_stale), modifier = Modifier.testTag("plan.stale"))
        }
        PlayNotesSection(state, onIntent)
        CatalogueSection(state, onIntent)
        // The billing actions, and only these (P-12.3): "Restore purchases" (C10, Android) beside "Enter code" (C11).
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                if (state.playSupported) {
                    val running = state.restore == RestoreUi.Running
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AppTheme.colors.primary)
                        AppTextButton(
                            stringResource(Res.string.plan_restore),
                            { onIntent(PlanIntent.RestorePurchases) },
                            Modifier.testTag("plan.restore"),
                            enabled = !running,
                        )
                    }
                }
                AppTextButton(
                    stringResource(Res.string.plan_enter_code),
                    { onIntent(PlanIntent.EnterCode) },
                    Modifier.testTag("plan.enterCode"),
                )
            }
            if (state.playSupported) RestoreMessage(state.restore, onIntent)
        }
    }
}

/** Payment problems, a pending payment and another account's purchase: state, shown while it holds (FR-031–FR-035). */
@Composable
private fun PlayNotesSection(state: PlanState, onIntent: (PlanIntent) -> Unit) {
    state.problems.forEach { problem ->
        when (problem) {
            is PaymentProblemUi.Grace -> ProblemAlert(
                AlertTone.Warning,
                stringResource(Res.string.plan_play_grace, formatDate(problem.until, state.timeZoneId), problem.planName),
                stringResource(Res.string.plan_play_fix_payment),
                "plan.play.problem",
                "plan.play.fixPayment",
            ) { onIntent(PlanIntent.OpenFixPayment) }
            is PaymentProblemUi.Hold -> ProblemAlert(
                AlertTone.Warning,
                stringResource(Res.string.plan_play_hold, problem.planName),
                stringResource(Res.string.plan_play_fix_payment),
                "plan.play.hold",
                "plan.play.fixPayment",
            ) { onIntent(PlanIntent.OpenFixPayment) }
            is PaymentProblemUi.Paused -> ProblemAlert(
                AlertTone.Info,
                stringResource(Res.string.plan_play_paused),
                stringResource(Res.string.plan_play_manage),
                "plan.play.paused",
                "plan.play.pausedManage",
            ) { onIntent(PlanIntent.OpenManage) }
        }
    }
    if (state.showPending) {
        CalmAlert(AlertTone.Info, stringResource(Res.string.plan_play_pending), Modifier.testTag("plan.play.pending"))
    }
    if (state.showAnotherAccount) {
        // No "Sign out" and nothing about the other account (FR-032).
        CalmAlert(AlertTone.Info, stringResource(Res.string.billing_play_another_account), Modifier.testTag("plan.play.anotherAccount"))
    }
}

@Composable
private fun ProblemAlert(tone: AlertTone, text: String, action: String, tag: String, actionTag: String, onAction: () -> Unit) {
    Column {
        CalmAlert(tone, text, Modifier.testTag(tag))
        AppTextButton(action, onAction, Modifier.align(Alignment.End).testTag(actionTag))
    }
}

@Composable
private fun RestoreMessage(restore: RestoreUi, onIntent: (PlanIntent) -> Unit) {
    when (restore) {
        RestoreUi.Idle, RestoreUi.Running -> Unit
        is RestoreUi.Result -> Column {
            val sentences = restore.parts.map { it.text() }
            CalmAlert(
                AlertTone.Info,
                sentences.joinToString(" "),
                Modifier.testTag("plan.restore.result"),
            )
            AppTextButton(stringResource(Res.string.action_done), { onIntent(PlanIntent.DismissRestoreResult) }, Modifier.align(Alignment.End).testTag("plan.restore.dismiss"))
        }
        is RestoreUi.Refused -> Column {
            CalmAlert(AlertTone.Error, restore.text(), Modifier.testTag("plan.restore.result"))
            if (restore.kind != RestoreRefusal.GooglePlayUnavailable) {
                AppTextButton(stringResource(Res.string.action_retry), { onIntent(PlanIntent.RetryRestore) }, Modifier.align(Alignment.End).testTag("plan.restore.retry"))
            }
        }
    }
}

@Composable
private fun RestoreMessagePart.text(): String = when (this) {
    RestoreMessagePart.Restored -> stringResource(Res.string.plan_restore_done)
    RestoreMessagePart.Nothing -> stringResource(Res.string.plan_restore_nothing)
    RestoreMessagePart.AnotherAccount -> stringResource(Res.string.billing_play_another_account)
    RestoreMessagePart.Pending -> stringResource(Res.string.plan_restore_pending)
    RestoreMessagePart.Unavailable -> stringResource(Res.string.plan_restore_unavailable)
}

@Composable
private fun RestoreUi.Refused.text(): String = when (kind) {
    RestoreRefusal.GooglePlayUnavailable -> stringResource(Res.string.plan_play_not_available)
    RestoreRefusal.Offline -> stringResource(Res.string.plan_restore_offline)
    RestoreRefusal.Busy -> stringResource(Res.string.plan_restore_busy)
    RestoreRefusal.RateLimited -> {
        val minutes = retryAfterSeconds?.let { ((it + 59) / 60).toInt().coerceAtLeast(1) }
        when {
            minutes == null -> stringResource(Res.string.redeem_rate_limited_later)
            minutes <= 1 -> stringResource(Res.string.redeem_rate_limited_minute)
            else -> stringResource(Res.string.redeem_rate_limited_minutes, minutes)
        }
    }
    RestoreRefusal.Failed ->
        if (requestId != null) stringResource(Res.string.error_generic_reference, requestId) else stringResource(Res.string.error_generic)
}

@Composable
private fun CurrentSection(current: CurrentPlanUi, state: PlanState, onIntent: (PlanIntent) -> Unit) {
    val timeZoneId = state.timeZoneId
    val onInk = AppTheme.colors.background
    Column(
        modifier = Modifier
            .testTag("plan.current")
            .fillMaxWidth()
            .background(AppTheme.colors.ink, RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.FlowCrown, contentDescription = null, tint = PlanGold, modifier = Modifier.size(28.dp))
            Text(
                current.name,
                style = AppTheme.type.headline.copy(fontSize = 22.sp),
                color = onInk,
                modifier = Modifier.testTag("plan.current.name").semantics { heading() },
            )
        }
        when {
            current.status != null -> Text(
                current.status.text(timeZoneId),
                style = AppTheme.type.label,
                color = onInk.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 6.dp).testTag("plan.current.status"),
            )
            current.statusLoading -> Skeleton(
                Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(20.dp),
                AppTheme.shapes.control,
            )
        }
        if (state.showTestLabel) {
            Text(
                stringResource(Res.string.plan_current_test),
                style = AppTheme.type.label,
                color = onInk.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp).testTag("plan.current.test"),
            )
        }
        if (state.showManage) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag("plan.play.manage")
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onIntent(PlanIntent.OpenManage) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .background(onInk.copy(alpha = 0.14f), AppTheme.shapes.pill)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(stringResource(Res.string.plan_play_manage), style = AppTheme.type.label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold), color = onInk)
                    Icon(AppIcons.FlowOpenInNew, contentDescription = null, tint = onInk, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private val PlanGold = Color(0xFFE3B45A)

@Composable
private fun PlanStatusUi.text(timeZoneId: String): String = when (this) {
    is PlanStatusUi.Renews -> stringResource(Res.string.plan_status_active_renews, formatDate(on, timeZoneId))
    is PlanStatusUi.Ends -> stringResource(Res.string.plan_status_active_ends, formatDate(on, timeZoneId))
    PlanStatusUi.NoEnd -> stringResource(Res.string.plan_status_no_end)
    is PlanStatusUi.Grace -> stringResource(Res.string.plan_status_grace, formatDate(until, timeZoneId))
}

@Composable
private fun CatalogueSection(state: PlanState, onIntent: (PlanIntent) -> Unit) {
    when (state.catalogueStatus) {
        CatalogueStatus.Loading -> repeat(2) { Skeleton(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(24.dp)) }
        CatalogueStatus.Unavailable -> EmptyState(
            title = stringResource(Res.string.plan_catalogue_unavailable),
            body = stringResource(Res.string.plan_catalogue_unavailable_body),
            actionLabel = stringResource(Res.string.action_retry),
            onAction = { onIntent(PlanIntent.Refresh) },
            actionModifier = Modifier.testTag("plan.catalogue.retry"),
        )
        CatalogueStatus.Loaded -> PlanTable(state)
    }
}

@Composable
private fun periodText(period: BillingPeriod): String? = when (period) {
    BillingPeriod.Monthly -> stringResource(Res.string.plan_period_monthly)
    BillingPeriod.Yearly -> stringResource(Res.string.plan_period_yearly)
    BillingPeriod.Lifetime -> stringResource(Res.string.plan_period_lifetime)
    is BillingPeriod.EveryDays -> stringResource(Res.string.plan_period_every_days, period.days)
    BillingPeriod.Unknown -> null
}

@Preview
@Composable
private fun PlanScreenPreview() {
    AppTheme { PlanScreen(PlanState(catalogueStatus = CatalogueStatus.Unavailable), onIntent = {}) }
}
