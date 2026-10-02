package com.fares.saveme.feature.wallets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.ChoiceGrid
import com.fares.saveme.designsystem.component.ChoiceItem
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SelectionSheet
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.WalletIcons
import com.fares.saveme.designsystem.money.knownCurrencies
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.designsystem.tokens.WalletAccents
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletType
import com.fares.saveme.feature.wallets.ui.WalletsBackBar
import com.fares.saveme.feature.wallets.ui.cardType
import com.fares.saveme.feature.wallets.ui.label
import com.fares.saveme.feature.wallets.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_wallet_color_0
import saveme.shared.generated.resources.cd_wallet_color_1
import saveme.shared.generated.resources.cd_wallet_color_2
import saveme.shared.generated.resources.cd_wallet_color_3
import saveme.shared.generated.resources.cd_wallet_color_4
import saveme.shared.generated.resources.cd_wallet_color_5
import saveme.shared.generated.resources.cd_wallet_color_6
import saveme.shared.generated.resources.cd_wallet_color_7
import saveme.shared.generated.resources.cd_wallet_icon_bank
import saveme.shared.generated.resources.cd_wallet_icon_card
import saveme.shared.generated.resources.cd_wallet_icon_cash
import saveme.shared.generated.resources.cd_wallet_icon_investment
import saveme.shared.generated.resources.cd_wallet_icon_loan
import saveme.shared.generated.resources.cd_wallet_icon_savings
import saveme.shared.generated.resources.wallet_currency_locked_note
import saveme.shared.generated.resources.wallet_field_amount_owed
import saveme.shared.generated.resources.wallet_field_color
import saveme.shared.generated.resources.wallet_field_currency
import saveme.shared.generated.resources.wallet_field_icon
import saveme.shared.generated.resources.wallet_field_name
import saveme.shared.generated.resources.wallet_field_opening_balance
import saveme.shared.generated.resources.wallet_field_type
import saveme.shared.generated.resources.wallet_none
import saveme.shared.generated.resources.wallet_save
import saveme.shared.generated.resources.wallet_title_edit
import saveme.shared.generated.resources.wallet_title_new

private const val NONE_KEY = "none"

@Composable
fun WalletEditorScreen(state: WalletEditorState, onIntent: (WalletEditorIntent) -> Unit) {
    AppScreen(
        topBar = {
            WalletsBackBar(
                stringResource(if (state.isCreate) Res.string.wallet_title_new else Res.string.wallet_title_edit),
            ) { onIntent(WalletEditorIntent.Back) }
        },
    ) {
        if (!state.loaded) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
            return@AppScreen
        }
        TypeSection(state, onIntent)
        AppTextField(
            value = state.name,
            onValueChange = { onIntent(WalletEditorIntent.NameChanged(it)) },
            label = stringResource(Res.string.wallet_field_name),
            modifier = Modifier.testTag("walletEditor.name"),
            errorText = state.errors[WalletField.Name]?.text(WalletField.Name),
        )
        AppSelectField(
            label = stringResource(Res.string.wallet_field_currency),
            value = state.currency,
            onClick = { onIntent(WalletEditorIntent.CurrencyClicked) },
            modifier = Modifier.testTag("walletEditor.currency"),
            enabled = state.currencyEditable,
            supportingText = if (state.currencyLocked) stringResource(Res.string.wallet_currency_locked_note) else null,
            errorText = state.errors[WalletField.Currency]?.text(WalletField.Currency),
        )
        AppTextField(
            value = state.amountText,
            onValueChange = { onIntent(WalletEditorIntent.AmountChanged(it)) },
            label = stringResource(if (state.isLiability) Res.string.wallet_field_amount_owed else Res.string.wallet_field_opening_balance),
            modifier = Modifier.testTag("walletEditor.amount"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            errorText = (state.errors[WalletField.OpeningBalance])?.text(WalletField.OpeningBalance),
        )
        IconSection(state, onIntent)
        ColorSection(state, onIntent)
        PrimaryButton(
            stringResource(Res.string.wallet_save),
            { onIntent(WalletEditorIntent.SaveClicked) },
            modifier = Modifier.testTag("walletEditor.save"),
            enabled = state.canSave,
            loading = state.saving,
        )
    }
    if (state.currencyPickerVisible) {
        SelectionSheet(
            title = stringResource(Res.string.wallet_field_currency),
            options = knownCurrencies,
            label = { it },
            selected = state.currency,
            onSelect = { onIntent(WalletEditorIntent.CurrencyChosen(it)) },
            onDismiss = { onIntent(WalletEditorIntent.DismissCurrencyPicker) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.ink2)
}

@Composable
private fun TypeSection(state: WalletEditorState, onIntent: (WalletEditorIntent) -> Unit) {
    if (!state.typeEditable) {
        AppTextField(
            value = state.type.label(),
            onValueChange = {},
            label = stringResource(Res.string.wallet_field_type),
            modifier = Modifier.testTag("walletEditor.type"),
            enabled = false,
            errorText = state.errors[WalletField.Type]?.text(WalletField.Type),
        )
        return
    }
    SectionLabel(stringResource(Res.string.wallet_field_type))
    val items = WalletType.selectable.map { type ->
        val label = type.label()
        ChoiceItem(type.wire, label, "walletEditor.type.${type.wire}") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                Icon(type.cardType().icon, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(24.dp))
                Text(label, style = AppTheme.type.label, color = AppTheme.colors.ink)
            }
        }
    }
    ChoiceGrid(items, state.type.wire, { key -> WalletType.selectable.firstOrNull { it.wire == key }?.let { onIntent(WalletEditorIntent.TypeChosen(it)) } })
}

