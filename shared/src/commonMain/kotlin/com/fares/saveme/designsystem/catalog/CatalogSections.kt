package com.fares.saveme.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AmountDisplay
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppFab
import com.fares.saveme.designsystem.component.AppFilterChip
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppNavigationBar
import com.fares.saveme.designsystem.component.AppSnackbarHost
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.BalanceHero
import com.fares.saveme.designsystem.component.BudgetProgress
import com.fares.saveme.designsystem.component.DateHeader
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.EmptyState
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.MemberRole
import com.fares.saveme.designsystem.component.MemberRow
import com.fares.saveme.designsystem.component.MoneyKind
import com.fares.saveme.designsystem.component.MoneyText
import com.fares.saveme.designsystem.component.NavItem
import com.fares.saveme.designsystem.component.PasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.component.TransactionRow
import com.fares.saveme.designsystem.component.WalletCard
import com.fares.saveme.designsystem.component.WalletType
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

/** All catalogue sections, in board order. Also rendered by the screenshot tests (P-14.4). */
@Composable
fun CatalogSections() {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sectionGap)) {
        ButtonsSection()
        BarsSection()
        SelectionSection()
        FieldsSection()
        MoneySection()
        CardsSection()
        ListsSection()
        BudgetsSection()
        MembersSection()
        FeedbackSection()
        SheetSection()
        EmptySection()
    }
}

@Composable
internal fun CatalogSection(title: StringResource, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(AppTheme.spacing.m),
    ) {
        val text = stringResource(title)
        Text(
            if (AppTheme.type.overlineUppercase) text.uppercase() else text,
            style = AppTheme.type.overline,
            color = AppTheme.colors.ink2,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
fun ButtonsSection() = CatalogSection(Res.string.catalog_section_buttons) {
    PrimaryButton(stringResource(Res.string.catalog_save_expense), {})
    PrimaryButton(stringResource(Res.string.catalog_loading), {}, loading = true)
    PrimaryButton(stringResource(Res.string.catalog_disabled), {}, enabled = false)
    SecondaryButton(stringResource(Res.string.catalog_restore_purchases), {})
    AppTextButton(stringResource(Res.string.catalog_enter_code), {})
    DestructiveButton(stringResource(Res.string.catalog_delete_wallet), {})
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
        AppFab(AppIcons.Plus, stringResource(Res.string.catalog_add), {}, text = stringResource(Res.string.catalog_add))
        AppFab(AppIcons.Plus, stringResource(Res.string.catalog_add), {})
        AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), {})
    }
}

@Composable
fun BarsSection() = CatalogSection(Res.string.catalog_section_bars) {
    AppTopAppBar(
        title = stringResource(Res.string.catalog_wallet_cash),
        navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), {}) },
        actions = { AppIconButton(AppIcons.More, stringResource(Res.string.catalog_nav_more), {}) },
    )
    var selected by remember { mutableIntStateOf(0) }
    AppNavigationBar(
        items = listOf(
            NavItem(stringResource(Res.string.catalog_nav_home), AppIcons.Home),
            NavItem(stringResource(Res.string.catalog_nav_activity), AppIcons.Activity),
            NavItem(stringResource(Res.string.catalog_nav_budgets), AppIcons.Budgets),
            NavItem(stringResource(Res.string.catalog_nav_more), AppIcons.More),
        ),
        selectedIndex = selected,
        onSelect = { selected = it },
    )
}

@Composable
fun SelectionSection() = CatalogSection(Res.string.catalog_section_selection) {
    val types = listOf(Res.string.catalog_type_expense, Res.string.catalog_type_income, Res.string.catalog_type_transfer)
    var type by remember { mutableStateOf(types.first()) }
    SegmentedControl(types, { stringResource(it) }, type, { type = it })
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        var chip by remember { mutableIntStateOf(1) }
        listOf(Res.string.catalog_chip_all_wallets, Res.string.catalog_chip_groceries, Res.string.catalog_chip_this_month)
            .forEachIndexed { i, label -> AppFilterChip(stringResource(label), chip == i, { chip = i }) }
    }
    var alerts by remember { mutableStateOf(true) }
    SettingsSwitchRow(stringResource(Res.string.catalog_budget_alerts), alerts, { alerts = it }, supportingText = stringResource(Res.string.catalog_budget_alerts_hint))
    var hide by remember { mutableStateOf(false) }
    SettingsSwitchRow(stringResource(Res.string.catalog_hide_amounts), hide, { hide = it }, supportingText = stringResource(Res.string.catalog_hide_amounts_hint))
}

@Composable
fun FieldsSection() = CatalogSection(Res.string.catalog_section_fields) {
    AmountDisplay(45_000, "EGP")
    var note by remember { mutableStateOf("") }
    AppTextField(note, { note = it }, stringResource(Res.string.catalog_field_note))
    AppTextField("0", {}, stringResource(Res.string.catalog_field_limit), errorText = stringResource(Res.string.catalog_field_limit_error))
    var password by remember { mutableStateOf("secret-pass") }
    var visible by remember { mutableStateOf(false) }
    PasswordField(password, { password = it }, stringResource(Res.string.catalog_field_password), visible, { visible = !visible })
    AppTextField("EGP 450.00", {}, stringResource(Res.string.catalog_field_amount), enabled = false)
}

