package com.fares.saveme.feature.auth.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.budgetPercent
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmVRule
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.BudgetsChevronDown
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.icon.TxEventUpcoming
import com.fares.saveme.designsystem.component.trimHeight
import com.fares.saveme.designsystem.money.MoneyFormatter
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.CategoryColors
import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.ui.verify.text
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_resend_email
import saveme.shared.generated.resources.budgets_all_expenses
import saveme.shared.generated.resources.budgets_cd_card
import saveme.shared.generated.resources.budgets_category_unavailable
import saveme.shared.generated.resources.budgets_home_card_title
import saveme.shared.generated.resources.budgets_home_see_all
import saveme.shared.generated.resources.budgets_status_waiting
import saveme.shared.generated.resources.budgets_unknown_progress
import saveme.shared.generated.resources.cd_account
import saveme.shared.generated.resources.delete_being_deleted
import saveme.shared.generated.resources.home_cancel_deletion
import saveme.shared.generated.resources.home_deletion_cancelled
import saveme.shared.generated.resources.home_deletion_pending
import saveme.shared.generated.resources.home_invitation
import saveme.shared.generated.resources.home_invitation_open
import saveme.shared.generated.resources.home_title
import saveme.shared.generated.resources.home_verify_prompt
import saveme.shared.generated.resources.homec_days_left
import saveme.shared.generated.resources.homec_greeting_afternoon
import saveme.shared.generated.resources.homec_greeting_evening
import saveme.shared.generated.resources.homec_greeting_morning
import saveme.shared.generated.resources.homec_greeting_named
import saveme.shared.generated.resources.homec_hero_title
import saveme.shared.generated.resources.homec_left_to_spend
import saveme.shared.generated.resources.homec_of_limit
import saveme.shared.generated.resources.homec_on_pace
import saveme.shared.generated.resources.homec_over
import saveme.shared.generated.resources.homec_over_by
import saveme.shared.generated.resources.homec_period_cd
import saveme.shared.generated.resources.homec_review
import saveme.shared.generated.resources.homec_spent
import saveme.shared.generated.resources.recurring_cd_due_card
import saveme.shared.generated.resources.recurring_due_count
import saveme.shared.generated.resources.recurring_rule_title
import kotlin.time.Instant

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val hero = state.budgets.firstOrNull { it.categoryName == null && it.spentMinor != null }
    val rows = state.budgets.filter { it !== hero }.take(HOME_ROWS)
    CalmScreen(
        title = null,
        snackbarHostState = snackbar,
        spacing = 12.dp,
        titleSlot = { HomeHeader(state, hero, onIntent) },
    ) {
        state.deletionPendingOn?.let { date ->
            // Always visible while pending, with its way out (spec 002 FR-019).
            InlineAlert(
                tone = AlertTone.Warning,
                text = if (state.beingDeleted) {
                    stringResource(Res.string.delete_being_deleted)
                } else {
                    stringResource(Res.string.home_deletion_pending, formatDate(date, TimeZone.currentSystemDefault().id))
                },
                actionLabel = stringResource(Res.string.home_cancel_deletion),
                onAction = { onIntent(HomeIntent.CancelDeletion) }.takeUnless { state.cancellingDeletion },
            )
        }
        if (!state.emailVerified) {
            // Persistent until the address is verified (FR-012); resend is throttled (FR-013).
            InlineAlert(
                tone = AlertTone.Warning,
                text = stringResource(Res.string.home_verify_prompt),
                modifier = Modifier.testTag("home.verifyPrompt"),
                actionLabel = stringResource(Res.string.action_resend_email),
                onAction = { onIntent(HomeIntent.ResendClicked) }.takeIf { state.resend.canResend },
            )
            state.resend.message?.let { Text(it.text(), style = AppTheme.type.caption, color = AppTheme.colors.ink2) }
        }
        if (state.deletionCancelledNotice) {
            InlineAlert(
                tone = AlertTone.Info,
                text = stringResource(Res.string.home_deletion_cancelled),
            )
        }
        if (state.invitationHeld) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.primaryContainer, RoundedCornerShape(16.dp))
                    .padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                    .testTag("home.invitation"),
            ) {
                Icon(AppIcons.Info, contentDescription = null, tint = AppTheme.colors.primary)
                Text(
                    stringResource(Res.string.home_invitation),
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink,
                    modifier = Modifier.weight(1f),
                )
                AppTextButton(stringResource(Res.string.home_invitation_open), { onIntent(HomeIntent.InvitationClicked) })
            }
        }
        if (hero != null) BudgetHero(hero, onIntent)
        if (state.dueCount > 0) {
            val card = pluralStringResource(Res.plurals.recurring_due_count, state.dueCount, state.dueCount)
            val first = state.firstDue?.title ?: stringResource(Res.string.recurring_rule_title)
            val date = state.firstDue?.date?.let { formatDayMonth(it) }.orEmpty()
            val description = stringResource(Res.string.recurring_cd_due_card, card)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppTheme.money.pendingContainer)
                    .semantics { contentDescription = description }
                    .clickable(role = Role.Button) { onIntent(HomeIntent.DueClicked) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("home.due"),
            ) {
                Icon(AppIcons.TxEventUpcoming, contentDescription = null, tint = AppTheme.money.pending)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(card) }
                        append(" · $first, $date")
                    },
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(Res.string.homec_review), style = AppTheme.type.label, color = AppTheme.money.pending)
            }
        }
        if (rows.isNotEmpty()) BudgetsCard(rows, onIntent)
    }
}

