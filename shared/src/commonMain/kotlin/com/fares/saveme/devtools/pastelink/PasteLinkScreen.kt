package com.fares.saveme.devtools.pastelink

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun PasteLinkScreen(state: PasteLinkState, onIntent: (PasteLinkIntent) -> Unit) {
    AppScreen(
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.dev_tools_paste_link),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(PasteLinkIntent.Back) }) },
            )
        },
    ) {
        Text(stringResource(Res.string.paste_link_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        AppTextField(
            value = state.text,
            onValueChange = { onIntent(PasteLinkIntent.TextChanged(it)) },
            label = stringResource(Res.string.paste_link_field),
            modifier = Modifier.testTag("pasteLink.text"),
            errorText = if (state.parseError) stringResource(Res.string.paste_link_error) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = false,
        )
        PrimaryButton(
            stringResource(Res.string.action_open),
            { onIntent(PasteLinkIntent.OpenClicked) },
            modifier = Modifier.testTag("pasteLink.open"),
        )
    }
}

@Preview
@Composable
private fun PasteLinkScreenPreview() {
    AppTheme { PasteLinkScreen(PasteLinkState(parseError = true)) {} }
}
