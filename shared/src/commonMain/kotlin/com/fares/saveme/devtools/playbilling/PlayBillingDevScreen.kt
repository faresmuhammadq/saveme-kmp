package com.fares.saveme.devtools.playbilling

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SettingsSwitchRow
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.play_dev_acknowledged
import saveme.shared.generated.resources.play_dev_add
import saveme.shared.generated.resources.play_dev_clear
import saveme.shared.generated.resources.play_dev_count
import saveme.shared.generated.resources.play_dev_emit
import saveme.shared.generated.resources.play_dev_id_label
import saveme.shared.generated.resources.play_dev_pending
import saveme.shared.generated.resources.play_dev_price_label
import saveme.shared.generated.resources.play_dev_product_label
import saveme.shared.generated.resources.play_dev_set_price
import saveme.shared.generated.resources.play_dev_title
import saveme.shared.generated.resources.play_dev_unavailable
import saveme.shared.generated.resources.play_dev_use_scripted

@Composable
fun PlayBillingDevScreen(state: PlayBillingDevState, onIntent: (PlayBillingDevIntent) -> Unit) {
    AppScreen(
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.play_dev_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(PlayBillingDevIntent.Back) }) },
            )
        },
    ) {
        SettingsSwitchRow(
            stringResource(Res.string.play_dev_use_scripted),
            state.enabled,
            { onIntent(PlayBillingDevIntent.SetEnabled(it)) },
            Modifier.testTag("playDev.enabled"),
        )
        SettingsSwitchRow(
            stringResource(Res.string.play_dev_unavailable),
            state.unavailable,
            { onIntent(PlayBillingDevIntent.SetUnavailable(it)) },
            Modifier.testTag("playDev.unavailable"),
        )
        Text(stringResource(Res.string.play_dev_count, state.purchaseCount), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("playDev.count"))
        AppTextField(state.purchaseId, { onIntent(PlayBillingDevIntent.PurchaseIdChanged(it)) }, stringResource(Res.string.play_dev_id_label), Modifier.testTag("playDev.purchaseId"))
        AppTextField(state.productId, { onIntent(PlayBillingDevIntent.ProductIdChanged(it)) }, stringResource(Res.string.play_dev_product_label), Modifier.testTag("playDev.productId"))
        SettingsSwitchRow(stringResource(Res.string.play_dev_pending), state.pending, { onIntent(PlayBillingDevIntent.PendingChanged(it)) }, Modifier.testTag("playDev.pending"))
        SettingsSwitchRow(stringResource(Res.string.play_dev_acknowledged), state.acknowledged, { onIntent(PlayBillingDevIntent.AcknowledgedChanged(it)) }, Modifier.testTag("playDev.acknowledged"))
        SecondaryButton(stringResource(Res.string.play_dev_add), { onIntent(PlayBillingDevIntent.AddPurchase) }, Modifier.testTag("playDev.add"), enabled = state.canAdd)
        SecondaryButton(stringResource(Res.string.play_dev_emit), { onIntent(PlayBillingDevIntent.EmitUpdate) }, Modifier.testTag("playDev.emit"))
        SecondaryButton(stringResource(Res.string.play_dev_clear), { onIntent(PlayBillingDevIntent.ClearPurchases) }, Modifier.testTag("playDev.clear"))
        AppTextField(
            state.priceMicros,
            { onIntent(PlayBillingDevIntent.PriceChanged(it)) },
            stringResource(Res.string.play_dev_price_label),
            Modifier.testTag("playDev.price"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        SecondaryButton(stringResource(Res.string.play_dev_set_price), { onIntent(PlayBillingDevIntent.SetPrice) }, Modifier.testTag("playDev.setPrice"), enabled = state.canSetPrice)
    }
}

@Preview
@Composable
private fun PlayBillingDevScreenPreview() {
    AppTheme { PlayBillingDevScreen(PlayBillingDevState(enabled = true, purchaseCount = 1)) {} }
}