@Composable
fun MoneySection() = CatalogSection(Res.string.catalog_section_money) {
    MoneyText(3_200_000, "EGP", MoneyKind.Income)
    MoneyText(45_000, "EGP", MoneyKind.Expense)
    MoneyText(-312_425, "EGP", MoneyKind.Liability)
    MoneyText(-32_000, "EGP", MoneyKind.Balance)
    MoneyText(1_234_567, "KWD", MoneyKind.Balance)
}

@Composable
fun CardsSection() = CatalogSection(Res.string.catalog_section_cards) {
    BalanceHero(stringResource(Res.string.catalog_total_balance), 4_821_575, "EGP", caption = stringResource(Res.string.catalog_liabilities_owed))
    WalletCard(stringResource(Res.string.catalog_wallet_cash), WalletType.Cash, 234_000, "EGP")
    WalletCard(stringResource(Res.string.catalog_wallet_credit_card), WalletType.CreditCard, -312_425, "EGP")
}

@Composable
fun ListsSection() = CatalogSection(Res.string.catalog_section_lists) {
    DateHeader(stringResource(Res.string.catalog_today), -53_500, "EGP")
    TransactionRow(stringResource(Res.string.catalog_tx_groceries), stringResource(Res.string.catalog_tx_groceries_sub), 45_000, "EGP", MoneyKind.Expense, pendingSync = true)
    TransactionRow(stringResource(Res.string.catalog_tx_transport), stringResource(Res.string.catalog_tx_transport_sub), 8_500, "EGP", MoneyKind.Expense, pendingSync = false)
    TransactionRow(stringResource(Res.string.catalog_tx_salary), stringResource(Res.string.catalog_tx_salary_sub), 3_200_000, "EGP", MoneyKind.Income, pendingSync = false)
}

@Composable
fun BudgetsSection() = CatalogSection(Res.string.catalog_section_budgets) {
    BudgetProgress(stringResource(Res.string.catalog_budget_transport), 61_000, 120_000, "EGP")
    BudgetProgress(stringResource(Res.string.catalog_budget_groceries), 342_000, 400_000, "EGP")
    BudgetProgress(stringResource(Res.string.catalog_budget_dining), 154_000, 120_000, "EGP")
}

@Composable
fun MembersSection() = CatalogSection(Res.string.catalog_section_members) {
    MemberRow("", "Y", MemberRole.Owner, isYou = true)
    MemberRow(stringResource(Res.string.catalog_member_partner), "P", MemberRole.Editor, isYou = false)
    MemberRow(stringResource(Res.string.catalog_member_partner), "P", MemberRole.Viewer, isYou = false)
}

@Composable
fun FeedbackSection() = CatalogSection(Res.string.catalog_section_feedback) {
    SyncBanner(SyncBannerState.Offline(3))
    SyncBanner(SyncBannerState.Synced)
    InlineAlert(AlertTone.Warning, stringResource(Res.string.catalog_verify_alert), actionLabel = stringResource(Res.string.catalog_resend_email), onAction = {})
    InlineAlert(AlertTone.Error, stringResource(Res.string.catalog_error_alert))
    InlineAlert(AlertTone.Info, stringResource(Res.string.catalog_liabilities_owed))
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message = stringResource(Res.string.catalog_tx_deleted)
    val undo = stringResource(Res.string.catalog_undo)
    SecondaryButton(stringResource(Res.string.catalog_show_snackbar), { scope.launch { host.showSnackbar(message, undo) } })
    AppSnackbarHost(host)
}

@Composable
fun SheetSection() = CatalogSection(Res.string.catalog_section_sheet) {
    var open by remember { mutableStateOf(false) }
    SecondaryButton(stringResource(Res.string.catalog_open_sheet), { open = true })
    if (open) {
        AppBottomSheet(onDismiss = { open = false }, title = stringResource(Res.string.catalog_sheet_title)) {
            listOf(Res.string.catalog_tx_groceries, Res.string.catalog_tx_transport, Res.string.catalog_cat_rent, Res.string.catalog_cat_dining)
                .forEach { AppTextButton(stringResource(it), { open = false }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun EmptySection() = CatalogSection(Res.string.catalog_section_empty) {
    EmptyState(stringResource(Res.string.catalog_empty_title), stringResource(Res.string.catalog_empty_body), actionLabel = stringResource(Res.string.catalog_add), onAction = {})
    Skeleton(Modifier.fillMaxWidth().height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
        Skeleton(Modifier.size(AppTheme.sizes.iconTile), AppTheme.shapes.tile)
        Skeleton(Modifier.width(160.dp).height(AppTheme.sizes.iconTile))
    }
}
