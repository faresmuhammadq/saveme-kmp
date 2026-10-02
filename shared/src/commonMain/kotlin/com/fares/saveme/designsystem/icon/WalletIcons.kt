package com.fares.saveme.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector

/** The icon keys a wallet can carry (spec 006, research R-13). An unknown key falls back to the type's icon. */
object WalletIcons {
    val keys: List<String> = listOf("cash", "bank", "savings", "card", "loan", "investment")

    fun find(key: String?): ImageVector? = when (key) {
        "cash" -> AppIcons.Cash
        "bank" -> AppIcons.Bank
        "savings" -> AppIcons.Savings
        "card" -> AppIcons.Card
        "loan" -> AppIcons.Loan
        "investment" -> AppIcons.Investment
        else -> null
    }
}
