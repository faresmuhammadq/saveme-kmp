package com.fares.saveme.feature.auth.ui.update

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.update_action
import saveme.shared.generated.resources.update_body
import saveme.shared.generated.resources.update_body_version
import saveme.shared.generated.resources.update_title

/** Blocking: no back action (FR-018). */
@Composable
fun UpdateRequiredScreen(state: UpdateRequiredState, onIntent: (UpdateRequiredIntent) -> Unit) {
    AppScreen {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(Res.string.update_title),
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            state.minimumVersion?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.update_body_version, it) }
                ?: stringResource(Res.string.update_body),
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(Res.string.update_action), { onIntent(UpdateRequiredIntent.UpdateClicked) })
    }
}

@Preview
@Composable
private fun UpdateRequiredScreenPreview() {
    AppTheme { UpdateRequiredScreen(UpdateRequiredState("0.3.0")) {} }
}
