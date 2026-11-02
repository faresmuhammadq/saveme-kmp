package com.fares.saveme.feature.sharedbudgets.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.BudgetProgress
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmSectionTitle
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.format.formatDayMonth
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import com.fares.saveme.feature.sharedbudgets.ui.editor.EditorMessage
import com.fares.saveme.feature.sharing.domain.LinkSharer
import com.fares.saveme.feature.sharing.domain.ShareText
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.app_name
import saveme.shared.generated.resources.budgets_on_pace
import saveme.shared.generated.resources.budgets_period_range
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.sb_categories
import saveme.shared.generated.resources.sb_category_unavailable
import saveme.shared.generated.resources.sb_earlier
import saveme.shared.generated.resources.sb_edit
import saveme.shared.generated.resources.sb_expenses
import saveme.shared.generated.resources.sb_later
import saveme.shared.generated.resources.sb_past_period
import saveme.shared.generated.resources.sb_former_member
import saveme.shared.generated.resources.sb_invite
import saveme.shared.generated.resources.sb_leave
import saveme.shared.generated.resources.sb_leave_body
import saveme.shared.generated.resources.sb_leave_title
import saveme.shared.generated.resources.sb_link_unavailable
import saveme.shared.generated.resources.sb_members
import saveme.shared.generated.resources.sb_msg_failed
import saveme.shared.generated.resources.sb_msg_not_allowed
import saveme.shared.generated.resources.sb_msg_offline
import saveme.shared.generated.resources.sb_msg_plan_full
import saveme.shared.generated.resources.sb_msg_stale
import saveme.shared.generated.resources.sb_no_limit
import saveme.shared.generated.resources.sb_other
import saveme.shared.generated.resources.sb_owner
import saveme.shared.generated.resources.sb_remove
import saveme.shared.generated.resources.sb_remove_body
import saveme.shared.generated.resources.sb_remove_title
import saveme.shared.generated.resources.sb_share_text
import saveme.shared.generated.resources.sb_spent_label
import saveme.shared.generated.resources.sb_transfer
import saveme.shared.generated.resources.sb_transfer_body
import saveme.shared.generated.resources.sb_transfer_title
import saveme.shared.generated.resources.sb_updated
import saveme.shared.generated.resources.sb_you
import kotlin.time.Instant

@Composable
fun SharedBudgetDetailRoute(
    budgetId: String,
    onEffect: (SharedBudgetDetailEffect) -> Unit,
    onRequireVerification: () -> Unit,
    viewModel: SharedBudgetDetailViewModel = koinViewModel(key = "shared-budget-$budgetId") { parametersOf(budgetId) },
    sharer: LinkSharer = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.onIntent(SharedBudgetDetailIntent.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                // The text carries a one-time link: straight to the platform, never stored, logged or shown (P-09.2).
                is SharedBudgetDetailEffect.ShareLink -> sharer.share(ShareText.build(getString(Res.string.app_name), effect.url, getString(Res.string.sb_share_text)))
                SharedBudgetDetailEffect.LinkUnavailable -> scope.launch { snackbar.showSnackbar(getString(Res.string.sb_link_unavailable)) }
                is SharedBudgetDetailEffect.ShowMessage -> scope.launch { snackbar.showSnackbar(getString(effect.message.res())) }
                SharedBudgetDetailEffect.RequireVerification -> onRequireVerification()
                else -> onEffect(effect)
            }
        }
    }
    SharedBudgetDetailScreen(state, viewModel::onIntent, snackbar)
}

private fun EditorMessage.res() = when (this) {
    EditorMessage.Offline -> Res.string.sb_msg_offline
    EditorMessage.NotAllowed -> Res.string.sb_msg_not_allowed
    EditorMessage.PlanFull -> Res.string.sb_msg_plan_full
    EditorMessage.Stale -> Res.string.sb_msg_stale
    EditorMessage.Failed -> Res.string.sb_msg_failed
}

