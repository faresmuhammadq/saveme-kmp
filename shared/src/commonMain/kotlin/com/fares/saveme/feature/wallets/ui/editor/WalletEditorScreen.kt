package com.fares.saveme.feature.wallets.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmVRule
import com.fares.saveme.designsystem.icon.WlcExpandMore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.ltr
import com.fares.saveme.designsystem.icon.AppIcons
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
import com.fares.saveme.feature.wallets.ui.cardType
import com.fares.saveme.feature.wallets.ui.label
import com.fares.saveme.feature.wallets.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.adj_opening_hint
import saveme.shared.generated.resources.cd_back
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
import saveme.shared.generated.resources.wlc_create_wallet
import saveme.shared.generated.resources.wlc_preview

private const val NONE_KEY = "none"

@Composable
fun WalletEditorScreen(state: WalletEditorState, onIntent: (WalletEditorIntent) -> Unit) {
    val title = stringResource(if (state.isCreate) Res.string.wallet_title_new else Res.string.wallet_title_edit)
    CalmScreen(
        title = null,
        titleSlot = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CalmCircleButton(AppIcons.Close, stringResource(Res.string.cd_back), { onIntent(WalletEditorIntent.Back) })
                Text(
                    title,
                    style = AppTheme.type.headline.copy(fontSize = 22.sp),
                    color = AppTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        bottomBar = {
            if (state.loaded) {
                CalmActionBar {
                    PrimaryButton(
                        stringResource(if (state.isCreate) Res.string.wlc_create_wallet else Res.string.wallet_save),
                        { onIntent(WalletEditorIntent.SaveClicked) },
                        modifier = Modifier.testTag("walletEditor.save"),
                        enabled = state.canSave,
                        loading = state.saving,
                    )
                }
            }
        },
    ) {
        if (!state.loaded) {
            repeat(4) { Skeleton(Modifier.fillMaxWidth().height(52.dp)) }
        } else {
            WalletPreviewCard(state)
            TypeSection(state, onIntent)
            FieldsCard(state, onIntent)
            ColorSection(state, onIntent)
            IconSection(state, onIntent)
        }
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

/** The wallet as it will look on the list: its colour, icon, name and the opening balance as typed. */
@Composable
private fun WalletPreviewCard(state: WalletEditorState) {
    val on = AppTheme.colors.onPrimary
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .background(WalletAccents.find(state.accentKey)?.swatch ?: AppTheme.colors.primary, shape)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("walletEditor.preview"),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(WalletIcons.find(state.iconKey) ?: state.type.cardType().icon, contentDescription = null, tint = on, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.wlc_preview),
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = on.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
        Text(
            state.name.ifBlank { stringResource(Res.string.wallet_title_new) },
            style = AppTheme.type.label.copy(fontSize = 14.sp),
            color = on.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                state.amountText.ifBlank { "0" },
                style = AppTheme.type.numeralHero.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold).ltr(),
                color = on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(state.currency, style = AppTheme.type.label.copy(fontSize = 15.sp), color = on.copy(alpha = 0.85f), maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.ink2, modifier = Modifier.semantics { heading() })
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        WalletType.selectable.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { type ->
                    TypeTile(type, type == state.type, Modifier.weight(1f)) { onIntent(WalletEditorIntent.TypeChosen(type)) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TypeTile(type: WalletType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val label = type.label()
    val shape = RoundedCornerShape(16.dp)
    val tint = if (selected) AppTheme.money.positive else colors.ink2
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .testTag("walletEditor.type.${type.wire}")
            .clip(shape)
            .background(if (selected) AppTheme.money.positiveContainer else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .heightIn(min = 48.dp),
    ) {
        Icon(type.cardType().icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = AppTheme.type.caption.copy(fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) AppTheme.money.positive else colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FieldsCard(state: WalletEditorState, onIntent: (WalletEditorIntent) -> Unit) {
    val colors = AppTheme.colors
    val nameError = state.errors[WalletField.Name]?.text(WalletField.Name)
    val currencyError = state.errors[WalletField.Currency]?.text(WalletField.Currency)
    val amountError = state.errors[WalletField.OpeningBalance]?.text(WalletField.OpeningBalance)
    val hasError = nameError != null || currencyError != null || amountError != null
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface, shape)
                .border(1.dp, if (hasError) colors.error else colors.outlineVariant, shape),
        ) {
            LabeledInput(
                label = stringResource(Res.string.wallet_field_name),
                value = state.name,
                onValueChange = { onIntent(WalletEditorIntent.NameChanged(it)) },
                tag = "walletEditor.name",
            )
            CalmDivider()
            Row(Modifier.height(IntrinsicSize.Min)) {
                val editable = state.currencyEditable
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .fillMaxHeight()
                        .testTag("walletEditor.currency")
                        .clickable(enabled = editable, role = Role.Button) { onIntent(WalletEditorIntent.CurrencyClicked) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(Res.string.wallet_field_currency), style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.ink2)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            state.currency,
                            style = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            color = if (editable) colors.ink else colors.ink2,
                            maxLines = 1,
                        )
                        Icon(AppIcons.WlcExpandMore, contentDescription = null, tint = colors.caption, modifier = Modifier.size(18.dp))
                    }
                }
                CalmVRule(Modifier.fillMaxHeight())
                LabeledInput(
                    label = stringResource(if (state.isLiability) Res.string.wallet_field_amount_owed else Res.string.wallet_field_opening_balance),
                    value = state.amountText,
                    onValueChange = { onIntent(WalletEditorIntent.AmountChanged(it)) },
                    tag = "walletEditor.amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        listOfNotNull(
            nameError,
            currencyError,
            amountError,
            stringResource(Res.string.wallet_currency_locked_note).takeIf { state.currencyLocked },
            stringResource(Res.string.adj_opening_hint).takeIf { state.walletId != null },
        ).forEachIndexed { index, text ->
            Text(
                text,
                style = AppTheme.type.caption,
                color = if (index < listOfNotNull(nameError, currencyError, amountError).size) colors.error else colors.ink2,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** A caption over a borderless 15sp input; the whole cell focuses the input. */
@Composable
private fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val focus = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focus.requestFocus() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.ink2)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AppTheme.type.itemTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.ink),
            cursorBrush = SolidColor(AppTheme.colors.primary),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .focusRequester(focus)
                .testTag(tag)
                .semantics { contentDescription = label },
        )
    }
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
    val selectedKey = state.accentKey?.let { WalletAccents.find(it)?.key } ?: if (state.accentKey == null) NONE_KEY else null
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        WalletAccents.all.forEachIndexed { index, accent ->
            Swatch(colorLabel(index), "walletEditor.color.$index", selectedKey == accent.key, accent.swatch, Modifier.weight(1f)) {
                onIntent(WalletEditorIntent.AccentChosen(accent.key))
            }
        }
        Swatch(none, "walletEditor.color.none", selectedKey == NONE_KEY, AppTheme.colors.surface, Modifier.weight(1f), outlined = true) {
            onIntent(WalletEditorIntent.AccentChosen(null))
        }
    }
}

@Composable
private fun Swatch(label: String, tag: String, selected: Boolean, color: Color, modifier: Modifier, outlined: Boolean = false, onClick: () -> Unit) {
    val ground = AppTheme.colors.background
    val ring = if (outlined) AppTheme.colors.ink2 else color
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .drawBehind {
                    if (selected) {
                        drawCircle(ring, radius = 22.dp.toPx())
                        drawCircle(ground, radius = 20.dp.toPx())
                    }
                }
                .background(color, CircleShape)
                .then(if (outlined) Modifier.border(1.dp, AppTheme.colors.outlineVariant, CircleShape) else Modifier),
        ) {
            if (outlined) Icon(AppIcons.Close, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(16.dp))
        }
    }
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
