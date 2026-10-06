package com.fares.saveme.feature.auth.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.BudgetProgress
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.DueCard
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.ui.signout.SignOutConfirmSheet
import com.fares.saveme.feature.auth.ui.verify.text
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.recurring_cd_due_card
import saveme.shared.generated.resources.recurring_due_count
import saveme.shared.generated.resources.recurring_rule_title
import saveme.shared.generated.resources.action_resend_email
import saveme.shared.generated.resources.home_verify_prompt
import saveme.shared.generated.resources.cd_account
import saveme.shared.generated.resources.home_cancel_deletion
import saveme.shared.generated.resources.home_deletion_cancelled
import saveme.shared.generated.resources.home_deletion_pending
import saveme.shared.generated.resources.home_signed_in_as
import saveme.shared.generated.resources.home_title
import saveme.shared.generated.resources.home_add_transaction
import saveme.shared.generated.resources.home_categories
import saveme.shared.generated.resources.budgets_all_expenses
import saveme.shared.generated.resources.budgets_cd_card
import saveme.shared.generated.resources.budgets_category_unavailable
import saveme.shared.generated.resources.budgets_home_card_title
import saveme.shared.generated.resources.budgets_home_row
import saveme.shared.generated.resources.budgets_home_see_all
import saveme.shared.generated.resources.budgets_limit_line
import saveme.shared.generated.resources.budgets_status_waiting
import saveme.shared.generated.resources.reports_home_row
import saveme.shared.generated.resources.budgets_unknown_progress
import saveme.shared.generated.resources.home_invitation
import saveme.shared.generated.resources.home_invitation_open
import saveme.shared.generated.resources.home_wallets
import saveme.shared.generated.resources.sign_out_action
import kotlin.time.Instant

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            AppTopAppBar(
                stringResource(Res.string.home_title),
                actions = {
                    AppIconButton(AppIcons.Person, stringResource(Res.string.cd_account), { onIntent(HomeIntent.AccountClicked) })
                },
            )
        },
    ) {
        state.deletionPendingOn?.let { date ->
            // Always visible while pending, with its way out (spec 002 FR-019).
            InlineAlert(
                tone = AlertTone.Warning,
                text = stringResource(Res.string.home_deletion_pending, formatDate(date, TimeZone.currentSystemDefault().id)),
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
            InlineAlert(
                tone = AlertTone.Info,
                text = stringResource(Res.string.home_invitation),
                modifier = Modifier.testTag("home.invitation"),
                actionLabel = stringResource(Res.string.home_invitation_open),
                onAction = { onIntent(HomeIntent.InvitationClicked) },
            )
        }
        if (state.dueCount > 0) {
            val card = pluralStringResource(Res.plurals.recurring_due_count, state.dueCount, state.dueCount)
            DueCard(
                countText = card,
                firstTitle = state.firstDue?.title ?: stringResource(Res.string.recurring_rule_title),
                firstDate = state.firstDue?.date?.let { formatDate(it) }.orEmpty(),
                modifier = Modifier.testTag("home.due"),
                contentDescription = stringResource(Res.string.recurring_cd_due_card, card),
                onClick = { onIntent(HomeIntent.DueClicked) },
            )
        }
        if (state.budgets.isNotEmpty()) BudgetsCard(state.budgets, onIntent)
        PrimaryButton(
            stringResource(Res.string.home_add_transaction),
            { onIntent(HomeIntent.AddTransactionClicked) },
            modifier = Modifier.testTag("home.addTransaction"),
        )
        AppListItem(
            headline = stringResource(Res.string.home_wallets),
            modifier = Modifier.testTag("home.wallets"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(HomeIntent.WalletsClicked) },
        )
        AppListItem(
            headline = stringResource(Res.string.home_categories),
            modifier = Modifier.testTag("home.categories"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(HomeIntent.CategoriesClicked) },
        )
        AppListItem(
            headline = stringResource(Res.string.budgets_home_row),
            modifier = Modifier.testTag("home.budgets"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(HomeIntent.BudgetsClicked) },
        )
        AppListItem(
            headline = stringResource(Res.string.reports_home_row),
            modifier = Modifier.testTag("home.reports"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(HomeIntent.ReportsClicked) },
        )
        Text(stringResource(Res.string.home_signed_in_as, state.email), style = AppTheme.type.body, color = AppTheme.colors.ink)
        DestructiveButton(stringResource(Res.string.sign_out_action), { onIntent(HomeIntent.SignOutClicked) })
    }
    if (state.confirmSignOutVisible) {
        SignOutConfirmSheet(
            signingOut = state.signingOut,
            onConfirm = { onIntent(HomeIntent.SignOutConfirmed) },
            onDismiss = { onIntent(HomeIntent.SignOutDismissed) },
            pendingChanges = state.sync.pending,
            onSyncNow = { onIntent(HomeIntent.SyncNowClicked) },
        )
    }
}

/** Up to three active budgets with the design system's progress element, and "See all" (spec 012 FR-017). */
@Composable
internal fun BudgetsCard(budgets: List<BudgetGlimpse>, onIntent: (HomeIntent) -> Unit) {
    val card = stringResource(Res.string.budgets_cd_card)
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.xs)
            .testTag("home.budgetsCard"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.budgets_home_card_title),
                style = AppTheme.type.itemTitle,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            AppTextButton(
                stringResource(Res.string.budgets_home_see_all),
                { onIntent(HomeIntent.BudgetsClicked) },
                modifier = Modifier.testTag("home.budgetsCard.all").semantics { contentDescription = card },
            )
        }
        budgets.forEachIndexed { index, glimpse ->
            Column(
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onIntent(HomeIntent.BudgetClicked(glimpse.id)) }
                    .testTag("home.budgetsCard.$index"),
            ) {
                val name = when {
                    glimpse.categoryName == null -> stringResource(Res.string.budgets_all_expenses)
                    glimpse.categoryName.isEmpty() -> stringResource(Res.string.budgets_category_unavailable)
                    else -> glimpse.categoryName
                }
                if (glimpse.spentMinor != null) {
                    BudgetProgress(name, glimpse.spentMinor, glimpse.limitMinor, glimpse.currency)
                } else {
                    Text(name, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
                    Text(
                        stringResource(Res.string.budgets_limit_line, formatMoney(glimpse.limitMinor, glimpse.currency)),
                        style = AppTheme.type.body, color = AppTheme.colors.ink2,
                    )
                    Text(stringResource(Res.string.budgets_unknown_progress), style = AppTheme.type.caption, color = AppTheme.colors.caption)
                }
                if (glimpse.includesWaiting) {
                    Text(stringResource(Res.string.budgets_status_waiting), style = AppTheme.type.caption, color = AppTheme.colors.caption)
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
            HomeState(email = "sara@example.com", deletionPendingOn = Instant.parse("2026-10-28T10:00:00Z")),
            {},
        )
    }
}
