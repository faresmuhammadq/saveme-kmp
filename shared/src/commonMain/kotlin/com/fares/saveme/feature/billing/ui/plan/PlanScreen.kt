package com.fares.saveme.feature.billing.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.cd_refresh
import saveme.shared.generated.resources.plan_catalogue_heading
import saveme.shared.generated.resources.plan_catalogue_unavailable
import saveme.shared.generated.resources.plan_catalogue_unavailable_body
import saveme.shared.generated.resources.plan_current_heading
import saveme.shared.generated.resources.plan_current_label
import saveme.shared.generated.resources.plan_enter_code
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

/** The person's plan and the catalogue. Read-only: no purchase, checkout or payment element (FR-010, FR-014). */
@Composable
fun PlanScreen(state: PlanState, onIntent: (PlanIntent) -> Unit) {
    AppScreen(
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
        state.current?.let { CurrentSection(it, state.timeZoneId) }
        CatalogueSection(state, onIntent)
        // The billing actions: "Enter code" (C11); Google Play (C10) takes its place here later. Nothing else (P-12.3).
        Column {
            SecondaryButton(
                stringResource(Res.string.plan_enter_code),
                { onIntent(PlanIntent.EnterCode) },
                Modifier.testTag("plan.enterCode"),
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
}

@Composable
private fun CurrentSection(current: CurrentPlanUi, timeZoneId: String) {
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
    AppTheme { PlanScreen(PlanState(catalogueStatus = CatalogueStatus.Unavailable)) {} }
}
