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
import com.fares.saveme.designsystem.component.formatMoney
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.SelectionList
import com.fares.saveme.designsystem.component.AmountDisplay
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppFab
import com.fares.saveme.designsystem.component.AppFilterChip
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppNavigationBar
import com.fares.saveme.designsystem.component.AppSnackbarHost
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CodeField
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
import com.fares.saveme.designsystem.component.PlanCapability
import com.fares.saveme.designsystem.component.MoneyText
import com.fares.saveme.designsystem.component.NavItem
import com.fares.saveme.designsystem.component.PasswordField
import com.fares.saveme.designsystem.component.PlanCard
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.component.SyncBanner
import com.fares.saveme.designsystem.component.SyncBannerState
import com.fares.saveme.designsystem.component.TransactionRow
import com.fares.saveme.designsystem.component.ChoiceGrid
import com.fares.saveme.designsystem.component.ChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceGroup
import com.fares.saveme.designsystem.component.CategoryChoiceItem
import com.fares.saveme.designsystem.component.CategoryChoiceList
import com.fares.saveme.designsystem.component.CategoryRow
import com.fares.saveme.designsystem.component.CategoryRowStatus
import com.fares.saveme.designsystem.component.CategoryRowStatusKind
import com.fares.saveme.designsystem.component.CategoryTile
import com.fares.saveme.designsystem.icon.CategoryIcons
import com.fares.saveme.designsystem.component.WalletCard
import com.fares.saveme.designsystem.component.WalletCardStatus
import com.fares.saveme.designsystem.component.WalletCardStatusKind
import com.fares.saveme.designsystem.icon.WalletIcons
import com.fares.saveme.designsystem.tokens.WalletAccents
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
        PlansSection()
        WalletsSection()
        CategoriesSection()
        ListsSection()
        BudgetsSection()
        MembersSection()
        FeedbackSection()
        SheetSection()
        EmptySection()
        RowsSection()
        DialogsSection()
        TransactionsSection()
        RecurringSection()
        ReportsSection()
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
    var code by remember { mutableStateOf("7KQ2MX") }
    CodeField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(16) }, stringResource(Res.string.redeem_field_label))
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

/** Wallet card options, the icon and colour pickers (spec 006, research R-13). */
@Composable
fun WalletsSection() = CatalogSection(Res.string.catalog_section_wallets) {
    WalletCard(
        stringResource(Res.string.catalog_wallet_cash), WalletType.Cash, 234_000, "EGP",
        iconKey = "savings", accentKey = "#2C7A5B",
    )
    WalletCard(
        stringResource(Res.string.catalog_wallet_cash), WalletType.Cash, 234_000, "EGP",
        accentKey = "#A9503A", status = WalletCardStatus(WalletCardStatusKind.Waiting, stringResource(Res.string.wallet_status_waiting)),
    )
    WalletCard(
        stringResource(Res.string.catalog_wallet_credit_card), WalletType.CreditCard, -312_425, "EGP",
        status = WalletCardStatus(WalletCardStatusKind.Changed, stringResource(Res.string.wallet_status_clash)),
        roleLabel = stringResource(Res.string.role_viewer),
    )
    WalletCard(
        stringResource(Res.string.catalog_wallet_cash), WalletType.Cash, 5_000, "EGP",
        iconKey = "unknown-key", accentKey = "#123456",
        status = WalletCardStatus(WalletCardStatusKind.NotSaved, stringResource(Res.string.wallet_status_not_saved)),
    )
    WalletCard(stringResource(Res.string.catalog_wallet_cash), WalletType.Savings, 90_000, "EGP", archived = true)
    Text(stringResource(Res.string.catalog_choice_grid_icon), style = AppTheme.type.label, color = AppTheme.colors.ink2)
    ChoiceGrid(
        items = WalletIcons.keys.map { key ->
            ChoiceItem(key, key) { Icon(WalletIcons.find(key)!!, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(24.dp)) }
        },
        selectedKey = "savings",
        onSelect = {},
    )
    Text(stringResource(Res.string.catalog_choice_grid_color), style = AppTheme.type.label, color = AppTheme.colors.ink2)
    ChoiceGrid(
        items = WalletAccents.all.map { accent ->
            ChoiceItem(accent.key, accent.key) { Box(Modifier.size(24.dp).background(accent.swatch, CircleShape)) }
        },
        selectedKey = WalletAccents.all[1].key,
        onSelect = {},
    )
}

