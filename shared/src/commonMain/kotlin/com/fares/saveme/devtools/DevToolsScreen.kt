package com.fares.saveme.devtools

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.dev_tools_components
import saveme.shared.generated.resources.dev_tools_paste_link
import saveme.shared.generated.resources.dev_tools_title

@Composable
fun DevToolsScreen(onIntent: (DevToolsIntent) -> Unit) {
    AppScreen(
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.dev_tools_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(DevToolsIntent.Back) }) },
            )
        },
    ) {
        SecondaryButton(stringResource(Res.string.dev_tools_components), { onIntent(DevToolsIntent.ComponentsClicked) })
        SecondaryButton(stringResource(Res.string.dev_tools_paste_link), { onIntent(DevToolsIntent.PasteLinkClicked) })
    }
}

@Preview
@Composable
private fun DevToolsScreenPreview() {
    AppTheme { DevToolsScreen {} }
}
