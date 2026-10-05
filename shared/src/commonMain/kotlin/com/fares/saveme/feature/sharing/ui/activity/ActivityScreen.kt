package com.fares.saveme.feature.sharing.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityItem
import com.fares.saveme.feature.sharing.domain.model.ActivitySubject
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import com.fares.saveme.feature.sharing.ui.roleLabelRes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.activity_archived
import saveme.shared.generated.resources.activity_automatic
import saveme.shared.generated.resources.activity_confirmed
import saveme.shared.generated.resources.activity_created
import saveme.shared.generated.resources.activity_deleted
import saveme.shared.generated.resources.activity_edited
import saveme.shared.generated.resources.activity_empty
import saveme.shared.generated.resources.activity_failed
import saveme.shared.generated.resources.activity_failed_plain
import saveme.shared.generated.resources.activity_former_member
import saveme.shared.generated.resources.activity_invitation_revoked
import saveme.shared.generated.resources.activity_invited
import saveme.shared.generated.resources.activity_joined
import saveme.shared.generated.resources.activity_left
import saveme.shared.generated.resources.activity_loading_more
import saveme.shared.generated.resources.activity_offline
import saveme.shared.generated.resources.activity_ownership_transferred
import saveme.shared.generated.resources.activity_removed
import saveme.shared.generated.resources.activity_restored
import saveme.shared.generated.resources.activity_role_changed
import saveme.shared.generated.resources.activity_role_limited_by_plan
import saveme.shared.generated.resources.activity_skipped
import saveme.shared.generated.resources.activity_subject_invitation
import saveme.shared.generated.resources.activity_subject_member
import saveme.shared.generated.resources.activity_subject_occurrence
import saveme.shared.generated.resources.activity_subject_rule
import saveme.shared.generated.resources.activity_subject_transaction
import saveme.shared.generated.resources.activity_subject_wallet
import saveme.shared.generated.resources.activity_title
import saveme.shared.generated.resources.activity_window
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.member_unnamed
import kotlin.time.Instant

@Composable
fun ActivityScreen(state: ActivityState, onIntent: (ActivityIntent) -> Unit) {
    AppScreen(
        scrollable = false,
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.activity_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(ActivityIntent.Back) }) },
            )
        },
    ) {
        when {
            state.loading -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                repeat(4) { Skeleton(Modifier.fillMaxWidth().height(56.dp)) }
            }
            state.error != null && state.items.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
                ErrorBox(state.error, onIntent)
            }
            state.isEmpty -> EmptyState(title = stringResource(Res.string.activity_title), body = stringResource(Res.string.activity_empty))
            else -> ActivityList(state, onIntent)
        }
    }
}

@Composable
private fun ErrorBox(error: ActivityError, onIntent: (ActivityIntent) -> Unit) {
    val text = when (error) {
        ActivityError.Offline -> stringResource(Res.string.activity_offline)
        is ActivityError.Failed -> if (error.requestId != null) stringResource(Res.string.activity_failed, error.requestId) else stringResource(Res.string.activity_failed_plain)
    }
    InlineAlert(
        if (error == ActivityError.Offline) AlertTone.Info else AlertTone.Error, text, Modifier.testTag("activity.error"),
        actionLabel = stringResource(Res.string.action_retry), onAction = { onIntent(ActivityIntent.Retry) },
    )
}

private sealed interface Row {
    data class Day(val date: LocalDate) : Row
    data class Entry(val item: ActivityItem) : Row
}

private fun rows(items: List<ActivityItem>, timeZoneId: String): List<Row> {
    val zone = runCatching { TimeZone.of(timeZoneId) }.getOrElse { TimeZone.UTC }
    val out = mutableListOf<Row>()
    var last: LocalDate? = null
    for (item in items) {
        val day = item.at.toLocalDateTime(zone).date
        if (day != last) out += Row.Day(day)
        last = day
        out += Row.Entry(item)
    }
    return out
}

