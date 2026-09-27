package com.fares.saveme.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.fares.saveme.designsystem.icon.AppIcons
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

@Composable
fun WalletCard(
    name: String,
    type: WalletType,
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .border(androidx.compose.ui.unit.Dp.Hairline, AppTheme.colors.outlineVariant, AppTheme.shapes.card)
            .padding(AppTheme.spacing.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AppTheme.sizes.iconTile)
                .background(AppTheme.colors.primaryContainer, AppTheme.shapes.tile),
        ) {
            Icon(type.icon, contentDescription = null, tint = AppTheme.colors.onPrimaryContainer)
        }
        Text(
            name,
            style = AppTheme.type.itemTitle,
            color = AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MoneyText(amountMinor, currency, if (type.isLiability) MoneyKind.Liability else MoneyKind.Balance)
    }
}