private const val HOME_ROWS = 3

/** Greeting over the budget period (when one is known), and the account button on the right. */
@Composable
private fun HomeHeader(state: HomeState, hero: BudgetGlimpse?, onIntent: (HomeIntent) -> Unit) {
    val hour = state.now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = stringResource(
        when {
            hour < 12 -> Res.string.homec_greeting_morning
            hour < 18 -> Res.string.homec_greeting_afternoon
            else -> Res.string.homec_greeting_evening
        },
    )
    val line = state.displayName?.let { stringResource(Res.string.homec_greeting_named, greeting, it) } ?: greeting
    val period = (hero ?: state.budgets.firstOrNull { it.periodStart != null })
        ?.let { g -> g.periodStart?.let { start -> g.periodEnd?.let { end -> "${formatDayMonth(start)} – ${formatDayMonth(end)}" } } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(line, style = AppTheme.type.label.copy(fontWeight = FontWeight.Medium), color = AppTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (period != null) {
                val description = stringResource(Res.string.homec_period_cd, period)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .trimHeight(16.dp)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = description; heading() }
                        .clickable(role = Role.Button) { onIntent(HomeIntent.BudgetsClicked) }
                        .testTag("home.period"),
                ) {
                    Text(
                        period,
                        style = AppTheme.type.headline.copy(fontSize = 22.sp),
                        color = AppTheme.colors.ink,
                        maxLines = 1,
                    )
                    Icon(AppIcons.BudgetsChevronDown, contentDescription = null, tint = AppTheme.colors.caption)
                }
            } else {
                Text(stringResource(Res.string.home_title), style = AppTheme.type.headline.copy(fontSize = 22.sp), color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
            }
        }
        val account = stringResource(Res.string.cd_account)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = account }
                .clickable(role = Role.Button) { onIntent(HomeIntent.AccountClicked) },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).background(AppTheme.colors.primary, CircleShape)) {
                Text(
                    (state.displayName ?: state.email).firstOrNull()?.uppercase().orEmpty(),
                    style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.onPrimary,
                )
            }
        }
    }
}

/** A whole-unit figure without the currency: "9,700", or "9,700.50" when it has a fraction. */
private fun plainAmount(minor: Long, currency: String): String {
    val digits = MoneyFormatter.digitsFor(currency)
    var unit = 1L
    repeat(digits) { unit *= 10 }
    return if (minor % unit == 0L) MoneyFormatter.number(minor / unit, 0) else MoneyFormatter.number(minor, digits)
}

