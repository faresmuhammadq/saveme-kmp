package com.fares.saveme.feature.billing.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowCheckCircle
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.billing.domain.FlagFeature
import com.fares.saveme.feature.billing.domain.QuotaFeature
import com.fares.saveme.feature.billing.domain.QuotaLimit
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.authb_plan_compare
import saveme.shared.generated.resources.authb_plan_export
import saveme.shared.generated.resources.authb_plan_free
import saveme.shared.generated.resources.authb_plan_included
import saveme.shared.generated.resources.authb_plan_not_included
import saveme.shared.generated.resources.authb_plan_people
import saveme.shared.generated.resources.authb_plan_price
import saveme.shared.generated.resources.authb_plan_reports
import saveme.shared.generated.resources.authb_plan_sharing
import saveme.shared.generated.resources.authb_plan_unlimited
import saveme.shared.generated.resources.authb_plan_up_to
import saveme.shared.generated.resources.authb_plan_wallets
import saveme.shared.generated.resources.plan_current_label
import saveme.shared.generated.resources.plan_period_every_days
import saveme.shared.generated.resources.plan_period_lifetime
import saveme.shared.generated.resources.plan_period_monthly
import saveme.shared.generated.resources.plan_period_yearly

private val ColumnWidth = 100.dp

/** Shown periods, Monthly then Yearly then the rest, one per distinct period of the paid plans. */
private fun paidPeriods(plans: List<CataloguePlan>): List<BillingPeriod> =
    plans.filter { !it.isDefault && it.period != BillingPeriod.Unknown }
        .map { it.period }
        .distinct()
        .sortedBy {
            when (it) {
                BillingPeriod.Monthly -> 0
                BillingPeriod.Yearly -> 1
                else -> 2
            }
        }

@Composable
private fun BillingPeriod.label(): String = when (this) {
    BillingPeriod.Monthly -> stringResource(Res.string.plan_period_monthly)
    BillingPeriod.Yearly -> stringResource(Res.string.plan_period_yearly)
    BillingPeriod.Lifetime -> stringResource(Res.string.plan_period_lifetime)
    is BillingPeriod.EveryDays -> stringResource(Res.string.plan_period_every_days, days)
    BillingPeriod.Unknown -> ""
}

/**
 * The catalogue as a comparison table: the default plan beside the paid plans of the chosen period, the person's plan's
 * column tinted. Prices are Google Play's own, shown only where Google Play answered (spec 018 FR-037).
 */
@Composable
internal fun PlanTable(state: PlanState) {
    val plans = state.plans.orEmpty()
    val periods = remember(plans) { paidPeriods(plans) }
    val currentPeriod = plans.firstOrNull { it.id == state.plan?.id && !it.isDefault }?.period
    var chosen by remember { mutableStateOf<BillingPeriod?>(null) }
    val period = chosen?.takeIf { it in periods } ?: currentPeriod?.takeIf { it in periods } ?: periods.firstOrNull()
    val columns = plans.withIndex().filter { (_, plan) -> plan.isDefault || plan.period == period }
    val tinted = columns.firstOrNull { (_, plan) -> plan.id == state.plan?.id }?.index
        ?: columns.firstOrNull { (_, plan) -> !plan.isDefault }?.index
    val currentLabel = stringResource(Res.string.plan_current_label)

    CalmCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.authb_plan_compare),
                style = AppTheme.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (periods.isNotEmpty()) PeriodTabs(periods, period) { chosen = it }
        }
        val rows = listOf(
            TableSpec(stringResource(Res.string.authb_plan_price)) { index, plan -> PriceCell(index, plan, state) },
            TableSpec(stringResource(Res.string.authb_plan_wallets)) { _, plan -> WalletsCell(plan) },
            TableSpec(stringResource(Res.string.authb_plan_sharing)) { _, plan -> SharingCell(plan) },
            TableSpec(stringResource(Res.string.authb_plan_export)) { _, plan -> FlagCell(plan.features.can(FlagFeature.ExportEnabled)) },
            TableSpec(stringResource(Res.string.authb_plan_reports)) { _, plan -> FlagCell(plan.features.can(FlagFeature.ReportsAdvanced)) },
        )
        TableRow(border = false, tintedShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp), columns = columns, tinted = tinted, label = {}) { index, plan ->
            val current = plan.id == state.plan?.id
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .testTag("plan.card.$index")
                    .semantics { if (current) stateDescription = currentLabel },
            ) {
                Text(
                    plan.name,
                    style = AppTheme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppTheme.colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = if (current) Modifier.testTag("plan.card.$index.current") else Modifier,
                )
            }
        }
        rows.forEachIndexed { rowIndex, spec ->
            val last = rowIndex == rows.lastIndex
            TableRow(
                border = true,
                tintedShape = if (last) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) else RectangleShape,
                columns = columns,
                tinted = tinted,
                label = { Text(spec.title, style = AppTheme.type.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink2) },
                cell = spec.cell,
            )
        }
    }
}

