package com.fares.saveme.feature.wallets.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.WalletType as CardType
import com.fares.saveme.feature.wallets.domain.model.WalletType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.wallet_type_cash
import saveme.shared.generated.resources.wallet_type_checking
import saveme.shared.generated.resources.wallet_type_credit_card
import saveme.shared.generated.resources.wallet_type_investment
import saveme.shared.generated.resources.wallet_type_loan
import saveme.shared.generated.resources.wallet_type_savings
import saveme.shared.generated.resources.wallet_type_unknown

/** Every type, including one this app doesn't know, has its own label (P-06.5). */
fun WalletType.labelRes(): StringResource = when (this) {
    WalletType.Cash -> Res.string.wallet_type_cash
    WalletType.Checking -> Res.string.wallet_type_checking
    WalletType.Savings -> Res.string.wallet_type_savings
    WalletType.CreditCard -> Res.string.wallet_type_credit_card
    WalletType.Loan -> Res.string.wallet_type_loan
    WalletType.Investment -> Res.string.wallet_type_investment
    WalletType.Unknown -> Res.string.wallet_type_unknown
}

@Composable
fun WalletType.label(): String = stringResource(labelRes())

/** The design system's card type; a type this app doesn't know shows the cash icon. */
fun WalletType.cardType(): CardType = when (this) {
    WalletType.Cash, WalletType.Unknown -> CardType.Cash
    WalletType.Checking -> CardType.Checking
    WalletType.Savings -> CardType.Savings
    WalletType.CreditCard -> CardType.CreditCard
    WalletType.Loan -> CardType.Loan
    WalletType.Investment -> CardType.Investment
}
