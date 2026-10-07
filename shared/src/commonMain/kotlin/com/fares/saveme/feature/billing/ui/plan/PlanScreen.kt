package com.fares.saveme.feature.billing.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
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
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PlanCapability
import com.fares.saveme.designsystem.component.PlanCard
import com.fares.saveme.designsystem.component.SecondaryButton
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
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.plan_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(PlanIntent.Back) }) },
                actions = {
                    AppIconButton(
                        AppIcons.Sync,
                        stringResource(Res.string.cd_refresh),
                        { onIntent(PlanIntent.Refresh) },
                        enabled = !state.refreshing,
                        modifier = Modifier.testTag("plan.refresh"),
                    )
                },
            )
        },
    ) {
        if (state.stale) {
            InlineAlert(AlertTone.Info, stringResource(Res.string.plan_stale), modifier = Modifier.testTag("plan.stale"))
        }
        PlayNotesSection(state, onIntent)
        state.current?.let { CurrentSection(it, state, onIntent) }
        CatalogueSection(state, onIntent)
        // The billing actions, and only these (P-12.3): "Restore purchases" (C10, Android) above "Enter code" (C11).
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
            if (state.playSupported) {
                SecondaryButton(
                    stringResource(Res.string.plan_restore),
                    { onIntent(PlanIntent.RestorePurchases) },
                    Modifier.testTag("plan.restore"),
                    enabled = state.restore != RestoreUi.Running,
                    loading = state.restore == RestoreUi.Running,
                )
                RestoreMessage(state.restore, onIntent)
            }
            SecondaryButton(
                stringResource(Res.string.plan_enter_code),
                { onIntent(PlanIntent.EnterCode) },
                Modifier.testTag("plan.enterCode"),
            )
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
        InlineAlert(AlertTone.Info, stringResource(Res.string.plan_play_pending), Modifier.testTag("plan.play.pending"))
    }
    if (state.showAnotherAccount) {
        // No "Sign out" and nothing about the other account (FR-032).
        InlineAlert(AlertTone.Info, stringResource(Res.string.billing_play_another_account), Modifier.testTag("plan.play.anotherAccount"))
    }
}

@Composable
private fun ProblemAlert(tone: AlertTone, text: String, action: String, tag: String, actionTag: String, onAction: () -> Unit) {
    Column {
        InlineAlert(tone, text, Modifier.testTag(tag))
        AppTextButton(action, onAction, Modifier.align(Alignment.End).testTag(actionTag))
    }
}

@Composable
private fun RestoreMessage(restore: RestoreUi, onIntent: (PlanIntent) -> Unit) {
    when (restore) {
        RestoreUi.Idle, RestoreUi.Running -> Unit
        is RestoreUi.Result -> Column {
            val sentences = restore.parts.map { it.text() }
            InlineAlert(
                AlertTone.Info,
                sentences.joinToString(" "),
                Modifier.testTag("plan.restore.result"),
            )
            AppTextButton(stringResource(Res.string.action_done), { onIntent(PlanIntent.DismissRestoreResult) }, Modifier.align(Alignment.End).testTag("plan.restore.dismiss"))
        }
        is RestoreUi.Refused -> Column {
            InlineAlert(AlertTone.Error, restore.text(), Modifier.testTag("plan.restore.result"))
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
private fun SectionHeading(text: String) {
    Text(text, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
}

@Composable
private fun CurrentSection(current: CurrentPlanUi, state: PlanState, onIntent: (PlanIntent) -> Unit) {
    val timeZoneId = state.timeZoneId
    Column(Modifier.testTag("plan.current")) {
        SectionHeading(stringResource(Res.string.plan_current_heading))
        Text(
            current.name,
            style = AppTheme.type.title,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(top = AppTheme.spacing.xs).testTag("plan.current.name"),
        )
        when {
            current.status != null -> Text(
                current.status.text(timeZoneId),
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("plan.current.status"),
            )
            current.statusLoading -> Skeleton(Modifier.padding(top = AppTheme.spacing.xxs).fillMaxWidth(0.5f).height(20.dp))
        }
        if (state.showTestLabel) {
            Text(
                stringResource(Res.string.plan_current_test),
                style = AppTheme.type.label,
                color = AppTheme.colors.caption,
                modifier = Modifier.testTag("plan.current.test"),
            )
        }
        if (state.showManage) {
            AppTextButton(
                stringResource(Res.string.plan_play_manage),
                { onIntent(PlanIntent.OpenManage) },
                Modifier.testTag("plan.play.manage"),
            )
        }
    }
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        current.capabilities.forEach { line ->
            AppListItem(if (line.count != null) stringResource(line.text, line.count) else stringResource(line.text))
        }
    }
}

@Composable
private fun PlanStatusUi.text(timeZoneId: String): String = when (this) {
    is PlanStatusUi.Renews -> stringResource(Res.string.plan_status_active_renews, formatDate(on, timeZoneId))
    is PlanStatusUi.Ends -> stringResource(Res.string.plan_status_active_ends, formatDate(on, timeZoneId))
    PlanStatusUi.NoEnd -> stringResource(Res.string.plan_status_no_end)
    is PlanStatusUi.Grace -> stringResource(Res.string.plan_status_grace, formatDate(until, timeZoneId))
}

@Composable
private fun CatalogueSection(state: PlanState, onIntent: (PlanIntent) -> Unit) {
    SectionHeading(stringResource(Res.string.plan_catalogue_heading))
    when (state.catalogueStatus) {
        CatalogueStatus.Loading -> repeat(2) { Skeleton(Modifier.fillMaxWidth().height(120.dp), AppTheme.shapes.card) }
        CatalogueStatus.Unavailable -> EmptyState(
            title = stringResource(Res.string.plan_catalogue_unavailable),
            body = stringResource(Res.string.plan_catalogue_unavailable_body),
            actionLabel = stringResource(Res.string.action_retry),
            onAction = { onIntent(PlanIntent.Refresh) },
            actionModifier = Modifier.testTag("plan.catalogue.retry"),
        )
        CatalogueStatus.Loaded -> state.cards.forEachIndexed { index, card ->
            PlanCard(
                name = card.name,
                periodLine = card.period?.let { periodText(it) },
                priceMinor = card.price?.amountMinor,
                priceCurrency = card.price?.currency,
                capabilities = card.capabilities.map {
                    PlanCapability(if (it.count != null) stringResource(it.text, it.count) else stringResource(it.text), it.included)
                },
                currentLabel = if (card.isCurrent) stringResource(Res.string.plan_current_label) else null,
                modifier = Modifier.testTag("plan.card.$index"),
                priceModifier = Modifier.testTag("plan.card.$index.price"),
                currentLabelModifier = Modifier.testTag("plan.card.$index.current"),
            )
        }
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