/** The all-expenses budget: a ring around what is left to spend, then what is spent and where it is heading. */
@Composable
private fun BudgetHero(hero: BudgetGlimpse, onIntent: (HomeIntent) -> Unit) {
    val spent = hero.spentMinor ?: return
    val colors = AppTheme.colors
    val over = spent > hero.limitMinor
    val ring = if (over) AppTheme.money.over else colors.primary
    val fraction = if (hero.limitMinor <= 0) 0f else (spent.toFloat() / hero.limitMinor).coerceIn(0f, 1f)
    val projected = hero.projectedMinor
    CalmCard(
        padding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        modifier = Modifier.testTag("home.hero"),
        onClick = { onIntent(HomeIntent.BudgetClicked(hero.id)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.homec_hero_title),
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            hero.daysLeft?.let { days ->
                Text(
                    pluralStringResource(Res.plurals.homec_days_left, days, days),
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = colors.primary,
                    modifier = Modifier
                        .background(colors.primaryContainer, RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).fillMaxWidth()) {
            val track = colors.sunken
            Canvas(Modifier.size(176.dp)) {
                val width = 13.2.dp.toPx()
                val diameter = 144.3.dp.toPx()
                val arc = Size(diameter, diameter)
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(width))
                if (fraction > 0f) drawArc(ring, -90f, 360f * fraction, false, topLeft, arc, style = Stroke(width, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (over) Res.string.homec_over_by else Res.string.homec_left_to_spend),
                    style = AppTheme.type.caption,
                    color = colors.ink2,
                )
                Text(
                    plainAmount(kotlin.math.abs(hero.limitMinor - spent), hero.currency),
                    style = AppTheme.type.numeralHero.copy(fontSize = 36.sp, textDirection = TextDirection.Ltr),
                    color = if (over) AppTheme.money.over else colors.ink,
                    maxLines = 1,
                )
                Text(
                    stringResource(Res.string.homec_of_limit, hero.currency, plainAmount(hero.limitMinor, hero.currency)),
                    style = AppTheme.type.caption.copy(textDirection = TextDirection.Ltr),
                    color = colors.ink2,
                )
            }
        }
        CalmDivider(Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 12.dp).height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.homec_spent), style = AppTheme.type.caption.copy(fontSize = 11.5.sp), color = colors.ink2)
                Text(
                    "${hero.currency} ${plainAmount(spent, hero.currency)}",
                    style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold, textDirection = TextDirection.Ltr),
                    color = colors.ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            CalmVRule(Modifier.padding(horizontal = 14.dp, vertical = 2.dp).fillMaxHeight())
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.homec_on_pace), style = AppTheme.type.caption.copy(fontSize = 11.5.sp), color = colors.ink2)
                if (projected != null) {
                    Text(
                        "${hero.currency} ${plainAmount(projected, hero.currency)}",
                        style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold, textDirection = TextDirection.Ltr),
                        color = if (projected > hero.limitMinor) AppTheme.money.over else AppTheme.money.positive,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (hero.includesWaiting) {
            Text(
                stringResource(Res.string.budgets_status_waiting),
                style = AppTheme.type.caption,
                color = colors.caption,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Up to three more active budgets: category tile, spent amount and a thin bar with its share (spec 012 FR-017). */
@Composable
internal fun BudgetsCard(budgets: List<BudgetGlimpse>, onIntent: (HomeIntent) -> Unit) {
    val card = stringResource(Res.string.budgets_cd_card)
    CalmCard(
        padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        modifier = Modifier.testTag("home.budgetsCard"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                stringResource(Res.string.budgets_home_card_title),
                style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .trimHeight(16.dp)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onIntent(HomeIntent.BudgetsClicked) }
                    .testTag("home.budgetsCard.all")
                    .semantics { contentDescription = card },
            ) {
                Text(
                    stringResource(Res.string.budgets_home_see_all),
                    style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.primary,
                )
            }
        }
        budgets.forEachIndexed { index, glimpse ->
            if (index > 0) CalmDivider()
            val name = when {
                glimpse.categoryName == null -> stringResource(Res.string.budgets_all_expenses)
                glimpse.categoryName.isEmpty() -> stringResource(Res.string.budgets_category_unavailable)
                else -> glimpse.categoryName
            }
            val spent = glimpse.spentMinor
            val over = spent != null && spent > glimpse.limitMinor
            val swatch = CategoryColors.of(glimpse.colorHex)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onIntent(HomeIntent.BudgetClicked(glimpse.id)) }
                    .padding(vertical = 11.dp)
                    .testTag("home.budgetsCard.$index"),
            ) {
                CalmIconTile(
                    CategoryIcons.of(glimpse.iconKey),
                    tint = if (over) AppTheme.money.over else swatch.tint,
                    container = if (over) AppTheme.money.overContainer else swatch.container,
                    size = 34.dp,
                    iconSize = 18.dp,
                    radius = 11.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row {
                        Text(name, style = AppTheme.type.label.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${glimpse.currency} ${plainAmount(spent ?: glimpse.limitMinor, glimpse.currency)}",
                            style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
                            color = if (over) AppTheme.money.over else AppTheme.colors.ink,
                        )
                    }
                    if (spent != null) {
                        val share = budgetPercent(spent, glimpse.limitMinor)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f).height(6.dp).background(AppTheme.colors.sunken, RoundedCornerShape(9.dp))) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((share / 100f).coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .background(if (over) AppTheme.money.over else AppTheme.colors.primary, RoundedCornerShape(9.dp)),
                                )
                            }
                            Text(
                                if (over) stringResource(Res.string.homec_over) else "$share%",
                                style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = if (over) FontWeight.Bold else FontWeight.SemiBold),
                                color = if (over) AppTheme.money.over else AppTheme.colors.ink2,
                            )
                        }
                    } else {
                        Text(stringResource(Res.string.budgets_unknown_progress), style = AppTheme.type.caption, color = AppTheme.colors.caption)
                    }
                    if (glimpse.includesWaiting) {
                        Text(stringResource(Res.string.budgets_status_waiting), style = AppTheme.type.caption, color = AppTheme.colors.caption)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeScreen(
            HomeState(email = "sara@example.com", displayName = "Sara", deletionPendingOn = Instant.parse("2026-10-28T10:00:00Z")),
            {},
        )
    }
}