@Composable
private fun ActivityList(state: ActivityState, onIntent: (ActivityIntent) -> Unit) {
    val listState = rememberLazyListState()
    val rows = remember(state.items, state.timeZoneId) { rows(state.items, state.timeZoneId) }
    // The next page loads when the list is scrolled near its end (FR-024).
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 5 } ?: false } }
    LaunchedEffect(state.nextCursor, state.items.size) {
        snapshotFlow { nearEnd }.collect { if (it) onIntent(ActivityIntent.LoadMore) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("activity.list"), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
        items(rows, key = { row -> when (row) { is Row.Day -> "day-${row.date}"; is Row.Entry -> row.item.id } }) { row ->
            when (row) {
                is Row.Day -> Text(
                    formatDate(row.date),
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.fillMaxWidth().background(AppTheme.colors.background).padding(vertical = AppTheme.spacing.xs).semantics { heading() },
                )
                is Row.Entry -> EntryRow(row.item, state.timeZoneId, onIntent)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = Modifier.padding(vertical = AppTheme.spacing.m)) {
                if (state.loadingMore) Text(stringResource(Res.string.activity_loading_more), style = AppTheme.type.caption, color = AppTheme.colors.caption)
                state.error?.let { ErrorBox(it, onIntent) }
                Text(stringResource(Res.string.activity_window), style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag("activity.window"))
            }
        }
    }
}

@Composable
private fun EntryRow(item: ActivityItem, timeZoneId: String, onIntent: (ActivityIntent) -> Unit) {
    val time = item.at.toLocalDateTime(runCatching { TimeZone.of(timeZoneId) }.getOrElse { TimeZone.UTC }).let {
        it.hour.toString().padStart(2, '0') + ":" + it.minute.toString().padStart(2, '0')
    }
    Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        AppListItem(
            headline = item.sentence(),
            supporting = time,
            modifier = Modifier.testTag("activity.row.${item.id}"),
            trailing = if (item.transactionId != null) ListTrailing.Chevron else ListTrailing.None,
            onClick = if (item.transactionId != null) ({ onIntent(ActivityIntent.ItemClicked(item.id)) }) else null,
        )
    }
}

/** "<actor> <action> <subject>" in the app's language; the roles and names come from the phone (FR-025). */
@Composable
internal fun ActivityItem.sentence(): String {
    val actorText = when (val a = actor) {
        is Actor.Named -> a.name
        Actor.FormerMember -> stringResource(Res.string.activity_former_member)
        Actor.UnnamedMember -> stringResource(Res.string.member_unnamed)
        Actor.Automatic -> stringResource(Res.string.activity_automatic)
    }
    val subjectText = subject.text(roleWire)
    val roleText = roleWire?.let { stringResource(roleLabelRes(it)) } ?: ""
    return stringResource(action.res(), actorText, subjectText, roleText)
}

@Composable
private fun ActivitySubject.text(roleWire: String?): String = name ?: when (kind) {
    SubjectKind.Transaction -> stringResource(Res.string.activity_subject_transaction)
    SubjectKind.RecurringRule -> stringResource(Res.string.activity_subject_rule)
    SubjectKind.Occurrence -> stringResource(Res.string.activity_subject_occurrence)
    SubjectKind.Member -> stringResource(Res.string.activity_subject_member)
    SubjectKind.Invitation -> stringResource(Res.string.activity_subject_invitation, roleWire?.let { stringResource(roleLabelRes(it)) } ?: "")
    SubjectKind.Wallet -> stringResource(Res.string.activity_subject_wallet)
}

private fun ActivityAction.res(): StringResource = when (this) {
    ActivityAction.Created -> Res.string.activity_created
    ActivityAction.Edited -> Res.string.activity_edited
    ActivityAction.Deleted -> Res.string.activity_deleted
    ActivityAction.Restored -> Res.string.activity_restored
    ActivityAction.Archived -> Res.string.activity_archived
    ActivityAction.OwnershipTransferred -> Res.string.activity_ownership_transferred
    ActivityAction.Confirmed -> Res.string.activity_confirmed
    ActivityAction.Skipped -> Res.string.activity_skipped
    ActivityAction.Joined -> Res.string.activity_joined
    ActivityAction.Left -> Res.string.activity_left
    ActivityAction.Removed -> Res.string.activity_removed
    ActivityAction.RoleChanged -> Res.string.activity_role_changed
    ActivityAction.RoleLimitedByPlan -> Res.string.activity_role_limited_by_plan
    ActivityAction.Invited -> Res.string.activity_invited
    ActivityAction.InvitationRevoked -> Res.string.activity_invitation_revoked
}

@Preview
@Composable
private fun ActivityScreenPreview() {
    AppTheme {
        ActivityScreen(
            ActivityState(
                walletId = "w1", loading = false,
                items = listOf(
                    ActivityItem("a1", Instant.parse("2026-10-02T10:20:00Z"), Actor.Named("Karim"), ActivityAction.Created, ActivitySubject(SubjectKind.Transaction, "t1", "Groceries", true), null),
                ),
            ),
            {},
        )
    }
}