/** Category tile, rows in each state and the choice list (spec 007, research R-13). */
@Composable
fun CategoriesSection() = CatalogSection(Res.string.catalog_section_categories) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        CategoryTile("cart", "#2E7D32")
        CategoryTile("bolt", "#F9A825")
        CategoryTile("child", "#F06292")
        CategoryTile("unknown-key", "red")
        CategoryTile(null, null)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
        CategoryIcons.keys.take(8).forEach { CategoryTile(it, "#4C6FD6") }
    }
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        CategoryRow(
            stringResource(Res.string.catalog_category_coffee), "restaurant", "#1F7F8C",
            status = CategoryRowStatus(CategoryRowStatusKind.Waiting, stringResource(Res.string.category_status_waiting)),
            onClick = {},
        )
        CategoryRow(
            stringResource(Res.string.catalog_category_groceries), "cart", "#2E7D32",
            caption = stringResource(Res.string.category_default),
        )
        CategoryRow(
            stringResource(Res.string.catalog_category_gym), "health", "#C62828",
            status = CategoryRowStatus(CategoryRowStatusKind.Changed, stringResource(Res.string.category_status_clash)),
            onClick = {},
        )
        CategoryRow(
            stringResource(Res.string.catalog_category_coffee), "unknown-key", "#123456",
            status = CategoryRowStatus(CategoryRowStatusKind.NotSaved, stringResource(Res.string.category_status_in_use)),
            onClick = {},
        )
        CategoryRow(
            stringResource(Res.string.catalog_category_gym), "laptop", "#4527A0",
            caption = stringResource(Res.string.category_archived), onClick = {},
        )
        CategoryRow(stringResource(Res.string.category_unavailable), null, null)
    }
    Text(stringResource(Res.string.category_choice_title), style = AppTheme.type.label, color = AppTheme.colors.ink2)
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        CategoryChoiceList(
            groups = listOf(
                CategoryChoiceGroup(
                    stringResource(Res.string.category_choice_current),
                    listOf(
                        CategoryChoiceItem(
                            "gym", stringResource(Res.string.catalog_category_gym), "health", "#C62828",
                            caption = stringResource(Res.string.category_archived),
                        ),
                    ),
                    "categoryChoice.current", indexed = false,
                ),
                CategoryChoiceGroup(
                    stringResource(Res.string.category_choice_own),
                    listOf(CategoryChoiceItem("coffee", stringResource(Res.string.catalog_category_coffee), "restaurant", "#1F7F8C")),
                    "categoryChoice.own",
                ),
                CategoryChoiceGroup(
                    stringResource(Res.string.category_choice_defaults),
                    listOf(
                        CategoryChoiceItem("groceries", stringResource(Res.string.catalog_category_groceries), "cart", "#2E7D32"),
                        // A choice that can't be taken: dimmed, announced as disabled, its caption says why (spec 012 R-13).
                        CategoryChoiceItem(
                            "dining", stringResource(Res.string.catalog_budget_dining), "restaurant", "#EF6C00",
                            caption = stringResource(Res.string.budgets_already_budgeted), enabled = false,
                        ),
                    ),
                    "categoryChoice.default",
                ),
            ),
            selectedId = "gym",
            onSelect = {},
            emptyMessage = stringResource(Res.string.category_choice_empty),
        )
    }
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card).padding(AppTheme.spacing.m)) {
        CategoryChoiceList(emptyList(), null, {}, stringResource(Res.string.category_choice_empty))
    }
}

@Composable
fun PlansSection() = CatalogSection(Res.string.catalog_section_plans) {
    PlanCard(
        name = stringResource(Res.string.catalog_plan_free),
        capabilities = listOf(
            PlanCapability(stringResource(Res.string.plan_cap_wallets_unlimited)),
            PlanCapability(stringResource(Res.string.plan_cap_sharing_off), included = false),
        ),
        currentLabel = stringResource(Res.string.plan_current_label),
    )
    PlanCard(
        name = stringResource(Res.string.catalog_plan_plus),
        periodLine = stringResource(Res.string.plan_period_monthly),
        priceMinor = 9_900,
        priceCurrency = "EGP",
        capabilities = listOf(
            PlanCapability(stringResource(Res.string.plan_cap_wallets_unlimited)),
            PlanCapability(stringResource(Res.string.plan_cap_sharing_members, 5)),
        ),
    )
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
    // The caption lines a budget row carries under the bar, as the budgets screen writes them (spec 012 R-13).
    BudgetProgress(stringResource(Res.string.budgets_catalog_all), 350_000, 600_000, "EGP")
    Text(stringResource(Res.string.budgets_on_pace, formatMoney(1_312_500, "EGP")), style = AppTheme.type.caption, color = AppTheme.colors.caption)
    Text(stringResource(Res.string.budgets_other_currencies, "USD"), style = AppTheme.type.caption, color = AppTheme.colors.caption)
    Text(stringResource(Res.string.budgets_includes_waiting), style = AppTheme.type.caption, color = AppTheme.colors.caption)
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

@Composable
fun RowsSection() = CatalogSection(Res.string.catalog_section_rows) {
    AppListItem(stringResource(Res.string.account_profile), leadingIcon = AppIcons.Person, trailing = ListTrailing.Chevron, onClick = {})
    AppListItem(
        "Pixel 8",
        supporting = stringResource(Res.string.sessions_not_used_yet),
        leadingIcon = AppIcons.Devices,
        trailing = ListTrailing.Text(stringResource(Res.string.sessions_this_device)),
    )
    AppListItem(
        stringResource(Res.string.account_delete),
        leadingIcon = AppIcons.Delete,
        headlineColor = AppTheme.colors.error,
        trailing = ListTrailing.Chevron,
        onClick = {},
    )
    AppSelectField(stringResource(Res.string.profile_timezone), "Africa/Cairo", onClick = {})
    AppSelectField(
        stringResource(Res.string.profile_currency),
        "EGP",
        onClick = {},
        enabled = false,
        supportingText = stringResource(Res.string.profile_currency_locked),
    )
}

@Composable
fun DialogsSection() = CatalogSection(Res.string.catalog_section_dialogs) {
    AppAlertDialogCard(
        title = stringResource(Res.string.sessions_confirm_end_others_title),
        text = stringResource(Res.string.sessions_confirm_end_others_body),
        confirmLabel = stringResource(Res.string.sessions_end_others),
        onConfirm = {},
        dismissLabel = stringResource(Res.string.action_cancel),
        onDismiss = {},
        destructive = true,
    )
    SelectionList(
        options = listOf("EGP", "SAR", "USD"),
        label = { it },
        selected = "EGP",
        onSelect = {},
        searchable = false,
    )
}