@Composable
fun SharedBudgetDetailScreen(
    state: SharedBudgetDetailState,
    onIntent: (SharedBudgetDetailIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val progress = state.progress
    CalmScreen(
        title = null,
        onBack = { onIntent(SharedBudgetDetailIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        titleSlot = { CalmTitle(progress?.name.orEmpty()) },
        actions = {
            if (progress != null) AppTextButton(stringResource(Res.string.sb_edit), { onIntent(SharedBudgetDetailIntent.EditClicked) }, modifier = Modifier.testTag("sharedBudget.edit"))
        },
    ) {
        if (progress == null) {
            repeat(3) { Skeleton(Modifier.fillMaxWidth().height(96.dp), AppTheme.shapes.hero) }
            return@CalmScreen
        }
        PeriodBar(state, onIntent, progress)
        Overall(progress)
        Categories(progress)
        Entries(progress)
        Members(state, onIntent, progress)
        progress.refreshedAt?.let { at ->
            Text(
                stringResource(Res.string.sb_updated, formatDateTime(Instant.parse(at), TimeZone.currentSystemDefault().id)),
                style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag("sharedBudget.updatedAt"),
            )
        }
    }
    Confirms(state, onIntent)
}

/** The period's dates with a way to step to the one before or after; earlier periods keep the limits they had. */
@Composable
private fun PeriodBar(state: SharedBudgetDetailState, onIntent: (SharedBudgetDetailIntent) -> Unit, progress: SharedBudgetProgress) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AppTextButton(stringResource(Res.string.sb_earlier), { onIntent(SharedBudgetDetailIntent.EarlierClicked) }, enabled = progress.canGoBack, modifier = Modifier.testTag("sharedBudget.earlier"))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(Res.string.budgets_period_range, formatDayMonth(progress.period.start), formatDayMonth(progress.period.end)),
                style = AppTheme.type.label, color = AppTheme.colors.ink2, modifier = Modifier.testTag("sharedBudget.period"),
            )
            if (!progress.isCurrent) Text(stringResource(Res.string.sb_past_period), style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
        AppTextButton(stringResource(Res.string.sb_later), { onIntent(SharedBudgetDetailIntent.LaterClicked) }, enabled = state.periodsBack > 0, modifier = Modifier.testTag("sharedBudget.later"))
    }
}

/** What was spent, newest first: amount, date, category, note and who paid (never a wallet). */
@Composable
private fun Entries(progress: SharedBudgetProgress) {
    if (progress.entries.isEmpty()) return
    CalmSectionTitle(stringResource(Res.string.sb_expenses))
    CalmCard(modifier = Modifier.testTag("sharedBudget.entries")) {
        Rows {
            progress.entries.forEach { entry ->
                val who = when {
                    entry.isMine -> stringResource(Res.string.sb_you)
                    entry.authorName != null -> entry.authorName
                    else -> stringResource(Res.string.sb_former_member)
                }
                Column(modifier = Modifier.testTag("sharedBudget.entry.${entry.id}")) {
                    MoneyRow(entry.categoryName.ifEmpty { stringResource(Res.string.sb_other) }, entry.spentMinor, progress.currency)
                    Text("${formatDayMonth(LocalDate.parse(entry.date))} · $who", style = AppTheme.type.caption, color = AppTheme.colors.caption)
                    entry.note?.let { Text(it, style = AppTheme.type.caption, color = AppTheme.colors.ink2) }
                }
            }
        }
    }
}

@Composable
private fun Overall(progress: SharedBudgetProgress) {
    CalmCard(modifier = Modifier.testTag("sharedBudget.overall")) {
        val limit = progress.limitMinor
        if (limit != null) {
            BudgetProgress(progress.name, progress.spentMinor, limit, progress.currency)
        } else {
            Text(stringResource(Res.string.sb_spent_label), style = AppTheme.type.label, color = AppTheme.colors.ink2)
            Text(formatMoney(progress.spentMinor, progress.currency), style = AppTheme.type.itemTitle, color = AppTheme.colors.ink)
            Text(stringResource(Res.string.sb_no_limit), style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
        progress.projectedMinor?.let {
            Text(stringResource(Res.string.budgets_on_pace, formatMoney(it, progress.currency)), style = AppTheme.type.caption, color = AppTheme.colors.caption)
        }
    }
}

/** Each category of the budget's list, then what went into other categories. */
@Composable
private fun Categories(progress: SharedBudgetProgress) {
    if (progress.lines.isEmpty() && progress.unlistedMinor == 0L) return
    CalmSectionTitle(stringResource(Res.string.sb_categories))
    CalmCard(modifier = Modifier.testTag("sharedBudget.categories")) {
        Rows {
            progress.lines.forEach { line ->
                val name = line.name.ifEmpty { stringResource(Res.string.sb_category_unavailable) }
                val limit = line.limitMinor
                if (limit != null) BudgetProgress(name, line.spentMinor, limit, progress.currency) else MoneyRow(name, line.spentMinor, progress.currency)
            }
            if (progress.unlistedMinor > 0) MoneyRow(stringResource(Res.string.sb_other), progress.unlistedMinor, progress.currency)
        }
    }
}

/** Who spent what; the owner invites, removes and hands over, anyone else can leave. */
@Composable
private fun Members(state: SharedBudgetDetailState, onIntent: (SharedBudgetDetailIntent) -> Unit, progress: SharedBudgetProgress) {
    CalmSectionTitle(stringResource(Res.string.sb_members))
    CalmCard(modifier = Modifier.testTag("sharedBudget.members")) {
        Rows {
            progress.members.forEach { member ->
                val name = when {
                    member.isMe -> stringResource(Res.string.sb_you)
                    member.name != null -> member.name
                    else -> stringResource(Res.string.sb_former_member)
                }
                Column {
                    MoneyRow(if (member.isOwner) "$name · ${stringResource(Res.string.sb_owner)}" else name, member.spentMinor, progress.currency)
                    if (state.isOwner && member.isMember && !member.isMe) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextButton(stringResource(Res.string.sb_transfer), { onIntent(SharedBudgetDetailIntent.TransferClicked(member.userId)) }, enabled = !state.busy)
                            AppTextButton(stringResource(Res.string.sb_remove), { onIntent(SharedBudgetDetailIntent.RemoveClicked(member.userId)) }, enabled = !state.busy)
                        }
                    }
                }
            }
            if (state.isOwner) {
                SecondaryButton(stringResource(Res.string.sb_invite), { onIntent(SharedBudgetDetailIntent.InviteClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudget.invite"), enabled = !state.busy, loading = state.busy)
            } else {
                SecondaryButton(stringResource(Res.string.sb_leave), { onIntent(SharedBudgetDetailIntent.LeaveClicked) }, modifier = Modifier.fillMaxWidth().testTag("sharedBudget.leave"), enabled = !state.busy)
            }
        }
    }
}

@Composable
private fun Confirms(state: SharedBudgetDetailState, onIntent: (SharedBudgetDetailIntent) -> Unit) {
    val step = state.confirm ?: return
    val (title, body, label, destructive) = when (step) {
        DetailConfirm.Leave -> Quad(stringResource(Res.string.sb_leave_title), stringResource(Res.string.sb_leave_body), stringResource(Res.string.sb_leave), true)
        is DetailConfirm.Remove -> Quad(stringResource(Res.string.sb_remove_title), stringResource(Res.string.sb_remove_body, step.name ?: stringResource(Res.string.sb_former_member)), stringResource(Res.string.sb_remove), true)
        is DetailConfirm.Transfer -> Quad(stringResource(Res.string.sb_transfer_title), stringResource(Res.string.sb_transfer_body, step.name ?: stringResource(Res.string.sb_former_member)), stringResource(Res.string.sb_transfer), false)
    }
    AppAlertDialog(
        title = title, text = body, confirmLabel = label, onConfirm = { onIntent(SharedBudgetDetailIntent.ConfirmYes) },
        dismissLabel = stringResource(Res.string.action_cancel), onDismiss = { onIntent(SharedBudgetDetailIntent.ConfirmNo) },
        destructive = destructive, confirmLoading = state.busy,
    )
}

private data class Quad(val title: String, val body: String, val label: String, val destructive: Boolean)

@Composable
private fun MoneyRow(label: String, minor: Long, currency: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
        Text(label, style = AppTheme.type.body, color = AppTheme.colors.ink)
        Text(formatMoney(minor, currency), style = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}

@Composable
private fun Rows(content: @Composable ColumnScope.() -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
