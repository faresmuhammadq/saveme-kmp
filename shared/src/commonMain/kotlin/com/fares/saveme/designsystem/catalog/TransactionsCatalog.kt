package com.fares.saveme.designsystem.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fares.saveme.designsystem.component.AmountField
import com.fares.saveme.designsystem.component.AppDatePickerContent
import com.fares.saveme.designsystem.component.CategoryTile
import com.fares.saveme.designsystem.component.DateField
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.TransactionRow
import com.fares.saveme.designsystem.format.formatDate
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.catalog_section_transactions
import saveme.shared.generated.resources.catalog_tx_groceries
import saveme.shared.generated.resources.catalog_tx_groceries_sub
import saveme.shared.generated.resources.catalog_tx_salary
import saveme.shared.generated.resources.catalog_tx_salary_sub
import saveme.shared.generated.resources.tx_cd_money_in
import saveme.shared.generated.resources.tx_cd_money_out
import saveme.shared.generated.resources.tx_field_amount
import saveme.shared.generated.resources.tx_field_date
import saveme.shared.generated.resources.wallet_status_waiting

/** The transaction editor's and list's building blocks (spec 008): date field and picker, amount field, richer rows. */
@Composable
fun TransactionsSection() = CatalogSection(Res.string.catalog_section_transactions) {
    var amount by remember { mutableStateOf("45.50") }
    AmountField(amount, { amount = it }, "EGP", stringResource(Res.string.tx_field_amount))
    DateField(stringResource(Res.string.tx_field_date), formatDate(LocalDate(2026, 10, 2)), onClick = {})
    AppDatePickerContent(LocalDate(2026, 10, 2))
    Column {
        TransactionRow(
            stringResource(Res.string.catalog_tx_groceries), stringResource(Res.string.catalog_tx_groceries_sub), 45_000, "EGP",
            MoneyKind.Expense, pendingSync = true,
            leading = { CategoryTile("cart", "#2E7D32") },
            status = stringResource(Res.string.wallet_status_waiting),
            directionLabel = stringResource(Res.string.tx_cd_money_out),
        )
        TransactionRow(
            stringResource(Res.string.catalog_tx_salary), stringResource(Res.string.catalog_tx_salary_sub), 3_200_000, "EGP",
            MoneyKind.Income, pendingSync = false,
            leading = { CategoryTile("laptop", "#1F7F8C") },
            directionLabel = stringResource(Res.string.tx_cd_money_in),
        )
    }
}