private class TableSpec(val title: String, val cell: @Composable (Int, CataloguePlan) -> Unit)

@Composable
private fun TableRow(
    border: Boolean,
    tintedShape: Shape,
    columns: List<IndexedValue<CataloguePlan>>,
    tinted: Int?,
    label: @Composable () -> Unit,
    cell: @Composable (Int, CataloguePlan) -> Unit,
) {
    val line = AppTheme.colors.outlineVariant
    val tint = AppTheme.colors.primaryContainer
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f).fillMaxHeight().ruled(border, line).padding(vertical = 12.dp),
        ) { label() }
        columns.forEach { (index, plan) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(ColumnWidth)
                    .fillMaxHeight()
                    .then(if (index == tinted) Modifier.background(tint, tintedShape) else Modifier)
                    .ruled(border, line)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            ) { cell(index, plan) }
        }
    }
}

private fun Modifier.ruled(border: Boolean, color: Color): Modifier =
    if (!border) this else drawBehind { drawRect(color, Offset.Zero, Size(size.width, 1.dp.toPx())) }

@Composable
private fun PriceCell(index: Int, plan: CataloguePlan, state: PlanState) {
    val price = plan.playProductId?.let { state.playPrices[it] }
    when {
        price != null -> PlanCellText(formatMoney(price.amountMinor, price.currency), AppTheme.colors.ink, Modifier.testTag("plan.card.$index.price"))
        plan.isDefault -> PlanCellText(stringResource(Res.string.authb_plan_free), AppTheme.colors.ink)
        else -> PlanCellText("—", AppTheme.colors.ink2)
    }
}

@Composable
private fun WalletsCell(plan: CataloguePlan) {
    when (val limit = plan.features.quota(QuotaFeature.Wallets)) {
        QuotaLimit.Unlimited -> PlanCellText(stringResource(Res.string.authb_plan_unlimited), AppTheme.colors.ink)
        is QuotaLimit.Limited -> PlanCellText(stringResource(Res.string.authb_plan_up_to, limit.max), AppTheme.colors.ink)
    }
}

@Composable
private fun SharingCell(plan: CataloguePlan) {
    if (!plan.features.can(FlagFeature.SharingEnabled)) {
        FlagCell(false)
        return
    }
    when (val limit = plan.features.quota(QuotaFeature.MembersPerWallet)) {
        QuotaLimit.Unlimited -> PlanCellText(stringResource(Res.string.authb_plan_unlimited), AppTheme.colors.ink)
        is QuotaLimit.Limited -> PlanCellText(stringResource(Res.string.authb_plan_people, limit.max), AppTheme.colors.ink)
    }
}

@Composable
private fun FlagCell(included: Boolean) {
    if (included) {
        val description = stringResource(Res.string.authb_plan_included)
        Icon(
            AppIcons.FlowCheckCircle,
            contentDescription = description,
            tint = AppTheme.money.positive,
            modifier = Modifier.size(18.dp),
        )
    } else {
        val description = stringResource(Res.string.authb_plan_not_included)
        Icon(AppIcons.Minus, contentDescription = description, tint = AppTheme.colors.caption, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PeriodTabs(periods: List<BillingPeriod>, selected: BillingPeriod?, onSelect: (BillingPeriod) -> Unit) {
    val track = AppTheme.colors.sunken
    Row(
        modifier = Modifier
            .selectableGroup()
            .drawBehind {
                val inset = 7.dp.toPx()
                drawRoundRect(track, Offset(0f, inset), Size(size.width, size.height - 2 * inset), CornerRadius(10.dp.toPx()))
            }
            .padding(horizontal = 3.dp),
    ) {
        periods.forEach { period ->
            val on = period == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab) { onSelect(period) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(28.dp)
                        .background(if (on) AppTheme.colors.surface else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                ) {
                    Text(
                        period.label(),
                        style = AppTheme.type.caption.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                        color = if (on) AppTheme.colors.ink else AppTheme.colors.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCellText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
