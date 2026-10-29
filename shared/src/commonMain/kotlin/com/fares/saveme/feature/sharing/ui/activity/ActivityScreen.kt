package com.fares.saveme.feature.sharing.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmChip
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.SharingCheckCircle
import com.fares.saveme.designsystem.icon.SharingEdit
import com.fares.saveme.designsystem.icon.SharingPersonAdd
import com.fares.saveme.designsystem.icon.SharingSwap
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityItem
import com.fares.saveme.feature.sharing.domain.model.ActivitySubject
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import com.fares.saveme.feature.sharing.ui.calmColorAt
import com.fares.saveme.feature.sharing.ui.shortDate
import com.fares.saveme.feature.sharing.ui.roleLabelRes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.calm_shr_everyone
import saveme.shared.generated.resources.calm_shr_role_changed
import saveme.shared.generated.resources.calm_shr_today
import saveme.shared.generated.resources.calm_shr_yesterday
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
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun ActivityScreen(state: ActivityState, onIntent: (ActivityIntent) -> Unit) {
    CalmScreen(
        title = stringResource(Res.string.activity_title),
        eyebrow = state.walletName.takeIf { it.isNotEmpty() },
        onBack = { onIntent(ActivityIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        scrollable = false,
        spacing = 10.dp,
    ) {
        when {
            state.loading -> Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                repeat(4) { Skeleton(Modifier.fillMaxWidth().height(68.dp), RoundedCornerShape(20.dp)) }
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

private sealed interface ListRow {
    data class Day(val date: LocalDate) : ListRow
    data class Entry(val item: ActivityItem) : ListRow
}

private fun rows(items: List<ActivityItem>, timeZoneId: String): List<ListRow> {
    val zone = runCatching { TimeZone.of(timeZoneId) }.getOrElse { TimeZone.UTC }
    val out = mutableListOf<ListRow>()
    var last: LocalDate? = null
    for (item in items) {
        val day = item.at.toLocalDateTime(zone).date
        if (day != last) out += ListRow.Day(day)
        last = day
        out += ListRow.Entry(item)
    }
    return out
}

@Composable
private fun ColumnScope.ActivityList(state: ActivityState, onIntent: (ActivityIntent) -> Unit) {
    val listState = rememberLazyListState()
    var person by remember { mutableStateOf<String?>(null) }
    val people = remember(state.items) { state.items.mapNotNull { (it.actor as? Actor.Named)?.name }.distinct() }
    val shown = remember(state.items, person) { person?.let { name -> state.items.filter { (it.actor as? Actor.Named)?.name == name } } ?: state.items }
    val today = remember(state.timeZoneId) {
        Clock.System.now().toLocalDateTime(runCatching { TimeZone.of(state.timeZoneId) }.getOrElse { TimeZone.UTC }).date
    }
    val rows = remember(shown, state.timeZoneId) { rows(shown, state.timeZoneId) }
    if (people.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("activity.filter"),
        ) {
            CalmChip(stringResource(Res.string.calm_shr_everyone), selected = person == null, onClick = { person = null })
            people.forEachIndexed { index, name -> PersonChip(name, calmColorAt(index), selected = person == name, onClick = { person = if (person == name) null else name }) }
        }
    }
    // The next page loads when the list is scrolled near its end (FR-024).
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 5 } ?: false } }
    LaunchedEffect(state.nextCursor, state.items.size) {
        snapshotFlow { nearEnd }.collect { if (it) onIntent(ActivityIntent.LoadMore) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("activity.list"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { row -> when (row) { is ListRow.Day -> "day-${row.date}"; is ListRow.Entry -> row.item.id } }) { row ->
            when (row) {
                is ListRow.Day -> Text(
                    when (row.date) {
                        today -> stringResource(Res.string.calm_shr_today)
                        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.calm_shr_yesterday)
                        else -> shortDate(row.date)
                    },
                    style = AppTheme.type.label,
                    color = AppTheme.colors.ink2,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { heading() },
                )
                is ListRow.Entry -> EntryRow(row.item, state.timeZoneId, onIntent)
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
    val badge = item.action.badge()
    val tappable = item.transactionId != null
    val named = item.subject.kind.carriesName()
    val detail = listOfNotNull(
        item.subject.name.takeIf { named },
        if (named && item.subject.name != null && item.subject.kind != SubjectKind.Transaction) item.subject.kind.label() else null,
    ).joinToString(", ")
    val subtitle = if (detail.isNotEmpty()) "$detail · $time" else time
    CalmCard(
        radius = 20.dp,
        padding = PaddingValues(14.dp),
        modifier = Modifier.testTag("activity.row.${item.id}"),
        onClick = if (item.transactionId != null) ({ onIntent(ActivityIntent.ItemClicked(item.id)) }) else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            CalmIconTile(badge.icon, badge.tint, badge.container, size = 40.dp, radius = 13.dp)
            Column(Modifier.weight(1f)) {
                Text(item.sentence(), style = AppTheme.type.label.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold), color = if (tappable) AppTheme.colors.primary else AppTheme.colors.ink)
                Text(subtitle, style = AppTheme.type.caption, color = AppTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private class Badge(val icon: ImageVector, val tint: Color, val container: Color)

private val memberTint = Color(0xFF6B49A8)
private val memberContainer = Color(0xFFEEE6F8)

@Composable
private fun ActivityAction.badge(): Badge {
    val colors = AppTheme.colors
    val money = AppTheme.money
    return when (this) {
        ActivityAction.Created -> Badge(AppIcons.Plus, colors.primary, colors.primaryContainer)
        ActivityAction.Edited -> Badge(AppIcons.SharingEdit, colors.primary, colors.primaryContainer)
        ActivityAction.Deleted -> Badge(AppIcons.Delete, money.over, money.overContainer)
        ActivityAction.Restored -> Badge(AppIcons.Undo, colors.primary, colors.primaryContainer)
        ActivityAction.Archived -> Badge(AppIcons.Wallet, colors.ink2, colors.sunken)
        ActivityAction.OwnershipTransferred, ActivityAction.RoleChanged, ActivityAction.RoleLimitedByPlan ->
            Badge(AppIcons.SharingSwap, money.pending, money.pendingContainer)
        ActivityAction.Confirmed -> Badge(AppIcons.SharingCheckCircle, money.positive, money.positiveContainer)
        ActivityAction.Skipped -> Badge(AppIcons.Minus, colors.ink2, colors.sunken)
        ActivityAction.Joined, ActivityAction.Invited -> Badge(AppIcons.SharingPersonAdd, memberTint, memberContainer)
        ActivityAction.Left -> Badge(AppIcons.Logout, memberTint, memberContainer)
        ActivityAction.Removed, ActivityAction.InvitationRevoked -> Badge(AppIcons.Close, memberTint, memberContainer)
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
    val subjectText = if (subject.kind.carriesName()) subject.kind.label(roleWire) else subject.text(roleWire)
    val subjectText2 = if (subject.kind == SubjectKind.RecurringRule || subject.kind == SubjectKind.Occurrence) "" else subjectText
    val roleText = roleWire?.let { stringResource(roleLabelRes(it)) } ?: ""
    return stringResource(action.res(), actorText, subjectText2, roleText).trim()
}

/** A transaction, rule or occurrence is named in the line under the sentence; people and wallets are named in it. */
private fun SubjectKind.carriesName(): Boolean = this == SubjectKind.Transaction || this == SubjectKind.RecurringRule || this == SubjectKind.Occurrence

@Composable
private fun SubjectKind.label(roleWire: String? = null): String = when (this) {
    SubjectKind.Transaction -> stringResource(Res.string.activity_subject_transaction)
    SubjectKind.RecurringRule -> stringResource(Res.string.activity_subject_rule)
    SubjectKind.Occurrence -> stringResource(Res.string.activity_subject_occurrence)
    SubjectKind.Member -> stringResource(Res.string.activity_subject_member)
    SubjectKind.Invitation -> stringResource(Res.string.activity_subject_invitation, roleWire?.let { stringResource(roleLabelRes(it)) } ?: "")
    SubjectKind.Wallet -> stringResource(Res.string.activity_subject_wallet)
}

/** The avatar-and-name filter chip of one person who appears in the list. */
@Composable
private fun PersonChip(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.heightIn(min = 48.dp).selectable(selected = selected, role = Role.Button, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(36.dp)
                .background(if (selected) AppTheme.colors.ink else AppTheme.colors.surface, AppTheme.shapes.pill)
                .then(if (selected) Modifier else Modifier.border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill))
                .padding(start = 6.dp, end = 12.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp).background(color, CircleShape)) {
                Text(name.take(1).uppercase(), style = AppTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = Color.White)
            }
            Text(
                name,
                style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = if (selected) AppTheme.colors.background else AppTheme.colors.ink,
                maxLines = 1,
            )
        }
    }
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
    ActivityAction.RoleChanged -> Res.string.calm_shr_role_changed
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