@Composable
private fun iconLabel(key: String): String = stringResource(
    when (key) {
        "cash" -> Res.string.cd_wallet_icon_cash
        "bank" -> Res.string.cd_wallet_icon_bank
        "savings" -> Res.string.cd_wallet_icon_savings
        "card" -> Res.string.cd_wallet_icon_card
        "loan" -> Res.string.cd_wallet_icon_loan
        else -> Res.string.cd_wallet_icon_investment
    },
)

@Composable
private fun IconSection(state: WalletEditorState, onIntent: (WalletEditorIntent) -> Unit) {
    SectionLabel(stringResource(Res.string.wallet_field_icon))
    val none = stringResource(Res.string.wallet_none)
    val items = listOf(ChoiceItem(NONE_KEY, none, "walletEditor.icon.none") { Text(none, style = AppTheme.type.label, color = AppTheme.colors.ink2) }) +
        WalletIcons.keys.map { key ->
            ChoiceItem(key, iconLabel(key), "walletEditor.icon.$key") {
                Icon(WalletIcons.find(key)!!, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(24.dp))
            }
        }
    ChoiceGrid(items, state.iconKey ?: NONE_KEY, { key -> onIntent(WalletEditorIntent.IconChosen(key.takeUnless { it == NONE_KEY })) })
}

@Composable
private fun colorLabel(index: Int): String = stringResource(
    when (index) {
        0 -> Res.string.cd_wallet_color_0
        1 -> Res.string.cd_wallet_color_1
        2 -> Res.string.cd_wallet_color_2
        3 -> Res.string.cd_wallet_color_3
        4 -> Res.string.cd_wallet_color_4
        5 -> Res.string.cd_wallet_color_5
        6 -> Res.string.cd_wallet_color_6
        else -> Res.string.cd_wallet_color_7
    },
)

@Composable
private fun ColorSection(state: WalletEditorState, onIntent: (WalletEditorIntent) -> Unit) {
    SectionLabel(stringResource(Res.string.wallet_field_color))
    val none = stringResource(Res.string.wallet_none)
    val items = listOf(ChoiceItem(NONE_KEY, none, "walletEditor.color.none") { Text(none, style = AppTheme.type.label, color = AppTheme.colors.ink2) }) +
        WalletAccents.all.mapIndexed { index, accent ->
            ChoiceItem(accent.key, colorLabel(index), "walletEditor.color.$index") {
                Box(Modifier.size(24.dp).background(accent.swatch, CircleShape))
            }
        }
    ChoiceGrid(items, state.accentKey?.let { WalletAccents.find(it)?.key } ?: if (state.accentKey == null) NONE_KEY else null, { key -> onIntent(WalletEditorIntent.AccentChosen(key.takeUnless { it == NONE_KEY })) })
}

@Preview
@Composable
private fun WalletEditorScreenPreview() {
    AppTheme {
        WalletEditorScreen(
            WalletEditorState(loaded = true, name = "Pocket", currency = "EGP", amountText = "250.00", iconKey = "cash", accentKey = "#4C6FD6"),
            {},
        )
    }
}
