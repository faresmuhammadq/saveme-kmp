package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.WalletIcons
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.designsystem.theme.AppTheme

/** Radius 22, solid primary, no gradient (board: BalanceHero). */
@Composable
fun BalanceHero(
    label: String,
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = AppTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primary, AppTheme.shapes.hero)
            .padding(AppTheme.spacing.l),
    ) {
        Text(label, style = AppTheme.type.label, color = colors.onPrimary.copy(alpha = 0.85f))
        AmountDisplay(amountMinor, currency, color = colors.onPrimary)
        if (caption != null) Text(caption, style = AppTheme.type.caption, color = colors.onPrimary.copy(alpha = 0.85f))
    }
}

/** The fixed wallet type enum; liability types hold negative balances (P-01 rule 2 on the backend). */
enum class WalletType(val isLiability: Boolean) {
    Cash(false), Checking(false), Savings(false), CreditCard(true), Loan(true), Investment(false);

    val icon: ImageVector
        get() = when (this) {
            Cash -> AppIcons.Cash
            Checking -> AppIcons.Bank
            Savings -> AppIcons.Savings
            CreditCard -> AppIcons.Card
            Loan -> AppIcons.Loan
            Investment -> AppIcons.Investment
        }
}

/** What a wallet's change is doing, shown as an icon and text, never colour alone (P-08.3). */
enum class WalletCardStatusKind { Waiting, Changed, NotSaved }

data class WalletCardStatus(val kind: WalletCardStatusKind, val label: String)

/**
 * [iconKey] and [accentKey] are the wire keys a wallet carries; an unknown or missing one falls
 * back to the type's icon and the default tile colour. With no options the card looks as before.
 */
@Composable
fun WalletCard(
    name: String,
    type: WalletType,
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
    accentKey: String? = null,
    status: WalletCardStatus? = null,
    archived: Boolean = false,
    roleLabel: String? = null,
    onClick: (() -> Unit)? = null,
    statusModifier: Modifier = Modifier,
) {
    val accent = WalletAccents.find(accentKey)
    val tileColor = when {
        archived -> AppTheme.colors.sunken
        accent != null -> accent.container
        else -> AppTheme.colors.primaryContainer
    }
    val iconColor = when {
        archived -> AppTheme.colors.ink2
        accent != null -> accent.tint
        else -> AppTheme.colors.onPrimaryContainer
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .border(androidx.compose.ui.unit.Dp.Hairline, AppTheme.colors.outlineVariant, AppTheme.shapes.card)
            .then(if (onClick != null) Modifier.clip(AppTheme.shapes.card).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(AppTheme.spacing.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AppTheme.sizes.iconTile)
                .background(tileColor, AppTheme.shapes.tile),
        ) {
            Icon(WalletIcons.find(iconKey) ?: type.icon, contentDescription = null, tint = iconColor)
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = AppTheme.type.itemTitle,
                color = if (archived) AppTheme.colors.ink2 else AppTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (roleLabel != null) {
                Text(roleLabel, style = AppTheme.type.caption, color = AppTheme.colors.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (status != null) WalletStatusLine(status, statusModifier)
        }
        MoneyText(amountMinor, currency, if (type.isLiability) MoneyKind.Liability else MoneyKind.Balance)
    }
}

/** The small "waiting / changed / not saved" line under a wallet's name. */
@Composable
fun WalletStatusLine(status: WalletCardStatus, modifier: Modifier = Modifier) {
    val (icon, color) = when (status.kind) {
        WalletCardStatusKind.Waiting -> AppIcons.Sync to AppTheme.money.pending
        WalletCardStatusKind.Changed -> AppIcons.Warning to AppTheme.money.pending
        WalletCardStatusKind.NotSaved -> AppIcons.Error to AppTheme.colors.error
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(status.label, style = AppTheme.type.caption, color = color)
    }
}
