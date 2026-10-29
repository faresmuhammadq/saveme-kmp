package com.fares.saveme.feature.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.server_explainer
import saveme.shared.generated.resources.server_field_hint
import saveme.shared.generated.resources.server_field_label
import saveme.shared.generated.resources.server_invalid
import saveme.shared.generated.resources.server_reachable
import saveme.shared.generated.resources.server_reset
import saveme.shared.generated.resources.server_save
import saveme.shared.generated.resources.server_test
import saveme.shared.generated.resources.server_testing
import saveme.shared.generated.resources.server_title
import saveme.shared.generated.resources.server_unreachable

@Composable
fun ServerAddressScreen(state: ServerAddressState, onIntent: (ServerAddressIntent) -> Unit) {
    AppScreen(
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.server_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(ServerAddressIntent.Back) }) },
            )
        },
    ) {
        Text(stringResource(Res.string.server_explainer), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        AppTextField(
            value = state.input,
            onValueChange = { onIntent(ServerAddressIntent.InputChanged(it)) },
            label = stringResource(Res.string.server_field_label),
            supportingText = stringResource(Res.string.server_field_hint, state.default),
            errorText = if (state.invalid) stringResource(Res.string.server_invalid) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("server.input"),
        )
        when (state.test) {
            ServerTestResult.Idle -> Unit
            ServerTestResult.Testing -> Text(stringResource(Res.string.server_testing), style = AppTheme.type.caption, color = AppTheme.colors.ink2)
            ServerTestResult.Reachable -> Text(stringResource(Res.string.server_reachable), style = AppTheme.type.caption, color = AppTheme.colors.primary)
            ServerTestResult.Unreachable -> Text(stringResource(Res.string.server_unreachable), style = AppTheme.type.caption, color = AppTheme.colors.error)
        }
        SecondaryButton(
            stringResource(Res.string.server_test),
            { onIntent(ServerAddressIntent.TestClicked) },
            enabled = state.test != ServerTestResult.Testing,
            modifier = Modifier.testTag("server.test"),
        )
        PrimaryButton(
            stringResource(Res.string.server_save),
            { onIntent(ServerAddressIntent.SaveClicked) },
            modifier = Modifier.testTag("server.save"),
        )
        AppTextButton(
            stringResource(Res.string.server_reset),
            { onIntent(ServerAddressIntent.ResetClicked) },
            modifier = Modifier.fillMaxWidth().testTag("server.reset"),
        )
    }
}

@Preview
@Composable
private fun ServerAddressScreenPreview() {
    AppTheme { ServerAddressScreen(ServerAddressState(input = "192.168.1.14:8091", default = "192.168.1.14:8091")) {} }
}
