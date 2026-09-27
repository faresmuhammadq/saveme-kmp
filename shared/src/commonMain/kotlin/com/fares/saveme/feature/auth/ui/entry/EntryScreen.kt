package com.fares.saveme.feature.auth.ui.entry

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_create_account
import saveme.shared.generated.resources.action_dev_tools
import saveme.shared.generated.resources.action_sign_in
import saveme.shared.generated.resources.app_name
import saveme.shared.generated.resources.entry_tagline

@Composable
fun EntryScreen(state: EntryState, onIntent: (EntryIntent) -> Unit) {
    AppScreen {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(Res.string.app_name),
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.entry_tagline), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(Res.string.action_sign_in), { onIntent(EntryIntent.SignInClicked) })
        SecondaryButton(stringResource(Res.string.action_create_account), { onIntent(EntryIntent.CreateAccountClicked) })
        if (state.isDebugTools) {
            AppTextButton(stringResource(Res.string.action_dev_tools), { onIntent(EntryIntent.DevToolsClicked) })
        }
    }
}

@Preview
@Composable
private fun EntryScreenPreview() {
    AppTheme { EntryScreen(EntryState(isDebugTools = true)) {} }
}
