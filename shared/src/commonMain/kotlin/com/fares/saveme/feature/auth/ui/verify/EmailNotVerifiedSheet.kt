package com.fares.saveme.feature.auth.ui.verify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_resend_email
import saveme.shared.generated.resources.error_email_not_verified

/** The verify prompt for an action the server refused with EMAIL_NOT_VERIFIED, never a bare denial (FR-014). */
@Composable
fun EmailNotVerifiedSheet(
    onDismiss: () -> Unit,
    viewModel: EmailNotVerifiedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppBottomSheet(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
            Text(stringResource(Res.string.error_email_not_verified), style = AppTheme.type.body, color = AppTheme.colors.ink)
            PrimaryButton(
                stringResource(Res.string.action_resend_email),
                { viewModel.onIntent(EmailNotVerifiedIntent.ResendClicked) },
                modifier = Modifier.testTag("verifySheet.resend"),
                enabled = state.canResend,
                loading = state.sending,
            )
            state.message?.let { Text(it.text(), style = AppTheme.type.caption, color = AppTheme.colors.ink2) }
            AppTextButton(stringResource(Res.string.action_cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
