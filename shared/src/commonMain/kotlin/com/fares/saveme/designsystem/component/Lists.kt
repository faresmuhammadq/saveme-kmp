package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_not_synced
import saveme.shared.generated.resources.member_you
import saveme.shared.generated.resources.role_editor
import saveme.shared.generated.resources.role_owner
import saveme.shared.generated.resources.role_viewer

/**
 * 72dp row. A pending dot means the change has not reached the server yet. [leading] (a category
 * tile) and [status] (words for what the dot means, P-08.3) are optional; [directionLabel] names
 * money in or out for screen readers, since the sign alone is not announced (FR-035).
 */
@Composable
fun TransactionRow(
    title: String,
    subtitle: String,
    amountMinor: Long,
    currency: String,
    kind: MoneyKind,
    pendingSync: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    status: String? = null,
    directionLabel: String? = null,
    amountModifier: Modifier = Modifier,
    statusModifier: Modifier = Modifier,
) {
    val notSynced = stringResource(Res.string.cd_not_synced)
    val amountText = formatMoney(
        when (kind) {
            MoneyKind.Income -> kotlin.math.abs(amountMinor)
            MoneyKind.Expense -> -kotlin.math.abs(amountMinor)
            else -> amountMinor
        },
        currency,
        showPlus = kind == MoneyKind.Income,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.rowTwoLine)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppTheme.spacing.m, vertical = if (leading != null || status != null) AppTheme.spacing.xs else 0.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = AppTheme.type.caption, color = AppTheme.colors.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                Text(status, style = AppTheme.type.caption, color = AppTheme.money.pending, modifier = statusModifier)
            }
        }
        if (pendingSync) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(AppTheme.money.pending, CircleShape)
                    .semantics { contentDescription = notSynced },
            )
        }
        MoneyText(
            amountMinor, currency, kind,
            modifier = amountModifier.then(
                if (directionLabel != null) Modifier.semantics { contentDescription = "$directionLabel $amountText" } else Modifier,
            ),
        )
    }
}

/** Sticky-capable day header with the day's total. */
@Composable
fun DateHeader(
    label: String,
    dayTotalMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.background)
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.xs)
            .semantics { heading() },
    ) {
        Text(label, style = AppTheme.type.label, color = AppTheme.colors.ink2, modifier = Modifier.weight(1f))
        Text(formatMoney(dayTotalMinor, currency), style = AppTheme.type.label.ltr(), color = AppTheme.colors.ink2)
    }
}

enum class MemberRole { Owner, Editor, Viewer }

@Composable
fun RolePill(role: MemberRole, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (role) {
            MemberRole.Owner -> Res.string.role_owner
            MemberRole.Editor -> Res.string.role_editor
            MemberRole.Viewer -> Res.string.role_viewer
        },
    )
    Text(
        text,
        style = AppTheme.type.caption,
        color = if (role == MemberRole.Owner) AppTheme.colors.onPrimaryContainer else AppTheme.colors.ink2,
        modifier = modifier
            .background(if (role == MemberRole.Owner) AppTheme.colors.primaryContainer else AppTheme.colors.sunken, AppTheme.shapes.pill)
            .padding(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.xxs),
    )
}

@Composable
fun MemberRow(
    name: String,
    initial: String,
    role: MemberRole,
    isYou: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.row)
            .padding(horizontal = AppTheme.spacing.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(AppTheme.sizes.iconTile).background(AppTheme.colors.primaryContainer, CircleShape),
        ) {
            Text(initial.take(1).uppercase(), style = AppTheme.type.label, color = AppTheme.colors.onPrimaryContainer)
        }
        Text(
            if (isYou) stringResource(Res.string.member_you) else name,
            style = AppTheme.type.itemTitle,
            color = AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RolePill(role)
    }
}
