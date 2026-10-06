package com.fares.saveme.feature.notifications.ui.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.notifications_turn_on
import saveme.shared.generated.resources.push_prompt_body
import saveme.shared.generated.resources.push_prompt_not_now
import saveme.shared.generated.resources.push_prompt_title

/**
 * The short explanation offered once per installation after a moment where notifications clearly help (FR-011). "Not now"
 * closes it for good; "Turn on" is the person's own action that may raise the system dialog (FR-010).
 */
@Composable
fun NotificationPromptSheet(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    AppBottomSheet(onDismiss = onNotNow) {
        PromptContent(onTurnOn, onNotNow)
    }
}

/** The sheet's content without the window, so screenshots can show it. */
@Composable
fun PromptContent(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.push_prompt_title),
            style = AppTheme.type.title,
            color = AppTheme.colors.ink,
            modifier = Modifier.testTag("pushPrompt.title").semantics { heading() },
        )
        Text(
            stringResource(Res.string.push_prompt_body),
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
            modifier = Modifier.testTag("pushPrompt.body"),
        )
        PrimaryButton(stringResource(Res.string.notifications_turn_on), onTurnOn, modifier = Modifier.testTag("pushPrompt.turnOn"))
        AppTextButton(stringResource(Res.string.push_prompt_not_now), onNotNow, Modifier.fillMaxWidth().testTag("pushPrompt.notNow"))
    }
}
